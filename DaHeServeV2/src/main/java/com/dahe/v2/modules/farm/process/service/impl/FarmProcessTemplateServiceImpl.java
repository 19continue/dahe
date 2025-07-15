package com.dahe.v2.modules.farm.process.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dahe.v2.modules.crop.model.Crop;
import com.dahe.v2.modules.crop.service.CropService;
import com.dahe.v2.modules.farm.process.mapper.FarmProcessTemplateMapper;
import com.dahe.v2.modules.farm.process.model.FarmProcessStep;
import com.dahe.v2.modules.farm.process.model.FarmProcessTemplate;
import com.dahe.v2.modules.farm.process.service.FarmProcessStepService;
import com.dahe.v2.modules.farm.process.service.FarmProcessTemplateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 流程模板服务实现。 */
@Service
public class FarmProcessTemplateServiceImpl extends ServiceImpl<FarmProcessTemplateMapper, FarmProcessTemplate>
        implements FarmProcessTemplateService {

    private final CropService cropService;
    private final FarmProcessStepService farmProcessStepService;

    public FarmProcessTemplateServiceImpl(
            CropService cropService,
            FarmProcessStepService farmProcessStepService
    ) {
        this.cropService = cropService;
        this.farmProcessStepService = farmProcessStepService;
    }

    @Override
    /** 查询作物默认模板，策略：品种默认 -> 分类默认 -> 品种首个启用 -> 分类首个启用。 */
    public FarmProcessTemplate findDefaultTemplate(Long cropId) {
        FarmProcessTemplate directDefault = queryDefaultByCropId(cropId);
        if (directDefault != null) {
            return directDefault;
        }

        Long categoryCropId = resolveCategoryCropId(cropId);
        if (categoryCropId != null && !categoryCropId.equals(cropId)) {
            FarmProcessTemplate categoryDefault = queryDefaultByCropId(categoryCropId);
            if (categoryDefault != null) {
                return categoryDefault;
            }
        }

        FarmProcessTemplate directFirst = queryFirstByCropId(cropId);
        if (directFirst != null) {
            return directFirst;
        }

        if (categoryCropId != null && !categoryCropId.equals(cropId)) {
            FarmProcessTemplate categoryFirst = queryFirstByCropId(categoryCropId);
            if (categoryFirst != null) {
                return categoryFirst;
            }
        }

        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeTemplateCascade(Long templateId) {
        if (templateId == null) {
            return false;
        }
        LambdaQueryWrapper<FarmProcessStep> stepQw = new LambdaQueryWrapper<FarmProcessStep>()
                .eq(FarmProcessStep::getTemplateId, templateId);
        farmProcessStepService.remove(stepQw);
        return this.removeById(templateId);
    }

    /** 若传入作物为品种节点，则解析其所属分类 ID。 */
    private Long resolveCategoryCropId(Long cropId) {
        if (cropId == null) {
            return null;
        }
        Crop crop = cropService.getById(cropId);
        if (crop == null) {
            return null;
        }
        String nodeType = StringUtils.hasText(crop.getNodeType()) ? crop.getNodeType().trim().toLowerCase() : "";
        if (!"variety".equals(nodeType)) {
            return null;
        }
        return crop.getParentId();
    }

    /** 查询某作物的启用默认模板。 */
    private FarmProcessTemplate queryDefaultByCropId(Long cropId) {
        LambdaQueryWrapper<FarmProcessTemplate> qw = new LambdaQueryWrapper<>();
        if (cropId != null) {
            qw.eq(FarmProcessTemplate::getCropId, cropId);
        }
        qw.eq(FarmProcessTemplate::getEnabled, 1)
                .eq(FarmProcessTemplate::getIsDefault, 1)
                .last("limit 1");
        return this.getOne(qw, false);
    }

    /** 查询某作物第一个启用模板。 */
    private FarmProcessTemplate queryFirstByCropId(Long cropId) {
        if (cropId == null) {
            return null;
        }
        LambdaQueryWrapper<FarmProcessTemplate> qw = new LambdaQueryWrapper<>();
        qw.eq(FarmProcessTemplate::getCropId, cropId)
                .eq(FarmProcessTemplate::getEnabled, 1)
                .orderByAsc(FarmProcessTemplate::getId)
                .last("limit 1");
        return this.getOne(qw, false);
    }
}
