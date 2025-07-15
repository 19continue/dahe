package com.dahe.v2.modules.amap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dahe.v2.modules.amap.mapper.AmapApiAuditMapper;
import com.dahe.v2.modules.amap.model.AmapApiAudit;
import com.dahe.v2.modules.amap.model.AmapQuotaConfig;
import com.dahe.v2.modules.amap.service.AmapApiAuditService;
import com.dahe.v2.modules.amap.service.AmapQuotaConfigService;
import com.dahe.v2.modules.amap.service.AmapUsageDailyService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
/**
 * 高德 API 审计服务实现。
 */
public class AmapApiAuditServiceImpl extends ServiceImpl<AmapApiAuditMapper, AmapApiAudit> implements AmapApiAuditService {

    private static final long AUTO_PURGE_INTERVAL_MS = 30L * 60L * 1000L;
    private static final int AUTO_PURGE_BATCH_SIZE = 500;
    private static final int AUTO_PURGE_MAX_BATCHES = 20;

    private final AmapQuotaConfigService amapQuotaConfigService;
    private final AmapUsageDailyService amapUsageDailyService;
    private volatile long nextAutoPurgeAtMs = 0L;

    public AmapApiAuditServiceImpl(
            AmapQuotaConfigService amapQuotaConfigService,
            AmapUsageDailyService amapUsageDailyService
    ) {
        this.amapQuotaConfigService = amapQuotaConfigService;
        this.amapUsageDailyService = amapUsageDailyService;
    }

