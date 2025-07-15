-- DaHe V2 演示数据脚本（UTF-8，可重复执行）
USE `dahe_v2`;

-- ----------------------------
-- 0. 作物与流程模板（补充小麦演示）
-- ----------------------------
INSERT INTO `crop` (`id`, `name`, `variety`, `deleted`)
VALUES
  (1002, '小麦', '济麦22', 0)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `variety` = VALUES(`variety`),
  `deleted` = 0;

INSERT INTO `farm_process_template` (`id`, `crop_id`, `template_name`, `is_default`, `deleted`)
VALUES
  (2002, 1002, '小麦标准流程', 1, 0)
ON DUPLICATE KEY UPDATE
  `template_name` = VALUES(`template_name`),
  `is_default` = VALUES(`is_default`),
  `deleted` = 0;

-- ----------------------------
-- 0.1 流程步骤字段兼容（防止老库缺列）
-- ----------------------------
SET @ddl_form_schema_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `farm_process_step` ADD COLUMN `form_schema` LONGTEXT DEFAULT NULL COMMENT ''步骤参数表单配置(JSON)''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_process_step' AND COLUMN_NAME = 'form_schema'
);
PREPARE stmt_form_schema_seed FROM @ddl_form_schema_seed;
EXECUTE stmt_form_schema_seed;
DEALLOCATE PREPARE stmt_form_schema_seed;

SET @ddl_form_config_id_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `farm_process_step` ADD COLUMN `form_config_id` BIGINT DEFAULT NULL COMMENT ''Step dynamic form config id''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_process_step' AND COLUMN_NAME = 'form_config_id'
);
PREPARE stmt_form_config_id_seed FROM @ddl_form_config_id_seed;
EXECUTE stmt_form_config_id_seed;
DEALLOCATE PREPARE stmt_form_config_id_seed;

SET @ddl_idx_step_form_config_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `farm_process_step` ADD INDEX `idx_step_form_config` (`form_config_id`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_process_step' AND INDEX_NAME = 'idx_step_form_config'
);
PREPARE stmt_idx_step_form_config_seed FROM @ddl_idx_step_form_config_seed;
EXECUTE stmt_idx_step_form_config_seed;
DEALLOCATE PREPARE stmt_idx_step_form_config_seed;

SET @ddl_extra_json_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `farm_record` ADD COLUMN `extra_json` LONGTEXT DEFAULT NULL COMMENT ''流程步骤动态参数(JSON)''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_record' AND COLUMN_NAME = 'extra_json'
);
PREPARE stmt_extra_json_seed FROM @ddl_extra_json_seed;
EXECUTE stmt_extra_json_seed;
DEALLOCATE PREPARE stmt_extra_json_seed;

SET @ddl_drop_record_work_type_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'SELECT 1',
    'ALTER TABLE `farm_record` DROP COLUMN `work_type`'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_record' AND COLUMN_NAME = 'work_type'
);
PREPARE stmt_drop_record_work_type_seed FROM @ddl_drop_record_work_type_seed;
EXECUTE stmt_drop_record_work_type_seed;
DEALLOCATE PREPARE stmt_drop_record_work_type_seed;

-- ----------------------------
-- 0.2 小麦流程步骤
-- ----------------------------
INSERT INTO `farm_process_step` (`id`, `template_id`, `step_name`, `sort_order`, `requirement_desc`, `form_schema`)
VALUES
  (3011, 2002, '整地', 1, '地块平整后进入下一步', '[{"key":"machineType","label":"机械类型","type":"text","required":true}]'),
  (3012, 2002, '播种', 2, '记录播种量与播深', '[{"key":"seedAmount","label":"播种量(kg/亩)","type":"number","required":true},{"key":"rowSpacing","label":"行距(cm)","type":"number","required":false}]'),
  (3013, 2002, '灌溉', 3, '记录灌溉时长与墒情', '[{"key":"duration","label":"灌溉时长(min)","type":"number","required":true}]'),
  (3014, 2002, '返青追肥', 4, '记录肥料类型和施用量', '[{"key":"amountPerMu","label":"数量(kg/亩)","type":"number","required":true},{"key":"operationDate","label":"作业日期","type":"date","required":false},{"key":"operationPoint","label":"作业位置","type":"location","required":false}]'),
  (3015, 2002, '收获', 5, '记录收获日期与估产', '[{"key":"yieldPerMu","label":"亩产(kg)","type":"number","required":true}]')
ON DUPLICATE KEY UPDATE
  `template_id` = VALUES(`template_id`),
  `step_name` = VALUES(`step_name`),
  `sort_order` = VALUES(`sort_order`),
  `requirement_desc` = VALUES(`requirement_desc`),
  `form_schema` = VALUES(`form_schema`);

UPDATE `farm_process_step` SET `growth_stage`='sowing' WHERE `id` IN (3003,3012);
UPDATE `farm_process_step` SET `growth_stage`='growing' WHERE `id` IN (3001,3002,3004,3011,3013,3014);
UPDATE `farm_process_step` SET `growth_stage`='harvesting' WHERE `id` IN (3005,3015);

-- ----------------------------
-- 1. 田块演示数据
-- ----------------------------
INSERT INTO `field`
(`id`, `name`, `area_mu`, `crop_type`, `status`, `location_lat`, `location_lng`, `location_desc`, `cover_image_url`, `remark`, `deleted`)
VALUES
  (41001, '东一号田', 32.50, '玉米', 'growing', 36.7023561, 117.1026943, '东区机井东侧', NULL, '演示数据-长势良好', 0),
  (41002, '东二号田', 24.00, '小麦', 'sowing', 36.7012042, 117.1061812, '东区道路北侧', NULL, '演示数据-已完成播种', 0),
  (41003, '南试验田', 12.80, '玉米', 'idle', 36.6958824, 117.1102945, '南区试验片', NULL, '演示数据-待排产', 0),
  (41004, '西高产田', 40.20, '玉米', 'harvesting', 36.7081900, 117.0912514, '西区高产示范片', NULL, '演示数据-收获中', 0),
  (41005, '北轮作田', 28.60, '小麦', 'fallow', 36.7142214, 117.1180315, '北区轮作片', NULL, '演示数据-休耕期', 0)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `area_mu` = VALUES(`area_mu`),
  `crop_type` = VALUES(`crop_type`),
  `status` = VALUES(`status`),
  `location_lat` = VALUES(`location_lat`),
  `location_lng` = VALUES(`location_lng`),
  `location_desc` = VALUES(`location_desc`),
  `cover_image_url` = VALUES(`cover_image_url`),
  `remark` = VALUES(`remark`),
  `deleted` = 0;

