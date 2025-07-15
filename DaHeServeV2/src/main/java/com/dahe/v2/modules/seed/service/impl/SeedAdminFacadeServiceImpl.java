package com.dahe.v2.modules.seed.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.modules.miniapp.search.service.MiniappSearchIndexService;
import com.dahe.v2.modules.seed.model.SeedBatch;
import com.dahe.v2.modules.seed.model.SeedQualityRule;
import com.dahe.v2.modules.seed.model.SeedQualityTest;
import com.dahe.v2.modules.seed.service.SeedAdminCommand;
import com.dahe.v2.modules.seed.service.SeedAdminFacadeService;
import com.dahe.v2.modules.seed.service.SeedBatchService;
import com.dahe.v2.modules.seed.service.SeedNotFoundException;
import com.dahe.v2.modules.seed.service.SeedQualityRuleService;
import com.dahe.v2.modules.seed.service.SeedQualityTestService;
import com.dahe.v2.modules.seed.service.SeedServiceException;
import com.dahe.v2.modules.seed.support.SeedDynamicSchemaSupport;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * seed 后台业务门面实现。
 * 负责编排批次、检测、规则三条业务链路。
 */
@Service
public class SeedAdminFacadeServiceImpl implements SeedAdminFacadeService {

    private static final int PERCENT_MIN = 0;
    private static final int PERCENT_MAX = 100;
    private static final int REQUEST_KEY_MAX_LENGTH = 64;

    private final SeedBatchService seedBatchService;
    private final SeedQualityTestService seedQualityTestService;
    private final SeedQualityRuleService seedQualityRuleService;
    private final SeedDynamicSchemaSupport seedDynamicSchemaSupport;
    private final SeedDynamicMetaAssembler seedDynamicMetaAssembler;
    private final SeedSampleCalculator seedSampleCalculator;
    private final SeedQueryNormalizer seedQueryNormalizer;
    private final MiniappSearchIndexService miniappSearchIndexService;

    public SeedAdminFacadeServiceImpl(
            SeedBatchService seedBatchService,
            SeedQualityTestService seedQualityTestService,
            SeedQualityRuleService seedQualityRuleService,
            SeedDynamicSchemaSupport seedDynamicSchemaSupport,
            SeedDynamicMetaAssembler seedDynamicMetaAssembler,
            SeedSampleCalculator seedSampleCalculator,
            SeedQueryNormalizer seedQueryNormalizer,
            MiniappSearchIndexService miniappSearchIndexService
    ) {
        this.seedBatchService = seedBatchService;
        this.seedQualityTestService = seedQualityTestService;
        this.seedQualityRuleService = seedQualityRuleService;
        this.seedDynamicSchemaSupport = seedDynamicSchemaSupport;
        this.seedDynamicMetaAssembler = seedDynamicMetaAssembler;
        this.seedSampleCalculator = seedSampleCalculator;
        this.seedQueryNormalizer = seedQueryNormalizer;
        this.miniappSearchIndexService = miniappSearchIndexService;
    }

    @Override
    public Page<SeedBatch> pageBatches(SeedAdminCommand.BatchPageQuery query) {
        Page<SeedBatch> rows = seedBatchService.pageBatches(
                seedQueryNormalizer.normalizeQueryText(query.getKeyword()),
                seedQueryNormalizer.normalizeQueryText(query.getCropType()),
                seedQueryNormalizer.normalizeQueryText(query.getVarietyName()),
                seedQueryNormalizer.normalizeEnabled(query.getEnabled()),
                query.isIncludeDisabled(),
                query.getPage(),
                query.getPageSize()
        );
        if (rows != null && rows.getRecords() != null) {
            for (SeedBatch row : rows.getRecords()) {
                fillBatchDynamicMeta(row);
            }
        }
        return rows;
    }

