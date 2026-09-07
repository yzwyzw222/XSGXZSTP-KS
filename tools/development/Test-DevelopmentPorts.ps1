[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Development-Ports.ps1')
$failures = 0
$passed = 0

function Assert-Equal($Actual, $Expected) {
    if ($Actual -ne $Expected) { throw "期望：$Expected；实际：$Actual" }
}

function Assert-Throws([scriptblock]$Action, [string]$Pattern) {
    try { & $Action } catch {
        if ($_.Exception.Message -notlike $Pattern) { throw }
        return
    }
    throw "未出现预期错误：$Pattern"
}

function Invoke-PortTest([string]$Name, [scriptblock]$Action) {
    try {
        & $Action
        $script:passed++
        Write-Host "[通过] $Name"
    } catch {
        $script:failures++
        Write-Host "[失败] ${Name}：$($_.Exception.Message)"
    }
}

Invoke-PortTest '检测可用端口后释放探测 Socket' {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    try {
        $listener.Start()
        $port = $listener.LocalEndpoint.Port
    } finally { $listener.Stop() }
    Assert-Equal (Get-DevelopmentPortStatus $port) 'Available'
    $probe = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $port)
    try {
        $probe.Server.ExclusiveAddressUse = $true
        $probe.Start()
        Assert-Equal $probe.LocalEndpoint.Port $port
    } finally { $probe.Stop() }
}

foreach ($address in @([Net.IPAddress]::Loopback, [Net.IPAddress]::Any)) {
    Invoke-PortTest "识别 $address 上的已有监听，不影响现有服务" {
        $listener = [Net.Sockets.TcpListener]::new($address, 0)
        try {
            $listener.Server.ExclusiveAddressUse = $true
            $listener.Start()
            $port = $listener.LocalEndpoint.Port
            Assert-Equal (Get-DevelopmentPortStatus $port) 'Occupied'
            Assert-Throws { Assert-DevelopmentPortAvailable $port } '*已占用*'
            Assert-Equal $listener.Server.IsBound $true
        } finally { $listener.Stop() }
    }
}

Invoke-PortTest '默认端口可用时保持 5173，不探测备用端口' {
    function Get-DevelopmentPortStatus([int]$Port) {
        if ($Port -ne 5173) { throw '不应探测备用端口。' }
        return 'Available'
    }
    Assert-Equal (Resolve-DevelopmentFrontendPort) 5173
}

Invoke-PortTest '默认端口禁止绑定时选择已验证的 15173，并提示实际地址' {
    function Get-DevelopmentPortStatus([int]$Port) {
        if ($Port -eq 5173) { return 'Denied' }
        if ($Port -eq 15173) { return 'Available' }
        throw '探测了未约定的端口。'
    }
    $result = @(Resolve-DevelopmentFrontendPort 3>&1)
    Assert-Equal $result.Count 2
    Assert-Equal ($result[0] -is [Management.Automation.WarningRecord]) $true
    Assert-Equal ($result[0].Message -like '*http://127.0.0.1:15173/login*') $true
    Assert-Equal $result[1] 15173
}

Invoke-PortTest '默认端口已占用时拒绝重复启动，不改用备用端口' {
    function Get-DevelopmentPortStatus([int]$Port) {
        if ($Port -ne 5173) { throw '不应探测备用端口。' }
        return 'Occupied'
    }
    Assert-Throws { Resolve-DevelopmentFrontendPort } '*5173*已占用*'
}

Invoke-PortTest '默认端口禁止绑定且备用端口已占用时停止' {
    function Get-DevelopmentPortStatus([int]$Port) {
        if ($Port -eq 5173) { return 'Denied' }
        return 'Occupied'
    }
    Assert-Throws { Resolve-DevelopmentFrontendPort } '*15173*已占用*'
}

Invoke-PortTest '两个端口都禁止绑定时给出系统保留端口检查命令' {
    function Get-DevelopmentPortStatus([int]$Port) { return 'Denied' }
    Assert-Throws { Resolve-DevelopmentFrontendPort } '*15173*netsh interface ipv4 show excludedportrange protocol=tcp*'
}

Invoke-PortTest '显式端口检查不绕过绑定权限错误' {
    function Get-DevelopmentPortStatus([int]$Port) { return 'Denied' }
    Assert-Throws { Assert-DevelopmentPortAvailable 5173 } '*系统拒绝绑定*5173*'
}

Invoke-PortTest '端口查询异常向上传递，不静默改用备用端口' {
    function Get-DevelopmentPortStatus([int]$Port) { throw '模拟端口查询失败。' }
    Assert-Throws { Resolve-DevelopmentFrontendPort } '模拟端口查询失败。'
}

Invoke-PortTest '未知端口状态不能被当作可用' {
    function Get-DevelopmentPortStatus([int]$Port) { return 'Unknown' }
    Assert-Throws { Resolve-DevelopmentFrontendPort } '*无法确认*5173*'
    Assert-Throws { Assert-DevelopmentPortAvailable 15173 } '*无法确认*15173*'
}

Invoke-PortTest '拒绝范围外端口' {
    Assert-Throws { Get-DevelopmentPortStatus 0 } '*'
    Assert-Throws { Assert-DevelopmentPortAvailable 65536 } '*'
}

# 仅载入停止目标识别函数，回归验证不执行停止脚本的进程或容器操作。
$workspace = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$tokens = $null
$parseErrors = $null
$stopAst = [Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Stop-All.ps1'), [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count -gt 0) { throw ($parseErrors | Out-String) }
$targetFunction = $stopAst.Find({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Get-ApplicationTargets' }, $true)
if (-not $targetFunction) { throw '未找到停止目标识别函数。' }
. ([scriptblock]::Create($targetFunction.Extent.Text))

foreach ($vitePath in @('vite\bin\vite.js', '.bin\..\vite\bin\vite.js', '.bin\\..\vite\bin\vite.js')) {
    Invoke-PortTest "停止入口识别本项目 $vitePath 在备用端口运行的前端" {
        $process = [pscustomobject]@{
            Name = 'node.exe'; ProcessId = 100; ParentProcessId = 99; CreationDate = [DateTime]::UtcNow
            CommandLine = '"node" "' + $workspace + '\frontend\node_modules\' + $vitePath + '" --host 127.0.0.1 --port 15173 --strictPort'
        }
        $targets = @(Get-ApplicationTargets @($process))
        Assert-Equal $targets.Count 1
        Assert-Equal $targets[0].Component 'Frontend'
        Assert-Equal $targets[0].Process.ProcessId 100
    }
}

Invoke-PortTest '停止入口不识别相似项目或越出预期路径的进程' {
    foreach ($vitePath in @(
        ($workspace + '-other\frontend\node_modules\.bin\\..\vite\bin\vite.js'),
        ($workspace + '\frontend\node_modules\.bin\..\..\vite\bin\vite.js'),
        ($workspace + '\frontend\node_modules\.bin\..\vite\bin\vite.js.bak')
    )) {
        $process = [pscustomobject]@{
            Name = 'node.exe'; ProcessId = 100; ParentProcessId = 99; CreationDate = [DateTime]::UtcNow
            CommandLine = '"node" "' + $vitePath + '" --port 15173'
        }
        Assert-Equal @(Get-ApplicationTargets @($process)).Count 0
    }
}

Write-Host "端口回归验证：$passed 项通过，$failures 项失败。"
if ($failures -gt 0) { exit 1 }
