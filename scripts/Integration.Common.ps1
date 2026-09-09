Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:IntegrationRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')).TrimEnd('\', '/')
$script:IntegrationStateDirectory = Join-Path $script:IntegrationRoot '.local/integration'
$script:IntegrationStatePath = Join-Path $script:IntegrationStateDirectory 'processes.json'

function Get-IntegrationPlan {
    param([string]$System = 'all', [string]$Mode = 'Demo')
    $nodeCommand = (Get-Command node -ErrorAction Stop).Source
    $version = & $nodeCommand -p 'process.versions.node'
    if ([version]$version -lt [version]'22.12.0') { throw '门户需要 Node.js 22.12.0 或更新版本。' }
    $json = & $nodeCommand (Join-Path $script:IntegrationRoot 'scripts/config.mjs') $System $Mode
    if ($LASTEXITCODE -ne 0) { throw '接入配置校验失败，未启动任何组件。' }
    return $json | ConvertFrom-Json
}

function Enter-IntegrationLock {
    New-Item -ItemType Directory -Path $script:IntegrationStateDirectory -Force | Out-Null
    try {
        return [IO.File]::Open((Join-Path $script:IntegrationStateDirectory 'operation.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    } catch [IO.IOException] {
        throw '已有启停操作正在执行，请待其结束后重试。'
    }
}

function Read-IntegrationState {
    if (-not (Test-Path -LiteralPath $script:IntegrationStatePath)) { return @() }
    $state = Get-Content -LiteralPath $script:IntegrationStatePath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($state.root -ne $script:IntegrationRoot) { throw '进程记录不属于当前工作区，拒绝操作。' }
    return @($state.processes)
}

function Save-IntegrationState {
    param([object[]]$Records)
    $json = @{ root = $script:IntegrationRoot; processes = @($Records) } | ConvertTo-Json -Depth 8
    $temporary = Join-Path $script:IntegrationStateDirectory 'processes.next.json'
    [IO.File]::WriteAllText($temporary, $json, [Text.UTF8Encoding]::new($false))
    Move-Item -LiteralPath $temporary -Destination $script:IntegrationStatePath -Force
}

function Assert-IntegrationPort {
    param([int]$Port)
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $Port)
    $listener.Server.ExclusiveAddressUse = $true
    try { $listener.Start() } catch { throw "端口 $Port 不可绑定或已被占用；不会停止占用进程。" } finally { $listener.Stop() }
}

function Test-IntegrationIdentity {
    param($Record)
    $directory = [IO.Path]::GetFullPath($Record.directory)
    if (-not $directory.StartsWith($script:IntegrationRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw '记录的工作目录越界，拒绝操作。'
    }
    $process = Get-Process -Id $Record.pid -ErrorAction SilentlyContinue
    if (-not $process) { return $false }
    $details = Get-CimInstance Win32_Process -Filter "ProcessId = $($Record.pid)" -ErrorAction Stop
    if (-not $details) { return $false }
    if (-not $details.CommandLine) { throw "无法核对 $($Record.id) 的进程参数，拒绝操作。" }
    return $process.StartTime.ToUniversalTime().Ticks.ToString() -eq $Record.createdTicks -and
        $details.ExecutablePath -eq $Record.executable -and $details.CommandLine -eq $Record.commandLine
}

function Start-IntegrationProcess {
    param([string]$Id, [string]$System, [string]$Executable, [string[]]$Arguments, [string]$WorkingDirectory, [int]$Port)
    Assert-IntegrationPort $Port
    $directory = [IO.Path]::GetFullPath($WorkingDirectory)
    if (-not $directory.StartsWith($script:IntegrationRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw '进程工作目录必须位于当前整合仓库内。'
    }
    $command = (Get-Command $Executable -ErrorAction Stop).Source
    # 参数经过配置校验，并独立加引号；不启动 cmd 或按进程名称批量结束。
    $argumentLine = ($Arguments | ForEach-Object {
        $_ = $_.Replace('{workspace}', $script:IntegrationRoot.Replace('\', '/'))
        if ($_ -match '["\r\n]') { throw '参数包含不支持的引号或换行。' }
        '"' + $_.TrimEnd('\') + '"'
    }) -join ' '
    $process = Start-Process -FilePath $command -ArgumentList $argumentLine -WorkingDirectory $directory -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $script:IntegrationStateDirectory "$Id.out.log") `
        -RedirectStandardError (Join-Path $script:IntegrationStateDirectory "$Id.err.log")
    try {
        $process.Refresh()
        $createdTicks = $process.StartTime.ToUniversalTime().Ticks.ToString()
        $details = Get-CimInstance Win32_Process -Filter "ProcessId = $($process.Id)"
        if (-not $details -or -not $details.CommandLine) { throw '启动进程已退出或无法读取归属信息。' }
        return [pscustomobject]@{ id=$Id; system=$System; pid=$process.Id; port=$Port; createdTicks=$createdTicks;
            executable=$details.ExecutablePath; commandLine=$details.CommandLine; directory=$directory }
    } catch {
        # 仅清理本函数刚创建且仍持有句柄的进程，不按端口或名称推断归属。
        if (-not $process.HasExited) { $process.Kill() }
        throw
    }
}

function Wait-IntegrationReady {
    param($Record, [string]$Url, [string]$Revision = '', [int]$TimeoutSeconds = 90)
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        if (-not (Test-IntegrationIdentity $Record)) { throw "$($Record.id) 已退出或进程身份发生变化。" }
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 2
            if ($response.StatusCode -eq 200) {
                if ($Revision) {
                    $health = $response.Content | ConvertFrom-Json
                    if ($health.status -eq 'UP' -and $health.revision -eq $Revision) { return }
                } else { return }
            }
        } catch {
            # 启动期间只在有界期限内重试就绪探针；超时会报告所属组件和日志位置。
        }
        Start-Sleep -Milliseconds 200
    }
    throw "$($Record.id) 在 $TimeoutSeconds 秒内未就绪。日志位于 .local/integration，已启动组件保持运行。"
}
