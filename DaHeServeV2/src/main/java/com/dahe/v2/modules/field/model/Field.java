package com.dahe.v2.modules.field.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("field")
/**
 * 田块实体。
 * 对应 `field` 表，承载田块基础信息、位置信息、作物摘要与展示排序。
 */
public class Field {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;

    private Double areaMu;

    private String cropType;

    private String cropVariety;

    /**
     * 落库的结构化作物组合 JSON。
     * 结构示例：[{"cropType":"玉米","cropVariety":"先玉335"}]
     */
    private String cropVarietyGroupsJson;

    private String province;

    private String city;

    private String district;

    private String township;

    private String formattedAddress;

    private String status;

    /**
     * 运行态别名字段，不落库。
     * 统一与 `status` 保持同值，兼容前端阶段字段命名。
     */
    @TableField(exist = false)
    private String stage;

    /**
     * 运行态结构化作物组合，不落库。
     * 由 controller/service 在响应前解析填充，供前端直接展示。
     */
    @TableField(exist = false)
    private List<FieldCropVarietyGroup> cropVarietyGroups;

    /**
     * 与当前位置的直线距离，单位米。
     * 仅在“附近田块 / 当前匹配田块”等基于定位的接口中回填，不落库。
     */
    @TableField(exist = false)
    private Double distanceMeters;

    /**
     * 面向前端展示的距离文案，例如 `320m`、`1.6km`。
     * 由后端统一格式化，避免多端各自重复实现。
     */
    @TableField(exist = false)
    private String distanceText;

    /**
     * 与当前位置的关系类型，仅在位置感知接口中回填，不落库。
     * 当前仅约定 `inside`，表示当前定位命中了该田块。
     */
    @TableField(exist = false)
    private String relationType;

    /**
     * 面向前端展示的当前位置关系文案，例如“您当前所处田块”。
     */
    @TableField(exist = false)
    private String relationText;

    /**
     * 当前定位是否命中该田块。
     */
    @TableField(exist = false)
    private Boolean currentMatched;

    private Integer enabled;

    private Double locationLat;

    private Double locationLng;

    private String locationDesc;

    private String coverImageUrl;

    private String remark;

    private Integer sortOrder;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}


