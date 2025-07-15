package com.dahe.v2.modules.amap.service.impl;

import com.dahe.v2.modules.amap.service.AmapHealthCheckService;
import com.dahe.v2.modules.amap.service.AmapOpenService;
import com.dahe.v2.modules.amap.service.AmapQuotaConfigService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
/**
 * 高德健康检查服务实现。
 *
 * <p>将控制层的健康检查编排下沉到 service，便于复用与测试。</p>
 */
public class AmapHealthCheckServiceImpl implements AmapHealthCheckService {

    private final AmapOpenService amapOpenService;
    private final AmapQuotaConfigService amapQuotaConfigService;

    public AmapHealthCheckServiceImpl(
            AmapOpenService amapOpenService,
            AmapQuotaConfigService amapQuotaConfigService
    ) {
        this.amapOpenService = amapOpenService;
        this.amapQuotaConfigService = amapQuotaConfigService;
    }

    @Override
    public Map<String, Object> runHealthCheck(String appKey, Double longitude, Double latitude) {
        if (!StringUtils.hasText(appKey)) {
            amapQuotaConfigService.updateHealthCheck("尚未配置高德 Key");
            Map<String, Object> blocked = new LinkedHashMap<>();
            blocked.put("status", "blocked");
            blocked.put("message", "尚未配置高德 Key");
            blocked.put("steps", defaultHealthSteps());
            blocked.put("checks", new ArrayList<>());
            return blocked;
        }

        List<Map<String, Object>> checks = new ArrayList<>();
        boolean allPassed = true;
        boolean anyPassed = false;

        Map<String, Object> verifyCheck = runCheck("key_verify", () -> {
            amapOpenService.verifyKey(appKey);
            amapQuotaConfigService.updateKeyCheck("valid", "Key 校验通过");
            return "通过";
        });
        checks.add(verifyCheck);
        if (Boolean.TRUE.equals(verifyCheck.get("success"))) {
            anyPassed = true;
        } else {
            allPassed = false;
            amapQuotaConfigService.updateKeyCheck("invalid", safeText(verifyCheck.get("message")));
        }

        Double checkLng = longitude != null ? longitude : 116.397470;
        Double checkLat = latitude != null ? latitude : 39.908823;
        Map<String, Object> regeoCheck = runCheck("reverse_geocode", () -> {
            Map<String, Object> data = amapOpenService.reverseGeocode(appKey, checkLng, checkLat);
            Map<String, Object> regeo = toMap(data == null ? null : data.get("regeocode"));
            String formattedAddress = safeText(regeo == null ? null : regeo.get("formatted_address"));
            if (!StringUtils.hasText(formattedAddress)) {
                throw new IllegalStateException("逆地理结果为空");
            }
            return formattedAddress;
        });
        checks.add(regeoCheck);
        if (Boolean.TRUE.equals(regeoCheck.get("success"))) {
            anyPassed = true;
        } else {
            allPassed = false;
        }

        Map<String, Object> weatherCheck = runCheck("weather_live", () -> {
            Map<String, Object> weatherData = amapOpenService.weatherLive(appKey, "110000");
            List<Map<String, Object>> lives = toMapList(weatherData == null ? null : weatherData.get("lives"));
            Map<String, Object> live = lives.isEmpty() ? null : lives.get(0);
            String weather = safeText(live == null ? null : live.get("weather"));
            if (!StringUtils.hasText(weather)) {
                throw new IllegalStateException("天气结果为空");
            }
            return weather;
        });
        checks.add(weatherCheck);
        if (Boolean.TRUE.equals(weatherCheck.get("success"))) {
            anyPassed = true;
        } else {
            allPassed = false;
        }

        String status;
        if (allPassed) {
            status = "healthy";
        } else if (anyPassed) {
            status = "degraded";
        } else {
            status = "blocked";
        }

        String message;
        if ("healthy".equals(status)) {
            message = "高德服务状态正常";
        } else if ("degraded".equals(status)) {
            message = "高德服务部分能力异常";
        } else {
            message = "高德服务当前不可用";
        }
        amapQuotaConfigService.updateHealthCheck(message);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", status);
        out.put("message", message);
        out.put("checks", checks);
        out.put("steps", defaultHealthSteps());
        return out;
    }

    private List<Map<String, String>> defaultHealthSteps() {
        List<Map<String, String>> out = new ArrayList<>();
        out.add(step("1", "登录高德控制台确认应用已开通 Web 服务能力"));
        out.add(step("2", "复制 Web 服务 Key 并保存到系统配置"));
        out.add(step("3", "执行一键体检，确认密钥与接口链路可用"));
        out.add(step("4", "按业务峰值调整额度、缓存策略与告警阈值"));
        return out;
    }

    private Map<String, String> step(String order, String text) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("order", order);
        row.put("text", text);
        return row;
    }

    private Map<String, Object> runCheck(String name, CheckAction action) {
        long startedAt = System.currentTimeMillis();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", name);
        try {
            String message = action.run();
            out.put("success", true);
            out.put("message", StringUtils.hasText(message) ? message : "通过");
        } catch (Exception e) {
            out.put("success", false);
            out.put("message", shortenError(e));
        }
        out.put("costMs", Math.max(0L, System.currentTimeMillis() - startedAt));
        return out;
    }

    @FunctionalInterface
    private interface CheckAction {
        String run() throws Exception;
    }

    private String safeText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String lower = text.toLowerCase();
        if ("undefined".equals(lower) || "null".equals(lower)) {
            return null;
        }
        return text;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object raw) {
        if (!(raw instanceof Map)) {
            return null;
        }
        return (Map<String, Object>) raw;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toMapList(Object raw) {
        if (!(raw instanceof List)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object row : (List<?>) raw) {
            if (row instanceof Map) {
                out.add((Map<String, Object>) row);
            }
        }
        return out;
    }

    private String shortenError(Exception e) {
        if (e == null) {
            return "请求失败";
        }
        String text = safeText(e.getMessage());
        return StringUtils.hasText(text) ? text : "请求失败";
    }
}
