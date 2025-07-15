package com.dahe.v2.modules.farm.process.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.crop.model.Crop;
import com.dahe.v2.modules.crop.service.CropService;
import com.dahe.v2.modules.dynamic.model.DynamicFormConfig;
import com.dahe.v2.modules.dynamic.service.DynamicFormConfigService;
import com.dahe.v2.modules.farm.process.model.FarmProcessStep;
import com.dahe.v2.modules.farm.process.model.FarmProcessTemplate;
import com.dahe.v2.modules.farm.process.service.FarmProcessStepService;
import com.dahe.v2.modules.farm.process.service.FarmProcessTemplateService;
import com.dahe.v2.modules.farm.process.support.StepFormSchemaResolver;
import lombok.Data;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;

    /**
 * 农事流程模板控制器。
 * 提供模板与步骤的后台管理能力，并支持步骤动态参数模板关联。
 */
@RestController
@RequestMapping("/api/v2/farm-process")
@Validated
public class FarmProcessController {

    /** 分类节点标识。 */
    private static final String NODE_CATEGORY = "category";
    /** 品种节点标识。 */
    private static final String NODE_VARIETY = "variety";

    private final FarmProcessTemplateService farmProcessTemplateService;
    private final FarmProcessStepService farmProcessStepService;
    private final CropService cropService;
    private final DynamicFormConfigService dynamicFormConfigService;
    private final StepFormSchemaResolver stepFormSchemaResolver;

    public FarmProcessController(
            FarmProcessTemplateService farmProcessTemplateService,
            FarmProcessStepService farmProcessStepService,
            CropService cropService,
            DynamicFormConfigService dynamicFormConfigService,
            StepFormSchemaResolver stepFormSchemaResolver
    ) {
        this.farmProcessTemplateService = farmProcessTemplateService;
        this.farmProcessStepService = farmProcessStepService;
        this.cropService = cropService;
        this.dynamicFormConfigService = dynamicFormConfigService;
        this.stepFormSchemaResolver = stepFormSchemaResolver;
    }

