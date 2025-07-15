package com.dahe.v2.modules.amap.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dahe.v2.modules.amap.mapper.AmapQuotaConfigMapper;
import com.dahe.v2.modules.amap.model.AmapQuotaConfig;
import com.dahe.v2.modules.amap.service.AmapQuotaConfigService;
import com.dahe.v2.modules.amap.service.AmapUsageDailyService;
import com.dahe.v2.modules.auth.domain.AuthDomainConstants;
import com.dahe.v2.modules.auth.service.UserNoticeService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.Locale;

@Service
/**
 * 高德配额配置服务实现。
 *
 * <p>职责：</p>
 * <p>1. 维护单行配额配置（ID=1）；</p>
 * <p>2. 维护月限额配置，并同步当前月份已用量；</p>
 * <p>3. 统一 weather/location 两类计费口径；</p>
 * <p>4. 维护缓存策略与审计保留策略配置。</p>
 */
public class AmapQuotaConfigServiceImpl extends ServiceImpl<AmapQuotaConfigMapper, AmapQuotaConfig> implements AmapQuotaConfigService {

    private static final long DEFAULT_ID = 1L;
    private static final int DEFAULT_ALERT_THRESHOLD = 80;
    private static final int DEFAULT_WEATHER_LIMIT = 20000;
    private static final int DEFAULT_LOCATION_LIMIT = 20000;
    private static final int DEFAULT_QPS_LIMIT = 3;
    private static final String DEFAULT_KEY_STATUS = "unknown";
    private static final String DEFAULT_CONSOLE_URL = "https://console.amap.com/dev";
    private static final String DEFAULT_KEY_CONSOLE_URL = "https://console.amap.com/dev/key/app";

    private static final int DEFAULT_CACHE_REDIS_ENABLED = 1;
    private static final String DEFAULT_CACHE_REDIS_PREFIX = "dahe:v2:amap:cache:";
    private static final int DEFAULT_CACHE_REGION_TTL_MINUTES = 720;
    private static final int DEFAULT_CACHE_REGION_STALE_MINUTES = 1440;
    private static final int DEFAULT_CACHE_WEATHER_TTL_MINUTES = 60;
    private static final int DEFAULT_CACHE_LOCAL_REGION_MAX_ENTRIES = 256;
    private static final int DEFAULT_CACHE_LOCAL_WEATHER_MAX_ENTRIES = 256;

    private static final int DEFAULT_AUDIT_AUTO_PURGE_ENABLED = 1;
    private static final int DEFAULT_AUDIT_RETAIN_DAYS = 90;
    private final AmapUsageDailyService amapUsageDailyService;
    private final UserNoticeService userNoticeService;

    public AmapQuotaConfigServiceImpl(AmapUsageDailyService amapUsageDailyService, UserNoticeService userNoticeService) {
        this.amapUsageDailyService = amapUsageDailyService;
        this.userNoticeService = userNoticeService;
    }

