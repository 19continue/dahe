package com.dahe.v2.modules.meta.terminology.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dahe.v2.modules.meta.terminology.model.TerminologyDictEntry;

import java.util.List;

public interface TerminologyDictService extends IService<TerminologyDictEntry> {

    /** 分页查询词典条目。 */
    Page<TerminologyDictEntry> pageRows(String keyword, long page, long pageSize);

    /** 按关键词查询词典条目列表（带上限）。 */
    List<TerminologyDictEntry> listRows(String keyword, int limit);

    /** 判断源术语是否已存在（可排除当前记录）。 */
    boolean existsSourceText(String sourceText, Long excludeId);

    /** 新增术语条目。 */
    TerminologyDictEntry createRow(String sourceText, String targetText, Integer sortOrder);

    /** 更新术语条目。 */
    TerminologyDictEntry updateRow(Long id, String sourceText, String targetText, Integer sortOrder);

    /** 全量替换（增量写入+差异删除，避免先删后插）。 */
    int replaceAllRows(List<TerminologyDictEntry> rows);
}
