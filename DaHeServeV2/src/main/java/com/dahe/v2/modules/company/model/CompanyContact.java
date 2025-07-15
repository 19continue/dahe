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
 * 企业联系方式实体。
 */
@Data
@TableName("company_contact")
public class CompanyContact {

    @TableId(type = IdType.ASSIGN_ID)
    /** 主键。 */
    private Long id;

    /** 联系方式类型：address/phone/email/website 等。 */
    private String contactType;

    /** 联系方式标签，如“联系电话”。 */
    private String contactLabel;

    /** 联系方式内容。 */
    private String contactValue;

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
