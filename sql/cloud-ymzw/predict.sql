-- =============================================
-- 云眸智维预测性维护 DDL
-- 数据库: MySQL 8.0+ / ymzw2 库(与 alert_rule 等告警业务表同库)
-- 字段映射规则: entity @TableField/@TableId 注解 + Java 类型
--   Integer → INT, Long → BIGINT, Double → DOUBLE
--   String(短) → VARCHAR(255), String(长文本/JSON) → TEXT, LocalDateTime → DATETIME
-- 生成日期: 2026-08-30
-- =============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================
-- 1. Alert 模块 — 预测性维护结果表
-- entity: com.ruoyi.alert.entity.PredictResult
-- 每传感器一行的最新快照,service 层按 sensor_code upsert
-- (先查后写,uk_predict_result_sensor_code 兜底防并发重复)
-- =============================================
DROP TABLE IF EXISTS `predict_result`;
CREATE TABLE `predict_result` (
    `id`                    BIGINT          AUTO_INCREMENT  PRIMARY KEY     COMMENT '主键ID',
    `sensor_code`           VARCHAR(50)     NOT NULL                        COMMENT '传感器编号',
    `equipment_id`          INT                                             COMMENT '设备ID',
    `status`                VARCHAR(20)     DEFAULT 'NORMAL'                COMMENT '预测状态(NORMAL/DEGRADING/BREACHED)',
    `health_score`          DOUBLE                                          COMMENT '健康度得分(0-100)',
    `slope`                 DOUBLE                                          COMMENT '趋势斜率(WLS拟合)',
    `t1_points`             INT             DEFAULT 0                       COMMENT '预计越界点数(相对当前)',
    `predicted_breach_time` DATETIME                                        COMMENT '预测越限时间(趋势外推)',
    `onset_time`            DATETIME                                        COMMENT '劣化起点时间',
    `band_json`             TEXT                                            COMMENT '趋势置信带JSON',
    `ai_available`          INT             DEFAULT 0                       COMMENT 'AI预测可用(0-不可用,1-可用)',
    `ai_p10_json`           TEXT                                            COMMENT 'AI预测P10分位JSON',
    `ai_p50_json`           TEXT                                            COMMENT 'AI预测P50分位JSON',
    `ai_p90_json`           TEXT                                            COMMENT 'AI预测P90分位JSON',
    `divergence_ratio`      DOUBLE                                          COMMENT 'AI与统计外激发散比',
    `update_time`           DATETIME                                        COMMENT '更新时间',
    UNIQUE KEY `uk_predict_result_sensor_code` (`sensor_code`),
    KEY `idx_predict_result_equipment` (`equipment_id`),
    KEY `idx_predict_result_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预测性维护结果表(按传感器的最新快照)';

-- =============================================
-- 2. Alert 模块 — alert_event 加列(PREDICT 告警专用)
-- entity: com.ruoyi.alert.entity.AlertEvent#predictedBreachTime
-- PREDICT 告警在真实越界发生前发出,该列记录"预计何时越界";
-- RULE 阈值告警不写该列(保持 NULL)
-- =============================================
ALTER TABLE `alert_event`
    ADD COLUMN `predicted_breach_time` DATETIME NULL COMMENT '预计越界时刻(PREDICT类型专用)' AFTER `escalation_count`;

-- =============================================
-- 3. Alert 模块 — 预测告警独立表(物理分流)
-- entity: com.ruoyi.alert.entity.PredictAlert
-- 阈值告警(RULE/STAT)落 alert_event(告警记录),
-- 预测告警(PREDICT)落本表(预测性维护),列名与 alert_event 对齐:
-- 迁移 SQL 简单(字段一一对应)、前端实体字段零改动
-- =============================================
DROP TABLE IF EXISTS `predict_alert`;
CREATE TABLE `predict_alert` (
    `id`                    BIGINT          AUTO_INCREMENT  PRIMARY KEY     COMMENT '主键ID',
    `equipment_id`          INT                                             COMMENT '设备ID',
    `equipment_name`        VARCHAR(100)                                    COMMENT '设备名称',
    `sensor_id`             INT                                             COMMENT '传感器ID',
    `sensor_code`           VARCHAR(50)                                     COMMENT '传感器编号',
    `sensor_name`           VARCHAR(100)                                    COMMENT '传感器名称',
    `alert_type`            VARCHAR(20)                                     COMMENT '告警类型(本表恒为PREDICT,保留列与alert_event对齐)',
    `alert_level`           VARCHAR(20)                                     COMMENT '告警级别(NORMAL/WARNING/SEVERE)',
    `alert_status`          VARCHAR(20)                                     COMMENT '告警状态(FIRING/ACKED/RESOLVED)',
    `evidence`              TEXT                                            COMMENT '证据JSON(slope/r2/onset/t1等)',
    `summary`               TEXT                                            COMMENT '告警摘要',
    `root_cause`            TEXT                                            COMMENT '根因',
    `suggestion`            TEXT                                            COMMENT '处置建议',
    `sensor_value`          DOUBLE                                          COMMENT '触发时数值(平滑值,两位小数)',
    `trigger_time`          DATETIME                                        COMMENT '触发时间',
    `resolve_time`          DATETIME                                        COMMENT '解除时间',
    `escalation_count`      INT                                             COMMENT '升级次数',
    `predicted_breach_time` DATETIME                                        COMMENT '预计越界时刻',
    `create_time`           DATETIME                                        COMMENT '创建时间',
    KEY `idx_predict_alert_sensor` (`sensor_code`),
    KEY `idx_predict_alert_trigger_time` (`trigger_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预测告警表(PREDICT类型,与告警记录alert_event物理分流)';

-- =============================================
-- 4. 历史数据迁移: alert_event 中既有 PREDICT 记录迁入 predict_alert
-- (迁移后删除源记录,告警记录表只留阈值告警;两步分开执行便于核对行数)
-- =============================================
-- 4.1 迁移(字段一一对应,保留原 id 使状态机既有句柄语义不破坏)
INSERT INTO `predict_alert`
    (`id`, `equipment_id`, `equipment_name`, `sensor_id`, `sensor_code`, `sensor_name`,
     `alert_type`, `alert_level`, `alert_status`, `evidence`, `summary`, `root_cause`, `suggestion`,
     `sensor_value`, `trigger_time`, `resolve_time`, `escalation_count`, `predicted_breach_time`, `create_time`)
SELECT `id`, `equipment_id`, `equipment_name`, `sensor_id`, `sensor_code`, `sensor_name`,
       `alert_type`, `alert_level`, `alert_status`, `evidence`, `summary`, `root_cause`, `suggestion`,
       `sensor_value`, `trigger_time`, `resolve_time`, `escalation_count`, `predicted_breach_time`, `create_time`
FROM `alert_event`
WHERE `alert_type` = 'PREDICT';

-- 4.2 删除源表中已迁移的 PREDICT 记录(行数核对后再执行)
DELETE FROM `alert_event` WHERE `alert_type` = 'PREDICT';

-- =============================================
-- 5. B4 模型推理切换 — predict_result 加列(增量迁移)
-- entity: com.ruoyi.alert.entity.PredictResult#anomalyScore/rulEarliest/rulLatest/modelVersion
-- 检测链路由统计算法(CUSUM/MAD/趋势外推)切换为模型推理(Feign→ruoyi-ai→pdm-server):
-- 新增列承载推理产物(anomaly_score 异常评分/rul_earliest+rul_latest RUL 区间/model_version 模型版本);
-- RUL 点估计(rulPoint,分钟)不单独建列,由 predicted_breach_time 按"落库时刻+rulPoint 分钟"换算承载。
-- 统计旧列(slope/t1_points/onset_time/band_json)保留不删:兼容历史数据排查,
-- 代码已不再写入(状态回 NORMAL 时顺带清空残留)。
-- 执行方式: 存量环境仅执行本节 ALTER;全新环境按序执行第 1-4 节后同样须补执行本节
-- (第 1 节 CREATE TABLE 生成于加列之前,未含新列)。
-- 生成日期: 2026-09-06
-- =============================================
ALTER TABLE `predict_result`
    ADD COLUMN `anomaly_score`  DOUBLE        NULL COMMENT '异常评分(越高越异常,pdm-server推理输出)' AFTER `health_score`,
    ADD COLUMN `rul_earliest`   BIGINT        NULL COMMENT 'RUL最早失效剩余分钟数(推理输出,可空)' AFTER `predicted_breach_time`,
    ADD COLUMN `rul_latest`     BIGINT        NULL COMMENT 'RUL最晚失效剩余分钟数(推理输出,可空)' AFTER `rul_earliest`,
    ADD COLUMN `model_version`  VARCHAR(64)   NULL COMMENT '推理模型版本(结果溯源与模型迭代比对)' AFTER `rul_latest`;

-- =============================================
-- 后续任务追加语句预留:
-- 后续如需对 predict_result 追加字段或维护闭环新表,
-- 在此追加 CREATE/ALTER 语句
-- =============================================

SET FOREIGN_KEY_CHECKS = 1;