    @Override
    // 单行配置模型：不存在则初始化；已用量按当前月份从月表同步。
    public synchronized AmapQuotaConfig getOrInitToday() {
        LocalDate today = LocalDate.now();
        LocalDate currentMonth = monthStart(today);
        AmapQuotaConfig row = this.getById(DEFAULT_ID);
        if (row == null) {
            row = new AmapQuotaConfig();
            row.setId(DEFAULT_ID);
            row.setRecordDate(today);
            row.setAlertThreshold(DEFAULT_ALERT_THRESHOLD);
            row.setWeatherMonthlyLimit(DEFAULT_WEATHER_LIMIT);
            row.setLocationMonthlyLimit(DEFAULT_LOCATION_LIMIT);
            row.setWeatherDailyLimit(DEFAULT_WEATHER_LIMIT);
            row.setLocationDailyLimit(DEFAULT_LOCATION_LIMIT);
            row.setWeatherUsedCount(0);
            row.setLocationUsedCount(0);
            row.setGeocodeDailyLimit(DEFAULT_LOCATION_LIMIT);
            row.setCityDailyLimit(DEFAULT_LOCATION_LIMIT);
            row.setGeocodeUsedCount(0);
            row.setCityUsedCount(0);
            row.setQpsLimit(DEFAULT_QPS_LIMIT);
            row.setRechargeTotal(0);
            row.setWeatherRechargeTotal(0);
            row.setLocationRechargeTotal(0);
            row.setGeocodeRechargeTotal(0);
            row.setCityRechargeTotal(0);
            row.setAppKeyStatus(DEFAULT_KEY_STATUS);
            row.setConsoleUrl(DEFAULT_CONSOLE_URL);
            row.setKeyConsoleUrl(DEFAULT_KEY_CONSOLE_URL);
            row.setCacheRedisEnabled(DEFAULT_CACHE_REDIS_ENABLED);
            row.setCacheRedisKeyPrefix(DEFAULT_CACHE_REDIS_PREFIX);
            row.setCacheRegionTtlMinutes(DEFAULT_CACHE_REGION_TTL_MINUTES);
            row.setCacheRegionStaleMinutes(DEFAULT_CACHE_REGION_STALE_MINUTES);
            row.setCacheWeatherTtlMinutes(DEFAULT_CACHE_WEATHER_TTL_MINUTES);
            row.setCacheLocalRegionMaxEntries(DEFAULT_CACHE_LOCAL_REGION_MAX_ENTRIES);
            row.setCacheLocalWeatherMaxEntries(DEFAULT_CACHE_LOCAL_WEATHER_MAX_ENTRIES);
            row.setAuditAutoPurgeEnabled(DEFAULT_AUDIT_AUTO_PURGE_ENABLED);
            row.setAuditRetainDays(DEFAULT_AUDIT_RETAIN_DAYS);
            row.setUsedCount(0);
            row.setDailyLimit(DEFAULT_WEATHER_LIMIT + DEFAULT_LOCATION_LIMIT);
            row.setRemark("default");
            this.save(row);
            syncCurrentMonthUsage(row, currentMonth);
            this.updateById(row);
            return row;
        }

        boolean dirty = false;
        if (row.getRecordDate() == null || !today.equals(row.getRecordDate())) {
            row.setRecordDate(today);
            dirty = true;
        }
        if (row.getAlertThreshold() == null || row.getAlertThreshold() <= 0 || row.getAlertThreshold() > 100) {
            row.setAlertThreshold(DEFAULT_ALERT_THRESHOLD);
            dirty = true;
        }
        int weatherMonthlyLimit = resolveNonNegative(row.getWeatherMonthlyLimit(), row.getWeatherDailyLimit(), DEFAULT_WEATHER_LIMIT);
        if (row.getWeatherMonthlyLimit() == null || row.getWeatherMonthlyLimit() != weatherMonthlyLimit) {
            row.setWeatherMonthlyLimit(weatherMonthlyLimit);
            dirty = true;
        }
        if (row.getWeatherDailyLimit() == null || row.getWeatherDailyLimit() != weatherMonthlyLimit) {
            row.setWeatherDailyLimit(weatherMonthlyLimit);
            dirty = true;
        }

        int locationMonthlyLimit = resolveLocationDailyLimit(
                row.getLocationMonthlyLimit(),
                row.getLocationDailyLimit(),
                row.getGeocodeDailyLimit(),
                row.getCityDailyLimit(),
                DEFAULT_LOCATION_LIMIT
        );
        if (row.getLocationMonthlyLimit() == null || row.getLocationMonthlyLimit() != locationMonthlyLimit) {
            row.setLocationMonthlyLimit(locationMonthlyLimit);
            dirty = true;
        }
        if (row.getLocationDailyLimit() == null || row.getLocationDailyLimit() != locationMonthlyLimit) {
            row.setLocationDailyLimit(locationMonthlyLimit);
            dirty = true;
        }
        if (row.getGeocodeDailyLimit() == null || row.getGeocodeDailyLimit() != locationMonthlyLimit) {
            row.setGeocodeDailyLimit(locationMonthlyLimit);
            dirty = true;
        }
        if (row.getCityDailyLimit() == null || row.getCityDailyLimit() != locationMonthlyLimit) {
            row.setCityDailyLimit(locationMonthlyLimit);
            dirty = true;
        }

        if (row.getQpsLimit() == null || row.getQpsLimit() <= 0) {
            row.setQpsLimit(DEFAULT_QPS_LIMIT);
            dirty = true;
        }
        if (row.getRechargeTotal() == null || row.getRechargeTotal() < 0) {
            row.setRechargeTotal(0);
            dirty = true;
        }
        if (row.getWeatherRechargeTotal() == null || row.getWeatherRechargeTotal() < 0) {
            row.setWeatherRechargeTotal(0);
            dirty = true;
        }
        int locationRechargeTotal = resolveLocationRechargeTotal(
                row.getLocationRechargeTotal(),
                row.getGeocodeRechargeTotal(),
                row.getCityRechargeTotal()
        );
        if (row.getLocationRechargeTotal() == null || row.getLocationRechargeTotal() != locationRechargeTotal) {
            row.setLocationRechargeTotal(locationRechargeTotal);
            dirty = true;
        }
        if (row.getGeocodeRechargeTotal() == null || row.getGeocodeRechargeTotal() != locationRechargeTotal) {
            row.setGeocodeRechargeTotal(locationRechargeTotal);
            dirty = true;
        }
        if (row.getCityRechargeTotal() == null || row.getCityRechargeTotal() != 0) {
            row.setCityRechargeTotal(0);
            dirty = true;
        }

        if (!StringUtils.hasText(row.getAppKeyStatus())) {
            row.setAppKeyStatus(DEFAULT_KEY_STATUS);
            dirty = true;
        }
        if (!StringUtils.hasText(row.getConsoleUrl())) {
            row.setConsoleUrl(DEFAULT_CONSOLE_URL);
            dirty = true;
        }
        if (!StringUtils.hasText(row.getKeyConsoleUrl())) {
            row.setKeyConsoleUrl(DEFAULT_KEY_CONSOLE_URL);
            dirty = true;
        }

        if (row.getCacheRedisEnabled() == null) {
            row.setCacheRedisEnabled(DEFAULT_CACHE_REDIS_ENABLED);
            dirty = true;
        }
        if (!StringUtils.hasText(row.getCacheRedisKeyPrefix())) {
            row.setCacheRedisKeyPrefix(DEFAULT_CACHE_REDIS_PREFIX);
            dirty = true;
        }
        if (row.getCacheRegionTtlMinutes() == null || row.getCacheRegionTtlMinutes() <= 0) {
            row.setCacheRegionTtlMinutes(DEFAULT_CACHE_REGION_TTL_MINUTES);
            dirty = true;
        }
        if (row.getCacheRegionStaleMinutes() == null || row.getCacheRegionStaleMinutes() <= 0) {
            row.setCacheRegionStaleMinutes(DEFAULT_CACHE_REGION_STALE_MINUTES);
            dirty = true;
        }
        if (row.getCacheWeatherTtlMinutes() == null || row.getCacheWeatherTtlMinutes() <= 0) {
            row.setCacheWeatherTtlMinutes(DEFAULT_CACHE_WEATHER_TTL_MINUTES);
            dirty = true;
        }
        if (row.getCacheLocalRegionMaxEntries() == null || row.getCacheLocalRegionMaxEntries() < 32) {
            row.setCacheLocalRegionMaxEntries(DEFAULT_CACHE_LOCAL_REGION_MAX_ENTRIES);
            dirty = true;
        }
        if (row.getCacheLocalWeatherMaxEntries() == null || row.getCacheLocalWeatherMaxEntries() < 32) {
            row.setCacheLocalWeatherMaxEntries(DEFAULT_CACHE_LOCAL_WEATHER_MAX_ENTRIES);
            dirty = true;
        }

        if (row.getAuditAutoPurgeEnabled() == null) {
            row.setAuditAutoPurgeEnabled(DEFAULT_AUDIT_AUTO_PURGE_ENABLED);
            dirty = true;
        }
        if (row.getAuditRetainDays() == null || row.getAuditRetainDays() < 7) {
            row.setAuditRetainDays(DEFAULT_AUDIT_RETAIN_DAYS);
            dirty = true;
        }

        dirty = syncCurrentMonthUsage(row, currentMonth) || dirty;
        int computedUsedCount = sumUsedCount(row);
        if (row.getUsedCount() == null || row.getUsedCount() != computedUsedCount) {
            row.setUsedCount(computedUsedCount);
            dirty = true;
        }
        int computedMonthlyLimit = sumDailyLimit(row);
        if (row.getDailyLimit() == null || row.getDailyLimit() != computedMonthlyLimit) {
            row.setDailyLimit(computedMonthlyLimit);
            dirty = true;
        }
        if (dirty) {
            this.updateById(row);
        }
        return row;
    }

