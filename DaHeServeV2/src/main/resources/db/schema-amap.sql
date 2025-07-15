-- DaHe V2 database init - AMap audit & quota module
USE `dahe_v2`;

CREATE TABLE IF NOT EXISTS `amap_quota_config` (
  `id` BIGINT NOT NULL COMMENT 'Primary key',
  `record_date` DATE NOT NULL COMMENT 'Daily usage date',
  `daily_limit` INT NOT NULL DEFAULT '50000' COMMENT 'Daily API call limit',
  `alert_threshold` INT NOT NULL DEFAULT '80' COMMENT 'Alert threshold percentage',
  `used_count` INT NOT NULL DEFAULT '0' COMMENT 'Used call count in current day',
  `account_name` VARCHAR(64) DEFAULT NULL COMMENT 'Account name',
  `account_login` VARCHAR(96) DEFAULT NULL COMMENT 'AMap account login',
  `app_name` VARCHAR(96) DEFAULT NULL COMMENT 'AMap application name',
  `console_url` VARCHAR(255) DEFAULT NULL COMMENT 'AMap console url',
  `key_console_url` VARCHAR(255) DEFAULT NULL COMMENT 'AMap key console url',
  `app_key` VARCHAR(128) DEFAULT NULL COMMENT 'AMap app key',
  `app_key_status` VARCHAR(24) DEFAULT 'unknown' COMMENT 'Key status: unknown/valid/invalid',
  `app_key_bound_at` DATETIME DEFAULT NULL COMMENT 'Key bind timestamp',
  `app_key_last_check_at` DATETIME DEFAULT NULL COMMENT 'Key last verify timestamp',
  `app_key_last_check_message` VARCHAR(255) DEFAULT NULL COMMENT 'Key last verify message',
  `last_health_check_at` DATETIME DEFAULT NULL COMMENT 'Health check timestamp',
  `last_health_check_message` VARCHAR(255) DEFAULT NULL COMMENT 'Health check summary',
  `weather_monthly_limit` INT NOT NULL DEFAULT '20000' COMMENT 'Weather monthly limit',
  `location_monthly_limit` INT NOT NULL DEFAULT '20000' COMMENT 'Location monthly limit',
  `weather_daily_limit` INT NOT NULL DEFAULT '20000' COMMENT 'Weather daily limit',
  `weather_used_count` INT NOT NULL DEFAULT '0' COMMENT 'Weather used count',
  `location_daily_limit` INT NOT NULL DEFAULT '20000' COMMENT 'Location/City daily limit',
  `location_used_count` INT NOT NULL DEFAULT '0' COMMENT 'Location/City used count',
  `geocode_daily_limit` INT NOT NULL DEFAULT '20000' COMMENT 'Geocode daily limit',
  `geocode_used_count` INT NOT NULL DEFAULT '0' COMMENT 'Geocode used count',
  `city_daily_limit` INT NOT NULL DEFAULT '10000' COMMENT 'City query daily limit',
  `city_used_count` INT NOT NULL DEFAULT '0' COMMENT 'City query used count',
  `recharge_total` INT NOT NULL DEFAULT '0' COMMENT 'Recharge total',
  `weather_recharge_total` INT NOT NULL DEFAULT '0' COMMENT 'Weather recharge total',
  `location_recharge_total` INT NOT NULL DEFAULT '0' COMMENT 'Location recharge total',
  `geocode_recharge_total` INT NOT NULL DEFAULT '0' COMMENT 'Geocode recharge total',
  `city_recharge_total` INT NOT NULL DEFAULT '0' COMMENT 'City recharge total',
  `cache_redis_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Cache redis enabled',
  `cache_redis_key_prefix` VARCHAR(64) DEFAULT 'dahe:v2:amap:cache:' COMMENT 'Cache redis key prefix',
  `cache_region_ttl_minutes` INT NOT NULL DEFAULT 720 COMMENT 'Region cache ttl minutes',
  `cache_region_stale_minutes` INT NOT NULL DEFAULT 1440 COMMENT 'Region stale cache minutes',
  `cache_weather_ttl_minutes` INT NOT NULL DEFAULT 60 COMMENT 'Weather cache ttl minutes',
  `cache_local_region_max_entries` INT NOT NULL DEFAULT 256 COMMENT 'Local region cache max entries',
  `cache_local_weather_max_entries` INT NOT NULL DEFAULT 256 COMMENT 'Local weather cache max entries',
  `audit_auto_purge_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Audit auto purge enabled',
  `audit_retain_days` INT NOT NULL DEFAULT 90 COMMENT 'Audit retain days',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT 'Remark',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AMap daily quota config';

CREATE TABLE IF NOT EXISTS `amap_api_audit` (
  `id` BIGINT NOT NULL COMMENT 'Primary key',
  `record_date` DATE NOT NULL COMMENT 'Record date',
  `user_id` BIGINT DEFAULT NULL COMMENT 'Operator user id',
  `operator_name` VARCHAR(64) DEFAULT NULL COMMENT 'Operator name',
  `biz_scene` VARCHAR(64) DEFAULT NULL COMMENT 'Business scene',
  `api_type` VARCHAR(16) DEFAULT NULL COMMENT 'Billing api type: weather/location',
  `api_path` VARCHAR(128) DEFAULT NULL COMMENT 'AMap API path',
  `request_source` VARCHAR(64) DEFAULT NULL COMMENT 'Request source',
  `success_flag` TINYINT(1) DEFAULT '1' COMMENT '1=success,0=failed',
  `cost_ms` INT DEFAULT NULL COMMENT 'Request cost time(ms)',
  `error_message` VARCHAR(255) DEFAULT NULL COMMENT 'Error details',
  `deleted` TINYINT(1) DEFAULT '0',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_amap_audit_date` (`record_date`),
  INDEX `idx_amap_audit_scene` (`biz_scene`),
  INDEX `idx_amap_audit_type_date` (`api_type`,`record_date`),
  INDEX `idx_amap_audit_success` (`success_flag`),
  INDEX `idx_amap_audit_created` (`created_at`),
  INDEX `idx_amap_audit_deleted` (`deleted`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AMap API audit log';

CREATE TABLE IF NOT EXISTS `amap_usage_daily` (
  `id` BIGINT NOT NULL COMMENT 'Primary key',
  `record_date` DATE NOT NULL COMMENT 'Usage date',
  `api_type` VARCHAR(16) NOT NULL COMMENT 'Billing api type: weather/location',
  `remote_count` INT NOT NULL DEFAULT '0' COMMENT 'Official remote usage count',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_amap_usage_day_type` (`record_date`,`api_type`),
  INDEX `idx_amap_usage_type_date` (`api_type`,`record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AMap official remote usage daily snapshot';

CREATE TABLE IF NOT EXISTS `amap_usage_monthly` (
  `id` BIGINT NOT NULL COMMENT 'Primary key',
  `record_month` DATE NOT NULL COMMENT 'Usage month(first day of month)',
  `api_type` VARCHAR(16) NOT NULL COMMENT 'Billing api type: weather/location',
  `remote_count` INT NOT NULL DEFAULT '0' COMMENT 'Official remote usage count',
  `warning_sent` TINYINT(1) NOT NULL DEFAULT '0' COMMENT 'Quota warning sent flag',
  `warning_sent_at` DATETIME DEFAULT NULL COMMENT 'Quota warning sent time',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_amap_usage_month_type` (`record_month`,`api_type`),
  INDEX `idx_amap_usage_month_type` (`api_type`,`record_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AMap official remote usage monthly snapshot';

SET @ddl_amap_audit_deleted = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_api_audit` ADD COLUMN `deleted` TINYINT(1) DEFAULT ''0''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_api_audit' AND COLUMN_NAME = 'deleted'
);
PREPARE stmt_amap_audit_deleted FROM @ddl_amap_audit_deleted;
EXECUTE stmt_amap_audit_deleted;
DEALLOCATE PREPARE stmt_amap_audit_deleted;

SET @idx_amap_audit_deleted = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_api_audit` ADD INDEX `idx_amap_audit_deleted` (`deleted`,`created_at`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_api_audit' AND INDEX_NAME = 'idx_amap_audit_deleted'
);
PREPARE stmt_idx_amap_audit_deleted FROM @idx_amap_audit_deleted;
EXECUTE stmt_idx_amap_audit_deleted;
DEALLOCATE PREPARE stmt_idx_amap_audit_deleted;

UPDATE `amap_api_audit`
SET `deleted` = 0
WHERE `deleted` IS NULL;

INSERT INTO `amap_usage_daily` (`id`,`record_date`,`api_type`,`remote_count`,`created_at`,`updated_at`)
SELECT UUID_SHORT(), `record_date`, 'weather', GREATEST(IFNULL(`weather_used_count`,0),0), NOW(), NOW()
FROM `amap_quota_config`
WHERE IFNULL(`weather_used_count`,0) > 0
ON DUPLICATE KEY UPDATE
  `remote_count` = GREATEST(`remote_count`, VALUES(`remote_count`)),
  `updated_at` = NOW();

INSERT INTO `amap_usage_monthly` (`id`,`record_month`,`api_type`,`remote_count`,`warning_sent`,`warning_sent_at`,`created_at`,`updated_at`)
SELECT UUID_SHORT(), DATE_FORMAT(COALESCE(`record_date`, CURDATE()), '%Y-%m-01'), 'weather', GREATEST(IFNULL(`weather_used_count`,0),0), 0, NULL, NOW(), NOW()
FROM `amap_quota_config`
WHERE IFNULL(`weather_used_count`,0) > 0
ON DUPLICATE KEY UPDATE
  `remote_count` = GREATEST(`remote_count`, VALUES(`remote_count`)),
  `updated_at` = NOW();

INSERT INTO `amap_usage_monthly` (`id`,`record_month`,`api_type`,`remote_count`,`warning_sent`,`warning_sent_at`,`created_at`,`updated_at`)
SELECT UUID_SHORT(), DATE_FORMAT(COALESCE(`record_date`, CURDATE()), '%Y-%m-01'), 'location', GREATEST(IFNULL(`location_used_count`, IFNULL(`geocode_used_count`,0) + IFNULL(`city_used_count`,0)),0), 0, NULL, NOW(), NOW()
FROM `amap_quota_config`
WHERE GREATEST(IFNULL(`location_used_count`, IFNULL(`geocode_used_count`,0) + IFNULL(`city_used_count`,0)),0) > 0
ON DUPLICATE KEY UPDATE
  `remote_count` = GREATEST(`remote_count`, VALUES(`remote_count`)),
  `updated_at` = NOW();

DELETE FROM `amap_usage_daily`
WHERE `record_date` < DATE_SUB(CURDATE(), INTERVAL 6 DAY);

INSERT INTO `amap_usage_daily` (`id`,`record_date`,`api_type`,`remote_count`,`created_at`,`updated_at`)
SELECT UUID_SHORT(), `record_date`, 'location', GREATEST(IFNULL(`location_used_count`, IFNULL(`geocode_used_count`,0) + IFNULL(`city_used_count`,0)),0), NOW(), NOW()
FROM `amap_quota_config`
WHERE GREATEST(IFNULL(`location_used_count`, IFNULL(`geocode_used_count`,0) + IFNULL(`city_used_count`,0)),0) > 0
ON DUPLICATE KEY UPDATE
  `remote_count` = GREATEST(`remote_count`, VALUES(`remote_count`)),
  `updated_at` = NOW();

SET @ddl_amap_account_name = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `account_name` VARCHAR(64) DEFAULT NULL COMMENT ''Account name''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'account_name'
);
PREPARE stmt_amap_account_name FROM @ddl_amap_account_name;
EXECUTE stmt_amap_account_name;
DEALLOCATE PREPARE stmt_amap_account_name;

SET @ddl_amap_account_login = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `account_login` VARCHAR(96) DEFAULT NULL COMMENT ''AMap account login''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'account_login'
);
PREPARE stmt_amap_account_login FROM @ddl_amap_account_login;
EXECUTE stmt_amap_account_login;
DEALLOCATE PREPARE stmt_amap_account_login;

SET @ddl_amap_app_name = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `app_name` VARCHAR(96) DEFAULT NULL COMMENT ''AMap application name''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'app_name'
);
PREPARE stmt_amap_app_name FROM @ddl_amap_app_name;
EXECUTE stmt_amap_app_name;
DEALLOCATE PREPARE stmt_amap_app_name;

SET @ddl_amap_console_url = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `console_url` VARCHAR(255) DEFAULT NULL COMMENT ''AMap console url''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'console_url'
);
PREPARE stmt_amap_console_url FROM @ddl_amap_console_url;
EXECUTE stmt_amap_console_url;
DEALLOCATE PREPARE stmt_amap_console_url;

SET @ddl_amap_key_console_url = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `key_console_url` VARCHAR(255) DEFAULT NULL COMMENT ''AMap key console url''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'key_console_url'
);
PREPARE stmt_amap_key_console_url FROM @ddl_amap_key_console_url;
EXECUTE stmt_amap_key_console_url;
DEALLOCATE PREPARE stmt_amap_key_console_url;

SET @ddl_amap_app_key = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `app_key` VARCHAR(128) DEFAULT NULL COMMENT ''AMap app key''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'app_key'
);
PREPARE stmt_amap_app_key FROM @ddl_amap_app_key;
EXECUTE stmt_amap_app_key;
DEALLOCATE PREPARE stmt_amap_app_key;

SET @ddl_amap_app_key_status = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `app_key_status` VARCHAR(24) DEFAULT ''unknown'' COMMENT ''Key status: unknown/valid/invalid''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'app_key_status'
);
PREPARE stmt_amap_app_key_status FROM @ddl_amap_app_key_status;
EXECUTE stmt_amap_app_key_status;
DEALLOCATE PREPARE stmt_amap_app_key_status;

SET @ddl_amap_app_key_bound_at = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `app_key_bound_at` DATETIME DEFAULT NULL COMMENT ''Key bind timestamp''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'app_key_bound_at'
);
PREPARE stmt_amap_app_key_bound_at FROM @ddl_amap_app_key_bound_at;
EXECUTE stmt_amap_app_key_bound_at;
DEALLOCATE PREPARE stmt_amap_app_key_bound_at;

SET @ddl_amap_app_key_last_check_at = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `app_key_last_check_at` DATETIME DEFAULT NULL COMMENT ''Key last verify timestamp''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'app_key_last_check_at'
);
PREPARE stmt_amap_app_key_last_check_at FROM @ddl_amap_app_key_last_check_at;
EXECUTE stmt_amap_app_key_last_check_at;
DEALLOCATE PREPARE stmt_amap_app_key_last_check_at;

SET @ddl_amap_app_key_last_check_message = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `app_key_last_check_message` VARCHAR(255) DEFAULT NULL COMMENT ''Key last verify message''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'app_key_last_check_message'
);
PREPARE stmt_amap_app_key_last_check_message FROM @ddl_amap_app_key_last_check_message;
EXECUTE stmt_amap_app_key_last_check_message;
DEALLOCATE PREPARE stmt_amap_app_key_last_check_message;

SET @ddl_amap_last_health_check_at = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `last_health_check_at` DATETIME DEFAULT NULL COMMENT ''Health check timestamp''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'last_health_check_at'
);
PREPARE stmt_amap_last_health_check_at FROM @ddl_amap_last_health_check_at;
EXECUTE stmt_amap_last_health_check_at;
DEALLOCATE PREPARE stmt_amap_last_health_check_at;

SET @ddl_amap_last_health_check_message = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `last_health_check_message` VARCHAR(255) DEFAULT NULL COMMENT ''Health check summary''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'last_health_check_message'
);
PREPARE stmt_amap_last_health_check_message FROM @ddl_amap_last_health_check_message;
EXECUTE stmt_amap_last_health_check_message;
DEALLOCATE PREPARE stmt_amap_last_health_check_message;

SET @ddl_amap_weather_monthly_limit = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `weather_monthly_limit` INT NOT NULL DEFAULT ''20000'' COMMENT ''Weather monthly limit''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'weather_monthly_limit'
);
PREPARE stmt_amap_weather_monthly_limit FROM @ddl_amap_weather_monthly_limit;
EXECUTE stmt_amap_weather_monthly_limit;
DEALLOCATE PREPARE stmt_amap_weather_monthly_limit;

SET @ddl_amap_weather_daily_limit = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `weather_daily_limit` INT NOT NULL DEFAULT ''20000'' COMMENT ''Weather daily limit''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'weather_daily_limit'
);
PREPARE stmt_amap_weather_daily_limit FROM @ddl_amap_weather_daily_limit;
EXECUTE stmt_amap_weather_daily_limit;
DEALLOCATE PREPARE stmt_amap_weather_daily_limit;

SET @ddl_amap_weather_used_count = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `weather_used_count` INT NOT NULL DEFAULT ''0'' COMMENT ''Weather used count''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'weather_used_count'
);
PREPARE stmt_amap_weather_used_count FROM @ddl_amap_weather_used_count;
EXECUTE stmt_amap_weather_used_count;
DEALLOCATE PREPARE stmt_amap_weather_used_count;

SET @ddl_amap_geocode_daily_limit = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `geocode_daily_limit` INT NOT NULL DEFAULT ''20000'' COMMENT ''Geocode daily limit''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'geocode_daily_limit'
);
PREPARE stmt_amap_geocode_daily_limit FROM @ddl_amap_geocode_daily_limit;
EXECUTE stmt_amap_geocode_daily_limit;
DEALLOCATE PREPARE stmt_amap_geocode_daily_limit;

SET @ddl_amap_geocode_used_count = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `geocode_used_count` INT NOT NULL DEFAULT ''0'' COMMENT ''Geocode used count''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'geocode_used_count'
);
PREPARE stmt_amap_geocode_used_count FROM @ddl_amap_geocode_used_count;
EXECUTE stmt_amap_geocode_used_count;
DEALLOCATE PREPARE stmt_amap_geocode_used_count;

SET @ddl_amap_city_daily_limit = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `city_daily_limit` INT NOT NULL DEFAULT ''10000'' COMMENT ''City query daily limit''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'city_daily_limit'
);
PREPARE stmt_amap_city_daily_limit FROM @ddl_amap_city_daily_limit;
EXECUTE stmt_amap_city_daily_limit;
DEALLOCATE PREPARE stmt_amap_city_daily_limit;

SET @ddl_amap_city_used_count = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `city_used_count` INT NOT NULL DEFAULT ''0'' COMMENT ''City query used count''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'city_used_count'
);
PREPARE stmt_amap_city_used_count FROM @ddl_amap_city_used_count;
EXECUTE stmt_amap_city_used_count;
DEALLOCATE PREPARE stmt_amap_city_used_count;

SET @ddl_amap_recharge_total = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `recharge_total` INT NOT NULL DEFAULT ''0'' COMMENT ''Recharge total''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'recharge_total'
);
PREPARE stmt_amap_recharge_total FROM @ddl_amap_recharge_total;
EXECUTE stmt_amap_recharge_total;
DEALLOCATE PREPARE stmt_amap_recharge_total;

SET @ddl_amap_weather_recharge_total = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `weather_recharge_total` INT NOT NULL DEFAULT ''0'' COMMENT ''Weather recharge total''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'weather_recharge_total'
);
PREPARE stmt_amap_weather_recharge_total FROM @ddl_amap_weather_recharge_total;
EXECUTE stmt_amap_weather_recharge_total;
DEALLOCATE PREPARE stmt_amap_weather_recharge_total;

SET @ddl_amap_geocode_recharge_total = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `geocode_recharge_total` INT NOT NULL DEFAULT ''0'' COMMENT ''Geocode recharge total''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'geocode_recharge_total'
);
PREPARE stmt_amap_geocode_recharge_total FROM @ddl_amap_geocode_recharge_total;
EXECUTE stmt_amap_geocode_recharge_total;
DEALLOCATE PREPARE stmt_amap_geocode_recharge_total;

SET @ddl_amap_city_recharge_total = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `city_recharge_total` INT NOT NULL DEFAULT ''0'' COMMENT ''City recharge total''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'city_recharge_total'
);
PREPARE stmt_amap_city_recharge_total FROM @ddl_amap_city_recharge_total;
EXECUTE stmt_amap_city_recharge_total;
DEALLOCATE PREPARE stmt_amap_city_recharge_total;

SET @ddl_amap_location_monthly_limit = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `location_monthly_limit` INT NOT NULL DEFAULT ''20000'' COMMENT ''Location monthly limit''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'location_monthly_limit'
);
PREPARE stmt_amap_location_monthly_limit FROM @ddl_amap_location_monthly_limit;
EXECUTE stmt_amap_location_monthly_limit;
DEALLOCATE PREPARE stmt_amap_location_monthly_limit;

SET @ddl_amap_location_daily_limit = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `location_daily_limit` INT NOT NULL DEFAULT ''20000'' COMMENT ''Location/City daily limit''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'location_daily_limit'
);
PREPARE stmt_amap_location_daily_limit FROM @ddl_amap_location_daily_limit;
EXECUTE stmt_amap_location_daily_limit;
DEALLOCATE PREPARE stmt_amap_location_daily_limit;

SET @ddl_amap_location_used_count = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `location_used_count` INT NOT NULL DEFAULT ''0'' COMMENT ''Location/City used count''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'location_used_count'
);
PREPARE stmt_amap_location_used_count FROM @ddl_amap_location_used_count;
EXECUTE stmt_amap_location_used_count;
DEALLOCATE PREPARE stmt_amap_location_used_count;

SET @ddl_amap_location_recharge_total = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `location_recharge_total` INT NOT NULL DEFAULT ''0'' COMMENT ''Location recharge total''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'location_recharge_total'
);
PREPARE stmt_amap_location_recharge_total FROM @ddl_amap_location_recharge_total;
EXECUTE stmt_amap_location_recharge_total;
DEALLOCATE PREPARE stmt_amap_location_recharge_total;

SET @ddl_amap_cache_redis_enabled = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `cache_redis_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''Cache redis enabled''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'cache_redis_enabled'
);
PREPARE stmt_amap_cache_redis_enabled FROM @ddl_amap_cache_redis_enabled;
EXECUTE stmt_amap_cache_redis_enabled;
DEALLOCATE PREPARE stmt_amap_cache_redis_enabled;

SET @ddl_amap_cache_redis_key_prefix = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `cache_redis_key_prefix` VARCHAR(64) DEFAULT ''dahe:v2:amap:cache:'' COMMENT ''Cache redis key prefix''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'cache_redis_key_prefix'
);
PREPARE stmt_amap_cache_redis_key_prefix FROM @ddl_amap_cache_redis_key_prefix;
EXECUTE stmt_amap_cache_redis_key_prefix;
DEALLOCATE PREPARE stmt_amap_cache_redis_key_prefix;

SET @ddl_amap_cache_region_ttl = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `cache_region_ttl_minutes` INT NOT NULL DEFAULT 720 COMMENT ''Region cache ttl minutes''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'cache_region_ttl_minutes'
);
PREPARE stmt_amap_cache_region_ttl FROM @ddl_amap_cache_region_ttl;
EXECUTE stmt_amap_cache_region_ttl;
DEALLOCATE PREPARE stmt_amap_cache_region_ttl;

