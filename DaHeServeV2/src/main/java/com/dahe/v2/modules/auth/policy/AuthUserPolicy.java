package com.dahe.v2.modules.auth.policy;

import com.dahe.v2.modules.auth.domain.AuthDomainConstants;
import com.dahe.v2.modules.auth.domain.AuthMessageCatalog;
import com.dahe.v2.modules.auth.role.model.AdminRole;
import com.dahe.v2.modules.auth.role.service.AdminRoleService;
import com.dahe.v2.modules.user.model.AppUser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 用户认证领域策略。
 *
 * <p>统一封装用户类型、审核状态、角色有效性、会话签发等判断规则。</p>
 *
 * <p>定位：</p>
 * <p>1. 这是“用户是否可登录/可维持会话”的唯一判定层；</p>
 * <p>2. 业务服务只调用该策略，不直接写 if-else 组合判断；</p>
 * <p>3. 超级管理员兜底策略也在此集中定义，避免多处语义不一致。</p>
 */
@Component
public class AuthUserPolicy {

    private final AdminRoleService adminRoleService;

    public AuthUserPolicy(AdminRoleService adminRoleService) {
        this.adminRoleService = adminRoleService;
    }

    public String normalizeUserType(String userType) {
        String normalized = normalize(userType);
        if (!StringUtils.hasText(normalized)) {
            return AuthDomainConstants.USER_TYPE_MINIAPP;
        }
        if (AuthDomainConstants.USER_TYPE_ADMIN.equals(normalized) || AuthDomainConstants.USER_TYPE_MINIAPP.equals(normalized)) {
            return normalized;
        }
        return normalized;
    }

    public String normalizeReviewStatus(String status) {
        return normalize(status);
    }

    public boolean isAdminUser(AppUser user) {
        return AuthDomainConstants.USER_TYPE_ADMIN.equals(normalizeUserType(user == null ? null : user.getUserType()));
    }

    public boolean isMiniappUser(AppUser user) {
        return !isAdminUser(user);
    }

    public boolean isSuperAdmin(AppUser user) {
        return user != null && user.getIsSuperAdmin() != null && user.getIsSuperAdmin() == 1;
    }

    public boolean isApprovedMiniappUser(AppUser user) {
        if (!isMiniappUser(user)) {
            return false;
        }
        return AuthDomainConstants.REVIEW_STATUS_APPROVED.equals(normalizeReviewStatus(user == null ? null : user.getStatus()));
    }

    public boolean isRevokedMiniappUser(AppUser user) {
        return isMiniappUser(user)
                && AuthDomainConstants.REVIEW_STATUS_REVOKED.equals(normalizeReviewStatus(user == null ? null : user.getStatus()));
    }

    public boolean isBlacklistedMiniappUser(AppUser user) {
        return isMiniappUser(user)
                && AuthDomainConstants.REVIEW_STATUS_BLACKLISTED.equals(normalizeReviewStatus(user == null ? null : user.getStatus()));
    }

    public String normalizeAvatarSource(String source) {
        String normalized = normalize(source);
        if (AuthDomainConstants.AVATAR_SOURCE_WX.equals(normalized)
                || AuthDomainConstants.AVATAR_SOURCE_UPLOAD.equals(normalized)
                || AuthDomainConstants.AVATAR_SOURCE_ADMIN.equals(normalized)) {
            return normalized;
        }
        return AuthDomainConstants.AVATAR_SOURCE_NONE;
    }

    /**
     * 校验后台用户角色是否可用。
     *
     * <p>设计要点：</p>
     * <p>1. 普通后台用户必须绑定有效角色；</p>
     * <p>2. 超级管理员具备全量兜底能力，不能因为角色表异常被“锁死”在登录阶段；</p>
     * <p>3. 若角色编码可归一化，会回写到 user，保证后续查询一致性。</p>
     *
     * @return null 表示合法，非空为可直接返回给前端的错误文案。
     */
    public String validateAdminRole(AppUser user) {
        if (!isAdminUser(user)) {
            return null;
        }
        if (isSuperAdmin(user)) {
            String roleCode = adminRoleService.normalizeRoleCode(user.getRoleCode());
            if (StringUtils.hasText(roleCode)) {
                user.setRoleCode(roleCode);
            }
            return null;
        }
        String roleCode = adminRoleService.normalizeRoleCode(user.getRoleCode());
        if (!StringUtils.hasText(roleCode)) {
            return AuthMessageCatalog.ROLE_MISSING;
        }
        AdminRole role = adminRoleService.findByRoleCode(roleCode, false);
        if (role == null) {
            return AuthMessageCatalog.ROLE_INVALID;
        }
        user.setRoleCode(roleCode);
        return null;
    }

