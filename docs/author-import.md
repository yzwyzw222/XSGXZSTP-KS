# 知网作者信息表导入

适用版本：2026-09-11 的 crawler 子系统。目录和集成地址继续使用 `systems/crawler`、`/crawler/`，relation 子系统保持现状。

## 使用流程

1. 进入“作者导入”（集成入口 `/crawler/author-import`）。ADMIN、DATA_OPERATOR 可以导入；RESEARCHER 可以检索成果和查看图谱。
2. 一次选择同一学者的本人署名成果表，以及可选的硕论、博论表。硕博文件必须按这位学者的导师姓名筛选导出；此约定已由用户确认，页面在确认导入处再次展示。无需预先填写姓名、机构或关系。
3. 系统从全部非学位论文和专利记录的完整作者交集中识别学者；交集唯一时自动选定，多人时仅需点选文件候选，不能任意填写姓名。没有共同作者、缺少完整作者列或只上传硕博文件时阻止确认，不按出现次数、第一作者或第一责任人猜测。补充指导成果时同时附上本人署名成果表，既有成果按原规则去重。
4. 支持 XLSX、二进制 XLS、HTML 表格形式的 XLS，以及 CSV。每份文件选择一个工作表，可分别调整工作表序号、表头行和映射；工作表从 1 开始。来源库为自动识别所必需，逐行区分期刊、会议、专利、硕士和博士；同一文件中的硕博记录自动分组。
5. 核对自动识别的学者、表内机构列表、文件关系及每份文件前 20 条记录和全部问题行。任何文件存在错误行都会阻止整批提交；缺少摘要或年份只给提示。文件、候选、工作表、表头行或映射改变后必须重新预览。
6. 确认导入后显示新增成果、补充关系和已存在成果数量。可进入该学者的两跳图谱；Neo4j 由后台 Outbox 同步，刚提交的数据可能需要稍后刷新。
7. 成果详情的“知网导入记录”显示文件、工作表、行号和全部原始列。最近导入页显示最近 20 个成功批次；单项成果显示最近 50 条导入来源记录。

## 已确认的表头

兼容用户提供的以下 18 个列名，也识别独立英文名及常见中文名。按英文前缀和中文别名自动映射，列顺序可调整。

| 知网导出列 | 解析用途 |
| --- | --- |
| SrcDatabase-来源库 | 识别期刊/会议论文、专利、硕士学位论文、博士学位论文 |
| Title-题名 | 成果题名，必需 |
| Author-作者 | 署名作者、专利发明人；指导模式下为学生 |
| Organ-单位 | 成果所属机构 |
| Source-文献来源 | 期刊或文献载体 |
| Keyword-关键词 | 主题节点与成果关联 |
| Summary-摘要 | 成果摘要，详情及图谱节点属性可查看 |
| PubTime-发表时间 | 发表日期，保留年/月/日精度 |
| FirstDuty-第一责任人 | 作者列为空时的回退，并提示署名可能不完整；不当作导师 |
| Fund-基金 | 原始字段保留 |
| Year-年 | 发表时间为空时使用年份 |
| Volume-卷 | 原始字段保留 |
| Period-期 | 原始字段保留 |
| PageCount-页码 | 原始字段保留 |
| CLC-中图分类号 | 原始字段保留 |
| ISSN-国际标准刊号 | 文献载体 ISSN |
| URL-网址 | 原始链接保留，不主动访问 |
| DOI-DOI | 规范化 DOI 与重复成果识别 |

所有原始列均保存，不限于映射列。作者、机构和关键词支持分号、中文分号、顿号及换行分隔；不按空格拆分，避免拆坏英文姓名和机构名称。CSV 支持引号、逗号/制表符、单元格内换行、UTF-8、UTF-16 BOM 和 GB18030。不能以此推断未提供的机构对应关系、摘要或专利信息。旧单文件 API 仍支持第一责任人回退；新多文件自动识别要求完整作者列。

## 关系与重复处理

