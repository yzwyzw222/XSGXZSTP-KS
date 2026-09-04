param(
    [ValidateSet('127.0.0.1', 'localhost')][string]$DatabaseHost = '127.0.0.1',
    [ValidateRange(1, 65535)][int]$DatabasePort = 3306,
    [ValidatePattern('^[a-z][a-z0-9_]{0,63}$')][string]$DatabaseName = 'aacv_system',
    [string]$BackupRoot = 'E:\AACV_System_Backups',
    [switch]$InitializeBackupRoot,
    [switch]$ApplyRetention,
    [string]$OutputPath
)

. (Join-Path $PSScriptRoot 'Stage8.Common.ps1')

function Get-Stage8IsoWeek {
    param([Parameter(Mandatory = $true)][datetime]$Date)

    $day = [int]$Date.DayOfWeek
    if ($day -eq 0) { $day = 7 }
    $thursday = $Date.Date.AddDays(4 - $day)
    $firstThursday = [datetime]::new($thursday.Year, 1, 4)
    $firstDay = [int]$firstThursday.DayOfWeek
    if ($firstDay -eq 0) { $firstDay = 7 }
    $firstThursday = $firstThursday.AddDays(4 - $firstDay)
    return [pscustomobject]@{
        Year = $thursday.Year
        Week = [int](1 + [Math]::Floor(($thursday - $firstThursday).TotalDays / 7))
    }
}

function Set-Stage8RestrictedDirectoryAcl {
    param([Parameter(Mandatory = $true)][string]$Path)

    $currentUser = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $system = [Security.Principal.SecurityIdentifier]::new('S-1-5-18')
    $administrators = [Security.Principal.SecurityIdentifier]::new('S-1-5-32-544')
    $acl = [Security.AccessControl.DirectorySecurity]::new()
    $acl.SetAccessRuleProtection($true, $false)
    $inheritance = [Security.AccessControl.InheritanceFlags]'ContainerInherit, ObjectInherit'
    $propagation = [Security.AccessControl.PropagationFlags]::None
    foreach ($identity in @($currentUser, $system, $administrators)) {
        $rule = [Security.AccessControl.FileSystemAccessRule]::new(
            $identity,
            [Security.AccessControl.FileSystemRights]::FullControl,
            $inheritance,
            $propagation,
            [Security.AccessControl.AccessControlType]::Allow)
        $acl.AddAccessRule($rule) | Out-Null
    }
    $acl.SetOwner($currentUser)
    Set-Acl -LiteralPath $Path -AclObject $acl
}

function Get-Stage8DatabaseSnapshot {
    param(
        [Parameter(Mandatory = $true)][System.Management.Automation.CommandInfo]$MySql,
        [Parameter(Mandatory = $true)][string]$HostName,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$UserName,
        [Parameter(Mandatory = $true)][string]$Name
    )

    $sql = @'
SELECT CONCAT('serverVersion=', VERSION());
SELECT CONCAT('flywayVersion=', version)
FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1;
SELECT CONCAT('tableCount=', COUNT(*))
FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE';
SELECT CONCAT('achievementCount=', COUNT(*)) FROM achievement;
SELECT CONCAT('userCount=', COUNT(*)) FROM sys_user;
SELECT CONCAT('auditCount=', COUNT(*)) FROM audit_log;
SELECT CONCAT('outboxCount=', COUNT(*)) FROM graph_outbox_event;
SELECT CONCAT('projectionStateCount=', COUNT(*)) FROM graph_projection_state;
SELECT CONCAT('databaseBytes=', COALESCE(SUM(data_length + index_length), 0))
FROM information_schema.tables WHERE table_schema = DATABASE();
'@
    $arguments = @(
        "--host=$HostName",
        "--port=$Port",
        "--user=$UserName",
        '--batch',
        '--skip-column-names',
        "--execute=$sql",
        $Name
    )
    $lines = @(& $MySql.Source @arguments)
    if ($LASTEXITCODE -ne 0) { throw '读取备份源数据库基线失败。' }
    $result = [ordered]@{}
    foreach ($line in $lines) {
        $parts = ([string]$line).Split(@('='), 2)
        if ($parts.Count -eq 2) { $result[$parts[0]] = $parts[1] }
    }
    foreach ($required in @(
        'serverVersion', 'flywayVersion', 'tableCount', 'achievementCount',
        'userCount', 'auditCount', 'outboxCount', 'projectionStateCount', 'databaseBytes')) {
        if (-not $result.Contains($required)) { throw "数据库基线缺少字段：$required" }
    }
    if ([string]$result.flywayVersion -ne '11') {
        throw "备份源Flyway版本必须为11，实际为 $($result.flywayVersion)。"
    }
    return $result
}

