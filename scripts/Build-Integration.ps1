param([switch]$Restore)
. (Join-Path $PSScriptRoot 'Integration.Common.ps1')

foreach ($record in @(Read-IntegrationState)) {
    if ($record.system -ne 'portal' -and (Test-IntegrationIdentity $record)) {
        throw '请先执行 scripts/Stop-Integration.ps1，避免 Windows 锁定正在运行的 jar。'
    }
}

$builds = @(
    @{ id='relation'; frontend='systems/relation/frontend'; backend='systems/relation/backend'; wrapper='systems/relation/backend/mvnw.cmd'; pom='pom.xml' },
    @{ id='extraction'; frontend='systems/extraction/frontend'; backend='systems/extraction'; wrapper='systems/extraction/mvnw.cmd'; pom='pom.xml' },
    @{ id='crawler'; frontend='systems/crawler/frontend'; backend='systems/crawler'; wrapper='systems/crawler/mvnw.cmd'; pom='backend/pom.xml' },
    @{ id='scholar'; frontend='systems/scholar/web'; backend='systems/scholar/web/backend'; wrapper='systems/scholar/web/backend/mvnw.cmd'; pom='pom.xml' }
)
$logDirectory = Join-Path $script:IntegrationRoot '.local/integration-build'
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
foreach ($build in $builds) {
    if ($Restore) {
        & npm.cmd --prefix (Join-Path $script:IntegrationRoot $build.frontend) ci --no-audit --no-fund --offline=false --registry=https://registry.npmjs.org --cache (Join-Path $script:IntegrationRoot '.local/npm-cache')
        if ($LASTEXITCODE -ne 0) { throw "$($build.id) 依赖恢复失败。" }
    }
    Write-Output "构建 $($build.id) 前端。"
    & npm.cmd --prefix (Join-Path $script:IntegrationRoot $build.frontend) run build -- "--base=/$($build.id)/" *> (Join-Path $logDirectory "$($build.id)-frontend.log")
    if ($LASTEXITCODE -ne 0) { throw "$($build.id) 前端构建失败，见 .local/integration-build。" }
    Write-Output "打包 $($build.id) 后端；业务测试由验收步骤单独执行。"
    Push-Location (Join-Path $script:IntegrationRoot $build.backend)
    try {
        & (Join-Path $script:IntegrationRoot $build.wrapper) -B -f $build.pom '-DskipTests' package *> (Join-Path $logDirectory "$($build.id)-backend.log")
        if ($LASTEXITCODE -ne 0) { throw "$($build.id) 后端打包失败，见 .local/integration-build。" }
    } finally { Pop-Location }
}
if ($Restore) {
    & npm.cmd --prefix (Join-Path $script:IntegrationRoot 'portal') ci --no-audit --no-fund --offline=false --registry=https://registry.npmjs.org --cache (Join-Path $script:IntegrationRoot '.local/npm-cache')
    if ($LASTEXITCODE -ne 0) { throw '门户依赖恢复失败。' }
}
& npm.cmd --prefix (Join-Path $script:IntegrationRoot 'portal') run build
if ($LASTEXITCODE -ne 0) { throw '门户构建失败。' }
Write-Output '四套子系统与门户构建完成。'
