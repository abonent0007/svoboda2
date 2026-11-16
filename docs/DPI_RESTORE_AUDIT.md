# DPI-0 — Forensic Audit of First Successful Build

## Objective
Identify the exact commit, configuration, and code state where YouTube and Telegram first began working via the VPN client. This is the "golden reference" for all subsequent DPI restoration work.

## Constraints
- No on-device testing available (device `97db202c` unavailable)
- GPT screenshot-based review blocked by chatgpt.com 403
- Text-only CLI reviews via `python C:\lib\chatgpt-ai.py chat --session svoboda2` work
- Build environment: JDK 17, Go, Gradle, Android NDK 27.2.12479018
- targetSdk 35, compileSdk 35, minSdk 26
- ABIs: arm64-v8a + armeabi-v7a

## 1. Git History Analysis

### Goal: Find the commit where DPI/YouTube/Telegram first worked

### What to examine:
- All branches and tags
- Commit messages mentioning: `ciadpi`, `byedpi`, `hev-socks5-tunnel`, `nfqws2`, `zapret2`, `LuaJIT`, `strategy`, `DPI`, `YouTube`, `Telegram`
- Changes to: `tunnel/`, `warp/`, `vpn/`, `AndroidManifest.xml`, `build.gradle*`, native `jni/` or `cpp/` directories
- Diff of critical files between earliest and later commits

### How to proceed (without git CLI access in this session):
1. Use `git log --all --oneline --graph` to view commit history
2. Use `git log --all --oneline --grep="DPI\|ciadpi\|byedpi\|nfqws2\|zapret\|strategy\|youtube\|telegram"` to search commits
3. Use `git diff <commit1> <commit2>` to compare critical files
4. Use `git tag --list` to find release tags
5. Use `git branch -a` to list all branches

### Expected findings:
- A commit or tag marking the "first working version"
- Files present/absent at that point
- DPI engine configuration
- TUN adapter setup
- Strategy selections

### Current knowledge from codebase review:
- P3.10 (Android VPN Lifecycle) was the most recent completed stage
- GoBackend Lifecycle patch was applied (onRevoke, AlwaysOnCallback)
- VpnLifecycleCallback was added in AppGraph.kt
- Foreground Service permissions added to manifests
- SplitTunnel, SecureConfigStorage, ProfileHistory, Fingerprint all pre-existing
- Tunnel CMake has pre-existing NDK build issues (uname -m errors)

### Next step:
Run `git log --all --oneline --grep="DPI\|ciadpi\|byedpi\|nfqws2\|zapret\|strategy\|youtube\|telegram"` to locate relevant commits.

If git is unavailable in this session, proceed to Section 2 using file-level analysis.

## 2. File-Level Analysis (Alternative if git unavailable)

### Examine these directories for DPI-related code:

#### `third_party/amneziawg-android/`
- Contains the GoBackend and tunnel module
- Check for any DPI/Lua/nfqws2 references
- Look at CMakeLists.txt for NFQUEUE or raw packet references

#### `app/src/main/jni/`
- Check for native libraries
- Look for .so files or .c/.cpp source

#### `app/src/main/jniLibs/`
- Check for compiled .so binaries by ABI

#### `app/build/intermediates/`
- Check for mapped native libraries

#### `app/src/main/java/svoboda/.../vpn/`
- Review all VpnController, VpnService related code
- Look for TUN adapter, Engine Adapter references

#### `app/src/main/res/`
- Check for any XML configs related to networking

#### `gradle/`
- Check libs.versions.toml for any DPI-related dependencies
- settings.gradle.kts for included modules

## 3. Known Working Components (from prior sessions)

### Confirmed working:
- AWG/WireGuard via GoBackend - connects and passes traffic (exit IP 104.28.198.246, RU, ping ~23–39 ms)
- WARP via Cloudflare API - connects and passes traffic
- SeedCatalog: 74 WARP-verified configs with MTU 1240
- SecureConfigStorage - AES-256-GCM via Android Keystore
- ProfileHistoryStore - encrypted configs with ≤10 revisions
- ProfileFingerprintManager - HMAC-SHA256 Keystore fingerprints
- ReconnectCoordinator - backoff 2/5/10/20/40/60s with jitter
- SplitTunnel: 3 modes (ALL/ONLY_SELECTED/ALL_EXCEPT_SELECTED)
- Presets: RU apps, messengers, browsers
- ConfigValidator - pre-flight AWG/WG config validation
- ProfileTestCoordinator - pre-flight → temporary connect → checks → Commit/Rollback
- Android VpnService Lifecycle (P3.10 Stage B)
- Always-on callback registration
- onRevoke callback
- Foreground Service with systemExempted type
- Process recovery logic

