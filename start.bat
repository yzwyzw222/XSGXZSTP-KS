@echo off
setlocal EnableExtensions DisableDelayedExpansion
chcp 65001 >nul
set "AACV_MODE="
if /i "%~1"=="--check" set "AACV_MODE=-CheckOnly"
if /i "%~1"=="--help" set "AACV_MODE=-ShowHelp"
if not "%~1"=="" if not defined AACV_MODE goto usage_error
if not "%~2"=="" goto usage_error

"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "%~dp0tools\development\Start-All.ps1" %AACV_MODE%
set "AACV_EXIT_CODE=%errorlevel%"
if not defined AACV_MODE pause
exit /b %AACV_EXIT_CODE%

:usage_error
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "%~dp0tools\development\Start-All.ps1" -ShowHelp
exit /b 2
