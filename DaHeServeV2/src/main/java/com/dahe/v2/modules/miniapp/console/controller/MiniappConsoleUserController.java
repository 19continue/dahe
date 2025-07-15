package com.dahe.v2.modules.miniapp.console.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.auth.dto.AdminUserManageDTO;
import com.dahe.v2.modules.auth.service.AdminUserManageService;
import com.dahe.v2.modules.auth.support.AuthContext;
import com.dahe.v2.modules.user.model.AppUser;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Min;
import java.util.Map;

/**
 * 小程序控制台用户审核与权限管理接口。
 *
 * <p>仅对具备控制台权限的小程序用户开放。</p>
 */
@RestController
@RequestMapping("/api/v2/miniapp/console/users")
@Validated
public class MiniappConsoleUserController {

    private final AdminUserManageService adminUserManageService;

    public MiniappConsoleUserController(AdminUserManageService adminUserManageService) {
        this.adminUserManageService = adminUserManageService;
    }

    @GetMapping
    public Result<Page<AppUser>> page(
            HttpServletRequest request,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) Integer enabled,
            @RequestParam(required = false, defaultValue = "0") Integer recycleFlag,
            @RequestParam(defaultValue = "true") boolean reviewOnly,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) long pageSize
    ) {
        Result<Void> guard = requireMiniappConsole(request);
        if (guard != null) {
            return Result.failure(guard.getCode(), guard.getMessage());
        }
        return Result.success(adminUserManageService.pageUsers(keyword, status, userType, enabled, recycleFlag, reviewOnly, page, pageSize));
    }

    @GetMapping("/pending-count")
    public Result<Map<String, Long>> pendingCount(HttpServletRequest request) {
        Result<Void> guard = requireMiniappConsole(request);
        if (guard != null) {
            return Result.failure(guard.getCode(), guard.getMessage());
        }
        return Result.success(adminUserManageService.pendingCountView());
    }

    @PutMapping("/{id}/approve")
    public Result<AppUser> approve(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody @Validated AdminUserManageDTO.ApproveReq req
    ) {
        Result<Void> guard = requireMiniappConsole(request);
        if (guard != null) {
            return Result.failure(guard.getCode(), guard.getMessage());
        }
        return adminUserManageService.approve(id, req);
    }

    @PutMapping("/{id}/role")
    public Result<AppUser> updateRole(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody @Validated AdminUserManageDTO.RoleReq req
    ) {
        Result<Void> guard = requireMiniappConsole(request);
        if (guard != null) {
            return Result.failure(guard.getCode(), guard.getMessage());
        }
        return adminUserManageService.updateRole(id, req);
    }

    @PutMapping("/{id}/enabled")
    public Result<AppUser> updateEnabled(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody @Validated AdminUserManageDTO.EnabledReq req
    ) {
        Result<Void> guard = requireMiniappConsole(request);
        if (guard != null) {
            return Result.failure(guard.getCode(), guard.getMessage());
        }
        return adminUserManageService.updateEnabled(id, req);
    }

    private Result<Void> requireMiniappConsole(HttpServletRequest request) {
        AppUser user = AuthContext.getCurrentUser(request);
        if (user == null) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), "未登录或会话已失效");
        }
        String userType = user.getUserType() == null ? "" : user.getUserType().trim().toLowerCase();
        if (!"miniapp".equals(userType)) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), "仅小程序账号可访问该接口");
        }
        if (user.getCanConsole() == null || user.getCanConsole() != 1) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), "仅小程序控制台用户可访问该接口");
        }
        return null;
    }
}
