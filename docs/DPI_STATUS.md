# DPI Status — Svoboda 2.0

## Overview

| Stage | Description | Status |
|-------|-------------|--------|
| P3.10 | Android VPN Lifecycle (onRevoke, Always-on, FGS perms) | ✅ code done — device test pending |
| DPI-0 | Forensic audit of original DPI mechanism | ✅ done (gap documented) |
| DPI-1.1 | Port audit of ByeByeDPI v1.8.2 + TUN FD ownership correction | ✅ done |
| DPI-1.2A | Source acquisition (6 pinned sets) | ✅ done |
| DPI-1.2A | Native build bootstrap (libbyedpi.so, libhev-socks5-tunnel.so) | ⏳ next |
| DPI-1.3 | Local SOCKS5 integration | ⏳ pending builds |
| DPI-1.4 | TUN → HEV → ciadpi inside VPN lifecycle | ⏳ pending builds |
| DPI-1.5–1.7 | TCP/UDP/QUIC + YouTube/Telegram tests, BDP-01..BDP-13 | ⏳ pending |
| Zapret2 C/Lua | Separate research track | ⏸ not started, not mixed with ByeDPI |

## 1. Acquired sources (DPI-1.2A)

Manifest: `third_party/DPI_SOURCES.lock.json` (2026-11-16).

| Component | Repository | Commit SHA | License | Path in project | Acquisition | Required native deps |
|-----------|-----------|------------|---------|-----------------|-------------|----------------------|
| ByeDPI / ciadpi | `hufrea/byedpi` | `ba532298de7b28cfe854aea83d061369d13ca290` | MIT | `third_party/byedpi` | ✅ present | none |
| HEV SOCKS5 Tunnel | `heiher/hev-socks5-tunnel` | `4d6c334dbfb68a79d1970c2744e62d09f71df12f` | MIT (Apache-2.0 headers in Android.mk) | `third_party/hev-socks5-tunnel` | ✅ present | core, task-system, lwip, yaml |
| HEV SOCKS5 Core | `heiher/hev-socks5-core` | `4be2e621813ba0315cfacd995bf501bde91d6996` | MIT | `third_party/hev-socks5-tunnel/src/core` | ✅ present | none |
| HEV Task System | `heiher/hev-task-system` | `8d83bbbf79557138726c8ee5a5fae99cbb978d61` | MIT | `third_party/hev-socks5-tunnel/third-part/hev-task-system` | ✅ present | none |
| lwIP | `heiher/lwip` | `07dbf162c718cc78ddedb9e67c6ebd17065eaf13` | BSD-3-Clause-style | `third_party/hev-socks5-tunnel/third-part/lwip` | ✅ present | none |
| yaml | `heiher/yaml` | `efa36117a8646d26d12b58e05bac472d7854a70d` | MIT | `third_party/hev-socks5-tunnel/third-part/yaml` | ✅ present | none |

Verification: HEV parent tree contains exactly 4 gitlinks, all matching the pinned SHAs.
Licenses retained in place. GPL-3.0 ByeByeDPI application code was not copied.

## 2. TUN FD ownership (corrected in DPI-1.1)

### GoBackend (current)
`builder.establish()` → TUN → `tun.detachFd()` → `wgTurnOn(fd)`.
There is **no** public API in the standard GoBackend to hand the FD to an external adapter.

### ByeByeDPI (reference)
`ciadpi` starts → `createTunnel()` → `ParcelFileDescriptor` → `fd.fd` passed to HEV via JNI,
held until engine stop.

### Required extension for Svoboda
- `detachTunFD()` — GoBackend releases FD to the DPI adapter (skips `wgTurnOn` for this session).
- `restoreTunFD()` — FD returned to GoBackend on DPI deactivation.
- One active TUN FD at all times; WireGuard and HEV must never share it concurrently.
- `protect()` applies only to DPI transport sockets, never to engine internals.

### Architecture options
- **Preferred:** GoBackend DPI mode + explicit FD handoff (requires the extension above).
- **Fallback:** strict stop → start sequence (AWG/WARP stops, DPI starts; reverse on exit).
  Simpler, but loses session state and shows a visible reconnect delay.

## 2.5. Native build results (DPI-1.2B, host-side)

| Artifact | ABI | Result |
|----------|-----|--------|
| `third_party/hev-socks5-tunnel/libs/arm64-v8a/libhev-socks5-tunnel.so` | arm64-v8a | ✅ 342 KB, ELF64 AArch64, LOAD align `0x4000` |
| `third_party/hev-socks5-tunnel/libs/x86_64/libhev-socks5-tunnel.so` | x86_64 | ✅ 343 KB, ELF64 x86-64, LOAD align `0x4000` |
| `third_party/byedpi/dist/arm64-v8a/libbyedpi.so` | arm64-v8a | ✅ PIE executable (ciadpi 17.3) |
| `third_party/byedpi/dist/x86_64/libbyedpi.so` | x86_64 | ✅ PIE executable (ciadpi 17.3) |

- HEV собран `ndk-build` с `APP_PLATFORM=android-26` (совместим с minSdk 26), экспортирует
  `hev_socks5_tunnel_main_from_str` и `hev_socks5_tunnel_quit`, зависимости — `libc/libm/libdl`.