    @Override
    // 更新配置入口：统一落到 weather/location 月限额并同步缓存与审计策略。
    public synchronized AmapQuotaConfig updateConfig(
            Integer alertThreshold,
            String accountName,
            String accountLogin,
            String appName,
            String consoleUrl,
            String keyConsoleUrl,
            String appKey,
            Integer weatherMonthlyLimit,
            Integer locationMonthlyLimit,
            Integer qpsLimit,
            Boolean cacheRedisEnabled,
            String cacheRedisKeyPrefix,
            Integer cacheRegionTtlMinutes,
            Integer cacheRegionStaleMinutes,
            Integer cacheWeatherTtlMinutes,
            Integer cacheLocalRegionMaxEntries,
            Integer cacheLocalWeatherMaxEntries,
            Boolean auditAutoPurgeEnabled,
            Integer auditRetainDays,
            String remark
    ) {
        AmapQuotaConfig row = getOrInitToday();
        if (alertThreshold != null && alertThreshold > 0 && alertThreshold <= 100) {
            row.setAlertThreshold(alertThreshold);
        }
        if (weatherMonthlyLimit != null && weatherMonthlyLimit >= 0) {
            row.setWeatherMonthlyLimit(weatherMonthlyLimit);
            row.setWeatherDailyLimit(weatherMonthlyLimit);
        }
        if (locationMonthlyLimit != null && locationMonthlyLimit >= 0) {
            row.setLocationMonthlyLimit(locationMonthlyLimit);
            row.setLocationDailyLimit(locationMonthlyLimit);
            row.setGeocodeDailyLimit(locationMonthlyLimit);
            row.setCityDailyLimit(locationMonthlyLimit);
        }
        if (qpsLimit != null && qpsLimit > 0) {
            row.setQpsLimit(qpsLimit);
        }

        if (cacheRedisEnabled != null) {
            row.setCacheRedisEnabled(Boolean.TRUE.equals(cacheRedisEnabled) ? 1 : 0);
        }
        if (cacheRedisKeyPrefix != null) {
            row.setCacheRedisKeyPrefix(defaultIfEmpty(normalizeText(cacheRedisKeyPrefix), DEFAULT_CACHE_REDIS_PREFIX));
        }
        if (cacheRegionTtlMinutes != null && cacheRegionTtlMinutes > 0) {
            row.setCacheRegionTtlMinutes(cacheRegionTtlMinutes);
        }
        if (cacheRegionStaleMinutes != null && cacheRegionStaleMinutes > 0) {
            row.setCacheRegionStaleMinutes(cacheRegionStaleMinutes);
        }
        if (cacheWeatherTtlMinutes != null && cacheWeatherTtlMinutes > 0) {
            row.setCacheWeatherTtlMinutes(cacheWeatherTtlMinutes);
        }
        if (cacheLocalRegionMaxEntries != null && cacheLocalRegionMaxEntries >= 32) {
            row.setCacheLocalRegionMaxEntries(cacheLocalRegionMaxEntries);
        }
        if (cacheLocalWeatherMaxEntries != null && cacheLocalWeatherMaxEntries >= 32) {
            row.setCacheLocalWeatherMaxEntries(cacheLocalWeatherMaxEntries);
        }
        if (auditAutoPurgeEnabled != null) {
            row.setAuditAutoPurgeEnabled(Boolean.TRUE.equals(auditAutoPurgeEnabled) ? 1 : 0);
        }
        if (auditRetainDays != null && auditRetainDays >= 7) {
            row.setAuditRetainDays(auditRetainDays);
        }

        row.setAccountName(normalizeText(accountName));
        row.setAccountLogin(normalizeText(accountLogin));
        row.setAppName(normalizeText(appName));
        row.setConsoleUrl(defaultIfEmpty(normalizeText(consoleUrl), DEFAULT_CONSOLE_URL));
        row.setKeyConsoleUrl(defaultIfEmpty(normalizeText(keyConsoleUrl), DEFAULT_KEY_CONSOLE_URL));
        if (appKey != null) {
            String nextKey = normalizeText(appKey);
            String prevKey = normalizeText(row.getAppKey());
            row.setAppKey(nextKey);
            if (!equalsText(prevKey, nextKey)) {
                row.setAppKeyBoundAt(LocalDateTime.now());
                row.setAppKeyStatus(DEFAULT_KEY_STATUS);
                row.setAppKeyLastCheckAt(null);
                row.setAppKeyLastCheckMessage(null);
            }
        }

        syncCurrentMonthUsage(row, monthStart(LocalDate.now()));
        row.setLocationRechargeTotal(resolveLocationRechargeTotal(row.getLocationRechargeTotal(), row.getGeocodeRechargeTotal(), row.getCityRechargeTotal()));
        row.setGeocodeRechargeTotal(row.getLocationRechargeTotal());
        row.setCityRechargeTotal(0);
        row.setUsedCount(sumUsedCount(row));
        row.setDailyLimit(sumDailyLimit(row));
        row.setRemark(normalizeText(remark));
        this.updateById(row);
        notifyQuotaWarningIfNeeded(row, BILLING_WEATHER, false);
        notifyQuotaWarningIfNeeded(row, BILLING_LOCATION, false);
        return row;
    }

