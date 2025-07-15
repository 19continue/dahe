package com.dahe.v2.modules.farm.process.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dahe.v2.modules.farm.process.model.FarmProcessStep;

import java.util.List;

/** 流程步骤服务接口。 */
public interface FarmProcessStepService extends IService<FarmProcessStep> {

    /** 查询模板下步骤。 */
    List<FarmProcessStep> listByTemplateId(Long templateId);

    /** 批量查询多个模板下步骤。 */
    List<FarmProcessStep> listByTemplateIds(List<Long> templateIds);
}
