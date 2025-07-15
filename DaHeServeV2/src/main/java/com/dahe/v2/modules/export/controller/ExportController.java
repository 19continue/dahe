package com.dahe.v2.modules.export.controller;

import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.auth.support.AdminMenuCode;
import com.dahe.v2.modules.export.service.ExportCsvCommand;
import com.dahe.v2.modules.export.service.ExportCsvFacadeService;
import com.dahe.v2.modules.export.service.ExportServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 导出控制器。
 *
 * <p>控制层只处理 HTTP 协议参数与响应，导出编排逻辑统一下沉到 service。</p>
 */
@RestController
@RequestMapping("/api/v2/admin/exports")
@AdminMenuCode("/exports")
public class ExportController {

    private static final Logger log = LoggerFactory.getLogger(ExportController.class);

    private final ExportCsvFacadeService exportCsvFacadeService;

    public ExportController(ExportCsvFacadeService exportCsvFacadeService) {
        this.exportCsvFacadeService = exportCsvFacadeService;
    }

    @GetMapping("/farm-records.csv")
    public Object exportFarmRecords(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestParam(required = false) Long fieldId,
            @RequestParam(required = false) Long cycleId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String township,
            @RequestParam(required = false) String templateCode,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate
    ) {
        try {
            ExportCsvCommand.FarmQuery query = new ExportCsvCommand.FarmQuery();
            query.setFieldId(fieldId);
            query.setCycleId(cycleId);
            query.setYear(year);
            query.setTownship(township);
            query.setTemplateCode(templateCode);
            query.setStartDate(startDate);
            query.setEndDate(endDate);
            return exportCsvFacadeService.exportFarmRecordsCsv(query, response);
        } catch (ExportServiceException ex) {
            return Result.failure(ex.getCode(), ex.getMessage());
        } catch (Exception ex) {
            log.warn("Export farm records failed: {}", ex.getMessage());
            return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), "农事记录导出失败，请稍后重试");
        }
    }

    @GetMapping("/seed-tests.csv")
    public Object exportSeedTests(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String templateCode,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate
    ) {
        try {
            ExportCsvCommand.SeedQuery query = new ExportCsvCommand.SeedQuery();
            query.setBatchId(batchId);
            query.setYear(year);
            query.setTemplateCode(templateCode);
            query.setStartDate(startDate);
            query.setEndDate(endDate);
            return exportCsvFacadeService.exportSeedTestsCsv(query, response);
        } catch (ExportServiceException ex) {
            return Result.failure(ex.getCode(), ex.getMessage());
        } catch (Exception ex) {
            log.warn("Export seed tests failed: {}", ex.getMessage());
            return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), "种子检测导出失败，请稍后重试");
        }
    }
}