-- ----------------------------
-- 2. 农事记录演示数据
-- ----------------------------
INSERT INTO `farm_record`
(`id`, `field_id`, `step_id`, `work_date`, `operator_name`, `notes`, `extra_json`, `deleted`)
VALUES
  (51001, 41001, 3001, '2026-02-08 08:30:00', '张强', '旋耕2遍，土壤湿度适中', '{"machineType":"旋耕机","operationDepth":"22"}', 0),
  (51002, 41001, 3002, '2026-02-09 09:10:00', '李娜', '施复合肥 25kg/亩', '{"fertilizerName":"复合肥","amountPerMu":"25"}', 0),
  (51003, 41001, 3003, '2026-02-10 07:55:00', '王磊', '播深约4cm，行距55cm', '{"seedVariety":"先玉335","seedAmount":"3.2","rowSpacing":"55"}', 0),
  (51004, 41002, 3012, '2026-02-10 10:20:00', '赵敏', '小麦条播完成，覆土均匀', '{"seedAmount":"8.5","rowSpacing":"20"}', 0),
  (51005, 41002, 3013, '2026-02-11 15:40:00', '赵敏', '灌溉45分钟，沟渠通畅', '{"duration":"45"}', 0),
  (51006, 41004, 3005, '2026-02-12 13:15:00', '周凯', '机械收获首批，籽粒水分18%', '{"yieldPerMu":"610","moisture":"18"}', 0),
  (51007, 41003, NULL, '2026-02-12 16:20:00', '陈晨', '试验田预防性喷施', NULL, 0),
  (51008, 41005, NULL, '2026-02-13 09:00:00', '高飞', '轮作田块巡检，暂无异常', NULL, 0),
  (51009, 41001, 3004, '2026-02-13 11:25:00', '王磊', '追肥 12kg/亩', '{"amountPerMu":"12","operationDate":"2026-02-13","operationPoint":"东河镇田埂点A(36.7128,117.1021)"}', 0),
  (51010, 41001, NULL, '2026-02-13 14:10:00', '李娜', '病虫预防喷施，风力2级', NULL, 0),
  (51011, 41002, 3014, '2026-02-13 15:20:00', '赵敏', '返青追肥，复合肥10kg/亩', '{"amountPerMu":"10","operationDate":"2026-02-13","operationPoint":"东二号田中段"}', 0),
  (51012, 41004, NULL, '2026-02-13 17:05:00', '周凯', '收获后机具检查与地头清理', NULL, 0)
ON DUPLICATE KEY UPDATE
  `field_id` = VALUES(`field_id`),
  `step_id` = VALUES(`step_id`),
  `work_date` = VALUES(`work_date`),
  `operator_name` = VALUES(`operator_name`),
  `notes` = VALUES(`notes`),
  `extra_json` = VALUES(`extra_json`),
  `deleted` = 0;

-- ----------------------------
-- 3. 种子批次与检测演示数据
-- ----------------------------
SET @ddl_seed_batch_form_config_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `seed_batch` ADD COLUMN `form_config_id` BIGINT DEFAULT NULL COMMENT ''Dynamic form config id''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_batch' AND COLUMN_NAME = 'form_config_id'
);
PREPARE stmt_seed_batch_form_config_seed FROM @ddl_seed_batch_form_config_seed;
EXECUTE stmt_seed_batch_form_config_seed;
DEALLOCATE PREPARE stmt_seed_batch_form_config_seed;

SET @ddl_seed_batch_crop_type_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `seed_batch` ADD COLUMN `crop_type` VARCHAR(64) DEFAULT NULL COMMENT ''Crop type''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_batch' AND COLUMN_NAME = 'crop_type'
);
PREPARE stmt_seed_batch_crop_type_seed FROM @ddl_seed_batch_crop_type_seed;
EXECUTE stmt_seed_batch_crop_type_seed;
DEALLOCATE PREPARE stmt_seed_batch_crop_type_seed;

SET @ddl_idx_seed_batch_crop_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `seed_batch` ADD INDEX `idx_seed_batch_crop` (`crop_type`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_batch' AND INDEX_NAME = 'idx_seed_batch_crop'
);
PREPARE stmt_idx_seed_batch_crop_seed FROM @ddl_idx_seed_batch_crop_seed;
EXECUTE stmt_idx_seed_batch_crop_seed;
DEALLOCATE PREPARE stmt_idx_seed_batch_crop_seed;

SET @ddl_seed_batch_extra_json_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `seed_batch` ADD COLUMN `extra_json` LONGTEXT DEFAULT NULL COMMENT ''Dynamic fields value(JSON)''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_batch' AND COLUMN_NAME = 'extra_json'
);
PREPARE stmt_seed_batch_extra_json_seed FROM @ddl_seed_batch_extra_json_seed;
EXECUTE stmt_seed_batch_extra_json_seed;
DEALLOCATE PREPARE stmt_seed_batch_extra_json_seed;

SET @ddl_seed_batch_enabled_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `seed_batch` ADD COLUMN `enabled` TINYINT(1) DEFAULT ''1'' COMMENT ''1 enabled,0 disabled''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_batch' AND COLUMN_NAME = 'enabled'
);
PREPARE stmt_seed_batch_enabled_seed FROM @ddl_seed_batch_enabled_seed;
EXECUTE stmt_seed_batch_enabled_seed;
DEALLOCATE PREPARE stmt_seed_batch_enabled_seed;