SET @ddl_amap_cache_region_stale = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `cache_region_stale_minutes` INT NOT NULL DEFAULT 1440 COMMENT ''Region stale cache minutes''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'cache_region_stale_minutes'
);
PREPARE stmt_amap_cache_region_stale FROM @ddl_amap_cache_region_stale;
EXECUTE stmt_amap_cache_region_stale;
DEALLOCATE PREPARE stmt_amap_cache_region_stale;

SET @ddl_amap_cache_weather_ttl = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `cache_weather_ttl_minutes` INT NOT NULL DEFAULT 60 COMMENT ''Weather cache ttl minutes''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'cache_weather_ttl_minutes'
);
PREPARE stmt_amap_cache_weather_ttl FROM @ddl_amap_cache_weather_ttl;
EXECUTE stmt_amap_cache_weather_ttl;
DEALLOCATE PREPARE stmt_amap_cache_weather_ttl;

SET @ddl_amap_cache_local_region = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `cache_local_region_max_entries` INT NOT NULL DEFAULT 256 COMMENT ''Local region cache max entries''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'cache_local_region_max_entries'
);
PREPARE stmt_amap_cache_local_region FROM @ddl_amap_cache_local_region;
EXECUTE stmt_amap_cache_local_region;
DEALLOCATE PREPARE stmt_amap_cache_local_region;

