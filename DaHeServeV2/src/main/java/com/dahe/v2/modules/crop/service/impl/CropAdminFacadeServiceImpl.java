package com.dahe.v2.modules.crop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.modules.assets.service.MediaAssetBindingService;
import com.dahe.v2.modules.crop.model.Crop;
import com.dahe.v2.modules.crop.service.CropAdminCommand;
import com.dahe.v2.modules.crop.service.CropAdminFacadeService;
import com.dahe.v2.modules.crop.service.CropNotFoundException;
import com.dahe.v2.modules.crop.service.CropService;
import com.dahe.v2.modules.crop.service.CropServiceException;
import com.dahe.v2.modules.miniapp.search.service.MiniappSearchIndexService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CropAdminFacadeServiceImpl implements CropAdminFacadeService {

    private static final String NODE_CATEGORY = "category";
    private static final String NODE_VARIETY = "variety";

    private final CropService cropService;
    private final JdbcTemplate jdbcTemplate;
    private final MiniappSearchIndexService miniappSearchIndexService;
    private final MediaAssetBindingService mediaAssetBindingService;

    public CropAdminFacadeServiceImpl(
            CropService cropService,
            JdbcTemplate jdbcTemplate,
            MiniappSearchIndexService miniappSearchIndexService,
            MediaAssetBindingService mediaAssetBindingService
    ) {
        this.cropService = cropService;
        this.jdbcTemplate = jdbcTemplate;
        this.miniappSearchIndexService = miniappSearchIndexService;
        this.mediaAssetBindingService = mediaAssetBindingService;
    }

    @Override
    public Page<Crop> page(CropAdminCommand.PageQuery query) {
        CropAdminCommand.PageQuery safeQuery = query == null ? new CropAdminCommand.PageQuery() : query;
        long page = safeQuery.getPage() > 0 ? safeQuery.getPage() : 1L;
        long pageSize = safeQuery.getPageSize() > 0 ? safeQuery.getPageSize() : 10L;
        String nodeType = normalizeNodeType(safeQuery.getNodeType(), false);
        return cropService.pageCrops(safeQuery.getKeyword(), nodeType, safeQuery.getParentId(), page, pageSize);
    }

    @Override
    public List<TreeCategoryItem> tree(String keyword) {
        String key = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : "";
        List<Crop> categories = cropService.listCategories();
        if (categories.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, List<Crop>> varietyByCategory = cropService.listVarietiesByCategories(categories);

        List<TreeCategoryItem> out = new ArrayList<TreeCategoryItem>();
        for (Crop category : categories) {
            if (category == null || category.getId() == null) {
                continue;
            }
            List<TreeVarietyItem> varietyItems = varietyByCategory.getOrDefault(category.getId(), Collections.<Crop>emptyList())
                    .stream()
                    .filter(x -> StringUtils.hasText(x.getVariety()))
                    .map(this::toVarietyItem)
                    .collect(Collectors.toList());
            boolean hitByCategory = containsIgnoreCase(category.getName(), key);
            if (!key.isEmpty() && !hitByCategory) {
                varietyItems = varietyItems.stream()
                        .filter(x -> containsIgnoreCase(x.getName(), key))
                        .collect(Collectors.toList());
            }
            if (!key.isEmpty() && !hitByCategory && varietyItems.isEmpty()) {
                continue;
            }
            TreeCategoryItem item = new TreeCategoryItem();
            item.setId(category.getId());
            item.setName(category.getName());
            item.setImageUrl(category.getImageUrl());
            item.setSortOrder(category.getSortOrder());
            item.setVarieties(varietyItems);
            out.add(item);
        }
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Crop create(CropAdminCommand.Create command) {
        CropAdminCommand.Create safeCommand = command == null ? new CropAdminCommand.Create() : command;
        String nodeType = normalizeNodeType(safeCommand.getNodeType(), !StringUtils.hasText(safeCommand.getVariety()));
        if (NODE_CATEGORY.equals(nodeType)) {
            return createCategory(safeCommand);
        }
        return createVariety(safeCommand);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Crop update(Long id, CropAdminCommand.Update command) {
        Crop row = cropService.getById(id);
        if (row == null) {
            throw new CropNotFoundException("作物节点不存在");
        }
        CropAdminCommand.Update safeCommand = command == null ? new CropAdminCommand.Update() : command;
        String nodeType = normalizeNodeType(row.getNodeType(), false);
        if (NODE_CATEGORY.equals(nodeType)) {
            return updateCategory(row, safeCommand);
        }
        return updateVariety(row, safeCommand);
    }

    @Override
    public void reorder(CropAdminCommand.Reorder command) {
        CropAdminCommand.Reorder safeCommand = command == null ? new CropAdminCommand.Reorder() : command;
        if (safeCommand.getIds() == null || safeCommand.getIds().isEmpty()) {
            throw validation("编号列表不能为空");
        }
        String nodeType = requireNodeType(safeCommand.getNodeType());
        cropService.reorder(safeCommand.getIds(), nodeType, safeCommand.getParentId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        Crop row = cropService.getById(id);
        if (row == null) {
            throw new CropNotFoundException("作物节点不存在");
        }
        String nodeType = normalizeNodeType(row.getNodeType(), false);
        if (NODE_CATEGORY.equals(nodeType)) {
            return deleteCategory(row);
        }
        assertNotReferencedByTemplate(Collections.singletonList(row.getId()));
        clearCropImageBinding(row);
        boolean removed = cropService.removeById(row.getId());
        if (removed) {
            miniappSearchIndexService.removeCrop(row.getId());
        }
        return removed;
    }

    private Crop createCategory(CropAdminCommand.Create command) {
        String name = safeText(command.getName());
        if (!StringUtils.hasText(name)) {
            throw validation("分类名称不能为空");
        }
        if (findCategoryByName(name) != null) {
            throw validation("分类已存在");
        }
        Crop row = new Crop();
        row.setName(name);
        row.setVariety(null);
        row.setNodeType(NODE_CATEGORY);
        row.setParentId(null);
        row.setImageUrl(safeText(command.getImageUrl()));
        row.setSortOrder(cropService.nextSortOrder(NODE_CATEGORY, null));
        try {
            cropService.save(row);
            syncCropImageBinding(row);
            miniappSearchIndexService.syncCrop(row);
            return row;
        } catch (DuplicateKeyException ex) {
            throw validation("分类已存在");
        }
    }

    private Crop createVariety(CropAdminCommand.Create command) {
        Crop category = resolveCategory(command.getParentId(), command.getName(), true);
        if (category == null || category.getId() == null) {
            throw validation("分类不能为空");
        }
        String varietyName = safeText(command.getVariety());
        if (!StringUtils.hasText(varietyName)) {
            throw validation("品种名称不能为空");
        }
        if (existsVariety(category.getId(), varietyName, null)) {
            throw validation("该分类下品种已存在");
        }
        Crop row = new Crop();
        row.setName(category.getName());
        row.setVariety(varietyName);
        row.setNodeType(NODE_VARIETY);
        row.setParentId(category.getId());
        row.setImageUrl(safeText(command.getImageUrl()));
        row.setSortOrder(cropService.nextSortOrder(NODE_VARIETY, category.getId()));
        try {
            cropService.save(row);
            syncCropImageBinding(row);
            miniappSearchIndexService.syncCrop(row);
            return row;
        } catch (DuplicateKeyException ex) {
            throw validation("该分类下品种已存在");
        }
    }

    private Crop updateCategory(Crop row, CropAdminCommand.Update command) {
        String oldName = safeText(row.getName());
        String newName = safeText(command.getName());
        if (!StringUtils.hasText(newName)) {
            throw validation("分类名称不能为空");
        }
        Crop sameName = findCategoryByName(newName);
        if (sameName != null && !Objects.equals(sameName.getId(), row.getId())) {
            throw validation("分类已存在");
        }
        List<Long> affectedCropIds = new ArrayList<Long>();
        affectedCropIds.add(row.getId());
        for (Crop child : cropService.list(new LambdaQueryWrapper<Crop>()
                .select(Crop::getId)
                .eq(Crop::getNodeType, NODE_VARIETY)
                .eq(Crop::getParentId, row.getId()))) {
            if (child != null && child.getId() != null) {
                affectedCropIds.add(child.getId());
            }
        }
        row.setName(newName);
        row.setImageUrl(safeText(command.getImageUrl()));
        if (command.getSortOrder() != null && command.getSortOrder() > 0) {
            row.setSortOrder(command.getSortOrder());
        }
        try {
            cropService.updateById(row);
            syncCropImageBinding(row);
        } catch (DuplicateKeyException ex) {
            throw validation("分类已存在");
        }

        cropService.update(new Crop(), new LambdaUpdateWrapper<Crop>()
                .eq(Crop::getNodeType, NODE_VARIETY)
                .eq(Crop::getParentId, row.getId())
                .set(Crop::getName, newName));
        miniappSearchIndexService.rebuildCropsByIds(affectedCropIds);
        if (StringUtils.hasText(oldName) && !oldName.equals(newName)) {
            syncCategoryRename(oldName, newName);
            miniappSearchIndexService.rebuildFieldsByCrop(newName, null);
            miniappSearchIndexService.rebuildSeedBatchesByCrop(newName, null);
        }
        return row;
    }

    private Crop updateVariety(Crop row, CropAdminCommand.Update command) {
        String oldCategory = safeText(row.getName());
        String oldVariety = safeText(row.getVariety());

        Crop category = resolveCategory(command.getParentId(), command.getName(), false);
        if (category == null || category.getId() == null) {
            category = row.getParentId() == null ? null : cropService.getById(row.getParentId());
        }
        if (category == null || category.getId() == null
                || !NODE_CATEGORY.equals(normalizeNodeType(category.getNodeType(), false))) {
            throw validation("请选择有效分类");
        }

        String varietyName = safeText(command.getVariety());
        if (!StringUtils.hasText(varietyName)) {
            varietyName = oldVariety;
        }
        if (!StringUtils.hasText(varietyName)) {
            throw validation("品种名称不能为空");
        }
        if (existsVariety(category.getId(), varietyName, row.getId())) {
            throw validation("该分类下品种已存在");
        }

        row.setName(category.getName());
        row.setVariety(varietyName);
        row.setNodeType(NODE_VARIETY);
        row.setParentId(category.getId());
        row.setImageUrl(safeText(command.getImageUrl()));
        if (command.getSortOrder() != null && command.getSortOrder() > 0) {
            row.setSortOrder(command.getSortOrder());
        }
        try {
            cropService.updateById(row);
            syncCropImageBinding(row);
        } catch (DuplicateKeyException ex) {
            throw validation("该分类下品种已存在");
        }
        miniappSearchIndexService.syncCrop(row);
        syncVarietyRename(oldCategory, oldVariety, row.getName(), row.getVariety());
        miniappSearchIndexService.rebuildFieldsByCrop(row.getName(), row.getVariety());
        miniappSearchIndexService.rebuildSeedBatchesByCrop(row.getName(), row.getVariety());
        return row;
    }

    private boolean deleteCategory(Crop category) {
        List<Crop> children = cropService.list(new LambdaQueryWrapper<Crop>()
                .eq(Crop::getNodeType, NODE_VARIETY)
                .eq(Crop::getParentId, category.getId()));
        List<Long> targetIds = new ArrayList<Long>();
        targetIds.add(category.getId());
        List<Long> childIds = new ArrayList<Long>();
        for (Crop child : children) {
            if (child != null && child.getId() != null) {
                childIds.add(child.getId());
                targetIds.add(child.getId());
            }
        }
        assertNotReferencedByTemplate(targetIds);
        if (!childIds.isEmpty()) {
            for (Crop child : children) {
                clearCropImageBinding(child);
            }
            cropService.removeByIds(childIds);
        }
        clearCropImageBinding(category);
        boolean removed = cropService.removeById(category.getId());
        if (removed) {
            miniappSearchIndexService.removeCrop(category.getId());
            for (Long childId : childIds) {
                miniappSearchIndexService.removeCrop(childId);
            }
        }
        return removed;
    }

    private void syncCropImageBinding(Crop row) {
        if (row == null || row.getId() == null) {
            return;
        }
        mediaAssetBindingService.bindByUrls(resolveCropModuleKey(row), row.getId(), Collections.singletonList(row.getImageUrl()));
    }

    private void clearCropImageBinding(Crop row) {
        if (row == null || row.getId() == null) {
            return;
        }
        mediaAssetBindingService.clearBinding(resolveCropModuleKey(row), row.getId());
    }

    private String resolveCropModuleKey(Crop row) {
        String nodeType = normalizeNodeType(row == null ? null : row.getNodeType(), false);
        return NODE_CATEGORY.equals(nodeType) ? "crop_category" : "crop_variety";
    }
    private void assertNotReferencedByTemplate(List<Long> cropIds) {
        if (cropIds == null || cropIds.isEmpty()) {
            return;
        }
        Set<Long> cleanIds = new LinkedHashSet<Long>();
        for (Long cropId : cropIds) {
            if (cropId != null && cropId > 0) {
                cleanIds.add(cropId);
            }
        }
        if (cleanIds.isEmpty()) {
            return;
        }
        List<Object> args = new ArrayList<Object>(cleanIds);
        String placeholders = String.join(",", Collections.nCopies(cleanIds.size(), "?"));
        String sql = "SELECT COUNT(*) FROM `farm_process_template` WHERE `deleted`=0 AND `crop_id` IN (" + placeholders + ")";
        Long refCount = jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
        if (refCount != null && refCount > 0) {
            throw validation("作物已被流程模板引用，无法删除");
        }
    }

    private Crop resolveCategory(Long parentId, String categoryName, boolean autoCreate) {
        if (parentId != null) {
            Crop category = cropService.getById(parentId);
            if (category != null && NODE_CATEGORY.equals(normalizeNodeType(category.getNodeType(), false))) {
                return category;
            }
            return null;
        }
        String name = safeText(categoryName);
        if (!StringUtils.hasText(name)) {
            return null;
        }
        Crop existed = findCategoryByName(name);
        if (existed != null) {
            return existed;
        }
        if (!autoCreate) {
            return null;
        }
        Crop created = new Crop();
        created.setName(name);
        created.setVariety(null);
        created.setNodeType(NODE_CATEGORY);
        created.setParentId(null);
        created.setImageUrl(null);
        created.setSortOrder(cropService.nextSortOrder(NODE_CATEGORY, null));
        try {
            cropService.save(created);
            miniappSearchIndexService.syncCrop(created);
            return created;
        } catch (DuplicateKeyException ex) {
            Crop category = findCategoryByName(name);
            if (category != null) {
                return category;
            }
            throw validation("分类已存在");
        }
    }

    private Crop findCategoryByName(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        String normalized = name.trim();
        return cropService.listCategories().stream()
                .filter(x -> normalized.equals(safeText(x == null ? null : x.getName())))
                .findFirst()
                .orElse(null);
    }

    private boolean existsVariety(Long categoryId, String varietyName, Long excludeId) {
        if (categoryId == null || !StringUtils.hasText(varietyName)) {
            return false;
        }
        String normalized = varietyName.trim();
        return cropService.listVarietiesByCategory(categoryId).stream()
                .filter(x -> excludeId == null || !Objects.equals(x == null ? null : x.getId(), excludeId))
                .anyMatch(x -> normalized.equals(safeText(x == null ? null : x.getVariety())));
    }

    private void syncCategoryRename(String oldName, String newName) {
        jdbcTemplate.update("UPDATE `field` SET `crop_type`=? WHERE `crop_type`=?", newName, oldName);
        jdbcTemplate.update("UPDATE `seed_batch` SET `crop_type`=? WHERE `crop_type`=?", newName, oldName);
    }

    private void syncVarietyRename(String oldCategory, String oldVariety, String newCategory, String newVariety) {
        if (!StringUtils.hasText(oldCategory) || !StringUtils.hasText(oldVariety)) {
            return;
        }
        if (Objects.equals(oldCategory, newCategory) && Objects.equals(oldVariety, newVariety)) {
            return;
        }
        jdbcTemplate.update(
                "UPDATE `field` SET `crop_type`=?, `crop_variety`=? WHERE `crop_type`=? AND `crop_variety`=?",
                newCategory,
                newVariety,
                oldCategory,
                oldVariety
        );
        jdbcTemplate.update(
                "UPDATE `seed_batch` SET `crop_type`=?, `variety_name`=? WHERE `crop_type`=? AND `variety_name`=?",
                newCategory,
                newVariety,
                oldCategory,
                oldVariety
        );
    }

    private TreeVarietyItem toVarietyItem(Crop row) {
        TreeVarietyItem item = new TreeVarietyItem();
        item.setId(row.getId());
        item.setParentId(row.getParentId());
        item.setName(row.getVariety());
        item.setImageUrl(row.getImageUrl());
        item.setSortOrder(row.getSortOrder());
        return item;
    }

    private String normalizeNodeType(String nodeType, boolean inferCategoryWhenBlankVariety) {
        String raw = String.valueOf(nodeType == null ? "" : nodeType).trim().toLowerCase(Locale.ROOT);
        if (NODE_CATEGORY.equals(raw)) {
            return NODE_CATEGORY;
        }
        if (NODE_VARIETY.equals(raw)) {
            return NODE_VARIETY;
        }
        return inferCategoryWhenBlankVariety ? NODE_CATEGORY : NODE_VARIETY;
    }

    private String requireNodeType(String nodeType) {
        String raw = String.valueOf(nodeType == null ? "" : nodeType).trim().toLowerCase(Locale.ROOT);
        if (NODE_CATEGORY.equals(raw)) {
            return NODE_CATEGORY;
        }
        if (NODE_VARIETY.equals(raw)) {
            return NODE_VARIETY;
        }
        throw validation("节点类型无效");
    }

    private String safeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private boolean containsIgnoreCase(String text, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String source = String.valueOf(text == null ? "" : text).toLowerCase(Locale.ROOT);
        return source.contains(keyword);
    }

    private CropServiceException validation(String message) {
        return new CropServiceException(ErrorCode.VALIDATION_ERROR.getCode(), message);
    }
}
