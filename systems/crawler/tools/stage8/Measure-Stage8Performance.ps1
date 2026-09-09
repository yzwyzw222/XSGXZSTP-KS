param(
    [uri]$BaseUri = 'http://127.0.0.1:18080',
    [int]$Concurrency = 4,
    [int]$ListWarmup = 100,
    [int]$ListSamples = 1000,
    [int]$GraphWarmup = 50,
    [int]$GraphSamples = 500,
    [string]$OutputPath
)

. (Join-Path $PSScriptRoot 'Stage8.Common.ps1')
if ($Concurrency -lt 1 -or $Concurrency -gt 16) { throw '并发数必须在1至16之间。' }
foreach ($value in @($ListWarmup, $ListSamples, $GraphWarmup, $GraphSamples)) {
    if ($value -lt 1 -or $value -gt 10000) { throw '预热和样本数必须在1至10000之间。' }
}

$credential = Get-Credential -Message '输入阶段8隔离环境管理员账号' -UserName 'aacv-stage8-admin'
Assert-Stage8Credential $credential 'aacv-stage8-admin' '阶段8管理员'

$handler = [Net.Http.HttpClientHandler]::new()
$handler.CookieContainer = [Net.CookieContainer]::new()
$handler.UseProxy = $false
$client = [Net.Http.HttpClient]::new($handler)
$client.BaseAddress = $BaseUri
$client.Timeout = [TimeSpan]::FromSeconds(15)
$client.DefaultRequestHeaders.Accept.ParseAdd('application/json')
$workspaceDriveName = (Split-Path -Qualifier $PSScriptRoot).TrimEnd(':')
$storageBefore = Get-PSDrive -Name $workspaceDriveName
$backendListener = Get-NetTCPConnection -LocalAddress 127.0.0.1 -LocalPort $BaseUri.Port `
    -State Listen -ErrorAction Stop | Select-Object -First 1
$backendBefore = Get-Process -Id $backendListener.OwningProcess -ErrorAction Stop
$backendCpuBefore = $backendBefore.CPU
$measurementStartedAt = [DateTimeOffset]::Now

function Read-Stage8Response {
    param([Parameter(Mandatory = $true)][Net.Http.HttpResponseMessage]$Response)
    try {
        $bytes = $Response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
        if (-not $Response.IsSuccessStatusCode) {
            throw "HTTP请求失败：$([int]$Response.StatusCode)"
        }
        return [Text.Encoding]::UTF8.GetString($bytes)
    } finally {
        $Response.Dispose()
    }
}

try {
    $csrfResponse = $client.GetAsync('/api/v1/auth/csrf').GetAwaiter().GetResult()
    $csrf = Read-Stage8Response $csrfResponse | ConvertFrom-Json
    $password = ConvertTo-Stage8PlainText $credential.Password
    try {
        $loginJson = @{ username = $credential.UserName; password = $password } | ConvertTo-Json -Compress
        $request = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Post, '/api/v1/auth/login')
        $request.Headers.Add([string]$csrf.headerName, [string]$csrf.token)
        $request.Content = [Net.Http.StringContent]::new($loginJson, [Text.Encoding]::UTF8, 'application/json')
        $loginResponse = $client.SendAsync($request).GetAwaiter().GetResult()
        Read-Stage8Response $loginResponse | Out-Null
        $request.Dispose()
    } finally {
        $password = $null
        $loginJson = $null
    }

    function Invoke-Stage8Samples {
        param(
            [Parameter(Mandatory = $true)][string]$Path,
            [Parameter(Mandatory = $true)][int]$Count,
            [Parameter(Mandatory = $true)][string]$Name
        )
        $durations = [Collections.Generic.List[double]]::new()
        for ($offset = 0; $offset -lt $Count; $offset += $Concurrency) {
            $batchSize = [Math]::Min($Concurrency, $Count - $offset)
            $pending = [Collections.Generic.List[object]]::new()
            for ($index = 0; $index -lt $batchSize; $index++) {
                $watch = [Diagnostics.Stopwatch]::StartNew()
                $task = $client.GetAsync($Path)
                $pending.Add([pscustomobject]@{ Watch = $watch; Task = $task })
            }
            foreach ($item in $pending) {
                try {
                    $response = $item.Task.GetAwaiter().GetResult()
                    Read-Stage8Response $response | Out-Null
                    $item.Watch.Stop()
                    $durations.Add($item.Watch.Elapsed.TotalMilliseconds)
                } catch {
                    $item.Watch.Stop()
                    throw "$Name 在样本 $($offset + 1) 至 $($offset + $batchSize) 内失败：$($_.Exception.Message)"
                }
            }
        }
        return ,$durations.ToArray()
    }

    $limits = @(
        @{ Path = '/api/v1/catalog/achievements?size=101'; Expected = 400; Name = '成果最大分页101' },
        @{ Path = '/api/v1/graph/subgraph?centerType=VENUE&centerId=1&depth=2&nodeLimit=301'; Expected = 400; Name = '图节点上限301' }
    )
    foreach ($limit in $limits) {
        $response = $client.GetAsync($limit.Path).GetAwaiter().GetResult()
        try {
            $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult() | Out-Null
            if ([int]$response.StatusCode -ne $limit.Expected) {
                throw "$($limit.Name) 应返回HTTP $($limit.Expected)，实际为 $([int]$response.StatusCode)。"
            }
        } finally {
            $response.Dispose()
        }
    }

    $defaultPage = Read-Stage8Response ($client.GetAsync('/api/v1/catalog/achievements').GetAwaiter().GetResult()) |
        ConvertFrom-Json
    $maximumPage = Read-Stage8Response ($client.GetAsync('/api/v1/catalog/achievements?size=100').GetAwaiter().GetResult()) |
        ConvertFrom-Json
    $defaultGraph = Read-Stage8Response ($client.GetAsync('/api/v1/graph/subgraph?centerType=VENUE&centerId=1&depth=2').GetAwaiter().GetResult()) |
        ConvertFrom-Json
    $maximumGraph = Read-Stage8Response ($client.GetAsync('/api/v1/graph/subgraph?centerType=VENUE&centerId=1&depth=2&nodeLimit=300').GetAwaiter().GetResult()) |
        ConvertFrom-Json
    if ([int]$defaultPage.size -ne 20 -or @($defaultPage.items).Count -gt 20) {
        throw '成果默认分页20条校验失败。'
    }
    if ([int]$maximumPage.size -ne 100 -or @($maximumPage.items).Count -gt 100) {
        throw '成果最大分页100条校验失败。'
    }
    if ([int]$defaultGraph.appliedLimits.nodeLimit -ne 100 -or @($defaultGraph.nodes).Count -gt 100) {
        throw '图默认100节点校验失败。'
    }
    if ([int]$maximumGraph.appliedLimits.nodeLimit -ne 300 -or @($maximumGraph.nodes).Count -gt 300) {
        throw '图硬上限300节点校验失败。'
    }

    Invoke-Stage8Samples '/api/v1/catalog/achievements' $ListWarmup '成果默认分页预热' | Out-Null
    Invoke-Stage8Samples '/api/v1/catalog/achievements?size=100' $ListWarmup '成果最大分页预热' | Out-Null
    Invoke-Stage8Samples '/api/v1/catalog/achievements/1' $ListWarmup '成果详情预热' | Out-Null
    Invoke-Stage8Samples '/api/v1/graph/subgraph?centerType=VENUE&centerId=1&depth=2&nodeLimit=300' $GraphWarmup '局部图预热' | Out-Null

    $measurements = @(
        @{ Name = 'catalog-default-20'; TargetMs = 2000; Values = (Invoke-Stage8Samples '/api/v1/catalog/achievements' $ListSamples '成果默认分页') },
        @{ Name = 'catalog-max-100'; TargetMs = 2000; Values = (Invoke-Stage8Samples '/api/v1/catalog/achievements?size=100' $ListSamples '成果最大分页') },
        @{ Name = 'catalog-detail'; TargetMs = 2000; Values = (Invoke-Stage8Samples '/api/v1/catalog/achievements/1' $ListSamples '成果详情') },
        @{ Name = 'graph-local-300'; TargetMs = 3000; Values = (Invoke-Stage8Samples '/api/v1/graph/subgraph?centerType=VENUE&centerId=1&depth=2&nodeLimit=300' $GraphSamples '局部图') }
    )

    $results = foreach ($measurement in $measurements) {
        $values = [double[]]$measurement.Values
        $p95 = Get-Stage8PercentileNearestRank -Values $values -Percentile 0.95
        [ordered]@{
            name = $measurement.Name
            samples = $values.Count
            concurrency = $Concurrency
            minimumMs = [Math]::Round(($values | Measure-Object -Minimum).Minimum, 3)
            averageMs = [Math]::Round(($values | Measure-Object -Average).Average, 3)
            p95Ms = [Math]::Round($p95, 3)
            maximumMs = [Math]::Round(($values | Measure-Object -Maximum).Maximum, 3)
            targetMs = $measurement.TargetMs
            passed = $p95 -le $measurement.TargetMs
        }
    }

    $measurementCompletedAt = [DateTimeOffset]::Now
    $computer = Get-CimInstance Win32_ComputerSystem
    $operatingSystem = Get-CimInstance Win32_OperatingSystem
    $processor = Get-CimInstance Win32_Processor | Select-Object -First 1
    $storageAfter = Get-PSDrive -Name $workspaceDriveName
    $backendAfter = Get-Process -Id $backendListener.OwningProcess -ErrorAction Stop
    $dockerStats = @(docker stats --no-stream --format '{{.Name}}|{{.CPUPerc}}|{{.MemUsage}}|{{.BlockIO}}' `
        aacv-stage8-mysql aacv-stage8-neo4j)
    if ($LASTEXITCODE -ne 0) { throw '无法读取阶段8容器资源快照。' }
    $evidence = [ordered]@{
        measuredAt = $measurementCompletedAt.ToString('o')
        measurementStartedAt = $measurementStartedAt.ToString('o')
        measurementDurationSeconds = [Math]::Round(
            ($measurementCompletedAt - $measurementStartedAt).TotalSeconds, 3)
        method = 'nearest-rank-p95'
        baseUri = $BaseUri.AbsoluteUri
        warmup = [ordered]@{ listPerScenario = $ListWarmup; graph = $GraphWarmup }
        environment = [ordered]@{
            windows = $operatingSystem.Caption + ' ' + $operatingSystem.Version
            cpu = $processor.Name.Trim()
            logicalProcessors = [int]$computer.NumberOfLogicalProcessors
            memoryGiB = [Math]::Round($computer.TotalPhysicalMemory / 1GB, 2)
            workspaceDrive = $workspaceDriveName
            storageFreeGiBBefore = [Math]::Round($storageBefore.Free / 1GB, 2)
            storageFreeGiBAfter = [Math]::Round($storageAfter.Free / 1GB, 2)
            backendWorkingSetMiB = [Math]::Round($backendAfter.WorkingSet64 / 1MB, 2)
            backendCpuSeconds = [Math]::Round($backendAfter.CPU - $backendCpuBefore, 3)
            dockerResourceSnapshot = $dockerStats
            jdk = (& java -version 2>&1 | Select-Object -First 1).ToString()
            mysql = '8.0.42-container'
            neo4j = '5.26-community-container'
            achievements = 100000
            graphNodes = 413000
            graphRelationships = 1000000
        }
        results = @($results)
        passed = @($results | Where-Object { -not $_.passed }).Count -eq 0
    }
    $json = $evidence | ConvertTo-Json -Depth 8
    if ($OutputPath) {
        $parent = Split-Path -Parent $OutputPath
        if (-not $parent -or -not (Test-Path -LiteralPath $parent -PathType Container)) {
            throw '性能证据输出目录必须预先存在。'
        }
        Set-Content -LiteralPath $OutputPath -Value $json -Encoding utf8NoBOM
    }
    $json
    if (-not $evidence.passed) { exit 1 }
} finally {
    $client.Dispose()
    $handler.Dispose()
}
