[CmdletBinding()]
param(
    [switch]$CheckOnly,
    [switch]$ShowHelp
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$powershell = Join-Path $PSHOME 'powershell.exe'

if ($ShowHelp) {
    Write-Host '用法：start.bat [--check | --help]'
    Write-Host '  不带参数：检查环境，启动 Neo4j，并打开后端和前端日志窗口。'
    Write-Host '  --check：只检查环境和默认应用端口，不启动服务，不暂停等待按键。'
    Write-Host '  --help：显示帮助。'
    Write-Host '首次使用前请按 README.md 配置 .env，并执行 npm --prefix .\frontend ci。'
    exit 0
}

Push-Location $workspace
try {
    foreach ($path in @('tools/development/Test-DevelopmentEnvironment.ps1', 'tools/development/Start-Development.ps1', 'mvnw.cmd')) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            throw "缺少启动所需文件：$path"
        }
    }

    Write-Host '正在检查本地开发环境...'
    # 预检脚本使用 exit 返回结果，单独运行以免提前结束启动编排。
    & $powershell -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'Test-DevelopmentEnvironment.ps1')
    if ($LASTEXITCODE -ne 0) { throw '环境检查未通过，请处理上方错误后重试。' }

    # 在启动任何组件前检查应用端口，不停止或替换已有进程。
    foreach ($port in @(8080, 5173)) {
        $client = [Net.Sockets.TcpClient]::new()
        try {
            $connected = $false
            try { $connected = $client.ConnectAsync('127.0.0.1', $port).Wait(1000) -and $client.Connected }
            catch { $connected = $false }
            if ($connected) { throw "端口 $port 已占用，请在原终端停止服务后重试。" }
        } finally { $client.Dispose() }
    }

    if ($CheckOnly) {
        Write-Host '环境检查通过，未启动任何组件。'
        exit 0
    }

    $componentScript = Join-Path $PSScriptRoot 'Start-Development.ps1'
    Write-Host '[1/3] 启动 Neo4j...'
    & $powershell -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $componentScript -Component Neo4j
    if ($LASTEXITCODE -ne 0) { throw 'Neo4j 启动命令失败，未启动前后端。' }

    $step = 2
    foreach ($component in @('Backend', 'Frontend')) {
        Write-Host "[$step/3] 打开 $component 日志窗口..."
        # 保留交互终端，让用户查看启动错误并通过 Ctrl+C 停止该组件。
        $arguments = @('-NoLogo', '-NoProfile', '-NoExit', '-ExecutionPolicy', 'Bypass', '-File', ('"' + $componentScript + '"'), '-Component', $component)
        Start-Process -FilePath $powershell -ArgumentList $arguments -WorkingDirectory $workspace -WindowStyle Normal | Out-Null
        $step++
    }

    Write-Host ''
    Write-Host '已提交前后端启动命令，请等待各日志窗口完成启动。'
    Write-Host '登录页面：http://127.0.0.1:5173/login'
    Write-Host '后端就绪检查：http://127.0.0.1:8080/actuator/health/readiness'
    Write-Host '停止前后端：在对应窗口按 Ctrl+C。'
    Write-Host '停止 Neo4j：docker compose --env-file .\.env -f .\deploy\compose.yaml stop neo4j'
    Write-Host '关闭本窗口不会停止已启动的组件。'
} catch {
    Write-Host ('[错误] ' + $_.Exception.Message) -ForegroundColor Red
    Write-Host '请确认 MySQL 和 Docker Desktop 已运行，并已配置 .env、安装前端依赖。'
    Write-Host '已启动的组件会保留，请查看对应窗口；本脚本不会自动停止进程或删除数据。'
    exit 1
} finally { Pop-Location }
