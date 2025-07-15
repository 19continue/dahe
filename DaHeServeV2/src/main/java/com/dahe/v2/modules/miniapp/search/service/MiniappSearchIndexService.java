package com.dahe.v2.modules.miniapp.search.service;

import com.dahe.v2.modules.field.model.Field;
import com.dahe.v2.modules.crop.model.Crop;
import com.dahe.v2.modules.seed.model.SeedBatch;

import java.util.Collection;

public interface MiniappSearchIndexService {

    void syncField(Field field);

    void removeField(Long fieldId);

    void rebuildFieldsByIds(Collection<Long> fieldIds);

    void rebuildFieldsByCrop(String cropType, String cropVariety);

    void syncCrop(Crop crop);

    void removeCrop(Long cropId);

    void rebuildCropsByIds(Collection<Long> cropIds);

    void syncSeedBatch(SeedBatch batch);

    void removeSeedBatch(Long batchId);

    void rebuildSeedBatchesByIds(Collection<Long> batchIds);

    void rebuildSeedBatchesByCrop(String cropType, String varietyName);
}
