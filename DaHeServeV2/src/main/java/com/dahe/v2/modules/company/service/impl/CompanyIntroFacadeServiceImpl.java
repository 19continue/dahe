package com.dahe.v2.modules.company.service.impl;

import com.dahe.v2.modules.company.dto.CompanyIntroCommand;
import com.dahe.v2.modules.company.dto.CompanyIntroDTO;
import com.dahe.v2.modules.company.service.CompanyIntroFacadeService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * company 模块业务门面实现。
 */
@Service
public class CompanyIntroFacadeServiceImpl implements CompanyIntroFacadeService {

    private final CompanyIntroQueryServiceImpl queryService;
    private final CompanyInfoCommandServiceImpl companyInfoCommandService;
    private final CompanyProductCommandServiceImpl companyProductCommandService;
    private final CompanyHonorCommandServiceImpl companyHonorCommandService;
    private final CompanyContactCommandServiceImpl companyContactCommandService;

    public CompanyIntroFacadeServiceImpl(
            CompanyIntroQueryServiceImpl queryService,
            CompanyInfoCommandServiceImpl companyInfoCommandService,
            CompanyProductCommandServiceImpl companyProductCommandService,
            CompanyHonorCommandServiceImpl companyHonorCommandService,
            CompanyContactCommandServiceImpl companyContactCommandService
    ) {
        this.queryService = queryService;
        this.companyInfoCommandService = companyInfoCommandService;
        this.companyProductCommandService = companyProductCommandService;
        this.companyHonorCommandService = companyHonorCommandService;
        this.companyContactCommandService = companyContactCommandService;
    }

    @Override
    public CompanyIntroDTO getPublicIntro() {
        return queryService.getPublicIntro();
    }

    @Override
    public CompanyIntroDTO getAdminIntro() {
        return queryService.getAdminIntro();
    }

    @Override
    public CompanyIntroDTO.CompanyInfoItem upsertInfo(CompanyIntroCommand.CompanyInfoUpsert req) {
        return companyInfoCommandService.upsertInfo(req);
    }

    @Override
    public List<CompanyIntroDTO.ProductItem> listProducts() {
        return queryService.listProducts(false);
    }

    @Override
    public CompanyIntroDTO.ProductItem createProduct(CompanyIntroCommand.ProductUpsert req) {
        return companyProductCommandService.create(req);
    }

    @Override
    public CompanyIntroDTO.ProductItem updateProduct(Long id, CompanyIntroCommand.ProductUpsert req) {
        return companyProductCommandService.update(id, req);
    }

    @Override
    public CompanyIntroDTO.ProductItem setProductEnabled(Long id, boolean enabled) {
        return companyProductCommandService.setEnabled(id, enabled);
    }

    @Override
    public boolean deleteProduct(Long id) {
        return companyProductCommandService.delete(id);
    }

    @Override
    public List<CompanyIntroDTO.HonorItem> listHonors() {
        return queryService.listHonors(false);
    }

    @Override
    public CompanyIntroDTO.HonorItem createHonor(CompanyIntroCommand.HonorUpsert req) {
        return companyHonorCommandService.create(req);
    }

    @Override
    public CompanyIntroDTO.HonorItem updateHonor(Long id, CompanyIntroCommand.HonorUpsert req) {
        return companyHonorCommandService.update(id, req);
    }

    @Override
    public CompanyIntroDTO.HonorItem setHonorEnabled(Long id, boolean enabled) {
        return companyHonorCommandService.setEnabled(id, enabled);
    }

    @Override
    public boolean deleteHonor(Long id) {
        return companyHonorCommandService.delete(id);
    }

    @Override
    public List<CompanyIntroDTO.ContactItem> listContacts() {
        return queryService.listContacts(false);
    }

    @Override
    public CompanyIntroDTO.ContactItem createContact(CompanyIntroCommand.ContactUpsert req) {
        return companyContactCommandService.create(req);
    }

    @Override
    public CompanyIntroDTO.ContactItem updateContact(Long id, CompanyIntroCommand.ContactUpsert req) {
        return companyContactCommandService.update(id, req);
    }

    @Override
    public CompanyIntroDTO.ContactItem setContactEnabled(Long id, boolean enabled) {
        return companyContactCommandService.setEnabled(id, enabled);
    }

    @Override
    public boolean deleteContact(Long id) {
        return companyContactCommandService.delete(id);
    }
}
