@echo off
cd C:\Users\Abonen0007\Documents\svoboda2
set JAVA_HOME=C:\Users\Abonen0007\dev\jdk17
set PATH=C:\Users\Abonen0007\dev\jdk17\bin;C:\Users\Abonen0007\dev\go\bin;C:\Users\Abonen0007\dev\git\cmd;C:\Users\Abonen0007\dev\git\usr\bin;C:\Users\Abonen0007\dev\mingw64\bin;%PATH%
call .\gradlew.bat assembleDebug --console=plain --no-daemon > build_apk.log 2>&1
echo ERRORLEVEL=%ERRORLEVEL%
findstr /C:"BUILD SUCCESSFUL" /C:"BUILD FAILED" /C:"e: " build_apk.log