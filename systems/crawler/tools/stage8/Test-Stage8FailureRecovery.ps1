param(
    [string]$WorkspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [uri]$BaseUri = 'http://127.0.0.1:18080',
    [switch]$ConfirmFaultInjection,
    [string]$OutputPath
)

. (Join-Path $PSScriptRoot 'Stage8.Common.ps1')
if (-not $ConfirmFaultInjection) {
    throw '本脚本会依次停止并恢复阶段8隔离Neo4j和MySQL。确认目标无误后使用 -ConfirmFaultInjection。'
}
$workspace = Assert-Stage8Workspace -WorkspaceRoot $WorkspaceRoot
$composeFile = Join-Path $workspace 'deploy\compose.stage8.yaml'

$databaseCredential = Get-Credential -Message '输入阶段8隔离数据库账号' -UserName 'aacv_stage8'
$databaseRootCredential = Get-Credential -Message '输入阶段8隔离数据库root账号' -UserName 'root'
$neo4jCredential = Get-Credential -Message '输入阶段8隔离Neo4j账号' -UserName 'neo4j'
$adminCredential = Get-Credential -Message '输入阶段8隔离环境管理员账号' -UserName 'aacv-stage8-admin'
Assert-Stage8Credential $databaseCredential 'aacv_stage8' '阶段8数据库'
Assert-Stage8Credential $databaseRootCredential 'root' '阶段8数据库root'
Assert-Stage8Credential $neo4jCredential 'neo4j' '阶段8 Neo4j'
Assert-Stage8Credential $adminCredential 'aacv-stage8-admin' '阶段8管理员'

$databasePassword = ConvertTo-Stage8PlainText $databaseCredential.Password
$databaseRootPassword = ConvertTo-Stage8PlainText $databaseRootCredential.Password
$neo4jPassword = ConvertTo-Stage8PlainText $neo4jCredential.Password
$adminPassword = ConvertTo-Stage8PlainText $adminCredential.Password
$webSession = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
$startedAt = [DateTimeOffset]::Now

