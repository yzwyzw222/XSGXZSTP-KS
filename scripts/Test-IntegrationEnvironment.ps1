param([ValidateSet('Development', 'Demo')][string]$Mode = 'Demo')
. (Join-Path $PSScriptRoot 'Integration.Common.ps1')

$plan = Get-IntegrationPlan 'all' $Mode
Assert-IntegrationPort $plan.portalPort
foreach ($component in $plan.processes) {
    Get-Command $component.executable -ErrorAction Stop | Out-Null
    Assert-IntegrationPort $component.port
}
if (-not (Test-Path -LiteralPath (Join-Path $script:IntegrationRoot 'portal/node_modules/vite/package.json'))) {
    throw '门户依赖尚未安装，请执行 npm --prefix portal ci。'
}
if ($Mode -eq 'Demo' -and -not (Test-Path -LiteralPath (Join-Path $script:IntegrationRoot 'portal/dist/index.html'))) {
    throw '演示模式需要先执行 npm --prefix portal run build。'
}
Write-Output "环境检查通过：入口端口 $($plan.portalPort) 可绑定；已启用子系统进程数 $(@($plan.processes).Count)。"
foreach ($id in $plan.skipped) { Write-Output "${id}：维护中，不要求 Java、数据库或子系统构建产物。" }
