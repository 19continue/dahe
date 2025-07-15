package com.dahe.v2.modules.seed.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dahe.v2.modules.seed.model.SeedBatch;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/** 种子批次数据访问层。 */
public interface SeedBatchMapper extends BaseMapper<SeedBatch> {
}

