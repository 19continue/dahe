package com.dahe.v2.modules.seed.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dahe.v2.modules.seed.model.SeedQualityRule;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/** 种子检测规则数据访问层。 */
public interface SeedQualityRuleMapper extends BaseMapper<SeedQualityRule> {
}