- ByeDPI собирается как **исполняемый** файл с именем `libbyedpi.so` (запуск отдельным процессом,
  остановка `destroy()`), как в Nova/ByeByeDPI.
- Обнаружено и исправлено: GitHub-архивы хранят симлинки как текстовые файлы; 31 заголовок
  материализован скриптом `scripts/fix_symlinks.ps1` (иначе HEV не собирается).
- **Проверка парсера BDP-00 (host-сборка ciadpi 17.3):**
  - `--version` → `17.3` (совпадает с закреплённой версией);
  - неизвестный флаг `-Z` → отказ (exit -1);
  - BDP-00 целиком принят, процесс жив, SOCKS5 слушает порт 1080 (`Get-NetTCPConnection`: 1 listener).

## 3. Native build bootstrap — next actions

1. Author Svoboda-owned JNI wrapper for ciadpi (argv array, no shell string).
2. Add CMake target for `libbyedpi.so`; link `android`, `log`, `dl`.
3. Build HEV via its `Android.mk`/`build.mk` semantics → `libhev-socks5-tunnel.so`.
4. ABIs: `arm64-v8a`, `x86_64`.
5. Verify: ELF arch, 16 KB alignment, dynamic deps, exported symbols.
6. Assess `android-29` → `minSdk 26` compatibility (do not change minSdk automatically).
7. Smoke test: load/stop both libraries; pass BDP-00 as argv and confirm parser acceptance.

### BDP-00 (control profile — immutable)
```
-Ku -a3 -An -Kt,h -d1 -s0+s -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -An
```
Passed as an `Array<String>`, never as a single shell string.

## 4. Guardrails (unchanged)

- ❌ Second VpnService / second TUN
- ❌ GoBackend, AWG, WARP, ReconnectCoordinator, SecureConfigStorage modifications at this stage
- ❌ Mixing ByeDPI profiles with Zapret2 Lua
- ❌ Activating AUTO before individual transports are proven
- ❌ Presenting DPI_DIRECT / AUTO as working modes — they remain "in development"
- ✅ BDP-00 always retained as fallback

## 5. Build & UI status (2026-11-16)

### 5.1. APK build — SUCCESS
Toolchain PATH fix (`git\usr\bin`, `mingw64\bin`, `go\bin`) resolved the NDK `uname -m` failure;
`:tunnel:buildCMakeDebug[*]` now links and the APK packages all native libs.

| Artifact | Size | SHA-256 |
|----------|------|---------|
| `app-arm64-v8a-debug.apk` | 23.2 MB | `F1131260222888958A14902081C5C055995B67A7CA199637848FAF2116B18225` |
| `app-armeabi-v7a-debug.apk` | 22.7 MB | `A69A5419FB7F4BCCC740AC5CA07A57D1C3B25F3ED56BFDAA238D3BE7FDD8E75A` |
| `app-universal-debug.apk` | 28.3 MB | `8F5400352AA32CC5308C5FBB2697405642416DCF489F204192F90D32CA8195B4` |

Build command: `gradlew.bat assembleDebug --console=plain --no-daemon` with JDK17 + Go + MSYS PATH.
Helpers: `scripts/build_apk.bat` (full build), `scripts/verify_build.bat` (Kotlin-only), `scripts/apk_hashes.ps1`.
`:app:compileDebugKotlin` — clean.

### 5.2. Connection method selector — IMPLEMENTED
- `ConnectionMethod` enum: `AWG`, `WARP`, `DPI_DIRECT`, `AUTO` with `available` flag.
- Main screen tile "Способ подключения" — chips; `AWG`/`WARP` selectable,
  `DPI`/`AUTO` rendered disabled and labelled «в разработке».
- Selection persisted in `Prefs.connectionMethod` (`SharedPreferences`, key `connection_method`).
- Drawer regrouped into labelled sections: **ОБЩИЕ / ПОДКЛЮЧЕНИЕ / SPLIT-TUNNEL / СЕТЬ / ПРОЧЕЕ**;
  the drawer is now scrollable.
- Engine-specific settings shown only for the selected method:
  AWG → «Сервер AmneziaWG»; AWG/WARP → MTU slider; DPI/AUTO → «движок не подключён» tile.

### 5.3. On-device verification — BLOCKED
`adb devices` reports **no attached devices** (previous `97db202c` gone; no emulator running).
Consequently the following cannot be executed yet:
- APK install/launch smoke test
- AWG/WARP connect on device
- YouTube/Telegram DPI validation (needs real hardware + Wi-Fi/LTE)

## 6. Known environment limitations

- No Android device/emulator attached → on-device verification blocked.
- On-device DPI validation (YouTube/Telegram on Wi-Fi and LTE) requires a physical device.
- Build host: Windows 11, JDK 17, Gradle 9.3.1, AGP 9.1.0, Kotlin 2.2.10, NDK 27.2.12479018.
- The `uname -m` CMake failure was a PATH issue, not a code defect — fixed by adding
  `C:\Users\Abonen0007\dev\git\usr\bin` and `...\mingw64\bin` to PATH before Gradle.
