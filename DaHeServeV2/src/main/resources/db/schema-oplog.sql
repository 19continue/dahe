-- DaHe V2 database init - Operation log module
USE `dahe_v2`;

CREATE TABLE IF NOT EXISTS `operation_log` (
  `id` BIGINT NOT NULL COMMENT 'Primary key',
  `user_id` BIGINT DEFAULT NULL COMMENT 'Operator user id',
  `user_type` VARCHAR(16) DEFAULT NULL COMMENT 'miniapp/admin',
  `role_code` VARCHAR(32) DEFAULT NULL COMMENT 'Role code',
  `operator_name` VARCHAR(64) DEFAULT NULL COMMENT 'Operator display name',
  `operation_type` VARCHAR(32) NOT NULL COMMENT 'create/update/delete/review/restore/reorder...',
  `target_module` VARCHAR(32) DEFAULT NULL COMMENT 'Target module',
  `target_id` BIGINT DEFAULT NULL COMMENT 'Target entity id',
  `http_method` VARCHAR(16) DEFAULT NULL COMMENT 'HTTP method',
  `api_path` VARCHAR(255) DEFAULT NULL COMMENT 'API path',
  `query_string` VARCHAR(500) DEFAULT NULL COMMENT 'Request query string',
  `result_code` INT DEFAULT NULL COMMENT 'Result code',
  `result_message` VARCHAR(500) DEFAULT NULL COMMENT 'Result message',
  `success_flag` TINYINT(1) DEFAULT '1' COMMENT '1 success,0 failed',
  `cost_ms` INT DEFAULT NULL COMMENT 'Cost milliseconds',
  `client_ip` VARCHAR(64) DEFAULT NULL COMMENT 'Client IP',
  `undo_type` VARCHAR(32) DEFAULT NULL COMMENT 'Undo action type',
  `undo_payload_json` LONGTEXT DEFAULT NULL COMMENT 'Undo payload json',
  `undo_status` VARCHAR(16) DEFAULT NULL COMMENT 'pending/applied/failed',
  `undo_fail_reason` VARCHAR(500) DEFAULT NULL COMMENT 'Undo failed reason',
  `undo_applied_at` DATETIME DEFAULT NULL COMMENT 'Undo applied time',
  `undo_applied_by_user_id` BIGINT DEFAULT NULL COMMENT 'Undo operator user id',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_op_log_created` (`created_at`),
  INDEX `idx_op_log_type` (`operation_type`),
  INDEX `idx_op_log_user` (`user_id`),
  INDEX `idx_op_log_success` (`success_flag`),
  INDEX `idx_op_log_target` (`target_module`,`target_id`),
  INDEX `idx_op_log_undo` (`undo_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Operation logs';

SET @ddl_oplog_target_module = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `operation_log` ADD COLUMN `target_module` VARCHAR(32) DEFAULT NULL COMMENT ''Target module''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'operation_log' AND COLUMN_NAME = 'target_module'
);
PREPARE stmt_oplog_target_module FROM @ddl_oplog_target_module;
EXECUTE stmt_oplog_target_module;
DEALLOCATE PREPARE stmt_oplog_target_module;

SET @ddl_oplog_target_id = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `operation_log` ADD COLUMN `target_id` BIGINT DEFAULT NULL COMMENT ''Target entity id''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'operation_log' AND COLUMN_NAME = 'target_id'
);
PREPARE stmt_oplog_target_id FROM @ddl_oplog_target_id;
EXECUTE stmt_oplog_target_id;
DEALLOCATE PREPARE stmt_oplog_target_id;

SET @ddl_oplog_undo_type = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `operation_log` ADD COLUMN `undo_type` VARCHAR(32) DEFAULT NULL COMMENT ''Undo action type''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'operation_log' AND COLUMN_NAME = 'undo_type'
);
PREPARE stmt_oplog_undo_type FROM @ddl_oplog_undo_type;
EXECUTE stmt_oplog_undo_type;
DEALLOCATE PREPARE stmt_oplog_undo_type;