    public boolean canIssueSession(AppUser user) {
        if (user == null) {
            return false;
        }
        if (user.getEnabled() != null && user.getEnabled() == 0) {
            return false;
        }
        if (isAdminUser(user)) {
            // 超级管理员是系统兜底身份，不因角色表异常阻断登录。
            if (isSuperAdmin(user)) {
                return true;
            }
            return !StringUtils.hasText(validateAdminRole(user));
        }
        return isApprovedMiniappUser(user);
    }

    /**
     * 会话拒绝原因（返回给前端展示）。
     *
     * <p>输出顺序与 `canIssueSession` 保持一致，避免“判定通过/提示失败”矛盾。</p>
     */
    public String resolveSessionDeniedMessage(AppUser user) {
        if (user == null) {
            return AuthMessageCatalog.ACCOUNT_NOT_APPROVED;
        }
        if (user.getEnabled() != null && user.getEnabled() == 0) {
            return AuthMessageCatalog.ACCOUNT_DISABLED;
        }
        if (isAdminUser(user)) {
            String roleError = validateAdminRole(user);
            if (StringUtils.hasText(roleError)) {
                return roleError;
            }
            return AuthMessageCatalog.ACCOUNT_NOT_APPROVED;
        }
        if (isBlacklistedMiniappUser(user)) {
            return AuthMessageCatalog.ACCOUNT_BLACKLISTED;
        }
        if (isRevokedMiniappUser(user)) {
            return AuthMessageCatalog.ACCOUNT_REVOKED;
        }
        return AuthMessageCatalog.ACCOUNT_NOT_APPROVED;
    }

    public boolean isAccountApproved(AppUser user) {
        if (user == null) {
            return false;
        }
        if (user.getEnabled() != null && user.getEnabled() == 0) {
            return false;
        }
        if (isAdminUser(user)) {
            return true;
        }
        return isApprovedMiniappUser(user);
    }

    public String resolveLoginStatus(AppUser user) {
        if (user == null) {
            return AuthDomainConstants.LOGIN_STATUS_UNKNOWN;
        }
        if (user.getEnabled() != null && user.getEnabled() == 0) {
            return AuthDomainConstants.LOGIN_STATUS_DISABLED;
        }
        if (isAdminUser(user)) {
            return AuthDomainConstants.REVIEW_STATUS_APPROVED;
        }
        String status = normalizeReviewStatus(user.getStatus());
        return StringUtils.hasText(status) ? status : AuthDomainConstants.REVIEW_STATUS_PENDING;
    }

    public String resolveStatusMessage(AppUser user) {
        if (user == null) {
            return AuthMessageCatalog.STATUS_UNKNOWN;
        }
        if (user.getEnabled() != null && user.getEnabled() == 0) {
            return AuthMessageCatalog.ACCOUNT_DISABLED;
        }
        if (isAdminUser(user)) {
            return AuthMessageCatalog.STATUS_APPROVED;
        }
        String status = normalizeReviewStatus(user.getStatus());
        if (!StringUtils.hasText(status)) {
            return AuthMessageCatalog.STATUS_UNKNOWN;
        }
        if (AuthDomainConstants.REVIEW_STATUS_APPROVED.equals(status)) {
            return AuthMessageCatalog.STATUS_APPROVED;
        }
        if (AuthDomainConstants.REVIEW_STATUS_PENDING.equals(status)) {
            return AuthMessageCatalog.STATUS_PENDING;
        }
        if (AuthDomainConstants.REVIEW_STATUS_REJECTED.equals(status)) {
            return StringUtils.hasText(user.getRejectReason())
                    ? user.getRejectReason()
                    : AuthMessageCatalog.STATUS_REJECTED_DEFAULT;
        }
        if (AuthDomainConstants.REVIEW_STATUS_REVOKED.equals(status)) {
            return AuthMessageCatalog.STATUS_REVOKED;
        }
        if (AuthDomainConstants.REVIEW_STATUS_BLACKLISTED.equals(status)) {
            return AuthMessageCatalog.STATUS_BLACKLISTED;
        }
        return "状态：" + status;
    }

    private String normalize(String value) {
        return String.valueOf(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
    }
}
