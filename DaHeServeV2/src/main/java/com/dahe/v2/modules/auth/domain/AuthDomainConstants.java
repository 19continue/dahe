package com.dahe.v2.modules.auth.domain;

import com.dahe.v2.modules.user.domain.UserDomainConstants;

/**
 * auth 领域常量。
 *
 * <p>集中管理状态值、类型值、登录状态值与权限字面量，避免散落硬编码。</p>
 */
public final class AuthDomainConstants {

    private AuthDomainConstants() {
    }

    // user_type
    public static final String USER_TYPE_ADMIN = UserDomainConstants.USER_TYPE_ADMIN;
    public static final String USER_TYPE_MINIAPP = UserDomainConstants.USER_TYPE_MINIAPP;

    // 审核状态
    public static final String REVIEW_STATUS_PENDING = UserDomainConstants.REVIEW_STATUS_PENDING;
    public static final String REVIEW_STATUS_APPROVED = UserDomainConstants.REVIEW_STATUS_APPROVED;
    public static final String REVIEW_STATUS_REJECTED = UserDomainConstants.REVIEW_STATUS_REJECTED;
    public static final String REVIEW_STATUS_REVOKED = UserDomainConstants.REVIEW_STATUS_REVOKED;
    public static final String REVIEW_STATUS_BLACKLISTED = UserDomainConstants.REVIEW_STATUS_BLACKLISTED;

    // 角色与登录场景
    public static final String ROLE_CODE_SUPER_ADMIN = "super_admin";
    public static final String LOGIN_SCENE_ADMIN_CONSOLE = "admin_console";

    // 头像来源
    public static final String AVATAR_SOURCE_NONE = UserDomainConstants.AVATAR_SOURCE_NONE;
    public static final String AVATAR_SOURCE_WX = UserDomainConstants.AVATAR_SOURCE_WX;
    public static final String AVATAR_SOURCE_UPLOAD = UserDomainConstants.AVATAR_SOURCE_UPLOAD;
    public static final String AVATAR_SOURCE_ADMIN = UserDomainConstants.AVATAR_SOURCE_ADMIN;

    // 通知类型
    public static final String NOTICE_TYPE_SYSTEM = "system";
    public static final String NOTICE_TYPE_REVIEW = "review";
    public static final String NOTICE_TYPE_STATUS = "status";
    public static final String NOTICE_TYPE_REVIEW_APPLY = "review_apply";
    public static final String NOTICE_TYPE_MESSAGE = "message";

    // 通知来源
    public static final String NOTICE_SOURCE_SYSTEM = "system";
    public static final String NOTICE_SOURCE_MANUAL = "manual";

    // 通知目标类型
    public static final String NOTICE_TARGET_ADMIN_ALL = "admin_all";
    public static final String NOTICE_TARGET_ADMIN_ROUTE = "admin_route";
    public static final String NOTICE_TARGET_ADMIN_ROLE = "admin_role";
    public static final String NOTICE_TARGET_MINIAPP_APPROVED = "miniapp_approved";
    public static final String NOTICE_TARGET_MINIAPP_CONSOLE = "miniapp_console";
    public static final String NOTICE_TARGET_EXPLICIT_USERS = "explicit_users";

    // 登录状态
    public static final String LOGIN_STATUS_GUEST = "guest";
    public static final String LOGIN_STATUS_UNKNOWN = "unknown";
    public static final String LOGIN_STATUS_DISABLED = "disabled";

    // Spring Security 授权标识
    public static final String AUTHORITY_ROLE_ADMIN = "ROLE_ADMIN";
    public static final String AUTHORITY_ROLE_MINIAPP = "ROLE_MINIAPP";
    public static final String AUTHORITY_MINIAPP_CONSOLE = "MINIAPP_CONSOLE";

    // 通用键名
    public static final String PROFILE_KEY_USER_TYPE = "userType";
}
