@echo off
setlocal

set "ROOT=%~dp0"
set "APK=D:\procect vscode\sdkkkk\build-win\outputs\apk\debug\app-debug.apk"
set "ADB=D:\procect vscode\sdkkkk\android-sdk\platform-tools\adb.exe"

if not exist "%APK%" (
    echo APK not found: %APK%
    echo Build it first with assembleDebug.bat
    exit /b 1
)

if not exist "%ADB%" (
    where adb >nul 2>&1
    if errorlevel 1 (
        echo adb not found.
        echo Expected local adb at: %ADB%
        exit /b 1
    )
    set "ADB=adb"
)

"%ADB%" get-state >nul 2>&1
if errorlevel 1 (
    echo No Android device connected.
    echo Check USB debugging and run: "%ADB%" devices
    exit /b 1
)

echo Installing %APK% ...
"%ADB%" install -r "%APK%"
if errorlevel 1 (
    echo Install failed.
    exit /b 1
)

echo Done.
