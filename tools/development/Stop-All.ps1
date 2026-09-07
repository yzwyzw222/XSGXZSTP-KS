[CmdletBinding()]
param(
    [switch]$CheckOnly,
    [switch]$ShowHelp
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

if ($ShowHelp) {
    Write-Host '用法：stop.bat [--check | --no-pause | --help]'
    Write-Host '  不带参数：停止本项目的前后端及 Neo4j，保留 MySQL、Docker Desktop 和数据卷。'
    Write-Host '  --check：只显示已核实的停止目标，不停止任何组件。'
    Write-Host '  --no-pause：执行停止后直接退出，不等待按键。'
    Write-Host '前后端按核实的进程身份终止；如需等待业务任务完成，请先在原窗口按 Ctrl+C。'
    exit 0
}

function Get-ApplicationTargets([object[]]$Processes) {
    $rootPattern = [regex]::Escape($workspace.TrimEnd('\').Replace('/', '\'))
    $selected = @{}
    foreach ($process in $Processes) {
        $line = ([string]$process.CommandLine).Replace('/', '\')
        $component = $null
        # npm 的 Windows 入口会保留 .bin\.. 路径片段，只接受这一种已知的等价路径。
        if ($process.Name -eq 'node.exe' -and $line -match ('(?:^|[\s"])' + $rootPattern + '\\frontend\\node_modules\\(?:\.bin\\+\.\.\\)?vite\\bin\\vite\.js(?=$|[\s"])')) {
            $component = 'Frontend'
        } elseif ($process.Name -in @('java.exe', 'javaw.exe')) {
            $application = $line -match '(?:^|\s)com\.aacv\.system\.AacvSystemApplication(?:\s|$)'
            $classes = $line -match ('(?:^|[\s";=])' + $rootPattern + '\\backend\\target\\classes(?=$|[\s";])')
            $maven = $line -match ('(?:^|[\s"])\-Dmaven\.multiModuleProjectDirectory=' + $rootPattern + '(?:\\backend)?(?=$|[\s"])')
            if (($application -and $classes) -or ($maven -and $line -match '(?:^|\s)spring-boot:run(?:\s|$)')) {
                $component = 'Backend'
            }
        }
        if ($component) {
            $selected[[int]$process.ProcessId] = [pscustomobject]@{
                Process = $process; Component = $component; Depth = 0
            }
        }
    }

    # 仅扩展已确认组件的子进程，并检查创建时间，避免父PID复用导致误判。
    do {
        $added = $false
        foreach ($process in $Processes) {
            $processId = [int]$process.ProcessId
            $parent = $selected[[int]$process.ParentProcessId]
            if (-not $selected.ContainsKey($processId) -and $parent -and $process.CreationDate -ge $parent.Process.CreationDate) {
                $selected[$processId] = [pscustomobject]@{
                    Process = $process; Component = $parent.Component; Depth = 0
                }
                $added = $true
            }
        }
    } while ($added)

    foreach ($target in $selected.Values) {
        $parentId = [int]$target.Process.ParentProcessId
        $visited = @{}
        while ($selected.ContainsKey($parentId) -and -not $visited.ContainsKey($parentId)) {
            $visited[$parentId] = $true
            $target.Depth++
            $parentId = [int]$selected[$parentId].Process.ParentProcessId
        }
    }
    $selected.Values | Sort-Object @{ Expression = { $_.Component -eq 'Frontend' }; Descending = $true }, @{ Expression = 'Depth'; Descending = $true }
}

function Get-ProcessSnapshot {
    @(Get-CimInstance Win32_Process -Property ProcessId, ParentProcessId, Name, CommandLine, CreationDate -ErrorAction Stop)
}

function Stop-ApplicationProcess($Target) {
    $record = $Target.Process
    $live = Get-Process -Id $record.ProcessId -ErrorAction SilentlyContinue
    if (-not $live) { return }
    try {
        if ($live.HasExited) { return }
        # 先固定进程句柄，再比对创建时间，防止枚举后PID被其他程序复用。
        $null = $live.Handle
        if ([Math]::Abs(($live.StartTime.ToUniversalTime() - $record.CreationDate.ToUniversalTime()).TotalMilliseconds) -gt 1) {
            throw "进程 $($record.ProcessId) 的身份已变化，未执行停止。"
        }
        Write-Host "停止 $($Target.Component)：PID $($record.ProcessId)"
        Stop-Process -InputObject $live -Force -ErrorAction Stop
        if (-not $live.WaitForExit(10000)) { throw "进程 $($record.ProcessId) 在10秒内未退出。" }
    } catch {
        if (-not $live.HasExited) { throw }
    } finally { $live.Dispose() }
}

function Get-ProjectNeo4j {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw '未找到 Docker，无法核实 Neo4j 状态。' }
    $ids = @(& docker ps -a --no-trunc --filter 'name=^/aacv-neo4j$' --format '{{.ID}}')
    if ($LASTEXITCODE -ne 0) { throw '无法访问 Docker，请确认 Docker Desktop 已运行且当前终端有访问权限。' }
    if ($ids.Count -eq 0) { return $null }
    if ($ids.Count -ne 1 -or $ids[0] -notmatch '^[0-9a-f]{64}$') { throw 'Neo4j 容器标识不明确，未执行停止。' }
    $containerId = $ids[0]
    # 只查询标签和运行状态，不读取包含凭据的容器环境变量或项目.env。
    $labelJson = & docker inspect --format '{{json .Config.Labels}}' $containerId
    if ($LASTEXITCODE -ne 0) { throw '无法核实 Neo4j 容器标签，未执行停止。' }
    $labels = $labelJson | ConvertFrom-Json
    $service = $labels.PSObject.Properties['com.docker.compose.service']
    $config = $labels.PSObject.Properties['com.docker.compose.project.config_files']
    $expectedConfig = Join-Path $workspace 'deploy\compose.yaml'
    if (-not $service -or $service.Value -ne 'neo4j' -or -not $config -or
        $config.Value.Replace('/', '\') -ine $expectedConfig) {
        throw '同名 Neo4j 容器不属于当前项目的 deploy/compose.yaml，未执行停止。'
    }
    $running = & docker inspect --format '{{.State.Running}}' $containerId
    if ($LASTEXITCODE -ne 0 -or $running -notin @('true', 'false')) { throw '无法读取 Neo4j 运行状态。' }
    [pscustomobject]@{ Id = $containerId; Running = $running -eq 'true' }
}

function Assert-KnownApplicationPorts([object[]]$Targets) {
    $knownIds = @($Targets | ForEach-Object { [int]$_.Process.ProcessId })
    $allListeners = @(Get-NetTCPConnection -State Listen -ErrorAction Stop)
    if (@($allListeners | Where-Object { $_.LocalPort -in @(18080, 28080) -and $_.OwningProcess -in $knownIds }).Count -gt 0) {
        throw '发现阶段8隔离后端，不能使用默认开发停止入口，请使用对应隔离环境的操作流程。'
    }
    $listeners = @($allListeners | Where-Object { $_.LocalPort -in @(8080, 5173) })
    foreach ($listener in $listeners) {
        if ($listener.OwningProcess -notin $knownIds) {
            throw "端口 $($listener.LocalPort) 的 PID $($listener.OwningProcess) 无法确认为本项目，未停止该进程。"
        }
    }
}

try {
    $targets = @(Get-ApplicationTargets (Get-ProcessSnapshot))
    Assert-KnownApplicationPorts $targets
    $neo4j = Get-ProjectNeo4j
    if ($targets.Count -eq 0) { Write-Host '前后端：没有发现本项目的运行进程。' }
    foreach ($target in $targets) {
        Write-Host "已核实 $($target.Component)：$($target.Process.Name)，PID $($target.Process.ProcessId)"
    }
    if ($neo4j -and $neo4j.Running) { Write-Host '已核实 Neo4j：aacv-neo4j，运行中。' }
    else { Write-Host 'Neo4j：未创建或已停止。' }
    if ($CheckOnly) {
        Write-Host '检查完成，未停止任何组件。'
        exit 0
    }

    foreach ($target in $targets) { Stop-ApplicationProcess $target }
    if (@(Get-ApplicationTargets (Get-ProcessSnapshot)).Count -gt 0) {
        throw '仍有本项目前后端进程运行，已保留 Neo4j，请检查原日志窗口。'
    }
    Assert-KnownApplicationPorts @()
    if ($neo4j -and $neo4j.Running) {
        Write-Host '停止 Neo4j，最多等待30秒正常退出...'
        $null = & docker stop --time 30 $neo4j.Id
        if ($LASTEXITCODE -ne 0) { throw 'Neo4j 停止命令失败，请检查 Docker 状态。' }
        $running = & docker inspect --format '{{.State.Running}}' $neo4j.Id
        if ($LASTEXITCODE -ne 0 -or $running -ne 'false') { throw '尚未确认 Neo4j 已停止。' }
    }
    Write-Host '项目已停止。MySQL、Docker Desktop、数据库和命名卷均保留。'
    Write-Host '原启动日志窗口可自行关闭。'
} catch {
    Write-Host ('[错误] ' + $_.Exception.Message) -ForegroundColor Red
    Write-Host '未停止无法确认归属的资源；已完成的停止不会自动回滚。'
    exit 1
}