    @Override
    // 按计费分类增加用量：weather/location。
    public synchronized AmapQuotaConfig increaseUsageByBillingCategory(String billingCategory, int delta) {
        int nextDelta = delta <= 0 ? 1 : delta;
        AmapQuotaConfig row = getOrInitToday();
        String type = normalizeBillingCategory(billingCategory);
        LocalDate today = LocalDate.now();
        amapUsageDailyService.recordRemoteUsage(today, type, nextDelta);
        syncCurrentMonthUsage(row, monthStart(today));
        row.setUsedCount(sumUsedCount(row));
        this.updateById(row);
        notifyQuotaWarningIfNeeded(row, type, true);
        return row;
    }

    @Override
    // 充值规则：weather 独立，location(含 geocode/city 兼容)共享。
    public synchronized AmapQuotaConfig recharge(String apiType, int delta, String remark) {
        int nextDelta = delta <= 0 ? 0 : delta;
        if (nextDelta <= 0) {
            return getOrInitToday();
        }
        AmapQuotaConfig row = getOrInitToday();
        String type = StringUtils.hasText(apiType) ? apiType.trim().toLowerCase(Locale.ROOT) : "all";
        int totalRechargeAdded = 0;

        if ("weather".equals(type) || "all".equals(type)) {
            row.setWeatherMonthlyLimit((row.getWeatherMonthlyLimit() == null ? 0 : row.getWeatherMonthlyLimit()) + nextDelta);
            row.setWeatherDailyLimit(row.getWeatherMonthlyLimit());
            row.setWeatherRechargeTotal((row.getWeatherRechargeTotal() == null ? 0 : row.getWeatherRechargeTotal()) + nextDelta);
            totalRechargeAdded += nextDelta;
        }
        if ("location".equals(type) || "geocode".equals(type) || "city".equals(type) || "all".equals(type)) {
            row.setLocationMonthlyLimit((row.getLocationMonthlyLimit() == null ? 0 : row.getLocationMonthlyLimit()) + nextDelta);
            row.setLocationDailyLimit(row.getLocationMonthlyLimit());
            row.setLocationRechargeTotal((row.getLocationRechargeTotal() == null ? 0 : row.getLocationRechargeTotal()) + nextDelta);
            row.setGeocodeDailyLimit(row.getLocationDailyLimit());
            row.setCityDailyLimit(row.getLocationDailyLimit());
            row.setGeocodeRechargeTotal(row.getLocationRechargeTotal());
            row.setCityRechargeTotal(0);
            totalRechargeAdded += nextDelta;
        }
        row.setRechargeTotal((row.getRechargeTotal() == null ? 0 : row.getRechargeTotal()) + totalRechargeAdded);
        row.setDailyLimit(sumDailyLimit(row));
        if (StringUtils.hasText(remark)) {
            row.setRemark(remark.trim());
        }
        this.updateById(row);
        return row;
    }

