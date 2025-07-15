package com.dahe.v2.modules.company.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dahe.v2.modules.company.mapper.CompanyContactMapper;
import com.dahe.v2.modules.company.model.CompanyContact;
import com.dahe.v2.modules.company.service.CompanyContactService;
import org.springframework.stereotype.Service;

/**
 * 企业联系方式服务实现。
 */
@Service
public class CompanyContactServiceImpl extends ServiceImpl<CompanyContactMapper, CompanyContact> implements CompanyContactService {
}