SET @ddl_idx_seed_batch_enabled_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `seed_batch` ADD INDEX `idx_seed_batch_enabled` (`enabled`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_batch' AND INDEX_NAME = 'idx_seed_batch_enabled'
);
PREPARE stmt_idx_seed_batch_enabled_seed FROM @ddl_idx_seed_batch_enabled_seed;
EXECUTE stmt_idx_seed_batch_enabled_seed;
DEALLOCATE PREPARE stmt_idx_seed_batch_enabled_seed;

UPDATE `seed_batch` SET `enabled`=1 WHERE `enabled` IS NULL;

SET @ddl_seed_test_sample_count_before_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `seed_quality_test` ADD COLUMN `sample_count` INT DEFAULT NULL COMMENT ''Sample count for germination test''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_quality_test' AND COLUMN_NAME = 'sample_count'
);
PREPARE stmt_seed_test_sample_count_before_seed FROM @ddl_seed_test_sample_count_before_seed;
EXECUTE stmt_seed_test_sample_count_before_seed;
DEALLOCATE PREPARE stmt_seed_test_sample_count_before_seed;

SET @ddl_seed_test_germination_count_before_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `seed_quality_test` ADD COLUMN `germination_count` INT DEFAULT NULL COMMENT ''Germinated seed count''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_quality_test' AND COLUMN_NAME = 'germination_count'
);
PREPARE stmt_seed_test_germination_count_before_seed FROM @ddl_seed_test_germination_count_before_seed;
EXECUTE stmt_seed_test_germination_count_before_seed;
DEALLOCATE PREPARE stmt_seed_test_germination_count_before_seed;

SET @ddl_seed_test_form_config_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `seed_quality_test` ADD COLUMN `form_config_id` BIGINT DEFAULT NULL COMMENT ''Dynamic form config id''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_quality_test' AND COLUMN_NAME = 'form_config_id'
);
PREPARE stmt_seed_test_form_config_seed FROM @ddl_seed_test_form_config_seed;
EXECUTE stmt_seed_test_form_config_seed;
DEALLOCATE PREPARE stmt_seed_test_form_config_seed;

SET @ddl_seed_test_extra_json_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `seed_quality_test` ADD COLUMN `extra_json` LONGTEXT DEFAULT NULL COMMENT ''Dynamic fields value(JSON)''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_quality_test' AND COLUMN_NAME = 'extra_json'
);
PREPARE stmt_seed_test_extra_json_seed FROM @ddl_seed_test_extra_json_seed;
EXECUTE stmt_seed_test_extra_json_seed;
DEALLOCATE PREPARE stmt_seed_test_extra_json_seed;

INSERT INTO `seed_batch`
(`id`, `batch_code`, `crop_type`, `variety_name`, `production_date`, `remark`, `enabled`, `form_config_id`, `extra_json`, `deleted`)
VALUES
  (61001, 'DH-2026-CORN-001', '玉米', '先玉335', '2026-01-08', '演示数据-春播批次', 1, 92002, '{"supplier":"德州丰源种业","warehouseZone":"A"}', 0),
  (61002, 'DH-2026-CORN-002', '玉米', '郑单958', '2026-01-16', '演示数据-对照批次', 1, 92002, '{"supplier":"新乡绿禾种业","warehouseZone":"A"}', 0),
  (61003, 'DH-2026-WHEAT-001', '小麦', '济麦22', '2025-12-20', '演示数据-小麦主推', 1, 92002, '{"supplier":"泰安农科合作社","warehouseZone":"B"}', 0),
  (61004, 'DH-2026-WHEAT-002', '小麦', '良星99', '2025-12-28', '演示数据-小麦备用', 1, 92002, '{"supplier":"邯郸星禾农业","warehouseZone":"B"}', 0)
ON DUPLICATE KEY UPDATE
  `batch_code` = VALUES(`batch_code`),
  `crop_type` = VALUES(`crop_type`),
  `variety_name` = VALUES(`variety_name`),
  `production_date` = VALUES(`production_date`),
  `remark` = VALUES(`remark`),
  `enabled` = VALUES(`enabled`),
  `form_config_id` = VALUES(`form_config_id`),
  `extra_json` = VALUES(`extra_json`),
  `deleted` = 0;

INSERT INTO `seed_quality_test`
(`id`, `batch_id`, `test_date`, `sample_count`, `germination_count`, `germination_rate`, `purity`, `moisture`, `cleanliness`, `tester_name`, `remark`, `form_config_id`, `extra_json`)
VALUES
  (71001, 61001, '2026-01-20', 100, 96, 95.60, 99.10, 12.40, 98.60, '王蕾', '入库首检合格', 92003, '{"testMethod":"纸床法","environment":"实验室A"}'),
  (71002, 61001, '2026-02-05', 100, 95, 94.80, 99.00, 12.80, 98.20, '王蕾', '库存状态稳定', 92003, '{"testMethod":"砂床法","environment":"实验室A"}'),
  (71003, 61002, '2026-01-22', 100, 90, 90.20, 97.80, 13.60, 96.80, '李俊', '建议加强通风', 92003, '{"testMethod":"纸床法","environment":"实验室B"}'),
  (71004, 61003, '2026-01-18', 100, 96, 96.10, 99.30, 11.20, 99.10, '张宁', '指标优良', 92003, '{"testMethod":"纸床法","environment":"实验室C"}'),
  (71005, 61003, '2026-02-09', 100, 95, 95.40, 99.20, 11.50, 98.90, '张宁', '复检稳定', 92003, '{"testMethod":"砂床法","environment":"实验室C"}'),
  (71006, 61004, '2026-01-25', 100, 89, 88.90, 96.70, 14.10, 95.20, '刘峰', '水分偏高，暂缓出库', 92003, '{"testMethod":"纸床法","environment":"实验室D"}'),
  (71007, 61002, '2026-02-13', 100, 91, 91.40, 98.20, 12.90, 97.50, '李俊', '复检后指标回升，允许出库', 92003, '{"testMethod":"砂床法","environment":"实验室B"}'),
  (71008, 61004, '2026-02-13', 100, 90, 89.60, 97.10, 13.30, 95.80, '刘峰', '较上次改善，建议继续通风干燥', 92003, '{"testMethod":"纸床法","environment":"实验室D"}')
