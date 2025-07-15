package com.dahe.v2.modules.field.service;

import com.dahe.v2.modules.field.model.FieldCropVarietyGroup;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 田块作物品种结构化编解码器。
 *
 * <p>统一处理三类来源：</p>
 * <p>1) 计划配置 {@code cropsJson}（name/variety）；</p>
 * <p>2) 田块持久化字段 {@code cropVarietyGroupsJson}（cropType/cropVariety）；</p>
 * <p>3) 旧字段 {@code cropType/cropVariety}（字符串拼接）。</p>
 */
public final class FieldCropVarietyGroupCodec {

    private static final TypeReference<List<Map<String, Object>>> MAP_LIST_TYPE =
            new TypeReference<List<Map<String, Object>>>() {
            };

    /** 旧字符串拆分分隔符：支持中文/英文顿号、逗号、分号、竖线与斜杠。 */
    private static final String LEGACY_SPLIT_PATTERN = "[、，,;；|/]+";

    private FieldCropVarietyGroupCodec() {
    }

    /** 从计划 {@code cropsJson} 解析作物组合。 */
    public static List<FieldCropVarietyGroup> fromCycleCropsJson(ObjectMapper objectMapper, String cropsJson) {
        return fromJson(objectMapper, cropsJson, "name", "variety");
    }

    /** 从田块 {@code cropVarietyGroupsJson} 解析作物组合。 */
    public static List<FieldCropVarietyGroup> fromFieldJson(ObjectMapper objectMapper, String groupsJson) {
        return fromJson(objectMapper, groupsJson, "cropType", "cropVariety");
    }

