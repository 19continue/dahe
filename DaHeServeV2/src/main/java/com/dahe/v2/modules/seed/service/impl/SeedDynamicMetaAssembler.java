package com.dahe.v2.modules.seed.service.impl;

import com.dahe.v2.modules.seed.model.SeedBatch;
import com.dahe.v2.modules.seed.model.SeedQualityTest;
import com.dahe.v2.modules.seed.support.SeedDynamicSchemaSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * seed 动态配置元数据组装器。
 *
 * <p>负责把 schema_json 解析为标签映射，并回填到批次/检测对象。</p>
 */
@Component
public class SeedDynamicMetaAssembler {

    private final ObjectMapper objectMapper;

    public SeedDynamicMetaAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void fillBatchMeta(SeedBatch row, SeedDynamicSchemaSupport.ResolveResult resolved) {
        if (row == null || resolved == null) {
            return;
        }
        row.setFormConfigId(resolved.getFormConfigId());
        row.setFormConfigName(resolved.getFormConfigName());
        row.setFormSchema(resolved.getFormSchema());
        SchemaMeta meta = parseSchemaMeta(resolved.getFormSchema());
        row.setExtraLabelMap(meta.labelMap);
        row.setExtraValueLabelMap(meta.valueLabelMap);
    }

    public void fillTestMeta(SeedQualityTest row, SeedDynamicSchemaSupport.ResolveResult resolved) {
        if (row == null || resolved == null) {
            return;
        }
        row.setFormConfigId(resolved.getFormConfigId());
        row.setFormConfigName(resolved.getFormConfigName());
        row.setFormSchema(resolved.getFormSchema());
        SchemaMeta meta = parseSchemaMeta(resolved.getFormSchema());
        row.setExtraLabelMap(meta.labelMap);
        row.setExtraValueLabelMap(meta.valueLabelMap);
    }

    /** 从 schema_json 提取字段标签与选项值标签映射。 */
    private SchemaMeta parseSchemaMeta(String schemaJson) {
        if (!StringUtils.hasText(schemaJson)) {
            return SchemaMeta.empty();
        }
        try {
            List<Map<String, Object>> rows = objectMapper.readValue(schemaJson, new TypeReference<List<Map<String, Object>>>() {
            });
            Map<String, String> labelMap = new LinkedHashMap<String, String>();
            Map<String, Map<String, String>> valueLabelMap = new LinkedHashMap<String, Map<String, String>>();
            for (Map<String, Object> row : rows) {
                if (row == null) {
                    continue;
                }
                String key = row.get("key") == null ? "" : String.valueOf(row.get("key")).trim();
                if (!StringUtils.hasText(key)) {
                    continue;
                }
                String label = row.get("label") == null ? "" : String.valueOf(row.get("label")).trim();
                labelMap.putIfAbsent(key, StringUtils.hasText(label) ? label : key);
                Map<String, String> optionValueMap = parseOptionValueMap(row.get("options"));
                if (!optionValueMap.isEmpty()) {
                    valueLabelMap.put(key, optionValueMap);
                }
            }
            return new SchemaMeta(labelMap, valueLabelMap);
        } catch (Exception ignored) {
            return SchemaMeta.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseOptionValueMap(Object optionsRaw) {
        if (!(optionsRaw instanceof List)) {
            return Collections.emptyMap();
        }
        Map<String, String> out = new LinkedHashMap<String, String>();
        for (Object optionRaw : (List<Object>) optionsRaw) {
            if (optionRaw == null) {
                continue;
            }
            if (optionRaw instanceof Map) {
                Map<String, Object> optionMap = (Map<String, Object>) optionRaw;
                String value = optionMap.get("value") == null ? "" : String.valueOf(optionMap.get("value")).trim();
                String label = optionMap.get("label") == null ? "" : String.valueOf(optionMap.get("label")).trim();
                if (!StringUtils.hasText(value) && StringUtils.hasText(label)) {
                    value = label;
                }
                String finalLabel = StringUtils.hasText(label) ? label : value;
                if (StringUtils.hasText(value)) {
                    out.putIfAbsent(value, finalLabel);
                }
                if (StringUtils.hasText(label)) {
                    out.putIfAbsent(label, label);
                }
                continue;
            }
            String text = String.valueOf(optionRaw).trim();
            if (StringUtils.hasText(text)) {
                out.putIfAbsent(text, text);
            }
        }
        return out;
    }

    private static class SchemaMeta {
        private final Map<String, String> labelMap;
        private final Map<String, Map<String, String>> valueLabelMap;

        private SchemaMeta(Map<String, String> labelMap, Map<String, Map<String, String>> valueLabelMap) {
            this.labelMap = labelMap == null ? Collections.<String, String>emptyMap() : labelMap;
            this.valueLabelMap = valueLabelMap == null ? Collections.<String, Map<String, String>>emptyMap() : valueLabelMap;
        }

        private static SchemaMeta empty() {
            return new SchemaMeta(Collections.<String, String>emptyMap(), Collections.<String, Map<String, String>>emptyMap());
        }
    }
}