- 署名作者 → 成果：`AUTHORED`。导师不会作为学位论文的署名作者，也不进入作者合作统计。
- 同批识别的学者导师 → 学位论文：`SUPERVISED`。依据为“同一学者、硕博按导师姓名筛选导出”的导入约定，审计记录 `relationshipBasis=SAME_SCHOLAR_ADVISOR_FILTER`；不是从来源库推导导师姓名。学者出现在学位论文作者中或已有 DOI 成果类型冲突时整批拒绝。
- 成果 → 表内单位：`PRODUCED_AT`。单位列表不能逐一对应作者时，不将全部单位分配给全部作者。
- 自动流程仅汇总表内机构并保存成果机构关系，不把所有单位赋给学者，也不从频次或发表时间猜测当前所属单位。旧单文件 API 仍保留手动确认机构的 `AFFILIATED_WITH` 行为；既有关系保留。
- 相同文件内容和设置重复确认返回原成功批次。DOI 优先识别成果；没有 DOI 时按规范化题名、成果类型、年份、排序后的完整署名生成标识。
- 已存在成果保留既有题名、摘要等内容，新增导入来源证据，按需补充导师或确认的机构关系。缺少 DOI、年份或作者信息会影响跨文件的重复识别，不能保证自动合并所有重复成果。
- 自动导入优先通过本人署名表中的共同成果复用已有作者；无共同成果时以姓名及本批本人署名成果标识建立身份，不只按姓名合并。没有共同成果的不同导出批次可能保留同名学者节点。旧单文件 API 的姓名、确认机构及可选 authorId 规则保留。共同作者仍按当前学者范围保存，可能保留同名共同作者节点。

## 边界与实现

自动流程一次最多 10 份文件，合计最多 10 MB、2000 条；每份文件最多 20 张工作表，本次选择其中一张。每表最多 100 列、32767 字符/单元格，表头位于前 20 行。单工作簿解压总量最多 40 MB、500 个压缩条目。公式必须先转换为值。重复文件内容与相同工作表、空文件、重复表头、非空的超出表头列、无效日期/DOI/网址、损坏或加密文件明确报错，不提交部分数据。旧单文件 API 保留原上限。

后端按 `authorimport/api → application → infrastructure` 分工；表格读取和行解析独立于事务写入。确认时重新解析并核对预览摘要，使用 MySQL 事务锁串行处理人工导入，60 秒事务超时；业务记录、原始字段、审计与图谱 Outbox 在同一事务内提交。导入失败整批回滚；网络超时可使用相同内容和设置重试。前端请求超时为 90 秒，取消页面请求不代表取消已进入后端的事务。

