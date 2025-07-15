package com.dahe.v2.modules.oplog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dahe.v2.modules.oplog.mapper.OperationLogMapper;
import com.dahe.v2.modules.oplog.model.OperationLog;
import com.dahe.v2.modules.oplog.service.OperationLogService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
/**
 * 操作日志服务实现。
 * 核心职责：
 * 1. 日志分页检索与链式撤销可用性标记；
 * 2. 基于 undoType + undoPayload 执行可逆操作恢复。
 */
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog> implements OperationLogService {

    /** 支持 restore_deleted 的数据表白名单。 */
    private static final Set<String> RESTORE_DELETED_TABLES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "field",
            "farm_record",
            "seed_batch",
            "seed_quality_test",
            "field_crop_cycle",
            "farm_process_template",
            "dynamic_form_config",
            "export_template",
            "user",
            "export_field_dict",
            "amap_api_audit"
    )));

    /** 支持 restore_enabled 的数据表白名单。 */
    private static final Set<String> RESTORE_ENABLED_TABLES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "field",
            "seed_batch",
            "farm_process_template",
            "user"
    )));

    /** 支持 restore_reorder 的数据表白名单。 */
    private static final Set<String> RESTORE_REORDER_TABLES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "field",
            "crop",
            "media_asset"
    )));

    /** restore_update 可恢复字段白名单（按表维度）。 */
    private static final Map<String, List<String>> RESTORE_UPDATE_COLUMNS = buildRestoreUpdateColumns();

    private static final String UNDO_STATUS_PENDING = "pending";
    private static final String UNDO_STATUS_APPLIED = "applied";
    private static final String UNDO_STATUS_FAILED = "failed";

    private static final String UNDO_RESTORE_DELETED = "restore_deleted";
    private static final String UNDO_RESTORE_ENABLED = "restore_enabled";
    private static final String UNDO_RESTORE_RECYCLE = "restore_recycle";
    private static final String UNDO_RESTORE_UPDATE = "restore_update";
    private static final String UNDO_RESTORE_CURRENT_CYCLE = "restore_current_cycle";
    private static final String UNDO_RESTORE_STEP_SORT = "restore_step_sort";
    private static final String UNDO_RESTORE_REORDER = "restore_reorder";

    private static final String ERR_INVALID_LOG_ID = "invalid operation log id";
    private static final String ERR_LOG_NOT_FOUND = "operation log not found";
    private static final String ERR_ONLY_SUCCESS_UNDO = "only successful operation can be undone";
    private static final String ERR_UNDO_UNSUPPORTED = "undo is not supported for this log";
    private static final String ERR_UNDO_ALREADY_APPLIED = "undo has already been applied";
    private static final String ERR_UNDO_PAYLOAD_INVALID = "undo payload is invalid";
    private static final String ERR_UNDO_TYPE_UNSUPPORTED = "unsupported undo type";
    private static final String ERR_CHAIN_CHECK_FAILED = "chain undo validation failed";
    private static final String ERR_CHAIN_ORDER = "chain undo required, undo latest log first: ";
    private static final String ERR_TARGET_NOT_RESTORED = "target record not found or cannot be restored";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OperationLogServiceImpl(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    /** 分页查询日志并计算链式撤销可用性。 */
    public Page<OperationLog> pageLogs(
            String keyword,
            String operationType,
            Integer successFlag,
            String undoStatus,
            Long userId,
            String apiPath,
            LocalDateTime startAt,
            LocalDateTime endAt,
            long page,
            long pageSize
    ) {
        Page<OperationLog> out = new Page<>(page, pageSize);
        LambdaQueryWrapper<OperationLog> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(operationType)) {
            qw.eq(OperationLog::getOperationType, operationType.trim().toLowerCase());
        }
        if (successFlag != null) {
            qw.eq(OperationLog::getSuccessFlag, successFlag);
        }
        if (StringUtils.hasText(undoStatus)) {
            qw.eq(OperationLog::getUndoStatus, undoStatus.trim().toLowerCase(Locale.ROOT));
        }
        if (userId != null && userId > 0) {
            qw.eq(OperationLog::getUserId, userId);
        }
        if (StringUtils.hasText(apiPath)) {
            qw.like(OperationLog::getApiPath, apiPath.trim());
        }
        if (startAt != null) {
            qw.ge(OperationLog::getCreatedAt, startAt);
        }
        if (endAt != null) {
            qw.le(OperationLog::getCreatedAt, endAt);
        }
        if (StringUtils.hasText(keyword)) {
            String text = keyword.trim();
            qw.and(w -> w.like(OperationLog::getOperatorName, text)
                    .or()
                    .like(OperationLog::getApiPath, text)
                    .or()
                    .like(OperationLog::getQueryString, text)
                    .or()
                    .like(OperationLog::getResultMessage, text));
        }
        qw.orderByDesc(OperationLog::getCreatedAt).orderByDesc(OperationLog::getId);
        Page<OperationLog> result = this.page(out, qw);
        applyChainUndoFlags(result.getRecords());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /** 执行日志撤销入口：先校验，再按 undoType 派发具体恢复动作。 */
    public String undoOperation(Long logId, Long operatorUserId) {
        if (logId == null || logId <= 0) {
            return ERR_INVALID_LOG_ID;
        }
        OperationLog row = this.getById(logId);
        if (row == null) {
            return ERR_LOG_NOT_FOUND;
        }
        if (row.getSuccessFlag() == null || row.getSuccessFlag() != 1) {
            return ERR_ONLY_SUCCESS_UNDO;
        }

        String undoType = normalizeText(row.getUndoType()).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(undoType) || !StringUtils.hasText(row.getUndoPayloadJson())) {
            return ERR_UNDO_UNSUPPORTED;
        }

        String undoStatus = normalizeText(row.getUndoStatus()).toLowerCase(Locale.ROOT);
        if (UNDO_STATUS_APPLIED.equals(undoStatus)) {
            return ERR_UNDO_ALREADY_APPLIED;
        }

        Map<String, Object> payload = parsePayload(row.getUndoPayloadJson());
        if (payload.isEmpty()) {
            return ERR_UNDO_PAYLOAD_INVALID;
        }

        String chainError = checkChainUndoConstraint(row);
        if (StringUtils.hasText(chainError)) {
            return chainError;
        }

        String err = applyUndoByType(undoType, payload);
        if (StringUtils.hasText(err)) {
            markUndoFailed(row, operatorUserId, err);
            return err;
        }

        row.setUndoStatus(UNDO_STATUS_APPLIED);
        row.setUndoFailReason(null);
        row.setUndoAppliedAt(LocalDateTime.now());
        row.setUndoAppliedByUserId(operatorUserId);
        this.updateById(row);
        return null;
    }

    private String applyUndoByType(String undoType, Map<String, Object> payload) {
        switch (undoType) {
            case UNDO_RESTORE_DELETED:
                return applyRestoreDeleted(payload);
            case UNDO_RESTORE_ENABLED:
                return applyRestoreEnabled(payload);
            case UNDO_RESTORE_RECYCLE:
                return applyRestoreRecycle(payload);
            case UNDO_RESTORE_UPDATE:
                return applyRestoreUpdate(payload);
            case UNDO_RESTORE_CURRENT_CYCLE:
                return applyRestoreCurrentCycle(payload);
            case UNDO_RESTORE_STEP_SORT:
                return applyRestoreStepSort(payload);
            case UNDO_RESTORE_REORDER:
                return applyRestoreReorder(payload);
            default:
                return ERR_UNDO_TYPE_UNSUPPORTED;
        }
    }

    /** 撤销失败时回写失败状态与失败原因，便于排查。 */
    private void markUndoFailed(OperationLog row, Long operatorUserId, String failReason) {
        if (row == null || row.getId() == null) {
            return;
        }
        row.setUndoStatus(UNDO_STATUS_FAILED);
        row.setUndoFailReason(trimToLength(failReason, 500));
        row.setUndoAppliedAt(LocalDateTime.now());
        row.setUndoAppliedByUserId(operatorUserId);
        this.updateById(row);
    }

    /** 链式撤销约束：同目标对象只能先撤销最新一条可撤销日志。 */
    private String checkChainUndoConstraint(OperationLog row) {
        if (row == null || row.getId() == null) {
            return ERR_CHAIN_CHECK_FAILED;
        }
        String targetModule = normalizeText(row.getTargetModule());
        Long targetId = row.getTargetId();
        if (!StringUtils.hasText(targetModule) || targetId == null || targetId <= 0) {
            return null;
        }

        OperationLog latest = findLatestUndoableLogForTarget(targetModule, targetId);
        if (latest == null || latest.getId() == null) {
            return null;
        }
        if (row.getId().equals(latest.getId())) {
            return null;
        }
        return ERR_CHAIN_ORDER + latest.getId();
    }

    /** 查询目标对象当前“最新可撤销日志”。 */
    private OperationLog findLatestUndoableLogForTarget(String targetModule, Long targetId) {
        if (!StringUtils.hasText(targetModule) || targetId == null || targetId <= 0) {
            return null;
        }
        LambdaQueryWrapper<OperationLog> qw = new LambdaQueryWrapper<>();
        qw.eq(OperationLog::getTargetModule, targetModule)
                .eq(OperationLog::getTargetId, targetId)
                .eq(OperationLog::getSuccessFlag, 1)
                .isNotNull(OperationLog::getUndoType)
                .isNotNull(OperationLog::getUndoPayloadJson)
                .and(w -> w.isNull(OperationLog::getUndoStatus).or().ne(OperationLog::getUndoStatus, UNDO_STATUS_APPLIED))
                .orderByDesc(OperationLog::getCreatedAt)
                .orderByDesc(OperationLog::getId)
                .last("limit 1");
        return this.getOne(qw, false);
    }

    /** 为分页结果补充链式撤销标记（chainUndoAllowed/chainLatestUndoLogId）。 */
    private void applyChainUndoFlags(List<OperationLog> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Map<String, Long> latestByTarget = new HashMap<>();
        for (OperationLog row : rows) {
            if (row == null) {
                continue;
            }
            if (!isUndoCandidate(row)) {
                row.setChainUndoAllowed(false);
                row.setChainLatestUndoLogId(null);
                continue;
            }
            String targetModule = normalizeText(row.getTargetModule());
            Long targetId = row.getTargetId();
            if (!StringUtils.hasText(targetModule) || targetId == null || targetId <= 0) {
                row.setChainUndoAllowed(true);
                row.setChainLatestUndoLogId(row.getId());
                continue;
            }
            String key = targetModule + "#" + targetId;
            Long latestId = latestByTarget.get(key);
            if (latestId == null) {
                OperationLog latest = findLatestUndoableLogForTarget(targetModule, targetId);
                latestId = latest == null ? null : latest.getId();
                latestByTarget.put(key, latestId);
            }
            row.setChainLatestUndoLogId(latestId);
            row.setChainUndoAllowed(latestId == null || Objects.equals(row.getId(), latestId));
        }
    }

    /** 是否具备撤销候选资格。 */
    private boolean isUndoCandidate(OperationLog row) {
        if (row == null) {
            return false;
        }
        if (row.getSuccessFlag() == null || row.getSuccessFlag() != 1) {
            return false;
        }
        if (!StringUtils.hasText(row.getUndoType()) || !StringUtils.hasText(row.getUndoPayloadJson())) {
            return false;
        }
        String status = normalizeText(row.getUndoStatus()).toLowerCase(Locale.ROOT);
        return !UNDO_STATUS_APPLIED.equals(status);
    }

    /** 撤销“逻辑删除”操作。 */
    private String applyRestoreDeleted(Map<String, Object> payload) {
        String table = normalizeTable(payload.get("table"));
        Long id = toLong(payload.get("id"));
        Integer previousDeleted = toInteger(payload.get("previousDeleted"));
        Integer previousSortOrder = toInteger(payload.get("previousSortOrder"));
        if (!RESTORE_DELETED_TABLES.contains(table) || id == null || id <= 0) {
            return "撤销数据不匹配";
        }
        int deleted = previousDeleted == null ? 0 : (previousDeleted == 1 ? 1 : 0);
        int affected;
        if ("field".equals(table) && previousSortOrder != null) {
            affected = jdbcTemplate.update(
                    "UPDATE `field` SET `deleted`=?, `sort_order`=? WHERE `id`=?",
                    deleted,
                    previousSortOrder,
                    id
            );
        } else {
            affected = jdbcTemplate.update("UPDATE `" + table + "` SET `deleted`=? WHERE `id`=?", deleted, id);
        }
        return affected > 0 ? null : ERR_TARGET_NOT_RESTORED;
    }

    /** 撤销“启停切换”操作。 */
    private String applyRestoreEnabled(Map<String, Object> payload) {
        String table = normalizeTable(payload.get("table"));
        Long id = toLong(payload.get("id"));
        Integer previousEnabled = toInteger(payload.get("previousEnabled"));
        if (!RESTORE_ENABLED_TABLES.contains(table) || id == null || id <= 0 || previousEnabled == null) {
            return "撤销数据不匹配";
        }
        int enabled = previousEnabled == 1 ? 1 : 0;
        if ("user".equals(table) && enabled == 0) {
            Integer superAdmin = jdbcTemplate.queryForObject("SELECT `is_super_admin` FROM `user` WHERE `id`=?", Integer.class, id);
            if (superAdmin != null && superAdmin == 1) {
                return "超级管理员不可禁用";
            }
        }
        int affected = jdbcTemplate.update("UPDATE `" + table + "` SET `enabled`=? WHERE `id`=?", enabled, id);
        return affected > 0 ? null : ERR_TARGET_NOT_RESTORED;
    }

    /** 撤销资源回收站状态（media_asset.recycle_flag/deleted/sort_order）。 */
    private String applyRestoreRecycle(Map<String, Object> payload) {
        String table = normalizeTable(payload.get("table"));
        Long id = toLong(payload.get("id"));
        Integer previousRecycleFlag = toInteger(payload.get("previousRecycleFlag"));
        Integer previousDeleted = toInteger(payload.get("previousDeleted"));
        Integer previousSortOrder = toInteger(payload.get("previousSortOrder"));
        if (!"media_asset".equals(table) || id == null || id <= 0 || previousRecycleFlag == null) {
            return "撤销数据不匹配";
        }
        int recycleFlag = previousRecycleFlag == 1 ? 1 : 0;
        int deleted = previousDeleted == null ? 0 : (previousDeleted == 1 ? 1 : 0);
        if (recycleFlag == 0) {
            int affected;
            if (previousSortOrder != null) {
                affected = jdbcTemplate.update(
                        "UPDATE `media_asset` SET `recycle_flag`=0, `recycled_at`=NULL, `recycled_by_user_id`=NULL, `deleted`=?, `sort_order`=? WHERE `id`=?",
                        deleted,
                        previousSortOrder,
                        id
                );
            } else {
                affected = jdbcTemplate.update(
                        "UPDATE `media_asset` SET `recycle_flag`=0, `recycled_at`=NULL, `recycled_by_user_id`=NULL, `deleted`=? WHERE `id`=?",
                        deleted,
                        id
                );
            }
            return affected > 0 ? null : ERR_TARGET_NOT_RESTORED;
        }
        int affected;
        if (previousSortOrder != null) {
            affected = jdbcTemplate.update(
                    "UPDATE `media_asset` SET `recycle_flag`=1, `deleted`=?, `sort_order`=? WHERE `id`=?",
                    deleted,
                    previousSortOrder,
                    id
            );
        } else {
            affected = jdbcTemplate.update(
                    "UPDATE `media_asset` SET `recycle_flag`=1, `deleted`=? WHERE `id`=?",
                    deleted,
                    id
            );
        }
        return affected > 0 ? null : ERR_TARGET_NOT_RESTORED;
    }

    /** 撤销通用更新操作（仅允许白名单字段恢复）。 */
    private String applyRestoreUpdate(Map<String, Object> payload) {
        String table = normalizeTable(payload.get("table"));
        Long id = toLong(payload.get("id"));
        List<String> columns = RESTORE_UPDATE_COLUMNS.get(table);
        Map<String, Object> previousValues = asObjectMap(payload.get("previousValues"));
        if (id == null || id <= 0 || columns == null || columns.isEmpty() || previousValues.isEmpty()) {
            return "撤销数据不匹配";
        }

        List<String> restoreColumns = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (String column : columns) {
            if (!previousValues.containsKey(column)) {
                continue;
            }
            restoreColumns.add(column);
            args.add(normalizeSqlValue(previousValues.get(column)));
        }
        if (restoreColumns.isEmpty()) {
            return "撤销数据不匹配";
        }

        Map<String, Object> currentValues = Collections.emptyMap();
        if ("crop".equals(table)) {
            currentValues = queryCropSnapshot(id);
        }

        StringBuilder sql = new StringBuilder("UPDATE `")
                .append(table)
                .append("` SET ");
        for (int i = 0; i < restoreColumns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("`").append(restoreColumns.get(i)).append("`=?");
        }
        sql.append(" WHERE `id`=?");
        args.add(id);

        int affected = jdbcTemplate.update(sql.toString(), args.toArray());
        if (affected <= 0) {
            return ERR_TARGET_NOT_RESTORED;
        }
        if ("crop".equals(table)) {
            rollbackCropReferences(currentValues, previousValues);
        }
        return null;
    }

    /** 撤销“当前计划切换”操作。 */
    private String applyRestoreCurrentCycle(Map<String, Object> payload) {
        Long fieldId = toLong(payload.get("fieldId"));
        Long previousCurrentCycleId = toLong(payload.get("previousCurrentCycleId"));
        if (fieldId == null || fieldId <= 0) {
            return "撤销数据不匹配";
        }
        jdbcTemplate.update("UPDATE `field_crop_cycle` SET `is_current`=0 WHERE `field_id`=? AND `deleted`=0", fieldId);
        if (previousCurrentCycleId == null || previousCurrentCycleId <= 0) {
            return null;
        }
        int affected = jdbcTemplate.update(
                "UPDATE `field_crop_cycle` SET `is_current`=1 WHERE `id`=? AND `field_id`=? AND `deleted`=0",
                previousCurrentCycleId,
                fieldId
        );
        return affected > 0 ? null : ERR_TARGET_NOT_RESTORED;
    }

    /** 撤销步骤排序。 */
    private String applyRestoreStepSort(Map<String, Object> payload) {
        Long templateId = toLong(payload.get("templateId"));
        List<Map<String, Object>> previousOrders = asObjectMapList(payload.get("previousOrders"));
        if (templateId == null || templateId <= 0 || previousOrders.isEmpty()) {
            return "撤销数据不匹配";
        }
        int affectedTotal = 0;
        for (Map<String, Object> item : previousOrders) {
            if (item == null || item.isEmpty()) {
                continue;
            }
            Long stepId = toLong(item.get("id"));
            if (stepId == null || stepId <= 0) {
                continue;
            }
            Integer sortOrder = toInteger(firstPresent(item, "sortOrder", "sort_order", "sortorder"));
            int affected = jdbcTemplate.update(
                    "UPDATE `farm_process_step` SET `sort_order`=? WHERE `id`=? AND `template_id`=?",
                    sortOrder == null ? 0 : sortOrder,
                    stepId,
                    templateId
            );
            affectedTotal += affected;
        }
        return affectedTotal > 0 ? null : ERR_TARGET_NOT_RESTORED;
    }

    /** 撤销通用重排（field/crop/media_asset）。 */
    private String applyRestoreReorder(Map<String, Object> payload) {
        String table = normalizeTable(payload.get("table"));
        List<Map<String, Object>> previousOrders = asObjectMapList(payload.get("previousOrders"));
        if (!RESTORE_REORDER_TABLES.contains(table) || previousOrders.isEmpty()) {
            return "撤销数据不匹配";
        }
        int affectedTotal = 0;
        for (Map<String, Object> item : previousOrders) {
            if (item == null || item.isEmpty()) {
                continue;
            }
            Long id = toLong(item.get("id"));
            if (id == null || id <= 0) {
                continue;
            }
            Integer sortOrder = toInteger(firstPresent(item, "sortOrder", "sort_order", "sortorder"));
            int affected;
            if ("field".equals(table)) {
                affected = jdbcTemplate.update(
                        "UPDATE `field` SET `sort_order`=? WHERE `id`=?",
                        sortOrder == null ? 0 : sortOrder,
                        id
                );
            } else if ("crop".equals(table)) {
                affected = jdbcTemplate.update(
                        "UPDATE `crop` SET `sort_order`=? WHERE `id`=?",
                        sortOrder == null ? 0 : sortOrder,
                        id
                );
            } else {
                affected = jdbcTemplate.update(
                        "UPDATE `media_asset` SET `sort_order`=? WHERE `id`=?",
                        sortOrder == null ? 0 : sortOrder,
                        id
                );
            }
            affectedTotal += affected;
        }
        return affectedTotal > 0 ? null : ERR_TARGET_NOT_RESTORED;
    }

    /** 解析 undo payload JSON。 */
    private Map<String, Object> parsePayload(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            return parsed == null ? Collections.emptyMap() : parsed;
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    /** 归一化表名并限制为安全标识符。 */
    private String normalizeTable(Object value) {
        String text = normalizeText(value).toLowerCase(Locale.ROOT);
        if (!text.matches("^[a-z0-9_]+$")) {
            return "";
        }
        return text;
    }

    /** 归一化任意对象文本表示。 */
    private String normalizeText(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    private String trimToLength(String value, int maxLen) {
        String text = normalizeText(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, Math.max(1, maxLen));
    }

    /** 宽松解析 Long。 */
    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        String text = normalizeText(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 宽松解析 Integer。 */
    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = normalizeText(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 将任意对象转换为安全 Map（key 限制为表字段风格）。 */
    private Map<String, Object> asObjectMap(Object value) {
        if (!(value instanceof Map)) {
            return Collections.emptyMap();
        }
        Map<?, ?> raw = (Map<?, ?>) value;
        if (raw.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            String key = normalizeTable(entry.getKey());
            if (!StringUtils.hasText(key)) {
                continue;
            }
            out.put(key, entry.getValue());
        }
        return out;
    }

    /** 将任意对象转换为安全 Map 列表。 */
    private List<Map<String, Object>> asObjectMapList(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<?> raw = (List<?>) value;
        if (raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : raw) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<?, ?> row = (Map<?, ?>) item;
            Map<String, Object> next = new HashMap<>();
            for (Map.Entry<?, ?> entry : row.entrySet()) {
                String key = normalizeText(entry.getKey());
                if (!StringUtils.hasText(key)) {
                    continue;
                }
                String safe = key.trim();
                if (!safe.matches("^[A-Za-z0-9_]+$")) {
                    continue;
                }
                key = safe;
                next.put(key, entry.getValue());
                String lowered = key.toLowerCase(Locale.ROOT);
                if (!next.containsKey(lowered)) {
                    next.put(lowered, entry.getValue());
                }
            }
            if (!next.isEmpty()) {
                out.add(next);
            }
        }
        return out;
    }

    /** 按候选 key 顺序读取第一个非空值。 */
    private Object firstPresent(Map<String, Object> map, String... keys) {
        if (map == null || map.isEmpty() || keys == null || keys.length == 0) {
            return null;
        }
        for (String key : keys) {
            if (!StringUtils.hasText(key)) {
                continue;
            }
            Object value = map.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /** SQL 参数归一化（布尔转 0/1）。 */
    private Object normalizeSqlValue(Object value) {
        if (value instanceof Boolean) {
            return Boolean.TRUE.equals(value) ? 1 : 0;
        }
        return value;
    }

    /** 读取作物恢复前快照（用于回滚引用）。 */
    private Map<String, Object> queryCropSnapshot(Long cropId) {
        if (cropId == null || cropId <= 0) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT `name`,`variety`,`node_type` FROM `crop` WHERE `id`=?",
                    cropId
            );
            return row == null ? Collections.emptyMap() : row;
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    /** 作物恢复后，同步回滚 field/seed_batch 里的作物引用。 */
    private void rollbackCropReferences(Map<String, Object> currentValues, Map<String, Object> previousValues) {
        if (previousValues == null || previousValues.isEmpty()) {
            return;
        }
        String previousNodeType = normalizeNodeType(
                normalizeText(previousValues.get("node_type")),
                normalizeText(currentValues.get("node_type"))
        );
        String currentName = normalizeText(currentValues.get("name"));
        String currentVariety = normalizeText(currentValues.get("variety"));
        String previousName = normalizeText(previousValues.get("name"));
        String previousVariety = normalizeText(previousValues.get("variety"));

        if ("category".equals(previousNodeType)) {
            if (StringUtils.hasText(currentName) && StringUtils.hasText(previousName) && !currentName.equals(previousName)) {
                jdbcTemplate.update("UPDATE `field` SET `crop_type`=? WHERE `crop_type`=?", previousName, currentName);
                jdbcTemplate.update("UPDATE `seed_batch` SET `crop_type`=? WHERE `crop_type`=?", previousName, currentName);
            }
            return;
        }

        if ("variety".equals(previousNodeType)) {
            if (!StringUtils.hasText(currentName)
                    || !StringUtils.hasText(currentVariety)
                    || !StringUtils.hasText(previousName)
                    || !StringUtils.hasText(previousVariety)) {
                return;
            }
            boolean changed = !currentName.equals(previousName) || !currentVariety.equals(previousVariety);
            if (!changed) {
                return;
            }
            jdbcTemplate.update(
                    "UPDATE `field` SET `crop_type`=?, `crop_variety`=? WHERE `crop_type`=? AND `crop_variety`=?",
                    previousName,
                    previousVariety,
                    currentName,
                    currentVariety
            );
            jdbcTemplate.update(
                    "UPDATE `seed_batch` SET `crop_type`=?, `variety_name`=? WHERE `crop_type`=? AND `variety_name`=?",
                    previousName,
                    previousVariety,
                    currentName,
                    currentVariety
            );
        }
    }

    /** 归一化 nodeType，未知时按 variety 处理。 */
    private String normalizeNodeType(String preferred, String fallback) {
        String type = StringUtils.hasText(preferred) ? preferred.trim().toLowerCase(Locale.ROOT) : "";
        if (!StringUtils.hasText(type)) {
            type = StringUtils.hasText(fallback) ? fallback.trim().toLowerCase(Locale.ROOT) : "";
        }
        if ("category".equals(type)) {
            return "category";
        }
        return "variety";
    }

    /** 构建 restore_update 字段白名单。 */
    private static Map<String, List<String>> buildRestoreUpdateColumns() {
        Map<String, List<String>> out = new HashMap<>();
        out.put("field", immutableList(
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
        ));
        out.put("seed_batch", immutableList(
                "batch_code",
                "crop_type",
                "variety_name",
                "production_date",
                "remark",
                "enabled",
                "form_config_id",
                "extra_json"
        ));
        out.put("farm_process_template", immutableList(
                "crop_id",
                "template_name",
                "enabled",
                "is_default"
        ));
        out.put("farm_record", immutableList(
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
        ));
        out.put("seed_quality_test", immutableList(
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
        ));
        out.put("farm_process_step", immutableList(
                "step_name",
                "sort_order",
                "growth_stage",
                "requirement_desc",
                "form_config_id",
                "form_schema"
        ));
        out.put("dynamic_form_config", immutableList(
                "module_key",
                "scene_key",
                "config_name",
                "schema_json",
                "status",
                "version_no",
                "remark"
        ));
        out.put("user", immutableList(
                "status",
                "reject_reason",
                "role_code",
                "can_console"
        ));
        out.put("export_template", immutableList(
                "module_key",
                "template_code",
                "template_name",
                "version_no",
                "status",
                "fields_json",
                "remark"
        ));
        out.put("export_field_dict", immutableList(
                "module_key",
                "field_code",
                "field_name",
                "data_type",
                "description",
                "example_value"
        ));
        out.put("media_asset", immutableList(
                "review_status",
                "reviewed_at",
                "reviewed_by_user_id",
                "review_remark"
        ));
        out.put("media_asset_policy_config", immutableList(
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
        ));
        out.put("record_policy_config", immutableList(
                "edit_window_hours",
                "allow_operator_update",
                "allow_operator_delete",
                "remark"
        ));
        out.put("amap_quota_config", immutableList(
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
        ));
        out.put("field_crop_cycle", immutableList(
                "cycle_name",
                "crops_json",
                "template_ids_json",
                "plan_mode",
                "start_date",
                "end_date",
                "status",
                "is_current"
        ));
        out.put("crop", immutableList(
                "name",
                "variety",
                "node_type",
                "parent_id",
                "image_url",
                "sort_order"
        ));
        out.put("seed_quality_rule", immutableList(
                "fixed_sample_size",
                "default_sample_size",
                "remark"
        ));
        return Collections.unmodifiableMap(out);
    }

    /** 构建不可变列表。 */
    private static List<String> immutableList(String... values) {
        if (values == null || values.length == 0) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(Arrays.asList(values));
    }
}
