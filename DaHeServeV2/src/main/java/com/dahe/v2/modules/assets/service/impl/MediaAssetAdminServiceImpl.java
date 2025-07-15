package com.dahe.v2.modules.assets.service.impl;

import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.assets.domain.AssetDomainConstants;
import com.dahe.v2.modules.assets.model.MediaAsset;
import com.dahe.v2.modules.assets.policy.model.MediaAssetPolicyConfig;
import com.dahe.v2.modules.assets.policy.service.MediaAssetPolicyConfigService;
import com.dahe.v2.modules.assets.service.MediaAssetAdminService;
import com.dahe.v2.modules.assets.service.MediaAssetService;
import com.dahe.v2.modules.auth.domain.AuthMessageCatalog;
import com.dahe.v2.modules.auth.policy.AuthUserPolicy;
import com.dahe.v2.modules.user.model.AppUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 资源后台治理服务实现。
 *
 * <p>本服务只处理后台“治理动作”，不承载查询与上传流程：</p>
 * <p>1. 资源锁：全局密码存放在上传策略中，锁定资源删除到回收站或解除锁定时都需密码校验；</p>
 * <p>2. 驳回：驳回即进入回收站，避免误审核后资源直接丢失；</p>
 * <p>3. 彻底删除：继续保留严格来源与保留期约束。</p>
 */
@Service
public class MediaAssetAdminServiceImpl implements MediaAssetAdminService {

    private final MediaAssetService mediaAssetService;
    private final MediaAssetPolicyConfigService mediaAssetPolicyConfigService;
    private final AuthUserPolicy authUserPolicy;
    private final PasswordEncoder passwordEncoder;

    public MediaAssetAdminServiceImpl(
            MediaAssetService mediaAssetService,
            MediaAssetPolicyConfigService mediaAssetPolicyConfigService,
            AuthUserPolicy authUserPolicy,
            PasswordEncoder passwordEncoder
    ) {
        this.mediaAssetService = mediaAssetService;
        this.mediaAssetPolicyConfigService = mediaAssetPolicyConfigService;
        this.authUserPolicy = authUserPolicy;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public Result<Void> recycleAsset(Long id, String unlockPassword, AppUser operator) {
        MediaAsset row = mediaAssetService.getById(id);
        if (row == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        Result<Void> unlockError = validateDeleteUnlock(row, unlockPassword, "移入回收站");
        if (unlockError != null) {
            return unlockError;
        }
        Long userId = operator == null ? null : operator.getId();
        mediaAssetService.markRecycled(id, userId);
        return Result.success(null);
    }

    @Override
    @Transactional
    public Result<Void> restoreAsset(Long id) {
        MediaAsset row = mediaAssetService.getById(id);
        if (row == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        mediaAssetService.restoreFromRecycle(id);
        return Result.success(null);
    }

    @Override
    @Transactional
    public Result<MediaAsset> reviewAsset(Long id, String reviewStatus, String reviewRemark, String expectedUpdatedAt, AppUser operator) {
        MediaAsset row = mediaAssetService.getById(id);
        if (row == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        if (!sameUpdatedAt(row.getUpdatedAt(), expectedUpdatedAt)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), AuthMessageCatalog.ASSET_REVIEW_CONFLICT);
        }
        if (Objects.equals(row.getRecycleFlag(), 1)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "回收站中的资源不可审核");
        }
        String normalizedSourceType = AssetDomainConstants.normalizeSourceType(row.getSourceType());
        String normalizedReviewStatus = AssetDomainConstants.normalizeReviewStatus(reviewStatus);
        String normalizedRemark = trimToNull(reviewRemark);
        if (!AssetDomainConstants.SOURCE_MINIAPP_UPLOAD.equals(normalizedSourceType)
                && !AssetDomainConstants.REVIEW_STATUS_APPROVED.equals(normalizedReviewStatus)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "仅小程序用户上传资源支持待审核或驳回");
        }
        row.setReviewStatus(normalizedReviewStatus);
        row.setReviewRemark(normalizedRemark);
        if (AssetDomainConstants.REVIEW_STATUS_PENDING.equals(normalizedReviewStatus)) {
            row.setReviewedAt(null);
            row.setReviewedByUserId(null);
        } else {
            row.setReviewedAt(LocalDateTime.now());
            row.setReviewedByUserId(operator == null ? null : operator.getId());
        }
        if (AssetDomainConstants.REVIEW_STATUS_REJECTED.equals(normalizedReviewStatus)) {
            row.setRecycleFlag(1);
            row.setRecycledAt(LocalDateTime.now());
            row.setRecycledByUserId(operator == null ? null : operator.getId());
            row.setModuleKey(null);
            row.setBizId(null);
        }
        mediaAssetService.updateById(row);
        return Result.success(mediaAssetService.getById(id));
    }

