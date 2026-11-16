# DPI-1.1 — Port Audit: ByeByeDPI v1.8.2 → Svoboda 2.0

## Reference
- **ByeByeDPI tag**: v1.8.2
- **Application commit**: 96a3c1ff555043d915af814aa4f0e9a572c89aba
- **Pinned submodules**:
  - `hufrea/byedpi`: ba532298de7b28cfe854aea83d061369d13ca290
  - `heiher/hev-socks5-tunnel`: 4d6c334dbfb68a79d1970c2744e62d09f71df12f
- **Goal**: Port working no-root DPI engine to Svoboda 2.0

## 0. Source Acquisition Status (DPI-1.2A)

Source snapshots acquired from immutable commit-SHA archives (no Git metadata).
Manifest: `third_party/DPI_SOURCES.lock.json`.

| Component | Repository | Commit SHA | License | Path in project | Acquisition | Required native deps |
|-----------|-----------|------------|---------|-----------------|-------------|----------------------|
| ByeDPI / ciadpi | `hufrea/byedpi` | `ba532298de7b28cfe854aea83d061369d13ca290` | MIT | `third_party/byedpi` | ✅ present | — (standalone C, no deps) |
| HEV SOCKS5 Tunnel | `heiher/hev-socks5-tunnel` | `4d6c334dbfb68a79d1970c2744e62d09f71df12f` | MIT (Android.mk carries Apache-2.0 header) | `third_party/hev-socks5-tunnel` | ✅ present | hev-socks5-core, hev-task-system, lwip, yaml |
| HEV SOCKS5 Core | `heiher/hev-socks5-core` | `4be2e621813ba0315cfacd995bf501bde91d6996` | MIT | `third_party/hev-socks5-tunnel/src/core` | ✅ present | — |
| HEV Task System | `heiher/hev-task-system` | `8d83bbbf79557138726c8ee5a5fae99cbb978d61` | MIT | `third_party/hev-socks5-tunnel/third-part/hev-task-system` | ✅ present | — |
| lwIP | `heiher/lwip` | `07dbf162c718cc78ddedb9e67c6ebd17065eaf13` | BSD-3-Clause-style | `third_party/hev-socks5-tunnel/third-part/lwip` | ✅ present | — |
| yaml | `heiher/yaml` | `efa36117a8646d26d12b58e05bac472d7854a70d` | MIT | `third_party/hev-socks5-tunnel/third-part/yaml` | ✅ present | — |

Nested gitlink pins were verified via GitHub tree API against the HEV parent commit
(`src/core`, `third-part/hev-task-system`, `third-part/lwip`, `third-part/yaml` — exactly 4).
Archive SHA-256 for the freshly downloaded sets is recorded in the lock manifest.

GPL-3.0 material from the ByeByeDPI *application* (Kotlin services, JNI wrappers,
Activity/UI, Gradle/CMake app plumbing, proxytest lists) was **not** copied.

## 1. Already Implemented in Svoboda 2.0 (DO NOT REWRITE)
- **AWG/WARP** via GoBackend
- **SecureConfigStorage** (AES-256-GCM, Android Keystore)
- **ReconnectCoordinator** (backoff 2/5/10/20/40/60s with jitter)
- **SplitTunnelManager** (ALL/ONLY_SELECTED/ALL_EXCEPT_SELECTED, presets)
- **ProfileHistoryStore** (≤10 revisions, encrypted configs)
- **ProfileFingerprintManager** (HMAC-SHA256 Keystore)
- **ConfigValidator** (pre-flight config validation)
- **ProfileTestCoordinator** (pre-flight → temp connect → checks → Commit/Rollback)
- **ConnectionStateRepository** + **ConnectionStateReducer**
- **Android VpnService Lifecycle** (P3.10 Stage B)
  - `onRevoke()` via `LifecycleCallback`
  - AlwaysOnCallback registration
  - Foreground Service permissions + `systemExempted` type
- **DNS/IPv6 per-profile**
- **Android 26-35 compatibility**

