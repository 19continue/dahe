-- DaHe V2 database init - export module
USE `dahe_v2`;

CREATE TABLE IF NOT EXISTS `export_field_dict` (
  `id` BIGINT NOT NULL COMMENT 'Primary key',
  `module_key` VARCHAR(32) NOT NULL COMMENT 'Module key: farm/seed',
  `field_code` VARCHAR(64) NOT NULL COMMENT 'Field code',
  `field_name` VARCHAR(128) NOT NULL COMMENT 'Field display name',
  `data_type` VARCHAR(32) DEFAULT 'string' COMMENT 'string/number/date/datetime/json',
  `description` VARCHAR(255) DEFAULT NULL COMMENT 'Field description',
  `example_value` VARCHAR(255) DEFAULT NULL COMMENT 'Example value',
  `deleted` TINYINT(1) DEFAULT '0',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_export_field_code` (`module_key`, `field_code`),
  INDEX `idx_export_field_module` (`module_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Export field dictionary';

CREATE TABLE IF NOT EXISTS `export_template` (
  `id` BIGINT NOT NULL COMMENT 'Primary key',
  `module_key` VARCHAR(32) NOT NULL COMMENT 'Module key',
  `template_code` VARCHAR(64) NOT NULL COMMENT 'Template code',
  `template_name` VARCHAR(128) NOT NULL COMMENT 'Template name',
  `version_no` INT NOT NULL DEFAULT '1' COMMENT 'Version number',
  `status` VARCHAR(16) DEFAULT 'enabled' COMMENT 'enabled/disabled',
  `fields_json` LONGTEXT NOT NULL COMMENT 'Ordered field codes JSON array',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT 'Remark',
  `deleted` TINYINT(1) DEFAULT '0',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_export_template_ver` (`module_key`, `template_code`, `version_no`),
  INDEX `idx_export_template_status` (`module_key`, `template_code`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Export template';

INSERT INTO `export_field_dict` (`id`, `module_key`, `field_code`, `field_name`, `data_type`, `description`, `example_value`, `deleted`)
VALUES
  (93001, 'farm', 'recordId', '记录ID', 'number', NULL, '51001', 0),
  (93002, 'farm', 'fieldId', '田块ID', 'number', NULL, '41001', 0),
  (93003, 'farm', 'fieldName', '田块名称', 'string', NULL, '东一号田', 0),
  (93004, 'farm', 'cycleId', '种植计划ID', 'number', NULL, '81001', 0),
  (93005, 'farm', 'stepId', '步骤ID', 'number', NULL, '3001', 0),
  (93006, 'farm', 'stepName', '步骤名称', 'string', NULL, '整地', 0),
  (93008, 'farm', 'workDate', '作业时间', 'datetime', NULL, '2026-02-10 07:55:00', 0),
  (93009, 'farm', 'operatorName', '操作员', 'string', NULL, '王磊', 0),
  (93010, 'farm', 'weather', '天气', 'string', NULL, '多云', 0),
  (93011, 'farm', 'temperature', '温度', 'string', NULL, '16', 0),
  (93012, 'farm', 'weatherLocation', '天气位置', 'string', NULL, '东河镇', 0),
  (93015, 'farm', 'humidity', '湿度(%)', 'string', NULL, '68', 0),
  (93016, 'farm', 'windDirection', '风向', 'string', NULL, '北风', 0),
  (93017, 'farm', 'windPower', '风力', 'string', NULL, '3级', 0),
  (93018, 'farm', 'weatherReportTime', '天气发布时间', 'string', NULL, '2026-02-10 07:50:00', 0),
  (93013, 'farm', 'notes', '备注', 'string', NULL, '播深约4cm', 0),
  (93014, 'farm', 'extraJson', '动态参数', 'json', NULL, '{\"amountPerMu\":\"12\"}', 0),
  (93021, 'seed', 'testId', '检测ID', 'number', NULL, '71001', 0),
  (93022, 'seed', 'batchId', '批次ID', 'number', NULL, '61001', 0),
  (93023, 'seed', 'batchCode', '批次号', 'string', NULL, 'DH-2026-CORN-001', 0),
  (930236, 'seed', 'cropType', '作物', 'string', NULL, '玉米', 0),
  (93024, 'seed', 'varietyName', '品种名', 'string', NULL, '先玉335', 0),
  (93025, 'seed', 'testDate', '检测日期', 'date', NULL, '2026-01-20', 0),
  (93027, 'seed', 'sampleCount', '芽率样本数', 'number', NULL, '100', 0),
  (930271, 'seed', 'germinationCount', '发芽数量', 'number', NULL, '96', 0),
  (93028, 'seed', 'germinationRate', '芽率(%)', 'number', NULL, '95.6', 0),
  (93030, 'seed', 'moisture', '水分(%)', 'number', NULL, '12.4', 0),
  (93029, 'seed', 'purity', '纯度(%)', 'number', NULL, '99.1', 0),
  (93031, 'seed', 'cleanliness', '净度(%)', 'number', NULL, '98.6', 0),
  (93033, 'seed', 'testerName', '检测员', 'string', NULL, '王蕾', 0),
  (93034, 'seed', 'remark', '备注', 'string', NULL, '入库首检合格', 0),
  (93035, 'seed', 'createdAt', '创建时间', 'datetime', NULL, '2026-01-20 10:00:00', 0)
ON DUPLICATE KEY UPDATE
  `field_name` = VALUES(`field_name`),
  `data_type` = VALUES(`data_type`),
  `description` = VALUES(`description`),
  `example_value` = VALUES(`example_value`),
  `deleted` = 0;

INSERT INTO `export_template` (`id`, `module_key`, `template_code`, `template_name`, `version_no`, `status`, `fields_json`, `remark`, `deleted`)
VALUES
  (94001, 'farm', 'farm_records_standard', '农事记录标准模板', 1, 'enabled', '[\"recordId\",\"fieldId\",\"fieldName\",\"cycleId\",\"stepId\",\"stepName\",\"workDate\",\"operatorName\",\"weather\",\"temperature\",\"weatherLocation\",\"humidity\",\"windDirection\",\"windPower\",\"weatherReportTime\",\"notes\",\"extraJson\"]', 'default', 0),
  (94002, 'seed', 'seed_tests_standard', '种子检测标准模板', 1, 'enabled', '[\"testId\",\"batchId\",\"batchCode\",\"cropType\",\"varietyName\",\"testDate\",\"sampleCount\",\"germinationCount\",\"germinationRate\",\"moisture\",\"purity\",\"cleanliness\",\"testerName\",\"remark\",\"createdAt\"]', 'default', 0)
ON DUPLICATE KEY UPDATE
  `template_name` = VALUES(`template_name`),
  `status` = VALUES(`status`),
  `fields_json` = VALUES(`fields_json`),
  `remark` = VALUES(`remark`),
  `deleted` = 0;
