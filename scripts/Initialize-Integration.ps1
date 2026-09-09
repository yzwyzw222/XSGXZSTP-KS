param()
. (Join-Path $PSScriptRoot 'Integration.Common.ps1')

$runtimeRoot = Join-Path $script:IntegrationRoot '.local/integration-runtime'
$existingContainers = @(& docker ps -aq --filter 'label=com.docker.compose.project=course-integration')
if ($LASTEXITCODE -ne 0) { throw '无法访问 Docker，请先启动 Docker Desktop Linux Engine。' }
foreach ($containerId in $existingContainers) {
    $owner = & docker inspect --format '{{index .Config.Labels "com.docker.compose.project.working_dir"}}' $containerId
    if ($LASTEXITCODE -ne 0 -or [IO.Path]::GetFullPath($owner).TrimEnd('\', '/') -ne $runtimeRoot.TrimEnd('\', '/')) {
        throw '发现其他工作区拥有的 course-integration 容器，拒绝复用或修改。'
    }
}
New-Item -ItemType Directory -Path $runtimeRoot -Force | Out-Null
# 随机凭据只保存在忽略目录，限制为当前 Windows 用户读取，不输出到终端。
$identity = [Security.Principal.WindowsIdentity]::GetCurrent().User
$existingAcl = Get-Acl -LiteralPath $runtimeRoot
$rules = @($existingAcl.GetAccessRules($true, $true, [Security.Principal.SecurityIdentifier]))
$privateAccess = $existingAcl.AreAccessRulesProtected -and $rules.Count -eq 1 -and
    $rules[0].IdentityReference -eq $identity -and $rules[0].FileSystemRights -eq 'FullControl' -and
    $rules[0].AccessControlType -eq 'Allow'
if (-not $privateAccess) {
    $acl = [Security.AccessControl.DirectorySecurity]::new()
    $acl.SetAccessRuleProtection($true, $false)
    $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($identity, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow'))
    Set-Acl -LiteralPath $runtimeRoot -AclObject $acl
}

function New-IntegrationSecret {
    return [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24)).ToLowerInvariant()
}

$systems = @(
    @{ id='relation'; port=18081; db=23361; bolt=27681; http=27471 },
    @{ id='extraction'; port=18082; db=23362; bolt=27682; http=27472 },
    @{ id='crawler'; port=18083; db=23363; bolt=27683; http=27473 },
    @{ id='scholar'; port=18084; db=23364; bolt=0; http=0 }
)
$services = [ordered]@{}
$volumes = [ordered]@{}
foreach ($system in $systems) {
    $id = $system.id
    $directory = Join-Path $runtimeRoot $id
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $directory 'tmp'),(Join-Path $directory 'exports') -Force | Out-Null
    $credentialPath = Join-Path $directory 'credentials.json'
    if (-not (Test-Path -LiteralPath $credentialPath)) {
        $credentials = @{ database=New-IntegrationSecret; root=New-IntegrationSecret; graph=New-IntegrationSecret; admin=New-IntegrationSecret }
        [IO.File]::WriteAllText($credentialPath, ($credentials | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
    }
    $credentials = Get-Content -LiteralPath $credentialPath -Raw | ConvertFrom-Json
    $mysqlEnv = "MYSQL_DATABASE=course_${id}`nMYSQL_USER=course_${id}`nMYSQL_PASSWORD=$($credentials.database)`nMYSQL_ROOT_PASSWORD=$($credentials.root)`n"
    [IO.File]::WriteAllText((Join-Path $directory 'mysql.env'), $mysqlEnv, [Text.UTF8Encoding]::new($false))
    $services["$id-mysql"] = @{
        image='mysql:8.0.42'; env_file=@("./$id/mysql.env"); ports=@("127.0.0.1:$($system.db):3306")
        volumes=@("${id}-mysql:/var/lib/mysql"); restart='no'
        healthcheck=@{ test=@('CMD','mysqladmin','ping','-h','127.0.0.1','--silent'); interval='3s'; timeout='3s'; retries=50; start_period='20s' }
    }
    $volumes["$id-mysql"] = @{}
    $properties = @(
        "spring.profiles.active=integration"
        "server.address=127.0.0.1"
        "server.port=$($system.port)"
        "server.servlet.context-path=/$id"
        "server.servlet.session.cookie.name=$($id.ToUpperInvariant())_SESSION"
        "server.servlet.session.cookie.path=/$id"
        'server.servlet.session.cookie.http-only=true'
        'server.servlet.session.cookie.same-site=lax'
        "spring.datasource.url=jdbc:mysql://127.0.0.1:$($system.db)/course_${id}?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false"
        "spring.datasource.username=course_$id"
        "spring.datasource.password=$($credentials.database)"
        'spring.datasource.hikari.maximum-pool-size=5'
        'spring.datasource.hikari.connection-timeout=5000'
        'integration.bootstrap-admin.enabled=false'
        "app.admin.password=$($credentials.admin)"
        'spring.jpa.show-sql=false'
        'logging.level.org.hibernate.SQL=INFO'
        'logging.level.com.example=INFO'
        'logging.level.com.aacv=INFO'
    )
    if ($system.bolt) {
        [IO.File]::WriteAllText((Join-Path $directory 'neo4j.env'), "NEO4J_AUTH=neo4j/$($credentials.graph)`n", [Text.UTF8Encoding]::new($false))
        $services["$id-neo4j"] = @{
            image='neo4j:5.26-community'; env_file=@("./$id/neo4j.env")
            ports=@("127.0.0.1:$($system.bolt):7687", "127.0.0.1:$($system.http):7474")
            volumes=@("${id}-neo4j:/data"); restart='no'
            environment=@{ NEO4J_server_memory_heap_initial__size='128m'; NEO4J_server_memory_heap_max__size='256m'; NEO4J_server_memory_pagecache_size='128m' }
            healthcheck=@{ test=@('CMD-SHELL','wget -q -O /dev/null http://localhost:7474/ || exit 1'); interval='5s'; timeout='5s'; retries=40; start_period='20s' }
        }
        $volumes["$id-neo4j"] = @{}
        $properties += @("spring.neo4j.uri=bolt://127.0.0.1:$($system.bolt)", 'spring.neo4j.authentication.username=neo4j', "spring.neo4j.authentication.password=$($credentials.graph)", 'spring.neo4j.connection-timeout=5s', 'spring.neo4j.max-transaction-retry-time=5s')
    }
    if ($id -eq 'crawler') {
        # 初次启动只建表；统一账号由开发者在 crawler 数据库中手动添加。
        $properties += @('aacv.bootstrap-admin.enabled=false', 'aacv.bootstrap-admin.username=admin', "aacv.export.root-directory=$($directory.Replace('\','/'))/exports", 'management.endpoint.health.group.readiness.include=readinessState,db,neo4j,graphSchema')
    }
    # 配置作为外部文件读取，进程参数和 PID 记录不含凭据。
    $applicationPath = Join-Path $directory 'application.properties'
    if (-not (Test-Path -LiteralPath $applicationPath)) {
        [IO.File]::WriteAllText($applicationPath, (($properties -join "`n") + "`n"), [Text.UTF8Encoding]::new($false))
    }
}
$composePath = Join-Path $runtimeRoot 'compose.json'
[IO.File]::WriteAllText($composePath, (@{name='course-integration';services=$services;volumes=$volumes} | ConvertTo-Json -Depth 10), [Text.UTF8Encoding]::new($false))
& docker compose -f $composePath up -d --wait --wait-timeout 240
if ($LASTEXITCODE -ne 0) { throw '隔离基础服务未就绪，请检查 course-integration 项目容器状态。' }
Write-Output '四个独立 MySQL 与三个 Neo4j 已就绪。本机配置位于 .local/integration-runtime；新环境不会自动创建用户，请在后端首次启动建表后，按 README 在 crawler 数据库手动添加 admin。已有配置和账号保持不变。'
