package com.dahe.v2.modules.auth.policy;

import com.dahe.v2.modules.auth.domain.AuthDomainConstants;
import com.dahe.v2.modules.auth.domain.AuthMessageCatalog;
import com.dahe.v2.modules.auth.role.model.AdminRole;
import com.dahe.v2.modules.auth.role.service.AdminRoleService;
import com.dahe.v2.modules.user.model.AppUser;
import com.dahe.v2.modules.user.service.AppUserService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 后台角色保护策略。
 *
 * <p>该策略聚焦“哪些角色不可变更”这一治理问题，避免保护逻辑分散在 controller 中。</p>
 *
 * <p>当前规则：</p>
 * <p>1. 只要角色被超级管理员账号绑定，就不可编辑/禁用/删除；</p>
 * <p>2. 任何角色都不可继承“超级管理员绑定角色”，避免普通角色被间接提升为超管能力；</p>
 * <p>3. 超级管理员角色编码使用统一常量 `super_admin`，便于数据库初始化与策略识别。</p>
 */
@Component
public class AdminRoleProtectionPolicy {

    private final AppUserService appUserService;
    private final AdminRoleService adminRoleService;

    public AdminRoleProtectionPolicy(
            AppUserService appUserService,
            AdminRoleService adminRoleService
    ) {
        this.appUserService = appUserService;
        this.adminRoleService = adminRoleService;
    }

    /**
     * 返回角色不可变更的原因；返回 null 表示可修改。
     */
    public String resolveLockedReason(AdminRole role) {
        if (role == null) {
            return null;
        }
        String roleCode = adminRoleService.normalizeRoleCode(role.getRoleCode());
        if (!StringUtils.hasText(roleCode)) {
            return null;
        }
        if (isSuperAdminBoundRole(roleCode)) {
            return AuthMessageCatalog.SUPER_ADMIN_ROLE_IMMUTABLE;
        }
        return null;
    }

    /**
     * 判断角色是否可被继承。
     */
    public boolean canBeInherited(String roleCode) {
        String normalized = adminRoleService.normalizeRoleCode(roleCode);
        if (!StringUtils.hasText(normalized)) {
            return true;
        }
        return !isSuperAdminBoundRole(normalized);
    }

    /**
     * 是否为“超级管理员绑定角色”。
     *
     * <p>当角色编码是 `super_admin` 或至少有一位 super admin 用户绑定该角色时，认定为超管绑定角色。</p>
     */
    public boolean isSuperAdminBoundRole(String roleCode) {
        String normalized = adminRoleService.normalizeRoleCode(roleCode);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        if (AuthDomainConstants.ROLE_CODE_SUPER_ADMIN.equals(normalized)) {
            return true;
        }
        long count = appUserService.lambdaQuery()
                .eq(AppUser::getDeleted, 0)
                .eq(AppUser::getUserType, AuthDomainConstants.USER_TYPE_ADMIN)
                .eq(AppUser::getIsSuperAdmin, 1)
                .eq(AppUser::getRoleCode, normalized)
                .count();
        return count > 0;
    }

    /**
     * 统计后台用户对某角色的引用数量。
     */
    public long countReferencedAdminUsers(String roleCode) {
        String normalized = adminRoleService.normalizeRoleCode(roleCode);
        if (!StringUtils.hasText(normalized)) {
            return 0L;
        }
        return appUserService.lambdaQuery()
                .eq(AppUser::getDeleted, 0)
                .eq(AppUser::getUserType, AuthDomainConstants.USER_TYPE_ADMIN)
                .eq(AppUser::getRoleCode, normalized)
                .count();
    }
}

