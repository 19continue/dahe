package com.dahe.v2.modules.export.service;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 导出模块命令对象。
 *
 * <p>用于隔离 controller 查询参数与 service 编排入参，避免业务参数散落在控制层。</p>
 */
public final class ExportCsvCommand {

    private ExportCsvCommand() {
    }

    @Data
    public static class FarmQuery {
        private Long fieldId;
        private Long cycleId;
        private Integer year;
        private String township;
        private String templateCode;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
    }

    @Data
    public static class SeedQuery {
        private Long batchId;
        private Integer year;
        private String templateCode;
        private LocalDate startDate;
        private LocalDate endDate;
    }
}
