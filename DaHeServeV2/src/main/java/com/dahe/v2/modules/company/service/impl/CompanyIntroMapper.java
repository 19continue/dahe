package com.dahe.v2.modules.company.service.impl;

import com.dahe.v2.modules.company.dto.CompanyIntroDTO;
import com.dahe.v2.modules.company.model.CompanyContact;
import com.dahe.v2.modules.company.model.CompanyHonor;
import com.dahe.v2.modules.company.model.CompanyInfo;
import com.dahe.v2.modules.company.model.CompanyProduct;
import org.springframework.stereotype.Component;

/**
 * company 模块实体与 DTO 映射器。
 */
@Component
public class CompanyIntroMapper {

    public CompanyIntroDTO.CompanyInfoItem toInfoItem(CompanyInfo row) {
        if (row == null) {
            return null;
        }
        CompanyIntroDTO.CompanyInfoItem item = new CompanyIntroDTO.CompanyInfoItem();
        item.setId(row.getId());
        item.setCompanyName(row.getCompanyName());
        item.setLogo(row.getLogo());
        item.setBanner(row.getBanner());
        item.setIntroduction(row.getIntroduction());
        item.setMission(row.getMission());
        item.setCopyright(row.getCopyright());
        item.setSortOrder(row.getSortOrder());
        item.setStatus(row.getStatus());
        return item;
    }

    public CompanyIntroDTO.ProductItem toProductItem(CompanyProduct row) {
        if (row == null) {
            return null;
        }
        CompanyIntroDTO.ProductItem item = new CompanyIntroDTO.ProductItem();
        item.setId(row.getId());
        item.setName(row.getName());
        item.setDescription(row.getDescription());
        item.setImage(row.getImage());
        item.setSortOrder(row.getSortOrder());
        item.setStatus(row.getStatus());
        return item;
    }

    public CompanyIntroDTO.HonorItem toHonorItem(CompanyHonor row) {
        if (row == null) {
            return null;
        }
        CompanyIntroDTO.HonorItem item = new CompanyIntroDTO.HonorItem();
        item.setId(row.getId());
        item.setName(row.getName());
        item.setImage(row.getImage());
        item.setSortOrder(row.getSortOrder());
        item.setStatus(row.getStatus());
        return item;
    }

    public CompanyIntroDTO.ContactItem toContactItem(CompanyContact row) {
        if (row == null) {
            return null;
        }
        CompanyIntroDTO.ContactItem item = new CompanyIntroDTO.ContactItem();
        item.setId(row.getId());
        item.setContactType(row.getContactType());
        item.setContactLabel(row.getContactLabel());
        item.setContactValue(row.getContactValue());
        item.setSortOrder(row.getSortOrder());
        item.setStatus(row.getStatus());
        return item;
    }
}
