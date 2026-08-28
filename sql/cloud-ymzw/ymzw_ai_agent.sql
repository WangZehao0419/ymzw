-- =============================================
-- AI智能体配置表 DDL
-- 数据库: MySQL / OceanBase
-- =============================================

DROP TABLE IF EXISTS `ai_agent`;

CREATE TABLE `ai_agent` (
    `id`                BIGINT          AUTO_INCREMENT  PRIMARY KEY     COMMENT '主键ID',
    `agent_name`        VARCHAR(100)    NOT NULL                        COMMENT '智能体名称',
    `agent_type`        VARCHAR(50)     NOT NULL                        COMMENT '智能体类型(CHAT/PREDICTIVE_ALARM/PART_INSPECTION/REPORT/KNOWLEDGE_QA/EMBEDDING)',
    `agent_avatar`      VARCHAR(500)                                    COMMENT '头像URL',
    `system_prompt`     TEXT                                            COMMENT '系统提示词',
    `api_endpoint`      VARCHAR(500)                                    COMMENT 'API地址',
    `api_key`           VARCHAR(500)                                    COMMENT 'API密钥',
    `model_identifier`  VARCHAR(200)                                    COMMENT '基座模型标识符',
    `temperature`       DOUBLE                                          COMMENT '温度参数(0-2)',
    `tools_config`      TEXT                                            COMMENT '工具配置(JSON)',
    `knowledge_base_ids` VARCHAR(500)                                   COMMENT '关联知识库ID列表(逗号分隔)',
    `description`       VARCHAR(500)                                    COMMENT '描述',
    `status`            INT             DEFAULT 1                       COMMENT '状态(0-禁用,1-启用)',
    `sort_order`        INT             DEFAULT 0                       COMMENT '排序号',
    `remark`            VARCHAR(500)                                    COMMENT '备注',
    `create_time`       DATETIME        DEFAULT CURRENT_TIMESTAMP       COMMENT '创建时间',
    `update_time`       DATETIME        DEFAULT CURRENT_TIMESTAMP
                                        ON UPDATE CURRENT_TIMESTAMP     COMMENT '更新时间',
    `create_user`       VARCHAR(50)                                     COMMENT '创建人',
    `update_user`       VARCHAR(50)                                     COMMENT '更新人',
    `delete_flag`       INT             DEFAULT 0                       COMMENT '删除标志(0-未删除,1-已删除)',

    INDEX `idx_agent_type`   (`agent_type`),
    INDEX `idx_status`       (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI智能体配置表';