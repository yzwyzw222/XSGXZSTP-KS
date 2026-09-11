@echo off
setlocal EnableExtensions DisableDelayedExpansion
chcp 65001 >nul
set "COURSE_EXIT_CODE=0"
set "COURSE_PAUSE=1"
if not "%~2"=="" goto usage_error
if /i "%~1"=="--help" goto help
if /i "%~1"=="--no-pause" set "COURSE_PAUSE=0"
if not "%~1"=="" if /i not "%~1"=="--no-pause" goto usage_error

set "COURSE_START_SCRIPT=%~dp0scripts\Start-Integration.ps1"
set "COURSE_START_ARGS=-System all -Mode Demo"
rem 本机 MySQL 入口负责安全输入凭据，并调用统一启动流程。
if exist "%~dp0.local\Start-LocalProject.ps1" (
    set "COURSE_START_SCRIPT=%~dp0.local\Start-LocalProject.ps1"
    set "COURSE_START_ARGS=-System all"
)
if not exist "%COURSE_START_SCRIPT%" (
    echo [错误] 找不到启动脚本："%COURSE_START_SCRIPT%"
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
echo 正在启动统一门户、关系分析和信息采集系统……
"%COURSE_PWSH%" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%COURSE_START_SCRIPT%" %COURSE_START_ARGS%
set "COURSE_EXIT_CODE=%errorlevel%"
popd
if not "%COURSE_EXIT_CODE%"=="0" echo [错误] 启动失败，请查看上方错误及项目 .local\integration 中的日志。

:finish
if "%COURSE_PAUSE%"=="1" pause
exit /b %COURSE_EXIT_CODE%

:usage_error
echo [错误] 不支持此参数。
set "COURSE_EXIT_CODE=2"

:help
echo 用法：start.bat [--no-pause ^| --help]
echo 默认启动全部应用，完成后按任意键关闭窗口。
echo 本机 MySQL 环境按提示输入数据库密码，密码不会回显。
echo 首次使用需按 README.md 完成环境初始化和构建。
echo --no-pause  完成后直接退出，保留实际退出码。
echo --help      显示帮助，不启动应用。
exit /b %COURSE_EXIT_CODE%
