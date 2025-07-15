package com.dahe.v2.modules.amap.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dahe.v2.modules.amap.model.AmapApiAudit;

import java.time.LocalDate;
import java.util.Map;

public interface AmapApiAuditService extends IService<AmapApiAudit> {

    /** 审计日志分页查询。 */
    Page<AmapApiAudit> pageAudits(
            String bizScene,
            String apiType,
            String requestSource,
            Integer successFlag,
            LocalDate startDate,
            LocalDate endDate,
            long page,
            long pageSize
    );

    /** 审计趋势与命中率概览。 */
    Map<String, Object> buildAuditOverview(LocalDate startDate, LocalDate endDate);

    /** 根据保留天数清理历史审计日志。 */
    Map<String, Object> purgeByRetentionDays(int retainDays, int batchSize, int maxBatches);
}
