[CmdletBinding()]
param([switch]$AsJson)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$checks = [System.Collections.Generic.List[object]]::new()

function Add-Check([string]$Name, [string]$Status, [string]$Detail) {
    $checks.Add([pscustomobject]@{ name = $Name; status = $Status; detail = $Detail })
}

function Get-ToolVersion([string]$Name, [string[]]$Arguments) {
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if (-not $command) { return $null }
    try {
        $result = & $command.Source @Arguments 2>$null
        if ($LASTEXITCODE -ne 0) { return $null }
        return ($result | Out-String).Trim()
    } catch { return $null }
}

$java = Get-ToolVersion 'java' @('--version')
if ($java -and $java -match '(?m)^(openjdk|java) 21[. ]') {
    Add-Check 'Java' 'PASS' (($java -split '\r?\n')[0])
} else { Add-Check 'Java' 'FAIL' '需要可通过PATH运行的JDK 21；JAVA_HOME也应指向同一JDK。' }

$node = Get-ToolVersion 'node' @('--version')
if ($node -and $node -match '^v(\d+)\.(\d+)\.') {
    $major = [int]$Matches[1]
    $minor = [int]$Matches[2]
    if (($major -eq 20 -and $minor -ge 19) -or ($major -eq 22 -and $minor -ge 12) -or $major -ge 24) {
        Add-Check 'Node.js' 'PASS' $node
    } else { Add-Check 'Node.js' 'FAIL' '需要Node.js 20.19+、22.12+或兼容的新版本。' }
} else { Add-Check 'Node.js' 'FAIL' '未找到可运行的Node.js。' }

$npm = Get-ToolVersion 'npm.cmd' @('--version')
if ($npm) { Add-Check 'npm' 'PASS' $npm }
else { Add-Check 'npm' 'FAIL' '未找到可运行的npm。' }

$engine = Get-ToolVersion 'docker' @('info', '--format', '{{.OSType}}')
if ($engine -eq 'linux') { Add-Check 'Docker' 'PASS' 'Linux Engine可用。' }
else { Add-Check 'Docker' 'FAIL' 'Docker Linux Engine未就绪或当前终端没有访问权限。' }

$compose = Get-ToolVersion 'docker' @('compose', 'version', '--short')
if ($compose) { Add-Check 'Compose' 'PASS' $compose }
else { Add-Check 'Compose' 'FAIL' '未找到Docker Compose。' }

if (Test-Path -LiteralPath (Join-Path $workspace '.env') -PathType Leaf) {
    Add-Check '本地配置' 'PASS' '.env存在；未读取内容，凭据和数据库连接仍需启动验证。'
} else { Add-Check '本地配置' 'FAIL' '.env不存在，请从.env.example复制后在本机填写。' }

if (Test-Path -LiteralPath (Join-Path $workspace 'frontend/node_modules/vite/package.json')) {
    Add-Check '前端依赖' 'PASS' 'Vite依赖存在；使用npm ci可按锁文件恢复。'
} else { Add-Check '前端依赖' 'FAIL' '请运行npm --prefix .\frontend ci。' }

$mysql = Get-Service -Name MySQL80 -ErrorAction SilentlyContinue
if ($mysql -and $mysql.Status -eq 'Running') {
    Add-Check 'MySQL80' 'PASS' '本机服务运行中；未验证业务数据库、权限或服务端版本。'
} else { Add-Check 'MySQL80' 'WARN' '未发现运行中的MySQL80服务；如果使用其他数据库实例，请确认连接配置。' }

# 仅报告默认开发端口，不停止或替换任何现有进程。
try {
    $listeners = @(Get-NetTCPConnection -State Listen -ErrorAction Stop)
    foreach ($port in @(8080, 5173, 7474, 7687)) {
        $occupied = @($listeners | Where-Object { $_.LocalPort -eq $port }).Count -gt 0
        if ($occupied) { Add-Check "端口$port" 'WARN' '已占用，请确认是否为已启动的项目组件。' }
        else { Add-Check "端口$port" 'PASS' '空闲。' }
    }
} catch { Add-Check '端口' 'WARN' '当前权限无法枚举监听端口，请在普通PowerShell中检查。' }

if ($AsJson) { $checks | ConvertTo-Json -Depth 3 }
else { $checks | Format-Table -AutoSize -Wrap }
if (@($checks | Where-Object { $_.status -eq 'FAIL' }).Count -gt 0) { exit 1 }
