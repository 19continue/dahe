package com.dahe.v2.modules.user.service.impl;

import com.dahe.v2.modules.user.domain.UserDomainConstants;
import com.dahe.v2.modules.user.model.AppUser;
import com.dahe.v2.modules.user.service.AppUserService;
import com.dahe.v2.modules.user.service.UserDomainService;
import com.dahe.v2.modules.assets.service.MediaAssetBindingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 用户领域写服务实现。
 *
 * <p>统一承接 user 关键写入逻辑，降低上层服务对字段细节的耦合。</p>
 */
@Service
public class UserDomainServiceImpl implements UserDomainService {

    private final AppUserService appUserService;
    private final MediaAssetBindingService mediaAssetBindingService;

    public UserDomainServiceImpl(AppUserService appUserService, MediaAssetBindingService mediaAssetBindingService) {
        this.appUserService = appUserService;
        this.mediaAssetBindingService = mediaAssetBindingService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MiniappLoginUpsertResult upsertMiniappUserFromLogin(MiniappLoginCommand command) {
        if (command == null || !StringUtils.hasText(command.getWxOpenId())) {
            throw new IllegalArgumentException("wxOpenId is required");
        }
        String wxOpenId = command.getWxOpenId().trim();
        AppUser user = appUserService.findByWxOpenId(wxOpenId);
        MiniappLoginUpsertResult out = new MiniappLoginUpsertResult();

        if (user == null) {
            user = new AppUser();
            user.setWxOpenId(wxOpenId);
            user.setNickName(trimToNull(command.getNickName()));
            user.setRealName(trimToNull(command.getRealName()));
            user.setPhone(trimToNull(command.getPhone()));
            user.setApplyReason(trimToNull(command.getApplyReason()));
            user.setStatus(UserDomainConstants.REVIEW_STATUS_PENDING);
            // 小程序用户不参与后台角色体系，roleCode 保持空值。
            user.setRoleCode(null);
            user.setCanConsole(0);
            user.setUserType(UserDomainConstants.USER_TYPE_MINIAPP);
            user.setEnabled(1);
            user.setIsSuperAdmin(0);
            applyAvatarForLogin(user, command.getAvatarUrl(), command.getWxAvatarUrl(), command.getAvatarSource());
            appUserService.save(user);
            syncMiniappAvatarBinding(user);
            out.setReviewApplied(true);
            out.setReApply(false);
            out.setUser(user);
            return out;
        }

        patchMiniappProfile(user, command);
        if (!StringUtils.hasText(user.getUserType())) {
            user.setUserType(UserDomainConstants.USER_TYPE_MINIAPP);
        }
        if (UserDomainConstants.USER_TYPE_MINIAPP.equalsIgnoreCase(user.getUserType())) {
            user.setRoleCode(null);
        }
        if (user.getEnabled() == null) {
            user.setEnabled(1);
        }
        if (user.getIsSuperAdmin() == null) {
            user.setIsSuperAdmin(0);
        }
        if (!StringUtils.hasText(user.getAvatarSource())) {
            user.setAvatarSource(UserDomainConstants.AVATAR_SOURCE_NONE);
        }

        String status = normalize(user.getStatus());
        if (!StringUtils.hasText(status)) {
            user.setStatus(UserDomainConstants.REVIEW_STATUS_PENDING);
            out.setReviewApplied(true);
            out.setReApply(false);
        } else if (UserDomainConstants.REVIEW_STATUS_REJECTED.equals(status)
                || UserDomainConstants.REVIEW_STATUS_REVOKED.equals(status)) {
            user.setStatus(UserDomainConstants.REVIEW_STATUS_PENDING);
            user.setRejectReason(null);
            out.setReviewApplied(true);
            out.setReApply(true);
        } else if (UserDomainConstants.REVIEW_STATUS_BLACKLISTED.equals(status)) {
            throw new IllegalStateException("您没有申请资格，请联系管理员");
        } else {
            out.setReviewApplied(false);
            out.setReApply(false);
        }

        appUserService.updateById(user);
        syncMiniappAvatarBinding(user);
        out.setUser(user);
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppUser applyMiniappReviewDecision(AppUser user, boolean approve, Boolean canConsole, String rejectReason) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("user is required");
        }
        if (approve) {
            user.setStatus(UserDomainConstants.REVIEW_STATUS_APPROVED);
            user.setRejectReason(null);
            user.setRoleCode(null);
            user.setCanConsole(Boolean.TRUE.equals(canConsole) ? 1 : 0);
        } else {
            user.setStatus(UserDomainConstants.REVIEW_STATUS_REJECTED);
            user.setRejectReason(rejectReason == null ? "资料不全" : trimToNull(rejectReason));
            user.setRoleCode(null);
            if (canConsole != null) {
                user.setCanConsole(canConsole ? 1 : 0);
            }
        }
        appUserService.updateById(user);
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppUser applyMiniappConsole(AppUser user, Boolean canConsole) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("user is required");
        }
        user.setRoleCode(null);
        if (canConsole != null) {
            user.setCanConsole(canConsole ? 1 : 0);
        }
        appUserService.updateById(user);
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppUser applyAdminRoleAndConsole(AppUser user, String roleCode, Boolean canConsole) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("user is required");
        }
        if (StringUtils.hasText(roleCode)) {
            user.setRoleCode(roleCode.trim());
        }
        if (canConsole != null) {
            user.setCanConsole(canConsole ? 1 : 0);
        }
        appUserService.updateById(user);
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppUser applyEnabled(AppUser user, boolean enabled) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("user is required");
        }
        user.setEnabled(enabled ? 1 : 0);
        appUserService.updateById(user);
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppUser applyMiniappStatus(AppUser user, String status) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("user is required");
        }
        String normalized = normalize(status);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("status is required");
        }
        user.setStatus(normalized);
        if (UserDomainConstants.REVIEW_STATUS_APPROVED.equals(normalized)) {
            user.setRejectReason(null);
        } else if (UserDomainConstants.REVIEW_STATUS_PENDING.equals(normalized)) {
            user.setRejectReason(null);
            user.setCanConsole(0);
        } else if (UserDomainConstants.REVIEW_STATUS_REVOKED.equals(normalized)
                || UserDomainConstants.REVIEW_STATUS_BLACKLISTED.equals(normalized)) {
            user.setRejectReason(null);
            user.setRoleCode(null);
            user.setCanConsole(0);
        }
        appUserService.updateById(user);
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppUser createAdminUser(AdminCreateCommand command) {
        if (command == null
                || !StringUtils.hasText(command.getWxOpenId())
                || !StringUtils.hasText(command.getLoginName())
                || !StringUtils.hasText(command.getPasswordHash())
                || !StringUtils.hasText(command.getRealName())) {
            throw new IllegalArgumentException("admin create command is invalid");
        }
        AppUser user = new AppUser();
        user.setWxOpenId(command.getWxOpenId().trim());
        user.setLoginName(trimToNull(command.getLoginName()));
        user.setPasswordHash(trimToNull(command.getPasswordHash()));
        user.setWxUnionId(null);
        user.setRealName(command.getRealName().trim());
        user.setNickName(StringUtils.hasText(command.getNickName()) ? command.getNickName().trim() : command.getRealName().trim());
        user.setPhone(trimToNull(command.getPhone()));
        user.setStatus(null);
        user.setRoleCode(trimToNull(command.getRoleCode()));
        user.setCanConsole(Boolean.FALSE.equals(command.getCanConsole()) ? 0 : 1);
        user.setUserType(UserDomainConstants.USER_TYPE_ADMIN);
        user.setAvatarUrl(trimToNull(command.getAvatarUrl()));
        user.setWxAvatarUrl(null);
        user.setAvatarSource(StringUtils.hasText(command.getAvatarUrl()) ? UserDomainConstants.AVATAR_SOURCE_ADMIN : UserDomainConstants.AVATAR_SOURCE_NONE);
        user.setEnabled(1);
        user.setIsSuperAdmin(0);
        user.setApplyReason(null);
        user.setRejectReason(null);
        appUserService.save(user);
        return user;
    }

    private void patchMiniappProfile(AppUser user, MiniappLoginCommand command) {
        if (StringUtils.hasText(command.getNickName())) {
            user.setNickName(command.getNickName().trim());
        }
        if (StringUtils.hasText(command.getRealName())) {
            user.setRealName(command.getRealName().trim());
        }
        if (StringUtils.hasText(command.getPhone())) {
            user.setPhone(command.getPhone().trim());
        }
        if (StringUtils.hasText(command.getApplyReason())) {
            user.setApplyReason(command.getApplyReason().trim());
        }
        applyAvatarForLogin(user, command.getAvatarUrl(), command.getWxAvatarUrl(), command.getAvatarSource());
    }

    private void applyAvatarForLogin(AppUser user, String avatarUrl, String wxAvatarUrl, String avatarSource) {
        if (StringUtils.hasText(wxAvatarUrl)) {
            String wxAvatar = wxAvatarUrl.trim();
            user.setWxAvatarUrl(wxAvatar);
            if (!StringUtils.hasText(user.getAvatarUrl())
                    || UserDomainConstants.AVATAR_SOURCE_WX.equals(normalize(user.getAvatarSource()))) {
                user.setAvatarUrl(wxAvatar);
                user.setAvatarSource(UserDomainConstants.AVATAR_SOURCE_WX);
            }
        }
        if (StringUtils.hasText(avatarUrl)) {
            String target = avatarUrl.trim();
            user.setAvatarUrl(target);
            user.setAvatarSource(normalizeAvatarSource(avatarSource));
        } else if (!StringUtils.hasText(user.getAvatarSource())) {
            user.setAvatarSource(UserDomainConstants.AVATAR_SOURCE_NONE);
        }
    }

    private String normalizeAvatarSource(String raw) {
        String normalized = normalize(raw);
        if (UserDomainConstants.AVATAR_SOURCE_WX.equals(normalized)
                || UserDomainConstants.AVATAR_SOURCE_UPLOAD.equals(normalized)
                || UserDomainConstants.AVATAR_SOURCE_ADMIN.equals(normalized)) {
            return normalized;
        }
        return UserDomainConstants.AVATAR_SOURCE_NONE;
    }

    private String normalize(String raw) {
        return String.valueOf(raw == null ? "" : raw).trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim();
    }

    private void syncMiniappAvatarBinding(AppUser user) {
        if (user == null || user.getId() == null || user.getId() <= 0) {
            return;
        }
        List<String> urls = new ArrayList<String>();
        if (StringUtils.hasText(user.getAvatarUrl())) {
            urls.add(user.getAvatarUrl());
        }
        if (StringUtils.hasText(user.getWxAvatarUrl())) {
            urls.add(user.getWxAvatarUrl());
        }
        mediaAssetBindingService.bindByUrls("auth", user.getId(), urls);
    }
}
