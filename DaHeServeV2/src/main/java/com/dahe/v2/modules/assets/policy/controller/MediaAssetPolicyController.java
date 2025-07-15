package com.dahe.v2.modules.assets.policy.controller;

import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.assets.domain.AssetDomainConstants;
import com.dahe.v2.modules.assets.model.MediaAssetUploadUsage;
import com.dahe.v2.modules.assets.policy.model.MediaAssetPolicyConfig;
import com.dahe.v2.modules.assets.policy.service.MediaAssetPolicyConfigService;
import com.dahe.v2.modules.assets.service.MediaAssetService;
import com.dahe.v2.modules.auth.policy.AuthUserPolicy;
import com.dahe.v2.modules.auth.support.AuthContext;
import com.dahe.v2.modules.user.model.AppUser;
import lombok.Data;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.Min;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

    /**
 * 资源上传策略管理接口。
 * 说明：
 * 1. 仅后台角色可查看/修改策略；
 * 2. 同时返回当日用量，便于管理端在同一页面观测策略与使用情况。
 */
@RestController
@RequestMapping("/api/v2")
@Validated
public class MediaAssetPolicyController {

    private static final String SOURCE_MINIAPP_UPLOAD = AssetDomainConstants.SOURCE_MINIAPP_UPLOAD;
    private static final String SOURCE_OPERATOR_UPLOAD = AssetDomainConstants.SOURCE_OPERATOR_UPLOAD;
    private static final String SOURCE_ADMIN_UPLOAD = AssetDomainConstants.SOURCE_ADMIN_UPLOAD;
    private static final long MB = AssetDomainConstants.MB;
    private static final int LOCK_PASSWORD_MIN_LENGTH = 6;
    private static final int LOCK_PASSWORD_MAX_LENGTH = 32;

    private final MediaAssetPolicyConfigService mediaAssetPolicyConfigService;
    private final MediaAssetService mediaAssetService;
    private final AuthUserPolicy authUserPolicy;
    private final PasswordEncoder passwordEncoder;