新增 [Apache POI 5.5.1](https://poi.apache.org/download.cgi) 和 [Apache Commons CSV 1.14.1](https://commons.apache.org/proper/commons-csv/)；均使用 Apache License 2.0，版本由后端 POM 固定。未更换框架或调整无关依赖。原始文件不落业务存储，仅保留单元格证据；上传过程使用 Servlet 临时文件并由请求生命周期释放。

Flyway V17 新增导入锁、身份、批次、成果标识、来源记录、导师、成果机构和确认的作者机构关系表；允许 `subject.source_id` 为空，以复用主题查询与投影。新关系已接入图谱查询、样式类型和投影完整性检查；目录/导出筛选可查询学者指导成果，机构统计包含导入机构。

数据源、采集任务、数据治理、质量指标四个模块的页面、路由、浏览器客户端和业务控制器已删除。保留历史来源与治理表，以及被目录、图谱、统计依赖的内部代码；不删除既有业务记录。旧 Quartz 采集 Job 仅注销自身，启动恢复监听与配额恢复调度不再装配。V1–V16 历史迁移不改写，V17 由新版启动时执行。

## 接口

完整契约位于 `systems/crawler/docs/openapi.yaml`，集成运行时加 `/crawler` 前缀。

| 方法与路径 | 用途 | 权限 |
| --- | --- | --- |
| POST `/api/v1/author-import/preview` | multipart：`file` 与 JSON 字符串 `options`；返回预览和 `previewKey` | AUTHOR_IMPORT、会话与 CSRF |
| POST `/api/v1/author-import/confirm` | 同一文件、设置和 `previewKey`；原子导入 | AUTHOR_IMPORT、会话与 CSRF |
| POST `/api/v1/author-import/files/preview` | 多个同名 multipart 字段 `files` 与 JSON 字符串 `options`；返回候选、自动关系与各文件预览 | AUTHOR_IMPORT、会话与 CSRF |
| POST `/api/v1/author-import/files/confirm` | 同一顺序的全部文件、设置与 `previewKey`；一个事务内提交全部文件 | AUTHOR_IMPORT、会话与 CSRF |
| GET `/api/v1/author-import` | 最近 20 个成功批次 | AUTHOR_IMPORT |
| GET `/api/v1/author-import/achievements/{id}/evidence` | 原始字段来源 | CATALOG_READ |

`options` 包含 `scholarName`、`scholarOrganization`、可选 `authorId`、零基 `sheetIndex`、从 1 开始的 `headerRow`、`mode`、`mapping`。`mode` 为 `AUTHOR`、`MASTER_SUPERVISION`、`DOCTOR_SUPERVISION`；映射值为零基列号，`-1` 表示不映射。未授权、无 CSRF、非法文件和身份冲突保留现有错误响应约定。

上段为兼容的单文件 API。新多文件 `options` 只包含可留空的 `scholarName`（仅限返回的候选）和按上传顺序排列的 `files` 设置；每项为 `sheetIndex`、`headerRow`、`mapping`。不接受手填机构或关系。预览摘要绑定文件内容、文件顺序与名称、工作表、映射和最终候选；确认时重新解析全部文件。先在事务锁内确定同一学者，再复用原保存逻辑按文件和关系类型写入批次，任一后续文件失败时早先文件、审计与 Outbox 同时回滚。未增加依赖、迁移或数据库表。

## 多文件自动识别验收（2026-09-11）

本轮按用户确认的“所有文件属于同一学者，硕博按导师姓名筛选导出”实现。实施前核对 `integration-baseline.md`、`development.md`、本文与源码，保留工作区既有未提交改动；两份项目记忆和本文已同步新流程与边界。原文档描述的是手动单文件流程，已改为页面的自动多文件流程，同时明确旧 API 的兼容行为。

本轮只新增或修改以下文件；路径位于仓库根目录。原 `AuthorImportIntegrationTests.java` 在拆分新增测试后与实施前适配哈希一致，未列为本轮变更。没有新建数据库迁移、安装依赖、提交 Git 或重启业务服务。

| 文件 | 变更 |
| --- | --- |
| `systems/crawler/backend/src/main/java/com/aacv/system/authorimport/domain/ImportBundle.java` | 新增多文件设置、预览及汇总契约 |
| `systems/crawler/backend/src/main/java/com/aacv/system/authorimport/application/ScholarBundleParser.java` | 新增文件作者交集、候选校验、来源库分类和分组 |
| `systems/crawler/backend/src/main/java/com/aacv/system/authorimport/application/ScholarImportParser.java` | 拆出不依赖预填学者的读取入口，复用原行解析 |
| `systems/crawler/backend/src/main/java/com/aacv/system/authorimport/application/AuthorImportService.java` | 多文件事务、基于成果的身份复用及指导依据审计 |
| `systems/crawler/backend/src/main/java/com/aacv/system/authorimport/api/AuthorImportController.java` | 新增多文件预览和确认路由，保留原路由 |
| `systems/crawler/backend/src/test/java/com/aacv/system/authorimport/ScholarBundleParserTests.java` | 新增识别、歧义、错误行、重复与边界测试 |
| `systems/crawler/backend/src/test/java/com/aacv/system/authorimport/AutomaticAuthorImportIntegrationTests.java` | 新增独立容器中的事务回滚、重复提交、署名指导分离及权限测试 |
| `systems/crawler/backend/src/test/java/com/aacv/system/OpenApiDocumentTests.java` | 核对新增 API 契约 |
| `systems/crawler/frontend/src/services/author-import.ts` | 多文件 multipart 客户端与类型 |
| `systems/crawler/frontend/src/services/author-import.test.ts` | 多文件顺序、设置与 CSRF 验证 |
| `systems/crawler/frontend/src/views/AuthorImportView.vue` | 取消三项预填，增加文件选择、识别结果、候选点选及分文件核对 |
| `systems/crawler/frontend/src/views/AuthorImportView.test.ts` | 覆盖无手填、候选选择、多文件和失效预览 |
| `systems/crawler/frontend/e2e/author-import.spec.ts` | 桌面及手机多文件导入到图谱的交互验收 |
| `systems/crawler/frontend/e2e/page-layout.spec.ts` | 按新流程检查上传后可用的工作表设置 |
| `systems/crawler/docs/openapi.yaml` | 新增多文件接口、限制与关系依据 |
| `systems/crawler/README.md` | 更新作者导入操作说明 |
| `docs/author-import.md` | 更新使用规则、实现边界及本轮验收 |
| `docs/development.md` | 同步自动识别、接口和运行状态边界 |
| `docs/integration-baseline.md` | 同步当前源码与业务实例的区别 |
| `docs/source-adaptations.json` | 仅同步本轮 16 个子系统文件的适配哈希 |

以下后端命令在 `systems/crawler` 执行，前端命令在 `systems/crawler/frontend` 执行，其余在仓库根目录执行。

| 实际执行命令 | 结果 |
| --- | --- |
| `.\mvnw.cmd -o -f backend/pom.xml '-Dtest=ScholarBundleParserTests,ScholarImportParserTests' test -q` | 12 项通过。首轮发现新增静态方法引用使用方式错误，修正后复测通过。 |
| `.\mvnw.cmd -o -f backend/pom.xml '-DargLine=-Xmx512m -XX:MaxMetaspaceSize=256m' '-Dtest=AuthorImportIntegrationTests,ScholarBundleParserTests,ScholarImportParserTests,OpenApiDocumentTests' test -q` | 首轮 24 项中 1 项失败：新增测试数据影响原全库统计断言。随后将新增用例移到独立测试类和容器，保留原断言。 |
| `.\mvnw.cmd -o -f backend/pom.xml '-DargLine=-Xmx512m -XX:MaxMetaspaceSize=256m' '-Dtest=AutomaticAuthorImportIntegrationTests,AuthorImportIntegrationTests,ScholarBundleParserTests,ScholarImportParserTests,OpenApiDocumentTests' test -q` | 最终 24 项全部通过，0 失败、0 错误、0 跳过。包括原 MySQL/Neo4j 导入链路、独立的多文件确认与整批回滚。 |
| `npm.cmd test -- src/views/AuthorImportView.test.ts src/services/author-import.test.ts` | 8 项通过。受限环境首次报 Vite 子进程 `spawn EPERM` 和原生依赖加载失败，正常本机权限复测通过，未改变依赖。 |
| `npm.cmd exec -- vue-tsc -b` | 类型检查通过。 |
| `npm.cmd run build -- --base=/crawler/ --outDir ../../../../.local/author-auto-import-verification/dist` | 类型检查及生产构建通过，产物在 `E:\Program\Java\course_design\.local\author-auto-import-verification\dist`，没有覆盖运行服务的 dist。保留大于 500 kB 的现有分块警告。 |
| `npm.cmd run test:e2e -- e2e/author-import.spec.ts` | 3 项通过，覆盖 1440/390 像素多文件导入、无预填及问题行拦截。 |
| `npm.cmd run test:e2e -- e2e/page-layout.spec.ts -g '1440'` | 2 项通过，覆盖页面布局及长表格操作。 |
| `npm.cmd run test:e2e -- e2e/author-import.spec.ts --output=../../../.local/author-auto-import-browser` | 3 项通过，截图另存仓库 `.local/author-auto-import-browser`；已视觉检查桌面和手机预览。数据为测试样例。 |
| `git -c core.safecrlf=false diff --check` | 通过。 |
| `node scripts/check-source.mjs` | relation、crawler 全部文件和适配哈希通过；整仓最终仍因历史 scholar 对象 `f11b0b8d99f37e8e971aa86661d0ba59a21fbd9d` 缺失而退出 1，为既有历史对象问题。未跳过校验或改写来源记录。 |

真实知网 Excel 尚未提供，本轮不声称完成用户实际文件验收。姓名交集为空或多解、不同批次没有共同成果、仅有硕博文件、无法对应具体作者的机构仍按前文边界处理。本轮构建与容器验收没有更新业务 JAR、业务数据库或正在提供服务的前端；新页面需要按已有本机运行流程重建、启动后才会在业务实例生效。

## 运行切换

功能实施与隔离验收阶段没有切换真实业务实例。2026-09-11 随后按用户试用要求，已备份本机 `course_crawler`、启动当前 JAR，并确认真实业务库由 V16 升级到 V17；门户和两个后端健康检查通过。该次备份及状态记录在 `.local/review-start-20260911-171852/`，详情见 [本机试用启动](development.md#2026-09-11-本机试用启动)。其他实例应用新版前仍应先备份业务数据库，继续使用已有运行配置，无需重建数据库或重置账号。

在仓库根目录使用现有脚本停止 crawler 后，只重建本次子系统，再按原模式启动；crawler 停止期间，统一登录的身份确认会暂时不可用。前端构建必须保留集成路径：

```powershell
pwsh -NoProfile -File scripts/Stop-Integration.ps1 -System crawler
npm.cmd --prefix systems/crawler/frontend run build -- --base=/crawler/
Push-Location systems/crawler
.\mvnw.cmd -f backend/pom.xml -DskipTests package
Pop-Location
pwsh -NoProfile -File scripts/Start-Integration.ps1 -System crawler -Mode Demo
```

开发模式的最后一步使用 `-Mode Development`，与当前门户模式保持一致。启动后 Flyway 自动执行 V17；先核对健康检查并退出后重新登录，让会话取得新增 AUTHOR_IMPORT 权限，再导入少量已核对的知网记录。既有示例、采集可靠性及历史验收文档只描述对应旧版本。

## 功能实施阶段验证记录

以下结果来自本轮实际执行。浏览器上传内容和截图均为合成测试资料，不是用户真实知网文件。

| 命令与工作目录 | 结果 |
| --- | --- |
| crawler：`.\mvnw.cmd -f backend/pom.xml -DskipTests compile -q` | 编译通过；首次 Maven 缓存权限问题在正常本机权限下重试后解决 |
| crawler：`.\mvnw.cmd -f backend/pom.xml '-DargLine=-Xmx512m -XX:MaxMetaspaceSize=256m' '-Dtest=ScholarImportParserTests,CatalogExportFilterSqlTests,OpenApiDocumentTests' test -q` | 7 项解析、4 项目录/导出 SQL、1 项 OpenAPI 检查通过 |
| frontend：`npm.cmd test -- --maxWorkers=1` | 36 个文件、185 个用例通过；包括预览失效、非法文件、错误行、请求取消、权限导航及 multipart/CSRF |
| frontend：`npm.cmd run build -- --base=/crawler/` | 类型检查和集成子路径构建通过；保留现有大于 500 kB 的图表资源提示 |
| frontend：`npm.cmd run test:e2e -- e2e/author-import.spec.ts e2e/ux-workflows.spec.ts e2e/graph-overview.spec.ts` | 首轮 14 项通过、2 项新流程失败；排查修复下拉框定位和预览面板压缩后，导入流程已通过下述复测 |
| frontend：`npm.cmd run test:e2e -- e2e/author-import.spec.ts e2e/page-layout.spec.ts e2e/research-dashboard.spec.ts` | 11 项通过；导入和布局的 6 项失败随后修复，下述复测覆盖这些失败项 |
| frontend：`npm.cmd run test:e2e -- e2e/author-import.spec.ts e2e/page-layout.spec.ts` | 10 项全部通过；包含 1440/390 像素导入到指导图谱、旧路由失效、4 种视口布局及长表格操作 |
| 根目录：`node --check scripts/Test-PlatformBrowser.mjs` | 语法检查通过；门户实测需新版后端启动后另行执行 |
| 根目录：`node scripts/check-source.mjs` | relation、crawler 的来源、祖先和适配校验通过；完整检查因既有 retired scholar 的 Git 对象 `f11b0b8d...` 缺失而失败，未修改历史记录或绕过校验 |
| 根目录：`git -c core.safecrlf=false diff --check` | 通过，无空白错误；保留项目现有 CRLF 策略 |

后端集成测试曾成功启动 MySQL 8.0.42 与 Neo4j 5.26，6 个用例中 4 个数据用例通过，验证了论文、专利、两类学位论文、Outbox 投影、重复导入、并发去重及整批回滚；另外 2 项权限用例因 MockMvc 未接入测试认证上下文而失败，已按项目既有模式修正。此后补充了真实 multipart、导师类型冲突和投影完整性/统计断言，共 8 个导入集成用例。

该阶段执行 `.\mvnw.cmd -f backend/pom.xml '-DargLine=-Xmx512m -XX:MaxMetaspaceSize=256m' '-Dtest=AuthorImportIntegrationTests,ScholarImportParserTests,CatalogExportFilterSqlTests,OpenApiDocumentTests' test -q` 时，C 盘空间不足导致 Docker 镜像存储报 `input/output error`，随后 `docker image inspect testcontainers/ryuk:0.14.0 --format '{{.Id}}'` 返回 `Docker Desktop is unable to start`，当时未能完成最终数据库验收。真实 JAR 还在运行，`clean test` 清理其 JAR 时曾遇到 Windows 文件占用；仅清理已核对路径的生成类文件后继续测试，没有停止应用。此处保留失败历史；Docker 阻塞已解除，后续实际验收结果见下节。

该阶段的测试临时目录在命令进程内设为仓库忽略的 `.local/author-import-test-temp`，未修改系统级环境变量。Docker 修复后已在 crawler 目录执行以下重点后端验收；本次临时目录改用 `.local/docker-repair-20260911/temp`：

```powershell
.\mvnw.cmd -f backend/pom.xml '-DargLine=-Xmx512m -XX:MaxMetaspaceSize=256m' '-Dtest=AuthorImportIntegrationTests,ScholarImportParserTests,CatalogExportFilterSqlTests,OpenApiDocumentTests,SecurityIntegrationTests,FlywayMigrationTests,GraphProjectionIntegrationTests,GraphQueryIntegrationTests' test
```

其中 Flyway 测试验证从历史版本升级并保留旧数据，该测试阶段没有对真实业务库执行迁移；后续本机试用升级见上方运行切换记录。真实知网导出文件尚未提供，当前兼容性依据用户确认的表头和合成 XLSX/XLS/CSV 用例。

## 2026-09-11 Docker 修复后完整后端验收

Docker 数据盘与系统盘均位于 `E:\docker\data`。元数据库、文件系统和受损镜像内容已在整盘副本中修复，切换前全部数据卷文件校验与原值一致；原运行盘和迁移前备份均保留。两个项目 Neo4j 容器已恢复健康，原图库认证只读查询通过。

作者导入 8 项集成用例、7 项解析、4 项目录/导出 SQL、27 项权限、5 项 Flyway 历史升级、2 项图投影、10 项图查询以及 1 项 OpenAPI 均已通过。覆盖事务回滚、重复及并发导入、真实会话与 multipart/CSRF、旧接口退场、导师类型冲突、图谱一致性及统计；会话和文件接口使用 Spring 集成测试，文件为合成测试资料。

crawler 默认完整套件执行 67 个测试类/264 项，发现并修正两处旧迁移计数和一处旧自动采集触发器预期；全部失败项已定向复测通过。另通过十万条记录查询及数据库/图服务故障恢复两项专项，最终覆盖 266 项不同测试并完成打包。relation 使用隔离 MySQL/Neo4j，先通过真实 HTTP 登录与图同步验证，再完成 12 个测试类/81 项测试和打包。统一网关 18 项、来源适配 2 项测试通过。首轮失败与最终复测记录分别保留，没有删除或跳过用例。

完整命令、存储恢复证据、查询测量及本轮文件清单见 [Docker 存储修复与后端验收](backend-acceptance-20260911.md)。两个有效系统的来源和适配哈希检查通过；整仓历史校验仍受既有 retired scholar Git 对象缺失影响。该轮验收使用隔离库，未替换业务实例，也未对真实业务库执行 V17 升级；之后的本机试用启动与升级单独记录在上方运行切换章节。

## 本轮文件范围

以下清单以实施前工作区快照为基准，不把已有未提交改动归入本轮。旧模块包含的未提交内容保存在本机忽略的 `.local/author-import-before-20260911`；没有创建 Git 提交、分支或执行推送。

### 文档与验证脚本

| 操作 | 文件 |
| --- | --- |
| 修改 | `README.md` |
| 新增 | `docs/author-import.md` |
| 修改 | `docs/development.md` |
| 修改 | `docs/integration-baseline.md` |
| 修改 | `docs/source-adaptations.json` |
| 修改 | `scripts/Test-PlatformBrowser.mjs` |
| 修改 | `systems/crawler/README.md` |
| 修改 | `systems/crawler/docs/openapi.yaml` |

### 后端

| 操作 | 文件 |
| --- | --- |
| 修改 | `systems/crawler/backend/pom.xml` |
| 新增 | `systems/crawler/backend/src/main/java/com/aacv/system/authorimport/api/AuthorImportController.java` |
| 新增 | `systems/crawler/backend/src/main/java/com/aacv/system/authorimport/application/AuthorImportService.java` |
| 新增 | `systems/crawler/backend/src/main/java/com/aacv/system/authorimport/application/ScholarImportParser.java` |
| 新增 | `systems/crawler/backend/src/main/java/com/aacv/system/authorimport/domain/ImportOptions.java` |
| 新增 | `systems/crawler/backend/src/main/java/com/aacv/system/authorimport/domain/ImportPreview.java` |
| 新增 | `systems/crawler/backend/src/main/java/com/aacv/system/authorimport/domain/ImportRow.java` |
| 新增 | `systems/crawler/backend/src/main/java/com/aacv/system/authorimport/domain/ImportSummary.java` |
| 新增 | `systems/crawler/backend/src/main/java/com/aacv/system/authorimport/infrastructure/AuthorImportMapper.java` |
| 新增 | `systems/crawler/backend/src/main/java/com/aacv/system/authorimport/infrastructure/ImportId.java` |
| 新增 | `systems/crawler/backend/src/main/java/com/aacv/system/authorimport/infrastructure/ImportUploadConfiguration.java` |
| 新增 | `systems/crawler/backend/src/main/java/com/aacv/system/authorimport/infrastructure/ScholarTableReader.java` |
| 删除 | `systems/crawler/backend/src/main/java/com/aacv/system/crawl/api/CrawlTaskController.java` |
| 修改 | `systems/crawler/backend/src/main/java/com/aacv/system/crawl/application/CrawlRecoveryService.java` |
| 修改 | `systems/crawler/backend/src/main/java/com/aacv/system/crawl/infrastructure/quartz/QuartzCrawlTriggerJob.java` |
| 删除 | `systems/crawler/backend/src/main/java/com/aacv/system/crawl/infrastructure/quartz/QuartzQuotaResumeConfiguration.java` |
| 修改 | `systems/crawler/backend/src/main/java/com/aacv/system/crawl/infrastructure/quartz/QuartzQuotaResumeJob.java` |
| 删除 | `systems/crawler/backend/src/main/java/com/aacv/system/governance/api/AchievementFieldOverrideController.java` |
| 删除 | `systems/crawler/backend/src/main/java/com/aacv/system/governance/api/DuplicateCandidateController.java` |
| 删除 | `systems/crawler/backend/src/main/java/com/aacv/system/governance/api/MergeDecisionController.java` |
| 修改 | `systems/crawler/backend/src/main/java/com/aacv/system/graph/application/GraphQueryService.java` |
| 修改 | `systems/crawler/backend/src/main/java/com/aacv/system/graph/domain/GraphAchievementSnapshot.java` |
| 修改 | `systems/crawler/backend/src/main/java/com/aacv/system/graph/domain/GraphRelationshipType.java` |
| 修改 | `systems/crawler/backend/src/main/java/com/aacv/system/graph/infrastructure/neo4j/Neo4jProjectionInspector.java` |
| 修改 | `systems/crawler/backend/src/main/java/com/aacv/system/graph/infrastructure/neo4j/Neo4jProjectionTransaction.java` |
| 修改 | `systems/crawler/backend/src/main/java/com/aacv/system/graph/infrastructure/persistence/GraphSnapshotMapper.java` |
| 修改 | `systems/crawler/backend/src/main/java/com/aacv/system/graph/infrastructure/persistence/MyBatisGraphSnapshotReader.java` |
| 修改 | `systems/crawler/backend/src/main/java/com/aacv/system/identity/domain/AuthorizationPolicy.java` |
| 修改 | `systems/crawler/backend/src/main/java/com/aacv/system/identity/domain/Permission.java` |
| 修改 | `systems/crawler/backend/src/main/java/com/aacv/system/operations/domain/AuditAction.java` |
| 删除 | `systems/crawler/backend/src/main/java/com/aacv/system/quality/api/QualityMetricController.java` |
| 修改 | `systems/crawler/backend/src/main/java/com/aacv/system/shared/infrastructure/web/ApiExceptionHandler.java` |
| 删除 | `systems/crawler/backend/src/main/java/com/aacv/system/source/api/DataSourceController.java` |
| 删除 | `systems/crawler/backend/src/main/java/com/aacv/system/source/api/SourceEntityController.java` |
| 新增 | `systems/crawler/backend/src/main/resources/db/migration/V17__author_import.sql` |
| 修改 | `systems/crawler/backend/src/main/resources/mapper/analytics/AnalyticsMapper.xml` |
| 新增 | `systems/crawler/backend/src/main/resources/mapper/authorimport/AuthorImportMapper.xml` |
| 修改 | `systems/crawler/backend/src/main/resources/mapper/catalog/CatalogMapper.xml` |
| 修改 | `systems/crawler/backend/src/main/resources/mapper/graph/GraphSnapshotMapper.xml` |
| 修改 | `systems/crawler/backend/src/test/java/com/aacv/system/OpenApiDocumentTests.java` |
| 新增 | `systems/crawler/backend/src/test/java/com/aacv/system/authorimport/AuthorImportIntegrationTests.java` |
| 新增 | `systems/crawler/backend/src/test/java/com/aacv/system/authorimport/ScholarImportParserTests.java` |
| 修改 | `systems/crawler/backend/src/test/java/com/aacv/system/identity/api/SecurityIntegrationTests.java` |
| 修改 | `systems/crawler/backend/src/test/java/com/aacv/system/infrastructure/database/FlywayMigrationTests.java` |
| 删除 | `systems/crawler/backend/src/test/java/com/aacv/system/source/application/SourceEntityServiceTests.java` |

### 前端

| 操作 | 文件 |
| --- | --- |
| 新增 | `systems/crawler/frontend/e2e/author-import.spec.ts` |
| 删除 | `systems/crawler/frontend/e2e/crawl-entity-names.spec.ts` |
| 修改 | `systems/crawler/frontend/e2e/fixtures/workbench.ts` |
| 修改 | `systems/crawler/frontend/e2e/migration-visual.spec.ts` |
| 删除 | `systems/crawler/frontend/e2e/optimization-crawl.spec.ts` |
| 删除 | `systems/crawler/frontend/e2e/optimization-governance.spec.ts` |
| 修改 | `systems/crawler/frontend/e2e/page-layout.spec.ts` |
| 修改 | `systems/crawler/frontend/e2e/redesign-interactions.spec.ts` |
| 修改 | `systems/crawler/frontend/e2e/research-dashboard.spec.ts` |
| 修改 | `systems/crawler/frontend/e2e/stage6.spec.ts` |
| 修改 | `systems/crawler/frontend/e2e/ux-workflows.spec.ts` |
| 修改 | `systems/crawler/frontend/src/components/business/AppSidebar.test.ts` |
| 删除 | `systems/crawler/frontend/src/components/business/CandidateComparisonPanel.vue` |
| 新增 | `systems/crawler/frontend/src/components/business/ImportEvidencePanel.vue` |
| 删除 | `systems/crawler/frontend/src/components/business/SourceEntitySelect.test.ts` |
| 删除 | `systems/crawler/frontend/src/components/business/SourceEntitySelect.vue` |
| 删除 | `systems/crawler/frontend/src/components/business/crawl/CrawlRunDialog.vue` |
| 删除 | `systems/crawler/frontend/src/components/business/crawl/CrawlScheduleDialog.vue` |
| 删除 | `systems/crawler/frontend/src/components/business/crawl/CrawlTaskDialog.vue` |
| 修改 | `systems/crawler/frontend/src/components/business/graph-overview/NodeDetail.vue` |
| 修改 | `systems/crawler/frontend/src/composables/useDashboard.test.ts` |
| 修改 | `systems/crawler/frontend/src/composables/useDashboard.ts` |
| 删除 | `systems/crawler/frontend/src/composables/useDataSources.ts` |
| 修改 | `systems/crawler/frontend/src/config/nav.ts` |
| 修改 | `systems/crawler/frontend/src/router/index.test.ts` |
| 修改 | `systems/crawler/frontend/src/router/index.ts` |
| 新增 | `systems/crawler/frontend/src/services/author-import.test.ts` |
| 新增 | `systems/crawler/frontend/src/services/author-import.ts` |
| 修改 | `systems/crawler/frontend/src/services/business.test.ts` |
| 修改 | `systems/crawler/frontend/src/services/business.ts` |
| 修改 | `systems/crawler/frontend/src/types/api.ts` |
| 修改 | `systems/crawler/frontend/src/utils/audit.ts` |
| 修改 | `systems/crawler/frontend/src/utils/filter-options.ts` |
| 修改 | `systems/crawler/frontend/src/utils/graph-presentation.ts` |
| 修改 | `systems/crawler/frontend/src/utils/graph-query.ts` |
| 修改 | `systems/crawler/frontend/src/utils/graph.ts` |
| 修改 | `systems/crawler/frontend/src/views/AchievementDetailView.vue` |
| 新增 | `systems/crawler/frontend/src/views/AuthorImportView.test.ts` |
| 新增 | `systems/crawler/frontend/src/views/AuthorImportView.vue` |
| 修改 | `systems/crawler/frontend/src/views/CatalogView.vue` |
| 删除 | `systems/crawler/frontend/src/views/CrawlTasksView.test.ts` |
| 删除 | `systems/crawler/frontend/src/views/CrawlTasksView.vue` |
| 删除 | `systems/crawler/frontend/src/views/GovernanceView.vue` |
| 修改 | `systems/crawler/frontend/src/views/GraphExploreView.vue` |
| 修改 | `systems/crawler/frontend/src/views/GraphTypesView.vue` |
| 修改 | `systems/crawler/frontend/src/views/OverviewView.vue` |
| 删除 | `systems/crawler/frontend/src/views/QualityView.vue` |
| 删除 | `systems/crawler/frontend/src/views/SourcesView.vue` |
| 修改 | `systems/crawler/frontend/src/views/WorkbenchView.vue` |
