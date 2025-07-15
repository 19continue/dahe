package com.dahe.v2.modules.crop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.modules.crop.model.Crop;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class CropServiceImplTest {

    @Spy
    @InjectMocks
    private CropServiceImpl cropService;

    @Test
    void listVarietiesByCategoryShouldKeepMultipleVarietiesAndPreferBoundRow() {
        Crop category = buildCrop(100L, "玉米", null, "category", null, 1);
        Crop boundVariety = buildCrop(101L, "玉米", "先玉335", "variety", 100L, 1);
        Crop legacyDuplicate = buildCrop(102L, "玉米", "先玉335", "variety", null, 2);
        Crop legacyVariety = buildCrop(103L, "玉米", "郑单958", "variety", null, 3);

        doReturn(category).when(cropService).getById(100L);
        doReturn(Arrays.asList(boundVariety, legacyDuplicate, legacyVariety))
                .when(cropService)
                .list(ArgumentMatchers.any(LambdaQueryWrapper.class));

        List<Crop> rows = cropService.listVarietiesByCategory(100L);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(Crop::getVariety).containsExactly("先玉335", "郑单958");
        assertThat(rows.get(0).getId()).isEqualTo(101L);
    }

    @Test
    void pageCropsShouldExposeMultipleVarietiesUnderSameCategory() {
        Crop category = buildCrop(100L, "玉米", null, "category", null, 1);
        Crop boundVariety = buildCrop(101L, "玉米", "先玉335", "variety", 100L, 1);
        Crop legacyVariety = buildCrop(103L, "玉米", "郑单958", "variety", null, 3);

        doReturn(category).when(cropService).getById(100L);
        doReturn(Arrays.asList(boundVariety, legacyVariety))
                .when(cropService)
                .list(ArgumentMatchers.any(LambdaQueryWrapper.class));

        Page<Crop> page = cropService.pageCrops(null, "variety", 100L, 1L, 10L);

        assertThat(page.getTotal()).isEqualTo(2L);
        assertThat(page.getRecords()).extracting(Crop::getVariety).containsExactly("先玉335", "郑单958");
    }

    private Crop buildCrop(Long id, String name, String variety, String nodeType, Long parentId, Integer sortOrder) {
        Crop row = new Crop();
        row.setId(id);
        row.setName(name);
        row.setVariety(variety);
        row.setNodeType(nodeType);
        row.setParentId(parentId);
        row.setSortOrder(sortOrder);
        return row;
    }
}
