package com.dahe.v2.modules.export.service;

import javax.servlet.http.HttpServletResponse;

/**
 * 导出 CSV 门面服务。
 */
public interface ExportCsvFacadeService {

    Object exportFarmRecordsCsv(ExportCsvCommand.FarmQuery query, HttpServletResponse response);

    Object exportSeedTestsCsv(ExportCsvCommand.SeedQuery query, HttpServletResponse response);
}
