package com.dahe.v2.modules.company.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dahe.v2.modules.company.dto.CompanyIntroCommand;
import com.dahe.v2.modules.company.dto.CompanyIntroDTO;
import com.dahe.v2.modules.company.model.CompanyInfo;
import com.dahe.v2.modules.assets.service.MediaAssetBindingService;
import com.dahe.v2.modules.company.service.CompanyInfoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 企业基础信息写服务。
 */
@Service
public class CompanyInfoCommandServiceImpl {

    private final CompanyInfoService companyInfoService;
    private final CompanyIntroMapper companyIntroMapper;
    private final CompanyIntroValueNormalizer normalizer;
    private final MediaAssetBindingService mediaAssetBindingService;

    public CompanyInfoCommandServiceImpl(
            CompanyInfoService companyInfoService,
            CompanyIntroMapper companyIntroMapper,
            CompanyIntroValueNormalizer normalizer,
            MediaAssetBindingService mediaAssetBindingService
    ) {
        this.companyInfoService = companyInfoService;
        this.companyIntroMapper = companyIntroMapper;
        this.normalizer = normalizer;
        this.mediaAssetBindingService = mediaAssetBindingService;
    }

    public CompanyIntroDTO.CompanyInfoItem getInfoItem(boolean enabledOnly) {
        return companyIntroMapper.toInfoItem(resolveInfo(enabledOnly));
    }

    /**
     * upsert 企业信息，并在保存后收敛为单例（仅保留一条未删除记录）。
     */
    @Transactional(rollbackFor = Exception.class)
    public CompanyIntroDTO.CompanyInfoItem upsertInfo(CompanyIntroCommand.CompanyInfoUpsert req) {
        CompanyInfo row = resolveInfoForEdit(req.getId());
        if (row.getId() == null) {
            row.setSortOrder(nextInfoSortOrder());
            row.setStatus(1);
        }

        row.setCompanyName(normalizer.normalizeLimitedText(req.getCompanyName(), 100, "公司名称", true));
        row.setLogo(normalizer.normalizeUrl(req.getLogo(), 512, "企业Logo"));
        row.setBanner(normalizer.normalizeUrl(req.getBanner(), 512, "企业Banner"));
        row.setIntroduction(normalizer.normalizeLimitedText(req.getIntroduction(), 2000, "企业介绍", false));
        row.setMission(normalizer.normalizeLimitedText(req.getMission(), 500, "企业使命", false));
        row.setCopyright(normalizer.normalizeLimitedText(req.getCopyright(), 255, "版权文案", false));

        Integer normalizedSort = normalizer.normalizeNonNegative(req.getSortOrder(), null);
        if (normalizedSort != null) {
            row.setSortOrder(normalizedSort);
        }
        if (req.getStatus() != null) {
            row.setStatus(normalizer.normalizeStatus(req.getStatus(), 1));
        }

        if (row.getId() == null) {
            companyInfoService.save(row);
        } else {
            companyInfoService.updateById(row);
        }
        syncInfoAssets(row);
        markDuplicateRowsDeleted(row.getId());
        return companyIntroMapper.toInfoItem(companyInfoService.getById(row.getId()));
    }

    private CompanyInfo resolveInfo(boolean enabledOnly) {
        LambdaQueryWrapper<CompanyInfo> qw = new LambdaQueryWrapper<CompanyInfo>();
        if (enabledOnly) {
            qw.eq(CompanyInfo::getStatus, 1);
        }
        qw.orderByAsc(CompanyInfo::getSortOrder)
                .orderByDesc(CompanyInfo::getUpdatedAt)
                .orderByDesc(CompanyInfo::getId)
                .last("limit 1");
        return companyInfoService.getOne(qw, false);
    }

    private CompanyInfo resolveInfoForEdit(Long id) {
        if (id != null) {
            CompanyInfo hit = companyInfoService.getById(id);
            if (hit != null) {
                return hit;
            }
        }
        CompanyInfo hit = resolveInfo(false);
        return hit == null ? new CompanyInfo() : hit;
    }

    private int nextInfoSortOrder() {
        CompanyInfo top = companyInfoService.lambdaQuery()
                .select(CompanyInfo::getSortOrder)
                .orderByDesc(CompanyInfo::getSortOrder)
                .last("limit 1")
                .one();
        int base = top == null || top.getSortOrder() == null ? 0 : top.getSortOrder();
        return base + 1;
    }

    /**
     * 收敛历史脏数据，保证 company_info 仅有一条生效主记录。
     */
    private void markDuplicateRowsDeleted(Long keepId) {
        if (keepId == null) {
            return;
        }
        List<Long> duplicateIds = companyInfoService.lambdaQuery()
                .select(CompanyInfo::getId)
                .ne(CompanyInfo::getId, keepId)
                .list()
                .stream()
                .map(CompanyInfo::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (!duplicateIds.isEmpty()) {
            companyInfoService.removeByIds(duplicateIds);
        }
    }

    private void syncInfoAssets(CompanyInfo row) {
        if (row == null || row.getId() == null) {
            return;
        }
        mediaAssetBindingService.bindByUrls(
                "company_info",
                row.getId(),
                java.util.Arrays.asList(row.getLogo(), row.getBanner())
        );
    }
}
