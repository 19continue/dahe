package com.dahe.v2.modules.export.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.auth.support.AdminMenuCode;
import com.dahe.v2.modules.export.model.ExportFieldDict;
import com.dahe.v2.modules.export.service.ExportFieldDictService;
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
 * 导出字段词典管理控制器。
 */
@RestController
@RequestMapping("/api/v2/admin/export-dicts")
@AdminMenuCode("/export-templates")
@Validated
public class ExportDictionaryController {

    private final ExportFieldDictService exportFieldDictService;

    public ExportDictionaryController(ExportFieldDictService exportFieldDictService) {
        this.exportFieldDictService = exportFieldDictService;
    }

    @GetMapping
    public Result<Page<ExportFieldDict>> page(
            HttpServletRequest request,
            @RequestParam(required = false) String moduleKey,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) long pageSize
    ) {
        try {
            return Result.success(exportFieldDictService.pageRows(moduleKey, keyword, page, pageSize));
        } catch (Exception e) {
            return tableOrServerError(e);
        }
    }

    @PostMapping
    public Result<ExportFieldDict> create(HttpServletRequest request, @RequestBody @Validated SaveReq req) {
        try {
            ExportFieldDict row = new ExportFieldDict();
            row.setModuleKey(req.getModuleKey().trim());
            row.setFieldCode(req.getFieldCode().trim());
            row.setFieldName(req.getFieldName().trim());
            row.setDataType(StringUtils.hasText(req.getDataType()) ? req.getDataType().trim() : "string");
            row.setDescription(req.getDescription());
            row.setExampleValue(req.getExampleValue());
            exportFieldDictService.save(row);
            return Result.success(row);
        } catch (Exception e) {
            return tableOrServerError(e);
        }
    }

    @PutMapping("/{id}")
    public Result<ExportFieldDict> update(HttpServletRequest request, @PathVariable Long id, @RequestBody @Validated SaveReq req) {
        try {
            ExportFieldDict row = exportFieldDictService.getById(id);
            if (row == null) {
                return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
            }
            row.setModuleKey(req.getModuleKey().trim());
            row.setFieldCode(req.getFieldCode().trim());
            row.setFieldName(req.getFieldName().trim());
            row.setDataType(StringUtils.hasText(req.getDataType()) ? req.getDataType().trim() : "string");
            row.setDescription(req.getDescription());
            row.setExampleValue(req.getExampleValue());
            exportFieldDictService.updateById(row);
            return Result.success(row);
        } catch (Exception e) {
            return tableOrServerError(e);
        }
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(HttpServletRequest request, @PathVariable Long id) {
        try {
            ExportFieldDict row = exportFieldDictService.getById(id);
            if (row == null) {
                return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
            }
            boolean ok = exportFieldDictService.removeById(id);
            return ok ? Result.success(Boolean.TRUE) : Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        } catch (Exception e) {
            return tableOrServerError(e);
        }
    }

    private <T> Result<T> tableOrServerError(Exception e) {
        String message = e == null ? null : e.getMessage();
        String text = message == null ? "" : message.toLowerCase();
        if (text.contains("uk_export_field_code")) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "同模块下字段编码不能重复");
        }
        if (text.contains("export_field_dict")) {
            return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), "导出相关数据表未初始化，请执行数据库脚本 db/schema-export.sql");
        }
        return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage());
    }

    @Data
    public static class SaveReq {
        @NotBlank(message = "模块标识不能为空")
        private String moduleKey;

        @NotBlank(message = "字段编码不能为空")
        private String fieldCode;

        @NotBlank(message = "字段名称不能为空")
        private String fieldName;

        private String dataType;
        private String description;
        private String exampleValue;
    }
}
