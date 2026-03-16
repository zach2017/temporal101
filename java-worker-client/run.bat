@echo off
setlocal enabledelayedexpansion

:: ─────────────────────────────────────────────
::  run.bat — Run workers or CLI on Windows
:: ─────────────────────────────────────────────
::
::  Usage:
::    run.bat workers                           Start all workers
::    run.bat cli start Alice --wait            Start workflow (wait)
::    run.bat cli start Alice                   Start workflow (async)
::    run.bat cli status --id ID                Check status
::    run.bat cli result --id ID                Get result
::    run.bat cli describe --id ID --history    Full details
::    run.bat cli cancel --id ID                Cancel
::    run.bat cli terminate --id ID --force     Terminate
::    run.bat cli list                          List workflows
::    run.bat cli list --status RUNNING         Filter by status
::    run.bat cli --help                        CLI help
::
::  Environment variables:
::    TEMPORAL_HOST      (default: localhost)
::    TEMPORAL_PORT      (default: 7233)
::    TEMPORAL_NAMESPACE (default: default)
::    WORKER_LOG_LEVEL   (default: INFO)
::    JAVA_OPTS          (default: -Djava.net.preferIPv4Stack=true)
:: ─────────────────────────────────────────────

set "JAR_PATH=%~dp0target\temporal-workers-java-0.1.0.jar"

if not defined JAVA_OPTS set "JAVA_OPTS=-Djava.net.preferIPv4Stack=true"

:: ── Verify JAR exists ───────────────────────
if not exist "%JAR_PATH%" (
    echo [X] Fat JAR not found at: %JAR_PATH%
    echo     Run build.bat first.
    exit /b 1
)

:: ── Parse command ───────────────────────────
set "COMMAND=%~1"

if "%COMMAND%"=="" (
    echo Usage:
    echo   run.bat workers                   Start all Temporal workers
    echo   run.bat cli ^<command^> [options]    Run the CLI client
    echo.
    echo Examples:
    echo   run.bat workers
    echo   run.bat cli start Alice --wait
    echo   run.bat cli list --status RUNNING
    echo   run.bat cli --help
    exit /b 0
)

:: ── Collect remaining args ──────────────────
shift
set "ARGS="
:collect_args
if "%~1"=="" goto done_args
set "ARGS=!ARGS! %~1"
shift
goto collect_args
:done_args

:: ── Route command ───────────────────────────
if /i "%COMMAND%"=="workers" goto run_workers
if /i "%COMMAND%"=="worker"  goto run_workers
if /i "%COMMAND%"=="cli"     goto run_cli

echo [X] Unknown command: %COMMAND%
echo.
echo Valid commands: workers, cli
exit /b 1

:run_workers
echo ==================================================
echo   Starting Temporal Workers
echo ==================================================
if not defined TEMPORAL_HOST set "TEMPORAL_HOST=localhost"
if not defined TEMPORAL_PORT set "TEMPORAL_PORT=7233"
if not defined TEMPORAL_NAMESPACE set "TEMPORAL_NAMESPACE=default"
echo   Server  : %TEMPORAL_HOST%:%TEMPORAL_PORT%
echo   Namespace: %TEMPORAL_NAMESPACE%
echo ==================================================
echo.
java %JAVA_OPTS% -jar "%JAR_PATH%" %ARGS%
exit /b %errorlevel%

:run_cli
java %JAVA_OPTS% -cp "%JAR_PATH%" com.temporal.workers.cli.TemporalCli %ARGS%
exit /b %errorlevel%

endlocal