ON DUPLICATE KEY UPDATE
  `batch_id` = VALUES(`batch_id`),
  `test_date` = VALUES(`test_date`),
  `sample_count` = VALUES(`sample_count`),
  `germination_count` = VALUES(`germination_count`),
  `germination_rate` = VALUES(`germination_rate`),
  `purity` = VALUES(`purity`),
  `moisture` = VALUES(`moisture`),
  `cleanliness` = VALUES(`cleanliness`),
  `tester_name` = VALUES(`tester_name`),
  `remark` = VALUES(`remark`),
  `form_config_id` = VALUES(`form_config_id`),
  `extra_json` = VALUES(`extra_json`);

-- ----------------------------
-- 4. 田块扩展字段与种植计划兼容
-- ----------------------------
SET @ddl_field_crop_variety_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `field` ADD COLUMN `crop_variety` VARCHAR(64) DEFAULT NULL',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'field' AND COLUMN_NAME = 'crop_variety'
);
PREPARE stmt_field_crop_variety_seed FROM @ddl_field_crop_variety_seed;
EXECUTE stmt_field_crop_variety_seed;
DEALLOCATE PREPARE stmt_field_crop_variety_seed;

SET @ddl_field_crop_variety_groups_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `field` ADD COLUMN `crop_variety_groups_json` LONGTEXT DEFAULT NULL COMMENT ''Current crop-variety groups(JSON)''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'field' AND COLUMN_NAME = 'crop_variety_groups_json'
);
PREPARE stmt_field_crop_variety_groups_seed FROM @ddl_field_crop_variety_groups_seed;
EXECUTE stmt_field_crop_variety_groups_seed;
DEALLOCATE PREPARE stmt_field_crop_variety_groups_seed;

SET @ddl_field_township_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `field` ADD COLUMN `township` VARCHAR(64) DEFAULT NULL',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'field' AND COLUMN_NAME = 'township'
);
PREPARE stmt_field_township_seed FROM @ddl_field_township_seed;
EXECUTE stmt_field_township_seed;
DEALLOCATE PREPARE stmt_field_township_seed;

SET @ddl_field_enabled_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `field` ADD COLUMN `enabled` TINYINT(1) DEFAULT ''1'' COMMENT ''1 enabled,0 disabled''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'field' AND COLUMN_NAME = 'enabled'
);
PREPARE stmt_field_enabled_seed FROM @ddl_field_enabled_seed;
EXECUTE stmt_field_enabled_seed;
DEALLOCATE PREPARE stmt_field_enabled_seed;

SET @ddl_idx_field_enabled_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `field` ADD INDEX `idx_field_enabled` (`enabled`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'field' AND INDEX_NAME = 'idx_field_enabled'
);
PREPARE stmt_idx_field_enabled_seed FROM @ddl_idx_field_enabled_seed;
EXECUTE stmt_idx_field_enabled_seed;
DEALLOCATE PREPARE stmt_idx_field_enabled_seed;

UPDATE `field` SET `enabled`=1 WHERE `enabled` IS NULL;

SET @ddl_cycle_table_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'CREATE TABLE `field_crop_cycle` ( `id` BIGINT NOT NULL, `field_id` BIGINT NOT NULL, `cycle_name` VARCHAR(64) NOT NULL, `crops_json` LONGTEXT DEFAULT NULL, `template_ids_json` LONGTEXT DEFAULT NULL, `plan_mode` VARCHAR(32) DEFAULT ''single'', `start_date` DATE DEFAULT NULL, `end_date` DATE DEFAULT NULL, `status` VARCHAR(32) DEFAULT ''active'', `is_current` TINYINT(1) DEFAULT ''0'', `deleted` TINYINT(1) DEFAULT ''0'', `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP, `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, PRIMARY KEY (`id`), INDEX `idx_field_cycle_field` (`field_id`), INDEX `idx_field_cycle_current` (`is_current`) ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4',
    'SELECT 1'
  )
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'field_crop_cycle'
);
PREPARE stmt_cycle_table_seed FROM @ddl_cycle_table_seed;
EXECUTE stmt_cycle_table_seed;
DEALLOCATE PREPARE stmt_cycle_table_seed;

SET @ddl_cycle_plan_mode_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `field_crop_cycle` ADD COLUMN `plan_mode` VARCHAR(32) DEFAULT ''single''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'field_crop_cycle' AND COLUMN_NAME = 'plan_mode'
);
PREPARE stmt_cycle_plan_mode_seed FROM @ddl_cycle_plan_mode_seed;
EXECUTE stmt_cycle_plan_mode_seed;
DEALLOCATE PREPARE stmt_cycle_plan_mode_seed;

SET @ddl_record_cycle_id_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `farm_record` ADD COLUMN `cycle_id` BIGINT DEFAULT NULL',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_record' AND COLUMN_NAME = 'cycle_id'
);
PREPARE stmt_record_cycle_id_seed FROM @ddl_record_cycle_id_seed;
EXECUTE stmt_record_cycle_id_seed;
DEALLOCATE PREPARE stmt_record_cycle_id_seed;

SET @ddl_record_weather_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `farm_record` ADD COLUMN `weather` VARCHAR(64) DEFAULT NULL',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_record' AND COLUMN_NAME = 'weather'
);
PREPARE stmt_record_weather_seed FROM @ddl_record_weather_seed;
EXECUTE stmt_record_weather_seed;
DEALLOCATE PREPARE stmt_record_weather_seed;

SET @ddl_record_temperature_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `farm_record` ADD COLUMN `temperature` VARCHAR(32) DEFAULT NULL',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_record' AND COLUMN_NAME = 'temperature'
);
PREPARE stmt_record_temperature_seed FROM @ddl_record_temperature_seed;
EXECUTE stmt_record_temperature_seed;
DEALLOCATE PREPARE stmt_record_temperature_seed;

