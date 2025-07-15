package com.dahe.v2.modules.dynamic.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.auth.support.AdminMenuCode;
import com.dahe.v2.modules.dynamic.model.DynamicFormConfig;
import com.dahe.v2.modules.dynamic.service.DynamicFormConfigCommand;
import com.dahe.v2.modules.dynamic.service.DynamicFormConfigService;
import lombok.Data;
import org.springframework.dao.DataAccessException;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * 动态表单配置控制器。
 * 控制层只负责协议转换，业务规则统一下沉到 service。
 */
@RestController
@Validated
public class DynamicFormConfigController {

    private static final String SEED_MODULE_KEY = "seed";
    private static final String SEED_CONFIG_TYPE_BATCH = "batch";
    private static final String SEED_CONFIG_TYPE_TEST = "test";
    private static final String SEED_SCENE_BATCH_FIELDS = "batch_fields";
    private static final String SEED_SCENE_TEST_FIELDS = "test_fields";

    private final DynamicFormConfigService dynamicFormConfigService;

    public DynamicFormConfigController(DynamicFormConfigService dynamicFormConfigService) {
        this.dynamicFormConfigService = dynamicFormConfigService;
    }

    @GetMapping("/api/v2/dynamic-configs/current")
    public Result<DynamicFormConfig> current(
            @RequestParam String moduleKey,
            @RequestParam String sceneKey,
            @RequestParam(defaultValue = "enabled") String status
    ) {
        return execute(() -> {
            DynamicFormConfig row = dynamicFormConfigService.findCurrent(moduleKey, sceneKey, status);
            if (row == null) {
                throw new java.util.NoSuchElementException(ErrorCode.NOT_FOUND.getMessage());
            }
            return row;
        });
    }

