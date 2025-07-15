package com.dahe.v2.modules.dynamic.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 动态表单配置实体。
 * 用于按模块与场景管理动态参数表单结构。
 */
@Data
@TableName("dynamic_form_config")
public class DynamicFormConfig {

    @TableId(type = IdType.ASSIGN_ID)
    /** 主键。 */
    private Long id;

    /** 模块标识，如 farm/seed。 */
    private String moduleKey;

    /** 场景标识，如 step_fields/test_fields。 */
    private String sceneKey;

    /** 配置名称。 */
    private String configName;

    /** 表单结构 JSON。 */
    private String schemaJson;

    /** 状态：enabled/disabled。 */
    private String status;

    /** 版本号。 */
    private Integer versionNo;

    /** 备注。 */
    private String remark;

    @TableLogic
    /** 逻辑删除标记。 */
    private Integer deleted;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    /** 创建时间。 */
    private LocalDateTime createdAt;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
