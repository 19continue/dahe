package com.dahe.v2.modules.amap.service.impl;

import com.dahe.v2.modules.amap.model.AmapQuotaConfig;
import com.dahe.v2.modules.amap.service.AmapOpenService;
import com.dahe.v2.modules.amap.service.AmapQuotaConfigService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 高德开放平台调用服务。
 *
 * <p>能力：</p>
 * <p>1. 统一 HTTP 调用、QPS 节流与限流重试；</p>
 * <p>2. 行政区与天气查询双层缓存（本地有界 LRU + Redis TTL）；</p>
 * <p>3. 在限流场景下，区划查询允许短时使用本地过期缓存兜底。</p>
 */
@Service
public class AmapOpenServiceImpl implements AmapOpenService {

    private static final Logger log = LoggerFactory.getLogger(AmapOpenServiceImpl.class);

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {};

    private static final String AMAP_BASE_URL = "https://restapi.amap.com";
    private static final String PATH_INPUT_TIPS = "/v3/assistant/inputtips";
    private static final String PATH_GEOCODE = "/v3/geocode/geo";
    private static final String PATH_REVERSE_GEOCODE = "/v3/geocode/regeo";
    private static final String PATH_WEATHER = "/v3/weather/weatherInfo";
    private static final String PATH_DISTRICT = "/v3/config/district";

    private static final int DEFAULT_QPS_LIMIT = 3;
    private static final int MAX_QPS_RETRY = 2;
    private static final long QPS_LIMIT_CACHE_TTL_MS = 30_000L;
    private static final long RUNTIME_POLICY_CACHE_TTL_MS = 30_000L;

    private static final long DEFAULT_REGION_CACHE_TTL_MINUTES = 12L * 60L;
    private static final long DEFAULT_REGION_STALE_TTL_MINUTES = 24L * 60L;
    private static final long DEFAULT_WEATHER_CACHE_TTL_MINUTES = 60L;
    private static final int DEFAULT_LOCAL_REGION_MAX_ENTRIES = 256;
    private static final int DEFAULT_LOCAL_WEATHER_MAX_ENTRIES = 256;
    private static final String DEFAULT_REDIS_PREFIX = "dahe:v2:amap:cache:";
    private static final double REVERSE_GEOCODE_CACHE_GRID_METERS = 200D;
    private static final double WEATHER_REVERSE_GEOCODE_CACHE_GRID_METERS = 1000D;

    private static final String CACHE_TYPE_DISTRICT = "district";
    private static final String CACHE_TYPE_REVERSE_GEOCODE = "regeo";
    private static final String CACHE_TYPE_REVERSE_GEOCODE_WEATHER = "regeo-weather";
    private static final String CACHE_TYPE_WEATHER = "weather";
    private static final String CACHE_SOURCE_LOCAL = "local";
    private static final String CACHE_SOURCE_LOCAL_STALE = "local-stale";
    private static final String CACHE_SOURCE_REDIS = "redis";

    private final ObjectMapper objectMapper;
    private final AmapQuotaConfigService amapQuotaConfigService;
    private final RestTemplate restTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    private final boolean defaultRedisCacheEnabled;
    private final String defaultRedisCachePrefix;
    private final long defaultDistrictCacheTtlMs;
    private final long defaultDistrictStaleTtlMs;
    private final long defaultWeatherCacheTtlMs;
    private final int defaultLocalRegionMaxEntries;
    private final int defaultLocalWeatherMaxEntries;

    private volatile BoundedLocalCache districtLocalCache;
    private volatile BoundedLocalCache weatherLocalCache;
    private volatile CachePolicy cachePolicy;
    private volatile long cachePolicyExpireAtMs = 0L;
    private final Object cachePolicyLock = new Object();

    private final Object requestPaceLock = new Object();
    private volatile long nextRequestAllowedAtMs = 0L;
    private volatile long qpsLimitCacheExpireAtMs = 0L;
    private volatile int cachedQpsLimit = DEFAULT_QPS_LIMIT;

    public AmapOpenServiceImpl(
            ObjectMapper objectMapper,
            AmapQuotaConfigService amapQuotaConfigService,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
            @Value("${app.amap.cache.redis-enabled:true}") boolean redisCacheEnabled,
            @Value("${app.amap.cache.redis-key-prefix:dahe:v2:amap:cache:}") String redisCachePrefix,
            @Value("${app.amap.cache.region-ttl-minutes:720}") long regionTtlMinutes,
            @Value("${app.amap.cache.region-stale-minutes:1440}") long regionStaleMinutes,
            @Value("${app.amap.cache.weather-ttl-minutes:60}") long weatherTtlMinutes,
            @Value("${app.amap.cache.local-region-max-entries:256}") int localRegionMaxEntries,
            @Value("${app.amap.cache.local-weather-max-entries:256}") int localWeatherMaxEntries
    ) {
        this.objectMapper = objectMapper;
        this.amapQuotaConfigService = amapQuotaConfigService;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
        this.defaultRedisCacheEnabled = redisCacheEnabled;
        this.defaultRedisCachePrefix = normalizeRedisPrefix(redisCachePrefix);
        this.defaultDistrictCacheTtlMs = minutesToMs(regionTtlMinutes, DEFAULT_REGION_CACHE_TTL_MINUTES);
        this.defaultDistrictStaleTtlMs = minutesToMs(regionStaleMinutes, DEFAULT_REGION_STALE_TTL_MINUTES);
        this.defaultWeatherCacheTtlMs = minutesToMs(weatherTtlMinutes, DEFAULT_WEATHER_CACHE_TTL_MINUTES);
        this.defaultLocalRegionMaxEntries = normalizeMaxEntries(localRegionMaxEntries, DEFAULT_LOCAL_REGION_MAX_ENTRIES);
        this.defaultLocalWeatherMaxEntries = normalizeMaxEntries(localWeatherMaxEntries, DEFAULT_LOCAL_WEATHER_MAX_ENTRIES);
        this.districtLocalCache = createLocalCache(this.defaultRedisCacheEnabled, this.defaultLocalRegionMaxEntries);
        this.weatherLocalCache = createLocalCache(this.defaultRedisCacheEnabled, this.defaultLocalWeatherMaxEntries);
        this.cachePolicy = new CachePolicy(
                this.defaultRedisCacheEnabled,
                this.defaultRedisCachePrefix,
                this.defaultDistrictCacheTtlMs,
                this.defaultDistrictStaleTtlMs,
                this.defaultWeatherCacheTtlMs,
                this.defaultLocalRegionMaxEntries,
                this.defaultLocalWeatherMaxEntries
        );

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8000);
        factory.setReadTimeout(12000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    /** 地址输入提示。 */
    public Map<String, Object> inputTips(String appKey, String keywords, String city, String location, Boolean cityLimit) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("keywords", keywords);
        params.put("city", city);
        params.put("location", location);
        if (cityLimit != null) {
            params.put("citylimit", cityLimit ? "true" : "false");
        }
        params.put("datatype", "poi");
        return getJson(PATH_INPUT_TIPS, appKey, params);
    }

