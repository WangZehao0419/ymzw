/*
 Navicat Premium Dump SQL

 Source Server         : localhost_3306
 Source Server Type    : MySQL
 Source Server Version : 80046 (8.0.46)
 Source Host           : localhost:3306
 Source Schema         : ymzw2

 Target Server Type    : MySQL
 Target Server Version : 80046 (8.0.46)
 File Encoding         : 65001

 Date: 04/09/2026 08:55:22
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for ai_agent
-- ----------------------------
DROP TABLE IF EXISTS `ai_agent`;
CREATE TABLE `ai_agent`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ä¸»é”®ID',
  `agent_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'æ™ºèƒ½ä½“åç§°',
  `agent_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'æ™ºèƒ½ä½“ç±»åž‹(CHAT/PREDICTIVE_ALARM/PART_INSPECTION/REPORT/KNOWLEDGE_QA/EMBEDDING)',
  `agent_avatar` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'å¤´åƒURL',
  `system_prompt` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'ç³»ç»Ÿæç¤ºè¯',
  `api_endpoint` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'APIåœ°å€',
  `api_key` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'APIå¯†é’¥',
  `model_identifier` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'åŸºåº§æ¨¡åž‹æ ‡è¯†ç¬¦',
  `temperature` double NULL DEFAULT NULL COMMENT 'æ¸©åº¦å‚æ•°(0-2)',
  `tools_config` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'å·¥å…·é…ç½®(JSON)',
  `knowledge_base_ids` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'å…³è”çŸ¥è¯†åº“IDåˆ—è¡¨(é€—å·åˆ†éš”)',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'æè¿°',
  `status` int NULL DEFAULT 1 COMMENT 'çŠ¶æ€(0-ç¦ç”¨,1-å¯ç”¨)',
  `sort_order` int NULL DEFAULT 0 COMMENT 'æŽ’åºå·',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'å¤‡æ³¨',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  `create_user` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'åˆ›å»ºäºº',
  `update_user` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'æ›´æ–°äºº',
  `delete_flag` int NULL DEFAULT 0 COMMENT 'åˆ é™¤æ ‡å¿—(0-æœªåˆ é™¤,1-å·²åˆ é™¤)',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_agent_type`(`agent_type` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AIæ™ºèƒ½ä½“é…ç½®è¡¨' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_agent
-- ----------------------------

-- ----------------------------
-- Table structure for alert_action_log
-- ----------------------------
DROP TABLE IF EXISTS `alert_action_log`;
CREATE TABLE `alert_action_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `alert_id` bigint NOT NULL COMMENT '关联告警事件ID',
  `action` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '动作 ACK/RESOLVE/ESCALATE',
  `operator` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '操作人',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_alert_log_alert_id`(`alert_id` ASC) USING BTREE,
  INDEX `idx_alert_log_action`(`action` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '告警处置日志表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of alert_action_log
-- ----------------------------

-- ----------------------------
-- Table structure for alert_event
-- ----------------------------
DROP TABLE IF EXISTS `alert_event`;
CREATE TABLE `alert_event`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `equipment_id` int NULL DEFAULT NULL COMMENT '设备ID',
  `equipment_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '设备名称(冗余)',
  `sensor_id` int NULL DEFAULT NULL COMMENT '传感器ID',
  `sensor_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '传感器编号',
  `sensor_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '传感器名称',
  `alert_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '告警类型 RULE/STAT/PREDICT',
  `alert_level` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '告警等级 NORMAL/WARNING/SEVERE',
  `alert_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '告警状态 FIRING/ACKED/RESOLVED',
  `evidence` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '命中证据JSON({layer,value,threshold,sustain})',
  `summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '告警摘要(L3回填)',
  `root_cause` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '根因(L3回填)',
  `suggestion` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '处置建议(L3回填)',
  `sensor_value` double NULL DEFAULT NULL COMMENT '触发时的传感器值',
  `trigger_time` datetime NULL DEFAULT NULL COMMENT '触发时间',
  `resolve_time` datetime NULL DEFAULT NULL COMMENT '解除时间',
  `escalation_count` int NULL DEFAULT 0 COMMENT '升级次数',
  `predicted_breach_time` datetime NULL DEFAULT NULL COMMENT '预计越界时刻(PREDICT类型专用)',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_alert_event_equipment`(`equipment_id` ASC) USING BTREE,
  INDEX `idx_alert_event_sensor`(`sensor_id` ASC) USING BTREE,
  INDEX `idx_alert_event_status`(`alert_status` ASC) USING BTREE,
  INDEX `idx_alert_event_trigger_time`(`trigger_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 974 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '告警事件表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of alert_event
-- ----------------------------
INSERT INTO `alert_event` VALUES (966, 1, '一号数控机床', 1, 'TEMP-001', '温度传感器', 'RULE', 'CRITICAL', 'RESOLVED', '{\"breach\":\"upper\",\"value\":86.67,\"sustain\":1,\"layer\":\"RULE\"}', NULL, NULL, NULL, 86.67, '2026-09-02 14:22:19', '2026-09-02 14:23:02', 0, NULL, '2026-09-02 14:22:20');
INSERT INTO `alert_event` VALUES (967, 1, '一号数控机床', 1, 'TEMP-001', '温度传感器', 'RULE', 'CRITICAL', 'RESOLVED', '{\"breach\":\"upper\",\"value\":86.9,\"sustain\":1,\"layer\":\"RULE\"}', NULL, NULL, NULL, 86.9, '2026-09-02 14:45:41', '2026-09-02 14:46:05', 0, NULL, '2026-09-02 14:45:41');
INSERT INTO `alert_event` VALUES (968, 1, '一号数控机床', 1, 'TEMP-001', '温度传感器', 'RULE', 'CRITICAL', 'RESOLVED', '{\"breach\":\"upper\",\"value\":84.48,\"sustain\":1,\"layer\":\"RULE\"}', NULL, NULL, NULL, 84.48, '2026-09-02 14:46:21', '2026-09-02 14:46:38', 0, NULL, '2026-09-02 14:46:21');
INSERT INTO `alert_event` VALUES (969, 1, '一号数控机床', 1, 'TEMP-001', '温度传感器', 'RULE', 'CRITICAL', 'RESOLVED', '{\"breach\":\"upper\",\"value\":88.58,\"sustain\":1,\"layer\":\"RULE\"}', NULL, NULL, NULL, 88.58, '2026-09-02 14:47:01', '2026-09-02 14:47:43', 0, NULL, '2026-09-02 14:47:01');
INSERT INTO `alert_event` VALUES (970, 1, '一号数控机床', 1, 'TEMP-001', '温度传感器', 'RULE', 'CRITICAL', 'RESOLVED', '{\"breach\":\"upper\",\"value\":85.0,\"sustain\":1,\"layer\":\"RULE\"}', NULL, NULL, NULL, 85, '2026-09-02 16:00:33', '2026-09-02 16:01:13', 0, NULL, '2026-09-02 16:00:33');
INSERT INTO `alert_event` VALUES (971, 1, '一号数控机床', 1, 'TEMP-001', '温度传感器', 'RULE', 'CRITICAL', 'RESOLVED', '{\"breach\":\"upper\",\"value\":89.59,\"sustain\":1,\"layer\":\"RULE\"}', NULL, NULL, NULL, 89.59, '2026-09-02 16:01:14', '2026-09-02 16:10:19', 0, NULL, '2026-09-02 16:01:14');
INSERT INTO `alert_event` VALUES (972, 1, '一号数控机床', 1, 'TEMP-001', '温度传感器', 'RULE', 'CRITICAL', 'RESOLVED', '{\"breach\":\"upper\",\"value\":86.11,\"sustain\":1,\"layer\":\"RULE\"}', NULL, NULL, NULL, 86.11, '2026-09-02 16:39:53', '2026-09-02 16:41:08', 0, NULL, '2026-09-02 16:39:53');
INSERT INTO `alert_event` VALUES (973, 1, '一号数控机床', 1, 'TEMP-001', '温度传感器', 'RULE', 'CRITICAL', 'FIRING', '{\"breach\":\"upper\",\"value\":84.69,\"sustain\":1,\"layer\":\"RULE\"}', NULL, NULL, NULL, 84.69, '2026-09-02 16:51:27', NULL, 0, NULL, '2026-09-02 16:51:27');

-- ----------------------------
-- Table structure for alert_rule
-- ----------------------------
DROP TABLE IF EXISTS `alert_rule`;
CREATE TABLE `alert_rule`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `sensor_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '传感器编号',
  `sensor_id` int NULL DEFAULT NULL COMMENT '传感器ID(主键标识,匹配与关联用)',
  `upper_limit` double NULL DEFAULT NULL COMMENT '阈值上限',
  `lower_limit` double NULL DEFAULT NULL COMMENT '阈值下限',
  `sustain_points` int NULL DEFAULT NULL COMMENT '持续越界点数(防抖)',
  `silence_start` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '静默时段开始 HH:mm',
  `silence_end` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '静默时段结束 HH:mm',
  `level` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '命中告警等级 NORMAL/WARNING/SEVERE',
  `enabled` int NULL DEFAULT 1 COMMENT '是否启用(0-禁用,1-启用)',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_alert_rule_sensor`(`sensor_code` ASC) USING BTREE,
  INDEX `idx_alert_rule_enabled`(`enabled` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '告警规则表(L1阈值规则)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of alert_rule
-- ----------------------------
INSERT INTO `alert_rule` VALUES (1, 'TEMP-001', 1, 40, 0, 5, NULL, NULL, 'WARNING', 0, '2026-08-28 09:57:38', '2026-08-31 00:18:24');
INSERT INTO `alert_rule` VALUES (3, 'TEMP-002', 4, 50, 0, 4, NULL, NULL, 'WARNING', 1, '2026-08-30 11:06:17', '2026-08-30 11:06:17');
INSERT INTO `alert_rule` VALUES (4, 'TEMP-001', 1, 55, 0, 3, NULL, NULL, 'IMPORTANT', 0, '2026-08-31 00:18:29', '2026-08-31 00:46:34');
INSERT INTO `alert_rule` VALUES (5, 'TEMP-001', 1, 70, 0, 2, NULL, NULL, 'SEVERE', 0, '2026-08-31 00:46:46', '2026-08-31 00:46:46');
INSERT INTO `alert_rule` VALUES (6, 'TEMP-001', 1, 70, 0, 1, NULL, NULL, 'CRITICAL', 1, '2026-08-31 00:46:46', '2026-08-31 00:46:46');

-- ----------------------------
-- Table structure for equipment
-- ----------------------------
DROP TABLE IF EXISTS `equipment`;
CREATE TABLE `equipment`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `equipment_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '设备编号',
  `equipment_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '设备名称',
  `equipment_model_id` int NULL DEFAULT NULL COMMENT '设备型号ID',
  `equipment_model_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '设备型号名称',
  `workshop_id` int NULL DEFAULT NULL COMMENT '所属车间ID',
  `workshop_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '所属车间名称',
  `equipment_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '运行状态(0-运行中,1-停机,2-维修,3-待验收)',
  `equipment_install_date` date NULL DEFAULT NULL COMMENT '安装日期',
  `equipment_user_id` int NULL DEFAULT NULL COMMENT '负责人ID',
  `equipment_user_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '负责人名称',
  `equipment_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `layout_x` double NULL DEFAULT NULL COMMENT '孪生布局X(米,NULL未摆放)',
  `layout_y` double NULL DEFAULT NULL COMMENT '孪生布局Y(米,NULL未摆放)',
  `create_time` datetime NULL DEFAULT NULL COMMENT '记录创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '记录修改时间',
  `create_user` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人',
  `update_user` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '修改人',
  `delete_flag` int NULL DEFAULT 0 COMMENT '删除状态(0-未删除,1-已删除)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_equipment_no`(`equipment_no` ASC) USING BTREE,
  INDEX `idx_equipment_workshop`(`workshop_id` ASC) USING BTREE,
  INDEX `idx_equipment_status`(`equipment_status` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备基础信息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of equipment
-- ----------------------------
INSERT INTO `equipment` VALUES (1, 'EQ-001', '一号数控机床', NULL, 'CNC-750', 1, '一号车间', '1', '2026-01-10', 1, '若依', '机床易升温', 3.5, -2, '2026-08-28 09:57:08', NULL, NULL, NULL, 0);
INSERT INTO `equipment` VALUES (2, 'EQ-002', '二号数控机床', NULL, 'CNC-850', 2, '二号车间', '1', '2026-02-15', NULL, '', NULL, NULL, NULL, '2026-08-28 09:57:14', NULL, NULL, NULL, 0);
INSERT INTO `equipment` VALUES (3, 'TEST-OWNER-01', '负责人测试设备', NULL, 'TST', 1, '一号车间', '0', NULL, NULL, '', '', NULL, NULL, NULL, NULL, NULL, NULL, 1);

-- ----------------------------
-- Table structure for equipment_sensor
-- ----------------------------
DROP TABLE IF EXISTS `equipment_sensor`;
CREATE TABLE `equipment_sensor`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `sensor_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '传感器参数编号(如TH-001)',
  `sensor_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '传感器参数名称(如主轴转速)',
  `sensor_unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '传感器参数单位(rpm/°C/mm/s)',
  `sensor_status` int NULL DEFAULT 1 COMMENT '传感器状态(0-禁用,1-启用)',
  `equipment_id` int NULL DEFAULT NULL COMMENT '所属设备ID',
  `equipment_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '所属设备名称(冗余字段)',
  `create_time` datetime NULL DEFAULT NULL COMMENT '记录创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '记录修改时间',
  `create_user` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人',
  `update_user` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '修改人',
  `delete_flag` int NULL DEFAULT 0 COMMENT '删除状态(0-未删除,1-已删除)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sensor_code`(`sensor_code` ASC) USING BTREE,
  INDEX `idx_sensor_equipment`(`equipment_id` ASC) USING BTREE,
  INDEX `idx_sensor_status`(`sensor_status` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 17 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备传感器参数表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of equipment_sensor
-- ----------------------------
INSERT INTO `equipment_sensor` VALUES (1, 'TEMP-001', '温度传感器', '°C', 1, 1, '一号数控机床', '2026-08-28 09:57:17', NULL, NULL, NULL, 0);
INSERT INTO `equipment_sensor` VALUES (2, 'HUM-001', '湿度传感器', '%RH', 1, 1, '一号数控机床', '2026-08-28 09:57:32', NULL, NULL, NULL, 0);
INSERT INTO `equipment_sensor` VALUES (3, 'VIB-001', '振动传感器', 'mm/s', 1, 1, '一号数控机床', '2026-08-28 09:57:32', NULL, NULL, NULL, 0);
INSERT INTO `equipment_sensor` VALUES (4, 'TEMP-002', '温度传感器', '°C', 1, 2, '二号数控机床', '2026-08-30 03:56:58', NULL, NULL, NULL, 0);
INSERT INTO `equipment_sensor` VALUES (5, 'VIB-002', '振动传感器', 'mm/s', 1, 2, '二号数控机床', '2026-08-30 03:56:58', NULL, NULL, NULL, 0);
INSERT INTO `equipment_sensor` VALUES (6, 'HUM-002', '湿度传感器', '%RH', 1, 2, '二号数控机床', '2026-08-30 03:56:58', NULL, NULL, NULL, 0);
INSERT INTO `equipment_sensor` VALUES (7, 'AUTO-201944700160', '竞赛温度传感器-已校准', '°C', 1, 1, '一号数控机床', NULL, NULL, NULL, NULL, 1);
INSERT INTO `equipment_sensor` VALUES (8, 'AUTO-203902747857', '竞赛温度传感器-已校准', '°C', 1, 1, '一号数控机床', NULL, NULL, NULL, NULL, 1);
INSERT INTO `equipment_sensor` VALUES (9, 'AUTO-204039259113', '竞赛温度传感器-已校准', '°C', 1, 1, '一号数控机床', NULL, NULL, NULL, NULL, 1);
INSERT INTO `equipment_sensor` VALUES (10, 'AUTO-205024565465', '竞赛温度传感器-已校准', '°C', 1, 1, '一号数控机床', NULL, NULL, NULL, NULL, 1);
INSERT INTO `equipment_sensor` VALUES (11, 'AUTO-205801647844', '竞赛温度传感器-已校准', '°C', 1, 1, '一号数控机床', NULL, NULL, NULL, NULL, 1);
INSERT INTO `equipment_sensor` VALUES (12, 'AUTO-210254255849', '竞赛温度传感器-已校准', '°C', 1, 1, '一号数控机床', NULL, NULL, NULL, NULL, 1);
INSERT INTO `equipment_sensor` VALUES (13, 'AUTO-210427473565', '竞赛温度传感器-已校准', '°C', 1, 1, '一号数控机床', NULL, NULL, NULL, NULL, 1);
INSERT INTO `equipment_sensor` VALUES (14, 'AUTO-210608644425', '竞赛温度传感器-已校准', '°C', 1, 1, '一号数控机床', NULL, NULL, NULL, NULL, 1);
INSERT INTO `equipment_sensor` VALUES (15, 'AUTO-210915294302', '竞赛温度传感器-已校准', '°C', 1, 1, '一号数控机床', NULL, NULL, NULL, NULL, 1);
INSERT INTO `equipment_sensor` VALUES (16, 'AUTO-212821666655', '竞赛温度传感器-已校准', '°C', 1, 1, '一号数控机床', NULL, NULL, NULL, NULL, 1);

-- ----------------------------
-- Table structure for gen_table
-- ----------------------------
DROP TABLE IF EXISTS `gen_table`;
CREATE TABLE `gen_table`  (
  `table_id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `table_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '表名称',
  `table_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '表描述',
  `sub_table_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '关联子表的表名',
  `sub_table_fk_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '子表关联的外键名',
  `class_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '实体类名称',
  `tpl_category` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'crud' COMMENT '使用的模板（crud单表操作 tree树表操作）',
  `tpl_web_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '前端模板类型（element-ui模版 element-plus模版）',
  `package_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '生成包路径',
  `module_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '生成模块名',
  `business_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '生成业务名',
  `function_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '生成功能名',
  `function_author` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '生成功能作者',
  `gen_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '生成代码方式（0zip压缩包 1自定义路径）',
  `gen_path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '/' COMMENT '生成路径（不填默认项目路径）',
  `options` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '其它生成选项',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`table_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '代码生成业务表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of gen_table
-- ----------------------------

-- ----------------------------
-- Table structure for gen_table_column
-- ----------------------------
DROP TABLE IF EXISTS `gen_table_column`;
CREATE TABLE `gen_table_column`  (
  `column_id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `table_id` bigint NULL DEFAULT NULL COMMENT '归属表编号',
  `column_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '列名称',
  `column_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '列描述',
  `column_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '列类型',
  `java_type` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'JAVA类型',
  `java_field` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'JAVA字段名',
  `is_pk` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '是否主键（1是）',
  `is_increment` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '是否自增（1是）',
  `is_required` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '是否必填（1是）',
  `is_insert` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '是否为插入字段（1是）',
  `is_edit` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '是否编辑字段（1是）',
  `is_list` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '是否列表字段（1是）',
  `is_query` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '是否查询字段（1是）',
  `query_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'EQ' COMMENT '查询方式（等于、不等于、大于、小于、范围）',
  `html_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '显示类型（文本框、文本域、下拉框、复选框、单选框、日期控件）',
  `dict_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '字典类型',
  `sort` int NULL DEFAULT NULL COMMENT '排序',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`column_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '代码生成业务表字段' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of gen_table_column
-- ----------------------------

-- ----------------------------
-- Table structure for holiday_calendar
-- ----------------------------
DROP TABLE IF EXISTS `holiday_calendar`;
CREATE TABLE `holiday_calendar`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `cal_date` date NOT NULL COMMENT '日期(国务院安排涉及的例外日: 放假日/补班日)',
  `cal_year` int NOT NULL COMMENT '年份(同步批次键)',
  `is_workday` tinyint NOT NULL COMMENT '1=工作日(调休补班), 0=休息日(法定节假日/调休连休)',
  `name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '节假日名称(如 春节/国庆节后补班)',
  `source` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '数据来源: TIMOR/HOLIDAY_CN',
  `fetch_time` datetime NULL DEFAULT NULL COMMENT '同步时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_cal_date`(`cal_date` ASC) USING BTREE,
  INDEX `idx_cal_year`(`cal_year` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 742 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '法定工作日数据缓存(外部API自动同步,免人工维护)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of holiday_calendar
-- ----------------------------
INSERT INTO `holiday_calendar` VALUES (703, '2026-01-01', 2026, 0, '元旦', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (704, '2026-01-02', 2026, 0, '元旦', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (705, '2026-01-03', 2026, 0, '元旦', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (706, '2026-01-04', 2026, 1, '元旦后补班', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (707, '2026-02-14', 2026, 1, '春节前补班', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (708, '2026-02-15', 2026, 0, '春节', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (709, '2026-02-16', 2026, 0, '除夕', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (710, '2026-02-17', 2026, 0, '初一', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (711, '2026-02-18', 2026, 0, '初二', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (712, '2026-02-19', 2026, 0, '初三', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (713, '2026-02-20', 2026, 0, '初四', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (714, '2026-02-21', 2026, 0, '初五', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (715, '2026-02-22', 2026, 0, '初六', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (716, '2026-02-23', 2026, 0, '初七', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (717, '2026-02-28', 2026, 1, '春节后补班', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (718, '2026-04-04', 2026, 0, '清明节', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (719, '2026-04-05', 2026, 0, '清明节', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (720, '2026-04-06', 2026, 0, '清明节', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (721, '2026-05-01', 2026, 0, '劳动节', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (722, '2026-05-02', 2026, 0, '劳动节', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (723, '2026-05-03', 2026, 0, '劳动节', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (724, '2026-05-04', 2026, 0, '劳动节', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (725, '2026-05-05', 2026, 0, '劳动节', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (726, '2026-05-09', 2026, 1, '劳动节后补班', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (727, '2026-06-19', 2026, 0, '端午节', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (728, '2026-06-20', 2026, 0, '端午节', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (729, '2026-06-21', 2026, 0, '端午节', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (730, '2026-09-20', 2026, 1, '中秋节前补班', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (731, '2026-09-25', 2026, 0, '中秋节', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (732, '2026-09-26', 2026, 0, '中秋节', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (733, '2026-09-27', 2026, 0, '中秋节', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (734, '2026-10-01', 2026, 0, '国庆节', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (735, '2026-10-02', 2026, 0, '国庆节', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (736, '2026-10-03', 2026, 0, '国庆节', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (737, '2026-10-04', 2026, 0, '国庆节', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (738, '2026-10-05', 2026, 0, '国庆节', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (739, '2026-10-06', 2026, 0, '国庆节', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (740, '2026-10-07', 2026, 0, '国庆节', 'TIMOR', '2026-09-03 18:04:03');
INSERT INTO `holiday_calendar` VALUES (741, '2026-10-10', 2026, 1, '国庆节后补班', 'TIMOR', '2026-09-03 18:04:03');

-- ----------------------------
-- Table structure for inspection_records
-- ----------------------------
DROP TABLE IF EXISTS `inspection_records`;
CREATE TABLE `inspection_records`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `part_id` bigint NULL DEFAULT NULL COMMENT '零件ID',
  `inspection_time` datetime NULL DEFAULT NULL COMMENT '检测时间',
  `is_qualified` tinyint(1) NULL DEFAULT NULL COMMENT '是否合格(1-合格,0-不合格)',
  `inspection_details` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '检测详情',
  `inspection_suggestion` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '检测建议',
  `inspector` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '检测人员',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_record_part_id`(`part_id` ASC) USING BTREE,
  INDEX `idx_record_inspection_time`(`inspection_time` ASC) USING BTREE,
  INDEX `idx_record_qualified`(`is_qualified` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '检测记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of inspection_records
-- ----------------------------

-- ----------------------------
-- Table structure for inspection_standards
-- ----------------------------
DROP TABLE IF EXISTS `inspection_standards`;
CREATE TABLE `inspection_standards`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `part_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '零件类型',
  `standard_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '标准名称',
  `standard_parameters` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '标准参数(JSON格式)',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '描述',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_standard_part_type`(`part_type` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '检测标准表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of inspection_standards
-- ----------------------------

-- ----------------------------
-- Table structure for maintenance_plan
-- ----------------------------
DROP TABLE IF EXISTS `maintenance_plan`;
CREATE TABLE `maintenance_plan`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plan_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '计划编号 MP+yyyyMMddHHmmss+3位随机',
  `plan_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '计划名称',
  `equipment_id` int NOT NULL COMMENT '维保对象(设备级)',
  `equipment_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `maintenance_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '保养类型: 日常保养/一级保养/二级保养/精度校准/润滑保养',
  `content` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '维护内容说明',
  `repeat_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'ONCE/DAILY/WEEKDAYS/MONTHLY/LEGAL_WORKDAY',
  `fire_time` time NOT NULL COMMENT '触发时刻 HH:mm',
  `fire_day` int NULL DEFAULT NULL COMMENT 'MONTHLY: 每月几号(1-31)',
  `fire_date` date NULL DEFAULT NULL COMMENT 'ONCE: 触发日期',
  `next_fire_time` datetime NULL DEFAULT NULL COMMENT '下次触发时间(预计算,DONE 为 NULL)',
  `last_fire_time` datetime NULL DEFAULT NULL COMMENT '上次触发时间',
  `assignee_id` bigint NULL DEFAULT NULL COMMENT '负责人用户ID(可空=生成后待指派)',
  `assignee_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/PAUSED/DONE',
  `create_time` datetime NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_plan_no`(`plan_no` ASC) USING BTREE,
  INDEX `idx_next_fire`(`status` ASC, `next_fire_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '维护计划' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of maintenance_plan
-- ----------------------------
INSERT INTO `maintenance_plan` VALUES (1, 'MP20260831114259892', 'E2E测试-一次性计划', 1, '一号数控机床', '润滑保养', '导轨润滑与清洁', 'ONCE', '11:45:00', NULL, '2026-08-31', NULL, '2026-08-31 11:45:00', NULL, NULL, 'DONE', '2026-08-31 11:43:00', '2026-08-31 11:45:00');
INSERT INTO `maintenance_plan` VALUES (2, 'MP20260831114338739', 'E2E测试-每日巡检计划', 1, '一号数控机床', '日常保养', '开机前巡检油位与气压', 'DAILY', '11:46:00', NULL, NULL, '2026-09-01 11:46:00', '2026-08-31 11:47:00', NULL, NULL, 'PAUSED', '2026-08-31 11:43:38', '2026-08-31 11:53:43');

-- ----------------------------
-- Table structure for parts
-- ----------------------------
DROP TABLE IF EXISTS `parts`;
CREATE TABLE `parts`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `part_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '零件名称',
  `part_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '零件编码',
  `standard_id` bigint NULL DEFAULT NULL COMMENT '检测标准ID',
  `parameters` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '零件参数(JSON格式)',
  `is_qualified` tinyint(1) NULL DEFAULT NULL COMMENT '是否合格(1-合格,0-不合格)',
  `inspection_time` datetime NULL DEFAULT NULL COMMENT '检测时间',
  `inspection_details` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '检测详情',
  `inspection_suggestion` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '检测建议',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `delete_flag` tinyint(1) NULL DEFAULT 0 COMMENT '删除标志(0-正常,1-已删除)',
  `inspection_flag` tinyint(1) NULL DEFAULT NULL COMMENT '检测标志(0-未检测,1-已检测)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_part_code`(`part_code` ASC) USING BTREE,
  INDEX `idx_part_standard`(`standard_id` ASC) USING BTREE,
  INDEX `idx_part_qualified`(`is_qualified` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '零件表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of parts
-- ----------------------------

-- ----------------------------
-- Table structure for predict_alert
-- ----------------------------
DROP TABLE IF EXISTS `predict_alert`;
CREATE TABLE `predict_alert`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `equipment_id` int NULL DEFAULT NULL COMMENT '设备ID',
  `equipment_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '设备名称',
  `sensor_id` int NULL DEFAULT NULL COMMENT '传感器ID',
  `sensor_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '传感器编号',
  `sensor_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '传感器名称',
  `alert_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '告警类型(本表恒为PREDICT,保留列与alert_event对齐)',
  `alert_level` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '告警级别(NORMAL/WARNING/SEVERE)',
  `alert_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '告警状态(FIRING/ACKED/RESOLVED)',
  `evidence` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '证据JSON(slope/r2/onset/t1等)',
  `summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '告警摘要',
  `root_cause` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '根因',
  `suggestion` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '处置建议',
  `sensor_value` double NULL DEFAULT NULL COMMENT '触发时数值(平滑值,两位小数)',
  `trigger_time` datetime NULL DEFAULT NULL COMMENT '触发时间',
  `resolve_time` datetime NULL DEFAULT NULL COMMENT '解除时间',
  `escalation_count` int NULL DEFAULT NULL COMMENT '升级次数',
  `predicted_breach_time` datetime NULL DEFAULT NULL COMMENT '预计越界时刻',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_predict_alert_sensor`(`sensor_code` ASC) USING BTREE,
  INDEX `idx_predict_alert_trigger_time`(`trigger_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 975 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '预测告警表(PREDICT类型,与告警记录alert_event物理分流)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of predict_alert
-- ----------------------------
INSERT INTO `predict_alert` VALUES (962, 1, '一号数控机床', 1, 'TEMP-001', '温度传感器', 'PREDICT', 'WARNING', 'RESOLVED', '{\"r2\":null,\"cusum\":7.8049,\"madRatio\":0.9167,\"t1Points\":null,\"slope\":null,\"onset\":1788193114084,\"layer\":\"PREDICT\"}', NULL, NULL, NULL, 45.71, '2026-08-31 16:26:05', '2026-08-31 16:26:17', 0, NULL, '2026-08-31 16:26:05');
INSERT INTO `predict_alert` VALUES (963, 1, '一号数控机床', 2, 'HUM-001', '湿度传感器', 'PREDICT', 'WARNING', 'RESOLVED', '{\"r2\":null,\"cusum\":7.2855,\"madRatio\":0.7711,\"t1Points\":null,\"slope\":null,\"onset\":1788193114085,\"layer\":\"PREDICT\"}', NULL, NULL, NULL, 53.35, '2026-08-31 16:26:05', '2026-08-31 16:26:17', 0, NULL, '2026-08-31 16:26:05');
INSERT INTO `predict_alert` VALUES (964, 1, '一号数控机床', 3, 'VIB-001', '振动传感器', 'PREDICT', 'WARNING', 'RESOLVED', '{\"r2\":null,\"cusum\":0.9577,\"madRatio\":0.963,\"t1Points\":null,\"slope\":null,\"onset\":1788193114085,\"layer\":\"PREDICT\"}', NULL, NULL, NULL, 2.88, '2026-08-31 16:26:05', '2026-08-31 16:26:17', 0, NULL, '2026-08-31 16:26:05');
INSERT INTO `predict_alert` VALUES (965, 1, '一号数控机床', 1, 'TEMP-001', '温度传感器', 'PREDICT', 'WARNING', 'RESOLVED', '{\"r2\":null,\"cusum\":7.2616,\"madRatio\":3.9009,\"t1Points\":null,\"slope\":null,\"onset\":1788193114084,\"layer\":\"PREDICT\"}', NULL, NULL, NULL, 43.52, '2026-08-31 16:26:35', '2026-08-31 16:27:33', 0, NULL, '2026-08-31 16:26:35');
INSERT INTO `predict_alert` VALUES (966, 1, '一号数控机床', 2, 'HUM-001', '湿度传感器', 'PREDICT', 'WARNING', 'RESOLVED', '{\"r2\":null,\"cusum\":7.4694,\"madRatio\":2.4286,\"t1Points\":null,\"slope\":null,\"onset\":1788193114586,\"layer\":\"PREDICT\"}', NULL, NULL, NULL, 49.95, '2026-08-31 16:26:35', '2026-08-31 16:27:33', 0, NULL, '2026-08-31 16:26:35');
INSERT INTO `predict_alert` VALUES (967, 1, '一号数控机床', 3, 'VIB-001', '振动传感器', 'PREDICT', 'WARNING', 'RESOLVED', '{\"r2\":null,\"cusum\":0.9551,\"madRatio\":3.1111,\"t1Points\":null,\"slope\":null,\"onset\":1788193114586,\"layer\":\"PREDICT\"}', NULL, NULL, NULL, 2.48, '2026-08-31 16:26:35', '2026-08-31 16:27:33', 0, NULL, '2026-08-31 16:26:35');
INSERT INTO `predict_alert` VALUES (968, 1, '一号数控机床', 2, 'HUM-001', '湿度传感器', 'PREDICT', 'WARNING', 'RESOLVED', '{\"r2\":null,\"cusum\":4.8753,\"madRatio\":0.2671,\"t1Points\":null,\"slope\":null,\"onset\":1788193115086,\"layer\":\"PREDICT\"}', NULL, NULL, NULL, 49.97, '2026-08-31 16:27:36', '2026-08-31 16:27:52', 0, NULL, '2026-08-31 16:27:36');
INSERT INTO `predict_alert` VALUES (969, 1, '一号数控机床', 3, 'VIB-001', '振动传感器', 'PREDICT', 'WARNING', 'RESOLVED', '{\"r2\":null,\"cusum\":0.5466,\"madRatio\":0.2177,\"t1Points\":null,\"slope\":null,\"onset\":1788193115087,\"layer\":\"PREDICT\"}', NULL, NULL, NULL, 2.49, '2026-08-31 16:27:36', '2026-08-31 16:27:52', 0, NULL, '2026-08-31 16:27:36');
INSERT INTO `predict_alert` VALUES (970, 1, '一号数控机床', 1, 'TEMP-001', '温度传感器', 'PREDICT', 'WARNING', 'RESOLVED', '{\"r2\":null,\"cusum\":5.6528,\"madRatio\":0.1829,\"t1Points\":null,\"slope\":null,\"onset\":1788193115086,\"layer\":\"PREDICT\"}', NULL, NULL, NULL, 45.63, '2026-08-31 16:28:06', '2026-08-31 16:28:52', 0, NULL, '2026-08-31 16:28:06');
INSERT INTO `predict_alert` VALUES (971, 1, '一号数控机床', 2, 'HUM-001', '湿度传感器', 'PREDICT', 'WARNING', 'RESOLVED', '{\"r2\":null,\"cusum\":4.7285,\"madRatio\":0.1988,\"t1Points\":null,\"slope\":null,\"onset\":1788193115086,\"layer\":\"PREDICT\"}', NULL, NULL, NULL, 49.9, '2026-08-31 16:28:06', '2026-08-31 16:28:52', 0, NULL, '2026-08-31 16:28:06');
INSERT INTO `predict_alert` VALUES (972, 1, '一号数控机床', 1, 'TEMP-001', '温度传感器', 'PREDICT', 'WARNING', 'RESOLVED', '{\"r2\":null,\"cusum\":4.9471,\"madRatio\":0.4212,\"t1Points\":null,\"slope\":null,\"onset\":1788193494091,\"layer\":\"PREDICT\"}', NULL, NULL, NULL, 44.29, '2026-08-31 16:29:07', '2026-08-31 16:32:43', 0, NULL, '2026-08-31 16:29:07');
INSERT INTO `predict_alert` VALUES (973, 1, '一号数控机床', 2, 'HUM-001', '湿度传感器', 'PREDICT', 'WARNING', 'FIRING', '{\"r2\":null,\"cusum\":5.194,\"madRatio\":0.5364,\"t1Points\":null,\"slope\":null,\"onset\":1788193494091,\"layer\":\"PREDICT\"}', NULL, NULL, NULL, 49.71, '2026-08-31 16:29:07', NULL, 0, NULL, '2026-08-31 16:29:07');
INSERT INTO `predict_alert` VALUES (974, 1, '一号数控机床', 3, 'VIB-001', '振动传感器', 'PREDICT', 'WARNING', 'FIRING', '{\"r2\":null,\"cusum\":0.5628,\"madRatio\":0.5574,\"t1Points\":null,\"slope\":null,\"onset\":1788193494091,\"layer\":\"PREDICT\"}', NULL, NULL, NULL, 2.45, '2026-08-31 16:29:07', NULL, 0, NULL, '2026-08-31 16:29:07');

-- ----------------------------
-- Table structure for predict_result
-- ----------------------------
DROP TABLE IF EXISTS `predict_result`;
CREATE TABLE `predict_result`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `sensor_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '传感器编号',
  `equipment_id` int NULL DEFAULT NULL COMMENT '设备ID',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'NORMAL' COMMENT '预测状态(NORMAL/DEGRADING/BREACHED)',
  `health_score` double NULL DEFAULT NULL COMMENT '健康度得分(0-100)',
  `slope` double NULL DEFAULT NULL COMMENT '趋势斜率(WLS拟合)',
  `t1_points` int NULL DEFAULT 0 COMMENT '预计越界点数(相对当前)',
  `predicted_breach_time` datetime NULL DEFAULT NULL COMMENT '预测越限时间(趋势外推)',
  `onset_time` datetime NULL DEFAULT NULL COMMENT '劣化起点时间',
  `band_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '趋势置信带JSON',
  `ai_available` int NULL DEFAULT 0 COMMENT 'AI预测可用(0-不可用,1-可用)',
  `ai_p10_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'AI预测P10分位JSON',
  `ai_p50_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'AI预测P50分位JSON',
  `ai_p90_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'AI预测P90分位JSON',
  `divergence_ratio` double NULL DEFAULT NULL COMMENT 'AI与统计外激发散比',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_predict_result_sensor_code`(`sensor_code` ASC) USING BTREE,
  INDEX `idx_predict_result_equipment`(`equipment_id` ASC) USING BTREE,
  INDEX `idx_predict_result_status`(`status` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 13 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '预测性维护结果表(按传感器的最新快照)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of predict_result
-- ----------------------------
INSERT INTO `predict_result` VALUES (7, 'TEMP-001', 1, 'DEGRADING', 100, NULL, NULL, NULL, '2026-09-01 00:24:54', NULL, 0, NULL, NULL, NULL, NULL, '2026-08-31 16:32:39');
INSERT INTO `predict_result` VALUES (8, 'HUM-001', 1, 'DEGRADING', 82.1, NULL, 0, NULL, '2026-09-01 00:24:54', NULL, 0, NULL, NULL, NULL, NULL, '2026-08-31 16:32:39');
INSERT INTO `predict_result` VALUES (9, 'VIB-001', 1, 'DEGRADING', 81.4, NULL, NULL, NULL, '2026-09-01 00:24:54', NULL, 0, NULL, NULL, NULL, NULL, '2026-08-31 16:32:39');
INSERT INTO `predict_result` VALUES (10, 'TEMP-002', 2, 'NORMAL', 100, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, '2026-08-31 16:32:39');
INSERT INTO `predict_result` VALUES (11, 'VIB-002', 2, 'DEGRADING', 73.3, NULL, 0, NULL, '2026-08-31 14:46:02', NULL, 0, NULL, NULL, NULL, NULL, '2026-08-31 16:32:39');
INSERT INTO `predict_result` VALUES (12, 'HUM-002', 2, 'DEGRADING', 65.1, NULL, 0, NULL, '2026-08-31 14:46:02', NULL, 0, NULL, NULL, NULL, NULL, '2026-08-31 16:32:40');

-- ----------------------------
-- Table structure for qna_management
-- ----------------------------
DROP TABLE IF EXISTS `qna_management`;
CREATE TABLE `qna_management`  (
  `qna_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '问答记录ID(UUID)',
  `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用户ID',
  `question` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '问题内容',
  `answer` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '回答内容',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用户名',
  PRIMARY KEY (`qna_id`) USING BTREE,
  INDEX `idx_qna_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_qna_create_time`(`create_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI问答记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of qna_management
-- ----------------------------

-- ----------------------------
-- Table structure for sys_config
-- ----------------------------
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config`  (
  `config_id` int NOT NULL AUTO_INCREMENT COMMENT '参数主键',
  `config_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '参数名称',
  `config_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '参数键名',
  `config_value` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '参数键值',
  `config_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'N' COMMENT '系统内置（Y是 N否）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`config_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 100 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '参数配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_config
-- ----------------------------
INSERT INTO `sys_config` VALUES (1, '主框架页-默认皮肤样式名称', 'sys.index.skinName', 'skin-blue', 'Y', 'admin', '2026-08-26 09:13:26', '', NULL, '蓝色 skin-blue、绿色 skin-green、紫色 skin-purple、红色 skin-red、黄色 skin-yellow');
INSERT INTO `sys_config` VALUES (2, '用户管理-账号初始密码', 'sys.user.initPassword', '123456', 'Y', 'admin', '2026-08-26 09:13:26', '', NULL, '初始化密码 123456');
INSERT INTO `sys_config` VALUES (3, '主框架页-侧边栏主题', 'sys.index.sideTheme', 'theme-dark', 'Y', 'admin', '2026-08-26 09:13:26', '', NULL, '深色主题theme-dark，浅色主题theme-light');
INSERT INTO `sys_config` VALUES (4, '账号自助-是否开启用户注册功能', 'sys.account.registerUser', 'false', 'Y', 'admin', '2026-08-26 09:13:26', '', NULL, '是否开启注册用户功能（true开启，false关闭）');
INSERT INTO `sys_config` VALUES (5, '用户登录-黑名单列表', 'sys.login.blackIPList', '', 'Y', 'admin', '2026-08-26 09:13:26', '', NULL, '设置登录IP黑名单限制，多个匹配项以;分隔，支持匹配（*通配、网段）');
INSERT INTO `sys_config` VALUES (6, '用户管理-初始密码修改策略', 'sys.account.initPasswordModify', '1', 'Y', 'admin', '2026-08-26 09:13:26', '', NULL, '0：初始密码修改策略关闭，没有任何提示，1：提醒用户，如果未修改初始密码，则在登录时就会提醒修改密码对话框');
INSERT INTO `sys_config` VALUES (7, '用户管理-账号密码更新周期', 'sys.account.passwordValidateDays', '0', 'Y', 'admin', '2026-08-26 09:13:26', '', NULL, '密码更新周期（填写数字，数据初始化值为0不限制，若修改必须为大于0小于365的正整数），如果超过这个周期登录系统时，则在登录时就会提醒修改密码对话框');

-- ----------------------------
-- Table structure for sys_dept
-- ----------------------------
DROP TABLE IF EXISTS `sys_dept`;
CREATE TABLE `sys_dept`  (
  `dept_id` bigint NOT NULL AUTO_INCREMENT COMMENT '部门id',
  `parent_id` bigint NULL DEFAULT 0 COMMENT '父部门id',
  `ancestors` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '祖级列表',
  `dept_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '部门名称',
  `order_num` int NULL DEFAULT 0 COMMENT '显示顺序',
  `leader` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '负责人',
  `phone` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '联系电话',
  `email` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '邮箱',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '部门状态（0正常 1停用）',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`dept_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 200 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '部门表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_dept
-- ----------------------------
INSERT INTO `sys_dept` VALUES (100, 0, '0', '若依科技', 0, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2026-08-26 09:13:25', '', NULL);
INSERT INTO `sys_dept` VALUES (101, 100, '0,100', '深圳总公司', 1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2026-08-26 09:13:25', '', NULL);
INSERT INTO `sys_dept` VALUES (102, 100, '0,100', '长沙分公司', 2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2026-08-26 09:13:25', '', NULL);
INSERT INTO `sys_dept` VALUES (103, 101, '0,100,101', '研发部门', 1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2026-08-26 09:13:25', '', NULL);
INSERT INTO `sys_dept` VALUES (104, 101, '0,100,101', '市场部门', 2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2026-08-26 09:13:25', '', NULL);
INSERT INTO `sys_dept` VALUES (105, 101, '0,100,101', '测试部门', 3, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2026-08-26 09:13:25', '', NULL);
INSERT INTO `sys_dept` VALUES (106, 101, '0,100,101', '财务部门', 4, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2026-08-26 09:13:25', '', NULL);
INSERT INTO `sys_dept` VALUES (107, 101, '0,100,101', '运维部门', 5, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2026-08-26 09:13:25', '', NULL);
INSERT INTO `sys_dept` VALUES (108, 102, '0,100,102', '市场部门', 1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2026-08-26 09:13:25', '', NULL);
INSERT INTO `sys_dept` VALUES (109, 102, '0,100,102', '财务部门', 2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2026-08-26 09:13:25', '', NULL);

-- ----------------------------
-- Table structure for sys_dict_data
-- ----------------------------
DROP TABLE IF EXISTS `sys_dict_data`;
CREATE TABLE `sys_dict_data`  (
  `dict_code` bigint NOT NULL AUTO_INCREMENT COMMENT '字典编码',
  `dict_sort` int NULL DEFAULT 0 COMMENT '字典排序',
  `dict_label` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '字典标签',
  `dict_value` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '字典键值',
  `dict_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '字典类型',
  `css_class` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '样式属性（其他样式扩展）',
  `list_class` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '表格回显样式',
  `is_default` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'N' COMMENT '是否默认（Y是 N否）',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`dict_code`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 100 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '字典数据表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_dict_data
-- ----------------------------
INSERT INTO `sys_dict_data` VALUES (1, 1, '男', '0', 'sys_user_sex', '', '', 'Y', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '性别男');
INSERT INTO `sys_dict_data` VALUES (2, 2, '女', '1', 'sys_user_sex', '', '', 'N', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '性别女');
INSERT INTO `sys_dict_data` VALUES (3, 3, '未知', '2', 'sys_user_sex', '', '', 'N', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '性别未知');
INSERT INTO `sys_dict_data` VALUES (4, 1, '显示', '0', 'sys_show_hide', '', 'primary', 'Y', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '显示菜单');
INSERT INTO `sys_dict_data` VALUES (5, 2, '隐藏', '1', 'sys_show_hide', '', 'danger', 'N', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '隐藏菜单');
INSERT INTO `sys_dict_data` VALUES (6, 1, '正常', '0', 'sys_normal_disable', '', 'primary', 'Y', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '正常状态');
INSERT INTO `sys_dict_data` VALUES (7, 2, '停用', '1', 'sys_normal_disable', '', 'danger', 'N', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '停用状态');
INSERT INTO `sys_dict_data` VALUES (8, 1, '正常', '0', 'sys_job_status', '', 'primary', 'Y', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '正常状态');
INSERT INTO `sys_dict_data` VALUES (9, 2, '暂停', '1', 'sys_job_status', '', 'danger', 'N', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '停用状态');
INSERT INTO `sys_dict_data` VALUES (10, 1, '默认', 'DEFAULT', 'sys_job_group', '', '', 'Y', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '默认分组');
INSERT INTO `sys_dict_data` VALUES (11, 2, '系统', 'SYSTEM', 'sys_job_group', '', '', 'N', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '系统分组');
INSERT INTO `sys_dict_data` VALUES (12, 1, '是', 'Y', 'sys_yes_no', '', 'primary', 'Y', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '系统默认是');
INSERT INTO `sys_dict_data` VALUES (13, 2, '否', 'N', 'sys_yes_no', '', 'danger', 'N', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '系统默认否');
INSERT INTO `sys_dict_data` VALUES (14, 1, '通知', '1', 'sys_notice_type', '', 'warning', 'Y', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '通知');
INSERT INTO `sys_dict_data` VALUES (15, 2, '公告', '2', 'sys_notice_type', '', 'success', 'N', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '公告');
INSERT INTO `sys_dict_data` VALUES (16, 1, '正常', '0', 'sys_notice_status', '', 'primary', 'Y', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '正常状态');
INSERT INTO `sys_dict_data` VALUES (17, 2, '关闭', '1', 'sys_notice_status', '', 'danger', 'N', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '关闭状态');
INSERT INTO `sys_dict_data` VALUES (18, 99, '其他', '0', 'sys_oper_type', '', 'info', 'N', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '其他操作');
INSERT INTO `sys_dict_data` VALUES (19, 1, '新增', '1', 'sys_oper_type', '', 'info', 'N', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '新增操作');
INSERT INTO `sys_dict_data` VALUES (20, 2, '修改', '2', 'sys_oper_type', '', 'info', 'N', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '修改操作');
INSERT INTO `sys_dict_data` VALUES (21, 3, '删除', '3', 'sys_oper_type', '', 'danger', 'N', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '删除操作');
INSERT INTO `sys_dict_data` VALUES (22, 4, '授权', '4', 'sys_oper_type', '', 'primary', 'N', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '授权操作');
INSERT INTO `sys_dict_data` VALUES (23, 5, '导出', '5', 'sys_oper_type', '', 'warning', 'N', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '导出操作');
INSERT INTO `sys_dict_data` VALUES (24, 6, '导入', '6', 'sys_oper_type', '', 'warning', 'N', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '导入操作');
INSERT INTO `sys_dict_data` VALUES (25, 7, '强退', '7', 'sys_oper_type', '', 'danger', 'N', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '强退操作');
INSERT INTO `sys_dict_data` VALUES (26, 8, '生成代码', '8', 'sys_oper_type', '', 'warning', 'N', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '生成操作');
INSERT INTO `sys_dict_data` VALUES (27, 9, '清空数据', '9', 'sys_oper_type', '', 'danger', 'N', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '清空操作');
INSERT INTO `sys_dict_data` VALUES (28, 1, '成功', '0', 'sys_common_status', '', 'primary', 'N', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '正常状态');
INSERT INTO `sys_dict_data` VALUES (29, 2, '失败', '1', 'sys_common_status', '', 'danger', 'N', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '停用状态');

-- ----------------------------
-- Table structure for sys_dict_type
-- ----------------------------
DROP TABLE IF EXISTS `sys_dict_type`;
CREATE TABLE `sys_dict_type`  (
  `dict_id` bigint NOT NULL AUTO_INCREMENT COMMENT '字典主键',
  `dict_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '字典名称',
  `dict_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '字典类型',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`dict_id`) USING BTREE,
  UNIQUE INDEX `dict_type`(`dict_type` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 100 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '字典类型表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_dict_type
-- ----------------------------
INSERT INTO `sys_dict_type` VALUES (1, '用户性别', 'sys_user_sex', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '用户性别列表');
INSERT INTO `sys_dict_type` VALUES (2, '菜单状态', 'sys_show_hide', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '菜单状态列表');
INSERT INTO `sys_dict_type` VALUES (3, '系统开关', 'sys_normal_disable', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '系统开关列表');
INSERT INTO `sys_dict_type` VALUES (4, '任务状态', 'sys_job_status', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '任务状态列表');
INSERT INTO `sys_dict_type` VALUES (5, '任务分组', 'sys_job_group', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '任务分组列表');
INSERT INTO `sys_dict_type` VALUES (6, '系统是否', 'sys_yes_no', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '系统是否列表');
INSERT INTO `sys_dict_type` VALUES (7, '通知类型', 'sys_notice_type', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '通知类型列表');
INSERT INTO `sys_dict_type` VALUES (8, '通知状态', 'sys_notice_status', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '通知状态列表');
INSERT INTO `sys_dict_type` VALUES (9, '操作类型', 'sys_oper_type', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '操作类型列表');
INSERT INTO `sys_dict_type` VALUES (10, '系统状态', 'sys_common_status', '0', 'admin', '2026-08-26 09:13:26', '', NULL, '登录状态列表');

-- ----------------------------
-- Table structure for sys_job
-- ----------------------------
DROP TABLE IF EXISTS `sys_job`;
CREATE TABLE `sys_job`  (
  `job_id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `job_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '任务名称',
  `job_group` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'DEFAULT' COMMENT '任务组名',
  `invoke_target` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '调用目标字符串',
  `cron_expression` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'cron执行表达式',
  `misfire_policy` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '3' COMMENT '计划执行错误策略（1立即执行 2执行一次 3放弃执行）',
  `concurrent` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '1' COMMENT '是否并发执行（0允许 1禁止）',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '状态（0正常 1暂停）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '备注信息',
  PRIMARY KEY (`job_id`, `job_name`, `job_group`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 100 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '定时任务调度表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_job
-- ----------------------------
INSERT INTO `sys_job` VALUES (1, '系统默认（无参）', 'DEFAULT', 'ryTask.ryNoParams', '0/10 * * * * ?', '3', '1', '1', 'admin', '2026-08-26 09:13:26', '', NULL, '');
INSERT INTO `sys_job` VALUES (2, '系统默认（有参）', 'DEFAULT', 'ryTask.ryParams(\'ry\')', '0/15 * * * * ?', '3', '1', '1', 'admin', '2026-08-26 09:13:26', '', NULL, '');
INSERT INTO `sys_job` VALUES (3, '系统默认（多参）', 'DEFAULT', 'ryTask.ryMultipleParams(\'ry\', true, 2000L, 316.50D, 100)', '0/20 * * * * ?', '3', '1', '1', 'admin', '2026-08-26 09:13:26', '', NULL, '');

-- ----------------------------
-- Table structure for sys_job_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_job_log`;
CREATE TABLE `sys_job_log`  (
  `job_log_id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务日志ID',
  `job_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '任务名称',
  `job_group` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '任务组名',
  `invoke_target` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '调用目标字符串',
  `job_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '日志信息',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '执行状态（0正常 1失败）',
  `exception_info` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '异常信息',
  `start_time` datetime NULL DEFAULT NULL COMMENT '执行开始时间',
  `end_time` datetime NULL DEFAULT NULL COMMENT '执行结束时间',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`job_log_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '定时任务调度日志表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_job_log
-- ----------------------------

-- ----------------------------
-- Table structure for sys_logininfor
-- ----------------------------
DROP TABLE IF EXISTS `sys_logininfor`;
CREATE TABLE `sys_logininfor`  (
  `info_id` bigint NOT NULL AUTO_INCREMENT COMMENT '访问ID',
  `user_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '用户账号',
  `ipaddr` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '登录IP地址',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '登录状态（0成功 1失败）',
  `msg` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '提示信息',
  `access_time` datetime NULL DEFAULT NULL COMMENT '访问时间',
  PRIMARY KEY (`info_id`) USING BTREE,
  INDEX `idx_sys_logininfor_s`(`status` ASC) USING BTREE,
  INDEX `idx_sys_logininfor_lt`(`access_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 140 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统访问记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_logininfor
-- ----------------------------
INSERT INTO `sys_logininfor` VALUES (100, 'admin', '127.0.0.1', '0', '登录成功', '2026-08-28 02:19:48');
INSERT INTO `sys_logininfor` VALUES (101, 'admin', '127.0.0.1', '0', '退出成功', '2026-08-28 02:25:57');
INSERT INTO `sys_logininfor` VALUES (102, 'admin', '127.0.0.1', '0', '登录成功', '2026-08-28 02:26:01');
INSERT INTO `sys_logininfor` VALUES (103, 'admin', '10.77.137.155', '0', '登录成功', '2026-08-29 08:16:44');
INSERT INTO `sys_logininfor` VALUES (104, 'admin', '10.77.137.40', '0', '登录成功', '2026-08-29 08:21:53');
INSERT INTO `sys_logininfor` VALUES (105, 'admin', '127.0.0.1', '1', '密码输入错误1次', '2026-08-29 09:14:20');
INSERT INTO `sys_logininfor` VALUES (106, 'admin', '127.0.0.1', '1', '密码输入错误2次', '2026-08-29 09:14:38');
INSERT INTO `sys_logininfor` VALUES (107, 'admin', '127.0.0.1', '1', '密码输入错误3次', '2026-08-29 09:14:51');
INSERT INTO `sys_logininfor` VALUES (108, 'admin', '10.77.137.155', '0', '退出成功', '2026-08-29 09:15:12');
INSERT INTO `sys_logininfor` VALUES (109, 'admin', '127.0.0.1', '0', '登录成功', '2026-08-29 09:16:21');
INSERT INTO `sys_logininfor` VALUES (110, 'admin', '127.0.0.1', '0', '退出成功', '2026-08-30 02:58:34');
INSERT INTO `sys_logininfor` VALUES (111, 'admin', '127.0.0.1', '0', '登录成功', '2026-08-30 02:58:55');
INSERT INTO `sys_logininfor` VALUES (112, 'admin', '10.77.137.40', '0', '登录成功', '2026-08-30 03:10:46');
INSERT INTO `sys_logininfor` VALUES (113, 'admin', '10.77.137.40', '0', '登录成功', '2026-08-30 06:48:14');
INSERT INTO `sys_logininfor` VALUES (114, 'admin', '127.0.0.1', '0', '登录成功', '2026-08-30 13:12:31');
INSERT INTO `sys_logininfor` VALUES (115, 'admin', '10.77.137.40', '0', '登录成功', '2026-08-31 02:33:44');
INSERT INTO `sys_logininfor` VALUES (116, 'admin', '127.0.0.1', '0', '退出成功', '2026-08-31 03:34:06');
INSERT INTO `sys_logininfor` VALUES (117, 'admin', '127.0.0.1', '0', '登录成功', '2026-08-31 03:34:15');
INSERT INTO `sys_logininfor` VALUES (118, 'admin', '10.77.137.40', '0', '登录成功', '2026-08-31 06:47:07');
INSERT INTO `sys_logininfor` VALUES (119, 'admin', '10.77.137.40', '0', '登录成功', '2026-09-01 01:01:55');
INSERT INTO `sys_logininfor` VALUES (120, 'admin', '127.0.0.1', '0', '退出成功', '2026-09-02 01:23:05');
INSERT INTO `sys_logininfor` VALUES (121, 'admin', '127.0.0.1', '0', '登录成功', '2026-09-02 01:23:11');
INSERT INTO `sys_logininfor` VALUES (122, 'admin', '127.0.0.1', '0', '退出成功', '2026-09-03 10:04:20');
INSERT INTO `sys_logininfor` VALUES (123, 'admin', '127.0.0.1', '0', '登录成功', '2026-09-03 10:04:28');
INSERT INTO `sys_logininfor` VALUES (124, 'admin', '10.77.137.25', '0', '登录成功', '2026-09-03 10:18:46');
INSERT INTO `sys_logininfor` VALUES (125, 'admin', '10.77.137.25', '0', '登录成功', '2026-09-03 11:16:19');
INSERT INTO `sys_logininfor` VALUES (126, 'admin', '10.77.137.25', '0', '登录成功', '2026-09-03 11:24:38');
INSERT INTO `sys_logininfor` VALUES (127, 'admin', '10.77.137.25', '0', '登录成功', '2026-09-03 12:04:26');
INSERT INTO `sys_logininfor` VALUES (128, 'admin', '10.77.137.25', '0', '登录成功', '2026-09-03 12:07:49');
INSERT INTO `sys_logininfor` VALUES (129, 'admin', '10.77.137.25', '0', '登录成功', '2026-09-03 12:14:30');
INSERT INTO `sys_logininfor` VALUES (130, 'admin', '10.77.137.25', '0', '登录成功', '2026-09-03 12:19:44');
INSERT INTO `sys_logininfor` VALUES (131, 'admin', '10.77.137.25', '0', '登录成功', '2026-09-03 12:39:02');
INSERT INTO `sys_logininfor` VALUES (132, 'admin', '10.77.137.25', '0', '登录成功', '2026-09-03 12:40:39');
INSERT INTO `sys_logininfor` VALUES (133, 'admin', '10.77.137.25', '0', '登录成功', '2026-09-03 12:50:24');
INSERT INTO `sys_logininfor` VALUES (134, 'admin', '10.77.137.25', '0', '登录成功', '2026-09-03 12:58:01');
INSERT INTO `sys_logininfor` VALUES (135, 'admin', '10.77.137.25', '0', '登录成功', '2026-09-03 13:02:54');
INSERT INTO `sys_logininfor` VALUES (136, 'admin', '10.77.137.25', '0', '登录成功', '2026-09-03 13:04:27');
INSERT INTO `sys_logininfor` VALUES (137, 'admin', '10.77.137.25', '0', '登录成功', '2026-09-03 13:06:08');
INSERT INTO `sys_logininfor` VALUES (138, 'admin', '10.77.137.25', '0', '登录成功', '2026-09-03 13:09:15');
INSERT INTO `sys_logininfor` VALUES (139, 'admin', '10.77.137.25', '0', '登录成功', '2026-09-03 13:28:21');

-- ----------------------------
-- Table structure for sys_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu`  (
  `menu_id` bigint NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
  `menu_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '菜单名称',
  `parent_id` bigint NULL DEFAULT 0 COMMENT '父菜单ID',
  `order_num` int NULL DEFAULT 0 COMMENT '显示顺序',
  `path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '路由地址',
  `component` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '组件路径',
  `query` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '路由参数',
  `route_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '路由名称',
  `is_frame` int NULL DEFAULT 1 COMMENT '是否为外链（0是 1否）',
  `is_cache` int NULL DEFAULT 0 COMMENT '是否缓存（0缓存 1不缓存）',
  `menu_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '菜单类型（M目录 C菜单 F按钮）',
  `visible` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '菜单状态（0显示 1隐藏）',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '菜单状态（0正常 1停用）',
  `perms` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '权限标识',
  `icon` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '#' COMMENT '菜单图标',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '备注',
  PRIMARY KEY (`menu_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2000 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '菜单权限表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_menu
-- ----------------------------
INSERT INTO `sys_menu` VALUES (1, '系统管理', 0, 1, 'system', NULL, '', '', 1, 0, 'M', '0', '0', '', 'system', 'admin', '2026-08-26 09:13:25', '', NULL, '系统管理目录');
INSERT INTO `sys_menu` VALUES (2, '系统监控', 0, 2, 'monitor', NULL, '', '', 1, 0, 'M', '0', '0', '', 'monitor', 'admin', '2026-08-26 09:13:25', '', NULL, '系统监控目录');
INSERT INTO `sys_menu` VALUES (3, '系统工具', 0, 3, 'tool', NULL, '', '', 1, 0, 'M', '0', '0', '', 'tool', 'admin', '2026-08-26 09:13:25', '', NULL, '系统工具目录');
INSERT INTO `sys_menu` VALUES (4, '若依官网', 0, 4, 'http://ruoyi.vip', NULL, '', '', 0, 0, 'M', '1', '0', '', 'guide', 'admin', '2026-08-26 09:13:25', '', NULL, '若依官网地址');
INSERT INTO `sys_menu` VALUES (100, '用户管理', 1, 1, 'user', 'system/user/index', '', '', 1, 0, 'C', '0', '0', 'system:user:list', 'user', 'admin', '2026-08-26 09:13:25', '', NULL, '用户管理菜单');
INSERT INTO `sys_menu` VALUES (101, '角色管理', 1, 2, 'role', 'system/role/index', '', '', 1, 0, 'C', '0', '0', 'system:role:list', 'peoples', 'admin', '2026-08-26 09:13:25', '', NULL, '角色管理菜单');
INSERT INTO `sys_menu` VALUES (102, '菜单管理', 1, 3, 'menu', 'system/menu/index', '', '', 1, 0, 'C', '0', '0', 'system:menu:list', 'tree-table', 'admin', '2026-08-26 09:13:25', '', NULL, '菜单管理菜单');
INSERT INTO `sys_menu` VALUES (103, '部门管理', 1, 4, 'dept', 'system/dept/index', '', '', 1, 0, 'C', '0', '0', 'system:dept:list', 'tree', 'admin', '2026-08-26 09:13:25', '', NULL, '部门管理菜单');
INSERT INTO `sys_menu` VALUES (104, '岗位管理', 1, 5, 'post', 'system/post/index', '', '', 1, 0, 'C', '0', '0', 'system:post:list', 'post', 'admin', '2026-08-26 09:13:25', '', NULL, '岗位管理菜单');
INSERT INTO `sys_menu` VALUES (105, '字典管理', 1, 6, 'dict', 'system/dict/index', '', '', 1, 0, 'C', '0', '0', 'system:dict:list', 'dict', 'admin', '2026-08-26 09:13:25', '', NULL, '字典管理菜单');
INSERT INTO `sys_menu` VALUES (106, '参数设置', 1, 7, 'config', 'system/config/index', '', '', 1, 0, 'C', '0', '0', 'system:config:list', 'edit', 'admin', '2026-08-26 09:13:25', '', NULL, '参数设置菜单');
INSERT INTO `sys_menu` VALUES (107, '通知公告', 1, 8, 'notice', 'system/notice/index', '', '', 1, 0, 'C', '0', '0', 'system:notice:list', 'message', 'admin', '2026-08-26 09:13:25', '', NULL, '通知公告菜单');
INSERT INTO `sys_menu` VALUES (108, '日志管理', 1, 9, 'log', '', '', '', 1, 0, 'M', '0', '0', '', 'log', 'admin', '2026-08-26 09:13:25', '', NULL, '日志管理菜单');
INSERT INTO `sys_menu` VALUES (109, '在线用户', 2, 1, 'online', 'monitor/online/index', '', '', 1, 0, 'C', '0', '0', 'monitor:online:list', 'online', 'admin', '2026-08-26 09:13:25', '', NULL, '在线用户菜单');
INSERT INTO `sys_menu` VALUES (110, '定时任务', 2, 2, 'job', 'monitor/job/index', '', '', 1, 0, 'C', '0', '0', 'monitor:job:list', 'job', 'admin', '2026-08-26 09:13:25', '', NULL, '定时任务菜单');
INSERT INTO `sys_menu` VALUES (111, 'Sentinel控制台', 2, 3, 'http://localhost:8718', '', '', '', 0, 0, 'C', '0', '0', 'monitor:sentinel:list', 'sentinel', 'admin', '2026-08-26 09:13:25', '', NULL, '流量控制菜单');
INSERT INTO `sys_menu` VALUES (112, 'Nacos控制台', 2, 4, 'http://localhost:8848/nacos', '', '', '', 0, 0, 'C', '0', '0', 'monitor:nacos:list', 'nacos', 'admin', '2026-08-26 09:13:25', '', NULL, '服务治理菜单');
INSERT INTO `sys_menu` VALUES (113, 'Admin控制台', 2, 5, 'http://localhost:9100/login', '', '', '', 0, 0, 'C', '0', '0', 'monitor:server:list', 'server', 'admin', '2026-08-26 09:13:25', '', NULL, '服务监控菜单');
INSERT INTO `sys_menu` VALUES (114, '表单构建', 3, 1, 'build', 'tool/build/index', '', '', 1, 0, 'C', '0', '0', 'tool:build:list', 'build', 'admin', '2026-08-26 09:13:25', '', NULL, '表单构建菜单');
INSERT INTO `sys_menu` VALUES (115, '代码生成', 3, 2, 'gen', 'tool/gen/index', '', '', 1, 0, 'C', '0', '0', 'tool:gen:list', 'code', 'admin', '2026-08-26 09:13:25', '', NULL, '代码生成菜单');
INSERT INTO `sys_menu` VALUES (116, '系统接口', 3, 3, 'http://localhost:8080/swagger-ui/index.html', '', '', '', 0, 0, 'C', '0', '0', 'tool:swagger:list', 'swagger', 'admin', '2026-08-26 09:13:25', '', NULL, '系统接口菜单');
INSERT INTO `sys_menu` VALUES (500, '操作日志', 108, 1, 'operlog', 'system/operlog/index', '', '', 1, 0, 'C', '0', '0', 'system:operlog:list', 'form', 'admin', '2026-08-26 09:13:25', '', NULL, '操作日志菜单');
INSERT INTO `sys_menu` VALUES (501, '登录日志', 108, 2, 'logininfor', 'system/logininfor/index', '', '', 1, 0, 'C', '0', '0', 'system:logininfor:list', 'logininfor', 'admin', '2026-08-26 09:13:25', '', NULL, '登录日志菜单');
INSERT INTO `sys_menu` VALUES (1000, '用户查询', 100, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:query', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1001, '用户新增', 100, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:add', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1002, '用户修改', 100, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:edit', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1003, '用户删除', 100, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:remove', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1004, '用户导出', 100, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:export', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1005, '用户导入', 100, 6, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:import', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1006, '重置密码', 100, 7, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:resetPwd', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1007, '角色查询', 101, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:query', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1008, '角色新增', 101, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:add', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1009, '角色修改', 101, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:edit', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1010, '角色删除', 101, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:remove', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1011, '角色导出', 101, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:export', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1012, '菜单查询', 102, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:query', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1013, '菜单新增', 102, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:add', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1014, '菜单修改', 102, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:edit', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1015, '菜单删除', 102, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:remove', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1016, '部门查询', 103, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:query', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1017, '部门新增', 103, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:add', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1018, '部门修改', 103, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:edit', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1019, '部门删除', 103, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:remove', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1020, '岗位查询', 104, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:query', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1021, '岗位新增', 104, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:add', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1022, '岗位修改', 104, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:edit', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1023, '岗位删除', 104, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:remove', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1024, '岗位导出', 104, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:export', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1025, '字典查询', 105, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:query', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1026, '字典新增', 105, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:add', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1027, '字典修改', 105, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:edit', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1028, '字典删除', 105, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:remove', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1029, '字典导出', 105, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:export', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1030, '参数查询', 106, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:query', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1031, '参数新增', 106, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:add', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1032, '参数修改', 106, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:edit', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1033, '参数删除', 106, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:remove', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1034, '参数导出', 106, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:export', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1035, '公告查询', 107, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:query', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1036, '公告新增', 107, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:add', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1037, '公告修改', 107, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:edit', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1038, '公告删除', 107, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:remove', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1039, '操作查询', 500, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:operlog:query', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1040, '操作删除', 500, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:operlog:remove', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1041, '日志导出', 500, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:operlog:export', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1042, '登录查询', 501, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:logininfor:query', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1043, '登录删除', 501, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:logininfor:remove', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1044, '日志导出', 501, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:logininfor:export', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1045, '账户解锁', 501, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:logininfor:unlock', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1046, '在线查询', 109, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:query', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1047, '批量强退', 109, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:batchLogout', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1048, '单条强退', 109, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:forceLogout', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1049, '任务查询', 110, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:query', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1050, '任务新增', 110, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:add', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1051, '任务修改', 110, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:edit', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1052, '任务删除', 110, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:remove', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1053, '状态修改', 110, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:changeStatus', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1054, '任务导出', 110, 6, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:export', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1055, '生成查询', 115, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:query', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1056, '生成修改', 115, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:edit', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1057, '生成删除', 115, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:remove', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1058, '导入代码', 115, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:import', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1059, '预览代码', 115, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:preview', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1060, '生成代码', 115, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:code', '#', 'admin', '2026-08-26 09:13:25', '', NULL, '');

-- ----------------------------
-- Table structure for sys_notice
-- ----------------------------
DROP TABLE IF EXISTS `sys_notice`;
CREATE TABLE `sys_notice`  (
  `notice_id` int NOT NULL AUTO_INCREMENT COMMENT '公告ID',
  `notice_title` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '公告标题',
  `notice_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '公告类型（1通知 2公告）',
  `notice_content` longblob NULL COMMENT '公告内容',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '公告状态（0正常 1关闭）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`notice_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 10 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '通知公告表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_notice
-- ----------------------------
INSERT INTO `sys_notice` VALUES (1, '温馨提醒：2018-07-01 若依新版本发布啦', '2', 0xE696B0E78988E69CACE58685E5AEB9, '0', 'admin', '2026-08-26 09:13:26', '', NULL, '管理员');
INSERT INTO `sys_notice` VALUES (2, '维护通知：2018-07-01 若依系统凌晨维护', '1', 0xE7BBB4E68AA4E58685E5AEB9, '0', 'admin', '2026-08-26 09:13:26', '', NULL, '管理员');
INSERT INTO `sys_notice` VALUES (3, '若依开源框架介绍', '1', 0x3C703E3C7370616E207374796C653D22636F6C6F723A20726762283233302C20302C2030293B223EE9A1B9E79BAEE4BB8BE7BB8D3C2F7370616E3E3C2F703E3C703E3C666F6E7420636F6C6F723D2223333333333333223E52756F5969E5BC80E6BA90E9A1B9E79BAEE698AFE4B8BAE4BC81E4B89AE794A8E688B7E5AE9AE588B6E79A84E5908EE58FB0E8849AE6898BE69EB6E6A186E69EB6EFBC8CE4B8BAE4BC81E4B89AE68993E980A0E79A84E4B880E7AB99E5BC8FE8A7A3E586B3E696B9E6A188EFBC8CE9998DE4BD8EE4BC81E4B89AE5BC80E58F91E68890E69CACEFBC8CE68F90E58D87E5BC80E58F91E69588E78E87E38082E4B8BBE8A681E58C85E68BACE794A8E688B7E7AEA1E79086E38081E8A792E889B2E7AEA1E79086E38081E983A8E997A8E7AEA1E79086E38081E88F9CE58D95E7AEA1E79086E38081E58F82E695B0E7AEA1E79086E38081E5AD97E585B8E7AEA1E79086E380813C2F666F6E743E3C7370616E207374796C653D22636F6C6F723A207267622835312C2035312C203531293B223EE5B297E4BD8DE7AEA1E790863C2F7370616E3E3C7370616E207374796C653D22636F6C6F723A207267622835312C2035312C203531293B223EE38081E5AE9AE697B6E4BBBBE58AA13C2F7370616E3E3C7370616E207374796C653D22636F6C6F723A207267622835312C2035312C203531293B223EE380813C2F7370616E3E3C7370616E207374796C653D22636F6C6F723A207267622835312C2035312C203531293B223EE69C8DE58AA1E79B91E68EA7E38081E799BBE5BD95E697A5E5BF97E38081E6938DE4BD9CE697A5E5BF97E38081E4BBA3E7A081E7949FE68890E7AD89E58A9FE883BDE38082E585B6E4B8ADEFBC8CE8BF98E694AFE68C81E5A49AE695B0E68DAEE6BA90E38081E695B0E68DAEE69D83E99990E38081E59BBDE99985E58C96E380815265646973E7BC93E5AD98E38081446F636B6572E983A8E7BDB2E38081E6BB91E58AA8E9AA8CE8AF81E7A081E38081E7ACACE4B889E696B9E8AEA4E8AF81E799BBE5BD95E38081E58886E5B883E5BC8FE4BA8BE58AA1E380813C2F7370616E3E3C666F6E7420636F6C6F723D2223333333333333223EE58886E5B883E5BC8FE69687E4BBB6E5AD98E582A83C2F666F6E743E3C7370616E207374796C653D22636F6C6F723A207267622835312C2035312C203531293B223EE38081E58886E5BA93E58886E8A1A8E5A484E79086E7AD89E68A80E69CAFE789B9E782B9E380823C2F7370616E3E3C2F703E3C703E3C696D67207372633D2268747470733A2F2F666F727564612E67697465652E636F6D2F696D616765732F313737333933313834383334323433393033322F61346432323331335F313831353039352E706E6722207374796C653D2277696474683A20363470783B223E3C62723E3C2F703E3C703E3C7370616E207374796C653D22636F6C6F723A20726762283233302C20302C2030293B223EE5AE98E7BD91E58F8AE6BC94E7A4BA3C2F7370616E3E3C2F703E3C703E3C7370616E207374796C653D22636F6C6F723A207267622835312C2035312C203531293B223EE88BA5E4BE9DE5AE98E7BD91E59CB0E59D80EFBC9A266E6273703B3C2F7370616E3E3C6120687265663D22687474703A2F2F72756F79692E76697022207461726765743D225F626C616E6B223E687474703A2F2F72756F79692E7669703C2F613E3C6120687265663D22687474703A2F2F72756F79692E76697022207461726765743D225F626C616E6B223E3C2F613E3C2F703E3C703E3C7370616E207374796C653D22636F6C6F723A207267622835312C2035312C203531293B223EE88BA5E4BE9DE69687E6A1A3E59CB0E59D80EFBC9A266E6273703B3C2F7370616E3E3C6120687265663D22687474703A2F2F646F632E72756F79692E76697022207461726765743D225F626C616E6B223E687474703A2F2F646F632E72756F79692E7669703C2F613E3C62723E3C2F703E3C703E3C7370616E207374796C653D22636F6C6F723A207267622835312C2035312C203531293B223EE6BC94E7A4BAE59CB0E59D80E38090E4B88DE58886E7A6BBE78988E38091EFBC9A266E6273703B3C2F7370616E3E3C6120687265663D22687474703A2F2F64656D6F2E72756F79692E76697022207461726765743D225F626C616E6B223E687474703A2F2F64656D6F2E72756F79692E7669703C2F613E3C2F703E3C703E3C7370616E207374796C653D22636F6C6F723A207267622835312C2035312C203531293B223EE6BC94E7A4BAE59CB0E59D80E38090E58886E7A6BBE78988E69CACE38091EFBC9A266E6273703B3C2F7370616E3E3C6120687265663D22687474703A2F2F7675652E72756F79692E76697022207461726765743D225F626C616E6B223E687474703A2F2F7675652E72756F79692E7669703C2F613E3C2F703E3C703E3C7370616E207374796C653D22636F6C6F723A207267622835312C2035312C203531293B223EE6BC94E7A4BAE59CB0E59D80E38090E5BEAEE69C8DE58AA1E78988E38091EFBC9A266E6273703B3C2F7370616E3E3C6120687265663D22687474703A2F2F636C6F75642E72756F79692E76697022207461726765743D225F626C616E6B223E687474703A2F2F636C6F75642E72756F79692E7669703C2F613E3C2F703E3C703E3C7370616E207374796C653D22636F6C6F723A207267622835312C2035312C203531293B223EE6BC94E7A4BAE59CB0E59D80E38090E7A7BBE58AA8E7ABAFE78988E38091EFBC9A266E6273703B3C2F7370616E3E3C6120687265663D22687474703A2F2F68352E72756F79692E76697022207461726765743D225F626C616E6B223E687474703A2F2F68352E72756F79692E7669703C2F613E3C2F703E3C703E3C6272207374796C653D22636F6C6F723A207267622834382C2034392C203531293B20666F6E742D66616D696C793A202671756F743B48656C766574696361204E6575652671756F743B2C2048656C7665746963612C20417269616C2C2073616E732D73657269663B20666F6E742D73697A653A20313270783B223E3C2F703E, '0', 'admin', '2026-08-26 09:13:26', '', NULL, '管理员');

-- ----------------------------
-- Table structure for sys_notice_read
-- ----------------------------
DROP TABLE IF EXISTS `sys_notice_read`;
CREATE TABLE `sys_notice_read`  (
  `read_id` bigint NOT NULL AUTO_INCREMENT COMMENT '已读主键',
  `notice_id` int NOT NULL COMMENT '公告id',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `read_time` datetime NOT NULL COMMENT '阅读时间',
  PRIMARY KEY (`read_id`) USING BTREE,
  UNIQUE INDEX `uk_user_notice`(`user_id` ASC, `notice_id` ASC) USING BTREE COMMENT '同一用户同一公告只记录一次'
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '公告已读记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_notice_read
-- ----------------------------
INSERT INTO `sys_notice_read` VALUES (1, 3, 1, '2026-08-29 11:05:54');
INSERT INTO `sys_notice_read` VALUES (2, 2, 1, '2026-08-29 11:05:54');
INSERT INTO `sys_notice_read` VALUES (3, 1, 1, '2026-08-29 11:05:54');

-- ----------------------------
-- Table structure for sys_oper_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_oper_log`;
CREATE TABLE `sys_oper_log`  (
  `oper_id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志主键',
  `title` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '模块标题',
  `business_type` int NULL DEFAULT 0 COMMENT '业务类型（0其它 1新增 2修改 3删除）',
  `method` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '方法名称',
  `request_method` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '请求方式',
  `operator_type` int NULL DEFAULT 0 COMMENT '操作类别（0其它 1后台用户 2手机端用户）',
  `oper_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '操作人员',
  `dept_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '部门名称',
  `oper_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '请求URL',
  `oper_ip` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '主机地址',
  `oper_location` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '操作地点',
  `oper_param` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '请求参数',
  `json_result` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '返回参数',
  `status` int NULL DEFAULT 0 COMMENT '操作状态（0正常 1异常）',
  `error_msg` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '错误消息',
  `oper_time` datetime NULL DEFAULT NULL COMMENT '操作时间',
  `cost_time` bigint NULL DEFAULT 0 COMMENT '消耗时间',
  PRIMARY KEY (`oper_id`) USING BTREE,
  INDEX `idx_sys_oper_log_bt`(`business_type` ASC) USING BTREE,
  INDEX `idx_sys_oper_log_s`(`status` ASC) USING BTREE,
  INDEX `idx_sys_oper_log_ot`(`oper_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 100 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '操作日志记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_oper_log
-- ----------------------------

-- ----------------------------
-- Table structure for sys_post
-- ----------------------------
DROP TABLE IF EXISTS `sys_post`;
CREATE TABLE `sys_post`  (
  `post_id` bigint NOT NULL AUTO_INCREMENT COMMENT '岗位ID',
  `post_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '岗位编码',
  `post_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '岗位名称',
  `post_sort` int NOT NULL COMMENT '显示顺序',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`post_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '岗位信息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_post
-- ----------------------------
INSERT INTO `sys_post` VALUES (1, 'ceo', '董事长', 1, '0', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_post` VALUES (2, 'se', '项目经理', 2, '0', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_post` VALUES (3, 'hr', '人力资源', 3, '0', 'admin', '2026-08-26 09:13:25', '', NULL, '');
INSERT INTO `sys_post` VALUES (4, 'user', '普通员工', 4, '0', 'admin', '2026-08-26 09:13:25', '', NULL, '');

-- ----------------------------
-- Table structure for sys_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role`  (
  `role_id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色名称',
  `role_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色权限字符串',
  `role_sort` int NOT NULL COMMENT '显示顺序',
  `data_scope` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '1' COMMENT '数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）',
  `menu_check_strictly` tinyint(1) NULL DEFAULT 1 COMMENT '菜单树选择项是否关联显示',
  `dept_check_strictly` tinyint(1) NULL DEFAULT 1 COMMENT '部门树选择项是否关联显示',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色状态（0正常 1停用）',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`role_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 100 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色信息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_role
-- ----------------------------
INSERT INTO `sys_role` VALUES (1, '超级管理员', 'admin', 1, '1', 1, 1, '0', '0', 'admin', '2026-08-26 09:13:25', '', NULL, '超级管理员');
INSERT INTO `sys_role` VALUES (2, '普通角色', 'common', 2, '2', 1, 1, '0', '0', 'admin', '2026-08-26 09:13:25', '', NULL, '普通角色');

-- ----------------------------
-- Table structure for sys_role_dept
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_dept`;
CREATE TABLE `sys_role_dept`  (
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `dept_id` bigint NOT NULL COMMENT '部门ID',
  PRIMARY KEY (`role_id`, `dept_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色和部门关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_role_dept
-- ----------------------------
INSERT INTO `sys_role_dept` VALUES (2, 100);
INSERT INTO `sys_role_dept` VALUES (2, 101);
INSERT INTO `sys_role_dept` VALUES (2, 105);

-- ----------------------------
-- Table structure for sys_role_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu`  (
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `menu_id` bigint NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (`role_id`, `menu_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色和菜单关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_role_menu
-- ----------------------------
INSERT INTO `sys_role_menu` VALUES (2, 1);
INSERT INTO `sys_role_menu` VALUES (2, 2);
INSERT INTO `sys_role_menu` VALUES (2, 3);
INSERT INTO `sys_role_menu` VALUES (2, 4);
INSERT INTO `sys_role_menu` VALUES (2, 100);
INSERT INTO `sys_role_menu` VALUES (2, 101);
INSERT INTO `sys_role_menu` VALUES (2, 102);
INSERT INTO `sys_role_menu` VALUES (2, 103);
INSERT INTO `sys_role_menu` VALUES (2, 104);
INSERT INTO `sys_role_menu` VALUES (2, 105);
INSERT INTO `sys_role_menu` VALUES (2, 106);
INSERT INTO `sys_role_menu` VALUES (2, 107);
INSERT INTO `sys_role_menu` VALUES (2, 108);
INSERT INTO `sys_role_menu` VALUES (2, 109);
INSERT INTO `sys_role_menu` VALUES (2, 110);
INSERT INTO `sys_role_menu` VALUES (2, 111);
INSERT INTO `sys_role_menu` VALUES (2, 112);
INSERT INTO `sys_role_menu` VALUES (2, 113);
INSERT INTO `sys_role_menu` VALUES (2, 114);
INSERT INTO `sys_role_menu` VALUES (2, 115);
INSERT INTO `sys_role_menu` VALUES (2, 116);
INSERT INTO `sys_role_menu` VALUES (2, 500);
INSERT INTO `sys_role_menu` VALUES (2, 501);
INSERT INTO `sys_role_menu` VALUES (2, 1000);
INSERT INTO `sys_role_menu` VALUES (2, 1001);
INSERT INTO `sys_role_menu` VALUES (2, 1002);
INSERT INTO `sys_role_menu` VALUES (2, 1003);
INSERT INTO `sys_role_menu` VALUES (2, 1004);
INSERT INTO `sys_role_menu` VALUES (2, 1005);
INSERT INTO `sys_role_menu` VALUES (2, 1006);
INSERT INTO `sys_role_menu` VALUES (2, 1007);
INSERT INTO `sys_role_menu` VALUES (2, 1008);
INSERT INTO `sys_role_menu` VALUES (2, 1009);
INSERT INTO `sys_role_menu` VALUES (2, 1010);
INSERT INTO `sys_role_menu` VALUES (2, 1011);
INSERT INTO `sys_role_menu` VALUES (2, 1012);
INSERT INTO `sys_role_menu` VALUES (2, 1013);
INSERT INTO `sys_role_menu` VALUES (2, 1014);
INSERT INTO `sys_role_menu` VALUES (2, 1015);
INSERT INTO `sys_role_menu` VALUES (2, 1016);
INSERT INTO `sys_role_menu` VALUES (2, 1017);
INSERT INTO `sys_role_menu` VALUES (2, 1018);
INSERT INTO `sys_role_menu` VALUES (2, 1019);
INSERT INTO `sys_role_menu` VALUES (2, 1020);
INSERT INTO `sys_role_menu` VALUES (2, 1021);
INSERT INTO `sys_role_menu` VALUES (2, 1022);
INSERT INTO `sys_role_menu` VALUES (2, 1023);
INSERT INTO `sys_role_menu` VALUES (2, 1024);
INSERT INTO `sys_role_menu` VALUES (2, 1025);
INSERT INTO `sys_role_menu` VALUES (2, 1026);
INSERT INTO `sys_role_menu` VALUES (2, 1027);
INSERT INTO `sys_role_menu` VALUES (2, 1028);
INSERT INTO `sys_role_menu` VALUES (2, 1029);
INSERT INTO `sys_role_menu` VALUES (2, 1030);
INSERT INTO `sys_role_menu` VALUES (2, 1031);
INSERT INTO `sys_role_menu` VALUES (2, 1032);
INSERT INTO `sys_role_menu` VALUES (2, 1033);
INSERT INTO `sys_role_menu` VALUES (2, 1034);
INSERT INTO `sys_role_menu` VALUES (2, 1035);
INSERT INTO `sys_role_menu` VALUES (2, 1036);
INSERT INTO `sys_role_menu` VALUES (2, 1037);
INSERT INTO `sys_role_menu` VALUES (2, 1038);
INSERT INTO `sys_role_menu` VALUES (2, 1039);
INSERT INTO `sys_role_menu` VALUES (2, 1040);
INSERT INTO `sys_role_menu` VALUES (2, 1041);
INSERT INTO `sys_role_menu` VALUES (2, 1042);
INSERT INTO `sys_role_menu` VALUES (2, 1043);
INSERT INTO `sys_role_menu` VALUES (2, 1044);
INSERT INTO `sys_role_menu` VALUES (2, 1045);
INSERT INTO `sys_role_menu` VALUES (2, 1046);
INSERT INTO `sys_role_menu` VALUES (2, 1047);
INSERT INTO `sys_role_menu` VALUES (2, 1048);
INSERT INTO `sys_role_menu` VALUES (2, 1049);
INSERT INTO `sys_role_menu` VALUES (2, 1050);
INSERT INTO `sys_role_menu` VALUES (2, 1051);
INSERT INTO `sys_role_menu` VALUES (2, 1052);
INSERT INTO `sys_role_menu` VALUES (2, 1053);
INSERT INTO `sys_role_menu` VALUES (2, 1054);
INSERT INTO `sys_role_menu` VALUES (2, 1055);
INSERT INTO `sys_role_menu` VALUES (2, 1056);
INSERT INTO `sys_role_menu` VALUES (2, 1057);
INSERT INTO `sys_role_menu` VALUES (2, 1058);
INSERT INTO `sys_role_menu` VALUES (2, 1059);
INSERT INTO `sys_role_menu` VALUES (2, 1060);

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`  (
  `user_id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `dept_id` bigint NULL DEFAULT NULL COMMENT '部门ID',
  `user_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户账号',
  `nick_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户昵称',
  `user_type` varchar(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '00' COMMENT '用户类型（00系统用户）',
  `email` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '用户邮箱',
  `phonenumber` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '手机号码',
  `sex` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '用户性别（0男 1女 2未知）',
  `avatar` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '头像地址',
  `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '密码',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '账号状态（0正常 1停用）',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `login_ip` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '最后登录IP',
  `login_date` datetime NULL DEFAULT NULL COMMENT '最后登录时间',
  `pwd_update_date` datetime NULL DEFAULT NULL COMMENT '密码最后更新时间',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`user_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 100 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户信息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_user
-- ----------------------------
INSERT INTO `sys_user` VALUES (1, 103, 'admin', '云眸智维', '00', 'ry@163.com', '15888888888', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '10.77.137.25', '2026-09-03 21:28:22', '2026-08-26 09:13:25', 'admin', '2026-08-26 09:13:25', '', NULL, '管理员');
INSERT INTO `sys_user` VALUES (2, 105, 'ry', '张三', '00', 'ry@qq.com', '15666666666', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', '2026-08-26 09:13:25', '2026-08-26 09:13:25', 'admin', '2026-08-26 09:13:25', '', NULL, '测试员');

-- ----------------------------
-- Table structure for sys_user_post
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_post`;
CREATE TABLE `sys_user_post`  (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `post_id` bigint NOT NULL COMMENT '岗位ID',
  PRIMARY KEY (`user_id`, `post_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户与岗位关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_user_post
-- ----------------------------
INSERT INTO `sys_user_post` VALUES (1, 1);
INSERT INTO `sys_user_post` VALUES (2, 2);

-- ----------------------------
-- Table structure for sys_user_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role`  (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`user_id`, `role_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户和角色关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_user_role
-- ----------------------------
INSERT INTO `sys_user_role` VALUES (1, 1);
INSERT INTO `sys_user_role` VALUES (2, 2);

-- ----------------------------
-- Table structure for work_order
-- ----------------------------
DROP TABLE IF EXISTS `work_order`;
CREATE TABLE `work_order`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '工单编号 WO+yyyyMMddHHmmss+3位随机',
  `order_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '工单类型: 故障维修/预防维护',
  `equipment_id` int NOT NULL COMMENT '设备id',
  `equipment_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '设备名称',
  `sensor_id` int NULL DEFAULT NULL COMMENT '传感器id',
  `sensor_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '传感器名称',
  `alert_level` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '级别: WARNING/SEVERE',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '工单内容(自动生成)',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/COMPLETED/CANCELLED',
  `handler` bigint NULL DEFAULT NULL COMMENT '处理人用户ID(关联sys_user,生成时为设备负责人,未绑定为NULL待转派)',
  `handle_remark` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '处理结果说明',
  `cancel_reason` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '取消原因',
  `finish_time` datetime NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `related_id` bigint NULL DEFAULT NULL COMMENT '关联业务ID(order_type路由:故障维修→alert_event,预防维护→maintenance_plan)',
  `handler_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '处理人姓名',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_order_no`(`order_no` ASC) USING BTREE,
  INDEX `idx_dedup`(`equipment_id` ASC, `sensor_id` ASC, `order_type` ASC, `status` ASC) USING BTREE,
  INDEX `idx_related`(`related_id` ASC, `order_type` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 35 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '维保工单' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of work_order
-- ----------------------------
INSERT INTO `work_order` VALUES (27, 'WO20260902142219724', '故障维修', 1, '一号数控机床', 1, '温度传感器', 'CRITICAL', '【阈值告警】一号数控机床-温度传感器 当前值 86.67 超限，请检修', 'COMPLETED', 1, '1', NULL, '2026-09-02 14:23:02', '2026-09-02 14:22:20', '2026-09-02 14:23:02', 966, '若依');
INSERT INTO `work_order` VALUES (28, 'WO20260902144540714', '故障维修', 1, '一号数控机床', 1, '温度传感器', 'CRITICAL', '【阈值告警】一号数控机床-温度传感器 当前值 86.9 超限，请检修', 'COMPLETED', 1, '1', NULL, '2026-09-02 14:46:05', '2026-09-02 14:45:41', '2026-09-02 14:46:05', 967, '若依');
INSERT INTO `work_order` VALUES (29, 'WO20260902144620501', '故障维修', 1, '一号数控机床', 1, '温度传感器', 'CRITICAL', '【阈值告警】一号数控机床-温度传感器 当前值 84.48 超限，请检修', 'COMPLETED', 1, '1', NULL, '2026-09-02 14:46:38', '2026-09-02 14:46:21', '2026-09-02 14:46:38', 968, '若依');
INSERT INTO `work_order` VALUES (30, 'WO20260902144700969', '故障维修', 1, '一号数控机床', 1, '温度传感器', 'CRITICAL', '【阈值告警】一号数控机床-温度传感器 当前值 88.58 超限，请检修', 'COMPLETED', 1, '1', NULL, '2026-09-02 14:47:43', '2026-09-02 14:47:01', '2026-09-02 14:47:43', 969, '若依');
INSERT INTO `work_order` VALUES (31, 'WO20260902160032683', '故障维修', 1, '一号数控机床', 1, '温度传感器', 'CRITICAL', '【阈值告警】一号数控机床-温度传感器 当前值 85.0 超限，请检修', 'COMPLETED', 1, '11111', NULL, '2026-09-02 16:01:13', '2026-09-02 16:00:33', '2026-09-02 16:01:13', 970, '若依');
INSERT INTO `work_order` VALUES (32, 'WO20260902160114565', '故障维修', 1, '一号数控机床', 1, '温度传感器', 'CRITICAL', '【阈值告警】一号数控机床-温度传感器 当前值 89.59 超限，请检修', 'COMPLETED', 1, '1111', NULL, '2026-09-02 16:10:19', '2026-09-02 16:01:14', '2026-09-02 16:10:19', 971, '若依');
INSERT INTO `work_order` VALUES (33, 'WO20260902163953400', '故障维修', 1, '一号数控机床', 1, '温度传感器', 'CRITICAL', '【阈值告警】一号数控机床-温度传感器 当前值 86.11 超限，请检修', 'COMPLETED', 1, '1', NULL, '2026-09-02 16:41:08', '2026-09-02 16:39:54', '2026-09-02 16:41:08', 972, '若依');
INSERT INTO `work_order` VALUES (34, 'WO20260902165127434', '故障维修', 1, '一号数控机床', 1, '温度传感器', 'CRITICAL', '【阈值告警】一号数控机床-温度传感器 当前值 84.69 超限，请检修', 'PENDING', 1, NULL, NULL, NULL, '2026-09-02 16:51:27', '2026-09-02 16:51:27', 973, '若依');

-- ----------------------------
-- Table structure for work_order_action_log
-- ----------------------------
DROP TABLE IF EXISTS `work_order_action_log`;
CREATE TABLE `work_order_action_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL COMMENT '关联工单ID',
  `order_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '工单编号(冗余,免联表直查)',
  `action` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '动作: CREATE/ASSIGN/START/COMPLETE/CANCEL',
  `operator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '操作人(系统自动生成为 system)',
  `detail` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '流转详情(完整中文句子,含派单对象/转派方向/取消原因等)',
  `create_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_order_id`(`order_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 54 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '工单流转记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of work_order_action_log
-- ----------------------------
INSERT INTO `work_order_action_log` VALUES (1, 7, 'WO20260831114500266', 'CREATE', 'system', '维护计划 MP20260831114259892 触发自动生成，计划未配置负责人，待指派', '2026-08-31 11:45:00');
INSERT INTO `work_order_action_log` VALUES (2, 8, 'WO20260831114600305', 'CREATE', 'system', '维护计划 MP20260831114338739 触发自动生成，计划未配置负责人，待指派', '2026-08-31 11:46:00');
INSERT INTO `work_order_action_log` VALUES (3, 7, 'WO20260831114500266', 'ASSIGN', 'system', '指派负责人：admin', '2026-08-31 11:52:24');
INSERT INTO `work_order_action_log` VALUES (4, 7, 'WO20260831114500266', 'START', 'system', '接单，工单进入处理中', '2026-08-31 11:52:24');
INSERT INTO `work_order_action_log` VALUES (5, 7, 'WO20260831114500266', 'COMPLETE', 'system', '处理完成：E2E验证：导轨润滑保养已完成；解除告警 0 条；复位指令已下发,预测基线已重置', '2026-08-31 11:53:02');
INSERT INTO `work_order_action_log` VALUES (6, 8, 'WO20260831114600305', 'START', 'admin', '接单，工单进入处理中', '2026-08-31 14:21:37');
INSERT INTO `work_order_action_log` VALUES (7, 8, 'WO20260831114600305', 'COMPLETE', 'admin', '处理完成：1；解除告警 0 条；复位指令已下发,预测基线已重置', '2026-08-31 14:21:48');
INSERT INTO `work_order_action_log` VALUES (8, 6, 'WO20260831081856966', 'START', 'admin', '接单，工单进入处理中', '2026-08-31 14:23:27');
INSERT INTO `work_order_action_log` VALUES (9, 9, 'WO20260831162325061', 'CREATE', 'system', '阈值告警触发自动生成，处理人 若依', '2026-08-31 16:23:25');
INSERT INTO `work_order_action_log` VALUES (10, 10, 'WO20260831162545346', 'CREATE', 'system', '阈值告警触发自动生成，处理人 若依', '2026-08-31 16:25:45');
INSERT INTO `work_order_action_log` VALUES (11, 10, 'WO20260831162545346', 'COMPLETE', 'admin', '处理完成：1；解除告警 1 条；复位指令已下发,预测基线已重置', '2026-08-31 16:25:57');
INSERT INTO `work_order_action_log` VALUES (12, 11, 'WO20260831162558906', 'CREATE', 'system', '阈值告警触发自动生成，处理人 若依', '2026-08-31 16:25:58');
INSERT INTO `work_order_action_log` VALUES (13, 11, 'WO20260831162558906', 'COMPLETE', 'admin', '处理完成：1；解除告警 1 条；复位指令已下发,预测基线已重置', '2026-08-31 16:26:17');
INSERT INTO `work_order_action_log` VALUES (14, 12, 'WO20260831162617934', 'CREATE', 'system', '阈值告警触发自动生成，处理人 若依', '2026-08-31 16:26:18');
INSERT INTO `work_order_action_log` VALUES (15, 12, 'WO20260831162617934', 'COMPLETE', 'admin', '处理完成：1；解除告警 1 条；复位指令已下发,预测基线已重置', '2026-08-31 16:26:22');
INSERT INTO `work_order_action_log` VALUES (16, 13, 'WO20260831162622140', 'CREATE', 'system', '阈值告警触发自动生成，处理人 若依', '2026-08-31 16:26:22');
INSERT INTO `work_order_action_log` VALUES (17, 13, 'WO20260831162622140', 'COMPLETE', 'admin', '处理完成：1；解除告警 1 条；复位指令已下发,预测基线已重置', '2026-08-31 16:26:25');
INSERT INTO `work_order_action_log` VALUES (18, 14, 'WO20260831162625556', 'CREATE', 'system', '阈值告警触发自动生成，处理人 若依', '2026-08-31 16:26:25');
INSERT INTO `work_order_action_log` VALUES (19, 14, 'WO20260831162625556', 'COMPLETE', 'admin', '处理完成：1；解除告警 1 条；复位指令已下发,预测基线已重置', '2026-08-31 16:27:33');
INSERT INTO `work_order_action_log` VALUES (20, 15, 'WO20260831162745970', 'CREATE', 'system', '阈值告警触发自动生成，处理人 若依', '2026-08-31 16:27:45');
INSERT INTO `work_order_action_log` VALUES (21, 15, 'WO20260831162745970', 'COMPLETE', 'admin', '处理完成：1；解除告警 1 条；复位指令已下发,预测基线已重置', '2026-08-31 16:27:52');
INSERT INTO `work_order_action_log` VALUES (22, 16, 'WO20260831162805952', 'CREATE', 'system', '阈值告警触发自动生成，处理人 若依', '2026-08-31 16:28:06');
INSERT INTO `work_order_action_log` VALUES (23, 17, 'WO20260831162840550', 'CREATE', 'system', '阈值告警触发自动生成，处理人 若依', '2026-08-31 16:28:40');
INSERT INTO `work_order_action_log` VALUES (24, 17, 'WO20260831162840550', 'COMPLETE', 'admin', '处理完成：1；解除告警 1 条；复位指令已下发,预测基线已重置', '2026-08-31 16:28:52');
INSERT INTO `work_order_action_log` VALUES (25, 18, 'WO20260831163243339', 'CREATE', 'system', '阈值告警触发自动生成，处理人 若依', '2026-08-31 16:32:43');
INSERT INTO `work_order_action_log` VALUES (26, 19, 'WO20260831163355694', 'CREATE', 'system', '阈值告警触发自动生成，处理人 若依', '2026-08-31 16:33:55');
INSERT INTO `work_order_action_log` VALUES (27, 19, 'WO20260831163355694', 'COMPLETE', 'admin', '处理完成：1；解除告警 2 条；复位指令已下发,预测基线已重置', '2026-08-31 16:34:22');
INSERT INTO `work_order_action_log` VALUES (28, 20, 'WO20260831204914641', 'CREATE', 'system', '阈值告警触发自动生成，处理人 若依', '2026-08-31 20:49:15');
INSERT INTO `work_order_action_log` VALUES (29, 21, 'WO20260901090922066', 'CREATE', 'system', '阈值告警触发自动生成，处理人 若依', '2026-09-01 09:09:23');
INSERT INTO `work_order_action_log` VALUES (30, 21, 'WO20260901090922066', 'COMPLETE', 'admin', '处理完成：1；解除告警 1 条；复位指令已下发,预测基线已重置', '2026-09-01 09:09:43');
INSERT INTO `work_order_action_log` VALUES (31, 22, 'WO20260901091200003', 'CREATE', 'system', '阈值告警触发自动生成，处理人 若依', '2026-09-01 09:12:00');
INSERT INTO `work_order_action_log` VALUES (32, 22, 'WO20260901091200003', 'COMPLETE', 'admin', '处理完成：1；解除告警 1 条；复位指令已下发,预测基线已重置', '2026-09-01 09:12:14');
INSERT INTO `work_order_action_log` VALUES (33, 23, 'WO20260901151410450', 'CREATE', 'system', '阈值告警触发自动生成，处理人 若依', '2026-09-01 15:14:11');
INSERT INTO `work_order_action_log` VALUES (34, 23, 'WO20260901151410450', 'COMPLETE', 'admin', '处理完成：1；解除告警 1 条；复位指令已下发,预测基线已重置', '2026-09-01 15:15:04');
INSERT INTO `work_order_action_log` VALUES (35, 24, 'WO20260901151529375', 'CREATE', 'system', '阈值告警触发自动生成，处理人 若依', '2026-09-01 15:15:30');
INSERT INTO `work_order_action_log` VALUES (36, 24, 'WO20260901151529375', 'COMPLETE', 'admin', '处理完成：1；解除告警 1 条；复位指令已下发,预测基线已重置', '2026-09-02 09:23:28');
INSERT INTO `work_order_action_log` VALUES (37, 26, 'WO20260902100916076', 'CREATE', 'system', '阈值告警触发自动生成，处理人 若依', '2026-09-02 10:09:16');
INSERT INTO `work_order_action_log` VALUES (38, 25, 'WO20260902100916140', 'CREATE', 'system', '阈值告警触发自动生成，处理人 若依', '2026-09-02 10:09:16');
INSERT INTO `work_order_action_log` VALUES (39, 27, 'WO20260902142219724', 'CREATE', 'system', '阈值告警触发自动生成，处理人 若依', '2026-09-02 14:22:20');
INSERT INTO `work_order_action_log` VALUES (40, 27, 'WO20260902142219724', 'COMPLETE', 'admin', '处理完成：1；解除告警 1 条；复位指令已下发,预测基线已重置', '2026-09-02 14:23:03');
INSERT INTO `work_order_action_log` VALUES (41, 28, 'WO20260902144540714', 'CREATE', 'system', '阈值告警触发自动生成，处理人 若依', '2026-09-02 14:45:41');
INSERT INTO `work_order_action_log` VALUES (42, 28, 'WO20260902144540714', 'COMPLETE', 'admin', '处理完成：1；解除告警 1 条；复位指令已下发,预测基线已重置', '2026-09-02 14:46:06');
INSERT INTO `work_order_action_log` VALUES (43, 29, 'WO20260902144620501', 'CREATE', 'system', '阈值告警触发自动生成，处理人 若依', '2026-09-02 14:46:21');
INSERT INTO `work_order_action_log` VALUES (44, 29, 'WO20260902144620501', 'COMPLETE', 'admin', '处理完成：1；解除告警 1 条；复位指令已下发,预测基线已重置', '2026-09-02 14:46:38');
INSERT INTO `work_order_action_log` VALUES (45, 30, 'WO20260902144700969', 'CREATE', 'system', '阈值告警触发自动生成，处理人 若依', '2026-09-02 14:47:01');
INSERT INTO `work_order_action_log` VALUES (46, 30, 'WO20260902144700969', 'COMPLETE', 'admin', '处理完成：1；解除告警 1 条；复位指令已下发,预测基线已重置', '2026-09-02 14:47:43');
INSERT INTO `work_order_action_log` VALUES (47, 31, 'WO20260902160032683', 'CREATE', 'system', '阈值告警触发自动生成，处理人 若依', '2026-09-02 16:00:33');
INSERT INTO `work_order_action_log` VALUES (48, 31, 'WO20260902160032683', 'COMPLETE', 'admin', '处理完成：11111；解除告警 1 条；复位指令已下发,预测基线已重置', '2026-09-02 16:01:13');
INSERT INTO `work_order_action_log` VALUES (49, 32, 'WO20260902160114565', 'CREATE', 'system', '阈值告警触发自动生成，处理人 若依', '2026-09-02 16:01:14');
INSERT INTO `work_order_action_log` VALUES (50, 32, 'WO20260902160114565', 'COMPLETE', 'admin', '处理完成：1111；解除告警 1 条；复位指令已下发,预测基线已重置', '2026-09-02 16:10:19');
INSERT INTO `work_order_action_log` VALUES (51, 33, 'WO20260902163953400', 'CREATE', 'system', '阈值告警触发自动生成，处理人 若依', '2026-09-02 16:39:54');
INSERT INTO `work_order_action_log` VALUES (52, 33, 'WO20260902163953400', 'COMPLETE', 'admin', '处理完成：1；解除告警 1 条；复位指令已下发,预测基线已重置', '2026-09-02 16:41:08');
INSERT INTO `work_order_action_log` VALUES (53, 34, 'WO20260902165127434', 'CREATE', 'system', '阈值告警触发自动生成，处理人 若依', '2026-09-02 16:51:27');

-- ----------------------------
-- Table structure for workshop
-- ----------------------------
DROP TABLE IF EXISTS `workshop`;
CREATE TABLE `workshop`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `workshop_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '车间名称',
  `workshop_location` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '车间位置',
  `workshop_manager_id` int NULL DEFAULT NULL COMMENT '车间负责人用户ID',
  `workshop_manager` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '车间负责人',
  `workshop_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '状态(0-启用,1-停用)',
  `workshop_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime NULL DEFAULT NULL COMMENT '记录创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '记录修改时间',
  `create_user` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人',
  `update_user` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '修改人',
  `delete_flag` int NULL DEFAULT 0 COMMENT '删除状态(0-未删除,1-已删除)',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_workshop_status`(`workshop_status` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 18 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '车间表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of workshop
-- ----------------------------
INSERT INTO `workshop` VALUES (1, '一号车间', '一号厂房一层', NULL, '张三', '0', '一号车间温度较高，因此该车间内的设备的温度传感器可以调高10°', '2026-08-29 09:34:39', NULL, 'admin', NULL, 0);
INSERT INTO `workshop` VALUES (2, '二号车间', '一号厂房二层', NULL, '李四', '0', NULL, '2026-08-29 09:34:39', NULL, 'admin', NULL, 0);
INSERT INTO `workshop` VALUES (3, '三号车间', '二号厂房一层', NULL, '王五', '0', NULL, '2026-08-29 09:34:39', NULL, 'admin', NULL, 0);
INSERT INTO `workshop` VALUES (4, '验证车间(改名)', '测试位置改', NULL, '测试员改', '1', NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `workshop` VALUES (5, 'verify-no-code', 'loc-1', 2, 'RY-2', '0', NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `workshop` VALUES (6, '浏览器测试车间', '测试厂房A座', NULL, '', '0', '浏览器实测创建，用于删除验证', NULL, NULL, NULL, NULL, 1);
INSERT INTO `workshop` VALUES (7, 'AUTO-竞赛车间-200753838991', '竞赛实训基地A区', 1, '云眸智维', '0', 'AUTO-自动化测试数据', NULL, NULL, NULL, NULL, 1);
INSERT INTO `workshop` VALUES (8, 'AUTO-竞赛车间-200753838991', '竞赛实训基地B区', 1, '云眸智维', '0', 'AUTO-自动化测试数据', NULL, NULL, NULL, NULL, 1);
INSERT INTO `workshop` VALUES (9, 'AUTO-竞赛车间-201956923137', '竞赛实训基地B区', 1, '云眸智维', '0', 'AUTO-自动化测试数据', NULL, NULL, NULL, NULL, 1);
INSERT INTO `workshop` VALUES (10, 'AUTO-竞赛车间-203915401256', '竞赛实训基地B区', 1, '云眸智维', '0', 'AUTO-自动化测试数据', NULL, NULL, NULL, NULL, 1);
INSERT INTO `workshop` VALUES (11, 'AUTO-竞赛车间-204048050715', '竞赛实训基地B区', 1, '云眸智维', '0', 'AUTO-自动化测试数据', NULL, NULL, NULL, NULL, 1);
INSERT INTO `workshop` VALUES (12, 'AUTO-竞赛车间-205052053777', '竞赛实训基地B区', 1, '云眸智维', '0', 'AUTO-自动化测试数据', NULL, NULL, NULL, NULL, 1);
INSERT INTO `workshop` VALUES (13, 'AUTO-竞赛车间-205848100830', '竞赛实训基地B区', 1, '云眸智维', '0', 'AUTO-自动化测试数据', NULL, NULL, NULL, NULL, 1);
INSERT INTO `workshop` VALUES (14, 'AUTO-竞赛车间-210329232714', '竞赛实训基地B区', 1, '云眸智维', '0', 'AUTO-自动化测试数据', NULL, NULL, NULL, NULL, 1);
INSERT INTO `workshop` VALUES (15, 'AUTO-竞赛车间-210644004569', '竞赛实训基地B区', 1, '云眸智维', '0', 'AUTO-自动化测试数据', NULL, NULL, NULL, NULL, 1);
INSERT INTO `workshop` VALUES (16, 'AUTO-竞赛车间-210948563996', '竞赛实训基地B区', 1, '云眸智维', '0', 'AUTO-自动化测试数据', NULL, NULL, NULL, NULL, 1);
INSERT INTO `workshop` VALUES (17, 'AUTO-竞赛车间-212854967374', '竞赛实训基地B区', 1, '云眸智维', '0', 'AUTO-自动化测试数据', NULL, NULL, NULL, NULL, 1);

SET FOREIGN_KEY_CHECKS = 1;
