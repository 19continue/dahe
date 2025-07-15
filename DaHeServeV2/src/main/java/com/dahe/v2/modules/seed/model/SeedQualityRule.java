package com.dahe.v2.modules.seed.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("seed_quality_rule")
/** 种子检测规则实体（单例配置）。 */
public class SeedQualityRule {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Integer fixedSampleSize;

    private Integer defaultSampleSize;

    private String remark;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
