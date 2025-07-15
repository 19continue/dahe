-- DaHe V2 database init - Media asset module
USE `dahe_v2`;

CREATE TABLE IF NOT EXISTS `miniapp_static_asset` (
  `id` BIGINT NOT NULL COMMENT 'Primary key',
  `display_name` VARCHAR(120) DEFAULT NULL COMMENT 'Display name',
  `storage_name` VARCHAR(64) NOT NULL COMMENT 'Storage name without extension',
  `file_ext` VARCHAR(16) NOT NULL COMMENT 'Storage extension',
  `file_url` VARCHAR(1024) NOT NULL COMMENT 'Public url',
  `file_type` VARCHAR(32) DEFAULT 'file' COMMENT 'image/file',
  `size_bytes` BIGINT DEFAULT NULL COMMENT 'File size(bytes)',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT 'Remark',
  `created_by_user_id` BIGINT DEFAULT NULL COMMENT 'Creator user id',
  `created_by_name` VARCHAR(64) DEFAULT NULL COMMENT 'Creator name',
  `updated_by_user_id` BIGINT DEFAULT NULL COMMENT 'Updater user id',
  `updated_by_name` VARCHAR(64) DEFAULT NULL COMMENT 'Updater name',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_miniapp_static_asset_storage_name` (`storage_name`),
  INDEX `idx_miniapp_static_asset_updated` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Miniapp static assets';

CREATE TABLE IF NOT EXISTS `media_asset` (
  `id` BIGINT NOT NULL COMMENT 'Primary key',
  `file_name` VARCHAR(255) NOT NULL COMMENT 'Display file name',
  `file_url` VARCHAR(1024) NOT NULL COMMENT 'Public url',
  `file_type` VARCHAR(32) DEFAULT 'file' COMMENT 'image/file',
  `folder_path` VARCHAR(255) DEFAULT '/默认' COMMENT 'Folder path',
  `source_type` VARCHAR(32) DEFAULT 'system_upload' COMMENT 'admin_upload/miniapp_upload/system_upload',
  `recycle_flag` TINYINT(1) DEFAULT '0' COMMENT '0=normal,1=recycled',
  `recycled_at` DATETIME DEFAULT NULL COMMENT 'Recycle time',
  `recycled_by_user_id` BIGINT DEFAULT NULL COMMENT 'Recycle operator user id',
  `review_status` VARCHAR(16) DEFAULT 'approved' COMMENT 'approved/pending/rejected',
  `reviewed_at` DATETIME DEFAULT NULL COMMENT 'Review timestamp',
  `reviewed_by_user_id` BIGINT DEFAULT NULL COMMENT 'Review operator user id',
  `review_remark` VARCHAR(255) DEFAULT NULL COMMENT 'Review remark',
  `module_key` VARCHAR(64) DEFAULT NULL COMMENT 'Module key',
  `biz_id` BIGINT DEFAULT NULL COMMENT 'Business id',
  `sort_order` INT DEFAULT 0 COMMENT 'Display sort order',
  `size_bytes` BIGINT DEFAULT NULL COMMENT 'File size(bytes)',
  `created_by_user_id` BIGINT DEFAULT NULL COMMENT 'Creator user id',
  `created_by_name` VARCHAR(64) DEFAULT NULL COMMENT 'Creator name',
  `locked_flag` TINYINT(1) DEFAULT '0' COMMENT '0=unlocked,1=locked',
  `lock_password_hash` VARCHAR(255) DEFAULT NULL COMMENT 'Delete unlock password hash',
  `lock_remark` VARCHAR(255) DEFAULT NULL COMMENT 'Lock remark',
  `lock_updated_at` DATETIME DEFAULT NULL COMMENT 'Lock updated time',
  `lock_updated_by_user_id` BIGINT DEFAULT NULL COMMENT 'Lock updated user id',
  `lock_updated_by_name` VARCHAR(64) DEFAULT NULL COMMENT 'Lock updated user name',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT 'Remark',
  `deleted` TINYINT(1) DEFAULT '0',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_media_module` (`module_key`),
  INDEX `idx_media_biz` (`biz_id`),
  INDEX `idx_media_folder` (`folder_path`),
  INDEX `idx_media_source` (`source_type`),
  INDEX `idx_media_recycle` (`recycle_flag`,`created_at`),
  INDEX `idx_media_locked` (`locked_flag`,`recycle_flag`,`created_at`),
  INDEX `idx_media_review` (`review_status`,`source_type`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Media asset library';

SET @ddl_media_review_status = (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'media_asset' AND COLUMN_NAME = 'review_status'
    ),
    'SELECT 1',
    'ALTER TABLE `media_asset` ADD COLUMN `review_status` VARCHAR(16) DEFAULT ''approved'' COMMENT ''approved/pending/rejected'''
  )
);
PREPARE stmt_media_review_status FROM @ddl_media_review_status;
EXECUTE stmt_media_review_status;
DEALLOCATE PREPARE stmt_media_review_status;

