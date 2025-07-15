-- DaHe V2 database init - Auth module
USE `dahe_v2`;

CREATE TABLE IF NOT EXISTS `user` (
  `id` BIGINT NOT NULL COMMENT 'Primary key',
  `wx_open_id` VARCHAR(64) NOT NULL COMMENT 'WeChat open id',
  `wx_union_id` VARCHAR(64) DEFAULT NULL,
  `nick_name` VARCHAR(64) DEFAULT NULL,
  `real_name` VARCHAR(64) DEFAULT NULL,
  `phone` VARCHAR(32) DEFAULT NULL,
  `login_name` VARCHAR(64) DEFAULT NULL COMMENT 'admin login account',
  `password_hash` VARCHAR(255) DEFAULT NULL COMMENT 'admin password hash',
  `status` VARCHAR(32) DEFAULT 'pending' COMMENT 'miniapp review status only: pending/approved/rejected/revoked/blacklisted',
  `role_code` VARCHAR(32) DEFAULT NULL COMMENT 'Optional admin role code (admin users only)',
  `can_console` TINYINT(1) DEFAULT '0',
  `user_type` VARCHAR(16) DEFAULT 'miniapp' COMMENT 'miniapp/admin',
  `avatar_url` VARCHAR(512) DEFAULT NULL COMMENT 'Current avatar url',
  `wx_avatar_url` VARCHAR(512) DEFAULT NULL COMMENT 'WeChat avatar url',
  `avatar_source` VARCHAR(16) DEFAULT 'none' COMMENT 'none/wx/upload/admin',
  `enabled` TINYINT(1) DEFAULT '1' COMMENT '1 enabled,0 disabled',
  `is_super_admin` TINYINT(1) DEFAULT '0' COMMENT 'super admin flag',
  `apply_reason` VARCHAR(255) DEFAULT NULL,
  `reject_reason` VARCHAR(255) DEFAULT NULL,
  `recycle_flag` TINYINT(1) DEFAULT '0' COMMENT '0 normal,1 recycled',
  `recycled_at` DATETIME DEFAULT NULL COMMENT 'Recycle time',
  `recycled_by_user_id` BIGINT DEFAULT NULL COMMENT 'Recycle operator user id',
  `recycle_remark` VARCHAR(255) DEFAULT NULL COMMENT 'Recycle remark',
  `deleted` TINYINT(1) DEFAULT '0',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_wx_open_id` (`wx_open_id`),
  UNIQUE KEY `uk_user_login_name` (`login_name`),
  INDEX `idx_user_status` (`status`),
  INDEX `idx_user_role` (`role_code`),
  INDEX `idx_user_type` (`user_type`),
  INDEX `idx_user_enabled` (`enabled`),
  INDEX `idx_user_status_type` (`status`, `user_type`),
  INDEX `idx_user_recycle` (`recycle_flag`, `user_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App users';

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

CREATE TABLE IF NOT EXISTS `user_notice` (
  `id` BIGINT NOT NULL COMMENT 'Primary key',
  `task_id` BIGINT DEFAULT NULL COMMENT 'Dispatch task id',
  `user_id` BIGINT NOT NULL COMMENT 'Target user id',
  `notice_type` VARCHAR(32) DEFAULT 'system' COMMENT 'review/status/system',
  `title` VARCHAR(128) NOT NULL COMMENT 'Notice title',
  `content` VARCHAR(500) DEFAULT NULL COMMENT 'Notice content',
  `route_code` VARCHAR(120) DEFAULT NULL COMMENT 'Target route code',
  `source_kind` VARCHAR(16) DEFAULT 'system' COMMENT 'system/manual',
  `sender_user_id` BIGINT DEFAULT NULL COMMENT 'Sender user id',
  `sender_name` VARCHAR(64) DEFAULT NULL COMMENT 'Sender user name',
  `is_read` TINYINT(1) DEFAULT '0' COMMENT '0 unread,1 read',
  `read_at` DATETIME DEFAULT NULL COMMENT 'Read timestamp',
  `extra_json` LONGTEXT DEFAULT NULL COMMENT 'Extended json',
  `deleted` TINYINT(1) DEFAULT '0',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_user_notice_user` (`user_id`),
  INDEX `idx_user_notice_read` (`user_id`, `is_read`),
  INDEX `idx_user_notice_created` (`created_at`),
  INDEX `idx_user_notice_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='In-app user notices';

CREATE TABLE IF NOT EXISTS `notice_task` (
  `id` BIGINT NOT NULL COMMENT 'Primary key',
  `notice_type` VARCHAR(32) DEFAULT 'system' COMMENT 'Notice type',
  `source_kind` VARCHAR(16) DEFAULT 'system' COMMENT 'system/manual',
  `title` VARCHAR(128) NOT NULL COMMENT 'Notice title',
  `content` VARCHAR(500) DEFAULT NULL COMMENT 'Notice content',
  `route_code` VARCHAR(120) DEFAULT NULL COMMENT 'Related route code',
  `target_type` VARCHAR(32) NOT NULL COMMENT 'admin_all/admin_route/admin_role/miniapp_approved/miniapp_console/explicit_users',
  `target_config_json` LONGTEXT DEFAULT NULL COMMENT 'Dispatch target config json',
  `dispatch_status` VARCHAR(16) DEFAULT 'pending' COMMENT 'pending/sending/sent/failed/deleted',
  `target_count` INT DEFAULT '0' COMMENT 'Target user count',
  `success_count` INT DEFAULT '0' COMMENT 'Delivered user count',
  `failed_count` INT DEFAULT '0' COMMENT 'Failed user count',
  `result_message` VARCHAR(500) DEFAULT NULL COMMENT 'Dispatch result message',
  `deleted` TINYINT(1) DEFAULT '0',
  `deleted_at` DATETIME DEFAULT NULL COMMENT 'Delete time',
  `deleted_by_user_id` BIGINT DEFAULT NULL COMMENT 'Delete operator user id',
  `created_by_user_id` BIGINT DEFAULT NULL COMMENT 'Create operator user id',
  `created_by_name` VARCHAR(64) DEFAULT NULL COMMENT 'Create operator user name',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_notice_task_status` (`dispatch_status`, `deleted`, `created_at`),
  INDEX `idx_notice_task_source` (`source_kind`, `created_at`),
  INDEX `idx_notice_task_route` (`route_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Notice dispatch task';

CREATE TABLE IF NOT EXISTS `notice_dispatch_config` (
  `id` BIGINT NOT NULL COMMENT 'Primary key',
  `auto_purge_enabled` TINYINT(1) DEFAULT '0' COMMENT 'Auto purge enabled',
  `retain_days` INT NOT NULL DEFAULT '90' COMMENT 'Notice retain days',
  `updated_by_user_id` BIGINT DEFAULT NULL COMMENT 'Last updater id',
  `updated_by_name` VARCHAR(64) DEFAULT NULL COMMENT 'Last updater name',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Notice dispatch config';

INSERT INTO `notice_dispatch_config` (`id`,`auto_purge_enabled`,`retain_days`,`created_at`,`updated_at`)
SELECT 1, 0, 90, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `notice_dispatch_config`);

SET @ddl_user_can_console = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user` ADD COLUMN `can_console` TINYINT(1) DEFAULT ''0''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'can_console'
);
PREPARE stmt_user_can_console FROM @ddl_user_can_console;
EXECUTE stmt_user_can_console;
DEALLOCATE PREPARE stmt_user_can_console;

SET @ddl_user_user_type = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user` ADD COLUMN `user_type` VARCHAR(16) DEFAULT ''miniapp'' COMMENT ''miniapp/admin''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'user_type'
);
PREPARE stmt_user_user_type FROM @ddl_user_user_type;
EXECUTE stmt_user_user_type;
DEALLOCATE PREPARE stmt_user_user_type;

SET @ddl_user_login_name = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user` ADD COLUMN `login_name` VARCHAR(64) DEFAULT NULL COMMENT ''admin login account''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'login_name'
);
PREPARE stmt_user_login_name FROM @ddl_user_login_name;
EXECUTE stmt_user_login_name;
DEALLOCATE PREPARE stmt_user_login_name;

SET @ddl_user_password_hash = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user` ADD COLUMN `password_hash` VARCHAR(255) DEFAULT NULL COMMENT ''admin password hash''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'password_hash'
);
PREPARE stmt_user_password_hash FROM @ddl_user_password_hash;
EXECUTE stmt_user_password_hash;
DEALLOCATE PREPARE stmt_user_password_hash;

SET @ddl_user_avatar_url = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user` ADD COLUMN `avatar_url` VARCHAR(512) DEFAULT NULL COMMENT ''Current avatar url''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'avatar_url'
);
PREPARE stmt_user_avatar_url FROM @ddl_user_avatar_url;
EXECUTE stmt_user_avatar_url;
DEALLOCATE PREPARE stmt_user_avatar_url;

SET @ddl_user_wx_avatar_url = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user` ADD COLUMN `wx_avatar_url` VARCHAR(512) DEFAULT NULL COMMENT ''WeChat avatar url''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'wx_avatar_url'
);
PREPARE stmt_user_wx_avatar_url FROM @ddl_user_wx_avatar_url;
EXECUTE stmt_user_wx_avatar_url;
DEALLOCATE PREPARE stmt_user_wx_avatar_url;

