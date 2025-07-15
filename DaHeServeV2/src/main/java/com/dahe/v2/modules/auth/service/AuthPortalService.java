package com.dahe.v2.modules.auth.service;

import com.dahe.v2.common.Result;
import com.dahe.v2.modules.auth.dto.AuthPortalDTO;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 认证门户应用服务。
 */
public interface AuthPortalService {

    Result<AuthPortalDTO.LoginResp> miniappEntry(AuthPortalDTO.MiniappEntryReq req, HttpServletRequest request);

    /**
     * 小程序认证申请提交/更新。
     */
    Result<AuthPortalDTO.LoginResp> miniappApply(AuthPortalDTO.MiniappLoginReq req, HttpServletRequest request);

    /**
     * 已认证小程序用户通过微信授权直接换取会话，不再改写审核资料。
     */
    Result<AuthPortalDTO.LoginResp> miniappWechatLogin(AuthPortalDTO.MiniappEntryReq req, HttpServletRequest request);

    Result<AuthPortalDTO.LoginResp> adminLogin(AuthPortalDTO.AdminLoginReq req, HttpServletRequest request);

    /**
     * 后台会话校验（自动屏蔽小程序身份结果）。
     */
    Result<AuthPortalDTO.LoginResp> adminValidate(AuthPortalDTO.SessionReq req, HttpServletRequest request);

    Result<AuthPortalDTO.LoginResp> validate(AuthPortalDTO.SessionReq req, HttpServletRequest request);

    /**
     * 小程序会话静默续期。
     */
    Result<AuthPortalDTO.LoginResp> refreshMiniappSession(AuthPortalDTO.SessionReq req, HttpServletRequest request);

    Result<Void> logout(AuthPortalDTO.SessionReq req, HttpServletRequest request);

    /** 当前登录用户全端下线（清理该用户全部有效会话）。 */
    Result<Void> logoutAll(HttpServletRequest request);

    /**
     * 后台当前用户信息（自动屏蔽小程序身份结果）。
     */
    Result<AuthPortalDTO.LoginResp> adminMe(HttpServletRequest request);

    Result<AuthPortalDTO.LoginResp> me(HttpServletRequest request);

    Result<Map<String, Object>> updateAvatar(HttpServletRequest request, AuthPortalDTO.AvatarReq req);

    Result<Map<String, Object>> updateProfile(HttpServletRequest request, AuthPortalDTO.ProfileReq req);

    Result<Map<String, Object>> changePassword(HttpServletRequest request, AuthPortalDTO.ChangePasswordReq req);

    Result<Map<String, Object>> notices(HttpServletRequest request, String noticeType, Boolean unreadOnly, long page, long pageSize);

    Result<Void> readNotice(HttpServletRequest request, Long id);

    Result<Void> readAllNotices(HttpServletRequest request);

    Result<Void> deleteNotice(HttpServletRequest request, Long id);
}