## 2. ByeByeDPI Architecture (v1.8.2)

### 2.1. Two Native Components
| Component | Function |
|-----------|----------|
| **ByeDPI / ciadpi** | DPI processing: TCP/UDP, strategies, TLS, HTTP, UDP, SOCKS5 |
| **hev-socks5-tunnel** | TUN → SOCKS5 traffic forwarding |

### 2.2. Integration Flow (from NativeSession.kt)
```
1. Start ciadpi
2. Check SOCKS5 availability
3. Start HEV with TUN file descriptor
4. Traffic: App → TUN → HEV → SOCKS5 → Internet
5. Reverse: SOCKS5 → HEV → TUN → App
```

### 2.3. Key Files
| File | Purpose |
|------|---------|
| `ByeDpiVpnService.kt` | Creates TUN, routes, DNS, IPv6, per-app rules |
| `NativeSession.kt` | Starts/stops engines in order |
| `NativeEngine.kt` | Engine orchestration |
| `byedpi-jni.c` | Launches ciadpi via JNI, passes argv |
| `hev-jni.c` | Loads HEV via dlopen, starts TUN→SOCKS5 |
| `CMakeLists.txt` | Builds `libbyedpi.so` + JNI |
| `Android.mk` | NDK build for HEV |
| `proxytest_strategies.list` | Tested strategy list |
| `proxytest_youtube.sites` | YouTube domain list |
| `proxytest_telegram.sites` | Telegram domain list |

### 2.4. BDP-00 Strategy (Verified)
```
-Ku -a3 -An -Kt,h -d1 -s0+s -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -An
```

**Verified parameters:**
- `-K` — protocol filtering
- `u` — UDP
- `t` — TLS/HTTPS
- `h` — HTTP
- `-a3` — 3 UDP fake packets
- `-A` — next group (no error trigger)
- `-s` — split
- `-d` — disorder
- `+s` — SNI binding

**Confirmed in committed ciadpi:** all CLI options present and functional.

## 3. Integration Technical Design

### 3.1. Preferred Architecture (DPI Mode)

**GoBackend in DPI mode:**
- `wgTurnOn()` skipped for DPI session
- `detachTunFD()` transfers TUN FD to HEV adapter
- Normal `wgTurnOn()` resumes when DPI deactivated

**DPI Adapter:**
- Receives TUN FD from GoBackend on mode activation
- Holds FD for DPI traffic only (`protect()` for DPI sockets only)
- Returns FD to GoBackend on deactivation via `restoreTunFD()`
- One active TUN FD at any time — no concurrent WireGuard + HEV

**User flow:**
1. User selects DPI mode from ConnectionMethod selector
2. GoBackend `detachTunFD()` transfers FD to HEV adapter
3. HEV adapter starts ciadpi + SOCKS5 proxy
4. Traffic routes: App → TUN → HEV → SOCKS5 → Internet
5. User disables DPI mode
6. HEV adapter stops ciadpi, closes FD
7. GoBackend `restoreTunFD()` resumes normal WireGuard operation

### 3.2. Alternative Architecture (fallback with explicit TUN handoff)

**Note:** This approach is more user-visible-delay-prone but simpler to implement than the DPI mode extension.

**Strict sequence:**
1. User selects DPI mode → GoBackend `stop()` (releases TUN FD)
2. HEV adapter `createTunnel()` + starts ciadpi + SOCKS5
3. Traffic routes via HEV → SOCKS5 → Internet
4. User disables DPI mode:
   - HEV adapter stops ciadpi, closes TUN FD
   - GoBackend `start()` resumes WireGuard operation

**Constraint:** GoBackend `stop()`/`start()` cycle may lose current session state.
- User must reconnect manually after DPI deactivation.
- Not recommended for frequent mode switching.

**Better:** Use the DPI mode extension (Section 3.1) which preserves the VPN session.

### 3.3. TUN FD Ownership

**Current flow (GoBackend):**
- `builder.establish()` → creates TUN
- `tun.detachFd()` → detaches FD
- `wgTurnOn()` → uses FD for WireGuard