SET @ddl_user_avatar_source = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user` ADD COLUMN `avatar_source` VARCHAR(16) DEFAULT ''none'' COMMENT ''none/wx/upload/admin''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'avatar_source'
);
PREPARE stmt_user_avatar_source FROM @ddl_user_avatar_source;
EXECUTE stmt_user_avatar_source;
DEALLOCATE PREPARE stmt_user_avatar_source;

SET @ddl_user_enabled = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user` ADD COLUMN `enabled` TINYINT(1) DEFAULT ''1'' COMMENT ''1 enabled,0 disabled''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'enabled'
);
PREPARE stmt_user_enabled FROM @ddl_user_enabled;
EXECUTE stmt_user_enabled;
DEALLOCATE PREPARE stmt_user_enabled;

SET @ddl_user_recycle_flag = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user` ADD COLUMN `recycle_flag` TINYINT(1) DEFAULT ''0'' COMMENT ''0 normal,1 recycled''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'recycle_flag'
);
PREPARE stmt_user_recycle_flag FROM @ddl_user_recycle_flag;
EXECUTE stmt_user_recycle_flag;
DEALLOCATE PREPARE stmt_user_recycle_flag;

SET @ddl_user_recycled_at = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user` ADD COLUMN `recycled_at` DATETIME DEFAULT NULL COMMENT ''Recycle time''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'recycled_at'
);
PREPARE stmt_user_recycled_at FROM @ddl_user_recycled_at;
EXECUTE stmt_user_recycled_at;
DEALLOCATE PREPARE stmt_user_recycled_at;

