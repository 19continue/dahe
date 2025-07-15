package com.dahe.v2.modules.miniapp.auth.service;

import com.dahe.v2.common.Result;
import com.dahe.v2.modules.auth.dto.AuthPortalDTO;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 小程序认证应用服务。
 *
 * <p>将小程序端请求从 auth 管理端模块中解耦，形成独立模块入口。</p>
 */
public interface MiniappAuthService {

    Result<AuthPortalDTO.LoginResp> entry(AuthPortalDTO.MiniappEntryReq req, HttpServletRequest request);

    Result<AuthPortalDTO.LoginResp> apply(AuthPortalDTO.MiniappLoginReq req, HttpServletRequest request);

    Result<AuthPortalDTO.LoginResp> wechatLogin(AuthPortalDTO.MiniappEntryReq req, HttpServletRequest request);

    Result<AuthPortalDTO.LoginResp> validate(AuthPortalDTO.SessionReq req, HttpServletRequest request);

    Result<AuthPortalDTO.LoginResp> refreshSession(AuthPortalDTO.SessionReq req, HttpServletRequest request);

    Result<Void> logout(AuthPortalDTO.SessionReq req, HttpServletRequest request);

    Result<Void> logoutAll(HttpServletRequest request);

    Result<AuthPortalDTO.LoginResp> me(HttpServletRequest request);

    Result<Map<String, Object>> updateAvatar(HttpServletRequest request, AuthPortalDTO.AvatarReq req);

    Result<Map<String, Object>> updateProfile(HttpServletRequest request, AuthPortalDTO.ProfileReq req);

    Result<Map<String, Object>> notices(HttpServletRequest request, String noticeType, Boolean unreadOnly, long page, long pageSize);

    Result<Void> readNotice(HttpServletRequest request, Long id);

    Result<Void> readAllNotices(HttpServletRequest request);

    Result<Void> deleteNotice(HttpServletRequest request, Long id);
}
