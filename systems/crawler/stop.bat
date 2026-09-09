@echo off
setlocal EnableExtensions DisableDelayedExpansion
chcp 65001 >nul
set "AACV_MODE="
if not "%~2"=="" goto usage_error
if /i "%~1"=="--check" set "AACV_MODE=-CheckOnly"
if /i "%~1"=="--help" set "AACV_MODE=-ShowHelp"
if /i "%~1"=="--no-pause" goto run
if not "%~1"=="" if not defined AACV_MODE goto usage_error

:run
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "%~dp0tools\development\Stop-All.ps1" %AACV_MODE%
set "AACV_EXIT_CODE=%errorlevel%"
if "%~1"=="" pause
exit /b %AACV_EXIT_CODE%

:usage_error
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "%~dp0tools\development\Stop-All.ps1" -ShowHelp
exit /b 2
