[CmdletBinding()]
param(
    [string]$WorkspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [ValidateSet('127.0.0.1', 'localhost')]
    [string]$DatabaseHost = '127.0.0.1',
    [ValidateRange(1, 65535)]
    [int]$DatabasePort = 3306,
    [ValidatePattern('^aacv_system$')]
    [string]$DatabaseName = 'aacv_system',
    [ValidatePattern('^[A-Za-z0-9_.-]+$')]
    [string]$DatabaseUsername = 'aacv'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function ConvertTo-RenderingSamplePlainText {
    param([Parameter(Mandatory = $true)][Security.SecureString]$SecureString)

    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureString)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

$workspace = (Resolve-Path -LiteralPath $WorkspaceRoot).Path
$sqlPath = Join-Path $workspace 'tools\development\rendering-sample-data.sql'
$requiredFiles = @(
    (Join-Path $workspace 'backend\pom.xml'),
    (Join-Path $workspace 'backend\src\main\resources\db\migration\V11__create_alert_event_schema.sql'),
    $sqlPath
)
foreach ($requiredFile in $requiredFiles) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "页面测试数据工具缺少必要文件：$requiredFile"
    }
}

$mysql = Get-Command mysql.exe -ErrorAction Stop
$databaseCredential = Get-Credential -Message '输入本机 AACV 开发数据库账号' -UserName $DatabaseUsername
if ($null -eq $databaseCredential -or $databaseCredential.UserName -cne $DatabaseUsername) {
    throw "数据库用户名必须为 $DatabaseUsername。"
}

$databasePassword = ConvertTo-RenderingSamplePlainText -SecureString $databaseCredential.Password
if ([string]::IsNullOrWhiteSpace($databasePassword)) {
    throw '数据库密码不能为空。'
}

$previousOutputEncoding = $OutputEncoding
try {
    $OutputEncoding = [Text.UTF8Encoding]::new($false)
    $env:MYSQL_PWD = $databasePassword
    $connectionArguments = @(
        '--protocol=TCP',
        "--host=$DatabaseHost",
        "--port=$DatabasePort",
        "--user=$DatabaseUsername",
        '--connect-timeout=5',
        '--default-character-set=utf8mb4',
        '--batch',
        '--raw'
    )

    $selectedDatabase = & $mysql.Source @connectionArguments --skip-column-names `
        --execute='SELECT DATABASE()' $DatabaseName
    if ($LASTEXITCODE -ne 0 -or $selectedDatabase.Trim() -cne $DatabaseName) {
        throw '数据库身份或本机开发库名称校验失败，未写入页面测试数据。'
    }

    $migrationState = & $mysql.Source @connectionArguments --skip-column-names `
        --execute="SELECT CONCAT(COUNT(*), '|', COALESCE(MAX(CAST(version AS UNSIGNED)), 0)) FROM flyway_schema_history WHERE success = 1 AND version REGEXP '^[0-9]+$'" `
        $DatabaseName
    if ($LASTEXITCODE -ne 0 -or $migrationState.Trim() -cne '11|11') {
        throw "开发库必须完整应用且仅应用 Flyway V1 至 V11；当前状态为 $($migrationState.Trim())。"
    }

    $actorId = & $mysql.Source @connectionArguments --skip-column-names `
        --execute="SELECT MIN(user_value.id) FROM sys_user user_value JOIN sys_user_role user_role ON user_role.user_id = user_value.id JOIN sys_role role_value ON role_value.id = user_role.role_id WHERE user_value.status = 'ACTIVE' AND role_value.role_code = 'ADMIN'" `
        $DatabaseName
    $parsedActorId = 0L
    if ($LASTEXITCODE -ne 0 -or -not [long]::TryParse($actorId.Trim(), [ref]$parsedActorId)) {
        throw '开发库中不存在有效管理员账号；请先按 README 完成一次性初始管理员引导。'
    }

    $sql = Get-Content -LiteralPath $sqlPath -Raw -Encoding UTF8
    $sessionSql = "SET @aacv_demo_actor_id = $parsedActorId;`n$sql"
    $result = $sessionSql | & $mysql.Source @connectionArguments $DatabaseName
    if ($LASTEXITCODE -ne 0) {
        throw '页面测试数据事务执行失败；连接已关闭，未提交的事务会自动回滚。'
    }

    Write-Output $result
    Write-Host '页面测试数据已写入。启动 Neo4j 与后端后，Outbox 会自动投影样例图数据。'
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    $OutputEncoding = $previousOutputEncoding
    $databasePassword = $null
    $sql = $null
    $sessionSql = $null
}