SET @ddl_media_reviewed_at = (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'media_asset' AND COLUMN_NAME = 'reviewed_at'
    ),
    'SELECT 1',
    'ALTER TABLE `media_asset` ADD COLUMN `reviewed_at` DATETIME DEFAULT NULL COMMENT ''Review timestamp'''
  )
);
PREPARE stmt_media_reviewed_at FROM @ddl_media_reviewed_at;
EXECUTE stmt_media_reviewed_at;
DEALLOCATE PREPARE stmt_media_reviewed_at;

SET @ddl_media_reviewed_by = (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'media_asset' AND COLUMN_NAME = 'reviewed_by_user_id'
    ),
    'SELECT 1',
    'ALTER TABLE `media_asset` ADD COLUMN `reviewed_by_user_id` BIGINT DEFAULT NULL COMMENT ''Review operator user id'''
  )
);
PREPARE stmt_media_reviewed_by FROM @ddl_media_reviewed_by;
EXECUTE stmt_media_reviewed_by;
DEALLOCATE PREPARE stmt_media_reviewed_by;

SET @ddl_media_review_remark = (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'media_asset' AND COLUMN_NAME = 'review_remark'
    ),
    'SELECT 1',
    'ALTER TABLE `media_asset` ADD COLUMN `review_remark` VARCHAR(255) DEFAULT NULL COMMENT ''Review remark'''
  )
);
PREPARE stmt_media_review_remark FROM @ddl_media_review_remark;
EXECUTE stmt_media_review_remark;
DEALLOCATE PREPARE stmt_media_review_remark;

SET @ddl_media_locked_flag = (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'media_asset' AND COLUMN_NAME = 'locked_flag'
    ),
    'SELECT 1',
    'ALTER TABLE `media_asset` ADD COLUMN `locked_flag` TINYINT(1) DEFAULT 0 COMMENT ''0=unlocked,1=locked'''
  )
);
PREPARE stmt_media_locked_flag FROM @ddl_media_locked_flag;
EXECUTE stmt_media_locked_flag;
DEALLOCATE PREPARE stmt_media_locked_flag;

SET @ddl_media_lock_password_hash = (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'media_asset' AND COLUMN_NAME = 'lock_password_hash'
    ),
    'SELECT 1',
    'ALTER TABLE `media_asset` ADD COLUMN `lock_password_hash` VARCHAR(255) DEFAULT NULL COMMENT ''Delete unlock password hash'''
  )
);
PREPARE stmt_media_lock_password_hash FROM @ddl_media_lock_password_hash;
EXECUTE stmt_media_lock_password_hash;
DEALLOCATE PREPARE stmt_media_lock_password_hash;

SET @ddl_media_lock_remark = (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'media_asset' AND COLUMN_NAME = 'lock_remark'
    ),
    'SELECT 1',
    'ALTER TABLE `media_asset` ADD COLUMN `lock_remark` VARCHAR(255) DEFAULT NULL COMMENT ''Lock remark'''
  )
);
PREPARE stmt_media_lock_remark FROM @ddl_media_lock_remark;
EXECUTE stmt_media_lock_remark;
DEALLOCATE PREPARE stmt_media_lock_remark;

SET @ddl_media_lock_updated_at = (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'media_asset' AND COLUMN_NAME = 'lock_updated_at'
    ),
    'SELECT 1',
    'ALTER TABLE `media_asset` ADD COLUMN `lock_updated_at` DATETIME DEFAULT NULL COMMENT ''Lock updated time'''
  )
);
PREPARE stmt_media_lock_updated_at FROM @ddl_media_lock_updated_at;
EXECUTE stmt_media_lock_updated_at;
DEALLOCATE PREPARE stmt_media_lock_updated_at;

