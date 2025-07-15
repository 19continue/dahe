package com.dahe.v2.modules.miniapp.farm.process.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.farm.process.controller.FarmProcessController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.Min;

/**
 * 小程序端流程模板读取接口。
 */
@RestController
@RequestMapping("/api/v2/miniapp/farm-process")
@Validated
public class MiniappFarmProcessController {

    private final FarmProcessController farmProcessController;

    public MiniappFarmProcessController(FarmProcessController farmProcessController) {
        this.farmProcessController = farmProcessController;
    }

    @GetMapping("/templates")
    public Result<Page<FarmProcessController.TemplateItem>> templates(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long cropId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long varietyId,
            @RequestParam(required = false) Integer enabled,
            @RequestParam(defaultValue = "false") boolean includeDisabled,
            @RequestParam(defaultValue = "false") boolean includeSteps,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "50") @Min(1) long pageSize
    ) {
        return farmProcessController.templates(
                keyword, cropId, categoryId, varietyId, enabled, includeDisabled, includeSteps, page, pageSize
        );
    }

    @GetMapping("/templates/{id}")
    public Result<FarmProcessController.TemplateItem> templateDetail(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean includeSteps
    ) {
        return farmProcessController.templateDetail(id, includeSteps);
    }
}

