$ErrorActionPreference = 'Stop'
$g = 'C:\Users\Abonen0007\dev\git\cmd\git.exe'
Set-Location 'C:\Users\Abonen0007\Documents\svoboda2'

# 1) P3.10 Stage B — VPN lifecycle
& $g add 'third_party/amneziawg-android/tunnel/src/main/AndroidManifest.xml' `
         'third_party/amneziawg-android/tunnel/src/main/java/org/amnezia/awg/backend/GoBackend.java' `
         'app/src/main/java/svoboda/runet/abonent0007/vpn/VpnLifecycleCallback.kt' `
         'app/src/main/java/svoboda/runet/abonent0007/AppGraph.kt' `
         'app/src/main/AndroidManifest.xml'
& $g commit -m 'feat(P3.10-B): Android VPN lifecycle — onRevoke hook, Always-on callback, FGS perms'
Write-Host '--- commit 1 done ---'

# 2) UI: connection method, MTU presets, layout, reconnect fixes
& $g add 'app/src/main/java/svoboda/runet/abonent0007/MainActivity.kt' `
         'app/src/main/java/svoboda/runet/abonent0007/ui/home/HomeScreen.kt' `
         'app/src/main/java/svoboda/runet/abonent0007/data/Prefs.kt' `
         'app/src/main/java/svoboda/runet/abonent0007/vpn/VpnController.kt' `
         'app/src/main/java/svoboda/runet/abonent0007/vpn/ReconnectCoordinator.kt' `
         'app/src/main/java/svoboda/runet/abonent0007/vpn/ConnectionManager.kt' `
         'app/src/main/java/svoboda/runet/abonent0007/warp/WarpGenerator.kt'
& $g commit -m 'feat(ui): connection method selector, MTU 1420/1480 + presets, bottom layout, reconnect fixes'
Write-Host '--- commit 2 done ---'

# 3) DPI-1.2A sources + documentation + scripts
& $g add '.gitignore' 'scripts' 'docs' 'third_party/byedpi' 'third_party/hev-socks5-tunnel' 'third_party/DPI_SOURCES.lock.json'
& $g commit -m 'chore(DPI-1.2A): pinned ByeDPI + HEV sources, port map and audit docs'
Write-Host '--- commit 3 done ---'

& $g log --oneline -5
& $g status --short
