package com.dahe.v2.modules.auth.service;

/**
 * 微信 openId 解析服务。
 */
public interface WeChatOpenIdService {

    /**
     * 通过微信登录 code 解析 openId，并返回适合直接提示给前端的失败原因。
     */
    ResolveResult resolve(String code);

    /**
     * 通过微信登录 code 解析 openId。
     *
     * @return 成功返回 openId，失败返回 null。
     */
    default String resolveOpenId(String code) {
        ResolveResult result = resolve(code);
        return result == null ? null : result.getOpenId();
    }

    final class ResolveResult {
        private final String openId;
        private final String message;

        private ResolveResult(String openId, String message) {
            this.openId = openId;
            this.message = message;
        }

        public static ResolveResult success(String openId) {
            return new ResolveResult(openId, null);
        }

        public static ResolveResult failure(String message) {
            return new ResolveResult(null, message);
        }

        public boolean isSuccess() {
            return openId != null && !openId.trim().isEmpty();
        }

        public String getOpenId() {
            return openId;
        }

        public String getMessage() {
            return message;
        }
    }
}
