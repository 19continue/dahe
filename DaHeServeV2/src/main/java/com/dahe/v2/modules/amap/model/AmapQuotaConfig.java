package com.dahe.v2.modules.amap.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("amap_quota_config")
public class AmapQuotaConfig {
    /** 主键（当前实现固定单行配置，默认 ID=1）。 */
    @TableId(type = IdType.INPUT)
    private Long id;
    /** 配置快照日期。 */
    private LocalDate recordDate;
    /** 总月限额（聚合值，为兼容保留旧字段名）。 */
    private Integer dailyLimit;
    /** 告警阈值（百分比）。 */
    private Integer alertThreshold;
    /** 总月已用量（聚合值，为兼容保留旧字段名）。 */
    private Integer usedCount;
    /** 高德账号展示信息。 */
    private String accountName;
    private String accountLogin;
    private String appName;
    /** 控制台入口与 Key 管理入口。 */
    private String consoleUrl;
    private String keyConsoleUrl;
    /** 绑定的高德 Web 服务 Key。 */
    private String appKey;
    /** Key 状态：unknown/valid/invalid。 */
    private String appKeyStatus;
    private LocalDateTime appKeyBoundAt;
    private LocalDateTime appKeyLastCheckAt;
    private String appKeyLastCheckMessage;
    /** 体检结果快照。 */
    private LocalDateTime lastHealthCheckAt;
    private String lastHealthCheckMessage;
    /** 分接口月限额与当月用量。 */
    private Integer weatherMonthlyLimit;
    private Integer locationMonthlyLimit;
    /** 兼容历史字段，内部同步为月限额。 */
    private Integer weatherDailyLimit;
    private Integer weatherUsedCount;
    private Integer locationDailyLimit;
    private Integer locationUsedCount;
    /** 兼容历史 geocode/city 统计字段（内部会与 location 归一化）。 */
    private Integer geocodeDailyLimit;
    private Integer geocodeUsedCount;
    private Integer cityDailyLimit;
    private Integer cityUsedCount;
    /** QPS 节流阈值。 */
    private Integer qpsLimit;
    /** 充值累计值。 */
    private Integer rechargeTotal;
    private Integer weatherRechargeTotal;
    private Integer locationRechargeTotal;
    private Integer geocodeRechargeTotal;
    private Integer cityRechargeTotal;
    /** 缓存策略（后台可配置，运行时生效）。 */
    private Integer cacheRedisEnabled;
    private String cacheRedisKeyPrefix;
    private Integer cacheRegionTtlMinutes;
    private Integer cacheRegionStaleMinutes;
    private Integer cacheWeatherTtlMinutes;
    private Integer cacheLocalRegionMaxEntries;
    private Integer cacheLocalWeatherMaxEntries;
    /** 审计日志保留策略。 */
    private Integer auditAutoPurgeEnabled;
    private Integer auditRetainDays;
    /** 备注信息。 */
    private String remark;
    /** 审计时间。 */
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
