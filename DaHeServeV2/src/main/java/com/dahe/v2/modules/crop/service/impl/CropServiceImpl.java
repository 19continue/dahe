package com.dahe.v2.modules.crop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dahe.v2.modules.crop.mapper.CropMapper;
import com.dahe.v2.modules.crop.model.Crop;
import com.dahe.v2.modules.crop.service.CropService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.Locale;

@Service
/**
 * 作物层级服务实现。
 * 负责分页查询、排序号计算与节点重排。
 */
public class CropServiceImpl extends ServiceImpl<CropMapper, Crop> implements CropService {

    private final JdbcTemplate jdbcTemplate;
    private final Object hierarchyRepairLock = new Object();

    public CropServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    /** 分页查询作物节点。 */
    public Page<Crop> pageCrops(String keyword, String nodeType, Long parentId, long page, long pageSize) {
        ensureHierarchyReady();
        String type = normalizeNodeType(nodeType);
        if ("variety".equals(type) && parentId != null) {
            return pageVarietiesByCategory(keyword, parentId, page, pageSize);
        }
        Page<Crop> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<Crop> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(Crop::getName, keyword).or().like(Crop::getVariety, keyword));
        }
        qw.eq(Crop::getNodeType, type);
        qw.orderByAsc(Crop::getSortOrder).orderByDesc(Crop::getCreatedAt);
        return this.page(p, qw);
    }

    @Override
    public List<Crop> listCategories() {
        ensureHierarchyReady();
        LambdaQueryWrapper<Crop> qw = new LambdaQueryWrapper<Crop>();
        qw.eq(Crop::getNodeType, "category")
                .isNotNull(Crop::getName)
                .orderByAsc(Crop::getSortOrder)
                .orderByAsc(Crop::getName)
                .orderByDesc(Crop::getId);
        return this.list(qw);
    }

    @Override
    /** 计算指定节点范围内的下一个排序值。 */
    public int nextSortOrder(String nodeType, Long parentId) {
        ensureHierarchyReady();
        LambdaQueryWrapper<Crop> qw = new LambdaQueryWrapper<>();
        String type = normalizeNodeType(nodeType);
        qw.eq(Crop::getNodeType, type);
        if ("variety".equals(type) && parentId != null) {
            qw.eq(Crop::getParentId, parentId);
        }
        qw.orderByDesc(Crop::getSortOrder).last("limit 1");
        Crop top = this.getOne(qw, false);
        if (top == null || top.getSortOrder() == null || top.getSortOrder() < 0) {
            return 1;
        }
        return top.getSortOrder() + 1;
    }

    @Override
    @Transactional
    /** 按给定顺序重排节点，未传入节点保持原相对顺序并追加在后。 */
    public void reorder(List<Long> ids, String nodeType, Long parentId) {
        ensureHierarchyReady();
        String type = normalizeNodeType(nodeType);
        LambdaQueryWrapper<Crop> scopeQw = new LambdaQueryWrapper<>();
        scopeQw.eq(Crop::getNodeType, type);
        if ("variety".equals(type) && parentId != null) {
            scopeQw.eq(Crop::getParentId, parentId);
        }
        scopeQw.orderByAsc(Crop::getSortOrder).orderByDesc(Crop::getCreatedAt);
        List<Crop> all = this.list(scopeQw);
        if (all.isEmpty()) {
            return;
        }
        Map<Long, Crop> byId = new HashMap<>();
        for (Crop row : all) {
            if (row != null && row.getId() != null) {
                byId.put(row.getId(), row);
            }
        }
        List<Crop> ordered = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id == null || used.contains(id)) {
                    continue;
                }
                Crop row = byId.get(id);
                if (row == null) {
                    continue;
                }
                ordered.add(row);
                used.add(id);
            }
        }
        for (Crop row : all) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (used.contains(row.getId())) {
                continue;
            }
            ordered.add(row);
        }

        // 重建连续排序，避免重复/断档。
        int order = 1;
        for (Crop row : ordered) {
            if (row == null) {
                continue;
            }
            if (!Objects.equals(row.getSortOrder(), order)) {
                row.setSortOrder(order);
                this.updateById(row);
            }
            order += 1;
        }
    }

    @Override
    public List<Crop> listVarietiesByCategory(Long categoryId) {
        ensureHierarchyReady();
        if (categoryId == null) {
            return Collections.emptyList();
        }
        Crop category = this.getById(categoryId);
        if (!isCategory(category)) {
            return Collections.emptyList();
        }
        return listVarietiesByCategories(Collections.singletonList(category))
                .getOrDefault(categoryId, Collections.emptyList());
    }

    @Override
    public Map<Long, List<Crop>> listVarietiesByCategories(List<Crop> categories) {
        ensureHierarchyReady();
        if (categories == null || categories.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashMap<Long, Crop> categoryMap = new LinkedHashMap<>();
        LinkedHashMap<Long, List<Crop>> out = new LinkedHashMap<>();
        LinkedHashMap<String, Long> categoryIdByName = new LinkedHashMap<>();
        for (Crop row : categories) {
            if (!isCategory(row) || row.getId() == null) {
                continue;
            }
            categoryMap.put(row.getId(), row);
            out.put(row.getId(), new ArrayList<>());
            String categoryName = safeText(row.getName());
            if (StringUtils.hasText(categoryName)) {
                categoryIdByName.putIfAbsent(categoryName, row.getId());
            }
        }
        if (categoryMap.isEmpty()) {
            return Collections.emptyMap();
        }

        LambdaQueryWrapper<Crop> varietyQw = new LambdaQueryWrapper<>();
        varietyQw.eq(Crop::getNodeType, "variety")
                .and(scope -> {
                    scope.in(Crop::getParentId, categoryMap.keySet());
                    if (!categoryIdByName.isEmpty()) {
                        scope.or(legacy -> legacy.in(Crop::getName, categoryIdByName.keySet())
                                .and(parent -> parent.isNull(Crop::getParentId).or().eq(Crop::getParentId, 0L)));
                    }
                })
                .orderByAsc(Crop::getSortOrder)
                .orderByDesc(Crop::getCreatedAt)
                .orderByDesc(Crop::getId);
        List<Crop> rows = this.list(varietyQw);
        if (rows.isEmpty()) {
            return out;
        }

        Map<Long, LinkedHashMap<String, Crop>> deduped = new LinkedHashMap<>();
        for (Long categoryId : categoryMap.keySet()) {
            deduped.put(categoryId, new LinkedHashMap<>());
        }
        for (Crop row : rows) {
            Long categoryId = resolveVarietyCategoryId(row, categoryMap, categoryIdByName);
            if (categoryId == null) {
                continue;
            }
            String varietyName = safeText(row.getVariety());
            if (!StringUtils.hasText(varietyName)) {
                continue;
            }
            LinkedHashMap<String, Crop> bucket = deduped.get(categoryId);
            Crop existed = bucket.get(varietyName);
            if (existed == null || preferCurrentRow(row, existed, categoryId)) {
                bucket.put(varietyName, row);
            }
        }
        for (Map.Entry<Long, LinkedHashMap<String, Crop>> entry : deduped.entrySet()) {
            out.put(entry.getKey(), new ArrayList<>(entry.getValue().values()));
        }
        return out;
    }

    /** 节点类型归一化，默认按 variety 处理。 */
    private String normalizeNodeType(String nodeType) {
        String raw = StringUtils.hasText(nodeType) ? nodeType.trim().toLowerCase(Locale.ROOT) : "";
        if ("category".equals(raw)) {
            return "category";
        }
        return "variety";
    }

    /** 分类下的品种分页查询，兼容历史平铺数据。 */
    private Page<Crop> pageVarietiesByCategory(String keyword, Long parentId, long page, long pageSize) {
        List<Crop> rows = new ArrayList<>(listVarietiesByCategory(parentId));
        if (StringUtils.hasText(keyword)) {
            String key = keyword.trim().toLowerCase(Locale.ROOT);
            rows = rows.stream()
                    .filter(x -> containsIgnoreCase(x == null ? null : x.getVariety(), key)
                            || containsIgnoreCase(x == null ? null : x.getName(), key))
                    .collect(java.util.stream.Collectors.toList());
        }
        long safeCurrent = Math.max(page, 1L);
        long safeSize = Math.max(pageSize, 1L);
        int fromIndex = (int) Math.min((safeCurrent - 1L) * safeSize, rows.size());
        int toIndex = (int) Math.min(fromIndex + safeSize, rows.size());
        Page<Crop> out = new Page<>(safeCurrent, safeSize, rows.size());
        out.setRecords(fromIndex >= rows.size() ? Collections.emptyList() : rows.subList(fromIndex, toIndex));
        return out;
    }

    /** 是否为有效分类节点。 */
    private boolean isCategory(Crop row) {
        return row != null
                && row.getId() != null
                && "category".equals(normalizeNodeType(row.getNodeType()));
    }

    /** 解析品种所属分类。 */
    private Long resolveVarietyCategoryId(
            Crop row,
            Map<Long, Crop> categoryMap,
            Map<String, Long> categoryIdByName
    ) {
        if (row == null) {
            return null;
        }
        Long parentId = row.getParentId();
        if (parentId != null && categoryMap.containsKey(parentId)) {
            return parentId;
        }
        String categoryName = safeText(row.getName());
        if (!StringUtils.hasText(categoryName)) {
            return null;
        }
        return categoryIdByName.get(categoryName);
    }

    /** 当前行是否比既有行更应作为最终展示结果。 */
    private boolean preferCurrentRow(Crop current, Crop existed, Long categoryId) {
        boolean currentBound = current != null && Objects.equals(current.getParentId(), categoryId);
        boolean existedBound = existed != null && Objects.equals(existed.getParentId(), categoryId);
        return currentBound && !existedBound;
    }

    /** 安全文本清洗。 */
    private String safeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    /** 忽略大小写包含判断。 */
    private boolean containsIgnoreCase(String value, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String text = String.valueOf(value == null ? "" : value).toLowerCase(Locale.ROOT);
        return text.contains(keyword);
    }

    /** 必要时修复作物层级数据，使旧平铺数据也具备分类节点。 */
    private void ensureHierarchyReady() {
        if (!needsHierarchyRepair()) {
            return;
        }
        synchronized (hierarchyRepairLock) {
            if (!needsHierarchyRepair()) {
                return;
            }
            repairCropHierarchy();
        }
    }

    /** 判断当前库是否还存在未层级化的作物数据。 */
    private boolean needsHierarchyRepair() {
        Long categoryCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `crop` WHERE `deleted`=0 AND `node_type`='category'",
                Long.class
        );
        if (categoryCount == null || categoryCount <= 0) {
            return true;
        }
        Long detachedVarietyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `crop` " +
                        "WHERE `deleted`=0 " +
                        "AND (`node_type`='variety' OR `node_type` IS NULL OR TRIM(`node_type`)='') " +
                        "AND `variety` IS NOT NULL AND TRIM(`variety`)<>'' " +
                        "AND (`parent_id` IS NULL OR `parent_id`=0)",
                Long.class
        );
        return detachedVarietyCount != null && detachedVarietyCount > 0;
    }

    /** 将历史平铺 crop 数据修复为“分类 + 品种”层级结构。 */
    @Transactional(rollbackFor = Exception.class)
    protected void repairCropHierarchy() {
        jdbcTemplate.update(
                "UPDATE `crop` " +
                        "SET `node_type`='category', `parent_id`=NULL " +
                        "WHERE `deleted`=0 " +
                        "AND `name` IS NOT NULL AND TRIM(`name`)<>'' " +
                        "AND (`variety` IS NULL OR TRIM(`variety`)='')"
        );
        jdbcTemplate.update(
                "UPDATE `crop` " +
                        "SET `node_type`='variety' " +
                        "WHERE `deleted`=0 " +
                        "AND `variety` IS NOT NULL AND TRIM(`variety`)<>'' " +
                        "AND (`node_type` IS NULL OR TRIM(`node_type`)='' OR `node_type`<>'category')"
        );
        jdbcTemplate.update(
                "INSERT INTO `crop` (`id`,`name`,`variety`,`node_type`,`parent_id`,`image_url`,`sort_order`,`deleted`) " +
                        "SELECT UUID_SHORT(), base.`name`, NULL, 'category', NULL, NULL, base.`min_sort`, 0 " +
                        "FROM (" +
                        "  SELECT TRIM(`name`) AS `name`, COALESCE(MIN(`sort_order`), 0) AS `min_sort` " +
                        "  FROM `crop` " +
                        "  WHERE `deleted`=0 AND `name` IS NOT NULL AND TRIM(`name`)<>'' " +
                        "  GROUP BY TRIM(`name`)" +
                        ") base " +
                        "LEFT JOIN `crop` c " +
                        "  ON c.`deleted`=0 AND c.`node_type`='category' AND c.`name`=base.`name` " +
                        "WHERE c.`id` IS NULL"
        );
        jdbcTemplate.update(
                "UPDATE `crop` v " +
                        "JOIN `crop` c ON c.`deleted`=0 AND c.`node_type`='category' AND c.`name`=v.`name` " +
                        "SET v.`node_type`='variety', v.`parent_id`=c.`id` " +
                        "WHERE v.`deleted`=0 " +
                        "AND (`node_type`='variety' OR v.`node_type` IS NULL OR TRIM(v.`node_type`)='') " +
                        "AND v.`variety` IS NOT NULL AND TRIM(v.`variety`)<>'' " +
                        "AND (v.`parent_id` IS NULL OR v.`parent_id`=0)"
        );
    }
}

