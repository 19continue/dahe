package com.dahe.v2.modules.export.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.export.model.ExportTemplate;
import com.dahe.v2.modules.export.service.ExportTemplateService;
import lombok.Data;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

/**
 * 导出模板管理控制器。
 */
@RestController
@RequestMapping("/api/v2/admin/export-templates")
@Validated
public class ExportTemplateAdminController {

    private final ExportTemplateService exportTemplateService;

    public ExportTemplateAdminController(ExportTemplateService exportTemplateService) {
        this.exportTemplateService = exportTemplateService;
    }

    @GetMapping
    public Result<Page<ExportTemplate>> page(
            HttpServletRequest request,
            @RequestParam(required = false) String moduleKey,
            @RequestParam(required = false) String templateCode,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) long pageSize
    ) {
        try {
            return Result.success(exportTemplateService.pageRows(moduleKey, templateCode, status, page, pageSize));
        } catch (Exception e) {
            return tableOrServerError(e);
        }
    }

    @PostMapping
    public Result<ExportTemplate> create(HttpServletRequest request, @RequestBody @Validated SaveReq req) {
        try {
            ExportTemplate row = new ExportTemplate();
            row.setModuleKey(req.getModuleKey().trim());
            row.setTemplateCode(req.getTemplateCode().trim());
            row.setTemplateName(req.getTemplateName().trim());
            row.setVersionNo(req.getVersionNo() == null || req.getVersionNo() <= 0 ? 1 : req.getVersionNo());
            row.setStatus(StringUtils.hasText(req.getStatus()) ? req.getStatus().trim() : "enabled");
            row.setFieldsJson(exportTemplateService.normalizeFieldsJson(req.getFieldsJson()));
            row.setRemark(req.getRemark());
            exportTemplateService.save(row);
            return Result.success(row);
        } catch (IllegalArgumentException ex) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), ex.getMessage());
        } catch (Exception e) {
            return tableOrServerError(e);
        }
    }

    @PutMapping("/{id}")
    public Result<ExportTemplate> update(HttpServletRequest request, @PathVariable Long id, @RequestBody @Validated SaveReq req) {
        try {
            ExportTemplate row = exportTemplateService.getById(id);
            if (row == null) {
                return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
            }
            row.setModuleKey(req.getModuleKey().trim());
            row.setTemplateCode(req.getTemplateCode().trim());
            row.setTemplateName(req.getTemplateName().trim());
            row.setVersionNo(req.getVersionNo() == null || req.getVersionNo() <= 0 ? 1 : req.getVersionNo());
            row.setStatus(StringUtils.hasText(req.getStatus()) ? req.getStatus().trim() : "enabled");
            row.setFieldsJson(exportTemplateService.normalizeFieldsJson(req.getFieldsJson()));
            row.setRemark(req.getRemark());
            exportTemplateService.updateById(row);
            return Result.success(row);
        } catch (IllegalArgumentException ex) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), ex.getMessage());
        } catch (Exception e) {
            return tableOrServerError(e);
        }
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(HttpServletRequest request, @PathVariable Long id) {
        try {
            ExportTemplate row = exportTemplateService.getById(id);
            if (row == null) {
                return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
            }
            boolean ok = exportTemplateService.removeById(id);
            if (!ok) {
                return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
            }
            return Result.success(Boolean.TRUE);
        } catch (Exception e) {
            return tableOrServerError(e);
        }
    }

    private <T> Result<T> tableOrServerError(Exception e) {
        String message = e == null ? null : e.getMessage();
        String text = message == null ? "" : message.toLowerCase();
        if (text.contains("uk_export_template_ver")) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "同模块下模板编码和版本号不能重复");
        }
        if (text.contains("export_template")) {
            return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), "导出相关数据表未初始化，请执行数据库脚本 db/schema-export.sql");
        }
        return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage());
    }

    @Data
    public static class SaveReq {
        @NotBlank(message = "模块标识不能为空")
        private String moduleKey;

        @NotBlank(message = "模板编码不能为空")
        private String templateCode;

        @NotBlank(message = "模板名称不能为空")
        private String templateName;

        @NotBlank(message = "字段配置不能为空")
        private String fieldsJson;

        private Integer versionNo;
        private String status;
        private String remark;
    }
}
