package com.dahe.v2.modules.auth.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.auth.domain.AuthDomainConstants;
import com.dahe.v2.modules.auth.domain.AuthMessageCatalog;
import com.dahe.v2.modules.auth.dto.AdminUserManageDTO;
import com.dahe.v2.modules.auth.policy.AdminPasswordPolicy;
import com.dahe.v2.modules.auth.policy.AdminOpenIdPolicy;
import com.dahe.v2.modules.auth.policy.AuthUserPolicy;
import com.dahe.v2.modules.auth.role.model.AdminRole;
import com.dahe.v2.modules.auth.role.service.AdminRoleService;
import com.dahe.v2.modules.auth.service.AdminUserManageService;
import com.dahe.v2.modules.auth.service.UserNoticeService;
import com.dahe.v2.modules.session.service.TokenSessionService;
import com.dahe.v2.modules.user.model.AppUser;
import com.dahe.v2.modules.user.service.AppUserService;
import com.dahe.v2.modules.user.service.UserDomainService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class AdminUserManageServiceImpl implements AdminUserManageService {

    private final AppUserService appUserService;
    private final AdminRoleService adminRoleService;
    private final UserNoticeService userNoticeService;
    private final TokenSessionService tokenSessionService;
    private final AuthUserPolicy authUserPolicy;
    private final AdminOpenIdPolicy adminOpenIdPolicy;
    private final AdminPasswordPolicy adminPasswordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final UserDomainService userDomainService;

    public AdminUserManageServiceImpl(
            AppUserService appUserService,
            AdminRoleService adminRoleService,
            UserNoticeService userNoticeService,
            TokenSessionService tokenSessionService,
            AuthUserPolicy authUserPolicy,
            AdminOpenIdPolicy adminOpenIdPolicy,
            AdminPasswordPolicy adminPasswordPolicy,
            PasswordEncoder passwordEncoder,
            UserDomainService userDomainService
    ) {
        this.appUserService = appUserService;
        this.adminRoleService = adminRoleService;
        this.userNoticeService = userNoticeService;
        this.tokenSessionService = tokenSessionService;
        this.authUserPolicy = authUserPolicy;
        this.adminOpenIdPolicy = adminOpenIdPolicy;
        this.adminPasswordPolicy = adminPasswordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.userDomainService = userDomainService;
    }

    @Override
    public Page<AppUser> pageUsers(String keyword, String status, String userType, Integer enabled, Integer recycleFlag, boolean reviewOnly, long page, long pageSize) {
        String normalizedUserType = normalizeUserType(userType, reviewOnly);
        String statusRaw = normalize(status);
        String reviewStatus = normalizeReviewStatus(statusRaw);
        Integer enabledFilter = normalizeEnabled(enabled);
        Integer recycleFilter = recycleFlag == null ? 0 : (recycleFlag == 1 ? 1 : 0);
        if (AuthDomainConstants.USER_TYPE_ADMIN.equals(normalizedUserType)) {
            reviewStatus = null;
        }
        if ("disabled".equals(statusRaw)) {
            enabledFilter = 0;
            reviewStatus = null;
        } else if ("enabled".equals(statusRaw)) {
            enabledFilter = 1;
            reviewStatus = null;
        }
        return appUserService.pageUsers(keyword, reviewStatus, normalizedUserType, enabledFilter, recycleFilter, page, pageSize);
    }

    @Override
    public long pendingCount() {
        return appUserService.lambdaQuery()
                .eq(AppUser::getStatus, AuthDomainConstants.REVIEW_STATUS_PENDING)
                .eq(AppUser::getUserType, AuthDomainConstants.USER_TYPE_MINIAPP)
                .eq(AppUser::getRecycleFlag, 0)
                .count();
    }

    @Override
    public List<AdminRoleService.RoleOption> roleOptions(boolean includeDisabled) {
        List<AdminRoleService.RoleOption> options = adminRoleService.listRoleOptions(includeDisabled);
        if (options == null || options.isEmpty()) {
            return options;
        }
        return options.stream()
                .filter(item -> {
                    String code = item == null ? null : adminRoleService.normalizeRoleCode(item.getRoleCode());
                    return !AuthDomainConstants.ROLE_CODE_SUPER_ADMIN.equals(code);
                })
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public Result<AppUser> approve(Long id, AdminUserManageDTO.ApproveReq req) {
        AppUser user = appUserService.getById(id);
        if (user == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        if (!authUserPolicy.isMiniappUser(user)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.MINIAPP_ONLY_FOR_REVIEW);
        }
        Result<AppUser> conflictError = validateUserAction(user, req == null ? null : req.getExpectedUpdatedAt(), false);
        if (conflictError != null) {
            return conflictError;
        }

        boolean approve = Boolean.TRUE.equals(req.getApprove());
        if (approve) {
            user = userDomainService.applyMiniappReviewDecision(user, true, req.getCanConsole(), null);
            userNoticeService.pushNoticeSafe(
                    user.getId(),
                    "审核通过通知",
                    Boolean.TRUE.equals(req.getCanConsole()) ? "您的账号已审核通过，控制台权限已开通。" : "您的账号已审核通过，可正常使用小程序。",
                    AuthDomainConstants.NOTICE_TYPE_REVIEW
            );
        } else {
            user = userDomainService.applyMiniappReviewDecision(user, false, req.getCanConsole(), req.getRejectReason());
            userNoticeService.pushNoticeSafe(user.getId(), "审核驳回通知", user.getRejectReason(), AuthDomainConstants.NOTICE_TYPE_REVIEW);
        }
        return Result.success(user);
    }

    @Override
    public Result<AppUser> updateRole(Long id, AdminUserManageDTO.RoleReq req) {
        AppUser user = appUserService.getById(id);
        if (user == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        if (authUserPolicy.isMiniappUser(user)) {
            Result<AppUser> conflictError = validateUserAction(user, req == null ? null : req.getExpectedUpdatedAt(), false);
            if (conflictError != null) {
                return conflictError;
            }
            if (Boolean.TRUE.equals(req.getCanConsole()) && !authUserPolicy.isApprovedMiniappUser(user)) {
                return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.MINIAPP_CONSOLE_ONLY_APPROVED);
            }
            user = userDomainService.applyMiniappConsole(user, req.getCanConsole());
            if (req.getCanConsole() != null) {
                userNoticeService.pushNoticeSafe(
                        user.getId(),
                        "控制台权限更新",
                        req.getCanConsole() ? "您的控制台权限已开通。" : "您的控制台权限已关闭。",
                        AuthDomainConstants.NOTICE_TYPE_STATUS
                );
            }
        } else {
            String roleCode = normalizeRoleCode(req.getRoleCode());
            if (!StringUtils.hasText(roleCode)) {
                return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.ROLE_CODE_INVALID);
            }
            if (AuthDomainConstants.ROLE_CODE_SUPER_ADMIN.equalsIgnoreCase(roleCode) && !authUserPolicy.isSuperAdmin(user)) {
                return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.SUPER_ADMIN_ROLE_ASSIGN_DENIED);
            }
            if (authUserPolicy.isSuperAdmin(user)) {
                if (!AuthDomainConstants.ROLE_CODE_SUPER_ADMIN.equalsIgnoreCase(roleCode)) {
                    return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.SUPER_ADMIN_ROLE_LOCKED);
                }
                roleCode = AuthDomainConstants.ROLE_CODE_SUPER_ADMIN;
            }
            user = userDomainService.applyAdminRoleAndConsole(user, roleCode, Boolean.TRUE);
        }
        return Result.success(user);
    }

    @Override
    public Result<AppUser> updateEnabled(Long id, AdminUserManageDTO.EnabledReq req) {
        AppUser user = appUserService.getById(id);
        if (user == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        if (req.getEnabled() == null) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.ENABLE_REQUIRED);
        }
        Result<AppUser> conflictError = validateUserAction(user, req.getExpectedUpdatedAt(), false);
        if (conflictError != null) {
            return conflictError;
        }
        if (authUserPolicy.isMiniappUser(user) && !req.getEnabled() && !authUserPolicy.isApprovedMiniappUser(user)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.MINIAPP_DISABLE_ONLY_APPROVED);
        }
        if (authUserPolicy.isSuperAdmin(user) && !req.getEnabled()) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.SUPER_ADMIN_FORBID_DISABLE);
        }
        user = userDomainService.applyEnabled(user, req.getEnabled());
        if (req.getEnabled()) {
            userNoticeService.pushNoticeSafe(user.getId(), "账号状态变更", "您的账号已恢复正常使用。", AuthDomainConstants.NOTICE_TYPE_STATUS);
        } else {
            userNoticeService.pushNoticeSafe(user.getId(), "账号状态变更", "您的账号已被禁用，请联系管理员。", AuthDomainConstants.NOTICE_TYPE_STATUS);
        }
        return Result.success(user);
    }

    @Override
    public Result<Boolean> deleteUser(Long id, Long currentUserId, AdminUserManageDTO.DeleteReq req) {
        AppUser user = appUserService.getById(id);
        if (user == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        Result<AppUser> conflictError = validateUserAction(user, req == null ? null : req.getExpectedUpdatedAt(), true);
        if (conflictError != null) {
            return Result.failure(conflictError.getCode(), conflictError.getMessage());
        }
        if (authUserPolicy.isSuperAdmin(user)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.SUPER_ADMIN_FORBID_DELETE);
        }
        if (currentUserId != null && Objects.equals(currentUserId, user.getId())) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "当前登录用户不可删除");
        }
        boolean removed = appUserService.removeById(id);
        if (!removed) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        tokenSessionService.invalidateByUserId(id);
        return Result.success(Boolean.TRUE);
    }

    @Override
    public Result<AppUser> updateMiniappStatus(Long id, Long currentUserId, AdminUserManageDTO.MiniappStatusReq req) {
        AppUser user = appUserService.getById(id);
        if (user == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        if (!authUserPolicy.isMiniappUser(user)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.MINIAPP_ONLY_FOR_REVIEW);
        }
        if (currentUserId != null && Objects.equals(currentUserId, user.getId())) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "当前登录用户不可修改自己的小程序用户状态");
        }
        Result<AppUser> conflictError = validateUserAction(user, req == null ? null : req.getExpectedUpdatedAt(), false);
        if (conflictError != null) {
            return conflictError;
        }
        String nextStatus = normalizeReviewStatus(req == null ? null : req.getStatus());
        if (!StringUtils.hasText(nextStatus)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "仅支持收回资格、加入黑名单、解除黑名单或恢复待审核");
        }
        boolean supportedStatus = AuthDomainConstants.REVIEW_STATUS_PENDING.equals(nextStatus)
                || AuthDomainConstants.REVIEW_STATUS_REVOKED.equals(nextStatus)
                || AuthDomainConstants.REVIEW_STATUS_BLACKLISTED.equals(nextStatus)
                || AuthDomainConstants.REVIEW_STATUS_APPROVED.equals(nextStatus);
        if (!supportedStatus || AuthDomainConstants.REVIEW_STATUS_REJECTED.equals(nextStatus)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "仅支持收回资格、加入黑名单、解除黑名单或恢复待审核");
        }
        user = userDomainService.applyMiniappStatus(user, nextStatus);
        tokenSessionService.invalidateByUserId(user.getId());
        if (AuthDomainConstants.REVIEW_STATUS_PENDING.equals(nextStatus)) {
            userNoticeService.pushNoticeSafe(
                    user.getId(),
                    "账号可重新申请",
                    "当前账号已恢复为待审核状态，请重新提交申请资料。",
                    AuthDomainConstants.NOTICE_TYPE_STATUS
            );
        } else if (AuthDomainConstants.REVIEW_STATUS_REVOKED.equals(nextStatus)) {
            userNoticeService.pushNoticeSafe(
                    user.getId(),
                    "登录资格已收回",
                    "您的用户登录资格已被收回，如需继续使用，请重新提交申请。",
                    AuthDomainConstants.NOTICE_TYPE_STATUS
            );
        } else if (AuthDomainConstants.REVIEW_STATUS_BLACKLISTED.equals(nextStatus)) {
            userNoticeService.pushNoticeSafe(
                    user.getId(),
                    "账号已限制申请",
                    "您没有申请资格，请联系管理员。",
                    AuthDomainConstants.NOTICE_TYPE_STATUS
            );
        } else if (AuthDomainConstants.REVIEW_STATUS_APPROVED.equals(nextStatus)) {
            userNoticeService.pushNoticeSafe(
                    user.getId(),
                    "资格已恢复",
                    "您的账号已恢复登录资格，可重新进入小程序。",
                    AuthDomainConstants.NOTICE_TYPE_STATUS
            );
        }
        return Result.success(user);
    }

    @Override
    public Result<AppUser> createAdminUser(AdminUserManageDTO.AdminCreateReq req) {
        String openId = adminOpenIdPolicy.generateAdminOpenId();
        String loginName = adminPasswordPolicy.normalizeLoginName(req.getLoginName());
        if (!adminPasswordPolicy.isValidLoginName(loginName)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.ADMIN_LOGIN_NAME_INVALID);
        }
        if (!adminPasswordPolicy.isStrongPassword(req.getPassword())) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.ADMIN_PASSWORD_WEAK);
        }

        AppUser exists = appUserService.findByWxOpenId(openId);
        if (exists != null) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.OPEN_ID_EXISTS);
        }
        AppUser existsLoginName = appUserService.findAdminByLoginName(loginName);
        if (existsLoginName != null) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.ADMIN_LOGIN_NAME_EXISTS);
        }

        String roleCode = normalizeRoleCode(req.getRoleCode());
        if (!StringUtils.hasText(roleCode)) {
            roleCode = resolveDefaultAdminRoleCode();
        }
        if (AuthDomainConstants.ROLE_CODE_SUPER_ADMIN.equalsIgnoreCase(roleCode)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.SUPER_ADMIN_ROLE_ASSIGN_DENIED);
        }
        if (!StringUtils.hasText(roleCode)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.DEFAULT_ROLE_MISSING);
        }
        UserDomainService.AdminCreateCommand command = new UserDomainService.AdminCreateCommand();
        command.setWxOpenId(openId);
        command.setLoginName(loginName);
        command.setPasswordHash(adminPasswordPolicy.encodePassword(req.getPassword(), passwordEncoder));
        command.setRealName(req.getRealName().trim());
        command.setNickName(StringUtils.hasText(req.getNickName()) ? req.getNickName().trim() : req.getRealName().trim());
        command.setPhone(StringUtils.hasText(req.getPhone()) ? req.getPhone().trim() : null);
        command.setRoleCode(roleCode);
        command.setCanConsole(Boolean.TRUE);
        command.setAvatarUrl(StringUtils.hasText(req.getAvatarUrl()) ? req.getAvatarUrl().trim() : null);
        AppUser user = userDomainService.createAdminUser(command);
        return Result.success(user);
    }

    @Override
    public Result<Map<String, Object>> resetPassword(Long id, Long currentUserId, AdminUserManageDTO.ResetPasswordReq req) {
        AppUser user = appUserService.getById(id);
        if (user == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        if (!authUserPolicy.isAdminUser(user)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.TYPE_MISMATCH_USE_ADMIN);
        }
        if (authUserPolicy.isSuperAdmin(user)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.SUPER_ADMIN_ROLE_LOCKED);
        }
        if (currentUserId != null && Objects.equals(currentUserId, user.getId())) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.ADMIN_PASSWORD_RESET_SELF_DENIED);
        }
        String newPassword = String.valueOf(req.getNewPassword() == null ? "" : req.getNewPassword());
        if (!adminPasswordPolicy.isStrongPassword(newPassword)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.ADMIN_PASSWORD_WEAK);
        }
        user.setPasswordHash(adminPasswordPolicy.encodePassword(newPassword, passwordEncoder));
        appUserService.updateById(user);
        long revokedCount = tokenSessionService.invalidateByUserId(user.getId());
        Map<String, Object> out = new HashMap<String, Object>();
        out.put("userId", user.getId());
        out.put("loginName", user.getLoginName());
        out.put("revokedCount", revokedCount);
        return Result.success(out);
    }

    @Override
    public Result<Map<String, Object>> revokeSessions(Long id) {
        AppUser user = appUserService.getById(id);
        if (user == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        long revokedCount = tokenSessionService.invalidateByUserId(id);
        Map<String, Object> out = new HashMap<String, Object>();
        out.put("userId", id);
        out.put("revokedCount", revokedCount);
        return Result.success(out);
    }

    @Override
    public Map<String, Long> pendingCountView() {
        Map<String, Long> data = new HashMap<String, Long>();
        data.put("pendingCount", pendingCount());
        return data;
    }

    private String normalizeUserType(String userType, boolean reviewOnly) {
        if (reviewOnly) {
            return AuthDomainConstants.USER_TYPE_MINIAPP;
        }
        String raw = normalize(userType);
        if (AuthDomainConstants.USER_TYPE_MINIAPP.equals(raw) || AuthDomainConstants.USER_TYPE_ADMIN.equals(raw)) {
            return raw;
        }
        return null;
    }

    private String normalizeReviewStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        if (AuthDomainConstants.REVIEW_STATUS_PENDING.equals(status)
                || AuthDomainConstants.REVIEW_STATUS_APPROVED.equals(status)
                || AuthDomainConstants.REVIEW_STATUS_REJECTED.equals(status)
                || AuthDomainConstants.REVIEW_STATUS_REVOKED.equals(status)
                || AuthDomainConstants.REVIEW_STATUS_BLACKLISTED.equals(status)) {
            return status;
        }
        return null;
    }

    private Integer normalizeEnabled(Integer enabled) {
        if (enabled == null) {
            return null;
        }
        return enabled == 0 ? 0 : 1;
    }

    private String normalizeRoleCode(String roleCode) {
        String normalized = adminRoleService.normalizeRoleCode(roleCode);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        AdminRole role = adminRoleService.findByRoleCode(normalized, false);
        return role == null ? null : normalized;
    }

    private String resolveDefaultAdminRoleCode() {
        List<AdminRoleService.RoleOption> options = adminRoleService.listRoleOptions(false);
        if (options != null && !options.isEmpty()) {
            String code = adminRoleService.normalizeRoleCode(options.get(0).getRoleCode());
            if (StringUtils.hasText(code)) {
                return code;
            }
        }
        return null;
    }

    private String normalize(String raw) {
        return String.valueOf(raw == null ? "" : raw).trim().toLowerCase(Locale.ROOT);
    }

    private Result<AppUser> validateUserAction(AppUser user, String expectedUpdatedAt, boolean allowRecycled) {
        if (user == null || user.getId() == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        if (!allowRecycled && Objects.equals(user.getRecycleFlag(), 1)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.USER_ALREADY_RECYCLED);
        }
        if (!sameUpdatedAt(user.getUpdatedAt(), expectedUpdatedAt)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.USER_REVIEW_CONFLICT);
        }
        return null;
    }

    private boolean sameUpdatedAt(LocalDateTime actual, String expected) {
        if (!StringUtils.hasText(expected)) {
            return true;
        }
        String actualText = actual == null ? "" : actual.toString();
        String expectedText = String.valueOf(expected == null ? "" : expected).trim();
        return Objects.equals(actualText, expectedText);
    }

    private String trimToNull(String value) {
        String text = String.valueOf(value == null ? "" : value).trim();
        return StringUtils.hasText(text) ? text : null;
    }
}
