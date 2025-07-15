-- DaHe V2 数据库初始化脚本 - 企业介绍模块

USE `dahe_v2`;

CREATE TABLE IF NOT EXISTS `company_info` (
  `id` BIGINT NOT NULL COMMENT 'Primary key',
  `company_name` VARCHAR(100) DEFAULT NULL COMMENT 'Company name',
  `logo` VARCHAR(512) DEFAULT NULL COMMENT 'Company logo url',
  `banner` VARCHAR(512) DEFAULT NULL COMMENT 'Company banner url',
  `introduction` VARCHAR(2000) DEFAULT NULL COMMENT 'Company introduction',
  `mission` VARCHAR(500) DEFAULT NULL COMMENT 'Company mission',
  `copyright` VARCHAR(255) DEFAULT NULL COMMENT 'Copyright text',
  `sort_order` INT DEFAULT 0 COMMENT 'Sort order',
  `status` TINYINT(1) DEFAULT 1 COMMENT '1 enabled,0 disabled',
  `deleted` TINYINT(1) DEFAULT 0,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_company_info_status` (`status`,`deleted`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Company basic info';

CREATE TABLE IF NOT EXISTS `company_product` (
  `id` BIGINT NOT NULL COMMENT 'Primary key',
  `name` VARCHAR(100) DEFAULT NULL COMMENT 'Product name',
  `description` VARCHAR(500) DEFAULT NULL COMMENT 'Product description',
  `image` VARCHAR(512) DEFAULT NULL COMMENT 'Product image url',
  `sort_order` INT DEFAULT 0 COMMENT 'Sort order',
  `status` TINYINT(1) DEFAULT 1 COMMENT '1 enabled,0 disabled',
  `deleted` TINYINT(1) DEFAULT 0,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_company_product_status` (`status`,`deleted`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Company core products';

CREATE TABLE IF NOT EXISTS `company_honor` (
  `id` BIGINT NOT NULL COMMENT 'Primary key',
  `name` VARCHAR(100) DEFAULT NULL COMMENT 'Honor name',
  `image` VARCHAR(512) DEFAULT NULL COMMENT 'Honor image url',
  `sort_order` INT DEFAULT 0 COMMENT 'Sort order',
  `status` TINYINT(1) DEFAULT 1 COMMENT '1 enabled,0 disabled',
  `deleted` TINYINT(1) DEFAULT 0,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_company_honor_status` (`status`,`deleted`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Company honors';

CREATE TABLE IF NOT EXISTS `company_contact` (
  `id` BIGINT NOT NULL COMMENT 'Primary key',
  `contact_type` VARCHAR(32) DEFAULT NULL COMMENT 'address/phone/email/website',
  `contact_label` VARCHAR(50) DEFAULT NULL COMMENT 'Label text',
  `contact_value` VARCHAR(255) DEFAULT NULL COMMENT 'Contact value',
  `sort_order` INT DEFAULT 0 COMMENT 'Sort order',
  `status` TINYINT(1) DEFAULT 1 COMMENT '1 enabled,0 disabled',
  `deleted` TINYINT(1) DEFAULT 0,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_company_contact_status` (`status`,`deleted`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Company contacts';

UPDATE `company_info` SET `sort_order`=0 WHERE `sort_order` IS NULL;
UPDATE `company_info` SET `status`=1 WHERE `status` IS NULL;
UPDATE `company_info` SET `deleted`=0 WHERE `deleted` IS NULL;

UPDATE `company_product` SET `sort_order`=0 WHERE `sort_order` IS NULL;
UPDATE `company_product` SET `status`=1 WHERE `status` IS NULL;
UPDATE `company_product` SET `deleted`=0 WHERE `deleted` IS NULL;

UPDATE `company_honor` SET `sort_order`=0 WHERE `sort_order` IS NULL;
UPDATE `company_honor` SET `status`=1 WHERE `status` IS NULL;
UPDATE `company_honor` SET `deleted`=0 WHERE `deleted` IS NULL;

UPDATE `company_contact` SET `sort_order`=0 WHERE `sort_order` IS NULL;
UPDATE `company_contact` SET `status`=1 WHERE `status` IS NULL;
UPDATE `company_contact` SET `deleted`=0 WHERE `deleted` IS NULL;

INSERT INTO `company_info` (`id`,`company_name`,`introduction`,`mission`,`copyright`,`sort_order`,`status`,`deleted`)
SELECT 1, '大禾种业', '大禾种业围绕“田块分布、农事管理、种子质量”三大业务持续完善作业标准。', '以数字化手段服务农业生产，提升一线协同效率。', 'Copyright © 大禾种业', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `company_info` WHERE `deleted`=0);

INSERT INTO `company_contact` (`id`,`contact_type`,`contact_label`,`contact_value`,`sort_order`,`status`,`deleted`)
SELECT 11, 'phone', '联系电话', '400-800-1234', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `company_contact` WHERE `contact_type`='phone' AND `deleted`=0);

INSERT INTO `company_contact` (`id`,`contact_type`,`contact_label`,`contact_value`,`sort_order`,`status`,`deleted`)
SELECT 12, 'email', '联系邮箱', 'service@dahe.example.com', 2, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `company_contact` WHERE `contact_type`='email' AND `deleted`=0);

INSERT INTO `company_contact` (`id`,`contact_type`,`contact_label`,`contact_value`,`sort_order`,`status`,`deleted`)
SELECT 13, 'address', '公司地址', '山东省济南市高新区示范路 18 号', 3, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `company_contact` WHERE `contact_type`='address' AND `deleted`=0);
