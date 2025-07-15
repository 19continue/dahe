package com.dahe.v2.modules.export.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dahe.v2.modules.export.mapper.ExportTemplateMapper;
import com.dahe.v2.modules.export.model.ExportTemplate;
import com.dahe.v2.modules.export.service.ExportTemplateService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 导出模板服务实现。
 */
@Service
public class ExportTemplateServiceImpl extends ServiceImpl<ExportTemplateMapper, ExportTemplate> implements ExportTemplateService {

    private final ObjectMapper objectMapper;

    public ExportTemplateServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Page<ExportTemplate> pageRows(String moduleKey, String templateCode, String status, long page, long pageSize) {
        Page<ExportTemplate> out = new Page<ExportTemplate>(page, pageSize);
        LambdaQueryWrapper<ExportTemplate> qw = new LambdaQueryWrapper<ExportTemplate>();
        if (StringUtils.hasText(moduleKey)) {
            qw.eq(ExportTemplate::getModuleKey, moduleKey.trim());
        }
        if (StringUtils.hasText(templateCode)) {
            qw.eq(ExportTemplate::getTemplateCode, templateCode.trim());
        }
        if (StringUtils.hasText(status)) {
            qw.eq(ExportTemplate::getStatus, status.trim());
        }
        qw.orderByAsc(ExportTemplate::getModuleKey)
                .orderByAsc(ExportTemplate::getTemplateCode)
                .orderByDesc(ExportTemplate::getVersionNo);
        return this.page(out, qw);
    }

    @Override
    public ExportTemplate findEnabledLatest(String moduleKey, String templateCode) {
        if (!StringUtils.hasText(moduleKey) || !StringUtils.hasText(templateCode)) {
            return null;
        }
        LambdaQueryWrapper<ExportTemplate> qw = new LambdaQueryWrapper<ExportTemplate>();
        qw.eq(ExportTemplate::getModuleKey, moduleKey.trim())
                .eq(ExportTemplate::getTemplateCode, templateCode.trim())
                .eq(ExportTemplate::getStatus, "enabled")
                .orderByDesc(ExportTemplate::getVersionNo)
                .last("limit 1");
        return this.getOne(qw, false);
    }

    @Override
    public List<String> parseFieldCodes(String fieldsJson) {
        if (!StringUtils.hasText(fieldsJson)) {
            return Collections.emptyList();
        }
        try {
            List<Object> raw = objectMapper.readValue(fieldsJson, new TypeReference<List<Object>>() {
            });
            List<String> out = new ArrayList<String>();
            for (Object row : raw) {
                if (row == null) {
                    continue;
                }
                String code = String.valueOf(row).trim();
                if (!code.isEmpty()) {
                    out.add(code);
                }
            }
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public String normalizeFieldsJson(String fieldsJson) {
        if (!StringUtils.hasText(fieldsJson)) {
            throw new IllegalArgumentException("字段配置不能为空");
        }
        try {
            List<Object> raw = objectMapper.readValue(fieldsJson, new TypeReference<List<Object>>() {
            });
            if (raw == null || raw.isEmpty()) {
                throw new IllegalArgumentException("字段配置不能为空");
            }
            Set<String> deduplicated = new LinkedHashSet<String>();
            for (Object row : raw) {
                if (row == null) {
                    continue;
                }
                String code = String.valueOf(row).trim();
                if (!StringUtils.hasText(code)) {
                    continue;
                }
                if (code.length() > 64) {
                    throw new IllegalArgumentException("字段编码长度不能超过64");
                }
                for (int i = 0; i < code.length(); i += 1) {
                    char c = code.charAt(i);
                    boolean ok = (c >= 'a' && c <= 'z')
                            || (c >= 'A' && c <= 'Z')
                            || (c >= '0' && c <= '9')
                            || c == '_';
                    if (!ok) {
                        throw new IllegalArgumentException("字段编码仅允许字母、数字和下划线");
                    }
                }
                deduplicated.add(code);
            }
            if (deduplicated.isEmpty()) {
                throw new IllegalArgumentException("字段配置不能为空");
            }
            if (deduplicated.size() > 200) {
                throw new IllegalArgumentException("字段数量不能超过200");
            }
            return objectMapper.writeValueAsString(new ArrayList<String>(deduplicated));
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("字段配置格式不正确，必须为字符串数组");
        }
    }
}
