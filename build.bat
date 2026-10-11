@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

set "JDKBIN="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "JDKBIN=%JAVA_HOME%\bin"
if not defined JDKBIN (
  for /d %%D in ("%LOCALAPPDATA%\jdk-21\jdk-*") do (
    if exist "%%D\bin\javac.exe" set "JDKBIN=%%D\bin"
  )
)

if defined JDKBIN (
  set "JAVAC=!JDKBIN!\javac.exe"
  set "JAR=!JDKBIN!\jar.exe"
) else (
  where javac >nul 2>nul
  if errorlevel 1 (
    echo [ERROR] Java JDK not found.
    echo Install it with:  winget install EclipseAdoptium.Temurin.21.JDK
    echo Or unpack a portable JDK into %%LOCALAPPDATA%%\jdk-21
    exit /b 1
  )
  set "JAVAC=javac"
  set "JAR=jar"
)

set "FLAF=lib\flatlaf-3.5.4.jar"
if not exist "%FLAF%" (
  echo [ERROR] %FLAF% not found. Download it from Maven Central:
  echo   https://repo1.maven.org/maven2/com/formdev/flatlaf/3.5.4/flatlaf-3.5.4.jar
  exit /b 1
)

echo [1/4] Compiling sources...
if exist build rmdir /s /q build
mkdir build\classes
"%JAVAC%" -encoding UTF-8 -cp "%FLAF%" -sourcepath src -d build\classes src\installer\Main.java
if errorlevel 1 (
  echo [ERROR] Compilation failed.
  exit /b 1
)

echo [2/4] Bundling FlatLaf into the jar...
pushd build\classes
"%JAR%" xf "..\..\%FLAF%"
if exist META-INF\MANIFEST.MF del /q META-INF\MANIFEST.MF
popd

echo [3/4] Copying resources...
if exist boykisser-spin.gif copy /y boykisser-spin.gif build\classes\boykisser-spin.gif >nul

echo [4/4] Packaging jar...
if not exist dist mkdir dist
> build\MANIFEST.MF echo Main-Class: installer.Main
"%JAR%" cfm dist\installer.jar build\MANIFEST.MF -C build\classes .
if errorlevel 1 (
  echo [ERROR] Packaging failed.
  exit /b 1
)
if exist boykisser-spin.gif copy /y boykisser-spin.gif dist\boykisser-spin.gif >nul
copy /y apps.json dist\apps.json >nul

echo Done:
echo   dist\installer.jar
echo   dist\apps.json
echo   dist\boykisser-spin.gif
echo Run it with run.bat
endlocal
