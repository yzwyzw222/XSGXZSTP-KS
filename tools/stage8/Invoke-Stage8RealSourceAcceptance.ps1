param(
    [uri]$BaseUri = 'http://127.0.0.1:8080',
    [int]$MaxRecordsPerSource = 20,
    [switch]$CreateMissingSources,
    [switch]$ConfirmExternalRequests
)

. (Join-Path $PSScriptRoot 'Stage8.Common.ps1')
if (-not $ConfirmExternalRequests) {
    throw '本脚本会访问OpenAlex和Crossref并写入小批量真实元数据。确认后使用 -ConfirmExternalRequests。'
}
if ($MaxRecordsPerSource -lt 1 -or $MaxRecordsPerSource -gt 500) {
    throw '每个来源记录数必须在1至500之间。'
}

$credential = Get-Credential -Message '输入当前本地AACV管理员账号'
$webSession = [Microsoft.PowerShell.Commands.WebRequestSession]::new()

function Invoke-Stage8Api {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [ValidateSet('GET', 'POST', 'PUT')][string]$Method = 'GET',
        [object]$Body,
        [int[]]$ExpectedStatus = @(200)
    )
    $parameters = @{
        Uri = [uri]::new($BaseUri, $Path)
        WebSession = $webSession
        Method = $Method
        TimeoutSeconds = 125
        Headers = @{ Accept = 'application/json' }
    }
    if ($Method -ne 'GET') {
        $parameters.Headers[[string]$script:csrf.headerName] = [string]$script:csrf.token
        $parameters.ContentType = 'application/json'
        $parameters.Body = if ($null -eq $Body) { '{}' } else { $Body | ConvertTo-Json -Depth 8 -Compress }
    }
    $response = Invoke-Stage8WebRequest @parameters
    if ($ExpectedStatus -notcontains [int]$response.StatusCode) {
        throw "$Method $Path 返回HTTP $([int]$response.StatusCode)，期望：$($ExpectedStatus -join ',')。"
    }
    if ([int]$response.StatusCode -eq 204 -or [string]::IsNullOrWhiteSpace($response.Content)) {
        return $null
    }
    return $response.Content | ConvertFrom-Json
}

