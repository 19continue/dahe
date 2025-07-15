-- 单表维护脚本：token_session
-- 用途：会话表结构初始化与增量兼容（支持存量库补齐字段/索引）
USE `dahe_v2`;

CREATE TABLE IF NOT EXISTS `token_session` (
  `id` BIGINT NOT NULL COMMENT 'Primary key',
  `user_id` BIGINT NOT NULL,
  `user_type` VARCHAR(16) DEFAULT NULL COMMENT 'admin/miniapp',
  `login_scene` VARCHAR(64) DEFAULT NULL COMMENT 'login scene code',
  `device_id` VARCHAR(64) DEFAULT NULL COMMENT 'frontend device id',
  `device_name` VARCHAR(128) DEFAULT NULL COMMENT 'frontend device name',
  `client_ip` VARCHAR(64) DEFAULT NULL COMMENT 'client ip',
  `user_agent` VARCHAR(512) DEFAULT NULL COMMENT 'client user-agent',
  `access_token` VARCHAR(128) NOT NULL,
  `expires_at` DATETIME NOT NULL,
  `status` TINYINT(1) DEFAULT '1' COMMENT '1 active, 0 invalid',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token_access_token` (`access_token`),
  INDEX `idx_token_user` (`user_id`),
  INDEX `idx_token_user_type` (`user_type`),
  INDEX `idx_token_scene` (`login_scene`),
  INDEX `idx_token_device_id` (`device_id`),
  INDEX `idx_token_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Access token sessions';

SET @ddl_token_user_type = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `token_session` ADD COLUMN `user_type` VARCHAR(16) DEFAULT NULL COMMENT ''admin/miniapp''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'token_session' AND COLUMN_NAME = 'user_type'
);
PREPARE stmt_token_user_type FROM @ddl_token_user_type;
EXECUTE stmt_token_user_type;
DEALLOCATE PREPARE stmt_token_user_type;

SET @ddl_token_login_scene = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `token_session` ADD COLUMN `login_scene` VARCHAR(64) DEFAULT NULL COMMENT ''login scene code''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'token_session' AND COLUMN_NAME = 'login_scene'
);
PREPARE stmt_token_login_scene FROM @ddl_token_login_scene;
EXECUTE stmt_token_login_scene;
DEALLOCATE PREPARE stmt_token_login_scene;

SET @ddl_token_device_id = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `token_session` ADD COLUMN `device_id` VARCHAR(64) DEFAULT NULL COMMENT ''frontend device id''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'token_session' AND COLUMN_NAME = 'device_id'
);
PREPARE stmt_token_device_id FROM @ddl_token_device_id;
EXECUTE stmt_token_device_id;
DEALLOCATE PREPARE stmt_token_device_id;

SET @ddl_token_device_name = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `token_session` ADD COLUMN `device_name` VARCHAR(128) DEFAULT NULL COMMENT ''frontend device name''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'token_session' AND COLUMN_NAME = 'device_name'
);
PREPARE stmt_token_device_name FROM @ddl_token_device_name;
EXECUTE stmt_token_device_name;
DEALLOCATE PREPARE stmt_token_device_name;

SET @ddl_token_client_ip = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `token_session` ADD COLUMN `client_ip` VARCHAR(64) DEFAULT NULL COMMENT ''client ip''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'token_session' AND COLUMN_NAME = 'client_ip'
);
PREPARE stmt_token_client_ip FROM @ddl_token_client_ip;
EXECUTE stmt_token_client_ip;
DEALLOCATE PREPARE stmt_token_client_ip;

SET @ddl_token_user_agent = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `token_session` ADD COLUMN `user_agent` VARCHAR(512) DEFAULT NULL COMMENT ''client user-agent''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'token_session' AND COLUMN_NAME = 'user_agent'
);
PREPARE stmt_token_user_agent FROM @ddl_token_user_agent;
EXECUTE stmt_token_user_agent;
DEALLOCATE PREPARE stmt_token_user_agent;

SET @ddl_token_idx_user = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `token_session` ADD INDEX `idx_token_user` (`user_id`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'token_session' AND INDEX_NAME = 'idx_token_user'
);
PREPARE stmt_token_idx_user FROM @ddl_token_idx_user;
EXECUTE stmt_token_idx_user;
DEALLOCATE PREPARE stmt_token_idx_user;

SET @ddl_token_idx_user_type = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `token_session` ADD INDEX `idx_token_user_type` (`user_type`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'token_session' AND INDEX_NAME = 'idx_token_user_type'
);
PREPARE stmt_token_idx_user_type FROM @ddl_token_idx_user_type;
EXECUTE stmt_token_idx_user_type;
DEALLOCATE PREPARE stmt_token_idx_user_type;

SET @ddl_token_idx_scene = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `token_session` ADD INDEX `idx_token_scene` (`login_scene`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'token_session' AND INDEX_NAME = 'idx_token_scene'
);
PREPARE stmt_token_idx_scene FROM @ddl_token_idx_scene;
EXECUTE stmt_token_idx_scene;
DEALLOCATE PREPARE stmt_token_idx_scene;

SET @ddl_token_idx_device_id = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `token_session` ADD INDEX `idx_token_device_id` (`device_id`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'token_session' AND INDEX_NAME = 'idx_token_device_id'
);
PREPARE stmt_token_idx_device_id FROM @ddl_token_idx_device_id;
EXECUTE stmt_token_idx_device_id;
DEALLOCATE PREPARE stmt_token_idx_device_id;

SET @ddl_token_idx_expires = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `token_session` ADD INDEX `idx_token_expires` (`expires_at`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'token_session' AND INDEX_NAME = 'idx_token_expires'
);
PREPARE stmt_token_idx_expires FROM @ddl_token_idx_expires;
EXECUTE stmt_token_idx_expires;
DEALLOCATE PREPARE stmt_token_idx_expires;
