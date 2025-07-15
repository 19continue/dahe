package com.dahe.v2.modules.auth.role.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dahe.v2.modules.auth.role.model.AdminRole;
import lombok.Data;

import java.util.List;

public interface AdminRoleService extends IService<AdminRole> {

    String normalizeRoleCode(String roleCode);

    String normalizeInheritRole(String inheritRoleCode);

    boolean wouldCreateInheritanceCycle(String roleCode, String inheritRoleCode);

    AdminRole findByRoleCode(String roleCode, boolean includeDisabled);

    String resolveEffectiveRoleCode(String roleCode);

    List<String> resolveMenuPermissions(String roleCode);

    List<RoleOption> listRoleOptions(boolean includeDisabled);

    Page<AdminRole> pageRoles(String keyword, Integer enabled, long page, long pageSize);

    /**
     * 返回可分配的后台菜单权限码，仅路径码，不包含中文。
     */
    List<String> listMenuPermissionCodes();

    String toMenuPermissionsJson(List<String> menuPermissions, String inheritRoleCode);

    List<String> fromMenuPermissionsJson(String text, String inheritRoleCode);

    String buildRoleCodeFromName(String roleName);

    @Data
    class RoleOption {
        private Long id;
        private String roleCode;
        private String roleName;
        private String inheritRoleCode;
        private Integer enabled;
        private Integer isSystem;
        private Integer sortOrder;
    }
}