function Remove-Stage8ExpiredBackups {
    param(
        [Parameter(Mandatory = $true)][string]$Directory,
        [Parameter(Mandatory = $true)][int]$Keep
    )

    $removed = [Collections.Generic.List[string]]::new()
    $expired = @(Get-ChildItem -LiteralPath $Directory -Filter '*.sql' -File |
        Sort-Object LastWriteTimeUtc -Descending | Select-Object -Skip $Keep)
    $directoryPrefix = [IO.Path]::GetFullPath($Directory).TrimEnd('\') + '\'
    foreach ($file in $expired) {
        $fullPath = [IO.Path]::GetFullPath($file.FullName)
        if (-not $fullPath.StartsWith($directoryPrefix, [StringComparison]::OrdinalIgnoreCase)) {
            throw "拒绝删除保留目录之外的文件：$fullPath"
        }
        $hashPath = $fullPath + '.sha256'
        $metadataPath = $fullPath + '.metadata.json'
        Remove-Item -LiteralPath $fullPath -Force
        if (Test-Path -LiteralPath $hashPath -PathType Leaf) {
            Remove-Item -LiteralPath $hashPath -Force
        }
        if (Test-Path -LiteralPath $metadataPath -PathType Leaf) {
            Remove-Item -LiteralPath $metadataPath -Force
        }
        $removed.Add($file.Name)
    }
    return ,$removed.ToArray()
}

$backupRootFull = [IO.Path]::GetFullPath($BackupRoot).TrimEnd('\')
if ($backupRootFull -ne 'E:\AACV_System_Backups') {
    throw '阶段8业务备份目录固定为 E:\AACV_System_Backups。'
}
$createdRoot = $false
if (-not (Test-Path -LiteralPath $backupRootFull)) {
    if (-not $InitializeBackupRoot) {
        throw '备份目录不存在；确认路径和权限后使用 -InitializeBackupRoot 创建并限制ACL。'
    }
    New-Item -ItemType Directory -Path $backupRootFull -ErrorAction Stop | Out-Null
    $createdRoot = $true
    try {
        Set-Stage8RestrictedDirectoryAcl -Path $backupRootFull
    } catch {
        if (@(Get-ChildItem -LiteralPath $backupRootFull -Force).Count -eq 0) {
            Remove-Item -LiteralPath $backupRootFull -Force -ErrorAction SilentlyContinue
        }
        throw
    }
}
if (-not (Test-Path -LiteralPath $backupRootFull -PathType Container)) {
    throw '阶段8业务备份路径不是目录。'
}
if (((Get-Item -LiteralPath $backupRootFull -Force).Attributes -band
    [IO.FileAttributes]::ReparsePoint) -ne 0) {
    throw '阶段8业务备份目录不能是重解析点。'
}
Assert-Stage8RestrictedBackupAcl -Path $backupRootFull

$dailyDirectory = Join-Path $backupRootFull 'daily'
$weeklyDirectory = Join-Path $backupRootFull 'weekly'
foreach ($directory in @($dailyDirectory, $weeklyDirectory)) {
    if (-not (Test-Path -LiteralPath $directory -PathType Container)) {
        New-Item -ItemType Directory -Path $directory -ErrorAction Stop | Out-Null
    }
}

$mysql = Get-Command mysql.exe -ErrorAction Stop
$mysqldump = Get-Command mysqldump.exe -ErrorAction Stop
$credential = Get-Credential -Message "输入 $DatabaseName 业务备份账号"
$password = ConvertTo-Stage8PlainText -SecureString $credential.Password
$startedAt = [DateTimeOffset]::Now
$partialPath = $null
$errorPath = $null
try {
    $env:MYSQL_PWD = $password
    $source = Get-Stage8DatabaseSnapshot -MySql $mysql -HostName $DatabaseHost `
        -Port $DatabasePort -UserName $credential.UserName -Name $DatabaseName
    $driveName = (Split-Path -Qualifier $backupRootFull).TrimEnd(':')
    $drive = Get-PSDrive -Name $driveName -ErrorAction Stop
    $requiredBytes = [Math]::Max(256MB, ([long]$source.databaseBytes * 2))
    if ($drive.Free -lt $requiredBytes) {
        throw "备份磁盘剩余空间不足；至少需要 $requiredBytes 字节。"
    }

    $timestamp = [DateTimeOffset]::Now.ToString('yyyyMMddTHHmmssfff')
    $dailyPath = Join-Path $dailyDirectory ("{0}_{1}.sql" -f $DatabaseName, $timestamp)
    $partialPath = $dailyPath + '.partial'
    $errorPath = Join-Path $env:TEMP ("aacv-stage8-mysqldump-{0}.err" -f ([guid]::NewGuid()))
    $dumpArguments = @(
        "--host=$DatabaseHost",
        "--port=$DatabasePort",
        "--user=$($credential.UserName)",
        '--single-transaction',
        '--quick',
        '--skip-lock-tables',
        '--default-character-set=utf8mb4',
        '--hex-blob',
        '--set-gtid-purged=OFF',
        '--no-tablespaces',
        '--column-statistics=0',
        $DatabaseName
    )
    $process = Start-Process -FilePath $mysqldump.Source -ArgumentList $dumpArguments `
        -NoNewWindow -Wait -PassThru -RedirectStandardOutput $partialPath `
        -RedirectStandardError $errorPath
    if ($process.ExitCode -ne 0) {
        throw "mysqldump失败，退出码：$($process.ExitCode)。"
    }
    if (-not (Test-Path -LiteralPath $partialPath -PathType Leaf) -or
        (Get-Item -LiteralPath $partialPath).Length -eq 0) {
        throw 'mysqldump未生成非空备份。'
    }
    Move-Item -LiteralPath $partialPath -Destination $dailyPath
    $partialPath = $null
    $dailyHash = (Get-FileHash -LiteralPath $dailyPath -Algorithm SHA256).Hash
    [IO.File]::WriteAllText($dailyPath + '.sha256', $dailyHash + [Environment]::NewLine,
        [Text.UTF8Encoding]::new($false))
    $backupMetadata = [ordered]@{
        formatVersion = 1
        createdAt = [DateTimeOffset]::Now.ToString('o')
        database = $DatabaseName
        sha256 = $dailyHash
        serverVersion = $source.serverVersion
        flywayVersion = $source.flywayVersion
        tableCount = [long]$source.tableCount
        achievementCount = [long]$source.achievementCount
        userCount = [long]$source.userCount
        auditCount = [long]$source.auditCount
        outboxCount = [long]$source.outboxCount
        projectionStateCount = [long]$source.projectionStateCount
    }
    $metadataJson = $backupMetadata | ConvertTo-Json -Depth 4
    [IO.File]::WriteAllText($dailyPath + '.metadata.json',
        $metadataJson + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))

    $week = Get-Stage8IsoWeek -Date ([DateTimeOffset]::Now.LocalDateTime)
    $weeklyPath = Join-Path $weeklyDirectory (
        '{0}_{1}-W{2:D2}.sql' -f $DatabaseName, $week.Year, $week.Week)
    $weeklyCreated = $false
    if (-not (Test-Path -LiteralPath $weeklyPath -PathType Leaf)) {
        Copy-Item -LiteralPath $dailyPath -Destination $weeklyPath
        $weeklyHash = (Get-FileHash -LiteralPath $weeklyPath -Algorithm SHA256).Hash
        [IO.File]::WriteAllText($weeklyPath + '.sha256', $weeklyHash + [Environment]::NewLine,
            [Text.UTF8Encoding]::new($false))
        $weeklyMetadata = [ordered]@{}
        foreach ($entry in $backupMetadata.GetEnumerator()) {
            $weeklyMetadata[$entry.Key] = $entry.Value
        }
        $weeklyMetadata.sha256 = $weeklyHash
        $weeklyMetadataJson = $weeklyMetadata | ConvertTo-Json -Depth 4
        [IO.File]::WriteAllText($weeklyPath + '.metadata.json',
            $weeklyMetadataJson + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
        $weeklyCreated = $true
    }

    $removedDaily = @()
    $removedWeekly = @()
    if ($ApplyRetention) {
        $removedDaily = @(Remove-Stage8ExpiredBackups -Directory $dailyDirectory -Keep 7)
        $removedWeekly = @(Remove-Stage8ExpiredBackups -Directory $weeklyDirectory -Keep 4)
    }
    $completedAt = [DateTimeOffset]::Now
    $evidence = [ordered]@{
        startedAt = $startedAt.ToString('o')
        completedAt = $completedAt.ToString('o')
        durationSeconds = [Math]::Round(($completedAt - $startedAt).TotalSeconds, 3)
        source = [ordered]@{
            host = $DatabaseHost
            port = $DatabasePort
            database = $DatabaseName
            serverVersion = $source.serverVersion
            clientVersion = (& $mysqldump.Source --version).ToString().Trim()
            flywayVersion = $source.flywayVersion
            tableCount = [long]$source.tableCount
            achievementCount = [long]$source.achievementCount
            userCount = [long]$source.userCount
            auditCount = [long]$source.auditCount
            outboxCount = [long]$source.outboxCount
            projectionStateCount = [long]$source.projectionStateCount
        }
        backup = [ordered]@{
            dailyPath = $dailyPath
            dailyBytes = (Get-Item -LiteralPath $dailyPath).Length
            sha256 = $dailyHash
            weeklyPath = $weeklyPath
            weeklyCreated = $weeklyCreated
            rootCreated = $createdRoot
            aclRestricted = $true
        }
        retention = [ordered]@{
            applied = [bool]$ApplyRetention
            dailyKeep = 7
            weeklyKeep = 4
            removedDaily = @($removedDaily)
            removedWeekly = @($removedWeekly)
        }
        passed = $true
    }
    $json = $evidence | ConvertTo-Json -Depth 8
    if ($OutputPath) {
        $parent = Split-Path -Parent $OutputPath
        if (-not $parent -or -not (Test-Path -LiteralPath $parent -PathType Container)) {
            throw '备份证据输出目录必须预先存在。'
        }
        [IO.File]::WriteAllText([IO.Path]::GetFullPath($OutputPath), $json + [Environment]::NewLine,
            [Text.UTF8Encoding]::new($false))
    }
    $json
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    if ($partialPath -and (Test-Path -LiteralPath $partialPath -PathType Leaf)) {
        Remove-Item -LiteralPath $partialPath -Force -ErrorAction SilentlyContinue
    }
    if ($errorPath -and (Test-Path -LiteralPath $errorPath -PathType Leaf)) {
        Remove-Item -LiteralPath $errorPath -Force -ErrorAction SilentlyContinue
    }
    $password = $null
}
