-- =============================================
-- AI诊断报告表 DDL
-- 数据库: MySQL / OceanBase
-- 说明: ruoyi-ai 诊断智能体(B6)生成的设备诊断报告落库,
--       report 为 LLM 生成的 Markdown 全文,历史接口按设备/传感器分页回查
-- =============================================

DROP TABLE IF EXISTS `diagnosis_record`;

CREATE TABLE `diagnosis_record` (
    `id`                BIGINT          AUTO_INCREMENT  PRIMARY KEY     COMMENT '主键ID',
    `equipment_id`      INT                                             COMMENT '设备ID',
    `sensor_code`       VARCHAR(50)                                     COMMENT '传感器编号',
    `report`            LONGTEXT                                       COMMENT '诊断报告(Markdown全文)',
    `created_time`      DATETIME        DEFAULT CURRENT_TIMESTAMP         COMMENT '创建时间',

    KEY `idx_diagnosis_record_equipment` (`equipment_id`),
    KEY `idx_diagnosis_record_sensor` (`sensor_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI诊断报告表';
