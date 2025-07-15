package com.dahe.v2.modules.farm.policy.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 农事记录权限策略配置实体。 */
@Data
@TableName("record_policy_config")
public class RecordPolicyConfig {

    @TableId(type = IdType.INPUT)
    /** 固定主键（单行配置）。 */
    private Long id;

    /** 可编辑窗口（小时），0 表示不限制。 */
    private Integer editWindowHours;

    /** 是否允许操作员编辑（1/0）。 */
    private Integer allowOperatorUpdate;

    /** 是否允许操作员删除（1/0）。 */
    private Integer allowOperatorDelete;

    /** 备注。 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    /** 创建时间。 */
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
