package com.dahe.v2.modules.dynamic.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.mock;

class DynamicFormConfigServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DynamicFormConfigServiceImpl service =
            new DynamicFormConfigServiceImpl(objectMapper, mock(JdbcTemplate.class));

    @Test
    void normalizeAndValidateSchemaJson_shouldNormalizeKeyAndOptions() throws Exception {
        String raw = "[" +
                "{\"label\":\"检测方法\",\"type\":\"select\",\"required\":\"true\",\"options\":[{\"label\":\"纸床法\",\"value\":\"paper\"},{\"label\":\"纸床法\",\"value\":\"paper\"}]}," +
                "{\"label\":\"发芽率(%)\",\"key\":\" germination-rate \",\"type\":\"number\"}" +
                "]";

        String normalized = service.normalizeAndValidateSchemaJson(raw);
        JsonNode node = objectMapper.readTree(normalized);

        Assertions.assertEquals(2, node.size());
        Assertions.assertEquals("paper", node.get(0).get("options").get(0).get("value").asText());
        Assertions.assertEquals("select", node.get(0).get("type").asText());
        Assertions.assertTrue(node.get(0).get("required").asBoolean());
        Assertions.assertEquals("germination_rate", node.get(1).get("key").asText());
    }

    @Test
    void normalizeAndValidateSchemaJson_shouldRejectWhenNotArray() {
        IllegalArgumentException ex = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.normalizeAndValidateSchemaJson("{\"key\":\"x\"}")
        );
        Assertions.assertTrue(ex.getMessage().contains("JSON数组"));
    }

    @Test
    void normalizeAndValidateSchemaJson_shouldRejectEmptyOptionsForSelect() {
        String raw = "[{\"label\":\"检测方法\",\"type\":\"select\",\"options\":[]}]";
        IllegalArgumentException ex = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.normalizeAndValidateSchemaJson(raw)
        );
        Assertions.assertTrue(ex.getMessage().contains("options不能为空"));
    }
}