SET @ddl_amap_cache_local_weather = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `cache_local_weather_max_entries` INT NOT NULL DEFAULT 256 COMMENT ''Local weather cache max entries''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'cache_local_weather_max_entries'
);
PREPARE stmt_amap_cache_local_weather FROM @ddl_amap_cache_local_weather;
EXECUTE stmt_amap_cache_local_weather;
DEALLOCATE PREPARE stmt_amap_cache_local_weather;

SET @ddl_amap_audit_auto_purge = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `audit_auto_purge_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''Audit auto purge enabled''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'audit_auto_purge_enabled'
);
PREPARE stmt_amap_audit_auto_purge FROM @ddl_amap_audit_auto_purge;
EXECUTE stmt_amap_audit_auto_purge;
DEALLOCATE PREPARE stmt_amap_audit_auto_purge;

SET @ddl_amap_audit_retain_days = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_quota_config` ADD COLUMN `audit_retain_days` INT NOT NULL DEFAULT 90 COMMENT ''Audit retain days''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_quota_config' AND COLUMN_NAME = 'audit_retain_days'
);
PREPARE stmt_amap_audit_retain_days FROM @ddl_amap_audit_retain_days;
EXECUTE stmt_amap_audit_retain_days;
DEALLOCATE PREPARE stmt_amap_audit_retain_days;

