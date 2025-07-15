package com.dahe.v2.modules.seed.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dahe.v2.modules.seed.model.SeedBatch;

public interface SeedBatchService extends IService<SeedBatch> {

    /** 分页查询批次，支持作物、品种、启用状态等过滤。 */
    Page<SeedBatch> pageBatches(
            String keyword,
            String cropType,
            String varietyName,
            Integer enabled,
            boolean includeDisabled,
            long page,
            long pageSize
    );
}

