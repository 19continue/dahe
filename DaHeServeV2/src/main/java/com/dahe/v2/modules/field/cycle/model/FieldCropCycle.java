package com.dahe.v2.modules.field.cycle.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("field_crop_cycle")
/**
 * 田块种植计划实体。
 * 一条记录代表某田块一个周期的作物配置与流程模板绑定关系。
 */
public class FieldCropCycle {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long fieldId;

    private String cycleName;

    private String cropsJson;

    private String templateIdsJson;

    @TableField("plan_mode")
    private String planMode;

    private LocalDate startDate;

    private LocalDate endDate;

    private String status;

    private Integer isCurrent;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
