package com.dahe.v2.modules.farm.process.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 步骤动态参数校验器。
 * 根据步骤 schema 对 `extraJson` 做 required 与类型校验。
 */
@Component
public class StepFormSchemaValidator {

    /** 日期格式：yyyy-MM-dd。 */
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    /** 时间格式：HH:mm 或 HH:mm:ss。 */
    private static final Pattern TIME_PATTERN = Pattern.compile("^\\d{2}:\\d{2}(:\\d{2})?$");
    /** 日期时间格式：yyyy-MM-dd HH:mm:ss。 */
    private static final Pattern DATETIME_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}(:\\d{2})?$");

    private final ObjectMapper objectMapper;

    public StepFormSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 按 schema 校验动态参数 payload。 */
    public String validate(String formSchema, String extraJson) {
        /*
         * 动态参数校验入口。
         *
         * 核心思想是：固定表结构之外的字段虽然落在 extraJson 里，
         * 但并不是“随便传什么都行”，仍然要受步骤 schema 约束。
         *
         * 当前校验范围主要覆盖：
         * - 必填 required；
         * - number/date/time/datetime/select/location 等基础类型；
         * - select 是否落在 options 中。
         */
        List<Map<String, Object>> schemaRows = parseSchemaRows(formSchema);
        // schema JSON 自己都不合法时，后面的字段校验就没有意义了。
        if (schemaRows == null) {
            return "步骤表单模板JSON格式不正确";
        }
        // 没有 schema 说明这个步骤没有动态字段，直接视为通过。
        if (schemaRows.isEmpty()) {
            return null;
        }

        // 把前端提交的 extraJson 先解析成对象。
        Map<String, Object> payload = parsePayload(extraJson);
        if (payload == null) {
            return "步骤参数必须是有效的JSON对象";
        }

        for (Map<String, Object> row : schemaRows) {
            if (row == null) {
                continue;
            }
            String key = text(row.get("key"));
            if (!StringUtils.hasText(key)) {
                continue;
            }
            String label = text(row.get("label"));
            if (!StringUtils.hasText(label)) {
                // 没配置 label 时，退回 key，保证报错信息至少还能定位字段。
                label = key;
            }
            boolean required = toBoolean(row.get("required"));
            String type = normalizeType(text(row.get("type")));

            // 从 extraJson 中拿出当前字段值。
            Object value = payload.get(key);
            if (isBlank(value)) {
                if (required) {
                    return label + "不能为空";
                }
                continue;
            }

            String typeError = validateByType(type, value, row, label);
            if (typeError != null) {
                return typeError;
            }
        }
        return null;
    }

    /** 解析 schema JSON 数组。 */
    private List<Map<String, Object>> parseSchemaRows(String formSchema) {
        if (!StringUtils.hasText(formSchema)) {
            return Collections.emptyList();
        }
        try {
            // schema 约定是一个字段描述数组。
            List<Map<String, Object>> rows = objectMapper.readValue(formSchema, new TypeReference<List<Map<String, Object>>>() {});
            return rows == null ? Collections.emptyList() : rows;
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析前端提交的动态参数 JSON 对象。 */
    private Map<String, Object> parsePayload(String extraJson) {
        /*
         * extraJson 约定必须是 JSON 对象，而不是数组或任意字符串。
         * 这样后端才能按 key 精确对应 schema 字段。
         */
        if (!StringUtils.hasText(extraJson)) {
            // 没传 extraJson 时，当作空对象处理。
            return Collections.emptyMap();
        }
        try {
            // extraJson 必须能被解析成 key-value 对象。
            Map<String, Object> payload = objectMapper.readValue(extraJson, new TypeReference<Map<String, Object>>() {});
            return payload == null ? Collections.emptyMap() : payload;
        } catch (Exception e) {
            return null;
        }
    }

    /** 按字段类型执行单值校验。 */
    private String validateByType(String type, Object value, Map<String, Object> schemaRow, String label) {
        /*
         * 这里没有追求完整 JSON Schema 级别能力，而是覆盖当前项目真实需要的常见字段类型。
         * 对校招项目来说，这种“够用且可维护”的约束方式比上来做很重的规则引擎更合适。
         */
        String textValue = text(value);
        switch (type) {
            case "number":
                // 数字类型允许前端传 Number 或可转成数字的字符串。
                if (!isNumber(value)) {
                    return label + "必须为数字";
                }
                return null;
            case "date":
                if (!DATE_PATTERN.matcher(textValue).matches()) {
                    return label + "格式应为yyyy-MM-dd";
                }
                return null;
            case "time":
                if (!TIME_PATTERN.matcher(textValue).matches()) {
                    return label + "格式应为HH:mm或HH:mm:ss";
                }
                return null;
            case "datetime":
                if (!DATETIME_PATTERN.matcher(textValue).matches()) {
                    return label + "格式应为yyyy-MM-dd HH:mm:ss";
                }
                return null;
            case "select":
                // select 类型如果配置了 options，就必须命中 options。
                Set<String> options = resolveOptions(schemaRow.get("options"));
                if (!options.isEmpty() && !options.contains(textValue)) {
                    return label + "不在可选范围内";
                }
                return null;
            case "location":
                if (isLocationValue(value)) {
                    return null;
                }
                return label + "必须为有效位置值";
            case "text":
            case "textarea":
            default:
                return null;
        }
    }

    /** 解析 select 选项集合。 */
    private Set<String> resolveOptions(Object raw) {
        Set<String> out = new HashSet<>();
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                if (item == null) {
                    continue;
                }
                if (item instanceof Map) {
                    // 优先取 value，没有时再退回 label。
                    Map<?, ?> map = (Map<?, ?>) item;
                    String v = text(map.get("value"));
                    if (!StringUtils.hasText(v)) {
                        v = text(map.get("label"));
                    }
                    if (StringUtils.hasText(v)) {
                        out.add(v);
                    }
                } else {
                    String v = text(item);
                    if (StringUtils.hasText(v)) {
                        out.add(v);
                    }
                }
            }
        } else if (raw != null) {
            String v = text(raw);
            if (StringUtils.hasText(v)) {
                out.add(v);
            }
        }
        return out;
    }

    /** 校验 location 类型值。 */
    private boolean isLocationValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Map) {
            // location 允许前端传 {lat,lng} 结构。
            Map<?, ?> row = (Map<?, ?>) value;
            return canParseDouble(row.get("lat")) && canParseDouble(row.get("lng"));
        }
        if (value instanceof List) {
            // 也允许传 [lat,lng] 数组结构。
            List<?> arr = (List<?>) value;
            return arr.size() >= 2 && canParseDouble(arr.get(0)) && canParseDouble(arr.get(1));
        }
        String text = text(value);
        return StringUtils.hasText(text);
    }

    /** 校验数值。 */
    private boolean isNumber(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Number) {
            return true;
        }
        String text = text(value);
        if (!StringUtils.hasText(text)) {
            return false;
        }
        try {
            new BigDecimal(text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 判断对象是否可转 Double。 */
    private boolean canParseDouble(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Number) {
            return true;
        }
        String text = text(value);
        if (!StringUtils.hasText(text)) {
            return false;
        }
        try {
            Double.parseDouble(text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 判断值是否为空。 */
    private boolean isBlank(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String) {
            return !StringUtils.hasText((String) value);
        }
        if (value instanceof Collection) {
            return ((Collection<?>) value).isEmpty();
        }
        if (value instanceof Map) {
            return ((Map<?, ?>) value).isEmpty();
        }
        return false;
    }

    /** 归一化类型别名。 */
    private String normalizeType(String raw) {
        String type = StringUtils.hasText(raw) ? raw.trim().toLowerCase() : "text";
        // 把常见数字别名统一折叠成 number，减少前端配置心智负担。
        if ("integer".equals(type) || "float".equals(type) || "double".equals(type)) {
            return "number";
        }
        return type;
    }

    /** 宽松布尔转换。 */
    private boolean toBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = text(value).toLowerCase();
        return "1".equals(text) || "true".equals(text) || "yes".equals(text);
    }

    /** 安全转文本。 */
    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
