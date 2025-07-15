package com.dahe.v2.modules.miniapp.auth.service.impl;

import com.dahe.v2.common.Result;
import com.dahe.v2.modules.auth.domain.AuthDomainConstants;
import com.dahe.v2.modules.auth.domain.AuthMessageCatalog;
import com.dahe.v2.modules.auth.dto.AuthPortalDTO;
import com.dahe.v2.modules.auth.service.AuthPortalService;
import com.dahe.v2.modules.miniapp.auth.service.MiniappAuthService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Map;

/**
 * 小程序认证服务实现。
 *
 * <p>复用 auth 领域认证能力，并在小程序模块统一处理端侧类型约束。</p>
 */
@Service
public class MiniappAuthServiceImpl implements MiniappAuthService {

    private final AuthPortalService authPortalService;

    public MiniappAuthServiceImpl(AuthPortalService authPortalService) {
        this.authPortalService = authPortalService;
    }

    @Override
    public Result<AuthPortalDTO.LoginResp> entry(AuthPortalDTO.MiniappEntryReq req, HttpServletRequest request) {
        return authPortalService.miniappEntry(req, request);
    }

    @Override
    public Result<AuthPortalDTO.LoginResp> apply(AuthPortalDTO.MiniappLoginReq req, HttpServletRequest request) {
        return authPortalService.miniappApply(req, request);
    }

    @Override
    public Result<AuthPortalDTO.LoginResp> wechatLogin(AuthPortalDTO.MiniappEntryReq req, HttpServletRequest request) {
        return authPortalService.miniappWechatLogin(req, request);
    }

    @Override
    public Result<AuthPortalDTO.LoginResp> validate(AuthPortalDTO.SessionReq req, HttpServletRequest request) {
        Result<AuthPortalDTO.LoginResp> result = authPortalService.validate(req, request);
        return normalizeMiniappSessionResp(result);
    }

    @Override
    public Result<AuthPortalDTO.LoginResp> refreshSession(AuthPortalDTO.SessionReq req, HttpServletRequest request) {
        Result<AuthPortalDTO.LoginResp> result = authPortalService.refreshMiniappSession(req, request);
        return normalizeMiniappSessionResp(result);
    }

    @Override
    public Result<Void> logout(AuthPortalDTO.SessionReq req, HttpServletRequest request) {
        return authPortalService.logout(req, request);
    }

    @Override
    public Result<Void> logoutAll(HttpServletRequest request) {
        return authPortalService.logoutAll(request);
    }

    @Override
    public Result<AuthPortalDTO.LoginResp> me(HttpServletRequest request) {
        Result<AuthPortalDTO.LoginResp> result = authPortalService.me(request);
        return normalizeMiniappSessionResp(result);
    }

    @Override
    public Result<Map<String, Object>> updateAvatar(HttpServletRequest request, AuthPortalDTO.AvatarReq req) {
        return authPortalService.updateAvatar(request, req);
    }

    @Override
    public Result<Map<String, Object>> updateProfile(HttpServletRequest request, AuthPortalDTO.ProfileReq req) {
        return authPortalService.updateProfile(request, req);
    }

    @Override
    public Result<Map<String, Object>> notices(HttpServletRequest request, String noticeType, Boolean unreadOnly, long page, long pageSize) {
        return authPortalService.notices(request, noticeType, unreadOnly, page, pageSize);
    }

    @Override
    public Result<Void> readNotice(HttpServletRequest request, Long id) {
        return authPortalService.readNotice(request, id);
    }

    @Override
    public Result<Void> readAllNotices(HttpServletRequest request) {
        return authPortalService.readAllNotices(request);
    }

    @Override
    public Result<Void> deleteNotice(HttpServletRequest request, Long id) {
        return authPortalService.deleteNotice(request, id);
    }

    private Result<AuthPortalDTO.LoginResp> normalizeMiniappSessionResp(Result<AuthPortalDTO.LoginResp> result) {
        if (result == null || result.getData() == null || result.getData().getUser() == null) {
            return result;
        }
        String userType = resolveUserType(result.getData().getUser());
        if (!StringUtils.hasText(userType) || AuthDomainConstants.USER_TYPE_MINIAPP.equals(userType)) {
            return result;
        }
        return Result.success(AuthPortalDTO.LoginResp.guest(AuthMessageCatalog.TYPE_MISMATCH_USE_ADMIN));
    }

    private String resolveUserType(Map<String, Object> profile) {
        if (profile == null) {
            return null;
        }
        String raw = String.valueOf(profile.get(AuthDomainConstants.PROFILE_KEY_USER_TYPE) == null
                ? ""
                : profile.get(AuthDomainConstants.PROFILE_KEY_USER_TYPE)).trim().toLowerCase(Locale.ROOT);
        return StringUtils.hasText(raw) ? raw : null;
    }
}
