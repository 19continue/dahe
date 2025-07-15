package com.dahe.v2.modules.amap.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.amap.model.AmapApiAudit;
import com.dahe.v2.modules.amap.model.AmapQuotaConfig;
import com.dahe.v2.modules.amap.service.AmapApiAuditService;
import com.dahe.v2.modules.amap.service.AmapHealthCheckService;
import com.dahe.v2.modules.amap.service.AmapOpenService;
import com.dahe.v2.modules.amap.service.AmapQuotaConfigService;
import com.dahe.v2.modules.amap.service.AmapUsageDailyService;
import com.dahe.v2.modules.amap.service.impl.AmapOpenApplicationService;
import com.dahe.v2.modules.auth.support.AdminMenuCode;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/admin/amap")
@AdminMenuCode("/amap-audit")
@Validated
public class AmapAdminController {

    private final AmapQuotaConfigService amapQuotaConfigService;
    private final AmapApiAuditService amapApiAuditService;
    private final AmapOpenService amapOpenService;
    private final AmapOpenApplicationService amapOpenApplicationService;
    private final AmapHealthCheckService amapHealthCheckService;
    private final AmapUsageDailyService amapUsageDailyService;

    public AmapAdminController(
            AmapQuotaConfigService amapQuotaConfigService,
            AmapApiAuditService amapApiAuditService,
            AmapOpenService amapOpenService,
            AmapOpenApplicationService amapOpenApplicationService,
            AmapHealthCheckService amapHealthCheckService,
            AmapUsageDailyService amapUsageDailyService
    ) {
        this.amapQuotaConfigService = amapQuotaConfigService;
        this.amapApiAuditService = amapApiAuditService;
        this.amapOpenService = amapOpenService;
        this.amapOpenApplicationService = amapOpenApplicationService;
        this.amapHealthCheckService = amapHealthCheckService;
        this.amapUsageDailyService = amapUsageDailyService;
    }

    @GetMapping("/quota")
    public Result<Map<String, Object>> quota() {
        try {
            return Result.success(toQuotaMap(amapQuotaConfigService.getOrInitToday()));
        } catch (Exception e) {
            return tableOrServerError(e);
        }
    }

    @PutMapping("/quota")
    public Result<Map<String, Object>> updateQuota(@RequestBody @Validated QuotaReq req) {
        try {
            AmapQuotaConfig row = amapQuotaConfigService.updateConfig(
                    req.getAlertThreshold(),
                    req.getAccountName(),
                    req.getAccountLogin(),
                    req.getAppName(),
                    req.getConsoleUrl(),
                    req.getKeyConsoleUrl(),
                    req.getAppKey(),
                    req.resolveWeatherMonthlyLimit(),
                    req.resolveLocationMonthlyLimit(),
                    req.getQpsLimit(),
                    req.getCacheRedisEnabled(),
                    req.getCacheRedisKeyPrefix(),
                    req.getCacheRegionTtlMinutes(),
                    req.getCacheRegionStaleMinutes(),
                    req.getCacheWeatherTtlMinutes(),
                    req.getCacheLocalRegionMaxEntries(),
                    req.getCacheLocalWeatherMaxEntries(),
                    req.getAuditAutoPurgeEnabled(),
                    req.getAuditRetainDays(),
                    req.getRemark()
            );
            amapOpenService.refreshRuntimeConfig();
            return Result.success(toQuotaMap(row));
        } catch (Exception e) {
            return tableOrServerError(e);
        }
    }

    @PostMapping("/quota/recharge")
    public Result<Map<String, Object>> rechargeQuota(@RequestBody @Validated RechargeReq req) {
        try {
            int amount = req.getAmount() == null ? 0 : req.getAmount();
            AmapQuotaConfig row = amapQuotaConfigService.recharge(req.getApiType(), amount, req.getRemark());
            return Result.success(toQuotaMap(row));
        } catch (Exception e) {
            return tableOrServerError(e);
        }
    }

