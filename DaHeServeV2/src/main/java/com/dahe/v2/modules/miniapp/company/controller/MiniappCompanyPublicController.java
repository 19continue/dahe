package com.dahe.v2.modules.miniapp.company.controller;

import com.dahe.v2.common.Result;
import com.dahe.v2.modules.company.dto.CompanyIntroDTO;
import com.dahe.v2.modules.company.service.CompanyIntroFacadeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序端企业介绍接口。
 */
@RestController
@RequestMapping("/api/v2/miniapp/public/company-intro")
public class MiniappCompanyPublicController {

    private final CompanyIntroFacadeService companyIntroFacadeService;

    public MiniappCompanyPublicController(CompanyIntroFacadeService companyIntroFacadeService) {
        this.companyIntroFacadeService = companyIntroFacadeService;
    }

    @GetMapping
    public Result<CompanyIntroDTO> getPublic() {
        return Result.success(companyIntroFacadeService.getPublicIntro());
    }
}

