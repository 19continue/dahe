package com.dahe.v2.modules.amap.service.impl;

import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.amap.model.AmapApiAudit;
import com.dahe.v2.modules.amap.service.AmapApiAuditService;
import com.dahe.v2.modules.amap.service.AmapOpenService;
import com.dahe.v2.modules.amap.service.AmapQuotaConfigService;
import com.dahe.v2.modules.auth.support.AuthContext;
import com.dahe.v2.modules.user.model.AppUser;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
/**
 * 高德能力应用服务。
 *
 * <p>职责：承载小程序 AMap 业务编排与审计写入。</p>
 */
public class AmapOpenApplicationService {

    private static final String REQUEST_SOURCE_BACKEND_PROXY = "backend-proxy";
    private static final String REQUEST_SOURCE_CACHE_PREFIX = "backend-proxy-cache:";
    private static final String REQUEST_SOURCE_MIXED_PREFIX = "backend-proxy-mixed:";

    private final AmapQuotaConfigService amapQuotaConfigService;
    private final AmapApiAuditService amapApiAuditService;
    private final AmapOpenService amapOpenService;

    public AmapOpenApplicationService(
            AmapQuotaConfigService amapQuotaConfigService,
            AmapApiAuditService amapApiAuditService,
            AmapOpenService amapOpenService
    ) {
        this.amapQuotaConfigService = amapQuotaConfigService;
        this.amapApiAuditService = amapApiAuditService;
        this.amapOpenService = amapOpenService;
    }

