package com.dahe.v2.modules.crop.service;

/**
 * crop 业务异常。
 */
public class CropServiceException extends RuntimeException {

    private final int code;

    public CropServiceException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
