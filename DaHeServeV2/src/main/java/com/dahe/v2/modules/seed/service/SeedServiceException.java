package com.dahe.v2.modules.seed.service;

/**
 * seed 模块业务异常。
 *
 * <p>用于服务层向控制层透出可控错误码与错误信息。</p>
 */
public class SeedServiceException extends RuntimeException {

    private final int code;

    public SeedServiceException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}

