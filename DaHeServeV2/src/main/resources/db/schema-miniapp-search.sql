-- DaHe V2 database init - Miniapp search term module

CREATE DATABASE IF NOT EXISTS `dahe_v2` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE `dahe_v2`;

CREATE TABLE IF NOT EXISTS `miniapp_search_term` (
  `id` BIGINT NOT NULL COMMENT 'Primary key',
  `scene_key` VARCHAR(32) NOT NULL COMMENT 'Suggestion scene key',
  `entity_type` VARCHAR(32) NOT NULL COMMENT 'Entity type',
  `entity_id` BIGINT NOT NULL COMMENT 'Entity id',
  `term_type` VARCHAR(32) NOT NULL COMMENT 'Term type',
  `type_label` VARCHAR(32) NOT NULL COMMENT 'Display type label',
  `label` VARCHAR(191) NOT NULL COMMENT 'Display label',
  `value_text` VARCHAR(191) NOT NULL COMMENT 'Applied keyword value',
  `search_text` VARCHAR(255) NOT NULL COMMENT 'Normalized search text',
  `search_compact` VARCHAR(255) NOT NULL COMMENT 'Compacted search text',
  `pinyin_full` VARCHAR(255) DEFAULT NULL COMMENT 'Pinyin full text',
  `pinyin_initials` VARCHAR(255) DEFAULT NULL COMMENT 'Pinyin initials',
  `term_key_hash` CHAR(32) NOT NULL COMMENT 'Stable unique term hash',
  `sort_weight` INT NOT NULL DEFAULT 0 COMMENT 'Display weight',
  `deleted` TINYINT(1) DEFAULT 0,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_search_term_hash` (`term_key_hash`),
  INDEX `idx_search_scene_text` (`scene_key`, `search_text`),
  INDEX `idx_search_scene_compact` (`scene_key`, `search_compact`),
  INDEX `idx_search_scene_pinyin_full` (`scene_key`, `pinyin_full`),
  INDEX `idx_search_scene_pinyin_initials` (`scene_key`, `pinyin_initials`),
  INDEX `idx_search_entity` (`entity_type`, `entity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Miniapp internal search projection';
