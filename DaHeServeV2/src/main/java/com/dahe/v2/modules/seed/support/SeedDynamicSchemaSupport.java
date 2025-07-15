package com.dahe.v2.modules.seed.support;

import com.dahe.v2.modules.dynamic.model.DynamicFormConfig;
import com.dahe.v2.modules.dynamic.service.DynamicFormConfigService;
import com.dahe.v2.modules.farm.process.support.StepFormSchemaValidator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
/**
 * 种子动态 schema 支撑组件。
 * 统一处理种子模块动态配置加载、schema 回填与 extraJson 校验。
 */
public class SeedDynamicSchemaSupport {

    private static final String MODULE_KEY = "seed";

    private final DynamicFormConfigService dynamicFormConfigService;
    private final StepFormSchemaValidator stepFormSchemaValidator;

    public SeedDynamicSchemaSupport(
            DynamicFormConfigService dynamicFormConfigService,
            StepFormSchemaValidator stepFormSchemaValidator
    ) {
        this.dynamicFormConfigService = dynamicFormConfigService;
        this.stepFormSchemaValidator = stepFormSchemaValidator;
    }

    /** 先解析动态 schema，再校验 extraJson 是否满足 schema 要求。 */
    public ResolveResult resolveAndValidate(String sceneKey, Long formConfigId, String extraJson) {
        ResolveResult result = resolve(sceneKey, formConfigId);
        if (StringUtils.hasText(result.getErrorMessage()) || !StringUtils.hasText(result.getFormSchema())) {
            return result;
        }
        String err = stepFormSchemaValidator.validate(result.getFormSchema(), extraJson);
        if (StringUtils.hasText(err)) {
            result.setErrorMessage(err);
        }
        return result;
    }

    /** 解析动态 schema（指定配置优先，否则取当前启用配置）。 */
    public ResolveResult resolve(String sceneKey, Long formConfigId) {
        ResolveResult result = new ResolveResult();
        DynamicFormConfig config = null;
        try {
            if (formConfigId != null) {
                config = dynamicFormConfigService.getById(formConfigId);
                if (config == null) {
                    result.setErrorMessage("参数模板配置无效");
                    return result;
                }
            } else {
                config = dynamicFormConfigService.findCurrent(MODULE_KEY, sceneKey, "enabled");
            }
        } catch (Exception e) {
            String message = e == null ? null : e.getMessage();
            if (message != null && message.toLowerCase().contains("dynamic_form_config")) {
                result.setErrorMessage("动态参数配置模块未初始化，请联系管理员检查服务启动日志和数据库权限");
                return result;
            }
            result.setErrorMessage("动态表单配置加载失败");
            return result;
        }

        if (config == null || !StringUtils.hasText(config.getSchemaJson())) {
            return result;
        }

        result.setFormConfigId(config.getId());
        result.setFormConfigName(config.getConfigName());
        result.setFormSchema(config.getSchemaJson());
        return result;
    }

    /** 动态 schema 解析结果对象。 */
    public static class ResolveResult {
        private Long formConfigId;
        private String formConfigName;
        private String formSchema;
        private String errorMessage;

        public Long getFormConfigId() {
            return formConfigId;
        }

        public void setFormConfigId(Long formConfigId) {
            this.formConfigId = formConfigId;
        }

        public String getFormConfigName() {
            return formConfigName;
        }

        public void setFormConfigName(String formConfigName) {
            this.formConfigName = formConfigName;
        }

        public String getFormSchema() {
            return formSchema;
        }

        public void setFormSchema(String formSchema) {
            this.formSchema = formSchema;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }
}
