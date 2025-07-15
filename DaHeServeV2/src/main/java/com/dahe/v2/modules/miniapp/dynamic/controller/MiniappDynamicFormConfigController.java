package com.dahe.v2.modules.miniapp.dynamic.controller;

import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.dynamic.model.DynamicFormConfig;
import com.dahe.v2.modules.dynamic.service.DynamicFormConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序端动态表单配置读取接口。
 */
@RestController
@RequestMapping("/api/v2/miniapp/dynamic-configs")
public class MiniappDynamicFormConfigController {

    private final DynamicFormConfigService dynamicFormConfigService;

    public MiniappDynamicFormConfigController(DynamicFormConfigService dynamicFormConfigService) {
        this.dynamicFormConfigService = dynamicFormConfigService;
    }

    @GetMapping("/current")
    public Result<DynamicFormConfig> current(
            @RequestParam String moduleKey,
            @RequestParam String sceneKey,
            @RequestParam(defaultValue = "enabled") String status
    ) {
        DynamicFormConfig row = dynamicFormConfigService.findCurrent(moduleKey, sceneKey, status);
        if (row == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        return Result.success(row);
    }
}

