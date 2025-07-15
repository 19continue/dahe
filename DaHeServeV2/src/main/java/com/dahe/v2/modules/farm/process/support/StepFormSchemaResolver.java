package com.dahe.v2.modules.farm.process.support;

import com.dahe.v2.modules.dynamic.model.DynamicFormConfig;
import com.dahe.v2.modules.dynamic.service.DynamicFormConfigService;
import com.dahe.v2.modules.farm.process.model.FarmProcessStep;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 步骤表单结构解析器。
 * 解析优先级：动态配置表 `dynamic_form_config` > 步骤内置 `formSchema`。
 */
@Component
public class StepFormSchemaResolver {

    private final DynamicFormConfigService dynamicFormConfigService;

    public StepFormSchemaResolver(DynamicFormConfigService dynamicFormConfigService) {
        this.dynamicFormConfigService = dynamicFormConfigService;
    }

    /** 解析单个步骤的最终表单结构。 */
    public String resolveFormSchema(FarmProcessStep step) {
        /*
         * 单步骤 schema 解析入口。
         * 调用方不需要关心 schema 到底存在哪里，只要拿“最终可用的表单定义”即可。
         */
        if (step == null) {
            return null;
        }
        // 单步骤场景也复用批量配置加载逻辑，避免出现两套解析规则。
        Map<Long, DynamicFormConfig> configMap = resolveConfigMap(Collections.singletonList(step));
        return resolveFormSchema(step, configMap);
    }

    /** 使用预加载配置映射解析步骤表单结构。 */
    public String resolveFormSchema(FarmProcessStep step, Map<Long, DynamicFormConfig> configMap) {
        /*
         * schema 的优先级是：
         * dynamic_form_config.schema_json > step.formSchema。
         *
         * 这样做的好处是，步骤本身可以有一个默认表单，
         * 后续如果想在不改步骤主数据的情况下调整表单结构，也可以通过动态配置覆盖。
         */
        if (step == null) {
            return null;
        }
        String fallback = step.getFormSchema();
        // formConfigId 指向动态表单配置表。
        Long formConfigId = step.getFormConfigId();
        if (formConfigId == null) {
            // 没配置动态表单时，直接回退到步骤自带 schema。
            return fallback;
        }
        DynamicFormConfig config = configMap == null ? null : configMap.get(formConfigId);
        if (config != null && StringUtils.hasText(config.getSchemaJson())) {
            // 动态配置存在且有 schema 时，优先使用动态配置。
            return config.getSchemaJson();
        }
        // 动态配置缺失时，再回退到步骤内置 schema。
        return fallback;
    }

    /** 批量加载步骤引用的动态配置。 */
    public Map<Long, DynamicFormConfig> resolveConfigMap(List<FarmProcessStep> steps) {
        /*
         * 批量预加载动态表单配置，避免列表场景下每个步骤都单独查一次数据库。
         */
        if (steps == null || steps.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> configIds = steps.stream()
                .map(FarmProcessStep::getFormConfigId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (configIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            // 一次性批量查出所有动态表单配置，减少循环查库。
            return dynamicFormConfigService.listByIds(configIds).stream()
                    .collect(Collectors.toMap(DynamicFormConfig::getId, x -> x, (a, b) -> a));
        } catch (Exception e) {
            // 动态配置表可能尚未初始化，回退到步骤内置 formSchema。
            return Collections.emptyMap();
        }
    }

    /** 批量回填步骤最终 formSchema。 */
    public void applyResolvedSchemas(List<FarmProcessStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return;
        }
        Map<Long, DynamicFormConfig> configMap = resolveConfigMap(steps);
        for (FarmProcessStep step : steps) {
            // 直接把最终解析结果回填到 step 上，方便调用方后续直接用。
            step.setFormSchema(resolveFormSchema(step, configMap));
        }
    }
}
