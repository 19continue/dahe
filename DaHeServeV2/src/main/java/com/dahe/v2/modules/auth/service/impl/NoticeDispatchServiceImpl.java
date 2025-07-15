package com.dahe.v2.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.dahe.v2.modules.auth.domain.AuthDomainConstants;
import com.dahe.v2.modules.auth.role.service.AdminRoleService;
import com.dahe.v2.modules.auth.service.NoticeDispatchService;
import com.dahe.v2.modules.user.model.AppUser;
import com.dahe.v2.modules.user.service.AppUserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 通知异步派发实现。
 *
 * <p>职责仅限“按任务解析收件人并写入收件箱”，不负责管理端请求编排。这样可以把消息投递
 * 从主业务请求链路中剥离出去，避免审核、申请、状态变更等接口被同步写通知拖慢。</p>
 */
@Service
public class NoticeDispatchServiceImpl implements NoticeDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NoticeDispatchServiceImpl.class);

    private final JdbcTemplate jdbcTemplate;
    private final AppUserService appUserService;
    private final AdminRoleService adminRoleService;
    private final ObjectMapper objectMapper;

    public NoticeDispatchServiceImpl(
            JdbcTemplate jdbcTemplate,
            AppUserService appUserService,
            AdminRoleService adminRoleService,
            ObjectMapper objectMapper
    ) {
        // 读写 notice_task / user_notice 使用的数据库访问对象。
        this.jdbcTemplate = jdbcTemplate;
        // 用来查收件用户。
        this.appUserService = appUserService;
        // 用来解析管理员角色权限。
        this.adminRoleService = adminRoleService;
        // 用来解析 target_config_json。
        this.objectMapper = objectMapper;
    }

    @Override
    @Async("authNoticeExecutor")
    public void dispatchTaskAsync(Long taskId) {
        // 没有任务 id 时，不做任何处理。
        if (taskId == null) {
            return;
        }
        try {
            // 先把任务状态抢成 sending，避免同一任务被多个线程重复派发。
            int locked = jdbcTemplate.update(
                    "UPDATE `notice_task` SET `dispatch_status`='sending', `result_message`=NULL " +
                            "WHERE `id`=? AND `deleted`=0 AND `dispatch_status` IN ('pending','failed')",
                    taskId
            );
            // 没抢到说明任务已经被处理或状态不允许派发。
            if (locked <= 0) {
                return;
            }
            // 读取任务主体信息。
            Map<String, Object> task = queryTask(taskId);
            // 空任务、已删除任务都不再继续。
            if (task == null || task.isEmpty() || toInt(task.get("deleted")) == 1) {
                return;
            }
            // 根据任务里的目标类型动态解析收件人。
            List<Long> recipientIds = resolveRecipients(task);
            if (recipientIds.isEmpty()) {
                // 没命中收件人时，也把任务置为 sent，只是投递数为 0。
                jdbcTemplate.update(
                        "UPDATE `notice_task` SET `target_count`=0, `success_count`=0, `failed_count`=0, " +
                                "`dispatch_status`='sent', `result_message`='没有命中可接收消息的用户' WHERE `id`=?",
                        taskId
                );
                return;
            }

            String noticeType = normalizeNoticeType(task.get("notice_type"));
            String title = trimToEmpty(task.get("title"));
            String content = trimToNull(task.get("content"));
            // routeCode 通常用于通知点击后的页面跳转。
            String routeCode = trimToNull(task.get("route_code"));
            String sourceKind = normalizeSourceKind(task.get("source_kind"));
            Long senderUserId = toLong(task.get("created_by_user_id"));
            String senderName = trimToNull(task.get("created_by_name"));
            // 给每条通知附带来源任务 id，方便后续追踪。
            String extraJson = buildExtraJson(taskId, task);

            List<Object[]> args = new ArrayList<Object[]>();
            for (Long userId : recipientIds) {
                // 每个收件人都生成一条 user_notice 记录。
                args.add(new Object[]{
                        IdWorker.getId(),
                        taskId,
                        userId,
                        noticeType,
                        title,
                        content,
                        routeCode,
                        sourceKind,
                        senderUserId,
                        senderName,
                        extraJson
                });
            }
            // 批量插入通知，减少数据库往返次数。
            jdbcTemplate.batchUpdate(
                    "INSERT INTO `user_notice` " +
                            "(`id`,`task_id`,`user_id`,`notice_type`,`title`,`content`,`route_code`,`source_kind`,`sender_user_id`,`sender_name`,`is_read`,`extra_json`,`deleted`) " +
                            "VALUES (?,?,?,?,?,?,?,?,?,?,0,?,0)",
                    args
            );
            // 批量插入成功后，回写任务统计。
            jdbcTemplate.update(
                    "UPDATE `notice_task` SET `target_count`=?, `success_count`=?, `failed_count`=0, " +
                            "`dispatch_status`='sent', `result_message`='投递完成' WHERE `id`=?",
                    recipientIds.size(),
                    recipientIds.size(),
                    taskId
            );
        } catch (Exception e) {
            // 异步派发失败时，把任务标记成 failed，便于后台重试或排查。
            jdbcTemplate.update(
                    "UPDATE `notice_task` SET `dispatch_status`='failed', `result_message`=? WHERE `id`=?",
                    shortenError(e),
                    taskId
            );
            log.warn("Dispatch notice task failed, taskId={}, err={}", taskId, shortenError(e));
        }
    }

    private Map<String, Object> queryTask(Long taskId) {
        // 这里只查派发真正需要的最小字段集合。
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT `id`,`notice_type`,`source_kind`,`title`,`content`,`route_code`,`target_type`,`target_config_json`," +
                        "`created_by_user_id`,`created_by_name`,`deleted`,`dispatch_status` " +
                        "FROM `notice_task` WHERE `id`=? LIMIT 1",
                taskId
        );
        return rows == null || rows.isEmpty() ? null : rows.get(0);
    }

    private List<Long> resolveRecipients(Map<String, Object> task) {
        // 先把 target_type 归一化。
        String targetType = normalizeTargetType(task.get("target_type"));
        if (!StringUtils.hasText(targetType)) {
            return Collections.emptyList();
        }
        if (AuthDomainConstants.NOTICE_TARGET_ADMIN_ALL.equals(targetType)) {
            // 全部管理员。
            return listActiveUserIds(AuthDomainConstants.USER_TYPE_ADMIN, null, false);
        }
        if (AuthDomainConstants.NOTICE_TARGET_ADMIN_ROUTE.equals(targetType)) {
            // 某个路由对应的管理员。
            String routeCode = trimToNull(task.get("route_code"));
            if (!StringUtils.hasText(routeCode)) {
                return Collections.emptyList();
            }
            return listAdminIdsByRoute(routeCode);
        }
        if (AuthDomainConstants.NOTICE_TARGET_ADMIN_ROLE.equals(targetType)) {
            // 某些角色对应的管理员。
            return listAdminIdsByRoleConfig(task.get("target_config_json"));
        }
        if (AuthDomainConstants.NOTICE_TARGET_MINIAPP_APPROVED.equals(targetType)) {
            // 所有审核通过的小程序用户。
            return listActiveUserIds(AuthDomainConstants.USER_TYPE_MINIAPP, AuthDomainConstants.REVIEW_STATUS_APPROVED, false);
        }
        if (AuthDomainConstants.NOTICE_TARGET_MINIAPP_CONSOLE.equals(targetType)) {
            // 审核通过且有控制台能力的小程序用户。
            return listActiveUserIds(AuthDomainConstants.USER_TYPE_MINIAPP, AuthDomainConstants.REVIEW_STATUS_APPROVED, true);
        }
        if (AuthDomainConstants.NOTICE_TARGET_EXPLICIT_USERS.equals(targetType)) {
            // 显式指定用户列表。
            return listExplicitUserIds(task.get("target_config_json"));
        }
        return Collections.emptyList();
    }

    private List<Long> listActiveUserIds(String userType, String status, boolean canConsoleOnly) {
        // deleted=0、recycle_flag=0、enabled=1 共同表示“当前仍可接收通知”。
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT `id` FROM `user` WHERE `deleted`=0 AND IFNULL(`recycle_flag`,0)=0 AND `enabled`=1 " +
                        "AND LOWER(COALESCE(`user_type`,''))=? " +
                        (StringUtils.hasText(status) ? "AND LOWER(COALESCE(`status`,''))=? " : "") +
                        (canConsoleOnly ? "AND IFNULL(`can_console`,0)=1 " : "") +
                        "ORDER BY `id` ASC",
                buildQueryArgs(userType, status)
        );
        return collectIdRows(rows);
    }

    private Object[] buildQueryArgs(String userType, String status) {
        if (StringUtils.hasText(status)) {
            return new Object[]{normalizeLower(userType), normalizeLower(status)};
        }
        return new Object[]{normalizeLower(userType)};
    }

    private List<Long> listAdminIdsByRoute(String routeCode) {
        // 先把所有有效管理员查出来，再按路由权限做二次过滤。
        List<AppUser> rows = appUserService.lambdaQuery()
                .eq(AppUser::getDeleted, 0)
                .eq(AppUser::getRecycleFlag, 0)
                .eq(AppUser::getEnabled, 1)
                .eq(AppUser::getUserType, AuthDomainConstants.USER_TYPE_ADMIN)
                .orderByAsc(AppUser::getId)
                .list();
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> out = new ArrayList<Long>();
        for (AppUser row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            // 超级管理员天然拥有全部菜单权限。
            if (row.getIsSuperAdmin() != null && row.getIsSuperAdmin() == 1) {
                out.add(row.getId());
                continue;
            }
            // 普通管理员则按 roleCode 展开菜单权限来判断。
            List<String> permissions = adminRoleService.resolveMenuPermissions(row.getRoleCode());
            if (hasRoutePermission(permissions, routeCode)) {
                out.add(row.getId());
            }
        }
        return out;
    }

    private boolean hasRoutePermission(List<String> permissions, String routeCode) {
        String target = trimToNull(routeCode);
        if (!StringUtils.hasText(target)) {
            return false;
        }
        List<String> rows = permissions == null ? Collections.<String>emptyList() : permissions;
        for (String permission : rows) {
            String code = trimToNull(permission);
            if (!StringUtils.hasText(code)) {
                continue;
            }
            // * 表示全量权限；前缀命中表示子路由也算命中。
            if ("*".equals(code) || target.equals(code) || target.startsWith(code + "/")) {
                return true;
            }
        }
        return false;
    }

    private List<Long> listExplicitUserIds(Object rawConfig) {
        String text = trimToNull(rawConfig);
        if (!StringUtils.hasText(text)) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> config = objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {
            });
            Object rawIds = config == null ? null : config.get("userIds");
            if (!(rawIds instanceof List)) {
                return Collections.emptyList();
            }
            Set<Long> ids = new LinkedHashSet<Long>();
            for (Object rawId : (List<?>) rawIds) {
                Long id = toLong(rawId);
                if (id != null && id > 0) {
                    ids.add(id);
                }
            }
            if (ids.isEmpty()) {
                return Collections.emptyList();
            }
            StringBuilder sql = new StringBuilder(
                    "SELECT `id` FROM `user` WHERE `deleted`=0 AND IFNULL(`recycle_flag`,0)=0 AND `enabled`=1 AND `id` IN ("
            );
            List<Object> args = new ArrayList<Object>();
            int index = 0;
            for (Long id : ids) {
                if (index > 0) {
                    sql.append(',');
                }
                sql.append('?');
                args.add(id);
                index += 1;
            }
            sql.append(") ORDER BY `id` ASC");
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
            return collectIdRows(rows);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<Long> listAdminIdsByRoleConfig(Object rawConfig) {
        String text = trimToNull(rawConfig);
        if (!StringUtils.hasText(text)) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> config = objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {
            });
            Object rawRoleCodes = config == null ? null : config.get("roleCodes");
            if (!(rawRoleCodes instanceof List)) {
                return Collections.emptyList();
            }
            Set<String> roleCodes = new LinkedHashSet<String>();
            for (Object rawRoleCode : (List<?>) rawRoleCodes) {
                String roleCode = adminRoleService.normalizeRoleCode(rawRoleCode == null ? null : String.valueOf(rawRoleCode));
                if (StringUtils.hasText(roleCode)) {
                    roleCodes.add(roleCode);
                }
            }
            if (roleCodes.isEmpty()) {
                return Collections.emptyList();
            }
            StringBuilder sql = new StringBuilder(
                    "SELECT `id` FROM `user` WHERE `deleted`=0 AND IFNULL(`recycle_flag`,0)=0 AND `enabled`=1 " +
                            "AND LOWER(COALESCE(`user_type`,''))=? AND `role_code` IS NOT NULL AND LOWER(`role_code`) IN ("
            );
            List<Object> args = new ArrayList<Object>();
            args.add(AuthDomainConstants.USER_TYPE_ADMIN);
            int index = 0;
            for (String roleCode : roleCodes) {
                if (index > 0) {
                    sql.append(',');
                }
                sql.append('?');
                args.add(roleCode);
                index += 1;
            }
            sql.append(") ORDER BY `id` ASC");
            return collectIdRows(jdbcTemplate.queryForList(sql.toString(), args.toArray()));
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<Long> collectIdRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> out = new ArrayList<Long>();
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            Long id = toLong(row.get("id"));
            if (id != null && id > 0) {
                out.add(id);
            }
        }
        return out;
    }

    private String buildExtraJson(Long taskId, Map<String, Object> task) {
        try {
            return objectMapper.writeValueAsString(Collections.singletonMap("taskId", taskId));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeNoticeType(Object value) {
        String text = normalizeLower(value);
        return StringUtils.hasText(text) ? text : AuthDomainConstants.NOTICE_TYPE_SYSTEM;
    }

    private String normalizeSourceKind(Object value) {
        String text = normalizeLower(value);
        if (AuthDomainConstants.NOTICE_SOURCE_MANUAL.equals(text)) {
            return text;
        }
        return AuthDomainConstants.NOTICE_SOURCE_SYSTEM;
    }

    private String normalizeTargetType(Object value) {
        String text = normalizeLower(value);
        if (AuthDomainConstants.NOTICE_TARGET_ADMIN_ALL.equals(text)
                || AuthDomainConstants.NOTICE_TARGET_ADMIN_ROUTE.equals(text)
                || AuthDomainConstants.NOTICE_TARGET_ADMIN_ROLE.equals(text)
                || AuthDomainConstants.NOTICE_TARGET_MINIAPP_APPROVED.equals(text)
                || AuthDomainConstants.NOTICE_TARGET_MINIAPP_CONSOLE.equals(text)
                || AuthDomainConstants.NOTICE_TARGET_EXPLICIT_USERS.equals(text)) {
            return text;
        }
        return null;
    }

    private String normalizeLower(Object value) {
        return String.valueOf(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(Object value) {
        String text = String.valueOf(value == null ? "" : value).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private String trimToEmpty(Object value) {
        return String.valueOf(value == null ? "" : value).trim();
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            String text = String.valueOf(value).trim();
            return StringUtils.hasText(text) ? Long.parseLong(text) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            String text = String.valueOf(value).trim();
            return StringUtils.hasText(text) ? Integer.parseInt(text) : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String shortenError(Exception e) {
        String message = e == null ? null : e.getMessage();
        if (!StringUtils.hasText(message)) {
            return "消息投递失败";
        }
        message = message.trim();
        return message.length() > 180 ? message.substring(0, 180) : message;
    }
}
