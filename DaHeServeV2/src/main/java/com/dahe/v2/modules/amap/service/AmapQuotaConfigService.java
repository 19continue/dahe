package com.dahe.v2.modules.amap.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dahe.v2.modules.amap.model.AmapQuotaConfig;

public interface AmapQuotaConfigService extends IService<AmapQuotaConfig> {

    String BILLING_WEATHER = "weather";
    String BILLING_LOCATION = "location";

    /** 获取今日配置；若不存在则初始化。 */
    AmapQuotaConfig getOrInitToday();

    /** 更新配额配置。 */
    AmapQuotaConfig updateConfig(
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
    );

    /** 递增某类接口的用量。 */
    AmapQuotaConfig increaseUsageByBillingCategory(String billingCategory, int delta);

    /** 兼容旧接口写法。 */
    default AmapQuotaConfig increaseUsage(String apiType, int delta) {
        return increaseUsageByBillingCategory(apiType, delta);
    }

    /** 充值配额。 */
    AmapQuotaConfig recharge(String apiType, int delta, String remark);

    /** 更新 key 校验状态。 */
    AmapQuotaConfig updateKeyCheck(String keyStatus, String checkMessage);

    /** 更新体检结果。 */
    AmapQuotaConfig updateHealthCheck(String checkMessage);

    /** 获取当前绑定的高德 key。 */
    String resolveBoundAppKey();
}
