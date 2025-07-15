package com.dahe.v2.modules.export.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dahe.v2.modules.export.model.ExportTemplate;

import java.util.List;

/**
 * 导出模板服务接口。
 */
public interface ExportTemplateService extends IService<ExportTemplate> {

    Page<ExportTemplate> pageRows(String moduleKey, String templateCode, String status, long page, long pageSize);

    ExportTemplate findEnabledLatest(String moduleKey, String templateCode);

    List<String> parseFieldCodes(String fieldsJson);

    /**
     * 规范化字段配置 JSON：
     * 1. 必须是字符串数组
     * 2. 自动去重并保持顺序
     * 3. 字段编码仅允许字母/数字/下划线，长度 <= 64
     * 4. 字段数量 <= 200
     */
    String normalizeFieldsJson(String fieldsJson);
}