SET @ddl_user_recycled_by = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user` ADD COLUMN `recycled_by_user_id` BIGINT DEFAULT NULL COMMENT ''Recycle operator user id''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'recycled_by_user_id'
);
PREPARE stmt_user_recycled_by FROM @ddl_user_recycled_by;
EXECUTE stmt_user_recycled_by;
DEALLOCATE PREPARE stmt_user_recycled_by;

SET @ddl_user_recycle_remark = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user` ADD COLUMN `recycle_remark` VARCHAR(255) DEFAULT NULL COMMENT ''Recycle remark''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'recycle_remark'
);
PREPARE stmt_user_recycle_remark FROM @ddl_user_recycle_remark;
EXECUTE stmt_user_recycle_remark;
DEALLOCATE PREPARE stmt_user_recycle_remark;

SET @ddl_user_super_admin = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user` ADD COLUMN `is_super_admin` TINYINT(1) DEFAULT ''0'' COMMENT ''super admin flag''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'is_super_admin'
);
PREPARE stmt_user_super_admin FROM @ddl_user_super_admin;
EXECUTE stmt_user_super_admin;
DEALLOCATE PREPARE stmt_user_super_admin;

SET @ddl_user_idx_type = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user` ADD INDEX `idx_user_type` (`user_type`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND INDEX_NAME = 'idx_user_type'
);
PREPARE stmt_user_idx_type FROM @ddl_user_idx_type;
EXECUTE stmt_user_idx_type;
DEALLOCATE PREPARE stmt_user_idx_type;

