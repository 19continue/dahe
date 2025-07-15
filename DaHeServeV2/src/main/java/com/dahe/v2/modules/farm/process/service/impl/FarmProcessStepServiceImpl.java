package com.dahe.v2.modules.farm.process.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dahe.v2.modules.farm.process.mapper.FarmProcessStepMapper;
import com.dahe.v2.modules.farm.process.model.FarmProcessStep;
import com.dahe.v2.modules.farm.process.service.FarmProcessStepService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/** 流程步骤服务实现。 */
@Service
public class FarmProcessStepServiceImpl extends ServiceImpl<FarmProcessStepMapper, FarmProcessStep>
        implements FarmProcessStepService {

    @Override
    /** 查询模板下步骤，按 sortOrder、id 升序。 */
    public List<FarmProcessStep> listByTemplateId(Long templateId) {
        if (templateId == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<FarmProcessStep> qw = new LambdaQueryWrapper<>();
        qw.eq(FarmProcessStep::getTemplateId, templateId)
                .orderByAsc(FarmProcessStep::getSortOrder)
                .orderByAsc(FarmProcessStep::getId);
        return this.list(qw);
    }

    @Override
    /** 批量查询步骤，按 templateId、sortOrder、id 升序。 */
    public List<FarmProcessStep> listByTemplateIds(List<Long> templateIds) {
        if (templateIds == null || templateIds.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<FarmProcessStep> qw = new LambdaQueryWrapper<>();
        qw.in(FarmProcessStep::getTemplateId, templateIds)
                .orderByAsc(FarmProcessStep::getTemplateId)
                .orderByAsc(FarmProcessStep::getSortOrder)
                .orderByAsc(FarmProcessStep::getId);
        return this.list(qw);
    }
}
