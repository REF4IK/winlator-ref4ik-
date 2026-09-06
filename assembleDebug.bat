@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem Root directory of this project (where this .bat is located)
set "PROJECT_DIR=%~dp0"
if "%PROJECT_DIR:~-1%"=="\" set "PROJECT_DIR=%PROJECT_DIR:~0,-1%"

set "LOCAL_JAVA_HOME=D:\procect vscode\sdkkkk\jdk-17.0.2"
set "LOCAL_ANDROID_SDK_ROOT=D:\procect vscode\sdkkkk\android-sdk"

if exist "%LOCAL_JAVA_HOME%\bin\java.exe" (
  set "JAVA_HOME=%LOCAL_JAVA_HOME%"
) else if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" (
  rem keep existing JAVA_HOME
) else if exist "C:\Program Files\Java\jdk-17\bin\java.exe" (
  set "JAVA_HOME=C:\Program Files\Java\jdk-17"
) else if exist "C:\Program Files\Eclipse Adoptium\jdk-17.0.2.8-hotspot\bin\java.exe" (
  set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.2.8-hotspot"
)

if exist "%LOCAL_ANDROID_SDK_ROOT%\platforms" (
  set "ANDROID_SDK_ROOT=%LOCAL_ANDROID_SDK_ROOT%"
) else if defined ANDROID_SDK_ROOT if exist "%ANDROID_SDK_ROOT%\platforms" (
  rem keep existing ANDROID_SDK_ROOT
) else if defined ANDROID_HOME if exist "%ANDROID_HOME%\platforms" (
  set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
) else if exist "%LOCALAPPDATA%\Android\Sdk\platforms" (
  set "ANDROID_SDK_ROOT=%LOCALAPPDATA%\Android\Sdk"
)

set "ANDROID_HOME=%ANDROID_SDK_ROOT%"

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo ERROR: JAVA_HOME not found: %JAVA_HOME%
  echo Put JDK 17 into: D:\procect vscode\sdkkkk\jdk-17.0.2
  echo or set JAVA_HOME to an existing JDK 17 installation.
  exit /b 1
)

if not exist "%ANDROID_SDK_ROOT%\platforms" (
  echo ERROR: ANDROID_SDK_ROOT not found: %ANDROID_SDK_ROOT%
  echo Put Android SDK into: D:\procect vscode\sdkkkk\android-sdk
  echo or set ANDROID_SDK_ROOT/ANDROID_HOME to an existing SDK.
  exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"

rem Give Gradle/Kotlin heap (не больше 4+2GB: на 15GB-машине больше = OOM/crash daemon)
if not defined GRADLE_OPTS set "GRADLE_OPTS=-Xmx4096m -Dfile.encoding=UTF-8 -Dkotlin.daemon.jvm.options=-Xmx2048m"
if not defined JAVA_TOOL_OPTIONS set "JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8"

rem Keep Gradle caches outside project (moved to sdkkkk to avoid indexing 1.9GB cache)
set "GRADLE_USER_HOME=D:\procect vscode\sdkkkk\.gradle-local"
if not exist "%GRADLE_USER_HOME%" mkdir "%GRADLE_USER_HOME%" >nul 2>&1

rem Keep Android user state outside project as well
set "ANDROID_USER_HOME=D:\procect vscode\sdkkkk\.android-home"
if not exist "%ANDROID_USER_HOME%" mkdir "%ANDROID_USER_HOME%" >nul 2>&1

rem Some Android tooling still resolves through HOME/USERPROFILE or user.home
set "HOME=D:\procect vscode\sdkkkk"
set "USERPROFILE=D:\procect vscode\sdkkkk"

rem Detect NDK folder under android-sdk\ndk\*
set "NDK_DIR="
if exist "%ANDROID_SDK_ROOT%\ndk" (
  for /f "delims=" %%D in ('dir /b /ad "%ANDROID_SDK_ROOT%\ndk" 2^>nul') do (
    if exist "%ANDROID_SDK_ROOT%\ndk\%%D\ndk-build.cmd" (
      set "NDK_DIR=%ANDROID_SDK_ROOT%\ndk\%%D"
      goto :ndk_found
    )
  )
)
:ndk_found

if "%NDK_DIR%"=="" (
  echo ERROR: NDK not found under: %ANDROID_SDK_ROOT%\ndk
  echo Install NDK via SDK Manager or copy it into android-sdk\ndk\[version]
  exit /b 1
)

if not exist "%ANDROID_SDK_ROOT%\cmake\3.22.1\bin\cmake.exe" (
  echo ERROR: CMake 3.22.1 not found under: %ANDROID_SDK_ROOT%\cmake\3.22.1
  echo Install component "cmake;3.22.1" into the Android SDK.
  exit /b 1
)

rem Write local.properties in project root (some tools read it)
(
  echo sdk.dir=%ANDROID_SDK_ROOT:\=/%
) > "%PROJECT_DIR%\local.properties"

rem Some setups also have app\local.properties; ensure it doesn't keep old paths
if exist "%PROJECT_DIR%\app" (
  (
    echo sdk.dir=%ANDROID_SDK_ROOT:\=/%
  ) > "%PROJECT_DIR%\app\local.properties"
)

echo ========================================
echo PROJECT_DIR=%PROJECT_DIR%
echo JAVA_HOME=%JAVA_HOME%
echo ANDROID_SDK_ROOT=%ANDROID_SDK_ROOT%
echo NDK_DIR=%NDK_DIR%
echo GRADLE_OPTS=%GRADLE_OPTS%
echo ========================================

call "%PROJECT_DIR%\gradlew.bat" :app:assembleDebug --no-configuration-cache
set "ERR=%ERRORLEVEL%"

if not "%ERR%"=="0" (
  echo.
  echo BUILD FAILED with exit code %ERR%
  exit /b %ERR%
)

echo.
echo BUILD OK
echo APK should be here:
echo D:\procect vscode\sdkkkk\build-win\outputs\apk\debug\app-debug.apk
exit /b 0
