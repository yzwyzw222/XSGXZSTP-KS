# 学术成果信息采集及可视化系统

本目录是仓库当前唯一业务系统，在 `dev` 维护。首次启动、数据库准备和手动建号统一从[仓库 README](../../README.md)开始，文档总入口见[项目文档](../../docs/README.md)。

## 当前能力

前后端分离；MySQL 保存业务数据，Neo4j 保存经事务 Outbox 异步生成、可重建的图投影。前端固定浅色研究主题，提供工作台、大屏、成果目录、实体编目、作者导入、统计、三类学术图谱、账号及操作/登录日志。

作者导入支持知网 XLSX、XLS、CSV。同批 1～10 份文件、总计最多 10 MB 和 2000 条记录；每份选择一个工作表，支持字段映射、预览、身份确认和原始列留存。本人署名、导师指导和成果机构分别建立真实关系，不根据姓名或机构猜测身份及任职。详见[作者导入](../../docs/author-import.md)。

三类图谱为独立模块，按作者与成果分页，共享 Cytoscape 画布。图谱最多 300 节点，Neo4j 查询超时 3 秒；合作证据只覆盖当前页，导师指导不等同共同署名。日期和逐篇机构来自已收录数据，详见[图谱口径](../../docs/academic-graphs.md)。

成果目录支持题名、作者、机构、期刊和主题检索及发表年份筛选；编目包含作者、机构、期刊、主题、专利、指导硕论和指导博论。导师由真实指导记录返回并按规范身份去重。CSV/JSON 导出使用已提交查询条件，单次最多 10000 条。

网络采集、来源管理、治理和质量页面及 API 已退役，对应无当前调用的实现已清理。已有来源指标、合并结果和数据表保留；ORCID 自动获取已停用，已有编号仍可显示。

## 技术与结构

依赖版本以 [pom.xml](backend/pom.xml)、[package.json](frontend/package.json) 和锁文件为准。主要技术为 Java 21、Spring Boot 4.1.1、MyBatis、Spring Security/Session JDBC、Flyway、Quartz、Spring Batch，以及 Vue 3、TypeScript、Vite、Element Plus、Pinia、ECharts 和 Cytoscape。

```text
systems/crawler/
├─ backend/
│  ├─ pom.xml
│  └─ src/
│     ├─ main/java/com/aacv/system/
│     │  ├─ authorimport/          作者导入
│     │  ├─ catalog/               检索与编目
│     │  ├─ graph/                 图投影、查询与维护
│     │  ├─ analytics/、export/    统计与导出
│     │  ├─ identity/、operations/ 账号权限、审计与运维
│     │  ├─ shared/                共享类型与错误处理
│     │  ├─ infrastructure/        数据库及通用 Batch/Quartz 配置
│     │  └─ source/、crawl/、ingestion/、authororcid/
│     │                            当前来源模型、到期清理及旧调度兼容
│     ├─ main/resources/
│     │  ├─ application.yml        应用配置
│     │  ├─ db/migration/          完整保留 Flyway V1～V18
│     │  ├─ mapper/                MyBatis SQL
│     │  └─ neo4j/schema/          图数据库约束
│     └─ test/                     后端测试
├─ frontend/
│  ├─ src/                         页面、组件、路由、接口、状态与样式
│  ├─ public/                      静态资源
│  └─ e2e/                         当前浏览器回归用例
├─ docs/openapi.yaml               接口契约
├─ tools/development/              独立开发工具
├─ tools/stage8/                   仍可用于隔离容量、备份与恢复的工具
├─ deploy/                        独立开发及隔离验证配置
└─ mvnw / mvnw.cmd                Maven Wrapper
```

`source` 只保留当前目录、统计、导出使用的数据类型；`crawl` 只保留数据库中旧 Job 类名的自清理实现；`ingestion` 只维护原始 Payload 到期清理；`authororcid` 只清理旧 ORCID 调度。通用调度配置已迁到 `infrastructure`，这些保留项的原因见[项目基线](../../docs/integration-baseline.md#业务数据与保留边界)。

## 开发与验证

集成模式使用网关 18000、后端 18083、路径 `/crawler`，Development 业务 Vite 为 5176。根 `start.bat` 使用既有构建产物；源码改动后按根 README 构建并重启对应组件。IDEA 使用正式后端 POM 手动建立本机配置，步骤见[开发说明](../../docs/development.md#idea-调试)。

在本目录运行前端测试与构建：

```powershell
npm.cmd --prefix frontend test
npm.cmd --prefix frontend run build -- --base=/crawler/
npm.cmd --prefix frontend run test:e2e
```

在 `backend` 运行 `..\mvnw.cmd -o -B test`。数据库集成测试使用隔离 Testcontainers，要求可用的 Docker Linux Engine。源码删除后应在停止对应应用后清理旧编译输出，或使用独立构建目录，不能让遗留 class 掩盖缺失引用。

`tools/development` 与 `deploy/compose.yaml` 保留独立调试流程，端口不同于整仓集成模式。`tools/stage8` 的容量、故障与恢复工具会操作隔离环境，执行前需要核对目标归属；已删除调用退役采集接口的真实来源验收脚本。旧阶段名称不代表必须依次执行这些工具。

本机配置、依赖、构建产物及真实数据库不随 Git 克隆提供。当前验证范围见[验证指南](../../docs/crawler-acceptance.md)，不要从历史记录推断本机已经就绪。
