package com.dahe.v2.modules.user.domain;

/**
 * user 领域常量。
 *
 * <p>用于统一用户域的状态、类型与头像来源字面量，避免散落硬编码。</p>
 */
public final class UserDomainConstants {

    private UserDomainConstants() {
    }

    // user_type
    public static final String USER_TYPE_ADMIN = "admin";
    public static final String USER_TYPE_MINIAPP = "miniapp";

    // 审核状态
    public static final String REVIEW_STATUS_PENDING = "pending";
    public static final String REVIEW_STATUS_APPROVED = "approved";
    public static final String REVIEW_STATUS_REJECTED = "rejected";
    public static final String REVIEW_STATUS_REVOKED = "revoked";
    public static final String REVIEW_STATUS_BLACKLISTED = "blacklisted";

    // 头像来源
    public static final String AVATAR_SOURCE_NONE = "none";
    public static final String AVATAR_SOURCE_WX = "wx";
    public static final String AVATAR_SOURCE_UPLOAD = "upload";
    public static final String AVATAR_SOURCE_ADMIN = "admin";
}
