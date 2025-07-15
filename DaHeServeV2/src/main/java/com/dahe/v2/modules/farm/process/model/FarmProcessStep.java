package com.dahe.v2.modules.farm.process.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 农事流程步骤实体。 */
@Data
@TableName("farm_process_step")
public class FarmProcessStep {

    @TableId(type = IdType.ASSIGN_ID)
    /** 主键。 */
    private Long id;

    /** 模板 ID。 */
    private Long templateId;

    /** 步骤名称。 */
    private String stepName;

    /** 排序序号。 */
    private Integer sortOrder;

    /** 操作要求说明。 */
    private String requirementDesc;

    /** 生长阶段（sowing/growing/harvesting/fallow...）。 */
    private String growthStage;

    /** 动态表单配置 ID。 */
    private Long formConfigId;

    /** 步骤内置表单结构 JSON（兜底）。 */
    private String formSchema;
}