function Invoke-CheckedGet {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [int[]]$Expected = @(200),
        [int]$TimeoutSeconds = 15
    )
    $response = Invoke-Stage8WebRequest -Uri ([uri]::new($BaseUri, $Path)) -WebSession $webSession `
        -Method Get -TimeoutSeconds $TimeoutSeconds
    if ($Expected -notcontains [int]$response.StatusCode) {
        throw "$Path 返回HTTP $([int]$response.StatusCode)，期望：$($Expected -join ',')。"
    }
    return $response
}

try {
    $env:AACV_STAGE8_DB_USERNAME = $databaseCredential.UserName
    $env:AACV_STAGE8_DB_PASSWORD = $databasePassword
    $env:AACV_STAGE8_DB_ROOT_PASSWORD = $databaseRootPassword
    $env:AACV_STAGE8_NEO4J_USERNAME = $neo4jCredential.UserName
    $env:AACV_STAGE8_NEO4J_PASSWORD = $neo4jPassword

    $csrfResponse = Invoke-CheckedGet '/api/v1/auth/csrf'
    $csrf = $csrfResponse.Content | ConvertFrom-Json
    $loginBody = @{ username = $adminCredential.UserName; password = $adminPassword } | ConvertTo-Json -Compress
    $loginResponse = Invoke-Stage8WebRequest -Uri ([uri]::new($BaseUri, '/api/v1/auth/login')) `
        -WebSession $webSession -Method Post -Headers @{ ([string]$csrf.headerName) = [string]$csrf.token } `
        -ContentType 'application/json' -Body $loginBody -TimeoutSeconds 15
    if ([int]$loginResponse.StatusCode -ne 200) { throw '阶段8管理员登录失败。' }
    $loginBody = $null
    $adminPassword = $null

    Invoke-CheckedGet '/api/v1/catalog/achievements?size=20' | Out-Null
    Invoke-CheckedGet '/api/v1/graph/subgraph?centerType=VENUE&centerId=1&depth=2&nodeLimit=100' | Out-Null

    $neo4jStopWatch = [Diagnostics.Stopwatch]::StartNew()
    try {
        Write-Host '正在停止阶段8隔离Neo4j并验证MySQL功能降级边界。'
        docker compose -f $composeFile stop --timeout 60 neo4j
        if ($LASTEXITCODE -ne 0) { throw '停止阶段8 Neo4j失败。' }
        $neo4jStopWatch.Stop()
        $neo4jLiveness = Invoke-CheckedGet '/actuator/health/liveness'
        $neo4jCatalog = Invoke-CheckedGet '/api/v1/catalog/achievements?size=20'
        $neo4jGraphHealth = Invoke-CheckedGet '/actuator/health/graph' @(200, 503)
        $neo4jGraphQueryWatch = [Diagnostics.Stopwatch]::StartNew()
        try {
            $neo4jGraphQuery = Invoke-CheckedGet `
                '/api/v1/graph/subgraph?centerType=VENUE&centerId=1&depth=2&nodeLimit=100' `
                -Expected @(503) -TimeoutSeconds 45
        } finally {
            $neo4jGraphQueryWatch.Stop()
        }
    } finally {
        Write-Host '正在恢复阶段8隔离Neo4j。'
        $neo4jRecoveryWatch = [Diagnostics.Stopwatch]::StartNew()
        docker compose -f $composeFile start neo4j
        if ($LASTEXITCODE -ne 0) { throw '恢复阶段8 Neo4j失败。' }
        Wait-Stage8ContainerHealthy -ContainerName 'aacv-stage8-neo4j' -TimeoutSeconds 180
        Wait-Stage8Http -Uri ([uri]::new($BaseUri, '/actuator/health/graph')) -TimeoutSeconds 180
        $neo4jRecoveryWatch.Stop()
    }
    Invoke-CheckedGet '/api/v1/graph/subgraph?centerType=VENUE&centerId=1&depth=2&nodeLimit=100' | Out-Null

    $mysql = Get-Command mysql.exe -ErrorAction Stop
    $env:MYSQL_PWD = $databasePassword
    $before = & $mysql.Source --host=127.0.0.1 --port=13306 --user=$($databaseCredential.UserName) `
        --batch --skip-column-names --execute='SELECT COUNT(*) FROM export_task' aacv_stage8_capacity_20260903
    if ($LASTEXITCODE -ne 0) { throw 'MySQL故障前一致性计数失败。' }
    $mysqlFailureKind = $null
    $mysqlStopWatch = [Diagnostics.Stopwatch]::StartNew()
    try {
        Write-Host '正在停止阶段8隔离MySQL并验证写请求不会虚假成功。'
        docker compose -f $composeFile stop --timeout 60 mysql
        if ($LASTEXITCODE -ne 0) { throw '停止阶段8 MySQL失败。' }
        $mysqlStopWatch.Stop()
        $failedWrite = $null
        try {
            $failedWrite = Invoke-Stage8WebRequest -Uri ([uri]::new($BaseUri, '/api/v1/exports')) `
                -WebSession $webSession -Method Post -Headers @{ ([string]$csrf.headerName) = [string]$csrf.token } `
                -ContentType 'application/json' -Body '{"format":"CSV","filters":{}}' `
                -TimeoutSeconds 15
            $mysqlFailureKind = "HTTP_$([int]$failedWrite.StatusCode)"
        } catch {
            # 数据库故障期间连接中断或请求超时同样属于明确失败，而不是业务成功。
            $mysqlFailureKind = $_.Exception.GetType().Name
        }
        if ($null -ne $failedWrite -and
            [int]$failedWrite.StatusCode -ge 200 -and [int]$failedWrite.StatusCode -lt 300) {
            throw 'MySQL停止期间写请求出现虚假成功。'
        }
    } finally {
        Write-Host '正在恢复阶段8隔离MySQL。'
        $mysqlRecoveryWatch = [Diagnostics.Stopwatch]::StartNew()
        docker compose -f $composeFile start mysql
        if ($LASTEXITCODE -ne 0) { throw '恢复阶段8 MySQL失败。' }
        Wait-Stage8ContainerHealthy -ContainerName 'aacv-stage8-mysql' -TimeoutSeconds 180
        Wait-Stage8Http -Uri ([uri]::new($BaseUri, '/actuator/health/readiness')) -TimeoutSeconds 180
        $mysqlRecoveryWatch.Stop()
    }
    $after = & $mysql.Source --host=127.0.0.1 --port=13306 --user=$($databaseCredential.UserName) `
        --batch --skip-column-names --execute='SELECT COUNT(*) FROM export_task' aacv_stage8_capacity_20260903
    if ($LASTEXITCODE -ne 0 -or [long]$after -ne [long]$before) {
        throw 'MySQL故障恢复后检测到导出任务计数异常。'
    }
    $finalLiveness = Invoke-CheckedGet '/actuator/health/liveness'
    $finalReadiness = Invoke-CheckedGet '/actuator/health/readiness'
    $finalGraph = Invoke-CheckedGet '/actuator/health/graph'
    $completedAt = [DateTimeOffset]::Now
    $evidence = [ordered]@{
        startedAt = $startedAt.ToString('o')
        completedAt = $completedAt.ToString('o')
        durationSeconds = [Math]::Round(($completedAt - $startedAt).TotalSeconds, 3)
        baseUri = $BaseUri.AbsoluteUri
        neo4jFailure = [ordered]@{
            stopSeconds = [Math]::Round($neo4jStopWatch.Elapsed.TotalSeconds, 3)
            livenessHttpStatus = [int]$neo4jLiveness.StatusCode
            catalogHttpStatus = [int]$neo4jCatalog.StatusCode
            graphHealthHttpStatus = [int]$neo4jGraphHealth.StatusCode
            graphQueryHttpStatus = [int]$neo4jGraphQuery.StatusCode
            graphQueryElapsedSeconds = [Math]::Round($neo4jGraphQueryWatch.Elapsed.TotalSeconds, 3)
            recoverySeconds = [Math]::Round($neo4jRecoveryWatch.Elapsed.TotalSeconds, 3)
        }
        mysqlFailure = [ordered]@{
            stopSeconds = [Math]::Round($mysqlStopWatch.Elapsed.TotalSeconds, 3)
            writeResult = $mysqlFailureKind
            exportTaskCountBefore = [long]$before
            exportTaskCountAfter = [long]$after
            recoverySeconds = [Math]::Round($mysqlRecoveryWatch.Elapsed.TotalSeconds, 3)
        }
        finalHealth = [ordered]@{
            livenessHttpStatus = [int]$finalLiveness.StatusCode
            readinessHttpStatus = [int]$finalReadiness.StatusCode
            graphHttpStatus = [int]$finalGraph.StatusCode
        }
        volumesDeleted = $false
        passed = $true
    }
    $json = $evidence | ConvertTo-Json -Depth 8
    if ($OutputPath) {
        $parent = Split-Path -Parent $OutputPath
        if (-not $parent -or -not (Test-Path -LiteralPath $parent -PathType Container)) {
            throw '可靠性证据输出目录必须预先存在。'
        }
        Set-Content -LiteralPath $OutputPath -Value $json -Encoding utf8NoBOM
    }
    $json
    Write-Host '阶段8依赖故障演练通过：Neo4j降级时MySQL目录可用；MySQL故障无虚假写成功；两项依赖均已恢复。'
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    Remove-Item Env:AACV_STAGE8_DB_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:AACV_STAGE8_DB_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:AACV_STAGE8_DB_ROOT_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:AACV_STAGE8_NEO4J_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:AACV_STAGE8_NEO4J_PASSWORD -ErrorAction SilentlyContinue
    $databasePassword = $null
    $databaseRootPassword = $null
    $neo4jPassword = $null
    $adminPassword = $null
    $loginBody = $null
}
