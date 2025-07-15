package com.dahe.v2.modules.miniapp.search.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.extra.pinyin.PinyinUtil;
import com.dahe.v2.modules.crop.model.Crop;
import com.dahe.v2.modules.crop.service.CropService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dahe.v2.modules.field.model.Field;
import com.dahe.v2.modules.field.model.FieldCropVarietyGroup;
import com.dahe.v2.modules.field.service.FieldCropVarietyGroupCodec;
import com.dahe.v2.modules.field.service.FieldService;
import com.dahe.v2.modules.miniapp.search.model.MiniappSearchTerm;
import com.dahe.v2.modules.miniapp.search.service.MiniappSearchIndexService;
import com.dahe.v2.modules.miniapp.search.service.MiniappSearchTermService;
import com.dahe.v2.modules.seed.model.SeedBatch;
import com.dahe.v2.modules.seed.service.SeedBatchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class MiniappSearchIndexServiceImpl implements MiniappSearchIndexService {

    private static final String SCENE_FIELD = "field";
    private static final String SCENE_FIELD_PICKER = "field-picker";
    private static final String SCENE_SEED_BATCH = "seed-batch";

    private static final String ENTITY_FIELD = "field";
    private static final String ENTITY_CROP = "crop";
    private static final String ENTITY_SEED_BATCH = "seed_batch";

    private static final String TYPE_FIELD = "field";
    private static final String TYPE_TOWNSHIP = "township";
    private static final String TYPE_ADDRESS = "address";
    private static final String TYPE_CROP = "crop";
    private static final String TYPE_VARIETY = "variety";
    private static final String TYPE_BATCH = "batch";

    private static final String LABEL_FIELD = "田块";
    private static final String LABEL_TOWNSHIP = "乡镇";
    private static final String LABEL_ADDRESS = "位置";
    private static final String LABEL_CROP = "作物";
    private static final String LABEL_VARIETY = "品种";
    private static final String LABEL_BATCH = "批次";

    private final MiniappSearchTermService miniappSearchTermService;
    private final CropService cropService;
    private final FieldService fieldService;
    private final SeedBatchService seedBatchService;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public MiniappSearchIndexServiceImpl(
            MiniappSearchTermService miniappSearchTermService,
            CropService cropService,
            FieldService fieldService,
            SeedBatchService seedBatchService,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate
    ) {
        this.miniappSearchTermService = miniappSearchTermService;
        this.cropService = cropService;
        this.fieldService = fieldService;
        this.seedBatchService = seedBatchService;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncField(Field field) {
        if (field == null || field.getId() == null) {
            return;
        }
        purgeEntityTerms(ENTITY_FIELD, field.getId());
        if (!isFieldSuggestible(field)) {
            return;
        }
        List<MiniappSearchTerm> rows = new ArrayList<>();
        appendFieldTerms(rows, SCENE_FIELD, field);
        appendFieldTerms(rows, SCENE_FIELD_PICKER, field);
        saveTerms(rows);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeField(Long fieldId) {
        if (fieldId == null) {
            return;
        }
        purgeEntityTerms(ENTITY_FIELD, fieldId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rebuildFieldsByIds(Collection<Long> fieldIds) {
        if (fieldIds == null || fieldIds.isEmpty()) {
            return;
        }
        List<Field> rows = fieldService.listByIds(fieldIds);
        Set<Long> hitIds = new LinkedHashSet<>();
        for (Field row : rows) {
            if (row != null && row.getId() != null) {
                hitIds.add(row.getId());
                syncField(row);
            }
        }
        for (Long fieldId : fieldIds) {
            if (fieldId != null && !hitIds.contains(fieldId)) {
                removeField(fieldId);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rebuildFieldsByCrop(String cropType, String cropVariety) {
        LambdaQueryWrapper<Field> wrapper = new LambdaQueryWrapper<Field>()
                .select(Field::getId)
                .eq(Field::getDeleted, 0);
        boolean hasCropType = StringUtils.hasText(cropType);
        boolean hasCropVariety = StringUtils.hasText(cropVariety);
        if (hasCropType && hasCropVariety) {
            wrapper.and(w -> w.eq(Field::getCropType, cropType).eq(Field::getCropVariety, cropVariety));
        } else if (hasCropType) {
            wrapper.eq(Field::getCropType, cropType);
        } else if (hasCropVariety) {
            wrapper.eq(Field::getCropVariety, cropVariety);
        } else {
            return;
        }
        List<Long> ids = new ArrayList<>();
        for (Field row : fieldService.list(wrapper)) {
            if (row != null && row.getId() != null) {
                ids.add(row.getId());
            }
        }
        rebuildFieldsByIds(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncCrop(Crop crop) {
        if (crop == null || crop.getId() == null) {
            return;
        }
        purgeEntityTerms(ENTITY_CROP, crop.getId());
        if (!isCropSuggestible(crop)) {
            return;
        }
        List<MiniappSearchTerm> rows = new ArrayList<>();
        appendCropTerms(rows, SCENE_FIELD, crop);
        appendCropTerms(rows, SCENE_FIELD_PICKER, crop);
        appendCropTerms(rows, SCENE_SEED_BATCH, crop);
        saveTerms(rows);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeCrop(Long cropId) {
        if (cropId == null) {
            return;
        }
        purgeEntityTerms(ENTITY_CROP, cropId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rebuildCropsByIds(Collection<Long> cropIds) {
        if (cropIds == null || cropIds.isEmpty()) {
            return;
        }
        List<Crop> rows = cropService.listByIds(cropIds);
        Set<Long> hitIds = new LinkedHashSet<>();
        for (Crop row : rows) {
            if (row != null && row.getId() != null) {
                hitIds.add(row.getId());
                syncCrop(row);
            }
        }
        for (Long cropId : cropIds) {
            if (cropId != null && !hitIds.contains(cropId)) {
                removeCrop(cropId);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncSeedBatch(SeedBatch batch) {
        if (batch == null || batch.getId() == null) {
            return;
        }
        purgeEntityTerms(ENTITY_SEED_BATCH, batch.getId());
        if (!isSeedBatchSuggestible(batch)) {
            return;
        }
        List<MiniappSearchTerm> rows = new ArrayList<>();
        appendSeedBatchTerms(rows, batch);
        saveTerms(rows);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeSeedBatch(Long batchId) {
        if (batchId == null) {
            return;
        }
        purgeEntityTerms(ENTITY_SEED_BATCH, batchId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rebuildSeedBatchesByIds(Collection<Long> batchIds) {
        if (batchIds == null || batchIds.isEmpty()) {
            return;
        }
        List<SeedBatch> rows = seedBatchService.listByIds(batchIds);
        Set<Long> hitIds = new LinkedHashSet<>();
        for (SeedBatch row : rows) {
            if (row != null && row.getId() != null) {
                hitIds.add(row.getId());
                syncSeedBatch(row);
            }
        }
        for (Long batchId : batchIds) {
            if (batchId != null && !hitIds.contains(batchId)) {
                removeSeedBatch(batchId);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rebuildSeedBatchesByCrop(String cropType, String varietyName) {
        LambdaQueryWrapper<SeedBatch> wrapper = new LambdaQueryWrapper<SeedBatch>()
                .select(SeedBatch::getId)
                .eq(SeedBatch::getDeleted, 0);
        boolean hasCropType = StringUtils.hasText(cropType);
        boolean hasVarietyName = StringUtils.hasText(varietyName);
        if (hasCropType && hasVarietyName) {
            wrapper.and(w -> w.eq(SeedBatch::getCropType, cropType).eq(SeedBatch::getVarietyName, varietyName));
        } else if (hasCropType) {
            wrapper.eq(SeedBatch::getCropType, cropType);
        } else if (hasVarietyName) {
            wrapper.eq(SeedBatch::getVarietyName, varietyName);
        } else {
            return;
        }
        List<Long> ids = new ArrayList<>();
        for (SeedBatch row : seedBatchService.list(wrapper)) {
            if (row != null && row.getId() != null) {
                ids.add(row.getId());
            }
        }
        rebuildSeedBatchesByIds(ids);
    }

    private void appendFieldTerms(List<MiniappSearchTerm> rows, String sceneKey, Field field) {
        List<FieldCropVarietyGroup> groups = resolveFieldCropGroups(field);
        addTerm(rows, sceneKey, ENTITY_FIELD, field.getId(), TYPE_FIELD, LABEL_FIELD, field.getName(), field.getName(), 1200);
        addTerm(rows, sceneKey, ENTITY_FIELD, field.getId(), TYPE_TOWNSHIP, LABEL_TOWNSHIP, field.getTownship(), field.getTownship(), 900);
        String address = firstNonBlank(field.getFormattedAddress(), field.getLocationDesc());
        addTerm(rows, sceneKey, ENTITY_FIELD, field.getId(), TYPE_ADDRESS, LABEL_ADDRESS, address, address, 760);
        if (groups.isEmpty()) {
            String cropType = safeText(field.getCropType());
            String cropVariety = safeText(field.getCropVariety());
            addTerm(rows, sceneKey, ENTITY_FIELD, field.getId(), TYPE_CROP, LABEL_CROP, cropType, cropType, 700);
            addTerm(rows, sceneKey, ENTITY_FIELD, field.getId(), TYPE_VARIETY, LABEL_VARIETY, cropVariety, cropVariety, 660);
            addTerm(rows, sceneKey, ENTITY_FIELD, field.getId(), TYPE_CROP, LABEL_CROP, joinPair(cropType, cropVariety), joinPair(cropType, cropVariety), 820);
            return;
        }
        for (FieldCropVarietyGroup group : groups) {
            String cropType = safeText(group == null ? null : group.getCropType());
            String cropVariety = safeText(group == null ? null : group.getCropVariety());
            addTerm(rows, sceneKey, ENTITY_FIELD, field.getId(), TYPE_CROP, LABEL_CROP, cropType, cropType, 700);
            addTerm(rows, sceneKey, ENTITY_FIELD, field.getId(), TYPE_VARIETY, LABEL_VARIETY, cropVariety, cropVariety, 660);
            addTerm(rows, sceneKey, ENTITY_FIELD, field.getId(), TYPE_CROP, LABEL_CROP, joinPair(cropType, cropVariety), joinPair(cropType, cropVariety), 820);
        }
    }

    private void appendSeedBatchTerms(List<MiniappSearchTerm> rows, SeedBatch batch) {
        String cropType = safeText(batch.getCropType());
        String varietyName = safeText(batch.getVarietyName());
        addTerm(rows, SCENE_SEED_BATCH, ENTITY_SEED_BATCH, batch.getId(), TYPE_BATCH, LABEL_BATCH, batch.getBatchCode(), batch.getBatchCode(), 1200);
        addTerm(rows, SCENE_SEED_BATCH, ENTITY_SEED_BATCH, batch.getId(), TYPE_CROP, LABEL_CROP, cropType, cropType, 780);
        addTerm(rows, SCENE_SEED_BATCH, ENTITY_SEED_BATCH, batch.getId(), TYPE_VARIETY, LABEL_VARIETY, varietyName, varietyName, 740);
        addTerm(rows, SCENE_SEED_BATCH, ENTITY_SEED_BATCH, batch.getId(), TYPE_CROP, LABEL_CROP, joinPair(cropType, varietyName), joinPair(cropType, varietyName), 900);
    }

    private void appendCropTerms(List<MiniappSearchTerm> rows, String sceneKey, Crop crop) {
        if (crop == null || crop.getId() == null) {
            return;
        }
        String nodeType = safeText(crop.getNodeType());
        if ("category".equals(nodeType)) {
            addTerm(rows, sceneKey, ENTITY_CROP, crop.getId(), TYPE_CROP, LABEL_CROP, crop.getName(), crop.getName(), 840);
            return;
        }
        if (!"variety".equals(nodeType)) {
            return;
        }
        String cropType = safeText(crop.getName());
        String varietyName = safeText(crop.getVariety());
        addTerm(rows, sceneKey, ENTITY_CROP, crop.getId(), TYPE_CROP, LABEL_CROP, cropType, cropType, 860);
        addTerm(rows, sceneKey, ENTITY_CROP, crop.getId(), TYPE_VARIETY, LABEL_VARIETY, varietyName, varietyName, 820);
        addTerm(rows, sceneKey, ENTITY_CROP, crop.getId(), TYPE_CROP, LABEL_CROP, joinPair(cropType, varietyName), joinPair(cropType, varietyName), 940);
    }

    private void addTerm(
            List<MiniappSearchTerm> rows,
            String sceneKey,
            String entityType,
            Long entityId,
            String termType,
            String typeLabel,
            String label,
            String valueText,
            int sortWeight
    ) {
        String safeLabel = safeText(label);
        String safeValue = safeText(valueText);
        if (!StringUtils.hasText(sceneKey) || entityId == null || !StringUtils.hasText(safeLabel) || !StringUtils.hasText(safeValue)) {
            return;
        }
        String normalized = normalizeText(safeLabel);
        String compact = compact(normalized);
        if (!StringUtils.hasText(normalized) || !StringUtils.hasText(compact)) {
            return;
        }
        MiniappSearchTerm row = new MiniappSearchTerm();
        row.setSceneKey(sceneKey);
        row.setEntityType(entityType);
        row.setEntityId(entityId);
        row.setTermType(termType);
        row.setTypeLabel(typeLabel);
        row.setLabel(safeLabel);
        row.setValueText(safeValue);
        row.setSearchText(normalized);
        row.setSearchCompact(compact);
        row.setPinyinFull(toPinyinFull(safeLabel));
        row.setPinyinInitials(toPinyinInitials(safeLabel));
        row.setSortWeight(sortWeight);
        row.setTermKeyHash(buildTermKeyHash(sceneKey, entityType, entityId, termType, safeValue));
        rows.add(row);
    }

    private void saveTerms(List<MiniappSearchTerm> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        miniappSearchTermService.saveBatch(deduplicateTerms(rows), 200);
    }

    private List<MiniappSearchTerm> deduplicateTerms(List<MiniappSearchTerm> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<MiniappSearchTerm> out = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        for (MiniappSearchTerm row : rows) {
            if (row == null || !StringUtils.hasText(row.getTermKeyHash())) {
                continue;
            }
            if (!keys.add(row.getTermKeyHash())) {
                continue;
            }
            out.add(row);
        }
        return out;
    }

    private void purgeEntityTerms(String entityType, Long entityId) {
        jdbcTemplate.update(
                "DELETE FROM `miniapp_search_term` WHERE `entity_type`=? AND `entity_id`=?",
                entityType,
                entityId
        );
    }

    private boolean isFieldSuggestible(Field field) {
        return field != null
                && field.getId() != null
                && !Objects.equals(field.getDeleted(), 1)
                && !Objects.equals(field.getEnabled(), 0);
    }

    private boolean isSeedBatchSuggestible(SeedBatch batch) {
        return batch != null
                && batch.getId() != null
                && !Objects.equals(batch.getDeleted(), 1)
                && !Objects.equals(batch.getEnabled(), 0);
    }

    private boolean isCropSuggestible(Crop crop) {
        return crop != null
                && crop.getId() != null
                && !Objects.equals(crop.getDeleted(), 1)
                && StringUtils.hasText(crop.getNodeType());
    }

    private List<FieldCropVarietyGroup> resolveFieldCropGroups(Field field) {
        if (field == null) {
            return Collections.emptyList();
        }
        List<FieldCropVarietyGroup> rows = FieldCropVarietyGroupCodec.fromFieldJson(objectMapper, field.getCropVarietyGroupsJson());
        if (rows.isEmpty()) {
            rows = FieldCropVarietyGroupCodec.fromLegacyTexts(field.getCropType(), field.getCropVariety());
        }
        return rows == null ? Collections.emptyList() : rows;
    }

    private String normalizeText(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String text = Normalizer.normalize(raw, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
        return text.isEmpty() ? null : text;
    }

    private String compact(String raw) {
        String normalized = normalizeText(raw);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String compacted = normalized.replaceAll("[\\s·•、,，;；/\\\\()（）\\-_.]+", "");
        return compacted.isEmpty() ? normalized : compacted;
    }

    private String toPinyinFull(String raw) {
        String text = safeText(raw);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            String value = PinyinUtil.getPinyin(text, "");
            return compact(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String toPinyinInitials(String raw) {
        String text = safeText(raw);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            String value = PinyinUtil.getFirstLetter(text, "");
            return compact(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String buildTermKeyHash(String sceneKey, String entityType, Long entityId, String termType, String valueText) {
        String raw = String.join("|",
                String.valueOf(sceneKey),
                String.valueOf(entityType),
                String.valueOf(entityId),
                String.valueOf(termType),
                String.valueOf(valueText));
        return DigestUtil.md5Hex(raw);
    }

    private String safeText(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String text = raw.trim();
        return text.isEmpty() ? null : text;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String text = safeText(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    private String joinPair(String left, String right) {
        String safeLeft = safeText(left);
        String safeRight = safeText(right);
        if (StringUtils.hasText(safeLeft) && StringUtils.hasText(safeRight)) {
            return safeLeft + " · " + safeRight;
        }
        return StringUtils.hasText(safeLeft) ? safeLeft : safeRight;
    }
}
