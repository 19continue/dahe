package com.dahe.v2.modules.company.controller;

import com.dahe.v2.common.Result;
import com.dahe.v2.modules.company.dto.CompanyIntroDTO;
import com.dahe.v2.modules.company.service.CompanyIntroFacadeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 企业介绍公开端控制器。
 */
@RestController
@RequestMapping("/api/v2/public/company-intro")
public class CompanyPublicController {

    private final CompanyIntroFacadeService companyIntroFacadeService;

    public CompanyPublicController(CompanyIntroFacadeService companyIntroFacadeService) {
        this.companyIntroFacadeService = companyIntroFacadeService;
    }

    @GetMapping
    public Result<CompanyIntroDTO> getPublic() {
        return Result.success(companyIntroFacadeService.getPublicIntro());
    }
}
