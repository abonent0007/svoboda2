@echo off
set NDK=C:\Users\Abonen0007\Android\Sdk\ndk\27.2.12479018
set BIN=%NDK%\toolchains\llvm\prebuilt\windows-x86_64\bin
cd /d C:\Users\Abonen0007\Documents\svoboda2\third_party\hev-socks5-tunnel
call "%NDK%\ndk-build.cmd" NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=Android.mk NDK_APPLICATION_MK=Application.mk APP_ABI=armeabi-v7a APP_PLATFORM=android-26 -j4 > build_hev_armv7.log 2>&1
echo hev ERRORLEVEL=%ERRORLEVEL%
if exist libs\armeabi-v7a\libhev-socks5-tunnel.so dir /b libs\armeabi-v7a\libhev-socks5-tunnel.so

cd /d C:\Users\Abonen0007\Documents\svoboda2\third_party\byedpi
if not exist dist\armeabi-v7a mkdir dist\armeabi-v7a
call "%BIN%\armv7a-linux-androideabi26-clang.cmd" -O2 -D_GNU_SOURCE -pthread -fPIE -pie -Wl,-z,max-page-size=16384 -mfpu=neon ^
  -o dist\armeabi-v7a\libbyedpi.so main.c conev.c desync.c extend.c mpool.c packets.c proxy.c -lm > dist\build_armv7.log 2>&1
echo byedpi ERRORLEVEL=%ERRORLEVEL%
if exist dist\armeabi-v7a\libbyedpi.so dir /b dist\armeabi-v7a\libbyedpi.so
findstr /C:"error:" build_hev_armv7.log dist\build_armv7.log