### Not yet verified on device:
- Actual YouTube/Telegram DPI bypass
- Original DPI engine that enabled YouTube/Telegram in "first successful build"
- Any NFQUEUE/raw-packet mechanism
- LuaJIT or ciadpi/byedpi integration

## 4. Audit Hypotheses

### Hypothesis A — ByeDPI + hev-socks5-tunnel
The first working version used:
```
Android VpnService → TUN → hev-socks5-tunnel → SOCKS5 → ciadpi/byedpi → Direct Internet
```
Evidence would include:
- References to hev-socks5-tunnel in git history
- SOCKS5 proxy configuration
- ciadpi/byedpi binary or source
- Strategy #11 Lua scripts

### Hypothesis B — Original Zapret2 C/Lua
The first working version used:
```
Android VpnService → TUN → nfqws2 C Core → LuaJIT → zapret-lib.lua strategies → Direct Internet
```
Evidence would include:
- References to nfqws2
- LuaJIT compilation
- zapret-lib.lua, zapret-antidpi.lua, zapret-auto.lua
- NFQUEUE mechanism
- raw packet injection

### Hypothesis C — No DPI bypass (plain VPN)
The "first working" state was just a functional VPN (AWG/WARP) without DPI-specific bypass. YouTube/Telegram worked due to:
- GoBackend's native WireGuard implementation
- Correct config normalization
- IPv4-only tunneling
- Proper handshake + HTTPS proof

### Hypothesis D — Hybrid approach
Some combination of the above, possibly with configuration changes or server selections enabling the traffic.

## 5. Immediate Action Items

### Primary: Locate the Lost DPI Mechanism
Since the mechanism isn't in the current codebase, we need to find where it went:

1. **Check git branches/tags** for any commit mentioning DPI, ciadpi, byedpi, nfqws2, zapret, strategy, youtube, telegram
   - Command: `git log --all --oneline --grep="DPI\|ciadpi\|byedpi\|nfqws2\|zapret\|strategy\|youtube\|telegram"`
   - Command: `git branch -a`
   - Command: `git tag --list`

2. **If git is fully accessible**, search for the first working commit and document:
   - The exact commit hash
   - Files present/absent at that point
   - The DPI engine configuration
   - How TUN was adapted
   - What enabled YouTube/Telegram

3. **If git access is limited** (as in this session):
   - Review prior development session notes for DPI-related configurations
   - Ask the user: "What specific configuration or feature enabled YouTube/Telegram in the first working version?"
   - Check for backup APKs, exported repos, or version archives
   - Look for any `.zip`, `.tar`, `.backup` files in the project directory

### Secondary: Document the Gap in DPI-0 Audit
Update `docs/DPI_RESTORE_AUDIT.md` with:
- ✅ Confirmed working components (AWG, WARP, VPN lifecycle)
- ❓ Missing DPI mechanism and its last known state
- 📋 Path forward based on what's discovered

### DPI-0 Success Criteria (revised):
The audit is complete when we have:
- [ ] Commit hash or tag of first successful build — OR — documented gap analysis
- [ ] List of files present/absent at that point
- [ ] DPI engine identification (ByeDPI/Zapret2/None — with explanation)
- [ ] TUN adapter configuration at working state
- [ ] Strategy type (if applicable) — with explanation of why it was lost
- [ ] Comparison with current code state
- [ ] Documented reason for loss of functionality
- [ ] Recommendation for restoration path

## 6. DPI-0 Delivery

`docs/DPI_RESTORE_AUDIT.md` — containing all findings above, serving as the foundation for DPI-1 restoration work.

---
*DPI-0 audit initiated. proceeding with file-level analysis while git CLI access is resolved.*