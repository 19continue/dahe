package com.dahe.v2.modules.export.service;

/**
 * export 模块业务异常。
 */
public class ExportServiceException extends RuntimeException {

    private final int code;

    public ExportServiceException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
