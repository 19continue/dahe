package com.dahe.v2.modules.farm.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dahe.v2.modules.farm.model.FarmRecordGroupStats;
import com.dahe.v2.modules.farm.model.FarmRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 农事记录服务接口。 */
public interface FarmRecordService extends IService<FarmRecord> {

    /** 分页查询农事记录。 */
    Page<FarmRecord> pageRecords(
            Long fieldId,
            Long cycleId,
            Long operatorUserId,
            String operatorName,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Set<Long> fieldIdScope,
            long page,
            long pageSize
    );

    /** 查询田块最近 N 条记录，可按步骤范围进一步收窄。 */
    List<FarmRecord> listRecentByField(Long fieldId, Long cycleId, Set<Long> stepIdScope, int limit);

    /** 按田块+计划分组统计农事记录。 */
    List<FarmRecordGroupStats> listGroupedRecords(
            Long fieldId,
            Long cycleId,
            Long operatorUserId,
            String operatorName,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Set<Long> fieldIdScope,
            int maxGroups
    );

}

