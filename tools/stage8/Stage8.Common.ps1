Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-Stage8Workspace {
    param([Parameter(Mandatory = $true)][string]$WorkspaceRoot)

    $resolved = (Resolve-Path -LiteralPath $WorkspaceRoot).Path
    $required = @(
        'backend\pom.xml',
        'frontend\package.json',
        'deploy\compose.stage8.yaml'
    )
    foreach ($relativePath in $required) {
        if (-not (Test-Path -LiteralPath (Join-Path $resolved $relativePath) -PathType Leaf)) {
            throw "阶段8工作区缺少必要文件：$relativePath"
        }
    }
    return $resolved
}

function ConvertTo-Stage8PlainText {
    param([Parameter(Mandatory = $true)][System.Security.SecureString]$SecureString)

    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureString)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

function Assert-Stage8Credential {
    param(
        [Parameter(Mandatory = $true)][System.Management.Automation.PSCredential]$Credential,
        [Parameter(Mandatory = $true)][string]$ExpectedUsername,
        [Parameter(Mandatory = $true)][string]$Purpose
    )

    if ($Credential.UserName -cne $ExpectedUsername) {
        throw "$Purpose 用户名必须为 $ExpectedUsername。"
    }
    $plainText = ConvertTo-Stage8PlainText -SecureString $Credential.Password
    try {
        if ($plainText.Length -lt 12 -or $plainText.Length -gt 128 -or $plainText.Trim().Length -eq 0) {
            throw "$Purpose 密码必须为12至128位且不能只包含空白。"
        }
        if (@($plainText.ToCharArray() | Where-Object { [char]::IsControl($_) }).Count -gt 0) {
            throw "$Purpose 密码不能包含控制字符。"
        }
    } finally {
        $plainText = $null
    }
}

function Assert-Stage8RestrictedBackupAcl {
    param([Parameter(Mandatory = $true)][string]$Path)

    $currentUser = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $allowed = @($currentUser, 'S-1-5-18', 'S-1-5-32-544')
    $acl = Get-Acl -LiteralPath $Path
    if (-not $acl.AreAccessRulesProtected) {
        throw "备份目录必须关闭继承并仅授权当前用户、SYSTEM和Administrators：$Path"
    }
    foreach ($rule in $acl.Access) {
        if ($rule.AccessControlType -ne [Security.AccessControl.AccessControlType]::Allow) { continue }
        try {
            $sid = $rule.IdentityReference.Translate(
                [Security.Principal.SecurityIdentifier]).Value
        } catch {
            throw "备份目录包含无法验证的访问主体：$($rule.IdentityReference.Value)"
        }
        if ($allowed -notcontains $sid) {
            throw "备份目录包含未批准的允许规则：$sid"
        }
    }
}

function Wait-Stage8ContainerHealthy {
    param(
        [Parameter(Mandatory = $true)][string]$ContainerName,
        [int]$TimeoutSeconds = 180
    )

    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $state = docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $ContainerName 2>$null
        if ($LASTEXITCODE -eq 0 -and $state -eq 'healthy') {
            return
        }
        if ($state -eq 'exited' -or $state -eq 'dead') {
            throw "容器 $ContainerName 在就绪前退出。"
        }
        Start-Sleep -Seconds 2
    } while ([DateTimeOffset]::UtcNow -lt $deadline)
    throw "等待容器 $ContainerName 健康超时（$TimeoutSeconds 秒）。"
}

function Invoke-Stage8WebRequest {
    param(
        [Parameter(Mandatory = $true)][uri]$Uri,
        [ValidateSet('GET', 'POST', 'PUT', 'DELETE')][string]$Method = 'GET',
        [Microsoft.PowerShell.Commands.WebRequestSession]$WebSession,
        [hashtable]$Headers,
        [string]$ContentType,
        [AllowNull()][object]$Body,
        [int]$TimeoutSeconds = 15
    )

    $parameters = @{
        Uri = $Uri
        Method = $Method
        TimeoutSec = $TimeoutSeconds
    }
    if ($null -ne $WebSession) { $parameters.WebSession = $WebSession }
    if ($null -ne $Headers) { $parameters.Headers = $Headers }
    if (-not [string]::IsNullOrWhiteSpace($ContentType)) { $parameters.ContentType = $ContentType }
    if ($PSBoundParameters.ContainsKey('Body')) { $parameters.Body = $Body }

    $command = Get-Command Invoke-WebRequest -ErrorAction Stop
    if ($command.Parameters.ContainsKey('UseBasicParsing')) { $parameters.UseBasicParsing = $true }
    if ($command.Parameters.ContainsKey('SkipHttpErrorCheck')) { $parameters.SkipHttpErrorCheck = $true }
    if ($command.Parameters.ContainsKey('NoProxy')) { $parameters.NoProxy = $true }

    try {
        return Invoke-WebRequest @parameters
    } catch {
        $responseProperty = $_.Exception.PSObject.Properties['Response']
        $errorResponse = if ($null -ne $responseProperty) { $responseProperty.Value } else { $null }
        if ($null -eq $errorResponse -or -not $errorResponse.PSObject.Methods['GetResponseStream']) {
            throw
        }

        $stream = $null
        $reader = $null
        try {
            $stream = $errorResponse.GetResponseStream()
            $content = ''
            if ($null -ne $stream) {
                $reader = [System.IO.StreamReader]::new($stream)
                $content = $reader.ReadToEnd()
            }
            return [pscustomobject]@{
                StatusCode = [int]$errorResponse.StatusCode
                Content = $content
                Headers = $errorResponse.Headers
            }
        } finally {
            if ($null -ne $reader) { $reader.Dispose() }
            elseif ($null -ne $stream) { $stream.Dispose() }
            $errorResponse.Close()
        }
    }
}

