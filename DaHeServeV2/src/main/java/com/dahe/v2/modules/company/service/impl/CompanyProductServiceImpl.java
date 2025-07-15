package com.dahe.v2.modules.company.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dahe.v2.modules.company.mapper.CompanyProductMapper;
import com.dahe.v2.modules.company.model.CompanyProduct;
import com.dahe.v2.modules.company.service.CompanyProductService;
import org.springframework.stereotype.Service;

/**
 * 企业产品服务实现。
 */
@Service
public class CompanyProductServiceImpl extends ServiceImpl<CompanyProductMapper, CompanyProduct> implements CompanyProductService {
}
