package com.dahe.v2.modules.company.service;

import com.dahe.v2.modules.company.dto.CompanyIntroCommand;
import com.dahe.v2.modules.company.dto.CompanyIntroDTO;

import java.util.List;

/**
 * company 模块门面服务。
 */
public interface CompanyIntroFacadeService {

    CompanyIntroDTO getPublicIntro();

    CompanyIntroDTO getAdminIntro();

    CompanyIntroDTO.CompanyInfoItem upsertInfo(CompanyIntroCommand.CompanyInfoUpsert req);

    List<CompanyIntroDTO.ProductItem> listProducts();

    CompanyIntroDTO.ProductItem createProduct(CompanyIntroCommand.ProductUpsert req);

    CompanyIntroDTO.ProductItem updateProduct(Long id, CompanyIntroCommand.ProductUpsert req);

    CompanyIntroDTO.ProductItem setProductEnabled(Long id, boolean enabled);

    boolean deleteProduct(Long id);

    List<CompanyIntroDTO.HonorItem> listHonors();

    CompanyIntroDTO.HonorItem createHonor(CompanyIntroCommand.HonorUpsert req);

    CompanyIntroDTO.HonorItem updateHonor(Long id, CompanyIntroCommand.HonorUpsert req);

    CompanyIntroDTO.HonorItem setHonorEnabled(Long id, boolean enabled);

    boolean deleteHonor(Long id);

    List<CompanyIntroDTO.ContactItem> listContacts();

    CompanyIntroDTO.ContactItem createContact(CompanyIntroCommand.ContactUpsert req);

    CompanyIntroDTO.ContactItem updateContact(Long id, CompanyIntroCommand.ContactUpsert req);

    CompanyIntroDTO.ContactItem setContactEnabled(Long id, boolean enabled);

    boolean deleteContact(Long id);
}
