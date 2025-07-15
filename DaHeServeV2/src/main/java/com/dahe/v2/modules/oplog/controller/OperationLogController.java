package com.dahe.v2.modules.oplog.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.auth.support.AdminMenuCode;
import com.dahe.v2.modules.auth.support.AuthContext;
import com.dahe.v2.modules.oplog.model.OperationLog;
import com.dahe.v2.modules.oplog.service.OperationLogService;
import com.dahe.v2.modules.user.model.AppUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Min;
import java.time.LocalDateTime;

/**
 * 操作日志后台控制器。
 */
@RestController
@RequestMapping("/api/v2/admin/operation-logs")
@AdminMenuCode("/operation-logs")
@Validated
public class OperationLogController {

    private final OperationLogService operationLogService;

    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @GetMapping
    public Result<Page<OperationLog>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) Integer successFlag,
            @RequestParam(required = false) String undoStatus,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String apiPath,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startAt,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endAt,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) long pageSize
    ) {
        Page<OperationLog> rows = operationLogService.pageLogs(
                keyword,
                operationType,
                successFlag,
                undoStatus,
                userId,
                apiPath,
                startAt,
                endAt,
                page,
                pageSize
        );
        return Result.success(rows);
    }

    @PostMapping("/{id}/undo")
    public Result<OperationLog> undo(HttpServletRequest request, @PathVariable Long id) {
        AppUser current = AuthContext.getCurrentUser(request);
        Long currentUserId = current == null ? null : current.getId();
        String err = operationLogService.undoOperation(id, currentUserId);
        if (err != null) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), err);
        }
        OperationLog row = operationLogService.getById(id);
        return row == null
                ? Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage())
                : Result.success(row);
    }
}
