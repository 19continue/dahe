package com.dahe.v2.modules.seed.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.seed.model.SeedBatch;
import com.dahe.v2.modules.seed.model.SeedQualityTest;
import com.dahe.v2.modules.seed.service.SeedAdminCommand;
import com.dahe.v2.modules.seed.service.SeedAdminFacadeService;
import com.dahe.v2.modules.seed.service.SeedNotFoundException;
import com.dahe.v2.modules.seed.service.SeedServiceException;
import lombok.Data;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

/**
 * 种子批次控制器。
 *
 * <p>本类只负责协议层处理，业务编排统一下沉至 `SeedAdminFacadeService`。</p>
 */
@RestController
@RequestMapping("/api/v2/seed-batches")
@Validated
public class SeedBatchController {

    private final SeedAdminFacadeService seedAdminFacadeService;

    public SeedBatchController(SeedAdminFacadeService seedAdminFacadeService) {
        this.seedAdminFacadeService = seedAdminFacadeService;
    }

    /** 分页查询批次。 */
    @GetMapping
    public Result<Page<SeedBatch>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cropType,
            @RequestParam(required = false) String varietyName,
            @RequestParam(required = false) Integer enabled,
            @RequestParam(defaultValue = "false") boolean includeDisabled,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) long pageSize
    ) {
        SeedAdminCommand.BatchPageQuery query = new SeedAdminCommand.BatchPageQuery();
        query.setKeyword(keyword);
        query.setCropType(cropType);
        query.setVarietyName(varietyName);
        query.setEnabled(enabled);
        query.setIncludeDisabled(includeDisabled);
        query.setPage(page);
        query.setPageSize(pageSize);
        return execute(() -> seedAdminFacadeService.pageBatches(query));
    }

    /** 新增批次。 */
    @PostMapping
    public Result<SeedBatch> create(@RequestBody @Validated SeedBatchCreateReq req) {
        return execute(() -> seedAdminFacadeService.createBatch(toBatchCreateCommand(req)));
    }

    /** 查询批次详情。 */
    @GetMapping("/{id}")
    public Result<SeedBatch> detail(@PathVariable Long id) {
        return execute(() -> seedAdminFacadeService.getBatchDetail(id));
    }

    /** 更新批次。 */
    @PutMapping("/{id}")
    public Result<SeedBatch> update(@PathVariable Long id, @RequestBody @Validated SeedBatchUpdateReq req) {
        return execute(() -> seedAdminFacadeService.updateBatch(id, toBatchUpdateCommand(req)));
    }

    /** 删除批次。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        return executeVoid(() -> seedAdminFacadeService.deleteBatch(id));
    }

    /** 批次启停切换。 */
    @PutMapping("/{id}/enabled")
    public Result<SeedBatch> updateEnabled(
            @PathVariable Long id,
            @RequestBody @Validated SeedBatchEnabledReq req
    ) {
        return execute(() -> seedAdminFacadeService.setBatchEnabled(id, Boolean.TRUE.equals(req.getEnabled())));
    }

    /** 查询批次下全部检测记录。 */
    @GetMapping("/{id}/tests")
    public Result<List<SeedQualityTest>> listTests(@PathVariable Long id) {
        return execute(() -> seedAdminFacadeService.listTests(id));
    }

    /** 查询单条检测详情。 */
    @GetMapping("/{batchId}/tests/{testId}")
    public Result<SeedQualityTest> testDetail(@PathVariable Long batchId, @PathVariable Long testId) {
        return execute(() -> seedAdminFacadeService.getTestDetail(batchId, testId));
    }

    /** 新增检测记录。 */
    @PostMapping("/{id}/tests")
    public Result<SeedQualityTest> createTest(@PathVariable Long id, @RequestBody @Validated SeedTestCreateReq req) {
        return execute(() -> seedAdminFacadeService.createTest(id, toTestUpsertCommand(req)));
    }

    /** 更新检测记录。 */
    @PutMapping("/{batchId}/tests/{testId}")
    public Result<SeedQualityTest> updateTest(
            @PathVariable Long batchId,
            @PathVariable Long testId,
            @RequestBody @Validated SeedTestCreateReq req
    ) {
        return execute(() -> seedAdminFacadeService.updateTest(batchId, testId, toTestUpsertCommand(req)));
    }

    /** 删除检测记录。 */
    @DeleteMapping("/{batchId}/tests/{testId}")
    public Result<Void> deleteTest(@PathVariable Long batchId, @PathVariable Long testId) {
        return executeVoid(() -> seedAdminFacadeService.deleteTest(batchId, testId));
    }

    private SeedAdminCommand.BatchCreate toBatchCreateCommand(SeedBatchCreateReq req) {
        SeedAdminCommand.BatchCreate command = new SeedAdminCommand.BatchCreate();
        command.setBatchCode(req.getBatchCode());
        command.setCropType(req.getCropType());
        command.setVarietyName(req.getVarietyName());
        command.setProductionDate(req.getProductionDate());
        command.setRemark(req.getRemark());
        command.setEnabled(req.getEnabled());
        command.setFormConfigId(req.getFormConfigId());
        command.setExtraJson(req.getExtraJson());
        return command;
    }

    private SeedAdminCommand.BatchUpdate toBatchUpdateCommand(SeedBatchUpdateReq req) {
        SeedAdminCommand.BatchUpdate command = new SeedAdminCommand.BatchUpdate();
        command.setBatchCode(req.getBatchCode());
        command.setCropType(req.getCropType());
        command.setVarietyName(req.getVarietyName());
        command.setProductionDate(req.getProductionDate());
        command.setRemark(req.getRemark());
        command.setEnabled(req.getEnabled());
        command.setFormConfigId(req.getFormConfigId());
        command.setExtraJson(req.getExtraJson());
        return command;
    }

    private SeedAdminCommand.TestUpsert toTestUpsertCommand(SeedTestCreateReq req) {
        SeedAdminCommand.TestUpsert command = new SeedAdminCommand.TestUpsert();
        command.setRequestKey(req.getRequestKey());
        command.setTestDate(req.getTestDate());
        command.setSampleCount(req.getSampleCount());
        command.setGerminationCount(req.getGerminationCount());
        command.setGerminationRate(req.getGerminationRate());
        command.setMoisture(req.getMoisture());
        command.setPurity(req.getPurity());
        command.setCleanliness(req.getCleanliness());
        command.setTesterName(req.getTesterName());
        command.setRemark(req.getRemark());
        command.setFormConfigId(req.getFormConfigId());
        command.setExtraJson(req.getExtraJson());
        return command;
    }

    private <T> Result<T> execute(Supplier<T> supplier) {
        try {
            return Result.success(supplier.get());
        } catch (SeedNotFoundException ex) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ex.getMessage());
        } catch (SeedServiceException ex) {
            return Result.failure(ex.getCode(), ex.getMessage());
        }
    }

    private Result<Void> executeVoid(Supplier<Boolean> supplier) {
        try {
            boolean ok = Boolean.TRUE.equals(supplier.get());
            if (ok) {
                return Result.success(null);
            }
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        } catch (SeedNotFoundException ex) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ex.getMessage());
        } catch (SeedServiceException ex) {
            return Result.failure(ex.getCode(), ex.getMessage());
        }
    }

    @Data
    /** 新增批次请求体。 */
    public static class SeedBatchCreateReq {
        @NotBlank(message = "批次编码不能为空")
        private String batchCode;
        @NotBlank(message = "作物类型不能为空")
        private String cropType;
        @NotBlank(message = "品种名称不能为空")
        private String varietyName;
        private LocalDate productionDate;
        private String remark;
        private Boolean enabled;
        private Long formConfigId;
        private String extraJson;
    }

    @Data
    /** 更新批次请求体。 */
    public static class SeedBatchUpdateReq {
        @NotBlank(message = "批次编码不能为空")
        private String batchCode;
        @NotBlank(message = "作物类型不能为空")
        private String cropType;
        @NotBlank(message = "品种名称不能为空")
        private String varietyName;
        private LocalDate productionDate;
        private String remark;
        private Boolean enabled;
        private Long formConfigId;
        private String extraJson;
    }

    @Data
    /** 批次启停请求体。 */
    public static class SeedBatchEnabledReq {
        @NotNull(message = "启用状态不能为空")
        private Boolean enabled;
    }

    @Data
    /** 新增/更新检测请求体。 */
    public static class SeedTestCreateReq {
        private String requestKey;
        @NotNull(message = "检测日期不能为空")
        private LocalDate testDate;
        private Integer sampleCount;
        private Integer germinationCount;
        private Double germinationRate;
        private Double moisture;
        private Double purity;
        private Double cleanliness;
        private String testerName;
        private String remark;
        private Long formConfigId;
        private String extraJson;
    }
}
