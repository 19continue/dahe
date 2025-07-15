package com.dahe.v2.modules.amap.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 高德真实远程用量服务。
 *
 * <p>只记录真正请求到高德官方的次数，不依赖审计日志。</p>
 */
public interface AmapUsageDailyService {

    /**
     * 记录真实远程调用次数。
     */
    void recordRemoteUsage(LocalDate recordDate, String apiType, int delta);

    /**
     * 查询日期区间内按日期与计费分类的真实远程用量。
     */
    List<Map<String, Object>> listDailyUsage(LocalDate startDate, LocalDate endDate);

    /**
     * 查询月份区间内按月份与计费分类的真实远程用量。
     */
    List<Map<String, Object>> listMonthlyUsage(LocalDate startMonth, LocalDate endMonth);

    /**
     * 查询指定月份两类接口的用量。
     */
    Map<String, Integer> getMonthlyUsage(LocalDate monthDate);

    /**
     * 查询累计总用量。
     */
    Map<String, Integer> getTotalUsage();

    /**
     * 当月首次达到告警阈值时标记已告警。
     *
     * @return true 表示本次成功抢占“首次告警”，可发送通知
     */
    boolean markMonthlyWarningSentIfAbsent(LocalDate monthDate, String apiType);
}
