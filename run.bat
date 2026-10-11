@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

set "JAVABIN="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVABIN=%JAVA_HOME%\bin"
if not defined JAVABIN (
  for /d %%D in ("%LOCALAPPDATA%\jdk-21\jdk-*") do (
    if exist "%%D\bin\java.exe" set "JAVABIN=%%D\bin"
  )
)

if defined JAVABIN (
  set "JAVA=!JAVABIN!\java.exe"
) else (
  where java >nul 2>nul
  if errorlevel 1 (
    echo [ERROR] Java runtime not found. Run build.bat first or install a JDK.
    pause
    exit /b 1
  )
  set "JAVA=java"
)

if not exist dist\installer.jar (
  echo [ERROR] dist\installer.jar not found. Run build.bat first.
  pause
  exit /b 1
)

"%JAVA%" -jar dist\installer.jar
endlocal
