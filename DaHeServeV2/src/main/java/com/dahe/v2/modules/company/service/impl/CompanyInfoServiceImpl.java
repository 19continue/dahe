package com.dahe.v2.modules.company.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dahe.v2.modules.company.mapper.CompanyInfoMapper;
import com.dahe.v2.modules.company.model.CompanyInfo;
import com.dahe.v2.modules.company.service.CompanyInfoService;
import org.springframework.stereotype.Service;

/**
 * 企业基础信息服务实现。
 */
@Service
public class CompanyInfoServiceImpl extends ServiceImpl<CompanyInfoMapper, CompanyInfo> implements CompanyInfoService {
}
