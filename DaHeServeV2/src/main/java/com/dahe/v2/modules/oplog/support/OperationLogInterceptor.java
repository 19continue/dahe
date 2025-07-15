package com.dahe.v2.modules.oplog.support;

import com.dahe.v2.common.Result;
import com.dahe.v2.modules.auth.support.AuthContext;
import com.dahe.v2.modules.oplog.model.OperationLog;
import com.dahe.v2.modules.oplog.service.OperationLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import com.dahe.v2.modules.user.model.AppUser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
/**
 * 操作日志拦截器。
 * 在写请求链路中做两件事：
 * 1. 采集审计基础信息（操作者、接口、耗时、结果码等）；
 * 2. 按接口类型预采集“撤销快照”并写入日志，供后续 `undo` 使用。
 */
public class OperationLogInterceptor implements HandlerInterceptor {

    /** request 里的撤销快照键。 */
    private static final String ATTR_UNDO_SNAPSHOT = "dahe.v2.oplog.undoSnapshot";
    private static final Pattern P_FIELD_ENABLED = Pattern.compile("^/api/v2/fields/(\\d+)/enabled$");
    private static final Pattern P_SEED_BATCH_ENABLED = Pattern.compile("^/api/v2/seed-batches/(\\d+)/enabled$");
    private static final Pattern P_TEMPLATE_ENABLED = Pattern.compile("^/api/v2/farm-process/templates/(\\d+)/enabled$");
    private static final Pattern P_USER_ENABLED = Pattern.compile("^/api/v2/admin/users/(\\d+)/enabled$");
    private static final Pattern P_USER_STATUS_COMPAT = Pattern.compile("^/api/v2/admin/users/(\\d+)/status$");
    private static final Pattern P_USER_APPROVE_UPDATE = Pattern.compile("^/api/v2/admin/users/(\\d+)/approve$");
    private static final Pattern P_USER_ROLE_UPDATE = Pattern.compile("^/api/v2/admin/users/(\\d+)/role$");
    private static final Pattern P_USER_DELETE = Pattern.compile("^/api/v2/admin/users/(\\d+)$");
    private static final Pattern P_EXPORT_TEMPLATE_UPDATE = Pattern.compile("^/api/v2/admin/export-templates/(\\d+)$");
    private static final Pattern P_EXPORT_TEMPLATE_DELETE = Pattern.compile("^/api/v2/admin/export-templates/(\\d+)$");
    private static final Pattern P_EXPORT_DICT_UPDATE = Pattern.compile("^/api/v2/admin/export-dicts/(\\d+)$");
    private static final Pattern P_EXPORT_DICT_DELETE = Pattern.compile("^/api/v2/admin/export-dicts/(\\d+)$");
    private static final Pattern P_ASSET_REVIEW_UPDATE = Pattern.compile("^/api/v2/admin/assets/(\\d+)/review$");
    private static final Pattern P_ASSET_POLICY_UPDATE = Pattern.compile("^/api/v2/admin/asset-policy$");
    private static final Pattern P_RECORD_POLICY_UPDATE = Pattern.compile("^/api/v2/admin/record-policy$");
    private static final Pattern P_AMAP_QUOTA_UPDATE = Pattern.compile("^/api/v2/admin/amap/quota$");
    private static final Pattern P_TEMPLATE_UPDATE = Pattern.compile("^/api/v2/farm-process/templates/(\\d+)$");
    private static final Pattern P_PROCESS_STEP_UPDATE = Pattern.compile("^/api/v2/farm-process/steps/(\\d+)$");
    private static final Pattern P_DYNAMIC_CONFIG_UPDATE = Pattern.compile("^/api/v2/admin/dynamic-configs/(\\d+)$");
    private static final Pattern P_SEED_DYNAMIC_CONFIG_UPDATE = Pattern.compile("^/api/v2/admin/seed-dynamic-configs/(\\d+)$");
    private static final Pattern P_FIELD_DELETE = Pattern.compile("^/api/v2/fields/(\\d+)$");
    private static final Pattern P_FARM_RECORD_DELETE = Pattern.compile("^/api/v2/farm-records/(\\d+)$");
    private static final Pattern P_SEED_BATCH_DELETE = Pattern.compile("^/api/v2/seed-batches/(\\d+)$");
    private static final Pattern P_SEED_TEST_DELETE = Pattern.compile("^/api/v2/seed-batches/(\\d+)/tests/(\\d+)$");
    private static final Pattern P_ASSET_DELETE = Pattern.compile("^/api/v2/admin/assets/(\\d+)$");
    private static final Pattern P_AMAP_AUDIT_DELETE = Pattern.compile("^/api/v2/admin/amap/audits/(\\d+)$");
    private static final Pattern P_FIELD_CYCLE_UPDATE = Pattern.compile("^/api/v2/fields/(\\d+)/cycles/(\\d+)$");
    private static final Pattern P_FIELD_CYCLE_DELETE = Pattern.compile("^/api/v2/fields/(\\d+)/cycles/(\\d+)$");
    private static final Pattern P_FIELD_CYCLE_CURRENT = Pattern.compile("^/api/v2/fields/(\\d+)/cycles/(\\d+)/current$");
    private static final Pattern P_TEMPLATE_DELETE = Pattern.compile("^/api/v2/farm-process/templates/(\\d+)$");
    private static final Pattern P_DYNAMIC_CONFIG_DELETE = Pattern.compile("^/api/v2/admin/dynamic-configs/(\\d+)$");
    private static final Pattern P_SEED_DYNAMIC_CONFIG_DELETE = Pattern.compile("^/api/v2/admin/seed-dynamic-configs/(\\d+)$");
    private static final Pattern P_FIELD_REORDER = Pattern.compile("^/api/v2/fields/reorder$");
    private static final Pattern P_CROP_UPDATE = Pattern.compile("^/api/v2/crops/(\\d+)$");
    private static final Pattern P_CROP_REORDER = Pattern.compile("^/api/v2/crops/reorder$");
    private static final Pattern P_SEED_RULE_UPDATE = Pattern.compile("^/api/v2/seed-settings$");
    private static final Pattern P_TEMPLATE_STEP_SORT = Pattern.compile("^/api/v2/farm-process/templates/(\\d+)/steps/sort$");
    private static final Pattern P_ASSET_REORDER = Pattern.compile("^/api/v2/admin/assets/reorder$");
    private static final Set<String> FIELD_UPDATE_COLUMNS = orderedSet(
            "name",
            "area_mu",
            "crop_type",
            "crop_variety",
            "province",
            "city",
            "district",
            "township",
            "formatted_address",
            "status",
            "enabled",
            "location_lat",
            "location_lng",
            "location_desc",
            "cover_image_url",
            "remark"
    );
    private static final Set<String> SEED_BATCH_UPDATE_COLUMNS = orderedSet(
            "batch_code",
            "crop_type",
            "variety_name",
            "production_date",
            "remark",
            "enabled",
            "form_config_id",
            "extra_json"
    );
    private static final Set<String> TEMPLATE_UPDATE_COLUMNS = orderedSet(
            "crop_id",
            "template_name",
            "enabled",
            "is_default"
    );
    private static final Set<String> FARM_RECORD_UPDATE_COLUMNS = orderedSet(
            "field_id",
            "cycle_id",
            "step_id",
            "work_date",
            "operator_name",
            "operator_user_id",
            "notes",
            "weather",
            "temperature",
            "weather_location",
            "humidity",
            "wind_direction",
            "wind_power",
            "weather_report_time",
            "extra_json"
    );
    private static final Set<String> SEED_TEST_UPDATE_COLUMNS = orderedSet(
            "test_date",
            "sample_count",
            "germination_count",
            "germination_rate",
            "purity",
            "moisture",
            "cleanliness",
            "tester_name",
            "remark",
            "form_config_id",
            "extra_json"
    );
    private static final Set<String> PROCESS_STEP_UPDATE_COLUMNS = orderedSet(
            "step_name",
            "sort_order",
            "growth_stage",
            "requirement_desc",
            "form_config_id",
            "form_schema"
    );
    private static final Set<String> DYNAMIC_CONFIG_UPDATE_COLUMNS = orderedSet(
            "module_key",
            "scene_key",
            "config_name",
            "schema_json",
            "status",
            "version_no",
            "remark"
    );
    private static final Set<String> USER_REVIEW_UPDATE_COLUMNS = orderedSet(
            "status",
            "reject_reason",
            "role_code",
            "can_console"
    );
    private static final Set<String> EXPORT_TEMPLATE_UPDATE_COLUMNS = orderedSet(
            "module_key",
            "template_code",
            "template_name",
            "version_no",
            "status",
            "fields_json",
            "remark"
    );
    private static final Set<String> EXPORT_DICT_UPDATE_COLUMNS = orderedSet(
            "module_key",
            "field_code",
            "field_name",
            "data_type",
            "description",
            "example_value"
    );
    private static final Set<String> RECORD_POLICY_UPDATE_COLUMNS = orderedSet(
            "edit_window_hours",
            "allow_operator_update",
            "allow_operator_delete",
            "remark"
    );
    private static final Set<String> ASSET_REVIEW_UPDATE_COLUMNS = orderedSet(
            "review_status",
            "reviewed_at",
            "reviewed_by_user_id",
            "review_remark"
    );
    private static final Set<String> ASSET_POLICY_UPDATE_COLUMNS = orderedSet(
            "miniapp_daily_upload_limit",
            "miniapp_daily_upload_size_mb",
            "miniapp_single_file_max_mb",
            "miniapp_allowed_file_types",
            "miniapp_require_review",
            "admin_daily_upload_limit",
            "admin_daily_upload_size_mb",
            "admin_single_file_max_mb",
            "admin_allowed_file_types",
            "admin_require_review",
            "operator_daily_upload_limit",
            "operator_daily_upload_size_mb",
            "operator_single_file_max_mb",
            "operator_allowed_file_types",
            "operator_require_review",
            "strict_source_purge_retain_days",
            "remark"
    );
    private static final Set<String> AMAP_QUOTA_UPDATE_COLUMNS = orderedSet(
            "daily_limit",
            "alert_threshold",
            "used_count",
            "account_name",
            "account_login",
            "app_name",
            "console_url",
            "key_console_url",
            "app_key",
            "app_key_status",
            "app_key_bound_at",
            "app_key_last_check_at",
            "app_key_last_check_message",
            "last_health_check_at",
            "last_health_check_message",
            "weather_daily_limit",
            "weather_used_count",
            "location_daily_limit",
            "location_used_count",
            "geocode_daily_limit",
            "geocode_used_count",
            "city_daily_limit",
            "city_used_count",
            "qps_limit",
            "recharge_total",
            "weather_recharge_total",
            "location_recharge_total",
            "geocode_recharge_total",
            "city_recharge_total",
            "cache_redis_enabled",
            "cache_redis_key_prefix",
            "cache_region_ttl_minutes",
            "cache_region_stale_minutes",
            "cache_weather_ttl_minutes",
            "cache_local_region_max_entries",
            "cache_local_weather_max_entries",
            "audit_auto_purge_enabled",
            "audit_retain_days",
            "remark"
    );
    private static final Set<String> FIELD_CYCLE_UPDATE_COLUMNS = orderedSet(
            "cycle_name",
            "crops_json",
            "template_ids_json",
            "plan_mode",
            "start_date",
            "end_date",
            "status",
            "is_current"
    );
    private static final Set<String> CROP_UPDATE_COLUMNS = orderedSet(
            "name",
            "variety",
            "node_type",
            "parent_id",
            "image_url",
            "sort_order"
    );
    private static final Set<String> SEED_RULE_UPDATE_COLUMNS = orderedSet(
            "fixed_sample_size",
            "default_sample_size",
            "remark"
    );

