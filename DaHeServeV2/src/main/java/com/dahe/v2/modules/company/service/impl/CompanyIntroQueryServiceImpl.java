package com.dahe.v2.modules.company.service.impl;

import com.dahe.v2.modules.company.dto.CompanyIntroDTO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * company 模块只读聚合服务。
 */
@Service
public class CompanyIntroQueryServiceImpl {

    private final CompanyInfoCommandServiceImpl companyInfoCommandService;
    private final CompanyProductCommandServiceImpl companyProductCommandService;
    private final CompanyHonorCommandServiceImpl companyHonorCommandService;
    private final CompanyContactCommandServiceImpl companyContactCommandService;

    public CompanyIntroQueryServiceImpl(
            CompanyInfoCommandServiceImpl companyInfoCommandService,
            CompanyProductCommandServiceImpl companyProductCommandService,
            CompanyHonorCommandServiceImpl companyHonorCommandService,
            CompanyContactCommandServiceImpl companyContactCommandService
    ) {
        this.companyInfoCommandService = companyInfoCommandService;
        this.companyProductCommandService = companyProductCommandService;
        this.companyHonorCommandService = companyHonorCommandService;
        this.companyContactCommandService = companyContactCommandService;
    }

    public CompanyIntroDTO getPublicIntro() {
        return buildIntro(true);
    }

    public CompanyIntroDTO getAdminIntro() {
        return buildIntro(false);
    }

    public List<CompanyIntroDTO.ProductItem> listProducts(boolean enabledOnly) {
        return companyProductCommandService.listItems(enabledOnly);
    }

    public List<CompanyIntroDTO.HonorItem> listHonors(boolean enabledOnly) {
        return companyHonorCommandService.listItems(enabledOnly);
    }

    public List<CompanyIntroDTO.ContactItem> listContacts(boolean enabledOnly) {
        return companyContactCommandService.listItems(enabledOnly);
    }

    private CompanyIntroDTO buildIntro(boolean enabledOnly) {
        CompanyIntroDTO dto = new CompanyIntroDTO();
        dto.setCompanyInfo(companyInfoCommandService.getInfoItem(enabledOnly));
        dto.setProducts(companyProductCommandService.listItems(enabledOnly));
        dto.setHonors(companyHonorCommandService.listItems(enabledOnly));
        dto.setContacts(companyContactCommandService.listItems(enabledOnly));
        return dto;
    }
}