SET @ddl_media_lock_updated_by_user_id = (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'media_asset' AND COLUMN_NAME = 'lock_updated_by_user_id'
    ),
    'SELECT 1',
    'ALTER TABLE `media_asset` ADD COLUMN `lock_updated_by_user_id` BIGINT DEFAULT NULL COMMENT ''Lock updated user id'''
  )
);
PREPARE stmt_media_lock_updated_by_user_id FROM @ddl_media_lock_updated_by_user_id;
EXECUTE stmt_media_lock_updated_by_user_id;
DEALLOCATE PREPARE stmt_media_lock_updated_by_user_id;

SET @ddl_media_lock_updated_by_name = (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'media_asset' AND COLUMN_NAME = 'lock_updated_by_name'
    ),
    'SELECT 1',
    'ALTER TABLE `media_asset` ADD COLUMN `lock_updated_by_name` VARCHAR(64) DEFAULT NULL COMMENT ''Lock updated user name'''
  )
);
PREPARE stmt_media_lock_updated_by_name FROM @ddl_media_lock_updated_by_name;
EXECUTE stmt_media_lock_updated_by_name;
DEALLOCATE PREPARE stmt_media_lock_updated_by_name;

SET @idx_media_review = (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'media_asset' AND INDEX_NAME = 'idx_media_review'
    ),
    'SELECT 1',
    'ALTER TABLE `media_asset` ADD INDEX `idx_media_review` (`review_status`,`source_type`,`created_at`)'
  )
);
PREPARE stmt_idx_media_review FROM @idx_media_review;
EXECUTE stmt_idx_media_review;
DEALLOCATE PREPARE stmt_idx_media_review;

SET @idx_media_locked = (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'media_asset' AND INDEX_NAME = 'idx_media_locked'
    ),
    'SELECT 1',
    'ALTER TABLE `media_asset` ADD INDEX `idx_media_locked` (`locked_flag`,`recycle_flag`,`created_at`)'
  )
);
PREPARE stmt_idx_media_locked FROM @idx_media_locked;
EXECUTE stmt_idx_media_locked;
DEALLOCATE PREPARE stmt_idx_media_locked;

UPDATE `media_asset`
SET `review_status` = 'approved'
WHERE `review_status` IS NULL OR TRIM(`review_status`) = '';

UPDATE `media_asset`
SET `locked_flag` = 0
WHERE `locked_flag` IS NULL;

UPDATE `media_asset`
SET `source_type` = 'miniapp_upload'
WHERE `source_type` = 'operator_upload';

