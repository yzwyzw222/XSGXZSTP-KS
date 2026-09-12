. (Join-Path $PSScriptRoot 'Integration.Common.ps1')

function Assert-Condition {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Assert-Fails {
    param([scriptblock]$Action, [string]$Expected)
    $failed = $false
    try { & $Action | Out-Null } catch {
        $failed = $true
        Assert-Condition ($_.Exception.Message -match $Expected) "失败原因与预期不符：$($_.Exception.Message)"
    }
    Assert-Condition $failed '预期拒绝操作，但操作成功。'
}

$plan = Get-IntegrationPlan 'portal'
$existing = @(Read-IntegrationState)
Assert-Condition (-not ($existing | Where-Object { Get-Process -Id $_.pid -ErrorAction SilentlyContinue })) '请先停止本仓库已启动的组件再运行验收。'
$sentinelPath = Join-Path $script:IntegrationStateDirectory 'data-preservation-check.txt'
New-Item -ItemType Directory -Path $script:IntegrationStateDirectory -Force | Out-Null
if (Test-Path -LiteralPath $sentinelPath) { throw '验收标记已存在，拒绝覆盖。' }
[IO.File]::WriteAllText($sentinelPath, 'integration-data-preservation', [Text.UTF8Encoding]::new($false))
$occupied = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $plan.portalPort)
$occupied.Server.ExclusiveAddressUse = $true
try {
    $occupied.Start()
    Assert-Fails { & (Join-Path $PSScriptRoot 'Start-Integration.ps1') } '端口'
    Assert-Condition $occupied.Server.IsBound '占用端口的测试服务受到影响。'
} finally { $occupied.Stop() }
try {
    & (Join-Path $PSScriptRoot 'Start-Integration.ps1') -System portal -Mode Demo
    $records = @(Read-IntegrationState)
    Assert-Condition ($records.Count -eq 1 -and $records[0].system -eq 'portal') '单独启动系统网关时启动了无关进程。'
    $portalRecord = $records[0]
    Assert-Fails { & (Join-Path $PSScriptRoot 'Start-Integration.ps1') -System all } '已经运行'
    & (Join-Path $PSScriptRoot 'Stop-Integration.ps1') -System crawler
    Assert-Condition (Test-IntegrationIdentity $portalRecord) '停止未启动的系统影响了系统网关。'
    $tampered = $portalRecord | ConvertTo-Json | ConvertFrom-Json
    $tampered.createdTicks = '0'
    Save-IntegrationState @($tampered)
    Assert-Fails { & (Join-Path $PSScriptRoot 'Stop-Integration.ps1') -System all } '不匹配'
    Assert-Condition (Test-IntegrationIdentity $portalRecord) '身份不匹配时仍停止了进程。'
    Save-IntegrationState @($portalRecord)
    & (Join-Path $PSScriptRoot 'Stop-Integration.ps1') -System all
    Assert-IntegrationPort $plan.portalPort
    Assert-Condition ((Get-Content -LiteralPath $sentinelPath -Raw) -eq 'integration-data-preservation') '停止操作修改了运行数据。'
    & (Join-Path $PSScriptRoot 'Stop-Integration.ps1') -System all
    & (Join-Path $PSScriptRoot 'Start-Integration.ps1') -System portal -Mode Development
    Assert-Condition (@(Read-IntegrationState).Count -eq 1) '开发模式单独启动系统网关时启动了后端。'
    & (Join-Path $PSScriptRoot 'Stop-Integration.ps1') -System all
    Write-Output '生命周期验收通过：端口占用、重复启动、按系统停止、身份拒绝、数据保留、重复停止、开发模式。'
} finally {
    if ($null -ne (Get-Variable portalRecord -ErrorAction SilentlyContinue) -and (Test-IntegrationIdentity $portalRecord)) {
        Save-IntegrationState @($portalRecord)
    }
    & (Join-Path $PSScriptRoot 'Stop-Integration.ps1') -System all
    # 只删除本测试创建的单个标记，不清理运行目录或业务数据。
    if (Test-Path -LiteralPath $sentinelPath) { Remove-Item -LiteralPath $sentinelPath }
}
