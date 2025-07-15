package com.dahe.v2.modules.company.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dahe.v2.modules.assets.service.MediaAssetBindingService;
import com.dahe.v2.modules.company.dto.CompanyIntroCommand;
import com.dahe.v2.modules.company.dto.CompanyIntroDTO;
import com.dahe.v2.modules.company.model.CompanyProduct;
import com.dahe.v2.modules.company.service.CompanyIntroNotFoundException;
import com.dahe.v2.modules.company.service.CompanyProductService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 企业产品写服务。
 */
@Service
public class CompanyProductCommandServiceImpl {

    private final CompanyProductService companyProductService;
    private final CompanyIntroMapper companyIntroMapper;
    private final CompanyIntroValueNormalizer normalizer;
    private final MediaAssetBindingService mediaAssetBindingService;

    public CompanyProductCommandServiceImpl(
            CompanyProductService companyProductService,
            CompanyIntroMapper companyIntroMapper,
            CompanyIntroValueNormalizer normalizer,
            MediaAssetBindingService mediaAssetBindingService
    ) {
        this.companyProductService = companyProductService;
        this.companyIntroMapper = companyIntroMapper;
        this.normalizer = normalizer;
        this.mediaAssetBindingService = mediaAssetBindingService;
    }

    public List<CompanyIntroDTO.ProductItem> listItems(boolean enabledOnly) {
        LambdaQueryWrapper<CompanyProduct> qw = new LambdaQueryWrapper<CompanyProduct>();
        if (enabledOnly) {
            qw.eq(CompanyProduct::getStatus, 1);
        }
        qw.orderByAsc(CompanyProduct::getSortOrder)
                .orderByDesc(CompanyProduct::getUpdatedAt)
                .orderByDesc(CompanyProduct::getId);
        return companyProductService.list(qw).stream()
                .map(companyIntroMapper::toProductItem)
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public CompanyIntroDTO.ProductItem create(CompanyIntroCommand.ProductUpsert req) {
        CompanyProduct row = new CompanyProduct();
        fillUpsertFields(row, req, true);
        companyProductService.save(row);
        syncProductAssets(row);
        return companyIntroMapper.toProductItem(companyProductService.getById(row.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public CompanyIntroDTO.ProductItem update(Long id, CompanyIntroCommand.ProductUpsert req) {
        CompanyProduct row = companyProductService.getById(id);
        if (row == null) {
            throw new CompanyIntroNotFoundException();
        }
        fillUpsertFields(row, req, false);
        companyProductService.updateById(row);
        syncProductAssets(row);
        return companyIntroMapper.toProductItem(companyProductService.getById(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public CompanyIntroDTO.ProductItem setEnabled(Long id, boolean enabled) {
        CompanyProduct row = companyProductService.getById(id);
        if (row == null) {
            throw new CompanyIntroNotFoundException();
        }
        row.setStatus(enabled ? 1 : 0);
        companyProductService.updateById(row);
        return companyIntroMapper.toProductItem(companyProductService.getById(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        mediaAssetBindingService.clearBinding("company_product", id);
        return companyProductService.removeById(id);
    }

    private void fillUpsertFields(CompanyProduct row, CompanyIntroCommand.ProductUpsert req, boolean createMode) {
        row.setName(normalizer.normalizeLimitedText(req.getName(), 100, "产品名称", true));
        row.setDescription(normalizer.normalizeLimitedText(req.getDescription(), 500, "产品描述", false));
        row.setImage(normalizer.normalizeUrl(req.getImage(), 512, "产品图片"));
        Integer sortOrder = normalizer.normalizeNonNegative(req.getSortOrder(), createMode ? nextSortOrder() : null);
        if (sortOrder != null) {
            row.setSortOrder(sortOrder);
        }
        if (createMode || req.getStatus() != null) {
            row.setStatus(normalizer.normalizeStatus(req.getStatus(), 1));
        }
    }

    private int nextSortOrder() {
        CompanyProduct top = companyProductService.lambdaQuery()
                .select(CompanyProduct::getSortOrder)
                .orderByDesc(CompanyProduct::getSortOrder)
                .last("limit 1")
                .one();
        int base = top == null || top.getSortOrder() == null ? 0 : top.getSortOrder();
        return base + 1;
    }

    private void syncProductAssets(CompanyProduct row) {
        if (row == null || row.getId() == null) {
            return;
        }
        mediaAssetBindingService.bindByUrls("company_product", row.getId(), java.util.Collections.singletonList(row.getImage()));
    }
}