CREATE TABLE IF NOT EXISTS `media_asset_reference` (
  `id` BIGINT NOT NULL COMMENT 'Primary key',
  `asset_id` BIGINT NOT NULL COMMENT 'Media asset id',
  `module_key` VARCHAR(64) NOT NULL COMMENT 'Module key',
  `biz_id` BIGINT NOT NULL COMMENT 'Business id',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_media_asset_reference` (`asset_id`,`module_key`,`biz_id`),
  INDEX `idx_media_asset_reference_asset` (`asset_id`),
  INDEX `idx_media_asset_reference_module` (`module_key`,`biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Current asset reference snapshot';

DELETE FROM `media_asset_reference`;

INSERT INTO `media_asset_reference` (`id`,`asset_id`,`module_key`,`biz_id`,`created_at`,`updated_at`)
SELECT UUID_SHORT(), `id`, TRIM(`module_key`), `biz_id`, NOW(), NOW()
FROM `media_asset`
WHERE `deleted` = 0
  AND `recycle_flag` = 0
  AND `biz_id` IS NOT NULL
  AND NULLIF(TRIM(`module_key`),'') IS NOT NULL;

CREATE TABLE IF NOT EXISTS `media_asset_folder` (
  `id` BIGINT NOT NULL COMMENT 'Primary key',
  `folder_path` VARCHAR(255) NOT NULL COMMENT 'Folder path',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT 'Folder remark',
  `protected_flag` TINYINT(1) DEFAULT '0' COMMENT '1 protected,0 normal',
  `created_by_user_id` BIGINT DEFAULT NULL COMMENT 'Creator user id',
  `created_by_name` VARCHAR(64) DEFAULT NULL COMMENT 'Creator name',
  `deleted` TINYINT(1) DEFAULT '0',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_media_asset_folder_path` (`folder_path`),
  INDEX `idx_media_asset_folder_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Media asset folder registry';

INSERT INTO `media_asset_folder` (`id`,`folder_path`,`remark`,`protected_flag`,`deleted`)
VALUES (UUID_SHORT(),'/默认','默认文件夹（受保护）',1,0)
ON DUPLICATE KEY UPDATE
  `deleted`=0,
  `protected_flag`=1,
  `remark`=VALUES(`remark`);

INSERT INTO `media_asset_folder` (`id`,`folder_path`,`remark`,`protected_flag`,`deleted`)
VALUES (UUID_SHORT(),'/小程序上传图片','小程序上传图片',1,0)
ON DUPLICATE KEY UPDATE
  `deleted`=0,
  `protected_flag`=1,
  `remark`=COALESCE(`remark`, VALUES(`remark`));

UPDATE `media_asset`
SET `folder_path` = '/默认'
WHERE IFNULL(NULLIF(TRIM(`folder_path`),''),'/默认') = '/default';

UPDATE `media_asset`
SET `folder_path` = '/田块封面'
WHERE IFNULL(NULLIF(TRIM(`folder_path`),''),'/默认') IN ('/field','/田块图片');

INSERT INTO `media_asset_folder` (`id`,`folder_path`,`remark`,`protected_flag`,`deleted`)
VALUES (UUID_SHORT(),'/田块封面','田块封面（田块封面、田块卡片与详情展示）',0,0)
ON DUPLICATE KEY UPDATE
  `deleted`=0,
  `protected_flag`=0,
  `remark`='田块封面（田块封面、田块卡片与详情展示）';

UPDATE `media_asset`
SET `folder_path` = '/作物封面'
WHERE IFNULL(NULLIF(TRIM(`folder_path`),''),'/默认') = '/crop';

INSERT INTO `media_asset_folder` (`id`,`folder_path`,`remark`,`protected_flag`,`deleted`)
VALUES (UUID_SHORT(),'/作物封面','作物封面（作物分类与品种封面）',0,0)
ON DUPLICATE KEY UPDATE
  `deleted`=0,
  `protected_flag`=0,
  `remark`='作物封面（作物分类与品种封面）';

UPDATE `media_asset_folder`
SET `remark`='默认文件夹（受保护）', `protected_flag`=1, `deleted`=0
WHERE `folder_path`='/默认';

UPDATE `media_asset_folder`
SET `remark`='小程序上传图片', `protected_flag`=1, `deleted`=0
WHERE `folder_path`='/小程序上传图片';

UPDATE `media_asset_folder`
SET `remark`='田块封面（田块封面、田块卡片与详情展示）', `protected_flag`=0, `deleted`=0
WHERE `folder_path`='/田块封面';

UPDATE `media_asset_folder`
SET `remark`='作物封面（作物分类与品种封面）', `protected_flag`=0, `deleted`=0
WHERE `folder_path`='/作物封面';

UPDATE `media_asset_folder`
SET `deleted`=1
WHERE `folder_path` IN ('/field','/田块图片')
  AND `deleted`=0
  AND NOT EXISTS (
    SELECT 1
    FROM `media_asset` ma
    WHERE ma.`deleted`=0
      AND IFNULL(NULLIF(TRIM(ma.`folder_path`),''),'/默认') IN ('/field','/田块图片')
  );

UPDATE `media_asset_folder`
SET `deleted`=1
WHERE `folder_path`='/crop'
  AND `deleted`=0
  AND NOT EXISTS (
    SELECT 1
    FROM `media_asset` ma
    WHERE ma.`deleted`=0
      AND IFNULL(NULLIF(TRIM(ma.`folder_path`),''),'/默认') = '/crop'
  );

UPDATE `media_asset_folder`
SET `deleted`=1
WHERE `folder_path`='/default'
  AND `deleted`=0
  AND NOT EXISTS (
    SELECT 1
    FROM `media_asset` ma
    WHERE ma.`deleted`=0
      AND IFNULL(NULLIF(TRIM(ma.`folder_path`),''),'/默认') = '/default'
  );

UPDATE `media_asset`
SET `folder_path` = '/小程序上传图片'
WHERE `source_type` IN ('miniapp_upload','operator_upload')
  AND IFNULL(NULLIF(TRIM(`folder_path`),''),'/默认') IN ('/默认','/default');

INSERT INTO `media_asset_folder` (`id`,`folder_path`,`remark`,`protected_flag`,`deleted`)
SELECT UUID_SHORT() AS `id`,
       src.`folder_path`,
       'auto-discovered' AS `remark`,
       CASE WHEN src.`folder_path` IN ('/默认','/小程序上传图片') THEN 1 ELSE 0 END AS `protected_flag`,
       0 AS `deleted`
FROM (
  SELECT DISTINCT IFNULL(NULLIF(TRIM(`folder_path`),''),'/默认') AS `folder_path`
  FROM `media_asset`
) src
LEFT JOIN `media_asset_folder` mf ON mf.`folder_path` = src.`folder_path`
WHERE mf.`id` IS NULL;

UPDATE `media_asset_folder`
SET `deleted`=0,
    `protected_flag`=CASE WHEN `folder_path` IN ('/默认','/小程序上传图片') THEN 1 ELSE `protected_flag` END
WHERE `folder_path` IN (
  SELECT x.`folder_path`
  FROM (
    SELECT DISTINCT IFNULL(NULLIF(TRIM(`folder_path`),''),'/默认') AS `folder_path`
    FROM `media_asset`
  ) x
);

CREATE TABLE IF NOT EXISTS `media_asset_policy_config` (
  `id` BIGINT NOT NULL COMMENT 'Primary key',
  `miniapp_daily_upload_limit` INT NOT NULL DEFAULT '500' COMMENT 'Miniapp daily upload count limit,0=unlimited',
  `miniapp_daily_upload_size_mb` INT NOT NULL DEFAULT '2048' COMMENT 'Miniapp daily upload size limit(MB),0=unlimited',
  `miniapp_single_file_max_mb` INT NOT NULL DEFAULT '30' COMMENT 'Miniapp single file max size(MB),0=unlimited',
  `miniapp_allowed_file_types` VARCHAR(64) DEFAULT 'image,file' COMMENT 'Allowed file types for miniapp',
  `miniapp_require_review` TINYINT(1) NOT NULL DEFAULT '0' COMMENT 'Miniapp upload requires review',
  `admin_daily_upload_limit` INT NOT NULL DEFAULT '0' COMMENT 'Admin daily upload count limit,0=unlimited',
  `admin_daily_upload_size_mb` INT NOT NULL DEFAULT '0' COMMENT 'Admin daily upload size limit(MB),0=unlimited',
  `admin_single_file_max_mb` INT NOT NULL DEFAULT '0' COMMENT 'Admin single file max size(MB),0=unlimited',
  `admin_allowed_file_types` VARCHAR(64) DEFAULT 'image,file' COMMENT 'Allowed file types for admin',
  `admin_require_review` TINYINT(1) NOT NULL DEFAULT '0' COMMENT 'Admin upload requires review',
  `operator_daily_upload_limit` INT NOT NULL DEFAULT '500' COMMENT 'Operator daily upload count limit,0=unlimited',
  `operator_daily_upload_size_mb` INT NOT NULL DEFAULT '2048' COMMENT 'Operator daily upload size limit(MB),0=unlimited',
  `operator_single_file_max_mb` INT NOT NULL DEFAULT '30' COMMENT 'Operator single file max size(MB),0=unlimited',
  `operator_allowed_file_types` VARCHAR(64) DEFAULT 'image,file' COMMENT 'Allowed file types for operator',
  `operator_require_review` TINYINT(1) NOT NULL DEFAULT '0' COMMENT 'Operator upload requires review',
  `strict_source_purge_retain_days` INT NOT NULL DEFAULT '7' COMMENT 'Strict source purge retain days',
  `lock_password_hash` VARCHAR(255) DEFAULT NULL COMMENT 'Global asset lock password hash',
  `lock_password_updated_at` DATETIME DEFAULT NULL COMMENT 'Global asset lock password updated time',
  `lock_password_updated_by_user_id` BIGINT DEFAULT NULL COMMENT 'Global asset lock password updated by user id',
  `lock_password_updated_by_name` VARCHAR(64) DEFAULT NULL COMMENT 'Global asset lock password updated by name',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT 'Remark',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Media asset upload policy config';

SET @ddl_policy_miniapp_daily_upload_limit = (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'media_asset_policy_config' AND COLUMN_NAME = 'miniapp_daily_upload_limit'
    ),
    'SELECT 1',
    'ALTER TABLE `media_asset_policy_config` ADD COLUMN `miniapp_daily_upload_limit` INT NOT NULL DEFAULT 500 COMMENT ''Miniapp daily upload count limit,0=unlimited'''
  )
);
PREPARE stmt_policy_miniapp_daily_upload_limit FROM @ddl_policy_miniapp_daily_upload_limit;
EXECUTE stmt_policy_miniapp_daily_upload_limit;
DEALLOCATE PREPARE stmt_policy_miniapp_daily_upload_limit;

SET @ddl_policy_miniapp_daily_upload_size_mb = (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'media_asset_policy_config' AND COLUMN_NAME = 'miniapp_daily_upload_size_mb'
    ),
    'SELECT 1',
    'ALTER TABLE `media_asset_policy_config` ADD COLUMN `miniapp_daily_upload_size_mb` INT NOT NULL DEFAULT 2048 COMMENT ''Miniapp daily upload size limit(MB),0=unlimited'''
  )
);
PREPARE stmt_policy_miniapp_daily_upload_size_mb FROM @ddl_policy_miniapp_daily_upload_size_mb;
EXECUTE stmt_policy_miniapp_daily_upload_size_mb;
DEALLOCATE PREPARE stmt_policy_miniapp_daily_upload_size_mb;

