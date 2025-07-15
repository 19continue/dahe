-- DaHe V2 数据库初始化脚本 - 农事管理模块 (流程化增强版)

USE `dahe_v2`;

-- ----------------------------
-- 1. 作物基本信息
-- ----------------------------
CREATE TABLE IF NOT EXISTS `crop` (
  `id` BIGINT NOT NULL COMMENT '主键ID',
  `name` VARCHAR(64) NOT NULL COMMENT '作物名称(如:玉米、小麦)',
  `variety` VARCHAR(64) DEFAULT NULL COMMENT '品种信息',
  `sort_order` INT DEFAULT 0 COMMENT '显示排序',
  `deleted` TINYINT(1) DEFAULT '0',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作物表';

SET @ddl_crop_sort_order = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `crop` ADD COLUMN `sort_order` INT DEFAULT 0 COMMENT ''显示排序''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crop' AND COLUMN_NAME = 'sort_order'
);
PREPARE stmt_crop_sort_order FROM @ddl_crop_sort_order;
EXECUTE stmt_crop_sort_order;
DEALLOCATE PREPARE stmt_crop_sort_order;

-- ----------------------------
-- 2. 农事流程模板
-- ----------------------------
CREATE TABLE IF NOT EXISTS `farm_process_template` (
  `id` BIGINT NOT NULL COMMENT '主键ID',
  `crop_id` BIGINT NOT NULL COMMENT '关联作物ID',
  `template_name` VARCHAR(128) NOT NULL COMMENT '模板名称',
  `is_default` TINYINT(1) DEFAULT '0' COMMENT '是否为该作物的默认模板',
  `enabled` TINYINT(1) DEFAULT '1' COMMENT '是否启用(1启用,0禁用)',
  `deleted` TINYINT(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  INDEX `idx_template_crop` (`crop_id`),
  INDEX `idx_template_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='农事流程模板表';

SET @ddl_template_enabled = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `farm_process_template` ADD COLUMN `enabled` TINYINT(1) DEFAULT ''1'' COMMENT ''是否启用(1启用,0禁用)''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_process_template' AND COLUMN_NAME = 'enabled'
);
PREPARE stmt_template_enabled FROM @ddl_template_enabled;
EXECUTE stmt_template_enabled;
DEALLOCATE PREPARE stmt_template_enabled;

SET @ddl_idx_template_enabled = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `farm_process_template` ADD INDEX `idx_template_enabled` (`enabled`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_process_template' AND INDEX_NAME = 'idx_template_enabled'
);
PREPARE stmt_idx_template_enabled FROM @ddl_idx_template_enabled;
EXECUTE stmt_idx_template_enabled;
DEALLOCATE PREPARE stmt_idx_template_enabled;

UPDATE `farm_process_template`
SET `enabled`=1
WHERE `enabled` IS NULL;

UPDATE `farm_process_template`
SET `is_default`=0
WHERE `enabled`=0 AND `is_default`=1;

-- ----------------------------
-- 3. 流程步骤定义
-- ----------------------------
CREATE TABLE IF NOT EXISTS `farm_process_step` (
  `id` BIGINT NOT NULL COMMENT '主键ID',
  `template_id` BIGINT NOT NULL COMMENT '关联模板ID',
  `step_name` VARCHAR(64) NOT NULL COMMENT '步骤名称(如:基肥、播种)',
  `sort_order` INT DEFAULT 0 COMMENT '排序/执行顺序',
  `requirement_desc` VARCHAR(512) DEFAULT NULL COMMENT '操作要求说明',
  `form_config_id` BIGINT DEFAULT NULL COMMENT '动态参数配置ID(dynamic_form_config.id)',
  `form_schema` LONGTEXT DEFAULT NULL COMMENT '步骤参数表单配置(JSON)',
  PRIMARY KEY (`id`),
  INDEX `idx_step_template` (`template_id`),
  INDEX `idx_step_form_config` (`form_config_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程步骤定义表';

-- ----------------------------
-- 4. 农事记录 (增加步骤关联)
-- ----------------------------
CREATE TABLE IF NOT EXISTS `farm_record` (
  `id` BIGINT NOT NULL COMMENT '主键ID',
  `field_id` BIGINT NOT NULL COMMENT '关联田块ID',
  `step_id` BIGINT DEFAULT NULL COMMENT '关联流程步骤ID(如果是按流程操作)',
  `work_date` DATETIME NOT NULL COMMENT '作业时间',
  `operator_name` VARCHAR(64) DEFAULT NULL COMMENT '执行人',
  `operator_user_id` BIGINT DEFAULT NULL COMMENT '执行人用户ID',
  `notes` VARCHAR(1024) DEFAULT NULL COMMENT '备注/实测数据',
  `extra_json` LONGTEXT DEFAULT NULL COMMENT '流程步骤动态参数(JSON)',
  `deleted` TINYINT(1) DEFAULT '0',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_farm_record_field` (`field_id`),
  INDEX `idx_farm_record_step` (`step_id`),
  INDEX `idx_farm_record_operator_user` (`operator_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='农事作业记录表';

SET @ddl_drop_step_work_type = (
  SELECT IF(
    COUNT(*) = 0,
    'SELECT 1',
    'ALTER TABLE `farm_process_step` DROP COLUMN `work_type`'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_process_step' AND COLUMN_NAME = 'work_type'
);
PREPARE stmt_drop_step_work_type FROM @ddl_drop_step_work_type;
EXECUTE stmt_drop_step_work_type;
DEALLOCATE PREPARE stmt_drop_step_work_type;

SET @ddl_drop_record_work_type = (
  SELECT IF(
    COUNT(*) = 0,
    'SELECT 1',
    'ALTER TABLE `farm_record` DROP COLUMN `work_type`'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_record' AND COLUMN_NAME = 'work_type'
);
PREPARE stmt_drop_record_work_type FROM @ddl_drop_record_work_type;
EXECUTE stmt_drop_record_work_type;
DEALLOCATE PREPARE stmt_drop_record_work_type;

SET @ddl_form_schema = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `farm_process_step` ADD COLUMN `form_schema` LONGTEXT DEFAULT NULL COMMENT ''步骤参数表单配置(JSON)''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_process_step' AND COLUMN_NAME = 'form_schema'
);
PREPARE stmt_form_schema FROM @ddl_form_schema;
EXECUTE stmt_form_schema;
DEALLOCATE PREPARE stmt_form_schema;

SET @ddl_form_config_id = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `farm_process_step` ADD COLUMN `form_config_id` BIGINT DEFAULT NULL COMMENT ''动态参数配置ID(dynamic_form_config.id)''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_process_step' AND COLUMN_NAME = 'form_config_id'
);
PREPARE stmt_form_config_id FROM @ddl_form_config_id;
EXECUTE stmt_form_config_id;
DEALLOCATE PREPARE stmt_form_config_id;

SET @ddl_idx_step_form_config = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `farm_process_step` ADD INDEX `idx_step_form_config` (`form_config_id`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_process_step' AND INDEX_NAME = 'idx_step_form_config'
);
PREPARE stmt_idx_step_form_config FROM @ddl_idx_step_form_config;
EXECUTE stmt_idx_step_form_config;
DEALLOCATE PREPARE stmt_idx_step_form_config;

SET @ddl_extra_json = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `farm_record` ADD COLUMN `extra_json` LONGTEXT DEFAULT NULL COMMENT ''流程步骤动态参数(JSON)''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_record' AND COLUMN_NAME = 'extra_json'
);
PREPARE stmt_extra_json FROM @ddl_extra_json;
EXECUTE stmt_extra_json;
DEALLOCATE PREPARE stmt_extra_json;

SET @ddl_record_operator_user_id = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `farm_record` ADD COLUMN `operator_user_id` BIGINT DEFAULT NULL COMMENT ''执行人用户ID''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_record' AND COLUMN_NAME = 'operator_user_id'
);
PREPARE stmt_record_operator_user_id FROM @ddl_record_operator_user_id;
EXECUTE stmt_record_operator_user_id;
DEALLOCATE PREPARE stmt_record_operator_user_id;

SET @ddl_idx_record_operator_user_id = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `farm_record` ADD INDEX `idx_farm_record_operator_user` (`operator_user_id`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_record' AND INDEX_NAME = 'idx_farm_record_operator_user'
);
PREPARE stmt_idx_record_operator_user_id FROM @ddl_idx_record_operator_user_id;
EXECUTE stmt_idx_record_operator_user_id;
DEALLOCATE PREPARE stmt_idx_record_operator_user_id;

-- ----------------------------
-- 5. 作业图片
-- ----------------------------
CREATE TABLE IF NOT EXISTS `farm_record_photo` (
  `id` BIGINT NOT NULL COMMENT '主键ID',
  `record_id` BIGINT NOT NULL COMMENT '农事记录ID',
  `url` VARCHAR(512) NOT NULL COMMENT '图片URL',
  `sort` INT DEFAULT 0,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_farm_photo_record` (`record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='农事记录图片表';

-- ----------------------------
-- 6. 流程化演示数据（玉米）
-- ----------------------------
INSERT INTO `crop` (`id`, `name`, `variety`, `deleted`)
SELECT 1001, '玉米', '先玉335', 0
WHERE NOT EXISTS (SELECT 1 FROM `crop` WHERE `id` = 1001);

INSERT INTO `farm_process_template` (`id`, `crop_id`, `template_name`, `is_default`, `enabled`, `deleted`)
SELECT 2001, 1001, '玉米标准流程', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `farm_process_template` WHERE `id` = 2001);

INSERT INTO `farm_process_step` (`id`, `template_id`, `step_name`, `sort_order`, `requirement_desc`, `form_schema`)
SELECT 3001, 2001, '整地', 1, '确认土壤墒情，地块表面平整后再入下一步',
       '[{\"key\":\"machineType\",\"label\":\"机械类型\",\"type\":\"text\",\"required\":true,\"placeholder\":\"如：旋耕机\"},{\"key\":\"operationDepth\",\"label\":\"耕作深度(cm)\",\"type\":\"number\",\"required\":false}]'
WHERE NOT EXISTS (SELECT 1 FROM `farm_process_step` WHERE `id` = 3001);

INSERT INTO `farm_process_step` (`id`, `template_id`, `step_name`, `sort_order`, `requirement_desc`, `form_schema`)
SELECT 3002, 2001, '施基肥', 2, '记录肥料类型与亩用量，避免超量',
       '[{\"key\":\"fertilizerName\",\"label\":\"肥料名称\",\"type\":\"text\",\"required\":true},{\"key\":\"amountPerMu\",\"label\":\"施肥量(kg/亩)\",\"type\":\"number\",\"required\":true}]'
WHERE NOT EXISTS (SELECT 1 FROM `farm_process_step` WHERE `id` = 3002);

INSERT INTO `farm_process_step` (`id`, `template_id`, `step_name`, `sort_order`, `requirement_desc`, `form_schema`)
SELECT 3003, 2001, '播种', 3, '记录播种深度与株距，确保地块覆盖完整',
       '[{\"key\":\"seedVariety\",\"label\":\"种子品种\",\"type\":\"text\",\"required\":true},{\"key\":\"seedAmount\",\"label\":\"播种量(kg/亩)\",\"type\":\"number\",\"required\":true},{\"key\":\"rowSpacing\",\"label\":\"行距(cm)\",\"type\":\"number\",\"required\":false}]'
WHERE NOT EXISTS (SELECT 1 FROM `farm_process_step` WHERE `id` = 3003);

INSERT INTO `farm_process_step` (`id`, `template_id`, `step_name`, `sort_order`, `requirement_desc`, `form_schema`)
SELECT 3004, 2001, '追肥', 4, '按苗情追肥，建议同时记录天气情况',
       '[{\"key\":\"amountPerMu\",\"label\":\"追肥量(kg/亩)\",\"type\":\"number\",\"required\":true},{\"key\":\"fertilizerType\",\"label\":\"肥料类型\",\"type\":\"text\",\"required\":false}]'
WHERE NOT EXISTS (SELECT 1 FROM `farm_process_step` WHERE `id` = 3004);

INSERT INTO `farm_process_step` (`id`, `template_id`, `step_name`, `sort_order`, `requirement_desc`, `form_schema`)
SELECT 3005, 2001, '收获', 5, '收获前确认成熟度并记录估产',
       '[{\"key\":\"yieldPerMu\",\"label\":\"亩产(kg)\",\"type\":\"number\",\"required\":true},{\"key\":\"moisture\",\"label\":\"籽粒水分(%)\",\"type\":\"number\",\"required\":false}]'
WHERE NOT EXISTS (SELECT 1 FROM `farm_process_step` WHERE `id` = 3005);


-- ---- V2 extension: planting plan + weather + stage ----
CREATE TABLE IF NOT EXISTS `field_crop_cycle` (
  `id` BIGINT NOT NULL COMMENT 'Primary key',
  `field_id` BIGINT NOT NULL COMMENT 'Field id',
  `cycle_name` VARCHAR(64) NOT NULL COMMENT 'Planting plan name',
  `crops_json` LONGTEXT DEFAULT NULL COMMENT 'Crops of this plan(JSON)',
  `template_ids_json` LONGTEXT DEFAULT NULL COMMENT 'Bound process template ids(JSON)',
  `plan_mode` VARCHAR(32) DEFAULT 'single' COMMENT 'single/rotation/intercropping/relay/mixed/fallow/custom',
  `start_date` DATE DEFAULT NULL,
  `end_date` DATE DEFAULT NULL,
  `status` VARCHAR(32) DEFAULT 'active' COMMENT 'active/completed',
  `is_current` TINYINT(1) DEFAULT '0',
  `deleted` TINYINT(1) DEFAULT '0',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_field_cycle_field` (`field_id`),
  INDEX `idx_field_cycle_current` (`is_current`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Field crop cycle';

SET @ddl_cycle_plan_mode = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `field_crop_cycle` ADD COLUMN `plan_mode` VARCHAR(32) DEFAULT ''single'' COMMENT ''single/rotation/intercropping/relay/mixed/fallow/custom''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'field_crop_cycle' AND COLUMN_NAME = 'plan_mode'
);
PREPARE stmt_cycle_plan_mode FROM @ddl_cycle_plan_mode;
EXECUTE stmt_cycle_plan_mode;
DEALLOCATE PREPARE stmt_cycle_plan_mode;

UPDATE `field_crop_cycle`
SET `plan_mode`='single'
WHERE `plan_mode` IS NULL OR TRIM(`plan_mode`)='';

SET @ddl_step_growth_stage = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `farm_process_step` ADD COLUMN `growth_stage` VARCHAR(16) DEFAULT NULL COMMENT ''sowing/growing/harvesting''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_process_step' AND COLUMN_NAME = 'growth_stage'
);
PREPARE stmt_step_growth_stage FROM @ddl_step_growth_stage;
EXECUTE stmt_step_growth_stage;
DEALLOCATE PREPARE stmt_step_growth_stage;

SET @ddl_record_cycle_id = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `farm_record` ADD COLUMN `cycle_id` BIGINT DEFAULT NULL COMMENT ''Field cycle id''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_record' AND COLUMN_NAME = 'cycle_id'
);
PREPARE stmt_record_cycle_id FROM @ddl_record_cycle_id;
EXECUTE stmt_record_cycle_id;
DEALLOCATE PREPARE stmt_record_cycle_id;

SET @ddl_record_weather = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `farm_record` ADD COLUMN `weather` VARCHAR(64) DEFAULT NULL COMMENT ''Weather text''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_record' AND COLUMN_NAME = 'weather'
);
PREPARE stmt_record_weather FROM @ddl_record_weather;
EXECUTE stmt_record_weather;
DEALLOCATE PREPARE stmt_record_weather;

SET @ddl_record_temperature = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `farm_record` ADD COLUMN `temperature` VARCHAR(32) DEFAULT NULL COMMENT ''Temperature''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_record' AND COLUMN_NAME = 'temperature'
);
PREPARE stmt_record_temperature FROM @ddl_record_temperature;
EXECUTE stmt_record_temperature;
DEALLOCATE PREPARE stmt_record_temperature;

SET @ddl_record_weather_location = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `farm_record` ADD COLUMN `weather_location` VARCHAR(128) DEFAULT NULL COMMENT ''Weather location''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_record' AND COLUMN_NAME = 'weather_location'
);
PREPARE stmt_record_weather_location FROM @ddl_record_weather_location;
EXECUTE stmt_record_weather_location;
DEALLOCATE PREPARE stmt_record_weather_location;

SET @ddl_record_humidity = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `farm_record` ADD COLUMN `humidity` VARCHAR(16) DEFAULT NULL COMMENT ''Humidity(%)''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_record' AND COLUMN_NAME = 'humidity'
);
PREPARE stmt_record_humidity FROM @ddl_record_humidity;
EXECUTE stmt_record_humidity;
DEALLOCATE PREPARE stmt_record_humidity;

SET @ddl_record_wind_direction = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `farm_record` ADD COLUMN `wind_direction` VARCHAR(16) DEFAULT NULL COMMENT ''Wind direction''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_record' AND COLUMN_NAME = 'wind_direction'
);
PREPARE stmt_record_wind_direction FROM @ddl_record_wind_direction;
EXECUTE stmt_record_wind_direction;
DEALLOCATE PREPARE stmt_record_wind_direction;

SET @ddl_record_wind_power = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `farm_record` ADD COLUMN `wind_power` VARCHAR(16) DEFAULT NULL COMMENT ''Wind power''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_record' AND COLUMN_NAME = 'wind_power'
);
PREPARE stmt_record_wind_power FROM @ddl_record_wind_power;
EXECUTE stmt_record_wind_power;
DEALLOCATE PREPARE stmt_record_wind_power;

SET @ddl_record_weather_report_time = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `farm_record` ADD COLUMN `weather_report_time` VARCHAR(32) DEFAULT NULL COMMENT ''Weather report time''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'farm_record' AND COLUMN_NAME = 'weather_report_time'
);
PREPARE stmt_record_weather_report_time FROM @ddl_record_weather_report_time;
EXECUTE stmt_record_weather_report_time;
DEALLOCATE PREPARE stmt_record_weather_report_time;

CREATE TABLE IF NOT EXISTS `record_policy_config` (
  `id` BIGINT NOT NULL COMMENT 'Primary key',
  `edit_window_hours` INT NOT NULL DEFAULT '48' COMMENT 'Editable window(hours), 0 means unlimited',
  `allow_operator_update` TINYINT(1) NOT NULL DEFAULT '1',
  `allow_operator_delete` TINYINT(1) NOT NULL DEFAULT '1',
  `remark` VARCHAR(255) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Farm record policy config';

INSERT INTO `record_policy_config` (`id`, `edit_window_hours`, `allow_operator_update`, `allow_operator_delete`, `remark`)
VALUES (1, 48, 1, 1, 'default')
ON DUPLICATE KEY UPDATE
  `edit_window_hours` = VALUES(`edit_window_hours`),
  `allow_operator_update` = VALUES(`allow_operator_update`),
  `allow_operator_delete` = VALUES(`allow_operator_delete`),
  `remark` = VALUES(`remark`);