SET @ddl_oplog_undo_payload = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `operation_log` ADD COLUMN `undo_payload_json` LONGTEXT DEFAULT NULL COMMENT ''Undo payload json''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'operation_log' AND COLUMN_NAME = 'undo_payload_json'
);
PREPARE stmt_oplog_undo_payload FROM @ddl_oplog_undo_payload;
EXECUTE stmt_oplog_undo_payload;
DEALLOCATE PREPARE stmt_oplog_undo_payload;

SET @ddl_oplog_undo_payload_longtext = (
  SELECT IF(
    COUNT(*) > 0,
    'ALTER TABLE `operation_log` MODIFY COLUMN `undo_payload_json` LONGTEXT DEFAULT NULL COMMENT ''Undo payload json''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'operation_log'
    AND COLUMN_NAME = 'undo_payload_json'
    AND LOWER(DATA_TYPE) <> 'longtext'
);
PREPARE stmt_oplog_undo_payload_longtext FROM @ddl_oplog_undo_payload_longtext;
EXECUTE stmt_oplog_undo_payload_longtext;
DEALLOCATE PREPARE stmt_oplog_undo_payload_longtext;

SET @ddl_oplog_undo_status = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `operation_log` ADD COLUMN `undo_status` VARCHAR(16) DEFAULT NULL COMMENT ''pending/applied/failed''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'operation_log' AND COLUMN_NAME = 'undo_status'
);
PREPARE stmt_oplog_undo_status FROM @ddl_oplog_undo_status;
EXECUTE stmt_oplog_undo_status;
DEALLOCATE PREPARE stmt_oplog_undo_status;

SET @ddl_oplog_undo_fail_reason = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `operation_log` ADD COLUMN `undo_fail_reason` VARCHAR(500) DEFAULT NULL COMMENT ''Undo failed reason''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'operation_log' AND COLUMN_NAME = 'undo_fail_reason'
);
PREPARE stmt_oplog_undo_fail_reason FROM @ddl_oplog_undo_fail_reason;
EXECUTE stmt_oplog_undo_fail_reason;
DEALLOCATE PREPARE stmt_oplog_undo_fail_reason;

SET @ddl_oplog_undo_applied_at = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `operation_log` ADD COLUMN `undo_applied_at` DATETIME DEFAULT NULL COMMENT ''Undo applied time''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'operation_log' AND COLUMN_NAME = 'undo_applied_at'
);
PREPARE stmt_oplog_undo_applied_at FROM @ddl_oplog_undo_applied_at;
EXECUTE stmt_oplog_undo_applied_at;
DEALLOCATE PREPARE stmt_oplog_undo_applied_at;

SET @ddl_oplog_undo_applied_by = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `operation_log` ADD COLUMN `undo_applied_by_user_id` BIGINT DEFAULT NULL COMMENT ''Undo operator user id''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'operation_log' AND COLUMN_NAME = 'undo_applied_by_user_id'
);
PREPARE stmt_oplog_undo_applied_by FROM @ddl_oplog_undo_applied_by;
EXECUTE stmt_oplog_undo_applied_by;
DEALLOCATE PREPARE stmt_oplog_undo_applied_by;

SET @ddl_idx_oplog_target = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `operation_log` ADD INDEX `idx_op_log_target` (`target_module`,`target_id`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'operation_log' AND INDEX_NAME = 'idx_op_log_target'
);
PREPARE stmt_idx_oplog_target FROM @ddl_idx_oplog_target;
EXECUTE stmt_idx_oplog_target;
DEALLOCATE PREPARE stmt_idx_oplog_target;

SET @ddl_idx_oplog_undo = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `operation_log` ADD INDEX `idx_op_log_undo` (`undo_status`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'operation_log' AND INDEX_NAME = 'idx_op_log_undo'
);
PREPARE stmt_idx_oplog_undo FROM @ddl_idx_oplog_undo;
EXECUTE stmt_idx_oplog_undo;
DEALLOCATE PREPARE stmt_idx_oplog_undo;
