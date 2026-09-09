param(
    [ValidateSet('all', 'portal', 'relation', 'extraction', 'crawler')][string]$System = 'all',
    [ValidateSet('Development', 'Demo')][string]$Mode = 'Demo'
)
. (Join-Path $PSScriptRoot 'Integration.Common.ps1')

$plan = Get-IntegrationPlan $System $Mode
if (-not (Test-Path -LiteralPath (Join-Path $script:IntegrationRoot 'portal/node_modules/vite/package.json'))) {
    throw '门户依赖尚未安装，请执行 npm --prefix portal ci。'
}
if ($Mode -eq 'Demo' -and -not (Test-Path -LiteralPath (Join-Path $script:IntegrationRoot 'portal/dist/index.html'))) {
    throw '门户尚未构建，请执行 npm --prefix portal run build。'
}
$operationLock = Enter-IntegrationLock
try {
    $records = @(Read-IntegrationState)
    foreach ($record in $records) {
        if (Test-IntegrationIdentity $record) { throw "当前整合环境已经运行：$($record.id)。请先停止所属组件，再重新启动。" }
        if (Get-Process -Id $record.pid -ErrorAction SilentlyContinue) { throw '进程记录与现有进程不一致，拒绝覆盖记录。' }
    }
    $records = @()
    Assert-IntegrationPort $plan.portalPort
    $portal = Start-IntegrationProcess -Id 'portal' -System 'portal' -Executable 'node' `
        -Arguments @((Join-Path $script:IntegrationRoot 'portal/server.mjs'), $Mode) `
        -WorkingDirectory (Join-Path $script:IntegrationRoot 'portal') -Port $plan.portalPort
    $records += $portal
    Save-IntegrationState $records
    Wait-IntegrationReady $portal "http://127.0.0.1:$($plan.portalPort)/__integration/health" $plan.revision
    Write-Output "门户已启动：http://127.0.0.1:$($plan.portalPort)/ （$Mode）"
    foreach ($id in $plan.skipped) { Write-Output "${id}：维护中，未启动。" }
    $failures = @()
    foreach ($component in $plan.processes) {
        try {
            $record = Start-IntegrationProcess -Id $component.id -System $component.system -Executable $component.executable `
                -Arguments $component.args -WorkingDirectory (Join-Path $script:IntegrationRoot $component.cwd) -Port $component.port
            $records += $record
            Save-IntegrationState $records
            Wait-IntegrationReady $record $component.readinessUrl
            Write-Output "$($component.id)：已就绪。"
        } catch { $failures += "$($component.id)：$($_.Exception.Message)" }
    }
    if ($failures.Count -gt 0) { throw ($failures -join [Environment]::NewLine) }
} finally { $operationLock.Dispose() }
