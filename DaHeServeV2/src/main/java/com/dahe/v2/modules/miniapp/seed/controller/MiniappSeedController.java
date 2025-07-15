package com.dahe.v2.modules.miniapp.seed.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.seed.controller.SeedBatchController;
import com.dahe.v2.modules.seed.controller.SeedRuleController;
import com.dahe.v2.modules.seed.model.SeedBatch;
import com.dahe.v2.modules.seed.model.SeedQualityTest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.Min;
import java.util.List;

/**
 * 小程序端种子相关接口入口。
 */
@RestController
@RequestMapping("/api/v2/miniapp")
@Validated
public class MiniappSeedController {

    private final SeedBatchController seedBatchController;
    private final SeedRuleController seedRuleController;

    public MiniappSeedController(
            SeedBatchController seedBatchController,
            SeedRuleController seedRuleController
    ) {
        this.seedBatchController = seedBatchController;
        this.seedRuleController = seedRuleController;
    }

    @GetMapping("/seed-batches")
    public Result<Page<SeedBatch>> pageBatches(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cropType,
            @RequestParam(required = false) String varietyName,
            @RequestParam(required = false) Integer enabled,
            @RequestParam(defaultValue = "false") boolean includeDisabled,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) long pageSize
    ) {
        return seedBatchController.page(keyword, cropType, varietyName, enabled, includeDisabled, page, pageSize);
    }

    @PostMapping("/seed-batches")
    public Result<SeedBatch> createBatch(@RequestBody @Validated SeedBatchController.SeedBatchCreateReq req) {
        return seedBatchController.create(req);
    }

    @GetMapping("/seed-batches/{id}")
    public Result<SeedBatch> batchDetail(@PathVariable Long id) {
        return seedBatchController.detail(id);
    }

    @PutMapping("/seed-batches/{id}")
    public Result<SeedBatch> updateBatch(
            @PathVariable Long id,
            @RequestBody @Validated SeedBatchController.SeedBatchUpdateReq req
    ) {
        return seedBatchController.update(id, req);
    }

    @DeleteMapping("/seed-batches/{id}")
    public Result<Void> deleteBatch(@PathVariable Long id) {
        return seedBatchController.delete(id);
    }

    @PutMapping("/seed-batches/{id}/enabled")
    public Result<SeedBatch> updateBatchEnabled(
            @PathVariable Long id,
            @RequestBody @Validated SeedBatchController.SeedBatchEnabledReq req
    ) {
        return seedBatchController.updateEnabled(id, req);
    }

    @GetMapping("/seed-batches/{id}/tests")
    public Result<List<SeedQualityTest>> listTests(@PathVariable Long id) {
        return seedBatchController.listTests(id);
    }

    @GetMapping("/seed-batches/{batchId}/tests/{testId}")
    public Result<SeedQualityTest> testDetail(@PathVariable Long batchId, @PathVariable Long testId) {
        return seedBatchController.testDetail(batchId, testId);
    }

    @PostMapping("/seed-batches/{id}/tests")
    public Result<SeedQualityTest> createTest(
            @PathVariable Long id,
            @RequestBody @Validated SeedBatchController.SeedTestCreateReq req
    ) {
        return seedBatchController.createTest(id, req);
    }

    @PutMapping("/seed-batches/{batchId}/tests/{testId}")
    public Result<SeedQualityTest> updateTest(
            @PathVariable Long batchId,
            @PathVariable Long testId,
            @RequestBody @Validated SeedBatchController.SeedTestCreateReq req
    ) {
        return seedBatchController.updateTest(batchId, testId, req);
    }

    @DeleteMapping("/seed-batches/{batchId}/tests/{testId}")
    public Result<Void> deleteTest(@PathVariable Long batchId, @PathVariable Long testId) {
        return seedBatchController.deleteTest(batchId, testId);
    }

    @GetMapping("/seed-settings")
    public Result<SeedRuleController.SeedRuleResp> getRule() {
        return seedRuleController.getCurrent();
    }

    @PutMapping("/seed-settings")
    public Result<SeedRuleController.SeedRuleResp> updateRule(
            @RequestBody @Validated SeedRuleController.SeedRuleUpdateReq req
    ) {
        return seedRuleController.update(req);
    }
}

