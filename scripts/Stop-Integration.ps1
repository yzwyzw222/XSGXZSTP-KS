param([ValidateSet('all', 'portal', 'relation', 'crawler')][string]$System = 'all')
. (Join-Path $PSScriptRoot 'Integration.Common.ps1')

# 停止依赖所属进程记录，不依赖当前接入配置仍然有效。
$operationLock = Enter-IntegrationLock
try {
    $records = @(Read-IntegrationState)
    $remaining = @()
    $failures = @()
    foreach ($record in $records) {
        if ($System -ne 'all' -and $record.system -ne $System) { $remaining += $record; continue }
        try {
            $process = Get-Process -Id $record.pid -ErrorAction SilentlyContinue
            if (-not $process) { continue }
            if (-not (Test-IntegrationIdentity $record)) { throw 'PID、创建时间或命令行不匹配，拒绝停止。' }
            Stop-Process -InputObject $process -ErrorAction Stop
            if (-not $process.WaitForExit(5000)) { throw '停止等待超时，保留进程记录。' }
            Write-Output "已停止 $($record.id)。"
        } catch { $remaining += $record; $failures += "$($record.id)：$($_.Exception.Message)" }
    }
    Save-IntegrationState $remaining
    if ($failures.Count -gt 0) { throw ($failures -join [Environment]::NewLine) }
    Write-Output '停止完成；数据库、容器、卷和运行数据均保留。'
} finally { $operationLock.Dispose() }
