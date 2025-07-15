package com.dahe.v2.modules.company.service;

/**
 * company 模块未找到目标数据异常。
 */
public class CompanyIntroNotFoundException extends RuntimeException {
    public CompanyIntroNotFoundException() {
        super("记录不存在");
    }
}
