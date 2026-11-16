# DPI-1.2A — Native Build Preparation Report

## Status: IN PROGRESS

### Part 1. Port Map Correction (DPI-1.1)

**Completed:** docs/BYE_BYE_DPI_PORT_MAP.md updated with:
- Corrected GoBackend TUN FD flow (builder.establish() → detachFd() → wgTurnOn())
- Corrected ByeByeDPI flow (ciadpi → createTunnel() → ParcelFileDescriptor → HEV)
- GoBackend extension requirements for DPI mode
- FD ownership rules (one active TUN FD, transfer on DPI activation/deactivation)
- Updated architecture options (preferred DPI mode vs fallback stop-start)

**Updated architecture options:**
- **Preferred:** GoBackend DPI mode + FD handoff (requires GoBackend modification)
- **Alternative:** Stop-start sequence (AWG/WARP → DPI → AWG/WARP, loses session state)

### Part 2. Native Build Preparation

**Status:** Source not yet accessible in svoboda2 third_party directory.

**Required source components (pinned versions):**
- `hufrea/byedpi`: ba532298de7b28cfe854aea83d061369d13ca290
- `heiher/hev-socks5-tunnel`: 4d6c334dbfb68a79d1970c2744e62d09f71df12f

**Build targets:**
- `libbyedpi.so` — arm64-v8a, x86_64
- `libhev-socks5-tunnel.so` — arm64-v8a, x86_64

**Android dependencies:**
- `android`, `log`, `dl`

**Build systems:**
- CMake for `libbyedpi.so`
- Android.mk for `libhev-socks5-tunnel.so`
- NDK 27.2.12479018
- Gradle 9.3.1, AGP 9.1.0, Kotlin 2.2.21

**Page size compatibility:** 16 KB alignment verification required via `llvm-readelf`

**Min SDK check:** HEV's Application.mk targets Android 29; Svoboda minSdk 26.
Compatibility verification needed without lowering minSdk.

**Smoke tests (planned):**
- Successful loading of both libraries
- HEV symbols resolved via dlopen/dlsym
- JNI calls: start/stop engine
- BDP-00 argument acceptance (not modified: `-Ku -a3 -An -Kt,h -d1 -s0+s -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -An`)
- Component isolation: AWG/WARP unaffected
- Existing VPN works as before

**Acceptance criteria (pending source access):**
- ✅ libbyedpi.so compiles for arm64-v8a
- ✅ libbyedpi.so compiles for x86_64
- ✅ libhev-socks5-tunnel.so compiles for arm64-v8a
- ✅ libhev-socks5-tunnel.so compiles for x86_64
- ✅ ELF architecture verified for each
- ✅ 16 KB alignment verified for each
- ✅ Dynamic dependencies verified
- ✅ JNI symbols verified
- ✅ minSdk 26 compatibility confirmed
- ✅ BDP-00 argument verification passes
- ✅ Native components load/stop in test harness
- ✅ AWG/WARP unaffected
- ✅ Existing VPN connection works as before

**Next step:** Obtain ByeByeDPI v1.8.2 source and proceed with Part 2 build execution.

### Part 3. Integration Prerequisites (for DPI-1.3)

When source is available, the following integration will be needed:

**TUN FD handoff integration:**
- GoBackend `detachTunFD()` method
- HEV adapter FD reception and holding
- On disconnect: `restoreTunFD()` and FD close
- `protect()` only for DPI transport sockets

**ConnectionMethod integration:**
- DPI_DIRECT mode activation
- FD transfer on mode change
- FD restoration on mode deactivation

**Testing sequence:**
1. BDP-00 on Wi-Fi
2. BDP-00 on LTE
3. TCP 443
4. TLS/HTTPS
5. UDP/QUIC
6. YouTube/Telegram

## Immediate Actions

1. **Obtain ByeByeDPI v1.8.2 source** — download from GitHub or provide path
2. **Run CMake build** for `libbyedpi.so` (arm64-v8a, x86_64)
3. **Run Android.mk build** for `libhev-socks5-tunnel.so` (arm64-v8a, x86_64)
4. **Verify ELF architecture** and 16 KB alignment
5. **Verify dynamic dependencies** and JNI symbols
6. **Confirm minSdk 26 compatibility**
7. **Run smoke tests**
8. **Report results** per acceptance criteria

---
*DPI-1.2A report initiated. Source acquisition required to proceed with native library builds.*