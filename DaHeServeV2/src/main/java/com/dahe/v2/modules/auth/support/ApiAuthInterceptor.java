package com.dahe.v2.modules.auth.support;

import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.auth.domain.AuthDomainConstants;
import com.dahe.v2.modules.auth.domain.AuthMessageCatalog;
import com.dahe.v2.modules.auth.policy.AuthUserPolicy;
import com.dahe.v2.modules.auth.role.service.AdminRoleService;
import com.dahe.v2.modules.session.model.TokenSession;
import com.dahe.v2.modules.session.service.TokenSessionService;
import com.dahe.v2.modules.user.model.AppUser;
import com.dahe.v2.modules.user.service.AppUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * API 鉴权拦截器。
 *
 * <p>负责会话校验、用户状态校验、角色菜单权限装配，并调用路由授权服务完成最终准入判断。</p>
 *
 * <p>链路说明：</p>
 * <p>1. 解析 token -> 校验会话是否有效；</p>
 * <p>2. 加载用户并校验禁用/审核状态；</p>
 * <p>3. 后台用户解析生效角色与菜单权限；小程序用户仅保留 miniapp 能力；</p>
 * <p>4. 委托 `AuthApiRouteAuthorizationService` 做路由级授权；</p>
 * <p>5. 将用户与权限写入上下文，供后续链路（日志/审计）使用。</p>
 */
