package com.dahe.v2.modules.auth.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.auth.config.AuthProperties;
import com.dahe.v2.modules.auth.domain.AuthDomainConstants;
import com.dahe.v2.modules.auth.domain.AuthMessageCatalog;
import com.dahe.v2.modules.auth.dto.AuthPortalDTO;
import com.dahe.v2.modules.auth.policy.AdminPasswordPolicy;
import com.dahe.v2.modules.auth.policy.AdminOpenIdPolicy;
import com.dahe.v2.modules.auth.policy.AuthUserPolicy;
import com.dahe.v2.modules.auth.policy.MiniappLoginScenePolicy;
import com.dahe.v2.modules.auth.role.model.AdminRole;
import com.dahe.v2.modules.auth.role.service.AdminRoleService;
import com.dahe.v2.modules.auth.service.AuthPortalService;
import com.dahe.v2.modules.auth.service.UserNoticeService;
import com.dahe.v2.modules.auth.service.WeChatOpenIdService;
import com.dahe.v2.modules.auth.support.AuthDeviceContextResolver;
import com.dahe.v2.modules.auth.support.AuthContext;
import com.dahe.v2.modules.assets.service.MediaAssetBindingService;
import com.dahe.v2.modules.session.model.SessionDeviceContext;
import com.dahe.v2.modules.session.model.TokenSession;
import com.dahe.v2.modules.session.service.TokenSessionService;
import com.dahe.v2.modules.user.model.AppUser;
import com.dahe.v2.modules.user.service.UserDomainService;
import com.dahe.v2.modules.user.service.AppUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 认证门户应用服务实现。
 *
 * <p>统一承载后台/小程序登录、会话校验、当前用户信息、资料更新与通知能力。</p>
 */
@Service
public class AuthPortalServiceImpl implements AuthPortalService {

    private static final Logger log = LoggerFactory.getLogger(AuthPortalServiceImpl.class);
    private static final int DEFAULT_SESSION_VALID_DAYS = 30;

    private final AppUserService appUserService;
    private final TokenSessionService tokenSessionService;
    private final AdminRoleService adminRoleService;
    private final UserNoticeService userNoticeService;
    private final WeChatOpenIdService weChatOpenIdService;
    private final AuthProperties authProperties;
    private final MiniappLoginScenePolicy miniappLoginScenePolicy;
    private final AuthUserPolicy authUserPolicy;
    private final AdminOpenIdPolicy adminOpenIdPolicy;
    private final AdminPasswordPolicy adminPasswordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final AuthDeviceContextResolver authDeviceContextResolver;
    private final UserDomainService userDomainService;
    private final MediaAssetBindingService mediaAssetBindingService;

    public AuthPortalServiceImpl(
            AppUserService appUserService,
            TokenSessionService tokenSessionService,
            AdminRoleService adminRoleService,
            UserNoticeService userNoticeService,
            WeChatOpenIdService weChatOpenIdService,
            AuthProperties authProperties,
            MiniappLoginScenePolicy miniappLoginScenePolicy,
            AuthUserPolicy authUserPolicy,
            AdminOpenIdPolicy adminOpenIdPolicy,
            AdminPasswordPolicy adminPasswordPolicy,
            PasswordEncoder passwordEncoder,
            AuthDeviceContextResolver authDeviceContextResolver,
            UserDomainService userDomainService,
            MediaAssetBindingService mediaAssetBindingService
    ) {
        this.appUserService = appUserService;
        this.tokenSessionService = tokenSessionService;
        this.adminRoleService = adminRoleService;
        this.userNoticeService = userNoticeService;
        this.weChatOpenIdService = weChatOpenIdService;
        this.authProperties = authProperties;
        this.miniappLoginScenePolicy = miniappLoginScenePolicy;
        this.authUserPolicy = authUserPolicy;
        this.adminOpenIdPolicy = adminOpenIdPolicy;
        this.adminPasswordPolicy = adminPasswordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.authDeviceContextResolver = authDeviceContextResolver;
        this.userDomainService = userDomainService;
        this.mediaAssetBindingService = mediaAssetBindingService;
    }

