package com.dahe.v2.modules.assets.staticasset.service;

public class MiniappStaticAssetServiceException extends RuntimeException {

    private final int code;

    public MiniappStaticAssetServiceException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