    @GetMapping("/templates")
    /** 分页查询流程模板。 */
    public Result<Page<TemplateItem>> templates(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long cropId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long varietyId,
            @RequestParam(required = false) Integer enabled,
            @RequestParam(defaultValue = "false") boolean includeDisabled,
            @RequestParam(defaultValue = "false") boolean includeSteps,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "50") @Min(1) long pageSize
    ) {
        LambdaQueryWrapper<FarmProcessTemplate> qw = new LambdaQueryWrapper<>();
        Integer enabledFilter = normalizeEnabled(enabled);
        if (enabledFilter == null && !includeDisabled) {
            enabledFilter = 1;
        }
        if (enabledFilter != null) {
            qw.eq(FarmProcessTemplate::getEnabled, enabledFilter);
        }
        Set<Long> filterCropIds = resolveTemplateFilterCropIds(cropId, categoryId, varietyId);
        if (filterCropIds != null) {
            if (filterCropIds.isEmpty()) {
                Page<TemplateItem> empty = new Page<>(page, pageSize, 0L);
                empty.setRecords(Collections.emptyList());
                return Result.success(empty);
            }
            if (filterCropIds.size() == 1) {
                qw.eq(FarmProcessTemplate::getCropId, filterCropIds.iterator().next());
            } else {
                qw.in(FarmProcessTemplate::getCropId, filterCropIds);
            }
        }
        if (StringUtils.hasText(keyword)) {
            qw.like(FarmProcessTemplate::getTemplateName, keyword.trim());
        }
        qw.orderByDesc(FarmProcessTemplate::getEnabled)
                .orderByDesc(FarmProcessTemplate::getIsDefault)
                .orderByAsc(FarmProcessTemplate::getId);

        Page<FarmProcessTemplate> raw = farmProcessTemplateService.page(new Page<>(page, pageSize), qw);
        List<FarmProcessTemplate> templates = raw.getRecords();

        Map<Long, TemplateBinding> bindingMap = resolveTemplateBindingMap(templates);
        Map<Long, List<StepItem>> stepMap = includeSteps ? resolveStepMap(templates) : Collections.emptyMap();

        Page<TemplateItem> out = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        out.setRecords(templates.stream().map(t -> {
            return buildTemplateItem(t, bindingMap, stepMap);
        }).collect(Collectors.toList()));
        return Result.success(out);
    }

    @GetMapping("/templates/{id}")
    /** 查询流程模板详情。 */
    public Result<TemplateItem> templateDetail(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean includeSteps
    ) {
        FarmProcessTemplate template = farmProcessTemplateService.getById(id);
        if (template == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        List<FarmProcessTemplate> rows = Collections.singletonList(template);
        Map<Long, TemplateBinding> bindingMap = resolveTemplateBindingMap(rows);
        Map<Long, List<StepItem>> stepMap = includeSteps ? resolveStepMap(rows) : Collections.emptyMap();
        return Result.success(buildTemplateItem(template, bindingMap, stepMap));
    }

    @PostMapping("/templates")
    /** 新增流程模板（仅 admin）。 */
    public Result<FarmProcessTemplate> createTemplate(
            @RequestBody @Validated TemplateSaveReq req
    ) {
        Crop bindCrop = resolveTemplateBindCrop(req);
        if (bindCrop == null || bindCrop.getId() == null) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "请选择作物，品种可选");
        }
        boolean enabled = req.getEnabled() == null || Boolean.TRUE.equals(req.getEnabled());
        if (!enabled && Boolean.TRUE.equals(req.getIsDefault())) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "禁用模板不能设为默认模板");
        }
        FarmProcessTemplate template = new FarmProcessTemplate();
        template.setCropId(bindCrop.getId());
        template.setTemplateName(req.getTemplateName().trim());
        template.setEnabled(enabled ? 1 : 0);
        template.setIsDefault(enabled && Boolean.TRUE.equals(req.getIsDefault()) ? 1 : 0);
        farmProcessTemplateService.save(template);
        if (template.getIsDefault() != null && template.getIsDefault() == 1) {
            clearDefaultOnSameCrop(template.getCropId(), template.getId());
        }
        return Result.success(template);
    }

    @PutMapping("/templates/{id}")
    /** 更新流程模板（仅 admin）。 */
    public Result<FarmProcessTemplate> updateTemplate(
            @PathVariable Long id,
            @RequestBody @Validated TemplateSaveReq req
    ) {
        FarmProcessTemplate template = farmProcessTemplateService.getById(id);
        if (template == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        Crop bindCrop = resolveTemplateBindCrop(req);
        if (bindCrop == null || bindCrop.getId() == null) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "请选择作物，品种可选");
        }
        boolean enabled = req.getEnabled() == null
                ? (template.getEnabled() == null || template.getEnabled() == 1)
                : Boolean.TRUE.equals(req.getEnabled());
        if (!enabled && Boolean.TRUE.equals(req.getIsDefault())) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "禁用模板不能设为默认模板");
        }
        template.setCropId(bindCrop.getId());
        template.setTemplateName(req.getTemplateName().trim());
        template.setEnabled(enabled ? 1 : 0);
        template.setIsDefault(enabled && Boolean.TRUE.equals(req.getIsDefault()) ? 1 : 0);
        farmProcessTemplateService.updateById(template);
        if (template.getIsDefault() != null && template.getIsDefault() == 1) {
            clearDefaultOnSameCrop(template.getCropId(), template.getId());
        }
        return Result.success(template);
    }

    @PutMapping("/templates/{id}/enabled")
    /** 更新模板启用状态（仅 admin）。 */
    public Result<FarmProcessTemplate> updateTemplateEnabled(
            @PathVariable Long id,
            @RequestBody @Validated TemplateEnabledReq req
    ) {
        if (req.getEnabled() == null) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "启用状态不能为空");
        }
        FarmProcessTemplate template = farmProcessTemplateService.getById(id);
        if (template == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        boolean enabled = Boolean.TRUE.equals(req.getEnabled());
        template.setEnabled(enabled ? 1 : 0);
        if (!enabled) {
            template.setIsDefault(0);
        }
        farmProcessTemplateService.updateById(template);
        return Result.success(template);
    }

    @DeleteMapping("/templates/{id}")
    /** 删除流程模板（仅 admin）。 */
    public Result<Boolean> deleteTemplate(@PathVariable Long id) {
        FarmProcessTemplate template = farmProcessTemplateService.getById(id);
        if (template == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        boolean removed = farmProcessTemplateService.removeTemplateCascade(id);
        if (!removed) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        return Result.success(Boolean.TRUE);
    }

    @GetMapping("/templates/{id}/steps")
    /** 查询模板下步骤。 */
    public Result<List<FarmProcessStep>> steps(@PathVariable Long id) {
        FarmProcessTemplate template = farmProcessTemplateService.getById(id);
        if (template == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        List<FarmProcessStep> rows = farmProcessStepService.listByTemplateId(id);
        stepFormSchemaResolver.applyResolvedSchemas(rows);
        return Result.success(rows);
    }

    @PostMapping("/templates/{id}/steps")
    /** 新增模板步骤（仅 admin）。 */
    public Result<FarmProcessStep> createStep(
            @PathVariable Long id,
            @RequestBody @Validated StepSaveReq req
    ) {
        FarmProcessTemplate template = farmProcessTemplateService.getById(id);
        if (template == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        Result<FarmProcessStep> invalidFormConfig = validateFormConfigId(req.getFormConfigId());
        if (invalidFormConfig != null) {
            return invalidFormConfig;
        }
        FarmProcessStep row = new FarmProcessStep();
        row.setTemplateId(id);
        row.setStepName(req.getStepName().trim());
        row.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        row.setGrowthStage(req.getGrowthStage());
        row.setRequirementDesc(req.getRequirementDesc());
        row.setFormConfigId(req.getFormConfigId());
        row.setFormSchema(req.getFormSchema());
        farmProcessStepService.save(row);
        row.setFormSchema(stepFormSchemaResolver.resolveFormSchema(row));
        return Result.success(row);
    }

    @PutMapping("/steps/{id}")
    /** 更新步骤（仅 admin）。 */
    public Result<FarmProcessStep> updateStep(
            @PathVariable Long id,
            @RequestBody @Validated StepSaveReq req
    ) {
        FarmProcessStep row = farmProcessStepService.getById(id);
        if (row == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        Result<FarmProcessStep> invalidFormConfig = validateFormConfigId(req.getFormConfigId());
        if (invalidFormConfig != null) {
            return invalidFormConfig;
        }
        row.setStepName(req.getStepName().trim());
        row.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        row.setGrowthStage(req.getGrowthStage());
        row.setRequirementDesc(req.getRequirementDesc());
        row.setFormConfigId(req.getFormConfigId());
        row.setFormSchema(req.getFormSchema());
        farmProcessStepService.updateById(row);
        row.setFormSchema(stepFormSchemaResolver.resolveFormSchema(row));
        return Result.success(row);
    }

    @PutMapping("/templates/{id}/steps/sort")
    /** 步骤排序（仅 admin）。 */
    public Result<List<FarmProcessStep>> sortSteps(
            @PathVariable Long id,
            @RequestBody @Validated StepSortReq req
    ) {
        FarmProcessTemplate template = farmProcessTemplateService.getById(id);
        if (template == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        List<Long> sortedStepIds = req.getStepIds() == null ? Collections.emptyList() : req.getStepIds().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (sortedStepIds.isEmpty()) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "步骤编号列表不能为空");
        }
        List<FarmProcessStep> rows = farmProcessStepService.listByTemplateId(id);
        Map<Long, FarmProcessStep> rowMap = rows.stream().collect(Collectors.toMap(FarmProcessStep::getId, x -> x, (a, b) -> a));
        int sort = 1;
        for (Long stepId : sortedStepIds) {
            FarmProcessStep step = rowMap.get(stepId);
            if (step == null) {
                continue;
            }
            step.setSortOrder(sort++);
            farmProcessStepService.updateById(step);
        }
        List<FarmProcessStep> latest = farmProcessStepService.listByTemplateId(id);
        stepFormSchemaResolver.applyResolvedSchemas(latest);
        return Result.success(latest);
    }

    /** 解析模板查询范围（按 crop/category/variety）。 */
    private Set<Long> resolveTemplateFilterCropIds(Long cropId, Long categoryId, Long varietyId) {
        if (varietyId != null) {
            Crop variety = cropService.getById(varietyId);
            if (variety == null || !NODE_VARIETY.equals(normalizeNodeType(variety.getNodeType()))) {
                return Collections.emptySet();
            }
            return Collections.singleton(variety.getId());
        }
        if (categoryId != null) {
            Crop category = cropService.getById(categoryId);
            if (category == null || !NODE_CATEGORY.equals(normalizeNodeType(category.getNodeType()))) {
                return Collections.emptySet();
            }
            LinkedHashSet<Long> ids = new LinkedHashSet<>();
            ids.add(category.getId());
            List<Crop> varieties = cropService.list(new LambdaQueryWrapper<Crop>()
                    .eq(Crop::getNodeType, NODE_VARIETY)
                    .eq(Crop::getParentId, category.getId()));
            for (Crop row : varieties) {
                if (row != null && row.getId() != null) {
                    ids.add(row.getId());
                }
            }
            return ids;
        }
        if (cropId != null) {
            return Collections.singleton(cropId);
        }
        return null;
    }

    /** 解析模板绑定作物节点。 */
    private Crop resolveTemplateBindCrop(TemplateSaveReq req) {
        Long cropId = req == null ? null : req.getCropId();
        Long categoryId = req == null ? null : req.getCategoryId();
        Long varietyId = req == null ? null : req.getVarietyId();

        if (varietyId != null) {
            Crop variety = cropService.getById(varietyId);
            if (variety == null || !NODE_VARIETY.equals(normalizeNodeType(variety.getNodeType()))) {
                return null;
            }
            if (categoryId != null && !Objects.equals(variety.getParentId(), categoryId)) {
                return null;
            }
            return variety;
        }
        if (categoryId != null) {
            Crop category = cropService.getById(categoryId);
            if (category == null || !NODE_CATEGORY.equals(normalizeNodeType(category.getNodeType()))) {
                return null;
            }
            return category;
        }
        if (cropId != null) {
            return cropService.getById(cropId);
        }
        return null;
    }

    /** 组装模板绑定信息（分类/品种展示文案）。 */
    private Map<Long, TemplateBinding> resolveTemplateBindingMap(List<FarmProcessTemplate> templates) {
        if (templates == null || templates.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> cropIds = templates.stream()
                .map(FarmProcessTemplate::getCropId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (cropIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Crop> cropMap = cropService.listByIds(cropIds).stream()
                .filter(Objects::nonNull)
                .filter(x -> x.getId() != null)
                .collect(Collectors.toMap(Crop::getId, x -> x, (a, b) -> a));
        Set<Long> categoryIds = cropMap.values().stream()
                .filter(x -> NODE_VARIETY.equals(normalizeNodeType(x.getNodeType())))
                .map(Crop::getParentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Crop> categoryMap = categoryIds.isEmpty()
                ? Collections.emptyMap()
                : cropService.listByIds(categoryIds).stream()
                .filter(Objects::nonNull)
                .filter(x -> x.getId() != null)
                .collect(Collectors.toMap(Crop::getId, x -> x, (a, b) -> a));

        Map<Long, TemplateBinding> out = new HashMap<>();
        for (Map.Entry<Long, Crop> entry : cropMap.entrySet()) {
            Long id = entry.getKey();
            Crop row = entry.getValue();
            if (id == null || row == null) {
                continue;
            }
            TemplateBinding binding = new TemplateBinding();
            String nodeType = normalizeNodeType(row.getNodeType());
            if (NODE_VARIETY.equals(nodeType)) {
                binding.setBindScope(NODE_VARIETY);
                binding.setVarietyId(row.getId());
                binding.setVarietyName(safeText(row.getVariety()));
                binding.setCategoryId(row.getParentId());
                String categoryName = safeText(row.getName());
                if (!StringUtils.hasText(categoryName) && row.getParentId() != null) {
                    Crop parent = categoryMap.get(row.getParentId());
                    categoryName = parent == null ? null : safeText(parent.getName());
                }
                binding.setCategoryName(categoryName);
                if (StringUtils.hasText(binding.getCategoryName()) && StringUtils.hasText(binding.getVarietyName())) {
                    binding.setDisplayName(binding.getCategoryName() + " · " + binding.getVarietyName());
                } else if (StringUtils.hasText(binding.getCategoryName())) {
                    binding.setDisplayName(binding.getCategoryName());
                } else {
                    binding.setDisplayName(binding.getVarietyName());
                }
            } else {
                binding.setBindScope(NODE_CATEGORY);
                binding.setCategoryId(row.getId());
                binding.setCategoryName(safeText(row.getName()));
                binding.setDisplayName(StringUtils.hasText(binding.getCategoryName())
                        ? binding.getCategoryName() + "（通用）"
                        : "作物通用模板");
            }
            out.put(id, binding);
        }
        return out;
    }

    /** 组装模板步骤视图，并回填动态 schema。 */
    private Map<Long, List<StepItem>> resolveStepMap(List<FarmProcessTemplate> templates) {
        if (templates == null || templates.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> templateIds = templates.stream()
                .map(FarmProcessTemplate::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (templateIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<FarmProcessStep> steps = farmProcessStepService.listByTemplateIds(templateIds);
        Map<Long, DynamicFormConfig> configMap = stepFormSchemaResolver.resolveConfigMap(steps);
        Map<Long, List<StepItem>> grouped = new HashMap<>();
        for (FarmProcessStep step : steps) {
            if (step.getTemplateId() == null) {
                continue;
            }
            StepItem item = new StepItem();
            item.setId(step.getId());
            item.setTemplateId(step.getTemplateId());
            item.setStepName(step.getStepName());
            item.setSortOrder(step.getSortOrder());
            item.setGrowthStage(step.getGrowthStage());
            item.setRequirementDesc(step.getRequirementDesc());
            item.setFormConfigId(step.getFormConfigId());
            DynamicFormConfig config = step.getFormConfigId() == null ? null : configMap.get(step.getFormConfigId());
            item.setFormConfigName(config == null ? null : config.getConfigName());
            item.setFormSchema(stepFormSchemaResolver.resolveFormSchema(step, configMap));
            grouped.computeIfAbsent(step.getTemplateId(), key -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    /** 将模板实体映射为前端展示对象。 */
    private TemplateItem buildTemplateItem(
            FarmProcessTemplate template,
            Map<Long, TemplateBinding> bindingMap,
            Map<Long, List<StepItem>> stepMap
    ) {
        TemplateItem item = new TemplateItem();
        item.setId(template.getId());
        item.setCropId(template.getCropId());
        TemplateBinding binding = bindingMap == null ? null : bindingMap.get(template.getCropId());
        item.setCropName(binding == null ? null : binding.getDisplayName());
        item.setCategoryId(binding == null ? null : binding.getCategoryId());
        item.setCategoryName(binding == null ? null : binding.getCategoryName());
        item.setVarietyId(binding == null ? null : binding.getVarietyId());
        item.setVarietyName(binding == null ? null : binding.getVarietyName());
        item.setBindScope(binding == null ? null : binding.getBindScope());
        item.setTemplateName(template.getTemplateName());
        item.setIsDefault(template.getIsDefault());
        item.setEnabled(template.getEnabled());
        item.setSteps(stepMap == null ? Collections.emptyList() : stepMap.getOrDefault(template.getId(), Collections.emptyList()));
        return item;
    }

    /** 校验动态配置 ID 是否有效。 */
    private Result<FarmProcessStep> validateFormConfigId(Long formConfigId) {
        if (formConfigId == null) {
            return null;
        }
        try {
            DynamicFormConfig row = dynamicFormConfigService.getById(formConfigId);
            if (row == null) {
                return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "参数模板配置无效");
            }
            return null;
        } catch (Exception e) {
            String message = e.getMessage();
            if (message != null && message.toLowerCase().contains("dynamic_form_config")) {
                return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), "动态参数配置模块未初始化，请联系管理员检查服务启动日志和数据库权限");
            }
            return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage());
        }
    }

    /** 归一化节点类型。 */
    private String normalizeNodeType(String value) {
        if (!StringUtils.hasText(value)) {
            return NODE_VARIETY;
        }
        String text = value.trim().toLowerCase(Locale.ROOT);
        if (NODE_CATEGORY.equals(text)) {
            return NODE_CATEGORY;
        }
        return NODE_VARIETY;
    }

    /** 安全文本清洗。 */
    private String safeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    /** 启用状态归一化（仅 0/1）。 */
    private Integer normalizeEnabled(Integer enabled) {
        if (enabled == null) {
            return null;
        }
        return enabled == 0 ? 0 : 1;
    }

    /** 清理同作物其他默认模板，保持单默认。 */
    private void clearDefaultOnSameCrop(Long cropId, Long currentTemplateId) {
        if (cropId == null) {
            return;
        }
        LambdaQueryWrapper<FarmProcessTemplate> qw = new LambdaQueryWrapper<>();
        qw.eq(FarmProcessTemplate::getCropId, cropId)
                .eq(FarmProcessTemplate::getIsDefault, 1)
                .ne(currentTemplateId != null, FarmProcessTemplate::getId, currentTemplateId);
        List<FarmProcessTemplate> rows = farmProcessTemplateService.list(qw);
        for (FarmProcessTemplate row : rows) {
            row.setIsDefault(0);
            farmProcessTemplateService.updateById(row);
        }
    }

    @Data
    public static class TemplateItem {
        /** 模板 ID。 */
        private Long id;
        /** 绑定作物 ID。 */
        private Long cropId;
        /** 绑定作物展示名。 */
        private String cropName;
        /** 分类 ID。 */
        private Long categoryId;
        /** 分类名称。 */
        private String categoryName;
        /** 品种 ID。 */
        private Long varietyId;
        /** 品种名称。 */
        private String varietyName;
        /** 绑定范围（category/variety）。 */
        private String bindScope;
        /** 模板名称。 */
        private String templateName;
        /** 是否默认。 */
        private Integer isDefault;
        /** 是否启用。 */
        private Integer enabled;
        /** 步骤列表。 */
        private List<StepItem> steps;
    }

    @Data
    private static class TemplateBinding {
        /** 分类 ID。 */
        private Long categoryId;
        /** 分类名称。 */
        private String categoryName;
        /** 品种 ID。 */
        private Long varietyId;
        /** 品种名称。 */
        private String varietyName;
        /** 绑定范围。 */
        private String bindScope;
        /** 组合显示名。 */
        private String displayName;
    }

    @Data
    public static class StepItem {
        /** 步骤 ID。 */
        private Long id;
        /** 模板 ID。 */
        private Long templateId;
        /** 步骤名称。 */
        private String stepName;
        /** 排序。 */
        private Integer sortOrder;
        /** 生长阶段。 */
        private String growthStage;
        /** 要求说明。 */
        private String requirementDesc;
        /** 动态配置 ID。 */
        private Long formConfigId;
        /** 动态配置名称。 */
        private String formConfigName;
        /** 最终表单 schema。 */
        private String formSchema;
    }

    @Data
    public static class TemplateSaveReq {
        /** 兼容旧参数：作物 ID。 */
        private Long cropId;
        /** 分类 ID。 */
        private Long categoryId;
        /** 品种 ID。 */
        private Long varietyId;

        @NotBlank(message = "模板名称不能为空")
        /** 模板名称。 */
        private String templateName;

        /** 是否默认模板。 */
        private Boolean isDefault;
        /** 是否启用。 */
        private Boolean enabled;
    }

    @Data
    public static class TemplateEnabledReq {
        @NotNull(message = "启用状态不能为空")
        /** 启用状态。 */
        private Boolean enabled;
    }

    @Data
    public static class StepSaveReq {
        @NotBlank(message = "步骤名称不能为空")
        /** 步骤名称。 */
        private String stepName;

        /** 步骤排序。 */
        private Integer sortOrder;
        /** 生长阶段。 */
        private String growthStage;
        /** 要求说明。 */
        private String requirementDesc;
        /** 动态配置 ID。 */
        private Long formConfigId;
        /** 步骤内置 schema（兜底）。 */
        private String formSchema;
    }

    @Data
    public static class StepSortReq {
        /** 排序后的步骤 ID 列表。 */
        private List<Long> stepIds;
    }
}
