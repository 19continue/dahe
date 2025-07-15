package com.dahe.v2.modules.export.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导出字段词典实体。
 * 用于维护字段编码到展示名称、数据类型的标准映射。
 */
@Data
@TableName("export_field_dict")
public class ExportFieldDict {
    @TableId(type = IdType.ASSIGN_ID)
    /** 主键。 */
    private Long id;
    /** 模块标识（farm/seed）。 */
    private String moduleKey;
    /** 字段编码。 */
    private String fieldCode;
    /** 字段展示名。 */
    private String fieldName;
    /** 数据类型。 */
    private String dataType;
    /** 字段描述。 */
    private String description;
    /** 示例值。 */
    private String exampleValue;
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