    @Override
    // 更新 key 校验结果快照。
    public synchronized AmapQuotaConfig updateKeyCheck(String keyStatus, String checkMessage) {
        AmapQuotaConfig row = getOrInitToday();
        row.setAppKeyStatus(normalizeKeyStatus(keyStatus));
        row.setAppKeyLastCheckAt(LocalDateTime.now());
        row.setAppKeyLastCheckMessage(cutText(checkMessage, 240));
        this.updateById(row);
        return row;
    }

    @Override
    // 更新体检结果快照。
    public synchronized AmapQuotaConfig updateHealthCheck(String checkMessage) {
        AmapQuotaConfig row = getOrInitToday();
        row.setLastHealthCheckAt(LocalDateTime.now());
        row.setLastHealthCheckMessage(cutText(checkMessage, 240));
        this.updateById(row);
        return row;
    }

    @Override
    /** 读取当前绑定 key（空值返回 null）。 */
    public synchronized String resolveBoundAppKey() {
        AmapQuotaConfig row = getOrInitToday();
        String key = normalizeText(row.getAppKey());
        if (!StringUtils.hasText(key)) {
            return null;
        }
        return key;
    }

    private int sumDailyLimit(AmapQuotaConfig row) {
        int weather = row == null
                ? 0
                : (row.getWeatherMonthlyLimit() == null ? nvl(row.getWeatherDailyLimit()) : row.getWeatherMonthlyLimit());
        int location = row == null
                ? 0
                : (row.getLocationMonthlyLimit() == null ? nvl(row.getLocationDailyLimit()) : row.getLocationMonthlyLimit());
        return weather + location;
    }

