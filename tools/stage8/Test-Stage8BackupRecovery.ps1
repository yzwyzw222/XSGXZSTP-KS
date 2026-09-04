param(
    [string]$WorkspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [string]$BackupRoot = 'E:\AACV_System_Backups',
    [string]$BackupPath,
    [uri]$BaseUri = 'http://127.0.0.1:28080',
    [ValidateRange(1, 240)][int]$RecoveryTimeoutMinutes = 240,
    [switch]$ConfirmIsolatedRestore,
    [string]$OutputPath
)

. (Join-Path $PSScriptRoot 'Stage8.Common.ps1')
if (-not $ConfirmIsolatedRestore) {
    throw '本脚本会写入独立恢复数据库并重建独立Neo4j投影。确认目标无误后使用 -ConfirmIsolatedRestore。'
}

function Get-Stage8RecoverySnapshot {
    param(
        [Parameter(Mandatory = $true)][System.Management.Automation.CommandInfo]$MySql,
        [Parameter(Mandatory = $true)][System.Management.Automation.PSCredential]$Credential
    )

    $sql = @'
SELECT CONCAT('serverVersion=', VERSION());
SELECT CONCAT('flywayVersion=', version)
FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1;
SELECT CONCAT('tableCount=', COUNT(*))
FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE';
SELECT CONCAT('achievementCount=', COUNT(*)) FROM achievement;
SELECT CONCAT('userCount=', COUNT(*)) FROM sys_user;
SELECT CONCAT('auditCount=', COUNT(*)) FROM audit_log;
SELECT CONCAT('outboxCount=', COUNT(*)) FROM graph_outbox_event;
SELECT CONCAT('projectionStateCount=', COUNT(*)) FROM graph_projection_state;
'@
    $arguments = @(
        '--host=127.0.0.1',
        '--port=23306',
        "--user=$($Credential.UserName)",
        '--batch',
        '--skip-column-names',
        "--execute=$sql",
        'aacv_stage8_recovery'
    )
    $lines = @(& $MySql.Source @arguments)
    if ($LASTEXITCODE -ne 0) { throw '读取隔离恢复数据库基线失败。' }
    $result = [ordered]@{}
    foreach ($line in $lines) {
        $parts = ([string]$line).Split(@('='), 2)
        if ($parts.Count -eq 2) { $result[$parts[0]] = $parts[1] }
    }
    foreach ($required in @(
        'serverVersion', 'flywayVersion', 'tableCount', 'achievementCount',
        'userCount', 'auditCount', 'outboxCount', 'projectionStateCount')) {
        if (-not $result.Contains($required)) { throw "恢复数据库基线缺少字段：$required" }
    }
    return $result
}

function Invoke-Stage8RecoveryApi {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [ValidateSet('GET', 'POST')][string]$Method = 'GET',
        [AllowNull()][object]$Body,
        [int[]]$ExpectedStatus = @(200),
        [int]$TimeoutSeconds = 30
    )

    $parameters = @{
        Uri = [uri]::new($BaseUri, $Path)
        WebSession = $script:webSession
        Method = $Method
        TimeoutSeconds = $TimeoutSeconds
        Headers = @{ Accept = 'application/json' }
    }
    if ($Method -eq 'POST') {
        $parameters.Headers[[string]$script:csrf.headerName] = [string]$script:csrf.token
        $parameters.ContentType = 'application/json'
        $parameters.Body = if ($PSBoundParameters.ContainsKey('Body') -and $null -ne $Body) {
            $Body | ConvertTo-Json -Depth 8 -Compress
        } else {
            '{}'
        }
    }
    $response = Invoke-Stage8WebRequest @parameters
    if ($ExpectedStatus -notcontains [int]$response.StatusCode) {
        throw "$Method $Path 返回HTTP $([int]$response.StatusCode)，期望：$($ExpectedStatus -join ',')。"
    }
    if ([int]$response.StatusCode -eq 204 -or [string]::IsNullOrWhiteSpace($response.Content)) {
        return $null
    }
    return $response.Content | ConvertFrom-Json
}

