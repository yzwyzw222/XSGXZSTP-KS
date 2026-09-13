# 项目基线

核对日期：2026-09-13。本文件与 [development.md](development.md) 是仓库指定项目记忆，记录当前源码约定；运行状态必须另行验证。

## 系统范围与结构

当前唯一业务系统为“学术成果信息采集及可视化系统”，目录 `systems/crawler`。运行清单由 [deploy/systems.json](../deploy/systems.json) 定义。一个 Vue 前端、一个 Java 21 / Spring Boot 后端构成业务应用；根目录 `scripts/server.mjs` 复用前端 Vite 依赖提供本机访问网关。

`relation`、`extraction`、`scholar` 和独立门户均已退役。`portalPort`、网关进程标识 `portal`、`PORTAL_SESSION` 及 `/__integration/*` 是兼容名称，不代表其他业务系统。

前端按页面、组件、路由、services、stores 和 composables 组织。后端主要模块为 `authorimport`、`catalog`、`graph`、`analytics`、`export`、`identity`、`operations`、`shared`、`infrastructure`，通常按 api、application、domain、infrastructure 分层。

通用 Batch 和 Quartz 配置位于 `infrastructure/batch`、`infrastructure/quartz`。图谱维护使用 `batchJobOperator`；线程池大小、队列和关闭等待沿用原设置。不能删除通用调度配置来清理旧采集功能。

## 业务数据与保留边界

MySQL 保存权威业务数据，Neo4j 保存可重建的图投影。作者导入在同一 MySQL 事务写入业务记录与 Outbox；Quartz 消费 Outbox 后投影到 Neo4j。图谱可能稍后反映刚提交的数据。

Flyway 迁移完整保留 V1～V18，不改写已执行迁移、不删除历史业务表、账号或数据。现有治理合并结果、来源证据、来源统计及运维查询继续读取历史表。

已删除数据源管理、OpenAlex/Crossref 网络适配、采集调度与解析入库、治理和质量服务的退役实现及专用测试；当前路由、Controller 和 OpenAPI 均不提供这些入口。仅保留以下有当前用途的代码：

- `source/domain/SourceType`、`ScholarlyMetadata`：目录、统计、导出及已有来源指标的数据类型。
- `crawl/infrastructure/quartz/QuartzCrawlTriggerJob`、`QuartzQuotaResumeJob`：兼容数据库持久化类名，仅删除自己的旧调度。
- `ingestion`：只保留原始 Payload 到期清理服务、最小 MyBatis 接口及既有 Quartz 任务；每日 UTC 03:15、每批 500 条，清空过期 Payload，保留原始记录、哈希和成果证据链接。
- `authororcid`：只清理旧 ORCID 调度。V18、历史候选、已有外部编号及相关审计仍保留。

数据库中的旧权限编码和审计动作可用于读取历史记录，不因页面退役而删除。源码清理不改变现有认证与授权规则。

## 当前页面与交互

工作台、大屏、成果目录、实体编目、作者导入、统计、三类学术图谱、账号和日志位于同一前端。页面固定浅色：`index.html` 提供首帧浅色，`useTheme.ts` 统一迁移旧主题偏好为 `light`；共享样式位于 `src/styles`。工作台与大屏数据读取受权限约束，统计与导入记录分别处理加载失败。

学术关系、成果和背景使用独立路径 `/academic-relations`、`/academic-achievements`、`/academic-background`，共享作者条件与画布。合作只依据同篇成果真实 `AUTHORED`，指导使用 `SUPERVISED`，成果机构使用 `PRODUCED_AT`，不从指导推断合作或从机构推断当前任职。

作者图谱先按作品分页再补共同作者；最多 300 节点、Neo4j 查询超时 3 秒。背景按真实发表日期排序，空日期末尾；机构来自本篇成果及对应本篇的署名关系，最多 100 家并提示截断。详细口径见 [academic-graphs.md](academic-graphs.md)。

成果目录采用右上角紧凑字段搜索与发表年份筛选，CSV/JSON 导出沿用已提交查询条件。实体编目包含作者、机构、期刊、主题、专利和指导硕博；指导硕博必须存在 `achievement_advisor`，`advisors` 按规范作者身份去重，多人姓名并列展示，不从论文署名推断导师。

作者导入支持 XLSX、XLS 和 CSV，多文件同批提交、校验预览、幂等去重及原始列留存。身份识别与关系规则见 [author-import.md](author-import.md)。ORCID 后台获取和绑定 API 已移除，作者内部标识及已有 ORCID 保留。

## 运行、认证与管理

网关 `18000` 的根地址转入 `/crawler/`，未登录由业务前端进入 `/crawler/login`。后端为 `18083 /crawler`；Development 额外使用 `5176` 的业务 Vite。Demo 使用正式 `frontend/dist` 和后端 JAR，不自动构建。

登录会话、CSRF、Origin 校验、会话轮换和后端权限判断保留。账号管理位于 `/crawler/users`；日志管理位于 `/crawler/logs`，仅保留操作与登录日志。旧管理、请求日志和运维书签安全跳转，`/__integration/platform/logs` 已移除；磁盘旧日志不自动删除。

角色编码为 `ADMIN`、`DATA_OPERATOR`、`RESEARCHER`，展示为管理员、数据运营人员、科研用户。新环境仅初始化表结构、角色和字典，不自动创建应用账号或导入业务数据。

IDEA 配置不随仓库提供；按 [开发说明](development.md#idea-调试)从正式 POM 创建本机运行配置。`.idea/` 是用户本机工作区，不把固定模块名或不存在的 `.run/CrawlerBackend.run.xml` 当成共享配置。

## 来源、文档与验证

有效来源仍为 feature/Luo 的 `3a0127019052c182962d6630981e0acec2d66d53`。[import-records.json](import-records.json) 保留不可变来源证据；[source-adaptations.json](source-adaptations.json) 记录当前 crawler 的修改、新增和删除。源码删除也必须同步适配记录，不能通过跳过来源校验掩盖差异。

当前文档从 [文档索引](README.md)进入。多阶段过程已从现行说明移除，必要恢复材料见 [历史索引](history.md)。本机凭据、数据与验收产物留在被忽略的 `.local/`，不放入项目记忆。测试方式见 [验证指南](crawler-acceptance.md)，历史结果不能替代当前验证。
