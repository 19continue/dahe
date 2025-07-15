package com.dahe.v2.modules.auth.controller;

import com.dahe.v2.common.Result;
import com.dahe.v2.modules.auth.dto.AuthPortalDTO;
import com.dahe.v2.modules.auth.service.AuthPortalService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 后台认证入口（管理员专用）。
 */
@RestController
@RequestMapping("/api/v2/admin/auth")
@Validated
public class AdminAuthController {

    private final AuthPortalService authPortalService;

    public AdminAuthController(AuthPortalService authPortalService) {
        this.authPortalService = authPortalService;
    }

    @PostMapping("/login")
    public Result<AuthPortalDTO.LoginResp> login(
            @RequestBody @Validated AuthPortalDTO.AdminLoginReq req,
            HttpServletRequest request
    ) {
        return authPortalService.adminLogin(req, request);
    }

    @PostMapping("/session/validate")
    public Result<AuthPortalDTO.LoginResp> validate(
            @RequestBody(required = false) AuthPortalDTO.SessionReq req,
            HttpServletRequest request
    ) {
        return authPortalService.adminValidate(req, request);
    }

    @PostMapping("/logout")
    public Result<Void> logout(
            @RequestBody(required = false) AuthPortalDTO.SessionReq req,
            HttpServletRequest request
    ) {
        return authPortalService.logout(req, request);
    }

    @PostMapping("/logout-all")
    public Result<Void> logoutAll(HttpServletRequest request) {
        return authPortalService.logoutAll(request);
    }

    @GetMapping("/me")
    public Result<AuthPortalDTO.LoginResp> me(HttpServletRequest request) {
        return authPortalService.adminMe(request);
    }

    @PutMapping("/me/avatar")
    public Result<Map<String, Object>> updateAvatar(
            HttpServletRequest request,
            @RequestBody @Validated AuthPortalDTO.AvatarReq req
    ) {
        return authPortalService.updateAvatar(request, req);
    }

    @PutMapping("/me/profile")
    public Result<Map<String, Object>> updateProfile(
            HttpServletRequest request,
            @RequestBody @Validated AuthPortalDTO.ProfileReq req
    ) {
        return authPortalService.updateProfile(request, req);
    }

    @PutMapping("/me/password")
    public Result<Map<String, Object>> changePassword(
            HttpServletRequest request,
            @RequestBody @Validated AuthPortalDTO.ChangePasswordReq req
    ) {
        return authPortalService.changePassword(request, req);
    }

    @GetMapping("/me/notices")
    public Result<Map<String, Object>> notices(
            HttpServletRequest request,
            @RequestParam(required = false) String noticeType,
            @RequestParam(required = false) Boolean unreadOnly,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize
    ) {
        return authPortalService.notices(request, noticeType, unreadOnly, page, pageSize);
    }

    @PutMapping("/me/notices/{id}/read")
    public Result<Void> readNotice(HttpServletRequest request, @PathVariable Long id) {
        return authPortalService.readNotice(request, id);
    }

    @PutMapping("/me/notices/read-all")
    public Result<Void> readAllNotices(HttpServletRequest request) {
        return authPortalService.readAllNotices(request);
    }

    @DeleteMapping("/me/notices/{id}")
    public Result<Void> deleteNotice(HttpServletRequest request, @PathVariable Long id) {
        return authPortalService.deleteNotice(request, id);
    }
}