    @Override
    public Result<AuthPortalDTO.LoginResp> miniappEntry(AuthPortalDTO.MiniappEntryReq req, HttpServletRequest request) {
        // 第一步：先把前端传来的微信 code 换成 openId。
        // 只有拿到 openId，后端才能知道“当前到底是哪一个微信用户”。
        WeChatOpenIdService.ResolveResult resolveResult = resolveOpenId(req.getCode());
        // 微信侧解析失败，直接返回给前端，前端不应继续走后续准入判断。
        if (!resolveResult.isSuccess()) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), resolveWechatResolveMessage(resolveResult));
        }
        // openId 是小程序侧识别用户的核心外部身份。
        String wxOpenId = resolveResult.getOpenId();
        // 统一整理登录场景，避免前端传值大小写不一致。
        String loginScene = miniappLoginScenePolicy.normalizeScene(req.getLoginScene());
        // 不允许的登录场景直接拦截，防止前端伪造场景进入错误链路。
        if (!miniappLoginScenePolicy.isAllowed(loginScene)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.MINIAPP_SCENE_INVALID);
        }

        // 用 openId 找系统里的用户绑定关系。
        AppUser user = appUserService.findByWxOpenId(wxOpenId);
        // 如果这个 openId 实际上绑定的是后台管理员，就不能从小程序入口登录。
        if (user != null && authUserPolicy.isAdminUser(user)) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), AuthMessageCatalog.ADMIN_LOGIN_ONLY_FROM_ADMIN);
        }
        // 根本没有绑定用户时，不发 token，而是提示先去提交认证申请。
        if (user == null) {
            return Result.success(AuthPortalDTO.LoginResp.guest("请先提交申请，审核通过后方可进入小程序"));
        }
        // 这里故意只返回 user + status，不发 session。
        // 这正是“先认证、后登录”的第一步：先让前端知道当前账号处于什么状态。
        return Result.success(buildResp(user, null));
    }

    @Override
    public Result<AuthPortalDTO.LoginResp> miniappApply(AuthPortalDTO.MiniappLoginReq req, HttpServletRequest request) {
        // 认证资料提交也要先把 code 换成 openId，确保提交动作和某个微信用户绑定。
        WeChatOpenIdService.ResolveResult resolveResult = resolveOpenId(req.getCode());
        if (!resolveResult.isSuccess()) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), resolveWechatResolveMessage(resolveResult));
        }
        String wxOpenId = resolveResult.getOpenId();
        // 统一整理登录场景，后续发 session 时会继续用到这个值。
        String loginScene = miniappLoginScenePolicy.normalizeScene(req.getLoginScene());
        if (!miniappLoginScenePolicy.isAllowed(loginScene)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.MINIAPP_SCENE_INVALID);
        }

        // 查当前 openId 是否已经绑定系统用户。
        AppUser user = appUserService.findByWxOpenId(wxOpenId);
        // 管理员账号不允许从小程序认证入口进入。
        if (user != null && authUserPolicy.isAdminUser(user)) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), AuthMessageCatalog.ADMIN_LOGIN_ONLY_FROM_ADMIN);
        }
        // 如果还没有 user，但 openId 长得像管理员账号，也要拦住，避免误建成小程序用户。
        if (user == null && adminOpenIdPolicy.isAdminOpenIdLike(wxOpenId)) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), AuthMessageCatalog.ADMIN_AUTO_CREATE_BLOCKED);
        }
        // 黑名单用户不允许重新发起申请。
        if (user != null && authUserPolicy.isBlacklistedMiniappUser(user)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.MINIAPP_REAPPLY_BLOCKED);
        }
        if (user != null && authUserPolicy.isApprovedMiniappUser(user)) {
            // 已审核通过的小程序用户不能再通过“提交认证”链路覆盖资料；前端应改走纯微信登录。
            return Result.success(buildResp(user, null));
        }

        // 这里开始把前端提交的数据组装成领域层命令对象。
        // 控制器不自己决定新增还是更新，而是统一交给领域服务。
        UserDomainService.MiniappLoginCommand command = new UserDomainService.MiniappLoginCommand();
        // 绑定本次申请对应的 openId。
        command.setWxOpenId(wxOpenId);
        // 前端提交的小程序昵称。
        command.setNickName(req.getNickName());
        // 真实姓名，通常是审核时最关心的字段之一。
        command.setRealName(req.getRealName());
        // 联系手机号。
        command.setPhone(req.getPhone());
        // 申请原因。
        command.setApplyReason(req.getApplyReason());
        // 当前头像地址。
        command.setAvatarUrl(req.getAvatarUrl());
        // 微信原始头像地址。
        command.setWxAvatarUrl(req.getWxAvatarUrl());
        // 头像来源。
        command.setAvatarSource(req.getAvatarSource());

        UserDomainService.MiniappLoginUpsertResult upsertResult;
        try {
            // 领域服务内部会负责“新建用户 / 更新已有申请 / 重申请”的具体规则。
            upsertResult = userDomainService.upsertMiniappUserFromLogin(command);
        } catch (IllegalStateException e) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), e.getMessage());
        }
        // 取回 upsert 后的最新用户对象，后续所有状态判断都基于它。
        user = upsertResult == null ? null : upsertResult.getUser();
        if (user == null) {
            return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), "用户登录处理失败");
        }
        if (upsertResult.isReviewApplied()) {
            // 只有真正进入审核流程时，才给管理员派发审核通知。
            userNoticeService.pushReviewApplyNoticeToAdmins(user, upsertResult.isReApply());
        }

        // 资料提交成功，不代表现在就能登录。
        // 例如：审核中、未通过、已停用、资格已收回，这些状态都不能发 session。
        if (!authUserPolicy.canIssueSession(user)) {
            return Result.success(buildResp(user, null));
        }

        // 只有通过准入检查后，才真正签发小程序 session。
        return issueMiniappSession(user, loginScene, authDeviceContextResolver.resolve(req.getDeviceContext(), request));
    }

    @Override
    public Result<AuthPortalDTO.LoginResp> miniappWechatLogin(AuthPortalDTO.MiniappEntryReq req, HttpServletRequest request) {
        WeChatOpenIdService.ResolveResult resolveResult = resolveOpenId(req.getCode());
        if (!resolveResult.isSuccess()) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), resolveWechatResolveMessage(resolveResult));
        }
        String wxOpenId = resolveResult.getOpenId();
        String loginScene = miniappLoginScenePolicy.normalizeScene(req.getLoginScene());
        if (!miniappLoginScenePolicy.isAllowed(loginScene)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.MINIAPP_SCENE_INVALID);
        }

        AppUser user = appUserService.findByWxOpenId(wxOpenId);
        if (user != null && authUserPolicy.isAdminUser(user)) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), AuthMessageCatalog.ADMIN_LOGIN_ONLY_FROM_ADMIN);
        }
        if (user == null) {
            return Result.success(AuthPortalDTO.LoginResp.guest("请先提交申请，审核通过后方可进入小程序"));
        }
        if (!authUserPolicy.canIssueSession(user)) {
            return Result.success(buildResp(user, null));
        }
        return issueMiniappSession(user, loginScene, authDeviceContextResolver.resolve(req.getDeviceContext(), request));
    }

    @Override
    public Result<AuthPortalDTO.LoginResp> adminLogin(AuthPortalDTO.AdminLoginReq req, HttpServletRequest request) {
        String loginName = adminPasswordPolicy.normalizeLoginName(req.getLoginName());
        if (!adminPasswordPolicy.isValidLoginName(loginName)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.ADMIN_LOGIN_NAME_INVALID);
        }
        String rawPassword = String.valueOf(req.getPassword() == null ? "" : req.getPassword());
        AppUser user = appUserService.findAdminByLoginName(loginName);
        if (user == null || !authUserPolicy.isAdminUser(user)) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), AuthMessageCatalog.ADMIN_ACCOUNT_NOT_FOUND);
        }
        if (!adminPasswordPolicy.matches(rawPassword, user.getPasswordHash(), passwordEncoder)) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), AuthMessageCatalog.ADMIN_PASSWORD_INVALID);
        }
        String adminRoleError = authUserPolicy.validateAdminRole(user);
        if (StringUtils.hasText(adminRoleError)) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), adminRoleError);
        }
        if (!authUserPolicy.canIssueSession(user)) {
            return Result.success(buildResp(user, null));
        }
        SessionDeviceContext context = authDeviceContextResolver.resolve(req.getDeviceContext(), request);
        TokenSession session = tokenSessionService.createSession(
                user.getId(),
                AuthDomainConstants.USER_TYPE_ADMIN,
                AuthDomainConstants.LOGIN_SCENE_ADMIN_CONSOLE,
                context,
                30
        );
        return Result.success(buildResp(user, session));
    }

    @Override
    public Result<AuthPortalDTO.LoginResp> adminValidate(AuthPortalDTO.SessionReq req, HttpServletRequest request) {
        return normalizeAdminSessionResp(validate(req, request));
    }

    @Override
    public Result<AuthPortalDTO.LoginResp> validate(AuthPortalDTO.SessionReq req, HttpServletRequest request) {
        // validate 是运行期校验接口，前端会用它判断当前 token 是否仍可继续使用。
        String token = req == null ? null : req.getAccessToken();
        // 请求体没传 token 时，再从请求头里兜底读取。
        if (!StringUtils.hasText(token)) {
            token = AuthContext.resolveToken(request);
        }
        // 连 token 都没有，说明当前就是未登录状态。
        if (!StringUtils.hasText(token)) {
            return Result.success(AuthPortalDTO.LoginResp.guest(AuthMessageCatalog.LOGIN_REQUIRED));
        }

        // 第一步先校验 session 是否还有效。
        // 这个查询内部会优先走 Redis，再回源数据库。
        TokenSession session = tokenSessionService.findValidByToken(token);
        if (session == null) {
            return Result.success(AuthPortalDTO.LoginResp.guest(AuthMessageCatalog.SESSION_EXPIRED));
        }
        // 第二步再校验用户状态。
        // 这是“先认证、后登录”在运行期的关键补充：旧 token 不能绕过最新状态。
        AppUser user = appUserService.getById(session.getUserId());
        if (!authUserPolicy.canIssueSession(user)) {
            return Result.success(AuthPortalDTO.LoginResp.guest(authUserPolicy.resolveSessionDeniedMessage(user)));
        }
        // session 和用户状态都合法，才返回已登录态。
        return Result.success(buildResp(user, session));
    }

    @Override
    public Result<AuthPortalDTO.LoginResp> refreshMiniappSession(AuthPortalDTO.SessionReq req, HttpServletRequest request) {
        String token = req == null ? null : req.getAccessToken();
        // 先从请求体取 token，没有再去请求头里找。
        if (!StringUtils.hasText(token)) {
            token = AuthContext.resolveToken(request);
        }
        // 没 token 就没有可刷新的会话。
        if (!StringUtils.hasText(token)) {
            return Result.success(AuthPortalDTO.LoginResp.guest(AuthMessageCatalog.LOGIN_REQUIRED));
        }

        // 查当前 token 对应的会话。
        TokenSession current = tokenSessionService.findValidByToken(token);
        if (current == null || current.getUserId() == null) {
            return Result.success(AuthPortalDTO.LoginResp.guest(AuthMessageCatalog.SESSION_EXPIRED));
        }
        // 只允许刷新小程序会话，后台 token 不能混到这个入口。
        if (!AuthDomainConstants.USER_TYPE_MINIAPP.equals(authUserPolicy.normalizeUserType(current.getUserType()))) {
            return Result.success(AuthPortalDTO.LoginResp.guest(AuthMessageCatalog.TYPE_MISMATCH_USE_ADMIN));
        }

        // 再次读取用户最新状态，避免停用后仍可无限续期。
        AppUser user = appUserService.getById(current.getUserId());
        if (!authUserPolicy.canIssueSession(user)) {
            return Result.success(AuthPortalDTO.LoginResp.guest(authUserPolicy.resolveSessionDeniedMessage(user)));
        }

        // 刷新时会尽量保留旧 session 的设备信息，再与本次请求里的信息合并。
        SessionDeviceContext deviceContext = mergeSessionDeviceContext(current, request);
        return issueMiniappSession(user, current.getLoginScene(), deviceContext);
    }

    @Override
    public Result<Void> logout(AuthPortalDTO.SessionReq req, HttpServletRequest request) {
        String token = req == null ? null : req.getAccessToken();
        if (!StringUtils.hasText(token)) {
            token = AuthContext.resolveToken(request);
        }
        tokenSessionService.invalidateToken(token);
        return Result.success(null);
    }

    @Override
    public Result<Void> logoutAll(HttpServletRequest request) {
        AppUser user = resolveLoggedInUser(request);
        if (user == null || user.getId() == null) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), AuthMessageCatalog.LOGIN_REQUIRED);
        }
        tokenSessionService.invalidateByUserId(user.getId());
        return Result.success(null);
    }

    @Override
    public Result<AuthPortalDTO.LoginResp> adminMe(HttpServletRequest request) {
        return normalizeAdminSessionResp(me(request));
    }

    @Override
    public Result<AuthPortalDTO.LoginResp> me(HttpServletRequest request) {
        String token = AuthContext.resolveToken(request);
        if (!StringUtils.hasText(token)) {
            return Result.success(AuthPortalDTO.LoginResp.guest(AuthMessageCatalog.LOGIN_REQUIRED));
        }
        TokenSession session = tokenSessionService.findValidByToken(token);
        if (session == null) {
            return Result.success(AuthPortalDTO.LoginResp.guest(AuthMessageCatalog.SESSION_EXPIRED));
        }
        AppUser user = appUserService.getById(session.getUserId());
        if (!authUserPolicy.canIssueSession(user)) {
            return Result.success(AuthPortalDTO.LoginResp.guest(authUserPolicy.resolveSessionDeniedMessage(user)));
        }
        return Result.success(buildResp(user, session));
    }

    @Override
    public Result<Map<String, Object>> updateAvatar(HttpServletRequest request, AuthPortalDTO.AvatarReq req) {
        AppUser user = resolveLoggedInUser(request);
        if (user == null) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), AuthMessageCatalog.LOGIN_REQUIRED);
        }
        String target = String.valueOf(req.getAvatarUrl() == null ? "" : req.getAvatarUrl()).trim();
        user.setAvatarUrl(target);
        String source = authUserPolicy.normalizeAvatarSource(req.getAvatarSource());
        user.setAvatarSource(source);
        if (AuthDomainConstants.AVATAR_SOURCE_WX.equals(source)) {
            user.setWxAvatarUrl(target);
        }
        appUserService.updateById(user);
        mediaAssetBindingService.bindByUrls("auth", user.getId(), java.util.Arrays.asList(user.getAvatarUrl(), user.getWxAvatarUrl()));
        Map<String, Object> out = new HashMap<String, Object>();
        out.put("avatarUrl", user.getAvatarUrl());
        out.put("avatarSource", user.getAvatarSource());
        out.put("wxAvatarUrl", user.getWxAvatarUrl());
        return Result.success(out);
    }

    @Override
    public Result<Map<String, Object>> updateProfile(HttpServletRequest request, AuthPortalDTO.ProfileReq req) {
        AppUser user = resolveLoggedInUser(request);
        if (user == null) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), AuthMessageCatalog.LOGIN_REQUIRED);
        }
        if (StringUtils.hasText(req.getRealName())) {
            user.setRealName(req.getRealName().trim());
        }
        if (StringUtils.hasText(req.getNickName())) {
            user.setNickName(req.getNickName().trim());
        }
        if (req.getPhone() != null) {
            String phone = String.valueOf(req.getPhone()).trim();
            user.setPhone(StringUtils.hasText(phone) ? phone : null);
        }
        appUserService.updateById(user);
        AppUser latest = appUserService.getById(user.getId());
        Map<String, Object> out = new HashMap<String, Object>();
        out.put("id", latest == null ? null : latest.getId());
        out.put("realName", latest == null ? null : latest.getRealName());
        out.put("nickName", latest == null ? null : latest.getNickName());
        out.put("phone", latest == null ? null : latest.getPhone());
        out.put("avatarUrl", latest == null ? null : latest.getAvatarUrl());
        out.put("avatarSource", latest == null ? null : latest.getAvatarSource());
        out.put("updatedAt", latest == null ? null : latest.getUpdatedAt());
        return Result.success(out);
    }

    @Override
    public Result<Map<String, Object>> changePassword(HttpServletRequest request, AuthPortalDTO.ChangePasswordReq req) {
        AppUser user = resolveLoggedInUser(request);
        if (user == null) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), AuthMessageCatalog.LOGIN_REQUIRED);
        }
        if (!authUserPolicy.isAdminUser(user)) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), AuthMessageCatalog.TYPE_MISMATCH_USE_ADMIN);
        }
        String oldPassword = String.valueOf(req.getOldPassword() == null ? "" : req.getOldPassword());
        String newPassword = String.valueOf(req.getNewPassword() == null ? "" : req.getNewPassword());
        if (!adminPasswordPolicy.matches(oldPassword, user.getPasswordHash(), passwordEncoder)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.ADMIN_PASSWORD_OLD_INVALID);
        }
        if (!adminPasswordPolicy.isStrongPassword(newPassword)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.ADMIN_PASSWORD_WEAK);
        }
        if (adminPasswordPolicy.matches(newPassword, user.getPasswordHash(), passwordEncoder)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.ADMIN_PASSWORD_SAME_AS_OLD);
        }
        user.setPasswordHash(adminPasswordPolicy.encodePassword(newPassword, passwordEncoder));
        appUserService.updateById(user);
        long revokedCount = tokenSessionService.invalidateByUserId(user.getId());
        Map<String, Object> out = new HashMap<String, Object>();
        out.put("userId", user.getId());
        out.put("reloginRequired", Boolean.TRUE);
        out.put("revokedCount", revokedCount);
        return Result.success(out);
    }

    @Override
    public Result<Map<String, Object>> notices(HttpServletRequest request, String noticeType, Boolean unreadOnly, long page, long pageSize) {
        AppUser user = resolveLoggedInUser(request);
        if (user == null) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), AuthMessageCatalog.LOGIN_REQUIRED);
        }
        return Result.success(userNoticeService.queryUserNotices(user.getId(), noticeType, unreadOnly, page, pageSize));
    }

    @Override
    public Result<Void> readNotice(HttpServletRequest request, Long id) {
        AppUser user = resolveLoggedInUser(request);
        if (user == null) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), AuthMessageCatalog.LOGIN_REQUIRED);
        }
        userNoticeService.markRead(user.getId(), id);
        return Result.success(null);
    }

    @Override
    public Result<Void> readAllNotices(HttpServletRequest request) {
        AppUser user = resolveLoggedInUser(request);
        if (user == null) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), AuthMessageCatalog.LOGIN_REQUIRED);
        }
        userNoticeService.markAllRead(user.getId());
        return Result.success(null);
    }

    @Override
    public Result<Void> deleteNotice(HttpServletRequest request, Long id) {
        AppUser user = resolveLoggedInUser(request);
        if (user == null) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), AuthMessageCatalog.LOGIN_REQUIRED);
        }
        userNoticeService.deleteUserNotice(user.getId(), id);
        return Result.success(null);
    }

    private AppUser resolveLoggedInUser(HttpServletRequest request) {
        String token = AuthContext.resolveToken(request);
        if (!StringUtils.hasText(token)) {
            return null;
        }
        TokenSession session = tokenSessionService.findValidByToken(token);
        if (session == null || session.getUserId() == null) {
            return null;
        }
        AppUser user = appUserService.getById(session.getUserId());
        if (!authUserPolicy.canIssueSession(user)) {
            return null;
        }
        return user;
    }

    private Result<AuthPortalDTO.LoginResp> normalizeAdminSessionResp(Result<AuthPortalDTO.LoginResp> result) {
        if (result == null || result.getData() == null || result.getData().getUser() == null) {
            return result;
        }
        String userType = String.valueOf(result.getData().getUser().get(AuthDomainConstants.PROFILE_KEY_USER_TYPE) == null
                ? ""
                : result.getData().getUser().get(AuthDomainConstants.PROFILE_KEY_USER_TYPE)).trim().toLowerCase(Locale.ROOT);
        if (AuthDomainConstants.USER_TYPE_ADMIN.equals(userType)) {
            return result;
        }
        return Result.success(AuthPortalDTO.LoginResp.guest(AuthMessageCatalog.TYPE_MISMATCH_USE_MINIAPP));
    }

    private WeChatOpenIdService.ResolveResult resolveOpenId(String code) {
        WeChatOpenIdService.ResolveResult result = weChatOpenIdService.resolve(code);
        if (result != null && result.isSuccess()) {
            return result;
        }
        String loginCode = String.valueOf(code == null ? "" : code).trim();
        if (!StringUtils.hasText(loginCode)) {
            return result == null ? WeChatOpenIdService.ResolveResult.failure(AuthMessageCatalog.MINIAPP_WECHAT_CODE_REQUIRED) : result;
        }
        if (authProperties == null || !authProperties.isAllowMockOpenIdFallback()) {
            return result == null ? WeChatOpenIdService.ResolveResult.failure(AuthMessageCatalog.MINIAPP_WECHAT_LOGIN_FAILED) : result;
        }
        String fallback = "mock_" + DigestUtil.md5Hex(loginCode).substring(0, 24);
        log.warn("WeChat openId resolve failed, fallback mock openId is used, reason={}",
                result == null ? AuthMessageCatalog.MINIAPP_WECHAT_LOGIN_FAILED : String.valueOf(result.getMessage()));
        return WeChatOpenIdService.ResolveResult.success(fallback);
    }

    private String resolveWechatResolveMessage(WeChatOpenIdService.ResolveResult result) {
        if (result == null || !StringUtils.hasText(result.getMessage())) {
            return AuthMessageCatalog.MINIAPP_WECHAT_LOGIN_FAILED;
        }
        return result.getMessage();
    }

    private Result<AuthPortalDTO.LoginResp> issueMiniappSession(AppUser user, String loginScene, SessionDeviceContext deviceContext) {
        // 真正发 token 的地方集中在这里，避免不同入口自己拼 session 逻辑。
        TokenSession session = tokenSessionService.createSession(
                user.getId(),
                AuthDomainConstants.USER_TYPE_MINIAPP,
                // 场景值在落库前再次归一化，避免脏值写入 session 表。
                miniappLoginScenePolicy.normalizeScene(loginScene),
                deviceContext,
                DEFAULT_SESSION_VALID_DAYS
        );
        return Result.success(buildResp(user, session));
    }

    private SessionDeviceContext mergeSessionDeviceContext(TokenSession current, HttpServletRequest request) {
        SessionDeviceContext requestContext = authDeviceContextResolver.resolve(null, request);
        SessionDeviceContext out = new SessionDeviceContext();
        out.setDeviceId(firstNonBlank(current == null ? null : current.getDeviceId(), requestContext.getDeviceId()));
        out.setDeviceName(firstNonBlank(current == null ? null : current.getDeviceName(), requestContext.getDeviceName()));
        out.setClientIp(firstNonBlank(requestContext.getClientIp(), current == null ? null : current.getClientIp()));
        out.setUserAgent(firstNonBlank(requestContext.getUserAgent(), current == null ? null : current.getUserAgent()));
        return out;
    }

    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        if (StringUtils.hasText(second)) {
            return second.trim();
        }
        return null;
    }

    private AuthPortalDTO.LoginResp buildResp(AppUser user, TokenSession session) {
        // 统一组装前端需要的认证态响应。
        AuthPortalDTO.LoginResp resp = new AuthPortalDTO.LoginResp();
        // 是否已达到“允许登录”的业务状态。
        resp.setApproved(authUserPolicy.isAccountApproved(user));
        // 登录状态字符串，前端通常据此切换“审核中 / 未通过 / 已登录”等页面。
        resp.setLoginStatus(authUserPolicy.resolveLoginStatus(user));
        // 给前端直接展示的人类可读提示。
        resp.setMessage(authUserPolicy.resolveStatusMessage(user));

        // 下面是前端展示所需的用户信息快照。
        Map<String, Object> profile = new HashMap<String, Object>();
        profile.put("id", user.getId());
        profile.put("wxOpenId", user.getWxOpenId());
        profile.put("nickName", user.getNickName());
        profile.put("realName", user.getRealName());
        profile.put("phone", user.getPhone());
        profile.put("status", user.getStatus());
        profile.put("roleCode", user.getRoleCode());
        profile.put("roleName", null);
        profile.put("effectiveRoleCode", null);
        profile.put("menuPermissions", Collections.emptyList());
        profile.put("canConsole", user.getCanConsole());
        profile.put("applyReason", user.getApplyReason());
        profile.put("rejectReason", user.getRejectReason());
        profile.put(AuthDomainConstants.PROFILE_KEY_USER_TYPE, user.getUserType());
        profile.put("avatarUrl", user.getAvatarUrl());
        profile.put("wxAvatarUrl", user.getWxAvatarUrl());
        profile.put("avatarSource", user.getAvatarSource());
        profile.put("enabled", user.getEnabled());
        profile.put("isSuperAdmin", user.getIsSuperAdmin());
        profile.put("createdAt", user.getCreatedAt());
        profile.put("updatedAt", user.getUpdatedAt());
        patchAdminRoleProfile(user, profile);
        resp.setUser(profile);

        if (session != null) {
            // 只有真正发了 session，才把 token 和过期时间带给前端。
            resp.setAccessToken(session.getAccessToken());
            resp.setTokenExpiresAt(session.getExpiresAt().toString());
        }
        return resp;
    }

    private void patchAdminRoleProfile(AppUser user, Map<String, Object> profile) {
        if (profile == null || user == null || !authUserPolicy.isAdminUser(user)) {
            return;
        }
        if (authUserPolicy.isSuperAdmin(user)) {
            String roleCode = adminRoleService.normalizeRoleCode(user.getRoleCode());
            if (!StringUtils.hasText(roleCode)) {
                roleCode = AuthDomainConstants.ROLE_CODE_SUPER_ADMIN;
            }
            AdminRole role = adminRoleService.findByRoleCode(roleCode, true);
            profile.put("roleCode", roleCode);
            profile.put("roleName", role == null ? "超级管理员" : role.getRoleName());
            profile.put("effectiveRoleCode", roleCode);
            profile.put("menuPermissions", Collections.singletonList("*"));
            return;
        }
        String roleCode = adminRoleService.normalizeRoleCode(user.getRoleCode());
        if (!StringUtils.hasText(roleCode)) {
            return;
        }
        AdminRole role = adminRoleService.findByRoleCode(roleCode, true);
        if (role == null) {
            return;
        }
        profile.put("roleCode", roleCode);
        profile.put("roleName", role.getRoleName());
        profile.put("effectiveRoleCode", adminRoleService.resolveEffectiveRoleCode(roleCode));
        profile.put("menuPermissions", adminRoleService.resolveMenuPermissions(roleCode));
    }
}
