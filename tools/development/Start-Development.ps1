[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Neo4j', 'Backend', 'Frontend')]
    [string]$Component
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

function Assert-FreePort([int]$Port) {
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $connection = $client.ConnectAsync('127.0.0.1', $Port)
        try { $connected = $connection.Wait(1000) -and $client.Connected }
        catch { $connected = $false }
        if ($connected) { throw "端口${Port}已占用，未重复启动。请使用已有终端或核对占用进程。" }
    } finally { $client.Dispose() }
}

Push-Location $workspace
try {
    if ($Component -ne 'Frontend' -and -not (Test-Path -LiteralPath '.env' -PathType Leaf)) {
        throw '.env不存在，请先从.env.example复制并在本机填写；脚本不会创建或覆盖凭据。'
    }
    switch ($Component) {
        'Neo4j' {
            & docker compose --env-file .\.env -f .\deploy\compose.yaml config --quiet
            if ($LASTEXITCODE -ne 0) { throw 'Compose校验失败，请检查本地配置。' }
            & docker compose --env-file .\.env -f .\deploy\compose.yaml up -d neo4j
            if ($LASTEXITCODE -ne 0) { throw 'Neo4j启动失败，请检查Docker Desktop状态。' }
            & docker compose --env-file .\.env -f .\deploy\compose.yaml ps
            if ($LASTEXITCODE -ne 0) { throw '无法读取Neo4j运行状态。' }
        }
        'Backend' {
            Assert-FreePort 8080
            $socketDirectory = Join-Path $workspace '.local\java-sockets'
            if ([System.Text.Encoding]::UTF8.GetByteCount($socketDirectory) -gt 80) {
                throw '项目路径过长，无法安全生成Windows本地Socket地址。请使用较短的开发目录。'
            }
            [void][System.IO.Directory]::CreateDirectory($socketDirectory)
            # 只为当前应用JVM指定Socket目录，不修改系统环境或默认SelectorProvider。
            $jvmArguments = '-Dspring-boot.run.jvmArguments="-Djdk.net.unixdomain.tmpdir=' + $socketDirectory + '"'
            Write-Host '启动后端，当前终端按Ctrl+C停止。默认地址：http://127.0.0.1:8080'
            & .\mvnw.cmd -f .\backend\pom.xml $jvmArguments spring-boot:run
            if ($LASTEXITCODE -ne 0) { throw '后端未正常退出，请检查数据库、配置或启动错误。' }
        }
        'Frontend' {
            Assert-FreePort 5173
            if (-not (Test-Path -LiteralPath 'frontend/node_modules/vite/package.json')) {
                throw '前端依赖不存在，请先执行npm --prefix .\frontend ci。'
            }
            Write-Host '启动前端，当前终端按Ctrl+C停止。访问：http://127.0.0.1:5173/login'
            & npm.cmd --prefix .\frontend run dev
            if ($LASTEXITCODE -ne 0) { throw '前端未正常退出，请检查终端中的错误。' }
        }
    }
} finally { Pop-Location }
