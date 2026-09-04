-- ----------------------------
-- 维护计划模块 DDL（add-maintenance-plan spec）
-- 已于 2026-08-31 在 ymzw2 库执行
-- ----------------------------

-- 1. 维护计划主表
CREATE TABLE IF NOT EXISTS maintenance_plan (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  plan_no          VARCHAR(32)  NOT NULL COMMENT '计划编号 MP+yyyyMMddHHmmss+3位随机',
  plan_name        VARCHAR(128) NOT NULL COMMENT '计划名称',
  equipment_id     INT          NOT NULL COMMENT '维保对象(设备级)',
  equipment_name   VARCHAR(128),
  maintenance_type VARCHAR(32)  NOT NULL COMMENT '保养类型: 日常保养/一级保养/二级保养/精度校准/润滑保养',
  content          VARCHAR(512) COMMENT '维护内容说明',
  repeat_type      VARCHAR(16)  NOT NULL COMMENT 'ONCE/DAILY/WEEKDAYS/MONTHLY/LEGAL_WORKDAY',
  fire_time        TIME         NOT NULL COMMENT '触发时刻 HH:mm',
  fire_day         INT          COMMENT 'MONTHLY: 每月几号(1-31)',
  fire_date        DATE         COMMENT 'ONCE: 触发日期',
  next_fire_time   DATETIME     COMMENT '下次触发时间(预计算,DONE 为 NULL)',
  last_fire_time   DATETIME     COMMENT '上次触发时间',
  assignee_id      BIGINT       COMMENT '负责人用户ID(可空=生成后待指派)',
  assignee_name    VARCHAR(64),
  status           VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/PAUSED/DONE',
  create_time      DATETIME,
  update_time      DATETIME,
  UNIQUE KEY uk_plan_no (plan_no),
  KEY idx_next_fire (status, next_fire_time)
) COMMENT '维护计划';

-- 2. work_order 关联计划（在线 DDL，存量数据 plan_id=NULL 不影响告警工单链路）
ALTER TABLE work_order ADD COLUMN plan_id BIGINT NULL COMMENT '关联维护计划ID' AFTER alert_event_id,
  ADD KEY idx_plan_id (plan_id);

-- 3. 法定工作日数据缓存（外部 API 自动同步，免人工维护）
CREATE TABLE IF NOT EXISTS holiday_calendar (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  cal_date    DATE         NOT NULL COMMENT '日期(国务院安排涉及的例外日: 放假日/补班日)',
  cal_year    INT          NOT NULL COMMENT '年份(同步批次键)',
  is_workday  TINYINT      NOT NULL COMMENT '1=工作日(调休补班), 0=休息日(法定节假日/调休连休)',
  name        VARCHAR(32)  COMMENT '节假日名称(如 春节/国庆节后补班)',
  source      VARCHAR(16)  COMMENT '数据来源: TIMOR/HOLIDAY_CN',
  fetch_time  DATETIME     COMMENT '同步时间',
  UNIQUE KEY uk_cal_date (cal_date),
  KEY idx_cal_year (cal_year)
) COMMENT '法定工作日数据缓存(外部API自动同步,免人工维护)';
