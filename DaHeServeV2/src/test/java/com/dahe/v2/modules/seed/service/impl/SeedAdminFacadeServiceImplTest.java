package com.dahe.v2.modules.seed.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.modules.miniapp.search.service.MiniappSearchIndexService;
import com.dahe.v2.modules.seed.model.SeedBatch;
import com.dahe.v2.modules.seed.model.SeedQualityRule;
import com.dahe.v2.modules.seed.model.SeedQualityTest;
import com.dahe.v2.modules.seed.service.SeedAdminCommand;
import com.dahe.v2.modules.seed.service.SeedBatchService;
import com.dahe.v2.modules.seed.service.SeedQualityRuleService;
import com.dahe.v2.modules.seed.service.SeedQualityTestService;
import com.dahe.v2.modules.seed.service.SeedServiceException;
import com.dahe.v2.modules.seed.support.SeedDynamicSchemaSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeedAdminFacadeServiceImplTest {

    @Mock
    private SeedBatchService seedBatchService;
    @Mock
    private SeedQualityTestService seedQualityTestService;
    @Mock
    private SeedQualityRuleService seedQualityRuleService;
    @Mock
    private SeedDynamicSchemaSupport seedDynamicSchemaSupport;
    @Mock
    private SeedDynamicMetaAssembler seedDynamicMetaAssembler;
    @Mock
    private SeedSampleCalculator seedSampleCalculator;
    @Mock
    private SeedQueryNormalizer seedQueryNormalizer;
    @Mock
    private MiniappSearchIndexService miniappSearchIndexService;

    @Test
    void createTest_shouldReturnExistingWhenRequestKeyExists() {
        SeedAdminFacadeServiceImpl service = createService();
        SeedAdminCommand.TestUpsert command = buildCommand();
        command.setRequestKey("req-seed-001");

        SeedQualityTest existed = new SeedQualityTest();
        existed.setId(9001L);
        existed.setBatchId(100L);
        existed.setFormConfigId(901L);

        SeedDynamicSchemaSupport.ResolveResult resolved = mockBatchAndSchema();
        when(seedDynamicSchemaSupport.resolve(eq("test_fields"), any())).thenReturn(resolved);
        when(seedQualityTestService.getOne(any(LambdaQueryWrapper.class), eq(false))).thenReturn(existed);

        SeedQualityTest out = service.createTest(100L, command);

        Assertions.assertEquals(Long.valueOf(9001L), out.getId());
        verify(seedQualityTestService, never()).save(any(SeedQualityTest.class));
    }

    @Test
    void createTest_shouldReturnExistingWhenDuplicateKeyRetry() {
        SeedAdminFacadeServiceImpl service = createService();
        SeedAdminCommand.TestUpsert command = buildCommand();
        command.setRequestKey("req-seed-dup");

        SeedQualityTest existed = new SeedQualityTest();
        existed.setId(9002L);
        existed.setBatchId(100L);
        existed.setRequestKey("req-seed-dup");
        existed.setFormConfigId(901L);

        SeedDynamicSchemaSupport.ResolveResult resolved = mockBatchAndSchema();
        when(seedDynamicSchemaSupport.resolve(eq("test_fields"), any())).thenReturn(resolved);
        mockRuleAndSampleCalc();
        when(seedSampleCalculator.calcGerminationRate(eq(96), eq(100))).thenReturn(96.0);
        when(seedQualityTestService.getOne(any(LambdaQueryWrapper.class), eq(false))).thenReturn(null, existed);
        doThrow(new DuplicateKeyException("duplicate")).when(seedQualityTestService).save(any(SeedQualityTest.class));

        SeedQualityTest out = service.createTest(100L, command);

        Assertions.assertEquals(Long.valueOf(9002L), out.getId());
        verify(seedQualityTestService).save(any(SeedQualityTest.class));
    }

    @Test
    void createTest_shouldValidatePurityRange() {
        SeedAdminFacadeServiceImpl service = createService();
        SeedAdminCommand.TestUpsert command = buildCommand();
        command.setPurity(120.0);

        mockBatchAndSchema();
        mockRuleAndSampleCalc();

        SeedServiceException ex = Assertions.assertThrows(
                SeedServiceException.class,
                () -> service.createTest(100L, command)
        );
        Assertions.assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), ex.getCode());
        verify(seedQualityTestService, never()).save(any(SeedQualityTest.class));
    }

    @Test
    void createTest_shouldPersistRequestKey() {
        SeedAdminFacadeServiceImpl service = createService();
        SeedAdminCommand.TestUpsert command = buildCommand();
        command.setRequestKey("req-seed-persist");

        mockBatchAndSchema();
        mockRuleAndSampleCalc();
        when(seedSampleCalculator.calcGerminationRate(eq(96), eq(100))).thenReturn(96.0);

        SeedQualityTest out = service.createTest(100L, command);

        ArgumentCaptor<SeedQualityTest> captor = ArgumentCaptor.forClass(SeedQualityTest.class);
        verify(seedQualityTestService).save(captor.capture());
        SeedQualityTest saved = captor.getValue();
        Assertions.assertEquals("req-seed-persist", saved.getRequestKey());
        Assertions.assertEquals("req-seed-persist", out.getRequestKey());
    }

    private SeedAdminFacadeServiceImpl createService() {
        return new SeedAdminFacadeServiceImpl(
                seedBatchService,
                seedQualityTestService,
                seedQualityRuleService,
                seedDynamicSchemaSupport,
                seedDynamicMetaAssembler,
                seedSampleCalculator,
                seedQueryNormalizer,
                miniappSearchIndexService
        );
    }

    private SeedAdminCommand.TestUpsert buildCommand() {
        SeedAdminCommand.TestUpsert command = new SeedAdminCommand.TestUpsert();
        command.setTestDate(LocalDate.of(2026, 3, 5));
        command.setSampleCount(100);
        command.setGerminationCount(96);
        command.setPurity(99.1);
        command.setMoisture(12.4);
        command.setExtraJson("{\"testMethod\":\"paper\"}");
        return command;
    }

    private SeedDynamicSchemaSupport.ResolveResult mockBatchAndSchema() {
        SeedBatch batch = new SeedBatch();
        batch.setId(100L);
        when(seedBatchService.getById(100L)).thenReturn(batch);

        SeedDynamicSchemaSupport.ResolveResult resolved = new SeedDynamicSchemaSupport.ResolveResult();
        resolved.setFormConfigId(901L);
        resolved.setFormConfigName("seed-test");
        resolved.setFormSchema("[]");
        when(seedDynamicSchemaSupport.resolveAndValidate(eq("test_fields"), any(), any())).thenReturn(resolved);
        return resolved;
    }

    private void mockRuleAndSampleCalc() {
        SeedQualityRule rule = new SeedQualityRule();
        rule.setFixedSampleSize(1);
        rule.setDefaultSampleSize(100);
        when(seedQualityRuleService.getOrInitDefault()).thenReturn(rule);
        when(seedSampleCalculator.resolveSampleCount(eq(rule), any())).thenReturn(100);
        when(seedSampleCalculator.resolveGerminationCount(any(), any(), eq(100))).thenReturn(96);
    }
}
