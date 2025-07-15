package com.dahe.v2.modules.seed.controller;

import com.dahe.v2.common.Result;
import com.dahe.v2.modules.seed.model.SeedQualityRule;
import com.dahe.v2.modules.seed.service.SeedAdminCommand;
import com.dahe.v2.modules.seed.service.SeedAdminFacadeService;
import com.dahe.v2.modules.seed.service.SeedServiceException;
import lombok.Data;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * 种子检测规则控制器（单例配置）。
 *
 * <p>仅负责协议层处理，业务由 `SeedAdminFacadeService` 承载。</p>
 */
@RestController
@RequestMapping("/api/v2/seed-settings")
@Validated
public class SeedRuleController {

    private final SeedAdminFacadeService seedAdminFacadeService;

    public SeedRuleController(SeedAdminFacadeService seedAdminFacadeService) {
        this.seedAdminFacadeService = seedAdminFacadeService;
    }

    /** 查询当前检测规则。 */
    @GetMapping
    public Result<SeedRuleResp> getCurrent() {
        return Result.success(SeedRuleResp.from(seedAdminFacadeService.getRule()));
    }

    /** 更新检测规则。 */
    @PutMapping
    public Result<SeedRuleResp> update(@RequestBody @Validated SeedRuleUpdateReq req) {
        try {
            SeedAdminCommand.RuleUpdate command = new SeedAdminCommand.RuleUpdate();
            command.setFixedSampleSize(req.getFixedSampleSize());
            command.setDefaultSampleSize(req.getDefaultSampleSize());
            command.setRemark(req.getRemark());
            SeedQualityRule row = seedAdminFacadeService.updateRule(command);
            return Result.success(SeedRuleResp.from(row));
        } catch (SeedServiceException ex) {
            return Result.failure(ex.getCode(), ex.getMessage());
        }
    }

    @Data
    /** 检测规则更新请求。 */
    public static class SeedRuleUpdateReq {
        @Min(0)
        @Max(1)
        private Integer fixedSampleSize;

        @Min(1)
        private Integer defaultSampleSize;

        private String remark;
    }

    @Data
    /** 检测规则响应体。 */
    public static class SeedRuleResp {
        private Integer fixedSampleSize;
        private Integer defaultSampleSize;
        private String remark;

        static SeedRuleResp from(SeedQualityRule row) {
            SeedRuleResp resp = new SeedRuleResp();
            resp.setFixedSampleSize(row == null ? 1 : row.getFixedSampleSize());
            resp.setDefaultSampleSize(row == null ? 100 : row.getDefaultSampleSize());
            resp.setRemark(row == null ? null : row.getRemark());
            return resp;
        }
    }
}
