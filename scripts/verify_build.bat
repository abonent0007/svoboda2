@echo off
cd C:\Users\Abonen0007\Documents\svoboda2
call .\gradlew.bat :app:compileDebugKotlin --console=plain --no-daemon > verify_build.log 2>&1
echo ERRORLEVEL=%ERRORLEVEL%
findstr /C:"BUILD SUCCESSFUL" /C:"BUILD FAILED" /C:"e: " verify_build.log