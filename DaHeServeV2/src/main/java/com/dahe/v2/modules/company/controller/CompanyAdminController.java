package com.dahe.v2.modules.company.controller;

import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.auth.support.AdminMenuCode;
import com.dahe.v2.modules.company.dto.CompanyIntroCommand;
import com.dahe.v2.modules.company.dto.CompanyIntroDTO;
import com.dahe.v2.modules.company.service.CompanyIntroFacadeService;
import com.dahe.v2.modules.company.service.CompanyIntroNotFoundException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 企业介绍后台控制器。
 */
@RestController
@RequestMapping("/api/v2/admin/company-intro")
@AdminMenuCode("/company-intro")
@Validated
public class CompanyAdminController {

    private final CompanyIntroFacadeService companyIntroFacadeService;

    public CompanyAdminController(CompanyIntroFacadeService companyIntroFacadeService) {
        this.companyIntroFacadeService = companyIntroFacadeService;
    }

    @GetMapping
    public Result<CompanyIntroDTO> getAdmin() {
        return Result.success(companyIntroFacadeService.getAdminIntro());
    }

    @PutMapping("/info")
    public Result<CompanyIntroDTO.CompanyInfoItem> upsertInfo(@RequestBody @Validated CompanyIntroCommand.CompanyInfoUpsert req) {
        return Result.success(companyIntroFacadeService.upsertInfo(req));
    }

    @GetMapping("/products")
    public Result<List<CompanyIntroDTO.ProductItem>> listProducts() {
        return Result.success(companyIntroFacadeService.listProducts());
    }

    @PostMapping("/products")
    public Result<CompanyIntroDTO.ProductItem> createProduct(@RequestBody @Validated CompanyIntroCommand.ProductUpsert req) {
        return Result.success(companyIntroFacadeService.createProduct(req));
    }

    @PutMapping("/products/{id}")
    public Result<CompanyIntroDTO.ProductItem> updateProduct(
            @PathVariable Long id,
            @RequestBody @Validated CompanyIntroCommand.ProductUpsert req
    ) {
        try {
            return Result.success(companyIntroFacadeService.updateProduct(id, req));
        } catch (CompanyIntroNotFoundException ex) {
            return notFound();
        }
    }

    @PutMapping("/products/{id}/enabled")
    public Result<CompanyIntroDTO.ProductItem> setProductEnabled(
            @PathVariable Long id,
            @RequestBody @Validated CompanyIntroCommand.Enabled req
    ) {
        try {
            return Result.success(companyIntroFacadeService.setProductEnabled(id, Boolean.TRUE.equals(req.getEnabled())));
        } catch (CompanyIntroNotFoundException ex) {
            return notFound();
        }
    }

    @DeleteMapping("/products/{id}")
    public Result<Void> deleteProduct(@PathVariable Long id) {
        boolean ok = companyIntroFacadeService.deleteProduct(id);
        return ok ? Result.success(null) : Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
    }

    @GetMapping("/honors")
    public Result<List<CompanyIntroDTO.HonorItem>> listHonors() {
        return Result.success(companyIntroFacadeService.listHonors());
    }

    @PostMapping("/honors")
    public Result<CompanyIntroDTO.HonorItem> createHonor(@RequestBody @Validated CompanyIntroCommand.HonorUpsert req) {
        return Result.success(companyIntroFacadeService.createHonor(req));
    }

    @PutMapping("/honors/{id}")
    public Result<CompanyIntroDTO.HonorItem> updateHonor(
            @PathVariable Long id,
            @RequestBody @Validated CompanyIntroCommand.HonorUpsert req
    ) {
        try {
            return Result.success(companyIntroFacadeService.updateHonor(id, req));
        } catch (CompanyIntroNotFoundException ex) {
            return notFound();
        }
    }

    @PutMapping("/honors/{id}/enabled")
    public Result<CompanyIntroDTO.HonorItem> setHonorEnabled(
            @PathVariable Long id,
            @RequestBody @Validated CompanyIntroCommand.Enabled req
    ) {
        try {
            return Result.success(companyIntroFacadeService.setHonorEnabled(id, Boolean.TRUE.equals(req.getEnabled())));
        } catch (CompanyIntroNotFoundException ex) {
            return notFound();
        }
    }

    @DeleteMapping("/honors/{id}")
    public Result<Void> deleteHonor(@PathVariable Long id) {
        boolean ok = companyIntroFacadeService.deleteHonor(id);
        return ok ? Result.success(null) : Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
    }

    @GetMapping("/contacts")
    public Result<List<CompanyIntroDTO.ContactItem>> listContacts() {
        return Result.success(companyIntroFacadeService.listContacts());
    }

    @PostMapping("/contacts")
    public Result<CompanyIntroDTO.ContactItem> createContact(@RequestBody @Validated CompanyIntroCommand.ContactUpsert req) {
        return Result.success(companyIntroFacadeService.createContact(req));
    }

    @PutMapping("/contacts/{id}")
    public Result<CompanyIntroDTO.ContactItem> updateContact(
            @PathVariable Long id,
            @RequestBody @Validated CompanyIntroCommand.ContactUpsert req
    ) {
        try {
            return Result.success(companyIntroFacadeService.updateContact(id, req));
        } catch (CompanyIntroNotFoundException ex) {
            return notFound();
        }
    }

    @PutMapping("/contacts/{id}/enabled")
    public Result<CompanyIntroDTO.ContactItem> setContactEnabled(
            @PathVariable Long id,
            @RequestBody @Validated CompanyIntroCommand.Enabled req
    ) {
        try {
            return Result.success(companyIntroFacadeService.setContactEnabled(id, Boolean.TRUE.equals(req.getEnabled())));
        } catch (CompanyIntroNotFoundException ex) {
            return notFound();
        }
    }

    @DeleteMapping("/contacts/{id}")
    public Result<Void> deleteContact(@PathVariable Long id) {
        boolean ok = companyIntroFacadeService.deleteContact(id);
        return ok ? Result.success(null) : Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
    }

    private <T> Result<T> notFound() {
        return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
    }
}