SET @ddl_record_weather_location_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `farm_record` ADD COLUMN `weather_location` VARCHAR(128) DEFAULT NULL',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_record' AND COLUMN_NAME = 'weather_location'
);
PREPARE stmt_record_weather_location_seed FROM @ddl_record_weather_location_seed;
EXECUTE stmt_record_weather_location_seed;
DEALLOCATE PREPARE stmt_record_weather_location_seed;

SET @ddl_record_humidity_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `farm_record` ADD COLUMN `humidity` VARCHAR(16) DEFAULT NULL',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_record' AND COLUMN_NAME = 'humidity'
);
PREPARE stmt_record_humidity_seed FROM @ddl_record_humidity_seed;
EXECUTE stmt_record_humidity_seed;
DEALLOCATE PREPARE stmt_record_humidity_seed;

SET @ddl_record_wind_direction_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `farm_record` ADD COLUMN `wind_direction` VARCHAR(16) DEFAULT NULL',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_record' AND COLUMN_NAME = 'wind_direction'
);
PREPARE stmt_record_wind_direction_seed FROM @ddl_record_wind_direction_seed;
EXECUTE stmt_record_wind_direction_seed;
DEALLOCATE PREPARE stmt_record_wind_direction_seed;

SET @ddl_record_wind_power_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `farm_record` ADD COLUMN `wind_power` VARCHAR(16) DEFAULT NULL',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_record' AND COLUMN_NAME = 'wind_power'
);
PREPARE stmt_record_wind_power_seed FROM @ddl_record_wind_power_seed;
EXECUTE stmt_record_wind_power_seed;
DEALLOCATE PREPARE stmt_record_wind_power_seed;

SET @ddl_record_weather_report_time_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `farm_record` ADD COLUMN `weather_report_time` VARCHAR(32) DEFAULT NULL',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_record' AND COLUMN_NAME = 'weather_report_time'
);
PREPARE stmt_record_weather_report_time_seed FROM @ddl_record_weather_report_time_seed;
EXECUTE stmt_record_weather_report_time_seed;
DEALLOCATE PREPARE stmt_record_weather_report_time_seed;

UPDATE `field` SET `township`='东河镇', `crop_variety`='先玉335' WHERE `id`=41001;
UPDATE `field` SET `township`='东河镇', `crop_variety`='济麦22' WHERE `id`=41002;
UPDATE `field` SET `township`='南川镇', `crop_variety`='先玉335' WHERE `id`=41003;
UPDATE `field` SET `township`='西源镇', `crop_variety`='郑单958' WHERE `id`=41004;
UPDATE `field` SET `township`='北岭镇', `crop_variety`='济麦22' WHERE `id`=41005;

SET @ddl_drop_field_soil_type_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'SELECT 1',
    'ALTER TABLE `field` DROP COLUMN `soil_type`'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'field' AND COLUMN_NAME = 'soil_type'
);
PREPARE stmt_drop_field_soil_type_seed FROM @ddl_drop_field_soil_type_seed;
EXECUTE stmt_drop_field_soil_type_seed;
DEALLOCATE PREPARE stmt_drop_field_soil_type_seed;

SET @ddl_drop_field_irrigation_type_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'SELECT 1',
    'ALTER TABLE `field` DROP COLUMN `irrigation_type`'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'field' AND COLUMN_NAME = 'irrigation_type'
);
PREPARE stmt_drop_field_irrigation_type_seed FROM @ddl_drop_field_irrigation_type_seed;
EXECUTE stmt_drop_field_irrigation_type_seed;
DEALLOCATE PREPARE stmt_drop_field_irrigation_type_seed;

INSERT INTO `field_crop_cycle`
(`id`,`field_id`,`cycle_name`,`crops_json`,`template_ids_json`,`plan_mode`,`start_date`,`end_date`,`status`,`is_current`,`deleted`)
VALUES
  (81001,41001,'2026春玉米计划','[{"name":"玉米","variety":"先玉335","templateIds":[2001]}]','[2001]','single','2026-02-01',NULL,'active',1,0),
  (81002,41001,'2025冬小麦计划','[{"name":"小麦","variety":"济麦22","templateIds":[2002]}]','[2002]','rotation','2025-10-15','2026-01-20','completed',0,0),
  (81003,41004,'2026玉米大豆间作计划','[{"name":"玉米","variety":"郑单958","templateIds":[2001]},{"name":"大豆","variety":"中黄13","templateIds":[2002]}]','[2001,2002]','intercropping','2026-01-28',NULL,'active',1,0)
ON DUPLICATE KEY UPDATE
  `field_id`=VALUES(`field_id`),
  `cycle_name`=VALUES(`cycle_name`),
  `crops_json`=VALUES(`crops_json`),
  `template_ids_json`=VALUES(`template_ids_json`),
  `plan_mode`=VALUES(`plan_mode`),
  `start_date`=VALUES(`start_date`),
  `end_date`=VALUES(`end_date`),
  `status`=VALUES(`status`),
  `is_current`=VALUES(`is_current`),
  `deleted`=0;

UPDATE `farm_record` SET `cycle_id`=81001, `weather`='多云', `temperature`='16', `weather_location`='东河镇', `humidity`='66', `wind_direction`='东北风', `wind_power`='3级', `weather_report_time`='2026-02-10 07:50:00' WHERE `id` IN (51001,51002,51003,51009,51010);
UPDATE `farm_record` SET `cycle_id`=81003, `weather`='晴', `temperature`='18', `weather_location`='西源镇', `humidity`='58', `wind_direction`='东风', `wind_power`='2级', `weather_report_time`='2026-02-11 08:10:00' WHERE `id` IN (51006,51012);
UPDATE `farm_record` SET `weather`='晴', `temperature`='15', `weather_location`='东河镇', `humidity`='62', `wind_direction`='北风', `wind_power`='2级', `weather_report_time`='2026-02-12 06:50:00' WHERE `id`=51011;

