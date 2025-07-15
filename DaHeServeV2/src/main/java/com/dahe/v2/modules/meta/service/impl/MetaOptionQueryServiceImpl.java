package com.dahe.v2.modules.meta.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dahe.v2.modules.crop.model.Crop;
import com.dahe.v2.modules.crop.service.CropService;
import com.dahe.v2.modules.field.cycle.model.FieldCropCycle;
import com.dahe.v2.modules.field.cycle.service.FieldCropCycleService;
import com.dahe.v2.modules.field.model.Field;
import com.dahe.v2.modules.field.model.FieldCropVarietyGroup;
import com.dahe.v2.modules.field.service.FieldCropVarietyGroupCodec;
import com.dahe.v2.modules.field.service.FieldService;
import com.dahe.v2.modules.meta.service.MetaOptionQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
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
public class MetaOptionQueryServiceImpl implements MetaOptionQueryService {

    private static final String NODE_CATEGORY = "category";
    private static final String NODE_VARIETY = "variety";
    private static final int DEFAULT_LIMIT = 5000;

    private final FieldService fieldService;
    private final FieldCropCycleService fieldCropCycleService;
    private final CropService cropService;
    private final ObjectMapper objectMapper;

    public MetaOptionQueryServiceImpl(
            FieldService fieldService,
            FieldCropCycleService fieldCropCycleService,
            CropService cropService,
            ObjectMapper objectMapper
    ) {
        this.fieldService = fieldService;
        this.fieldCropCycleService = fieldCropCycleService;
        this.cropService = cropService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<String> listTownships(String keyword, String province, String city, String district) {
        String safeKeyword = normalizeQueryText(keyword);
        String safeProvince = normalizeQueryText(province);
        String safeCity = normalizeQueryText(city);
        String safeDistrict = normalizeQueryText(district);
        LambdaQueryWrapper<Field> qw = new LambdaQueryWrapper<Field>();
        if (StringUtils.hasText(safeProvince)) {
            qw.like(Field::getProvince, safeProvince);
        }
        if (StringUtils.hasText(safeCity)) {
            qw.like(Field::getCity, safeCity);
        }
        if (StringUtils.hasText(safeDistrict)) {
            qw.like(Field::getDistrict, safeDistrict);
        }
        qw.isNotNull(Field::getTownship).orderByAsc(Field::getTownship).last("limit " + DEFAULT_LIMIT);
        List<Field> fields = fieldService.list(qw);
        return normalizeDistinct(
                fields.stream().map(Field::getTownship).collect(Collectors.toList()),
                safeKeyword
        );
    }

    @Override
    public List<String> listProvinces(String keyword) {
        String safeKeyword = normalizeQueryText(keyword);
        LambdaQueryWrapper<Field> qw = new LambdaQueryWrapper<Field>();
        qw.isNotNull(Field::getProvince).orderByAsc(Field::getProvince).last("limit " + DEFAULT_LIMIT);
        List<Field> fields = fieldService.list(qw);
        return normalizeDistinct(
                fields.stream().map(Field::getProvince).collect(Collectors.toList()),
                safeKeyword
        );
    }

    @Override
    public List<String> listCities(String keyword, String province) {
        String safeKeyword = normalizeQueryText(keyword);
        String safeProvince = normalizeQueryText(province);
        LambdaQueryWrapper<Field> qw = new LambdaQueryWrapper<Field>();
        if (StringUtils.hasText(safeProvince)) {
            qw.like(Field::getProvince, safeProvince);
        }
        qw.isNotNull(Field::getCity).orderByAsc(Field::getCity).last("limit " + DEFAULT_LIMIT);
        List<Field> fields = fieldService.list(qw);
        return normalizeDistinct(
                fields.stream().map(Field::getCity).collect(Collectors.toList()),
                safeKeyword
        );
    }

    @Override
    public List<String> listDistricts(String keyword, String province, String city) {
        String safeKeyword = normalizeQueryText(keyword);
        String safeProvince = normalizeQueryText(province);
        String safeCity = normalizeQueryText(city);
        LambdaQueryWrapper<Field> qw = new LambdaQueryWrapper<Field>();
        if (StringUtils.hasText(safeProvince)) {
            qw.like(Field::getProvince, safeProvince);
        }
        if (StringUtils.hasText(safeCity)) {
            qw.like(Field::getCity, safeCity);
        }
        qw.isNotNull(Field::getDistrict).orderByAsc(Field::getDistrict).last("limit " + DEFAULT_LIMIT);
        List<Field> fields = fieldService.list(qw);
        return normalizeDistinct(
                fields.stream().map(Field::getDistrict).collect(Collectors.toList()),
                safeKeyword
        );
    }

    @Override
    public List<String> listCrops(String keyword) {
        String safeKeyword = normalizeQueryText(keyword);
        Map<String, LinkedHashSet<String>> runtimeGroupMap = listRuntimeVarietyGroupMap();
        Set<String> values = new LinkedHashSet<String>();
        for (Crop row : listCategories()) {
            if (StringUtils.hasText(row.getName())) {
                values.add(row.getName().trim());
            }
        }
        values.addAll(runtimeGroupMap.keySet());
        if (values.isEmpty()) {
            LambdaQueryWrapper<Crop> qw = new LambdaQueryWrapper<Crop>();
            qw.isNotNull(Crop::getName).orderByAsc(Crop::getSortOrder).orderByAsc(Crop::getName).last("limit " + DEFAULT_LIMIT);
            for (Crop row : cropService.list(qw)) {
                if (StringUtils.hasText(row.getName())) {
                    values.add(row.getName().trim());
                }
            }
        }
        return filterByKeyword(values, safeKeyword);
    }

    @Override
    public List<String> listVarieties(String cropName, String keyword) {
        String safeCropName = normalizeQueryText(cropName);
        String safeKeyword = normalizeQueryText(keyword);
        Map<String, LinkedHashSet<String>> runtimeGroupMap = listRuntimeVarietyGroupMap();
        Set<String> values = new LinkedHashSet<String>();
        if (StringUtils.hasText(safeCropName)) {
            Crop category = findCategoryByName(safeCropName);
            if (category != null && category.getId() != null) {
                for (Crop row : cropService.listVarietiesByCategory(category.getId())) {
                    if (StringUtils.hasText(row.getVariety())) {
                        values.add(row.getVariety().trim());
                    }
                }
            } else {
                LambdaQueryWrapper<Crop> cropQw = new LambdaQueryWrapper<Crop>();
                cropQw.eq(Crop::getName, safeCropName)
                        .isNotNull(Crop::getVariety)
                        .orderByAsc(Crop::getSortOrder)
                        .orderByAsc(Crop::getVariety)
                        .last("limit " + DEFAULT_LIMIT);
                for (Crop row : cropService.list(cropQw)) {
                    if (StringUtils.hasText(row.getVariety())) {
                        values.add(row.getVariety().trim());
                    }
                }
            }
            values.addAll(runtimeGroupMap.getOrDefault(safeCropName, new LinkedHashSet<String>()));
        } else {
            LambdaQueryWrapper<Crop> qw = new LambdaQueryWrapper<Crop>();
            qw.eq(Crop::getNodeType, NODE_VARIETY)
                    .isNotNull(Crop::getVariety)
                    .orderByAsc(Crop::getSortOrder)
                    .orderByAsc(Crop::getVariety)
                    .last("limit " + DEFAULT_LIMIT);
            for (Crop row : cropService.list(qw)) {
                if (StringUtils.hasText(row.getVariety())) {
                    values.add(row.getVariety().trim());
                }
            }
            if (values.isEmpty()) {
                LambdaQueryWrapper<Crop> fallback = new LambdaQueryWrapper<Crop>();
                fallback.isNotNull(Crop::getVariety)
                        .orderByAsc(Crop::getSortOrder)
                        .orderByAsc(Crop::getVariety)
                        .last("limit " + DEFAULT_LIMIT);
                for (Crop row : cropService.list(fallback)) {
                    if (StringUtils.hasText(row.getVariety())) {
                        values.add(row.getVariety().trim());
                    }
                }
            }
            for (Set<String> runtimeVarieties : runtimeGroupMap.values()) {
                values.addAll(runtimeVarieties);
            }
        }
        return filterByKeyword(values, safeKeyword);
    }

    @Override
    public List<CropTreeItem> listCropTree(String keyword) {
        String key = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : "";
        List<Crop> categories = listCategories();
        if (categories.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, List<Crop>> varietyMap = cropService.listVarietiesByCategories(categories);

        List<CropTreeItem> out = new ArrayList<CropTreeItem>();
        for (Crop category : categories) {
            if (category == null || category.getId() == null) {
                continue;
            }
            List<CropVarietyItem> child = varietyMap.getOrDefault(category.getId(), Collections.<Crop>emptyList()).stream()
                    .map(this::toVarietyItem)
                    .collect(Collectors.toList());
            boolean matchCategory = containsIgnoreCase(category.getName(), key);
            if (!key.isEmpty() && !matchCategory) {
                child = child.stream()
                        .filter(x -> containsIgnoreCase(x.getName(), key))
                        .collect(Collectors.toList());
            }
            if (!key.isEmpty() && !matchCategory && child.isEmpty()) {
                continue;
            }
            CropTreeItem item = new CropTreeItem();
            item.setCategoryId(category.getId());
            item.setCategoryName(category.getName());
            item.setCategoryImageUrl(category.getImageUrl());
            item.setCategorySortOrder(category.getSortOrder());
            item.setVarieties(child);
            out.add(item);
        }
        return out;
    }

    @Override
    public List<VarietyGroupItem> listVarietyGroups(String keyword) {
        String key = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : "";
        Map<String, LinkedHashSet<String>> runtimeGroupMap = listRuntimeVarietyGroupMap();
        Map<String, LinkedHashSet<String>> grouped = new LinkedHashMap<String, LinkedHashSet<String>>();
        List<Crop> categories = listCategories();
        if (!categories.isEmpty()) {
            Map<Long, List<Crop>> varietyMap = cropService.listVarietiesByCategories(categories);
            for (Crop category : categories) {
                if (category == null || category.getId() == null || !StringUtils.hasText(category.getName())) {
                    continue;
                }
                String cropType = category.getName().trim();
                LinkedHashSet<String> bucket = grouped.computeIfAbsent(cropType, ignored -> new LinkedHashSet<String>());
                for (Crop variety : varietyMap.getOrDefault(category.getId(), Collections.<Crop>emptyList())) {
                    if (StringUtils.hasText(variety.getVariety())) {
                        bucket.add(variety.getVariety().trim());
                    }
                }
            }
        }
        mergeVarietyGroups(grouped, runtimeGroupMap);

        List<VarietyGroupItem> out = new ArrayList<VarietyGroupItem>();
        for (Map.Entry<String, LinkedHashSet<String>> entry : grouped.entrySet()) {
            String cropType = entry.getKey();
            List<String> varietyList = new ArrayList<String>(entry.getValue());
            boolean matchCategory = containsIgnoreCase(cropType, key);
            if (!key.isEmpty() && !matchCategory) {
                varietyList = varietyList.stream()
                        .filter(x -> containsIgnoreCase(cropType + " " + x, key))
                        .collect(Collectors.toList());
            }
            if (!key.isEmpty() && !matchCategory && varietyList.isEmpty()) {
                continue;
            }
            VarietyGroupItem item = new VarietyGroupItem();
            item.setCropType(cropType);
            item.setVarieties(varietyList);
            out.add(item);
        }
        return out;
    }

    private Map<String, LinkedHashSet<String>> listRuntimeVarietyGroupMap() {
        LinkedHashMap<String, LinkedHashSet<String>> out = new LinkedHashMap<String, LinkedHashSet<String>>();
        appendFieldGroups(out);
        appendCurrentPlanGroups(out);
        return out;
    }

    private void appendFieldGroups(Map<String, LinkedHashSet<String>> out) {
        LambdaQueryWrapper<Field> qw = new LambdaQueryWrapper<Field>();
        qw.select(Field::getCropType, Field::getCropVariety, Field::getCropVarietyGroupsJson)
                .eq(Field::getEnabled, 1)
                .orderByAsc(Field::getSortOrder)
                .orderByAsc(Field::getName)
                .last("limit " + DEFAULT_LIMIT);
        for (Field field : fieldService.list(qw)) {
            mergeGroupRows(out, resolveFieldGroups(field));
        }
    }

    private void appendCurrentPlanGroups(Map<String, LinkedHashSet<String>> out) {
        LambdaQueryWrapper<FieldCropCycle> qw = new LambdaQueryWrapper<FieldCropCycle>();
        qw.select(FieldCropCycle::getCropsJson)
                .eq(FieldCropCycle::getStatus, "active")
                .orderByDesc(FieldCropCycle::getIsCurrent)
                .orderByDesc(FieldCropCycle::getUpdatedAt)
                .last("limit " + DEFAULT_LIMIT);
        for (FieldCropCycle cycle : fieldCropCycleService.list(qw)) {
            mergeGroupRows(out, FieldCropVarietyGroupCodec.fromCycleCropsJson(objectMapper, cycle == null ? null : cycle.getCropsJson()));
        }
    }

    private List<FieldCropVarietyGroup> resolveFieldGroups(Field field) {
        if (field == null) {
            return Collections.emptyList();
        }
        List<FieldCropVarietyGroup> groups = FieldCropVarietyGroupCodec.fromFieldJson(objectMapper, field.getCropVarietyGroupsJson());
        if (!groups.isEmpty()) {
            return groups;
        }
        return FieldCropVarietyGroupCodec.fromLegacyTexts(field.getCropType(), field.getCropVariety());
    }

    private void mergeGroupRows(Map<String, LinkedHashSet<String>> out, List<FieldCropVarietyGroup> groups) {
        if (out == null || groups == null || groups.isEmpty()) {
            return;
        }
        for (FieldCropVarietyGroup group : groups) {
            String cropType = normalizeQueryText(group == null ? null : group.getCropType());
            String cropVariety = normalizeQueryText(group == null ? null : group.getCropVariety());
            if (!StringUtils.hasText(cropType)) {
                continue;
            }
            LinkedHashSet<String> bucket = out.computeIfAbsent(cropType, ignored -> new LinkedHashSet<String>());
            if (StringUtils.hasText(cropVariety)) {
                bucket.add(cropVariety);
            }
        }
    }

    private void mergeVarietyGroups(
            Map<String, LinkedHashSet<String>> target,
            Map<String, LinkedHashSet<String>> source
    ) {
        if (target == null || source == null || source.isEmpty()) {
            return;
        }
        for (Map.Entry<String, LinkedHashSet<String>> entry : source.entrySet()) {
            if (!StringUtils.hasText(entry.getKey())) {
                continue;
            }
            LinkedHashSet<String> bucket = target.computeIfAbsent(entry.getKey(), ignored -> new LinkedHashSet<String>());
            bucket.addAll(entry.getValue());
        }
    }

    private CropVarietyItem toVarietyItem(Crop row) {
        CropVarietyItem item = new CropVarietyItem();
        item.setId(row.getId());
        item.setCategoryId(row.getParentId());
        item.setName(row.getVariety());
        item.setImageUrl(row.getImageUrl());
        item.setSortOrder(row.getSortOrder());
        return item;
    }

    private List<Crop> listCategories() {
        return cropService.listCategories();
    }

    private Crop findCategoryByName(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        LambdaQueryWrapper<Crop> qw = new LambdaQueryWrapper<Crop>();
        qw.eq(Crop::getNodeType, NODE_CATEGORY)
                .eq(Crop::getName, name.trim())
                .orderByAsc(Crop::getSortOrder)
                .orderByDesc(Crop::getCreatedAt)
                .last("limit 1");
        return cropService.getOne(qw, false);
    }

    private List<String> normalizeDistinct(List<String> rows, String keyword) {
        Set<String> out = new LinkedHashSet<String>();
        for (String row : rows) {
            if (!StringUtils.hasText(row)) {
                continue;
            }
            out.add(row.trim());
        }
        return filterByKeyword(out, keyword);
    }

    private List<String> filterByKeyword(Set<String> rows, String keyword) {
        String key = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : "";
        return rows.stream()
                .filter(StringUtils::hasText)
                .filter(x -> key.isEmpty() || x.toLowerCase(Locale.ROOT).contains(key))
                .collect(Collectors.toList());
    }

    private String normalizeQueryText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if ("undefined".equals(lower) || "null".equals(lower)) {
            return null;
        }
        return text;
    }

    private boolean containsIgnoreCase(String text, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String src = String.valueOf(text == null ? "" : text).toLowerCase(Locale.ROOT);
        return src.contains(keyword);
    }
}
