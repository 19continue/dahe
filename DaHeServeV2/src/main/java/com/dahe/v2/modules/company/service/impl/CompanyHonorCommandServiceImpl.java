package com.dahe.v2.modules.company.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dahe.v2.modules.assets.service.MediaAssetBindingService;
import com.dahe.v2.modules.company.dto.CompanyIntroCommand;
import com.dahe.v2.modules.company.dto.CompanyIntroDTO;
import com.dahe.v2.modules.company.model.CompanyHonor;
import com.dahe.v2.modules.company.service.CompanyHonorService;
import com.dahe.v2.modules.company.service.CompanyIntroNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 企业荣誉写服务。
 */
@Service
public class CompanyHonorCommandServiceImpl {

    private final CompanyHonorService companyHonorService;
    private final CompanyIntroMapper companyIntroMapper;
    private final CompanyIntroValueNormalizer normalizer;
    private final MediaAssetBindingService mediaAssetBindingService;

    public CompanyHonorCommandServiceImpl(
            CompanyHonorService companyHonorService,
            CompanyIntroMapper companyIntroMapper,
            CompanyIntroValueNormalizer normalizer,
            MediaAssetBindingService mediaAssetBindingService
    ) {
        this.companyHonorService = companyHonorService;
        this.companyIntroMapper = companyIntroMapper;
        this.normalizer = normalizer;
        this.mediaAssetBindingService = mediaAssetBindingService;
    }

    public List<CompanyIntroDTO.HonorItem> listItems(boolean enabledOnly) {
        LambdaQueryWrapper<CompanyHonor> qw = new LambdaQueryWrapper<CompanyHonor>();
        if (enabledOnly) {
            qw.eq(CompanyHonor::getStatus, 1);
        }
        qw.orderByAsc(CompanyHonor::getSortOrder)
                .orderByDesc(CompanyHonor::getUpdatedAt)
                .orderByDesc(CompanyHonor::getId);
        return companyHonorService.list(qw).stream()
                .map(companyIntroMapper::toHonorItem)
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public CompanyIntroDTO.HonorItem create(CompanyIntroCommand.HonorUpsert req) {
        CompanyHonor row = new CompanyHonor();
        fillUpsertFields(row, req, true);
        companyHonorService.save(row);
        syncHonorAssets(row);
        return companyIntroMapper.toHonorItem(companyHonorService.getById(row.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public CompanyIntroDTO.HonorItem update(Long id, CompanyIntroCommand.HonorUpsert req) {
        CompanyHonor row = companyHonorService.getById(id);
        if (row == null) {
            throw new CompanyIntroNotFoundException();
        }
        fillUpsertFields(row, req, false);
        companyHonorService.updateById(row);
        syncHonorAssets(row);
        return companyIntroMapper.toHonorItem(companyHonorService.getById(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public CompanyIntroDTO.HonorItem setEnabled(Long id, boolean enabled) {
        CompanyHonor row = companyHonorService.getById(id);
        if (row == null) {
            throw new CompanyIntroNotFoundException();
        }
        row.setStatus(enabled ? 1 : 0);
        companyHonorService.updateById(row);
        return companyIntroMapper.toHonorItem(companyHonorService.getById(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        mediaAssetBindingService.clearBinding("company_honor", id);
        return companyHonorService.removeById(id);
    }

    private void fillUpsertFields(CompanyHonor row, CompanyIntroCommand.HonorUpsert req, boolean createMode) {
        row.setName(normalizer.normalizeLimitedText(req.getName(), 100, "荣誉名称", true));
        row.setImage(normalizer.normalizeUrl(req.getImage(), 512, "荣誉图片"));
        Integer sortOrder = normalizer.normalizeNonNegative(req.getSortOrder(), createMode ? nextSortOrder() : null);
        if (sortOrder != null) {
            row.setSortOrder(sortOrder);
        }
        if (createMode || req.getStatus() != null) {
            row.setStatus(normalizer.normalizeStatus(req.getStatus(), 1));
        }
    }

    private int nextSortOrder() {
        CompanyHonor top = companyHonorService.lambdaQuery()
                .select(CompanyHonor::getSortOrder)
                .orderByDesc(CompanyHonor::getSortOrder)
                .last("limit 1")
                .one();
        int base = top == null || top.getSortOrder() == null ? 0 : top.getSortOrder();
        return base + 1;
    }

    private void syncHonorAssets(CompanyHonor row) {
        if (row == null || row.getId() == null) {
            return;
        }
        mediaAssetBindingService.bindByUrls("company_honor", row.getId(), java.util.Collections.singletonList(row.getImage()));
    }
}
