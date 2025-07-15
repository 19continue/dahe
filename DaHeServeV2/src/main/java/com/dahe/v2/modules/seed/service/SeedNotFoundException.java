package com.dahe.v2.modules.seed.service;

/**
 * seed 模块资源不存在异常。
 */
public class SeedNotFoundException extends RuntimeException {
    public SeedNotFoundException(String message) {
        super(message);
    }
}