    private int sumUsedCount(AmapQuotaConfig row) {
        return (row.getWeatherUsedCount() == null ? 0 : row.getWeatherUsedCount())
                + (row.getLocationUsedCount() == null ? 0 : row.getLocationUsedCount());
    }

    private int resolveLocationDailyLimit(Integer locationMonthlyLimit, Integer locationDailyLimit, Integer geocodeDailyLimit, Integer cityDailyLimit, int fallback) {
        int locationMonth = locationMonthlyLimit == null ? -1 : locationMonthlyLimit;
        int location = locationDailyLimit == null ? -1 : locationDailyLimit;
        int geocode = geocodeDailyLimit == null ? -1 : geocodeDailyLimit;
        int city = cityDailyLimit == null ? -1 : cityDailyLimit;
        int candidate = Math.max(locationMonth, Math.max(location, Math.max(geocode, city)));
        if (candidate < 0) {
            candidate = Math.max(0, fallback);
        }
        return candidate;
    }

    private int resolveNonNegative(Integer primary, Integer fallbackValue, int defaultValue) {
        if (primary != null && primary >= 0) {
            return primary;
        }
        if (fallbackValue != null && fallbackValue >= 0) {
            return fallbackValue;
        }
        return Math.max(0, defaultValue);
    }

    private int resolveLocationUsedCount(Integer locationUsedCount, Integer geocodeUsedCount, Integer cityUsedCount) {
        if (locationUsedCount != null && locationUsedCount >= 0) {
            return locationUsedCount;
        }
        int geocode = geocodeUsedCount == null || geocodeUsedCount < 0 ? 0 : geocodeUsedCount;
        int city = cityUsedCount == null || cityUsedCount < 0 ? 0 : cityUsedCount;
        return geocode + city;
    }

    private int resolveLocationRechargeTotal(Integer locationRechargeTotal, Integer geocodeRechargeTotal, Integer cityRechargeTotal) {
        if (locationRechargeTotal != null && locationRechargeTotal >= 0) {
            return locationRechargeTotal;
        }
        int geocode = geocodeRechargeTotal == null || geocodeRechargeTotal < 0 ? 0 : geocodeRechargeTotal;
        int city = cityRechargeTotal == null || cityRechargeTotal < 0 ? 0 : cityRechargeTotal;
        return Math.max(geocode, city);
    }