try {
    $csrfResponse = Invoke-Stage8WebRequest -Uri ([uri]::new($BaseUri, '/api/v1/auth/csrf')) `
        -WebSession $webSession -Method Get -TimeoutSeconds 15
    if ([int]$csrfResponse.StatusCode -ne 200) { throw '无法获取CSRF令牌。' }
    $script:csrf = $csrfResponse.Content | ConvertFrom-Json
    $password = ConvertTo-Stage8PlainText $credential.Password
    try {
        Invoke-Stage8Api '/api/v1/auth/login' POST `
            @{ username = $credential.UserName; password = $password } @(200) | Out-Null
    } finally {
        $password = $null
    }

    $sources = Invoke-Stage8Api '/api/v1/sources?size=100'
    $selectedSources = @{}
    foreach ($sourceType in @('OPENALEX', 'CROSSREF')) {
        $source = $sources.items | Where-Object { $_.sourceType -eq $sourceType } | Select-Object -First 1
        if ($null -eq $source) {
            if (-not $CreateMissingSources) {
                throw "缺少 $sourceType 数据源；如确认允许创建，重新运行并增加 -CreateMissingSources。"
            }
            $source = Invoke-Stage8Api '/api/v1/sources' POST @{
                sourceType = $sourceType
                requestsPerSecond = 1
                maxConcurrency = 1
                connectTimeoutSeconds = 10
                responseTimeoutSeconds = 60
                maxRetries = 2
                maxResponseBytes = 5242880
                complianceNote = '阶段8真实来源受控小批量验收；单并发、每秒一次。'
            } @(201)
        }
        if (-not $source.enabled) {
            throw "$sourceType 数据源当前已停用；脚本不会擅自改变既有启停状态。"
        }
        $probe = Invoke-Stage8Api "/api/v1/sources/$($source.id)/probe" POST @{} @(200)
        if (-not $probe.reachable) {
            throw "$sourceType 探测失败，分类：$($probe.errorCategory)。这是外部依赖结果，不能记为代码通过。"
        }
        $selectedSources[$sourceType] = $source
    }

    $runResults = [Collections.Generic.List[object]]::new()
    foreach ($sourceType in @('OPENALEX', 'CROSSREF')) {
        $source = $selectedSources[$sourceType]
        $task = Invoke-Stage8Api '/api/v1/crawl/tasks' POST @{
            sourceId = [long]$source.id
            name = "Stage8-$sourceType-$([DateTimeOffset]::Now.ToString('yyyyMMdd-HHmmss'))"
            parameters = @{
                publicationDateFrom = '2024-01-01'
                publicationDateTo = '2024-01-31'
                keyword = 'machine learning'
                authorIds = @()
                institutionIds = @()
                dois = @()
                orcids = @()
                rorIds = @()
                updatedFrom = $null
                updatedUntil = $null
                maxPages = 1
                maxRecords = $MaxRecordsPerSource
            }
        } @(201)
        $run = Invoke-Stage8Api "/api/v1/crawl/tasks/$($task.id)/trigger" POST @{} @(202)
        $deadline = [DateTimeOffset]::UtcNow.AddMinutes(10)
        do {
            Start-Sleep -Seconds 2
            $run = Invoke-Stage8Api "/api/v1/crawl/runs/$($run.id)"
        } while ($run.status -notin @('SUCCEEDED', 'FAILED', 'CANCELLED') -and
                 [DateTimeOffset]::UtcNow -lt $deadline)
        if ($run.status -ne 'SUCCEEDED') {
            throw "$sourceType 真实小批量运行未成功，最终状态：$($run.status)。"
        }
        if ([long]$run.requestCount -gt 5 -or [long]$run.readCount -gt $MaxRecordsPerSource) {
            throw "$sourceType 运行超过受控页数或记录数。"
        }
        $runResults.Add([pscustomobject]@{
            sourceType = $sourceType
            runId = $run.id
            readCount = $run.readCount
            parsedCount = $run.parsedCount
            failureCount = $run.failureCount
            requestCount = $run.requestCount
        })
    }

    $catalog = Invoke-Stage8Api '/api/v1/catalog/achievements?size=20'
    if ([long]$catalog.totalElements -lt 1) { throw '真实采集后成果目录为空。' }
    Invoke-Stage8Api "/api/v1/catalog/achievements/$($catalog.items[0].id)" | Out-Null
    Invoke-Stage8Api '/api/v1/graph/sync-status' | Out-Null
    Invoke-Stage8Api '/api/v1/analytics/overview' | Out-Null
    Invoke-Stage8Api '/api/v1/operations/overview' | Out-Null
    Invoke-Stage8Api '/api/v1/operations/alerts?size=20' | Out-Null
    Invoke-Stage8Api '/api/v1/operations/audits?size=20' | Out-Null

    $export = Invoke-Stage8Api '/api/v1/exports' POST @{ format = 'CSV'; filters = @{} } @(202)
    $exportDeadline = [DateTimeOffset]::UtcNow.AddMinutes(5)
    do {
        Start-Sleep -Seconds 1
        $export = Invoke-Stage8Api "/api/v1/exports/$($export.id)"
    } while ($export.status -in @('PENDING', 'RUNNING') -and [DateTimeOffset]::UtcNow -lt $exportDeadline)
    if ($export.status -ne 'SUCCEEDED' -or -not $export.downloadAvailable) {
        throw "真实联合运行导出未成功，最终状态：$($export.status)。"
    }

    [ordered]@{
        acceptedAt = [DateTimeOffset]::Now.ToString('o')
        sources = @($runResults)
        catalogTotal = $catalog.totalElements
        exportStatus = $export.status
        exportCount = $export.exportedCount
        note = '未输出Cookie、CSRF、下载令牌、密码或请求正文。'
    } | ConvertTo-Json -Depth 6
} finally {
    $password = $null
    $script:csrf = $null
}
