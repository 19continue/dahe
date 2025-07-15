package com.dahe.v2.modules.meta.terminology.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dahe.v2.modules.meta.terminology.mapper.TerminologyDictMapper;
import com.dahe.v2.modules.meta.terminology.model.TerminologyDictEntry;
import com.dahe.v2.modules.meta.terminology.service.TerminologyDictService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TerminologyDictServiceImpl extends ServiceImpl<TerminologyDictMapper, TerminologyDictEntry>
        implements TerminologyDictService {

    @Override
    public Page<TerminologyDictEntry> pageRows(String keyword, long page, long pageSize) {
        LambdaQueryWrapper<TerminologyDictEntry> qw = buildQuery(keyword);
        qw.orderByAsc(TerminologyDictEntry::getSortOrder)
                .orderByDesc(TerminologyDictEntry::getUpdatedAt)
                .orderByDesc(TerminologyDictEntry::getId);
        return this.page(new Page<TerminologyDictEntry>(page, pageSize), qw);
    }

    @Override
    public List<TerminologyDictEntry> listRows(String keyword, int limit) {
        int safeLimit = Math.max(1, Math.min(5000, limit));
        LambdaQueryWrapper<TerminologyDictEntry> qw = buildQuery(keyword);
        qw.orderByAsc(TerminologyDictEntry::getSortOrder)
                .orderByDesc(TerminologyDictEntry::getUpdatedAt)
                .orderByDesc(TerminologyDictEntry::getId)
                .last("limit " + safeLimit);
        return this.list(qw);
    }

    @Override
    public boolean existsSourceText(String sourceText, Long excludeId) {
        String source = normalizeText(sourceText);
        if (!StringUtils.hasText(source)) {
            return false;
        }
        LambdaQueryWrapper<TerminologyDictEntry> qw = new LambdaQueryWrapper<TerminologyDictEntry>();
        qw.eq(TerminologyDictEntry::getSourceText, source);
        if (excludeId != null) {
            qw.ne(TerminologyDictEntry::getId, excludeId);
        }
        qw.last("limit 1");
        return this.getOne(qw, false) != null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TerminologyDictEntry createRow(String sourceText, String targetText, Integer sortOrder) {
        if (existsSourceText(sourceText, null)) {
            throw new IllegalArgumentException("源术语已存在");
        }
        TerminologyDictEntry row = new TerminologyDictEntry();
        row.setSourceText(sourceText);
        row.setTargetText(targetText);
        row.setSortOrder(sortOrder);
        try {
            this.save(row);
            return row;
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("源术语已存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TerminologyDictEntry updateRow(Long id, String sourceText, String targetText, Integer sortOrder) {
        TerminologyDictEntry row = this.getById(id);
        if (row == null) {
            throw new java.util.NoSuchElementException("术语不存在");
        }
        if (existsSourceText(sourceText, id)) {
            throw new IllegalArgumentException("源术语已存在");
        }
        row.setSourceText(sourceText);
        row.setTargetText(targetText);
        row.setSortOrder(sortOrder);
        try {
            this.updateById(row);
            return row;
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("源术语已存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int replaceAllRows(List<TerminologyDictEntry> rows) {
        List<TerminologyDictEntry> normalized = rows == null ? new ArrayList<TerminologyDictEntry>() : rows;
        Map<String, TerminologyDictEntry> incomingMap = new LinkedHashMap<String, TerminologyDictEntry>();
        for (TerminologyDictEntry row : normalized) {
            if (row == null || !StringUtils.hasText(row.getSourceText()) || !StringUtils.hasText(row.getTargetText())) {
                continue;
            }
            incomingMap.put(row.getSourceText().trim(), row);
        }

        List<TerminologyDictEntry> existingRows = this.list(new LambdaQueryWrapper<TerminologyDictEntry>()
                .orderByAsc(TerminologyDictEntry::getSortOrder)
                .orderByAsc(TerminologyDictEntry::getId));
        Map<String, TerminologyDictEntry> existingMap = new LinkedHashMap<String, TerminologyDictEntry>();
        for (TerminologyDictEntry row : existingRows) {
            if (row != null && StringUtils.hasText(row.getSourceText())) {
                existingMap.put(row.getSourceText().trim(), row);
            }
        }

        for (Map.Entry<String, TerminologyDictEntry> item : incomingMap.entrySet()) {
            String source = item.getKey();
            TerminologyDictEntry incoming = item.getValue();
            TerminologyDictEntry existed = existingMap.get(source);
            if (existed == null) {
                TerminologyDictEntry insert = new TerminologyDictEntry();
                insert.setSourceText(source);
                insert.setTargetText(incoming.getTargetText());
                insert.setSortOrder(incoming.getSortOrder());
                try {
                    this.save(insert);
                } catch (DuplicateKeyException ex) {
                    throw new IllegalArgumentException("源术语已存在：" + source);
                }
                continue;
            }
            boolean changed = !source.equals(existed.getSourceText())
                    || !equalsText(existed.getTargetText(), incoming.getTargetText())
                    || !equalsInt(existed.getSortOrder(), incoming.getSortOrder());
            if (!changed) {
                continue;
            }
            existed.setSourceText(source);
            existed.setTargetText(incoming.getTargetText());
            existed.setSortOrder(incoming.getSortOrder());
            this.updateById(existed);
        }

        Set<Long> staleIds = new LinkedHashSet<Long>();
        for (TerminologyDictEntry existed : existingRows) {
            if (existed == null || existed.getId() == null || !StringUtils.hasText(existed.getSourceText())) {
                continue;
            }
            if (!incomingMap.containsKey(existed.getSourceText().trim())) {
                staleIds.add(existed.getId());
            }
        }
        if (!staleIds.isEmpty()) {
            this.removeByIds(staleIds);
        }
        return incomingMap.size();
    }

    private LambdaQueryWrapper<TerminologyDictEntry> buildQuery(String keyword) {
        LambdaQueryWrapper<TerminologyDictEntry> qw = new LambdaQueryWrapper<TerminologyDictEntry>();
        String key = normalizeText(keyword);
        if (StringUtils.hasText(key)) {
            qw.and(w -> w.like(TerminologyDictEntry::getSourceText, key)
                    .or()
                    .like(TerminologyDictEntry::getTargetText, key));
        }
        return qw;
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean equalsText(String a, String b) {
        String left = a == null ? "" : a.trim();
        String right = b == null ? "" : b.trim();
        return left.equals(right);
    }

    private boolean equalsInt(Integer a, Integer b) {
        return (a == null ? 0 : a) == (b == null ? 0 : b);
    }
}
