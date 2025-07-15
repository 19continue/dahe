package com.dahe.v2.modules.farm.process.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dahe.v2.modules.farm.process.model.FarmProcessTemplate;

/** 流程模板服务接口。 */
public interface FarmProcessTemplateService extends IService<FarmProcessTemplate> {

    /** 查询作物可用默认模板（支持分类回退）。 */
    FarmProcessTemplate findDefaultTemplate(Long cropId);

    /**
     * 级联删除模板及其步骤。
     */
    boolean removeTemplateCascade(Long templateId);
}
