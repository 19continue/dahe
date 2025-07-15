package com.dahe.v2.modules.auth.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.auth.dto.AdminUserManageDTO;
import com.dahe.v2.modules.auth.role.service.AdminRoleService;
import com.dahe.v2.modules.auth.service.AdminUserManageService;
import com.dahe.v2.modules.auth.support.AdminMenuCode;
import com.dahe.v2.modules.auth.support.AuthContext;
import com.dahe.v2.modules.user.model.AppUser;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Min;
import java.util.List;
import java.util.Map;

/**
 * 后台用户管理控制器。
 *
 * <p>控制器职责限定为参数接收与响应封装，业务逻辑统一下沉到 {@link AdminUserManageService}。</p>
 */
@RestController
@RequestMapping("/api/v2/admin/users")
@AdminMenuCode({"/users", "/admin-users"})
@Validated
public class AdminUserController {

    private final AdminUserManageService adminUserManageService;

    public AdminUserController(AdminUserManageService adminUserManageService) {
        this.adminUserManageService = adminUserManageService;
    }

    @GetMapping
    @AdminMenuCode({"/users", "/users/manage", "/admin-users", "/messages"})
    public Result<Page<AppUser>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) Integer enabled,
            @RequestParam(required = false, defaultValue = "0") Integer recycleFlag,
            @RequestParam(defaultValue = "true") boolean reviewOnly,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) long pageSize
    ) {
        return Result.success(adminUserManageService.pageUsers(keyword, status, userType, enabled, recycleFlag, reviewOnly, page, pageSize));
    }

    @GetMapping("/pending-count")
    @AdminMenuCode("/users")
    public Result<Map<String, Long>> pendingCount() {
        return Result.success(adminUserManageService.pendingCountView());
    }

    @GetMapping("/role-options")
    @AdminMenuCode({"/admin-users", "/messages"})
    public Result<List<AdminRoleService.RoleOption>> roleOptions(@RequestParam(defaultValue = "false") boolean includeDisabled) {
        return Result.success(adminUserManageService.roleOptions(includeDisabled));
    }

    @PutMapping("/{id}/approve")
    @AdminMenuCode("/users")
    public Result<AppUser> approve(@PathVariable Long id, @RequestBody @Validated AdminUserManageDTO.ApproveReq req) {
        return adminUserManageService.approve(id, req);
    }

    @PutMapping("/{id}/role")
    @AdminMenuCode({"/users/manage", "/admin-users"})
    public Result<AppUser> updateRole(@PathVariable Long id, @RequestBody @Validated AdminUserManageDTO.RoleReq req) {
        return adminUserManageService.updateRole(id, req);
    }

    @PutMapping("/{id}/enabled")
    @AdminMenuCode({"/users/manage", "/admin-users"})
    public Result<AppUser> updateEnabled(@PathVariable Long id, @RequestBody @Validated AdminUserManageDTO.EnabledReq req) {
        return adminUserManageService.updateEnabled(id, req);
    }

    @PutMapping("/{id}/miniapp-status")
    @AdminMenuCode({"/users", "/users/manage"})
    public Result<AppUser> updateMiniappStatus(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody @Validated AdminUserManageDTO.MiniappStatusReq req
    ) {
        AppUser currentUser = AuthContext.getCurrentUser(request);
        Long currentUserId = currentUser == null ? null : currentUser.getId();
        return adminUserManageService.updateMiniappStatus(id, currentUserId, req);
    }

    @DeleteMapping("/{id}")
    @AdminMenuCode({"/users/manage", "/admin-users"})
    public Result<Boolean> deleteUser(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody(required = false) AdminUserManageDTO.DeleteReq req
    ) {
        AppUser currentUser = AuthContext.getCurrentUser(request);
        Long currentUserId = currentUser == null ? null : currentUser.getId();
        return adminUserManageService.deleteUser(id, currentUserId, req == null ? new AdminUserManageDTO.DeleteReq() : req);
    }

    @PostMapping("/admin-create")
    @AdminMenuCode("/admin-users")
    public Result<AppUser> createAdminUser(@RequestBody @Validated AdminUserManageDTO.AdminCreateReq req) {
        return adminUserManageService.createAdminUser(req);
    }

    @PutMapping("/{id}/password/reset")
    @AdminMenuCode("/admin-users")
    public Result<Map<String, Object>> resetPassword(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody @Validated AdminUserManageDTO.ResetPasswordReq req
    ) {
        AppUser currentUser = AuthContext.getCurrentUser(request);
        Long currentUserId = currentUser == null ? null : currentUser.getId();
        return adminUserManageService.resetPassword(id, currentUserId, req);
    }

    @PostMapping("/{id}/sessions/revoke")
    @AdminMenuCode({"/users/manage", "/admin-users"})
    public Result<Map<String, Object>> revokeSessions(@PathVariable Long id) {
        return adminUserManageService.revokeSessions(id);
    }
}
