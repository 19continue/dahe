package com.dahe.v2.modules.meta.terminology.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.meta.terminology.model.TerminologyDictEntry;
import com.dahe.v2.modules.meta.terminology.service.TerminologyDictService;
import lombok.Data;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/v2/admin/terminology-dict")
@Validated
public class AdminTerminologyDictController {

    private static final int MAX_SOURCE_LEN = 120;
    private static final int MAX_TARGET_LEN = 255;
    private static final int MAX_REPLACE_SIZE = 5000;

    private final TerminologyDictService terminologyDictService;

    public AdminTerminologyDictController(TerminologyDictService terminologyDictService) {
        this.terminologyDictService = terminologyDictService;
    }

    @GetMapping
    public Result<Page<Map<String, Object>>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) long pageSize
    ) {
        return execute(() -> {
            Page<TerminologyDictEntry> sourcePage = terminologyDictService.pageRows(keyword, page, pageSize);
            Page<Map<String, Object>> out = new Page<Map<String, Object>>(sourcePage.getCurrent(), sourcePage.getSize(), sourcePage.getTotal());
            List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
            for (TerminologyDictEntry row : sourcePage.getRecords()) {
                rows.add(toView(row));
            }
            out.setRecords(rows);
            return out;
        });
    }

    @GetMapping("/all")
    public Result<List<Map<String, Object>>> all(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "5000") @Min(1) int limit
    ) {
        return execute(() -> {
            List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
            for (TerminologyDictEntry row : terminologyDictService.listRows(keyword, limit)) {
                out.add(toView(row));
            }
            return out;
        });
    }

    @GetMapping("/sync-meta")
    public Result<Map<String, Object>> syncMeta(@RequestParam(required = false) String version) {
        return execute(() -> {
            long count = terminologyDictService.count();
            LambdaQueryWrapper<TerminologyDictEntry> qw = new LambdaQueryWrapper<TerminologyDictEntry>();
            qw.select(TerminologyDictEntry::getId, TerminologyDictEntry::getUpdatedAt)
                    .orderByDesc(TerminologyDictEntry::getUpdatedAt)
                    .orderByDesc(TerminologyDictEntry::getId)
                    .last("limit 1");
            TerminologyDictEntry latest = terminologyDictService.getOne(qw, false);
            String currentVersion = buildSyncVersion(count, latest);

            Map<String, Object> out = new LinkedHashMap<String, Object>();
            out.put("count", count);
            out.put("latestUpdatedAt", latest == null ? null : latest.getUpdatedAt());
            out.put("version", currentVersion);
            out.put("changed", !StringUtils.hasText(version) || !currentVersion.equals(version.trim()));
            return out;
        });
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody @Valid SaveReq req) {
        return execute(() -> {
            String source = normalizeText(req.getSource());
            String target = normalizeText(req.getTarget());
            String validationMessage = validateEntry(source, target);
            if (StringUtils.hasText(validationMessage)) {
                throw new IllegalArgumentException(validationMessage);
            }
            TerminologyDictEntry row = terminologyDictService.createRow(
                    source,
                    target,
                    normalizeSortOrder(req.getSortOrder())
            );
            return toView(row);
        });
    }

    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@PathVariable Long id, @RequestBody @Valid SaveReq req) {
        return execute(() -> {
            String source = normalizeText(req.getSource());
            String target = normalizeText(req.getTarget());
            String validationMessage = validateEntry(source, target);
            if (StringUtils.hasText(validationMessage)) {
                throw new IllegalArgumentException(validationMessage);
            }
            TerminologyDictEntry row = terminologyDictService.updateRow(
                    id,
                    source,
                    target,
                    normalizeSortOrder(req.getSortOrder())
            );
            return toView(row);
        });
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        return execute(() -> {
            terminologyDictService.removeById(id);
            return null;
        });
    }

    @PostMapping("/batch-delete")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> batchDelete(@RequestBody BatchDeleteReq req) {
        return execute(() -> {
            List<Long> ids = sanitizeIdList(req == null ? null : req.getIds());
            if (ids.isEmpty()) {
                throw new IllegalArgumentException("编号列表不能为空");
            }
            terminologyDictService.removeByIds(ids);
            Map<String, Object> out = new LinkedHashMap<String, Object>();
            out.put("deletedCount", ids.size());
            return out;
        });
    }

    @PostMapping("/replace-all")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> replaceAll(@RequestBody ReplaceAllReq req) {
        return execute(() -> {
            List<TerminologyDictEntry> rows = normalizeEntries(req == null ? null : req.getEntries());
            int count = terminologyDictService.replaceAllRows(rows);
            Map<String, Object> out = new LinkedHashMap<String, Object>();
            out.put("count", count);
            return out;
        });
    }

    private <T> Result<T> execute(Supplier<T> supplier) {
        try {
            return Result.success(supplier.get());
        } catch (java.util.NoSuchElementException ex) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ErrorCode.NOT_FOUND.getMessage());
        } catch (IllegalArgumentException ex) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), ex.getMessage());
        } catch (Exception ex) {
            return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage());
        }
    }

    private List<Long> sanitizeIdList(List<Long> ids) {
        List<Long> out = new ArrayList<Long>();
        if (ids == null) {
            return out;
        }
        Set<Long> used = new LinkedHashSet<Long>();
        for (Long id : ids) {
            if (id == null || id <= 0 || used.contains(id)) {
                continue;
            }
            used.add(id);
            out.add(id);
        }
        return out;
    }

    private List<TerminologyDictEntry> normalizeEntries(List<ImportEntryReq> entries) {
        List<TerminologyDictEntry> out = new ArrayList<TerminologyDictEntry>();
        if (entries == null) {
            return out;
        }
        Set<String> usedSource = new LinkedHashSet<String>();
        for (ImportEntryReq item : entries) {
            String source = normalizeText(item == null ? null : item.getSource());
            String target = normalizeText(item == null ? null : item.getTarget());
            if (!StringUtils.hasText(source) || !StringUtils.hasText(target)) {
                continue;
            }
            if (source.length() > MAX_SOURCE_LEN || target.length() > MAX_TARGET_LEN) {
                continue;
            }
            if (usedSource.contains(source)) {
                continue;
            }
            usedSource.add(source);
            TerminologyDictEntry row = new TerminologyDictEntry();
            row.setSourceText(source);
            row.setTargetText(target);
            row.setSortOrder(normalizeSortOrder(item == null ? null : item.getSortOrder()));
            out.add(row);
            if (out.size() >= MAX_REPLACE_SIZE) {
                break;
            }
        }
        return out;
    }

    private String validateEntry(String source, String target) {
        if (!StringUtils.hasText(source)) {
            return "源术语不能为空";
        }
        if (!StringUtils.hasText(target)) {
            return "目标术语不能为空";
        }
        if (source.length() > MAX_SOURCE_LEN) {
            return "源术语长度不能超过120个字符";
        }
        if (target.length() > MAX_TARGET_LEN) {
            return "目标术语长度不能超过255个字符";
        }
        return null;
    }

    private int normalizeSortOrder(Integer sortOrder) {
        if (sortOrder == null) {
            return 0;
        }
        return Math.max(0, Math.min(99999, sortOrder));
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String buildSyncVersion(long count, TerminologyDictEntry latest) {
        LocalDateTime updatedAt = latest == null ? null : latest.getUpdatedAt();
        String updatedAtText = updatedAt == null ? "0" : updatedAt.toString();
        String latestId = latest == null || latest.getId() == null ? "0" : String.valueOf(latest.getId());
        return count + ":" + updatedAtText + ":" + latestId;
    }

    private Map<String, Object> toView(TerminologyDictEntry row) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("id", row.getId());
        out.put("source", row.getSourceText());
        out.put("target", row.getTargetText());
        out.put("sortOrder", row.getSortOrder());
        out.put("createdAt", row.getCreatedAt());
        out.put("updatedAt", row.getUpdatedAt());
        return out;
    }

    @Data
    public static class SaveReq {
        @NotBlank(message = "源术语不能为空")
        private String source;
        @NotBlank(message = "目标术语不能为空")
        private String target;
        private Integer sortOrder;
    }

    @Data
    public static class BatchDeleteReq {
        private List<Long> ids;
    }

    @Data
    public static class ReplaceAllReq {
        private List<ImportEntryReq> entries;
    }

    @Data
    public static class ImportEntryReq {
        private String source;
        private String target;
        private Integer sortOrder;
    }
}
