param(
    [string]$WorkspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [uri]$BaseUri = 'http://127.0.0.1:18080',
    [string]$OutputPath
)

. (Join-Path $PSScriptRoot 'Stage8.Common.ps1')
$workspace = Assert-Stage8Workspace -WorkspaceRoot $WorkspaceRoot
$databaseCredential = Get-Credential -Message '输入阶段8隔离数据库账号' -UserName 'aacv_stage8'
$neo4jCredential = Get-Credential -Message '输入阶段8隔离Neo4j账号' -UserName 'neo4j'
Assert-Stage8Credential $databaseCredential 'aacv_stage8' '阶段8数据库'
Assert-Stage8Credential $neo4jCredential 'neo4j' '阶段8 Neo4j'

$databasePassword = ConvertTo-Stage8PlainText $databaseCredential.Password
$neo4jPassword = ConvertTo-Stage8PlainText $neo4jCredential.Password
$startedAt = [DateTimeOffset]::Now
$backend = $null
try {
    Wait-Stage8ContainerHealthy -ContainerName 'aacv-stage8-mysql' -TimeoutSeconds 30
    Wait-Stage8ContainerHealthy -ContainerName 'aacv-stage8-neo4j' -TimeoutSeconds 30

    $listener = Get-NetTCPConnection -LocalAddress 127.0.0.1 -LocalPort $BaseUri.Port `
        -State Listen -ErrorAction Stop | Select-Object -First 1
    $applicationProcess = Get-CimInstance Win32_Process `
        -Filter "ProcessId=$($listener.OwningProcess)" -ErrorAction Stop
    $parentProcess = Get-CimInstance Win32_Process `
        -Filter "ProcessId=$($applicationProcess.ParentProcessId)" -ErrorAction Stop
    $applicationMatches = $applicationProcess.Name -eq 'java.exe' -and
        ([string]$applicationProcess.CommandLine).IndexOf(
            'com.aacv.system.AacvSystemApplication', [StringComparison]::Ordinal) -ge 0
    $parentMatches = $parentProcess.Name -eq 'java.exe' -and
        ([string]$parentProcess.CommandLine).IndexOf($workspace, [StringComparison]::OrdinalIgnoreCase) -ge 0 -and
        ([string]$parentProcess.CommandLine).IndexOf('spring-boot:run', [StringComparison]::Ordinal) -ge 0
    if (-not $applicationMatches -or -not $parentMatches) {
        throw '18080端口监听进程不能证明属于当前阶段8后端，拒绝停止。'
    }

    $oldProcessId = [int]$applicationProcess.ProcessId
    Write-Host "正在停止已验证的阶段8后端PID=$oldProcessId。"
    Stop-Process -Id $oldProcessId -ErrorAction Stop
    Wait-Process -Id $oldProcessId -Timeout 30 -ErrorAction SilentlyContinue
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds(30)
    do {
        $remainingListener = Get-NetTCPConnection -LocalAddress 127.0.0.1 -LocalPort $BaseUri.Port `
            -State Listen -ErrorAction SilentlyContinue
        if ($null -eq $remainingListener) { break }
        Start-Sleep -Seconds 1
    } while ([DateTimeOffset]::UtcNow -lt $deadline)
    if ($null -ne $remainingListener) { throw '阶段8后端端口未在30秒内释放。' }

    $env:AACV_DB_URL = 'jdbc:mysql://127.0.0.1:13306/aacv_stage8_capacity_20260903?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true'
    $env:AACV_DB_USERNAME = $databaseCredential.UserName
    $env:AACV_DB_PASSWORD = $databasePassword
    $env:AACV_NEO4J_URI = 'bolt://127.0.0.1:17687'
    $env:AACV_NEO4J_USERNAME = $neo4jCredential.UserName
    $env:AACV_NEO4J_PASSWORD = $neo4jPassword
    $env:AACV_SERVER_PORT = [string]$BaseUri.Port
    $env:AACV_EXPORT_ROOT = Join-Path $env:TEMP 'aacv-stage8-exports'

    $backend = Start-Process -FilePath (Join-Path $workspace 'mvnw.cmd') `
        -ArgumentList @('-f', (Join-Path $workspace 'backend\pom.xml'), 'spring-boot:run') `
        -WorkingDirectory $workspace -WindowStyle Hidden -PassThru
    Wait-Stage8Http -Uri ([uri]::new($BaseUri, '/actuator/health/liveness')) -TimeoutSeconds 240
    Wait-Stage8Http -Uri ([uri]::new($BaseUri, '/actuator/health/readiness')) -TimeoutSeconds 240
    Wait-Stage8Http -Uri ([uri]::new($BaseUri, '/actuator/health/graph')) -TimeoutSeconds 240
    if ($backend.HasExited) { throw '阶段8后端重启后已退出。' }

    $newListener = Get-NetTCPConnection -LocalAddress 127.0.0.1 -LocalPort $BaseUri.Port `
        -State Listen -ErrorAction Stop | Select-Object -First 1
    if ([int]$newListener.OwningProcess -eq $oldProcessId) {
        throw '阶段8后端重启后PID未变化。'
    }
    $completedAt = [DateTimeOffset]::Now
    $evidence = [ordered]@{
        startedAt = $startedAt.ToString('o')
        completedAt = $completedAt.ToString('o')
        durationSeconds = [Math]::Round(($completedAt - $startedAt).TotalSeconds, 3)
        oldProcessId = $oldProcessId
        newProcessId = [int]$newListener.OwningProcess
        livenessHttpStatus = 200
        readinessHttpStatus = 200
        graphHttpStatus = 200
        passed = $true
    }
    $json = $evidence | ConvertTo-Json -Depth 5
    if ($OutputPath) {
        $parent = Split-Path -Parent $OutputPath
        if (-not $parent -or -not (Test-Path -LiteralPath $parent -PathType Container)) {
            throw '重启证据输出目录必须预先存在。'
        }
        Set-Content -LiteralPath $OutputPath -Value $json -Encoding utf8NoBOM
    }
    $json
    Write-Host '阶段8后端已安全重启并通过三个健康检查。'
} catch {
    if ($null -ne $backend -and -not $backend.HasExited) {
        Stop-Process -Id $backend.Id -ErrorAction SilentlyContinue
    }
    throw
} finally {
    Remove-Item Env:AACV_DB_URL -ErrorAction SilentlyContinue
    Remove-Item Env:AACV_DB_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:AACV_DB_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:AACV_NEO4J_URI -ErrorAction SilentlyContinue
    Remove-Item Env:AACV_NEO4J_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:AACV_NEO4J_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:AACV_SERVER_PORT -ErrorAction SilentlyContinue
    Remove-Item Env:AACV_EXPORT_ROOT -ErrorAction SilentlyContinue
    $databasePassword = $null
    $neo4jPassword = $null
}