    private final OperationLogService operationLogService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OperationLogInterceptor(
            OperationLogService operationLogService,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.operationLogService = operationLogService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    /** 进入控制器前：记录开始时间并尝试构建撤销快照。 */
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (shouldLog(request)) {
            request.setAttribute(OperationLogTraceKeys.ATTR_START_AT, System.currentTimeMillis());
            UndoSnapshot snapshot = resolveUndoSnapshot(request);
            if (snapshot != null) {
                request.setAttribute(ATTR_UNDO_SNAPSHOT, snapshot);
            }
        }
        return true;
    }

    @Override
    /** 请求完成后：拼装并落库操作日志。 */
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!shouldLog(request)) {
            return;
        }
        try {
            OperationLog row = buildLogRow(request, response, ex);
            if (row == null) {
                return;
            }
            operationLogService.save(row);
        } catch (Exception ignored) {
        }
    }

    /** 组装单条日志实体。 */
    private OperationLog buildLogRow(HttpServletRequest request, HttpServletResponse response, Exception ex) {
        if (request == null) {
            return null;
        }
        AppUser user = AuthContext.getCurrentUser(request);
        UndoSnapshot snapshot = resolveUndoSnapshotFromAttr(request);
        Integer resultCode = toInteger(request.getAttribute(OperationLogTraceKeys.ATTR_RESULT_CODE));
        String resultMessage = trimToLength(stringValue(request.getAttribute(OperationLogTraceKeys.ATTR_RESULT_MESSAGE)), 500);
        if (ex != null) {
            resultMessage = trimToLength(stringValue(ex.getMessage()), 500);
        }
        Integer successFlag = resolveSuccessFlag(resultCode, ex);
        String operationType = resolveOperationType(request);

        OperationLog row = new OperationLog();
        if (user != null) {
            row.setUserId(user.getId());
            row.setUserType(trimToLength(stringValue(user.getUserType()), 16));
            row.setRoleCode(trimToLength(stringValue(user.getRoleCode()), 32));
            row.setOperatorName(trimToLength(resolveDisplayName(user), 64));
        }
        row.setOperationType(operationType);
        row.setHttpMethod(trimToLength(stringValue(request.getMethod()).toUpperCase(Locale.ROOT), 16));
        row.setApiPath(trimToLength(stringValue(request.getRequestURI()), 255));
        row.setQueryString(trimToLength(stringValue(request.getQueryString()), 500));
        row.setResultCode(resultCode);
        row.setResultMessage(resultMessage);
        row.setSuccessFlag(successFlag);
        row.setCostMs(resolveCostMs(request));
        row.setClientIp(trimToLength(resolveClientIp(request), 64));
        if (snapshot != null) {
            row.setTargetModule(trimToLength(snapshot.targetModule, 32));
            row.setTargetId(snapshot.targetId);
        }
        if (successFlag != null && successFlag == 1 && snapshot != null && StringUtils.hasText(snapshot.undoType) && snapshot.payload != null && !snapshot.payload.isEmpty()) {
            String payloadJson = toJson(snapshot.payload);
            if (StringUtils.hasText(payloadJson)) {
                row.setUndoType(trimToLength(snapshot.undoType, 32));
                row.setUndoPayloadJson(payloadJson);
                row.setUndoStatus("pending");
            }
        }
        return row;
    }

    /** 推导 successFlag：异常失败，Result 非成功码失败。 */
    private Integer resolveSuccessFlag(Integer resultCode, Exception ex) {
        if (ex != null) {
            return 0;
        }
        if (resultCode == null) {
            return 1;
        }
        return resultCode == Result.SUCCESS_CODE ? 1 : 0;
    }

    /** 计算请求耗时（毫秒）。 */
    private Integer resolveCostMs(HttpServletRequest request) {
        Object startedAt = request.getAttribute(OperationLogTraceKeys.ATTR_START_AT);
        if (!(startedAt instanceof Number)) {
            return null;
        }
        long cost = System.currentTimeMillis() - ((Number) startedAt).longValue();
        if (cost < 0) {
            cost = 0;
        }
        if (cost > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) cost;
    }

    /** 按请求路径和方法粗粒度推断操作类型。 */
    private String resolveOperationType(HttpServletRequest request) {
        String method = stringValue(request == null ? null : request.getMethod()).trim().toUpperCase(Locale.ROOT);
        String path = stringValue(request == null ? null : request.getRequestURI()).trim().toLowerCase(Locale.ROOT);
        if (path.contains("/reorder") || path.endsWith("/sort") || path.contains("/steps/sort")) {
            return "reorder";
        }
        if (path.contains("/enabled")) {
            return "enable_toggle";
        }
        if (path.contains("/restore") || path.contains("/recover")) {
            return "restore";
        }
        if (path.contains("/purge") || path.contains("/permanent")) {
            return "purge";
        }
        if (path.contains("/review") || path.contains("/approve") || path.contains("/reject")) {
            return "review";
        }
        if ("POST".equals(method)) {
            return "create";
        }
        if ("PUT".equals(method) || "PATCH".equals(method)) {
            return "update";
        }
        if ("DELETE".equals(method)) {
            return "delete";
        }
        return "operate";
    }

    /** 仅记录 `/api/v2/**` 下的写请求，并排除公开与日志自身接口。 */
    private boolean shouldLog(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String method = stringValue(request.getMethod()).trim().toUpperCase(Locale.ROOT);
        if (!("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method))) {
            return false;
        }
        String path = stringValue(request.getRequestURI()).trim();
        if (!path.startsWith("/api/v2/")) {
            return false;
        }
        if (path.startsWith("/api/v2/public/")) {
            return false;
        }
        if (path.startsWith("/api/v2/admin/operation-logs")) {
            return false;
        }
        if (path.startsWith("/api/v2/miniapp/amap/audit")) {
            return false;
        }
        return true;
    }

    /** 从 request attribute 获取预采集的撤销快照。 */
    private UndoSnapshot resolveUndoSnapshotFromAttr(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object raw = request.getAttribute(ATTR_UNDO_SNAPSHOT);
        if (raw instanceof UndoSnapshot) {
            return (UndoSnapshot) raw;
        }
        return null;
    }

    /** 按请求方法分派撤销快照构建逻辑。 */
    private UndoSnapshot resolveUndoSnapshot(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String method = stringValue(request.getMethod()).trim().toUpperCase(Locale.ROOT);
        String path = stringValue(request.getRequestURI()).trim();
        if ("POST".equals(method)) {
            return resolvePostUndoSnapshot(path);
        }
        if ("PUT".equals(method) || "PATCH".equals(method)) {
            UndoSnapshot enabledSnapshot = resolveEnabledUndoSnapshot(path);
            if (enabledSnapshot != null) {
                return enabledSnapshot;
            }
            return resolveUpdateUndoSnapshot(path);
        }
        if ("DELETE".equals(method)) {
            return resolveDeleteUndoSnapshot(path);
        }
        return null;
    }

    /** 处理 POST 场景的快照（目前覆盖重排接口）。 */
    private UndoSnapshot resolvePostUndoSnapshot(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        if (P_FIELD_REORDER.matcher(path).matches()) {
            return buildReorderSnapshot("field_reorder", 1L, "field", queryFieldSortOrders());
        }
        if (P_CROP_REORDER.matcher(path).matches()) {
            return buildReorderSnapshot("crop_reorder", 1L, "crop", queryCropSortOrders());
        }
        if (P_ASSET_REORDER.matcher(path).matches()) {
            return buildReorderSnapshot("asset_reorder", 1L, "media_asset", queryAssetSortOrders());
        }
        return null;
    }

    /** 处理 enabled/status 这类启停开关的快照。 */
    private UndoSnapshot resolveEnabledUndoSnapshot(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        Long id = matchLong(path, P_FIELD_ENABLED, 1);
        if (id != null) {
            return buildEnabledSnapshot("field", "field", id);
        }
        id = matchLong(path, P_SEED_BATCH_ENABLED, 1);
        if (id != null) {
            return buildEnabledSnapshot("seed", "seed_batch", id);
        }
        id = matchLong(path, P_TEMPLATE_ENABLED, 1);
        if (id != null) {
            return buildEnabledSnapshot("process_template", "farm_process_template", id);
        }
        id = matchLong(path, P_USER_ENABLED, 1);
        if (id != null) {
            return buildEnabledSnapshot("user", "user", id);
        }
        id = matchLong(path, P_USER_STATUS_COMPAT, 1);
        if (id != null) {
            return buildEnabledSnapshot("user", "user", id);
        }
        return null;
    }

    /** 构建“启停恢复”快照。 */
    private UndoSnapshot buildEnabledSnapshot(String module, String table, Long id) {
        Integer previousEnabled = queryIntColumn(table, "enabled", id);
        if (previousEnabled == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("table", table);
        payload.put("id", id);
        payload.put("previousEnabled", previousEnabled);
        UndoSnapshot snapshot = new UndoSnapshot();
        snapshot.targetModule = module;
        snapshot.targetId = id;
        snapshot.undoType = "restore_enabled";
        snapshot.payload = payload;
        return snapshot;
    }

    /** 处理 DELETE 场景快照（逻辑删除/回收站删除）。 */
    private UndoSnapshot resolveDeleteUndoSnapshot(String path) {
        if (!StringUtils.hasText(path) || path.endsWith("/purge")) {
            return null;
        }
        Long id = matchLong(path, P_FIELD_DELETE, 1);
        if (id != null) {
            return buildDeletedSnapshot("field", "field", id);
        }
        id = matchLong(path, P_FARM_RECORD_DELETE, 1);
        if (id != null) {
            return buildDeletedSnapshot("farm_record", "farm_record", id);
        }
        id = matchLong(path, P_SEED_BATCH_DELETE, 1);
        if (id != null) {
            return buildDeletedSnapshot("seed_batch", "seed_batch", id);
        }
        id = matchLong(path, P_SEED_TEST_DELETE, 2);
        if (id != null) {
            return buildDeletedSnapshot("seed_test", "seed_quality_test", id);
        }
        id = matchLong(path, P_FIELD_CYCLE_DELETE, 2);
        if (id != null) {
            return buildDeletedSnapshot("field_cycle", "field_crop_cycle", id);
        }
        id = matchLong(path, P_AMAP_AUDIT_DELETE, 1);
        if (id != null) {
            return buildDeletedSnapshot("amap_audit", "amap_api_audit", id);
        }
        id = matchLong(path, P_TEMPLATE_DELETE, 1);
        if (id != null) {
            return buildDeletedSnapshot("process_template", "farm_process_template", id);
        }
        id = matchLong(path, P_DYNAMIC_CONFIG_DELETE, 1);
        if (id != null) {
            return buildDeletedSnapshot("dynamic_config", "dynamic_form_config", id);
        }
        id = matchLong(path, P_SEED_DYNAMIC_CONFIG_DELETE, 1);
        if (id != null) {
            return buildDeletedSnapshot("dynamic_config", "dynamic_form_config", id);
        }
        id = matchLong(path, P_EXPORT_TEMPLATE_DELETE, 1);
        if (id != null) {
            return buildDeletedSnapshot("export_template", "export_template", id);
        }
        id = matchLong(path, P_USER_DELETE, 1);
        if (id != null) {
            return buildDeletedSnapshot("user", "user", id);
        }
        id = matchLong(path, P_EXPORT_DICT_DELETE, 1);
        if (id != null) {
            return buildDeletedSnapshot("export_dict", "export_field_dict", id);
        }
        id = matchLong(path, P_ASSET_DELETE, 1);
        if (id != null) {
            return buildAssetRecycleSnapshot(id);
        }
        return null;
    }

    /** 处理 PUT/PATCH 场景快照（字段更新、排序更新、当前计划切换等）。 */
    private UndoSnapshot resolveUpdateUndoSnapshot(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        Long id = matchLong(path, P_FIELD_DELETE, 1);
        if (id != null) {
            return buildUpdateSnapshot("field", "field", id, FIELD_UPDATE_COLUMNS);
        }
        id = matchLong(path, P_SEED_BATCH_DELETE, 1);
        if (id != null) {
            return buildUpdateSnapshot("seed", "seed_batch", id, SEED_BATCH_UPDATE_COLUMNS);
        }
        id = matchLong(path, P_TEMPLATE_UPDATE, 1);
        if (id != null) {
            return buildUpdateSnapshot("process_template", "farm_process_template", id, TEMPLATE_UPDATE_COLUMNS);
        }
        id = matchLong(path, P_FARM_RECORD_DELETE, 1);
        if (id != null) {
            return buildUpdateSnapshot("farm_record", "farm_record", id, FARM_RECORD_UPDATE_COLUMNS);
        }
        id = matchLong(path, P_SEED_TEST_DELETE, 2);
        if (id != null) {
            return buildUpdateSnapshot("seed_test", "seed_quality_test", id, SEED_TEST_UPDATE_COLUMNS);
        }
        id = matchLong(path, P_FIELD_CYCLE_UPDATE, 2);
        if (id != null) {
            return buildUpdateSnapshot("field_cycle", "field_crop_cycle", id, FIELD_CYCLE_UPDATE_COLUMNS);
        }
        Long fieldId = matchLong(path, P_FIELD_CYCLE_CURRENT, 1);
        if (fieldId != null) {
            return buildCurrentCycleSnapshot(fieldId);
        }
        id = matchLong(path, P_CROP_UPDATE, 1);
        if (id != null) {
            return buildUpdateSnapshot("crop", "crop", id, CROP_UPDATE_COLUMNS);
        }
        if (P_SEED_RULE_UPDATE.matcher(path).matches()) {
            return buildUpdateSnapshot("seed_rule", "seed_quality_rule", 1L, SEED_RULE_UPDATE_COLUMNS);
        }
        Long templateId = matchLong(path, P_TEMPLATE_STEP_SORT, 1);
        if (templateId != null) {
            return buildStepSortSnapshot(templateId);
        }
        id = matchLong(path, P_PROCESS_STEP_UPDATE, 1);
        if (id != null) {
            return buildUpdateSnapshot("process_step", "farm_process_step", id, PROCESS_STEP_UPDATE_COLUMNS);
        }
        id = matchLong(path, P_DYNAMIC_CONFIG_UPDATE, 1);
        if (id != null) {
            return buildUpdateSnapshot("dynamic_config", "dynamic_form_config", id, DYNAMIC_CONFIG_UPDATE_COLUMNS);
        }
        id = matchLong(path, P_SEED_DYNAMIC_CONFIG_UPDATE, 1);
        if (id != null) {
            return buildUpdateSnapshot("dynamic_config", "dynamic_form_config", id, DYNAMIC_CONFIG_UPDATE_COLUMNS);
        }
        id = matchLong(path, P_USER_APPROVE_UPDATE, 1);
        if (id != null) {
            return buildUpdateSnapshot("user", "user", id, USER_REVIEW_UPDATE_COLUMNS);
        }
        id = matchLong(path, P_USER_ROLE_UPDATE, 1);
        if (id != null) {
            return buildUpdateSnapshot("user", "user", id, USER_REVIEW_UPDATE_COLUMNS);
        }
        id = matchLong(path, P_EXPORT_TEMPLATE_UPDATE, 1);
        if (id != null) {
            return buildUpdateSnapshot("export_template", "export_template", id, EXPORT_TEMPLATE_UPDATE_COLUMNS);
        }
        id = matchLong(path, P_EXPORT_DICT_UPDATE, 1);
        if (id != null) {
            return buildUpdateSnapshot("export_dict", "export_field_dict", id, EXPORT_DICT_UPDATE_COLUMNS);
        }
        id = matchLong(path, P_ASSET_REVIEW_UPDATE, 1);
        if (id != null) {
            return buildUpdateSnapshot("assets", "media_asset", id, ASSET_REVIEW_UPDATE_COLUMNS);
        }
        if (P_ASSET_POLICY_UPDATE.matcher(path).matches()) {
            return buildUpdateSnapshot("asset_policy", "media_asset_policy_config", 1L, ASSET_POLICY_UPDATE_COLUMNS);
        }
        if (P_RECORD_POLICY_UPDATE.matcher(path).matches()) {
            return buildUpdateSnapshot("record_policy", "record_policy_config", 1L, RECORD_POLICY_UPDATE_COLUMNS);
        }
        if (P_AMAP_QUOTA_UPDATE.matcher(path).matches()) {
            return buildUpdateSnapshot("amap_quota", "amap_quota_config", 1L, AMAP_QUOTA_UPDATE_COLUMNS);
        }
        return null;
    }

    /** 构建“当前计划恢复”快照。 */
    private UndoSnapshot buildCurrentCycleSnapshot(Long fieldId) {
        if (fieldId == null || fieldId <= 0) {
            return null;
        }
        Long previousCurrentCycleId = queryCurrentCycleId(fieldId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fieldId", fieldId);
        payload.put("previousCurrentCycleId", previousCurrentCycleId);
        UndoSnapshot snapshot = new UndoSnapshot();
        snapshot.targetModule = "field_cycle_current";
        snapshot.targetId = fieldId;
        snapshot.undoType = "restore_current_cycle";
        snapshot.payload = payload;
        return snapshot;
    }

    /** 构建“步骤排序恢复”快照。 */
    private UndoSnapshot buildStepSortSnapshot(Long templateId) {
        if (templateId == null || templateId <= 0) {
            return null;
        }
        List<Map<String, Object>> previousOrders = queryStepSortOrders(templateId);
        if (previousOrders.isEmpty()) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("templateId", templateId);
        payload.put("previousOrders", previousOrders);
        UndoSnapshot snapshot = new UndoSnapshot();
        snapshot.targetModule = "process_template";
        snapshot.targetId = templateId;
        snapshot.undoType = "restore_step_sort";
        snapshot.payload = payload;
        return snapshot;
    }

    /** 构建“通用重排恢复”快照。 */
    private UndoSnapshot buildReorderSnapshot(String targetModule, Long targetId, String table, List<Map<String, Object>> previousOrders) {
        if (!StringUtils.hasText(targetModule) || !StringUtils.hasText(table) || previousOrders == null || previousOrders.isEmpty()) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("table", table);
        payload.put("previousOrders", previousOrders);
        UndoSnapshot snapshot = new UndoSnapshot();
        snapshot.targetModule = targetModule;
        snapshot.targetId = targetId;
        snapshot.undoType = "restore_reorder";
        snapshot.payload = payload;
        return snapshot;
    }

    /** 构建“字段更新恢复”快照。 */
    private UndoSnapshot buildUpdateSnapshot(String module, String table, Long id, Set<String> columns) {
        Map<String, Object> previousValues = queryRowColumns(table, id, columns);
        if (previousValues == null || previousValues.isEmpty()) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("table", table);
        payload.put("id", id);
        payload.put("previousValues", previousValues);
        UndoSnapshot snapshot = new UndoSnapshot();
        snapshot.targetModule = module;
        snapshot.targetId = id;
        snapshot.undoType = "restore_update";
        snapshot.payload = payload;
        return snapshot;
    }

    /** 构建“逻辑删除恢复”快照。 */
    private UndoSnapshot buildDeletedSnapshot(String module, String table, Long id) {
        Integer previousDeleted = queryIntColumn(table, "deleted", id);
        if (previousDeleted == null) {
            return null;
        }
        Integer previousSortOrder = queryIntColumn(table, "sort_order", id);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("table", table);
        payload.put("id", id);
        payload.put("previousDeleted", previousDeleted);
        if (previousSortOrder != null) {
            payload.put("previousSortOrder", previousSortOrder);
        }
        UndoSnapshot snapshot = new UndoSnapshot();
        snapshot.targetModule = module;
        snapshot.targetId = id;
        snapshot.undoType = "restore_deleted";
        snapshot.payload = payload;
        return snapshot;
    }

    /** 构建“资源回收站恢复”快照。 */
    private UndoSnapshot buildAssetRecycleSnapshot(Long id) {
        Integer previousRecycleFlag = queryIntColumn("media_asset", "recycle_flag", id);
        if (previousRecycleFlag == null) {
            return null;
        }
        Integer previousDeleted = queryIntColumn("media_asset", "deleted", id);
        Integer previousSortOrder = queryIntColumn("media_asset", "sort_order", id);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("table", "media_asset");
        payload.put("id", id);
        payload.put("previousRecycleFlag", previousRecycleFlag);
        payload.put("previousDeleted", previousDeleted == null ? 0 : previousDeleted);
        if (previousSortOrder != null) {
            payload.put("previousSortOrder", previousSortOrder);
        }
        UndoSnapshot snapshot = new UndoSnapshot();
        snapshot.targetModule = "assets";
        snapshot.targetId = id;
        snapshot.undoType = "restore_recycle";
        snapshot.payload = payload;
        return snapshot;
    }

    /** 查询 int 列值。 */
    private Integer queryIntColumn(String table, String column, Long id) {
        if (!StringUtils.hasText(table) || !StringUtils.hasText(column) || id == null || id <= 0) {
            return null;
        }
        try {
            String sql = "SELECT `" + column + "` FROM `" + table + "` WHERE `id`=?";
            return jdbcTemplate.queryForObject(sql, Integer.class, id);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 查询田块当前计划 ID。 */
    private Long queryCurrentCycleId(Long fieldId) {
        if (fieldId == null || fieldId <= 0) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT `id` FROM `field_crop_cycle` WHERE `field_id`=? AND `deleted`=0 AND `is_current`=1 ORDER BY `id` DESC LIMIT 1",
                    Long.class,
                    fieldId
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 查询模板步骤原排序。 */
    private List<Map<String, Object>> queryStepSortOrders(Long templateId) {
        if (templateId == null || templateId <= 0) {
            return Collections.emptyList();
        }
        return querySortOrders(
                "SELECT `id`,`sort_order` FROM `farm_process_step` WHERE `template_id`=? ORDER BY `id` ASC",
                templateId
        );
    }

    /** 查询田块原排序。 */
    private List<Map<String, Object>> queryFieldSortOrders() {
        return querySortOrders("SELECT `id`,`sort_order` FROM `field` WHERE `deleted`=0 ORDER BY `id` ASC");
    }

    /** 查询作物原排序。 */
    private List<Map<String, Object>> queryCropSortOrders() {
        return querySortOrders("SELECT `id`,`sort_order` FROM `crop` WHERE `deleted`=0 ORDER BY `id` ASC");
    }

    /** 查询资源原排序。 */
    private List<Map<String, Object>> queryAssetSortOrders() {
        return querySortOrders("SELECT `id`,`sort_order` FROM `media_asset` WHERE `deleted`=0 AND `recycle_flag`=0 ORDER BY `id` ASC");
    }

    /** 通用排序快照查询。 */
    private List<Map<String, Object>> querySortOrders(String sql, Object... args) {
        if (!StringUtils.hasText(sql)) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> rows = args == null || args.length == 0
                    ? jdbcTemplate.queryForList(sql)
                    : jdbcTemplate.queryForList(sql, args);
            if (rows == null || rows.isEmpty()) {
                return Collections.emptyList();
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                if (row == null) {
                    continue;
                }
                Object rawId = row.get("id");
                if (!(rawId instanceof Number)) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", ((Number) rawId).longValue());
                Object rawSort = row.get("sort_order");
                if (rawSort instanceof Number) {
                    item.put("sortOrder", ((Number) rawSort).intValue());
                } else {
                    item.put("sortOrder", 0);
                }
                out.add(item);
            }
            return out;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    /** 查询并截取指定行的指定字段快照（字段名白名单控制）。 */
    private Map<String, Object> queryRowColumns(String table, Long id, Set<String> columns) {
        if (!StringUtils.hasText(table) || id == null || id <= 0 || columns == null || columns.isEmpty()) {
            return null;
        }
        List<String> selectedColumns = new ArrayList<>();
        for (String column : columns) {
            if (!StringUtils.hasText(column)) {
                continue;
            }
            String safe = column.trim().toLowerCase(Locale.ROOT);
            if (!isSafeIdentifier(safe)) {
                continue;
            }
            selectedColumns.add(safe);
        }
        if (selectedColumns.isEmpty()) {
            return null;
        }
        try {
            String selectClause = selectedColumns.stream().map(x -> "`" + x + "`").collect(Collectors.joining(","));
            Map<String, Object> raw = jdbcTemplate.queryForMap("SELECT " + selectClause + " FROM `" + table + "` WHERE `id`=?", id);
            if (raw == null || raw.isEmpty()) {
                return null;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            for (String column : selectedColumns) {
                if (!raw.containsKey(column)) {
                    continue;
                }
                out.put(column, normalizeSnapshotValue(raw.get(column)));
            }
            return out;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 标准化快照值，时间类型统一转字符串。 */
    private Object normalizeSnapshotValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime().toString().replace('T', ' ');
        }
        if (value instanceof java.sql.Date || value instanceof java.sql.Time) {
            return value.toString();
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).toString().replace('T', ' ');
        }
        return value;
    }

    /** 标识符安全校验。 */
    private boolean isSafeIdentifier(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return value.matches("^[a-z0-9_]+$");
    }

    /** 基于正则路径提取 long 参数。 */
    private Long matchLong(String path, Pattern pattern, int group) {
        if (!StringUtils.hasText(path) || pattern == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(path);
        if (!matcher.matches()) {
            return null;
        }
        if (group <= 0 || group > matcher.groupCount()) {
            return null;
        }
        String text = stringValue(matcher.group(group)).trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 将撤销 payload 序列化为 JSON。 */
    private String toJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 解析操作者展示名。 */
    private String resolveDisplayName(AppUser user) {
        if (user == null) {
            return null;
        }
        if (StringUtils.hasText(user.getRealName())) {
            return user.getRealName().trim();
        }
        if (StringUtils.hasText(user.getNickName())) {
            return user.getNickName().trim();
        }
        return user.getId() == null ? null : ("用户#" + user.getId());
    }

    /** 解析客户端 IP（优先代理头）。 */
    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = stringValue(request.getHeader("X-Forwarded-For"));
        if (StringUtils.hasText(forwarded)) {
            String[] rows = forwarded.split(",");
            if (rows.length > 0 && StringUtils.hasText(rows[0])) {
                return rows[0].trim();
            }
        }
        String real = stringValue(request.getHeader("X-Real-IP"));
        if (StringUtils.hasText(real)) {
            return real.trim();
        }
        return stringValue(request.getRemoteAddr());
    }

    /** 宽松解析 Integer。 */
    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = stringValue(value).trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 空安全字符串转换。 */
    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** 文本裁剪到指定长度。 */
    private String trimToLength(String text, int maxLength) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength));
    }

    /** 撤销快照对象（仅请求内传递）。 */
    private static class UndoSnapshot {
        private String targetModule;
        private Long targetId;
        private String undoType;
        private Map<String, Object> payload;
    }

    /** 构建按插入顺序保留的不可变集合。 */
    private static Set<String> orderedSet(String... values) {
        if (values == null || values.length == 0) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(values)));
    }
}