    @GetMapping("/api/v2/admin/dynamic-configs")
    @AdminMenuCode("/farm-step-dynamic-configs")
    public Result<Page<DynamicFormConfig>> page(
            @RequestParam(required = false) String moduleKey,
            @RequestParam(required = false) String sceneKey,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) long pageSize
    ) {
        return execute(() -> dynamicFormConfigService.pageConfigs(moduleKey, sceneKey, keyword, status, page, pageSize));
    }

    @GetMapping("/api/v2/admin/dynamic-configs/{id}")
    @AdminMenuCode("/farm-step-dynamic-configs")
    public Result<DynamicFormConfig> detail(@PathVariable Long id) {
        return execute(() -> {
            DynamicFormConfig row = dynamicFormConfigService.getById(id);
            if (row == null) {
                throw new java.util.NoSuchElementException(ErrorCode.NOT_FOUND.getMessage());
            }
            return row;
        });
    }

    @PostMapping("/api/v2/admin/dynamic-configs")
    @AdminMenuCode("/farm-step-dynamic-configs")
    public Result<DynamicFormConfig> create(@RequestBody @Valid SaveReq req) {
        return execute(() -> dynamicFormConfigService.createConfig(toCommand(req)));
    }

    @PutMapping("/api/v2/admin/dynamic-configs/{id}")
    @AdminMenuCode("/farm-step-dynamic-configs")
    public Result<DynamicFormConfig> update(@PathVariable Long id, @RequestBody @Valid SaveReq req) {
        return execute(() -> dynamicFormConfigService.updateConfig(id, toCommand(req)));
    }

    @DeleteMapping("/api/v2/admin/dynamic-configs/{id}")
    @AdminMenuCode("/farm-step-dynamic-configs")
    public Result<Boolean> delete(@PathVariable Long id) {
        return execute(() -> {
            dynamicFormConfigService.deleteConfig(id);
            return Boolean.TRUE;
        });
    }

    @GetMapping("/api/v2/admin/seed-dynamic-configs")
    @AdminMenuCode({"/seed-dynamic-configs", "/seed-dynamic-configs/batch", "/seed-dynamic-configs/test"})
    public Result<Page<DynamicFormConfig>> pageSeedConfigs(
            @RequestParam String configType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) long pageSize
    ) {
        String sceneKey = resolveSeedSceneKey(configType);
        if (!StringUtils.hasText(sceneKey)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "configType仅支持batch/test");
        }
        return execute(() -> dynamicFormConfigService.pageConfigs(SEED_MODULE_KEY, sceneKey, keyword, status, page, pageSize));
    }

    @GetMapping("/api/v2/admin/seed-dynamic-configs/{id}")
    @AdminMenuCode({"/seed-dynamic-configs", "/seed-dynamic-configs/batch", "/seed-dynamic-configs/test"})
    public Result<DynamicFormConfig> seedConfigDetail(@PathVariable Long id) {
        return execute(() -> {
            DynamicFormConfig row = dynamicFormConfigService.getById(id);
            if (row == null) {
                throw new java.util.NoSuchElementException(ErrorCode.NOT_FOUND.getMessage());
            }
            if (!isSeedConfig(row)) {
                throw new IllegalArgumentException("配置不属于种子批次或检测场景");
            }
            return row;
        });
    }

    @PostMapping("/api/v2/admin/seed-dynamic-configs")
    @AdminMenuCode({"/seed-dynamic-configs", "/seed-dynamic-configs/batch", "/seed-dynamic-configs/test"})
    public Result<DynamicFormConfig> createSeedConfig(@RequestBody @Valid SeedSaveReq req) {
        String sceneKey = resolveSeedSceneKey(req.getConfigType());
        if (!StringUtils.hasText(sceneKey)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "configType仅支持batch/test");
        }
        return execute(() -> {
            DynamicFormConfigCommand.Upsert command = toSeedCommand(req, sceneKey);
            return dynamicFormConfigService.createConfig(command);
        });
    }

    @PutMapping("/api/v2/admin/seed-dynamic-configs/{id}")
    @AdminMenuCode({"/seed-dynamic-configs", "/seed-dynamic-configs/batch", "/seed-dynamic-configs/test"})
    public Result<DynamicFormConfig> updateSeedConfig(@PathVariable Long id, @RequestBody @Valid SeedSaveReq req) {
        String sceneKey = resolveSeedSceneKey(req.getConfigType());
        if (!StringUtils.hasText(sceneKey)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "configType仅支持batch/test");
        }
        return execute(() -> {
            DynamicFormConfig row = dynamicFormConfigService.getById(id);
            if (row == null) {
                throw new java.util.NoSuchElementException(ErrorCode.NOT_FOUND.getMessage());
            }
            if (!isSeedConfig(row)) {
                throw new IllegalArgumentException("配置不属于种子批次或检测场景");
            }
            return dynamicFormConfigService.updateConfig(id, toSeedCommand(req, sceneKey));
        });
    }

    @DeleteMapping("/api/v2/admin/seed-dynamic-configs/{id}")
    @AdminMenuCode({"/seed-dynamic-configs", "/seed-dynamic-configs/batch", "/seed-dynamic-configs/test"})
    public Result<Boolean> deleteSeedConfig(@PathVariable Long id) {
        return execute(() -> {
            DynamicFormConfig row = dynamicFormConfigService.getById(id);
            if (row == null) {
                throw new java.util.NoSuchElementException(ErrorCode.NOT_FOUND.getMessage());
            }
            if (!isSeedConfig(row)) {
                throw new IllegalArgumentException("配置不属于种子批次或检测场景");
            }
            dynamicFormConfigService.deleteConfig(id);
            return Boolean.TRUE;
        });
    }

    private DynamicFormConfigCommand.Upsert toCommand(SaveReq req) {
        DynamicFormConfigCommand.Upsert command = new DynamicFormConfigCommand.Upsert();
        command.setModuleKey(req.getModuleKey());
        command.setSceneKey(req.getSceneKey());
        command.setConfigName(req.getConfigName());
        command.setSchemaJson(req.getSchemaJson());
        command.setStatus(req.getStatus());
        command.setVersionNo(req.getVersionNo());
        command.setRemark(req.getRemark());
        return command;
    }

    private DynamicFormConfigCommand.Upsert toSeedCommand(SeedSaveReq req, String sceneKey) {
        DynamicFormConfigCommand.Upsert command = new DynamicFormConfigCommand.Upsert();
        command.setModuleKey(SEED_MODULE_KEY);
        command.setSceneKey(sceneKey);
        command.setConfigName(req.getConfigName());
        command.setSchemaJson(req.getSchemaJson());
        command.setStatus(req.getStatus());
        command.setVersionNo(req.getVersionNo());
        command.setRemark(req.getRemark());
        return command;
    }

    private String resolveSeedSceneKey(String configType) {
        String type = StringUtils.hasText(configType) ? configType.trim().toLowerCase(Locale.ROOT) : "";
        if (SEED_CONFIG_TYPE_BATCH.equals(type)) {
            return SEED_SCENE_BATCH_FIELDS;
        }
        if (SEED_CONFIG_TYPE_TEST.equals(type)) {
            return SEED_SCENE_TEST_FIELDS;
        }
        return null;
    }

    private boolean isSeedConfig(DynamicFormConfig row) {
        if (row == null) {
            return false;
        }
        String moduleKey = StringUtils.hasText(row.getModuleKey()) ? row.getModuleKey().trim().toLowerCase(Locale.ROOT) : "";
        String sceneKey = StringUtils.hasText(row.getSceneKey()) ? row.getSceneKey().trim().toLowerCase(Locale.ROOT) : "";
        return SEED_MODULE_KEY.equals(moduleKey)
                && (SEED_SCENE_BATCH_FIELDS.equals(sceneKey) || SEED_SCENE_TEST_FIELDS.equals(sceneKey));
    }

    private <T> Result<T> execute(Supplier<T> supplier) {
        try {
            return Result.success(supplier.get());
        } catch (java.util.NoSuchElementException ex) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ErrorCode.NOT_FOUND.getMessage());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), ex.getMessage());
        } catch (DataAccessException ex) {
            return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), resolveDataAccessMessage(ex));
        } catch (Exception ex) {
            return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage());
        }
    }

    private String resolveDataAccessMessage(DataAccessException ex) {
        String message = ex == null ? null : ex.getMessage();
        String lower = message == null ? null : message.toLowerCase(Locale.ROOT);
        if (lower != null && (lower.contains("uk_dynamic_module_scene_name_version") || lower.contains("uk_dynamic_module_scene_version"))) {
            return "同一模块、场景、配置名称下的版本号已存在，请调整版本号后重试";
        }
        if (lower != null && lower.contains("dynamic_form_config")) {
            return "动态参数配置模块未初始化，请联系管理员检查服务启动日志和数据库权限";
        }
        return ErrorCode.INTERNAL_ERROR.getMessage();
    }

    @Data
    public static class SaveReq {
        @NotBlank(message = "moduleKey不能为空")
        private String moduleKey;
        @NotBlank(message = "sceneKey不能为空")
        private String sceneKey;
        @NotBlank(message = "configName不能为空")
        private String configName;
        @NotBlank(message = "schemaJson不能为空")
        private String schemaJson;
        private String status;
        @Min(1)
        private Integer versionNo;
        private String remark;
    }

    @Data
    public static class SeedSaveReq {
        @NotBlank(message = "configType不能为空")
        private String configType;
        @NotBlank(message = "configName不能为空")
        private String configName;
        @NotBlank(message = "schemaJson不能为空")
        private String schemaJson;
        private String status;
        @Min(1)
        private Integer versionNo;
        private String remark;
    }
}