SET @ddl_policy_miniapp_single_file_max_mb = (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'media_asset_policy_config' AND COLUMN_NAME = 'miniapp_single_file_max_mb'
    ),
    'SELECT 1',
    'ALTER TABLE `media_asset_policy_config` ADD COLUMN `miniapp_single_file_max_mb` INT NOT NULL DEFAULT 30 COMMENT ''Miniapp single file max size(MB),0=unlimited'''
  )
);
PREPARE stmt_policy_miniapp_single_file_max_mb FROM @ddl_policy_miniapp_single_file_max_mb;
EXECUTE stmt_policy_miniapp_single_file_max_mb;
DEALLOCATE PREPARE stmt_policy_miniapp_single_file_max_mb;

SET @ddl_policy_miniapp_allowed_file_types = (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'media_asset_policy_config' AND COLUMN_NAME = 'miniapp_allowed_file_types'
    ),
    'SELECT 1',
    'ALTER TABLE `media_asset_policy_config` ADD COLUMN `miniapp_allowed_file_types` VARCHAR(64) DEFAULT ''image,file'' COMMENT ''Allowed file types for miniapp'''
  )
);
PREPARE stmt_policy_miniapp_allowed_file_types FROM @ddl_policy_miniapp_allowed_file_types;
EXECUTE stmt_policy_miniapp_allowed_file_types;
DEALLOCATE PREPARE stmt_policy_miniapp_allowed_file_types;

