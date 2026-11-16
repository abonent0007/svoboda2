# DPI-1.3/1.4 — Architecture Plan: ByeDPI inside Svoboda 2.0

## Current state (done)
- `third_party/byedpi` (ciadpi 17.3, MIT) built for arm64-v8a/x86_64 as PIE executable `libbyedpi.so`
- `third_party/hev-socks5-tunnel` (MIT) built for arm64-v8a/x86_64 as shared lib `libhev-socks5-tunnel.so`, 16KB aligned, exports `hev_socks5_tunnel_main_from_str` / `_quit` / `_stats`
- HEV API: `int hev_socks5_tunnel_main_from_str(const unsigned char*, unsigned int len, int tun_fd)` (blocking), `void hev_socks5_tunnel_quit(void)`
- HEV config sample: `tunnel: {name, mtu, ipv4, ipv6} socks5: {address, port, udp} misc: {log-level}`
- GoBackend (vendored) creates TUN via `builder.establish()`, then `tun.detachFd()` → `wgTurnOn(fd)`; no public handoff API
- One VpnService/TUN owner (GoBackend.VpnService) — hard constraint

## Goal
App traffic → TUN (single, owned by GoBackend) → HEV (in-process, tun2socks) → SOCKS5 127.0.0.1:1080 (ciadpi, separate process) → Internet with DPI bypass.

## Plan

### 1. Packaging
- Copy `libbyedpi.so` (arm64-v8a/x86_64) and `libhev-socks5-tunnel.so` into `app/src/main/jniLibs/<abi>/`
- `android:extractNativeLibs="true"` (explicit) so ciadpi can be exec'd from `nativeLibraryDir`

### 2. HEV JNI wrapper (own file `app/src/main/cpp/hev_dpi_jni.cpp`, MIT-own code, NOT GPL)
- `nativeStart(configStr: String, tunFd: Int): Boolean` — spawns thread → `hev_socks5_tunnel_main_from_str(cfg.bytes, cfg.len, tunFd)`
- `nativeStop(): Unit` — calls `hev_socks5_tunnel_quit()`
- `nativeStats(): LongArray` — wraps `hev_socks5_tunnel_stats`
- CMake: link `libhev-socks5-tunnel.so` from jniLibs

### 3. GoBackend DPI handoff (minimal patch to vendored GoBackend)
- New `GoBackend.DpiMode` / `dpiSession: DpiSession?`
- `setDpiSession(session)` + `enableDpiMode(boolean)`
- In `setState`: when DPI mode and state == UP → after `establish()` + `detachFd()`, pass fd to `dpiSession.onTunFd(fd)` and **do not call `wgTurnOn()`**
- DPI establish uses same builder; config can be a minimal synthetic AWG config (MTU from prefs, no AWG-specific fields needed because wgTurnOn is skipped); empty [Peer] acceptable (verify)

### 4. DpiEngine (Kotlin, `dpi/DpiEngine.kt`)
- `start(config: DpiConfig): Boolean`:
  1. Ensure ciadpi process not running; exec `File(nativeLibraryDir, "libbyedpi.so")` with `Array<String>` args built from Prefs (BDP strategy or custom), `ProcessBuilder`
  2. Wait for SOCKS5 port accepting (poll `Socket("127.0.0.1", port).connect`, 20×250ms, overall 5s; AbortSignal if process died)
  3. Ask GoBackend to establish TUN in DPI mode (fd handoff) → HEV `nativeStart(yaml, fd)`
  4. Verify via Cloudflare trace → mark CONNECTED (reuse NetProbe) or DEGRADED/FAILED
- `stop()`: `nativeStop()`; `process.destroy()` (SIGTERM), `waitFor` 2s, force-kill if needed; report DOWN
- Monitor ciadpi process; on unexpected exit → state ERROR, block reconnect loops
- Reconnect: bounded retries, never re-register WARP/AWG in DPI mode

### 5. Lifecycle integration (VpnController / ConnectionManager)
- `ConnectionMethod.DPI_DIRECT.available = true` once engine works
- `ConnectionManager.connectManual()` / AUTO path: if `prefs.connectionMethod == "DPI_DIRECT"` → `DpiEngine.connect()` instead of AWG/WARP
- `VpnLifecycleCallback` unchanged; `onRevoke` already stops everything (and DpiEngine.stop is called from AppGraph too)
- `AppGraph.dpiEngine` registered once; callbacks funnel through existing ConnectionStateRepository
- FGS: existing notification remains ("VPN-туннель активен")

### 6. DPI process & tunnel ordering (stop path)
1. User/system disconnect → `VpnController.disconnect()` → `DpiEngine.stop()`:
   - `nativeStop()` (HEV quit, TUN closed by GoBackend) → TUN FD returned/closed
   - ciadpi process `destroy()` + `waitFor`
   - publish STOPPED
