-- DaHe V2 database init - Seed module
USE `dahe_v2`;

CREATE TABLE IF NOT EXISTS `seed_batch` (
  `id` BIGINT NOT NULL COMMENT 'Primary key',
  `batch_code` VARCHAR(64) NOT NULL COMMENT 'Batch code',
  `crop_type` VARCHAR(64) DEFAULT NULL COMMENT 'Crop type',
  `variety_name` VARCHAR(128) NOT NULL COMMENT 'Variety name',
  `production_date` DATE DEFAULT NULL COMMENT 'Production date',
  `remark` VARCHAR(512) DEFAULT NULL COMMENT 'Remark',
  `enabled` TINYINT(1) DEFAULT '1' COMMENT '1 enabled,0 disabled',
  `form_config_id` BIGINT DEFAULT NULL COMMENT 'Dynamic form config id',
  `extra_json` LONGTEXT DEFAULT NULL COMMENT 'Dynamic fields value(JSON)',
  `deleted` TINYINT(1) DEFAULT '0' COMMENT 'Logical delete',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_seed_batch_code` (`batch_code`),
  INDEX `idx_seed_batch_crop` (`crop_type`),
  INDEX `idx_seed_batch_variety` (`variety_name`),
  INDEX `idx_seed_batch_enabled` (`enabled`),
  INDEX `idx_seed_batch_form_config` (`form_config_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Seed batch';

CREATE TABLE IF NOT EXISTS `seed_quality_test` (
  `id` BIGINT NOT NULL COMMENT 'Primary key',
  `batch_id` BIGINT NOT NULL COMMENT 'Seed batch id',
  `test_date` DATE NOT NULL COMMENT 'Test date',
  `sample_count` INT DEFAULT NULL COMMENT 'Sample count for germination test',
  `germination_count` INT DEFAULT NULL COMMENT 'Germinated seed count',
  `germination_rate` DECIMAL(5,2) DEFAULT NULL COMMENT 'Germination rate(%)',
  `moisture` DECIMAL(5,2) DEFAULT NULL COMMENT 'Moisture(%)',
  `purity` DECIMAL(5,2) DEFAULT NULL COMMENT 'Purity(%)',
  `cleanliness` DECIMAL(5,2) DEFAULT NULL COMMENT 'Cleanliness(%)',
  `tester_name` VARCHAR(64) DEFAULT NULL COMMENT 'Tester name',
  `remark` VARCHAR(512) DEFAULT NULL COMMENT 'Remark',
  `request_key` VARCHAR(64) DEFAULT NULL COMMENT 'Idempotency request key',
  `form_config_id` BIGINT DEFAULT NULL COMMENT 'Dynamic form config id',
  `extra_json` LONGTEXT DEFAULT NULL COMMENT 'Dynamic fields value(JSON)',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_seed_test_batch_request_key` (`batch_id`, `request_key`),
  INDEX `idx_seed_test_batch_id` (`batch_id`),
  INDEX `idx_seed_test_date` (`test_date`),
  INDEX `idx_seed_test_form_config` (`form_config_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Seed quality test';

CREATE TABLE IF NOT EXISTS `seed_quality_rule` (
  `id` BIGINT NOT NULL COMMENT 'Primary key',
  `fixed_sample_size` TINYINT(1) DEFAULT '1' COMMENT '1=fixed sample count, 0=input each test',
  `default_sample_size` INT DEFAULT '100' COMMENT 'Default sample count',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT 'Remark',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Seed quality rule';

SET @ddl_seed_test_sample_count = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `seed_quality_test` ADD COLUMN `sample_count` INT DEFAULT NULL COMMENT ''Sample count for germination test''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_quality_test' AND COLUMN_NAME = 'sample_count'
);
PREPARE stmt_seed_test_sample_count FROM @ddl_seed_test_sample_count;
EXECUTE stmt_seed_test_sample_count;
DEALLOCATE PREPARE stmt_seed_test_sample_count;

SET @ddl_seed_test_germination_count = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `seed_quality_test` ADD COLUMN `germination_count` INT DEFAULT NULL COMMENT ''Germinated seed count''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_quality_test' AND COLUMN_NAME = 'germination_count'
);
PREPARE stmt_seed_test_germination_count FROM @ddl_seed_test_germination_count;
EXECUTE stmt_seed_test_germination_count;
DEALLOCATE PREPARE stmt_seed_test_germination_count;

SET @ddl_seed_test_cleanliness = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `seed_quality_test` ADD COLUMN `cleanliness` DECIMAL(5,2) DEFAULT NULL COMMENT ''Cleanliness(%)''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_quality_test' AND COLUMN_NAME = 'cleanliness'
);
PREPARE stmt_seed_test_cleanliness FROM @ddl_seed_test_cleanliness;
EXECUTE stmt_seed_test_cleanliness;
DEALLOCATE PREPARE stmt_seed_test_cleanliness;

