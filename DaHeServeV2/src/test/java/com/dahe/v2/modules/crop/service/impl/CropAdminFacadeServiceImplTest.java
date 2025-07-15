package com.dahe.v2.modules.crop.service.impl;

import com.dahe.v2.modules.assets.service.MediaAssetBindingService;
import com.dahe.v2.modules.crop.model.Crop;
import com.dahe.v2.modules.crop.service.CropAdminCommand;
import com.dahe.v2.modules.crop.service.CropService;
import com.dahe.v2.modules.crop.service.CropServiceException;
import com.dahe.v2.modules.miniapp.search.service.MiniappSearchIndexService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CropAdminFacadeServiceImplTest {

    private final CropService cropService = mock(CropService.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final MiniappSearchIndexService miniappSearchIndexService =  mock(MiniappSearchIndexService.class);
    private final MediaAssetBindingService mediaAssetBindingService =  mock(MediaAssetBindingService.class);
    private final CropAdminFacadeServiceImpl service = new CropAdminFacadeServiceImpl(cropService, jdbcTemplate,miniappSearchIndexService,mediaAssetBindingService);

    @Test
    void reorder_shouldRejectWhenIdsEmpty() {
        CropAdminCommand.Reorder command = new CropAdminCommand.Reorder();
        command.setNodeType("category");
        command.setIds(java.util.Collections.emptyList());

        CropServiceException ex = Assertions.assertThrows(CropServiceException.class, () -> service.reorder(command));
        Assertions.assertTrue(ex.getMessage().contains("编号列表不能为空"));
    }

    @Test
    void delete_shouldRejectWhenReferencedByTemplate() {
        Crop row = new Crop();
        row.setId(101L);
        row.setNodeType("variety");
        when(cropService.getById(101L)).thenReturn(row);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyLong())).thenReturn(1L);

        CropServiceException ex = Assertions.assertThrows(CropServiceException.class, () -> service.delete(101L));
        Assertions.assertTrue(ex.getMessage().contains("流程模板引用"));
        verify(cropService, never()).removeById(any(Long.class));
    }
}
