package com.dahe.v2.modules.dynamic.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dahe.v2.modules.dynamic.mapper.DynamicFormConfigMapper;
import com.dahe.v2.modules.dynamic.model.DynamicFormConfig;
import com.dahe.v2.modules.dynamic.service.DynamicFormConfigCommand;
import com.dahe.v2.modules.dynamic.service.DynamicFormConfigService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 动态表单配置服务实现。
 */
@Service
public class DynamicFormConfigServiceImpl extends ServiceImpl<DynamicFormConfigMapper, DynamicFormConfig>
        implements DynamicFormConfigService {

    private static final int MAX_AUTO_VERSION_RETRY = 3;
    private static final int MAX_FIELD_KEY_LENGTH = 64;
    private static final Set<String> OPTION_TYPES = new HashSet<String>(Arrays.asList("select", "radio", "checkbox"));
    private static final Set<String> ALLOWED_TYPES = new HashSet<String>(Arrays.asList(
            "text", "textarea", "number", "select", "radio", "checkbox", "date", "datetime", "switch"
    ));

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public DynamicFormConfigServiceImpl(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Page<DynamicFormConfig> pageConfigs(
            String moduleKey,
            String sceneKey,
            String keyword,
            String status,
            long page,
            long pageSize
    ) {
        LambdaQueryWrapper<DynamicFormConfig> qw = new LambdaQueryWrapper<DynamicFormConfig>();
        if (StringUtils.hasText(moduleKey)) {
            qw.eq(DynamicFormConfig::getModuleKey, normalizeIdentifier(moduleKey));
        }
        if (StringUtils.hasText(sceneKey)) {
            qw.eq(DynamicFormConfig::getSceneKey, normalizeIdentifier(sceneKey));
        }
        if (StringUtils.hasText(status)) {
            qw.eq(DynamicFormConfig::getStatus, normalizeStatus(status));
        }
        if (StringUtils.hasText(keyword)) {
            String text = keyword.trim();
            qw.and(q -> q.like(DynamicFormConfig::getConfigName, text)
                    .or().like(DynamicFormConfig::getRemark, text)
                    .or().like(DynamicFormConfig::getModuleKey, text)
                    .or().like(DynamicFormConfig::getSceneKey, text));
        }
        qw.orderByDesc(DynamicFormConfig::getUpdatedAt)
                .orderByDesc(DynamicFormConfig::getId);
        return this.page(new Page<DynamicFormConfig>(page, pageSize), qw);
    }

    @Override
    public DynamicFormConfig findCurrent(String moduleKey, String sceneKey, String status) {
        if (!StringUtils.hasText(moduleKey) || !StringUtils.hasText(sceneKey)) {
            return null;
        }
        LambdaQueryWrapper<DynamicFormConfig> qw = new LambdaQueryWrapper<DynamicFormConfig>();
        qw.eq(DynamicFormConfig::getModuleKey, normalizeIdentifier(moduleKey))
                .eq(DynamicFormConfig::getSceneKey, normalizeIdentifier(sceneKey));
        if (StringUtils.hasText(status)) {
            qw.eq(DynamicFormConfig::getStatus, normalizeStatus(status));
        }
        qw.orderByDesc(DynamicFormConfig::getVersionNo)
                .orderByDesc(DynamicFormConfig::getUpdatedAt)
                .orderByDesc(DynamicFormConfig::getId)
                .last("limit 1");
        return this.getOne(qw, false);
    }

    @Override
    public int nextVersionNo(String moduleKey, String sceneKey, String configName) {
        String module = normalizeIdentifier(moduleKey);
        String scene = normalizeIdentifier(sceneKey);
        String name = normalizeRequiredText(configName, "configName不能为空");
        LambdaQueryWrapper<DynamicFormConfig> qw = new LambdaQueryWrapper<DynamicFormConfig>();
        qw.eq(DynamicFormConfig::getModuleKey, module)
                .eq(DynamicFormConfig::getSceneKey, scene)
                .eq(DynamicFormConfig::getConfigName, name)
                .orderByDesc(DynamicFormConfig::getVersionNo)
                .orderByDesc(DynamicFormConfig::getUpdatedAt)
                .orderByDesc(DynamicFormConfig::getId)
                .last("limit 1");
        DynamicFormConfig current = this.getOne(qw, false);
        if (current == null || current.getVersionNo() == null || current.getVersionNo() < 1) {
            return 1;
        }
        return current.getVersionNo() + 1;
    }

    @Override
    public DynamicFormConfig createConfig(DynamicFormConfigCommand.Upsert command) {
        if (command == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String moduleKey = normalizeRequiredIdentifier(command.getModuleKey(), "moduleKey不能为空");
        String sceneKey = normalizeRequiredIdentifier(command.getSceneKey(), "sceneKey不能为空");
        String configName = normalizeRequiredText(command.getConfigName(), "configName不能为空");
        String schemaJson = normalizeAndValidateSchemaJson(command.getSchemaJson());
        String status = normalizeStatus(command.getStatus());
        String remark = normalizeOptionalText(command.getRemark());

        if (command.getVersionNo() != null && command.getVersionNo() > 0) {
            return doCreate(moduleKey, sceneKey, configName, schemaJson, status, command.getVersionNo(), remark);
        }

        DuplicateKeyException lastConflict = null;
        for (int i = 0; i < MAX_AUTO_VERSION_RETRY; i++) {
            int nextVersion = nextVersionNo(moduleKey, sceneKey, configName);
            try {
                return doCreate(moduleKey, sceneKey, configName, schemaJson, status, nextVersion, remark);
            } catch (DuplicateKeyException ex) {
                lastConflict = ex;
            }
        }
        if (lastConflict != null) {
            throw new IllegalStateException("版本号冲突，请重试");
        }
        throw new IllegalStateException("创建配置失败");
    }

    @Override
    public DynamicFormConfig updateConfig(Long id, DynamicFormConfigCommand.Upsert command) {
        if (id == null) {
            throw new IllegalArgumentException("id不能为空");
        }
        if (command == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        DynamicFormConfig row = this.getById(id);
        if (row == null) {
            throw new java.util.NoSuchElementException("配置不存在");
        }
        row.setModuleKey(normalizeRequiredIdentifier(command.getModuleKey(), "moduleKey不能为空"));
        row.setSceneKey(normalizeRequiredIdentifier(command.getSceneKey(), "sceneKey不能为空"));
        row.setConfigName(normalizeRequiredText(command.getConfigName(), "configName不能为空"));
        row.setSchemaJson(normalizeAndValidateSchemaJson(command.getSchemaJson()));
        row.setStatus(normalizeStatus(command.getStatus()));
        row.setVersionNo(resolveVersionNoForUpdate(row, command.getVersionNo()));
        row.setRemark(normalizeOptionalText(command.getRemark()));
        this.updateById(row);
        return this.getById(id);
    }

    @Override
    public void deleteConfig(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id不能为空");
        }
        DynamicFormConfig row = this.getById(id);
        if (row == null) {
            throw new java.util.NoSuchElementException("配置不存在");
        }
        Map<String, Long> references = countReferences(id);
        long total = 0L;
        for (Long count : references.values()) {
            total += (count == null ? 0L : count);
        }
        if (total > 0) {
            throw new IllegalStateException(
                    "配置正在被引用，无法删除：farm_process_step=" + references.get("farm_process_step")
                            + "，seed_batch=" + references.get("seed_batch")
                            + "，seed_quality_test=" + references.get("seed_quality_test")
            );
        }
        boolean removed = this.removeById(id);
        if (!removed) {
            throw new java.util.NoSuchElementException("配置不存在");
        }
    }

    @Override
    public String normalizeAndValidateSchemaJson(String schemaJson) {
        if (!StringUtils.hasText(schemaJson)) {
            throw new IllegalArgumentException("表单结构不能为空");
        }
        Object parsed;
        try {
            parsed = objectMapper.readValue(schemaJson, Object.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("表单结构JSON格式错误");
        }
        if (!(parsed instanceof List)) {
            throw new IllegalArgumentException("表单结构必须为JSON数组");
        }

        List<?> rows = (List<?>) parsed;
        List<Map<String, Object>> normalized = new ArrayList<Map<String, Object>>();
        Set<String> usedKeys = new LinkedHashSet<String>();
        int autoSeq = 1;
        int index = 0;
        for (Object rowObj : rows) {
            index += 1;
            if (!(rowObj instanceof Map)) {
                throw new IllegalArgumentException("schema第" + index + "项必须是对象");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> row = new LinkedHashMap<String, Object>((Map<String, Object>) rowObj);

            String label = normalizeRequiredText(asText(row.get("label")), "schema第" + index + "项label不能为空");
            String key = normalizeFieldKey(asText(row.get("key")));
            if (!StringUtils.hasText(key)) {
                key = autoFieldKey(label, autoSeq++);
            }
            while (usedKeys.contains(key)) {
                key = key + "_" + autoSeq++;
            }
            if (key.length() > MAX_FIELD_KEY_LENGTH) {
                throw new IllegalArgumentException("schema第" + index + "项key长度不能超过64");
            }
            usedKeys.add(key);

            String type = normalizeFieldType(asText(row.get("type")));
            Boolean required = parseRequired(row.get("required"));
            List<Map<String, String>> options = normalizeOptions(row.get("options"), type, index);

            row.put("label", label);
            row.put("key", key);
            row.put("type", type);
            row.put("required", required);
            if (row.containsKey("options") || OPTION_TYPES.contains(type)) {
                row.put("options", options);
            }
            normalized.add(row);
        }
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            throw new IllegalArgumentException("表单结构序列化失败");
        }
    }

    private DynamicFormConfig doCreate(
            String moduleKey,
            String sceneKey,
            String configName,
            String schemaJson,
            String status,
            Integer versionNo,
            String remark
    ) {
        DynamicFormConfig row = new DynamicFormConfig();
        row.setModuleKey(moduleKey);
        row.setSceneKey(sceneKey);
        row.setConfigName(configName);
        row.setSchemaJson(schemaJson);
        row.setStatus(status);
        row.setVersionNo(versionNo);
        row.setRemark(remark);
        this.save(row);
        return row;
    }

    private Integer resolveVersionNoForUpdate(DynamicFormConfig row, Integer requestVersionNo) {
        if (requestVersionNo != null && requestVersionNo > 0) {
            return requestVersionNo;
        }
        if (row != null && row.getVersionNo() != null && row.getVersionNo() > 0) {
            return row.getVersionNo();
        }
        return 1;
    }

    private Map<String, Long> countReferences(Long configId) {
        Map<String, Long> out = new LinkedHashMap<String, Long>();
        out.put("farm_process_step", countTableReference("farm_process_step", "form_config_id", configId));
        out.put("seed_batch", countTableReference("seed_batch", "form_config_id", configId));
        out.put("seed_quality_test", countTableReference("seed_quality_test", "form_config_id", configId));
        return out;
    }

    private long countTableReference(String tableName, String columnName, Long configId) {
        if (!tableExists(tableName) || configId == null) {
            return 0L;
        }
        String sql = "SELECT COUNT(*) FROM `" + tableName + "` WHERE `" + columnName + "`=?";
        if (tableHasDeletedColumn(tableName)) {
            sql += " AND COALESCE(`deleted`,0)=0";
        }
        try {
            Long count = jdbcTemplate.queryForObject(sql, Long.class, configId);
            return count == null ? 0L : count;
        } catch (Exception e) {
            return 0L;
        }
    }

    private boolean tableExists(String tableName) {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?",
                    Long.class,
                    tableName
            );
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean tableHasDeletedColumn(String tableName) {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME='deleted'",
                    Long.class,
                    tableName
            );
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String normalizeRequiredIdentifier(String value, String message) {
        String normalized = normalizeIdentifier(value);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeRequiredText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeOptionalText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeIdentifier(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "enabled";
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        if (!"enabled".equals(normalized) && !"disabled".equals(normalized)) {
            throw new IllegalArgumentException("status仅支持enabled/disabled");
        }
        return normalized;
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String autoFieldKey(String label, int seq) {
        String byLabel = normalizeFieldKey(label);
        if (StringUtils.hasText(byLabel) && !"field".equals(byLabel)) {
            return byLabel;
        }
        return "field_" + seq;
    }

    private String normalizeFieldKey(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String text = raw.trim().toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            boolean alphaNum = (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9');
            if (alphaNum) {
                out.append(ch);
                continue;
            }
            if (out.length() > 0 && out.charAt(out.length() - 1) != '_') {
                out.append('_');
            }
        }
        String normalized = out.toString().replaceAll("^_+|_+$", "");
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        if (Character.isDigit(normalized.charAt(0))) {
            normalized = "f_" + normalized;
        }
        return normalized;
    }

    private String normalizeFieldType(String typeRaw) {
        String type = StringUtils.hasText(typeRaw) ? typeRaw.trim().toLowerCase(Locale.ROOT) : "text";
        if (!ALLOWED_TYPES.contains(type)) {
            throw new IllegalArgumentException("字段类型不支持：" + typeRaw);
        }
        return type;
    }

    private Boolean parseRequired(Object requiredRaw) {
        if (requiredRaw == null) {
            return Boolean.FALSE;
        }
        if (requiredRaw instanceof Boolean) {
            return (Boolean) requiredRaw;
        }
        String text = String.valueOf(requiredRaw).trim().toLowerCase(Locale.ROOT);
        return "1".equals(text) || "true".equals(text) || "yes".equals(text);
    }

    private List<Map<String, String>> normalizeOptions(Object optionsRaw, String type, int index) {
        if (optionsRaw == null) {
            return new ArrayList<Map<String, String>>();
        }
        if (!(optionsRaw instanceof List)) {
            throw new IllegalArgumentException("schema第" + index + "项options必须为数组");
        }
        @SuppressWarnings("unchecked")
        List<Object> rows = (List<Object>) optionsRaw;
        List<Map<String, String>> out = new ArrayList<Map<String, String>>();
        Set<String> optionValues = new HashSet<String>();
        for (Object item : rows) {
            if (item == null) {
                continue;
            }
            String label;
            String value;
            if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> itemMap = (Map<String, Object>) item;
                label = asText(itemMap.get("label"));
                value = asText(itemMap.get("value"));
            } else {
                label = asText(item);
                value = asText(item);
            }
            if (!StringUtils.hasText(label) && !StringUtils.hasText(value)) {
                continue;
            }
            if (!StringUtils.hasText(value)) {
                value = label;
            }
            if (!StringUtils.hasText(label)) {
                label = value;
            }
            if (optionValues.contains(value)) {
                continue;
            }
            optionValues.add(value);
            Map<String, String> normalized = new LinkedHashMap<String, String>();
            normalized.put("label", label);
            normalized.put("value", value);
            out.add(normalized);
        }
        if (OPTION_TYPES.contains(type) && out.isEmpty()) {
            throw new IllegalArgumentException("schema第" + index + "项options不能为空");
        }
        return out;
    }
}
