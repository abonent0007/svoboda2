@echo off
set NDK=C:\Users\Abonen0007\Android\Sdk\ndk\27.2.12479018
cd /d C:\Users\Abonen0007\Documents\svoboda2\third_party\hev-socks5-tunnel
rmdir /s /q libs 2>nul

echo === arm64-v8a ===
call "%NDK%\ndk-build.cmd" NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=Android.mk NDK_APPLICATION_MK=Application.mk APP_ABI=arm64-v8a APP_PLATFORM=android-26 -j4 > build_arm64.log 2>&1
echo arm64 ERRORLEVEL=%ERRORLEVEL%

echo === armeabi-v7a ===
call "%NDK%\ndk-build.cmd" NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=Android.mk NDK_APPLICATION_MK=Application.mk APP_ABI=armeabi-v7a APP_PLATFORM=android-26 -j4 > build_armv7.log 2>&1
echo armv7 ERRORLEVEL=%ERRORLEVEL%

echo === x86_64 ===
call "%NDK%\ndk-build.cmd" NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=Android.mk NDK_APPLICATION_MK=Application.mk APP_ABI=x86_64 APP_PLATFORM=android-26 -j4 > build_x86_64.log 2>&1
echo x86_64 ERRORLEVEL=%ERRORLEVEL%

findstr /C:"error:" build_arm64.log build_armv7.log build_x86_64.log
dir /b libs\*