function Wait-Stage8Http {
    param(
        [Parameter(Mandatory = $true)][uri]$Uri,
        [int]$TimeoutSeconds = 180,
        [int[]]$AcceptedStatusCodes = @(200)
    )

    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        try {
            $response = Invoke-Stage8WebRequest -Uri $Uri -Method Get -TimeoutSeconds 5
            if ($AcceptedStatusCodes -contains [int]$response.StatusCode) {
                return
            }
        } catch {
            # 服务启动过程中连接拒绝是预期状态，直到超过统一超时才失败。
        }
        Start-Sleep -Seconds 2
    } while ([DateTimeOffset]::UtcNow -lt $deadline)
    throw "等待HTTP端点就绪超时：$Uri"
}

function Invoke-Stage8Neo4jStatement {
    param(
        [Parameter(Mandatory = $true)][string]$Statement,
        [Parameter(Mandatory = $true)][hashtable]$Parameters,
        [Parameter(Mandatory = $true)][System.Management.Automation.PSCredential]$Credential,
        [uri]$Endpoint = 'http://127.0.0.1:17474/db/neo4j/tx/commit',
        [int]$TimeoutSeconds = 120
    )

    $password = ConvertTo-Stage8PlainText -SecureString $Credential.Password
    try {
        $pair = '{0}:{1}' -f $Credential.UserName, $password
        $authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($pair))
        $body = @{ statements = @(@{ statement = $Statement; parameters = $Parameters }) } |
            ConvertTo-Json -Depth 8 -Compress
        $restParameters = @{
            Uri = $Endpoint
            Method = 'Post'
            Headers = @{
                Authorization = $authorization
                Accept = 'application/json'
            }
            ContentType = 'application/json'
            Body = $body
            TimeoutSec = $TimeoutSeconds
        }
        $restCommand = Get-Command Invoke-RestMethod -ErrorAction Stop
        if ($restCommand.Parameters.ContainsKey('NoProxy')) { $restParameters.NoProxy = $true }
        $result = Invoke-RestMethod @restParameters
        if ($result.errors.Count -gt 0) {
            $code = [string]$result.errors[0].code
            throw "Neo4j语句执行失败，错误码：$code"
        }
        return $result
    } finally {
        $password = $null
        $pair = $null
        $authorization = $null
        $body = $null
    }
}

function Get-Stage8PercentileNearestRank {
    param(
        [Parameter(Mandatory = $true)][double[]]$Values,
        [Parameter(Mandatory = $true)][ValidateRange(0.0, 1.0)][double]$Percentile
    )

    if ($Values.Count -eq 0) {
        throw '百分位计算至少需要一个样本。'
    }
    $ordered = @($Values | Sort-Object)
    $rank = [Math]::Ceiling($Percentile * $ordered.Count)
    return $ordered[[Math]::Max(0, $rank - 1)]
}

function Get-Stage8MySqlCapacityDisposition {
    param(
        [Parameter(Mandatory = $true)][string]$CountSignature,
        [Parameter(Mandatory = $true)][int]$SequenceTableCount,
        [Parameter(Mandatory = $true)][long]$SequenceRowCount
    )

    $emptySignature = '0|0|0|0|0|0|0|0|0|0|0|0'
    $completeSignature = '100000|300000|10000|1000|2000|100000|300000|300000|300000|200000|100000|100000'
    $tailMissingSignature = '100000|300000|10000|1000|2000|100000|300000|300000|300000|200000|0|0'
    $projectionMissingSignature = '100000|300000|10000|1000|2000|100000|300000|300000|300000|200000|100000|0'
    if ($CountSignature -eq $emptySignature -and $SequenceTableCount -eq 0 -and $SequenceRowCount -eq 0) {
        return 'Initialize'
    }
    if ($CountSignature -eq $completeSignature -and $SequenceTableCount -eq 1 -and $SequenceRowCount -eq 300000) {
        return 'Reuse'
    }
    if ($CountSignature -eq $tailMissingSignature -and $SequenceTableCount -eq 1 -and $SequenceRowCount -eq 300000) {
        return 'ResumeTail'
    }
    if ($CountSignature -eq $projectionMissingSignature -and $SequenceTableCount -eq 1 -and $SequenceRowCount -eq 300000) {
        return 'ResumeProjection'
    }
    throw "阶段8隔离库存在部分或不一致的容量数据：counts=$CountSignature，sequenceTables=$SequenceTableCount，sequenceRows=$SequenceRowCount。脚本不会清空或覆盖。"
}
