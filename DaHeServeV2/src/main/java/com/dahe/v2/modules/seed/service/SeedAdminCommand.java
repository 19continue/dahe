package com.dahe.v2.modules.seed.service;

import lombok.Data;

import java.time.LocalDate;

/**
 * seed 模块后台管理命令对象。
 *
 * <p>用于控制层到服务层的输入传递，避免服务层依赖 controller 请求对象。</p>
 */
public final class SeedAdminCommand {

    private SeedAdminCommand() {
    }

    @Data
    public static class BatchPageQuery {
        private String keyword;
        private String cropType;
        private String varietyName;
        private Integer enabled;
        private boolean includeDisabled;
        private long page;
        private long pageSize;
    }

    @Data
    public static class BatchCreate {
        private String batchCode;
        private String cropType;
        private String varietyName;
        private LocalDate productionDate;
        private String remark;
        private Boolean enabled;
        private Long formConfigId;
        private String extraJson;
    }

    @Data
    public static class BatchUpdate {
        private String batchCode;
        private String cropType;
        private String varietyName;
        private LocalDate productionDate;
        private String remark;
        private Boolean enabled;
        private Long formConfigId;
        private String extraJson;
    }

    @Data
    public static class TestUpsert {
        private String requestKey;
        private LocalDate testDate;
        private Integer sampleCount;
        private Integer germinationCount;
        private Double germinationRate;
        private Double moisture;
        private Double purity;
        private Double cleanliness;
        private String testerName;
        private String remark;
        private Long formConfigId;
        private String extraJson;
    }

    @Data
    public static class RuleUpdate {
        private Integer fixedSampleSize;
        private Integer defaultSampleSize;
        private String remark;
    }
}
