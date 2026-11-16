@echo off
set NDK=C:\Users\Abonen0007\Android\Sdk\ndk\27.2.12479018
set BIN=%NDK%\toolchains\llvm\prebuilt\windows-x86_64\bin
cd /d C:\Users\Abonen0007\Documents\svoboda2\third_party\byedpi
if not exist dist\arm64-v8a mkdir dist\arm64-v8a
if not exist dist\x86_64 mkdir dist\x86_64

echo === arm64-v8a ===
call "%BIN%\aarch64-linux-android26-clang.cmd" -O2 -D_GNU_SOURCE -pthread -fPIE -pie -Wl,-z,max-page-size=16384 ^
  -o dist\arm64-v8a\libbyedpi.so main.c conev.c desync.c extend.c mpool.c packets.c proxy.c -lm > dist\build_arm64.log 2>&1
echo arm64 ERRORLEVEL=%ERRORLEVEL%
if exist dist\arm64-v8a\libbyedpi.so dir /b dist\arm64-v8a\libbyedpi.so

echo === x86_64 ===
call "%BIN%\x86_64-linux-android26-clang.cmd" -O2 -D_GNU_SOURCE -pthread -fPIE -pie -Wl,-z,max-page-size=16384 ^
  -o dist\x86_64\libbyedpi.so main.c conev.c desync.c extend.c mpool.c packets.c proxy.c -lm > dist\build_x86_64.log 2>&1
echo x86_64 ERRORLEVEL=%ERRORLEVEL%
if exist dist\x86_64\libbyedpi.so dir /b dist\x86_64\libbyedpi.so

echo === errors (if any) ===
findstr /C:"error:" dist\build_arm64.log dist\build_x86_64.log
