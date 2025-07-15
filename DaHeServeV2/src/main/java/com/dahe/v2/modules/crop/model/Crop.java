package com.dahe.v2.modules.crop.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 作物节点实体。
 * 统一承载分类节点(category)与品种节点(variety)。
 */
@Data
@TableName("crop")
public class Crop {
    @TableId(type = IdType.ASSIGN_ID)
    /** 主键。 */
    private Long id;
    /** 作物分类名称（品种节点也冗余存分类名用于兼容历史数据）。 */
    private String name;
    /** 品种名称；分类节点为空。 */
    private String variety;
    /** 节点类型：category/variety。 */
    private String nodeType;
    /** 父分类 ID；分类节点为空。 */
    private Long parentId;
    /** 分类或品种图片地址。 */
    private String imageUrl;
    /** 排序号（越小越靠前）。 */
    private Integer sortOrder;
    @TableLogic
    /** 逻辑删除标记。 */
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    /** 创建时间。 */
    private LocalDateTime createdAt;
}

