package com.dahe.v2.modules.miniapp.auth.controller;

import com.dahe.v2.common.Result;
import com.dahe.v2.modules.auth.dto.AuthPortalDTO;
import com.dahe.v2.modules.miniapp.auth.service.MiniappAuthService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 小程序认证入口（小程序用户专用）。
 *
 * <p>仅保留“未登录也必须开放”的认证入口；其余会话相关接口由拦截器继续做身份校验。</p>
 */
@RestController
@RequestMapping("/api/v2/miniapp/auth")
@Validated
public class MiniappAuthController {

    private final MiniappAuthService miniappAuthService;

    public MiniappAuthController(MiniappAuthService miniappAuthService) {
        this.miniappAuthService = miniappAuthService;
    }

    @PostMapping("/entry")
    public Result<AuthPortalDTO.LoginResp> entry(
            @RequestBody @Validated AuthPortalDTO.MiniappEntryReq req,
            HttpServletRequest request
    ) {
        return miniappAuthService.entry(req, request);
    }

    @PostMapping("/apply")
    public Result<AuthPortalDTO.LoginResp> apply(
            @RequestBody @Validated AuthPortalDTO.MiniappLoginReq req,
            HttpServletRequest request
    ) {
        return miniappAuthService.apply(req, request);
    }

    @PostMapping("/wechat-login")
    public Result<AuthPortalDTO.LoginResp> wechatLogin(
            @RequestBody @Validated AuthPortalDTO.MiniappEntryReq req,
            HttpServletRequest request
    ) {
        return miniappAuthService.wechatLogin(req, request);
    }

    @PostMapping("/session/validate")
    public Result<AuthPortalDTO.LoginResp> validate(
            @RequestBody(required = false) AuthPortalDTO.SessionReq req,
            HttpServletRequest request
    ) {
        return miniappAuthService.validate(req, request);
    }

    @PostMapping("/session/refresh")
    public Result<AuthPortalDTO.LoginResp> refresh(
            @RequestBody(required = false) AuthPortalDTO.SessionReq req,
            HttpServletRequest request
    ) {
        return miniappAuthService.refreshSession(req, request);
    }

    @PostMapping("/logout")
    public Result<Void> logout(
            @RequestBody(required = false) AuthPortalDTO.SessionReq req,
            HttpServletRequest request
    ) {
        return miniappAuthService.logout(req, request);
    }

    @PostMapping("/logout-all")
    public Result<Void> logoutAll(HttpServletRequest request) {
        return miniappAuthService.logoutAll(request);
    }

    @GetMapping("/me")
    public Result<AuthPortalDTO.LoginResp> me(HttpServletRequest request) {
        return miniappAuthService.me(request);
    }

    @PutMapping("/me/avatar")
    public Result<Map<String, Object>> updateAvatar(
            HttpServletRequest request,
            @RequestBody @Validated AuthPortalDTO.AvatarReq req
    ) {
        return miniappAuthService.updateAvatar(request, req);
    }

    @PutMapping("/me/profile")
    public Result<Map<String, Object>> updateProfile(
            HttpServletRequest request,
            @RequestBody @Validated AuthPortalDTO.ProfileReq req
    ) {
        return miniappAuthService.updateProfile(request, req);
    }

    @GetMapping("/me/notices")
    public Result<Map<String, Object>> notices(
            HttpServletRequest request,
            @RequestParam(required = false) String noticeType,
            @RequestParam(required = false) Boolean unreadOnly,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize
    ) {
        return miniappAuthService.notices(request, noticeType, unreadOnly, page, pageSize);
    }

    @PutMapping("/me/notices/{id}/read")
    public Result<Void> readNotice(HttpServletRequest request, @PathVariable Long id) {
        return miniappAuthService.readNotice(request, id);
    }

    @PutMapping("/me/notices/read-all")
    public Result<Void> readAllNotices(HttpServletRequest request) {
        return miniappAuthService.readAllNotices(request);
    }

    @DeleteMapping("/me/notices/{id}")
    public Result<Void> deleteNotice(HttpServletRequest request, @PathVariable Long id) {
        return miniappAuthService.deleteNotice(request, id);
    }
}