SET @ddl_drop_seed_test_type = (
  SELECT IF(
    COUNT(*) = 0,
    'SELECT 1',
    'ALTER TABLE `seed_quality_test` DROP COLUMN `test_type`'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_quality_test' AND COLUMN_NAME = 'test_type'
);
PREPARE stmt_drop_seed_test_type FROM @ddl_drop_seed_test_type;
EXECUTE stmt_drop_seed_test_type;
DEALLOCATE PREPARE stmt_drop_seed_test_type;

SET @ddl_drop_seed_test_conclusion = (
  SELECT IF(
    COUNT(*) = 0,
    'SELECT 1',
    'ALTER TABLE `seed_quality_test` DROP COLUMN `conclusion`'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_quality_test' AND COLUMN_NAME = 'conclusion'
);
PREPARE stmt_drop_seed_test_conclusion FROM @ddl_drop_seed_test_conclusion;
EXECUTE stmt_drop_seed_test_conclusion;
DEALLOCATE PREPARE stmt_drop_seed_test_conclusion;

SET @ddl_drop_seed_test_weight = (
  SELECT IF(
    COUNT(*) = 0,
    'SELECT 1',
    'ALTER TABLE `seed_quality_test` DROP COLUMN `thousand_grain_weight`'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_quality_test' AND COLUMN_NAME = 'thousand_grain_weight'
);
PREPARE stmt_drop_seed_test_weight FROM @ddl_drop_seed_test_weight;
EXECUTE stmt_drop_seed_test_weight;
DEALLOCATE PREPARE stmt_drop_seed_test_weight;

SET @ddl_seed_rule_fixed = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `seed_quality_rule` ADD COLUMN `fixed_sample_size` TINYINT(1) DEFAULT ''1''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_quality_rule' AND COLUMN_NAME = 'fixed_sample_size'
);
PREPARE stmt_seed_rule_fixed FROM @ddl_seed_rule_fixed;
EXECUTE stmt_seed_rule_fixed;
DEALLOCATE PREPARE stmt_seed_rule_fixed;

SET @ddl_seed_rule_default = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `seed_quality_rule` ADD COLUMN `default_sample_size` INT DEFAULT ''100''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_quality_rule' AND COLUMN_NAME = 'default_sample_size'
);
PREPARE stmt_seed_rule_default FROM @ddl_seed_rule_default;
EXECUTE stmt_seed_rule_default;
DEALLOCATE PREPARE stmt_seed_rule_default;

SET @ddl_seed_batch_form_config = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `seed_batch` ADD COLUMN `form_config_id` BIGINT DEFAULT NULL COMMENT ''Dynamic form config id''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_batch' AND COLUMN_NAME = 'form_config_id'
);
PREPARE stmt_seed_batch_form_config FROM @ddl_seed_batch_form_config;
EXECUTE stmt_seed_batch_form_config;
DEALLOCATE PREPARE stmt_seed_batch_form_config;

SET @ddl_seed_batch_crop_type = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `seed_batch` ADD COLUMN `crop_type` VARCHAR(64) DEFAULT NULL COMMENT ''Crop type''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_batch' AND COLUMN_NAME = 'crop_type'
);
PREPARE stmt_seed_batch_crop_type FROM @ddl_seed_batch_crop_type;
EXECUTE stmt_seed_batch_crop_type;
DEALLOCATE PREPARE stmt_seed_batch_crop_type;

SET @ddl_seed_batch_extra_json = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `seed_batch` ADD COLUMN `extra_json` LONGTEXT DEFAULT NULL COMMENT ''Dynamic fields value(JSON)''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_batch' AND COLUMN_NAME = 'extra_json'
);
PREPARE stmt_seed_batch_extra_json FROM @ddl_seed_batch_extra_json;
EXECUTE stmt_seed_batch_extra_json;
DEALLOCATE PREPARE stmt_seed_batch_extra_json;

SET @ddl_seed_batch_enabled = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `seed_batch` ADD COLUMN `enabled` TINYINT(1) DEFAULT ''1'' COMMENT ''1 enabled,0 disabled''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_batch' AND COLUMN_NAME = 'enabled'
);
PREPARE stmt_seed_batch_enabled FROM @ddl_seed_batch_enabled;
EXECUTE stmt_seed_batch_enabled;
DEALLOCATE PREPARE stmt_seed_batch_enabled;

SET @ddl_idx_seed_batch_form_config = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `seed_batch` ADD INDEX `idx_seed_batch_form_config` (`form_config_id`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_batch' AND INDEX_NAME = 'idx_seed_batch_form_config'
);
PREPARE stmt_idx_seed_batch_form_config FROM @ddl_idx_seed_batch_form_config;
EXECUTE stmt_idx_seed_batch_form_config;
DEALLOCATE PREPARE stmt_idx_seed_batch_form_config;