    @PostMapping("/key/verify")
    public Result<Map<String, Object>> verifyKey(@RequestBody(required = false) VerifyKeyReq req) {
        try {
            String requestedKey = safeText(req == null ? null : req.getAppKey());
            if (StringUtils.hasText(requestedKey)) {
                AmapQuotaConfig current = amapQuotaConfigService.getOrInitToday();
                amapQuotaConfigService.updateConfig(
                        current.getAlertThreshold(),
                        current.getAccountName(),
                        current.getAccountLogin(),
                        current.getAppName(),
                        current.getConsoleUrl(),
                        current.getKeyConsoleUrl(),
                        requestedKey,
                        current.getWeatherMonthlyLimit(),
                        current.getLocationMonthlyLimit(),
                        current.getQpsLimit(),
                        current.getCacheRedisEnabled() != null && current.getCacheRedisEnabled() == 1,
                        current.getCacheRedisKeyPrefix(),
                        current.getCacheRegionTtlMinutes(),
                        current.getCacheRegionStaleMinutes(),
                        current.getCacheWeatherTtlMinutes(),
                        current.getCacheLocalRegionMaxEntries(),
                        current.getCacheLocalWeatherMaxEntries(),
                        current.getAuditAutoPurgeEnabled() != null && current.getAuditAutoPurgeEnabled() == 1,
                        current.getAuditRetainDays(),
                        current.getRemark()
                );
                amapOpenService.refreshRuntimeConfig();
            }
            String appKey = amapQuotaConfigService.resolveBoundAppKey();
            if (!StringUtils.hasText(appKey)) {
                return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "尚未配置高德 Key");
            }

            boolean valid;
            String message;
            try {
                amapOpenService.verifyKey(appKey);
                valid = true;
                message = "Key 校验通过";
                amapQuotaConfigService.updateKeyCheck("valid", message);
            } catch (Exception e) {
                valid = false;
                message = shortenError(e);
                amapQuotaConfigService.updateKeyCheck("invalid", message);
            }

            Map<String, Object> out = toQuotaMap(amapQuotaConfigService.getOrInitToday());
            out.put("valid", valid);
            out.put("verifyMessage", message);
            return Result.success(out);
        } catch (Exception e) {
            return tableOrServerError(e);
        }
    }

    @PostMapping("/health/check")
    public Result<Map<String, Object>> healthCheck(@RequestBody(required = false) HealthCheckReq req) {
        try {
            String appKey = amapQuotaConfigService.resolveBoundAppKey();
            Map<String, Object> out = amapHealthCheckService.runHealthCheck(
                    appKey,
                    req == null ? null : req.getLongitude(),
                    req == null ? null : req.getLatitude()
            );
            out.put("quota", toQuotaMap(amapQuotaConfigService.getOrInitToday()));
            return Result.success(out);
        } catch (Exception e) {
            return tableOrServerError(e);
        }
    }

    @PostMapping("/cache/clear")
    public Result<Map<String, Object>> clearCache() {
        try {
            return Result.success(amapOpenService.clearRuntimeCache());
        } catch (Exception e) {
            return tableOrServerError(e);
        }
    }

    @GetMapping("/regions/provinces")
    @AdminMenuCode("/field-manage")
    public Result<List<AmapOpenApplicationService.RegionOptionItem>> provinceOptions(
            HttpServletRequest request,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        return amapOpenApplicationService.provinceOptions(request, keyword, limit);
    }

    @GetMapping("/regions/cities")
    @AdminMenuCode("/field-manage")
    public Result<List<AmapOpenApplicationService.RegionOptionItem>> cityOptions(
            HttpServletRequest request,
            @RequestParam String province,
            @RequestParam(defaultValue = "80") @Min(1) @Max(200) int limit
    ) {
        return amapOpenApplicationService.cityOptions(request, province, limit);
    }

    @GetMapping("/regions/districts")
    @AdminMenuCode("/field-manage")
    public Result<List<AmapOpenApplicationService.RegionOptionItem>> districtOptions(
            HttpServletRequest request,
            @RequestParam String city,
            @RequestParam(defaultValue = "120") @Min(1) @Max(300) int limit
    ) {
        return amapOpenApplicationService.districtOptions(request, city, limit);
    }

    @GetMapping("/regions/townships")
    @AdminMenuCode("/field-manage")
    public Result<List<AmapOpenApplicationService.RegionOptionItem>> townshipOptions(
            HttpServletRequest request,
            @RequestParam String district,
            @RequestParam(defaultValue = "200") @Min(1) @Max(500) int limit
    ) {
        return amapOpenApplicationService.townshipOptions(request, district, limit);
    }

    @GetMapping("/audits")
    public Result<Page<AmapApiAudit>> audits(
            @RequestParam(required = false) String bizScene,
            @RequestParam(required = false) String apiType,
            @RequestParam(required = false) String requestSource,
            @RequestParam(required = false) Integer successFlag,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) long pageSize
    ) {
        try {
            return Result.success(amapApiAuditService.pageAudits(bizScene, apiType, requestSource, successFlag, startDate, endDate, page, pageSize));
        } catch (Exception e) {
            return tableOrServerError(e);
        }
    }

    @GetMapping("/audits/overview")
    public Result<Map<String, Object>> auditOverview(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate
    ) {
        try {
            return Result.success(amapApiAuditService.buildAuditOverview(startDate, endDate));
        } catch (Exception e) {
            return tableOrServerError(e);
        }
    }

    @PostMapping("/audits/purge")
    public Result<Map<String, Object>> purgeAudits(@RequestBody(required = false) PurgeReq req) {
        int retainDays = req == null || req.getRetainDays() == null ? 90 : req.getRetainDays();
        int batchSize = req == null || req.getBatchSize() == null ? 500 : req.getBatchSize();
        int maxBatches = req == null || req.getMaxBatches() == null ? 20 : req.getMaxBatches();
        try {
            return Result.success(amapApiAuditService.purgeByRetentionDays(retainDays, batchSize, maxBatches));
        } catch (Exception e) {
            return tableOrServerError(e);
        }
    }

    @DeleteMapping("/audits/{id}")
    public Result<Boolean> deleteAudit(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "日志ID无效");
        }
        try {
            boolean removed = amapApiAuditService.removeById(id);
            if (!removed) {
                return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
            }
            return Result.success(Boolean.TRUE);
        } catch (Exception e) {
            return tableOrServerError(e);
        }
    }

    @PostMapping("/audits/batch-delete")
    public Result<Map<String, Object>> batchDeleteAudits(@RequestBody(required = false) BatchDeleteReq req) {
        LinkedHashSet<Long> idSet = new LinkedHashSet<>();
        if (req != null && req.getIds() != null) {
            for (Long id : req.getIds()) {
                if (id != null && id > 0) {
                    idSet.add(id);
                }
            }
        }
        if (idSet.isEmpty()) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "删除ID列表不能为空");
        }

        int successCount = 0;
        List<Long> failedIds = new ArrayList<>();
        try {
            for (Long id : idSet) {
                boolean removed = amapApiAuditService.removeById(id);
                if (removed) {
                    successCount += 1;
                } else {
                    failedIds.add(id);
                }
            }
            if (successCount <= 0) {
                return Result.failure(ErrorCode.NOT_FOUND.getCode(), "未删除任何审计记录");
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("requested", idSet.size());
            out.put("success", successCount);
            out.put("failedIds", failedIds);
            return Result.success(out);
        } catch (Exception e) {
            return tableOrServerError(e);
        }
    }

    private Map<String, Object> toQuotaMap(AmapQuotaConfig row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("recordDate", row == null ? null : row.getRecordDate());
        LocalDate currentMonth = (row == null || row.getRecordDate() == null ? LocalDate.now() : row.getRecordDate()).withDayOfMonth(1);
        Map<String, Integer> monthlyUsage = amapUsageDailyService.getMonthlyUsage(currentMonth);
        Map<String, Integer> totalUsage = amapUsageDailyService.getTotalUsage();
        int usedCount = row == null || row.getUsedCount() == null ? 0 : row.getUsedCount();
        int dailyLimit = row == null || row.getDailyLimit() == null ? 0 : row.getDailyLimit();
        int alertThreshold = row == null || row.getAlertThreshold() == null ? 80 : row.getAlertThreshold();
        int usageRate = dailyLimit <= 0 ? 0 : (int) Math.round((usedCount * 100.0) / dailyLimit);
        out.put("usedCount", usedCount);
        out.put("dailyLimit", dailyLimit);
        out.put("alertThreshold", alertThreshold);
        out.put("usageRate", usageRate);
        out.put("accountName", row == null ? null : row.getAccountName());
        out.put("accountLogin", row == null ? null : row.getAccountLogin());
        out.put("appName", row == null ? null : row.getAppName());
        out.put("consoleUrl", row == null ? "https://console.amap.com/dev" : firstNonEmpty(row.getConsoleUrl(), "https://console.amap.com/dev"));
        out.put("keyConsoleUrl", row == null ? "https://console.amap.com/dev/key/app" : firstNonEmpty(row.getKeyConsoleUrl(), "https://console.amap.com/dev/key/app"));
        out.put("appKeyMasked", maskAppKey(row == null ? null : row.getAppKey()));
        out.put("appKeyConfigured", StringUtils.hasText(row == null ? null : row.getAppKey()));
        out.put("appKeyStatus", firstNonEmpty(row == null ? null : safeText(row.getAppKeyStatus()), "unknown"));
        out.put("appKeyBoundAt", row == null ? null : row.getAppKeyBoundAt());
        out.put("appKeyLastCheckAt", row == null ? null : row.getAppKeyLastCheckAt());
        out.put("appKeyLastCheckMessage", row == null ? null : row.getAppKeyLastCheckMessage());
        out.put("lastHealthCheckAt", row == null ? null : row.getLastHealthCheckAt());
        out.put("lastHealthCheckMessage", row == null ? null : row.getLastHealthCheckMessage());
        out.put("supportsAutoKeySync", false);
        out.put("supportsAutoQuotaRecharge", false);
        out.put("capabilityNote", "高德控制台暂无官方 Key 同步与自动充值开放接口");

        int weatherMonthlyLimit = row == null
                ? 0
                : nvl(row.getWeatherMonthlyLimit() == null ? row.getWeatherDailyLimit() : row.getWeatherMonthlyLimit());
        int weatherUsedCount = Math.max(0, monthlyUsage.getOrDefault(AmapQuotaConfigService.BILLING_WEATHER, nvl(row == null ? null : row.getWeatherUsedCount())));
        int locationMonthlyLimit = row == null
                ? 0
                : nvl(row.getLocationMonthlyLimit() == null ? row.getLocationDailyLimit() : row.getLocationMonthlyLimit());
        int locationUsedCount = Math.max(0, monthlyUsage.getOrDefault(AmapQuotaConfigService.BILLING_LOCATION, nvl(row == null ? null : row.getLocationUsedCount())));
        int locationRemain = calcRemain(locationMonthlyLimit, locationUsedCount);
        int weatherTotalUsed = Math.max(0, totalUsage.getOrDefault(AmapQuotaConfigService.BILLING_WEATHER, 0));
        int locationTotalUsed = Math.max(0, totalUsage.getOrDefault(AmapQuotaConfigService.BILLING_LOCATION, 0));
        out.put("currentMonth", currentMonth);
        out.put("weatherMonthlyLimit", weatherMonthlyLimit);
        out.put("weatherUsedCount", weatherUsedCount);
        out.put("weatherCurrentMonthUsedCount", weatherUsedCount);
        out.put("weatherTotalUsedCount", weatherTotalUsed);
        out.put("weatherRemain", calcRemain(weatherMonthlyLimit, weatherUsedCount));
        out.put("weatherUsageRate", weatherMonthlyLimit <= 0 ? 0 : (int) Math.round((weatherUsedCount * 100.0) / weatherMonthlyLimit));
        out.put("locationMonthlyLimit", locationMonthlyLimit);
        out.put("locationUsedCount", locationUsedCount);
        out.put("locationCurrentMonthUsedCount", locationUsedCount);
        out.put("locationTotalUsedCount", locationTotalUsed);
        out.put("locationRemain", locationRemain);
        out.put("locationUsageRate", locationMonthlyLimit <= 0 ? 0 : (int) Math.round((locationUsedCount * 100.0) / locationMonthlyLimit));
        out.put("geocodeDailyLimit", locationMonthlyLimit);
        out.put("geocodeUsedCount", locationUsedCount);
        out.put("geocodeRemain", locationRemain);
        out.put("cityDailyLimit", locationMonthlyLimit);
        out.put("cityUsedCount", locationUsedCount);
        out.put("cityRemain", locationRemain);
        out.put("weatherDailyLimit", weatherMonthlyLimit);
        out.put("locationDailyLimit", locationMonthlyLimit);
        out.put("qpsLimit", row == null || row.getQpsLimit() == null ? 3 : row.getQpsLimit());
        out.put("rechargeTotal", row == null ? 0 : row.getRechargeTotal());
        out.put("weatherRechargeTotal", row == null ? 0 : row.getWeatherRechargeTotal());
        int locationRechargeTotal = row == null || row.getLocationRechargeTotal() == null
                ? (row == null ? 0 : Math.max(nvl(row.getGeocodeRechargeTotal()), nvl(row.getCityRechargeTotal())))
                : row.getLocationRechargeTotal();
        out.put("locationRechargeTotal", locationRechargeTotal);
        out.put("geocodeRechargeTotal", locationRechargeTotal);
        out.put("cityRechargeTotal", locationRechargeTotal);
        out.put("cacheRedisEnabled", row != null && row.getCacheRedisEnabled() != null && row.getCacheRedisEnabled() == 1);
        out.put("cacheRedisKeyPrefix", row == null ? null : row.getCacheRedisKeyPrefix());
        out.put("cacheRegionTtlMinutes", row == null ? null : row.getCacheRegionTtlMinutes());
        out.put("cacheRegionStaleMinutes", row == null ? null : row.getCacheRegionStaleMinutes());
        out.put("cacheWeatherTtlMinutes", row == null ? null : row.getCacheWeatherTtlMinutes());
        out.put("cacheLocalRegionMaxEntries", row == null ? null : row.getCacheLocalRegionMaxEntries());
        out.put("cacheLocalWeatherMaxEntries", row == null ? null : row.getCacheLocalWeatherMaxEntries());
        out.put("auditAutoPurgeEnabled", row != null && row.getAuditAutoPurgeEnabled() != null && row.getAuditAutoPurgeEnabled() == 1);
        out.put("auditRetainDays", row == null ? null : row.getAuditRetainDays());
        out.put("remark", row == null ? null : row.getRemark());
        out.put("updatedAt", row == null ? null : row.getUpdatedAt());
        return out;
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
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

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private int calcRemain(Integer limit, Integer used) {
        int l = limit == null ? 0 : limit;
        int u = used == null ? 0 : used;
        return Math.max(0, l - u);
    }

    private String maskAppKey(String appKey) {
        if (appKey == null) {
            return null;
        }
        String key = appKey.trim();
        if (key.length() <= 8) {
            return key;
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    private String safeText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        String lower = text.toLowerCase();
        if ("undefined".equals(lower) || "null".equals(lower)) {
            return null;
        }
        return text;
    }

    private String firstNonEmpty(String first, String fallback) {
        return StringUtils.hasText(first) ? first : fallback;
    }

    private String shortenError(Exception e) {
        if (e == null) {
            return "请求失败";
        }
        String text = safeText(e.getMessage());
        return StringUtils.hasText(text) ? text : "请求失败";
    }

    private <T> Result<T> tableOrServerError(Exception e) {
        String message = e == null ? null : e.getMessage();
        String text = message == null ? "" : message.toLowerCase();
        if (text.contains("amap_quota_config") || text.contains("amap_api_audit") || text.contains("amap_usage_daily") || text.contains("amap_usage_monthly")) {
            return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), "高德模块数据表缺失，请先执行 db/schema-amap.sql");
        }
        return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage());
    }

    @Data
    public static class QuotaReq {
        @Min(1)
        @Max(100)
        private Integer alertThreshold;

        private String accountName;
        private String accountLogin;
        private String appName;
        private String consoleUrl;
        private String keyConsoleUrl;
        private String appKey;

        @Min(0)
        private Integer weatherMonthlyLimit;

        @Min(0)
        private Integer locationMonthlyLimit;

        @Min(0)
        private Integer weatherDailyLimit;

        @Min(0)
        private Integer locationDailyLimit;

        @Min(1)
        @Max(100)
        private Integer qpsLimit;

        private Boolean cacheRedisEnabled;
        private String cacheRedisKeyPrefix;

        @Min(1)
        private Integer cacheRegionTtlMinutes;

        @Min(1)
        private Integer cacheRegionStaleMinutes;

        @Min(1)
        private Integer cacheWeatherTtlMinutes;

        @Min(32)
        private Integer cacheLocalRegionMaxEntries;

        @Min(32)
        private Integer cacheLocalWeatherMaxEntries;

        private Boolean auditAutoPurgeEnabled;

        @Min(7)
        private Integer auditRetainDays;

        private String remark;

        public Integer resolveWeatherMonthlyLimit() {
            if (weatherMonthlyLimit != null) {
                return weatherMonthlyLimit;
            }
            return weatherDailyLimit;
        }

        public Integer resolveLocationMonthlyLimit() {
            if (locationMonthlyLimit != null) {
                return locationMonthlyLimit;
            }
            return locationDailyLimit;
        }
    }

    @Data
    public static class RechargeReq {
        private String apiType;

        @Min(1)
        private Integer amount;

        private String remark;
    }

    @Data
    public static class VerifyKeyReq {
        private String appKey;
    }

    @Data
    public static class HealthCheckReq {
        private Double longitude;
        private Double latitude;
    }

    @Data
    public static class BatchDeleteReq {
        private List<Long> ids;
    }

    @Data
    public static class PurgeReq {
        @Min(7)
        private Integer retainDays;

        @Min(100)
        @Max(5000)
        private Integer batchSize;

        @Min(1)
        @Max(200)
        private Integer maxBatches;
    }
}