    @Override
    public SeedBatch createBatch(SeedAdminCommand.BatchCreate command) {
        SeedDynamicSchemaSupport.ResolveResult resolved = seedDynamicSchemaSupport.resolveAndValidate(
                "batch_fields", command.getFormConfigId(), command.getExtraJson()
        );
        ensureResolveSuccess(resolved);

        SeedBatch batch = new SeedBatch();
        batch.setBatchCode(command.getBatchCode());
        batch.setCropType(command.getCropType());
        batch.setVarietyName(command.getVarietyName());
        batch.setProductionDate(command.getProductionDate());
        batch.setRemark(command.getRemark());
        batch.setEnabled(seedQueryNormalizer.resolveEnabled(command.getEnabled(), true));
        batch.setFormConfigId(resolved.getFormConfigId());
        batch.setExtraJson(command.getExtraJson());
        seedBatchService.save(batch);
        miniappSearchIndexService.syncSeedBatch(batch);
        seedDynamicMetaAssembler.fillBatchMeta(batch, resolved);
        return batch;
    }

    @Override
    public SeedBatch getBatchDetail(Long id) {
        SeedBatch batch = getBatchOrThrow(id);
        fillBatchDynamicMeta(batch);
        return batch;
    }

    @Override
    public SeedBatch updateBatch(Long id, SeedAdminCommand.BatchUpdate command) {
        SeedBatch batch = getBatchOrThrow(id);
        SeedDynamicSchemaSupport.ResolveResult resolved = seedDynamicSchemaSupport.resolveAndValidate(
                "batch_fields", command.getFormConfigId(), command.getExtraJson()
        );
        ensureResolveSuccess(resolved);

        batch.setBatchCode(command.getBatchCode());
        batch.setCropType(command.getCropType());
        batch.setVarietyName(command.getVarietyName());
        batch.setProductionDate(command.getProductionDate());
        batch.setRemark(command.getRemark());
        batch.setEnabled(seedQueryNormalizer.resolveEnabled(command.getEnabled(), batch.getEnabled() == null || batch.getEnabled() == 1));
        batch.setFormConfigId(resolved.getFormConfigId());
        batch.setExtraJson(command.getExtraJson());
        seedBatchService.updateById(batch);
        miniappSearchIndexService.syncSeedBatch(batch);
        seedDynamicMetaAssembler.fillBatchMeta(batch, resolved);
        return batch;
    }

    @Override
    public boolean deleteBatch(Long id) {
        boolean removed = seedBatchService.removeById(id);
        if (removed) {
            miniappSearchIndexService.removeSeedBatch(id);
        }
        return removed;
    }

    @Override
    public SeedBatch setBatchEnabled(Long id, boolean enabled) {
        SeedBatch row = getBatchOrThrow(id);
        row.setEnabled(enabled ? 1 : 0);
        seedBatchService.updateById(row);
        miniappSearchIndexService.syncSeedBatch(row);
        fillBatchDynamicMeta(row);
        return row;
    }

    @Override
    public List<SeedQualityTest> listTests(Long batchId) {
        LambdaQueryWrapper<SeedQualityTest> qw = new LambdaQueryWrapper<SeedQualityTest>();
        qw.eq(SeedQualityTest::getBatchId, batchId)
                .orderByDesc(SeedQualityTest::getTestDate)
                .orderByDesc(SeedQualityTest::getId);
        List<SeedQualityTest> rows = seedQualityTestService.list(qw);
        for (SeedQualityTest row : rows) {
            fillTestDynamicMeta(row);
        }
        return rows;
    }