SET @ddl_policy_miniapp_require_review = (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'media_asset_policy_config' AND COLUMN_NAME = 'miniapp_require_review'
    ),
    'SELECT 1',
    'ALTER TABLE `media_asset_policy_config` ADD COLUMN `miniapp_require_review` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''Miniapp upload requires review'''
  )
);
PREPARE stmt_policy_miniapp_require_review FROM @ddl_policy_miniapp_require_review;
EXECUTE stmt_policy_miniapp_require_review;
DEALLOCATE PREPARE stmt_policy_miniapp_require_review;

SET @ddl_policy_admin_daily_upload_limit = (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'media_asset_policy_config' AND COLUMN_NAME = 'admin_daily_upload_limit'
    ),
    'SELECT 1',
    'ALTER TABLE `media_asset_policy_config` ADD COLUMN `admin_daily_upload_limit` INT NOT NULL DEFAULT 0 COMMENT ''Admin daily upload count limit,0=unlimited'''
  )
);
PREPARE stmt_policy_admin_daily_upload_limit FROM @ddl_policy_admin_daily_upload_limit;
EXECUTE stmt_policy_admin_daily_upload_limit;
DEALLOCATE PREPARE stmt_policy_admin_daily_upload_limit;

SET @ddl_policy_admin_daily_upload_size_mb = (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'media_asset_policy_config' AND COLUMN_NAME = 'admin_daily_upload_size_mb'
    ),
    'SELECT 1',
    'ALTER TABLE `media_asset_policy_config` ADD COLUMN `admin_daily_upload_size_mb` INT NOT NULL DEFAULT 0 COMMENT ''Admin daily upload size limit(MB),0=unlimited'''
  )
);
PREPARE stmt_policy_admin_daily_upload_size_mb FROM @ddl_policy_admin_daily_upload_size_mb;
EXECUTE stmt_policy_admin_daily_upload_size_mb;
DEALLOCATE PREPARE stmt_policy_admin_daily_upload_size_mb;

