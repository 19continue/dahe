package com.dahe.v2.modules.crop.service;

/**
 * crop 资源不存在异常。
 */
public class CropNotFoundException extends RuntimeException {
    public CropNotFoundException(String message) {
        super(message);
    }
}
