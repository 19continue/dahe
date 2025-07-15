package com.dahe.v2.modules.seed.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dahe.v2.modules.seed.mapper.SeedQualityRuleMapper;
import com.dahe.v2.modules.seed.model.SeedQualityRule;
import com.dahe.v2.modules.seed.service.SeedQualityRuleService;
import org.springframework.stereotype.Service;

@Service
/** 种子检测规则服务实现（单例配置，固定主键 id=1）。 */
public class SeedQualityRuleServiceImpl extends ServiceImpl<SeedQualityRuleMapper, SeedQualityRule> implements SeedQualityRuleService {

    private static final long DEFAULT_ID = 1L;

    @Override
    /** 获取规则单例，不存在则按默认值初始化。 */
    public SeedQualityRule getOrInitDefault() {
        SeedQualityRule row = this.getById(DEFAULT_ID);
        if (row != null) {
            return row;
        }
        row = new SeedQualityRule();
        row.setId(DEFAULT_ID);
        row.setFixedSampleSize(1);
        row.setDefaultSampleSize(100);
        row.setRemark("default");
        this.save(row);
        return this.getById(DEFAULT_ID);
    }
}