    @Override
    /** 地址转坐标。 */
    public Map<String, Object> geocode(String appKey, String address, String city) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("address", address);
        params.put("city", city);
        return getJson(PATH_GEOCODE, appKey, params);
    }

    @Override
    /** 坐标逆地理。 */
    public Map<String, Object> reverseGeocode(String appKey, Double longitude, Double latitude) {
        return reverseGeocodeWithMeta(appKey, longitude, latitude).getData();
    }

    @Override
    public AmapOpenService.AmapApiResult<Map<String, Object>> reverseGeocodeWithMeta(String appKey, Double longitude, Double latitude) {
        /*
         * 位置反查的业务入口。
         *
         * 这里返回的不只是 data，还会带回 cacheSource，
         * 方便上层判断这次命中的是本地缓存、Redis 还是远程高德接口。
         *
         * 之所以要给“附近田块展示/当前位置匹配”做缓存，是因为这类场景重复请求多，
         * 但真实地理结果在短时间内变化不大，直接走第三方接口既慢又浪费额度。
         */
        return reverseGeocodeWithMeta(
                appKey,
                longitude,
                latitude,
                REVERSE_GEOCODE_CACHE_GRID_METERS,
                CACHE_TYPE_REVERSE_GEOCODE,
                false
        );
    }

    @Override
    public Map<String, Object> reverseGeocodeForWeather(String appKey, Double longitude, Double latitude) {
        return reverseGeocodeForWeatherWithMeta(appKey, longitude, latitude).getData();
    }

    @Override
    public AmapOpenService.AmapApiResult<Map<String, Object>> reverseGeocodeForWeatherWithMeta(String appKey, Double longitude, Double latitude) {
        return reverseGeocodeWithMeta(
                appKey,
                longitude,
                latitude,
                WEATHER_REVERSE_GEOCODE_CACHE_GRID_METERS,
                CACHE_TYPE_REVERSE_GEOCODE_WEATHER,
                true
        );
    }

    private AmapOpenService.AmapApiResult<Map<String, Object>> reverseGeocodeWithMeta(
            String appKey,
            Double longitude,
            Double latitude,
            double gridMeters,
            String cacheType,
            boolean compactForWeather
    ) {
        /*
         * 位置缓存的关键点不在“有没有缓存”，而在“key 怎么设计”。
         *
         * 原始经纬度如果直接拿来做 key，会因为精度过细导致命中率很差。
         * 这里通过 gridMeters 把相近位置归到同一网格，换来更高的缓存复用率。
         *
         * compactForWeather=true 时只保留天气场景真正需要的字段，减少缓存体积。
         */
        CachePolicy policy = resolveCachePolicy();
        // 统一组装高德逆地理接口参数。
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("location", formatLocation(longitude, latitude));
        params.put("extensions", "base");
        // 根据经纬度网格化后的结果构造缓存键，提升相近位置的复用率。
        String cacheKey = buildReverseGeocodeCacheKey(longitude, latitude, gridMeters);
        if (StringUtils.hasText(cacheKey)) {
            // 先查缓存，命中时直接返回。
            CacheReadResult hotCache = readCacheValue(
                    cacheType,
                    cacheKey,
                    districtLocalCache,
                    policy,
                    policy.districtCacheTtlMs,
                    policy.districtStaleTtlMs,
                    false
            );
            if (hotCache != null) {
                return AmapOpenService.AmapApiResult.cached(hotCache.data, hotCache.cacheSource);
            }
        }
        try {
            // 缓存未命中时，才真实请求高德。
            Map<String, Object> data = getJson(PATH_REVERSE_GEOCODE, appKey, params);
            // 天气场景只需要部分地址字段，因此可以做一层瘦身，减小缓存体积。
            Map<String, Object> cachePayload = compactForWeather ? compactReverseGeocodePayload(data) : data;
            if (StringUtils.hasText(cacheKey)) {
                // 请求成功后，把结果写回缓存。
                putCacheValue(
                        cacheType,
                        cacheKey,
                        cachePayload,
                        districtLocalCache,
                        policy,
                        policy.districtCacheTtlMs,
                        policy.districtStaleTtlMs
                );
            }
            return AmapOpenService.AmapApiResult.remote(cachePayload);
        } catch (RuntimeException e) {
            // 如果是高德 QPS 限流异常，则允许读一份短暂过期的旧缓存兜底。
            if (StringUtils.hasText(cacheKey) && isQpsExceeded(e)) {
                CacheReadResult staleCache = readCacheValue(
                        cacheType,
                        cacheKey,
                        districtLocalCache,
                        policy,
                        policy.districtCacheTtlMs,
                        policy.districtStaleTtlMs,
                        true
                );
                if (staleCache != null) {
                    return AmapOpenService.AmapApiResult.cached(staleCache.data, staleCache.cacheSource);
                }
            }
            throw e;
        }
    }

    private Map<String, Object> compactReverseGeocodePayload(Map<String, Object> data) {
        Map<String, Object> source = data == null ? null : deepCopyMap(data);
        Map<String, Object> regeo = asMap(source == null ? null : source.get("regeocode"));
        if (regeo == null) {
            return source;
        }
        Map<String, Object> component = asMap(regeo.get("addressComponent"));
        Map<String, Object> streetNumber = asMap(component == null ? null : component.get("streetNumber"));

        Map<String, Object> compactStreet = new LinkedHashMap<>();
        putIfText(compactStreet, "street", streetNumber == null ? null : streetNumber.get("street"));
        putIfText(compactStreet, "number", streetNumber == null ? null : streetNumber.get("number"));

        Map<String, Object> compactComponent = new LinkedHashMap<>();
        putIfText(compactComponent, "province", component == null ? null : component.get("province"));
        putIfText(compactComponent, "city", component == null ? null : component.get("city"));
        putIfText(compactComponent, "district", component == null ? null : component.get("district"));
        putIfText(compactComponent, "township", component == null ? null : component.get("township"));
        putIfText(compactComponent, "adcode", component == null ? null : component.get("adcode"));
        if (!compactStreet.isEmpty()) {
            compactComponent.put("streetNumber", compactStreet);
        }

        Map<String, Object> compactRegeo = new LinkedHashMap<>();
        putIfText(compactRegeo, "formatted_address", regeo.get("formatted_address"));
        if (!compactComponent.isEmpty()) {
            compactRegeo.put("addressComponent", compactComponent);
        }

        Map<String, Object> compact = new LinkedHashMap<>();
        if (!compactRegeo.isEmpty()) {
            compact.put("regeocode", compactRegeo);
        }
        return compact.isEmpty() ? source : compact;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    private void putIfText(Map<String, Object> target, String key, Object value) {
        String text = asText(value);
        if (StringUtils.hasText(text)) {
            target.put(key, text);
        }
    }

    @Override
    /** 实时天气（缓存 1 小时）。 */
    public Map<String, Object> weatherLive(String appKey, String adcode) {
        return weatherLiveWithMeta(appKey, adcode).getData();
    }

    @Override
    public AmapOpenService.AmapApiResult<Map<String, Object>> weatherLiveWithMeta(String appKey, String adcode) {
        /*
         * 实时天气查询入口。
         *
         * 天气是按 adcode 查询的，天然比经纬度更适合做缓存键。
         * 首页天气快照通常不需要秒级刷新，因此这里直接用 TTL 缓存即可显著减少真实调用次数。
         */
        CachePolicy policy = resolveCachePolicy();
        // 天气查询按 adcode 组织参数。
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("city", adcode);
        params.put("extensions", "base");
        // adcode 比经纬度稳定，更适合做首页天气快照缓存键。
        String cacheKey = buildWeatherCacheKey(params);
        if (StringUtils.hasText(cacheKey)) {
            // 先读缓存，命中则不再请求高德。
            CacheReadResult hotCache = readCacheValue(
                    CACHE_TYPE_WEATHER,
                    cacheKey,
                    weatherLocalCache,
                    policy,
                    policy.weatherCacheTtlMs,
                    0L,
                    false
            );
            if (hotCache != null) {
                return AmapOpenService.AmapApiResult.cached(hotCache.data, hotCache.cacheSource);
            }
        }
        // 未命中时，再真实请求高德天气接口。
        Map<String, Object> data = getJson(PATH_WEATHER, appKey, params);
        if (StringUtils.hasText(cacheKey)) {
            // 回写缓存，减少首页重复刷新带来的真实调用。
            putCacheValue(
                    CACHE_TYPE_WEATHER,
                    cacheKey,
                    data,
                    weatherLocalCache,
                    policy,
                    policy.weatherCacheTtlMs,
                    0L
            );
        }
        return AmapOpenService.AmapApiResult.remote(data);
    }

    @Override
    /**
     * 行政区划检索。
     *
     * <p>策略：</p>
     * <p>1. 先读本地 LRU，再读 Redis；</p>
     * <p>2. 未命中才请求高德并回写双层缓存；</p>
     * <p>3. QPS 限流时允许回退到本地过期缓存（受 stale 窗口约束）。</p>
     */
    public Map<String, Object> districtSearch(String appKey, String keywords, Integer subdistrict, String filter, Integer page, Integer offset) {
        return districtSearchWithMeta(appKey, keywords, subdistrict, filter, page, offset).getData();
    }

    @Override
    public AmapOpenService.AmapApiResult<Map<String, Object>> districtSearchWithMeta(
            String appKey,
            String keywords,
            Integer subdistrict,
            String filter,
            Integer page,
            Integer offset
    ) {
        CachePolicy policy = resolveCachePolicy();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("keywords", keywords);
        params.put("subdistrict", subdistrict == null ? 1 : subdistrict);
        params.put("filter", filter);
        params.put("page", page == null ? 1 : page);
        params.put("offset", offset == null ? 100 : offset);
        params.put("extensions", "base");

        boolean cacheable = shouldCacheDistrictQuery(params);
        String cacheKey = cacheable ? buildDistrictCacheKey(params) : null;
        if (cacheable) {
            CacheReadResult hotCache = readCacheValue(
                    CACHE_TYPE_DISTRICT,
                    cacheKey,
                    districtLocalCache,
                    policy,
                    policy.districtCacheTtlMs,
                    policy.districtStaleTtlMs,
                    false
            );
            if (hotCache != null) {
                return AmapOpenService.AmapApiResult.cached(hotCache.data, hotCache.cacheSource);
            }
        }

        try {
            Map<String, Object> data = getJson(PATH_DISTRICT, appKey, params);
            if (cacheable) {
                putCacheValue(
                        CACHE_TYPE_DISTRICT,
                        cacheKey,
                        data,
                        districtLocalCache,
                        policy,
                        policy.districtCacheTtlMs,
                        policy.districtStaleTtlMs
                );
            }
            return AmapOpenService.AmapApiResult.remote(data);
        } catch (RuntimeException e) {
            if (cacheable && isQpsExceeded(e)) {
                CacheReadResult staleCache = readCacheValue(
                        CACHE_TYPE_DISTRICT,
                        cacheKey,
                        districtLocalCache,
                        policy,
                        policy.districtCacheTtlMs,
                        policy.districtStaleTtlMs,
                        true
                );
                if (staleCache != null) {
                    return AmapOpenService.AmapApiResult.cached(staleCache.data, staleCache.cacheSource);
                }
            }
            throw e;
        }
    }

    @Override
    /** 通过一次天气查询来校验 key 可用性。 */
    public void verifyKey(String appKey) {
        weatherLive(appKey, "110000");
    }

    @Override
    // 主动刷新运行时配置缓存（后台保存配置后可立即生效）。
    public void refreshRuntimeConfig() {
        cachePolicyExpireAtMs = 0L;
        qpsLimitCacheExpireAtMs = 0L;
    }

    @Override
    public Map<String, Object> clearRuntimeCache() {
        refreshRuntimeConfig();
        CachePolicy policy = resolveCachePolicy();
        int districtLocalCleared = districtLocalCache == null ? 0 : districtLocalCache.clear();
        int weatherLocalCleared = weatherLocalCache == null ? 0 : weatherLocalCache.clear();
        int redisCleared = clearRedisCacheKeys(policy);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("districtLocalCleared", districtLocalCleared);
        out.put("weatherLocalCleared", weatherLocalCleared);
        out.put("redisCleared", redisCleared);
        out.put("redisEnabled", isRedisCacheEnabled(policy));
        out.put("cacheRedisKeyPrefix", policy == null ? null : policy.redisPrefix);
        return out;
    }

    /** 经纬度格式化。 */
    private String formatLocation(Double longitude, Double latitude) {
        if (longitude == null || latitude == null) {
            throw new IllegalArgumentException("经度和纬度不能为空");
        }
        return String.format(Locale.ROOT, "%.7f,%.7f", longitude, latitude);
    }

    /**
     * 统一 GET JSON 入口，内置 QPS 节流与限流重试。
     */
    private Map<String, Object> getJson(String path, String appKey, Map<String, Object> params) {
        String key = StringUtils.hasText(appKey) ? appKey.trim() : "";
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("未配置高德密钥");
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(AMAP_BASE_URL + path)
                .queryParam("key", key)
                .queryParam("output", "JSON");
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String value = asText(entry.getValue());
            if (!StringUtils.hasText(value)) {
                continue;
            }
            builder.queryParam(entry.getKey(), value);
        }
        String url = builder.build().encode(StandardCharsets.UTF_8).toUriString();
        int qpsLimit = resolveQpsLimit();
        int attempt = 0;
        while (true) {
            attempt++;
            throttleBeforeRequest(qpsLimit);
            try {
                return doGetJson(url, path);
            } catch (RuntimeException re) {
                if (isQpsExceeded(re) && attempt <= MAX_QPS_RETRY) {
                    sleepQuietly(calculateQpsRetryDelayMs(qpsLimit, attempt));
                    continue;
                }
                throw re;
            }
        }
    }

    /** 执行 HTTP 调用并校验高德返回状态。 */
    private Map<String, Object> doGetJson(String url, String path) {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            // 额度统计以真实远程调用为锚点，缓存命中路径不会进入此处。
            recordQuotaCharge(path, 1);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("高德接口HTTP状态异常：" + response.getStatusCodeValue());
            }
            String body = response.getBody();
            if (!StringUtils.hasText(body)) {
                throw new IllegalStateException("高德接口返回为空");
            }
            Map<String, Object> data = objectMapper.readValue(body, MAP_TYPE);
            String status = asText(data.get("status"));
            if (!"1".equals(status)) {
                String message = asText(data.get("info"));
                String code = asText(data.get("infocode"));
                if (!StringUtils.hasText(message)) {
                    message = "高德接口调用失败";
                }
                if (StringUtils.hasText(code)) {
                    message = message + " (" + code + ")";
                }
                throw new IllegalStateException(message);
            }
            return data;
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new IllegalStateException("高德接口调用失败：" + e.getMessage(), e);
        }
    }

    /** 按接口路径映射计费分类并累加配额。 */
    private void recordQuotaCharge(String path, int delta) {
        if (amapQuotaConfigService == null || delta <= 0) {
            return;
        }
        String billingCategory = PATH_WEATHER.equals(path)
                ? AmapQuotaConfigService.BILLING_WEATHER
                : AmapQuotaConfigService.BILLING_LOCATION;
        try {
            amapQuotaConfigService.increaseUsageByBillingCategory(billingCategory, delta);
        } catch (Exception ignored) {
        }
    }

    /** 按配置 QPS 对请求节奏做串行限速。 */
    private void throttleBeforeRequest(int qpsLimit) {
        int limit = qpsLimit <= 0 ? DEFAULT_QPS_LIMIT : qpsLimit;
        long intervalMs = Math.max(1L, (1000L + limit - 1L) / limit);
        synchronized (requestPaceLock) {
            long now = System.currentTimeMillis();
            long waitMs = nextRequestAllowedAtMs - now;
            if (waitMs > 0L) {
                sleepQuietly(waitMs);
            }
            long startAt = System.currentTimeMillis();
            if (startAt < nextRequestAllowedAtMs) {
                startAt = nextRequestAllowedAtMs;
            }
            nextRequestAllowedAtMs = startAt + intervalMs;
        }
    }

    /** 计算限流重试等待时长。 */
    private long calculateQpsRetryDelayMs(int qpsLimit, int attempt) {
        int limit = qpsLimit <= 0 ? DEFAULT_QPS_LIMIT : qpsLimit;
        long base = Math.max(350L, (1000L + limit - 1L) / limit + 80L);
        return base * Math.max(1, attempt);
    }

    /** 解析并短期缓存 QPS 配置。 */
    private int resolveQpsLimit() {
        long now = System.currentTimeMillis();
        int localCache = cachedQpsLimit;
        if (now < qpsLimitCacheExpireAtMs && localCache > 0) {
            return localCache;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            if (now < qpsLimitCacheExpireAtMs && cachedQpsLimit > 0) {
                return cachedQpsLimit;
            }
            int resolved = DEFAULT_QPS_LIMIT;
            try {
                AmapQuotaConfig row = amapQuotaConfigService == null ? null : amapQuotaConfigService.getOrInitToday();
                if (row != null && row.getQpsLimit() != null && row.getQpsLimit() > 0) {
                    resolved = row.getQpsLimit();
                }
            } catch (Exception ignored) {
            }
            cachedQpsLimit = resolved;
            qpsLimitCacheExpireAtMs = now + QPS_LIMIT_CACHE_TTL_MS;
            return resolved;
        }
    }

    /** 解析并短期缓存缓存策略配置（数据库优先，YAML 兜底）。 */
    private CachePolicy resolveCachePolicy() {
        /*
         * 运行期缓存策略解析：
         * - 优先从数据库当天配置读取；
         * - 读不到时退回 YAML 默认值；
         * - 再按策略决定本地缓存容量和 Redis 开关。
         *
         * 这里不是把配置每次都查数据库，而是做一个短周期本地缓存，
         * 兼顾“后台配置更新能较快生效”和“运行期不要反复查库”。
         */
        long now = System.currentTimeMillis();
        // 先看短期本地缓存里是否还有未过期策略。
        CachePolicy local = cachePolicy;
        if (local != null && now < cachePolicyExpireAtMs) {
            return local;
        }
        synchronized (cachePolicyLock) {
            now = System.currentTimeMillis();
            local = cachePolicy;
            if (local != null && now < cachePolicyExpireAtMs) {
                return local;
            }
            // 真正重新解析运行时缓存策略。
            CachePolicy next = buildCachePolicyFromQuota();
            // 如果本地缓存容量配置发生变化，就重建本地缓存实例。
            if ((districtLocalCache == null ? -1 : districtLocalCache.maxEntries()) != next.localRegionMaxEntries) {
                districtLocalCache = createLocalCache(next.redisEnabled, next.localRegionMaxEntries);
            }
            if ((weatherLocalCache == null ? -1 : weatherLocalCache.maxEntries()) != next.localWeatherMaxEntries) {
                weatherLocalCache = createLocalCache(next.redisEnabled, next.localWeatherMaxEntries);
            }
            // 更新当前生效策略和过期时间。
            cachePolicy = next;
            cachePolicyExpireAtMs = now + RUNTIME_POLICY_CACHE_TTL_MS;
            return next;
        }
    }

    private BoundedLocalCache createLocalCache(boolean redisEnabled, int maxEntries) {
        if (redisEnabled || maxEntries <= 0) {
            return null;
        }
        return new BoundedLocalCache(maxEntries);
    }

    private CachePolicy buildCachePolicyFromQuota() {
        boolean redisEnabled = defaultRedisCacheEnabled;
        String redisPrefix = defaultRedisCachePrefix;
        long districtTtlMs = defaultDistrictCacheTtlMs;
        long districtStaleMs = defaultDistrictStaleTtlMs;
        long weatherTtlMs = defaultWeatherCacheTtlMs;
        int localRegionMaxEntries = defaultLocalRegionMaxEntries;
        int localWeatherMaxEntries = defaultLocalWeatherMaxEntries;
        try {
            AmapQuotaConfig row = amapQuotaConfigService == null ? null : amapQuotaConfigService.getOrInitToday();
            if (row != null) {
                if (row.getCacheRedisEnabled() != null) {
                    redisEnabled = row.getCacheRedisEnabled() == 1;
                }
                if (StringUtils.hasText(row.getCacheRedisKeyPrefix())) {
                    redisPrefix = normalizeRedisPrefix(row.getCacheRedisKeyPrefix());
                }
                if (row.getCacheRegionTtlMinutes() != null && row.getCacheRegionTtlMinutes() > 0) {
                    districtTtlMs = minutesToMs(row.getCacheRegionTtlMinutes(), DEFAULT_REGION_CACHE_TTL_MINUTES);
                }
                if (row.getCacheRegionStaleMinutes() != null && row.getCacheRegionStaleMinutes() > 0) {
                    districtStaleMs = minutesToMs(row.getCacheRegionStaleMinutes(), DEFAULT_REGION_STALE_TTL_MINUTES);
                }
                if (row.getCacheWeatherTtlMinutes() != null && row.getCacheWeatherTtlMinutes() > 0) {
                    weatherTtlMs = minutesToMs(row.getCacheWeatherTtlMinutes(), DEFAULT_WEATHER_CACHE_TTL_MINUTES);
                }
                if (row.getCacheLocalRegionMaxEntries() != null && row.getCacheLocalRegionMaxEntries() > 0) {
                    localRegionMaxEntries = normalizeMaxEntries(row.getCacheLocalRegionMaxEntries(), DEFAULT_LOCAL_REGION_MAX_ENTRIES);
                }
                if (row.getCacheLocalWeatherMaxEntries() != null && row.getCacheLocalWeatherMaxEntries() > 0) {
                    localWeatherMaxEntries = normalizeMaxEntries(row.getCacheLocalWeatherMaxEntries(), DEFAULT_LOCAL_WEATHER_MAX_ENTRIES);
                }
            }
        } catch (Exception ignored) {
        }
        if (redisEnabled) {
            localRegionMaxEntries = 0;
            localWeatherMaxEntries = 0;
        }
        return new CachePolicy(
                redisEnabled,
                redisPrefix,
                districtTtlMs,
                districtStaleMs,
                weatherTtlMs,
                localRegionMaxEntries,
                localWeatherMaxEntries
        );
    }

    /** 安全睡眠，处理中断标记。 */
    private void sleepQuietly(long millis) {
        if (millis <= 0L) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 判断区划请求是否适合缓存。
     *
     * <p>限制非结构化关键词进入缓存，防止随机长尾 query 放大内存占用。</p>
     */
    private boolean shouldCacheDistrictQuery(Map<String, Object> params) {
        String keywords = asText(params.get("keywords"));
        if (!StringUtils.hasText(keywords)) {
            return false;
        }
        if (keywords.length() > 40) {
            return false;
        }
        for (int i = 0; i < keywords.length(); i++) {
            char ch = keywords.charAt(i);
            boolean allowed =
                    (ch >= '0' && ch <= '9')
                            || (ch >= 'a' && ch <= 'z')
                            || (ch >= 'A' && ch <= 'Z')
                            || ch == '-' || ch == '_' || ch == ' ' || ch == '·'
                            || (ch >= 0x4E00 && ch <= 0x9FFF);
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    /** 对区划查询参数构造缓存键。 */
    private String buildDistrictCacheKey(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder("district");
        sb.append("|k=").append(asText(params.get("keywords")));
        sb.append("|sd=").append(asText(params.get("subdistrict")));
        sb.append("|f=").append(asText(params.get("filter")));
        sb.append("|p=").append(asText(params.get("page")));
        sb.append("|o=").append(asText(params.get("offset")));
        return sb.toString();
    }

    /** 天气查询缓存键。 */
    private String buildWeatherCacheKey(Map<String, Object> params) {
        String adcode = asText(params.get("city"));
        if (!StringUtils.hasText(adcode)) {
            return null;
        }
        return "weather|city=" + adcode;
    }

    /** 逆地理缓存键：按区域网格归一坐标，减少同一区域内的重复请求。 */
    private String buildReverseGeocodeCacheKey(Double longitude, Double latitude, double gridMeters) {
        if (longitude == null || latitude == null) {
            return null;
        }
        double normalizedLongitude = normalizeCoordinate(longitude);
        double normalizedLatitude = normalizeCoordinate(latitude);
        long latitudeBucket = resolveLatitudeBucket(normalizedLatitude, gridMeters);
        long longitudeBucket = resolveLongitudeBucket(normalizedLongitude, normalizedLatitude, gridMeters);
        return String.format(
                Locale.ROOT,
                "regeo|grid=%.0fm|latBucket=%d|lngBucket=%d|extensions=base",
                gridMeters,
                latitudeBucket,
                longitudeBucket
        );
    }

    private double normalizeCoordinate(Double value) {
        return Math.round((value == null ? 0D : value) * 10_000D) / 10_000D;
    }

    private long resolveLatitudeBucket(double latitude, double gridMeters) {
        double latitudeStep = Math.max(gridMeters, 50D) / 111_000D;
        return (long) Math.floor((latitude + 90D) / latitudeStep);
    }

    private long resolveLongitudeBucket(double longitude, double latitude, double gridMeters) {
        double latitudeRad = Math.toRadians(latitude);
        double longitudeMetersPerDegree = Math.max(111_000D * Math.cos(latitudeRad), 10_000D);
        double longitudeStep = Math.max(gridMeters, 50D) / longitudeMetersPerDegree;
        return (long) Math.floor((longitude + 180D) / longitudeStep);
    }

    /** 读取缓存（本地优先，其次 Redis）。 */
    private CacheReadResult readCacheValue(
            String cacheType,
            String cacheKey,
            BoundedLocalCache localCache,
            CachePolicy policy,
            long ttlMs,
            long staleTtlMs,
            boolean allowExpired
    ) {
        /*
         * 统一读缓存：
         * 1. 先读本地缓存；
         * 2. 本地没有再读 Redis；
         * 3. Redis 命中后再回填本地缓存。
         *
         * allowExpired=true 只用于“高德限流时允许拿短暂过期数据兜底”的场景，
         * 平常正常查询不会读过期值。
         */
        if (!StringUtils.hasText(cacheKey) || policy == null) {
            return null;
        }
        if (localCache != null) {
            // 第一层先读本地缓存，适合单机热点加速。
            BoundedLocalCache.CacheLookup local = localCache.getWithMeta(cacheKey, allowExpired);
            if (local != null && local.data != null) {
                String source = local.stale ? CACHE_SOURCE_LOCAL_STALE : CACHE_SOURCE_LOCAL;
                return new CacheReadResult(deepCopyMap(local.data), source);
            }
        }
        // allowExpired=true 只表示允许从本地取 stale，不会再去 Redis 读过期值。
        if (allowExpired) {
            return null;
        }
        // 第二层再读 Redis，适合跨实例共享缓存。
        Map<String, Object> redisData = getRedisCacheValue(cacheType, cacheKey, policy);
        if (redisData == null) {
            return null;
        }
        if (localCache != null) {
            // Redis 命中后顺便回填本地缓存，降低下次读取成本。
            localCache.put(cacheKey, redisData, ttlMs, staleTtlMs);
        }
        return new CacheReadResult(deepCopyMap(redisData), CACHE_SOURCE_REDIS);
    }

    /** 写入缓存（本地 + Redis）。 */
    private void putCacheValue(
            String cacheType,
            String cacheKey,
            Map<String, Object> data,
            BoundedLocalCache localCache,
            CachePolicy policy,
            long ttlMs,
            long staleTtlMs
    ) {
        /*
         * 统一写缓存：
         * - 本地缓存更偏单机热点加速；
         * - Redis 更偏跨请求、跨实例共享。
         *
         * 当前项目量级下，这里主要目标是降低真实高德请求次数，而不是构建复杂缓存平台。
         */
        if (!StringUtils.hasText(cacheKey) || data == null || data.isEmpty() || policy == null) {
            return;
        }
        if (localCache != null) {
            // 先写本地缓存，让当前实例后续直接受益。
            localCache.put(cacheKey, data, ttlMs, staleTtlMs);
        }
        // 再写 Redis，让多实例共享这份第三方结果。
        putRedisCacheValue(cacheType, cacheKey, data, ttlMs, policy);
    }

    private Map<String, Object> getRedisCacheValue(String cacheType, String cacheKey, CachePolicy policy) {
        /*
         * Redis 读取失败时只记日志，不向上抛异常。
         * 原因很直接：高德缓存是优化项，不能因为缓存层失败把主业务直接打死。
         */
        if (!isRedisCacheEnabled(policy)) {
            return null;
        }
        // Redis 键会带上缓存类型前缀，避免不同业务缓存互相污染。
        String redisKey = buildRedisCacheKey(cacheType, cacheKey, policy);
        if (!StringUtils.hasText(redisKey)) {
            return null;
        }
        try {
            String json = stringRedisTemplate.opsForValue().get(redisKey);
            if (!StringUtils.hasText(json)) {
                return null;
            }
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            log.warn("Amap redis cache read failed, type={}, key={}, err={}", cacheType, redisKey, e.getMessage());
            return null;
        }
    }

    private void putRedisCacheValue(String cacheType, String cacheKey, Map<String, Object> data, long ttlMs, CachePolicy policy) {
        /*
         * 这里用 setIfAbsent 而不是无脑覆盖，目的是减少相同热点 key 被并发重复覆盖的写放大。
         * 对当前“第三方接口结果缓存”这个场景来说，谁先写进去都可以接受，不要求强一致覆盖。
         */
        if (!isRedisCacheEnabled(policy) || data == null || data.isEmpty()) {
            return;
        }
        String redisKey = buildRedisCacheKey(cacheType, cacheKey, policy);
        if (!StringUtils.hasText(redisKey)) {
            return;
        }
        // Redis TTL 统一换算成秒。
        long finalTtl = Math.max(1L, (Math.max(1000L, ttlMs) + 999L) / 1000L);
        try {
            String json = objectMapper.writeValueAsString(data);
            // 使用 setIfAbsent，减少同一个热点 key 被并发重复覆盖。
            Boolean written = stringRedisTemplate.opsForValue().setIfAbsent(redisKey, json, finalTtl, TimeUnit.SECONDS);
            if (Boolean.FALSE.equals(written)) {
                return;
            }
        } catch (Exception e) {
            log.warn("Amap redis cache write failed, type={}, key={}, err={}", cacheType, redisKey, e.getMessage());
        }
    }

    private boolean isRedisCacheEnabled(CachePolicy policy) {
        return policy != null && policy.redisEnabled && stringRedisTemplate != null;
    }

    private int clearRedisCacheKeys(CachePolicy policy) {
        if (!isRedisCacheEnabled(policy) || !StringUtils.hasText(policy.redisPrefix)) {
            return 0;
        }
        try {
            RedisCallback<Integer> callback = connection -> {
                int deleted = 0;
                ScanOptions options = ScanOptions.scanOptions().match(policy.redisPrefix + "*").count(200).build();
                try (Cursor<byte[]> cursor = connection.scan(options)) {
                    while (cursor != null && cursor.hasNext()) {
                        byte[] key = cursor.next();
                        if (key == null || key.length == 0) {
                            continue;
                        }
                        Long affected = connection.del(key);
                        if (affected != null && affected > 0) {
                            deleted += affected.intValue();
                        }
                    }
                }
                return deleted;
            };
            Integer cleared = stringRedisTemplate.execute(callback);
            return cleared == null ? 0 : cleared;
        } catch (Exception e) {
            log.warn("Amap redis cache clear failed, prefix={}, err={}", policy.redisPrefix, e.getMessage());
            return 0;
        }
    }

    private String buildRedisCacheKey(String cacheType, String cacheKey, CachePolicy policy) {
        String type = String.valueOf(cacheType == null ? "" : cacheType).trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(type) || !StringUtils.hasText(cacheKey) || policy == null || !StringUtils.hasText(policy.redisPrefix)) {
            return null;
        }
        return policy.redisPrefix + type + ":" + digestHex(cacheKey);
    }

    private String digestHex(String text) {
        String source = String.valueOf(text == null ? "" : text);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) {
                int v = b & 0xFF;
                if (v < 16) {
                    hex.append('0');
                }
                hex.append(Integer.toHexString(v));
            }
            return hex.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(source.hashCode());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopyMap(Map<String, Object> src) {
        if (src == null) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> it : src.entrySet()) {
            out.put(it.getKey(), deepCopyValue(it.getValue()));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Object deepCopyValue(Object value) {
        if (value instanceof Map) {
            Map<String, Object> src = (Map<String, Object>) value;
            return deepCopyMap(src);
        }
        if (value instanceof List) {
            List<Object> src = (List<Object>) value;
            List<Object> out = new ArrayList<>(src.size());
            for (Object item : src) {
                out.add(deepCopyValue(item));
            }
            return out;
        }
        if (value instanceof Number || value instanceof Boolean || value == null) {
            return value;
        }
        return String.valueOf(value);
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /** 判断错误是否为高德 QPS 超限。 */
    private boolean isQpsExceeded(Throwable e) {
        if (e == null) {
            return false;
        }
        String msg = e.getMessage();
        if (msg == null) {
            return false;
        }
        String lower = msg.toLowerCase();
        return lower.contains("10021") || lower.contains("cuqps_has_exceeded_the_limit");
    }

    private long minutesToMs(long minutes, long fallbackMinutes) {
        long source = minutes > 0L ? minutes : fallbackMinutes;
        return Math.max(1000L, source * 60_000L);
    }

    private int normalizeMaxEntries(int value, int fallback) {
        int source = value > 0 ? value : fallback;
        return Math.max(32, source);
    }

    private String normalizeRedisPrefix(String prefix) {
        String raw = String.valueOf(prefix == null ? "" : prefix).trim();
        if (!StringUtils.hasText(raw)) {
            return DEFAULT_REDIS_PREFIX;
        }
        return raw;
    }

    /**
     * 有界本地缓存（LRU + TTL）。
     *
     * <p>设计目标：</p>
     * <p>1. 最大条目数可控，避免内存随请求无限增长；</p>
     * <p>2. 访问顺序淘汰，热点条目保留；</p>
     * <p>3. 过期条目自动清理，stale 窗口仅用于兜底读取。</p>
     */
    private static class BoundedLocalCache {
        private final int maxEntries;
        private final LinkedHashMap<String, CacheEntry> rows;

        private BoundedLocalCache(int maxEntries) {
            this.maxEntries = Math.max(32, maxEntries);
            this.rows = new LinkedHashMap<>(128, 0.75f, true);
        }

        private int maxEntries() {
            return maxEntries;
        }

        private synchronized CacheLookup getWithMeta(String key, boolean allowExpired) {
            /*
             * 本地缓存除了正常 TTL，还额外保留 stale 窗口。
             * stale 数据平时不读，只在上游高德接口被 QPS 限流时作为短暂降级兜底。
             */
            if (!StringUtils.hasText(key)) {
                return null;
            }
            long now = System.currentTimeMillis();
            CacheEntry entry = rows.get(key);
            if (entry == null || entry.data == null) {
                prune(now);
                return null;
            }
            if (now <= entry.expireAt) {
                return new CacheLookup(entry.data, false);
            }
            if (allowExpired && now <= entry.staleExpireAt) {
                return new CacheLookup(entry.data, true);
            }
            rows.remove(key);
            prune(now);
            return null;
        }

        private synchronized void put(String key, Map<String, Object> data, long ttlMs, long staleTtlMs) {
            if (!StringUtils.hasText(key) || data == null || data.isEmpty()) {
                return;
            }
            long now = System.currentTimeMillis();
            CacheEntry entry = new CacheEntry();
            entry.expireAt = now + Math.max(1000L, ttlMs);
            long staleWindow = Math.max(0L, staleTtlMs);
            entry.staleExpireAt = entry.expireAt + staleWindow;
            entry.data = data;
            rows.put(key, entry);
            evictOverflow();
            prune(now);
        }

        private synchronized int clear() {
            int size = rows.size();
            rows.clear();
            return size;
        }

        private void evictOverflow() {
            while (rows.size() > maxEntries) {
                Iterator<Map.Entry<String, CacheEntry>> it = rows.entrySet().iterator();
                if (!it.hasNext()) {
                    return;
                }
                it.next();
                it.remove();
            }
        }

        private void prune(long nowMs) {
            Iterator<Map.Entry<String, CacheEntry>> it = rows.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, CacheEntry> row = it.next();
                CacheEntry entry = row.getValue();
                if (entry == null || entry.data == null || nowMs > entry.staleExpireAt) {
                    it.remove();
                }
            }
        }

        private static class CacheLookup {
            private final Map<String, Object> data;
            private final boolean stale;

            private CacheLookup(Map<String, Object> data, boolean stale) {
                this.data = data;
                this.stale = stale;
            }
        }
    }

    private static class CacheEntry {
        private long expireAt;
        private long staleExpireAt;
        private Map<String, Object> data;
    }

    private static class CacheReadResult {
        private final Map<String, Object> data;
        private final String cacheSource;

        private CacheReadResult(Map<String, Object> data, String cacheSource) {
            this.data = data;
            this.cacheSource = cacheSource;
        }
    }

    private static class CachePolicy {
        private final boolean redisEnabled;
        private final String redisPrefix;
        private final long districtCacheTtlMs;
        private final long districtStaleTtlMs;
        private final long weatherCacheTtlMs;
        private final int localRegionMaxEntries;
        private final int localWeatherMaxEntries;

        private CachePolicy(
                boolean redisEnabled,
                String redisPrefix,
                long districtCacheTtlMs,
                long districtStaleTtlMs,
                long weatherCacheTtlMs,
                int localRegionMaxEntries,
                int localWeatherMaxEntries
        ) {
            this.redisEnabled = redisEnabled;
            this.redisPrefix = redisPrefix;
            this.districtCacheTtlMs = districtCacheTtlMs;
            this.districtStaleTtlMs = districtStaleTtlMs;
            this.weatherCacheTtlMs = weatherCacheTtlMs;
            this.localRegionMaxEntries = localRegionMaxEntries;
            this.localWeatherMaxEntries = localWeatherMaxEntries;
        }
    }
}
