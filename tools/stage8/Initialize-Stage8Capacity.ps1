param(
    [string]$WorkspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [int]$Neo4jBatchSize = 5000
)

. (Join-Path $PSScriptRoot 'Stage8.Common.ps1')
$workspace = Assert-Stage8Workspace -WorkspaceRoot $WorkspaceRoot
if ($Neo4jBatchSize -lt 100 -or $Neo4jBatchSize -gt 10000) {
    throw 'Neo4j批量大小必须在100至10000之间。'
}

$databaseCredential = Get-Credential -Message '输入阶段8隔离数据库账号' -UserName 'aacv_stage8'
$neo4jCredential = Get-Credential -Message '输入阶段8隔离Neo4j账号' -UserName 'neo4j'
Assert-Stage8Credential $databaseCredential 'aacv_stage8' '阶段8数据库'
Assert-Stage8Credential $neo4jCredential 'neo4j' '阶段8 Neo4j'

$mysql = Get-Command mysql.exe -ErrorAction Stop
$databasePassword = ConvertTo-Stage8PlainText $databaseCredential.Password
try {
    $env:MYSQL_PWD = $databasePassword
    $databaseName = & $mysql.Source --host=127.0.0.1 --port=13306 `
        --user=$($databaseCredential.UserName) --batch --skip-column-names `
        --execute='SELECT DATABASE()' aacv_stage8_capacity_20260903
    if ($LASTEXITCODE -ne 0 -or $databaseName.Trim() -ne 'aacv_stage8_capacity_20260903') {
        throw '数据库身份或隔离库名称校验失败，容量数据未写入。'
    }
    $migrationCount = & $mysql.Source --host=127.0.0.1 --port=13306 `
        --user=$($databaseCredential.UserName) --batch --skip-column-names `
        --execute='SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1 AND CAST(version AS UNSIGNED) BETWEEN 1 AND 11' `
        aacv_stage8_capacity_20260903
    if ($LASTEXITCODE -ne 0 -or [int]$migrationCount -ne 11) {
        throw 'Flyway V1至V11尚未完整应用，容量数据未写入。'
    }

    $countQuery = "SELECT CONCAT_WS('|',(SELECT COUNT(*) FROM achievement),(SELECT COUNT(*) FROM author),(SELECT COUNT(*) FROM organization),(SELECT COUNT(*) FROM venue),(SELECT COUNT(*) FROM topic),(SELECT COUNT(*) FROM paper_detail),(SELECT COUNT(*) FROM author_external_id),(SELECT COUNT(*) FROM achievement_author),(SELECT COUNT(*) FROM authorship_organization),(SELECT COUNT(*) FROM achievement_topic),(SELECT COUNT(*) FROM achievement_reference),(SELECT COUNT(*) FROM graph_projection_state))"
    $counts = & $mysql.Source --host=127.0.0.1 --port=13306 `
        --user=$($databaseCredential.UserName) --batch --skip-column-names `
        --execute=$countQuery `
        aacv_stage8_capacity_20260903
    if ($LASTEXITCODE -ne 0) { throw '无法读取阶段8 MySQL容量计数。' }
    $sequenceTableCount = & $mysql.Source --host=127.0.0.1 --port=13306 `
        --user=$($databaseCredential.UserName) --batch --skip-column-names `
        --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='stage8_sequence'" `
        aacv_stage8_capacity_20260903
    if ($LASTEXITCODE -ne 0) { throw '无法检查阶段8辅助序列表。' }
    $sequenceRows = 0
    if ([int]$sequenceTableCount -eq 1) {
        $sequenceRows = & $mysql.Source --host=127.0.0.1 --port=13306 `
            --user=$($databaseCredential.UserName) --batch --skip-column-names `
            --execute='SELECT COUNT(*) FROM stage8_sequence' aacv_stage8_capacity_20260903
        if ($LASTEXITCODE -ne 0) { throw '无法读取阶段8辅助序列表计数。' }
    }
    $disposition = Get-Stage8MySqlCapacityDisposition -CountSignature $counts.Trim() `
        -SequenceTableCount ([int]$sequenceTableCount) -SequenceRowCount ([long]$sequenceRows)
    $referenceSql = @'
INSERT INTO achievement_reference (
    citing_achievement_id, referenced_external_work_id, referenced_id_type,
    referenced_id_value, cited_achievement_id
)
SELECT n, CONCAT('https://openalex.org/S8W', MOD(n, 100000) + 1), 'OPENALEX',
       CONCAT('https://openalex.org/S8W', MOD(n, 100000) + 1), MOD(n, 100000) + 1
FROM stage8_sequence WHERE n <= 100000;
'@
    $projectionStateSql = @'
INSERT INTO graph_projection_state (
    achievement_id, desired_version, applied_version, last_enqueued_at, last_projected_at
)
SELECT n, 1, 1, '2026-09-03 00:00:00.000000', '2026-09-03 00:00:00.000000'
FROM stage8_sequence WHERE n <= 100000;
'@
    if ($disposition -eq 'Initialize') {
        $sql = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'stage8-capacity.sql') -Raw
        $sql | & $mysql.Source --host=127.0.0.1 --port=13306 `
            --user=$($databaseCredential.UserName) --default-character-set=utf8mb4 `
            aacv_stage8_capacity_20260903
        if ($LASTEXITCODE -ne 0) { throw 'MySQL容量数据生成失败。' }
    } elseif ($disposition -eq 'ResumeTail') {
        $tailSql = "START TRANSACTION;`n$referenceSql`n$projectionStateSql`nCOMMIT;"
        $tailSql | & $mysql.Source --host=127.0.0.1 --port=13306 `
            --user=$($databaseCredential.UserName) --default-character-set=utf8mb4 `
            aacv_stage8_capacity_20260903
        if ($LASTEXITCODE -ne 0) { throw 'MySQL容量数据尾部事务补写失败。' }
        Write-Host '已在事务中补写缺失的成果引用和图投影状态。'
    } elseif ($disposition -eq 'ResumeProjection') {
        $tailSql = "START TRANSACTION;`n$projectionStateSql`nCOMMIT;"
        $tailSql | & $mysql.Source --host=127.0.0.1 --port=13306 `
            --user=$($databaseCredential.UserName) --default-character-set=utf8mb4 `
            aacv_stage8_capacity_20260903
        if ($LASTEXITCODE -ne 0) { throw 'MySQL图投影状态事务补写失败。' }
        Write-Host '已在事务中补写缺失的图投影状态。'
    } else {
        Write-Host '检测到完整MySQL容量数据，跳过重复生成并继续Neo4j校验。'
    }
    if ($disposition -ne 'Reuse') {
        $counts = & $mysql.Source --host=127.0.0.1 --port=13306 `
            --user=$($databaseCredential.UserName) --batch --skip-column-names `
            --execute=$countQuery aacv_stage8_capacity_20260903
        $sequenceRows = & $mysql.Source --host=127.0.0.1 --port=13306 `
            --user=$($databaseCredential.UserName) --batch --skip-column-names `
            --execute='SELECT COUNT(*) FROM stage8_sequence' aacv_stage8_capacity_20260903
        if ($LASTEXITCODE -ne 0) { throw 'MySQL容量数据生成后的计数检查失败。' }
        $verifiedDisposition = Get-Stage8MySqlCapacityDisposition -CountSignature $counts.Trim() `
            -SequenceTableCount 1 -SequenceRowCount ([long]$sequenceRows)
        if ($verifiedDisposition -ne 'Reuse') {
            throw "MySQL容量数据处理后未达到完整状态：$verifiedDisposition"
        }
    }
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    $databasePassword = $null
    $sql = $null
    $tailSql = $null
    $referenceSql = $null
    $projectionStateSql = $null
}

function Invoke-BatchedCypher {
    param(
        [Parameter(Mandatory = $true)][int]$Count,
        [Parameter(Mandatory = $true)][string]$Statement,
        [Parameter(Mandatory = $true)][string]$Label
    )
    for ($start = 1; $start -le $Count; $start += $Neo4jBatchSize) {
        $end = [Math]::Min($Count, $start + $Neo4jBatchSize - 1)
        Invoke-Stage8Neo4jStatement -Statement $Statement `
            -Parameters @{ start = $start; end = $end } -Credential $neo4jCredential | Out-Null
        Write-Progress -Activity '生成阶段8 Neo4j容量数据' -Status $Label `
            -PercentComplete ([Math]::Floor(100 * $end / $Count))
    }
}

$graphValidationStatement = @'
MATCH (n {aacvManaged: true})
WITH count(n) AS nodes,
     count(CASE WHEN n:Achievement THEN 1 END) AS achievements,
     count(CASE WHEN n:Author THEN 1 END) AS authors,
     count(CASE WHEN n:Institution THEN 1 END) AS institutions,
     count(CASE WHEN n:Venue THEN 1 END) AS venues,
     count(CASE WHEN n:Topic THEN 1 END) AS topics
OPTIONAL MATCH ()-[r {aacvManaged: true}]->()
RETURN nodes, achievements, authors, institutions, venues, topics, count(r) AS relationships,
       count(CASE WHEN type(r) = 'AUTHORED' THEN 1 END) AS authored,
       count(CASE WHEN type(r) = 'AFFILIATED_WITH' THEN 1 END) AS affiliated,
       count(CASE WHEN type(r) = 'PUBLISHED_IN' THEN 1 END) AS published,
       count(CASE WHEN type(r) = 'HAS_TOPIC' THEN 1 END) AS topicRelationships,
       count(CASE WHEN type(r) = 'CITES' THEN 1 END) AS citations
'@
$existingGraph = Invoke-Stage8Neo4jStatement -Statement $graphValidationStatement `
    -Parameters @{} -Credential $neo4jCredential
$existingGraphRow = $existingGraph.results[0].data[0].row
$graphComplete = [long]$existingGraphRow[0] -eq 413000 -and
    [long]$existingGraphRow[1] -eq 100000 -and [long]$existingGraphRow[2] -eq 300000 -and
    [long]$existingGraphRow[3] -eq 10000 -and [long]$existingGraphRow[4] -eq 1000 -and
    [long]$existingGraphRow[5] -eq 2000 -and [long]$existingGraphRow[6] -eq 1000000 -and
    [long]$existingGraphRow[7] -eq 300000 -and [long]$existingGraphRow[8] -eq 300000 -and
    [long]$existingGraphRow[9] -eq 100000 -and [long]$existingGraphRow[10] -eq 200000 -and
    [long]$existingGraphRow[11] -eq 100000
if ($graphComplete) {
    Write-Host '阶段8容量数据校验通过：复用100,000条成果、413,000个图节点、1,000,000条图关系。'
    return
}
if ([long]$existingGraphRow[0] -ne 0 -or [long]$existingGraphRow[6] -ne 0) {
    throw "阶段8隔离Neo4j存在部分或不一致的受管数据：nodes=$($existingGraphRow[0])，relationships=$($existingGraphRow[6])。脚本不会删除或覆盖。"
}

Invoke-BatchedCypher 100000 `
    'UNWIND range($start,$end) AS n CREATE (:Achievement {businessId:n, aacvManaged:true, title:"Stage8 Achievement " + toString(n), achievementType:"article", language:"en", publicationDate:date("2020-01-01"), projectionVersion:1})' `
    'Achievement节点'
Invoke-BatchedCypher 300000 `
    'UNWIND range($start,$end) AS n CREATE (:Author {businessId:n, aacvManaged:true, name:"Stage8 Author " + toString(n), projectionVersion:1})' `
    'Author节点'
Invoke-BatchedCypher 10000 `
    'UNWIND range($start,$end) AS n CREATE (:Institution {businessId:n, aacvManaged:true, name:"Stage8 Institution " + toString(n), countryCode:"CN", projectionVersion:1})' `
    'Institution节点'
Invoke-BatchedCypher 1000 `
    'UNWIND range($start,$end) AS n CREATE (:Venue {businessId:n, aacvManaged:true, name:"Stage8 Venue " + toString(n), venueType:"journal", projectionVersion:1})' `
    'Venue节点'
Invoke-BatchedCypher 2000 `
    'UNWIND range($start,$end) AS n CREATE (:Topic {businessId:n, aacvManaged:true, name:"Stage8 Topic " + toString(n), projectionVersion:1})' `
    'Topic节点'

Invoke-BatchedCypher 300000 `
    'UNWIND range($start,$end) AS n MATCH (a:Achievement {businessId:toInteger(ceil(n / 3.0))}), (u:Author {businessId:n}) CREATE (u)-[:AUTHORED {aacvManaged:true, achievementBusinessId:toInteger(ceil(n / 3.0))}]->(a)' `
    'AUTHORED关系'
Invoke-BatchedCypher 300000 `
    'UNWIND range($start,$end) AS n MATCH (u:Author {businessId:n}), (i:Institution {businessId:((n - 1) % 10000) + 1}) CREATE (u)-[:AFFILIATED_WITH {aacvManaged:true, achievementBusinessId:toInteger(ceil(n / 3.0))}]->(i)' `
    'AFFILIATED_WITH关系'
Invoke-BatchedCypher 100000 `
    'UNWIND range($start,$end) AS n MATCH (a:Achievement {businessId:n}), (v:Venue {businessId:((n - 1) % 1000) + 1}) CREATE (a)-[:PUBLISHED_IN {aacvManaged:true, achievementBusinessId:n}]->(v)' `
    'PUBLISHED_IN关系'
Invoke-BatchedCypher 200000 `
    'UNWIND range($start,$end) AS n MATCH (a:Achievement {businessId:toInteger(ceil(n / 2.0))}), (t:Topic {businessId:((n - 1) % 2000) + 1}) CREATE (a)-[:HAS_TOPIC {aacvManaged:true, achievementBusinessId:toInteger(ceil(n / 2.0))}]->(t)' `
    'HAS_TOPIC关系'
Invoke-BatchedCypher 100000 `
    'UNWIND range($start,$end) AS n MATCH (a:Achievement {businessId:n}), (c:Achievement {businessId:(n % 100000) + 1}) CREATE (a)-[:CITES {aacvManaged:true, achievementBusinessId:n}]->(c)' `
    'CITES关系'

$validation = Invoke-Stage8Neo4jStatement -Statement $graphValidationStatement `
    -Parameters @{} -Credential $neo4jCredential
$row = $validation.results[0].data[0].row
if ([long]$row[0] -ne 413000 -or [long]$row[1] -ne 100000 -or
    [long]$row[2] -ne 300000 -or [long]$row[3] -ne 10000 -or
    [long]$row[4] -ne 1000 -or [long]$row[5] -ne 2000 -or
    [long]$row[6] -ne 1000000 -or [long]$row[7] -ne 300000 -or
    [long]$row[8] -ne 300000 -or [long]$row[9] -ne 100000 -or
    [long]$row[10] -ne 200000 -or [long]$row[11] -ne 100000) {
    throw "Neo4j容量数据校验失败：nodes=$($row[0])，relationships=$($row[6])。"
}
Write-Host '阶段8容量数据校验通过：100,000条成果、413,000个图节点、1,000,000条图关系。'