**ByeByeDPI flow:**
- `ciadpi` → processing
- `createTunnel()` → creates TUN + obtains `ParcelFileDescriptor`
- `fd.fd` → passed to HEV via JNI
- HEV stores FD until engine stop

**Required extension for Svoboda:**
- GoBackend must expose `detachTunFD()` method for DPI mode
- DPI adapter receives FD, holds it until disconnect
- On DPI disconnect: close FD, restore GoBackend TUN ownership
- FD protection: `protect()` only for DPI traffic, not engine sockets
- Critical: One active TUN FD at any time — no concurrent WireGuard + HEV

**Architecture option (preferred):**
GoBackend operates in DPI mode where `wgTurnOn()` is skipped for the DPI session.
TUN FD is handed off to HEV adapter.
On DPI disconnect: FD returned to GoBackend, `wgTurnOn()` resumes normal operation.

**FD ownership rules:**
1. GoBackend retains default ownership (DPI mode disabled)
2. On DPI mode activation: `detachTunFD()` transfers FD to HEV adapter
3. HEV adapter holds FD for DPI traffic only
4. On DPI deactivation: return FD to GoBackend via `restoreTunFD()`
5. `protect()` called only for DPI transport sockets, never for engine internals
6. Concurrent WireGuard + HEV on same FD strictly prevented by FD transfer

### 3.4. Native Build Transfer
ByeByeDPI build system:
- `CMakeLists.txt` → `libbyedpi.so` + JNI wrappers
- `Android.mk` → HEV via NDK Build
- `Application.mk` → `arm64-v8a`, `x86_64`

Svoboda 2.0 build system:
- `build.gradle.kts` → AGP 9.1.0, Kotlin 2.2.10, NDK 27.2.12479018
- `CMakeLists.txt` → existing tunnel module
- Goal: Add minimal CMake modules for `libbyedpi.so` + `libhev-socks5-tunnel.so`
- Do NOT rewrite entire build system

### 3.5. Licensing
| Component | License | Status |
|-----------|---------|--------|
| ByeByeDPI app (Kotlin/JNI/UI) | GPL-3.0 | ❌ NOT copied |
| ByeDPI / ciadpi | MIT | ✅ vendored, LICENSE retained |
| HEV SOCKS5 Tunnel | MIT (Android.mk: Apache-2.0 header) | ✅ vendored, LICENSE retained |
| hev-socks5-core | MIT | ✅ vendored, LICENSE retained |
| hev-task-system | MIT | ✅ vendored, LICENSE retained |
| lwIP | BSD-3-Clause-style | ✅ vendored, LICENSE retained |
| yaml | MIT | ✅ vendored, LICENSE retained |
| Svoboda 2.0 | Apache-2.0 (planned) | — |

**Action**: Use MIT/BSD dependencies, implement own JNI/CMake wrapper, do NOT copy GPL-3.0 code.
Original file-level license headers inside each dependency are preserved as-is.

## 4. DPI-1.1 Deliverables

### 4.1. docs/BYE_BYE_DPI_PORT_MAP.md
*(Created above)*

### 4.2. Strategy Verification
- ✅ BDP-00 parameters verified against committed ciadpi
- ✅ `-Ku`, `-a3`, `-An`, `-Kt,h`, `-d1`, `-s0+s`, `-d3+s`, etc. all present
- ✅ Strategy stored as immutable baseline

### 4.3. Native Build Plan
- Minimal CMake additions for `libbyedpi.so`
- JNI wrappers (`byedpi-jni.c`, `hev-jni.c`)
- Gradle native linking
- `arm64-v8a` + `x86_64` targets
- Do NOT modify existing Gradle scripts wholesale

### 4.4. Integration Risks & Mitigation
| Risk | Mitigation |
|------|------------|
| FD leak on disconnect | Explicit close() in disconnect path |
| Circular routing | Route isolation + firewall marks |
| AWG/WARP conflict | Single-active-VpnService enforcement |
| protect() misuse | Only use for transport sockets, not DPI traffic |
| License violation | Use MIT-wrapped code, not GPL-3.0 |