    @Override
    @Transactional
    public Result<Void> purgeAsset(Long id, String unlockPassword, AppUser operator) {
        MediaAsset row = mediaAssetService.getById(id);
        if (row == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        if (!Objects.equals(row.getRecycleFlag(), 1)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "资源需先移入回收站后才能彻底删除");
        }
        String sourceType = AssetDomainConstants.normalizeSourceType(row.getSourceType());
        if (AssetDomainConstants.isStrictSourceType(sourceType)) {
            if (!authUserPolicy.isSuperAdmin(operator)) {
                return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), "小程序用户上传资源仅超级管理员可彻底删除");
            }
            MediaAssetPolicyConfig policy = mediaAssetPolicyConfigService.getOrInit();
            int retainDays = policy == null || policy.getStrictSourcePurgeRetainDays() == null
                    ? 7
                    : Math.max(0, policy.getStrictSourcePurgeRetainDays());
            LocalDateTime recycledAt = row.getRecycledAt();
            if (recycledAt != null && recycledAt.plusDays(retainDays).isAfter(LocalDateTime.now())) {
                return Result.failure(
                        ErrorCode.VALIDATION_ERROR.getCode(),
                        "小程序用户上传资源回收站保留期为" + retainDays + "天，保留期内不可彻底删除"
                );
            }
        }
        mediaAssetService.purgeAsset(id);
        return Result.success(null);
    }

    @Override
    @Transactional
    public Result<MediaAsset> updateAssetLock(Long id, Integer locked, String unlockPassword, String lockRemark, AppUser operator) {
        MediaAsset row = mediaAssetService.getById(id);
        if (row == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        if (!authUserPolicy.isSuperAdmin(operator)) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), "仅超级管理员可设置或解除资源锁");
        }
        int nextLocked = locked != null && locked == 1 ? 1 : 0;
        MediaAssetPolicyConfig policy = mediaAssetPolicyConfigService.getOrInit();
        String normalizedRemark = trimToNull(lockRemark);
        if (nextLocked == 1) {
            if (!hasGlobalLockPassword(policy)) {
                return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "请先在资源上传策略中设置全局资源锁密码");
            }
            row.setLockedFlag(1);
            row.setLockRemark(normalizedRemark);
        } else {
            Result<Void> unlockError = validateDeleteUnlock(row, unlockPassword, "解除资源锁");
            if (unlockError != null) {
                return Result.failure(unlockError.getCode(), unlockError.getMessage());
            }
            row.setLockedFlag(0);
            row.setLockRemark(null);
        }
        row.setLockPasswordHash(null);
        row.setLockUpdatedAt(LocalDateTime.now());
        row.setLockUpdatedByUserId(operator == null ? null : operator.getId());
        row.setLockUpdatedByName(resolveOperatorName(operator));
        mediaAssetService.updateById(row);
        return Result.success(mediaAssetService.getById(id));
    }

    private Result<Void> validateDeleteUnlock(MediaAsset row, String unlockPassword, String actionLabel) {
        if (row == null || !isLocked(row)) {
            return null;
        }
        MediaAssetPolicyConfig policy = mediaAssetPolicyConfigService.getOrInit();
        if (!hasGlobalLockPassword(policy)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "该资源已被锁定，请先由超级管理员设置全局资源锁密码");
        }
        String normalizedPassword = trimToNull(unlockPassword);
        if (!StringUtils.hasText(normalizedPassword)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "该资源已被锁定，" + actionLabel + "前请输入解锁密码");
        }
        if (!passwordEncoder.matches(normalizedPassword, policy.getLockPasswordHash())) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "资源锁密码错误，无法继续" + actionLabel);
        }
        return null;
    }

    private boolean isLocked(MediaAsset row) {
        return row != null
                && row.getLockedFlag() != null
                && row.getLockedFlag() == 1;
    }

    private boolean hasGlobalLockPassword(MediaAssetPolicyConfig policy) {
        return policy != null && StringUtils.hasText(policy.getLockPasswordHash());
    }

    private String trimToNull(String value) {
        String text = String.valueOf(value == null ? "" : value).trim();
        return text.isEmpty() ? null : text;
    }

    private String resolveOperatorName(AppUser operator) {
        if (operator == null) {
            return null;
        }
        String nickName = trimToNull(operator.getNickName());
        if (StringUtils.hasText(nickName)) {
            return nickName;
        }
        String realName = trimToNull(operator.getRealName());
        if (StringUtils.hasText(realName)) {
            return realName;
        }
        return trimToNull(operator.getLoginName());
    }

    private boolean sameUpdatedAt(LocalDateTime actual, String expected) {
        if (!StringUtils.hasText(expected)) {
            return true;
        }
        String actualText = actual == null ? "" : actual.toString();
        String expectedText = String.valueOf(expected == null ? "" : expected).trim();
        return Objects.equals(actualText, expectedText);
    }
}
