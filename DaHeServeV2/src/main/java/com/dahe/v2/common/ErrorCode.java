package com.dahe.v2.common;

/**
 * 全局业务错误码枚举。
 *
 * <p>项目约定：接口通常返回 HTTP 200，前端以业务码（code）判断成功或失败。
 * 因此该枚举是前后端统一错误语义的核心来源。</p>
 */
public enum ErrorCode {

    /** 通用成功码。 */
    SUCCESS(10200, "成功"),
    /** 认证/鉴权失败（未登录、token 失效、权限不足等）。 */
    UNAUTHORIZED(10100, "未授权"),
    /** 请求参数不合法（字段缺失、格式错误、业务校验失败）。 */
    VALIDATION_ERROR(10400, "参数校验失败"),
    /** 资源不存在（按 id 查询不到、目标对象已删除等）。 */
    NOT_FOUND(10404, "资源不存在"),
    /** 服务端内部错误（未分类异常兜底）。 */
    INTERNAL_ERROR(10500, "服务器内部错误");

    /** 业务码。 */
    private final int code;
    /** 默认提示文案。 */
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /** 获取业务码。 */
    public int getCode() {
        return code;
    }

    /** 获取默认提示文案。 */
    public String getMessage() {
        return message;
    }
}

