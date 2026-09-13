# 项目文档

本目录按当前用途组织。首次准备环境从[仓库 README](../README.md)开始；系统范围以 `deploy/systems.json` 和当前源码为准。

| 文档 | 用途 |
| --- | --- |
| [集成基线](integration-baseline.md) | 指定项目记忆：当前架构、模块边界、数据及兼容约定 |
| [开发与运行](development.md) | 指定项目记忆：构建、启停、IDEA 配置、测试及来源校验 |
| [后端目录与文件职责](backend-source-guide.md) | 当前 Java 源码目录、逐文件作用、业务调用链与阅读顺序 |
| [作者导入](author-import.md) | 文件格式、预览确认、身份识别、关系与重复处理 |
| [三类学术图谱](academic-graphs.md) | 图谱口径、分页、时间线及兼容入口 |
| [认证与权限](unified-login.md) | 登录、会话、CSRF、账号与日志入口 |
| [ORCID 停用边界](author-orcid.md) | 已停用获取能力与必须保留的历史数据、调度兼容 |
| [验证指南](crawler-acceptance.md) | 当前系统需要执行的验证及证据边界 |
| [界面验收要点](../design-qa.md) | 当前浅色页面、响应式布局与图谱视觉检查 |
| [OpenAPI](../systems/crawler/docs/openapi.yaml) | 当前接口契约 |
| [历史与恢复索引](history.md) | 来源记录及既有本机恢复材料的位置 |

过期的多系统实施、分阶段验收及旧采集使用文档已从当前工作树移除。完整过程仍可从 Git 历史查阅；历史验收不作为当前版本已通过测试或正在运行的证据。
