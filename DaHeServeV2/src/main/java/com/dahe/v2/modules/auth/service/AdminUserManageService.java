package com.dahe.v2.modules.auth.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.auth.dto.AdminUserManageDTO;
import com.dahe.v2.modules.auth.role.service.AdminRoleService;
import com.dahe.v2.modules.user.model.AppUser;

import java.util.List;
import java.util.Map;

/**
 * 后台用户管理应用服务。
 */
public interface AdminUserManageService {

    Page<AppUser> pageUsers(String keyword, String status, String userType, Integer enabled, Integer recycleFlag, boolean reviewOnly, long page, long pageSize);

    long pendingCount();

    List<AdminRoleService.RoleOption> roleOptions(boolean includeDisabled);

    Result<AppUser> approve(Long id, AdminUserManageDTO.ApproveReq req);

    Result<AppUser> updateRole(Long id, AdminUserManageDTO.RoleReq req);

    Result<AppUser> updateEnabled(Long id, AdminUserManageDTO.EnabledReq req);

    Result<AppUser> updateMiniappStatus(Long id, Long currentUserId, AdminUserManageDTO.MiniappStatusReq req);

    Result<Boolean> deleteUser(Long id, Long currentUserId, AdminUserManageDTO.DeleteReq req);

    Result<AppUser> createAdminUser(AdminUserManageDTO.AdminCreateReq req);

    Result<Map<String, Object>> resetPassword(Long id, Long currentUserId, AdminUserManageDTO.ResetPasswordReq req);

    Result<Map<String, Object>> revokeSessions(Long id);

    Map<String, Long> pendingCountView();
}
