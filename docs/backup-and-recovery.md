# 备份与恢复说明

## 当前决策

2026-09-03，用户决定将备份静态加密移出本地阶段8开发计划和验收范围。该决定不放宽凭据保护、目录访问控制、完整性校验和安全传输要求。备份与隔离恢复工具已经实现，但`E:\AACV_System_Backups`尚未创建，也未执行MySQL业务备份及恢复演练；备份恢复本身仍是阶段8退出门禁，不得标记为通过。

## 已冻结的恢复边界

- MySQL是唯一权威业务数据源；临时CSV/JSON导出不是备份。
- 目标仍为每日逻辑备份、7个每日副本、4个每周副本、RPO 24小时和RTO 4小时。
- 每个备份必须生成SHA-256并在恢复前复验；校验和不能替代目录访问控制或安全传输。
- 恢复只能创建名称明确的隔离数据库，绝不覆盖`aacv_system`。
- 恢复验证至少覆盖Flyway V1至V11、表数量、关键业务计数、抽样实体、审计和Outbox。
- Neo4j不作为权威备份；MySQL恢复后使用受控回填或全量重建生成投影，并对账关系数和同步状态。
- 不删除数据库、临时恢复环境或Docker卷；任何删除必须另行取得明确授权。

## 实施前置条件

继续8.5前必须检查`E:\AACV_System_Backups`的目标路径、剩余容量、写权限和访问控制，并确认使用与MySQL 8.0基线兼容的客户端工具。密码只允许由用户通过`Get-Credential`输入并注入当前进程环境变量；备份脚本、命令行、日志和文档均不得包含凭据。备份必须生成并复核SHA-256，恢复必须写入名称明确的隔离数据库且不得覆盖`aacv_system`。在完成实际备份、恢复和一致性检查前，备份时间、恢复时间、RPO和RTO保持“未验证”。

备份静态加密不是本地阶段8的实施项。若备份需要复制到其他主机或介质，仍必须使用受控访问和安全传输方式；该传输要求不等同于本地静态加密验收。

## 工具与隔离边界

- `New-Stage8DatabaseBackup.ps1`默认备份本机`127.0.0.1:3306/aacv_system`，使用`mysqldump --single-transaction --quick`，先写`.partial`再原子改名，并生成`.sha256`和`.metadata.json`。
- 首次执行必须显式使用`-InitializeBackupRoot`。脚本只创建固定目录`E:\AACV_System_Backups`，关闭ACL继承，并仅允许当前用户、SYSTEM和Administrators；已有目录若不满足该边界会停止，不会擅自放宽权限。
- `-ApplyRetention`才会删除超出7个每日和4个每周配额的旧SQL及配套旁车文件；不带该开关时不删除任何备份。
- `Test-Stage8BackupRecovery.ps1`使用独立`aacv-stage8-recovery-mysql`和`aacv-stage8-recovery-neo4j`，端口为23306、27474和27687，临时后端固定使用`127.0.0.1:28080`。启动前若28080已被占用会停止，清理时只停止可验证为本次脚本启动进程后代的监听进程。固定恢复库为`aacv_stage8_recovery`。
- 恢复脚本会在写入前复核SHA-256并要求MySQL零表、Neo4j零节点；恢复后比较Flyway V11和关键计数，禁用恢复副本中的数据源、采集任务及计划，防止恢复演练触发外部请求。
- 恢复脚本通过既有认证和CSRF边界抽样核对成果目录与详情；图恢复使用`REBUILD_AACV_MANAGED_GRAPH`确认边界执行全量重建，等待Outbox清空，再运行对账并要求`differenceCount=0`。临时后端和容器会停止，但恢复数据库与命名卷保留供复核。

## 实际执行顺序

必须在普通本地PowerShell中运行，并由用户本人在`Get-Credential`窗口输入凭据：

~~~powershell
.\tools\stage8\New-Stage8DatabaseBackup.ps1 `
    -InitializeBackupRoot -ApplyRetention `
    -OutputPath .\docs\stage8-backup-evidence.json

.\tools\stage8\Test-Stage8BackupRecovery.ps1 `
    -ConfirmIsolatedRestore `
    -OutputPath .\docs\stage8-backup-recovery-evidence.json
~~~

首次成功创建目录后，后续备份不再使用`-InitializeBackupRoot`。恢复脚本默认选择最新每日备份，也可用`-BackupPath`指定固定目录内的某个SQL。若恢复环境曾经使用过，脚本会因非空目标停止；任何清理、删除数据库或删除卷都必须另行明确授权。
