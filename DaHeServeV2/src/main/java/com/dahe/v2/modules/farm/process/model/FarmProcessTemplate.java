package com.dahe.v2.modules.farm.process.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 农事流程模板实体。 */
@Data
@TableName("farm_process_template")
public class FarmProcessTemplate {

    @TableId(type = IdType.ASSIGN_ID)
    /** 主键。 */
    private Long id;

    /** 绑定作物（分类或品种）ID。 */
    private Long cropId;

    /** 模板名称。 */
    private String templateName;

    /** 是否默认模板（1/0）。 */
    private Integer isDefault;

    /** 是否启用（1/0）。 */
    private Integer enabled;

    @TableLogic
    /** 逻辑删除标记。 */
    private Integer deleted;
}