2. Failure mid-start: all handles rolled back, no half-open TUN, no zombie process
3. Always-on path stays AWG/WARP (DPI not restored by Always-on in this stage)

### 7. Split tunnel / DNS in DPI mode
- Reuse `SplitTunnelManager.buildInjection` — works if the establish builder honors `IncludedApplications`/`ExcludedApplications` from the synthetic config ✓ (verify)
- DNS from prefs → synthetic config

### 8. Verification order
1. DPI_DIRECT connects; `tun0` visible; `lsof` shows ciadpi process; SOCKS5 1080 listening
2. HTTPS trace through HEV; YouTube/Telegram over DPI_DIRECT on Wi-Fi and LTE
3. AWG/WARP unaffected (regression: connect/disconnect AWG, WARP)
4. Always-on unchanged; Disconnect leaves no zombie ciadpi
5. DEGRADED / handshake failure does not kill AWG/WARP path

## ADDED (after GPT review of this plan)

### Blocker 1 — own-package exclusion (ciadpi as separate process)
- In DPI mode GoBackend establishes TUN with `addDisallowedApplication(packageName)` — own package bypasses VPN (Nova/ByeByeDPI pattern). Otherwise ciadpi's outbound sockets route via TUN → infinite recursion.
- Loopback is NOT captured by VPN 0.0.0.0/0 (longest-prefix match), so HEV→ciadpi (127.0.0.1:1080) is safe without exclusion.
- Consequence: NetProbe (HTTPS through TUN) CANNOT prove DPI tunnel in DPI mode. Replacement proof:
  1. **SOCKS5 CONNECT** via `SOCKS5 127.0.0.1:1080 CONNECT 1.1.1.1:443` from app — proves ciadpi→internet works.
  2. **HEV alive** — thread still running / `nativeStats` responds.
  3. Show honest status: «DPI активен» / «Обход DPI не работает».
- Split-tunnel rules (Russian direct) still apply for other apps (Included/Excluded applications from prefs) — unchanged.

### Blocker 2 — synthetic AWG config with one dummy peer
- Must NOT use empty-peer config (routes come only from peer AllowedIPs → no default route).
- DPI synthetic config:
  ```
  [Interface]
  PrivateKey = <dummy 44-char base64>   (never used; wgTurnOn skipped)
  Address = 10.0.0.2/32
  DNS = 1.1.1.1
  MTU = <prefs.tunnelMtu>
  ExcludedApplications = svoboda.runet.abonent0007, <user excluded>
  (or IncludedApplications when ONLY_SELECTED)

  [Peer]
  PublicKey = <dummy 44-char base64>
  AllowedIPs = 0.0.0.0/0
  ```
- `awgTurnOn()` skipped in DPI mode; TUN fd handed to HEV instead.
- Route 0.0.0.0/0 created → apps (except our package) route into TUN → HEV → ciadpi → internet.

### Blocker 3 — DPI lifecycle inside GoBackend
- Add explicit FSM to GoBackend for DPI: `DPI_NONE → DPI_STARTING → DPI_UP → DPI_STOPPING → DPI_NONE`.
- `getState()`, `getRunningTunnelNames()`, `setState(DOWN)`, `onRevoke()`, `onDestroy()` all respect the DPI branch symmetrically with the WG branch. No fake WG handles.
- `currentTunnel`/`currentTunnelHandle` untouched; TUN fd held via `dpiSession`; `onDestroy()` closes fd exactly once and quits HEV.
- FD ownership: `nativeStart()` holds FD; `nativeStop()` calls `hev_socks5_tunnel_quit()`, joins HEV thread, closes FD if HEV didn't.
- Async start: `nativeStart(cfg, tunFd)` returns after HEV thread enters blocking call; HEV readiness signaled back via callback/CountDownLatch; errors reported to `VpnController` for UI + rollback.

## Open questions for review (revised)

1. Is it acceptable to keep ciadpi as separate *process* (Nova-style) rather than in-process JNI? (Yes — simpler lifecycle, cleaner kill on disconnect; matches ByeByeDPI/Nova pattern.)
2. Minimal synthetic AWG config for establish in DPI mode vs exposing a new establish path in GoBackend? (Reuse establish; zero-peer config must be verified to not crash builder.)
3. FGS for ciadpi: keep single VpnService FGS notification, no second FGS — acceptable? (Yes — single foreground VPN is the only FGS.)
4. DPI process restart policy on crash: bounded (1 retry) + user-visible error, not infinite backoff — acceptable?

## Files & commits
1. Commit 1 — `chore(DPI-1.3a)`: package native libs + JNI wrapper + GoBackend handoff
2. Commit 2 — `feat(DPI-1.3b)`: DpiEngine + ciadpi process lifecycle + ConnectionState wiring
3. Commit 3 — `feat(DPI-1.4)`: DPI_DIRECT connect path, split-tunnel integration, UI enabled
4. Commit 4 — `test(DPI-1.5)`: on-device validation report