    public MediaAssetPolicyController(
            MediaAssetPolicyConfigService mediaAssetPolicyConfigService,
            MediaAssetService mediaAssetService,
            AuthUserPolicy authUserPolicy,
            PasswordEncoder passwordEncoder
    ) {
        this.mediaAssetPolicyConfigService = mediaAssetPolicyConfigService;
        this.mediaAssetService = mediaAssetService;
        this.authUserPolicy = authUserPolicy;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/admin/asset-policy")
    /** 查询当前生效策略（附带今日 miniapp/admin 用量）。 */
    public Result<PolicyResp> current() {
        MediaAssetPolicyConfig row = mediaAssetPolicyConfigService.getOrInit();
        MediaAssetUploadUsage miniappUsage = mediaAssetService.queryDailyUsage(SOURCE_MINIAPP_UPLOAD, LocalDate.now());
        MediaAssetUploadUsage adminUsage = mediaAssetService.queryDailyUsage(SOURCE_ADMIN_UPLOAD, LocalDate.now());
        return Result.success(PolicyResp.from(
                row,
                miniappUsage,
                adminUsage,
                mediaAssetPolicyConfigService.resolveAllowedFileTypes(row, SOURCE_MINIAPP_UPLOAD),
                mediaAssetPolicyConfigService.resolveAllowedFileTypes(row, SOURCE_ADMIN_UPLOAD)
        ));
    }

    @PutMapping("/admin/asset-policy")
    /** 更新策略配置。未传字段保持原值，传入字段会做归一化。 */
    public Result<PolicyResp> update(@RequestBody @Validated UpdateReq req) {
        MediaAssetPolicyConfig row = mediaAssetPolicyConfigService.getOrInit();

        if (req.getMiniappDailyUploadLimit() != null) {
            row.setMiniappDailyUploadLimit(Math.max(0, req.getMiniappDailyUploadLimit()));
        }
        if (req.getMiniappDailyUploadSizeMb() != null) {
            row.setMiniappDailyUploadSizeMb(Math.max(0, req.getMiniappDailyUploadSizeMb()));
        }
        if (req.getMiniappSingleFileMaxMb() != null) {
            row.setMiniappSingleFileMaxMb(Math.max(0, req.getMiniappSingleFileMaxMb()));
        }
        if (req.getMiniappRequireReview() != null) {
            row.setMiniappRequireReview(req.getMiniappRequireReview() == 1 ? 1 : 0);
        }
        if (req.getMiniappAllowedFileTypes() != null) {
            row.setMiniappAllowedFileTypes(joinAllowedTypes(req.getMiniappAllowedFileTypes()));
        }

        if (req.getAdminDailyUploadLimit() != null) {
            row.setAdminDailyUploadLimit(Math.max(0, req.getAdminDailyUploadLimit()));
        }
        if (req.getAdminDailyUploadSizeMb() != null) {
            row.setAdminDailyUploadSizeMb(Math.max(0, req.getAdminDailyUploadSizeMb()));
        }
        if (req.getAdminSingleFileMaxMb() != null) {
            row.setAdminSingleFileMaxMb(Math.max(0, req.getAdminSingleFileMaxMb()));
        }
        if (req.getAdminRequireReview() != null) {
            row.setAdminRequireReview(req.getAdminRequireReview() == 1 ? 1 : 0);
        }
        if (req.getAdminAllowedFileTypes() != null) {
            row.setAdminAllowedFileTypes(joinAllowedTypes(req.getAdminAllowedFileTypes()));
        }

        if (req.getStrictSourcePurgeRetainDays() != null) {
            row.setStrictSourcePurgeRetainDays(Math.max(1, req.getStrictSourcePurgeRetainDays()));
        }
        row.setRemark(StringUtils.hasText(req.getRemark()) ? req.getRemark().trim() : null);

        // 历史 operator_* 列保持与 miniapp 同步，避免历史脏数据反向污染。
        row.setOperatorDailyUploadLimit(row.getMiniappDailyUploadLimit());
        row.setOperatorDailyUploadSizeMb(row.getMiniappDailyUploadSizeMb());
        row.setOperatorSingleFileMaxMb(row.getMiniappSingleFileMaxMb());
        row.setOperatorAllowedFileTypes(row.getMiniappAllowedFileTypes());
        row.setOperatorRequireReview(row.getMiniappRequireReview());
        mediaAssetPolicyConfigService.updateById(row);

        MediaAssetPolicyConfig latest = mediaAssetPolicyConfigService.getOrInit();
        MediaAssetUploadUsage miniappUsage = mediaAssetService.queryDailyUsage(SOURCE_MINIAPP_UPLOAD, LocalDate.now());
        MediaAssetUploadUsage adminUsage = mediaAssetService.queryDailyUsage(SOURCE_ADMIN_UPLOAD, LocalDate.now());
        return Result.success(PolicyResp.from(
                latest,
                miniappUsage,
                adminUsage,
                mediaAssetPolicyConfigService.resolveAllowedFileTypes(latest, SOURCE_MINIAPP_UPLOAD),
                mediaAssetPolicyConfigService.resolveAllowedFileTypes(latest, SOURCE_ADMIN_UPLOAD)
        ));
    }

    @PutMapping("/admin/asset-policy/lock-password")
    /** 设置或重置全局资源锁密码。 */
    public Result<PolicyResp> updateLockPassword(
            HttpServletRequest request,
            @RequestBody @Validated LockPasswordReq req
    ) {
        AppUser currentUser = AuthContext.getCurrentUser(request);
        if (!authUserPolicy.isSuperAdmin(currentUser)) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), "仅超级管理员可设置或重置全局资源锁密码");
        }
        String password = String.valueOf(req.getPassword() == null ? "" : req.getPassword()).trim();
        if (!StringUtils.hasText(password)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "请输入全局资源锁密码");
        }
        if (password.length() < LOCK_PASSWORD_MIN_LENGTH || password.length() > LOCK_PASSWORD_MAX_LENGTH) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "全局资源锁密码长度需为" + LOCK_PASSWORD_MIN_LENGTH + "-" + LOCK_PASSWORD_MAX_LENGTH + "位");
        }
        MediaAssetPolicyConfig row = mediaAssetPolicyConfigService.getOrInit();
        row.setLockPasswordHash(passwordEncoder.encode(password));
        row.setLockPasswordUpdatedAt(java.time.LocalDateTime.now());
        row.setLockPasswordUpdatedByUserId(currentUser == null ? null : currentUser.getId());
        row.setLockPasswordUpdatedByName(resolveOperatorName(currentUser));
        mediaAssetPolicyConfigService.updateById(row);

        MediaAssetPolicyConfig latest = mediaAssetPolicyConfigService.getOrInit();
        MediaAssetUploadUsage miniappUsage = mediaAssetService.queryDailyUsage(SOURCE_MINIAPP_UPLOAD, LocalDate.now());
        MediaAssetUploadUsage adminUsage = mediaAssetService.queryDailyUsage(SOURCE_ADMIN_UPLOAD, LocalDate.now());
        return Result.success(PolicyResp.from(
                latest,
                miniappUsage,
                adminUsage,
                mediaAssetPolicyConfigService.resolveAllowedFileTypes(latest, SOURCE_MINIAPP_UPLOAD),
                mediaAssetPolicyConfigService.resolveAllowedFileTypes(latest, SOURCE_ADMIN_UPLOAD)
        ));
    }

    @GetMapping("/admin/asset-policy/usage")
    /** 查询指定日期、指定来源的上传用量。 */
    public Result<UsageResp> usage(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String sourceType
    ) {
        LocalDate usageDate = LocalDate.now();
        if (StringUtils.hasText(date)) {
            try {
                usageDate = LocalDate.parse(date.trim());
            } catch (Exception e) {
                return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "日期格式应为 yyyy-MM-dd");
            }
        }
        String normalizedSourceType = normalizeSourceType(sourceType);
        MediaAssetPolicyConfig row = mediaAssetPolicyConfigService.getOrInit();
        MediaAssetUploadUsage usage = mediaAssetService.queryDailyUsage(normalizedSourceType, usageDate);
        return Result.success(UsageResp.from(row, usage, normalizedSourceType));
    }

    /** 将文件类型列表归一化为逗号串，仅保留 image/file。 */
    private String joinAllowedTypes(List<String> raw) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String token : raw) {
            String item = String.valueOf(token == null ? "" : token).trim().toLowerCase(Locale.ROOT);
            if (AssetDomainConstants.FILE_TYPE_IMAGE.equals(item) || AssetDomainConstants.FILE_TYPE_FILE.equals(item)) {
                normalized.add(item);
            }
        }
        if (normalized.isEmpty()) {
            normalized.add(AssetDomainConstants.FILE_TYPE_IMAGE);
            normalized.add(AssetDomainConstants.FILE_TYPE_FILE);
        }
        return String.join(",", normalized);
    }

    /** 来源归一化：历史 operator_upload 视同 miniapp，默认按 miniapp 处理。 */
    private String normalizeSourceType(String sourceType) {
        String text = String.valueOf(sourceType == null ? "" : sourceType).trim().toLowerCase(Locale.ROOT);
        if (SOURCE_ADMIN_UPLOAD.equals(text)) {
            return SOURCE_ADMIN_UPLOAD;
        }
        if (SOURCE_OPERATOR_UPLOAD.equals(text) || SOURCE_MINIAPP_UPLOAD.equals(text) || !StringUtils.hasText(text)) {
            return SOURCE_MINIAPP_UPLOAD;
        }
        return SOURCE_MINIAPP_UPLOAD;
    }

    @Data
    public static class UpdateReq {

        /** 小程序端每日上传次数上限。 */
        @Min(0)
        private Integer miniappDailyUploadLimit;

        /** 小程序端每日上传容量上限（MB）。 */
        @Min(0)
        private Integer miniappDailyUploadSizeMb;

        /** 小程序端单文件大小上限（MB）。 */
        @Min(0)
        private Integer miniappSingleFileMaxMb;

        /** 小程序端允许类型列表。 */
        private List<String> miniappAllowedFileTypes;

        /** 小程序端是否需要审核：1 需要，0 不需要。 */
        private Integer miniappRequireReview;

        /** 后台端每日上传次数上限。 */
        @Min(0)
        private Integer adminDailyUploadLimit;

        /** 后台端每日上传容量上限（MB）。 */
        @Min(0)
        private Integer adminDailyUploadSizeMb;

        /** 后台端单文件大小上限（MB）。 */
        @Min(0)
        private Integer adminSingleFileMaxMb;

        /** 后台端允许类型列表。 */
        private List<String> adminAllowedFileTypes;

        /** 后台端是否需要审核：1 需要，0 不需要。 */
        private Integer adminRequireReview;

        /** 严格来源资源在回收站保留天数（最小 1 天）。 */
        @Min(1)
        private Integer strictSourcePurgeRetainDays;

        /** 策略备注。 */
        private String remark;
    }

    @Data
    public static class LockPasswordReq {
        /** 全局资源锁密码。 */
        private String password;
    }

    @Data
    public static class PolicyResp {
        /** 小程序端每日上传次数上限。 */
        private Integer miniappDailyUploadLimit;
        /** 小程序端每日上传容量上限（MB）。 */
        private Integer miniappDailyUploadSizeMb;
        /** 小程序端单文件大小上限（MB）。 */
        private Integer miniappSingleFileMaxMb;
        /** 小程序端允许类型。 */
        private List<String> miniappAllowedFileTypes;
        /** 小程序端是否需要审核。 */
        private Integer miniappRequireReview;

        /** 后台端每日上传次数上限。 */
        private Integer adminDailyUploadLimit;
        /** 后台端每日上传容量上限（MB）。 */
        private Integer adminDailyUploadSizeMb;
        /** 后台端单文件大小上限（MB）。 */
        private Integer adminSingleFileMaxMb;
        /** 后台端允许类型。 */
        private List<String> adminAllowedFileTypes;
        /** 后台端是否需要审核。 */
        private Integer adminRequireReview;

        /** 严格来源资源回收站保留天数。 */
        private Integer strictSourcePurgeRetainDays;
        /** 策略备注。 */
        private String remark;
        /** 是否已设置全局资源锁密码。 */
        private Boolean hasLockPassword;
        /** 全局资源锁密码最近更新时间。 */
        private String lockPasswordUpdatedAt;
        /** 全局资源锁密码最近更新人。 */
        private String lockPasswordUpdatedByName;
        /** 小程序端今日用量。 */
        private UsageResp miniappTodayUsage;
        /** 后台端今日用量。 */
        private UsageResp adminTodayUsage;

        /** 将策略实体和用量实体合成响应对象。 */
        static PolicyResp from(
                MediaAssetPolicyConfig row,
                MediaAssetUploadUsage miniappUsage,
                MediaAssetUploadUsage adminUsage,
                Set<String> miniappAllowedTypes,
                Set<String> adminAllowedTypes
        ) {
            PolicyResp out = new PolicyResp();
            out.setMiniappDailyUploadLimit(row == null ? 0 : safeInt(row.getMiniappDailyUploadLimit()));
            out.setMiniappDailyUploadSizeMb(row == null ? 0 : safeInt(row.getMiniappDailyUploadSizeMb()));
            out.setMiniappSingleFileMaxMb(row == null ? 0 : safeInt(row.getMiniappSingleFileMaxMb()));
            out.setMiniappRequireReview(row == null ? 0 : normalizeFlag(row.getMiniappRequireReview()));
            out.setMiniappAllowedFileTypes(toList(miniappAllowedTypes));

            out.setAdminDailyUploadLimit(row == null ? 0 : safeInt(row.getAdminDailyUploadLimit()));
            out.setAdminDailyUploadSizeMb(row == null ? 0 : safeInt(row.getAdminDailyUploadSizeMb()));
            out.setAdminSingleFileMaxMb(row == null ? 0 : safeInt(row.getAdminSingleFileMaxMb()));
            out.setAdminRequireReview(row == null ? 0 : normalizeFlag(row.getAdminRequireReview()));
            out.setAdminAllowedFileTypes(toList(adminAllowedTypes));

            out.setStrictSourcePurgeRetainDays(row == null ? 7 : Math.max(1, safeInt(row.getStrictSourcePurgeRetainDays())));
            out.setRemark(row == null ? null : row.getRemark());
            out.setHasLockPassword(row != null && StringUtils.hasText(row.getLockPasswordHash()));
            out.setLockPasswordUpdatedAt(row == null || row.getLockPasswordUpdatedAt() == null ? null : row.getLockPasswordUpdatedAt().toString());
            out.setLockPasswordUpdatedByName(row == null ? null : trimToNull(row.getLockPasswordUpdatedByName()));
            out.setMiniappTodayUsage(UsageResp.from(row, miniappUsage, SOURCE_MINIAPP_UPLOAD));
            out.setAdminTodayUsage(UsageResp.from(row, adminUsage, SOURCE_ADMIN_UPLOAD));
            return out;
        }

        /** 将 Set 输出为稳定 List；为空时兜底 image/file。 */
        private static List<String> toList(Set<String> rows) {
            if (rows == null || rows.isEmpty()) {
                List<String> out = new ArrayList<>();
                out.add("image");
                out.add("file");
                return out;
            }
            return new ArrayList<>(rows);
        }

        /** 将可空整型安全转换为非负整型。 */
        private static int safeInt(Integer value) {
            return value == null ? 0 : Math.max(0, value);
        }

        /** 将审核标记规整为 0/1。 */
        private static int normalizeFlag(Integer value) {
            return value != null && value == 1 ? 1 : 0;
        }

        private static String trimToNull(String value) {
            String text = String.valueOf(value == null ? "" : value).trim();
            return StringUtils.hasText(text) ? text : null;
        }
    }

    @Data
    public static class UsageResp {
        /** 来源类型：admin_upload/miniapp_upload。 */
        private String sourceType;
        /** 统计日期。 */
        private String usageDate;
        /** 已上传次数。 */
        private Long uploadCount;
        /** 已上传容量（字节）。 */
        private Long uploadSizeBytes;
        /** 次数上限。 */
        private Integer countLimit;
        /** 容量上限（MB）。 */
        private Integer sizeLimitMb;
        /** 次数使用率（0-100）。 */
        private Integer countUsageRate;
        /** 容量使用率（0-100）。 */
        private Integer sizeUsageRate;
        /** 剩余可上传次数。不限额时为 null。 */
        private Long remainCount;
        /** 剩余可上传容量（字节）。不限额时为 null。 */
        private Long remainSizeBytes;

        /** 组装用量响应并计算使用率/剩余额度。 */
        static UsageResp from(MediaAssetPolicyConfig row, MediaAssetUploadUsage usage, String sourceType) {
            UsageResp out = new UsageResp();
            String normalizedSourceType = SOURCE_ADMIN_UPLOAD.equals(sourceType) ? SOURCE_ADMIN_UPLOAD : SOURCE_MINIAPP_UPLOAD;
            out.setSourceType(normalizedSourceType);
            out.setUsageDate(usage != null && usage.getUsageDate() != null ? usage.getUsageDate().toString() : LocalDate.now().toString());
            long uploadCount = usage == null || usage.getUploadCount() == null ? 0L : usage.getUploadCount();
            long uploadSizeBytes = usage == null || usage.getUploadSizeBytes() == null ? 0L : usage.getUploadSizeBytes();
            out.setUploadCount(uploadCount);
            out.setUploadSizeBytes(uploadSizeBytes);

            int countLimit;
            int sizeLimitMb;
            if (SOURCE_ADMIN_UPLOAD.equals(normalizedSourceType)) {
                countLimit = row == null ? 0 : Math.max(0, row.getAdminDailyUploadLimit() == null ? 0 : row.getAdminDailyUploadLimit());
                sizeLimitMb = row == null ? 0 : Math.max(0, row.getAdminDailyUploadSizeMb() == null ? 0 : row.getAdminDailyUploadSizeMb());
            } else {
                countLimit = row == null ? 0 : Math.max(0, row.getMiniappDailyUploadLimit() == null ? 0 : row.getMiniappDailyUploadLimit());
                sizeLimitMb = row == null ? 0 : Math.max(0, row.getMiniappDailyUploadSizeMb() == null ? 0 : row.getMiniappDailyUploadSizeMb());
            }
            out.setCountLimit(countLimit);
            out.setSizeLimitMb(sizeLimitMb);

            if (countLimit > 0) {
                out.setCountUsageRate((int) Math.min(100, Math.round(uploadCount * 100.0d / countLimit)));
                out.setRemainCount(Math.max(0L, countLimit - uploadCount));
            } else {
                out.setCountUsageRate(0);
                out.setRemainCount(null);
            }

            if (sizeLimitMb > 0) {
                long sizeLimitBytes = sizeLimitMb * MB;
                out.setSizeUsageRate((int) Math.min(100, Math.round(uploadSizeBytes * 100.0d / sizeLimitBytes)));
                out.setRemainSizeBytes(Math.max(0L, sizeLimitBytes - uploadSizeBytes));
            } else {
                out.setSizeUsageRate(0);
                out.setRemainSizeBytes(null);
            }
            return out;
        }
    }

    private String resolveOperatorName(AppUser user) {
        if (user == null) {
            return null;
        }
        String nickName = trimToNull(user.getNickName());
        if (StringUtils.hasText(nickName)) {
            return nickName;
        }
        String realName = trimToNull(user.getRealName());
        if (StringUtils.hasText(realName)) {
            return realName;
        }
        return trimToNull(user.getLoginName());
    }

    private String trimToNull(String value) {
        String text = String.valueOf(value == null ? "" : value).trim();
        return StringUtils.hasText(text) ? text : null;
    }
}
