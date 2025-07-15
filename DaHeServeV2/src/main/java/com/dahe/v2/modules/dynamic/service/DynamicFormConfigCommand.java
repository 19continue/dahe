package com.dahe.v2.modules.dynamic.service;

import lombok.Data;

/**
 * dynamic 模块写入命令对象。
 */
public final class DynamicFormConfigCommand {

    private DynamicFormConfigCommand() {
    }

    @Data
    public static class Upsert {
        private String moduleKey;
        private String sceneKey;
        private String configName;
        private String schemaJson;
        private String status;
        private Integer versionNo;
        private String remark;
    }
}