    /** 客户端审计上报入口。 */
    public Result<Void> reportAudit(HttpServletRequest request, AuditReq req) {
        if (req == null) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "审计参数不能为空");
        }
        try {
            AmapApiAudit row = new AmapApiAudit();
            row.setRecordDate(LocalDate.now());
            AppUser user = AuthContext.getCurrentUser(request);
            if (user != null) {
                row.setUserId(user.getId());
                row.setOperatorName(user.getRealName());
            }
            row.setBizScene(req.getBizScene());
            row.setApiType(resolveBillingApiType(req.getApiType(), req.getApiPath(), req.getBizScene()));
            row.setApiPath(req.getApiPath());
            row.setRequestSource(normalizeRequestSource(req.getRequestSource()));
            row.setSuccessFlag(Boolean.TRUE.equals(req.getSuccess()) ? 1 : 0);
            row.setCostMs(req.getCostMs());
            row.setErrorMessage(safeText(req.getErrorMessage()));
            amapApiAuditService.save(row);
            return Result.success(null);
        } catch (Exception e) {
            return tableOrServerError(e);
        }
    }

    /** 地址输入提示。 */
    public Result<List<AddressTipItem>> addressTips(
            HttpServletRequest request,
            String keywords,
            String city,
            Double longitude,
            Double latitude,
            Boolean cityLimit,
            int limit
    ) {
        String appKey = amapQuotaConfigService.resolveBoundAppKey();
        if (!StringUtils.hasText(appKey)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "未配置高德密钥");
        }
        String location = null;
        if (longitude != null && latitude != null) {
            location = String.format(java.util.Locale.ROOT, "%.7f,%.7f", longitude, latitude);
        }
        long startedAt = System.currentTimeMillis();
        try {
            Map<String, Object> data = amapOpenService.inputTips(appKey, keywords, city, location, cityLimit);
            List<AddressTipItem> out = parseTips(data, limit);
            recordProxyAudit(request, "address_tips", "geocode", "/v3/assistant/inputtips", true, startedAt, null);
            return Result.success(out);
        } catch (Exception e) {
            String message = shortenError(e);
            recordProxyAudit(request, "address_tips", "geocode", "/v3/assistant/inputtips", false, startedAt, message);
            return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), message);
        }
    }

    /** 坐标逆地理解析。 */
    public Result<NormalizedAddress> reverseGeocode(
            HttpServletRequest request,
            Double longitude,
            Double latitude
    ) {
        String appKey = amapQuotaConfigService.resolveBoundAppKey();
        if (!StringUtils.hasText(appKey)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "未配置高德密钥");
        }
        long startedAt = System.currentTimeMillis();
        try {
            AmapOpenService.AmapApiResult<Map<String, Object>> result = amapOpenService.reverseGeocodeWithMeta(appKey, longitude, latitude);
            NormalizedAddress out = parseRegeo(result.getData(), longitude, latitude);
            recordProxyAudit(
                    request,
                    "reverse_geocode",
                    "geocode",
                    "/v3/geocode/regeo",
                    true,
                    startedAt,
                    toCacheRequestSource(result.getCacheSource(), result.isCacheHit()),
                    null
            );
            return Result.success(out);
        } catch (Exception e) {
            String message = shortenError(e);
            recordProxyAudit(request, "reverse_geocode", "geocode", "/v3/geocode/regeo", false, startedAt, message);
            return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), message);
        }
    }

    /** 地址地理编码，并尝试补全逆地理信息。 */
    public Result<NormalizedAddress> geocode(
            HttpServletRequest request,
            String address,
            String city
    ) {
        String appKey = amapQuotaConfigService.resolveBoundAppKey();
        if (!StringUtils.hasText(appKey)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "未配置高德密钥");
        }
        long startedAt = System.currentTimeMillis();
        try {
            Map<String, Object> geocodeData = amapOpenService.geocode(appKey, address, city);
            NormalizedAddress out = parseGeoToNormalized(geocodeData, address);
            if (out != null && out.getLongitude() != null && out.getLatitude() != null) {
                try {
                    Map<String, Object> regeoData = amapOpenService.reverseGeocode(appKey, out.getLongitude(), out.getLatitude());
                    NormalizedAddress regeo = parseRegeo(regeoData, out.getLongitude(), out.getLatitude());
                    if (regeo != null) {
                        if (StringUtils.hasText(regeo.getFormattedAddress())) {
                            out.setFormattedAddress(regeo.getFormattedAddress());
                        }
                        out.setProvince(firstNonEmpty(regeo.getProvince(), out.getProvince()));
                        out.setCity(firstNonEmpty(regeo.getCity(), out.getCity()));
                        out.setDistrict(firstNonEmpty(regeo.getDistrict(), out.getDistrict()));
                        out.setTownship(firstNonEmpty(regeo.getTownship(), out.getTownship()));
                        out.setStreet(firstNonEmpty(regeo.getStreet(), out.getStreet()));
                        out.setStreetNumber(firstNonEmpty(regeo.getStreetNumber(), out.getStreetNumber()));
                        out.setAdcode(firstNonEmpty(regeo.getAdcode(), out.getAdcode()));
                    }
                } catch (Exception ignored) {
                }
            }
            recordProxyAudit(request, "geocode", "geocode", "/v3/geocode/geo", true, startedAt, null);
            return Result.success(out);
        } catch (Exception e) {
            String message = shortenError(e);
            recordProxyAudit(request, "geocode", "geocode", "/v3/geocode/geo", false, startedAt, message);
            return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), message);
        }
    }

    /** 省级区划选项。 */
    public Result<List<RegionOptionItem>> provinceOptions(
            HttpServletRequest request,
            String keyword,
            int limit
    ) {
        String appKey = amapQuotaConfigService.resolveBoundAppKey();
        if (!StringUtils.hasText(appKey)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "未配置高德密钥");
        }
        long startedAt = System.currentTimeMillis();
        UsageStats usage = new UsageStats();
        try {
            String query = StringUtils.hasText(keyword) ? keyword.trim() : "中国";
            AmapOpenService.AmapApiResult<Map<String, Object>> first = amapOpenService.districtSearchWithMeta(appKey, query, 1, null, 1, 100);
            usage.collect(first);
            Map<String, Object> data = first.getData();
            List<RegionOptionItem> out = collectRegionOptions(data, asLevelSet("province"), limit);
            if (out.isEmpty()) {
                out = collectDirectChildren(data, asLevelSet("province"), limit);
            }
            if (out.isEmpty()) {
                AmapOpenService.AmapApiResult<Map<String, Object>> second = amapOpenService.districtSearchWithMeta(appKey, query, 2, null, 1, 100);
                usage.collect(second);
                data = second.getData();
                out = collectRegionOptions(data, asLevelSet("province"), limit);
            }
            if (out.isEmpty()) {
                out = collectDirectChildren(data, asLevelSet("province"), limit);
            }
            if (out.isEmpty()) {
                out = defaultProvinceOptions(keyword, limit);
            }
            recordProxyAudit(request, "region_provinces", "city", "/v3/config/district", true, startedAt, usage.requestSource(), null);
            return Result.success(out);
        } catch (Exception e) {
            String message = shortenError(e);
            recordProxyAudit(request, "region_provinces", "city", "/v3/config/district", false, startedAt, usage.requestSource(), message);
            if (isQpsExceededMessage(message)) {
                return Result.success(defaultProvinceOptions(keyword, limit));
            }
            return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), message);
        }
    }

    /** 市级区划选项。 */
    public Result<List<RegionOptionItem>> cityOptions(
            HttpServletRequest request,
            String province,
            int limit
    ) {
        String appKey = amapQuotaConfigService.resolveBoundAppKey();
        if (!StringUtils.hasText(appKey)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "未配置高德密钥");
        }
        if (!StringUtils.hasText(province)) {
            return Result.success(new ArrayList<>());
        }
        long startedAt = System.currentTimeMillis();
        UsageStats usage = new UsageStats();
        try {
            String provinceName = normalizeRegionKeyword(province);
            if (!StringUtils.hasText(provinceName)) {
                return Result.success(new ArrayList<>());
            }
            String provinceQuery = provinceName;
            if (!provinceQuery.matches("^\\d{6}$")) {
                String provinceAdcodeByName = resolveDefaultProvinceAdcode(provinceQuery);
                if (StringUtils.hasText(provinceAdcodeByName)) {
                    provinceQuery = provinceAdcodeByName;
                }
            }
            Map<String, Object> data;
            List<RegionOptionItem> out;
            AmapOpenService.AmapApiResult<Map<String, Object>> first = amapOpenService.districtSearchWithMeta(appKey, provinceQuery, 2, null, 1, 100);
            usage.collect(first);
            data = first.getData();
            out = collectRegionOptions(data, asLevelSet("city"), limit);
            if (out.isEmpty()) {
                out = collectDirectChildren(data, asLevelSet("city"), limit);
            }
            if (out.isEmpty()) {
                String provinceAdcode = provinceQuery.matches("^\\d{6}$")
                        ? provinceQuery
                        : resolveAdcode(appKey, provinceName);
                if (StringUtils.hasText(provinceAdcode)) {
                    AmapOpenService.AmapApiResult<Map<String, Object>> second = amapOpenService.districtSearchWithMeta(appKey, "中国", 1, provinceAdcode, 1, 100);
                    usage.collect(second);
                    data = second.getData();
                    out = collectRegionOptions(data, asLevelSet("city"), limit);
                    if (out.isEmpty()) {
                        out = collectDirectChildren(data, asLevelSet("city"), limit);
                    }
                }
            }
            if (out.isEmpty()) {
                out = collectRegionOptions(data, asLevelSet("city"), limit);
                if (out.isEmpty()) {
                    out = collectDirectChildren(data, asLevelSet("city"), limit);
                }
            }
            recordProxyAudit(request, "region_cities", "city", "/v3/config/district", true, startedAt, usage.requestSource(), null);
            return Result.success(out);
        } catch (Exception e) {
            String message = shortenError(e);
            recordProxyAudit(request, "region_cities", "city", "/v3/config/district", false, startedAt, usage.requestSource(), message);
            if (isQpsExceededMessage(message)) {
                return Result.success(new ArrayList<>());
            }
            return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), message);
        }
    }

    /** 区县级区划选项。 */
    public Result<List<RegionOptionItem>> districtOptions(
            HttpServletRequest request,
            String city,
            int limit
    ) {
        String appKey = amapQuotaConfigService.resolveBoundAppKey();
        if (!StringUtils.hasText(appKey)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "未配置高德密钥");
        }
        if (!StringUtils.hasText(city)) {
            return Result.success(new ArrayList<>());
        }
        long startedAt = System.currentTimeMillis();
        UsageStats usage = new UsageStats();
        try {
            String cityName = normalizeRegionKeyword(city);
            if (!StringUtils.hasText(cityName)) {
                return Result.success(new ArrayList<>());
            }
            Map<String, Object> data;
            List<RegionOptionItem> out;
            AmapOpenService.AmapApiResult<Map<String, Object>> first = amapOpenService.districtSearchWithMeta(appKey, cityName, 2, null, 1, 100);
            usage.collect(first);
            data = first.getData();
            out = collectRegionOptions(data, asLevelSet("district"), limit);
            if (out.isEmpty()) {
                out = collectDirectChildren(data, asLevelSet("district"), limit);
            }
            if (out.isEmpty()) {
                String cityAdcode = resolveAdcode(appKey, cityName);
                if (StringUtils.hasText(cityAdcode)) {
                    AmapOpenService.AmapApiResult<Map<String, Object>> second = amapOpenService.districtSearchWithMeta(appKey, "中国", 1, cityAdcode, 1, 100);
                    usage.collect(second);
                    data = second.getData();
                    out = collectRegionOptions(data, asLevelSet("district"), limit);
                    if (out.isEmpty()) {
                        out = collectDirectChildren(data, asLevelSet("district"), limit);
                    }
                }
            }
            if (out.isEmpty()) {
                out = collectRegionOptions(data, asLevelSet("district"), limit);
                if (out.isEmpty()) {
                    out = collectDirectChildren(data, asLevelSet("district"), limit);
                }
            }
            recordProxyAudit(request, "region_districts", "city", "/v3/config/district", true, startedAt, usage.requestSource(), null);
            return Result.success(out);
        } catch (Exception e) {
            String message = shortenError(e);
            recordProxyAudit(request, "region_districts", "city", "/v3/config/district", false, startedAt, usage.requestSource(), message);
            if (isQpsExceededMessage(message)) {
                return Result.success(new ArrayList<>());
            }
            return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), message);
        }
    }

    /** 乡镇/街道级区划选项。 */
    public Result<List<RegionOptionItem>> townshipOptions(
            HttpServletRequest request,
            String district,
            int limit
    ) {
        String appKey = amapQuotaConfigService.resolveBoundAppKey();
        if (!StringUtils.hasText(appKey)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "未配置高德密钥");
        }
        if (!StringUtils.hasText(district)) {
            return Result.success(new ArrayList<>());
        }
        long startedAt = System.currentTimeMillis();
        UsageStats usage = new UsageStats();
        try {
            String districtName = normalizeRegionKeyword(district);
            if (!StringUtils.hasText(districtName)) {
                return Result.success(new ArrayList<>());
            }
            Map<String, Object> data;
            List<RegionOptionItem> out;
            AmapOpenService.AmapApiResult<Map<String, Object>> first = amapOpenService.districtSearchWithMeta(appKey, districtName, 3, null, 1, 100);
            usage.collect(first);
            data = first.getData();
            out = collectRegionOptions(data, asLevelSet("street", "township"), limit);
            if (out.isEmpty()) {
                out = collectDirectChildren(data, asLevelSet("street", "township"), limit);
            }
            if (out.isEmpty()) {
                AmapOpenService.AmapApiResult<Map<String, Object>> second = amapOpenService.districtSearchWithMeta(appKey, districtName, 2, null, 1, 100);
                usage.collect(second);
                data = second.getData();
                out = collectRegionOptions(data, asLevelSet("street", "township"), limit);
                if (out.isEmpty()) {
                    out = collectDirectChildren(data, asLevelSet("street", "township"), limit);
                }
            }
            if (out.isEmpty()) {
                String districtAdcode = resolveAdcode(appKey, districtName);
                if (StringUtils.hasText(districtAdcode)) {
                    AmapOpenService.AmapApiResult<Map<String, Object>> third = amapOpenService.districtSearchWithMeta(appKey, "中国", 1, districtAdcode, 1, 100);
                    usage.collect(third);
                    data = third.getData();
                    out = collectRegionOptions(data, asLevelSet("street", "township"), limit);
                    if (out.isEmpty()) {
                        out = collectDirectChildren(data, asLevelSet("street", "township"), limit);
                    }
                }
            }
            recordProxyAudit(request, "region_townships", "city", "/v3/config/district", true, startedAt, usage.requestSource(), null);
            return Result.success(out);
        } catch (Exception e) {
            String message = shortenError(e);
            recordProxyAudit(request, "region_townships", "city", "/v3/config/district", false, startedAt, usage.requestSource(), message);
            if (isQpsExceededMessage(message)) {
                return Result.success(new ArrayList<>());
            }
            return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), message);
        }
    }

    /** 天气快照（逆地理 -> 天气）。 */
    public Result<WeatherSnapshotResp> weatherSnapshot(
            HttpServletRequest request,
            Double longitude,
            Double latitude
    ) {
        String appKey = amapQuotaConfigService.resolveBoundAppKey();
        if (!StringUtils.hasText(appKey)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "未配置高德密钥");
        }
        long startedAt = System.currentTimeMillis();
        try {
            AmapOpenService.AmapApiResult<Map<String, Object>> regeoResult = amapOpenService.reverseGeocodeForWeatherWithMeta(appKey, longitude, latitude);
            NormalizedAddress normalized = parseRegeo(regeoResult.getData(), longitude, latitude);
            if (normalized == null || !isValidAdcode(normalized.getAdcode())) {
                recordProxyAudit(
                        request,
                        "weather_snapshot",
                        "geocode",
                        "/v3/geocode/regeo",
                        false,
                        startedAt,
                        toCacheRequestSource(regeoResult.getCacheSource(), regeoResult.isCacheHit()),
                        "无效行政区编码"
                );
                return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "天气查询仅支持中国大陆的坐标");
            }
            recordProxyAudit(
                    request,
                    "weather_snapshot",
                    "geocode",
                    "/v3/geocode/regeo",
                    true,
                    startedAt,
                    toCacheRequestSource(regeoResult.getCacheSource(), regeoResult.isCacheHit()),
                    null
            );

            long weatherStartedAt = System.currentTimeMillis();
            try {
                AmapOpenService.AmapApiResult<Map<String, Object>> weatherResult = amapOpenService.weatherLiveWithMeta(appKey, normalized.getAdcode());
                WeatherSnapshotResp out = parseWeatherSnapshot(weatherResult.getData(), normalized);
                recordProxyAudit(
                        request,
                        "weather_snapshot",
                        "weather",
                        "/v3/weather/weatherInfo",
                        true,
                        weatherStartedAt,
                        toCacheRequestSource(weatherResult.getCacheSource(), weatherResult.isCacheHit()),
                        null
                );
                return Result.success(out);
            } catch (Exception weatherEx) {
                String weatherMessage = shortenError(weatherEx);
                recordProxyAudit(request, "weather_snapshot", "weather", "/v3/weather/weatherInfo", false, weatherStartedAt, REQUEST_SOURCE_BACKEND_PROXY, weatherMessage);
                return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), weatherMessage);
            }
        } catch (Exception e) {
            String message = shortenError(e);
            recordProxyAudit(request, "weather_snapshot", "geocode", "/v3/geocode/regeo", false, startedAt, message);
            return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), message);
        }
    }

    private void recordProxyAudit(
            HttpServletRequest request,
            String bizScene,
            String apiType,
            String apiPath,
            boolean success,
            long startedAt,
            String errorMessage
    ) {
        recordProxyAudit(request, bizScene, apiType, apiPath, success, startedAt, REQUEST_SOURCE_BACKEND_PROXY, errorMessage);
    }

    private void recordProxyAudit(
            HttpServletRequest request,
            String bizScene,
            String apiType,
            String apiPath,
            boolean success,
            long startedAt,
            String requestSource,
            String errorMessage
    ) {
        try {
            AmapApiAudit row = new AmapApiAudit();
            row.setRecordDate(LocalDate.now());
            AppUser user = AuthContext.getCurrentUser(request);
            if (user != null) {
                row.setUserId(user.getId());
                row.setOperatorName(user.getRealName());
            }
            row.setBizScene(bizScene);
            row.setApiType(resolveBillingApiType(apiType, apiPath, bizScene));
            row.setApiPath(apiPath);
            row.setRequestSource(normalizeRequestSource(requestSource));
            row.setSuccessFlag(success ? 1 : 0);
            row.setCostMs((int) Math.max(0L, System.currentTimeMillis() - startedAt));
            row.setErrorMessage(safeText(errorMessage));
            amapApiAuditService.save(row);
        } catch (Exception ignored) {
        }
    }

    // 审计与筛选统一使用 weather/location 两类计费分类。
    private String resolveBillingApiType(String apiType, String apiPath, String bizScene) {
        String normalized = safeText(apiType);
        if (!StringUtils.hasText(normalized)) {
            normalized = safeText(apiPath);
        }
        if (!StringUtils.hasText(normalized)) {
            normalized = safeText(bizScene);
        }
        String key = String.valueOf(normalized == null ? "" : normalized).toLowerCase();
        if (key.contains("weather")) {
            return "weather";
        }
        return "location";
    }

    private String normalizeRequestSource(String requestSource) {
        String normalized = safeText(requestSource);
        return StringUtils.hasText(normalized) ? normalized : REQUEST_SOURCE_BACKEND_PROXY;
    }

    private String toCacheRequestSource(String cacheSource, boolean cacheHit) {
        if (!cacheHit) {
            return REQUEST_SOURCE_BACKEND_PROXY;
        }
        String source = safeText(cacheSource);
        if (!StringUtils.hasText(source)) {
            source = "unknown";
        }
        return REQUEST_SOURCE_CACHE_PREFIX + source;
    }

    private static final class UsageStats {
        private int chargeableCount = 0;
        private final Set<String> cacheSources = new LinkedHashSet<>();

        private void collect(AmapOpenService.AmapApiResult<Map<String, Object>> result) {
            if (result == null) {
                return;
            }
            if (result.isQuotaChargeable()) {
                chargeableCount += 1;
            }
            if (result.isCacheHit() && StringUtils.hasText(result.getCacheSource())) {
                cacheSources.add(result.getCacheSource());
            }
        }

        private String requestSource() {
            if (cacheSources.isEmpty()) {
                return REQUEST_SOURCE_BACKEND_PROXY;
            }
            String joined = String.join("+", cacheSources);
            if (chargeableCount > 0) {
                return REQUEST_SOURCE_MIXED_PREFIX + joined;
            }
            return REQUEST_SOURCE_CACHE_PREFIX + joined;
        }
    }

    /** Parse AMap tips payload to normalized list. */
    private List<AddressTipItem> parseTips(Map<String, Object> data, int limit) {
        List<Map<String, Object>> rows = toMapList(data == null ? null : data.get("tips"));
        List<AddressTipItem> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            AddressTipItem item = new AddressTipItem();
            item.setName(safeText(row.get("name")));
            item.setAddress(safeText(row.get("address")));
            item.setDistrict(safeText(row.get("district")));
            item.setTownship(safeText(row.get("district")));
            item.setAdcode(safeText(row.get("adcode")));
            item.setLocation(safeText(row.get("location")));
            applyLocation(item, item.getLocation());
            String formatted = joinAddress(item.getDistrict(), item.getAddress());
            item.setFormattedAddress(StringUtils.hasText(formatted) ? formatted : item.getName());
            if (!StringUtils.hasText(item.getName()) && !StringUtils.hasText(item.getAddress()) && !StringUtils.hasText(item.getLocation())) {
                continue;
            }
            out.add(item);
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    /** Collect options by traversing district tree recursively. */
    private List<RegionOptionItem> collectRegionOptions(Map<String, Object> data, Set<String> levels, int limit) {
        List<Map<String, Object>> roots = toMapList(data == null ? null : data.get("districts"));
        List<RegionOptionItem> out = new ArrayList<>();
        Set<String> dedup = new LinkedHashSet<>();
        walkDistrictNodes(roots, null, levels, dedup, out, Math.max(1, limit));
        return out;
    }

    private void walkDistrictNodes(
            List<Map<String, Object>> rows,
            String parentName,
            Set<String> levels,
            Set<String> dedup,
            List<RegionOptionItem> out,
            int limit
    ) {
        if (rows == null || rows.isEmpty() || out.size() >= limit) {
            return;
        }
        for (Map<String, Object> row : rows) {
            if (row == null || out.size() >= limit) {
                continue;
            }
            String name = safeText(row.get("name"));
            String level = safeText(row.get("level"));
            String adcode = safeText(row.get("adcode"));
            String key = (name == null ? "" : name) + "|" + (adcode == null ? "" : adcode) + "|" + (level == null ? "" : level);
            if (StringUtils.hasText(name) && levels.contains(StringUtils.hasText(level) ? level.toLowerCase() : "")) {
                if (!dedup.contains(key)) {
                    RegionOptionItem item = new RegionOptionItem();
                    item.setLabel(name);
                    item.setValue(name);
                    item.setAdcode(adcode);
                    item.setLevel(level);
                    item.setParentName(parentName);
                    out.add(item);
                    dedup.add(key);
                    if (out.size() >= limit) {
                        return;
                    }
                }
            }
            List<Map<String, Object>> children = toMapList(row.get("districts"));
            if (!children.isEmpty()) {
                walkDistrictNodes(children, name, levels, dedup, out, limit);
            }
        }
    }

    private Set<String> asLevelSet(String... levels) {
        Set<String> out = new LinkedHashSet<>();
        if (levels == null || levels.length == 0) {
            return out;
        }
        for (String level : levels) {
            String value = safeText(level);
            if (StringUtils.hasText(value)) {
                out.add(value.toLowerCase());
            }
        }
        return out;
    }

    /** 仅按直接子级收集的降级方案。 */
    private List<RegionOptionItem> collectDirectChildren(Map<String, Object> data, Set<String> levels, int limit) {
        List<Map<String, Object>> roots = toMapList(data == null ? null : data.get("districts"));
        List<RegionOptionItem> out = new ArrayList<>();
        Set<String> dedup = new LinkedHashSet<>();
        int max = Math.max(1, limit);
        for (Map<String, Object> root : roots) {
            if (out.size() >= max || root == null) {
                break;
            }
            String parentName = safeText(root.get("name"));
            List<Map<String, Object>> children = toMapList(root.get("districts"));
            for (Map<String, Object> child : children) {
                if (addRegionOptionFromNode(child, parentName, levels, dedup, out, max)) {
                    break;
                }
            }
        }
        if (!out.isEmpty()) {
            return out;
        }
        for (Map<String, Object> root : roots) {
            if (addRegionOptionFromNode(root, null, levels, dedup, out, max)) {
                break;
            }
        }
        return out;
    }

    private boolean addRegionOptionFromNode(
            Map<String, Object> node,
            String parentName,
            Set<String> levels,
            Set<String> dedup,
            List<RegionOptionItem> out,
            int limit
    ) {
        if (node == null || out.size() >= limit) {
            return out.size() >= limit;
        }
        String name = safeText(node.get("name"));
        String level = safeText(node.get("level"));
        String adcode = safeText(node.get("adcode"));
        String levelKey = StringUtils.hasText(level) ? level.toLowerCase() : "";
        if (!StringUtils.hasText(name) || (levels != null && !levels.isEmpty() && !levels.contains(levelKey))) {
            return false;
        }
        String key = (name == null ? "" : name) + "|" + (adcode == null ? "" : adcode) + "|" + levelKey;
        if (dedup.contains(key)) {
            return false;
        }
        RegionOptionItem item = new RegionOptionItem();
        item.setLabel(name);
        item.setValue(name);
        item.setAdcode(adcode);
        item.setLevel(level);
        item.setParentName(parentName);
        out.add(item);
        dedup.add(key);
        return out.size() >= limit;
    }

    /** 上游限流/异常时的内置省份兜底。 */
    private List<RegionOptionItem> defaultProvinceOptions(String keyword, int limit) {
        String[][] provinces = defaultProvinceRows();
        String key = safeText(keyword);
        List<RegionOptionItem> out = new ArrayList<>();
        int max = Math.max(1, limit);
        for (String[] provinceRow : provinces) {
            if (out.size() >= max) {
                break;
            }
            String province = provinceRow[0];
            String adcode = provinceRow[1];
            if (StringUtils.hasText(key) && !province.contains(key)) {
                continue;
            }
            RegionOptionItem item = new RegionOptionItem();
            item.setLabel(province);
            item.setValue(province);
            item.setAdcode(adcode);
            item.setLevel("province");
            out.add(item);
        }
        return out;
    }

    private String resolveDefaultProvinceAdcode(String provinceName) {
        String name = normalizeRegionKeyword(provinceName);
        if (!StringUtils.hasText(name)) {
            return null;
        }
        String[][] rows = defaultProvinceRows();
        for (String[] row : rows) {
            if (row == null || row.length < 2) {
                continue;
            }
            if (name.equals(row[0])) {
                return row[1];
            }
        }
        return null;
    }

    private String[][] defaultProvinceRows() {
        return new String[][]{
                {"北京市", "110000"}, {"天津市", "120000"}, {"上海市", "310000"}, {"重庆市", "500000"},
                {"河北省", "130000"}, {"山西省", "140000"}, {"辽宁省", "210000"}, {"吉林省", "220000"}, {"黑龙江省", "230000"},
                {"江苏省", "320000"}, {"浙江省", "330000"}, {"安徽省", "340000"}, {"福建省", "350000"}, {"江西省", "360000"}, {"山东省", "370000"},
                {"河南省", "410000"}, {"湖北省", "420000"}, {"湖南省", "430000"}, {"广东省", "440000"}, {"海南省", "460000"},
                {"四川省", "510000"}, {"贵州省", "520000"}, {"云南省", "530000"}, {"陕西省", "610000"}, {"甘肃省", "620000"}, {"青海省", "630000"},
                {"内蒙古自治区", "150000"}, {"广西壮族自治区", "450000"}, {"西藏自治区", "540000"}, {"宁夏回族自治区", "640000"}, {"新疆维吾尔自治区", "650000"},
                {"香港特别行政区", "810000"}, {"澳门特别行政区", "820000"}, {"台湾省", "710000"}
        };
    }

    /** Resolve adcode from keyword via district search. */
    private String resolveAdcode(String appKey, String keyword) {
        String text = normalizeRegionKeyword(keyword);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        if (text.matches("^\\d{6}$")) {
            return text;
        }
        try {
            Map<String, Object> data = amapOpenService.districtSearch(appKey, text, 0, null, 1, 20);
            List<Map<String, Object>> roots = toMapList(data == null ? null : data.get("districts"));
            if (roots.isEmpty()) {
                return null;
            }
            String exact = matchAdcodeByName(roots, text);
            if (StringUtils.hasText(exact)) {
                return exact;
            }
            return safeText(roots.get(0).get("adcode"));
        } catch (Exception e) {
            return null;
        }
    }

    private String matchAdcodeByName(List<Map<String, Object>> rows, String name) {
        String target = safeText(name);
        if (!StringUtils.hasText(target) || rows == null) {
            return null;
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            String rowName = safeText(row.get("name"));
            if (target.equals(rowName)) {
                return safeText(row.get("adcode"));
            }
            String child = matchAdcodeByName(toMapList(row.get("districts")), target);
            if (StringUtils.hasText(child)) {
                return child;
            }
        }
        return null;
    }

    /** 地理编码响应归一化。 */
    private NormalizedAddress parseGeoToNormalized(Map<String, Object> data, String rawAddress) {
        List<Map<String, Object>> rows = toMapList(data == null ? null : data.get("geocodes"));
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> first = rows.get(0);
        NormalizedAddress out = new NormalizedAddress();
        out.setSource("geocode");
        out.setRawAddress(rawAddress);
        out.setFormattedAddress(safeText(first.get("formatted_address")));
        out.setProvince(safeText(first.get("province")));
        out.setCity(toCityText(first.get("city")));
        out.setDistrict(safeText(first.get("district")));
        out.setAdcode(safeText(first.get("adcode")));
        String location = safeText(first.get("location"));
        out.setLocation(location);
        if (StringUtils.hasText(location)) {
            Double[] lngLat = parseLngLat(location);
            out.setLongitude(lngLat[0]);
            out.setLatitude(lngLat[1]);
        }
        return out;
    }

    /** 逆地理响应归一化。 */
    private NormalizedAddress parseRegeo(Map<String, Object> data, Double longitude, Double latitude) {
        Map<String, Object> regeo = toMap(data == null ? null : data.get("regeocode"));
        if (regeo == null) {
            return null;
        }
        Map<String, Object> comp = toMap(regeo.get("addressComponent"));
        Map<String, Object> streetNumber = toMap(comp == null ? null : comp.get("streetNumber"));
        NormalizedAddress out = new NormalizedAddress();
        out.setSource("regeo");
        out.setFormattedAddress(safeText(regeo.get("formatted_address")));
        out.setProvince(safeText(comp == null ? null : comp.get("province")));
        out.setCity(toCityText(comp == null ? null : comp.get("city")));
        out.setDistrict(safeText(comp == null ? null : comp.get("district")));
        out.setTownship(safeText(comp == null ? null : comp.get("township")));
        out.setStreet(safeText(streetNumber == null ? null : streetNumber.get("street")));
        out.setStreetNumber(safeText(streetNumber == null ? null : streetNumber.get("number")));
        out.setAdcode(safeText(comp == null ? null : comp.get("adcode")));
        out.setLongitude(longitude);
        out.setLatitude(latitude);
        if (longitude != null && latitude != null) {
            out.setLocation(String.format(java.util.Locale.ROOT, "%.7f,%.7f", longitude, latitude));
        }
        return out;
    }

    /** 天气响应归一化。 */
    private WeatherSnapshotResp parseWeatherSnapshot(Map<String, Object> data, NormalizedAddress normalized) {
        List<Map<String, Object>> lives = toMapList(data == null ? null : data.get("lives"));
        Map<String, Object> live = lives.isEmpty() ? null : lives.get(0);
        WeatherSnapshotResp out = new WeatherSnapshotResp();
        out.setLongitude(normalized == null ? null : normalized.getLongitude());
        out.setLatitude(normalized == null ? null : normalized.getLatitude());
        out.setProvince(normalized == null ? null : normalized.getProvince());
        out.setCity(normalized == null ? null : normalized.getCity());
        out.setDistrict(normalized == null ? null : normalized.getDistrict());
        out.setTownship(normalized == null ? null : normalized.getTownship());
        out.setAdcode(normalized == null ? null : normalized.getAdcode());
        out.setFormattedAddress(normalized == null ? null : normalized.getFormattedAddress());
        out.setWeather(safeText(live == null ? null : live.get("weather")));
        out.setTemperature(safeText(live == null ? null : live.get("temperature")));
        out.setHumidity(safeText(live == null ? null : live.get("humidity")));
        out.setWindDirection(safeText(live == null ? null : live.get("winddirection")));
        out.setWindPower(safeText(live == null ? null : live.get("windpower")));
        out.setReportTime(safeText(live == null ? null : live.get("reporttime")));
        return out;
    }

    private String shortenError(Exception e) {
        if (e == null) {
            return "请求失败";
        }
        String text = safeText(e.getMessage());
        return StringUtils.hasText(text) ? text : "请求失败";
    }

    private boolean isQpsExceededMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("10021") || lower.contains("cuqps_has_exceeded_the_limit");
    }

    /** Normalize text and drop null/undefined-like values. */
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
        if ("[]".equals(text) || "{}".equals(text)) {
            return null;
        }
        return text;
    }

    
    private boolean isValidAdcode(String adcode) {
        String text = safeText(adcode);
        return StringUtils.hasText(text) && text.matches("^\\d{6}$");
    }

    /** Region keyword normalization with mojibake handling. */
    private String normalizeRegionKeyword(String raw) {
        String text = safeText(raw);
        if (!StringUtils.hasText(text)) {
            return text;
        }
        if (containsCjk(text)) {
            return text;
        }
        if (!looksLikeUtf8Mojibake(text)) {
            return text;
        }
        try {
            String decoded = new String(text.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
            decoded = safeText(decoded);
            if (StringUtils.hasText(decoded) && containsCjk(decoded)) {
                return decoded;
            }
        } catch (Exception ignored) {
        }
        return text;
    }

    private boolean looksLikeUtf8Mojibake(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return text.indexOf('Ã') >= 0
                || text.indexOf('Â') >= 0
                || text.indexOf('æ') >= 0
                || text.indexOf('å') >= 0
                || text.indexOf('ç') >= 0
                || text.indexOf('é') >= 0
                || text.indexOf('ï') >= 0;
    }

    private boolean containsCjk(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= 0x4E00 && ch <= 0x9FFF) {
                return true;
            }
        }
        return false;
    }

    private String toCityText(Object cityValue) {
        if (cityValue instanceof Collection) {
            for (Object item : (Collection<?>) cityValue) {
                String text = safeText(item);
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
            return null;
        }
        return safeText(cityValue);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toMapList(Object raw) {
        if (!(raw instanceof Collection)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : (Collection<?>) raw) {
            if (item instanceof Map) {
                out.add((Map<String, Object>) item);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object raw) {
        if (!(raw instanceof Map)) {
            return null;
        }
        return (Map<String, Object>) raw;
    }

    private String joinAddress(String left, String right) {
        String first = safeText(left);
        String second = safeText(right);
        if (StringUtils.hasText(first) && StringUtils.hasText(second)) {
            return first + " " + second;
        }
        if (StringUtils.hasText(first)) {
            return first;
        }
        return second;
    }

    /** Default health-check guidance list. */
    private List<Map<String, String>> defaultHealthSteps() {
        List<Map<String, String>> out = new ArrayList<>();
        out.add(step("1", "登录高德控制台并确认应用开通 Web 服务能力"));
        out.add(step("2", "在控制台获取 Web 服务 Key，回到系统保存并校验"));
        out.add(step("3", "执行一键体检，确认密钥、逆地理与天气查询均通过"));
        out.add(step("4", "按业务峰值调整分接口预算并设置告警阈值"));
        return out;
    }

    private Map<String, String> step(String order, String text) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("order", order);
        row.put("text", text);
        return row;
    }

    /** Execute one health check and capture cost/result. */
    private Map<String, Object> runCheck(String name, CheckAction action) {
        long startedAt = System.currentTimeMillis();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", name);
        try {
            String message = action.run();
            out.put("成功", true);
            out.put("message", firstNonEmpty(message, "通过"));
        } catch (Exception e) {
            out.put("成功", false);
            out.put("message", shortenError(e));
        }
        out.put("costMs", Math.max(0L, System.currentTimeMillis() - startedAt));
        return out;
    }

    @FunctionalInterface
    private interface CheckAction {
        String run() throws Exception;
    }

    private void applyLocation(AddressTipItem item, String location) {
        if (item == null || !StringUtils.hasText(location)) {
            return;
        }
        Double[] values = parseLngLat(location);
        item.setLongitude(values[0]);
        item.setLatitude(values[1]);
    }

    private Double[] parseLngLat(String location) {
        Double[] out = new Double[] {null, null};
        if (!StringUtils.hasText(location)) {
            return out;
        }
        String[] arr = location.split(",");
        if (arr.length < 2) {
            return out;
        }
        try {
            out[0] = Double.parseDouble(arr[0].trim());
            out[1] = Double.parseDouble(arr[1].trim());
        } catch (Exception ignored) {
        }
        return out;
    }

    private String firstNonEmpty(String first, String fallback) {
        return StringUtils.hasText(first) ? first : fallback;
    }
    /** 将表缺失异常转换为可执行提示。 */
    private <T> Result<T> tableOrServerError(Exception e) {
        String message = e == null ? null : e.getMessage();
        if (message != null && (message.toLowerCase().contains("amap_quota_config") || message.toLowerCase().contains("amap_api_audit"))) {
            return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), "高德审计相关数据表未初始化，请执行数据库脚本 db/schema-amap.sql");
        }
        return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage());
    }

    @Data
    public static class AuditReq {
        private String bizScene;
        private String apiType;
        private String apiPath;
        private String requestSource;
        private Boolean success;
        private Integer consumeCount;
        private Integer costMs;
        private String errorMessage;
    }

    @Data
    public static class AddressTipItem {
        private String name;
        private String address;
        private String district;
        private String township;
        private String adcode;
        private String location;
        private Double longitude;
        private Double latitude;
        private String formattedAddress;
    }

    @Data
    public static class RegionOptionItem {
        private String value;
        private String label;
        private String adcode;
        private String level;
        private String parentName;
    }

    @Data
    public static class NormalizedAddress {
        private String source;
        private String rawAddress;
        private String formattedAddress;
        private String province;
        private String city;
        private String district;
        private String township;
        private String street;
        private String streetNumber;
        private String adcode;
        private String location;
        private Double longitude;
        private Double latitude;
    }

    @Data
    public static class WeatherSnapshotResp {
        private Double longitude;
        private Double latitude;
        private String province;
        private String city;
        private String district;
        private String township;
        private String adcode;
        private String formattedAddress;
        private String weather;
        private String temperature;
        private String humidity;
        private String windDirection;
        private String windPower;
        private String reportTime;
    }
}
