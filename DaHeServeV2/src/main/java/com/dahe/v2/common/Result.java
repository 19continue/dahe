package com.dahe.v2.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * 统一 API 返回结构。
 *
 * <p>字段约定：</p>
 * <p>1) code：业务码（优先用于成功/失败判断）。</p>
 * <p>2) message：面向调用方的提示信息。</p>
 * <p>3) data：业务数据载荷，失败时通常为 null。</p>
 */
public class Result<T> {

    /** 业务码。 */
    private int code;
    /** 提示信息。 */
    private String message;
    /** 返回数据。 */
    private T data;

    /** 统一成功业务码。 */
    public static final int SUCCESS_CODE = 10200;

    /**
     * 构造成功返回（默认文案“成功”）。
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(SUCCESS_CODE, "成功", data);
    }

    /**
     * 构造成功返回（支持自定义成功文案）。
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(SUCCESS_CODE, message, data);
    }

    /**
     * 构造失败返回。
     */
    public static <T> Result<T> failure(int code, String message) {
        return new Result<>(code, message, null);
    }
}

