@echo off
:: ─────────────────────────────────────────────
::  Maven Wrapper — auto-downloads Maven 3.9.9
:: ─────────────────────────────────────────────
::  Maven 3.9.9 includes Jansi 2.4.1 which is
::  compatible with JDK 21+. Older Maven versions
::  crash with NoSuchMethodError: AnsiConsole.out()
:: ─────────────────────────────────────────────

setlocal enabledelayedexpansion

set "MAVEN_VERSION=3.9.9"
set "MAVEN_HOME=%USERPROFILE%\.m2\wrapper\apache-maven-%MAVEN_VERSION%"
set "MAVEN_BIN=%MAVEN_HOME%\bin\mvn.cmd"
set "MAVEN_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip"
set "MAVEN_ZIP=%TEMP%\apache-maven-%MAVEN_VERSION%-bin.zip"

:: ── Try system Maven if it's 3.9.6+ ────────
where mvn >nul 2>&1
if %errorlevel% equ 0 (
    for /f "tokens=3" %%v in ('mvn --version 2^>nul ^| findstr /i "Apache Maven"') do set "SYS_VER=%%v"
    if defined SYS_VER (
        for /f "tokens=1-3 delims=." %%a in ("!SYS_VER!") do (
            set "SYS_MAJOR=%%a"
            set "SYS_MINOR=%%b"
            set "SYS_PATCH=%%c"
        )
        :: Maven 4+ or 3.10+ or 3.9.6+ are safe
        if !SYS_MAJOR! geq 4 (
            mvn %*
            exit /b %errorlevel%
        )
        if !SYS_MAJOR! equ 3 if !SYS_MINOR! geq 10 (
            mvn %*
            exit /b %errorlevel%
        )
        if !SYS_MAJOR! equ 3 if !SYS_MINOR! equ 9 if !SYS_PATCH! geq 6 (
            mvn %*
            exit /b %errorlevel%
        )
        echo [!] System Maven !SYS_VER! has a Jansi bug with JDK 21+.
        echo     Using Maven %MAVEN_VERSION% instead.
    )
)

:: ── Download Maven 3.9.9 if not cached ─────
if exist "%MAVEN_BIN%" goto run_maven

echo Downloading Maven %MAVEN_VERSION% ...

:: Use PowerShell to download and extract
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; " ^
    "$ProgressPreference = 'SilentlyContinue'; " ^
    "Invoke-WebRequest -Uri '%MAVEN_URL%' -OutFile '%MAVEN_ZIP%'; " ^
    "Expand-Archive -Path '%MAVEN_ZIP%' -DestinationPath '%USERPROFILE%\.m2\wrapper' -Force; " ^
    "Remove-Item '%MAVEN_ZIP%' -ErrorAction SilentlyContinue"

if not exist "%MAVEN_BIN%" (
    echo [X] Failed to download Maven %MAVEN_VERSION%.
    echo     Install Maven 3.9.9+ manually from https://maven.apache.org/download.cgi
    exit /b 1
)

echo [OK] Maven %MAVEN_VERSION% installed to %MAVEN_HOME%

:run_maven
call "%MAVEN_BIN%" %*
exit /b %errorlevel%

endlocal