SET @ddl_user_idx_enabled = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user` ADD INDEX `idx_user_enabled` (`enabled`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND INDEX_NAME = 'idx_user_enabled'
);
PREPARE stmt_user_idx_enabled FROM @ddl_user_idx_enabled;
EXECUTE stmt_user_idx_enabled;
DEALLOCATE PREPARE stmt_user_idx_enabled;

SET @ddl_user_idx_status_type = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user` ADD INDEX `idx_user_status_type` (`status`, `user_type`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND INDEX_NAME = 'idx_user_status_type'
);
PREPARE stmt_user_idx_status_type FROM @ddl_user_idx_status_type;
EXECUTE stmt_user_idx_status_type;
DEALLOCATE PREPARE stmt_user_idx_status_type;

SET @ddl_user_idx_recycle = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user` ADD INDEX `idx_user_recycle` (`recycle_flag`, `user_type`, `status`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND INDEX_NAME = 'idx_user_recycle'
);
PREPARE stmt_user_idx_recycle FROM @ddl_user_idx_recycle;
EXECUTE stmt_user_idx_recycle;
DEALLOCATE PREPARE stmt_user_idx_recycle;

SET @ddl_user_uk_login_name = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user` ADD UNIQUE KEY `uk_user_login_name` (`login_name`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND INDEX_NAME = 'uk_user_login_name'
);
PREPARE stmt_user_uk_login_name FROM @ddl_user_uk_login_name;
EXECUTE stmt_user_uk_login_name;
DEALLOCATE PREPARE stmt_user_uk_login_name;

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

UPDATE `user`
SET `user_type`='admin'
WHERE (
    LOWER(`wx_open_id`) LIKE 'mock_admin_%'
    OR LOWER(`wx_open_id`) LIKE 'mock_supervisor_%'
    OR LOWER(`wx_open_id`) LIKE 'admin_%'
  )
  AND (`user_type` IS NULL OR TRIM(`user_type`)='' OR LOWER(`user_type`)='miniapp');

UPDATE `user`
SET `user_type`='miniapp'
WHERE `user_type` IS NULL OR TRIM(`user_type`)='';

UPDATE `user`
SET `login_name`=LOWER(TRIM(`wx_open_id`))
WHERE `deleted`=0
  AND LOWER(COALESCE(`user_type`,'miniapp'))='admin'
  AND (`login_name` IS NULL OR TRIM(`login_name`)='')
  AND `wx_open_id` IS NOT NULL
  AND TRIM(`wx_open_id`)<>'';

UPDATE `user`
SET `login_name`=CONCAT('admin_', `id`)
WHERE `deleted`=0
  AND LOWER(COALESCE(`user_type`,'miniapp'))='admin'
  AND (`login_name` IS NULL OR TRIM(`login_name`)='');

UPDATE `user`
SET `password_hash`='$2a$10$ExTSLfeUeLwaUDYd2u4jI.ouGmbHKmklUcU9tlyx9RYcB6.QCkTqm'
WHERE `deleted`=0
  AND LOWER(COALESCE(`user_type`,'miniapp'))='admin'
  AND (`password_hash` IS NULL OR TRIM(`password_hash`)='');

UPDATE `user`
SET `avatar_source`='none'
WHERE `avatar_source` IS NULL OR TRIM(`avatar_source`)='';

UPDATE `user`
SET `enabled`=1
WHERE `enabled` IS NULL;

UPDATE `user`
SET `is_super_admin`=1
WHERE LOWER(`wx_open_id`)='mock_admin_0001';

UPDATE `user`
SET `recycle_flag`=0
WHERE `recycle_flag` IS NULL;