SET @ddl_idx_seed_batch_crop = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `seed_batch` ADD INDEX `idx_seed_batch_crop` (`crop_type`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_batch' AND INDEX_NAME = 'idx_seed_batch_crop'
);
PREPARE stmt_idx_seed_batch_crop FROM @ddl_idx_seed_batch_crop;
EXECUTE stmt_idx_seed_batch_crop;
DEALLOCATE PREPARE stmt_idx_seed_batch_crop;

SET @ddl_idx_seed_batch_enabled = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `seed_batch` ADD INDEX `idx_seed_batch_enabled` (`enabled`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_batch' AND INDEX_NAME = 'idx_seed_batch_enabled'
);
PREPARE stmt_idx_seed_batch_enabled FROM @ddl_idx_seed_batch_enabled;
EXECUTE stmt_idx_seed_batch_enabled;
DEALLOCATE PREPARE stmt_idx_seed_batch_enabled;

UPDATE `seed_batch` SET `enabled`=1 WHERE `enabled` IS NULL;

SET @ddl_seed_test_form_config = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `seed_quality_test` ADD COLUMN `form_config_id` BIGINT DEFAULT NULL COMMENT ''Dynamic form config id''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_quality_test' AND COLUMN_NAME = 'form_config_id'
);
PREPARE stmt_seed_test_form_config FROM @ddl_seed_test_form_config;
EXECUTE stmt_seed_test_form_config;
DEALLOCATE PREPARE stmt_seed_test_form_config;

SET @ddl_seed_test_extra_json = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `seed_quality_test` ADD COLUMN `extra_json` LONGTEXT DEFAULT NULL COMMENT ''Dynamic fields value(JSON)''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_quality_test' AND COLUMN_NAME = 'extra_json'
);
PREPARE stmt_seed_test_extra_json FROM @ddl_seed_test_extra_json;
EXECUTE stmt_seed_test_extra_json;
DEALLOCATE PREPARE stmt_seed_test_extra_json;

SET @ddl_seed_test_request_key = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `seed_quality_test` ADD COLUMN `request_key` VARCHAR(64) DEFAULT NULL COMMENT ''Idempotency request key''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_quality_test' AND COLUMN_NAME = 'request_key'
);
PREPARE stmt_seed_test_request_key FROM @ddl_seed_test_request_key;
EXECUTE stmt_seed_test_request_key;
DEALLOCATE PREPARE stmt_seed_test_request_key;

SET @ddl_idx_seed_test_form_config = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `seed_quality_test` ADD INDEX `idx_seed_test_form_config` (`form_config_id`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_quality_test' AND INDEX_NAME = 'idx_seed_test_form_config'
);
PREPARE stmt_idx_seed_test_form_config FROM @ddl_idx_seed_test_form_config;
EXECUTE stmt_idx_seed_test_form_config;
DEALLOCATE PREPARE stmt_idx_seed_test_form_config;

SET @ddl_uk_seed_test_batch_request_key = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `seed_quality_test` ADD UNIQUE KEY `uk_seed_test_batch_request_key` (`batch_id`,`request_key`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_quality_test' AND INDEX_NAME = 'uk_seed_test_batch_request_key'
);
PREPARE stmt_uk_seed_test_batch_request_key FROM @ddl_uk_seed_test_batch_request_key;
EXECUTE stmt_uk_seed_test_batch_request_key;
DEALLOCATE PREPARE stmt_uk_seed_test_batch_request_key;

INSERT INTO `seed_quality_rule` (`id`, `fixed_sample_size`, `default_sample_size`, `remark`)
VALUES (1, 1, 100, 'default')
ON DUPLICATE KEY UPDATE
  `fixed_sample_size` = VALUES(`fixed_sample_size`),
  `default_sample_size` = VALUES(`default_sample_size`),
  `remark` = VALUES(`remark`);

SET @ddl_drop_seed_batch_origin = (
  SELECT IF(
    COUNT(*) = 0,
    'SELECT 1',
    'ALTER TABLE `seed_batch` DROP COLUMN `origin`'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_batch' AND COLUMN_NAME = 'origin'
);
PREPARE stmt_drop_seed_batch_origin FROM @ddl_drop_seed_batch_origin;
EXECUTE stmt_drop_seed_batch_origin;
DEALLOCATE PREPARE stmt_drop_seed_batch_origin;

SET @ddl_drop_seed_batch_storage_location = (
  SELECT IF(
    COUNT(*) = 0,
    'SELECT 1',
    'ALTER TABLE `seed_batch` DROP COLUMN `storage_location`'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seed_batch' AND COLUMN_NAME = 'storage_location'
);
PREPARE stmt_drop_seed_batch_storage_location FROM @ddl_drop_seed_batch_storage_location;
EXECUTE stmt_drop_seed_batch_storage_location;
DEALLOCATE PREPARE stmt_drop_seed_batch_storage_location;
