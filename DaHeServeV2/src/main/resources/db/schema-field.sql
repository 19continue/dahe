-- DaHe V2 database init - Field module

CREATE DATABASE IF NOT EXISTS `dahe_v2` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE `dahe_v2`;

CREATE TABLE IF NOT EXISTS `field` (
  `id` BIGINT NOT NULL COMMENT 'Primary key',
  `name` VARCHAR(128) NOT NULL COMMENT 'Field name',
  `area_mu` DECIMAL(10,2) NOT NULL COMMENT 'Area in mu',
  `crop_type` VARCHAR(64) DEFAULT NULL COMMENT 'Current crop type',
  `crop_variety` VARCHAR(64) DEFAULT NULL COMMENT 'Current crop variety',
  `crop_variety_groups_json` LONGTEXT DEFAULT NULL COMMENT 'Current crop-variety groups(JSON)',
  `province` VARCHAR(64) DEFAULT NULL COMMENT 'Province',
  `city` VARCHAR(64) DEFAULT NULL COMMENT 'City',
  `district` VARCHAR(64) DEFAULT NULL COMMENT 'District/County',
  `township` VARCHAR(64) DEFAULT NULL COMMENT 'Township',
  `formatted_address` VARCHAR(255) DEFAULT NULL COMMENT 'Standardized formatted address',
  `status` VARCHAR(32) NOT NULL COMMENT 'idle/sowing/growing/harvesting/fallow',
  `enabled` TINYINT(1) DEFAULT '1' COMMENT '1 enabled,0 disabled',
  `location_lat` DECIMAL(10,7) DEFAULT NULL,
  `location_lng` DECIMAL(10,7) DEFAULT NULL,
  `location_point` POINT DEFAULT NULL COMMENT 'Field center point(lng,lat)',
  `location_desc` VARCHAR(255) DEFAULT NULL,
  `cover_image_url` VARCHAR(512) DEFAULT NULL,
  `remark` VARCHAR(512) DEFAULT NULL,
  `sort_order` INT DEFAULT 0 COMMENT 'Display sort order',
  `deleted` TINYINT(1) DEFAULT '0',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_field_name` (`name`),
  INDEX `idx_field_province` (`province`),
  INDEX `idx_field_city` (`city`),
  INDEX `idx_field_district` (`district`),
  INDEX `idx_field_township` (`township`),
  INDEX `idx_field_crop_variety` (`crop_variety`),
  INDEX `idx_field_sort_order` (`sort_order`),
  INDEX `idx_field_status` (`status`),
  INDEX `idx_field_enabled` (`enabled`),
  INDEX `idx_field_location_lat_lng` (`location_lat`, `location_lng`),
  SPATIAL INDEX `sp_idx_field_location_point` (`location_point`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Field info';

SET @ddl_field_crop_variety = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `field` ADD COLUMN `crop_variety` VARCHAR(64) DEFAULT NULL COMMENT ''Current crop variety''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'field' AND COLUMN_NAME = 'crop_variety'
);
PREPARE stmt_field_crop_variety FROM @ddl_field_crop_variety;
EXECUTE stmt_field_crop_variety;
DEALLOCATE PREPARE stmt_field_crop_variety;

SET @ddl_field_crop_variety_groups_json = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `field` ADD COLUMN `crop_variety_groups_json` LONGTEXT DEFAULT NULL COMMENT ''Current crop-variety groups(JSON)''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'field' AND COLUMN_NAME = 'crop_variety_groups_json'
);
PREPARE stmt_field_crop_variety_groups_json FROM @ddl_field_crop_variety_groups_json;
EXECUTE stmt_field_crop_variety_groups_json;
DEALLOCATE PREPARE stmt_field_crop_variety_groups_json;

SET @ddl_field_province = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `field` ADD COLUMN `province` VARCHAR(64) DEFAULT NULL COMMENT ''Province''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'field' AND COLUMN_NAME = 'province'
);
PREPARE stmt_field_province FROM @ddl_field_province;
EXECUTE stmt_field_province;
DEALLOCATE PREPARE stmt_field_province;

SET @ddl_field_city = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `field` ADD COLUMN `city` VARCHAR(64) DEFAULT NULL COMMENT ''City''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'field' AND COLUMN_NAME = 'city'
);
PREPARE stmt_field_city FROM @ddl_field_city;
EXECUTE stmt_field_city;
DEALLOCATE PREPARE stmt_field_city;

SET @ddl_field_district = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `field` ADD COLUMN `district` VARCHAR(64) DEFAULT NULL COMMENT ''District/County''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'field' AND COLUMN_NAME = 'district'
);
PREPARE stmt_field_district FROM @ddl_field_district;
EXECUTE stmt_field_district;
DEALLOCATE PREPARE stmt_field_district;

SET @ddl_field_township = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `field` ADD COLUMN `township` VARCHAR(64) DEFAULT NULL COMMENT ''Township''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'field' AND COLUMN_NAME = 'township'
);
PREPARE stmt_field_township FROM @ddl_field_township;
EXECUTE stmt_field_township;
DEALLOCATE PREPARE stmt_field_township;

SET @ddl_field_formatted_address = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `field` ADD COLUMN `formatted_address` VARCHAR(255) DEFAULT NULL COMMENT ''Standardized formatted address''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'field' AND COLUMN_NAME = 'formatted_address'
);
PREPARE stmt_field_formatted_address FROM @ddl_field_formatted_address;
EXECUTE stmt_field_formatted_address;
DEALLOCATE PREPARE stmt_field_formatted_address;

SET @ddl_field_sort_order = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `field` ADD COLUMN `sort_order` INT DEFAULT 0 COMMENT ''Display sort order''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'field' AND COLUMN_NAME = 'sort_order'
);
PREPARE stmt_field_sort_order FROM @ddl_field_sort_order;
EXECUTE stmt_field_sort_order;
DEALLOCATE PREPARE stmt_field_sort_order;

SET @ddl_field_enabled = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `field` ADD COLUMN `enabled` TINYINT(1) DEFAULT ''1'' COMMENT ''1 enabled,0 disabled''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'field' AND COLUMN_NAME = 'enabled'
);
PREPARE stmt_field_enabled FROM @ddl_field_enabled;
EXECUTE stmt_field_enabled;
DEALLOCATE PREPARE stmt_field_enabled;

SET @ddl_field_location_point = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `field` ADD COLUMN `location_point` POINT DEFAULT NULL COMMENT ''Field center point(lng,lat)''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'field' AND COLUMN_NAME = 'location_point'
);
PREPARE stmt_field_location_point FROM @ddl_field_location_point;
EXECUTE stmt_field_location_point;
DEALLOCATE PREPARE stmt_field_location_point;

SET @ddl_idx_field_enabled = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `field` ADD INDEX `idx_field_enabled` (`enabled`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'field' AND INDEX_NAME = 'idx_field_enabled'
);
PREPARE stmt_idx_field_enabled FROM @ddl_idx_field_enabled;
EXECUTE stmt_idx_field_enabled;
DEALLOCATE PREPARE stmt_idx_field_enabled;

SET @ddl_idx_field_location_lat_lng = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `field` ADD INDEX `idx_field_location_lat_lng` (`location_lat`, `location_lng`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'field' AND INDEX_NAME = 'idx_field_location_lat_lng'
);
PREPARE stmt_idx_field_location_lat_lng FROM @ddl_idx_field_location_lat_lng;
EXECUTE stmt_idx_field_location_lat_lng;
DEALLOCATE PREPARE stmt_idx_field_location_lat_lng;

UPDATE `field` SET `sort_order` = 0 WHERE `sort_order` IS NULL;
UPDATE `field` SET `enabled` = 1 WHERE `enabled` IS NULL;
UPDATE `field` SET `formatted_address` = `location_desc` WHERE (`formatted_address` IS NULL OR TRIM(`formatted_address`) = '') AND `location_desc` IS NOT NULL AND TRIM(`location_desc`) <> '';
UPDATE `field`
SET `location_point` = POINT(`location_lng`, `location_lat`)
WHERE `location_lat` IS NOT NULL
  AND `location_lng` IS NOT NULL
  AND `location_lat` BETWEEN -90 AND 90
  AND `location_lng` BETWEEN -180 AND 180;
UPDATE `field`
SET `location_point` = NULL
WHERE `location_lat` IS NULL
   OR `location_lng` IS NULL
   OR `location_lat` NOT BETWEEN -90 AND 90
   OR `location_lng` NOT BETWEEN -180 AND 180;

SET @ddl_drop_field_soil_type = (
  SELECT IF(
    COUNT(*) = 0,
    'SELECT 1',
    'ALTER TABLE `field` DROP COLUMN `soil_type`'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'field' AND COLUMN_NAME = 'soil_type'
);
PREPARE stmt_drop_field_soil_type FROM @ddl_drop_field_soil_type;
EXECUTE stmt_drop_field_soil_type;
DEALLOCATE PREPARE stmt_drop_field_soil_type;

SET @ddl_drop_field_irrigation_type = (
  SELECT IF(
    COUNT(*) = 0,
    'SELECT 1',
    'ALTER TABLE `field` DROP COLUMN `irrigation_type`'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'field' AND COLUMN_NAME = 'irrigation_type'
);
PREPARE stmt_drop_field_irrigation_type FROM @ddl_drop_field_irrigation_type;
EXECUTE stmt_drop_field_irrigation_type;
DEALLOCATE PREPARE stmt_drop_field_irrigation_type;