    @Override
    // 支持按场景、计费类型、来源、成功标记、日期区间过滤，并按创建时间倒序。
    public Page<AmapApiAudit> pageAudits(
            String bizScene,
            String apiType,
            String requestSource,
            Integer successFlag,
            LocalDate startDate,
            LocalDate endDate,
            long page,
            long pageSize
    ) {
        tryAutoPurge();
        Page<AmapApiAudit> out = new Page<>(page, pageSize);
        LambdaQueryWrapper<AmapApiAudit> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(bizScene)) {
            qw.eq(AmapApiAudit::getBizScene, bizScene.trim());
        }
        if (StringUtils.hasText(apiType)) {
            qw.eq(AmapApiAudit::getApiType, normalizeApiType(apiType));
        }
        if (StringUtils.hasText(requestSource)) {
            qw.eq(AmapApiAudit::getRequestSource, requestSource.trim());
        }
        if (successFlag != null) {
            qw.eq(AmapApiAudit::getSuccessFlag, successFlag);
        }
        if (startDate != null) {
            qw.ge(AmapApiAudit::getRecordDate, startDate);
        }
        if (endDate != null) {
            qw.le(AmapApiAudit::getRecordDate, endDate);
        }
        qw.orderByDesc(AmapApiAudit::getCreatedAt).orderByDesc(AmapApiAudit::getId);
        return this.page(out, qw);
    }

    @Override
    public Map<String, Object> buildAuditOverview(LocalDate startDate, LocalDate endDate) {
        tryAutoPurge();
        LocalDate today = LocalDate.now();
        LocalDate end = endDate == null ? today : endDate;
        LocalDate start = startDate == null ? end.minusDays(6) : startDate;
        if (start.isAfter(end)) {
            LocalDate swap = start;
            start = end;
            end = swap;
        }
        List<Map<String, Object>> rows = baseMapper.aggregateByDateAndApiType(start, end);
        Map<String, DailyCounter> byDayAndType = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            LocalDate recordDate = toLocalDate(row.get("recordDate"));
            if (recordDate == null) {
                continue;
            }
            String apiType = normalizeApiType(String.valueOf(row.get("apiType")));
            String key = recordDate.toString() + "|" + apiType;
            DailyCounter counter = byDayAndType.computeIfAbsent(key, it -> new DailyCounter());
            counter.total += toInt(row.get("totalCount"));
            counter.pureCache += toInt(row.get("pureCacheCount"));
            counter.mixed += toInt(row.get("mixedCount"));
            counter.remote += toInt(row.get("remoteCount"));
        }
        List<Map<String, Object>> usageRows = amapUsageDailyService.listDailyUsage(start, end);
        for (Map<String, Object> row : usageRows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            LocalDate recordDate = toLocalDate(row.get("recordDate"));
            if (recordDate == null) {
                continue;
            }
            String apiType = normalizeApiType(String.valueOf(row.get("apiType")));
            String key = recordDate + "|" + apiType;
            DailyCounter counter = byDayAndType.computeIfAbsent(key, it -> new DailyCounter());
            counter.officialUsage += toInt(row.get("remoteCount"));
        }

        SummaryCounter weatherSummary = new SummaryCounter();
        SummaryCounter locationSummary = new SummaryCounter();
        List<Map<String, Object>> trend = new ArrayList<>();
        DateTimeFormatter dayFmt = DateTimeFormatter.ISO_LOCAL_DATE;
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            DailyCounter weather = byDayAndType.getOrDefault(cursor.toString() + "|" + AmapQuotaConfigService.BILLING_WEATHER, new DailyCounter());
            DailyCounter location = byDayAndType.getOrDefault(cursor.toString() + "|" + AmapQuotaConfigService.BILLING_LOCATION, new DailyCounter());
            weatherSummary.collect(weather);
            locationSummary.collect(location);

            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", dayFmt.format(cursor));
            day.put("weather", weather.toMap());
            day.put("location", location.toMap());
            trend.add(day);
            cursor = cursor.plusDays(1);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("startDate", start);
        out.put("endDate", end);
        out.put("weatherSummary", weatherSummary.toMap());
        out.put("locationSummary", locationSummary.toMap());
        out.put("trend", trend);
        return out;
    }

    @Override
    public Map<String, Object> purgeByRetentionDays(int retainDays, int batchSize, int maxBatches) {
        int safeRetainDays = Math.max(7, retainDays);
        int safeBatchSize = Math.max(100, Math.min(5000, batchSize));
        int safeMaxBatches = Math.max(1, Math.min(200, maxBatches));
        LocalDate cutoffDate = LocalDate.now().minusDays(safeRetainDays);
        int totalDeleted = 0;
        int batches = 0;
        for (int i = 0; i < safeMaxBatches; i++) {
            int affected = baseMapper.purgeBeforeDate(cutoffDate, safeBatchSize);
            if (affected <= 0) {
                break;
            }
            batches += 1;
            totalDeleted += affected;
            if (affected < safeBatchSize) {
                break;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("retainDays", safeRetainDays);
        out.put("cutoffDate", cutoffDate);
        out.put("deletedRows", totalDeleted);
        out.put("batches", batches);
        out.put("batchSize", safeBatchSize);
        return out;
    }

    private void tryAutoPurge() {
        long now = System.currentTimeMillis();
        if (now < nextAutoPurgeAtMs) {
            return;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            if (now < nextAutoPurgeAtMs) {
                return;
            }
            nextAutoPurgeAtMs = now + AUTO_PURGE_INTERVAL_MS;
            try {
                AmapQuotaConfig quota = amapQuotaConfigService.getOrInitToday();
                boolean autoEnabled = quota != null && quota.getAuditAutoPurgeEnabled() != null && quota.getAuditAutoPurgeEnabled() == 1;
                if (!autoEnabled) {
                    return;
                }
                int retainDays = quota.getAuditRetainDays() == null ? 90 : quota.getAuditRetainDays();
                purgeByRetentionDays(retainDays, AUTO_PURGE_BATCH_SIZE, AUTO_PURGE_MAX_BATCHES);
            } catch (Exception ignored) {
            }
        }
    }

    private String normalizeApiType(String apiType) {
        String type = StringUtils.hasText(apiType) ? apiType.trim().toLowerCase(Locale.ROOT) : "";
        if (AmapQuotaConfigService.BILLING_WEATHER.equals(type)) {
            return AmapQuotaConfigService.BILLING_WEATHER;
        }
        if (type.contains("weather")) {
            return AmapQuotaConfigService.BILLING_WEATHER;
        }
        if (type.contains("geo") || type.contains("location") || type.contains("city") || type.contains("district")) {
            return AmapQuotaConfigService.BILLING_LOCATION;
        }
        return AmapQuotaConfigService.BILLING_LOCATION;
    }

    private int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return Math.max(0, ((Number) value).intValue());
        }
        try {
            return Math.max(0, Integer.parseInt(String.valueOf(value).trim()));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static final class DailyCounter {
        private int total;
        private int pureCache;
        private int mixed;
        private int remote;
        private int officialUsage;

        private int cacheHits() {
            return Math.max(0, pureCache + mixed);
        }

        private double cacheHitRate() {
            return total <= 0 ? 0D : round2((cacheHits() * 100.0D) / total);
        }

        private static double round2(double value) {
            return Math.round(value * 100.0D) / 100.0D;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("totalCount", total);
            out.put("remoteCount", remote);
            out.put("pureCacheCount", pureCache);
            out.put("mixedCount", mixed);
            out.put("cacheHitCount", cacheHits());
            out.put("cacheHitRate", cacheHitRate());
            out.put("officialUsageCount", officialUsage);
            return out;
        }
    }

    private static final class SummaryCounter {
        private int total;
        private int remote;
        private int pureCache;
        private int mixed;
        private int officialUsage;

        private void collect(DailyCounter daily) {
            if (daily == null) {
                return;
            }
            total += Math.max(0, daily.total);
            remote += Math.max(0, daily.remote);
            pureCache += Math.max(0, daily.pureCache);
            mixed += Math.max(0, daily.mixed);
            officialUsage += Math.max(0, daily.officialUsage);
        }

        private Map<String, Object> toMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            int cacheHits = Math.max(0, pureCache + mixed);
            double hitRate = total <= 0 ? 0D : Math.round((cacheHits * 10000.0D) / total) / 100.0D;
            out.put("totalCount", total);
            out.put("remoteCount", remote);
            out.put("pureCacheCount", pureCache);
            out.put("mixedCount", mixed);
            out.put("cacheHitCount", cacheHits);
            out.put("cacheHitRate", hitRate);
            out.put("officialUsageCount", officialUsage);
            return out;
        }
    }
}