SET @ddl_policy_admin_single_file_max_mb = (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'media_asset_policy_config' AND COLUMN_NAME = 'admin_single_file_max_mb'
    ),
    'SELECT 1',
    'ALTER TABLE `media_asset_policy_config` ADD COLUMN `admin_single_file_max_mb` INT NOT NULL DEFAULT 0 COMMENT ''Admin single file max size(MB),0=unlimited'''
  )
);
PREPARE stmt_policy_admin_single_file_max_mb FROM @ddl_policy_admin_single_file_max_mb;
EXECUTE stmt_policy_admin_single_file_max_mb;
DEALLOCATE PREPARE stmt_policy_admin_single_file_max_mb;

SET @ddl_policy_admin_allowed_file_types = (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'media_asset_policy_config' AND COLUMN_NAME = 'admin_allowed_file_types'
    ),
    'SELECT 1',
    'ALTER TABLE `media_asset_policy_config` ADD COLUMN `admin_allowed_file_types` VARCHAR(64) DEFAULT ''image,file'' COMMENT ''Allowed file types for admin'''
  )
);
PREPARE stmt_policy_admin_allowed_file_types FROM @ddl_policy_admin_allowed_file_types;
EXECUTE stmt_policy_admin_allowed_file_types;
DEALLOCATE PREPARE stmt_policy_admin_allowed_file_types;

SET @ddl_policy_admin_require_review = (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'media_asset_policy_config' AND COLUMN_NAME = 'admin_require_review'
    ),
    'SELECT 1',
    'ALTER TABLE `media_asset_policy_config` ADD COLUMN `admin_require_review` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''Admin upload requires review'''
  )
);
PREPARE stmt_policy_admin_require_review FROM @ddl_policy_admin_require_review;
EXECUTE stmt_policy_admin_require_review;
DEALLOCATE PREPARE stmt_policy_admin_require_review;

SET @ddl_policy_lock_password_hash = (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'media_asset_policy_config' AND COLUMN_NAME = 'lock_password_hash'
    ),
    'SELECT 1',
    'ALTER TABLE `media_asset_policy_config` ADD COLUMN `lock_password_hash` VARCHAR(255) DEFAULT NULL COMMENT ''Global asset lock password hash'''
  )
);
PREPARE stmt_policy_lock_password_hash FROM @ddl_policy_lock_password_hash;
EXECUTE stmt_policy_lock_password_hash;
DEALLOCATE PREPARE stmt_policy_lock_password_hash;

SET @ddl_policy_lock_password_updated_at = (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'media_asset_policy_config' AND COLUMN_NAME = 'lock_password_updated_at'
    ),
    'SELECT 1',
    'ALTER TABLE `media_asset_policy_config` ADD COLUMN `lock_password_updated_at` DATETIME DEFAULT NULL COMMENT ''Global asset lock password updated time'''
  )
);
PREPARE stmt_policy_lock_password_updated_at FROM @ddl_policy_lock_password_updated_at;
EXECUTE stmt_policy_lock_password_updated_at;
DEALLOCATE PREPARE stmt_policy_lock_password_updated_at;

SET @ddl_policy_lock_password_updated_by_user_id = (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'media_asset_policy_config' AND COLUMN_NAME = 'lock_password_updated_by_user_id'
    ),
    'SELECT 1',
    'ALTER TABLE `media_asset_policy_config` ADD COLUMN `lock_password_updated_by_user_id` BIGINT DEFAULT NULL COMMENT ''Global asset lock password updated by user id'''
  )
);
PREPARE stmt_policy_lock_password_updated_by_user_id FROM @ddl_policy_lock_password_updated_by_user_id;
EXECUTE stmt_policy_lock_password_updated_by_user_id;
DEALLOCATE PREPARE stmt_policy_lock_password_updated_by_user_id;

