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
 * 导出模板实体。
 * 描述某模块某模板编码在不同版本下的字段顺序配置。
 */
@Data
@TableName("export_template")
public class ExportTemplate {
    @TableId(type = IdType.ASSIGN_ID)
    /** 主键。 */
    private Long id;
    /** 模块标识（farm/seed）。 */
    private String moduleKey;
    /** 模板编码。 */
    private String templateCode;
    /** 模板名称。 */
    private String templateName;
    /** 模板版本号。 */
    private Integer versionNo;
    /** 模板状态（enabled/disabled）。 */
    private String status;
    /** 字段编码数组 JSON。 */
    private String fieldsJson;
    /** 备注。 */
    private String remark;
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
