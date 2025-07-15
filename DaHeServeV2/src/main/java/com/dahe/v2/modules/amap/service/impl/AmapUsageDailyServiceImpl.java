package com.dahe.v2.modules.amap.service.impl;

import com.dahe.v2.modules.amap.service.AmapQuotaConfigService;
import com.dahe.v2.modules.amap.service.AmapUsageDailyService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 高德真实远程用量服务实现。
 */
@Service
public class AmapUsageDailyServiceImpl implements AmapUsageDailyService {

    private final JdbcTemplate jdbcTemplate;

    public AmapUsageDailyServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void recordRemoteUsage(LocalDate recordDate, String apiType, int delta) {
        LocalDate usageDate = recordDate == null ? LocalDate.now() : recordDate;
        LocalDate usageMonth = usageDate.with(TemporalAdjusters.firstDayOfMonth());
        String normalizedType = normalizeApiType(apiType);
        int safeDelta = delta <= 0 ? 1 : delta;
        jdbcTemplate.update(
                "INSERT INTO `amap_usage_daily` (`id`,`record_date`,`api_type`,`remote_count`,`created_at`,`updated_at`) " +
                        "VALUES (UUID_SHORT(), ?, ?, ?, NOW(), NOW()) " +
                        "ON DUPLICATE KEY UPDATE `remote_count`=`remote_count` + VALUES(`remote_count`), `updated_at`=NOW()",
                usageDate,
                normalizedType,
                safeDelta
        );
        jdbcTemplate.update(
                "INSERT INTO `amap_usage_monthly` (`id`,`record_month`,`api_type`,`remote_count`,`warning_sent`,`warning_sent_at`,`created_at`,`updated_at`) " +
                        "VALUES (UUID_SHORT(), ?, ?, ?, 0, NULL, NOW(), NOW()) " +
                        "ON DUPLICATE KEY UPDATE `remote_count`=`remote_count` + VALUES(`remote_count`), `updated_at`=NOW()",
                usageMonth,
                normalizedType,
                safeDelta
        );
        jdbcTemplate.update(
                "DELETE FROM `amap_usage_daily` WHERE `record_date` < ?",
                LocalDate.now().minusDays(6)
        );
    }

    @Override
    public List<Map<String, Object>> listDailyUsage(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            return Collections.emptyList();
        }
        LocalDate start = startDate.isAfter(endDate) ? endDate : startDate;
        LocalDate end = startDate.isAfter(endDate) ? startDate : endDate;
        return jdbcTemplate.queryForList(
                "SELECT `record_date` AS recordDate, `api_type` AS apiType, `remote_count` AS remoteCount " +
                        "FROM `amap_usage_daily` " +
                        "WHERE `record_date` BETWEEN ? AND ? " +
                        "ORDER BY `record_date` ASC, `api_type` ASC",
                start,
                end
        );
    }

    @Override
    public List<Map<String, Object>> listMonthlyUsage(LocalDate startMonth, LocalDate endMonth) {
        if (startMonth == null || endMonth == null) {
            return Collections.emptyList();
        }
        LocalDate start = normalizeMonth(startMonth);
        LocalDate end = normalizeMonth(endMonth);
        if (start.isAfter(end)) {
            LocalDate temp = start;
            start = end;
            end = temp;
        }
        return jdbcTemplate.queryForList(
                "SELECT `record_month` AS recordMonth, `api_type` AS apiType, `remote_count` AS remoteCount, `warning_sent` AS warningSent " +
                        "FROM `amap_usage_monthly` " +
                        "WHERE `record_month` BETWEEN ? AND ? " +
                        "ORDER BY `record_month` ASC, `api_type` ASC",
                start,
                end
        );
    }

    @Override
    public Map<String, Integer> getMonthlyUsage(LocalDate monthDate) {
        LocalDate month = normalizeMonth(monthDate == null ? LocalDate.now() : monthDate);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT `api_type` AS apiType, `remote_count` AS remoteCount FROM `amap_usage_monthly` WHERE `record_month`=?",
                month
        );
        return aggregateUsageRows(rows);
    }

    @Override
    public Map<String, Integer> getTotalUsage() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT `api_type` AS apiType, SUM(`remote_count`) AS remoteCount FROM `amap_usage_monthly` GROUP BY `api_type`"
        );
        return aggregateUsageRows(rows);
    }

    @Override
    @Transactional
    public boolean markMonthlyWarningSentIfAbsent(LocalDate monthDate, String apiType) {
        LocalDate month = normalizeMonth(monthDate == null ? LocalDate.now() : monthDate);
        String normalizedType = normalizeApiType(apiType);
        return jdbcTemplate.update(
                "UPDATE `amap_usage_monthly` SET `warning_sent`=1, `warning_sent_at`=NOW(), `updated_at`=NOW() " +
                        "WHERE `record_month`=? AND `api_type`=? AND IFNULL(`warning_sent`,0)=0",
                month,
                normalizedType
        ) > 0;
    }

    private Map<String, Integer> aggregateUsageRows(List<Map<String, Object>> rows) {
        Map<String, Integer> out = new HashMap<>();
        out.put(AmapQuotaConfigService.BILLING_WEATHER, 0);
        out.put(AmapQuotaConfigService.BILLING_LOCATION, 0);
        if (rows == null || rows.isEmpty()) {
            return out;
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            String type = normalizeApiType(String.valueOf(row.get("apiType") == null ? "" : row.get("apiType")));
            int count = toInt(row.get("remoteCount"));
            out.put(type, out.getOrDefault(type, 0) + Math.max(0, count));
        }
        return out;
    }

    private int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private LocalDate normalizeMonth(LocalDate value) {
        LocalDate source = value == null ? LocalDate.now() : value;
        return source.with(TemporalAdjusters.firstDayOfMonth());
    }

    private String normalizeApiType(String apiType) {
        String value = StringUtils.hasText(apiType) ? apiType.trim().toLowerCase(Locale.ROOT) : "";
        if (AmapQuotaConfigService.BILLING_WEATHER.equals(value)) {
            return AmapQuotaConfigService.BILLING_WEATHER;
        }
        return AmapQuotaConfigService.BILLING_LOCATION;
    }
}