    @Override
    public SeedQualityTest getTestDetail(Long batchId, Long testId) {
        SeedQualityTest row = seedQualityTestService.getById(testId);
        if (row == null || row.getBatchId() == null || !batchId.equals(row.getBatchId())) {
            throw new SeedNotFoundException("种子检测记录不存在");
        }
        fillTestDynamicMeta(row);
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeedQualityTest createTest(Long batchId, SeedAdminCommand.TestUpsert command) {
        ensureBatchExists(batchId);
        SeedDynamicSchemaSupport.ResolveResult resolved = seedDynamicSchemaSupport.resolveAndValidate(
                "test_fields", command.getFormConfigId(), command.getExtraJson()
        );
        ensureResolveSuccess(resolved);

        String requestKey = normalizeRequestKey(command.getRequestKey());
        if (StringUtils.hasText(requestKey)) {
            SeedQualityTest existed = findByBatchIdAndRequestKey(batchId, requestKey);
            if (existed != null) {
                fillTestDynamicMeta(existed);
                return existed;
            }
        }

        SeedQualityRule rule = seedQualityRuleService.getOrInitDefault();
        int sampleCount = seedSampleCalculator.resolveSampleCount(rule, command.getSampleCount());
        if (sampleCount <= 0) {
            throw new SeedServiceException(ErrorCode.VALIDATION_ERROR.getCode(), "样本数必须大于0");
        }
        Integer germinationCount = seedSampleCalculator.resolveGerminationCount(
                command.getGerminationCount(),
                command.getGerminationRate(),
                sampleCount
        );
        if (germinationCount == null || germinationCount < 0 || germinationCount > sampleCount) {
            throw new SeedServiceException(ErrorCode.VALIDATION_ERROR.getCode(), "发芽数必须在0到样本数之间");
        }
        validatePercent(command.getPurity(), "纯度");
        validatePercent(command.getMoisture(), "水分");
        validatePercent(command.getCleanliness(), "净度");
        Double germinationRate = seedSampleCalculator.calcGerminationRate(germinationCount, sampleCount);

        SeedQualityTest row = new SeedQualityTest();
        row.setBatchId(batchId);
        row.setTestDate(command.getTestDate());
        row.setSampleCount(sampleCount);
        row.setGerminationCount(germinationCount);
        row.setGerminationRate(germinationRate);
        row.setMoisture(command.getMoisture());
        row.setPurity(command.getPurity());
        row.setCleanliness(command.getCleanliness());
        row.setTesterName(command.getTesterName());
        row.setRemark(command.getRemark());
        row.setRequestKey(requestKey);
        row.setFormConfigId(resolved.getFormConfigId());
        row.setExtraJson(command.getExtraJson());
        try {
            seedQualityTestService.save(row);
        } catch (DuplicateKeyException ex) {
            if (StringUtils.hasText(requestKey)) {
                SeedQualityTest existed = findByBatchIdAndRequestKey(batchId, requestKey);
                if (existed != null) {
                    fillTestDynamicMeta(existed);
                    return existed;
                }
            }
            throw ex;
        }
        seedDynamicMetaAssembler.fillTestMeta(row, resolved);
        return row;
    }

    @Override
    public SeedQualityTest updateTest(Long batchId, Long testId, SeedAdminCommand.TestUpsert command) {
        ensureBatchExists(batchId);
        SeedQualityTest row = seedQualityTestService.getById(testId);
        if (row == null || row.getBatchId() == null || !batchId.equals(row.getBatchId())) {
            throw new SeedNotFoundException("种子检测记录不存在");
        }

        SeedDynamicSchemaSupport.ResolveResult resolved = seedDynamicSchemaSupport.resolveAndValidate(
                "test_fields", command.getFormConfigId(), command.getExtraJson()
        );
        ensureResolveSuccess(resolved);

        SeedQualityRule rule = seedQualityRuleService.getOrInitDefault();
        int sampleCount = seedSampleCalculator.resolveSampleCount(rule, command.getSampleCount());
        if (sampleCount <= 0) {
            throw new SeedServiceException(ErrorCode.VALIDATION_ERROR.getCode(), "样本数必须大于0");
        }
        Integer germinationCount = seedSampleCalculator.resolveGerminationCount(
                command.getGerminationCount(),
                command.getGerminationRate(),
                sampleCount
        );
        if (germinationCount == null || germinationCount < 0 || germinationCount > sampleCount) {
            throw new SeedServiceException(ErrorCode.VALIDATION_ERROR.getCode(), "发芽数必须在0到样本数之间");
        }
        validatePercent(command.getPurity(), "纯度");
        validatePercent(command.getMoisture(), "水分");
        validatePercent(command.getCleanliness(), "净度");
        Double germinationRate = seedSampleCalculator.calcGerminationRate(germinationCount, sampleCount);

        row.setTestDate(command.getTestDate());
        row.setSampleCount(sampleCount);
        row.setGerminationCount(germinationCount);
        row.setGerminationRate(germinationRate);
        row.setMoisture(command.getMoisture());
        row.setPurity(command.getPurity());
        row.setCleanliness(command.getCleanliness());
        row.setTesterName(command.getTesterName());
        row.setRemark(command.getRemark());
        row.setFormConfigId(resolved.getFormConfigId());
        row.setExtraJson(command.getExtraJson());
        seedQualityTestService.updateById(row);
        seedDynamicMetaAssembler.fillTestMeta(row, resolved);
        return row;
    }

    @Override
    public boolean deleteTest(Long batchId, Long testId) {
        SeedQualityTest row = seedQualityTestService.getById(testId);
        if (row == null || row.getBatchId() == null || !batchId.equals(row.getBatchId())) {
            throw new SeedNotFoundException("种子检测记录不存在");
        }
        return seedQualityTestService.removeById(testId);
    }

    @Override
    public SeedQualityRule getRule() {
        return seedQualityRuleService.getOrInitDefault();
    }

    @Override
    public SeedQualityRule updateRule(SeedAdminCommand.RuleUpdate command) {
        SeedQualityRule row = seedQualityRuleService.getOrInitDefault();
        row.setFixedSampleSize(command.getFixedSampleSize() == null ? 1 : command.getFixedSampleSize());
        row.setDefaultSampleSize(command.getDefaultSampleSize());
        row.setRemark(command.getRemark());
        seedQualityRuleService.updateById(row);
        return seedQualityRuleService.getOrInitDefault();
    }

    private SeedBatch getBatchOrThrow(Long id) {
        SeedBatch batch = seedBatchService.getById(id);
        if (batch == null) {
            throw new SeedNotFoundException(ErrorCode.NOT_FOUND.getMessage());
        }
        return batch;
    }

    private void ensureBatchExists(Long batchId) {
        if (seedBatchService.getById(batchId) == null) {
            throw new SeedNotFoundException("种子批次不存在");
        }
    }

    private SeedQualityTest findByBatchIdAndRequestKey(Long batchId, String requestKey) {
        if (batchId == null || !StringUtils.hasText(requestKey)) {
            return null;
        }
        LambdaQueryWrapper<SeedQualityTest> qw = new LambdaQueryWrapper<SeedQualityTest>();
        qw.eq(SeedQualityTest::getBatchId, batchId)
                .eq(SeedQualityTest::getRequestKey, requestKey)
                .orderByDesc(SeedQualityTest::getId)
                .last("limit 1");
        return seedQualityTestService.getOne(qw, false);
    }

    private String normalizeRequestKey(String requestKey) {
        if (!StringUtils.hasText(requestKey)) {
            return null;
        }
        String normalized = requestKey.trim();
        if (normalized.length() > REQUEST_KEY_MAX_LENGTH) {
            throw new SeedServiceException(ErrorCode.VALIDATION_ERROR.getCode(), "requestKey长度不能超过64");
        }
        return normalized;
    }

    private void validatePercent(Double value, String fieldName) {
        if (value == null) {
            return;
        }
        if (value < PERCENT_MIN || value > PERCENT_MAX) {
            throw new SeedServiceException(
                    ErrorCode.VALIDATION_ERROR.getCode(),
                    fieldName + "必须在0到100之间"
            );
        }
    }

    private void fillBatchDynamicMeta(SeedBatch row) {
        if (row == null) {
            return;
        }
        SeedDynamicSchemaSupport.ResolveResult resolved = seedDynamicSchemaSupport.resolve("batch_fields", row.getFormConfigId());
        if (resolved == null || StringUtils.hasText(resolved.getErrorMessage())) {
            return;
        }
        seedDynamicMetaAssembler.fillBatchMeta(row, resolved);
    }

    private void fillTestDynamicMeta(SeedQualityTest row) {
        if (row == null) {
            return;
        }
        SeedDynamicSchemaSupport.ResolveResult resolved = seedDynamicSchemaSupport.resolve("test_fields", row.getFormConfigId());
        if (resolved == null || StringUtils.hasText(resolved.getErrorMessage())) {
            return;
        }
        seedDynamicMetaAssembler.fillTestMeta(row, resolved);
    }

    private void ensureResolveSuccess(SeedDynamicSchemaSupport.ResolveResult resolved) {
        if (resolved == null || !StringUtils.hasText(resolved.getErrorMessage())) {
            return;
        }
        String message = resolved.getErrorMessage();
        if (message.toLowerCase().contains("dynamic_form_config")) {
            throw new SeedServiceException(ErrorCode.INTERNAL_ERROR.getCode(), message);
        }
        throw new SeedServiceException(ErrorCode.VALIDATION_ERROR.getCode(), message);
    }
}
