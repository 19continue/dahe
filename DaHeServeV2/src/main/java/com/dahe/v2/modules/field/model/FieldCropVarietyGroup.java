package com.dahe.v2.modules.field.model;

import lombok.Data;

/**
 * 田块作物-品种组合项。
 *
 * <p>用于三端展示与筛选，不直接映射独立数据表。</p>
 */
@Data
public class FieldCropVarietyGroup {

    /** 作物分类名（示例：玉米）。 */
    private String cropType;

    /** 品种名（示例：先玉335，可为空表示作物通用品种）。 */
    private String cropVariety;
}