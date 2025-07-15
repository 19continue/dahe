package com.dahe.v2.modules.assets.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.assets.domain.AssetDomainConstants;
import com.dahe.v2.modules.assets.model.MediaAsset;
import com.dahe.v2.modules.assets.model.MediaAssetUploadUsage;
import com.dahe.v2.modules.assets.policy.model.MediaAssetPolicyConfig;
import com.dahe.v2.modules.assets.policy.service.MediaAssetPolicyConfigService;
import com.dahe.v2.modules.assets.service.MediaAssetContentValidationService;
import com.dahe.v2.modules.assets.service.MediaAssetService;
import com.dahe.v2.modules.assets.service.MediaAssetUploadFacadeService;
import com.dahe.v2.modules.auth.support.AuthContext;
import com.dahe.v2.modules.user.model.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 资源上传编排门面实现。
 *
 * <p>将 admin 与 miniapp 上传流程统一收口到 service，降低控制器复杂度。</p>
 */
@Service
public class MediaAssetUploadFacadeServiceImpl implements MediaAssetUploadFacadeService {

    private static final Logger log = LoggerFactory.getLogger(MediaAssetUploadFacadeServiceImpl.class);

    private static final DateTimeFormatter DIR_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");
    private static final long MB = AssetDomainConstants.MB;
    private static final int FOLDER_LOCK_TIMEOUT_SECONDS = 5;

    private static final String REVIEW_STATUS_PENDING = AssetDomainConstants.REVIEW_STATUS_PENDING;
    private static final String REVIEW_STATUS_APPROVED = AssetDomainConstants.REVIEW_STATUS_APPROVED;

    private static final String SOURCE_ADMIN_UPLOAD = AssetDomainConstants.SOURCE_ADMIN_UPLOAD;
    private static final String SOURCE_MINIAPP_UPLOAD = AssetDomainConstants.SOURCE_MINIAPP_UPLOAD;
    private static final String SOURCE_OPERATOR_UPLOAD = AssetDomainConstants.SOURCE_OPERATOR_UPLOAD;
    private static final String SOURCE_SYSTEM_UPLOAD = AssetDomainConstants.SOURCE_SYSTEM_UPLOAD;

    private static final String DEFAULT_FOLDER_PATH = AssetDomainConstants.DEFAULT_FOLDER_PATH;
    private static final String LEGACY_DEFAULT_FOLDER_PATH = AssetDomainConstants.LEGACY_DEFAULT_FOLDER_PATH;
    private static final String MINIAPP_UPLOAD_IMAGE_FOLDER_PATH = AssetDomainConstants.MINIAPP_UPLOAD_IMAGE_FOLDER_PATH;
    private static final String FIELD_IMAGE_FOLDER_PATH = AssetDomainConstants.FIELD_IMAGE_FOLDER_PATH;
    private static final String LEGACY_FIELD_IMAGE_FOLDER_PATH = AssetDomainConstants.LEGACY_FIELD_IMAGE_FOLDER_PATH;
    private static final String LEGACY_FIELD_IMAGE_FOLDER_PATH_V2 = AssetDomainConstants.LEGACY_FIELD_IMAGE_FOLDER_PATH_V2;
    private static final String CROP_COVER_FOLDER_PATH = AssetDomainConstants.CROP_COVER_FOLDER_PATH;
    private static final String LEGACY_CROP_COVER_FOLDER_PATH = AssetDomainConstants.LEGACY_CROP_COVER_FOLDER_PATH;

