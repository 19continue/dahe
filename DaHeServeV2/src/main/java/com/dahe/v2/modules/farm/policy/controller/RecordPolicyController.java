package com.dahe.v2.modules.farm.policy.controller;

import com.dahe.v2.common.Result;
import com.dahe.v2.modules.farm.policy.model.RecordPolicyConfig;
import com.dahe.v2.modules.farm.policy.service.RecordPolicyConfigService;
import lombok.Data;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * 农事记录权限策略控制器。
 * 提供公开读取、后台读取与后台更新接口。
 */
@RestController
@RequestMapping("/api/v2")
@Validated
public class RecordPolicyController {

    private final RecordPolicyConfigService recordPolicyConfigService;

    public RecordPolicyController(RecordPolicyConfigService recordPolicyConfigService) {
        this.recordPolicyConfigService = recordPolicyConfigService;
    }

    @GetMapping("/record-policy")
    /** 获取当前记录权限策略（公开接口）。 */
    public Result<RecordPolicyResp> current() {
        return Result.success(RecordPolicyResp.from(recordPolicyConfigService.getOrInit()));
    }

    @GetMapping("/admin/record-policy")
    /** 后台获取记录权限策略（admin/supervisor）。 */
    public Result<RecordPolicyResp> adminCurrent() {
        return Result.success(RecordPolicyResp.from(recordPolicyConfigService.getOrInit()));
    }

    @PutMapping("/admin/record-policy")
    /** 后台更新记录权限策略（仅 admin）。 */
    public Result<RecordPolicyResp> adminUpdate(@RequestBody @Validated UpdateReq req) {
        RecordPolicyConfig row = recordPolicyConfigService.getOrInit();
        row.setEditWindowHours(req.getEditWindowHours());
        Integer allowUpdate = req.getAllowMiniappUpdate() == null ? req.getAllowOperatorUpdate() : req.getAllowMiniappUpdate();
        Integer allowDelete = req.getAllowMiniappDelete() == null ? req.getAllowOperatorDelete() : req.getAllowMiniappDelete();
        row.setAllowOperatorUpdate(allowUpdate);
        row.setAllowOperatorDelete(allowDelete);
        row.setRemark(req.getRemark());
        recordPolicyConfigService.updateById(row);
        return Result.success(RecordPolicyResp.from(recordPolicyConfigService.getOrInit()));
    }

    @Data
    public static class UpdateReq {
        @Min(0)
        /** 可编辑窗口（小时）。 */
        private Integer editWindowHours;

        @Min(0)
        @Max(1)
        /** 是否允许操作员编辑（兼容旧字段）。 */
        private Integer allowOperatorUpdate;

        @Min(0)
        @Max(1)
        /** 是否允许小程序端编辑（优先）。 */
        private Integer allowMiniappUpdate;

        @Min(0)
        @Max(1)
        /** 是否允许操作员删除（兼容旧字段）。 */
        private Integer allowOperatorDelete;

        @Min(0)
        @Max(1)
        /** 是否允许小程序端删除（优先）。 */
        private Integer allowMiniappDelete;

        /** 备注。 */
        private String remark;
    }

    @Data
    public static class RecordPolicyResp {
        /** 可编辑窗口（小时）。 */
        private Integer editWindowHours;
        /** 是否允许操作员编辑。 */
        private Integer allowOperatorUpdate;
        /** 是否允许操作员删除。 */
        private Integer allowOperatorDelete;
        /** 是否允许小程序端编辑。 */
        private Integer allowMiniappUpdate;
        /** 是否允许小程序端删除。 */
        private Integer allowMiniappDelete;
        /** 备注。 */
        private String remark;

        /** 从策略实体映射为响应对象。 */
        static RecordPolicyResp from(RecordPolicyConfig row) {
            RecordPolicyResp out = new RecordPolicyResp();
            out.setEditWindowHours(row == null ? 48 : row.getEditWindowHours());
            out.setAllowOperatorUpdate(row == null ? 1 : row.getAllowOperatorUpdate());
            out.setAllowOperatorDelete(row == null ? 1 : row.getAllowOperatorDelete());
            out.setAllowMiniappUpdate(out.getAllowOperatorUpdate());
            out.setAllowMiniappDelete(out.getAllowOperatorDelete());
            out.setRemark(row == null ? null : row.getRemark());
            return out;
        }
    }
}
