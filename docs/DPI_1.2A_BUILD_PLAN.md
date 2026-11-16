# DPI-1.2A — Source Acquisition and Native Build Bootstrap

## Status: SOURCES ACQUIRED — build bootstrap next

Reference: https://github.com/romanvht/ByeByeDPI (tag v.1.8.2, app commit 96a3c1ff555043d915af814aa4f0e9a572c89aba)

## 1. Source acquisition result

Obtained without Git, from immutable commit-SHA archives via GitHub.
Manifest: `third_party/DPI_SOURCES.lock.json` (generated 2026-11-16).

| Component | Repository | Commit SHA | License | Project path | Acquisition | Native deps |
|-----------|-----------|------------|---------|--------------|-------------|-------------|
| ByeDPI / ciadpi | `hufrea/byedpi` | `ba532298de7b28cfe854aea83d061369d13ca290` | MIT | `third_party/byedpi` | ✅ present (37 files) | none |
| HEV SOCKS5 Tunnel | `heiher/hev-socks5-tunnel` | `4d6c334dbfb68a79d1970c2744e62d09f71df12f` | MIT + Apache-2.0 headers in Android.mk | `third_party/hev-socks5-tunnel` | ✅ present (108 files) | core, task-system, lwip, yaml |
| HEV SOCKS5 Core | `heiher/hev-socks5-core` | `4be2e621813ba0315cfacd995bf501bde91d6996` | MIT | `third_party/hev-socks5-tunnel/src/core` | ✅ present (45 files) | none |
| HEV Task System | `heiher/hev-task-system` | `8d83bbbf79557138726c8ee5a5fae99cbb978d61` | MIT | `third_party/hev-socks5-tunnel/third-part/hev-task-system` | ✅ present (166 files) | none |
| lwIP | `heiher/lwip` | `07dbf162c718cc78ddedb9e67c6ebd17065eaf13` | BSD-3-Clause-style | `third_party/hev-socks5-tunnel/third-part/lwip` | ✅ present (255 files) | none |
| yaml | `heiher/yaml` | `efa36117a8646d26d12b58e05bac472d7854a70d` | MIT | `third_party/hev-socks5-tunnel/third-part/yaml` | ✅ present (20 files) | none |

### Verification performed
- HEV parent tree verified via GitHub tree API: exactly **4** gitlinks
  (`src/core`, `third-part/hev-task-system`, `third-part/lwip`, `third-part/yaml`),
  each matching the pinned SHA recorded by the parent commit.
- Archive SHA-256 recorded in the lock manifest for the three freshly downloaded sets.
- Required build files present: `byedpi/main.c`, `byedpi/params.h`,
  `hev-socks5-tunnel/Android.mk`, `Application.mk`, `build.mk`,
  `hev-socks5-tunnel/src/core/include`.
- LICENSE present for all six components.

### Notes on the acquisition script
`scripts/fetch_pinned_dpi_sources.ps1` — PowerShell, no Git required.
Two fixes were applied during the run:
1. `${Repo}` escaping — bare `"$Repo:"` is parsed as a drive-qualified variable in PowerShell and aborted the script.
2. Work root moved to `%SystemDrive%\sdw-<8hex>` — extraction under `%TEMP%` produced paths over the
   legacy 260-char MAX_PATH limit (`hev-task-system/src/arch/microblaze/hev-task-execute-microblaze.s`).
   Re-runs now skip already-populated destinations instead of aborting.

## 2. Licensing position
- GPL-3.0 material from the ByeByeDPI *application* was **not** acquired:
  Kotlin services, `byedpi-jni.c`, `hev-jni.c`, Activity/UI, app Gradle/CMake plumbing, proxytest lists.
- MIT/BSD upstream sources are vendored with their original LICENSE files and file-level headers intact.
- Svoboda will provide its own JNI/CMake integration using the libraries' public APIs.

## 3. Next: native build bootstrap

### 3.1. libbyedpi.so
- Sources: pinned `third_party/byedpi` (C) + new Svoboda-owned JNI wrapper.
- Link against: `android`, `log`, `dl`.
- ABIs: `arm64-v8a`, `x86_64`.
- 16 KB page-size compatible link flags.

### 3.2. libhev-socks5-tunnel.so
- Sources: pinned `third_party/hev-socks5-tunnel` + verified nested deps.
- Prefer upstream `Android.mk` / `build.mk` semantics; adapt, do not rewrite without cause.
- ABI/API notes: upstream `Application.mk` targets `android-29`; project `minSdk` is 26.
  Verify which native APIs are actually used at API 26 before deciding anything.
  **Do not change the project's minSdk automatically.**
- Export check: `hev_socks5_tunnel_main_from_str`, `hev_socks5_tunnel_quit`, JNI entry points.

### 3.3. Verification gates
- `llvm-readelf` architecture check for all four artifacts.
- 16 KB alignment verification.
- Dynamic dependency audit (`readelf -d`).
- Exported symbol audit (JNI + HEV API).
- Load/stop smoke test in an isolated harness (no VPN session).

## 4. Hard constraints for this stage
- Do not modify GoBackend runtime.
- Do not modify VpnService lifecycle.
- Do not create a TUN or run ciadpi/HEV inside the app yet.
- Do not touch AWG/WARP, ConnectionMethod, split-tunnel, DNS, routing, or UI.
- Do not update pinned revisions.

## 5. Acceptance criteria
- [x] All six source sets present at pinned SHA
- [x] Nested HEV dependencies verified against parent commit gitlinks
- [x] Lock manifest written
- [x] Licenses retained and inventoried
- [x] GPL-3.0 app code not copied
- [x] Existing project still builds with unchanged network behaviour
- [ ] libbyedpi.so builds for arm64-v8a and x86_64
- [ ] libhev-socks5-tunnel.so builds for arm64-v8a and x86_64
- [ ] ELF/ABI and 16 KB alignment verified
- [ ] Dynamic deps and exported symbols verified
- [ ] minSdk 26 compatibility assessed
- [ ] Smoke test: load/stop + BDP-00 argv accepted

## 6. Commit
`DPI-1.2A: add pinned ByeDPI and HEV source dependencies`
