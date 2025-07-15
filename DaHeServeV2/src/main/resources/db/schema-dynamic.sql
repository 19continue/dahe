-- DaHe V2 database init - Dynamic form config module
USE `dahe_v2`;

CREATE TABLE IF NOT EXISTS `dynamic_form_config` (
  `id` BIGINT NOT NULL COMMENT 'Primary key',
  `module_key` VARCHAR(64) NOT NULL COMMENT 'Module key: farm/seed/field/...',
  `scene_key` VARCHAR(64) NOT NULL COMMENT 'Scene key in module',
  `config_name` VARCHAR(128) NOT NULL COMMENT 'Config display name',
  `schema_json` LONGTEXT NOT NULL COMMENT 'Dynamic schema definition JSON',
  `status` VARCHAR(16) DEFAULT 'enabled' COMMENT 'enabled/disabled',
  `version_no` INT DEFAULT '1' COMMENT 'Version number',
  `active_version_key` VARCHAR(255) GENERATED ALWAYS AS (CASE WHEN `deleted`=0 AND `module_key` IS NOT NULL AND TRIM(`module_key`)<>'' AND `scene_key` IS NOT NULL AND TRIM(`scene_key`)<>'' AND `config_name` IS NOT NULL AND TRIM(`config_name`)<>'' AND `version_no` IS NOT NULL THEN CONCAT(LOWER(TRIM(`module_key`)),'#',LOWER(TRIM(`scene_key`)),'#',LOWER(TRIM(`config_name`)),'#',`version_no`) ELSE NULL END) STORED COMMENT 'Active version unique key',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT 'Remark',
  `deleted` TINYINT(1) DEFAULT '0',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dynamic_module_scene_name_version` (`active_version_key`),
  INDEX `idx_dynamic_module_scene` (`module_key`, `scene_key`),
  INDEX `idx_dynamic_status` (`status`),
  INDEX `idx_dynamic_updated` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Dynamic form config';

SET @ddl_dynamic_version_no = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `dynamic_form_config` ADD COLUMN `version_no` INT DEFAULT ''1''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dynamic_form_config' AND COLUMN_NAME = 'version_no'
);
PREPARE stmt_dynamic_version_no FROM @ddl_dynamic_version_no;
EXECUTE stmt_dynamic_version_no;
DEALLOCATE PREPARE stmt_dynamic_version_no;

SET @ddl_dynamic_status = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `dynamic_form_config` ADD COLUMN `status` VARCHAR(16) DEFAULT ''enabled''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dynamic_form_config' AND COLUMN_NAME = 'status'
);
PREPARE stmt_dynamic_status FROM @ddl_dynamic_status;
EXECUTE stmt_dynamic_status;
DEALLOCATE PREPARE stmt_dynamic_status;

SET @ddl_dynamic_active_version_key = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `dynamic_form_config` ADD COLUMN `active_version_key` VARCHAR(255) GENERATED ALWAYS AS (CASE WHEN `deleted`=0 AND `module_key` IS NOT NULL AND TRIM(`module_key`)<>'''' AND `scene_key` IS NOT NULL AND TRIM(`scene_key`)<>'''' AND `config_name` IS NOT NULL AND TRIM(`config_name`)<>'''' AND `version_no` IS NOT NULL THEN CONCAT(LOWER(TRIM(`module_key`)),''#'',LOWER(TRIM(`scene_key`)),''#'',LOWER(TRIM(`config_name`)),''#'',`version_no`) ELSE NULL END) STORED COMMENT ''Active version unique key''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dynamic_form_config' AND COLUMN_NAME = 'active_version_key'
);
PREPARE stmt_dynamic_active_version_key FROM @ddl_dynamic_active_version_key;
EXECUTE stmt_dynamic_active_version_key;
DEALLOCATE PREPARE stmt_dynamic_active_version_key;

SET @ddl_drop_uk_dynamic_module_scene_version = (
  SELECT IF(
    COUNT(*) > 0,
    'ALTER TABLE `dynamic_form_config` DROP INDEX `uk_dynamic_module_scene_version`',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dynamic_form_config' AND INDEX_NAME = 'uk_dynamic_module_scene_version'
);
PREPARE stmt_drop_uk_dynamic_module_scene_version FROM @ddl_drop_uk_dynamic_module_scene_version;
EXECUTE stmt_drop_uk_dynamic_module_scene_version;
DEALLOCATE PREPARE stmt_drop_uk_dynamic_module_scene_version;

SET @ddl_uk_dynamic_module_scene_name_version = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `dynamic_form_config` ADD UNIQUE KEY `uk_dynamic_module_scene_name_version` (`active_version_key`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dynamic_form_config' AND INDEX_NAME = 'uk_dynamic_module_scene_name_version'
);
PREPARE stmt_uk_dynamic_module_scene_name_version FROM @ddl_uk_dynamic_module_scene_name_version;
EXECUTE stmt_uk_dynamic_module_scene_name_version;
DEALLOCATE PREPARE stmt_uk_dynamic_module_scene_name_version;
