package com.dahe.v2.modules.seed.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dahe.v2.modules.seed.mapper.SeedBatchMapper;
import com.dahe.v2.modules.seed.model.SeedBatch;
import com.dahe.v2.modules.seed.service.SeedBatchService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
/** 种子批次服务实现。 */
public class SeedBatchServiceImpl extends ServiceImpl<SeedBatchMapper, SeedBatch> implements SeedBatchService {

    @Override
    /** 批次分页查询实现。 */
    public Page<SeedBatch> pageBatches(
            String keyword,
            String cropType,
            String varietyName,
            Integer enabled,
            boolean includeDisabled,
            long page,
            long pageSize
    ) {
        Page<SeedBatch> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<SeedBatch> qw = new LambdaQueryWrapper<>();
        Integer enabledFilter = enabled;
        if (enabledFilter == null && !includeDisabled) {
            enabledFilter = 1;
        }
        if (enabledFilter != null) {
            qw.eq(SeedBatch::getEnabled, enabledFilter == 0 ? 0 : 1);
        }
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(SeedBatch::getBatchCode, keyword)
                    .or()
                    .like(SeedBatch::getCropType, keyword)
                    .or()
                    .like(SeedBatch::getVarietyName, keyword));
        }
        if (StringUtils.hasText(cropType)) {
            qw.eq(SeedBatch::getCropType, cropType);
        }
        if (StringUtils.hasText(varietyName)) {
            qw.eq(SeedBatch::getVarietyName, varietyName);
        }
        qw.orderByDesc(SeedBatch::getCreatedAt);
        return this.page(p, qw);
    }
}

