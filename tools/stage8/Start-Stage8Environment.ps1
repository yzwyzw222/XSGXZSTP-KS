param(
    [string]$WorkspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [switch]$SkipBackend
)

. (Join-Path $PSScriptRoot 'Stage8.Common.ps1')
$workspace = Assert-Stage8Workspace -WorkspaceRoot $WorkspaceRoot
$composeFile = Join-Path $workspace 'deploy\compose.stage8.yaml'

$databaseCredential = Get-Credential -Message '输入阶段8隔离数据库账号' -UserName 'aacv_stage8'
$databaseRootCredential = Get-Credential -Message '设置阶段8隔离数据库root密码' -UserName 'root'
$neo4jCredential = Get-Credential -Message '输入阶段8隔离Neo4j账号' -UserName 'neo4j'
Assert-Stage8Credential $databaseCredential 'aacv_stage8' '阶段8数据库'
Assert-Stage8Credential $databaseRootCredential 'root' '阶段8数据库root'
Assert-Stage8Credential $neo4jCredential 'neo4j' '阶段8 Neo4j'

$databasePassword = ConvertTo-Stage8PlainText $databaseCredential.Password
$databaseRootPassword = ConvertTo-Stage8PlainText $databaseRootCredential.Password
$neo4jPassword = ConvertTo-Stage8PlainText $neo4jCredential.Password
try {
    $env:AACV_STAGE8_DB_USERNAME = $databaseCredential.UserName
    $env:AACV_STAGE8_DB_PASSWORD = $databasePassword
    $env:AACV_STAGE8_DB_ROOT_PASSWORD = $databaseRootPassword
    $env:AACV_STAGE8_NEO4J_USERNAME = $neo4jCredential.UserName
    $env:AACV_STAGE8_NEO4J_PASSWORD = $neo4jPassword

    docker compose -f $composeFile config --quiet
    if ($LASTEXITCODE -ne 0) { throw '阶段8 Compose 配置校验失败。' }
    docker compose -f $composeFile up -d
    if ($LASTEXITCODE -ne 0) { throw '阶段8隔离容器启动失败。' }
    Wait-Stage8ContainerHealthy -ContainerName 'aacv-stage8-mysql'
    Wait-Stage8ContainerHealthy -ContainerName 'aacv-stage8-neo4j'

    if (-not $SkipBackend) {
        $adminCredential = Get-Credential -Message '设置阶段8隔离环境初始管理员' -UserName 'aacv-stage8-admin'
        Assert-Stage8Credential $adminCredential 'aacv-stage8-admin' '阶段8初始管理员'
        $adminPassword = ConvertTo-Stage8PlainText $adminCredential.Password
        try {
            $env:AACV_DB_URL = 'jdbc:mysql://127.0.0.1:13306/aacv_stage8_capacity_20260903?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true'
            $env:AACV_DB_USERNAME = $databaseCredential.UserName
            $env:AACV_DB_PASSWORD = $databasePassword
            $env:AACV_NEO4J_URI = 'bolt://127.0.0.1:17687'
            $env:AACV_NEO4J_USERNAME = $neo4jCredential.UserName
            $env:AACV_NEO4J_PASSWORD = $neo4jPassword
            $env:AACV_SERVER_PORT = '18080'
            $env:AACV_EXPORT_ROOT = Join-Path $env:TEMP 'aacv-stage8-exports'
            $env:AACV_BOOTSTRAP_ADMIN_ENABLED = 'true'
            $env:AACV_BOOTSTRAP_ADMIN_USERNAME = $adminCredential.UserName
            $env:AACV_BOOTSTRAP_ADMIN_PASSWORD = $adminPassword

            $backend = Start-Process -FilePath (Join-Path $workspace 'mvnw.cmd') `
                -ArgumentList @('-f', (Join-Path $workspace 'backend\pom.xml'), 'spring-boot:run') `
                -WorkingDirectory $workspace -WindowStyle Hidden -PassThru
            try {
                Wait-Stage8Http -Uri 'http://127.0.0.1:18080/actuator/health/liveness' -TimeoutSeconds 240
                if ($backend.HasExited) { throw '阶段8后端在迁移或初始化期间退出。' }
                $listener = Get-NetTCPConnection -LocalAddress 127.0.0.1 -LocalPort 18080 `
                    -State Listen -ErrorAction Stop | Select-Object -First 1
                Stop-Process -Id $listener.OwningProcess -ErrorAction Stop
                Wait-Process -Id $listener.OwningProcess -Timeout 30 -ErrorAction SilentlyContinue
                if (-not $backend.HasExited) {
                    Stop-Process -Id $backend.Id -ErrorAction SilentlyContinue
                }
            } catch {
                if (-not $backend.HasExited) { Stop-Process -Id $backend.Id }
                throw
            }
        } finally {
            Remove-Item Env:AACV_BOOTSTRAP_ADMIN_ENABLED -ErrorAction SilentlyContinue
            Remove-Item Env:AACV_BOOTSTRAP_ADMIN_USERNAME -ErrorAction SilentlyContinue
            Remove-Item Env:AACV_BOOTSTRAP_ADMIN_PASSWORD -ErrorAction SilentlyContinue
            $adminPassword = $null
        }

        $backend = Start-Process -FilePath (Join-Path $workspace 'mvnw.cmd') `
            -ArgumentList @('-f', (Join-Path $workspace 'backend\pom.xml'), 'spring-boot:run') `
            -WorkingDirectory $workspace -WindowStyle Hidden -PassThru
        try {
            Wait-Stage8Http -Uri 'http://127.0.0.1:18080/actuator/health/liveness' -TimeoutSeconds 240
            if ($backend.HasExited) { throw '阶段8后端在清除一次性管理员凭据后重启失败。' }
            $listener = Get-NetTCPConnection -LocalAddress 127.0.0.1 -LocalPort 18080 `
                -State Listen -ErrorAction Stop | Select-Object -First 1
            Write-Host "阶段8隔离环境已启动；后端PID=$($listener.OwningProcess)，地址=http://127.0.0.1:18080。"
            Write-Host '一次性管理员环境变量已从重启后的后端进程移除。'
        } catch {
            if (-not $backend.HasExited) { Stop-Process -Id $backend.Id }
            throw
        }
    }
} finally {
    Remove-Item Env:AACV_STAGE8_DB_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:AACV_STAGE8_DB_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:AACV_STAGE8_DB_ROOT_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:AACV_STAGE8_NEO4J_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:AACV_STAGE8_NEO4J_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:AACV_DB_URL -ErrorAction SilentlyContinue
    Remove-Item Env:AACV_DB_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:AACV_DB_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:AACV_NEO4J_URI -ErrorAction SilentlyContinue
    Remove-Item Env:AACV_NEO4J_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:AACV_NEO4J_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:AACV_SERVER_PORT -ErrorAction SilentlyContinue
    Remove-Item Env:AACV_EXPORT_ROOT -ErrorAction SilentlyContinue
    $databasePassword = $null
    $databaseRootPassword = $null
    $neo4jPassword = $null
}
