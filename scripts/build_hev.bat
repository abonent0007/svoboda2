@echo off
set NDK=C:\Users\Abonen0007\Android\Sdk\ndk\27.2.12479018
cd /d C:\Users\Abonen0007\Documents\svoboda2\third_party\hev-socks5-tunnel
call "%NDK%\ndk-build.cmd" NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=Android.mk NDK_APPLICATION_MK=Application.mk APP_ABI="arm64-v8a x86_64" APP_PLATFORM=android-26 -j4 > build_hev.log 2>&1
echo ERRORLEVEL=%ERRORLEVEL%
findstr /C:"error:" /C:"BUILD FAILED" build_hev.log
dir /b libs\arm64-v8a 2>nul
dir /b libs\x86_64 2>nul