# AACV System 阶段 4 验收记录

## 1. 验收范围

- 验收日期：2026-09-02；
- 实施范围：Crossref 受控 REST 适配器、双源最小通用化、V8 MySQL 数据治理、确定性融合、重复候选、人工决定与字段修正、质量指标、双源规范目录；
- 复用范围：阶段 3 的适配器契约、Quartz JDBC JobStore、Spring Batch、业务检查点、任务状态机、MyBatis 和目录模块；
- 明确非目标：Outbox、Neo4j 业务图投影/查询/对账、业务前端、服务器部署、模糊阈值、全文抓取、Crossref JATS/HTML 摘要标准化展示、Git 历史操作。

当前工作树原有项目文件整体为未跟踪状态。实施过程中未执行 Git 清理、重置、恢复、提交、分支、标签或推送，也未读取或输出凭据值。

## 2. 批次门禁结果

| 批次 | 主要结果 | 定向验证 |
| --- | --- | --- |
| 4.0 | 阶段 3 回归、Docker/Compose/Neo4j、本地健康组和 Crossref `rows=0` 接入门禁通过；公开池探测为 HTTP 200，响应当时声明每秒 1 个请求、并发 1 | 后端 `verify` 73 项通过；前端 2 项通过且构建通过；Compose 配置和 3 个健康组通过 |
| 4.1 | 增加 `CROSSREF` 固定身份、参数版本 2、通用适配器注册和来源感知 Batch/采集边界；参数版本 1 语义保持 | 32 项来源、任务、Batch 和阶段 3 兼容性测试通过 |
| 4.2 | 新增不可变 V8 迁移，包含双源约束、稳定标识、候选/决定/修订、字段来源/人工修正、质量指标和 Crossref 引用结构 | `FlywayMigrationTests` 3 项通过 |
| 4.3 | 实现 Crossref `/works` 客户端、解析器、门禁和传输；保持不透明游标、固定参数链、响应上限、限流和有限重试 | Crossref 契约及 OpenAlex 回归 27 项通过 |
| 4.4 | DOI、ORCID、ROR、ISSN 精确关联；无稳定标识只产生稳定候选；字段优先级与导入顺序无关 | 解析/标准化 7 项、双源融合 1 项、阶段 3 采集集成 1 项通过 |
| 4.5 | 先扩展 OpenAPI，再实现候选查询、接受/拒绝、逻辑合并、受控撤销和字段修正 API；不物理删除来源证据 | 治理单元 11 项、治理持久化 1 项通过 |
| 4.6 | 同页事务写入 11 项质量指标和有限问题样本，记录任务标识并支持按来源、运行查询 | 质量集成 1 项、Flyway 3 项通过 |
| 4.7 | 目录按规范实体和固定字段优先级聚合，返回字段来源/人工覆盖状态；支持 DOI 引用后到回填 | 采集、治理持久化、双源融合 3 项集成测试通过 |
| 4.8 | 补齐异常页、字段缺失、403、429、502/503/504、超大响应、调度模式、权限、CSRF、回滚、在线幂等等边界；同步文档 | Crossref/OpenAlex 边界 22 项、治理/安全/迁移/质量 23 项、调度/持久化/Batch 12 项、在线验收 1 项通过 |

## 3. Crossref 契约与在线证据

实现固定使用 `https://api.crossref.org`，请求方不能提交完整 URL、任意协议/主机/端口或重定向目标。首次游标为 `*`，后续游标按不透明字符串保存；同一游标链重复全部过滤、`rows` 和 `select` 参数。Crossref 每日任务使用 `CLOSED_INDEX_DATE_WINDOW`，不会使用游标不支持的发表日期排序。

可选 `CROSSREF_CONTACT_EMAIL` 只从当前进程读取，不写入数据库、日志、审计、错误响应或本验收记录。客户端动态读取限流响应头并在本地配置上取更保守边界；匿名公共池探测结果只是 2026-09-02 的外部快照，不是永久配置。

受控在线验收先用 OpenAlex 拉取最多 1 页/5 条并重复运行，再选取其中真实 DOI 创建 Crossref 最多 1 页/5 条任务并重复运行。首次 Crossref 运行因当前 `/works` 不接受 `select=language` 返回 HTTP 400，事务按预期回滚。经逐字段只读探测确认其他已选字段可用后，移除该可选选择字段并增加契约测试；重跑后四次 Batch 运行全部完成，相同 DOI 只形成一个规范成果、保留两条来源追溯，重复运行不新增成果。

在线验收没有在报告、测试输出或文档中记录真实 DOI、联系邮箱或完整游标。确定性构建仍以固定样例和 Testcontainers 为准，在线来源只提供当前兼容性证据。