@Component
public class ApiAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ApiAuthInterceptor.class);
    private static final String MINIAPP_UPLOAD_URI = "/api/v2/miniapp/files/upload";

    private final TokenSessionService tokenSessionService;
    private final AppUserService appUserService;
    private final AdminRoleService adminRoleService;
    private final AuthUserPolicy authUserPolicy;
    private final AuthApiRouteAuthorizationService authApiRouteAuthorizationService;
    private final ObjectMapper objectMapper;

    public ApiAuthInterceptor(
            TokenSessionService tokenSessionService,
            AppUserService appUserService,
            AdminRoleService adminRoleService,
            AuthUserPolicy authUserPolicy,
            AuthApiRouteAuthorizationService authApiRouteAuthorizationService,
            ObjectMapper objectMapper
    ) {
        this.tokenSessionService = tokenSessionService;
        this.appUserService = appUserService;
        this.adminRoleService = adminRoleService;
        this.authUserPolicy = authUserPolicy;
        this.authApiRouteAuthorizationService = authApiRouteAuthorizationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 预检请求直接放行，避免跨域场景被误判为未登录。
        if (request == null) {
            return false;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        boolean uploadProbe = isMiniappUploadRequest(request);
        String token = AuthContext.resolveToken(request);
        if (uploadProbe) {
            logUploadProbe(request, token, "resolved");
        }
        if (!StringUtils.hasText(token)) {
            if (uploadProbe) {
                log.warn("Miniapp upload auth probe: no token resolved");
            }
            SecurityContextHolder.clearContext();
            return writeUnauthorized(response, AuthMessageCatalog.LOGIN_REQUIRED);
        }

        TokenSession session = tokenSessionService.findValidByToken(token);
        if (session == null || session.getUserId() == null) {
            if (uploadProbe) {
                log.warn("Miniapp upload auth probe: token unresolved by session store, token={}", maskToken(token));
            }
            SecurityContextHolder.clearContext();
            return writeUnauthorized(response, AuthMessageCatalog.SESSION_EXPIRED);
        }
        if (uploadProbe) {
            log.info(
                    "Miniapp upload auth probe: session hit, token={}, userId={}, userType={}, scene={}",
                    maskToken(token),
                    session.getUserId(),
                    session.getUserType(),
                    session.getLoginScene()
            );
        }

        AppUser user = appUserService.getById(session.getUserId());
        if (user == null) {
            SecurityContextHolder.clearContext();
            return writeUnauthorized(response, AuthMessageCatalog.USER_NOT_FOUND);
        }

        if (user.getEnabled() != null && user.getEnabled() == 0) {
            SecurityContextHolder.clearContext();
            return writeUnauthorized(response, AuthMessageCatalog.ACCOUNT_DISABLED);
        }

        String userType = authUserPolicy.normalizeUserType(user.getUserType());
        String status = authUserPolicy.normalizeReviewStatus(user.getStatus());
        if (AuthDomainConstants.USER_TYPE_MINIAPP.equals(userType)
                && !AuthDomainConstants.REVIEW_STATUS_APPROVED.equals(status)) {
            SecurityContextHolder.clearContext();
            return writeUnauthorized(response, authUserPolicy.resolveSessionDeniedMessage(user));
        }

        if (AuthDomainConstants.USER_TYPE_ADMIN.equals(userType)) {
            /*
             * 后台端权限核心：
             * 1) 普通后台用户：角色必须有效，菜单权限来自角色；
             * 2) 超级管理员：直接授予全量权限兜底，不依赖角色表完整性。
             */
            if (authUserPolicy.isSuperAdmin(user)) {
                String normalized = adminRoleService.normalizeRoleCode(user.getRoleCode());
                AuthContext.bindEffectiveRoleCode(
                        request,
                        StringUtils.hasText(normalized) ? normalized : AuthDomainConstants.ROLE_CODE_SUPER_ADMIN
                );
                AuthContext.bindMenuPermissions(request, Collections.singletonList("*"));
            } else {
                String roleError = authUserPolicy.validateAdminRole(user);
                if (StringUtils.hasText(roleError)) {
                    SecurityContextHolder.clearContext();
                    return writeUnauthorized(response, roleError);
                }
                String effectiveRoleCode = adminRoleService.resolveEffectiveRoleCode(user.getRoleCode());
                if (!StringUtils.hasText(effectiveRoleCode)) {
                    SecurityContextHolder.clearContext();
                    return writeUnauthorized(response, AuthMessageCatalog.ROLE_EFFECTIVE_INVALID);
                }
                AuthContext.bindEffectiveRoleCode(request, effectiveRoleCode);
                AuthContext.bindMenuPermissions(request, adminRoleService.resolveMenuPermissions(user.getRoleCode()));
            }
        } else {
            // 小程序端不参与后台角色权限计算，菜单权限清空。
            AuthContext.bindEffectiveRoleCode(request, null);
            AuthContext.bindMenuPermissions(request, null);
        }

        AuthContext.bindUser(request, user);
        // 路由级准入最后一道闸门：不通过则业务控制器不会执行。
        String deniedReason = authApiRouteAuthorizationService.authorize(request, user);
        if (StringUtils.hasText(deniedReason)) {
            SecurityContextHolder.clearContext();
            return writeUnauthorized(response, deniedReason);
        }
        bindSecurityAuthentication(user, request);
        return true;
    }

    /**
     * 将鉴权结果写入 Spring Security 上下文，供后续统一上下文能力使用。
     */
    private void bindSecurityAuthentication(AppUser user, HttpServletRequest request) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<SimpleGrantedAuthority>();
        String userType = authUserPolicy.normalizeUserType(user.getUserType());
        if (AuthDomainConstants.USER_TYPE_ADMIN.equals(userType)) {
            authorities.add(new SimpleGrantedAuthority(AuthDomainConstants.AUTHORITY_ROLE_ADMIN));
        } else {
            authorities.add(new SimpleGrantedAuthority(AuthDomainConstants.AUTHORITY_ROLE_MINIAPP));
            if (user.getCanConsole() != null && user.getCanConsole() == 1) {
                authorities.add(new SimpleGrantedAuthority(AuthDomainConstants.AUTHORITY_MINIAPP_CONSOLE));
            }
        }
        List<String> menuPermissions = AuthContext.getMenuPermissions(request);
        for (String key : menuPermissions) {
            String item = String.valueOf(key == null ? "" : key).trim();
            if (StringUtils.hasText(item)) {
                authorities.add(new SimpleGrantedAuthority("PERM_" + item));
            }
        }
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * 统一输出未授权响应。
     *
     * <p>按当前项目契约，鉴权失败返回 HTTP 200 + 业务错误码。</p>
     */
    private boolean writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        if (response == null) {
            return false;
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Result<Void> body = Result.failure(ErrorCode.UNAUTHORIZED.getCode(), message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
        return false;
    }

    private boolean isMiniappUploadRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String uri = String.valueOf(request.getRequestURI() == null ? "" : request.getRequestURI()).trim();
        return MINIAPP_UPLOAD_URI.equals(uri);
    }

    private void logUploadProbe(HttpServletRequest request, String resolvedToken, String stage) {
        if (request == null) {
            return;
        }
        String authorization = String.valueOf(request.getHeader("Authorization") == null ? "" : request.getHeader("Authorization")).trim();
        String accessTokenHeader = String.valueOf(request.getHeader("X-Access-Token") == null ? "" : request.getHeader("X-Access-Token")).trim();
        String accessTokenParam = String.valueOf(request.getParameter("accessToken") == null ? "" : request.getParameter("accessToken")).trim();
        log.info(
                "Miniapp upload auth probe: stage={}, contentType={}, authHeader={}, xAccessToken={}, formAccessToken={}, resolved={}",
                stage,
                request.getContentType(),
                StringUtils.hasText(authorization),
                StringUtils.hasText(accessTokenHeader),
                StringUtils.hasText(accessTokenParam),
                maskToken(resolvedToken)
        );
    }

    private String maskToken(String token) {
        String value = String.valueOf(token == null ? "" : token).trim();
        if (!StringUtils.hasText(value)) {
            return "<empty>";
        }
        if (value.length() <= 12) {
            return value;
        }
        return value.substring(0, 6) + "..." + value.substring(value.length() - 4);
    }
}
