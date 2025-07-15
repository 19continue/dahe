package com.dahe.v2.modules.export.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dahe.v2.modules.export.model.ExportFieldDict;

import java.util.Collection;
import java.util.Map;

/**
 * 导出字段词典服务接口。
 */
public interface ExportFieldDictService extends IService<ExportFieldDict> {

    Page<ExportFieldDict> pageRows(String moduleKey, String keyword, long page, long pageSize);

    Map<String, ExportFieldDict> mapByModuleAndCodes(String moduleKey, Collection<String> fieldCodes);
}
