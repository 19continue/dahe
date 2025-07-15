package com.dahe.v2.config;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 启动期数据库结构自举器。
 *
 * <p>目标：在不依赖手工迁移的前提下，为存量环境补齐表、字段、索引及基础数据，
 * 保证各业务模块能够在统一结构上运行。</p>
 */
@Component
public class SchemaBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaBootstrapRunner.class);
    private static final String DEFAULT_ADMIN_PASSWORD_HASH = "$2a$10$ExTSLfeUeLwaUDYd2u4jI.ouGmbHKmklUcU9tlyx9RYcB6.QCkTqm";

    private final JdbcTemplate jdbcTemplate;

    public SchemaBootstrapRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 应用启动后执行的自举入口。
     *
     * <p>按模块顺序串行执行，单个模块异常仅记录日志，不中断后续模块。</p>
     */
    @Override
    public void run(ApplicationArguments args) {
        bootstrapUserModule();
        bootstrapTerminologyDictModule();
        bootstrapAdminRoleModule();
        bootstrapDynamicConfigModule();
        bootstrapSeedCompatibility();
        bootstrapAmapModule();
        bootstrapExportModule();
        bootstrapFarmPolicyModule();
        bootstrapAssetModule();
        bootstrapCompanyModule();
        bootstrapOperationLogModule();
    }

    /**
     * 用户体系兼容自举：
     * 补齐 user/user_notice 结构并做历史数据归一化。
     */
    private void bootstrapUserModule() {
        try {
            if (!tableExists("user")) {
                return;
            }
            ensureColumn("user", "user_type", "ALTER TABLE `user` ADD COLUMN `user_type` VARCHAR(16) DEFAULT 'miniapp' COMMENT 'miniapp/admin'");
            ensureColumn("user", "login_name", "ALTER TABLE `user` ADD COLUMN `login_name` VARCHAR(64) DEFAULT NULL COMMENT 'admin login account'");
            ensureColumn("user", "password_hash", "ALTER TABLE `user` ADD COLUMN `password_hash` VARCHAR(255) DEFAULT NULL COMMENT 'admin password hash'");
            ensureColumn("user", "avatar_url", "ALTER TABLE `user` ADD COLUMN `avatar_url` VARCHAR(512) DEFAULT NULL COMMENT 'Current avatar url'");
            ensureColumn("user", "wx_avatar_url", "ALTER TABLE `user` ADD COLUMN `wx_avatar_url` VARCHAR(512) DEFAULT NULL COMMENT 'WeChat avatar url'");
            ensureColumn("user", "avatar_source", "ALTER TABLE `user` ADD COLUMN `avatar_source` VARCHAR(16) DEFAULT 'none' COMMENT 'none/wx/upload/admin'");
            ensureColumn("user", "enabled", "ALTER TABLE `user` ADD COLUMN `enabled` TINYINT(1) DEFAULT 1 COMMENT '1 enabled,0 disabled'");
            ensureColumn("user", "is_super_admin", "ALTER TABLE `user` ADD COLUMN `is_super_admin` TINYINT(1) DEFAULT 0 COMMENT 'super admin flag'");
            ensureColumn("user", "recycle_flag", "ALTER TABLE `user` ADD COLUMN `recycle_flag` TINYINT(1) DEFAULT 0 COMMENT '0 normal,1 recycled'");
            ensureColumn("user", "recycled_at", "ALTER TABLE `user` ADD COLUMN `recycled_at` DATETIME DEFAULT NULL COMMENT 'Recycle time'");
            ensureColumn("user", "recycled_by_user_id", "ALTER TABLE `user` ADD COLUMN `recycled_by_user_id` BIGINT DEFAULT NULL COMMENT 'Recycle operator id'");
            ensureColumn("user", "recycle_remark", "ALTER TABLE `user` ADD COLUMN `recycle_remark` VARCHAR(255) DEFAULT NULL COMMENT 'Recycle remark'");
            ensureIndex("user", "idx_user_type", "ALTER TABLE `user` ADD INDEX `idx_user_type` (`user_type`)");
            ensureIndex("user", "idx_user_enabled", "ALTER TABLE `user` ADD INDEX `idx_user_enabled` (`enabled`)");
            ensureIndex("user", "idx_user_status_type", "ALTER TABLE `user` ADD INDEX `idx_user_status_type` (`status`, `user_type`)");
            ensureIndex("user", "idx_user_recycle", "ALTER TABLE `user` ADD INDEX `idx_user_recycle` (`recycle_flag`,`user_type`,`status`)");
            ensureIndex("user", "uk_user_login_name", "ALTER TABLE `user` ADD UNIQUE KEY `uk_user_login_name` (`login_name`)");
            if (tableExists("token_session")) {
                ensureColumn("token_session", "user_type", "ALTER TABLE `token_session` ADD COLUMN `user_type` VARCHAR(16) DEFAULT NULL COMMENT 'admin/miniapp'");
                ensureColumn("token_session", "login_scene", "ALTER TABLE `token_session` ADD COLUMN `login_scene` VARCHAR(64) DEFAULT NULL COMMENT 'login scene code'");
                ensureColumn("token_session", "device_id", "ALTER TABLE `token_session` ADD COLUMN `device_id` VARCHAR(64) DEFAULT NULL COMMENT 'frontend device id'");
                ensureColumn("token_session", "device_name", "ALTER TABLE `token_session` ADD COLUMN `device_name` VARCHAR(128) DEFAULT NULL COMMENT 'frontend device name'");
                ensureColumn("token_session", "client_ip", "ALTER TABLE `token_session` ADD COLUMN `client_ip` VARCHAR(64) DEFAULT NULL COMMENT 'client ip'");
                ensureColumn("token_session", "user_agent", "ALTER TABLE `token_session` ADD COLUMN `user_agent` VARCHAR(512) DEFAULT NULL COMMENT 'client user-agent'");
                ensureIndex("token_session", "idx_token_user_type", "ALTER TABLE `token_session` ADD INDEX `idx_token_user_type` (`user_type`)");
                ensureIndex("token_session", "idx_token_scene", "ALTER TABLE `token_session` ADD INDEX `idx_token_scene` (`login_scene`)");
                ensureIndex("token_session", "idx_token_device_id", "ALTER TABLE `token_session` ADD INDEX `idx_token_device_id` (`device_id`)");
                }

            ensureSingleSuperAdmin();
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS `user_notice` (" +
                            "`id` BIGINT NOT NULL COMMENT 'Primary key'," +
                            "`task_id` BIGINT DEFAULT NULL COMMENT 'Message task id'," +
                            "`user_id` BIGINT NOT NULL COMMENT 'Target user id'," +
                            "`notice_type` VARCHAR(32) DEFAULT 'system' COMMENT 'review/status/system'," +
                            "`title` VARCHAR(128) NOT NULL COMMENT 'Notice title'," +
                            "`content` VARCHAR(500) DEFAULT NULL COMMENT 'Notice content'," +
                            "`route_code` VARCHAR(128) DEFAULT NULL COMMENT 'Frontend route code'," +
                            "`source_kind` VARCHAR(32) DEFAULT 'system' COMMENT 'system/manual'," +
                            "`sender_user_id` BIGINT DEFAULT NULL COMMENT 'Sender user id'," +
                            "`sender_name` VARCHAR(64) DEFAULT NULL COMMENT 'Sender display name'," +
                            "`is_read` TINYINT(1) DEFAULT '0' COMMENT '0 unread,1 read'," +
                            "`read_at` DATETIME DEFAULT NULL COMMENT 'Read timestamp'," +
                            "`extra_json` LONGTEXT DEFAULT NULL COMMENT 'Extended json'," +
                            "`deleted` TINYINT(1) DEFAULT '0'," +
                            "`created_at` DATETIME DEFAULT CURRENT_TIMESTAMP," +
                            "`updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (`id`)," +
                            "INDEX `idx_user_notice_user` (`user_id`)," +
                            "INDEX `idx_user_notice_read` (`user_id`, `is_read`)," +
                            "INDEX `idx_user_notice_task` (`task_id`)," +
                            "INDEX `idx_user_notice_created` (`created_at`)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='In-app user notices'"
            );
            ensureColumn("user_notice", "task_id", "ALTER TABLE `user_notice` ADD COLUMN `task_id` BIGINT DEFAULT NULL COMMENT 'Message task id'");
            ensureColumn("user_notice", "route_code", "ALTER TABLE `user_notice` ADD COLUMN `route_code` VARCHAR(128) DEFAULT NULL COMMENT 'Frontend route code'");
            ensureColumn("user_notice", "source_kind", "ALTER TABLE `user_notice` ADD COLUMN `source_kind` VARCHAR(32) DEFAULT 'system' COMMENT 'system/manual'");
            ensureColumn("user_notice", "sender_user_id", "ALTER TABLE `user_notice` ADD COLUMN `sender_user_id` BIGINT DEFAULT NULL COMMENT 'Sender user id'");
            ensureColumn("user_notice", "sender_name", "ALTER TABLE `user_notice` ADD COLUMN `sender_name` VARCHAR(64) DEFAULT NULL COMMENT 'Sender display name'");
            ensureIndex("user_notice", "idx_user_notice_task", "ALTER TABLE `user_notice` ADD INDEX `idx_user_notice_task` (`task_id`)");

            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS `notice_task` (" +
                            "`id` BIGINT NOT NULL COMMENT 'Primary key'," +
                            "`notice_type` VARCHAR(32) DEFAULT 'system' COMMENT 'Message type'," +
                            "`source_kind` VARCHAR(32) DEFAULT 'manual' COMMENT 'system/manual'," +
                            "`title` VARCHAR(128) NOT NULL COMMENT 'Message title'," +
                            "`content` VARCHAR(1000) DEFAULT NULL COMMENT 'Message content'," +
                            "`route_code` VARCHAR(128) DEFAULT NULL COMMENT 'Target route code'," +
                            "`target_type` VARCHAR(32) NOT NULL COMMENT 'Target type'," +
                            "`target_config_json` LONGTEXT DEFAULT NULL COMMENT 'Target config json'," +
                            "`dispatch_status` VARCHAR(16) DEFAULT 'pending' COMMENT 'pending/sending/sent/failed'," +
                            "`target_count` INT DEFAULT 0 COMMENT 'Target user count'," +
                            "`success_count` INT DEFAULT 0 COMMENT 'Dispatch success count'," +
                            "`failed_count` INT DEFAULT 0 COMMENT 'Dispatch failed count'," +
                            "`result_message` VARCHAR(255) DEFAULT NULL COMMENT 'Dispatch result'," +
                            "`deleted` TINYINT(1) DEFAULT 0 COMMENT 'Soft delete flag'," +
                            "`deleted_at` DATETIME DEFAULT NULL COMMENT 'Delete time'," +
                            "`deleted_by_user_id` BIGINT DEFAULT NULL COMMENT 'Delete operator id'," +
                            "`created_by_user_id` BIGINT DEFAULT NULL COMMENT 'Creator id'," +
                            "`created_by_name` VARCHAR(64) DEFAULT NULL COMMENT 'Creator name'," +
                            "`created_at` DATETIME DEFAULT CURRENT_TIMESTAMP," +
                            "`updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (`id`)," +
                            "INDEX `idx_notice_task_created` (`created_at`)," +
                            "INDEX `idx_notice_task_status` (`dispatch_status`,`deleted`)," +
                            "INDEX `idx_notice_task_target` (`target_type`,`deleted`)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Notice dispatch tasks'"
            );
            ensureIndex("notice_task", "idx_notice_task_created", "ALTER TABLE `notice_task` ADD INDEX `idx_notice_task_created` (`created_at`)");
            ensureIndex("notice_task", "idx_notice_task_status", "ALTER TABLE `notice_task` ADD INDEX `idx_notice_task_status` (`dispatch_status`,`deleted`)");
            ensureIndex("notice_task", "idx_notice_task_target", "ALTER TABLE `notice_task` ADD INDEX `idx_notice_task_target` (`target_type`,`deleted`)");
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS `notice_dispatch_config` (" +
                            "`id` BIGINT NOT NULL COMMENT 'Primary key'," +
                            "`auto_purge_enabled` TINYINT(1) DEFAULT 0 COMMENT 'Auto purge enabled'," +
                            "`retain_days` INT NOT NULL DEFAULT 90 COMMENT 'Notice retain days'," +
                            "`updated_by_user_id` BIGINT DEFAULT NULL COMMENT 'Last updater id'," +
                            "`updated_by_name` VARCHAR(64) DEFAULT NULL COMMENT 'Last updater name'," +
                            "`created_at` DATETIME DEFAULT CURRENT_TIMESTAMP," +
                            "`updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (`id`)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Notice dispatch config'"
            );
            ensureColumn("notice_dispatch_config", "auto_purge_enabled", "ALTER TABLE `notice_dispatch_config` ADD COLUMN `auto_purge_enabled` TINYINT(1) DEFAULT 0 COMMENT 'Auto purge enabled'");
            ensureColumn("notice_dispatch_config", "retain_days", "ALTER TABLE `notice_dispatch_config` ADD COLUMN `retain_days` INT NOT NULL DEFAULT 90 COMMENT 'Notice retain days'");
            ensureColumn("notice_dispatch_config", "updated_by_user_id", "ALTER TABLE `notice_dispatch_config` ADD COLUMN `updated_by_user_id` BIGINT DEFAULT NULL COMMENT 'Last updater id'");
            ensureColumn("notice_dispatch_config", "updated_by_name", "ALTER TABLE `notice_dispatch_config` ADD COLUMN `updated_by_name` VARCHAR(64) DEFAULT NULL COMMENT 'Last updater name'");
            if (tableCount("notice_dispatch_config") <= 0L) {
                jdbcTemplate.update(
                        "INSERT INTO `notice_dispatch_config` (`id`,`auto_purge_enabled`,`retain_days`,`created_at`,`updated_at`) VALUES (1,0,90,NOW(),NOW())"
                );
            }
        } catch (Exception e) {
            log.warn("User schema bootstrap skipped: {}", safeMessage(e));
        }
    }

    /**
     * 术语词典表自举。
     */
    private void bootstrapTerminologyDictModule() {
        try {
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS `terminology_dict` (" +
                            "`id` BIGINT NOT NULL COMMENT 'Primary key'," +
                            "`source_text` VARCHAR(120) NOT NULL COMMENT 'Chinese source phrase'," +
                            "`target_text` VARCHAR(255) NOT NULL COMMENT 'English semantic phrase'," +
                            "`sort_order` INT DEFAULT 0 COMMENT 'Sort order'," +
                            "`deleted` TINYINT(1) DEFAULT 0," +
                            "`created_at` DATETIME DEFAULT CURRENT_TIMESTAMP," +
                            "`updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (`id`)," +
                            "UNIQUE KEY `uk_terminology_source_deleted` (`source_text`,`deleted`)," +
                            "INDEX `idx_terminology_sort` (`sort_order`,`id`)," +
                            "INDEX `idx_terminology_updated` (`updated_at`)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Terminology dictionary'"
            );
            ensureColumn("terminology_dict", "sort_order", "ALTER TABLE `terminology_dict` ADD COLUMN `sort_order` INT DEFAULT 0 COMMENT 'Sort order'");
            ensureColumn("terminology_dict", "deleted", "ALTER TABLE `terminology_dict` ADD COLUMN `deleted` TINYINT(1) DEFAULT 0");
            ensureIndex("terminology_dict", "uk_terminology_source_deleted", "ALTER TABLE `terminology_dict` ADD UNIQUE KEY `uk_terminology_source_deleted` (`source_text`,`deleted`)");
            ensureIndex("terminology_dict", "idx_terminology_sort", "ALTER TABLE `terminology_dict` ADD INDEX `idx_terminology_sort` (`sort_order`,`id`)");
            ensureIndex("terminology_dict", "idx_terminology_updated", "ALTER TABLE `terminology_dict` ADD INDEX `idx_terminology_updated` (`updated_at`)");
            } catch (Exception e) {
            log.warn("Terminology dictionary schema bootstrap skipped: {}", safeMessage(e));
        }
    }

    /**
     * 后台角色体系自举：
     * 创建 admin_role，修复角色继承关系，并保障默认可用角色存在。
     */
    private void bootstrapAdminRoleModule() {
        try {
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS `admin_role` (" +
                            "`id` BIGINT NOT NULL COMMENT 'Primary key'," +
                            "`role_code` VARCHAR(32) NOT NULL COMMENT 'Unique role code'," +
                            "`role_name` VARCHAR(64) NOT NULL COMMENT 'Role display name'," +
                            "`description` VARCHAR(255) DEFAULT NULL COMMENT 'Role description'," +
                            "`inherit_role_code` VARCHAR(32) DEFAULT NULL COMMENT 'Optional inherit role code'," +
                            "`menu_permissions_json` LONGTEXT DEFAULT NULL COMMENT 'Menu permission keys json'," +
                            "`sort_order` INT DEFAULT 0 COMMENT 'Display sort order'," +
                            "`enabled` TINYINT(1) DEFAULT 1 COMMENT '1 enabled,0 disabled'," +
                            "`is_system` TINYINT(1) DEFAULT 0 COMMENT '1 system role'," +
                            "`deleted` TINYINT(1) DEFAULT 0," +
                            "`created_at` DATETIME DEFAULT CURRENT_TIMESTAMP," +
                            "`updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (`id`)," +
                            "UNIQUE KEY `uk_admin_role_code` (`role_code`)," +
                            "INDEX `idx_admin_role_enabled` (`enabled`)," +
                            "INDEX `idx_admin_role_sort` (`sort_order`)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Admin role definitions'"
            );
            ensureColumn("admin_role", "inherit_role_code", "ALTER TABLE `admin_role` ADD COLUMN `inherit_role_code` VARCHAR(32) DEFAULT NULL COMMENT 'Optional inherit role code'");
            ensureColumn("admin_role", "menu_permissions_json", "ALTER TABLE `admin_role` ADD COLUMN `menu_permissions_json` LONGTEXT DEFAULT NULL COMMENT 'Menu permission keys json'");
            ensureColumn("admin_role", "sort_order", "ALTER TABLE `admin_role` ADD COLUMN `sort_order` INT DEFAULT 0 COMMENT 'Display sort order'");
            ensureColumn("admin_role", "enabled", "ALTER TABLE `admin_role` ADD COLUMN `enabled` TINYINT(1) DEFAULT 1 COMMENT '1 enabled,0 disabled'");
            ensureColumn("admin_role", "is_system", "ALTER TABLE `admin_role` ADD COLUMN `is_system` TINYINT(1) DEFAULT 0 COMMENT '1 system role'");
            ensureColumn("admin_role", "deleted", "ALTER TABLE `admin_role` ADD COLUMN `deleted` TINYINT(1) DEFAULT 0");
            ensureIndex("admin_role", "idx_admin_role_enabled", "ALTER TABLE `admin_role` ADD INDEX `idx_admin_role_enabled` (`enabled`)");
            ensureIndex("admin_role", "idx_admin_role_sort", "ALTER TABLE `admin_role` ADD INDEX `idx_admin_role_sort` (`sort_order`)");

            ensureSuperAdminRole();
            ensureDefaultAdminRole();
            if (tableExists("user")) {
                String defaultAdminRoleCode = resolveFirstEnabledAdminRoleCode();
                if (StringUtils.hasText(defaultAdminRoleCode)) {
                    }
                }
        } catch (Exception e) {
            log.warn("Admin role schema bootstrap skipped: {}", safeMessage(e));
        }
    }

    /**
     * dynamic_form_config 兼容自举：
     * 1. 补齐 version/status 字段；
     * 2. 清理模块+场景+配置名+版本号重复数据；
     * 3. 建立模块+场景+配置名+版本号唯一约束。
     */
    private void bootstrapDynamicConfigModule() {
        try {
            if (!tableExists("dynamic_form_config")) {
                return;
            }
            ensureColumn("dynamic_form_config", "version_no", "ALTER TABLE `dynamic_form_config` ADD COLUMN `version_no` INT DEFAULT 1 COMMENT 'Version number'");
            ensureColumn("dynamic_form_config", "status", "ALTER TABLE `dynamic_form_config` ADD COLUMN `status` VARCHAR(16) DEFAULT 'enabled' COMMENT 'enabled/disabled'");
            ensureColumn("dynamic_form_config", "active_version_key", "ALTER TABLE `dynamic_form_config` ADD COLUMN `active_version_key` VARCHAR(255) GENERATED ALWAYS AS (CASE WHEN `deleted`=0 AND `module_key` IS NOT NULL AND TRIM(`module_key`)<>'' AND `scene_key` IS NOT NULL AND TRIM(`scene_key`)<>'' AND `config_name` IS NOT NULL AND TRIM(`config_name`)<>'' AND `version_no` IS NOT NULL THEN CONCAT(LOWER(TRIM(`module_key`)),'#',LOWER(TRIM(`scene_key`)),'#',LOWER(TRIM(`config_name`)),'#',`version_no`) ELSE NULL END) STORED COMMENT 'Active version unique key'");
            ensureIndex("dynamic_form_config", "idx_dynamic_module_scene", "ALTER TABLE `dynamic_form_config` ADD INDEX `idx_dynamic_module_scene` (`module_key`,`scene_key`)");
            ensureIndex("dynamic_form_config", "idx_dynamic_status", "ALTER TABLE `dynamic_form_config` ADD INDEX `idx_dynamic_status` (`status`)");
            ensureIndex("dynamic_form_config", "idx_dynamic_updated", "ALTER TABLE `dynamic_form_config` ADD INDEX `idx_dynamic_updated` (`updated_at`)");

            normalizeDuplicateDynamicConfigVersions();
            dropIndexIfExists("dynamic_form_config", "uk_dynamic_module_scene_version");
            ensureIndex(
                    "dynamic_form_config",
                    "uk_dynamic_module_scene_name_version",
                    "ALTER TABLE `dynamic_form_config` ADD UNIQUE KEY `uk_dynamic_module_scene_name_version` (`active_version_key`)"
            );
        } catch (Exception e) {
            log.warn("Dynamic config schema bootstrap skipped: {}", safeMessage(e));
        }
    }

    /**
     * 对历史重复版本进行收口：同模块同场景同版本号冲突时，保留最早一条，后续记录顺延到新版本号。
     */
    private void normalizeDuplicateDynamicConfigVersions() {
        List<Map<String, Object>> duplicates = jdbcTemplate.queryForList(
                "SELECT `module_key`,`scene_key`,`config_name`,`version_no`,COUNT(*) AS `cnt` " +
                        "FROM `dynamic_form_config` " +
                        "WHERE `deleted`=0 " +
                        "GROUP BY `module_key`,`scene_key`,`config_name`,`version_no` " +
                        "HAVING COUNT(*)>1"
        );
        for (Map<String, Object> duplicate : duplicates) {
            String moduleKey = String.valueOf(duplicate.get("module_key"));
            String sceneKey = String.valueOf(duplicate.get("scene_key"));
            String configName = String.valueOf(duplicate.get("config_name"));
            Integer versionNo = toNullableInt(duplicate.get("version_no"));
            if (!StringUtils.hasText(moduleKey) || !StringUtils.hasText(sceneKey) || !StringUtils.hasText(configName) || versionNo == null) {
                continue;
            }
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT `id` FROM `dynamic_form_config` " +
                            "WHERE `deleted`=0 AND `module_key`=? AND `scene_key`=? AND `config_name`=? AND `version_no`=? " +
                            "ORDER BY `created_at` ASC, `id` ASC",
                    Long.class,
                    moduleKey,
                    sceneKey,
                    configName,
                    versionNo
            );
            if (ids == null || ids.size() <= 1) {
                continue;
            }
            for (int i = 1; i < ids.size(); i++) {
                Long id = ids.get(i);
                if (id == null) {
                    continue;
                }
                Integer maxVersion = jdbcTemplate.queryForObject(
                        "SELECT COALESCE(MAX(`version_no`),0) FROM `dynamic_form_config` " +
                                "WHERE `deleted`=0 AND `module_key`=? AND `scene_key`=? AND `config_name`=?",
                        Integer.class,
                        moduleKey,
                        sceneKey,
                        configName
                );
                int nextVersion = (maxVersion == null ? 0 : maxVersion) + 1;
                }
        }
    }

    /**
     * 确保系统内置超级管理员角色存在且不可弱化。
     */
    private void ensureSuperAdminRole() {
        }

    /**
     * 确保存在至少一个启用的默认后台角色。
     */
    private void ensureDefaultAdminRole() {
        String hit = resolveFirstEnabledAdminRoleCode();
        if (StringUtils.hasText(hit)) {
            return;
        }
        String roleCode = "console_manager";
        int seq = 2;
        while (existsAdminRoleCode(roleCode) && seq < 200) {
            roleCode = "console_manager_" + seq;
            seq += 1;
        }
        }

    /**
     * 判断指定 roleCode 是否已存在（忽略大小写）。
     */
    private boolean existsAdminRoleCode(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return false;
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `admin_role` WHERE LOWER(`role_code`)=LOWER(?)",
                Long.class,
                roleCode.trim()
        );
        return count != null && count > 0;
    }

    /**
     * 获取当前第一个启用角色（按排序优先级）。
     */
    private String resolveFirstEnabledAdminRoleCode() {
        if (!tableExists("admin_role")) {
            return null;
        }
        List<String> rows = jdbcTemplate.queryForList(
                "SELECT `role_code` FROM `admin_role` " +
                        "WHERE `deleted`=0 AND `enabled`=1 AND `role_code` IS NOT NULL AND TRIM(`role_code`)<>'' " +
                        "AND LOWER(`role_code`)<>'super_admin' " +
                        "ORDER BY `sort_order` ASC, `created_at` ASC, `id` ASC LIMIT 1",
                String.class
        );
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        String code = String.valueOf(rows.get(0) == null ? "" : rows.get(0)).trim().toLowerCase();
        return StringUtils.hasText(code) ? code : null;
    }

    /**
     * 保证超级管理员唯一且至少存在一名。
     */
    private void ensureSingleSuperAdmin() {
        try {
            List<Long> superIds = jdbcTemplate.queryForList(
                    "SELECT `id` FROM `user` WHERE `deleted`=0 AND `is_super_admin`=1 ORDER BY `created_at` ASC, `id` ASC",
                    Long.class
            );
            if (superIds != null && superIds.size() > 1) {
                Long keepId = superIds.get(0);
                superIds = java.util.Collections.singletonList(keepId);
            }
            if (superIds != null && !superIds.isEmpty()) {
                return;
            }
            List<Long> preferred = jdbcTemplate.queryForList(
                    "SELECT `id` FROM `user` " +
                            "WHERE `deleted`=0 AND (" +
                            "LOWER(`wx_open_id`)='mock_admin_0001' OR LOWER(`role_code`)='admin' OR LOWER(`user_type`)='admin'" +
                            ") ORDER BY `created_at` ASC, `id` ASC LIMIT 1",
                    Long.class
            );
            if (preferred != null && !preferred.isEmpty()) {
                }
        } catch (Exception e) {
            log.warn("Ensure single super admin skipped: {}", safeMessage(e));
        }
    }

    /**
     * 种植/种子相关历史结构兼容：
     * 包含 field、crop、farm_process_template、seed_batch、seed_quality_test、farm_record、field_crop_cycle 等表。
     */
    private void bootstrapSeedCompatibility() {
        try {
            if (tableExists("field")) {
                ensureColumn("field", "crop_variety", "ALTER TABLE `field` ADD COLUMN `crop_variety` VARCHAR(64) DEFAULT NULL COMMENT 'Current crop variety'");
                ensureColumn("field", "crop_variety_groups_json", "ALTER TABLE `field` ADD COLUMN `crop_variety_groups_json` LONGTEXT DEFAULT NULL COMMENT 'Current crop-variety groups(JSON)'");
                ensureColumn("field", "province", "ALTER TABLE `field` ADD COLUMN `province` VARCHAR(64) DEFAULT NULL COMMENT 'Province'");
                ensureColumn("field", "city", "ALTER TABLE `field` ADD COLUMN `city` VARCHAR(64) DEFAULT NULL COMMENT 'City'");
                ensureColumn("field", "district", "ALTER TABLE `field` ADD COLUMN `district` VARCHAR(64) DEFAULT NULL COMMENT 'District/County'");
                ensureColumn("field", "township", "ALTER TABLE `field` ADD COLUMN `township` VARCHAR(64) DEFAULT NULL COMMENT 'Township'");
                ensureColumn("field", "formatted_address", "ALTER TABLE `field` ADD COLUMN `formatted_address` VARCHAR(255) DEFAULT NULL COMMENT 'Standardized formatted address'");
                ensureColumn("field", "cover_image_url", "ALTER TABLE `field` ADD COLUMN `cover_image_url` VARCHAR(512) DEFAULT NULL COMMENT 'Cover image url'");
                ensureColumn("field", "sort_order", "ALTER TABLE `field` ADD COLUMN `sort_order` INT DEFAULT 0 COMMENT 'Display sort order'");
                ensureColumn("field", "enabled", "ALTER TABLE `field` ADD COLUMN `enabled` TINYINT(1) DEFAULT 1 COMMENT '1 enabled,0 disabled'");
                ensureColumn("field", "location_point", "ALTER TABLE `field` ADD COLUMN `location_point` POINT DEFAULT NULL COMMENT 'Field center point(lng,lat)'");
                ensureIndex("field", "idx_field_province", "ALTER TABLE `field` ADD INDEX `idx_field_province` (`province`)");
                ensureIndex("field", "idx_field_city", "ALTER TABLE `field` ADD INDEX `idx_field_city` (`city`)");
                ensureIndex("field", "idx_field_district", "ALTER TABLE `field` ADD INDEX `idx_field_district` (`district`)");
                ensureIndex("field", "idx_field_enabled", "ALTER TABLE `field` ADD INDEX `idx_field_enabled` (`enabled`)");
                ensureIndex("field", "idx_field_location_lat_lng", "ALTER TABLE `field` ADD INDEX `idx_field_location_lat_lng` (`location_lat`,`location_lng`)");
                ensureIndexSafely("field", "sp_idx_field_location_point", "ALTER TABLE `field` ADD SPATIAL INDEX `sp_idx_field_location_point` (`location_point`)");
                dropColumnIfExists("field", "soil_type");
                dropColumnIfExists("field", "irrigation_type");
            }
            if (!tableExists("miniapp_search_term")) {
                jdbcTemplate.execute(
                        "CREATE TABLE IF NOT EXISTS `miniapp_search_term` (" +
                                "`id` BIGINT NOT NULL COMMENT 'Primary key'," +
                                "`scene_key` VARCHAR(32) NOT NULL COMMENT 'Suggestion scene key'," +
                                "`entity_type` VARCHAR(32) NOT NULL COMMENT 'Entity type'," +
                                "`entity_id` BIGINT NOT NULL COMMENT 'Entity id'," +
                                "`term_type` VARCHAR(32) NOT NULL COMMENT 'Term type'," +
                                "`type_label` VARCHAR(32) NOT NULL COMMENT 'Display type label'," +
                                "`label` VARCHAR(191) NOT NULL COMMENT 'Display label'," +
                                "`value_text` VARCHAR(191) NOT NULL COMMENT 'Applied keyword value'," +
                                "`search_text` VARCHAR(255) NOT NULL COMMENT 'Normalized search text'," +
                                "`search_compact` VARCHAR(255) NOT NULL COMMENT 'Compacted search text'," +
                                "`term_key_hash` CHAR(32) NOT NULL COMMENT 'Stable unique term hash'," +
                                "`sort_weight` INT NOT NULL DEFAULT 0 COMMENT 'Display weight'," +
                                "`deleted` TINYINT(1) DEFAULT 0," +
                                "`created_at` DATETIME DEFAULT CURRENT_TIMESTAMP," +
                                "`updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                                "PRIMARY KEY (`id`)" +
                                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Miniapp internal search projection'"
                );
            }
            if (tableExists("miniapp_search_term")) {
                ensureColumn("miniapp_search_term", "scene_key", "ALTER TABLE `miniapp_search_term` ADD COLUMN `scene_key` VARCHAR(32) NOT NULL COMMENT 'Suggestion scene key'");
                ensureColumn("miniapp_search_term", "entity_type", "ALTER TABLE `miniapp_search_term` ADD COLUMN `entity_type` VARCHAR(32) NOT NULL COMMENT 'Entity type'");
                ensureColumn("miniapp_search_term", "entity_id", "ALTER TABLE `miniapp_search_term` ADD COLUMN `entity_id` BIGINT NOT NULL COMMENT 'Entity id'");
                ensureColumn("miniapp_search_term", "term_type", "ALTER TABLE `miniapp_search_term` ADD COLUMN `term_type` VARCHAR(32) NOT NULL COMMENT 'Term type'");
                ensureColumn("miniapp_search_term", "type_label", "ALTER TABLE `miniapp_search_term` ADD COLUMN `type_label` VARCHAR(32) NOT NULL COMMENT 'Display type label'");
                ensureColumn("miniapp_search_term", "label", "ALTER TABLE `miniapp_search_term` ADD COLUMN `label` VARCHAR(191) NOT NULL COMMENT 'Display label'");
                ensureColumn("miniapp_search_term", "value_text", "ALTER TABLE `miniapp_search_term` ADD COLUMN `value_text` VARCHAR(191) NOT NULL COMMENT 'Applied keyword value'");
                ensureColumn("miniapp_search_term", "search_text", "ALTER TABLE `miniapp_search_term` ADD COLUMN `search_text` VARCHAR(255) NOT NULL COMMENT 'Normalized search text'");
                ensureColumn("miniapp_search_term", "search_compact", "ALTER TABLE `miniapp_search_term` ADD COLUMN `search_compact` VARCHAR(255) NOT NULL COMMENT 'Compacted search text'");
                ensureColumn("miniapp_search_term", "pinyin_full", "ALTER TABLE `miniapp_search_term` ADD COLUMN `pinyin_full` VARCHAR(255) DEFAULT NULL COMMENT 'Pinyin full text'");
                ensureColumn("miniapp_search_term", "pinyin_initials", "ALTER TABLE `miniapp_search_term` ADD COLUMN `pinyin_initials` VARCHAR(255) DEFAULT NULL COMMENT 'Pinyin initials'");
                ensureColumn("miniapp_search_term", "term_key_hash", "ALTER TABLE `miniapp_search_term` ADD COLUMN `term_key_hash` CHAR(32) NOT NULL COMMENT 'Stable unique term hash'");
                ensureColumn("miniapp_search_term", "sort_weight", "ALTER TABLE `miniapp_search_term` ADD COLUMN `sort_weight` INT NOT NULL DEFAULT 0 COMMENT 'Display weight'");
                ensureColumn("miniapp_search_term", "deleted", "ALTER TABLE `miniapp_search_term` ADD COLUMN `deleted` TINYINT(1) DEFAULT 0");
                ensureIndex("miniapp_search_term", "uk_search_term_hash", "ALTER TABLE `miniapp_search_term` ADD UNIQUE KEY `uk_search_term_hash` (`term_key_hash`)");
                ensureIndex("miniapp_search_term", "idx_search_scene_text", "ALTER TABLE `miniapp_search_term` ADD INDEX `idx_search_scene_text` (`scene_key`,`search_text`)");
                ensureIndex("miniapp_search_term", "idx_search_scene_compact", "ALTER TABLE `miniapp_search_term` ADD INDEX `idx_search_scene_compact` (`scene_key`,`search_compact`)");
                ensureIndex("miniapp_search_term", "idx_search_scene_pinyin_full", "ALTER TABLE `miniapp_search_term` ADD INDEX `idx_search_scene_pinyin_full` (`scene_key`,`pinyin_full`)");
                ensureIndex("miniapp_search_term", "idx_search_scene_pinyin_initials", "ALTER TABLE `miniapp_search_term` ADD INDEX `idx_search_scene_pinyin_initials` (`scene_key`,`pinyin_initials`)");
                ensureIndex("miniapp_search_term", "idx_search_entity", "ALTER TABLE `miniapp_search_term` ADD INDEX `idx_search_entity` (`entity_type`,`entity_id`)");
            }
            if (tableExists("crop")) {
                ensureColumn("crop", "sort_order", "ALTER TABLE `crop` ADD COLUMN `sort_order` INT DEFAULT 0 COMMENT 'Display sort order'");
                ensureColumn("crop", "node_type", "ALTER TABLE `crop` ADD COLUMN `node_type` VARCHAR(16) DEFAULT 'variety' COMMENT 'category/variety'");
                ensureColumn("crop", "parent_id", "ALTER TABLE `crop` ADD COLUMN `parent_id` BIGINT DEFAULT NULL COMMENT 'Parent category id'");
                ensureColumn("crop", "image_url", "ALTER TABLE `crop` ADD COLUMN `image_url` VARCHAR(512) DEFAULT NULL COMMENT 'Category/variety image url'");
                ensureColumn("crop", "category_active_name", "ALTER TABLE `crop` ADD COLUMN `category_active_name` VARCHAR(191) GENERATED ALWAYS AS (CASE WHEN `deleted`=0 AND `node_type`='category' AND `name` IS NOT NULL AND TRIM(`name`)<>'' THEN TRIM(`name`) ELSE NULL END) STORED COMMENT 'Active category unique key'");
                ensureColumn("crop", "variety_active_key", "ALTER TABLE `crop` ADD COLUMN `variety_active_key` VARCHAR(255) GENERATED ALWAYS AS (CASE WHEN `deleted`=0 AND `node_type`='variety' AND `parent_id` IS NOT NULL AND `variety` IS NOT NULL AND TRIM(`variety`)<>'' THEN CONCAT(`parent_id`,'#',TRIM(`variety`)) ELSE NULL END) STORED COMMENT 'Active variety unique key'");
                ensureIndex("crop", "idx_crop_node_type", "ALTER TABLE `crop` ADD INDEX `idx_crop_node_type` (`node_type`)");
                ensureIndex("crop", "idx_crop_parent", "ALTER TABLE `crop` ADD INDEX `idx_crop_parent` (`parent_id`)");
                bootstrapCropCategoryNodes();
                deduplicateCropHierarchy();
                dropIndexIfExists("crop", "uk_crop_category_name");
                dropIndexIfExists("crop", "uk_crop_variety_parent");
                ensureIndex("crop", "uk_crop_category_active_name", "ALTER TABLE `crop` ADD UNIQUE KEY `uk_crop_category_active_name` (`category_active_name`)");
                ensureIndex("crop", "uk_crop_variety_active_key", "ALTER TABLE `crop` ADD UNIQUE KEY `uk_crop_variety_active_key` (`variety_active_key`)");
            }
            if (tableExists("farm_process_template")) {
                ensureColumn("farm_process_template", "enabled", "ALTER TABLE `farm_process_template` ADD COLUMN `enabled` TINYINT(1) DEFAULT 1 COMMENT '1 enabled,0 disabled'");
                ensureIndex("farm_process_template", "idx_template_enabled", "ALTER TABLE `farm_process_template` ADD INDEX `idx_template_enabled` (`enabled`)");
                }
            if (tableExists("seed_batch")) {
                ensureColumn("seed_batch", "crop_type", "ALTER TABLE `seed_batch` ADD COLUMN `crop_type` VARCHAR(64) DEFAULT NULL COMMENT 'Crop type'");
                ensureColumn("seed_batch", "form_config_id", "ALTER TABLE `seed_batch` ADD COLUMN `form_config_id` BIGINT DEFAULT NULL COMMENT 'Dynamic form config id'");
                ensureColumn("seed_batch", "extra_json", "ALTER TABLE `seed_batch` ADD COLUMN `extra_json` LONGTEXT DEFAULT NULL COMMENT 'Dynamic fields value(JSON)'");
                ensureColumn("seed_batch", "enabled", "ALTER TABLE `seed_batch` ADD COLUMN `enabled` TINYINT(1) DEFAULT 1 COMMENT '1 enabled,0 disabled'");
                ensureIndex("seed_batch", "idx_seed_batch_crop", "ALTER TABLE `seed_batch` ADD INDEX `idx_seed_batch_crop` (`crop_type`)");
                ensureIndex("seed_batch", "idx_seed_batch_form_config", "ALTER TABLE `seed_batch` ADD INDEX `idx_seed_batch_form_config` (`form_config_id`)");
                ensureIndex("seed_batch", "idx_seed_batch_enabled", "ALTER TABLE `seed_batch` ADD INDEX `idx_seed_batch_enabled` (`enabled`)");
                if (tableExists("crop")) {
                    }
                dropColumnIfExists("seed_batch", "origin");
                dropColumnIfExists("seed_batch", "storage_location");
            }
            if (tableExists("seed_quality_test")) {
                ensureColumn("seed_quality_test", "sample_count", "ALTER TABLE `seed_quality_test` ADD COLUMN `sample_count` INT DEFAULT NULL COMMENT 'Sample count for germination test'");
                ensureColumn("seed_quality_test", "germination_count", "ALTER TABLE `seed_quality_test` ADD COLUMN `germination_count` INT DEFAULT NULL COMMENT 'Germinated seed count'");
                ensureColumn("seed_quality_test", "cleanliness", "ALTER TABLE `seed_quality_test` ADD COLUMN `cleanliness` DECIMAL(5,2) DEFAULT NULL COMMENT 'Cleanliness(%)'");
                ensureColumn("seed_quality_test", "request_key", "ALTER TABLE `seed_quality_test` ADD COLUMN `request_key` VARCHAR(64) DEFAULT NULL COMMENT 'Idempotency request key'");
                ensureColumn("seed_quality_test", "form_config_id", "ALTER TABLE `seed_quality_test` ADD COLUMN `form_config_id` BIGINT DEFAULT NULL COMMENT 'Dynamic form config id'");
                ensureColumn("seed_quality_test", "extra_json", "ALTER TABLE `seed_quality_test` ADD COLUMN `extra_json` LONGTEXT DEFAULT NULL COMMENT 'Dynamic fields value(JSON)'");
                ensureIndex("seed_quality_test", "idx_seed_test_form_config", "ALTER TABLE `seed_quality_test` ADD INDEX `idx_seed_test_form_config` (`form_config_id`)");
                ensureIndex("seed_quality_test", "uk_seed_test_batch_request_key", "ALTER TABLE `seed_quality_test` ADD UNIQUE KEY `uk_seed_test_batch_request_key` (`batch_id`,`request_key`)");
                dropColumnIfExists("seed_quality_test", "test_type");
                dropColumnIfExists("seed_quality_test", "conclusion");
                dropColumnIfExists("seed_quality_test", "thousand_grain_weight");
            }
            if (tableExists("farm_record")) {
                dropColumnIfExists("farm_record", "work_type");
                ensureColumn("farm_record", "extra_json", "ALTER TABLE `farm_record` ADD COLUMN `extra_json` LONGTEXT DEFAULT NULL COMMENT 'Dynamic params json'");
                ensureColumn("farm_record", "cycle_id", "ALTER TABLE `farm_record` ADD COLUMN `cycle_id` BIGINT DEFAULT NULL COMMENT 'Planting plan id'");
                ensureColumn("farm_record", "weather", "ALTER TABLE `farm_record` ADD COLUMN `weather` VARCHAR(64) DEFAULT NULL COMMENT 'Weather'");
                ensureColumn("farm_record", "temperature", "ALTER TABLE `farm_record` ADD COLUMN `temperature` VARCHAR(32) DEFAULT NULL COMMENT 'Temperature'");
                ensureColumn("farm_record", "weather_location", "ALTER TABLE `farm_record` ADD COLUMN `weather_location` VARCHAR(128) DEFAULT NULL COMMENT 'Weather location'");
                ensureColumn("farm_record", "humidity", "ALTER TABLE `farm_record` ADD COLUMN `humidity` VARCHAR(16) DEFAULT NULL COMMENT 'Humidity(%)'");
                ensureColumn("farm_record", "wind_direction", "ALTER TABLE `farm_record` ADD COLUMN `wind_direction` VARCHAR(16) DEFAULT NULL COMMENT 'Wind direction'");
                ensureColumn("farm_record", "wind_power", "ALTER TABLE `farm_record` ADD COLUMN `wind_power` VARCHAR(16) DEFAULT NULL COMMENT 'Wind power'");
                ensureColumn("farm_record", "weather_report_time", "ALTER TABLE `farm_record` ADD COLUMN `weather_report_time` VARCHAR(32) DEFAULT NULL COMMENT 'Weather report time'");
                ensureColumn("farm_record", "operator_user_id", "ALTER TABLE `farm_record` ADD COLUMN `operator_user_id` BIGINT DEFAULT NULL COMMENT 'Operator user id'");
                ensureIndex("farm_record", "idx_farm_record_operator_user", "ALTER TABLE `farm_record` ADD INDEX `idx_farm_record_operator_user` (`operator_user_id`)");
            }
            if (tableExists("field_crop_cycle")) {
                ensureColumn("field_crop_cycle", "plan_mode", "ALTER TABLE `field_crop_cycle` ADD COLUMN `plan_mode` VARCHAR(32) DEFAULT 'single' COMMENT 'single/rotation/intercropping/relay/mixed/fallow/custom'");
                }
        } catch (Exception e) {
            log.warn("Seed schema bootstrap skipped: {}", safeMessage(e));
        }
    }

    /**
     * 将 crop 表规范为“分类(category) + 品种(variety)”两级节点结构。
     */
    private void bootstrapCropCategoryNodes() {
        try {
            List<String> categoryNames = jdbcTemplate.queryForList(
                    "SELECT DISTINCT TRIM(`name`) AS `name` " +
                            "FROM `crop` " +
                            "WHERE `deleted`=0 AND `name` IS NOT NULL AND TRIM(`name`)<>''",
                    String.class
            );

            for (String categoryName : categoryNames) {
                if (categoryName == null || categoryName.trim().isEmpty()) {
                    continue;
                }
                Long categoryId = queryCategoryIdByName(categoryName);
                if (categoryId == null) {
                    long newId = IdWorker.getId();
                    Integer minSort = jdbcTemplate.queryForObject(
                            "SELECT COALESCE(MIN(`sort_order`), 0) FROM `crop` WHERE `deleted`=0 AND `name`=?",
                            Integer.class,
                            categoryName
                    );
                    int sortOrder = minSort == null ? 0 : minSort;
                    categoryId = newId;
                }

                }
        } catch (Exception e) {
            log.warn("Crop category node bootstrap skipped: {}", safeMessage(e));
        }
    }

    /**
     * 规范化 crop 去重数据，保证后续唯一约束可平滑落地。
     */
    private void deduplicateCropHierarchy() {
        try {
            List<Map<String, Object>> duplicateCategories = jdbcTemplate.queryForList(
                    "SELECT TRIM(`name`) AS `name`, MIN(`id`) AS `keep_id` " +
                            "FROM `crop` " +
                            "WHERE `deleted`=0 AND `node_type`='category' AND `name` IS NOT NULL AND TRIM(`name`)<>'' " +
                            "GROUP BY TRIM(`name`) HAVING COUNT(*) > 1"
            );
            for (Map<String, Object> row : duplicateCategories) {
                String categoryName = String.valueOf(row == null ? "" : row.get("name")).trim();
                Long keepId = toNullableLong(row == null ? null : row.get("keep_id"));
                if (!StringUtils.hasText(categoryName) || keepId == null) {
                    continue;
                }
                List<Long> staleIds = jdbcTemplate.queryForList(
                        "SELECT `id` FROM `crop` " +
                                "WHERE `deleted`=0 AND `node_type`='category' AND TRIM(`name`)=? AND `id`<>?",
                        Long.class,
                        categoryName,
                        keepId
                );
                for (Long staleId : staleIds) {
                    if (staleId == null) {
                        continue;
                    }
                    }
            }

            List<Map<String, Object>> duplicateVarieties = jdbcTemplate.queryForList(
                    "SELECT `parent_id`, TRIM(`variety`) AS `variety`, MIN(`id`) AS `keep_id` " +
                            "FROM `crop` " +
                            "WHERE `deleted`=0 AND `node_type`='variety' " +
                            "AND `parent_id` IS NOT NULL AND `variety` IS NOT NULL AND TRIM(`variety`)<>'' " +
                            "GROUP BY `parent_id`, TRIM(`variety`) HAVING COUNT(*) > 1"
            );
            for (Map<String, Object> row : duplicateVarieties) {
                Long parentId = toNullableLong(row == null ? null : row.get("parent_id"));
                String variety = String.valueOf(row == null ? "" : row.get("variety")).trim();
                Long keepId = toNullableLong(row == null ? null : row.get("keep_id"));
                if (parentId == null || !StringUtils.hasText(variety) || keepId == null) {
                    continue;
                }
                }
        } catch (Exception e) {
            log.warn("Crop deduplicate bootstrap skipped: {}", safeMessage(e));
        }
    }

    /**
     * 按分类名查询分类节点 id（取排序最靠前一条）。
     */
    private Long queryCategoryIdByName(String categoryName) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT `id` FROM `crop` " +
                        "WHERE `deleted`=0 AND `node_type`='category' AND `name`=? " +
                        "ORDER BY `sort_order` ASC, `id` ASC LIMIT 1",
                Long.class,
                categoryName
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    /**
     * 高德配额与审计表结构自举。
     */
    private void bootstrapAmapModule() {
        try {
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS `amap_quota_config` (" +
                            "`id` BIGINT NOT NULL COMMENT 'Primary key'," +
                            "`record_date` DATE NOT NULL COMMENT 'Daily usage date'," +
                            "`daily_limit` INT NOT NULL DEFAULT '50000' COMMENT 'Daily API call limit'," +
                            "`alert_threshold` INT NOT NULL DEFAULT '80' COMMENT 'Alert threshold percentage'," +
                            "`used_count` INT NOT NULL DEFAULT '0' COMMENT 'Used call count in current day'," +
                            "`account_name` VARCHAR(64) DEFAULT NULL COMMENT 'Account name'," +
                            "`app_key` VARCHAR(128) DEFAULT NULL COMMENT 'AMap app key'," +
                            "`weather_daily_limit` INT NOT NULL DEFAULT '20000' COMMENT 'Weather daily limit'," +
                            "`weather_used_count` INT NOT NULL DEFAULT '0' COMMENT 'Weather used count'," +
                            "`location_daily_limit` INT NOT NULL DEFAULT '20000' COMMENT 'Location/City daily limit'," +
                            "`location_used_count` INT NOT NULL DEFAULT '0' COMMENT 'Location/City used count'," +
                            "`geocode_daily_limit` INT NOT NULL DEFAULT '20000' COMMENT 'Geocode daily limit'," +
                            "`geocode_used_count` INT NOT NULL DEFAULT '0' COMMENT 'Geocode used count'," +
                            "`city_daily_limit` INT NOT NULL DEFAULT '10000' COMMENT 'City query daily limit'," +
                            "`city_used_count` INT NOT NULL DEFAULT '0' COMMENT 'City query used count'," +
                            "`qps_limit` INT NOT NULL DEFAULT '3' COMMENT 'AMap QPS throttle limit'," +
                            "`recharge_total` INT NOT NULL DEFAULT '0' COMMENT 'Recharge total'," +
                            "`location_recharge_total` INT NOT NULL DEFAULT '0' COMMENT 'Location recharge total'," +
                            "`cache_redis_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Cache redis enabled'," +
                            "`cache_redis_key_prefix` VARCHAR(64) DEFAULT 'dahe:v2:amap:cache:' COMMENT 'Cache redis key prefix'," +
                            "`cache_region_ttl_minutes` INT NOT NULL DEFAULT 720 COMMENT 'Region cache ttl minutes'," +
                            "`cache_region_stale_minutes` INT NOT NULL DEFAULT 1440 COMMENT 'Region stale cache minutes'," +
                            "`cache_weather_ttl_minutes` INT NOT NULL DEFAULT 60 COMMENT 'Weather cache ttl minutes'," +
                            "`cache_local_region_max_entries` INT NOT NULL DEFAULT 256 COMMENT 'Local region cache max entries'," +
                            "`cache_local_weather_max_entries` INT NOT NULL DEFAULT 256 COMMENT 'Local weather cache max entries'," +
                            "`audit_auto_purge_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Audit auto purge enabled'," +
                            "`audit_retain_days` INT NOT NULL DEFAULT 90 COMMENT 'Audit retain days'," +
                            "`remark` VARCHAR(255) DEFAULT NULL COMMENT 'Remark'," +
                            "`created_at` DATETIME DEFAULT CURRENT_TIMESTAMP," +
                            "`updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (`id`)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AMap daily quota config'"
            );
            ensureColumn("amap_quota_config", "account_name", "ALTER TABLE `amap_quota_config` ADD COLUMN `account_name` VARCHAR(64) DEFAULT NULL COMMENT 'Account name'");
            ensureColumn("amap_quota_config", "account_login", "ALTER TABLE `amap_quota_config` ADD COLUMN `account_login` VARCHAR(96) DEFAULT NULL COMMENT 'AMap account login'");
            ensureColumn("amap_quota_config", "app_name", "ALTER TABLE `amap_quota_config` ADD COLUMN `app_name` VARCHAR(96) DEFAULT NULL COMMENT 'AMap application name'");
            ensureColumn("amap_quota_config", "console_url", "ALTER TABLE `amap_quota_config` ADD COLUMN `console_url` VARCHAR(255) DEFAULT NULL COMMENT 'AMap console url'");
            ensureColumn("amap_quota_config", "key_console_url", "ALTER TABLE `amap_quota_config` ADD COLUMN `key_console_url` VARCHAR(255) DEFAULT NULL COMMENT 'AMap key console url'");
            ensureColumn("amap_quota_config", "app_key", "ALTER TABLE `amap_quota_config` ADD COLUMN `app_key` VARCHAR(128) DEFAULT NULL COMMENT 'AMap app key'");
            ensureColumn("amap_quota_config", "app_key_status", "ALTER TABLE `amap_quota_config` ADD COLUMN `app_key_status` VARCHAR(24) DEFAULT 'unknown' COMMENT 'Key status: unknown/valid/invalid'");
            ensureColumn("amap_quota_config", "app_key_bound_at", "ALTER TABLE `amap_quota_config` ADD COLUMN `app_key_bound_at` DATETIME DEFAULT NULL COMMENT 'Key bind timestamp'");
            ensureColumn("amap_quota_config", "app_key_last_check_at", "ALTER TABLE `amap_quota_config` ADD COLUMN `app_key_last_check_at` DATETIME DEFAULT NULL COMMENT 'Key last verify timestamp'");
            ensureColumn("amap_quota_config", "app_key_last_check_message", "ALTER TABLE `amap_quota_config` ADD COLUMN `app_key_last_check_message` VARCHAR(255) DEFAULT NULL COMMENT 'Key last verify message'");
            ensureColumn("amap_quota_config", "last_health_check_at", "ALTER TABLE `amap_quota_config` ADD COLUMN `last_health_check_at` DATETIME DEFAULT NULL COMMENT 'Health check timestamp'");
            ensureColumn("amap_quota_config", "last_health_check_message", "ALTER TABLE `amap_quota_config` ADD COLUMN `last_health_check_message` VARCHAR(255) DEFAULT NULL COMMENT 'Health check summary'");
            ensureColumn("amap_quota_config", "weather_daily_limit", "ALTER TABLE `amap_quota_config` ADD COLUMN `weather_daily_limit` INT NOT NULL DEFAULT '20000' COMMENT 'Weather daily limit'");
            ensureColumn("amap_quota_config", "weather_used_count", "ALTER TABLE `amap_quota_config` ADD COLUMN `weather_used_count` INT NOT NULL DEFAULT '0' COMMENT 'Weather used count'");
            ensureColumn("amap_quota_config", "location_daily_limit", "ALTER TABLE `amap_quota_config` ADD COLUMN `location_daily_limit` INT NOT NULL DEFAULT '20000' COMMENT 'Location/City daily limit'");
            ensureColumn("amap_quota_config", "location_used_count", "ALTER TABLE `amap_quota_config` ADD COLUMN `location_used_count` INT NOT NULL DEFAULT '0' COMMENT 'Location/City used count'");
            ensureColumn("amap_quota_config", "geocode_daily_limit", "ALTER TABLE `amap_quota_config` ADD COLUMN `geocode_daily_limit` INT NOT NULL DEFAULT '20000' COMMENT 'Geocode daily limit'");
            ensureColumn("amap_quota_config", "geocode_used_count", "ALTER TABLE `amap_quota_config` ADD COLUMN `geocode_used_count` INT NOT NULL DEFAULT '0' COMMENT 'Geocode used count'");
            ensureColumn("amap_quota_config", "city_daily_limit", "ALTER TABLE `amap_quota_config` ADD COLUMN `city_daily_limit` INT NOT NULL DEFAULT '10000' COMMENT 'City query daily limit'");
            ensureColumn("amap_quota_config", "city_used_count", "ALTER TABLE `amap_quota_config` ADD COLUMN `city_used_count` INT NOT NULL DEFAULT '0' COMMENT 'City query used count'");
            ensureColumn("amap_quota_config", "qps_limit", "ALTER TABLE `amap_quota_config` ADD COLUMN `qps_limit` INT NOT NULL DEFAULT '3' COMMENT 'AMap QPS throttle limit'");
            ensureColumn("amap_quota_config", "recharge_total", "ALTER TABLE `amap_quota_config` ADD COLUMN `recharge_total` INT NOT NULL DEFAULT '0' COMMENT 'Recharge total'");
            ensureColumn("amap_quota_config", "weather_recharge_total", "ALTER TABLE `amap_quota_config` ADD COLUMN `weather_recharge_total` INT NOT NULL DEFAULT '0' COMMENT 'Weather recharge total'");
            ensureColumn("amap_quota_config", "location_recharge_total", "ALTER TABLE `amap_quota_config` ADD COLUMN `location_recharge_total` INT NOT NULL DEFAULT '0' COMMENT 'Location recharge total'");
            ensureColumn("amap_quota_config", "geocode_recharge_total", "ALTER TABLE `amap_quota_config` ADD COLUMN `geocode_recharge_total` INT NOT NULL DEFAULT '0' COMMENT 'Geocode recharge total'");
            ensureColumn("amap_quota_config", "city_recharge_total", "ALTER TABLE `amap_quota_config` ADD COLUMN `city_recharge_total` INT NOT NULL DEFAULT '0' COMMENT 'City recharge total'");
            ensureColumn("amap_quota_config", "cache_redis_enabled", "ALTER TABLE `amap_quota_config` ADD COLUMN `cache_redis_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Cache redis enabled'");
            ensureColumn("amap_quota_config", "cache_redis_key_prefix", "ALTER TABLE `amap_quota_config` ADD COLUMN `cache_redis_key_prefix` VARCHAR(64) DEFAULT 'dahe:v2:amap:cache:' COMMENT 'Cache redis key prefix'");
            ensureColumn("amap_quota_config", "cache_region_ttl_minutes", "ALTER TABLE `amap_quota_config` ADD COLUMN `cache_region_ttl_minutes` INT NOT NULL DEFAULT 720 COMMENT 'Region cache ttl minutes'");
            ensureColumn("amap_quota_config", "cache_region_stale_minutes", "ALTER TABLE `amap_quota_config` ADD COLUMN `cache_region_stale_minutes` INT NOT NULL DEFAULT 1440 COMMENT 'Region stale cache minutes'");
            ensureColumn("amap_quota_config", "cache_weather_ttl_minutes", "ALTER TABLE `amap_quota_config` ADD COLUMN `cache_weather_ttl_minutes` INT NOT NULL DEFAULT 60 COMMENT 'Weather cache ttl minutes'");
            ensureColumn("amap_quota_config", "cache_local_region_max_entries", "ALTER TABLE `amap_quota_config` ADD COLUMN `cache_local_region_max_entries` INT NOT NULL DEFAULT 256 COMMENT 'Local region cache max entries'");
            ensureColumn("amap_quota_config", "cache_local_weather_max_entries", "ALTER TABLE `amap_quota_config` ADD COLUMN `cache_local_weather_max_entries` INT NOT NULL DEFAULT 256 COMMENT 'Local weather cache max entries'");
            ensureColumn("amap_quota_config", "audit_auto_purge_enabled", "ALTER TABLE `amap_quota_config` ADD COLUMN `audit_auto_purge_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Audit auto purge enabled'");
            ensureColumn("amap_quota_config", "audit_retain_days", "ALTER TABLE `amap_quota_config` ADD COLUMN `audit_retain_days` INT NOT NULL DEFAULT 90 COMMENT 'Audit retain days'");
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS `amap_api_audit` (" +
                            "`id` BIGINT NOT NULL COMMENT 'Primary key'," +
                            "`record_date` DATE NOT NULL COMMENT 'Record date'," +
                            "`user_id` BIGINT DEFAULT NULL COMMENT 'Operator user id'," +
                            "`operator_name` VARCHAR(64) DEFAULT NULL COMMENT 'Operator name'," +
                            "`biz_scene` VARCHAR(64) DEFAULT NULL COMMENT 'Business scene'," +
                            "`api_type` VARCHAR(16) DEFAULT NULL COMMENT 'Billing api type: weather/location'," +
                            "`api_path` VARCHAR(128) DEFAULT NULL COMMENT 'AMap API path'," +
                            "`request_source` VARCHAR(64) DEFAULT NULL COMMENT 'Request source'," +
                            "`success_flag` TINYINT(1) DEFAULT '1' COMMENT '1=success,0=failed'," +
                            "`cost_ms` INT DEFAULT NULL COMMENT 'Request cost time(ms)'," +
                            "`error_message` VARCHAR(255) DEFAULT NULL COMMENT 'Error details'," +
                            "`deleted` TINYINT(1) DEFAULT '0'," +
                            "`created_at` DATETIME DEFAULT CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (`id`)," +
                            "INDEX `idx_amap_audit_date` (`record_date`)," +
                            "INDEX `idx_amap_audit_scene` (`biz_scene`)," +
                            "INDEX `idx_amap_audit_type_date` (`api_type`,`record_date`)," +
                            "INDEX `idx_amap_audit_success` (`success_flag`)," +
                            "INDEX `idx_amap_audit_created` (`created_at`)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AMap API audit log'"
            );
            ensureColumn("amap_api_audit", "api_type", "ALTER TABLE `amap_api_audit` ADD COLUMN `api_type` VARCHAR(16) DEFAULT NULL COMMENT 'Billing api type: weather/location'");
            ensureColumn("amap_api_audit", "deleted", "ALTER TABLE `amap_api_audit` ADD COLUMN `deleted` TINYINT(1) DEFAULT 0");
            ensureIndex("amap_api_audit", "idx_amap_audit_type_date", "ALTER TABLE `amap_api_audit` ADD INDEX `idx_amap_audit_type_date` (`api_type`,`record_date`)");
            ensureIndex("amap_api_audit", "idx_amap_audit_deleted", "ALTER TABLE `amap_api_audit` ADD INDEX `idx_amap_audit_deleted` (`deleted`,`created_at`)");
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS `amap_usage_daily` (" +
                            "`id` BIGINT NOT NULL COMMENT 'Primary key'," +
                            "`record_date` DATE NOT NULL COMMENT 'Usage date'," +
                            "`api_type` VARCHAR(16) NOT NULL COMMENT 'Billing api type: weather/location'," +
                            "`remote_count` INT NOT NULL DEFAULT '0' COMMENT 'Official remote usage count'," +
                            "`created_at` DATETIME DEFAULT CURRENT_TIMESTAMP," +
                            "`updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (`id`)," +
                            "UNIQUE KEY `uk_amap_usage_day_type` (`record_date`,`api_type`)," +
                            "INDEX `idx_amap_usage_type_date` (`api_type`,`record_date`)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AMap official remote usage daily snapshot'"
            );
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS `amap_usage_monthly` (" +
                            "`id` BIGINT NOT NULL COMMENT 'Primary key'," +
                            "`record_month` DATE NOT NULL COMMENT 'Usage month(first day of month)'," +
                            "`api_type` VARCHAR(16) NOT NULL COMMENT 'Billing api type: weather/location'," +
                            "`remote_count` INT NOT NULL DEFAULT '0' COMMENT 'Official remote usage count'," +
                            "`warning_sent` TINYINT(1) NOT NULL DEFAULT '0' COMMENT 'Quota warning sent flag'," +
                            "`warning_sent_at` DATETIME DEFAULT NULL COMMENT 'Quota warning sent time'," +
                            "`created_at` DATETIME DEFAULT CURRENT_TIMESTAMP," +
                            "`updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (`id`)," +
                            "UNIQUE KEY `uk_amap_usage_month_type` (`record_month`,`api_type`)," +
                            "INDEX `idx_amap_usage_month_type` (`api_type`,`record_month`)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AMap official remote usage monthly snapshot'"
            );
            ensureColumn("amap_quota_config", "weather_monthly_limit", "ALTER TABLE `amap_quota_config` ADD COLUMN `weather_monthly_limit` INT NOT NULL DEFAULT 20000 COMMENT 'Weather monthly limit'");
            ensureColumn("amap_quota_config", "location_monthly_limit", "ALTER TABLE `amap_quota_config` ADD COLUMN `location_monthly_limit` INT NOT NULL DEFAULT 20000 COMMENT 'Location monthly limit'");
            ensureIndex("amap_usage_daily", "idx_amap_usage_type_date", "ALTER TABLE `amap_usage_daily` ADD INDEX `idx_amap_usage_type_date` (`api_type`,`record_date`)");
            ensureIndex("amap_usage_monthly", "idx_amap_usage_month_type", "ALTER TABLE `amap_usage_monthly` ADD INDEX `idx_amap_usage_month_type` (`api_type`,`record_month`)");
            jdbcTemplate.update(
                    "UPDATE `amap_quota_config` " +
                            "SET `weather_monthly_limit` = GREATEST(IFNULL(`weather_monthly_limit`,0), IFNULL(`weather_daily_limit`,0), 0)"
            );
            jdbcTemplate.update(
                    "UPDATE `amap_quota_config` " +
                            "SET `location_monthly_limit` = GREATEST(IFNULL(`location_monthly_limit`,0), IFNULL(`location_daily_limit`, IFNULL(`geocode_daily_limit`,0)), IFNULL(`city_daily_limit`,0), 0)"
            );
            jdbcTemplate.update(
                    "INSERT INTO `amap_usage_daily` (`id`,`record_date`,`api_type`,`remote_count`,`created_at`,`updated_at`) " +
                            "SELECT UUID_SHORT(), `record_date`, 'weather', GREATEST(IFNULL(`weather_used_count`,0),0), NOW(), NOW() " +
                            "FROM `amap_quota_config` " +
                            "WHERE IFNULL(`weather_used_count`,0) > 0 " +
                            "ON DUPLICATE KEY UPDATE `remote_count`=GREATEST(`remote_count`, VALUES(`remote_count`)), `updated_at`=NOW()"
            );
            jdbcTemplate.update(
                    "INSERT INTO `amap_usage_daily` (`id`,`record_date`,`api_type`,`remote_count`,`created_at`,`updated_at`) " +
                            "SELECT UUID_SHORT(), `record_date`, 'location', GREATEST(IFNULL(`location_used_count`, IFNULL(`geocode_used_count`,0) + IFNULL(`city_used_count`,0)),0), NOW(), NOW() " +
                            "FROM `amap_quota_config` " +
                    "WHERE GREATEST(IFNULL(`location_used_count`, IFNULL(`geocode_used_count`,0) + IFNULL(`city_used_count`,0)),0) > 0 " +
                            "ON DUPLICATE KEY UPDATE `remote_count`=GREATEST(`remote_count`, VALUES(`remote_count`)), `updated_at`=NOW()"
            );
            jdbcTemplate.update(
                    "INSERT INTO `amap_usage_monthly` (`id`,`record_month`,`api_type`,`remote_count`,`warning_sent`,`warning_sent_at`,`created_at`,`updated_at`) " +
                            "SELECT UUID_SHORT(), DATE_FORMAT(COALESCE(`record_date`, CURDATE()), '%Y-%m-01'), 'weather', GREATEST(IFNULL(`weather_used_count`,0),0), 0, NULL, NOW(), NOW() " +
                            "FROM `amap_quota_config` " +
                            "WHERE IFNULL(`weather_used_count`,0) > 0 " +
                            "ON DUPLICATE KEY UPDATE `remote_count`=GREATEST(`remote_count`, VALUES(`remote_count`)), `updated_at`=NOW()"
            );
            jdbcTemplate.update(
                    "INSERT INTO `amap_usage_monthly` (`id`,`record_month`,`api_type`,`remote_count`,`warning_sent`,`warning_sent_at`,`created_at`,`updated_at`) " +
                            "SELECT UUID_SHORT(), DATE_FORMAT(COALESCE(`record_date`, CURDATE()), '%Y-%m-01'), 'location', GREATEST(IFNULL(`location_used_count`, IFNULL(`geocode_used_count`,0) + IFNULL(`city_used_count`,0)),0), 0, NULL, NOW(), NOW() " +
                            "FROM `amap_quota_config` " +
                            "WHERE GREATEST(IFNULL(`location_used_count`, IFNULL(`geocode_used_count`,0) + IFNULL(`city_used_count`,0)),0) > 0 " +
                            "ON DUPLICATE KEY UPDATE `remote_count`=GREATEST(`remote_count`, VALUES(`remote_count`)), `updated_at`=NOW()"
            );
            jdbcTemplate.update(
                    "DELETE FROM `amap_usage_daily` WHERE `record_date` < DATE_SUB(CURDATE(), INTERVAL 6 DAY)"
            );
            } catch (Exception e) {
            log.warn("AMap schema bootstrap skipped: {}", safeMessage(e));
        }
    }

    /**
     * 导出模块结构自举。
     */
    private void bootstrapExportModule() {
        try {
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS `export_field_dict` (" +
                            "`id` BIGINT NOT NULL COMMENT 'Primary key'," +
                            "`module_key` VARCHAR(32) NOT NULL COMMENT 'Module key: farm/seed'," +
                            "`field_code` VARCHAR(64) NOT NULL COMMENT 'Field code'," +
                            "`field_name` VARCHAR(128) NOT NULL COMMENT 'Field display name'," +
                            "`data_type` VARCHAR(32) DEFAULT 'string' COMMENT 'string/number/date/datetime/json'," +
                            "`description` VARCHAR(255) DEFAULT NULL COMMENT 'Field description'," +
                            "`example_value` VARCHAR(255) DEFAULT NULL COMMENT 'Example value'," +
                            "`deleted` TINYINT(1) DEFAULT '0'," +
                            "`created_at` DATETIME DEFAULT CURRENT_TIMESTAMP," +
                            "`updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (`id`)," +
                            "UNIQUE KEY `uk_export_field_code` (`module_key`, `field_code`)," +
                            "INDEX `idx_export_field_module` (`module_key`)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Export field dictionary'"
            );
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS `export_template` (" +
                            "`id` BIGINT NOT NULL COMMENT 'Primary key'," +
                            "`module_key` VARCHAR(32) NOT NULL COMMENT 'Module key'," +
                            "`template_code` VARCHAR(64) NOT NULL COMMENT 'Template code'," +
                            "`template_name` VARCHAR(128) NOT NULL COMMENT 'Template name'," +
                            "`version_no` INT NOT NULL DEFAULT '1' COMMENT 'Version number'," +
                            "`status` VARCHAR(16) DEFAULT 'enabled' COMMENT 'enabled/disabled'," +
                            "`fields_json` LONGTEXT NOT NULL COMMENT 'Ordered field codes JSON array'," +
                            "`remark` VARCHAR(255) DEFAULT NULL COMMENT 'Remark'," +
                            "`deleted` TINYINT(1) DEFAULT '0'," +
                            "`created_at` DATETIME DEFAULT CURRENT_TIMESTAMP," +
                            "`updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (`id`)," +
                            "UNIQUE KEY `uk_export_template_ver` (`module_key`, `template_code`, `version_no`)," +
                            "INDEX `idx_export_template_status` (`module_key`, `template_code`, `status`)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Export template'"
            );
        } catch (Exception e) {
            log.warn("Export schema bootstrap skipped: {}", safeMessage(e));
        }
    }

    /**
     * 种子导出模块保留结构入口，不在启动期改写当前数据。
     */
    private void ensureSeedExportCompatibility() {
    }

    /**
     * 启动期不再灌入默认导出字段或模板。
     */
    private void initExportDefaults() {
    }

    /**
     * 农事记录策略表自举（编辑窗口、操作权限等）。
     */
    private void bootstrapFarmPolicyModule() {
        try {
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS `record_policy_config` (" +
                            "`id` BIGINT NOT NULL COMMENT 'Primary key'," +
                            "`edit_window_hours` INT NOT NULL DEFAULT '48' COMMENT 'Editable window(hours),0=no limit'," +
                            "`allow_operator_update` TINYINT(1) NOT NULL DEFAULT '1' COMMENT 'Operator can update'," +
                            "`allow_operator_delete` TINYINT(1) NOT NULL DEFAULT '1' COMMENT 'Operator can delete'," +
                            "`remark` VARCHAR(255) DEFAULT NULL COMMENT 'Remark'," +
                            "`created_at` DATETIME DEFAULT CURRENT_TIMESTAMP," +
                            "`updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (`id`)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Farm record operation policy'"
            );
            } catch (Exception e) {
            log.warn("Farm policy schema bootstrap skipped: {}", safeMessage(e));
        }
    }

    /**
     * 资源中心相关表自举：
     * 资源主表、目录表、上传策略表与历史目录归一化。
     */
    private void bootstrapAssetModule() {
        try {
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS `miniapp_static_asset` (" +
                            "`id` BIGINT NOT NULL COMMENT 'Primary key'," +
                            "`display_name` VARCHAR(120) DEFAULT NULL COMMENT 'Display name'," +
                            "`storage_name` VARCHAR(64) NOT NULL COMMENT 'Storage name without extension'," +
                            "`file_ext` VARCHAR(16) NOT NULL COMMENT 'Storage extension'," +
                            "`file_url` VARCHAR(1024) NOT NULL COMMENT 'Public url'," +
                            "`file_type` VARCHAR(32) DEFAULT 'file' COMMENT 'image/file'," +
                            "`size_bytes` BIGINT DEFAULT NULL COMMENT 'File size(bytes)'," +
                            "`remark` VARCHAR(255) DEFAULT NULL COMMENT 'Remark'," +
                            "`created_by_user_id` BIGINT DEFAULT NULL COMMENT 'Creator user id'," +
                            "`created_by_name` VARCHAR(64) DEFAULT NULL COMMENT 'Creator name'," +
                            "`updated_by_user_id` BIGINT DEFAULT NULL COMMENT 'Updater user id'," +
                            "`updated_by_name` VARCHAR(64) DEFAULT NULL COMMENT 'Updater name'," +
                            "`created_at` DATETIME DEFAULT CURRENT_TIMESTAMP," +
                            "`updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (`id`)," +
                            "UNIQUE KEY `uk_miniapp_static_asset_storage_name` (`storage_name`)," +
                            "INDEX `idx_miniapp_static_asset_updated` (`updated_at`)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Miniapp static assets'"
            );
            ensureColumn("miniapp_static_asset", "display_name", "ALTER TABLE `miniapp_static_asset` ADD COLUMN `display_name` VARCHAR(120) DEFAULT NULL COMMENT 'Display name'");
            ensureColumn("miniapp_static_asset", "storage_name", "ALTER TABLE `miniapp_static_asset` ADD COLUMN `storage_name` VARCHAR(64) NOT NULL COMMENT 'Storage name without extension'");
            ensureColumn("miniapp_static_asset", "file_ext", "ALTER TABLE `miniapp_static_asset` ADD COLUMN `file_ext` VARCHAR(16) NOT NULL COMMENT 'Storage extension'");
            ensureColumn("miniapp_static_asset", "file_url", "ALTER TABLE `miniapp_static_asset` ADD COLUMN `file_url` VARCHAR(1024) NOT NULL COMMENT 'Public url'");
            ensureColumn("miniapp_static_asset", "file_type", "ALTER TABLE `miniapp_static_asset` ADD COLUMN `file_type` VARCHAR(32) DEFAULT 'file' COMMENT 'image/file'");
            ensureColumn("miniapp_static_asset", "size_bytes", "ALTER TABLE `miniapp_static_asset` ADD COLUMN `size_bytes` BIGINT DEFAULT NULL COMMENT 'File size(bytes)'");
            ensureColumn("miniapp_static_asset", "remark", "ALTER TABLE `miniapp_static_asset` ADD COLUMN `remark` VARCHAR(255) DEFAULT NULL COMMENT 'Remark'");
            ensureColumn("miniapp_static_asset", "created_by_user_id", "ALTER TABLE `miniapp_static_asset` ADD COLUMN `created_by_user_id` BIGINT DEFAULT NULL COMMENT 'Creator user id'");
            ensureColumn("miniapp_static_asset", "created_by_name", "ALTER TABLE `miniapp_static_asset` ADD COLUMN `created_by_name` VARCHAR(64) DEFAULT NULL COMMENT 'Creator name'");
            ensureColumn("miniapp_static_asset", "updated_by_user_id", "ALTER TABLE `miniapp_static_asset` ADD COLUMN `updated_by_user_id` BIGINT DEFAULT NULL COMMENT 'Updater user id'");
            ensureColumn("miniapp_static_asset", "updated_by_name", "ALTER TABLE `miniapp_static_asset` ADD COLUMN `updated_by_name` VARCHAR(64) DEFAULT NULL COMMENT 'Updater name'");
            ensureIndex("miniapp_static_asset", "uk_miniapp_static_asset_storage_name", "ALTER TABLE `miniapp_static_asset` ADD UNIQUE INDEX `uk_miniapp_static_asset_storage_name` (`storage_name`)");
            ensureIndex("miniapp_static_asset", "idx_miniapp_static_asset_updated", "ALTER TABLE `miniapp_static_asset` ADD INDEX `idx_miniapp_static_asset_updated` (`updated_at`)");
            jdbcTemplate.update("UPDATE `miniapp_static_asset` SET `display_name`=`storage_name` WHERE (`display_name` IS NULL OR TRIM(`display_name`)='') AND `storage_name` IS NOT NULL");
            jdbcTemplate.update("UPDATE `miniapp_static_asset` SET `created_at`=NOW() WHERE `created_at` IS NULL");
            jdbcTemplate.update("UPDATE `miniapp_static_asset` SET `updated_at`=COALESCE(`updated_at`,`created_at`,NOW()) WHERE `updated_at` IS NULL");
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS `media_asset` (" +
                            "`id` BIGINT NOT NULL COMMENT 'Primary key'," +
                            "`file_name` VARCHAR(255) NOT NULL COMMENT 'Display file name'," +
                            "`file_url` VARCHAR(1024) NOT NULL COMMENT 'Public url'," +
                            "`file_type` VARCHAR(32) DEFAULT 'file' COMMENT 'image/file'," +
                            "`folder_path` VARCHAR(255) DEFAULT '/默认' COMMENT 'Folder path'," +
                            "`source_type` VARCHAR(32) DEFAULT 'system_upload' COMMENT 'admin_upload/miniapp_upload/system_upload'," +
                            "`recycle_flag` TINYINT(1) DEFAULT '0' COMMENT '0=normal,1=recycled'," +
                            "`recycled_at` DATETIME DEFAULT NULL COMMENT 'Recycle time'," +
                            "`recycled_by_user_id` BIGINT DEFAULT NULL COMMENT 'Recycle operator user id'," +
                            "`module_key` VARCHAR(64) DEFAULT NULL COMMENT 'Module key'," +
                            "`biz_id` BIGINT DEFAULT NULL COMMENT 'Business id'," +
                            "`sort_order` INT DEFAULT 0 COMMENT 'Display sort order'," +
                            "`size_bytes` BIGINT DEFAULT NULL COMMENT 'File size(bytes)'," +
                            "`created_by_user_id` BIGINT DEFAULT NULL COMMENT 'Creator user id'," +
                            "`created_by_name` VARCHAR(64) DEFAULT NULL COMMENT 'Creator name'," +
                            "`remark` VARCHAR(255) DEFAULT NULL COMMENT 'Remark'," +
                            "`review_status` VARCHAR(16) DEFAULT 'approved' COMMENT 'approved/pending/rejected'," +
                            "`reviewed_at` DATETIME DEFAULT NULL COMMENT 'Review timestamp'," +
                            "`reviewed_by_user_id` BIGINT DEFAULT NULL COMMENT 'Review operator user id'," +
                            "`review_remark` VARCHAR(255) DEFAULT NULL COMMENT 'Review remark'," +
                            "`locked_flag` TINYINT(1) DEFAULT '0' COMMENT '0=unlocked,1=locked'," +
                            "`lock_password_hash` VARCHAR(255) DEFAULT NULL COMMENT 'Delete unlock password hash'," +
                            "`lock_remark` VARCHAR(255) DEFAULT NULL COMMENT 'Lock remark'," +
                            "`lock_updated_at` DATETIME DEFAULT NULL COMMENT 'Lock updated time'," +
                            "`lock_updated_by_user_id` BIGINT DEFAULT NULL COMMENT 'Lock updated user id'," +
                            "`lock_updated_by_name` VARCHAR(64) DEFAULT NULL COMMENT 'Lock updated user name'," +
                            "`deleted` TINYINT(1) DEFAULT '0'," +
                            "`created_at` DATETIME DEFAULT CURRENT_TIMESTAMP," +
                            "`updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (`id`)," +
                            "INDEX `idx_media_module` (`module_key`)," +
                            "INDEX `idx_media_biz` (`biz_id`)," +
                            "INDEX `idx_media_folder` (`folder_path`)," +
                            "INDEX `idx_media_source` (`source_type`)," +
                            "INDEX `idx_media_recycle` (`recycle_flag`,`created_at`)," +
                            "INDEX `idx_media_review` (`review_status`,`source_type`,`created_at`)," +
                            "INDEX `idx_media_locked` (`locked_flag`,`recycle_flag`,`created_at`)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Media asset library'"
            );
            ensureColumn("media_asset", "sort_order", "ALTER TABLE `media_asset` ADD COLUMN `sort_order` INT DEFAULT 0 COMMENT 'Display sort order'");
            ensureColumn("media_asset", "folder_path", "ALTER TABLE `media_asset` ADD COLUMN `folder_path` VARCHAR(255) DEFAULT '/默认' COMMENT 'Folder path'");
            ensureColumn("media_asset", "source_type", "ALTER TABLE `media_asset` ADD COLUMN `source_type` VARCHAR(32) DEFAULT 'system_upload' COMMENT 'admin_upload/miniapp_upload/system_upload'");
            ensureColumn("media_asset", "recycle_flag", "ALTER TABLE `media_asset` ADD COLUMN `recycle_flag` TINYINT(1) DEFAULT 0 COMMENT '0=normal,1=recycled'");
            ensureColumn("media_asset", "recycled_at", "ALTER TABLE `media_asset` ADD COLUMN `recycled_at` DATETIME DEFAULT NULL COMMENT 'Recycle time'");
            ensureColumn("media_asset", "recycled_by_user_id", "ALTER TABLE `media_asset` ADD COLUMN `recycled_by_user_id` BIGINT DEFAULT NULL COMMENT 'Recycle operator user id'");
            ensureColumn("media_asset", "review_status", "ALTER TABLE `media_asset` ADD COLUMN `review_status` VARCHAR(16) DEFAULT 'approved' COMMENT 'approved/pending/rejected'");
            ensureColumn("media_asset", "reviewed_at", "ALTER TABLE `media_asset` ADD COLUMN `reviewed_at` DATETIME DEFAULT NULL COMMENT 'Review timestamp'");
            ensureColumn("media_asset", "reviewed_by_user_id", "ALTER TABLE `media_asset` ADD COLUMN `reviewed_by_user_id` BIGINT DEFAULT NULL COMMENT 'Review operator user id'");
            ensureColumn("media_asset", "review_remark", "ALTER TABLE `media_asset` ADD COLUMN `review_remark` VARCHAR(255) DEFAULT NULL COMMENT 'Review remark'");
            ensureColumn("media_asset", "locked_flag", "ALTER TABLE `media_asset` ADD COLUMN `locked_flag` TINYINT(1) DEFAULT 0 COMMENT '0=unlocked,1=locked'");
            ensureColumn("media_asset", "lock_password_hash", "ALTER TABLE `media_asset` ADD COLUMN `lock_password_hash` VARCHAR(255) DEFAULT NULL COMMENT 'Delete unlock password hash'");
            ensureColumn("media_asset", "lock_remark", "ALTER TABLE `media_asset` ADD COLUMN `lock_remark` VARCHAR(255) DEFAULT NULL COMMENT 'Lock remark'");
            ensureColumn("media_asset", "lock_updated_at", "ALTER TABLE `media_asset` ADD COLUMN `lock_updated_at` DATETIME DEFAULT NULL COMMENT 'Lock updated time'");
            ensureColumn("media_asset", "lock_updated_by_user_id", "ALTER TABLE `media_asset` ADD COLUMN `lock_updated_by_user_id` BIGINT DEFAULT NULL COMMENT 'Lock updated user id'");
            ensureColumn("media_asset", "lock_updated_by_name", "ALTER TABLE `media_asset` ADD COLUMN `lock_updated_by_name` VARCHAR(64) DEFAULT NULL COMMENT 'Lock updated user name'");
            ensureIndex("media_asset", "idx_media_folder", "ALTER TABLE `media_asset` ADD INDEX `idx_media_folder` (`folder_path`)");
            ensureIndex("media_asset", "idx_media_source", "ALTER TABLE `media_asset` ADD INDEX `idx_media_source` (`source_type`)");
            ensureIndex("media_asset", "idx_media_recycle", "ALTER TABLE `media_asset` ADD INDEX `idx_media_recycle` (`recycle_flag`,`created_at`)");
            ensureIndex("media_asset", "idx_media_review", "ALTER TABLE `media_asset` ADD INDEX `idx_media_review` (`review_status`,`source_type`,`created_at`)");
            ensureIndex("media_asset", "idx_media_locked", "ALTER TABLE `media_asset` ADD INDEX `idx_media_locked` (`locked_flag`,`recycle_flag`,`created_at`)");
            jdbcTemplate.update("UPDATE `media_asset` SET `locked_flag`=0 WHERE `locked_flag` IS NULL");
            jdbcTemplate.update("UPDATE `media_asset` SET `created_at`=NOW() WHERE `created_at` IS NULL");
            jdbcTemplate.update("UPDATE `media_asset` SET `updated_at`=COALESCE(`updated_at`,`created_at`,NOW()) WHERE `updated_at` IS NULL");
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS `media_asset_reference` (" +
                            "`id` BIGINT NOT NULL COMMENT 'Primary key'," +
                            "`asset_id` BIGINT NOT NULL COMMENT 'Media asset id'," +
                            "`module_key` VARCHAR(64) NOT NULL COMMENT 'Module key'," +
                            "`biz_id` BIGINT NOT NULL COMMENT 'Business id'," +
                            "`created_at` DATETIME DEFAULT CURRENT_TIMESTAMP," +
                            "`updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (`id`)," +
                            "UNIQUE KEY `uk_media_asset_reference` (`asset_id`,`module_key`,`biz_id`)," +
                            "INDEX `idx_media_asset_reference_asset` (`asset_id`)," +
                            "INDEX `idx_media_asset_reference_module` (`module_key`,`biz_id`)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Current asset reference snapshot'"
            );
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS `media_asset_folder` (" +
                            "`id` BIGINT NOT NULL COMMENT 'Primary key'," +
                            "`folder_path` VARCHAR(255) NOT NULL COMMENT 'Folder path'," +
                            "`remark` VARCHAR(255) DEFAULT NULL COMMENT 'Folder remark'," +
                            "`protected_flag` TINYINT(1) DEFAULT '0' COMMENT '1 protected,0 normal'," +
                            "`created_by_user_id` BIGINT DEFAULT NULL COMMENT 'Creator user id'," +
                            "`created_by_name` VARCHAR(64) DEFAULT NULL COMMENT 'Creator name'," +
                            "`deleted` TINYINT(1) DEFAULT '0'," +
                            "`created_at` DATETIME DEFAULT CURRENT_TIMESTAMP," +
                            "`updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (`id`)," +
                            "UNIQUE KEY `uk_media_asset_folder_path` (`folder_path`)," +
                            "INDEX `idx_media_asset_folder_deleted` (`deleted`)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Media asset folder registry'"
            );
            ensureColumn("media_asset_folder", "folder_path", "ALTER TABLE `media_asset_folder` ADD COLUMN `folder_path` VARCHAR(255) NOT NULL COMMENT 'Folder path'");
            ensureColumn("media_asset_folder", "remark", "ALTER TABLE `media_asset_folder` ADD COLUMN `remark` VARCHAR(255) DEFAULT NULL COMMENT 'Folder remark'");
            ensureColumn("media_asset_folder", "protected_flag", "ALTER TABLE `media_asset_folder` ADD COLUMN `protected_flag` TINYINT(1) DEFAULT 0 COMMENT '1 protected,0 normal'");
            ensureColumn("media_asset_folder", "created_by_user_id", "ALTER TABLE `media_asset_folder` ADD COLUMN `created_by_user_id` BIGINT DEFAULT NULL COMMENT 'Creator user id'");
            ensureColumn("media_asset_folder", "created_by_name", "ALTER TABLE `media_asset_folder` ADD COLUMN `created_by_name` VARCHAR(64) DEFAULT NULL COMMENT 'Creator name'");
            ensureColumn("media_asset_folder", "deleted", "ALTER TABLE `media_asset_folder` ADD COLUMN `deleted` TINYINT(1) DEFAULT 0");
            ensureIndex("media_asset_folder", "uk_media_asset_folder_path", "ALTER TABLE `media_asset_folder` ADD UNIQUE INDEX `uk_media_asset_folder_path` (`folder_path`)");
            ensureIndex("media_asset_folder", "idx_media_asset_folder_deleted", "ALTER TABLE `media_asset_folder` ADD INDEX `idx_media_asset_folder_deleted` (`deleted`)");
            List<String> discoveredFolders = jdbcTemplate.queryForList(
                    "SELECT DISTINCT IFNULL(NULLIF(TRIM(`folder_path`),''),'/默认') AS `folder_path` FROM `media_asset`",
                    String.class
            );
            for (String rawPath : discoveredFolders) {
                String path = normalizeAssetFolderPath(rawPath);
                if (!StringUtils.hasText(path)) {
                    continue;
                }
                int protectedFlag = ("/默认".equals(path) || "/小程序上传图片".equals(path)) ? 1 : 0;
            }

            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS `media_asset_policy_config` (" +
                            "`id` BIGINT NOT NULL COMMENT 'Primary key'," +
                            "`miniapp_daily_upload_limit` INT NOT NULL DEFAULT '500' COMMENT 'Miniapp daily upload count limit,0=unlimited'," +
                            "`miniapp_daily_upload_size_mb` INT NOT NULL DEFAULT '2048' COMMENT 'Miniapp daily upload size limit(MB),0=unlimited'," +
                            "`miniapp_single_file_max_mb` INT NOT NULL DEFAULT '30' COMMENT 'Miniapp single file max size(MB),0=unlimited'," +
                            "`miniapp_allowed_file_types` VARCHAR(64) DEFAULT 'image,file' COMMENT 'Allowed file types for miniapp'," +
                            "`miniapp_require_review` TINYINT(1) NOT NULL DEFAULT '0' COMMENT 'Miniapp upload requires review'," +
                            "`admin_daily_upload_limit` INT NOT NULL DEFAULT '0' COMMENT 'Admin daily upload count limit,0=unlimited'," +
                            "`admin_daily_upload_size_mb` INT NOT NULL DEFAULT '0' COMMENT 'Admin daily upload size limit(MB),0=unlimited'," +
                            "`admin_single_file_max_mb` INT NOT NULL DEFAULT '0' COMMENT 'Admin single file max size(MB),0=unlimited'," +
                            "`admin_allowed_file_types` VARCHAR(64) DEFAULT 'image,file' COMMENT 'Allowed file types for admin'," +
                            "`admin_require_review` TINYINT(1) NOT NULL DEFAULT '0' COMMENT 'Admin upload requires review'," +
                            "`operator_daily_upload_limit` INT NOT NULL DEFAULT '500' COMMENT 'Operator daily upload count limit,0=unlimited'," +
                            "`operator_daily_upload_size_mb` INT NOT NULL DEFAULT '2048' COMMENT 'Operator daily upload size limit(MB),0=unlimited'," +
                            "`operator_single_file_max_mb` INT NOT NULL DEFAULT '30' COMMENT 'Operator single file max size(MB),0=unlimited'," +
                            "`operator_allowed_file_types` VARCHAR(64) DEFAULT 'image,file' COMMENT 'Allowed file types for operator'," +
                            "`operator_require_review` TINYINT(1) NOT NULL DEFAULT '0' COMMENT 'Operator upload requires review'," +
                            "`strict_source_purge_retain_days` INT NOT NULL DEFAULT '7' COMMENT 'Strict source purge retain days'," +
                            "`lock_password_hash` VARCHAR(255) DEFAULT NULL COMMENT 'Global asset lock password hash'," +
                            "`lock_password_updated_at` DATETIME DEFAULT NULL COMMENT 'Global asset lock password updated time'," +
                            "`lock_password_updated_by_user_id` BIGINT DEFAULT NULL COMMENT 'Global asset lock password updated by user id'," +
                            "`lock_password_updated_by_name` VARCHAR(64) DEFAULT NULL COMMENT 'Global asset lock password updated by name'," +
                            "`remark` VARCHAR(255) DEFAULT NULL COMMENT 'Remark'," +
                            "`created_at` DATETIME DEFAULT CURRENT_TIMESTAMP," +
                            "`updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (`id`)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Media asset upload policy config'"
            );
            ensureColumn("media_asset_policy_config", "miniapp_daily_upload_limit", "ALTER TABLE `media_asset_policy_config` ADD COLUMN `miniapp_daily_upload_limit` INT NOT NULL DEFAULT 500 COMMENT 'Miniapp daily upload count limit,0=unlimited'");
            ensureColumn("media_asset_policy_config", "miniapp_daily_upload_size_mb", "ALTER TABLE `media_asset_policy_config` ADD COLUMN `miniapp_daily_upload_size_mb` INT NOT NULL DEFAULT 2048 COMMENT 'Miniapp daily upload size limit(MB),0=unlimited'");
            ensureColumn("media_asset_policy_config", "miniapp_single_file_max_mb", "ALTER TABLE `media_asset_policy_config` ADD COLUMN `miniapp_single_file_max_mb` INT NOT NULL DEFAULT 30 COMMENT 'Miniapp single file max size(MB),0=unlimited'");
            ensureColumn("media_asset_policy_config", "miniapp_allowed_file_types", "ALTER TABLE `media_asset_policy_config` ADD COLUMN `miniapp_allowed_file_types` VARCHAR(64) DEFAULT 'image,file' COMMENT 'Allowed file types for miniapp'");
            ensureColumn("media_asset_policy_config", "miniapp_require_review", "ALTER TABLE `media_asset_policy_config` ADD COLUMN `miniapp_require_review` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Miniapp upload requires review'");
            ensureColumn("media_asset_policy_config", "admin_daily_upload_limit", "ALTER TABLE `media_asset_policy_config` ADD COLUMN `admin_daily_upload_limit` INT NOT NULL DEFAULT 0 COMMENT 'Admin daily upload count limit,0=unlimited'");
            ensureColumn("media_asset_policy_config", "admin_daily_upload_size_mb", "ALTER TABLE `media_asset_policy_config` ADD COLUMN `admin_daily_upload_size_mb` INT NOT NULL DEFAULT 0 COMMENT 'Admin daily upload size limit(MB),0=unlimited'");
            ensureColumn("media_asset_policy_config", "admin_single_file_max_mb", "ALTER TABLE `media_asset_policy_config` ADD COLUMN `admin_single_file_max_mb` INT NOT NULL DEFAULT 0 COMMENT 'Admin single file max size(MB),0=unlimited'");
            ensureColumn("media_asset_policy_config", "admin_allowed_file_types", "ALTER TABLE `media_asset_policy_config` ADD COLUMN `admin_allowed_file_types` VARCHAR(64) DEFAULT 'image,file' COMMENT 'Allowed file types for admin'");
            ensureColumn("media_asset_policy_config", "admin_require_review", "ALTER TABLE `media_asset_policy_config` ADD COLUMN `admin_require_review` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Admin upload requires review'");
            ensureColumn("media_asset_policy_config", "lock_password_hash", "ALTER TABLE `media_asset_policy_config` ADD COLUMN `lock_password_hash` VARCHAR(255) DEFAULT NULL COMMENT 'Global asset lock password hash'");
            ensureColumn("media_asset_policy_config", "lock_password_updated_at", "ALTER TABLE `media_asset_policy_config` ADD COLUMN `lock_password_updated_at` DATETIME DEFAULT NULL COMMENT 'Global asset lock password updated time'");
            ensureColumn("media_asset_policy_config", "lock_password_updated_by_user_id", "ALTER TABLE `media_asset_policy_config` ADD COLUMN `lock_password_updated_by_user_id` BIGINT DEFAULT NULL COMMENT 'Global asset lock password updated by user id'");
            ensureColumn("media_asset_policy_config", "lock_password_updated_by_name", "ALTER TABLE `media_asset_policy_config` ADD COLUMN `lock_password_updated_by_name` VARCHAR(64) DEFAULT NULL COMMENT 'Global asset lock password updated by name'");
            jdbcTemplate.update("UPDATE `media_asset_policy_config` SET `created_at`=NOW() WHERE `created_at` IS NULL");
            jdbcTemplate.update("UPDATE `media_asset_policy_config` SET `updated_at`=COALESCE(`updated_at`,`created_at`,NOW()) WHERE `updated_at` IS NULL");
            rebuildAssetReferenceSnapshot();
            } catch (Exception e) {
            log.warn("Asset schema bootstrap skipped: {}", safeMessage(e));
        }
    }

    private void rebuildAssetReferenceSnapshot() {
        jdbcTemplate.update("DELETE FROM `media_asset_reference`");
        jdbcTemplate.update(
                "INSERT INTO `media_asset_reference` (`id`,`asset_id`,`module_key`,`biz_id`,`created_at`,`updated_at`) " +
                        "SELECT UUID_SHORT(), `id`, TRIM(`module_key`), `biz_id`, NOW(), NOW() " +
                        "FROM `media_asset` " +
                        "WHERE `deleted`=0 AND `recycle_flag`=0 AND `biz_id` IS NOT NULL " +
                        "AND NULLIF(TRIM(`module_key`),'') IS NOT NULL"
        );
        if (tableExists("field")) {
            jdbcTemplate.update(
                    "INSERT IGNORE INTO `media_asset_reference` (`id`,`asset_id`,`module_key`,`biz_id`,`created_at`,`updated_at`) " +
                            "SELECT UUID_SHORT(), a.`id`, 'field', f.`id`, NOW(), NOW() " +
                            "FROM `field` f " +
                            "JOIN `media_asset` a ON a.`file_url`=f.`cover_image_url` " +
                            "WHERE f.`deleted`=0 AND a.`deleted`=0 AND a.`recycle_flag`=0 " +
                            "AND NULLIF(TRIM(f.`cover_image_url`),'') IS NOT NULL"
            );
        }
        if (tableExists("company_info")) {
            jdbcTemplate.update(
                    "INSERT IGNORE INTO `media_asset_reference` (`id`,`asset_id`,`module_key`,`biz_id`,`created_at`,`updated_at`) " +
                            "SELECT UUID_SHORT(), a.`id`, 'company_info', c.`id`, NOW(), NOW() " +
                            "FROM `company_info` c " +
                            "JOIN `media_asset` a ON a.`file_url` IN (c.`logo`, c.`banner`) " +
                            "WHERE c.`deleted`=0 AND a.`deleted`=0 AND a.`recycle_flag`=0"
            );
        }
        if (tableExists("company_product")) {
            jdbcTemplate.update(
                    "INSERT IGNORE INTO `media_asset_reference` (`id`,`asset_id`,`module_key`,`biz_id`,`created_at`,`updated_at`) " +
                            "SELECT UUID_SHORT(), a.`id`, 'company_product', p.`id`, NOW(), NOW() " +
                            "FROM `company_product` p " +
                            "JOIN `media_asset` a ON a.`file_url`=p.`image` " +
                            "WHERE p.`deleted`=0 AND a.`deleted`=0 AND a.`recycle_flag`=0 " +
                            "AND NULLIF(TRIM(p.`image`),'') IS NOT NULL"
            );
        }
        if (tableExists("company_honor")) {
            jdbcTemplate.update(
                    "INSERT IGNORE INTO `media_asset_reference` (`id`,`asset_id`,`module_key`,`biz_id`,`created_at`,`updated_at`) " +
                            "SELECT UUID_SHORT(), a.`id`, 'company_honor', h.`id`, NOW(), NOW() " +
                            "FROM `company_honor` h " +
                            "JOIN `media_asset` a ON a.`file_url`=h.`image` " +
                            "WHERE h.`deleted`=0 AND a.`deleted`=0 AND a.`recycle_flag`=0 " +
                            "AND NULLIF(TRIM(h.`image`),'') IS NOT NULL"
            );
        }
        if (tableExists("crop")) {
            jdbcTemplate.update(
                    "INSERT IGNORE INTO `media_asset_reference` (`id`,`asset_id`,`module_key`,`biz_id`,`created_at`,`updated_at`) " +
                            "SELECT UUID_SHORT(), a.`id`, CASE WHEN c.`node_type`='category' THEN 'crop_category' ELSE 'crop_variety' END, c.`id`, NOW(), NOW() " +
                            "FROM `crop` c " +
                            "JOIN `media_asset` a ON a.`file_url`=c.`image_url` " +
                            "WHERE c.`deleted`=0 AND a.`deleted`=0 AND a.`recycle_flag`=0 " +
                            "AND NULLIF(TRIM(c.`image_url`),'') IS NOT NULL"
            );
        }
        if (tableExists("user")) {
            jdbcTemplate.update(
                    "INSERT IGNORE INTO `media_asset_reference` (`id`,`asset_id`,`module_key`,`biz_id`,`created_at`,`updated_at`) " +
                            "SELECT UUID_SHORT(), a.`id`, 'auth', u.`id`, NOW(), NOW() " +
                            "FROM `user` u " +
                            "JOIN `media_asset` a ON a.`file_url` IN (u.`avatar_url`, u.`wx_avatar_url`) " +
                            "WHERE u.`deleted`=0 AND a.`deleted`=0 AND a.`recycle_flag`=0 " +
                            "AND (NULLIF(TRIM(u.`avatar_url`),'') IS NOT NULL OR NULLIF(TRIM(u.`wx_avatar_url`),'') IS NOT NULL)"
            );
        }
    }

    /**
     * 企业介绍模块表结构自举。
     */
    private void bootstrapCompanyModule() {
        try {
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS `company_info` (" +
                            "`id` BIGINT NOT NULL COMMENT 'Primary key'," +
                            "`company_name` VARCHAR(100) DEFAULT NULL COMMENT 'Company name'," +
                            "`logo` VARCHAR(512) DEFAULT NULL COMMENT 'Company logo url'," +
                            "`banner` VARCHAR(512) DEFAULT NULL COMMENT 'Company banner url'," +
                            "`introduction` VARCHAR(2000) DEFAULT NULL COMMENT 'Company introduction'," +
                            "`mission` VARCHAR(500) DEFAULT NULL COMMENT 'Company mission'," +
                            "`copyright` VARCHAR(255) DEFAULT NULL COMMENT 'Copyright text'," +
                            "`sort_order` INT DEFAULT 0 COMMENT 'Sort order'," +
                            "`status` TINYINT(1) DEFAULT 1 COMMENT '1 enabled,0 disabled'," +
                            "`deleted` TINYINT(1) DEFAULT 0," +
                            "`created_at` DATETIME DEFAULT CURRENT_TIMESTAMP," +
                            "`updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (`id`)," +
                            "INDEX `idx_company_info_status` (`status`,`deleted`,`sort_order`)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Company basic info'"
            );
            ensureColumn("company_info", "company_name", "ALTER TABLE `company_info` ADD COLUMN `company_name` VARCHAR(100) DEFAULT NULL COMMENT 'Company name'");
            ensureColumn("company_info", "logo", "ALTER TABLE `company_info` ADD COLUMN `logo` VARCHAR(512) DEFAULT NULL COMMENT 'Company logo url'");
            ensureColumn("company_info", "banner", "ALTER TABLE `company_info` ADD COLUMN `banner` VARCHAR(512) DEFAULT NULL COMMENT 'Company banner url'");
            ensureColumn("company_info", "introduction", "ALTER TABLE `company_info` ADD COLUMN `introduction` VARCHAR(2000) DEFAULT NULL COMMENT 'Company introduction'");
            ensureColumn("company_info", "mission", "ALTER TABLE `company_info` ADD COLUMN `mission` VARCHAR(500) DEFAULT NULL COMMENT 'Company mission'");
            ensureColumn("company_info", "copyright", "ALTER TABLE `company_info` ADD COLUMN `copyright` VARCHAR(255) DEFAULT NULL COMMENT 'Copyright text'");
            ensureColumn("company_info", "sort_order", "ALTER TABLE `company_info` ADD COLUMN `sort_order` INT DEFAULT 0 COMMENT 'Sort order'");
            ensureColumn("company_info", "status", "ALTER TABLE `company_info` ADD COLUMN `status` TINYINT(1) DEFAULT 1 COMMENT '1 enabled,0 disabled'");
            ensureColumn("company_info", "deleted", "ALTER TABLE `company_info` ADD COLUMN `deleted` TINYINT(1) DEFAULT 0");
            ensureColumn("company_info", "created_at", "ALTER TABLE `company_info` ADD COLUMN `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP");
            ensureColumn("company_info", "updated_at", "ALTER TABLE `company_info` ADD COLUMN `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
            ensureIndex("company_info", "idx_company_info_status", "ALTER TABLE `company_info` ADD INDEX `idx_company_info_status` (`status`,`deleted`,`sort_order`)");
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS `company_product` (" +
                            "`id` BIGINT NOT NULL COMMENT 'Primary key'," +
                            "`name` VARCHAR(100) DEFAULT NULL COMMENT 'Product name'," +
                            "`description` VARCHAR(500) DEFAULT NULL COMMENT 'Product description'," +
                            "`image` VARCHAR(512) DEFAULT NULL COMMENT 'Product image url'," +
                            "`sort_order` INT DEFAULT 0 COMMENT 'Sort order'," +
                            "`status` TINYINT(1) DEFAULT 1 COMMENT '1 enabled,0 disabled'," +
                            "`deleted` TINYINT(1) DEFAULT 0," +
                            "`created_at` DATETIME DEFAULT CURRENT_TIMESTAMP," +
                            "`updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (`id`)," +
                            "INDEX `idx_company_product_status` (`status`,`deleted`,`sort_order`)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Company core products'"
            );
            ensureColumn("company_product", "name", "ALTER TABLE `company_product` ADD COLUMN `name` VARCHAR(100) DEFAULT NULL COMMENT 'Product name'");
            ensureColumn("company_product", "description", "ALTER TABLE `company_product` ADD COLUMN `description` VARCHAR(500) DEFAULT NULL COMMENT 'Product description'");
            ensureColumn("company_product", "image", "ALTER TABLE `company_product` ADD COLUMN `image` VARCHAR(512) DEFAULT NULL COMMENT 'Product image url'");
            ensureColumn("company_product", "sort_order", "ALTER TABLE `company_product` ADD COLUMN `sort_order` INT DEFAULT 0 COMMENT 'Sort order'");
            ensureColumn("company_product", "status", "ALTER TABLE `company_product` ADD COLUMN `status` TINYINT(1) DEFAULT 1 COMMENT '1 enabled,0 disabled'");
            ensureColumn("company_product", "deleted", "ALTER TABLE `company_product` ADD COLUMN `deleted` TINYINT(1) DEFAULT 0");
            ensureColumn("company_product", "created_at", "ALTER TABLE `company_product` ADD COLUMN `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP");
            ensureColumn("company_product", "updated_at", "ALTER TABLE `company_product` ADD COLUMN `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
            ensureIndex("company_product", "idx_company_product_status", "ALTER TABLE `company_product` ADD INDEX `idx_company_product_status` (`status`,`deleted`,`sort_order`)");
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS `company_honor` (" +
                            "`id` BIGINT NOT NULL COMMENT 'Primary key'," +
                            "`name` VARCHAR(100) DEFAULT NULL COMMENT 'Honor name'," +
                            "`image` VARCHAR(512) DEFAULT NULL COMMENT 'Honor image url'," +
                            "`sort_order` INT DEFAULT 0 COMMENT 'Sort order'," +
                            "`status` TINYINT(1) DEFAULT 1 COMMENT '1 enabled,0 disabled'," +
                            "`deleted` TINYINT(1) DEFAULT 0," +
                            "`created_at` DATETIME DEFAULT CURRENT_TIMESTAMP," +
                            "`updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (`id`)," +
                            "INDEX `idx_company_honor_status` (`status`,`deleted`,`sort_order`)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Company honors'"
            );
            ensureColumn("company_honor", "name", "ALTER TABLE `company_honor` ADD COLUMN `name` VARCHAR(100) DEFAULT NULL COMMENT 'Honor name'");
            ensureColumn("company_honor", "image", "ALTER TABLE `company_honor` ADD COLUMN `image` VARCHAR(512) DEFAULT NULL COMMENT 'Honor image url'");
            ensureColumn("company_honor", "sort_order", "ALTER TABLE `company_honor` ADD COLUMN `sort_order` INT DEFAULT 0 COMMENT 'Sort order'");
            ensureColumn("company_honor", "status", "ALTER TABLE `company_honor` ADD COLUMN `status` TINYINT(1) DEFAULT 1 COMMENT '1 enabled,0 disabled'");
            ensureColumn("company_honor", "deleted", "ALTER TABLE `company_honor` ADD COLUMN `deleted` TINYINT(1) DEFAULT 0");
            ensureColumn("company_honor", "created_at", "ALTER TABLE `company_honor` ADD COLUMN `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP");
            ensureColumn("company_honor", "updated_at", "ALTER TABLE `company_honor` ADD COLUMN `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
            ensureIndex("company_honor", "idx_company_honor_status", "ALTER TABLE `company_honor` ADD INDEX `idx_company_honor_status` (`status`,`deleted`,`sort_order`)");
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS `company_contact` (" +
                            "`id` BIGINT NOT NULL COMMENT 'Primary key'," +
                            "`contact_type` VARCHAR(32) DEFAULT NULL COMMENT 'address/phone/email/website'," +
                            "`contact_label` VARCHAR(50) DEFAULT NULL COMMENT 'Label text'," +
                            "`contact_value` VARCHAR(255) DEFAULT NULL COMMENT 'Contact value'," +
                            "`sort_order` INT DEFAULT 0 COMMENT 'Sort order'," +
                            "`status` TINYINT(1) DEFAULT 1 COMMENT '1 enabled,0 disabled'," +
                            "`deleted` TINYINT(1) DEFAULT 0," +
                            "`created_at` DATETIME DEFAULT CURRENT_TIMESTAMP," +
                            "`updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (`id`)," +
                            "INDEX `idx_company_contact_status` (`status`,`deleted`,`sort_order`)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Company contacts'"
            );
            ensureColumn("company_contact", "contact_type", "ALTER TABLE `company_contact` ADD COLUMN `contact_type` VARCHAR(32) DEFAULT NULL COMMENT 'address/phone/email/website'");
            ensureColumn("company_contact", "contact_label", "ALTER TABLE `company_contact` ADD COLUMN `contact_label` VARCHAR(50) DEFAULT NULL COMMENT 'Label text'");
            ensureColumn("company_contact", "contact_value", "ALTER TABLE `company_contact` ADD COLUMN `contact_value` VARCHAR(255) DEFAULT NULL COMMENT 'Contact value'");
            ensureColumn("company_contact", "sort_order", "ALTER TABLE `company_contact` ADD COLUMN `sort_order` INT DEFAULT 0 COMMENT 'Sort order'");
            ensureColumn("company_contact", "status", "ALTER TABLE `company_contact` ADD COLUMN `status` TINYINT(1) DEFAULT 1 COMMENT '1 enabled,0 disabled'");
            ensureColumn("company_contact", "deleted", "ALTER TABLE `company_contact` ADD COLUMN `deleted` TINYINT(1) DEFAULT 0");
            ensureColumn("company_contact", "created_at", "ALTER TABLE `company_contact` ADD COLUMN `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP");
            ensureColumn("company_contact", "updated_at", "ALTER TABLE `company_contact` ADD COLUMN `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
            ensureIndex("company_contact", "idx_company_contact_status", "ALTER TABLE `company_contact` ADD INDEX `idx_company_contact_status` (`status`,`deleted`,`sort_order`)");
        } catch (Exception e) {
            log.warn("Company schema bootstrap skipped: {}", safeMessage(e));
        }
    }

    /**
     * 操作日志表自举（含撤销链路字段）。
     */
    private void bootstrapOperationLogModule() {
        try {
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS `operation_log` (" +
                            "`id` BIGINT NOT NULL COMMENT 'Primary key'," +
                            "`user_id` BIGINT DEFAULT NULL COMMENT 'Operator user id'," +
                            "`user_type` VARCHAR(16) DEFAULT NULL COMMENT 'miniapp/admin'," +
                            "`role_code` VARCHAR(32) DEFAULT NULL COMMENT 'Role code'," +
                            "`operator_name` VARCHAR(64) DEFAULT NULL COMMENT 'Operator display name'," +
                            "`operation_type` VARCHAR(32) NOT NULL COMMENT 'create/update/delete/review/restore...'," +
                            "`target_module` VARCHAR(32) DEFAULT NULL COMMENT 'Target module'," +
                            "`target_id` BIGINT DEFAULT NULL COMMENT 'Target entity id'," +
                            "`http_method` VARCHAR(16) DEFAULT NULL COMMENT 'HTTP method'," +
                            "`api_path` VARCHAR(255) DEFAULT NULL COMMENT 'API path'," +
                            "`query_string` VARCHAR(500) DEFAULT NULL COMMENT 'Request query string'," +
                            "`result_code` INT DEFAULT NULL COMMENT 'Result code'," +
                            "`result_message` VARCHAR(500) DEFAULT NULL COMMENT 'Result message'," +
                            "`success_flag` TINYINT(1) DEFAULT '1' COMMENT '1 success,0 failed'," +
                            "`cost_ms` INT DEFAULT NULL COMMENT 'Cost milliseconds'," +
                            "`client_ip` VARCHAR(64) DEFAULT NULL COMMENT 'Client IP'," +
                            "`undo_type` VARCHAR(32) DEFAULT NULL COMMENT 'Undo action type'," +
                            "`undo_payload_json` LONGTEXT DEFAULT NULL COMMENT 'Undo payload json'," +
                            "`undo_status` VARCHAR(16) DEFAULT NULL COMMENT 'pending/applied/failed'," +
                            "`undo_fail_reason` VARCHAR(500) DEFAULT NULL COMMENT 'Undo failed reason'," +
                            "`undo_applied_at` DATETIME DEFAULT NULL COMMENT 'Undo applied time'," +
                            "`undo_applied_by_user_id` BIGINT DEFAULT NULL COMMENT 'Undo operator user id'," +
                            "`created_at` DATETIME DEFAULT CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (`id`)," +
                            "INDEX `idx_op_log_created` (`created_at`)," +
                            "INDEX `idx_op_log_type` (`operation_type`)," +
                            "INDEX `idx_op_log_user` (`user_id`)," +
                            "INDEX `idx_op_log_success` (`success_flag`)," +
                            "INDEX `idx_op_log_target` (`target_module`,`target_id`)," +
                            "INDEX `idx_op_log_undo` (`undo_status`)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Operation logs'"
            );
            ensureColumn("operation_log", "target_module", "ALTER TABLE `operation_log` ADD COLUMN `target_module` VARCHAR(32) DEFAULT NULL COMMENT 'Target module'");
            ensureColumn("operation_log", "target_id", "ALTER TABLE `operation_log` ADD COLUMN `target_id` BIGINT DEFAULT NULL COMMENT 'Target entity id'");
            ensureColumn("operation_log", "undo_type", "ALTER TABLE `operation_log` ADD COLUMN `undo_type` VARCHAR(32) DEFAULT NULL COMMENT 'Undo action type'");
            ensureColumn("operation_log", "undo_payload_json", "ALTER TABLE `operation_log` ADD COLUMN `undo_payload_json` LONGTEXT DEFAULT NULL COMMENT 'Undo payload json'");
            ensureColumn("operation_log", "undo_status", "ALTER TABLE `operation_log` ADD COLUMN `undo_status` VARCHAR(16) DEFAULT NULL COMMENT 'pending/applied/failed'");
            ensureColumn("operation_log", "undo_fail_reason", "ALTER TABLE `operation_log` ADD COLUMN `undo_fail_reason` VARCHAR(500) DEFAULT NULL COMMENT 'Undo failed reason'");
            ensureColumn("operation_log", "undo_applied_at", "ALTER TABLE `operation_log` ADD COLUMN `undo_applied_at` DATETIME DEFAULT NULL COMMENT 'Undo applied time'");
            ensureColumn("operation_log", "undo_applied_by_user_id", "ALTER TABLE `operation_log` ADD COLUMN `undo_applied_by_user_id` BIGINT DEFAULT NULL COMMENT 'Undo operator user id'");
            ensureIndex("operation_log", "idx_op_log_target", "ALTER TABLE `operation_log` ADD INDEX `idx_op_log_target` (`target_module`,`target_id`)");
            ensureIndex("operation_log", "idx_op_log_undo", "ALTER TABLE `operation_log` ADD INDEX `idx_op_log_undo` (`undo_status`)");
        } catch (Exception e) {
            log.warn("Operation log schema bootstrap skipped: {}", safeMessage(e));
        }
    }

    /**
     * 插入导出字段字典记录。
     */
    private void insertExportDict(Long id, String moduleKey, String fieldCode, String fieldName, String dataType, String exampleValue) {
    }

    /**
     * 若字段不存在则执行新增 DDL。
     */
    private void ensureColumn(String tableName, String columnName, String ddl) {
        if (columnExists(tableName, columnName)) {
            return;
        }
        jdbcTemplate.execute(ddl);
        log.info("Schema bootstrap add column: {}.{}", tableName, columnName);
    }

    /**
     * 若索引不存在则执行新增 DDL。
     */
    private void ensureIndex(String tableName, String indexName, String ddl) {
        if (indexExists(tableName, indexName)) {
            return;
        }
        jdbcTemplate.execute(ddl);
        log.info("Schema bootstrap add index: {}.{}", tableName, indexName);
    }

    private void dropIndexIfExists(String tableName, String indexName) {
        if (!indexExists(tableName, indexName)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE `" + tableName + "` DROP INDEX `" + indexName + "`");
        log.info("Schema bootstrap drop index: {}.{}", tableName, indexName);
    }

    private void ensureIndexSafely(String tableName, String indexName, String ddl) {
        if (indexExists(tableName, indexName)) {
            return;
        }
        try {
            jdbcTemplate.execute(ddl);
            log.info("Schema bootstrap add index: {}.{}", tableName, indexName);
        } catch (Exception e) {
            log.warn("Schema bootstrap add index skipped: {}.{}, err={}", tableName, indexName, safeMessage(e));
        }
    }

    /**
     * 若字段存在则删除（用于历史字段清理）。
     */
    private void dropColumnIfExists(String tableName, String columnName) {
        if (!columnExists(tableName, columnName)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE `" + tableName + "` DROP COLUMN `" + columnName + "`");
        log.info("Schema bootstrap drop column: {}.{}", tableName, columnName);
    }

    /**
     * 判断表是否存在。
     */
    private boolean tableExists(String tableName) {
        Long c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Long.class,
                tableName
        );
        return c != null && c > 0;
    }

    /**
     * 判断字段是否存在。
     */
    private boolean columnExists(String tableName, String columnName) {
        Long c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Long.class,
                tableName,
                columnName
        );
        return c != null && c > 0;
    }

    /**
     * 判断索引是否存在。
     */
    private boolean indexExists(String tableName, String indexName) {
        Long c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                Long.class,
                tableName,
                indexName
        );
        return c != null && c > 0;
    }

    /**
     * 获取表记录总数；表不存在时返回 0。
     */
    private long tableCount(String tableName) {
        if (!tableExists(tableName)) {
            return 0L;
        }
        Long c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `" + tableName + "`", Long.class);
        return c == null ? 0L : c;
    }

    /**
     * 安全转换整数，转换失败返回 null。
     */
    private Integer toNullableInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            String text = String.valueOf(value).trim();
            if (!StringUtils.hasText(text)) {
                return null;
            }
            return Integer.parseInt(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 安全转换 Long，转换失败返回 null。
     */
    private Long toNullableLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            String text = String.valueOf(value).trim();
            if (!StringUtils.hasText(text)) {
                return null;
            }
            return Long.parseLong(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 归一化资源目录路径，兼容历史别名与非法格式。
     */
    private String normalizeAssetFolderPath(String raw) {
        String path = StringUtils.hasText(raw) ? raw.trim() : "/默认";
        path = path.replace('\\', '/');
        while (path.contains("//")) {
            path = path.replace("//", "/");
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (!StringUtils.hasText(path)) {
            return "/默认";
        }
        String lower = path.toLowerCase(Locale.ROOT);
        if ("/default".equals(lower)) {
            return "/默认";
        }
        if ("/field".equals(lower) || "/田块图片".equals(path)) {
            return "/田块封面";
        }
        if ("/crop".equals(lower)) {
            return "/作物封面";
        }
        if (path.length() > 255) {
            return path.substring(0, 255);
        }
        return path;
    }

    /**
     * 异常消息安全提取（避免空指针）。
     */
    private String safeMessage(Exception e) {
        return e == null ? "" : String.valueOf(e.getMessage());
    }
}
