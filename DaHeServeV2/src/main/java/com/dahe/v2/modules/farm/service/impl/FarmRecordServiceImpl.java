package com.dahe.v2.modules.farm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dahe.v2.modules.farm.mapper.FarmRecordMapper;
import com.dahe.v2.modules.farm.model.FarmRecord;
import com.dahe.v2.modules.farm.model.FarmRecordGroupStats;
import com.dahe.v2.modules.farm.service.FarmRecordService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 农事记录服务实现。 */
@Service
public class FarmRecordServiceImpl extends ServiceImpl<FarmRecordMapper, FarmRecord> implements FarmRecordService {

    @Override
    /** 分页查询农事记录。 */
    public Page<FarmRecord> pageRecords(
            Long fieldId,
            Long cycleId,
            Long operatorUserId,
            String operatorName,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Set<Long> fieldIdScope,
            long page,
            long pageSize
    ) {
        Page<FarmRecord> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<FarmRecord> qw = new LambdaQueryWrapper<>();
        if (fieldIdScope != null && !fieldIdScope.isEmpty()) {
            qw.in(FarmRecord::getFieldId, fieldIdScope);
        }
        if (fieldId != null) {
            qw.eq(FarmRecord::getFieldId, fieldId);
        }
        if (cycleId != null) {
            qw.eq(FarmRecord::getCycleId, cycleId);
        }
        String normalizedOperatorName = StringUtils.hasText(operatorName) ? operatorName.trim() : null;
        if (operatorUserId != null || StringUtils.hasText(normalizedOperatorName)) {
            qw.and(wrapper -> {
                boolean appended = false;
                if (operatorUserId != null) {
                    wrapper.eq(FarmRecord::getOperatorUserId, operatorUserId);
                    appended = true;
                }
                if (StringUtils.hasText(normalizedOperatorName)) {
                    if (appended) {
                        wrapper.or();
                    }
                    wrapper.eq(FarmRecord::getOperatorName, normalizedOperatorName);
                }
            });
        }
        if (startDate != null) {
            qw.ge(FarmRecord::getWorkDate, startDate);
        }
        if (endDate != null) {
            qw.le(FarmRecord::getWorkDate, endDate);
        }
        qw.orderByDesc(FarmRecord::getWorkDate);
        return this.page(p, qw);
    }

    @Override
    /** 查询田块最近 N 条记录。 */
    public List<FarmRecord> listRecentByField(Long fieldId, Long cycleId, Set<Long> stepIdScope, int limit) {
        if (fieldId == null || limit <= 0) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<FarmRecord> qw = new LambdaQueryWrapper<>();
        qw.eq(FarmRecord::getFieldId, fieldId);
        if (cycleId != null) {
            qw.eq(FarmRecord::getCycleId, cycleId);
        }
        if (stepIdScope != null && !stepIdScope.isEmpty()) {
            qw.in(FarmRecord::getStepId, stepIdScope);
        }
        qw
                .orderByDesc(FarmRecord::getWorkDate)
                .last("limit " + limit);
        return this.list(qw);
    }

