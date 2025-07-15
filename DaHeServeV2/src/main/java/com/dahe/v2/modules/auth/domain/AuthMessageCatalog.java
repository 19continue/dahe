package com.dahe.v2.modules.auth.domain;

/**
 * auth 模块统一文案目录。
 *
 * <p>仅收敛高频复用文案，避免多处散写导致语义漂移。</p>
 */
public final class AuthMessageCatalog {

    private AuthMessageCatalog() {
    }

    public static final String LOGIN_REQUIRED = "请先登录";
    public static final String LOGIN_CREDENTIAL_REQUIRED = "登录凭证不能为空";
    public static final String MINIAPP_WECHAT_LOGIN_FAILED = "微信登录失败，请重新进入小程序后重试";
    public static final String MINIAPP_WECHAT_CODE_REQUIRED = "微信登录凭证缺失，请重新进入小程序后重试";
    public static final String MINIAPP_WECHAT_DISABLED = "微信登录未启用，请联系管理员";
    public static final String MINIAPP_WECHAT_CONFIG_MISSING = "微信登录配置未完成，请联系管理员";
    public static final String MINIAPP_WECHAT_CODE_INVALID = "微信登录凭证已失效，请重新进入小程序后重试";
    public static final String MINIAPP_WECHAT_CODE_USED = "本次微信登录凭证已使用，请重新点击登录";
    public static final String MINIAPP_WECHAT_BUSY = "微信登录服务繁忙，请稍后重试";
    public static final String MINIAPP_WECHAT_TIMEOUT = "微信登录服务响应超时，请稍后重试";
    public static final String MINIAPP_WECHAT_UNAVAILABLE = "微信登录服务暂时不可用，请稍后重试";
    public static final String SESSION_EXPIRED = "登录已过期，请重新登录";
    public static final String USER_NOT_FOUND = "用户不存在";
    public static final String ACCOUNT_NOT_APPROVED = "账号未审核通过";
    public static final String ACCOUNT_DISABLED = "账号已被禁用";
    public static final String ACCOUNT_REVOKED = "您的用户登录资格已被收回";
    public static final String ACCOUNT_BLACKLISTED = "您没有申请资格，请联系管理员";

    public static final String MINIAPP_SCENE_INVALID = "小程序登录场景无效，请使用真实业务场景";
    public static final String ADMIN_LOGIN_ONLY_FROM_ADMIN = "后台账号必须从后台登录";
    public static final String ADMIN_AUTO_CREATE_BLOCKED = "后台账号不能通过小程序登录自动创建";
    public static final String ADMIN_ACCOUNT_NOT_FOUND = "后台账号不存在，请先在后台用户管理中创建";
    public static final String ADMIN_LOGIN_NAME_INVALID = "后台登录账号格式无效";
    public static final String ADMIN_LOGIN_NAME_EXISTS = "后台登录账号已存在";
    public static final String ADMIN_PASSWORD_INVALID = "后台账号或密码错误";
    public static final String ADMIN_PASSWORD_WEAK = "后台密码强度不足，至少8位且包含字母和数字";

    public static final String ROLE_MISSING = "后台角色缺失，请联系超级管理员";
    public static final String ROLE_INVALID = "后台角色已禁用或不存在，请联系超级管理员";
    public static final String ROLE_EFFECTIVE_INVALID = "后台角色无效";

    public static final String TYPE_MISMATCH_USE_MINIAPP = "账号类型不匹配，请使用小程序端登录";
    public static final String TYPE_MISMATCH_USE_ADMIN = "账号类型不匹配，请使用后台端登录";

    public static final String STATUS_UNKNOWN = "状态未知";
    public static final String STATUS_APPROVED = "审核通过";
    public static final String STATUS_PENDING = "已提交申请，等待审核";
    public static final String STATUS_REJECTED_DEFAULT = "申请已驳回，请联系管理员";
    public static final String STATUS_REVOKED = "您的用户登录资格已被收回，可重新申请";
    public static final String STATUS_BLACKLISTED = "您没有申请资格，请联系管理员";

    public static final String ADMIN_ONLY_CAN_CREATE = "仅管理员可创建后台账号";
    public static final String MINIAPP_ONLY_FOR_REVIEW = "仅小程序用户可审核";
    public static final String ENABLE_REQUIRED = "启用状态不能为空";
    public static final String MINIAPP_DISABLE_ONLY_APPROVED = "仅已审核通过的小程序用户可禁用";
    public static final String SUPER_ADMIN_FORBID_DISABLE = "超级管理员不可禁用";
    public static final String SUPER_ADMIN_FORBID_DELETE = "超级管理员不可删除";
    public static final String SUPER_ADMIN_ROLE_LOCKED = "超级管理员角色不可修改";
    public static final String SUPER_ADMIN_ROLE_IMMUTABLE = "超级管理员绑定角色不可修改";
    public static final String SUPER_ADMIN_ROLE_FORBID_INHERIT = "不可继承超级管理员绑定角色";
    public static final String SUPER_ADMIN_ROLE_ASSIGN_DENIED = "仅超级管理员账号可绑定 super_admin 角色";

    public static final String ROLE_CODE_INVALID = "角色编码无效";
    public static final String STATUS_COMPAT_INVALID = "状态仅支持：已通过、启用、禁用";
    public static final String OPEN_ID_EXISTS = "微信 OpenID 已存在";
    public static final String DEFAULT_ROLE_MISSING = "暂无可用的启用角色，请先创建角色";
    public static final String ADMIN_PASSWORD_OLD_INVALID = "当前密码错误";
    public static final String ADMIN_PASSWORD_SAME_AS_OLD = "新密码不能与当前密码相同";
    public static final String ADMIN_PASSWORD_RESET_SELF_DENIED = "请在个人主页中修改自己的密码";

    public static final String USER_ALREADY_RECYCLED = "该用户已在回收站中";
    public static final String USER_NOT_IN_RECYCLE = "该用户当前不在回收站中";
    public static final String APPROVED_MINIAPP_DELETE_REQUIRE_RECYCLE = "已通过的小程序用户请先移入回收站";
    public static final String USER_REVIEW_CONFLICT = "该用户已被其他人处理，请刷新列表后重试";
    public static final String ASSET_REVIEW_CONFLICT = "该资源已被其他人处理，请刷新列表后重试";
    public static final String MESSAGE_TARGET_INVALID = "消息发送对象无效";
    public static final String MESSAGE_ROUTE_REQUIRED = "按权限发送时必须指定路由权限码";
    public static final String MESSAGE_ROLE_REQUIRED = "按后台角色发送时必须指定角色";
    public static final String MINIAPP_REAPPLY_BLOCKED = "您没有申请资格，请联系管理员";
    public static final String MINIAPP_CONSOLE_ONLY_APPROVED = "仅已审核通过的小程序用户可开通控制台";
}
