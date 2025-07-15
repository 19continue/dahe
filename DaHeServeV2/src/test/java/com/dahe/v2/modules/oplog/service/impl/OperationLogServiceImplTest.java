package com.dahe.v2.modules.oplog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dahe.v2.modules.oplog.model.OperationLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationLogServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void undoOperation_shouldRejectWhenNotLatestInChain() throws Exception {
        OperationLogServiceImpl service = spy(new OperationLogServiceImpl(jdbcTemplate, new ObjectMapper()));

        OperationLog row = buildUndoLog(101L, "field_reorder", 1L, "restore_reorder", "{\n" +
                "  \"table\":\"field\",\n" +
                "  \"previousOrders\":[{\"id\":1,\"sortOrder\":1}]\n" +
                "}");
        OperationLog latest = buildUndoLog(102L, "field_reorder", 1L, "restore_reorder", "{\n" +
                "  \"table\":\"field\",\n" +
                "  \"previousOrders\":[{\"id\":1,\"sortOrder\":2}]\n" +
                "}");

        doReturn(row).when(service).getById(101L);
        doReturn(latest).when(service).getOne(any(LambdaQueryWrapper.class), eq(false));

        String err = service.undoOperation(101L, 9001L);

        Assertions.assertEquals("需按链式顺序撤销，请先撤销最新日志：102", err);
        verify(jdbcTemplate, never()).update(anyString(), ArgumentMatchers.<Object[]>any());
        verify(service, never()).updateById(any(OperationLog.class));
    }

    @Test
    void undoOperation_shouldApplyRestoreReorderForField() throws Exception {
        OperationLogServiceImpl service = spy(new OperationLogServiceImpl(jdbcTemplate, new ObjectMapper()));

        OperationLog row = buildUndoLog(201L, "field_reorder", 1L, "restore_reorder", "{\n" +
                "  \"table\":\"field\",\n" +
                "  \"previousOrders\":[{\"id\":11,\"sortOrder\":3},{\"id\":12,\"sortOrder\":1}]\n" +
                "}");

        doReturn(row).when(service).getById(201L);
        doReturn(row).when(service).getOne(any(LambdaQueryWrapper.class), eq(false));
        doReturn(true).when(service).updateById(any(OperationLog.class));
        doReturn(1).when(jdbcTemplate).update(anyString(), ArgumentMatchers.<Object[]>any());

        String err = service.undoOperation(201L, 9002L);

        Assertions.assertNull(err);
        Assertions.assertEquals("applied", row.getUndoStatus());
        Assertions.assertNotNull(row.getUndoAppliedAt());
        Assertions.assertEquals(Long.valueOf(9002L), row.getUndoAppliedByUserId());
        verify(jdbcTemplate).update(
                eq("UPDATE `field` SET `sort_order`=? WHERE `id`=?"),
                eq(3),
                eq(11L)
        );
        verify(jdbcTemplate).update(
                eq("UPDATE `field` SET `sort_order`=? WHERE `id`=?"),
                eq(1),
                eq(12L)
        );
        verify(service).updateById(row);
    }

    @Test
    void undoOperation_shouldMarkFailedWhenRestoreReorderPayloadInvalid() throws Exception {
        OperationLogServiceImpl service = spy(new OperationLogServiceImpl(jdbcTemplate, new ObjectMapper()));

        OperationLog row = buildUndoLog(301L, "field_reorder", 1L, "restore_reorder", "{\n" +
                "  \"table\":\"unknown_table\",\n" +
                "  \"previousOrders\":[{\"id\":11,\"sortOrder\":3}]\n" +
                "}");

        doReturn(row).when(service).getById(301L);
        doReturn(row).when(service).getOne(any(LambdaQueryWrapper.class), eq(false));
        doReturn(true).when(service).updateById(any(OperationLog.class));

        String err = service.undoOperation(301L, 9003L);

        Assertions.assertEquals("撤销数据不匹配", err);
        Assertions.assertEquals("failed", row.getUndoStatus());
        Assertions.assertNotNull(row.getUndoAppliedAt());
        Assertions.assertEquals(Long.valueOf(9003L), row.getUndoAppliedByUserId());
        verify(jdbcTemplate, never()).update(anyString(), ArgumentMatchers.<Object[]>any());
        verify(service).updateById(row);
    }

    @Test
    void undoOperation_shouldApplyRestoreStepSortWithCamelCaseSortOrder() throws Exception {
        OperationLogServiceImpl service = spy(new OperationLogServiceImpl(jdbcTemplate, new ObjectMapper()));

        OperationLog row = buildUndoLog(401L, "process_template", 77L, "restore_step_sort", "{\n" +
                "  \"templateId\":77,\n" +
                "  \"previousOrders\":[{\"id\":801,\"sortOrder\":5},{\"id\":802,\"sortOrder\":2}]\n" +
                "}");

        doReturn(row).when(service).getById(401L);
        doReturn(row).when(service).getOne(any(LambdaQueryWrapper.class), eq(false));
        doReturn(true).when(service).updateById(any(OperationLog.class));
        doReturn(1).when(jdbcTemplate).update(anyString(), ArgumentMatchers.<Object[]>any());

        String err = service.undoOperation(401L, 9004L);

        Assertions.assertNull(err);
        Assertions.assertEquals("applied", row.getUndoStatus());
        verify(jdbcTemplate).update(
                eq("UPDATE `farm_process_step` SET `sort_order`=? WHERE `id`=? AND `template_id`=?"),
                eq(5),
                eq(801L),
                eq(77L)
        );
        verify(jdbcTemplate).update(
                eq("UPDATE `farm_process_step` SET `sort_order`=? WHERE `id`=? AND `template_id`=?"),
                eq(2),
                eq(802L),
                eq(77L)
        );
    }

    private OperationLog buildUndoLog(Long id, String targetModule, Long targetId, String undoType, String payloadJson) {
        OperationLog row = new OperationLog();
        row.setId(id);
        row.setTargetModule(targetModule);
        row.setTargetId(targetId);
        row.setSuccessFlag(1);
        row.setUndoType(undoType);
        row.setUndoPayloadJson(payloadJson);
        row.setUndoStatus("pending");
        return row;
    }
}
