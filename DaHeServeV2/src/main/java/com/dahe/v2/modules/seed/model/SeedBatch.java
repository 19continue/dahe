package com.dahe.v2.modules.seed.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName("seed_batch")
/**
 * 种子批次实体。
 * 对应 `seed_batch` 表，保存批次基础信息与动态扩展参数。
 */
public class SeedBatch {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String batchCode;
    private String cropType;
    private String varietyName;
    private LocalDate productionDate;
    private String remark;
    private Integer enabled;
    private Long formConfigId;
    private String extraJson;
    /** 动态字段 key -> 中文标签（运行态回填，不落库）。 */
    @TableField(exist = false)
    private Map<String, String> extraLabelMap;
    /** 动态字段 key -> 选项值标签映射（运行态回填，不落库）。 */
    @TableField(exist = false)
    private Map<String, Map<String, String>> extraValueLabelMap;
    /** 动态表单 schema（运行态回填，不落库）。 */
    @TableField(exist = false)
    private String formSchema;
    /** 动态配置名称（运行态回填，不落库）。 */
    @TableField(exist = false)
    private String formConfigName;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

