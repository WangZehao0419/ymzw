-- =============================================
-- 云眸智维业务表 DDL（11 张表）
-- 数据库: MySQL 8.0+ / ry-cloud 库
-- 字段映射规则: entity @TableField/@TableId 注解 + Java 类型
--   Integer → INT, Long → BIGINT, Double → DOUBLE
--   String(短) → VARCHAR(255), String(长文本/JSON) → TEXT
--   LocalDate → DATE, LocalDateTime → DATETIME
--   Boolean → TINYINT(1), @TableLogic → DEFAULT 0
-- 生成日期: 2026-08-26
-- =============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================
-- 1. AI 模块 — 问答记录表（与 ai_agent 配套）
-- entity: com.ruoyi.ai.entity.QA
-- =============================================
DROP TABLE IF EXISTS `qna_management`;
CREATE TABLE `qna_management` (
    `qna_id`        VARCHAR(64)    NOT NULL                COMMENT '问答记录ID(UUID)',
    `user_id`       VARCHAR(64)                            COMMENT '用户ID',
    `question`      TEXT                                    COMMENT '问题内容',
    `answer`        TEXT                                    COMMENT '回答内容',
    `create_time`   DATETIME                                COMMENT '创建时间',
    `username`      VARCHAR(100)                           COMMENT '用户名',
    PRIMARY KEY (`qna_id`),
    KEY `idx_qna_user_id` (`user_id`),
    KEY `idx_qna_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI问答记录表';


-- =============================================
-- 2. Equipment 模块 — 设备基础信息表
-- entity: com.ruoyi.equipment.entity.Equipment
-- =============================================
DROP TABLE IF EXISTS `equipment`;
CREATE TABLE `equipment` (
    `id`                    INT             AUTO_INCREMENT  PRIMARY KEY     COMMENT '主键ID',
    `equipment_no`          VARCHAR(50)     NOT NULL                        COMMENT '设备编号',
    `equipment_name`        VARCHAR(100)    NOT NULL                        COMMENT '设备名称',
    `equipment_model_id`    INT                                             COMMENT '设备型号ID',
    `equipment_model_name`  VARCHAR(100)                                    COMMENT '设备型号名称',
    `workshop_id`           INT                                             COMMENT '所属车间ID',
    `workshop_name`         VARCHAR(100)                                    COMMENT '所属车间名称',
    `equipment_status`      VARCHAR(20)     DEFAULT '0'                     COMMENT '运行状态(0-运行中,1-停机,2-维修,3-待验收)',
    `equipment_install_date` DATE                                           COMMENT '安装日期',
    `equipment_user_id`     INT                                             COMMENT '负责人ID',
    `equipment_user_name`   VARCHAR(50)                                     COMMENT '负责人名称',
    `equipment_remark`       VARCHAR(500)                                    COMMENT '备注',
    `layout_x`              DOUBLE                                          COMMENT '孪生布局X(米,地面世界坐标,中心原点,NULL未摆放)',
    `layout_y`              DOUBLE                                          COMMENT '孪生布局Y(米,地面世界坐标,中心原点,NULL未摆放)',
    `create_time`          DATETIME                                        COMMENT '记录创建时间',
    `update_time`          DATETIME                                        COMMENT '记录修改时间',
    `create_user`          VARCHAR(50)                                     COMMENT '创建人',
    `update_user`          VARCHAR(50)                                     COMMENT '修改人',
    `delete_flag`          INT             DEFAULT 0                       COMMENT '删除状态(0-未删除,1-已删除)',
    UNIQUE KEY `uk_equipment_no` (`equipment_no`),
    KEY `idx_equipment_workshop` (`workshop_id`),
    KEY `idx_equipment_status` (`equipment_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备基础信息表';


-- =============================================
-- 3. Equipment 模块 — 设备传感器表
-- entity: com.ruoyi.equipment.entity.EquipmentSensor
-- =============================================
DROP TABLE IF EXISTS `equipment_sensor`;
CREATE TABLE `equipment_sensor` (
    `id`                INT             AUTO_INCREMENT  PRIMARY KEY     COMMENT '主键ID',
    `sensor_code`       VARCHAR(50)     NOT NULL                        COMMENT '传感器参数编号(如TH-001)',
    `sensor_name`       VARCHAR(100)    NOT NULL                        COMMENT '传感器参数名称(如主轴转速)',
    `sensor_unit`       VARCHAR(20)                                     COMMENT '传感器参数单位(rpm/°C/mm/s)',
    `sensor_status`     INT             DEFAULT 1                       COMMENT '传感器状态(0-禁用,1-启用)',
    `equipment_id`      INT                                             COMMENT '所属设备ID',
    `equipment_name`    VARCHAR(100)                                    COMMENT '所属设备名称(冗余字段)',
    `create_time`       DATETIME                                        COMMENT '记录创建时间',
    `update_time`       DATETIME                                        COMMENT '记录修改时间',
    `create_user`       VARCHAR(50)                                     COMMENT '创建人',
    `update_user`       VARCHAR(50)                                     COMMENT '修改人',
    `delete_flag`       INT             DEFAULT 0                       COMMENT '删除状态(0-未删除,1-已删除)',
    UNIQUE KEY `uk_sensor_code` (`sensor_code`),
    KEY `idx_sensor_equipment` (`equipment_id`),
    KEY `idx_sensor_status` (`sensor_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备传感器参数表';


-- =============================================
-- 4. Equipment 模块 — 设备传感器监测数据表
-- entity: com.ruoyi.equipment.entity.EquipmentSensorMonitor
-- 注: 高频时序数据建议走 TDengine，本表保留 MySQL 兜底最新快照
-- =============================================
DROP TABLE IF EXISTS `equipment_sensor_monitor`;
CREATE TABLE `equipment_sensor_monitor` (
    `id`                BIGINT          AUTO_INCREMENT  PRIMARY KEY     COMMENT '主键ID',
    `sensor_id`         INT                                             COMMENT '传感器ID',
    `sensor_value`      DOUBLE                                          COMMENT '传感器参数值',
    `create_time`       DATETIME                                        COMMENT '记录创建时间',
    `update_time`       DATETIME                                        COMMENT '记录修改时间',
    `create_user`       VARCHAR(50)                                     COMMENT '创建人',
    `update_user`       VARCHAR(50)                                     COMMENT '修改人',
    `delete_flag`       INT             DEFAULT 0                       COMMENT '删除状态(0-未删除,1-已删除)',
    KEY `idx_monitor_sensor_id` (`sensor_id`),
    KEY `idx_monitor_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备传感器监测数据表(MySQL兜底,时序走TDengine)';


-- =============================================
-- 5. Alert 模块 — 告警规则表(L1阈值规则)
-- entity: com.ruoyi.alert.entity.AlertRule
-- =============================================
DROP TABLE IF EXISTS `alert_rule`;
CREATE TABLE `alert_rule` (
    `id`                BIGINT          AUTO_INCREMENT  PRIMARY KEY     COMMENT '主键ID',
    `sensor_code`       VARCHAR(50)     NOT NULL                        COMMENT '传感器编号',
    `upper_limit`       DOUBLE                                          COMMENT '阈值上限',
    `lower_limit`       DOUBLE                                          COMMENT '阈值下限',
    `sustain_points`    INT                                             COMMENT '持续越界点数(防抖)',
    `silence_start`     VARCHAR(10)                                     COMMENT '静默时段开始 HH:mm',
    `silence_end`       VARCHAR(10)                                     COMMENT '静默时段结束 HH:mm',
    `level`             VARCHAR(20)                                     COMMENT '命中告警等级 NORMAL/WARNING/IMPORTANT/SEVERE/CRITICAL',
    `enabled`           INT             DEFAULT 1                       COMMENT '是否启用(0-禁用,1-启用)',
    `create_time`       DATETIME                                        COMMENT '创建时间',
    `update_time`       DATETIME                                        COMMENT '更新时间',
    KEY `idx_alert_rule_sensor` (`sensor_code`),
    KEY `idx_alert_rule_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警规则表(L1阈值规则)';


-- =============================================
-- 6. Alert 模块 — 告警事件表
-- entity: com.ruoyi.alert.entity.AlertEvent
-- =============================================
DROP TABLE IF EXISTS `alert_event`;
CREATE TABLE `alert_event` (
    `id`                BIGINT          AUTO_INCREMENT  PRIMARY KEY     COMMENT '主键ID',
    `equipment_id`      INT                                             COMMENT '设备ID',
    `equipment_name`    VARCHAR(100)                                    COMMENT '设备名称(冗余)',
    `sensor_id`         INT                                             COMMENT '传感器ID',
    `sensor_code`       VARCHAR(50)                                     COMMENT '传感器编号',
    `sensor_name`       VARCHAR(100)                                    COMMENT '传感器名称',
    `alert_type`        VARCHAR(20)                                     COMMENT '告警类型 RULE/STAT/PREDICT',
    `alert_level`       VARCHAR(20)                                     COMMENT '告警等级 NORMAL/WARNING/IMPORTANT/SEVERE/CRITICAL',
    `alert_status`      VARCHAR(20)                                     COMMENT '告警状态 FIRING/ACKED/RESOLVED',
    `evidence`          TEXT                                            COMMENT '命中证据JSON({layer,value,threshold,sustain})',
    `summary`           TEXT                                            COMMENT '告警摘要(L3回填)',
    `root_cause`        TEXT                                            COMMENT '根因(L3回填)',
    `suggestion`        TEXT                                            COMMENT '处置建议(L3回填)',
    `sensor_value`      DOUBLE                                          COMMENT '触发时的传感器值',
    `trigger_time`      DATETIME                                        COMMENT '触发时间',
    `resolve_time`      DATETIME                                        COMMENT '解除时间',
    `escalation_count`  INT             DEFAULT 0                       COMMENT '升级次数',
    `create_time`       DATETIME                                        COMMENT '创建时间',
    KEY `idx_alert_event_equipment` (`equipment_id`),
    KEY `idx_alert_event_sensor` (`sensor_id`),
    KEY `idx_alert_event_status` (`alert_status`),
    KEY `idx_alert_event_trigger_time` (`trigger_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警事件表';


-- =============================================
-- 7. Alert 模块 — 告警处置日志表
-- entity: com.ruoyi.alert.entity.AlertActionLog
-- =============================================
DROP TABLE IF EXISTS `alert_action_log`;
CREATE TABLE `alert_action_log` (
    `id`                BIGINT          AUTO_INCREMENT  PRIMARY KEY     COMMENT '主键ID',
    `alert_id`          BIGINT          NOT NULL                        COMMENT '关联告警事件ID',
    `action`            VARCHAR(20)                                     COMMENT '动作 ACK/RESOLVE/ESCALATE',
    `operator`          VARCHAR(50)                                     COMMENT '操作人',
    `remark`            VARCHAR(500)                                    COMMENT '备注',
    `create_time`       DATETIME                                        COMMENT '创建时间',
    KEY `idx_alert_log_alert_id` (`alert_id`),
    KEY `idx_alert_log_action` (`action`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警处置日志表';


-- =============================================
-- 8. Inspection 模块 — 零件表
-- entity: com.ruoyi.inspection.entity.Part
-- =============================================
DROP TABLE IF EXISTS `parts`;
CREATE TABLE `parts` (
    `id`                    BIGINT          AUTO_INCREMENT  PRIMARY KEY     COMMENT '主键ID',
    `part_name`             VARCHAR(100)    NOT NULL                        COMMENT '零件名称',
    `part_code`             VARCHAR(50)                                     COMMENT '零件编码',
    `standard_id`           BIGINT                                          COMMENT '检测标准ID',
    `parameters`            TEXT                                            COMMENT '零件参数(JSON格式)',
    `is_qualified`         TINYINT(1)                                      COMMENT '是否合格(1-合格,0-不合格)',
    `inspection_time`       DATETIME                                        COMMENT '检测时间',
    `inspection_details`    TEXT                                            COMMENT '检测详情',
    `inspection_suggestion` TEXT                                            COMMENT '检测建议',
    `create_time`           DATETIME                                        COMMENT '创建时间',
    `update_time`           DATETIME                                        COMMENT '更新时间',
    `delete_flag`           TINYINT(1)      DEFAULT 0                       COMMENT '删除标志(0-正常,1-已删除)',
    `inspection_flag`       TINYINT(1)                                      COMMENT '检测标志(0-未检测,1-已检测)',
    UNIQUE KEY `uk_part_code` (`part_code`),
    KEY `idx_part_standard` (`standard_id`),
    KEY `idx_part_qualified` (`is_qualified`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='零件表';


-- =============================================
-- 9. Inspection 模块 — 检测标准表
-- entity: com.ruoyi.inspection.entity.InspectionStandard
-- =============================================
DROP TABLE IF EXISTS `inspection_standards`;
CREATE TABLE `inspection_standards` (
    `id`                    BIGINT          AUTO_INCREMENT  PRIMARY KEY     COMMENT '主键ID',
    `part_type`             VARCHAR(50)                                     COMMENT '零件类型',
    `standard_name`         VARCHAR(100)                                    COMMENT '标准名称',
    `standard_parameters`   TEXT                                            COMMENT '标准参数(JSON格式)',
    `description`           VARCHAR(500)                                    COMMENT '描述',
    `create_time`           DATETIME                                        COMMENT '创建时间',
    `update_time`           DATETIME                                        COMMENT '更新时间',
    KEY `idx_standard_part_type` (`part_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检测标准表';


-- =============================================
-- 10. Inspection 模块 — 检测记录表
-- entity: com.ruoyi.inspection.entity.InspectionRecord
-- =============================================
DROP TABLE IF EXISTS `inspection_records`;
CREATE TABLE `inspection_records` (
    `id`                        BIGINT          AUTO_INCREMENT  PRIMARY KEY     COMMENT '主键ID',
    `part_id`                   BIGINT                                          COMMENT '零件ID',
    `inspection_time`           DATETIME                                        COMMENT '检测时间',
    `is_qualified`              TINYINT(1)                                      COMMENT '是否合格(1-合格,0-不合格)',
    `inspection_details`       TEXT                                            COMMENT '检测详情',
    `inspection_suggestion`    TEXT                                            COMMENT '检测建议',
    `inspector`                VARCHAR(50)                                     COMMENT '检测人员',
    KEY `idx_record_part_id` (`part_id`),
    KEY `idx_record_inspection_time` (`inspection_time`),
    KEY `idx_record_qualified` (`is_qualified`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检测记录表';

-- =============================================
-- 11. Equipment 模块 — 车间表
-- entity: com.ruoyi.equipment.entity.Workshop
-- =============================================
DROP TABLE IF EXISTS `workshop`;
CREATE TABLE `workshop` (
    `id`                    INT             AUTO_INCREMENT  PRIMARY KEY     COMMENT '主键ID',
    `workshop_name`         VARCHAR(100)    NOT NULL                        COMMENT '车间名称',
    `workshop_location`     VARCHAR(200)                                    COMMENT '车间位置',
    `workshop_manager_id`   INT                                             COMMENT '车间负责人用户ID',
    `workshop_manager`      VARCHAR(50)                                     COMMENT '车间负责人',
    `workshop_status`       VARCHAR(20)     DEFAULT '0'                     COMMENT '状态(0-启用,1-停用)',
    `workshop_remark`       VARCHAR(500)                                    COMMENT '备注',
    `create_time`           DATETIME                                        COMMENT '记录创建时间',
    `update_time`           DATETIME                                        COMMENT '记录修改时间',
    `create_user`           VARCHAR(50)                                     COMMENT '创建人',
    `update_user`           VARCHAR(50)                                     COMMENT '修改人',
    `delete_flag`           INT             DEFAULT 0                       COMMENT '删除状态(0-未删除,1-已删除)',
    KEY `idx_workshop_status` (`workshop_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车间表';

-- 种子数据: 3 个示例车间
INSERT INTO `workshop` (`workshop_name`, `workshop_location`, `workshop_manager`, `workshop_status`, `create_time`, `create_user`) VALUES
('一号车间', '一号厂房一层', '张三', '0', NOW(), 'admin'),
('二号车间', '一号厂房二层', '李四', '0', NOW(), 'admin'),
('三号车间', '二号厂房一层', '王五', '0', NOW(), 'admin');

SET FOREIGN_KEY_CHECKS = 1;
