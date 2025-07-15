package com.dahe.v2.exception;

import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.util.StringUtils;

/**
 * 全局异常处理器。
 *
 * <p>将常见异常统一转换为 `Result` 结构，保持前后端错误语义一致。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理 @Valid 请求体参数校验失败。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldError() != null
                ? ex.getBindingResult().getFieldError().getDefaultMessage()
                : ErrorCode.VALIDATION_ERROR.getMessage();
        return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), msg);
    }

    /**
     * 处理 query/path/form 绑定校验失败。
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException ex) {
        String msg = ex.getBindingResult().getFieldError() != null
                ? ex.getBindingResult().getFieldError().getDefaultMessage()
                : ErrorCode.VALIDATION_ERROR.getMessage();
        return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), msg);
    }

    /**
     * 处理业务参数异常（如手动 throw IllegalArgumentException）。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgumentException(IllegalArgumentException ex) {
        String message = StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ErrorCode.VALIDATION_ERROR.getMessage();
        return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), message);
    }

    /**
     * 处理业务状态异常（如非法状态流转）。
     */
    @ExceptionHandler(IllegalStateException.class)
    public Result<Void> handleIllegalStateException(IllegalStateException ex) {
        String message = StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : "当前状态不允许执行该操作";
        return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), message);
    }

    /**
     * 处理请求体 JSON 无法解析。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        String message = "请求参数格式错误，请检查 JSON 字段类型";
        return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), message);
    }

    /**
     * 处理数据库唯一键冲突。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKeyException(DuplicateKeyException ex) {
        return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "数据已存在，请勿重复提交");
    }

    /**
     * 处理数据库约束异常（外键、非空、长度等）。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "数据约束冲突，请检查输入后重试");
    }

    /**
     * 处理 multipart 上传体超限。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "上传文件过大，请压缩图片后重试");
    }

    /**
     * 处理兜底未捕获异常。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex) {
        log.error("Unhandled server exception", ex);
        return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage());
    }
}