## 4. 数据治理边界

- 自动关联：标准化 DOI、ORCID、ROR、ISSN/ISSN-L 及经验证的来源稳定标识；
- 只生成候选：无 DOI 成果、无 ORCID 作者、同名作者、纯文本机构及其他没有稳定标识的记录；
- 明确禁止：标题距离、姓名相似度、机构名称相似度、机器学习/向量匹配和任何未经标注样本验证的数值阈值自动合并；
- 可逆性：治理合并为逻辑规范关联，不删除原实体、原始记录或来源关系；候选决定和成果字段修正均保留修订、操作者、理由和乐观锁版本，并只允许受控撤销当前有效修订；
- 字段选择：人工修正最高优先；自动字段按明确来源/字段优先级选择，并以来源类型和来源记录 ID 作为稳定次序，避免导入顺序影响结果；
- 摘要边界：Crossref JATS/HTML 摘要只保留在受限原始 Payload，标准 `abstractText` 为 `null`。

## 5. 9.5 完整验证

| 命令或检查 | 结果 |
| --- | --- |
| `git status --short --branch` | `dev` 分支；项目文件整体仍为原有未跟踪状态，未执行清理或历史操作 |
| `docker version` | Docker Desktop 4.72.0、Client/Server 29.4.2、Linux/amd64 Engine 可用；沙箱内命名管道访问被拒后，按授权在沙箱外复验通过 |
| `docker compose -f .\deploy\compose.yaml config --quiet` | 子进程使用非真实占位环境变量，配置校验通过 |
| `docker compose -f .\deploy\compose.yaml up -d --no-recreate neo4j` | 既有 `aacv-neo4j` 保持运行，未重建容器或数据卷 |
| `docker compose -f .\deploy\compose.yaml ps` | `neo4j:5.26-community` 为 `healthy` |
| `.\mvnw.cmd -f .\backend\pom.xml verify` | 首轮 108 项中 1 项失败：应用启动测试仍期望 7 个迁移，实际正确为 8；更新测试基线并定向验证后，第二轮 108 项全部通过，JAR 打包成功 |
| `.\mvnw.cmd -f .\backend\pom.xml '-Dtest=AacvSystemApplicationTests' test` | 1 项通过，V1至V8迁移和 liveness/readiness/graph 隔离健康检查通过 |
| `npm --prefix .\frontend run test` | 沙箱内因 Vite `spawn EPERM` 被阻塞；按授权在沙箱外重跑，2 项全部通过 |
| `npm --prefix .\frontend run build` | 沙箱内因 Vite `spawn EPERM` 被阻塞；按授权在沙箱外重跑，Vue类型检查和Vite生产构建成功 |
| 三个本地 `Invoke-RestMethod` 健康检查 | `liveness`、`readiness`、`graph` 均返回 `{"status":"UP"}` |
| `.\mvnw.cmd -f .\backend\pom.xml '-Dtest=OpenApiDocumentTests' test` | 1 项通过，项目内 SnakeYAML 成功解析 OpenAPI 并验证阶段4路径边界 |
| Python `yaml.safe_load` 辅助检查 | 未执行成功：当前系统 Python 未安装 `PyYAML`；未擅自安装依赖，已由项目内 SnakeYAML 测试替代 |
| `git diff --check` 与受控源码扫描 | `git diff --check` 退出码为0；源码/文档未发现行尾空白、临时标记、硬编码凭据模式、模糊匹配实现或阶段5生产代码 |
| `git diff -- .\docs .\backend .\frontend .\deploy .\README.md` | 无输出，因为当前仓库 `git ls-files` 为空且项目文件整体未跟踪；不能把空 diff 当作无改动证据，最终文件范围改以本轮显式补丁、时间边界、源码复读和测试结果复核 |
| 最终 `git status --short --branch` | 仍为 `dev` 且保持原有未跟踪项目树；未产生 Git 历史或清理操作 |

上述差异、空白错误、敏感信息和工作树状态均在写入验收记录后再次复核；未跟踪工作树限制已明确保留，不以空 `git diff` 代替源文件审查。

## 6. 9.6 退出条件结论

阶段 4 已满足 9.6 的迁移、双源独立任务、游标/封闭窗口、顺序无关 DOI 融合、保守候选、固定字段来源、人工修正、可逆治理、质量定位、阶段3回归、在线小批量、前端回归/构建和文档同步要求。验收过程中发现的 `select=language` 外部契约变化与 Flyway V8 测试基线遗漏均已修复并完成定向及全量回归。阶段 4 退出门禁通过，工作停止在本阶段，不进入阶段 5。
