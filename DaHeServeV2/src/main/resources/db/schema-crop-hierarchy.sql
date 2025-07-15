-- DaHe V2 crop hierarchy migration (UTF-8)
USE `dahe_v2`;

-- 1) add columns
SET @ddl_crop_node_type = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `crop` ADD COLUMN `node_type` VARCHAR(16) DEFAULT ''variety'' COMMENT ''category/variety''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crop' AND COLUMN_NAME = 'node_type'
);
PREPARE stmt_crop_node_type FROM @ddl_crop_node_type;
EXECUTE stmt_crop_node_type;
DEALLOCATE PREPARE stmt_crop_node_type;

SET @ddl_crop_parent_id = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `crop` ADD COLUMN `parent_id` BIGINT DEFAULT NULL COMMENT ''Parent category id''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crop' AND COLUMN_NAME = 'parent_id'
);
PREPARE stmt_crop_parent_id FROM @ddl_crop_parent_id;
EXECUTE stmt_crop_parent_id;
DEALLOCATE PREPARE stmt_crop_parent_id;

SET @ddl_crop_image_url = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `crop` ADD COLUMN `image_url` VARCHAR(512) DEFAULT NULL COMMENT ''Category/variety image url''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crop' AND COLUMN_NAME = 'image_url'
);
PREPARE stmt_crop_image_url FROM @ddl_crop_image_url;
EXECUTE stmt_crop_image_url;
DEALLOCATE PREPARE stmt_crop_image_url;

-- 2) add indexes
SET @ddl_idx_crop_node_type = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `crop` ADD INDEX `idx_crop_node_type` (`node_type`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crop' AND INDEX_NAME = 'idx_crop_node_type'
);
PREPARE stmt_idx_crop_node_type FROM @ddl_idx_crop_node_type;
EXECUTE stmt_idx_crop_node_type;
DEALLOCATE PREPARE stmt_idx_crop_node_type;

SET @ddl_idx_crop_parent = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `crop` ADD INDEX `idx_crop_parent` (`parent_id`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crop' AND INDEX_NAME = 'idx_crop_parent'
);
PREPARE stmt_idx_crop_parent FROM @ddl_idx_crop_parent;
EXECUTE stmt_idx_crop_parent;
DEALLOCATE PREPARE stmt_idx_crop_parent;

-- 3) classify old rows
UPDATE `crop`
SET `node_type`='category', `parent_id`=NULL
WHERE (`node_type` IS NULL OR `node_type`='variety')
  AND (`variety` IS NULL OR TRIM(`variety`)='')
  AND (`parent_id` IS NULL OR `parent_id`=0);

UPDATE `crop`
SET `node_type`='variety'
WHERE `node_type` IS NULL OR TRIM(`node_type`)='';

-- 4) create missing category nodes (UUID_SHORT fits BIGINT)
INSERT INTO `crop` (`id`,`name`,`variety`,`node_type`,`parent_id`,`image_url`,`sort_order`,`deleted`)
SELECT
  UUID_SHORT(),
  base.`name`,
  NULL,
  'category',
  NULL,
  NULL,
  base.`min_sort`,
  0
FROM (
  SELECT TRIM(`name`) AS `name`, COALESCE(MIN(`sort_order`), 0) AS `min_sort`
  FROM `crop`
  WHERE `deleted`=0 AND `name` IS NOT NULL AND TRIM(`name`)<>''
  GROUP BY TRIM(`name`)
) base
LEFT JOIN `crop` c
  ON c.`deleted`=0 AND c.`node_type`='category' AND c.`name`=base.`name`
WHERE c.`id` IS NULL;

-- 5) bind varieties to category parent
UPDATE `crop` v
JOIN `crop` c
  ON c.`deleted`=0
 AND c.`node_type`='category'
 AND c.`name`=v.`name`
SET v.`node_type`='variety',
    v.`parent_id`=c.`id`
WHERE v.`deleted`=0
  AND (v.`node_type`<>'category' OR v.`node_type` IS NULL)
  AND (v.`parent_id` IS NULL OR v.`parent_id`=0);

