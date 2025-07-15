package com.dahe.v2.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.auth.domain.AuthDomainConstants;
import com.dahe.v2.modules.auth.domain.AuthMessageCatalog;
import com.dahe.v2.modules.auth.dto.AdminNoticeManageDTO;
import com.dahe.v2.modules.auth.role.service.AdminRoleService;
import com.dahe.v2.modules.auth.service.NoticeDispatchService;
import com.dahe.v2.modules.auth.service.UserNoticeService;
import com.dahe.v2.modules.user.model.AppUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 用户通知应用服务实现。
 *
 * <p>职责拆分如下：</p>
 * <p>1. 面向业务模块暴露“发通知”入口，统一落为 notice_task，再异步派发；</p>
 * <p>2. 提供当前用户收件箱查询、已读治理；</p>
 * <p>3. 提供后台消息任务管理能力，供“消息通知”独立页使用。</p>
 */
@Service
public class UserNoticeServiceImpl implements UserNoticeService {

    private static final Logger log = LoggerFactory.getLogger(UserNoticeServiceImpl.class);

    private final JdbcTemplate jdbcTemplate;
    private final NoticeDispatchService noticeDispatchService;
    private final AdminRoleService adminRoleService;
    private final ObjectMapper objectMapper;

    public UserNoticeServiceImpl(
            JdbcTemplate jdbcTemplate,
            NoticeDispatchService noticeDispatchService,
            AdminRoleService adminRoleService,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.noticeDispatchService = noticeDispatchService;
        this.adminRoleService = adminRoleService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void pushNoticeSafe(Long userId, String title, String content, String noticeType) {
        if (userId == null || !StringUtils.hasText(title)) {
            return;
        }
        try {
            Long taskId = createTask(
                    normalizeNoticeType(noticeType, true),
                    AuthDomainConstants.NOTICE_SOURCE_SYSTEM,
                    title.trim(),
                    trimToNull(content),
                    null,
                    AuthDomainConstants.NOTICE_TARGET_EXPLICIT_USERS,
                    buildExplicitUsersConfig(Collections.singletonList(userId)),
                    null,
                    null
            );
            if (taskId != null) {
                noticeDispatchService.dispatchTaskAsync(taskId);
            }
        } catch (Exception e) {
            log.warn("Push notice skipped, userId={}, title={}, err={}", userId, title, shortenError(e));
        }
    }

    @Override
    public void pushReviewApplyNoticeToAdmins(AppUser applicant, boolean reApply) {
        if (applicant == null || applicant.getId() == null) {
            return;
        }
        try {
            String applicantName = StringUtils.hasText(applicant.getRealName())
                    ? applicant.getRealName().trim()
                    : StringUtils.hasText(applicant.getNickName()) ? applicant.getNickName().trim() : "未命名用户";
            String phone = StringUtils.hasText(applicant.getPhone()) ? applicant.getPhone().trim() : "-";
            String applyReason = StringUtils.hasText(applicant.getApplyReason()) ? applicant.getApplyReason().trim() : "-";
            String title = reApply ? "用户重新提交审核申请" : "新的用户审核申请";
            String content = "申请人：" + applicantName + "，手机号：" + phone + "，申请说明：" + applyReason;
            Set<Long> recipients = new LinkedHashSet<Long>();
            recipients.addAll(listAdminIdsByRoute("/users"));
            recipients.addAll(listMiniappConsoleUserIds());
            if (recipients.isEmpty()) {
                return;
            }
            Long taskId = createTask(
                    AuthDomainConstants.NOTICE_TYPE_REVIEW_APPLY,
                    AuthDomainConstants.NOTICE_SOURCE_SYSTEM,
                    title,
                    content,
                    "/users",
                    AuthDomainConstants.NOTICE_TARGET_EXPLICIT_USERS,
                    buildExplicitUsersConfig(new ArrayList<Long>(recipients)),
                    null,
                    null
            );
            if (taskId != null) {
                noticeDispatchService.dispatchTaskAsync(taskId);
            }
        } catch (Exception e) {
            log.warn("Push review apply notice skipped, applicantId={}, err={}", applicant.getId(), shortenError(e));
        }
    }

    @Override
    public void pushAdminRouteNoticeSafe(String routeCode, String title, String content, String noticeType) {
        String targetRoute = trimToNull(routeCode);
        if (!StringUtils.hasText(targetRoute) || !StringUtils.hasText(title)) {
            return;
        }
        try {
            Long taskId = createTask(
                    normalizeNoticeType(noticeType, true),
                    AuthDomainConstants.NOTICE_SOURCE_SYSTEM,
                    title.trim(),
                    trimToNull(content),
                    targetRoute,
                    AuthDomainConstants.NOTICE_TARGET_ADMIN_ROUTE,
                    null,
                    null,
                    null
            );
            if (taskId != null) {
                noticeDispatchService.dispatchTaskAsync(taskId);
            }
        } catch (Exception e) {
            log.warn("Push admin route notice skipped, routeCode={}, title={}, err={}", targetRoute, title, shortenError(e));
        }
    }

    @Override
    public Map<String, Object> queryUserNotices(Long userId, String noticeType, Boolean unreadOnly, long page, long pageSize) {
        long safePage = Math.max(1L, page);
        long safePageSize = Math.max(1L, pageSize);
        String noticeTypeFilter = normalizeNoticeType(noticeType, false);
        boolean onlyUnread = Boolean.TRUE.equals(unreadOnly);

        String baseWhere = " FROM `user_notice` WHERE `deleted`=0 AND `user_id`=?";
        List<Object> args = new ArrayList<Object>();
        args.add(userId);
        if (StringUtils.hasText(noticeTypeFilter)) {
            baseWhere += " AND `notice_type`=?";
            args.add(noticeTypeFilter);
        }
        if (onlyUnread) {
            baseWhere += " AND `is_read`=0";
        }

        long total = queryLong("SELECT COUNT(*)" + baseWhere, args.toArray());
        long unreadCount = queryLong(
                "SELECT COUNT(*) FROM `user_notice` WHERE `deleted`=0 AND `user_id`=? AND `is_read`=0",
                userId
        );
        long offset = (safePage - 1) * safePageSize;
        List<Object> queryArgs = new ArrayList<Object>(args);
        queryArgs.add(safePageSize);
        queryArgs.add(offset);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT `id`,`task_id`,`notice_type`,`title`,`content`,`route_code`,`source_kind`,`sender_name`,`is_read`,`read_at`,`created_at`"
                        + baseWhere
                        + " ORDER BY `created_at` DESC, `id` DESC LIMIT ? OFFSET ?",
                queryArgs.toArray()
        );
        normalizeNoticeRows(rows);
        Map<String, Object> out = new HashMap<String, Object>();
        out.put("records", rows);
        out.put("total", total);
        out.put("page", safePage);
        out.put("pageSize", safePageSize);
        out.put("unreadCount", unreadCount);
        return out;
    }