function Wait-Stage8MaintenanceRun {
    param(
        [Parameter(Mandatory = $true)][long]$RunId,
        [Parameter(Mandatory = $true)][DateTimeOffset]$Deadline
    )

    do {
        Start-Sleep -Seconds 2
        $page = Invoke-Stage8RecoveryApi '/api/v1/operations/graph-maintenance/runs?page=0&size=100'
        $run = @($page.items | Where-Object { [long]$_.id -eq $RunId }) | Select-Object -First 1
        if ($null -eq $run) { throw "无法在维护运行分页中定位 runId=$RunId。" }
        if ($run.status -in @('SUCCEEDED', 'FAILED')) { return $run }
    } while ([DateTimeOffset]::UtcNow -lt $Deadline)
    throw "等待图维护运行超时，runId=$RunId。"
}

function Test-Stage8DescendantProcess {
    param(
        [Parameter(Mandatory = $true)][int]$ChildProcessId,
        [Parameter(Mandatory = $true)][int]$AncestorProcessId
    )

    $currentProcessId = $ChildProcessId
    for ($depth = 0; $depth -lt 16; $depth++) {
        if ($currentProcessId -eq $AncestorProcessId) { return $true }
        $process = Get-CimInstance -ClassName Win32_Process `
            -Filter "ProcessId = $currentProcessId" -ErrorAction SilentlyContinue
        if ($null -eq $process -or [int]$process.ParentProcessId -le 0 -or
            [int]$process.ParentProcessId -eq $currentProcessId) {
            return $false
        }
        $currentProcessId = [int]$process.ParentProcessId
    }
    return $false
}

function Assert-Stage8LocalPortAvailable {
    param([Parameter(Mandatory = $true)][int]$Port)

    $probe = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $Port)
    $probe.Server.ExclusiveAddressUse = $true
    try {
        $probe.Start()
    } catch [Net.Sockets.SocketException] {
        throw "阶段8隔离恢复端口$Port 已被占用；脚本不会停止或复用现有进程。"
    } finally {
        $probe.Stop()
    }
}

$workspace = Assert-Stage8Workspace -WorkspaceRoot $WorkspaceRoot
$composeFile = Join-Path $workspace 'deploy\compose.stage8-recovery.yaml'
if (-not (Test-Path -LiteralPath $composeFile -PathType Leaf)) {
    throw '缺少阶段8隔离恢复Compose文件。'
}
if ($BaseUri.AbsoluteUri.TrimEnd('/') -cne 'http://127.0.0.1:28080') {
    throw '阶段8隔离恢复后端地址固定为 http://127.0.0.1:28080。'
}
Assert-Stage8LocalPortAvailable -Port 28080
$backupRootFull = [IO.Path]::GetFullPath($BackupRoot).TrimEnd('\')
if ($backupRootFull -ne 'E:\AACV_System_Backups') {
    throw '阶段8业务备份目录固定为 E:\AACV_System_Backups。'
}
if (-not (Test-Path -LiteralPath $backupRootFull -PathType Container)) {
    throw '阶段8业务备份目录不存在。'
}
if (((Get-Item -LiteralPath $backupRootFull -Force).Attributes -band
    [IO.FileAttributes]::ReparsePoint) -ne 0) {
    throw '阶段8业务备份目录不能是重解析点。'
}
Assert-Stage8RestrictedBackupAcl -Path $backupRootFull
if ([string]::IsNullOrWhiteSpace($BackupPath)) {
    $latest = Get-ChildItem -LiteralPath (Join-Path $backupRootFull 'daily') -Filter '*.sql' -File |
        Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    if ($null -eq $latest) { throw '没有可供恢复的每日备份。' }
    $BackupPath = $latest.FullName
}
$backupPathFull = (Resolve-Path -LiteralPath $BackupPath).Path
$backupPrefix = $backupRootFull + '\'
if (-not $backupPathFull.StartsWith($backupPrefix, [StringComparison]::OrdinalIgnoreCase) -or
    [IO.Path]::GetExtension($backupPathFull) -ne '.sql') {
    throw '恢复文件必须是固定备份目录内的.sql文件。'
}
$hashPath = $backupPathFull + '.sha256'
$metadataPath = $backupPathFull + '.metadata.json'
if (-not (Test-Path -LiteralPath $hashPath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $metadataPath -PathType Leaf)) {
    throw '恢复文件缺少SHA-256或元数据旁车文件。'
}
$expectedHash = (Get-Content -LiteralPath $hashPath -Raw -Encoding UTF8).Trim().ToUpperInvariant()
if ($expectedHash -notmatch '^[0-9A-F]{64}$') { throw 'SHA-256旁车内容格式无效。' }
$actualHash = (Get-FileHash -LiteralPath $backupPathFull -Algorithm SHA256).Hash.ToUpperInvariant()
if ($actualHash -ne $expectedHash) { throw '恢复前SHA-256复核失败。' }
$metadata = Get-Content -LiteralPath $metadataPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ([int]$metadata.formatVersion -ne 1 -or
    ([string]$metadata.sha256).ToUpperInvariant() -ne $actualHash) {
    throw '备份元数据版本或摘要与备份文件不一致。'
}
if ([string]$metadata.flywayVersion -ne '11') {
    throw "备份Flyway版本必须为11，实际为 $($metadata.flywayVersion)。"
}

$databaseCredential = Get-Credential -Message '设置阶段8隔离恢复数据库账号' `
    -UserName 'aacv_stage8_recovery'
$databaseRootCredential = Get-Credential -Message '设置阶段8隔离恢复数据库root密码' -UserName 'root'
$neo4jCredential = Get-Credential -Message '设置阶段8隔离恢复Neo4j账号' -UserName 'neo4j'
$adminCredential = Get-Credential -Message '输入备份内已有的AACV管理员账号'
Assert-Stage8Credential $databaseCredential 'aacv_stage8_recovery' '阶段8恢复数据库'
Assert-Stage8Credential $databaseRootCredential 'root' '阶段8恢复数据库root'
Assert-Stage8Credential $neo4jCredential 'neo4j' '阶段8恢复Neo4j'
Assert-Stage8Credential $adminCredential $adminCredential.UserName '恢复环境管理员'

$mysql = Get-Command mysql.exe -ErrorAction Stop
$databasePassword = ConvertTo-Stage8PlainText $databaseCredential.Password
$databaseRootPassword = ConvertTo-Stage8PlainText $databaseRootCredential.Password
$neo4jPassword = ConvertTo-Stage8PlainText $neo4jCredential.Password
$adminPassword = ConvertTo-Stage8PlainText $adminCredential.Password
$backend = $null
$containersStarted = $false
$startedAt = [DateTimeOffset]::Now
$script:webSession = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
try {
    $env:AACV_STAGE8_RECOVERY_DB_USERNAME = $databaseCredential.UserName
    $env:AACV_STAGE8_RECOVERY_DB_PASSWORD = $databasePassword
    $env:AACV_STAGE8_RECOVERY_DB_ROOT_PASSWORD = $databaseRootPassword
    $env:AACV_STAGE8_RECOVERY_NEO4J_USERNAME = $neo4jCredential.UserName
    $env:AACV_STAGE8_RECOVERY_NEO4J_PASSWORD = $neo4jPassword

    docker compose -f $composeFile config --quiet
    if ($LASTEXITCODE -ne 0) { throw '阶段8隔离恢复Compose配置校验失败。' }
    docker compose -f $composeFile up -d
    if ($LASTEXITCODE -ne 0) { throw '阶段8隔离恢复容器启动失败。' }
    $containersStarted = $true
    Wait-Stage8ContainerHealthy 'aacv-stage8-recovery-mysql' 240
    Wait-Stage8ContainerHealthy 'aacv-stage8-recovery-neo4j' 240

    $env:MYSQL_PWD = $databasePassword
    $tableCount = & $mysql.Source --host=127.0.0.1 --port=23306 `
        --user=$($databaseCredential.UserName) --batch --skip-column-names `
        --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'aacv_stage8_recovery'" `
        aacv_stage8_recovery
    if ($LASTEXITCODE -ne 0) { throw '无法验证隔离恢复数据库是否为空。' }
    if ([long]$tableCount -ne 0) {
        throw '隔离恢复数据库已有对象；脚本不会清空或覆盖。'
    }
    $existingGraph = Invoke-Stage8Neo4jStatement `
        -Statement 'MATCH (n) RETURN count(n) AS nodes' -Parameters @{} `
        -Credential $neo4jCredential -Endpoint 'http://127.0.0.1:27474/db/neo4j/tx/commit'
    if ([long]$existingGraph.results[0].data[0].row[0] -ne 0) {
        throw '隔离恢复Neo4j已有节点；脚本不会清空或覆盖。'
    }

    $restoreErrorPath = Join-Path $env:TEMP (
        'aacv-stage8-mysql-restore-{0}.err' -f ([guid]::NewGuid()))
    try {
        $restoreArguments = @(
            '--host=127.0.0.1', '--port=23306',
            "--user=$($databaseCredential.UserName)",
            '--default-character-set=utf8mb4', 'aacv_stage8_recovery'
        )
        $restore = Start-Process -FilePath $mysql.Source -ArgumentList $restoreArguments `
            -NoNewWindow -Wait -PassThru -RedirectStandardInput $backupPathFull `
            -RedirectStandardError $restoreErrorPath
        if ($restore.ExitCode -ne 0) {
            throw "MySQL隔离恢复失败，退出码：$($restore.ExitCode)。"
        }
    } finally {
        if (Test-Path -LiteralPath $restoreErrorPath -PathType Leaf) {
            Remove-Item -LiteralPath $restoreErrorPath -Force -ErrorAction SilentlyContinue
        }
    }

    $restored = Get-Stage8RecoverySnapshot -MySql $mysql -Credential $databaseCredential
    foreach ($field in @(
        'flywayVersion', 'tableCount', 'achievementCount', 'userCount', 'auditCount',
        'outboxCount', 'projectionStateCount')) {
        if ([string]$restored[$field] -ne [string]$metadata.$field) {
            throw "隔离恢复计数不一致：$field，期望 $($metadata.$field)，实际 $($restored[$field])。"
        }
    }
    if ([long]$restored.userCount -lt 1) {
        throw '恢复数据库没有用户，无法执行真实认证和图重建验收。'
    }
    if ([long]$restored.achievementCount -lt 1) {
        throw '恢复数据库没有成果，无法执行业务实体抽样和图重建验收。'
    }

    $quiesceSql = @'
UPDATE data_source SET enabled = FALSE WHERE enabled = TRUE;
UPDATE crawl_task SET enabled = FALSE WHERE enabled = TRUE;
UPDATE crawl_schedule SET enabled = FALSE WHERE enabled = TRUE;
'@
    & $mysql.Source --host=127.0.0.1 --port=23306 --user=$($databaseCredential.UserName) `
        --execute=$quiesceSql aacv_stage8_recovery
    if ($LASTEXITCODE -ne 0) { throw '隔离恢复数据库外部采集静默化失败。' }

    $env:AACV_DB_URL = 'jdbc:mysql://127.0.0.1:23306/aacv_stage8_recovery?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true'
    $env:AACV_DB_USERNAME = $databaseCredential.UserName
    $env:AACV_DB_PASSWORD = $databasePassword
    $env:AACV_NEO4J_URI = 'bolt://127.0.0.1:27687'
    $env:AACV_NEO4J_USERNAME = $neo4jCredential.UserName
    $env:AACV_NEO4J_PASSWORD = $neo4jPassword
    $env:AACV_SERVER_PORT = [string]$BaseUri.Port
    $env:AACV_EXPORT_ROOT = Join-Path $env:TEMP 'aacv-stage8-recovery-exports'
    $env:AACV_OPERATIONS_ALERTS_ENABLED = 'false'

    $backend = Start-Process -FilePath (Join-Path $workspace 'mvnw.cmd') `
        -ArgumentList @('-f', (Join-Path $workspace 'backend\pom.xml'), 'spring-boot:run') `
        -WorkingDirectory $workspace -WindowStyle Hidden -PassThru
    Wait-Stage8Http ([uri]::new($BaseUri, '/actuator/health/liveness')) 240
    Wait-Stage8Http ([uri]::new($BaseUri, '/actuator/health/readiness')) 240
    Wait-Stage8Http ([uri]::new($BaseUri, '/actuator/health/graph')) 240
    if ($backend.HasExited) { throw '阶段8隔离恢复后端在启动后退出。' }

    $csrfResponse = Invoke-Stage8WebRequest -Uri ([uri]::new($BaseUri, '/api/v1/auth/csrf')) `
        -WebSession $script:webSession -Method Get -TimeoutSeconds 15
    if ([int]$csrfResponse.StatusCode -ne 200) { throw '隔离恢复环境无法获取CSRF令牌。' }
    $script:csrf = $csrfResponse.Content | ConvertFrom-Json
    try {
        Invoke-Stage8RecoveryApi '/api/v1/auth/login' POST `
            @{ username = $adminCredential.UserName; password = $adminPassword } @(200) | Out-Null
    } finally {
        $adminPassword = $null
    }

    $catalogPage = Invoke-Stage8RecoveryApi '/api/v1/catalog/achievements?page=0&size=1'
    $sample = @($catalogPage.items) | Select-Object -First 1
    if ($null -eq $sample -or [long]$sample.id -le 0) {
        throw '隔离恢复成果目录没有返回可验证的抽样实体。'
    }
    $sampleDetail = Invoke-Stage8RecoveryApi "/api/v1/catalog/achievements/$($sample.id)"
    if ([long]$sampleDetail.id -ne [long]$sample.id) {
        throw '隔离恢复成果目录与详情的抽样实体不一致。'
    }

    $deadline = [DateTimeOffset]::UtcNow.AddMinutes($RecoveryTimeoutMinutes)
    $rebuild = Invoke-Stage8RecoveryApi '/api/v1/operations/graph-maintenance/rebuild' POST `
        @{ confirmation = 'REBUILD_AACV_MANAGED_GRAPH' } @(202)
    $rebuild = Wait-Stage8MaintenanceRun -RunId ([long]$rebuild.id) -Deadline $deadline
    if ($rebuild.status -ne 'SUCCEEDED') {
        throw "隔离恢复图重建失败，错误码：$($rebuild.errorCode)。"
    }

    do {
        Start-Sleep -Seconds 2
        $syncStatus = Invoke-Stage8RecoveryApi '/api/v1/graph/sync-status'
        if ([long]$syncStatus.deadCount -gt 0) {
            throw '隔离恢复图重建产生死信事件。'
        }
        $syncCompleted = [long]$syncStatus.pendingCount -eq 0 -and
            [long]$syncStatus.processingCount -eq 0 -and -not [bool]$syncStatus.rebuildInProgress
    } while (-not $syncCompleted -and [DateTimeOffset]::UtcNow -lt $deadline)
    if (-not $syncCompleted) { throw '等待隔离恢复图Outbox完成超时。' }

    $reconcile = Invoke-Stage8RecoveryApi '/api/v1/operations/graph-maintenance/reconcile' POST $null @(202)
    $reconcile = Wait-Stage8MaintenanceRun -RunId ([long]$reconcile.id) -Deadline $deadline
    if ($reconcile.status -ne 'SUCCEEDED' -or [long]$reconcile.differenceCount -ne 0) {
        throw "隔离恢复图对账未通过：status=$($reconcile.status)，differences=$($reconcile.differenceCount)。"
    }

    $graphCounts = Invoke-Stage8Neo4jStatement -Statement @'
MATCH (n {aacvManaged: true})
WITH count(n) AS nodes
OPTIONAL MATCH ()-[r {aacvManaged: true}]->()
RETURN nodes, count(r) AS relationships
'@ -Parameters @{} -Credential $neo4jCredential `
        -Endpoint 'http://127.0.0.1:27474/db/neo4j/tx/commit'
    $graphRow = $graphCounts.results[0].data[0].row
    if ([long]$restored.achievementCount -gt 0 -and [long]$graphRow[0] -eq 0) {
        throw '恢复库存在成果，但独立Neo4j重建后没有受管节点。'
    }

    $completedAt = [DateTimeOffset]::Now
    $backupCreatedAt = [DateTimeOffset]::Parse([string]$metadata.createdAt)
    $backupAgeHours = ($startedAt - $backupCreatedAt).TotalHours
    $rpoPassed = $backupAgeHours -ge 0 -and $backupAgeHours -le 24
    $rtoPassed = ($completedAt - $startedAt).TotalHours -le 4
    $evidence = [ordered]@{
        startedAt = $startedAt.ToString('o')
        completedAt = $completedAt.ToString('o')
        durationSeconds = [Math]::Round(($completedAt - $startedAt).TotalSeconds, 3)
        backup = [ordered]@{
            path = $backupPathFull
            sha256 = $actualHash
            verified = $true
        }
        mysql = [ordered]@{
            sourceDatabase = $metadata.database
            recoveryDatabase = 'aacv_stage8_recovery'
            sourceServerVersion = $metadata.serverVersion
            recoveryServerVersion = $restored.serverVersion
            flywayVersion = $restored.flywayVersion
            tableCount = [long]$restored.tableCount
            achievementCount = [long]$restored.achievementCount
            userCount = [long]$restored.userCount
            auditCount = [long]$restored.auditCount
            outboxCount = [long]$restored.outboxCount
            projectionStateCount = [long]$restored.projectionStateCount
            countsMatchedBackup = $true
            sampledAchievementVerified = $true
        }
        graph = [ordered]@{
            rebuildRunId = [long]$rebuild.id
            rebuildScannedCount = [long]$rebuild.scannedCount
            reconcileRunId = [long]$reconcile.id
            reconcileDifferenceCount = [long]$reconcile.differenceCount
            managedNodes = [long]$graphRow[0]
            managedRelationships = [long]$graphRow[1]
            pendingCount = [long]$syncStatus.pendingCount
            processingCount = [long]$syncStatus.processingCount
            deadCount = [long]$syncStatus.deadCount
        }
        externalSourcesDisabledInRecoveryCopy = $true
        recoveryAssetsDeleted = $false
        rpoTargetHours = 24
        backupAgeHoursAtRecoveryStart = [Math]::Round($backupAgeHours, 3)
        rpoPassed = $rpoPassed
        rtoTargetHours = 4
        rtoPassed = $rtoPassed
        passed = $rpoPassed -and $rtoPassed
    }
    $json = $evidence | ConvertTo-Json -Depth 8
    if ($OutputPath) {
        $parent = Split-Path -Parent $OutputPath
        if (-not $parent -or -not (Test-Path -LiteralPath $parent -PathType Container)) {
            throw '恢复证据输出目录必须预先存在。'
        }
        [IO.File]::WriteAllText([IO.Path]::GetFullPath($OutputPath), $json + [Environment]::NewLine,
            [Text.UTF8Encoding]::new($false))
    }
    $json
    if (-not $evidence.passed) { throw '隔离恢复未同时满足24小时RPO和4小时RTO目标。' }
} finally {
    $listener = Get-NetTCPConnection -LocalAddress 127.0.0.1 -LocalPort $BaseUri.Port `
        -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -ne $listener -and $null -ne $backend -and
        (Test-Stage8DescendantProcess -ChildProcessId $listener.OwningProcess `
            -AncestorProcessId $backend.Id)) {
        Stop-Process -Id $listener.OwningProcess -ErrorAction SilentlyContinue
        Wait-Process -Id $listener.OwningProcess -Timeout 30 -ErrorAction SilentlyContinue
    }
    if ($null -ne $backend -and -not $backend.HasExited) {
        Stop-Process -Id $backend.Id -ErrorAction SilentlyContinue
    }
    if ($containersStarted) {
        docker compose -f $composeFile stop --timeout 60 2>$null | Out-Null
    }
    foreach ($name in @(
        'MYSQL_PWD', 'AACV_STAGE8_RECOVERY_DB_USERNAME', 'AACV_STAGE8_RECOVERY_DB_PASSWORD',
        'AACV_STAGE8_RECOVERY_DB_ROOT_PASSWORD', 'AACV_STAGE8_RECOVERY_NEO4J_USERNAME',
        'AACV_STAGE8_RECOVERY_NEO4J_PASSWORD', 'AACV_DB_URL', 'AACV_DB_USERNAME',
        'AACV_DB_PASSWORD', 'AACV_NEO4J_URI', 'AACV_NEO4J_USERNAME', 'AACV_NEO4J_PASSWORD',
        'AACV_SERVER_PORT', 'AACV_EXPORT_ROOT', 'AACV_OPERATIONS_ALERTS_ENABLED')) {
        Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
    }
    $databasePassword = $null
    $databaseRootPassword = $null
    $neo4jPassword = $null
    $adminPassword = $null
    $script:csrf = $null
    $script:webSession = $null
}
