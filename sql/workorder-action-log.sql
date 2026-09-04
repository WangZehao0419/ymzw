-- 工单流转记录表 DDL 存档：目标库 ymzw2（已于 2026-08-31 执行），仅作建表语句留档
CREATE TABLE work_order_action_log (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id    BIGINT       NOT NULL COMMENT '关联工单ID',
  order_no    VARCHAR(32)  COMMENT '工单编号(冗余,免联表直查)',
  action      VARCHAR(16)  NOT NULL COMMENT '动作: CREATE/ASSIGN/START/COMPLETE/CANCEL',
  operator    VARCHAR(64)  COMMENT '操作人(系统自动生成为 system)',
  detail      VARCHAR(1024) COMMENT '流转详情(完整中文句子,含派单对象/转派方向/取消原因等)',
  create_time DATETIME,
  KEY idx_order_id (order_id)
) COMMENT '工单流转记录';
