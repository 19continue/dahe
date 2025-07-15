package com.dahe.v2.modules.export.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dahe.v2.modules.export.mapper.ExportFieldDictMapper;
import com.dahe.v2.modules.export.model.ExportFieldDict;
import com.dahe.v2.modules.export.service.ExportFieldDictService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 导出字段词典服务实现。
 */
@Service
public class ExportFieldDictServiceImpl extends ServiceImpl<ExportFieldDictMapper, ExportFieldDict> implements ExportFieldDictService {

    @Override
    public Page<ExportFieldDict> pageRows(String moduleKey, String keyword, long page, long pageSize) {
        Page<ExportFieldDict> out = new Page<ExportFieldDict>(page, pageSize);
        LambdaQueryWrapper<ExportFieldDict> qw = new LambdaQueryWrapper<ExportFieldDict>();
        if (StringUtils.hasText(moduleKey)) {
            qw.eq(ExportFieldDict::getModuleKey, moduleKey.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String text = keyword.trim();
            qw.and(w -> w.like(ExportFieldDict::getFieldCode, text).or().like(ExportFieldDict::getFieldName, text));
        }
        qw.orderByAsc(ExportFieldDict::getModuleKey).orderByAsc(ExportFieldDict::getFieldCode);
        return this.page(out, qw);
    }

    @Override
    public Map<String, ExportFieldDict> mapByModuleAndCodes(String moduleKey, Collection<String> fieldCodes) {
        if (!StringUtils.hasText(moduleKey) || fieldCodes == null || fieldCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> codes = new ArrayList<String>();
        for (String code : fieldCodes) {
            if (StringUtils.hasText(code)) {
                codes.add(code.trim());
            }
        }
        if (codes.isEmpty()) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<ExportFieldDict> qw = new LambdaQueryWrapper<ExportFieldDict>();
        qw.eq(ExportFieldDict::getModuleKey, moduleKey.trim())
                .in(ExportFieldDict::getFieldCode, codes);
        List<ExportFieldDict> rows = this.list(qw);
        Map<String, ExportFieldDict> out = new HashMap<String, ExportFieldDict>();
        for (ExportFieldDict row : rows) {
            out.put(row.getFieldCode(), row);
        }
        return out;
    }
}