SET @ddl_policy_lock_password_updated_by_name = (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'media_asset_policy_config' AND COLUMN_NAME = 'lock_password_updated_by_name'
    ),
    'SELECT 1',
    'ALTER TABLE `media_asset_policy_config` ADD COLUMN `lock_password_updated_by_name` VARCHAR(64) DEFAULT NULL COMMENT ''Global asset lock password updated by name'''
  )
);
PREPARE stmt_policy_lock_password_updated_by_name FROM @ddl_policy_lock_password_updated_by_name;
EXECUTE stmt_policy_lock_password_updated_by_name;
DEALLOCATE PREPARE stmt_policy_lock_password_updated_by_name;

INSERT INTO `media_asset_policy_config`
(`id`,`miniapp_daily_upload_limit`,`miniapp_daily_upload_size_mb`,`miniapp_single_file_max_mb`,`miniapp_allowed_file_types`,`miniapp_require_review`,`admin_daily_upload_limit`,`admin_daily_upload_size_mb`,`admin_single_file_max_mb`,`admin_allowed_file_types`,`admin_require_review`,`operator_daily_upload_limit`,`operator_daily_upload_size_mb`,`operator_single_file_max_mb`,`operator_allowed_file_types`,`operator_require_review`,`strict_source_purge_retain_days`,`lock_password_hash`,`lock_password_updated_at`,`lock_password_updated_by_user_id`,`lock_password_updated_by_name`,`remark`)
VALUES
(1,500,2048,30,'image,file',0,0,0,0,'image,file',0,500,2048,30,'image,file',0,7,NULL,NULL,NULL,NULL,'default')
ON DUPLICATE KEY UPDATE
  `miniapp_daily_upload_limit` = IFNULL(`miniapp_daily_upload_limit`, IFNULL(`operator_daily_upload_limit`, 500)),
  `miniapp_daily_upload_size_mb` = IFNULL(`miniapp_daily_upload_size_mb`, IFNULL(`operator_daily_upload_size_mb`, 2048)),
  `miniapp_single_file_max_mb` = IFNULL(`miniapp_single_file_max_mb`, IFNULL(`operator_single_file_max_mb`, 30)),
  `miniapp_allowed_file_types` = IFNULL(NULLIF(TRIM(`miniapp_allowed_file_types`), ''), IFNULL(NULLIF(TRIM(`operator_allowed_file_types`), ''), 'image,file')),
  `miniapp_require_review` = IFNULL(`miniapp_require_review`, IFNULL(`operator_require_review`, 0)),
  `admin_daily_upload_limit` = IFNULL(`admin_daily_upload_limit`, 0),
  `admin_daily_upload_size_mb` = IFNULL(`admin_daily_upload_size_mb`, 0),
  `admin_single_file_max_mb` = IFNULL(`admin_single_file_max_mb`, 0),
  `admin_allowed_file_types` = IFNULL(NULLIF(TRIM(`admin_allowed_file_types`), ''), 'image,file'),
  `admin_require_review` = IFNULL(`admin_require_review`, 0),
  `operator_daily_upload_limit` = IFNULL(`operator_daily_upload_limit`, 500),
  `operator_daily_upload_size_mb` = IFNULL(`operator_daily_upload_size_mb`, 2048),
  `operator_single_file_max_mb` = IFNULL(`operator_single_file_max_mb`, 30),
  `operator_allowed_file_types` = IFNULL(NULLIF(TRIM(`operator_allowed_file_types`), ''), 'image,file'),
  `operator_require_review` = IFNULL(`operator_require_review`, 0),
  `strict_source_purge_retain_days` = IFNULL(`strict_source_purge_retain_days`, 7),
  `lock_password_hash` = `lock_password_hash`,
  `lock_password_updated_at` = `lock_password_updated_at`,
  `lock_password_updated_by_user_id` = `lock_password_updated_by_user_id`,
  `lock_password_updated_by_name` = `lock_password_updated_by_name`;
