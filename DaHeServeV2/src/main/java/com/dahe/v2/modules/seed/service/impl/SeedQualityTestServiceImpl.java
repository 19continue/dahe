package com.dahe.v2.modules.seed.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dahe.v2.modules.seed.mapper.SeedQualityTestMapper;
import com.dahe.v2.modules.seed.model.SeedQualityTest;
import com.dahe.v2.modules.seed.service.SeedQualityTestService;
import org.springframework.stereotype.Service;

@Service
/** 种子检测服务实现（基础 CRUD 由 MyBatis-Plus 提供）。 */
public class SeedQualityTestServiceImpl extends ServiceImpl<SeedQualityTestMapper, SeedQualityTest> implements SeedQualityTestService {
}

