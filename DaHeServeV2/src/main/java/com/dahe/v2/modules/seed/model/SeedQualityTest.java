package com.dahe.v2.modules.seed.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName("seed_quality_test")
/**
 * 种子检测实体。
 * 对应 `seed_quality_test` 表，记录单批次的检测结果与动态扩展参数。
 */
public class SeedQualityTest {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long batchId;
    private LocalDate testDate;
    private Integer sampleCount;
    private Integer germinationCount;
    private Double germinationRate;
    private Double moisture;
    private Double purity;
    private Double cleanliness;
    private String testerName;
    private String remark;
    /** 幂等请求键：同批次+同键重复提交时返回已有记录。 */
    private String requestKey;
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
    private LocalDateTime createdAt;
}

