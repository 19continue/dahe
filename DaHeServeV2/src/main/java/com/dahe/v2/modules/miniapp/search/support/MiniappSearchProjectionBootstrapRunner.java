package com.dahe.v2.modules.miniapp.search.support;

import cn.hutool.extra.pinyin.PinyinUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dahe.v2.modules.crop.model.Crop;
import com.dahe.v2.modules.crop.service.CropService;
import com.dahe.v2.modules.field.model.Field;
import com.dahe.v2.modules.field.service.FieldService;
import com.dahe.v2.modules.miniapp.search.model.MiniappSearchTerm;
import com.dahe.v2.modules.miniapp.search.service.MiniappSearchIndexService;
import com.dahe.v2.modules.miniapp.search.service.MiniappSearchTermService;
import com.dahe.v2.modules.seed.model.SeedBatch;
import com.dahe.v2.modules.seed.service.SeedBatchService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class MiniappSearchProjectionBootstrapRunner implements ApplicationRunner {

    private static final int BATCH_SIZE = 200;
    private static final String ENTITY_FIELD = "field";
    private static final String ENTITY_CROP = "crop";
    private static final String ENTITY_SEED_BATCH = "seed_batch";

    private final MiniappSearchTermService miniappSearchTermService;
    private final MiniappSearchIndexService miniappSearchIndexService;
    private final CropService cropService;
    private final FieldService fieldService;
    private final SeedBatchService seedBatchService;

    public MiniappSearchProjectionBootstrapRunner(
            MiniappSearchTermService miniappSearchTermService,
            MiniappSearchIndexService miniappSearchIndexService,
            CropService cropService,
            FieldService fieldService,
            SeedBatchService seedBatchService
    ) {
        this.miniappSearchTermService = miniappSearchTermService;
        this.miniappSearchIndexService = miniappSearchIndexService;
        this.cropService = cropService;
        this.fieldService = fieldService;
        this.seedBatchService = seedBatchService;
    }

    @Override
    public void run(ApplicationArguments args) {
        backfillProjectionPinyin();
        if (!hasProjection(ENTITY_FIELD)) {
            rebuildFields();
        }
        if (!hasProjection(ENTITY_CROP)) {
            rebuildCrops();
        }
        if (!hasProjection(ENTITY_SEED_BATCH)) {
            rebuildSeedBatches();
        }
    }

    private void rebuildFields() {
        List<Long> batch = new ArrayList<>(BATCH_SIZE);
        for (Field row : fieldService.lambdaQuery()
                .select(Field::getId)
                .eq(Field::getDeleted, 0)
                .list()) {
            if (row == null || row.getId() == null) {
                continue;
            }
            batch.add(row.getId());
            if (batch.size() >= BATCH_SIZE) {
                miniappSearchIndexService.rebuildFieldsByIds(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            miniappSearchIndexService.rebuildFieldsByIds(batch);
        }
    }

    private void rebuildCrops() {
        List<Long> batch = new ArrayList<>(BATCH_SIZE);
        for (Crop row : cropService.lambdaQuery()
                .select(Crop::getId)
                .eq(Crop::getDeleted, 0)
                .list()) {
            if (row == null || row.getId() == null) {
                continue;
            }
            batch.add(row.getId());
            if (batch.size() >= BATCH_SIZE) {
                miniappSearchIndexService.rebuildCropsByIds(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            miniappSearchIndexService.rebuildCropsByIds(batch);
        }
    }

    private void rebuildSeedBatches() {
        List<Long> batch = new ArrayList<>(BATCH_SIZE);
        for (SeedBatch row : seedBatchService.lambdaQuery()
                .select(SeedBatch::getId)
                .eq(SeedBatch::getDeleted, 0)
                .list()) {
            if (row == null || row.getId() == null) {
                continue;
            }
            batch.add(row.getId());
            if (batch.size() >= BATCH_SIZE) {
                miniappSearchIndexService.rebuildSeedBatchesByIds(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            miniappSearchIndexService.rebuildSeedBatchesByIds(batch);
        }
    }

    private boolean hasProjection(String entityType) {
        return miniappSearchTermService.lambdaQuery()
                .eq(MiniappSearchTerm::getDeleted, 0)
                .eq(MiniappSearchTerm::getEntityType, entityType)
                .last("limit 1")
                .count() > 0L;
    }

    private void backfillProjectionPinyin() {
        while (true) {
            List<MiniappSearchTerm> rows = miniappSearchTermService.list(new LambdaQueryWrapper<MiniappSearchTerm>()
                    .select(MiniappSearchTerm::getId,
                            MiniappSearchTerm::getLabel,
                            MiniappSearchTerm::getPinyinFull,
                            MiniappSearchTerm::getPinyinInitials)
                    .eq(MiniappSearchTerm::getDeleted, 0)
                    .and(w -> w.isNull(MiniappSearchTerm::getPinyinFull)
                            .or().isNull(MiniappSearchTerm::getPinyinInitials))
                    .last("limit " + BATCH_SIZE));
            if (rows == null || rows.isEmpty()) {
                return;
            }
            List<MiniappSearchTerm> updates = new ArrayList<>(rows.size());
            for (MiniappSearchTerm row : rows) {
                if (row == null || row.getId() == null || !StringUtils.hasText(row.getLabel())) {
                    continue;
                }
                MiniappSearchTerm update = new MiniappSearchTerm();
                update.setId(row.getId());
                update.setPinyinFull(toPinyinFull(row.getLabel()));
                update.setPinyinInitials(toPinyinInitials(row.getLabel()));
                updates.add(update);
            }
            if (!updates.isEmpty()) {
                miniappSearchTermService.updateBatchById(updates, BATCH_SIZE);
            }
            if (rows.size() < BATCH_SIZE) {
                return;
            }
        }
    }

    private String toPinyinFull(String raw) {
        String text = safeText(raw);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return compact(PinyinUtil.getPinyin(text, ""));
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
            return compact(PinyinUtil.getFirstLetter(text, ""));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String compact(String raw) {
        String normalized = normalize(raw);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String compacted = normalized.replaceAll("[\\s·•、,，;；/\\\\()（）\\-_.]+", "");
        return compacted.isEmpty() ? normalized : compacted;
    }

    private String normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String text = Normalizer.normalize(raw, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
        return text.isEmpty() ? null : text;
    }

    private String safeText(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String text = raw.trim();
        return text.isEmpty() ? null : text;
    }

}