SET @super_admin_count = (
  SELECT COUNT(*)
  FROM `user`
  WHERE `deleted`=0 AND `is_super_admin`=1
);

SET @first_admin_id = (
  SELECT `id`
  FROM `user`
  WHERE `deleted`=0 AND (
    LOWER(`role_code`)='admin' OR LOWER(`user_type`)='admin'
  )
  ORDER BY `created_at` ASC, `id` ASC
  LIMIT 1
);

SET @ddl_user_pick_super_admin = (
  SELECT IF(
    @super_admin_count = 0 AND @first_admin_id IS NOT NULL,
    CONCAT('UPDATE `user` SET `is_super_admin`=1 WHERE `id`=', @first_admin_id),
    'SELECT 1'
  )
);
PREPARE stmt_user_pick_super_admin FROM @ddl_user_pick_super_admin;
EXECUTE stmt_user_pick_super_admin;
DEALLOCATE PREPARE stmt_user_pick_super_admin;

UPDATE `user`
SET `status`='approved'
WHERE LOWER(`status`)='disabled';

UPDATE `user`
SET `status`=NULL
WHERE LOWER(`user_type`)='admin';

UPDATE `user`
SET `status`='pending'
WHERE LOWER(`user_type`)='miniapp'
  AND (`status` IS NULL OR TRIM(`status`)='');

UPDATE `user`
SET `role_code`=NULL
WHERE `deleted`=0
  AND LOWER(COALESCE(`user_type`,'miniapp'))='miniapp'
  AND `role_code` IS NOT NULL;

UPDATE `user`
SET `enabled`=1, `user_type`='admin'
WHERE `deleted`=0 AND `is_super_admin`=1;

SET @ddl_user_notice_task_id = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user_notice` ADD COLUMN `task_id` BIGINT DEFAULT NULL COMMENT ''Dispatch task id''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_notice' AND COLUMN_NAME = 'task_id'
);
PREPARE stmt_user_notice_task_id FROM @ddl_user_notice_task_id;
EXECUTE stmt_user_notice_task_id;
DEALLOCATE PREPARE stmt_user_notice_task_id;

SET @ddl_user_notice_route_code = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user_notice` ADD COLUMN `route_code` VARCHAR(120) DEFAULT NULL COMMENT ''Target route code''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_notice' AND COLUMN_NAME = 'route_code'
);
PREPARE stmt_user_notice_route_code FROM @ddl_user_notice_route_code;
EXECUTE stmt_user_notice_route_code;
DEALLOCATE PREPARE stmt_user_notice_route_code;

SET @ddl_user_notice_source_kind = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user_notice` ADD COLUMN `source_kind` VARCHAR(16) DEFAULT ''system'' COMMENT ''system/manual''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_notice' AND COLUMN_NAME = 'source_kind'
);
PREPARE stmt_user_notice_source_kind FROM @ddl_user_notice_source_kind;
EXECUTE stmt_user_notice_source_kind;
DEALLOCATE PREPARE stmt_user_notice_source_kind;

SET @ddl_user_notice_sender_user_id = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user_notice` ADD COLUMN `sender_user_id` BIGINT DEFAULT NULL COMMENT ''Sender user id''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_notice' AND COLUMN_NAME = 'sender_user_id'
);
PREPARE stmt_user_notice_sender_user_id FROM @ddl_user_notice_sender_user_id;
EXECUTE stmt_user_notice_sender_user_id;
DEALLOCATE PREPARE stmt_user_notice_sender_user_id;

SET @ddl_user_notice_sender_name = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user_notice` ADD COLUMN `sender_name` VARCHAR(64) DEFAULT NULL COMMENT ''Sender user name''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_notice' AND COLUMN_NAME = 'sender_name'
);
PREPARE stmt_user_notice_sender_name FROM @ddl_user_notice_sender_name;
EXECUTE stmt_user_notice_sender_name;
DEALLOCATE PREPARE stmt_user_notice_sender_name;

SET @ddl_user_notice_idx_task = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user_notice` ADD INDEX `idx_user_notice_task` (`task_id`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_notice' AND INDEX_NAME = 'idx_user_notice_task'
);
PREPARE stmt_user_notice_idx_task FROM @ddl_user_notice_idx_task;
EXECUTE stmt_user_notice_idx_task;
DEALLOCATE PREPARE stmt_user_notice_idx_task;

