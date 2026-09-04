-- 维保工单表 DDL 存档：目标库 ymzw2，仅作建表语句留档，不随部署自动执行
-- 2026-08-31 字段重构（refactor-workorder-fields）：删 source_type/alert_event_id/plan_id/
-- sensor_code/trigger_value/assignee_id/assignee_name 七列，新增 related_id/handler_name，
-- handler 改 BIGINT(系统用户ID)。order_type 成为唯一来源判别并路由 related_id 关联表。
CREATE TABLE work_order (
  id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no              VARCHAR(32)  NOT NULL COMMENT '工单编号 WO+yyyyMMddHHmmss+3位随机',
  order_type            VARCHAR(16)  NOT NULL COMMENT '工单类型: 故障维修(关联alert_event)/预防维护(关联maintenance_plan)',
  related_id            BIGINT       COMMENT '关联业务ID(order_type路由:故障维修→alert_event,预防维护→maintenance_plan)',
  equipment_id          INT          NOT NULL,
  equipment_name        VARCHAR(128),
  sensor_id             INT,
  sensor_name           VARCHAR(64),
  alert_level           VARCHAR(16)  COMMENT '级别: WARNING/IMPORTANT/SEVERE/CRITICAL',
  description           VARCHAR(512) COMMENT '工单内容(自动生成)',
  status                VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/COMPLETED/CANCELLED',
  handler               BIGINT       COMMENT '处理人用户ID(关联sys_user,生成时为设备负责人,未绑定为NULL待转派)',
  handler_name          VARCHAR(64)  COMMENT '处理人姓名',
  handle_remark         VARCHAR(512) COMMENT '处理结果说明',
  cancel_reason         VARCHAR(256) COMMENT '取消原因',
  finish_time           DATETIME,
  create_time           DATETIME,
  update_time           DATETIME,
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_dedup (equipment_id, sensor_id, order_type, status),
  KEY idx_related (related_id, order_type)
) COMMENT '维保工单';

-- 2026-08-31 已在 ymzw2 执行的迁移（存档）：
-- ALTER TABLE work_order ADD COLUMN related_id BIGINT NULL, ADD COLUMN handler_name VARCHAR(64) NULL;
-- UPDATE work_order SET related_id = alert_event_id WHERE source_type = 'RULE';
-- UPDATE work_order SET related_id = plan_id WHERE source_type = 'PLAN';
--   （旧 PREDICT 单(分表前遗留) related_id 保持 NULL：其 alert_event_id 语义已错位，不迁移）
-- UPDATE work_order SET handler = assignee_id, handler_name = assignee_name WHERE assignee_id IS NOT NULL;
-- UPDATE work_order SET handler = NULL WHERE assignee_id IS NULL;  （清 username 字符串存量）
-- ALTER TABLE work_order MODIFY COLUMN handler BIGINT NULL;
-- ALTER TABLE work_order DROP INDEX idx_dedup, DROP INDEX idx_plan_id,
--   ADD INDEX idx_dedup (equipment_id, sensor_id, order_type, status), ADD INDEX idx_related (related_id, order_type);
-- ALTER TABLE work_order DROP COLUMN source_type, DROP COLUMN alert_event_id, DROP COLUMN plan_id,
--   DROP COLUMN sensor_code, DROP COLUMN trigger_value, DROP COLUMN assignee_id, DROP COLUMN assignee_name;
