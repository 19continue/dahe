package com.dahe.v2.modules.session.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dahe.v2.modules.session.model.SessionDeviceContext;
import com.dahe.v2.modules.session.model.TokenSession;

public interface TokenSessionService extends IService<TokenSession> {

    /** 按 token 查询当前有效会话（status=1 且未过期）。 */
    TokenSession findValidByToken(String token);

    /**
     * 创建新会话并返回已落库记录。
     *
     * @param userId 用户 ID
     * @param userType 用户类型（admin/miniapp）
     * @param loginScene 登录场景（如 admin_console、field_record 等）
     * @param deviceContext 设备上下文（设备标识、设备名、IP、UA）
     * @param validDays 有效天数
     */
    TokenSession createSession(Long userId, String userType, String loginScene, SessionDeviceContext deviceContext, int validDays);

    /**
     * 兼容旧调用：未显式传入用户类型与场景时，按空值处理。
     */
    default TokenSession createSession(Long userId, int validDays) {
        return createSession(userId, null, null, null, validDays);
    }

    /**
     * 兼容旧调用：有用户类型与登录场景，但无设备上下文。
     */
    default TokenSession createSession(Long userId, String userType, String loginScene, int validDays) {
        return createSession(userId, userType, loginScene, null, validDays);
    }

    /** 使 token 失效（幂等）。 */
    void invalidateToken(String token);

    /** 使用户当前全部会话失效，返回失效会话数。 */
    long invalidateByUserId(Long userId);

    /**
     * 使用户会话按维度失效（全部参数都可选，空值表示不筛选）。
     *
     * <p>典型用于：按 userType/loginScene 批量下线。</p>
     */
    long invalidateByUserId(Long userId, String userType, String loginScene);
}