-- 6) deduplicate rows for unique constraints
UPDATE `crop` c1
JOIN `crop` c2
  ON c1.`deleted`=0
 AND c2.`deleted`=0
 AND c1.`node_type`='category'
 AND c2.`node_type`='category'
 AND TRIM(c1.`name`)=TRIM(c2.`name`)
 AND c1.`id` > c2.`id`
SET c1.`deleted`=1;

UPDATE `crop` v1
JOIN `crop` v2
  ON v1.`deleted`=0
 AND v2.`deleted`=0
 AND v1.`node_type`='variety'
 AND v2.`node_type`='variety'
 AND v1.`parent_id`=v2.`parent_id`
 AND TRIM(v1.`variety`)=TRIM(v2.`variety`)
 AND v1.`id` > v2.`id`
SET v1.`deleted`=1;

-- 7) add generated active-key columns
SET @ddl_crop_category_active_name = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `crop` ADD COLUMN `category_active_name` VARCHAR(191) GENERATED ALWAYS AS (CASE WHEN `deleted`=0 AND `node_type`=''category'' AND `name` IS NOT NULL AND TRIM(`name`)<>'''' THEN TRIM(`name`) ELSE NULL END) STORED COMMENT ''Active category unique key''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crop' AND COLUMN_NAME = 'category_active_name'
);
PREPARE stmt_crop_category_active_name FROM @ddl_crop_category_active_name;
EXECUTE stmt_crop_category_active_name;
DEALLOCATE PREPARE stmt_crop_category_active_name;

SET @ddl_crop_variety_active_key = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `crop` ADD COLUMN `variety_active_key` VARCHAR(255) GENERATED ALWAYS AS (CASE WHEN `deleted`=0 AND `node_type`=''variety'' AND `parent_id` IS NOT NULL AND `variety` IS NOT NULL AND TRIM(`variety`)<>'''' THEN CONCAT(`parent_id`,''#'',TRIM(`variety`)) ELSE NULL END) STORED COMMENT ''Active variety unique key''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crop' AND COLUMN_NAME = 'variety_active_key'
);
PREPARE stmt_crop_variety_active_key FROM @ddl_crop_variety_active_key;
EXECUTE stmt_crop_variety_active_key;
DEALLOCATE PREPARE stmt_crop_variety_active_key;

-- 8) drop wrong old unique indexes
SET @ddl_drop_uk_crop_category_name = (
  SELECT IF(
    COUNT(*) > 0,
    'ALTER TABLE `crop` DROP INDEX `uk_crop_category_name`',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crop' AND INDEX_NAME = 'uk_crop_category_name'
);
PREPARE stmt_drop_uk_crop_category_name FROM @ddl_drop_uk_crop_category_name;
EXECUTE stmt_drop_uk_crop_category_name;
DEALLOCATE PREPARE stmt_drop_uk_crop_category_name;

SET @ddl_drop_uk_crop_variety_parent = (
  SELECT IF(
    COUNT(*) > 0,
    'ALTER TABLE `crop` DROP INDEX `uk_crop_variety_parent`',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crop' AND INDEX_NAME = 'uk_crop_variety_parent'
);
PREPARE stmt_drop_uk_crop_variety_parent FROM @ddl_drop_uk_crop_variety_parent;
EXECUTE stmt_drop_uk_crop_variety_parent;
DEALLOCATE PREPARE stmt_drop_uk_crop_variety_parent;

-- 9) add corrected unique indexes
SET @ddl_uk_crop_category_active_name = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `crop` ADD UNIQUE KEY `uk_crop_category_active_name` (`category_active_name`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crop' AND INDEX_NAME = 'uk_crop_category_active_name'
);
PREPARE stmt_uk_crop_category_active_name FROM @ddl_uk_crop_category_active_name;
EXECUTE stmt_uk_crop_category_active_name;
DEALLOCATE PREPARE stmt_uk_crop_category_active_name;

SET @ddl_uk_crop_variety_active_key = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `crop` ADD UNIQUE KEY `uk_crop_variety_active_key` (`variety_active_key`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crop' AND INDEX_NAME = 'uk_crop_variety_active_key'
);
PREPARE stmt_uk_crop_variety_active_key FROM @ddl_uk_crop_variety_active_key;
EXECUTE stmt_uk_crop_variety_active_key;
DEALLOCATE PREPARE stmt_uk_crop_variety_active_key;
