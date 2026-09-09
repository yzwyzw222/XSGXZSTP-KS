. (Join-Path $PSScriptRoot 'Integration.Common.ps1')
$configuration = Get-Content -LiteralPath (Join-Path $script:IntegrationRoot 'deploy/systems.json') -Raw | ConvertFrom-Json
$base = "http://127.0.0.1:$($configuration.portalPort)"
foreach ($system in @($configuration.systems | Where-Object status -eq 'enabled')) {
    $stopped = $false
    try {
        & (Join-Path $PSScriptRoot 'Stop-Integration.ps1') -System $system.id
        $stopped = $true
        $response = Invoke-WebRequest "$base/$($system.id)/api/v1/auth/csrf" -SkipHttpErrorCheck -TimeoutSec 10
        if ($response.StatusCode -ne 503 -or ($response.Content | ConvertFrom-Json).code -ne 'BACKEND_UNAVAILABLE') {
            throw "$($system.id) 后端停止时未返回明确的 503。"
        }
        $portal = Invoke-WebRequest "$base/__integration/health" -TimeoutSec 5
        if ($portal.StatusCode -ne 200) { throw '单系统停止影响了门户。' }
        foreach ($other in @($configuration.systems | Where-Object { $_.status -eq 'enabled' -and $_.id -ne $system.id })) {
            $health = Invoke-RestMethod "$base$($other.runtime.readinessPath)" -TimeoutSec 10
            if ($health.status -ne 'UP') { throw "停止 $($system.id) 影响了 $($other.id)。" }
        }
        & (Join-Path $PSScriptRoot 'Start-Integration.ps1') -System $system.id -Mode Demo
        $stopped = $false
        Write-Output "$($system.id)：停止降级、其他服务隔离和恢复通过。"
    } finally {
        if ($stopped) { & (Join-Path $PSScriptRoot 'Start-Integration.ps1') -System $system.id -Mode Demo }
    }
}