CREATE TABLE IF NOT EXISTS `admin_role` (
  `id` BIGINT NOT NULL COMMENT 'Primary key',
  `role_code` VARCHAR(32) NOT NULL COMMENT 'Unique role code',
  `role_name` VARCHAR(64) NOT NULL COMMENT 'Role display name',
  `description` VARCHAR(255) DEFAULT NULL COMMENT 'Role description',
  `inherit_role_code` VARCHAR(32) DEFAULT NULL COMMENT 'Optional inherit role code',
  `menu_permissions_json` LONGTEXT DEFAULT NULL COMMENT 'Menu permission keys json',
  `sort_order` INT DEFAULT '0' COMMENT 'Display sort order',
  `enabled` TINYINT(1) DEFAULT '1' COMMENT '1 enabled,0 disabled',
  `is_system` TINYINT(1) DEFAULT '0' COMMENT '1 system role',
  `deleted` TINYINT(1) DEFAULT '0',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_role_code` (`role_code`),
  INDEX `idx_admin_role_enabled` (`enabled`),
  INDEX `idx_admin_role_sort` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Admin role definitions';

SET @ddl_admin_role_inherit = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `admin_role` ADD COLUMN `inherit_role_code` VARCHAR(32) DEFAULT NULL COMMENT ''Optional inherit role code''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_role' AND COLUMN_NAME = 'inherit_role_code'
);
PREPARE stmt_admin_role_inherit FROM @ddl_admin_role_inherit;
EXECUTE stmt_admin_role_inherit;
DEALLOCATE PREPARE stmt_admin_role_inherit;

SET @ddl_admin_role_menu = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `admin_role` ADD COLUMN `menu_permissions_json` LONGTEXT DEFAULT NULL COMMENT ''Menu permission keys json''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_role' AND COLUMN_NAME = 'menu_permissions_json'
);
PREPARE stmt_admin_role_menu FROM @ddl_admin_role_menu;
EXECUTE stmt_admin_role_menu;
DEALLOCATE PREPARE stmt_admin_role_menu;

SET @ddl_admin_role_sort = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `admin_role` ADD COLUMN `sort_order` INT DEFAULT ''0'' COMMENT ''Display sort order''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_role' AND COLUMN_NAME = 'sort_order'
);
PREPARE stmt_admin_role_sort FROM @ddl_admin_role_sort;
EXECUTE stmt_admin_role_sort;
DEALLOCATE PREPARE stmt_admin_role_sort;

SET @ddl_admin_role_enabled = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `admin_role` ADD COLUMN `enabled` TINYINT(1) DEFAULT ''1'' COMMENT ''1 enabled,0 disabled''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_role' AND COLUMN_NAME = 'enabled'
);
PREPARE stmt_admin_role_enabled FROM @ddl_admin_role_enabled;
EXECUTE stmt_admin_role_enabled;
DEALLOCATE PREPARE stmt_admin_role_enabled;

SET @ddl_admin_role_system = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `admin_role` ADD COLUMN `is_system` TINYINT(1) DEFAULT ''0'' COMMENT ''1 system role''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_role' AND COLUMN_NAME = 'is_system'
);
PREPARE stmt_admin_role_system FROM @ddl_admin_role_system;
EXECUTE stmt_admin_role_system;
DEALLOCATE PREPARE stmt_admin_role_system;

SET @ddl_admin_role_deleted = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `admin_role` ADD COLUMN `deleted` TINYINT(1) DEFAULT ''0''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_role' AND COLUMN_NAME = 'deleted'
);
PREPARE stmt_admin_role_deleted FROM @ddl_admin_role_deleted;
EXECUTE stmt_admin_role_deleted;
DEALLOCATE PREPARE stmt_admin_role_deleted;

SET @ddl_admin_role_idx_enabled = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `admin_role` ADD INDEX `idx_admin_role_enabled` (`enabled`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_role' AND INDEX_NAME = 'idx_admin_role_enabled'
);
PREPARE stmt_admin_role_idx_enabled FROM @ddl_admin_role_idx_enabled;
EXECUTE stmt_admin_role_idx_enabled;
DEALLOCATE PREPARE stmt_admin_role_idx_enabled;

SET @ddl_admin_role_idx_sort = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `admin_role` ADD INDEX `idx_admin_role_sort` (`sort_order`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_role' AND INDEX_NAME = 'idx_admin_role_sort'
);
PREPARE stmt_admin_role_idx_sort FROM @ddl_admin_role_idx_sort;
EXECUTE stmt_admin_role_idx_sort;
DEALLOCATE PREPARE stmt_admin_role_idx_sort;

UPDATE `admin_role`
SET `role_code`=LOWER(TRIM(`role_code`))
WHERE `role_code` IS NOT NULL AND `role_code`<>LOWER(TRIM(`role_code`));

UPDATE `admin_role`
SET `inherit_role_code`=NULL
WHERE `inherit_role_code` IS NOT NULL AND TRIM(`inherit_role_code`)='';

UPDATE `admin_role` r
LEFT JOIN `admin_role` parent
  ON parent.`deleted`=0 AND LOWER(parent.`role_code`)=LOWER(COALESCE(r.`inherit_role_code`, ''))
SET r.`inherit_role_code`=NULL
WHERE r.`inherit_role_code` IS NOT NULL AND parent.`id` IS NULL;

UPDATE `admin_role`
SET `enabled`=1
WHERE `enabled` IS NULL;

UPDATE `admin_role`
SET `is_system`=0
WHERE `is_system` IS NULL;

UPDATE `admin_role`
SET `sort_order`=0
WHERE `sort_order` IS NULL;

INSERT INTO `admin_role`
(`id`,`role_code`,`role_name`,`description`,`inherit_role_code`,`menu_permissions_json`,`sort_order`,`enabled`,`is_system`,`deleted`)
VALUES
(10000,'super_admin','超级管理员','系统内置超级管理员角色（不可变更）',NULL,'["*"]',-100,1,1,0)
ON DUPLICATE KEY UPDATE
  `role_name`=VALUES(`role_name`),
  `description`=VALUES(`description`),
  `menu_permissions_json`='["*"]',
  `sort_order`=VALUES(`sort_order`),
  `enabled`=1,
  `is_system`=1,
  `deleted`=0;

INSERT INTO `admin_role`
(`id`,`role_code`,`role_name`,`description`,`inherit_role_code`,`menu_permissions_json`,`sort_order`,`enabled`,`is_system`,`deleted`)
VALUES
(10001,'console_manager','控制台管理员','默认后台角色（自动创建）',NULL,'["/dashboard","/users","/admin-users","/roles","/field-manage","/crop-manage","/field-cycles","/farm-records-manage","/record-policy","/seed-manage","/seed-rules","/exports","/export-templates","/amap-audit","/operation-logs","/admin-guide","/system-settings","/process-templates","/terminology-dict","/farm-step-dynamic-configs","/seed-dynamic-configs/batch","/seed-dynamic-configs/test","/asset-policy","/assets"]',10,1,0,0)
ON DUPLICATE KEY UPDATE
  `role_name`=VALUES(`role_name`),
  `description`=VALUES(`description`),
  `menu_permissions_json`=IFNULL(NULLIF(`menu_permissions_json`, ''), VALUES(`menu_permissions_json`)),
  `sort_order`=IFNULL(`sort_order`, VALUES(`sort_order`)),
  `enabled`=IFNULL(`enabled`, 1),
  `is_system`=IFNULL(`is_system`, 0),
  `deleted`=0;

SET @enabled_role_count = (
  SELECT COUNT(*) FROM `admin_role` WHERE `deleted`=0 AND `enabled`=1
);

UPDATE `admin_role`
SET `enabled`=1
WHERE LOWER(`role_code`)='console_manager' AND @enabled_role_count = 0;

SET @default_admin_role = (
  SELECT `role_code` FROM `admin_role`
  WHERE `deleted`=0 AND `enabled`=1 AND `role_code` IS NOT NULL AND TRIM(`role_code`)<>'' AND LOWER(`role_code`)<>'super_admin'
  ORDER BY `sort_order` ASC, `created_at` ASC, `id` ASC
  LIMIT 1
);

UPDATE `user` u
LEFT JOIN `admin_role` r
  ON r.`deleted`=0 AND LOWER(r.`role_code`)=LOWER(COALESCE(u.`role_code`, ''))
SET u.`role_code`=@default_admin_role
WHERE @default_admin_role IS NOT NULL
  AND u.`deleted`=0
  AND LOWER(COALESCE(u.`user_type`, 'miniapp'))='admin'
  AND (u.`role_code` IS NULL OR TRIM(u.`role_code`)='' OR r.`id` IS NULL);

UPDATE `user`
SET `role_code`=@default_admin_role
WHERE @default_admin_role IS NOT NULL
  AND `deleted`=0
  AND `is_super_admin`=0
  AND (`role_code` IS NULL OR TRIM(`role_code`)='');

UPDATE `user`
SET `role_code`='super_admin', `user_type`='admin', `enabled`=1
WHERE `deleted`=0
  AND `is_super_admin`=1
  AND (LOWER(COALESCE(`role_code`,''))<>'super_admin' OR LOWER(COALESCE(`user_type`,''))<>'admin' OR COALESCE(`enabled`,0)=0);

CREATE TABLE IF NOT EXISTS `terminology_dict` (
  `id` BIGINT NOT NULL COMMENT 'Primary key',
  `source_text` VARCHAR(120) NOT NULL COMMENT 'Chinese source phrase',
  `target_text` VARCHAR(255) NOT NULL COMMENT 'English semantic phrase',
  `sort_order` INT DEFAULT '0' COMMENT 'Sort order',
  `deleted` TINYINT(1) DEFAULT '0',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_terminology_source_deleted` (`source_text`, `deleted`),
  INDEX `idx_terminology_sort` (`sort_order`, `id`),
  INDEX `idx_terminology_updated` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Terminology dictionary';

SET @ddl_terminology_sort_order = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `terminology_dict` ADD COLUMN `sort_order` INT DEFAULT ''0'' COMMENT ''Sort order''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'terminology_dict' AND COLUMN_NAME = 'sort_order'
);
PREPARE stmt_terminology_sort_order FROM @ddl_terminology_sort_order;
EXECUTE stmt_terminology_sort_order;
DEALLOCATE PREPARE stmt_terminology_sort_order;

SET @ddl_terminology_deleted = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `terminology_dict` ADD COLUMN `deleted` TINYINT(1) DEFAULT ''0''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'terminology_dict' AND COLUMN_NAME = 'deleted'
);
PREPARE stmt_terminology_deleted FROM @ddl_terminology_deleted;
EXECUTE stmt_terminology_deleted;
DEALLOCATE PREPARE stmt_terminology_deleted;

SET @ddl_terminology_idx_sort = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `terminology_dict` ADD INDEX `idx_terminology_sort` (`sort_order`, `id`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'terminology_dict' AND INDEX_NAME = 'idx_terminology_sort'
);
PREPARE stmt_terminology_idx_sort FROM @ddl_terminology_idx_sort;
EXECUTE stmt_terminology_idx_sort;
DEALLOCATE PREPARE stmt_terminology_idx_sort;

SET @ddl_terminology_idx_updated = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `terminology_dict` ADD INDEX `idx_terminology_updated` (`updated_at`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'terminology_dict' AND INDEX_NAME = 'idx_terminology_updated'
);
PREPARE stmt_terminology_idx_updated FROM @ddl_terminology_idx_updated;
EXECUTE stmt_terminology_idx_updated;
DEALLOCATE PREPARE stmt_terminology_idx_updated;