-- ----------------------------
-- 5. 用户演示数据
-- ----------------------------
INSERT INTO `user`
(`id`,`wx_open_id`,`nick_name`,`real_name`,`phone`,`status`,`role_code`,`can_console`,`user_type`,`avatar_source`,`enabled`,`is_super_admin`,`apply_reason`,`reject_reason`,`deleted`)
VALUES
  (91001,'mock_admin_0001','系统管理员','管理员','13800000001',NULL,'admin',1,'admin','none',1,1,NULL,NULL,0),
  (91004,'mock_supervisor_0001','监管员A','赵主管','13800000004',NULL,'supervisor',1,'admin','none',1,0,NULL,NULL,0),
  (91002,'mock_operator_0001','田间员A','张强','13800000002','approved','operator',0,'miniapp','none',1,0,'已通过',NULL,0),
  (91003,'mock_pending_0001','申请人B','李敏','13800000003','pending','operator',0,'miniapp','none',1,0,'申请加入农事系统',NULL,0)
ON DUPLICATE KEY UPDATE
  `nick_name`=VALUES(`nick_name`),
  `real_name`=VALUES(`real_name`),
  `phone`=VALUES(`phone`),
  `status`=VALUES(`status`),
  `role_code`=VALUES(`role_code`),
  `can_console`=VALUES(`can_console`),
  `user_type`=VALUES(`user_type`),
  `avatar_source`=VALUES(`avatar_source`),
  `enabled`=VALUES(`enabled`),
  `is_super_admin`=VALUES(`is_super_admin`),
  `apply_reason`=VALUES(`apply_reason`),
  `reject_reason`=VALUES(`reject_reason`),
  `deleted`=0;

-- ----------------------------
-- 6. 种子样本数规则兼容
-- ----------------------------
SET @ddl_seed_test_sample_count_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `seed_quality_test` ADD COLUMN `sample_count` INT DEFAULT NULL COMMENT ''Sample count for germination test''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_quality_test' AND COLUMN_NAME = 'sample_count'
);
PREPARE stmt_seed_test_sample_count_seed FROM @ddl_seed_test_sample_count_seed;
EXECUTE stmt_seed_test_sample_count_seed;
DEALLOCATE PREPARE stmt_seed_test_sample_count_seed;

SET @ddl_seed_test_germination_count_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `seed_quality_test` ADD COLUMN `germination_count` INT DEFAULT NULL COMMENT ''Germinated seed count''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_quality_test' AND COLUMN_NAME = 'germination_count'
);
PREPARE stmt_seed_test_germination_count_seed FROM @ddl_seed_test_germination_count_seed;
EXECUTE stmt_seed_test_germination_count_seed;
DEALLOCATE PREPARE stmt_seed_test_germination_count_seed;

SET @ddl_seed_rule_table_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'CREATE TABLE `seed_quality_rule` ( `id` BIGINT NOT NULL, `fixed_sample_size` TINYINT(1) DEFAULT ''1'', `default_sample_size` INT DEFAULT ''100'', `remark` VARCHAR(255) DEFAULT NULL, `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP, `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, PRIMARY KEY (`id`) ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4',
    'SELECT 1'
  )
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_quality_rule'
);
PREPARE stmt_seed_rule_table_seed FROM @ddl_seed_rule_table_seed;
EXECUTE stmt_seed_rule_table_seed;
DEALLOCATE PREPARE stmt_seed_rule_table_seed;

INSERT INTO `seed_quality_rule` (`id`, `fixed_sample_size`, `default_sample_size`, `remark`)
VALUES (1, 1, 100, 'default')
ON DUPLICATE KEY UPDATE
  `fixed_sample_size` = VALUES(`fixed_sample_size`),
  `default_sample_size` = VALUES(`default_sample_size`),
  `remark` = VALUES(`remark`);

UPDATE `seed_quality_test` SET `sample_count`=100 WHERE `id` IN (71001,71002,71003,71004,71005,71006,71007,71008);

SET @ddl_drop_seed_batch_origin_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'SELECT 1',
    'ALTER TABLE `seed_batch` DROP COLUMN `origin`'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_batch' AND COLUMN_NAME = 'origin'
);
PREPARE stmt_drop_seed_batch_origin_seed FROM @ddl_drop_seed_batch_origin_seed;
EXECUTE stmt_drop_seed_batch_origin_seed;
DEALLOCATE PREPARE stmt_drop_seed_batch_origin_seed;

SET @ddl_drop_seed_batch_storage_location_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'SELECT 1',
    'ALTER TABLE `seed_batch` DROP COLUMN `storage_location`'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_batch' AND COLUMN_NAME = 'storage_location'
);
PREPARE stmt_drop_seed_batch_storage_location_seed FROM @ddl_drop_seed_batch_storage_location_seed;
EXECUTE stmt_drop_seed_batch_storage_location_seed;
DEALLOCATE PREPARE stmt_drop_seed_batch_storage_location_seed;

-- ----------------------------
-- 7. 动态参数中心演示数据
-- ----------------------------
SET @ddl_dynamic_config_table_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'CREATE TABLE `dynamic_form_config` ( `id` BIGINT NOT NULL, `module_key` VARCHAR(64) NOT NULL, `scene_key` VARCHAR(64) NOT NULL, `config_name` VARCHAR(128) NOT NULL, `schema_json` LONGTEXT NOT NULL, `status` VARCHAR(16) DEFAULT ''enabled'', `version_no` INT DEFAULT ''1'', `remark` VARCHAR(255) DEFAULT NULL, `deleted` TINYINT(1) DEFAULT ''0'', `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP, `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, PRIMARY KEY (`id`), INDEX `idx_dynamic_module_scene` (`module_key`,`scene_key`), INDEX `idx_dynamic_status` (`status`) ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4',
    'SELECT 1'
  )
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dynamic_form_config'
);
PREPARE stmt_dynamic_config_table_seed FROM @ddl_dynamic_config_table_seed;
EXECUTE stmt_dynamic_config_table_seed;
DEALLOCATE PREPARE stmt_dynamic_config_table_seed;

