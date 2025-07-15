package com.dahe.v2.modules.oplog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dahe.v2.modules.oplog.model.OperationLog;

import java.time.LocalDateTime;

public interface OperationLogService extends IService<OperationLog> {

    /** 分页查询操作日志，支持按结果、撤销状态、接口、时间窗口等过滤。 */
    Page<OperationLog> pageLogs(
            String keyword,
            String operationType,
            Integer successFlag,
            String undoStatus,
            Long userId,
            String apiPath,
            LocalDateTime startAt,
            LocalDateTime endAt,
            long page,
            long pageSize
    );

    /** 执行日志撤销，成功返回 null，失败返回错误提示。 */
    String undoOperation(Long logId, Long operatorUserId);
}
