# 历史与恢复索引

此文件只定位必要来源及恢复材料，不是当前启动步骤或实时运行报告。当前系统范围见[项目基线](integration-baseline.md)，操作入口见[开发说明](development.md)。

## 源码追溯

[import-records.json](import-records.json) 保留 crawler 以及已退役 relation、extraction、scholar 的固定来源树和导入记录；[source-adaptations.json](source-adaptations.json) 保留当前 crawler 的逐文件适配。不可变来源信息不随文档整理删除。

多阶段实施、采集调试和旧验收正文已从当前工作树移除。需要具体原始记录时使用 `git log --all -- <原路径>` 查找提交，再以 `git show <提交>:<原路径>` 只读查看；不要直接执行历史文档中的迁移、清库、容器或恢复命令。

## 已记录的本机恢复材料

以下路径来自既有项目记忆，本次未读取备份内容或重新校验文件完整性；它们被 Git 忽略，不随 clone 提供，是否仍存在应在实际恢复前核对。

| 历史事项 | 已记录位置 |
| --- | --- |
| 知网真实文件替换前的业务备份与恢复工具 | `.local/cnki-replacement-20260911/` |
| 单系统删除前的源码归档 | `.local/single-system-review-20260912/removed/` |
| ORCID 早期启用版本的恢复材料 | `.local/orcid-review-20260912-104302/` |
| 作者内部标识与导师显示运行验证 | `.local/author-id-advisor-review-20260912/` |
| Docker 数据盘原始备份及迁移记录 | `E:/docker/backup-20260911/docker_data.vhdx`、`E:/docker/migration-20260911.json` |
| Docker 修复及后端隔离验收材料 | `.local/docker-repair-20260911/` |

2026-09-11 的记录说明真实知网替换已经执行过；不能因为重建工程或拉取源码而重复替换业务数据。旧 MySQL、Neo4j 容器、数据卷及日志不属于本次源码清理范围。恢复前需另行核对目标归属、备份哈希和当前数据，不能从历史计数推断现状。