INSERT INTO `dynamic_form_config`
(`id`,`module_key`,`scene_key`,`config_name`,`schema_json`,`status`,`version_no`,`remark`,`deleted`)
VALUES
  (92001,'farm','step_fields','农事步骤字段模板(演示)','[{"key":"amountPerMu","label":"数量(kg/亩)","type":"number","required":true},{"key":"operationDate","label":"作业日期","type":"date","required":false},{"key":"operationPoint","label":"作业位置","type":"location","required":false}]','enabled',1,'demo',0),
  (92002,'seed','batch_fields','种子批次字段模板(演示)','[{"key":"supplier","label":"供应商","type":"text","required":true},{"key":"warehouseZone","label":"库区","type":"select","required":false,"options":[{"label":"A区","value":"A"},{"label":"B区","value":"B"}]}]','enabled',1,'demo',0),
  (92003,'seed','test_fields','种子检测字段模板(演示)','[{"key":"testMethod","label":"检测方法","type":"select","required":true,"options":[{"label":"纸床法","value":"paper_bed"},{"label":"砂床法","value":"sand_bed"}]},{"key":"environment","label":"检测环境","type":"text","required":false}]','enabled',1,'demo',0)
ON DUPLICATE KEY UPDATE
  `module_key`=VALUES(`module_key`),
  `scene_key`=VALUES(`scene_key`),
  `config_name`=VALUES(`config_name`),
  `schema_json`=VALUES(`schema_json`),
  `status`=VALUES(`status`),
  `version_no`=VALUES(`version_no`),
  `remark`=VALUES(`remark`),
  `deleted`=0;

UPDATE `farm_process_step`
SET `form_config_id`=92001,
    `form_schema`='[{"key":"amountPerMu","label":"数量(kg/亩)","type":"number","required":true},{"key":"operationDate","label":"作业日期","type":"date","required":false},{"key":"operationPoint","label":"作业位置","type":"location","required":false}]'
WHERE `id` IN (3004, 3014);

UPDATE `seed_batch`
SET `form_config_id`=92002
WHERE `id` IN (61001,61002,61003,61004);

UPDATE `seed_quality_test`
SET `form_config_id`=92003
WHERE `id` IN (71001,71002,71003,71004,71005,71006,71007,71008);

-- ----------------------------
-- 8. 导出模板标准化演示数据
-- ----------------------------
SET @ddl_export_field_dict_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'CREATE TABLE `export_field_dict` ( `id` BIGINT NOT NULL, `module_key` VARCHAR(32) NOT NULL, `field_code` VARCHAR(64) NOT NULL, `field_name` VARCHAR(128) NOT NULL, `data_type` VARCHAR(32) DEFAULT ''string'', `description` VARCHAR(255) DEFAULT NULL, `example_value` VARCHAR(255) DEFAULT NULL, `deleted` TINYINT(1) DEFAULT ''0'', `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP, `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, PRIMARY KEY (`id`), UNIQUE KEY `uk_export_field_code` (`module_key`,`field_code`), INDEX `idx_export_field_module` (`module_key`) ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4',
    'SELECT 1'
  )
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'export_field_dict'
);
PREPARE stmt_export_field_dict_seed FROM @ddl_export_field_dict_seed;
EXECUTE stmt_export_field_dict_seed;
DEALLOCATE PREPARE stmt_export_field_dict_seed;

SET @ddl_export_template_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'CREATE TABLE `export_template` ( `id` BIGINT NOT NULL, `module_key` VARCHAR(32) NOT NULL, `template_code` VARCHAR(64) NOT NULL, `template_name` VARCHAR(128) NOT NULL, `version_no` INT NOT NULL DEFAULT ''1'', `status` VARCHAR(16) DEFAULT ''enabled'', `fields_json` LONGTEXT NOT NULL, `remark` VARCHAR(255) DEFAULT NULL, `deleted` TINYINT(1) DEFAULT ''0'', `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP, `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, PRIMARY KEY (`id`), UNIQUE KEY `uk_export_template_ver` (`module_key`,`template_code`,`version_no`), INDEX `idx_export_template_status` (`module_key`,`template_code`,`status`) ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4',
    'SELECT 1'
  )
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'export_template'
);
PREPARE stmt_export_template_seed FROM @ddl_export_template_seed;
EXECUTE stmt_export_template_seed;
DEALLOCATE PREPARE stmt_export_template_seed;

INSERT INTO `export_field_dict`
(`id`,`module_key`,`field_code`,`field_name`,`data_type`,`description`,`example_value`,`deleted`)
VALUES
  (93001,'farm','recordId','记录ID','number',NULL,'51001',0),
  (93002,'farm','fieldId','田块ID','number',NULL,'41001',0),
  (93003,'farm','fieldName','田块名称','string',NULL,'东一号田',0),
  (93004,'farm','cycleId','种植计划ID','number',NULL,'81001',0),
  (93005,'farm','stepId','步骤ID','number',NULL,'3001',0),
  (93006,'farm','stepName','步骤名称','string',NULL,'整地',0),
  (93008,'farm','workDate','作业时间','datetime',NULL,'2026-02-10 07:55:00',0),
  (93009,'farm','operatorName','操作员','string',NULL,'王磊',0),
  (93010,'farm','weather','天气','string',NULL,'多云',0),
  (93011,'farm','temperature','温度','string',NULL,'16',0),
  (93012,'farm','weatherLocation','天气位置','string',NULL,'东河镇',0),
  (93015,'farm','humidity','湿度(%)','string',NULL,'68',0),
  (93016,'farm','windDirection','风向','string',NULL,'北风',0),
  (93017,'farm','windPower','风力','string',NULL,'3级',0),
  (93018,'farm','weatherReportTime','天气发布时间','string',NULL,'2026-02-10 07:50:00',0),
  (93013,'farm','notes','备注','string',NULL,'播深约4cm',0),
  (93014,'farm','extraJson','动态参数','json',NULL,'{\"amountPerMu\":\"12\"}',0),
  (93021,'seed','testId','检测ID','number',NULL,'71001',0),
  (93022,'seed','batchId','批次ID','number',NULL,'61001',0),
  (93023,'seed','batchCode','批次号','string',NULL,'DH-2026-CORN-001',0),
  (930236,'seed','cropType','作物','string',NULL,'玉米',0),
  (93024,'seed','varietyName','品种名','string',NULL,'先玉335',0),
  (93025,'seed','testDate','检测日期','date',NULL,'2026-01-20',0),
  (93027,'seed','sampleCount','芽率样本数','number',NULL,'100',0),
  (930271,'seed','germinationCount','发芽数量','number',NULL,'96',0),
  (93028,'seed','germinationRate','芽率(%)','number',NULL,'95.6',0),
  (93030,'seed','moisture','水分(%)','number',NULL,'12.4',0),
  (93029,'seed','purity','纯度(%)','number',NULL,'99.1',0),
  (93031,'seed','cleanliness','净度(%)','number',NULL,'98.6',0),
  (93033,'seed','testerName','检测员','string',NULL,'王蕾',0),
  (93034,'seed','remark','备注','string',NULL,'入库首检合格',0),
  (93035,'seed','createdAt','创建时间','datetime',NULL,'2026-01-20 10:00:00',0)
