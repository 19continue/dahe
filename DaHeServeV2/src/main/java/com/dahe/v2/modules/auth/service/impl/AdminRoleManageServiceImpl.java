package com.dahe.v2.modules.auth.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.auth.domain.AuthMessageCatalog;
import com.dahe.v2.modules.auth.dto.AdminRoleManageDTO;
import com.dahe.v2.modules.auth.policy.AdminRoleProtectionPolicy;
import com.dahe.v2.modules.auth.role.model.AdminRole;
import com.dahe.v2.modules.auth.role.service.AdminRoleService;
import com.dahe.v2.modules.auth.service.AdminRoleManageService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminRoleManageServiceImpl implements AdminRoleManageService {

    private final AdminRoleService adminRoleService;
    private final AdminRoleProtectionPolicy adminRoleProtectionPolicy;

    public AdminRoleManageServiceImpl(
            AdminRoleService adminRoleService,
            AdminRoleProtectionPolicy adminRoleProtectionPolicy
    ) {
        this.adminRoleService = adminRoleService;
        this.adminRoleProtectionPolicy = adminRoleProtectionPolicy;
    }

    @Override
    public Page<Map<String, Object>> page(String keyword, Integer enabled, long page, long pageSize) {
        Page<AdminRole> rolePage = adminRoleService.pageRoles(keyword, enabled, page, pageSize);
        Page<Map<String, Object>> out = new Page<>(rolePage.getCurrent(), rolePage.getSize(), rolePage.getTotal());
        List<Map<String, Object>> viewRows = new ArrayList<>();
        List<AdminRole> records = rolePage.getRecords();
        if (records != null) {
            for (AdminRole row : records) {
                viewRows.add(toView(row));
            }
        }
        out.setRecords(viewRows);
        return out;
    }

    @Override
    public List<AdminRoleService.RoleOption> options(boolean includeDisabled) {
        return adminRoleService.listRoleOptions(includeDisabled);
    }

    @Override
    public List<String> menuCodes() {
        return adminRoleService.listMenuPermissionCodes();
    }

    @Override
    public Result<Map<String, Object>> create(AdminRoleManageDTO.CreateReq req) {
        String roleCode = adminRoleService.buildRoleCodeFromName(req.getRoleName());
        if (!StringUtils.hasText(roleCode)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "角色编码无效");
        }

        if (adminRoleService.findByRoleCode(roleCode, true) != null) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "角色编码已存在");
        }

        String inheritRoleCode = adminRoleService.normalizeInheritRole(req.getInheritRoleCode());
        if (adminRoleService.wouldCreateInheritanceCycle(roleCode, inheritRoleCode)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "角色继承关系存在循环，请调整后重试");
        }
        if (StringUtils.hasText(inheritRoleCode) && !adminRoleProtectionPolicy.canBeInherited(inheritRoleCode)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.SUPER_ADMIN_ROLE_FORBID_INHERIT);
        }

        AdminRole role = new AdminRole();
        role.setRoleCode(roleCode);
        role.setRoleName(req.getRoleName().trim());
        role.setDescription(StringUtils.hasText(req.getDescription()) ? req.getDescription().trim() : null);
        role.setInheritRoleCode(inheritRoleCode);
        role.setMenuPermissionsJson(adminRoleService.toMenuPermissionsJson(req.getMenuPermissions(), inheritRoleCode));
        role.setSortOrder(normalizeSortOrder(req.getSortOrder()));
        role.setEnabled(req.getEnabled() != null && req.getEnabled() == 0 ? 0 : 1);
        role.setIsSystem(0);
        adminRoleService.save(role);
        return Result.success(toView(role));
    }

    @Override
    public Result<Map<String, Object>> update(Long id, AdminRoleManageDTO.UpdateReq req) {
        AdminRole role = adminRoleService.getById(id);
        if (role == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        String lockedReason = adminRoleProtectionPolicy.resolveLockedReason(role);
        if (StringUtils.hasText(lockedReason)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), lockedReason);
        }
        role.setRoleName(req.getRoleName().trim());
        role.setDescription(StringUtils.hasText(req.getDescription()) ? req.getDescription().trim() : null);

        String inheritRoleCode = adminRoleService.normalizeInheritRole(req.getInheritRoleCode());
        if (adminRoleService.wouldCreateInheritanceCycle(role.getRoleCode(), inheritRoleCode)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "角色继承关系存在循环，请调整后重试");
        }
        if (StringUtils.hasText(inheritRoleCode) && !adminRoleProtectionPolicy.canBeInherited(inheritRoleCode)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.SUPER_ADMIN_ROLE_FORBID_INHERIT);
        }
        role.setInheritRoleCode(inheritRoleCode);
        role.setMenuPermissionsJson(adminRoleService.toMenuPermissionsJson(req.getMenuPermissions(), inheritRoleCode));
        role.setSortOrder(normalizeSortOrder(req.getSortOrder()));

        adminRoleService.updateById(role);
        return Result.success(toView(role));
    }

    @Override
    public Result<Map<String, Object>> updateEnabled(Long id, AdminRoleManageDTO.EnabledReq req) {
        AdminRole role = adminRoleService.getById(id);
        if (role == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        String lockedReason = adminRoleProtectionPolicy.resolveLockedReason(role);
        if (StringUtils.hasText(lockedReason)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), lockedReason);
        }
        if (req.getEnabled() == null) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "启用状态不能为空");
        }
        if (req.getEnabled() == 0) {
            long refCount = adminRoleProtectionPolicy.countReferencedAdminUsers(role.getRoleCode());
            if (refCount > 0) {
                return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "该角色下仍有后台用户，请先迁移用户");
            }
        }
        role.setEnabled(req.getEnabled() == 0 ? 0 : 1);
        adminRoleService.updateById(role);
        return Result.success(toView(role));
    }

    @Override
    public Result<Void> remove(Long id) {
        AdminRole role = adminRoleService.getById(id);
        if (role == null) {
            return Result.success(null);
        }
        String lockedReason = adminRoleProtectionPolicy.resolveLockedReason(role);
        if (StringUtils.hasText(lockedReason)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), lockedReason);
        }
        long refCount = adminRoleProtectionPolicy.countReferencedAdminUsers(role.getRoleCode());
        if (refCount > 0) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "该角色下仍有后台用户，请先迁移用户");
        }
        adminRoleService.removeById(id);
        return Result.success(null);
    }

    /** 将实体对象转换为前端视图对象。 */
    private Map<String, Object> toView(AdminRole role) {
        Map<String, Object> out = new LinkedHashMap<>();
        String lockedReason = adminRoleProtectionPolicy.resolveLockedReason(role);
        boolean superAdminBound = adminRoleProtectionPolicy.isSuperAdminBoundRole(role == null ? null : role.getRoleCode());
        out.put("id", role.getId());
        out.put("roleCode", role.getRoleCode());
        out.put("roleName", role.getRoleName());
        out.put("description", role.getDescription());
        out.put("inheritRoleCode", adminRoleService.normalizeInheritRole(role.getInheritRoleCode()));
        out.put("menuPermissions", adminRoleService.fromMenuPermissionsJson(role.getMenuPermissionsJson(), role.getInheritRoleCode()));
        out.put("sortOrder", role.getSortOrder());
        out.put("enabled", role.getEnabled());
        out.put("isSystem", role.getIsSystem());
        out.put("superAdminBound", superAdminBound ? 1 : 0);
        out.put("locked", StringUtils.hasText(lockedReason) ? 1 : 0);
        out.put("lockedReason", lockedReason);
        out.put("createdAt", role.getCreatedAt());
        out.put("updatedAt", role.getUpdatedAt());
        out.put("userCount", adminRoleProtectionPolicy.countReferencedAdminUsers(role.getRoleCode()));
        return out;
    }

    /** 对排序值做上下限保护，避免异常值影响排序语义。 */
    private int normalizeSortOrder(Integer value) {
        if (value == null) {
            return 0;
        }
        return Math.max(-99999, Math.min(99999, value));
    }
}
