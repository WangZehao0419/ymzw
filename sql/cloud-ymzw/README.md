# 云眸智维业务表 SQL（若依 RuoYi-Cloud v3.6.8）

## 数据库准备

- 数据库名：`ry-cloud`（与若依系统表 sys_* 共用同一库）
- 字符集：`utf8mb4` / `utf8mb4_unicode_ci`
- 业务表统一加 `ymzw_` 前缀（避免与若依 sys_* 命名冲突），entity `@TableName("ymzw_xxx")` 同步更新即可

## 文件清单

| 文件 | 范围 | 备注 |
|---|---|---|
| `ymzw_ai_agent.sql` | AI 智能体配置表 | 已迁移并提供 DDL |

## 待建表（需基于 entity 字段自行生成或由原项目数据库导出）

| 模块 | entity 类 | 建议表名 |
|---|---|---|
| ruoyi-ai | QAMapper/QAMapper | `ymzw_qa`（问答记录） |
| ruoyi-equipment | Equipment / EquipmentSensor / EquipmentSensorMonitor | `ymzw_equipment` / `ymzw_equipment_sensor` / `ymzw_equipment_sensor_monitor` |
| ruoyi-alert | AlertRule / AlertEvent / AlertActionLog | `ymzw_alert_rule` / `ymzw_alert_event` / `ymzw_alert_action_log` |
| ruoyi-inspection | Part / InspectionStandard / InspectionRecord | `ymzw_part` / `ymzw_inspection_standard` / `ymzw_inspection_record` |

## 表结构生成方式

1. 启动业务模块（ruoyi-ai/equipment/alert/inspection 任一），让 MyBatis-Plus 的 `spring-boot-starter` 自动建表策略
2. 或根据 entity 字段（`@TableField` / `@TableId`）手写 DDL
3. 或从原项目 `cloud-*` 数据库用 `mysqldump` 导出后批量重命名

## TDengine（时序库）

- 数据库名：`ymzw`
- 超级表：传感器监测数据、告警明细（按 RuoYi-Cloud `cloud-alert` 模块的 TdengineService 设计）
- 连接器：JDBC REST（`jdbc:TAOS-RS://host:6041/ymzw`）
- 建库建表语句由 alert 模块启动时自动执行（参考 TdengineService.initSchema()）