    private final MediaAssetService mediaAssetService;
    private final MediaAssetPolicyConfigService mediaAssetPolicyConfigService;
    private final MediaAssetContentValidationService mediaAssetContentValidationService;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.public-base-url:}")
    private String uploadPublicBaseUrl;

    @Value("${app.upload.public-path-prefix:/uploads}")
    private String uploadPublicPathPrefix;

    public MediaAssetUploadFacadeServiceImpl(
            MediaAssetService mediaAssetService,
            MediaAssetPolicyConfigService mediaAssetPolicyConfigService,
            MediaAssetContentValidationService mediaAssetContentValidationService,
            JdbcTemplate jdbcTemplate
    ) {
        this.mediaAssetService = mediaAssetService;
        this.mediaAssetPolicyConfigService = mediaAssetPolicyConfigService;
        this.mediaAssetContentValidationService = mediaAssetContentValidationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Result<MediaAsset> uploadForAdmin(
            HttpServletRequest request,
            MultipartFile file,
            String moduleKey,
            Long bizId,
            String folderPath,
            String displayName,
            String remark
    ) {
        return upload(request, file, moduleKey, bizId, folderPath, displayName, remark, UploadChannel.ADMIN);
    }

    @Override
    public Result<MediaAsset> uploadForMiniapp(
            HttpServletRequest request,
            MultipartFile file,
            String moduleKey,
            Long bizId,
            String folderPath,
            String displayName,
            String remark
    ) {
        return upload(request, file, moduleKey, bizId, folderPath, displayName, remark, UploadChannel.MINIAPP);
    }

    private Result<MediaAsset> upload(
            HttpServletRequest request,
            MultipartFile file,
            String moduleKey,
            Long bizId,
            String folderPath,
            String displayName,
            String remark,
            UploadChannel channel
    ) {
        if (file == null || file.isEmpty()) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "文件不能为空");
        }
        AppUser uploadUser = AuthContext.getCurrentUser(request);
        Result<Void> permissionError = validateUploadPermission(uploadUser, channel);
        if (permissionError != null) {
            return Result.failure(permissionError.getCode(), permissionError.getMessage());
        }

        String original = file.getOriginalFilename();
        MediaAssetContentValidationService.DetectionResult detectionResult;
        try {
            detectionResult = mediaAssetContentValidationService.validateForUpload(file, original);
        } catch (IllegalArgumentException e) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("资源内容识别失败: original={}, contentType={}", original, file.getContentType(), e);
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "图片内容暂不支持，请重新选择或截图后上传");
        }

        String normalizedModuleKey = normalizeModuleKey(moduleKey);
        String inferredFileType = normalizeFileType(detectionResult.getFileType());
        String sourceType = channel == UploadChannel.MINIAPP ? SOURCE_MINIAPP_UPLOAD : SOURCE_ADMIN_UPLOAD;
        String normalizedFolderPath = normalizeFolderPathBySource(folderPath, sourceType);
        MediaAssetPolicyConfig policy = mediaAssetPolicyConfigService.getOrInit();
        Result<Void> policyError = validateUploadPolicy(sourceType, inferredFileType, file.getSize(), policy);
        if (policyError != null) {
            return Result.failure(policyError.getCode(), policyError.getMessage());
        }

        String safeExt = String.valueOf(detectionResult.getStorageExtension() == null ? "" : detectionResult.getStorageExtension())
                .trim()
                .toLowerCase(Locale.ROOT);
        String fileName = UUID.randomUUID().toString().replace("-", "") + safeExt;
        String month = LocalDate.now().format(DIR_FORMATTER);
        Path dir = Paths.get(uploadDir, month);
        String folderLockKey = buildFolderLockKey(normalizedFolderPath);
        if (!acquireFolderLock(folderLockKey)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "文件夹正被使用，请稍后重试");
        }

        Path target = null;
        boolean uploadSucceeded = false;
        try {
            Files.createDirectories(dir);
            target = dir.resolve(fileName);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            String fileUrl = buildPublicUrl(request, buildUploadPublicPath(month, fileName));

            MediaAsset row = new MediaAsset();
            row.setFileName(resolveDisplayFileName(displayName, original, fileName));
            row.setFileUrl(fileUrl);
            row.setFileType(inferredFileType);
            row.setFolderPath(normalizedFolderPath);
            row.setSourceType(sourceType);
            row.setRecycleFlag(0);
            row.setModuleKey(normalizedModuleKey);
            row.setBizId(bizId);
            row.setSortOrder(mediaAssetService.nextSortOrder());
            row.setSizeBytes(file.getSize());
            row.setRemark(remark);
            applyReviewFields(row, sourceType, uploadUser, policy, true);
            bindCreator(row, uploadUser);
            mediaAssetService.save(row);
            upsertFolderRegistry(normalizedFolderPath, null, uploadUser, isSystemProtectedFolder(normalizedFolderPath));
            uploadSucceeded = true;
            return Result.success(row);
        } catch (IOException e) {
            log.error("文件上传失败: original={}, moduleKey={}, bizId={}", original, normalizedModuleKey, bizId, e);
            return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), "文件上传失败");
        } catch (RuntimeException e) {
            log.error("资源写入失败: original={}, moduleKey={}, bizId={}", original, normalizedModuleKey, bizId, e);
            return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), "资源写入失败");
        } finally {
            if (!uploadSucceeded && target != null) {
                deleteFileQuietly(target);
            }
            releaseFolderLock(folderLockKey);
        }
    }

    private Result<Void> validateUploadPermission(AppUser uploadUser, UploadChannel channel) {
        if (uploadUser == null) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), "请先登录后上传资源");
        }
        String userType = normalizeUserType(uploadUser);
        if (channel == UploadChannel.ADMIN && !"admin".equals(userType)) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), "仅后台用户可通过该接口上传资源");
        }
        if (channel == UploadChannel.MINIAPP && !"miniapp".equals(userType)) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), "仅小程序用户可通过该接口上传资源");
        }
        return null;
    }

    private Result<Void> validateUploadPolicy(String sourceType, String fileType, long incomingSizeBytes, MediaAssetPolicyConfig policy) {
        String normalizedSourceType = normalizeSourceType(sourceType);
        if (!SOURCE_MINIAPP_UPLOAD.equals(normalizedSourceType) && !SOURCE_ADMIN_UPLOAD.equals(normalizedSourceType)) {
            return null;
        }
        MediaAssetPolicyConfig config = policy == null ? mediaAssetPolicyConfigService.getOrInit() : policy;
        String normalizedFileType = normalizeFileType(fileType);
        java.util.Set<String> allowedTypes = mediaAssetPolicyConfigService.resolveAllowedFileTypes(config, normalizedSourceType);
        if (!allowedTypes.contains(normalizedFileType)) {
            return Result.failure(
                    ErrorCode.VALIDATION_ERROR.getCode(),
                    "当前策略不允许上传该类型文件，可上传类型：" + String.join("/", allowedTypes)
            );
        }

        int singleMaxMb = resolveSingleFileMaxMb(config, normalizedSourceType);
        if (singleMaxMb > 0 && incomingSizeBytes > (long) singleMaxMb * MB) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "单文件大小超限，当前上限为" + singleMaxMb + "MB");
        }

        MediaAssetUploadUsage usage = mediaAssetService.queryDailyUsage(normalizedSourceType, LocalDate.now());
        long usageCount = usage == null || usage.getUploadCount() == null ? 0L : usage.getUploadCount();
        long usageSizeBytes = usage == null || usage.getUploadSizeBytes() == null ? 0L : usage.getUploadSizeBytes();

        int dailyLimit = resolveDailyUploadLimit(config, normalizedSourceType);
        if (dailyLimit > 0 && usageCount + 1 > dailyLimit) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "今日上传次数已达上限（" + dailyLimit + "）");
        }

        int dailySizeMb = resolveDailyUploadSizeMb(config, normalizedSourceType);
        if (dailySizeMb > 0 && usageSizeBytes + Math.max(0L, incomingSizeBytes) > (long) dailySizeMb * MB) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "今日上传容量超限，当前上限为" + dailySizeMb + "MB");
        }
        return null;
    }

    private void applyReviewFields(
            MediaAsset row,
            String sourceType,
            AppUser currentUser,
            MediaAssetPolicyConfig policy,
            boolean clearRemarkWhenApproved
    ) {
        if (row == null) {
            return;
        }
        String normalizedSourceType = normalizeSourceType(sourceType);
        MediaAssetPolicyConfig config = policy == null ? mediaAssetPolicyConfigService.getOrInit() : policy;
        int requireReview = resolveRequireReview(config, normalizedSourceType);
        if (requireReview == 1) {
            row.setReviewStatus(REVIEW_STATUS_PENDING);
            row.setReviewedAt(null);
            row.setReviewedByUserId(null);
            if (clearRemarkWhenApproved) {
                row.setReviewRemark(null);
            }
            return;
        }
        row.setReviewStatus(REVIEW_STATUS_APPROVED);
        row.setReviewedAt(LocalDateTime.now());
        row.setReviewedByUserId(currentUser == null ? null : currentUser.getId());
        if (clearRemarkWhenApproved) {
            row.setReviewRemark(null);
        }
    }

    private int resolveDailyUploadLimit(MediaAssetPolicyConfig config, String sourceType) {
        if (config == null) {
            return 0;
        }
        if (SOURCE_ADMIN_UPLOAD.equals(sourceType)) {
            return config.getAdminDailyUploadLimit() == null ? 0 : Math.max(0, config.getAdminDailyUploadLimit());
        }
        return config.getMiniappDailyUploadLimit() == null ? 0 : Math.max(0, config.getMiniappDailyUploadLimit());
    }

    private int resolveDailyUploadSizeMb(MediaAssetPolicyConfig config, String sourceType) {
        if (config == null) {
            return 0;
        }
        if (SOURCE_ADMIN_UPLOAD.equals(sourceType)) {
            return config.getAdminDailyUploadSizeMb() == null ? 0 : Math.max(0, config.getAdminDailyUploadSizeMb());
        }
        return config.getMiniappDailyUploadSizeMb() == null ? 0 : Math.max(0, config.getMiniappDailyUploadSizeMb());
    }

    private int resolveSingleFileMaxMb(MediaAssetPolicyConfig config, String sourceType) {
        if (config == null) {
            return 0;
        }
        if (SOURCE_ADMIN_UPLOAD.equals(sourceType)) {
            return config.getAdminSingleFileMaxMb() == null ? 0 : Math.max(0, config.getAdminSingleFileMaxMb());
        }
        return config.getMiniappSingleFileMaxMb() == null ? 0 : Math.max(0, config.getMiniappSingleFileMaxMb());
    }

    private int resolveRequireReview(MediaAssetPolicyConfig config, String sourceType) {
        if (config == null) {
            return 0;
        }
        if (SOURCE_ADMIN_UPLOAD.equals(sourceType)) {
            return config.getAdminRequireReview() != null && config.getAdminRequireReview() == 1 ? 1 : 0;
        }
        return config.getMiniappRequireReview() != null && config.getMiniappRequireReview() == 1 ? 1 : 0;
    }

    private void bindCreator(MediaAsset row, AppUser user) {
        if (row == null || user == null) {
            return;
        }
        row.setCreatedByUserId(user.getId());
        String createdByName = StringUtils.hasText(user.getRealName()) ? user.getRealName().trim() : user.getNickName();
        row.setCreatedByName(StringUtils.hasText(createdByName) ? createdByName : null);
    }

    private String normalizeSourceType(String sourceType) {
        String text = String.valueOf(sourceType == null ? "" : sourceType).trim().toLowerCase(Locale.ROOT);
        if (SOURCE_MINIAPP_UPLOAD.equals(text) || SOURCE_OPERATOR_UPLOAD.equals(text)) {
            return SOURCE_MINIAPP_UPLOAD;
        }
        if (SOURCE_ADMIN_UPLOAD.equals(text)) {
            return SOURCE_ADMIN_UPLOAD;
        }
        if (SOURCE_SYSTEM_UPLOAD.equals(text)) {
            return SOURCE_SYSTEM_UPLOAD;
        }
        return text;
    }

    private String normalizeUserType(AppUser user) {
        if (user == null || !StringUtils.hasText(user.getUserType())) {
            return "";
        }
        return user.getUserType().trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeModuleKey(String moduleKey) {
        return StringUtils.hasText(moduleKey) ? moduleKey.trim() : null;
    }

    private String normalizeFileType(String fileType) {
        return AssetDomainConstants.normalizeFileType(fileType);
    }

    private String normalizeFolderPath(String raw) {
        String path = StringUtils.hasText(raw) ? raw.trim() : DEFAULT_FOLDER_PATH;
        path = path.replace('\\', '/');
        while (path.contains("//")) {
            path = path.replace("//", "/");
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (!StringUtils.hasText(path)) {
            return DEFAULT_FOLDER_PATH;
        }
        String normalizedLower = path.toLowerCase(Locale.ROOT);
        if (LEGACY_DEFAULT_FOLDER_PATH.equals(normalizedLower)) {
            return DEFAULT_FOLDER_PATH;
        }
        if (LEGACY_FIELD_IMAGE_FOLDER_PATH.equals(normalizedLower)) {
            return FIELD_IMAGE_FOLDER_PATH;
        }
        if (LEGACY_FIELD_IMAGE_FOLDER_PATH_V2.equals(path)) {
            return FIELD_IMAGE_FOLDER_PATH;
        }
        if (LEGACY_CROP_COVER_FOLDER_PATH.equals(normalizedLower)) {
            return CROP_COVER_FOLDER_PATH;
        }
        if (path.length() > 255) {
            return path.substring(0, 255);
        }
        return path;
    }

    private String normalizeFolderPathBySource(String folderPath, String sourceType) {
        String normalizedSourceType = normalizeSourceType(sourceType);
        if (SOURCE_MINIAPP_UPLOAD.equals(normalizedSourceType) || SOURCE_OPERATOR_UPLOAD.equals(normalizedSourceType)) {
            return MINIAPP_UPLOAD_IMAGE_FOLDER_PATH;
        }
        return normalizeFolderPath(folderPath);
    }

    private boolean isSystemProtectedFolder(String folderPath) {
        String path = normalizeFolderPath(folderPath);
        return DEFAULT_FOLDER_PATH.equals(path) || MINIAPP_UPLOAD_IMAGE_FOLDER_PATH.equals(path);
    }

    private void upsertFolderRegistry(String folderPath, String remark, AppUser user, boolean protectedFlag) {
        String normalizedPath = normalizeFolderPath(folderPath);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id AS id, protected_flag AS protectedFlag FROM media_asset_folder WHERE folder_path=? LIMIT 1",
                normalizedPath
        );
        Long userId = user == null ? null : user.getId();
        String userName = user == null ? null : (StringUtils.hasText(user.getRealName()) ? user.getRealName().trim() : user.getNickName());
        String normalizedRemark = StringUtils.hasText(remark) ? remark.trim() : null;
        if (rows.isEmpty()) {
            jdbcTemplate.update(
                    "INSERT INTO media_asset_folder (id,folder_path,remark,protected_flag,created_by_user_id,created_by_name,deleted) " +
                            "VALUES (?,?,?,?,?,?,0)",
                    IdWorker.getId(),
                    normalizedPath,
                    normalizedRemark,
                    protectedFlag ? 1 : 0,
                    userId,
                    userName
            );
            return;
        }
        int prevProtected = toInt(rows.get(0).get("protectedFlag"));
        jdbcTemplate.update(
                "UPDATE media_asset_folder SET deleted=0, protected_flag=?, remark=COALESCE(?, remark) WHERE folder_path=?",
                (protectedFlag || prevProtected == 1) ? 1 : 0,
                normalizedRemark,
                normalizedPath
        );
    }

    private String buildFolderLockKey(String folderPath) {
        String normalizedPath = normalizeFolderPath(folderPath).toLowerCase(Locale.ROOT);
        byte[] digest;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            digest = md.digest(normalizedPath.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            return "dahe:v2:assets:folder:" + Integer.toHexString(normalizedPath.hashCode());
        }
        StringBuilder hex = new StringBuilder(32);
        for (int i = 0; i < digest.length && i < 12; i++) {
            int b = digest[i] & 0xFF;
            if (b < 16) {
                hex.append('0');
            }
            hex.append(Integer.toHexString(b));
        }
        return "dahe:v2:assets:folder:" + hex;
    }

    private boolean acquireFolderLock(String lockKey) {
        if (!StringUtils.hasText(lockKey)) {
            return false;
        }
        try {
            Number state = jdbcTemplate.queryForObject(
                    "SELECT GET_LOCK(?, ?)",
                    Number.class,
                    lockKey,
                    FOLDER_LOCK_TIMEOUT_SECONDS
            );
            return state != null && state.intValue() == 1;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void releaseFolderLock(String lockKey) {
        if (!StringUtils.hasText(lockKey)) {
            return;
        }
        try {
            jdbcTemplate.queryForObject("SELECT RELEASE_LOCK(?)", Number.class, lockKey);
        } catch (Exception ignored) {
            // 释放失败不影响主流程
        }
    }

    private String resolveDisplayFileName(String displayName, String originalName, String fallbackName) {
        String normalizedDisplayName = String.valueOf(displayName == null ? "" : displayName).trim();
        String normalizedOriginalName = String.valueOf(originalName == null ? "" : originalName).trim();
        String normalizedFallbackName = String.valueOf(fallbackName == null ? "" : fallbackName).trim();
        String base = StringUtils.hasText(normalizedDisplayName)
                ? normalizedDisplayName
                : (StringUtils.hasText(normalizedOriginalName) ? normalizedOriginalName : normalizedFallbackName);
        if (!StringUtils.hasText(base)) {
            base = "file";
        }
        String originExt = "";
        int originExtIdx = normalizedOriginalName.lastIndexOf('.');
        if (originExtIdx > 0 && originExtIdx < normalizedOriginalName.length() - 1) {
            originExt = normalizedOriginalName.substring(originExtIdx);
        }
        if (StringUtils.hasText(originExt) && !base.contains(".")) {
            base = base + originExt;
        }
        if (base.length() > 255) {
            base = base.substring(0, 255);
        }
        return base;
    }

    private String buildPublicUrl(HttpServletRequest request, String path) {
        String p = path.startsWith("/") ? path : ("/" + path);
        String configuredBaseUrl = String.valueOf(uploadPublicBaseUrl == null ? "" : uploadPublicBaseUrl).trim();
        if (StringUtils.hasText(configuredBaseUrl)) {
            while (configuredBaseUrl.endsWith("/")) {
                configuredBaseUrl = configuredBaseUrl.substring(0, configuredBaseUrl.length() - 1);
            }
            return configuredBaseUrl + p;
        }

        String scheme = firstForwardedValue(request.getHeader("X-Forwarded-Proto"));
        if (!StringUtils.hasText(scheme)) {
            scheme = request.getScheme();
        }
        String contextPath = StringUtils.hasText(request.getContextPath()) ? request.getContextPath() : "";
        if (StringUtils.hasText(contextPath) && !p.equals(contextPath) && !p.startsWith(contextPath + "/")) {
            p = contextPath + p;
        }

        String forwardedHost = firstForwardedValue(request.getHeader("X-Forwarded-Host"));
        if (StringUtils.hasText(forwardedHost)) {
            String normalizedForwardedHost = normalizeHost(forwardedHost);
            String forwardedPort = firstForwardedValue(request.getHeader("X-Forwarded-Port"));
            if (StringUtils.hasText(forwardedPort) && !hostContainsPort(normalizedForwardedHost)) {
                try {
                    int parsedForwardedPort = Integer.parseInt(forwardedPort);
                    normalizedForwardedHost = appendPort(normalizedForwardedHost, parsedForwardedPort, scheme);
                } catch (NumberFormatException ignored) {
                    // 忽略非法代理端口头
                }
            }
            return scheme + "://" + normalizedForwardedHost + p;
        }

        String host = request.getServerName();
        int port = request.getServerPort();
        String forwardedPort = firstForwardedValue(request.getHeader("X-Forwarded-Port"));
        if (StringUtils.hasText(forwardedPort)) {
            try {
                port = Integer.parseInt(forwardedPort);
            } catch (NumberFormatException ignored) {
                // 忽略非法代理端口头
            }
        }
        boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80) || ("https".equalsIgnoreCase(scheme) && port == 443);
        String portPart = defaultPort ? "" : (":" + port);
        return scheme + "://" + host + portPart + p;
    }

    private String buildUploadPublicPath(String month, String fileName) {
        String prefix = String.valueOf(uploadPublicPathPrefix == null ? "" : uploadPublicPathPrefix).trim();
        if (!StringUtils.hasText(prefix)) {
            prefix = "/uploads";
        }
        if (!prefix.startsWith("/")) {
            prefix = "/" + prefix;
        }
        while (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix + "/" + month + "/" + fileName;
    }

    private String firstForwardedValue(String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            return "";
        }
        String[] segments = headerValue.split(",");
        if (segments.length == 0) {
            return "";
        }
        return String.valueOf(segments[0]).trim();
    }

    private String normalizeHost(String hostValue) {
        String host = String.valueOf(hostValue == null ? "" : hostValue).trim();
        if (!StringUtils.hasText(host)) {
            return "";
        }
        int schemeIndex = host.indexOf("://");
        if (schemeIndex >= 0 && schemeIndex + 3 < host.length()) {
            host = host.substring(schemeIndex + 3);
        }
        int slashIndex = host.indexOf('/');
        if (slashIndex >= 0) {
            host = host.substring(0, slashIndex);
        }
        return host.trim();
    }

    private boolean hostContainsPort(String host) {
        if (!StringUtils.hasText(host)) {
            return false;
        }
        if (host.startsWith("[")) {
            int rightBracket = host.indexOf(']');
            return rightBracket > 0 && rightBracket + 1 < host.length() && host.charAt(rightBracket + 1) == ':';
        }
        int firstColon = host.indexOf(':');
        if (firstColon < 0) {
            return false;
        }
        return firstColon == host.lastIndexOf(':');
    }

    private String appendPort(String host, int port, String scheme) {
        if (!StringUtils.hasText(host) || port <= 0) {
            return host;
        }
        boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80) || ("https".equalsIgnoreCase(scheme) && port == 443);
        if (defaultPort) {
            return host;
        }
        if (host.startsWith("[")) {
            return host + ":" + port;
        }
        if (host.indexOf(':') < 0) {
            return host + ":" + port;
        }
        if (host.indexOf(':') == host.lastIndexOf(':')) {
            return host + ":" + port;
        }
        return "[" + host + "]:" + port;
    }

    private void deleteFileQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // 清理失败不影响主流程
        }
    }

    private int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private enum UploadChannel {
        ADMIN,
        MINIAPP
    }
}
