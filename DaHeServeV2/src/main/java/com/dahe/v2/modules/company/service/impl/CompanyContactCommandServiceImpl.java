package com.dahe.v2.modules.company.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dahe.v2.modules.company.dto.CompanyIntroCommand;
import com.dahe.v2.modules.company.dto.CompanyIntroDTO;
import com.dahe.v2.modules.company.model.CompanyContact;
import com.dahe.v2.modules.company.service.CompanyContactService;
import com.dahe.v2.modules.company.service.CompanyIntroNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 企业联系方式写服务。
 */
@Service
public class CompanyContactCommandServiceImpl {

    private final CompanyContactService companyContactService;
    private final CompanyIntroMapper companyIntroMapper;
    private final CompanyIntroValueNormalizer normalizer;

    public CompanyContactCommandServiceImpl(
            CompanyContactService companyContactService,
            CompanyIntroMapper companyIntroMapper,
            CompanyIntroValueNormalizer normalizer
    ) {
        this.companyContactService = companyContactService;
        this.companyIntroMapper = companyIntroMapper;
        this.normalizer = normalizer;
    }

    public List<CompanyIntroDTO.ContactItem> listItems(boolean enabledOnly) {
        LambdaQueryWrapper<CompanyContact> qw = new LambdaQueryWrapper<CompanyContact>();
        if (enabledOnly) {
            qw.eq(CompanyContact::getStatus, 1);
        }
        qw.orderByAsc(CompanyContact::getSortOrder)
                .orderByDesc(CompanyContact::getUpdatedAt)
                .orderByDesc(CompanyContact::getId);
        return companyContactService.list(qw).stream()
                .map(companyIntroMapper::toContactItem)
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public CompanyIntroDTO.ContactItem create(CompanyIntroCommand.ContactUpsert req) {
        CompanyContact row = new CompanyContact();
        fillUpsertFields(row, req, true);
        companyContactService.save(row);
        return companyIntroMapper.toContactItem(companyContactService.getById(row.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public CompanyIntroDTO.ContactItem update(Long id, CompanyIntroCommand.ContactUpsert req) {
        CompanyContact row = companyContactService.getById(id);
        if (row == null) {
            throw new CompanyIntroNotFoundException();
        }
        fillUpsertFields(row, req, false);
        companyContactService.updateById(row);
        return companyIntroMapper.toContactItem(companyContactService.getById(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public CompanyIntroDTO.ContactItem setEnabled(Long id, boolean enabled) {
        CompanyContact row = companyContactService.getById(id);
        if (row == null) {
            throw new CompanyIntroNotFoundException();
        }
        row.setStatus(enabled ? 1 : 0);
        companyContactService.updateById(row);
        return companyIntroMapper.toContactItem(companyContactService.getById(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        return companyContactService.removeById(id);
    }

    private void fillUpsertFields(CompanyContact row, CompanyIntroCommand.ContactUpsert req, boolean createMode) {
        String contactType = normalizer.normalizeContactType(req.getContactType());
        String contactLabel = normalizer.normalizeLimitedText(req.getContactLabel(), 50, "联系方式标签", true);
        String contactValue = normalizer.normalizeLimitedText(req.getContactValue(), 255, "联系方式内容", true);
        normalizer.validateContactValue(contactType, contactValue);
        row.setContactType(contactType);
        row.setContactLabel(contactLabel);
        row.setContactValue(contactValue);
        Integer sortOrder = normalizer.normalizeNonNegative(req.getSortOrder(), createMode ? nextSortOrder() : null);
        if (sortOrder != null) {
            row.setSortOrder(sortOrder);
        }
        if (createMode || req.getStatus() != null) {
            row.setStatus(normalizer.normalizeStatus(req.getStatus(), 1));
        }
    }

    private int nextSortOrder() {
        CompanyContact top = companyContactService.lambdaQuery()
                .select(CompanyContact::getSortOrder)
                .orderByDesc(CompanyContact::getSortOrder)
                .last("limit 1")
                .one();
        int base = top == null || top.getSortOrder() == null ? 0 : top.getSortOrder();
        return base + 1;
    }
}
