package com.dahe.v2.modules.company.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 企业产品实体。
 */
@Data
@TableName("company_product")
public class CompanyProduct {

    @TableId(type = IdType.ASSIGN_ID)
    /** 主键。 */
    private Long id;

    /** 产品名称。 */
    private String name;

    /** 产品描述。 */
    private String description;

    /** 产品图片地址。 */
    private String image;

    /** 排序号（越小越靠前）。 */
    private Integer sortOrder;

    /** 状态：1 启用，0 停用。 */
    private Integer status;

    @TableLogic
    /** 逻辑删除标记。 */
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    /** 创建时间。 */
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