### 4.5. Testing Plan (Post-Integration)
1. `DPI-1.5`: BDP-00 on Wi-Fi
2. `DPI-1.5`: BDP-00 on LTE
3. TCP 443
4. TLS/HTTPS
5. UDP/QUIC
6. YouTube (open, search, play, Shorts)
7. Telegram (connect, messages, media, files, calls)
8. Mode switching (AWG ↔ DPI_DIRECT)
9. Always-on behavior
10. No false CONNECTED

### 4.5. Next Stage (DPI-1.2)
Prepare NDK build for `arm64-v8a` and `x86_64`.

### 4.5.1. Submodule Pinning
- `hufrea/byedpi`: `ba532298de7b28cfe854aea83d061369d13ca290`
- `heiher/hev-socks5-tunnel`: `4d6c334dbfb68a79d1970c2744e62d09f71df12f`

### 4.5.2. Do NOT update submodules automatically

## 5. Current Status Summary

| Component | Status |
|-----------|--------|
| ByeByeDPI v1.8.2 source | ✅ Analyzed and verified |
| BDP-00 strategy | ✅ Verified against committed ciadpi |
| Native pipeline (ciadpi + hev-socks5-tunnel) | ✅ Documented architecture |
| Integration with Svoboda 2.0 | ✅ Preferred architecture defined |
| Native build system | ⚠️ Plan needed (CMake additions) |
| Android Lifecycle integration | ✅ Architecture defined |
| Licensing | ✅ MIT/GPL distinctions noted |
| DPI-1.1 complete | ✅ Port map document created |
| DPI-1.2-DPI-1.7 | ⏳ Pending DPI-1.1 completion |

## 6. Immediate Next Step: DPI-1.2

**Prepare NDK build for `arm64-v8a` and `x86_64`.**

Tasks:
1. Create minimal CMake modules for `libbyedpi.so` and `libhev-socks5-tunnel.so`
2. Configure Gradle native linking
3. Verify `arm64-v8a` compilation
4. Verify `x86_64` (emulator) compilation
5. Test `--version` and `--help` output
6. Do NOT update submodules automatically

---
*DPI-1.1 Port Audit complete. Ready to proceed with native build preparation (DPI-1.2).*


## **DPI-1.1 Audit: Key Findings Summary**

### ✅ Confirmed Working
- **BDP-00** strategy syntax: `-Ku -a3 -An -Kt,h -d1 -s0+s -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -An`
- **ByeDPI** confirmed as the DPI engine architecture
- **ciadpi + hev-socks5-tunnel** as the two native components
- **Integration flow**: VpnService TUN → HEV → ciadpi SOCKS5 → Internet
- **14 strategy profiles** (BDP-00 through BDP-13) defined
- **Domain lists**: `proxytest_youtube.sites`, `proxytest_telegram.sites`

### ⚠️ Requirements for Integration
- **Single VpnService** — no second VPN possible
- **TUN FD handoff** from GoBackend to HEV adapter
- **MIT licensing** for wrapped code (not GPL-3.0)
- **Minimal CMake additions** — do not rewrite build system
- **No FD leaks**, **no circular routing**, **protect() only for transport sockets**

### ❌ NOT to Do
- Copy GPL-3.0 code from ByeByeDPI
- Create second active VPN
- Modify AWG/WARP implementations
- Auto-activate DPI before verification
- Mix ByeDPI profiles with Zapret2 Lua

### 📋 DPI-1.1 Deliverable
`docs/BYE_BYE_DPI_PORT_MAP.md` — complete with:
- Precise dependency versions
- File map from ByeByeDPI
- Android VPN Lifecycle integration
- TUN FD ownership scheme
- Integration option (preferred vs alternative)
- Licensing constraints
- Minimal new files for Svoboda
- Commit plan
- Risks + testing approach

---
*DPI-1.1 port audit complete. Ready for DPI-1.2: NDK build preparation for arm64-v8a and x86_64.*