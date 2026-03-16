@echo off
setlocal enabledelayedexpansion

:: ─────────────────────────────────────────────
::  build.bat — Build the fat JAR on Windows
:: ─────────────────────────────────────────────
::
::  Usage:
::    build.bat              Build (clean + package)
::    build.bat --skip-tests Skip tests
::
::  Requires: Java 21+
::  Maven: auto-downloaded via mvnw.cmd if needed
:: ─────────────────────────────────────────────

:: ── Check Java ──────────────────────────────
where java >nul 2>&1
if %errorlevel% neq 0 (
    echo [X] Java not found. Install JDK 21+ and try again.
    echo     https://adoptium.net/temurin/releases/?version=21
    exit /b 1
)

for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set "JAVA_VER_RAW=%%~v"
)
for /f "tokens=1 delims=." %%m in ("!JAVA_VER_RAW!") do set "JAVA_MAJOR=%%m"

if !JAVA_MAJOR! lss 21 (
    echo [X] Java 21+ required. Found: Java !JAVA_MAJOR!.
    echo     https://adoptium.net/temurin/releases/?version=21
    exit /b 1
)
echo [OK] Java !JAVA_MAJOR! detected.

:: ── Use Maven wrapper (auto-downloads 3.9.9) ──
set "MVN=%~dp0mvnw.cmd"
echo [OK] Using: !MVN!

:: ── Parse args ──────────────────────────────
set "EXTRA_ARGS="
if "%~1"=="--skip-tests" set "EXTRA_ARGS=-DskipTests"

:: ── Build ───────────────────────────────────
echo.
echo ==================================================
echo   Building temporal-workers-java
echo ==================================================
echo.

call "!MVN!" clean package -B %EXTRA_ARGS%
if %errorlevel% neq 0 (
    echo.
    echo [X] Build failed.
    exit /b 1
)

set "JAR_PATH=target\temporal-workers-java-0.1.0.jar"
if exist "%JAR_PATH%" (
    echo.
    echo ==================================================
    echo   [OK] Build successful!
    echo ==================================================
    echo.
    echo   Fat JAR: %JAR_PATH%
    echo.
    echo   Run workers:
    echo     run.bat workers
    echo.
    echo   Run CLI:
    echo     run.bat cli start Alice --wait
    echo     run.bat cli list
    echo     run.bat cli status --id ^<WORKFLOW_ID^>
    echo.
)

endlocal