ON DUPLICATE KEY UPDATE
  `field_name`=VALUES(`field_name`),
  `data_type`=VALUES(`data_type`),
  `description`=VALUES(`description`),
  `example_value`=VALUES(`example_value`),
  `deleted`=0;

INSERT INTO `export_template`
(`id`,`module_key`,`template_code`,`template_name`,`version_no`,`status`,`fields_json`,`remark`,`deleted`)
VALUES
  (94001,'farm','farm_records_standard','农事记录标准模板',1,'enabled','[\"recordId\",\"fieldId\",\"fieldName\",\"cycleId\",\"stepId\",\"stepName\",\"workDate\",\"operatorName\",\"weather\",\"temperature\",\"weatherLocation\",\"humidity\",\"windDirection\",\"windPower\",\"weatherReportTime\",\"notes\",\"extraJson\"]','default',0),
  (94002,'seed','seed_tests_standard','种子检测标准模板',1,'enabled','[\"testId\",\"batchId\",\"batchCode\",\"cropType\",\"varietyName\",\"testDate\",\"sampleCount\",\"germinationCount\",\"germinationRate\",\"moisture\",\"purity\",\"cleanliness\",\"testerName\",\"remark\",\"createdAt\"]','default',0)
ON DUPLICATE KEY UPDATE
  `template_name`=VALUES(`template_name`),
  `status`=VALUES(`status`),
  `fields_json`=VALUES(`fields_json`),
  `remark`=VALUES(`remark`),
  `deleted`=0;

-- ----------------------------
-- 9. 高德额度监控演示数据
-- ----------------------------
SET @ddl_amap_quota_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'CREATE TABLE `amap_quota_config` ( `id` BIGINT NOT NULL, `record_date` DATE NOT NULL, `daily_limit` INT NOT NULL DEFAULT ''50000'', `alert_threshold` INT NOT NULL DEFAULT ''80'', `used_count` INT NOT NULL DEFAULT ''0'', `remark` VARCHAR(255) DEFAULT NULL, `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP, `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, PRIMARY KEY (`id`) ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4',
    'SELECT 1'
  )
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config'
);
PREPARE stmt_amap_quota_seed FROM @ddl_amap_quota_seed;
EXECUTE stmt_amap_quota_seed;
DEALLOCATE PREPARE stmt_amap_quota_seed;

SET @ddl_amap_audit_seed = (
  SELECT IF(
    COUNT(*) = 0,
    'CREATE TABLE `amap_api_audit` ( `id` BIGINT NOT NULL, `record_date` DATE NOT NULL, `user_id` BIGINT DEFAULT NULL, `operator_name` VARCHAR(64) DEFAULT NULL, `biz_scene` VARCHAR(64) DEFAULT NULL, `api_path` VARCHAR(128) DEFAULT NULL, `request_source` VARCHAR(64) DEFAULT NULL, `success_flag` TINYINT(1) DEFAULT ''1'', `cost_ms` INT DEFAULT NULL, `error_message` VARCHAR(255) DEFAULT NULL, `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (`id`), INDEX `idx_amap_audit_date` (`record_date`), INDEX `idx_amap_audit_scene` (`biz_scene`) ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4',
    'SELECT 1'
  )
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_api_audit'
);
PREPARE stmt_amap_audit_seed FROM @ddl_amap_audit_seed;
EXECUTE stmt_amap_audit_seed;
DEALLOCATE PREPARE stmt_amap_audit_seed;

INSERT INTO `amap_quota_config`
(`id`,`record_date`,`daily_limit`,`alert_threshold`,`used_count`,`remark`)
VALUES
  (1,CURDATE(),50000,80,32,'demo')
ON DUPLICATE KEY UPDATE
  `record_date`=VALUES(`record_date`),
  `daily_limit`=VALUES(`daily_limit`),
  `alert_threshold`=VALUES(`alert_threshold`),
  `used_count`=VALUES(`used_count`),
  `remark`=VALUES(`remark`);

INSERT INTO `amap_api_audit`
(`id`,`record_date`,`user_id`,`operator_name`,`biz_scene`,`api_path`,`request_source`,`success_flag`,`cost_ms`,`error_message`)
VALUES
  (95001,CURDATE(),91002,'张强','weather_snapshot','/v3/weather/weatherInfo','miniapp',1,218,NULL),
  (95002,CURDATE(),91002,'张强','reverse_geocode','/v3/geocode/regeo','miniapp',1,132,NULL),
  (95003,CURDATE(),91004,'赵主管','city_search','/v3/config/district','admin',0,640,'AMAP_DAILY_LIMIT_NEAR_THRESHOLD')
ON DUPLICATE KEY UPDATE
  `record_date`=VALUES(`record_date`),
  `user_id`=VALUES(`user_id`),
  `operator_name`=VALUES(`operator_name`),
  `biz_scene`=VALUES(`biz_scene`),
  `api_path`=VALUES(`api_path`),
  `request_source`=VALUES(`request_source`),
  `success_flag`=VALUES(`success_flag`),
  `cost_ms`=VALUES(`cost_ms`),
  `error_message`=VALUES(`error_message`);