SET @ddl_amap_api_type = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_api_audit` ADD COLUMN `api_type` VARCHAR(16) DEFAULT NULL COMMENT ''Billing api type: weather/location''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_api_audit' AND COLUMN_NAME = 'api_type'
);
PREPARE stmt_amap_api_type FROM @ddl_amap_api_type;
EXECUTE stmt_amap_api_type;
DEALLOCATE PREPARE stmt_amap_api_type;

SET @idx_amap_audit_type_date = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `amap_api_audit` ADD INDEX `idx_amap_audit_type_date` (`api_type`,`record_date`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amap_api_audit' AND INDEX_NAME = 'idx_amap_audit_type_date'
);
PREPARE stmt_idx_amap_audit_type_date FROM @idx_amap_audit_type_date;
EXECUTE stmt_idx_amap_audit_type_date;
DEALLOCATE PREPARE stmt_idx_amap_audit_type_date;

INSERT INTO `amap_quota_config` (`id`, `record_date`, `daily_limit`, `alert_threshold`, `used_count`, `app_key_status`, `weather_monthly_limit`, `location_monthly_limit`, `weather_daily_limit`, `weather_used_count`, `location_daily_limit`, `location_used_count`, `geocode_daily_limit`, `geocode_used_count`, `city_daily_limit`, `city_used_count`, `recharge_total`, `weather_recharge_total`, `location_recharge_total`, `geocode_recharge_total`, `city_recharge_total`, `cache_redis_enabled`, `cache_redis_key_prefix`, `cache_region_ttl_minutes`, `cache_region_stale_minutes`, `cache_weather_ttl_minutes`, `cache_local_region_max_entries`, `cache_local_weather_max_entries`, `audit_auto_purge_enabled`, `audit_retain_days`, `remark`)
VALUES (1, CURDATE(), 40000, 80, 0, 'unknown', 20000, 20000, 20000, 0, 20000, 0, 20000, 0, 20000, 0, 0, 0, 0, 0, 0, 1, 'dahe:v2:amap:cache:', 720, 1440, 60, 256, 256, 1, 90, 'default')
ON DUPLICATE KEY UPDATE
  `record_date` = VALUES(`record_date`),
  `daily_limit` = VALUES(`daily_limit`),
  `alert_threshold` = VALUES(`alert_threshold`),
  `remark` = VALUES(`remark`);

UPDATE `amap_quota_config`
SET `console_url` = 'https://console.amap.com/dev'
WHERE (`console_url` IS NULL OR TRIM(`console_url`) = '');

UPDATE `amap_quota_config`
SET `key_console_url` = 'https://console.amap.com/dev/key/app'
WHERE (`key_console_url` IS NULL OR TRIM(`key_console_url`) = '');

UPDATE `amap_quota_config`
SET `cache_redis_enabled` = 1
WHERE (`cache_redis_enabled` IS NULL);

UPDATE `amap_quota_config`
SET `cache_redis_key_prefix` = 'dahe:v2:amap:cache:'
WHERE (`cache_redis_key_prefix` IS NULL OR TRIM(`cache_redis_key_prefix`) = '');

UPDATE `amap_quota_config`
SET `cache_region_ttl_minutes` = 720
WHERE (`cache_region_ttl_minutes` IS NULL OR `cache_region_ttl_minutes` <= 0);

UPDATE `amap_quota_config`
SET `cache_region_stale_minutes` = 1440
WHERE (`cache_region_stale_minutes` IS NULL OR `cache_region_stale_minutes` <= 0);

UPDATE `amap_quota_config`
SET `cache_weather_ttl_minutes` = 60
WHERE (`cache_weather_ttl_minutes` IS NULL OR `cache_weather_ttl_minutes` <= 0);

UPDATE `amap_quota_config`
SET `cache_local_region_max_entries` = 256
WHERE (`cache_local_region_max_entries` IS NULL OR `cache_local_region_max_entries` < 32);

UPDATE `amap_quota_config`
SET `cache_local_weather_max_entries` = 256
WHERE (`cache_local_weather_max_entries` IS NULL OR `cache_local_weather_max_entries` < 32);

UPDATE `amap_quota_config`
SET `audit_auto_purge_enabled` = 1
WHERE (`audit_auto_purge_enabled` IS NULL);

UPDATE `amap_quota_config`
SET `audit_retain_days` = 90
WHERE (`audit_retain_days` IS NULL OR `audit_retain_days` < 7);

UPDATE `amap_quota_config`
SET `location_daily_limit` = GREATEST(IFNULL(`location_daily_limit`, -1), IFNULL(`geocode_daily_limit`, -1), IFNULL(`city_daily_limit`, -1), 20000);

UPDATE `amap_quota_config`
SET `weather_monthly_limit` = GREATEST(IFNULL(`weather_monthly_limit`, -1), IFNULL(`weather_daily_limit`, -1), 20000);

UPDATE `amap_quota_config`
SET `location_monthly_limit` = GREATEST(IFNULL(`location_monthly_limit`, -1), IFNULL(`location_daily_limit`, -1), IFNULL(`geocode_daily_limit`, -1), IFNULL(`city_daily_limit`, -1), 20000);

UPDATE `amap_quota_config`
SET `geocode_daily_limit` = `location_daily_limit`,
    `city_daily_limit` = `location_daily_limit`;

UPDATE `amap_quota_config`
SET `location_used_count` = GREATEST(IFNULL(`location_used_count`, -1), IFNULL(`geocode_used_count`, 0) + IFNULL(`city_used_count`, 0), 0);

UPDATE `amap_quota_config`
SET `geocode_used_count` = `location_used_count`,
    `city_used_count` = 0;

UPDATE `amap_quota_config`
SET `location_recharge_total` = GREATEST(IFNULL(`location_recharge_total`, -1), IFNULL(`geocode_recharge_total`, 0), IFNULL(`city_recharge_total`, 0), 0);

UPDATE `amap_quota_config`
SET `geocode_recharge_total` = `location_recharge_total`,
    `city_recharge_total` = 0;

UPDATE `amap_quota_config`
SET `used_count` = IFNULL(`weather_used_count`, 0) + IFNULL(`location_used_count`, 0);

UPDATE `amap_quota_config`
SET `daily_limit` = IFNULL(`weather_monthly_limit`, IFNULL(`weather_daily_limit`, 0)) + IFNULL(`location_monthly_limit`, IFNULL(`location_daily_limit`, 0)),
    `weather_daily_limit` = IFNULL(`weather_monthly_limit`, IFNULL(`weather_daily_limit`, 0)),
    `location_daily_limit` = IFNULL(`location_monthly_limit`, IFNULL(`location_daily_limit`, 0)),
    `geocode_daily_limit` = IFNULL(`location_monthly_limit`, IFNULL(`geocode_daily_limit`, 0)),
    `city_daily_limit` = IFNULL(`location_monthly_limit`, IFNULL(`city_daily_limit`, 0));

UPDATE `amap_api_audit`
SET `api_type` = 'weather'
WHERE (`api_type` IS NULL OR TRIM(`api_type`) = '')
  AND (LOWER(COALESCE(`biz_scene`, '')) LIKE '%weather%' OR LOWER(COALESCE(`api_path`, '')) LIKE '%weather%');

UPDATE `amap_api_audit`
SET `api_type` = 'location'
WHERE (`api_type` IS NULL OR TRIM(`api_type`) = '');
