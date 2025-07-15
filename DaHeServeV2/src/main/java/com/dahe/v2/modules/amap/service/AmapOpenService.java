package com.dahe.v2.modules.amap.service;

import java.util.Map;

public interface AmapOpenService {

    /** 地址输入提示。 */
    Map<String, Object> inputTips(String appKey, String keywords, String city, String location, Boolean cityLimit);

    /** 地址转坐标。 */
    Map<String, Object> geocode(String appKey, String address, String city);

    /** 坐标逆地理。 */
    Map<String, Object> reverseGeocode(String appKey, Double longitude, Double latitude);

    /** 坐标逆地理（含缓存元信息）。 */
    AmapApiResult<Map<String, Object>> reverseGeocodeWithMeta(String appKey, Double longitude, Double latitude);

    /** 天气快照链路专用逆地理，允许使用更粗的区域缓存桶。 */
    Map<String, Object> reverseGeocodeForWeather(String appKey, Double longitude, Double latitude);

    /** 天气快照链路专用逆地理（含缓存元信息）。 */
    AmapApiResult<Map<String, Object>> reverseGeocodeForWeatherWithMeta(String appKey, Double longitude, Double latitude);

    /** 实时天气查询（兼容旧调用，不含缓存元信息）。 */
    Map<String, Object> weatherLive(String appKey, String adcode);

    /**
     * 实时天气查询（含缓存元信息）。
     * <p>用于审计与额度计费判断：缓存命中不计费，且可区分 local/redis/stale。</p>
     */
    AmapApiResult<Map<String, Object>> weatherLiveWithMeta(String appKey, String adcode);

    /** 行政区划检索（兼容旧调用，不含缓存元信息）。 */
    Map<String, Object> districtSearch(String appKey, String keywords, Integer subdistrict, String filter, Integer page, Integer offset);

    /**
     * 行政区划检索（含缓存元信息）。
     * <p>用于审计与额度计费判断：缓存命中不计费，且可区分 local/redis/stale。</p>
     */
    AmapApiResult<Map<String, Object>> districtSearchWithMeta(
            String appKey,
            String keywords,
            Integer subdistrict,
            String filter,
            Integer page,
            Integer offset
    );

    /** 校验 key 可用性。 */
    void verifyKey(String appKey);

    /** 刷新运行时配置缓存（QPS/缓存策略）。 */
    void refreshRuntimeConfig();

    /** 清空高德运行时缓存（本地缓存 + Redis 缓存）。 */
    Map<String, Object> clearRuntimeCache();

    /** 高德请求结果（含缓存命中信息与额度计费标识）。 */
    class AmapApiResult<T> {
        private final T data;
        private final boolean cacheHit;
        private final String cacheSource;
        private final boolean quotaChargeable;

        public AmapApiResult(T data, boolean cacheHit, String cacheSource, boolean quotaChargeable) {
            this.data = data;
            this.cacheHit = cacheHit;
            this.cacheSource = cacheSource;
            this.quotaChargeable = quotaChargeable;
        }

        public T getData() {
            return data;
        }

        public boolean isCacheHit() {
            return cacheHit;
        }

        public String getCacheSource() {
            return cacheSource;
        }

        public boolean isQuotaChargeable() {
            return quotaChargeable;
        }

        public static <T> AmapApiResult<T> remote(T data) {
            return new AmapApiResult<>(data, false, "none", true);
        }

        public static <T> AmapApiResult<T> cached(T data, String cacheSource) {
            return new AmapApiResult<>(data, true, cacheSource == null ? "unknown" : cacheSource, false);
        }
    }
}