    @Override
    public boolean markRead(Long userId, Long noticeId) {
        if (userId == null || noticeId == null) {
            return false;
        }
        return jdbcTemplate.update(
                "UPDATE `user_notice` SET `is_read`=1, `read_at`=NOW() WHERE `id`=? AND `user_id`=? AND `deleted`=0",
                noticeId,
                userId
        ) > 0;
    }

    @Override
    public long markAllRead(Long userId) {
        if (userId == null) {
            return 0L;
        }
        return jdbcTemplate.update(
                "UPDATE `user_notice` SET `is_read`=1, `read_at`=NOW() WHERE `user_id`=? AND `deleted`=0 AND `is_read`=0",
                userId
        );
    }

    @Override
    public boolean deleteUserNotice(Long userId, Long noticeId) {
        if (userId == null || noticeId == null) {
            return false;
        }
        return jdbcTemplate.update(
                "UPDATE `user_notice` SET `deleted`=1 WHERE `id`=? AND `user_id`=? AND `deleted`=0",
                noticeId,
                userId
        ) > 0;
    }

    @Override
    public Map<String, Object> pageNoticeTasks(String keyword, String noticeType, String targetType, String dispatchStatus, long page, long pageSize) {
        long safePage = Math.max(1L, page);
        long safePageSize = Math.max(1L, pageSize);
        StringBuilder where = new StringBuilder(" FROM `notice_task` WHERE `deleted`=0");
        List<Object> args = new ArrayList<Object>();
        String normalizedKeyword = trimToNull(keyword);
        String normalizedNoticeType = normalizeNoticeType(noticeType, false);
        String normalizedTargetType = normalizeTargetType(targetType);
        String normalizedDispatchStatus = normalizeDispatchStatus(dispatchStatus);
        if (StringUtils.hasText(normalizedKeyword)) {
            where.append(" AND (`title` LIKE ? OR `content` LIKE ? OR `created_by_name` LIKE ?)");
            String likeValue = "%" + normalizedKeyword + "%";
            args.add(likeValue);
            args.add(likeValue);
            args.add(likeValue);
        }
        if (StringUtils.hasText(normalizedNoticeType)) {
            where.append(" AND `notice_type`=?");
            args.add(normalizedNoticeType);
        }
        if (StringUtils.hasText(normalizedTargetType)) {
            where.append(" AND `target_type`=?");
            args.add(normalizedTargetType);
        }
        if (StringUtils.hasText(normalizedDispatchStatus)) {
            where.append(" AND `dispatch_status`=?");
            args.add(normalizedDispatchStatus);
        }
        long total = queryLong("SELECT COUNT(*)" + where, args.toArray());
        List<Object> pageArgs = new ArrayList<Object>(args);
        pageArgs.add(safePageSize);
        pageArgs.add((safePage - 1) * safePageSize);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT `id`,`notice_type`,`title`,`content`,`route_code`,`target_type`,`dispatch_status`,`target_count`,`success_count`,`failed_count`,"
                        + "`result_message`,`created_by_name`,`created_at`,`updated_at`"
                        + where
                        + " ORDER BY `created_at` DESC, `id` DESC LIMIT ? OFFSET ?",
                pageArgs.toArray()
        );
        normalizeTaskRows(rows);
        Map<String, Object> out = new HashMap<String, Object>();
        out.put("records", rows);
        out.put("total", total);
        out.put("page", safePage);
        out.put("pageSize", safePageSize);
        return out;
    }

    @Override
    public Result<Map<String, Object>> createManualNoticeTask(Long operatorUserId, String operatorName, AdminNoticeManageDTO.CreateReq req) {
        if (req == null || !StringUtils.hasText(req.getTitle()) || !StringUtils.hasText(req.getContent())) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "消息标题和内容不能为空");
        }
        String targetType = normalizeTargetType(req.getTargetType());
        if (!StringUtils.hasText(targetType)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.MESSAGE_TARGET_INVALID);
        }
        String routeCode = trimToNull(req.getRouteCode());
        String targetConfigJson = null;
        if (AuthDomainConstants.NOTICE_TARGET_ADMIN_ROUTE.equals(targetType)) {
            if (!StringUtils.hasText(routeCode)) {
                return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.MESSAGE_ROUTE_REQUIRED);
            }
            List<String> menuCodes = adminRoleService.listMenuPermissionCodes();
            if (menuCodes == null || !menuCodes.contains(routeCode)) {
                return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "路由权限码不存在，请重新选择发送对象");
            }
        } else if (AuthDomainConstants.NOTICE_TARGET_ADMIN_ROLE.equals(targetType)) {
            List<String> roleCodes = normalizeTargetRoleCodes(req.getTargetRoleCodes());
            if (roleCodes.isEmpty()) {
                return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.MESSAGE_ROLE_REQUIRED);
            }
            targetConfigJson = buildRoleTargetConfig(roleCodes);
        } else if (AuthDomainConstants.NOTICE_TARGET_EXPLICIT_USERS.equals(targetType)) {
            List<Long> userIds = normalizeTargetUserIds(req.getTargetUserIds());
            if (userIds.isEmpty()) {
                return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "请至少选择一个接收用户");
            }
            targetConfigJson = buildExplicitUsersConfig(userIds);
        } else {
            routeCode = null;
        }
        String noticeType = normalizeNoticeType(req.getNoticeType(), true);
        Long taskId;
        try {
            taskId = createTask(
                    noticeType,
                    AuthDomainConstants.NOTICE_SOURCE_MANUAL,
                    req.getTitle().trim(),
                    trimToNull(req.getContent()),
                    routeCode,
                    targetType,
                    targetConfigJson,
                    operatorUserId,
                    trimToNull(operatorName)
            );
            if (taskId != null) {
                noticeDispatchService.dispatchTaskAsync(taskId);
            }
        } catch (Exception e) {
            log.warn("Create manual notice task failed, operatorUserId={}, err={}", operatorUserId, shortenError(e));
            return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), "消息任务创建失败");
        }
        Map<String, Object> out = new HashMap<String, Object>();
        out.put("taskId", String.valueOf(taskId));
        out.put("dispatchStatus", "pending");
        return Result.success(out);
    }

    @Override
    public Result<Void> deleteNoticeTask(Long taskId, String expectedUpdatedAt, Long operatorUserId) {
        if (taskId == null) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "消息任务编号不能为空");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT `id`,`deleted`,`dispatch_status`,`updated_at` FROM `notice_task` WHERE `id`=? LIMIT 1",
                taskId
        );
        if (rows == null || rows.isEmpty()) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        Map<String, Object> row = rows.get(0);
        if (toInt(row.get("deleted")) == 1) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        if (!sameUpdatedAt(row.get("updated_at"), expectedUpdatedAt)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "该消息已被其他人处理，请刷新后重试");
        }
        if ("sending".equals(trimToNull(row.get("dispatch_status")))) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "消息正在发送中，请稍后再删除");
        }
        jdbcTemplate.update(
                "UPDATE `notice_task` SET `deleted`=1, `deleted_at`=NOW(), `deleted_by_user_id`=? WHERE `id`=? AND `deleted`=0",
                operatorUserId,
                taskId
        );
        jdbcTemplate.update(
                "UPDATE `user_notice` SET `deleted`=1 WHERE `task_id`=? AND `deleted`=0",
                taskId
        );
        return Result.success(null);
    }

    @Override
    public Map<String, Object> getNoticeDispatchConfig() {
        ensureNoticeDispatchConfigRow();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT `id`,`auto_purge_enabled`,`retain_days`,`updated_by_name`,`updated_at` " +
                        "FROM `notice_dispatch_config` ORDER BY `id` ASC LIMIT 1"
        );
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        if (rows == null || rows.isEmpty()) {
            out.put("autoPurgeEnabled", false);
            out.put("retainDays", 90);
            out.put("updatedByName", null);
            out.put("updatedAt", null);
            return out;
        }
        Map<String, Object> row = rows.get(0);
        out.put("autoPurgeEnabled", toInt(row.get("auto_purge_enabled")) == 1);
        out.put("retainDays", Math.max(1, toInt(row.get("retain_days"))));
        out.put("updatedByName", trimToNull(row.get("updated_by_name")));
        out.put("updatedAt", toDateText(row.get("updated_at")));
        return out;
    }

    @Override
    public Result<Void> updateNoticeDispatchConfig(Long operatorUserId, String operatorName, AdminNoticeManageDTO.ConfigReq req) {
        ensureNoticeDispatchConfigRow();
        int retainDays = req == null || req.getRetainDays() == null ? 90 : Math.max(1, req.getRetainDays());
        int autoPurgeEnabled = req != null && Boolean.TRUE.equals(req.getAutoPurgeEnabled()) ? 1 : 0;
        jdbcTemplate.update(
                "UPDATE `notice_dispatch_config` SET `auto_purge_enabled`=?, `retain_days`=?, `updated_by_user_id`=?, `updated_by_name`=?, `updated_at`=NOW() WHERE `id`=1",
                autoPurgeEnabled,
                retainDays,
                operatorUserId,
                trimToNull(operatorName)
        );
        return Result.success(null);
    }

    private Long createTask(
            String noticeType,
            String sourceKind,
            String title,
            String content,
            String routeCode,
            String targetType,
            String targetConfigJson,
            Long senderUserId,
            String senderName
    ) {
        Long taskId = IdWorker.getId();
        jdbcTemplate.update(
                "INSERT INTO `notice_task` (`id`,`notice_type`,`source_kind`,`title`,`content`,`route_code`,`target_type`,`target_config_json`,`dispatch_status`,`target_count`,`success_count`,`failed_count`,`result_message`,`deleted`,`created_by_user_id`,`created_by_name`) "
                        + "VALUES (?,?,?,?,?,?,?,?, 'pending',0,0,0,NULL,0,?,?)",
                taskId,
                normalizeNoticeType(noticeType, true),
                normalizeSourceKind(sourceKind),
                trimToEmpty(title),
                trimToNull(content),
                trimToNull(routeCode),
                normalizeTargetType(targetType),
                trimToNull(targetConfigJson),
                senderUserId,
                trimToNull(senderName)
        );
        return taskId;
    }

    private void ensureNoticeDispatchConfigRow() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM `notice_dispatch_config`", Long.class);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO `notice_dispatch_config` (`id`,`auto_purge_enabled`,`retain_days`,`created_at`,`updated_at`) VALUES (1,0,90,NOW(),NOW())"
        );
    }

    private String buildExplicitUsersConfig(List<Long> userIds) {
        try {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("userIds", userIds);
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("构建消息接收对象失败", e);
        }
    }

    private String buildRoleTargetConfig(List<String> roleCodes) {
        try {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("roleCodes", roleCodes);
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("构建角色消息接收对象失败", e);
        }
    }

    private List<Long> listAdminIdsByRoute(String routeCode) {
        String targetRoute = trimToNull(routeCode);
        if (!StringUtils.hasText(targetRoute)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT `id`,`role_code`,`is_super_admin` FROM `user` " +
                        "WHERE `deleted`=0 AND IFNULL(`recycle_flag`,0)=0 AND IFNULL(`enabled`,1)=1 " +
                        "AND LOWER(COALESCE(NULLIF(TRIM(`user_type`),''),'miniapp'))='admin'"
        );
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Boolean> permissionCache = new HashMap<String, Boolean>();
        List<Long> out = new ArrayList<Long>();
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            Long userId = toNullableLong(row.get("id"));
            if (userId == null) {
                continue;
            }
            if (toInt(row.get("is_super_admin")) == 1) {
                out.add(userId);
                continue;
            }
            String roleCode = trimToNull(row.get("role_code"));
            if (!StringUtils.hasText(roleCode)) {
                continue;
            }
            Boolean permitted = permissionCache.get(roleCode);
            if (permitted == null) {
                List<String> menuPermissions = adminRoleService.resolveMenuPermissions(roleCode);
                permitted = menuPermissions != null && menuPermissions.contains(targetRoute);
                permissionCache.put(roleCode, permitted);
            }
            if (Boolean.TRUE.equals(permitted)) {
                out.add(userId);
            }
        }
        return out;
    }

    private List<Long> listMiniappConsoleUserIds() {
        List<Long> rows = jdbcTemplate.queryForList(
                "SELECT `id` FROM `user` " +
                        "WHERE `deleted`=0 AND IFNULL(`recycle_flag`,0)=0 AND IFNULL(`enabled`,1)=1 " +
                        "AND LOWER(COALESCE(NULLIF(TRIM(`user_type`),''),'miniapp'))='miniapp' " +
                        "AND LOWER(COALESCE(NULLIF(TRIM(`status`),''),'pending'))='approved' " +
                        "AND IFNULL(`can_console`,0)=1",
                Long.class
        );
        return rows == null ? Collections.<Long>emptyList() : rows;
    }

    private List<Long> normalizeTargetUserIds(List<Long> targetUserIds) {
        if (targetUserIds == null || targetUserIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> out = new LinkedHashSet<Long>();
        for (Long userId : targetUserIds) {
            if (userId != null && userId > 0) {
                out.add(userId);
            }
        }
        return new ArrayList<Long>(out);
    }

    private List<String> normalizeTargetRoleCodes(List<String> targetRoleCodes) {
        if (targetRoleCodes == null || targetRoleCodes.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> out = new LinkedHashSet<String>();
        for (String rawRoleCode : targetRoleCodes) {
            String roleCode = adminRoleService.normalizeRoleCode(rawRoleCode);
            if (!StringUtils.hasText(roleCode)) {
                continue;
            }
            if (adminRoleService.findByRoleCode(roleCode, false) == null) {
                continue;
            }
            out.add(roleCode);
        }
        return new ArrayList<String>(out);
    }

    private long queryLong(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private void normalizeNoticeRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            row.put("id", toIdText(row.get("id")));
            row.put("taskId", toIdText(row.get("task_id")));
            row.put("createdAt", toDateText(row.get("created_at")));
            row.put("readAt", toDateText(row.get("read_at")));
            row.put("noticeType", trimToNull(row.get("notice_type")));
            row.put("routeCode", trimToNull(row.get("route_code")));
            row.put("sourceKind", trimToNull(row.get("source_kind")));
            row.put("senderName", trimToNull(row.get("sender_name")));
            row.put("isRead", toInt(row.get("is_read")));
        }
    }

    private void normalizeTaskRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            row.put("id", toIdText(row.get("id")));
            row.put("createdAt", toDateText(row.get("created_at")));
            row.put("updatedAt", toDateText(row.get("updated_at")));
            row.put("noticeType", trimToNull(row.get("notice_type")));
            row.put("routeCode", trimToNull(row.get("route_code")));
            row.put("targetType", trimToNull(row.get("target_type")));
            row.put("dispatchStatus", trimToNull(row.get("dispatch_status")));
            row.put("targetCount", toInt(row.get("target_count")));
            row.put("successCount", toInt(row.get("success_count")));
            row.put("failedCount", toInt(row.get("failed_count")));
            row.put("resultMessage", trimToNull(row.get("result_message")));
            row.put("createdByName", trimToNull(row.get("created_by_name")));
        }
    }

    private String toIdText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private String toDateText(Object value) {
        if (value instanceof Timestamp) {
            return value.toString();
        }
        String text = String.valueOf(value == null ? "" : value).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Boolean) {
            return Boolean.TRUE.equals(value) ? 1 : 0;
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

    private Long toNullableLong(Object value) {
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

    private boolean sameUpdatedAt(Object actual, String expected) {
        if (!StringUtils.hasText(expected)) {
            return true;
        }
        return Objects.equals(toDateText(actual), trimToNull(expected));
    }

    private String normalizeNoticeType(String noticeType, boolean withDefault) {
        String type = normalizeLower(noticeType);
        if (!StringUtils.hasText(type)) {
            return withDefault ? AuthDomainConstants.NOTICE_TYPE_SYSTEM : null;
        }
        if (AuthDomainConstants.NOTICE_TYPE_SYSTEM.equals(type)
                || AuthDomainConstants.NOTICE_TYPE_REVIEW.equals(type)
                || AuthDomainConstants.NOTICE_TYPE_STATUS.equals(type)
                || AuthDomainConstants.NOTICE_TYPE_REVIEW_APPLY.equals(type)
                || AuthDomainConstants.NOTICE_TYPE_MESSAGE.equals(type)) {
            return type;
        }
        return withDefault ? AuthDomainConstants.NOTICE_TYPE_SYSTEM : null;
    }

    private String normalizeSourceKind(String sourceKind) {
        return AuthDomainConstants.NOTICE_SOURCE_MANUAL.equals(normalizeLower(sourceKind))
                ? AuthDomainConstants.NOTICE_SOURCE_MANUAL
                : AuthDomainConstants.NOTICE_SOURCE_SYSTEM;
    }

    private String normalizeTargetType(String targetType) {
        String text = normalizeLower(targetType);
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

    private String normalizeDispatchStatus(String dispatchStatus) {
        String text = normalizeLower(dispatchStatus);
        if ("pending".equals(text) || "sending".equals(text) || "sent".equals(text) || "failed".equals(text)) {
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

    private String shortenError(Exception e) {
        String message = e == null ? null : e.getMessage();
        if (!StringUtils.hasText(message)) {
            return "消息发送失败";
        }
        message = message.trim();
        return message.length() > 180 ? message.substring(0, 180) : message;
    }
}
