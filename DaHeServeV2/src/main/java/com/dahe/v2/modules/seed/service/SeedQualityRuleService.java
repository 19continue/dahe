package com.dahe.v2.modules.seed.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dahe.v2.modules.seed.model.SeedQualityRule;

/** 种子检测规则服务接口。 */
public interface SeedQualityRuleService extends IService<SeedQualityRule> {

    /** 获取检测规则单例，不存在则初始化默认值。 */
    SeedQualityRule getOrInitDefault();
}
