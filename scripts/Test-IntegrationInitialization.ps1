param()
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-Initialization {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

# 在临时工作区执行真实初始化脚本，仅替代 Docker 调用，不接触现有数据库。
function docker {
    $global:LASTEXITCODE = 0
    if ($args[0] -eq 'ps') { return }
    if ($args[0] -eq 'compose' -and $args[3] -eq 'up') { return }
    throw '初始化测试遇到未预期的 Docker 操作。'
}

$temporaryRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\', '/')
$fixtureRoot = Join-Path $temporaryRoot ('course-initialization-' + [guid]::NewGuid().ToString('N'))
try {
    $fixtureScripts = Join-Path $fixtureRoot 'scripts'
    New-Item -ItemType Directory -Path $fixtureScripts -Force | Out-Null
    foreach ($name in @('Initialize-Integration.ps1', 'Integration.Common.ps1')) {
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination (Join-Path $fixtureScripts $name)
    }
    $initialize = Join-Path $fixtureScripts 'Initialize-Integration.ps1'
    & $initialize | Out-Null
    $runtime = Join-Path $fixtureRoot '.local/integration-runtime'
    $before = @{}
    foreach ($id in @('relation', 'extraction', 'crawler', 'scholar')) {
        $application = Join-Path $runtime "$id/application.properties"
        $properties = Get-Content -LiteralPath $application -Raw -Encoding UTF8
        Assert-Initialization ($properties -match '(?m)^integration.bootstrap-admin.enabled=false\r?$') "$id 未默认关闭账号引导。"
        Assert-Initialization ($properties -notmatch '(?m)^integration.admin-password=') "$id 仍写入旧管理员引导密码。"
        $credentials = Join-Path $runtime "$id/credentials.json"
        $before[$application] = (Get-FileHash -LiteralPath $application).Hash
        $before[$credentials] = (Get-FileHash -LiteralPath $credentials).Hash
    }
    $crawlerProperties = Get-Content -LiteralPath (Join-Path $runtime 'crawler/application.properties') -Raw -Encoding UTF8
    Assert-Initialization ($crawlerProperties -match '(?m)^aacv.bootstrap-admin.enabled=false\r?$') 'crawler 仍会自动创建用户。'
    Assert-Initialization ($crawlerProperties -match '(?m)^aacv.bootstrap-admin.username=admin\r?$') '管理员用户名未统一为 admin。'
    Assert-Initialization ($crawlerProperties -notmatch '(?m)^aacv.bootstrap-admin.password=') 'crawler 仍配置自动引导密码。'
    $crawlerCredentials = Get-Content -LiteralPath (Join-Path $runtime 'crawler/credentials.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    Assert-Initialization (-not [string]::IsNullOrWhiteSpace($crawlerCredentials.admin)) '未保留供手动建号使用的本机密码。'

    $existingConfiguration = Join-Path $runtime 'relation/application.properties'
    Add-Content -LiteralPath $existingConfiguration -Value '# 开发者已有配置应在重复初始化时保留。' -Encoding UTF8
    $before[$existingConfiguration] = (Get-FileHash -LiteralPath $existingConfiguration).Hash
    & $initialize | Out-Null
    foreach ($path in $before.Keys) {
        Assert-Initialization ((Get-FileHash -LiteralPath $path).Hash -eq $before[$path]) '重复初始化修改了已有配置或凭据。'
    }
    Write-Output '初始化测试通过：新环境默认关闭建号、保留本机密码、统一 admin、重复初始化保留已有配置和凭据；未调用真实 Docker。'
} finally {
    $resolvedFixture = [IO.Path]::GetFullPath($fixtureRoot)
    if (-not $resolvedFixture.StartsWith($temporaryRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw '临时测试目录越界，拒绝清理。'
    }
    if (Test-Path -LiteralPath $resolvedFixture) { Remove-Item -LiteralPath $resolvedFixture -Recurse -Force }
}