    private String normalizeBillingCategory(String billingCategory) {
        String type = StringUtils.hasText(billingCategory) ? billingCategory.trim().toLowerCase(Locale.ROOT) : "";
        if (BILLING_WEATHER.equals(type)) {
            return BILLING_WEATHER;
        }
        if (BILLING_LOCATION.equals(type)) {
            return BILLING_LOCATION;
        }
        if (type.contains("geo") || type.contains("regeo") || type.contains("location") || type.contains("city") || type.contains("district")) {
            return BILLING_LOCATION;
        }
        if (type.contains("weather")) {
            return BILLING_WEATHER;
        }
        return BILLING_LOCATION;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private String defaultIfEmpty(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private LocalDate monthStart(LocalDate value) {
        LocalDate source = value == null ? LocalDate.now() : value;
        return source.with(TemporalAdjusters.firstDayOfMonth());
    }

    private boolean syncCurrentMonthUsage(AmapQuotaConfig row, LocalDate currentMonth) {
        if (row == null) {
            return false;
        }
        Map<String, Integer> monthUsage = amapUsageDailyService.getMonthlyUsage(currentMonth);
        int weatherUsed = Math.max(0, monthUsage.getOrDefault(BILLING_WEATHER, 0));
        int locationUsed = Math.max(0, monthUsage.getOrDefault(BILLING_LOCATION, 0));
        boolean dirty = false;
        if (row.getWeatherUsedCount() == null || row.getWeatherUsedCount() != weatherUsed) {
            row.setWeatherUsedCount(weatherUsed);
            dirty = true;
        }
        if (row.getLocationUsedCount() == null || row.getLocationUsedCount() != locationUsed) {
            row.setLocationUsedCount(locationUsed);
            dirty = true;
        }
        if (row.getGeocodeUsedCount() == null || row.getGeocodeUsedCount() != locationUsed) {
            row.setGeocodeUsedCount(locationUsed);
            dirty = true;
        }
        if (row.getCityUsedCount() == null || row.getCityUsedCount() != 0) {
            row.setCityUsedCount(0);
            dirty = true;
        }
        return dirty;
    }

    private void notifyQuotaWarningIfNeeded(AmapQuotaConfig row, String billingCategory, boolean onlyWhenReachedByUsage) {
        if (row == null || row.getAlertThreshold() == null || row.getAlertThreshold() <= 0) {
            return;
        }
        String type = normalizeBillingCategory(billingCategory);
        int limit = BILLING_WEATHER.equals(type)
                ? nvl(row.getWeatherMonthlyLimit() == null ? row.getWeatherDailyLimit() : row.getWeatherMonthlyLimit())
                : nvl(row.getLocationMonthlyLimit() == null ? row.getLocationDailyLimit() : row.getLocationMonthlyLimit());
        int used = BILLING_WEATHER.equals(type) ? nvl(row.getWeatherUsedCount()) : nvl(row.getLocationUsedCount());
        if (limit <= 0 || used <= 0) {
            return;
        }
        int threshold = Math.min(100, Math.max(1, row.getAlertThreshold()));
        int rate = (int) Math.floor((used * 100.0) / limit);
        if (rate < threshold) {
            return;
        }
        LocalDate month = monthStart(row.getRecordDate() == null ? LocalDate.now() : row.getRecordDate());
        if (!amapUsageDailyService.markMonthlyWarningSentIfAbsent(month, type)) {
            return;
        }
        if (onlyWhenReachedByUsage || rate >= threshold) {
            String title = BILLING_WEATHER.equals(type) ? "高德天气额度预警" : "高德位置额度预警";
            String typeName = BILLING_WEATHER.equals(type) ? "天气" : "位置";
            String content = String.format(
                    Locale.ROOT,
                    "%s接口本月真实用量已达 %d/%d（%d%%），达到预警阈值 %d%%，请及时处理。",
                    typeName,
                    used,
                    limit,
                    rate,
                    threshold
            );
            userNoticeService.pushAdminRouteNoticeSafe("/amap-audit", title, content, AuthDomainConstants.NOTICE_TYPE_STATUS);
        }
    }

    private boolean equalsText(String left, String right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equals(right);
    }

    private String normalizeKeyStatus(String keyStatus) {
        String status = StringUtils.hasText(keyStatus) ? keyStatus.trim().toLowerCase(Locale.ROOT) : "";
        if ("valid".equals(status) || "invalid".equals(status) || "unknown".equals(status)) {
            return status;
        }
        return DEFAULT_KEY_STATUS;
    }

    private String cutText(String text, int maxLen) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String value = text.trim();
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }
}