    /**
     * 从旧字段回退构造作物组合。
     * 旧值可能是多组拼接文本，按分隔符拆分后按索引对齐。
     */
    public static List<FieldCropVarietyGroup> fromLegacyTexts(String cropType, String cropVariety) {
        List<String> cropTypes = splitLegacyText(cropType);
        List<String> cropVarieties = splitLegacyText(cropVariety);
        if (cropTypes.isEmpty() && cropVarieties.isEmpty()) {
            return Collections.emptyList();
        }
        int count = Math.max(cropTypes.size(), cropVarieties.size());
        List<FieldCropVarietyGroup> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String type = i < cropTypes.size() ? cropTypes.get(i) : null;
            String variety = i < cropVarieties.size() ? cropVarieties.get(i) : null;
            if (!StringUtils.hasText(type) && cropTypes.size() == 1) {
                type = cropTypes.get(0);
            }
            if (!StringUtils.hasText(variety) && cropVarieties.size() == 1) {
                variety = cropVarieties.get(0);
            }
            FieldCropVarietyGroup item = createItem(type, variety);
            if (item != null) {
                rows.add(item);
            }
        }
        if (!rows.isEmpty()) {
            return normalize(rows);
        }
        FieldCropVarietyGroup single = createItem(cropType, cropVariety);
        return single == null ? Collections.emptyList() : Collections.singletonList(single);
    }

    /** 将组合列表序列化为可落库 JSON。 */
    public static String toFieldJson(ObjectMapper objectMapper, List<FieldCropVarietyGroup> rows) {
        List<FieldCropVarietyGroup> normalized = normalize(rows);
        if (normalized.isEmpty() || objectMapper == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 生成兼容旧前端的摘要文本（作物/品种按 {@code /} 对齐拼接）。 */
    public static CropSummary toLegacySummary(List<FieldCropVarietyGroup> rows) {
        List<FieldCropVarietyGroup> normalized = normalize(rows);
        if (normalized.isEmpty()) {
            return new CropSummary(null, null);
        }
        List<String> cropTypes = new ArrayList<>();
        List<String> cropVarieties = new ArrayList<>();
        for (FieldCropVarietyGroup row : normalized) {
            cropTypes.add(readText(row == null ? null : row.getCropType()));
            cropVarieties.add(readText(row == null ? null : row.getCropVariety()));
        }
        return new CropSummary(joinSegments(cropTypes), joinSegments(cropVarieties));
    }

    /** 构造 JSON 模糊检索 token（如 {@code "cropType":"玉米"}）。 */
    public static String buildFieldJsonLikeToken(String key, String value) {
        return buildJsonLikeToken(key, value);
    }

    /** 构造任意 JSON 键值对的模糊检索 token。 */
    public static String buildJsonLikeToken(String key, String value) {
        String safeKey = readText(key);
        String safeValue = readText(value);
        if (!StringUtils.hasText(safeKey) || !StringUtils.hasText(safeValue)) {
            return null;
        }
        return "\"" + escapeJson(safeKey) + "\":\"" + escapeJson(safeValue) + "\"";
    }

    /** 去重并过滤空值。 */
    public static List<FieldCropVarietyGroup> normalize(List<FieldCropVarietyGroup> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<FieldCropVarietyGroup> out = new ArrayList<>();
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (FieldCropVarietyGroup row : rows) {
            FieldCropVarietyGroup item = createItem(
                    row == null ? null : row.getCropType(),
                    row == null ? null : row.getCropVariety()
            );
            if (item == null) {
                continue;
            }
            String key = String.valueOf(item.getCropType()) + "\u0001" + String.valueOf(item.getCropVariety());
            if (!keys.add(key)) {
                continue;
            }
            out.add(item);
        }
        return out;
    }

    private static List<FieldCropVarietyGroup> fromJson(
            ObjectMapper objectMapper,
            String json,
            String primaryCropKey,
            String primaryVarietyKey
    ) {
        if (!StringUtils.hasText(json) || objectMapper == null) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(json, MAP_LIST_TYPE);
            List<FieldCropVarietyGroup> rows = new ArrayList<>();
            for (Map<String, Object> row : raw) {
                if (row == null) {
                    continue;
                }
                FieldCropVarietyGroup item = createItem(
                        readText(row.get(primaryCropKey), row.get("cropType"), row.get("name"), row.get("cropName"), row.get("categoryName"), row.get("crop")),
                        readText(row.get(primaryVarietyKey), row.get("cropVariety"), row.get("variety"), row.get("varietyName"))
                );
                if (item != null) {
                    rows.add(item);
                }
            }
            return normalize(rows);
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private static FieldCropVarietyGroup createItem(String cropType, String cropVariety) {
        String safeCropType = readText(cropType);
        String safeCropVariety = readText(cropVariety);
        if (!StringUtils.hasText(safeCropType) && !StringUtils.hasText(safeCropVariety)) {
            return null;
        }
        FieldCropVarietyGroup item = new FieldCropVarietyGroup();
        item.setCropType(safeCropType);
        item.setCropVariety(safeCropVariety);
        return item;
    }

    private static List<String> splitLegacyText(String value) {
        String text = readText(value);
        if (!StringUtils.hasText(text)) {
            return Collections.emptyList();
        }
        String[] raw = text.split(LEGACY_SPLIT_PATTERN, -1);
        if (raw.length <= 1) {
            return Collections.singletonList(text);
        }
        List<String> out = new ArrayList<>();
        for (String part : raw) {
            out.add(readText(part));
        }
        return out;
    }

    private static String readText(Object... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        for (Object value : values) {
            String text = readText(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    private static String readText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String joinSegments(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<String> normalized = new ArrayList<>();
        boolean hasAny = false;
        for (String value : values) {
            String text = readText(value);
            normalized.add(text == null ? "" : text);
            if (text != null) {
                hasAny = true;
            }
        }
        if (!hasAny) {
            return null;
        }
        return String.join("/", normalized);
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** 旧字段摘要对象。 */
    public static final class CropSummary {
        private final String cropType;
        private final String cropVariety;

        public CropSummary(String cropType, String cropVariety) {
            this.cropType = cropType;
            this.cropVariety = cropVariety;
        }

        public String getCropType() {
            return cropType;
        }

        public String getCropVariety() {
            return cropVariety;
        }
    }
}
