package com.dahe.v2.modules.farm.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 农事记录实体。
 * 记录某田块在某步骤下的执行数据及动态扩展参数。
 */
@Data
@TableName("farm_record")
public class FarmRecord {

    @TableId(type = IdType.ASSIGN_ID)
    /** 主键。 */
    private Long id;

    /** 田块 ID。 */
    private Long fieldId;

    /** 种植计划 ID。 */
    private Long cycleId;

    /** 流程步骤 ID。 */
    private Long stepId;

    /** 作业时间。 */
    private LocalDateTime workDate;

    /** 执行人姓名。 */
    private String operatorName;

    /** 执行人用户 ID。 */
    private Long operatorUserId;

    /** 备注。 */
    private String notes;

    /** 天气。 */
    private String weather;

    /** 温度。 */
    private String temperature;

    /** 天气位置。 */
    private String weatherLocation;

    /** 湿度。 */
    private String humidity;

    /** 风向。 */
    private String windDirection;

    /** 风力。 */
    private String windPower;

    /** 天气发布时间文本。 */
    private String weatherReportTime;

    /** 动态参数 JSON。 */
    private String extraJson;

    @TableField(exist = false)
    /** 步骤名称（回显字段）。 */
    private String stepName;

    @TableField(exist = false)
    /** 田块名称（回显字段）。 */
    private String fieldName;

    @TableField(exist = false)
    /** 动态字段标签映射（key -> label）。 */
    private Map<String, String> extraLabelMap;

    @TableField(exist = false)
    /** 动态选项标签映射（key -> (value -> label)）。 */
    private Map<String, Map<String, String>> extraValueLabelMap;

    @TableLogic
    /** 逻辑删除标记。 */
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    /** 创建时间。 */
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    /** 更新时间。 */
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    /** 当前用户是否可编辑。 */
    private Boolean canEdit;

    @TableField(exist = false)
    /** 当前用户是否可删除。 */
    private Boolean canDelete;
}