    @Override
    /** 按田块与计划维度聚合统计记录。 */
    public List<FarmRecordGroupStats> listGroupedRecords(
            Long fieldId,
            Long cycleId,
            Long operatorUserId,
            String operatorName,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Set<Long> fieldIdScope,
            int maxGroups
    ) {
        if (fieldIdScope != null && fieldIdScope.isEmpty()) {
            return Collections.emptyList();
        }
        int safeMaxGroups = Math.max(1, Math.min(maxGroups <= 0 ? 200 : maxGroups, 1000));

        QueryWrapper<FarmRecord> groupedQw = new QueryWrapper<>();
        if (fieldIdScope != null) {
            groupedQw.in("field_id", fieldIdScope);
        }
        if (fieldId != null) {
            groupedQw.eq("field_id", fieldId);
        }
        if (cycleId != null) {
            groupedQw.eq("cycle_id", cycleId);
        }
        String normalizedOperatorName = StringUtils.hasText(operatorName) ? operatorName.trim() : null;
        if (operatorUserId != null || StringUtils.hasText(normalizedOperatorName)) {
            groupedQw.and(wrapper -> {
                boolean appended = false;
                if (operatorUserId != null) {
                    wrapper.eq("operator_user_id", operatorUserId);
                    appended = true;
                }
                if (StringUtils.hasText(normalizedOperatorName)) {
                    if (appended) {
                        wrapper.or();
                    }
                    wrapper.eq("operator_name", normalizedOperatorName);
                }
            });
        }
        if (startDate != null) {
            groupedQw.ge("work_date", startDate);
        }
        if (endDate != null) {
            groupedQw.le("work_date", endDate);
        }
        groupedQw
                .select(
                        "field_id AS fieldId",
                        "cycle_id AS cycleId",
                        "COUNT(1) AS recordCount",
                        "MAX(work_date) AS latestWorkDate",
                        "MIN(work_date) AS earliestWorkDate"
                )
                .groupBy("field_id", "cycle_id")
                .orderByDesc("latestWorkDate")
                .last("LIMIT " + safeMaxGroups);

        List<Map<String, Object>> groupedRows = this.baseMapper.selectMaps(groupedQw);
        if (groupedRows == null || groupedRows.isEmpty()) {
            return Collections.emptyList();
        }

        List<FarmRecordGroupStats> out = new ArrayList<>(groupedRows.size());
        for (Map<String, Object> row : groupedRows) {
            if (row == null) {
                continue;
            }
            Long groupedFieldId = toNullableLong(row.get("fieldId"));
            Long groupedCycleId = toNullableLong(row.get("cycleId"));
            if (groupedFieldId == null) {
                continue;
            }
            FarmRecordGroupStats stats = new FarmRecordGroupStats();
            stats.setFieldId(groupedFieldId);
            stats.setCycleId(groupedCycleId);
            stats.setRecordCount(toNullableLong(row.get("recordCount")));
            stats.setLatestWorkDate(toNullableLocalDateTime(row.get("latestWorkDate")));
            stats.setEarliestWorkDate(toNullableLocalDateTime(row.get("earliestWorkDate")));

            FarmRecord latest = findLatestRecordInGroup(
                    groupedFieldId,
                    groupedCycleId,
                    operatorUserId,
                    normalizedOperatorName,
                    startDate,
                    endDate,
                    fieldIdScope
            );
            if (latest != null) {
                stats.setLatestRecordId(latest.getId());
                stats.setLatestStepId(latest.getStepId());
                stats.setLatestOperatorName(latest.getOperatorName());
                stats.setLatestWeather(latest.getWeather());
                stats.setLatestWeatherLocation(latest.getWeatherLocation());
                stats.setLatestHumidity(latest.getHumidity());
                stats.setLatestWindDirection(latest.getWindDirection());
                stats.setLatestWindPower(latest.getWindPower());
                stats.setLatestWeatherReportTime(latest.getWeatherReportTime());
                stats.setLatestNotes(latest.getNotes());
                stats.setLatestExtraJson(latest.getExtraJson());
                if (stats.getLatestWorkDate() == null) {
                    stats.setLatestWorkDate(latest.getWorkDate());
                }
                if (stats.getEarliestWorkDate() == null) {
                    stats.setEarliestWorkDate(latest.getWorkDate());
                }
            }
            out.add(stats);
        }
        return out;
    }

    /** 查询某分组最新一条记录，用于补齐统计明细字段。 */
    private FarmRecord findLatestRecordInGroup(
            Long fieldId,
            Long cycleId,
            Long operatorUserId,
            String operatorName,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Set<Long> fieldIdScope
    ) {
        if (fieldId == null) {
            return null;
        }
        LambdaQueryWrapper<FarmRecord> latestQw = new LambdaQueryWrapper<>();
        if (fieldIdScope != null) {
            latestQw.in(FarmRecord::getFieldId, fieldIdScope);
        }
        latestQw.eq(FarmRecord::getFieldId, fieldId);
        if (cycleId == null) {
            latestQw.isNull(FarmRecord::getCycleId);
        } else {
            latestQw.eq(FarmRecord::getCycleId, cycleId);
        }
        String normalizedOperatorName = StringUtils.hasText(operatorName) ? operatorName.trim() : null;
        if (operatorUserId != null || StringUtils.hasText(normalizedOperatorName)) {
            latestQw.and(wrapper -> {
                boolean appended = false;
                if (operatorUserId != null) {
                    wrapper.eq(FarmRecord::getOperatorUserId, operatorUserId);
                    appended = true;
                }
                if (StringUtils.hasText(normalizedOperatorName)) {
                    if (appended) {
                        wrapper.or();
                    }
                    wrapper.eq(FarmRecord::getOperatorName, normalizedOperatorName);
                }
            });
        }
        if (startDate != null) {
            latestQw.ge(FarmRecord::getWorkDate, startDate);
        }
        if (endDate != null) {
            latestQw.le(FarmRecord::getWorkDate, endDate);
        }
        latestQw
                .orderByDesc(FarmRecord::getWorkDate)
                .orderByDesc(FarmRecord::getId)
                .last("limit 1");
        return this.getOne(latestQw, false);
    }

    /** 安全转换 Long。 */
    private Long toNullableLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 安全转换 LocalDateTime。 */
    private LocalDateTime toNullableLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof LocalDate) {
            return ((LocalDate) value).atStartOfDay();
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime();
        }
        if (value instanceof java.util.Date) {
            Instant instant = ((java.util.Date) value).toInstant();
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        String normalized = text.replace('T', ' ');
        List<String> patterns = Arrays.asList(
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss.S",
                "yyyy-MM-dd HH:mm:ss.SS",
                "yyyy-MM-dd HH:mm:ss.SSS"
        );
        for (String pattern : patterns) {
            try {
                return LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern(pattern));
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }
}

