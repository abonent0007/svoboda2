@echo off
set GCC=C:\Users\Abonen0007\dev\mingw64\bin\gcc.exe
cd /d C:\Users\Abonen0007\Documents\svoboda2\third_party\byedpi
if not exist dist\host mkdir dist\host
call "%GCC%" -O2 -D_DEFAULT_SOURCE -o dist\host\byedpi.exe main.c conev.c desync.c extend.c mpool.c packets.c proxy.c win_service.c -lws2_32 -lmswsock -pthread > dist\build_host.log 2>&1
echo BUILD ERRORLEVEL=%ERRORLEVEL%
findstr /C:"error:" dist\build_host.log

echo.
echo === --version ===
dist\host\byedpi.exe --version

echo.
echo === unknown option (must fail) ===
dist\host\byedpi.exe -Z > dist\unknown.log 2>&1
echo unknown-option exit=%ERRORLEVEL%
type dist\unknown.log

echo.
echo === BDP-00 parse test (3s, port 0) ===
start /B cmd /c "dist\host\byedpi.exe -Ku -a3 -An -Kt,h -d1 -s0+s -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -An -p 0 > dist\bdp00.log 2>&1"
timeout /t 3 >nul
taskkill /IM byedpi.exe /F >nul 2>&1
echo BDP-00 output:
type dist\bdp00.log