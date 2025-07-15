package com.dahe.v2.modules.seed.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.modules.seed.model.SeedBatch;
import com.dahe.v2.modules.seed.model.SeedQualityRule;
import com.dahe.v2.modules.seed.model.SeedQualityTest;

import java.util.List;

/**
 * seed 模块后台业务门面服务。
 */
public interface SeedAdminFacadeService {

    Page<SeedBatch> pageBatches(SeedAdminCommand.BatchPageQuery query);

    SeedBatch createBatch(SeedAdminCommand.BatchCreate command);

    SeedBatch getBatchDetail(Long id);

    SeedBatch updateBatch(Long id, SeedAdminCommand.BatchUpdate command);

    boolean deleteBatch(Long id);

    SeedBatch setBatchEnabled(Long id, boolean enabled);

    List<SeedQualityTest> listTests(Long batchId);

    SeedQualityTest getTestDetail(Long batchId, Long testId);

    SeedQualityTest createTest(Long batchId, SeedAdminCommand.TestUpsert command);

    SeedQualityTest updateTest(Long batchId, Long testId, SeedAdminCommand.TestUpsert command);

    boolean deleteTest(Long batchId, Long testId);

    SeedQualityRule getRule();

    SeedQualityRule updateRule(SeedAdminCommand.RuleUpdate command);
}

