@echo off
setlocal EnableExtensions DisableDelayedExpansion
chcp 65001 >nul
set "COURSE_EXIT_CODE=0"
set "COURSE_PAUSE=1"
if not "%~2"=="" goto usage_error
if /i "%~1"=="--help" goto help
if /i "%~1"=="--no-pause" set "COURSE_PAUSE=0"
if not "%~1"=="" if /i not "%~1"=="--no-pause" goto usage_error

set "COURSE_STOP_SCRIPT=%~dp0scripts\Stop-Integration.ps1"
if not exist "%COURSE_STOP_SCRIPT%" (
    echo [错误] 找不到停止脚本："%COURSE_STOP_SCRIPT%"
    set "COURSE_EXIT_CODE=1"
    goto finish
)

set "COURSE_PWSH="
for /f "delims=" %%I in ('%SystemRoot%\System32\where.exe pwsh.exe 2^>nul') do if not defined COURSE_PWSH set "COURSE_PWSH=%%I"
if not defined COURSE_PWSH if exist "%ProgramFiles%\PowerShell\7\pwsh.exe" set "COURSE_PWSH=%ProgramFiles%\PowerShell\7\pwsh.exe"
rem 当前机器的 PowerShell 7 由 Codex 提供，双击时可能不在 PATH 中。
if not defined COURSE_PWSH if exist "%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe" set "COURSE_PWSH=%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe"
if not defined COURSE_PWSH (
    echo [错误] 未找到 PowerShell 7，请安装后将 pwsh.exe 加入 PATH。
    set "COURSE_EXIT_CODE=1"
    goto finish
)

pushd "%~dp0" >nul
if errorlevel 1 (
    echo [错误] 无法进入项目根目录："%~dp0"
    set "COURSE_EXIT_CODE=1"
    goto finish
)
rem 复用已有进程归属校验，只停止当前仓库记录的应用进程。
echo 正在停止统一门户、关系分析和信息采集系统……
"%COURSE_PWSH%" -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "%COURSE_STOP_SCRIPT%" -System all
set "COURSE_EXIT_CODE=%errorlevel%"
popd
if not "%COURSE_EXIT_CODE%"=="0" echo [错误] 停止失败，请查看上方错误；未能停止的进程记录已保留。

:finish
if "%COURSE_PAUSE%"=="1" pause
exit /b %COURSE_EXIT_CODE%

:usage_error
echo [错误] 不支持此参数。
set "COURSE_EXIT_CODE=2"

:help
echo 用法：stop.bat [--no-pause ^| --help]
echo 默认停止全部应用，完成后按任意键关闭窗口。
echo 数据库、容器、卷和运行数据保留。
echo --no-pause  完成后直接退出，保留实际退出码。
echo --help      显示帮助，不停止应用。
exit /b %COURSE_EXIT_CODE%
