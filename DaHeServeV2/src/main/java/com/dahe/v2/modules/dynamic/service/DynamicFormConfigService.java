package com.dahe.v2.modules.dynamic.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dahe.v2.modules.dynamic.model.DynamicFormConfig;

/**
 * 动态表单配置服务接口。
 */
public interface DynamicFormConfigService extends IService<DynamicFormConfig> {

    /** 分页查询动态表单配置。 */
    Page<DynamicFormConfig> pageConfigs(
            String moduleKey,
            String sceneKey,
            String keyword,
            String status,
            long page,
            long pageSize
    );

    /** 查询某模块某场景当前生效（或指定状态）的最高版本配置。 */
    DynamicFormConfig findCurrent(String moduleKey, String sceneKey, String status);

    /** 计算某配置名称下的下一个可用版本号。 */
    int nextVersionNo(String moduleKey, String sceneKey, String configName);

    /** 创建配置（含 schema 合同校验、版本号冲突重试）。 */
    DynamicFormConfig createConfig(DynamicFormConfigCommand.Upsert command);

    /** 更新配置（含 schema 合同校验）。 */
    DynamicFormConfig updateConfig(Long id, DynamicFormConfigCommand.Upsert command);

    /** 删除配置（含引用保护校验）。 */
    void deleteConfig(Long id);

    /** 校验并归一化 schema_json，返回可落库 JSON。 */
    String normalizeAndValidateSchemaJson(String schemaJson);
}
