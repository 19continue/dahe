package com.dahe.v2.modules.assets.controller;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.assets.domain.AssetDomainConstants;
import com.dahe.v2.modules.assets.model.MediaAsset;
import com.dahe.v2.modules.assets.model.MediaAssetUploadUsage;
import com.dahe.v2.modules.assets.model.MediaAssetReferenceStats;
import com.dahe.v2.modules.assets.model.MediaAssetStats;
import com.dahe.v2.modules.assets.policy.model.MediaAssetPolicyConfig;
import com.dahe.v2.modules.assets.policy.service.MediaAssetPolicyConfigService;
import com.dahe.v2.modules.assets.service.MediaAssetContentValidationService;
import com.dahe.v2.modules.assets.service.MediaAssetAdminService;
import com.dahe.v2.modules.assets.service.MediaAssetReferenceService;
import com.dahe.v2.modules.assets.service.MediaAssetService;
import com.dahe.v2.modules.assets.service.MediaAssetUploadFacadeService;
import com.dahe.v2.modules.auth.support.AdminMenuCode;
import com.dahe.v2.modules.auth.support.AuthContext;
import com.dahe.v2.modules.crop.model.Crop;
import com.dahe.v2.modules.crop.service.CropService;
import com.dahe.v2.modules.export.model.ExportTemplate;
import com.dahe.v2.modules.export.service.ExportTemplateService;
import com.dahe.v2.modules.field.model.Field;
import com.dahe.v2.modules.field.service.FieldService;
import com.dahe.v2.modules.farm.model.FarmRecord;
import com.dahe.v2.modules.farm.service.FarmRecordService;
import com.dahe.v2.modules.seed.model.SeedBatch;
import com.dahe.v2.modules.seed.service.SeedBatchService;
import com.dahe.v2.modules.user.model.AppUser;
import com.dahe.v2.modules.user.service.AppUserService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.io.File;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

    /**
 * 资源管理控制器。
 * 主要职责：
 * 1. 提供资源上传、链接创建、编辑、回收站、彻底删除等接口；
 * 2. 承接资源策略校验（类型、次数、容量、审核）；
 * 3. 提供目录管理与统计聚合能力。
 */
@RestController
@RequestMapping("/api/v2")
@Validated
public class MediaAssetController {

    private static final DateTimeFormatter DIR_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");
    private static final String REVIEW_STATUS_PENDING = AssetDomainConstants.REVIEW_STATUS_PENDING;
    private static final String REVIEW_STATUS_APPROVED = AssetDomainConstants.REVIEW_STATUS_APPROVED;
    private static final String REVIEW_STATUS_REJECTED = AssetDomainConstants.REVIEW_STATUS_REJECTED;
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
    private static final String FIELD_IMAGE_FOLDER_REMARK = AssetDomainConstants.FIELD_IMAGE_FOLDER_REMARK;
    private static final String CROP_COVER_FOLDER_PATH = AssetDomainConstants.CROP_COVER_FOLDER_PATH;
    private static final String LEGACY_CROP_COVER_FOLDER_PATH = AssetDomainConstants.LEGACY_CROP_COVER_FOLDER_PATH;
    private static final String CROP_COVER_FOLDER_REMARK = AssetDomainConstants.CROP_COVER_FOLDER_REMARK;
    private static final long MB = AssetDomainConstants.MB;
    private static final int FOLDER_LOCK_TIMEOUT_SECONDS = 5;

    private final MediaAssetService mediaAssetService;
    private final MediaAssetAdminService mediaAssetAdminService;
    private final MediaAssetPolicyConfigService mediaAssetPolicyConfigService;
    private final MediaAssetContentValidationService mediaAssetContentValidationService;
    private final MediaAssetUploadFacadeService mediaAssetUploadFacadeService;
    private final MediaAssetReferenceService mediaAssetReferenceService;
    private final FieldService fieldService;
    private final FarmRecordService farmRecordService;
    private final CropService cropService;
    private final SeedBatchService seedBatchService;
    private final ExportTemplateService exportTemplateService;
    private final AppUserService appUserService;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.public-base-url:}")
    private String uploadPublicBaseUrl;

    @Value("${app.upload.public-path-prefix:/uploads}")
    private String uploadPublicPathPrefix;

    public MediaAssetController(
            MediaAssetService mediaAssetService,
            MediaAssetAdminService mediaAssetAdminService,
            MediaAssetPolicyConfigService mediaAssetPolicyConfigService,
            MediaAssetContentValidationService mediaAssetContentValidationService,
            MediaAssetUploadFacadeService mediaAssetUploadFacadeService,
            MediaAssetReferenceService mediaAssetReferenceService,
            FieldService fieldService,
            FarmRecordService farmRecordService,
            CropService cropService,
            SeedBatchService seedBatchService,
            ExportTemplateService exportTemplateService,
            AppUserService appUserService,
            JdbcTemplate jdbcTemplate
    ) {
        this.mediaAssetService = mediaAssetService;
        this.mediaAssetAdminService = mediaAssetAdminService;
        this.mediaAssetPolicyConfigService = mediaAssetPolicyConfigService;
        this.mediaAssetContentValidationService = mediaAssetContentValidationService;
        this.mediaAssetUploadFacadeService = mediaAssetUploadFacadeService;
        this.mediaAssetReferenceService = mediaAssetReferenceService;
        this.fieldService = fieldService;
        this.farmRecordService = farmRecordService;
        this.cropService = cropService;
        this.seedBatchService = seedBatchService;
        this.exportTemplateService = exportTemplateService;
        this.appUserService = appUserService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/assets")
    /** 前台可用资源分页（默认仅正常资源 + 严格来源仅展示审核通过）。 */
    public Result<Page<MediaAsset>> pageAssets(
            @RequestParam(required = false) String moduleKey,
            @RequestParam(required = false) String fileType,
            @RequestParam(required = false) Long bizId,
            @RequestParam(required = false) String folderPath,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false, defaultValue = "0") Integer recycleFlag,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) long pageSize
    ) {
        return Result.success(mediaAssetService.pageAssets(
                null,
                moduleKey,
                fileType,
                bizId,
                folderPath,
                sourceType,
                0,
                null,
                reviewStatus,
                true,
                page,
                pageSize
        ));
    }

    @GetMapping("/admin/assets")
    @AdminMenuCode({"/assets", "/assets/library", "/assets/recycle", "/assets/review"})
    /** 后台资源分页（支持关键词、回收站、审核状态等完整筛选）。 */
    public Result<Page<MediaAsset>> adminPageAssets(
            HttpServletRequest request,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String moduleKey,
            @RequestParam(required = false) String fileType,
            @RequestParam(required = false) Long bizId,
            @RequestParam(required = false) String folderPath,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false, defaultValue = "0") Integer recycleFlag,
            @RequestParam(required = false) Integer lockedFlag,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) long pageSize
    ) {
        return Result.success(mediaAssetService.pageAssets(
                keyword,
                moduleKey,
                fileType,
                bizId,
                folderPath,
                sourceType,
                recycleFlag,
                lockedFlag,
                reviewStatus,
                false,
                page,
                pageSize
        ));
    }

    @PostMapping("/admin/assets/link")
    @AdminMenuCode({"/assets", "/assets/library"})
    /** 后台创建外链资源（不写本地文件，仅落库）。 */
    public Result<MediaAsset> createLink(HttpServletRequest request, @RequestBody @Validated LinkCreateReq req) {
        AppUser currentUser = AuthContext.getCurrentUser(request);
        if (!StringUtils.hasText(req.getFileUrl())) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "文件链接不能为空");
        }
        MediaAsset row = new MediaAsset();
        row.setFileName(StringUtils.hasText(req.getFileName()) ? req.getFileName().trim() : req.getFileUrl().trim());
        row.setFileUrl(req.getFileUrl().trim());
        row.setFileType(normalizeFileType(StringUtils.hasText(req.getFileType()) ? req.getFileType().trim() : inferTypeByName(row.getFileName())));
        String sourceType = resolveSourceType(currentUser);
        String normalizedFolderPath = normalizeFolderPathBySource(req.getFolderPath(), sourceType);
        row.setFolderPath(normalizedFolderPath);
        row.setSourceType(sourceType);
        row.setRecycleFlag(0);
        row.setSizeBytes(0L);
        row.setModuleKey(normalizeModuleKey(req.getModuleKey()));
        row.setBizId(req.getBizId());
        row.setSortOrder(mediaAssetService.nextSortOrder());
        row.setRemark(req.getRemark());
        MediaAssetPolicyConfig policy = mediaAssetPolicyConfigService.getOrInit();
        // 外链资源按“0 字节”参与策略校验（次数限制仍生效）。
        Result<Void> policyError = validateUploadPolicy(sourceType, row.getFileType(), 0L, policy);
        if (policyError != null) {
            return Result.failure(policyError.getCode(), policyError.getMessage());
        }
        String folderLockKey = buildFolderLockKey(normalizedFolderPath);
        if (!acquireFolderLock(folderLockKey)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "文件夹正被使用，请稍后重试");
        }
        try {
            applyReviewFields(row, sourceType, currentUser, policy, false);
            bindCreator(row, currentUser);
            mediaAssetService.save(row);
            upsertFolderRegistry(normalizedFolderPath, null, currentUser, isSystemProtectedFolder(normalizedFolderPath));
            return Result.success(row);
        } finally {
            releaseFolderLock(folderLockKey);
        }
    }

    @PostMapping("/files/upload")
    /** 上传二进制文件到本地目录并登记资源记录。 */
    public Result<MediaAsset> upload(
            HttpServletRequest request,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String moduleKey,
            @RequestParam(required = false) Long bizId,
            @RequestParam(required = false) String folderPath,
            @RequestParam(required = false) String displayName,
            @RequestParam(required = false) String remark
    ) {
        return mediaAssetUploadFacadeService.uploadForAdmin(
                request,
                file,
                moduleKey,
                bizId,
                folderPath,
                displayName,
                remark
        );
    }

    @PutMapping("/admin/assets/{id}")
    @AdminMenuCode({"/assets", "/assets/library"})
    /** 后台编辑资源基础信息（文件名/目录/备注）。 */
    public Result<MediaAsset> updateAsset(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody @Validated AssetUpdateReq req
    ) {
        MediaAsset row = mediaAssetService.getById(id);
        if (row == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        if (Objects.equals(row.getRecycleFlag(), 1)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "回收站中的资源不可编辑");
        }
        String fileName = String.valueOf(req.getFileName() == null ? "" : req.getFileName()).trim();
        if (!StringUtils.hasText(fileName)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "文件名不能为空");
        }
        if (fileName.length() > 255) {
            fileName = fileName.substring(0, 255);
        }
        String sourceType = normalizeSourceType(row.getSourceType());
        String folderPath = normalizeFolderPathBySource(req.getFolderPath(), sourceType);
        String folderLockKey = buildFolderLockKey(folderPath);
        if (!acquireFolderLock(folderLockKey)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "文件夹正被使用，请稍后重试");
        }
        try {
            row.setFileName(fileName);
            row.setFolderPath(folderPath);
            row.setRemark(StringUtils.hasText(req.getRemark()) ? req.getRemark().trim() : null);
            mediaAssetService.updateById(row);
            AppUser current = AuthContext.getCurrentUser(request);
            upsertFolderRegistry(folderPath, null, current, isSystemProtectedFolder(folderPath));
            return Result.success(mediaAssetService.getById(id));
        } finally {
            releaseFolderLock(folderLockKey);
        }
    }

    @DeleteMapping("/admin/assets/{id}")
    @AdminMenuCode({"/assets", "/assets/library", "/assets/recycle"})
    /** 后台删除资源（软删除：移入回收站）。 */
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id, @RequestBody(required = false) AssetDeleteReq req) {
        AppUser currentUser = AuthContext.getCurrentUser(request);
        return mediaAssetAdminService.recycleAsset(id, req == null ? null : req.getUnlockPassword(), currentUser);
    }

    @PostMapping("/admin/assets/{id}/restore")
    @AdminMenuCode({"/assets", "/assets/recycle"})
    /** 后台恢复回收站资源。 */
    public Result<Void> restore(HttpServletRequest request, @PathVariable Long id) {
        return mediaAssetAdminService.restoreAsset(id);
    }

    @PutMapping("/admin/assets/{id}/review")
    @AdminMenuCode("/assets/review")
    /** 后台审核资源（pending/approved/rejected）。 */
    public Result<MediaAsset> review(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody @Validated ReviewReq req
    ) {
        AppUser currentUser = AuthContext.getCurrentUser(request);
        return mediaAssetAdminService.reviewAsset(id, req.getReviewStatus(), req.getReviewRemark(), req.getExpectedUpdatedAt(), currentUser);
    }

    @DeleteMapping("/admin/assets/{id}/purge")
    @AdminMenuCode({"/assets", "/assets/recycle"})
    /** 后台彻底删除资源（需先在回收站，且严格来源受保留期约束）。 */
    public Result<Void> purge(HttpServletRequest request, @PathVariable Long id, @RequestBody(required = false) AssetDeleteReq req) {
        AppUser currentUser = AuthContext.getCurrentUser(request);
        Result<Void> result = mediaAssetAdminService.purgeAsset(id, req == null ? null : req.getUnlockPassword(), currentUser);
        return result.getCode() == Result.SUCCESS_CODE ? Result.success(null) : result;
    }

    @PutMapping("/admin/assets/{id}/lock")
    @AdminMenuCode({"/assets", "/assets/library", "/assets/recycle"})
    /** 后台设置或重置资源锁。 */
    public Result<MediaAsset> updateAssetLock(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody @Validated AssetLockReq req
    ) {
        AppUser currentUser = AuthContext.getCurrentUser(request);
        return mediaAssetAdminService.updateAssetLock(id, req.getLocked(), req.getUnlockPassword(), req.getLockRemark(), currentUser);
    }

    @PostMapping("/admin/assets/reorder")
    @AdminMenuCode({"/assets", "/assets/library"})
    /** 后台手工调整资源排序。 */
    public Result<Void> reorder(HttpServletRequest request, @RequestBody @Validated ReorderReq req) {
        mediaAssetService.reorder(req.getIds());
        return Result.success(null);
    }

    @GetMapping("/admin/assets/stats")
    @AdminMenuCode("/assets")
    /** 后台资源总览统计。 */
    public Result<MediaAssetStats> stats(HttpServletRequest request) {
        return Result.success(mediaAssetService.stats());
    }

    @GetMapping("/admin/assets/references")
    @AdminMenuCode("/assets")
    /** 后台资源引用统计，并补充业务标签。 */
    public Result<MediaAssetReferenceStats> references(HttpServletRequest request) {
        MediaAssetReferenceStats out = mediaAssetService.referenceStats();
        enrichBizLabels(out);
        return Result.success(out);
    }

    @GetMapping("/admin/assets/{id}/reference-details")
    @AdminMenuCode({"/assets", "/assets/library", "/assets/recycle"})
    /** 后台查询单个资源的业务引用详情，用于删除前风险提示。 */
    public Result<Map<String, Object>> referenceDetails(HttpServletRequest request, @PathVariable Long id) {
        if (id == null || id <= 0) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "资源ID无效");
        }
        return referenceDetails(request, id, 0L, 5);
    }

    @GetMapping("/admin/assets/{id}/reference-details/page")
    @AdminMenuCode({"/assets", "/assets/library", "/assets/recycle"})
    /** 后台分页查询单个资源的业务引用详情，用于删除前风险提示。 */
    public Result<Map<String, Object>> referenceDetails(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") long offset,
            @RequestParam(defaultValue = "5") int limit
    ) {
        if (id == null || id <= 0) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "资源ID无效");
        }
        long safeOffset = Math.max(0L, offset);
        int safeLimit = Math.max(1, Math.min(limit, 20));
        long total = mediaAssetReferenceService.countReferenceDetails(id);
        List<Map<String, Object>> refs = mediaAssetReferenceService.listReferenceDetails(id, safeOffset, safeLimit);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("referenced", total > 0);
        out.put("total", total);
        out.put("offset", safeOffset);
        out.put("limit", safeLimit);
        out.put("hasMore", safeOffset + (refs == null ? 0 : refs.size()) < total);
        out.put("nextOffset", safeOffset + (refs == null ? 0 : refs.size()));
        out.put("references", buildReferenceDetailItems(refs));
        return Result.success(out);
    }

    @GetMapping("/admin/assets/folders")
    @AdminMenuCode({"/assets", "/assets/library", "/assets/recycle", "/assets/review", "/assets/folders"})
    /** 后台查询文件夹路径集合。 */
    public Result<List<String>> folderPaths(
            HttpServletRequest request,
            @RequestParam(required = false, defaultValue = "0") Integer recycleFlag
    ) {
        return Result.success(mediaAssetService.listFolderPaths(recycleFlag));
    }

    @GetMapping("/admin/assets/folders/manage")
    @AdminMenuCode({"/assets", "/assets/folders"})
    /** 后台文件夹管理列表（含资源占用、删除可行性）。 */
    public Result<List<FolderManageResp>> folderManage(
            HttpServletRequest request,
            @RequestParam(required = false) String keyword
    ) {
        return Result.success(loadFolderManageRows(keyword));
    }

    @PostMapping("/admin/assets/folders")
    @AdminMenuCode({"/assets", "/assets/folders"})
    /** 后台创建或激活文件夹（写入 media_asset_folder）。 */
    public Result<FolderManageResp> createFolder(
            HttpServletRequest request,
            @RequestBody @Validated FolderCreateReq req
    ) {
        if (req == null || !StringUtils.hasText(req.getFolderPath())) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "文件夹路径不能为空");
        }
        String folderPath = normalizeFolderPath(req.getFolderPath());
        AppUser current = AuthContext.getCurrentUser(request);
        String folderLockKey = buildFolderLockKey(folderPath);
        if (!acquireFolderLock(folderLockKey)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "文件夹正被使用，请稍后重试");
        }
        try {
            upsertFolderRegistry(folderPath, req.getRemark(), current, isSystemProtectedFolder(folderPath));
            return Result.success(findFolderRow(folderPath));
        } finally {
            releaseFolderLock(folderLockKey);
        }
    }

    @DeleteMapping("/admin/assets/folders")
    @AdminMenuCode({"/assets", "/assets/folders"})
    /** 后台删除文件夹（仅允许空目录且非保护目录）。 */
    public Result<Void> deleteFolder(
            HttpServletRequest request,
            @RequestParam String folderPath
    ) {
        String normalizedPath = normalizeFolderPath(folderPath);
        if (DEFAULT_FOLDER_PATH.equals(normalizedPath)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "默认文件夹不可删除");
        }
        if (MINIAPP_UPLOAD_IMAGE_FOLDER_PATH.equals(normalizedPath)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "小程序上传图片文件夹不可删除");
        }
        String folderLockKey = buildFolderLockKey(normalizedPath);
        if (!acquireFolderLock(folderLockKey)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "文件夹正被使用，请稍后重试");
        }
        try {
            FolderUsageStats usage = queryFolderUsage(normalizedPath);
            if (usage.miniappAssetCount > 0) {
                return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "包含小程序上传资源的文件夹不可删除");
            }
            if (usage.totalAssetCount > 0) {
                return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "文件夹仍有资源，请先清空");
            }
            List<Map<String, Object>> existedRows;
            if (FIELD_IMAGE_FOLDER_PATH.equals(normalizedPath)) {
                existedRows = jdbcTemplate.queryForList(
                        "SELECT protected_flag AS protectedFlag FROM media_asset_folder WHERE folder_path IN (?,?,?) AND deleted=0 LIMIT 1",
                        FIELD_IMAGE_FOLDER_PATH,
                        LEGACY_FIELD_IMAGE_FOLDER_PATH,
                        LEGACY_FIELD_IMAGE_FOLDER_PATH_V2
                );
            } else if (CROP_COVER_FOLDER_PATH.equals(normalizedPath)) {
                existedRows = jdbcTemplate.queryForList(
                        "SELECT protected_flag AS protectedFlag FROM media_asset_folder WHERE folder_path IN (?,?) AND deleted=0 LIMIT 1",
                        CROP_COVER_FOLDER_PATH,
                        LEGACY_CROP_COVER_FOLDER_PATH
                );
            } else {
                existedRows = jdbcTemplate.queryForList(
                        "SELECT protected_flag AS protectedFlag FROM media_asset_folder WHERE folder_path=? AND deleted=0 LIMIT 1",
                        normalizedPath
                );
            }
            if (existedRows.isEmpty()) {
                return Result.failure(ErrorCode.NOT_FOUND.getCode(), "文件夹不存在");
            }
            int protectedFlag = toInt(existedRows.get(0).get("protectedFlag"));
            if (protectedFlag == 1) {
                return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "系统保护文件夹不可删除");
            }

            int affected = markFolderDeletedIfNoAssets(normalizedPath);
            if (affected <= 0) {
                FolderUsageStats latestUsage = queryFolderUsage(normalizedPath);
                if (latestUsage.totalAssetCount > 0) {
                    return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "文件夹仍有资源，请先清空");
                }
                return Result.failure(ErrorCode.NOT_FOUND.getCode(), "文件夹不存在或已删除");
            }
            return Result.success(null);
        } finally {
            releaseFolderLock(folderLockKey);
        }
    }

    /**
     * 组装资源引用详情。
     */
    private List<AssetReferenceDetailItem> buildReferenceDetailItems(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> fieldIds = new HashSet<>();
        Set<Long> farmIds = new HashSet<>();
        Set<Long> cropIds = new HashSet<>();
        Set<Long> seedIds = new HashSet<>();
        Set<Long> exportIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        Set<Long> companyInfoIds = new HashSet<>();
        Set<Long> companyProductIds = new HashSet<>();
        Set<Long> companyHonorIds = new HashSet<>();
        for (Map<String, Object> row : rows) {
            Long bizId = toNullableLong(row == null ? null : row.get("bizId"));
            if (bizId == null) {
                continue;
            }
            String moduleKey = normalizeReferenceModuleKey(String.valueOf(row.get("moduleKey") == null ? "" : row.get("moduleKey")));
            switch (moduleKey) {
                case "field":
                    fieldIds.add(bizId);
                    break;
                case "farm":
                    farmIds.add(bizId);
                    break;
                case "crop":
                    cropIds.add(bizId);
                    break;
                case "seed":
                    seedIds.add(bizId);
                    break;
                case "export":
                    exportIds.add(bizId);
                    break;
                case "auth":
                    userIds.add(bizId);
                    break;
                case "company_info":
                    companyInfoIds.add(bizId);
                    break;
                case "company_product":
                    companyProductIds.add(bizId);
                    break;
                case "company_honor":
                    companyHonorIds.add(bizId);
                    break;
                default:
                    break;
            }
        }

        Map<Long, Field> fieldMap = toFieldMap(fieldIds);
        Map<Long, FarmRecord> farmMap = toFarmRecordMap(farmIds);
        Map<Long, Crop> cropMap = toCropMap(cropIds);
        Map<Long, SeedBatch> seedMap = toSeedBatchMap(seedIds);
        Map<Long, ExportTemplate> exportMap = toExportTemplateMap(exportIds);
        Map<Long, AppUser> userMap = toUserMap(userIds);
        Map<Long, String> companyInfoMap = toCompanyInfoMap(companyInfoIds);
        Map<Long, String> companyProductMap = toCompanyProductMap(companyProductIds);
        Map<Long, String> companyHonorMap = toCompanyHonorMap(companyHonorIds);

        List<AssetReferenceDetailItem> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            Long bizId = toNullableLong(row.get("bizId"));
            if (bizId == null) {
                continue;
            }
            String moduleKey = normalizeReferenceModuleKey(String.valueOf(row.get("moduleKey") == null ? "" : row.get("moduleKey")));
            AssetReferenceDetailItem item = new AssetReferenceDetailItem();
            item.setModuleKey(moduleKey);
            item.setModuleName(moduleName(moduleKey));
            item.setBizId(bizId);
            item.setSummary(summaryForReference(
                    moduleKey,
                    bizId,
                    fieldMap,
                    farmMap,
                    cropMap,
                    seedMap,
                    exportMap,
                    userMap,
                    companyInfoMap,
                    companyProductMap,
                    companyHonorMap
            ));
            out.add(item);
        }
        return out;
    }

    private String normalizeReferenceModuleKey(String raw) {
        String key = String.valueOf(raw == null ? "" : raw).trim().toLowerCase(Locale.ROOT);
        if ("farm_record".equals(key)) {
            return "farm";
        }
        if ("field_cover".equals(key)) {
            return "field";
        }
        if ("crop_category".equals(key) || "crop_variety".equals(key)) {
            return "crop";
        }
        if ("company_logo".equals(key) || "company_banner".equals(key) || "company_contact".equals(key)) {
            return "company_info";
        }
        if ("company_product".equals(key)) {
            return "company_product";
        }
        if ("company_honor".equals(key)) {
            return "company_honor";
        }
        return key;
    }

    private String moduleName(String moduleKey) {
        switch (normalizeReferenceModuleKey(moduleKey)) {
            case "field":
                return "田块";
            case "farm":
                return "农事记录";
            case "crop":
                return "作物";
            case "seed":
                return "种子批次";
            case "export":
                return "导出模板";
            case "auth":
                return "用户";
            case "company_info":
                return "公司信息";
            case "company_product":
                return "公司产品";
            case "company_honor":
                return "公司荣誉";
            default:
                return "其他模块";
        }
    }

    private String summaryForReference(
            String moduleKey,
            Long bizId,
            Map<Long, Field> fieldMap,
            Map<Long, FarmRecord> farmMap,
            Map<Long, Crop> cropMap,
            Map<Long, SeedBatch> seedMap,
            Map<Long, ExportTemplate> exportMap,
            Map<Long, AppUser> userMap,
            Map<Long, String> companyInfoMap,
            Map<Long, String> companyProductMap,
            Map<Long, String> companyHonorMap
    ) {
        switch (normalizeReferenceModuleKey(moduleKey)) {
            case "field":
                Field field = fieldMap.get(bizId);
                if (field == null) {
                    return "田块信息暂不可读";
                }
                String fieldName = firstNonEmpty(field.getName(), "未命名田块");
                String area = field.getAreaMu() == null ? "-" : String.valueOf(field.getAreaMu());
                String address = firstNonEmpty(field.getTownship(), field.getFormattedAddress(), "-");
                return fieldName + "｜" + area + "亩｜" + address;
            case "farm":
                FarmRecord farm = farmMap.get(bizId);
                if (farm == null) {
                    return "农事记录信息暂不可读";
                }
                String farmFieldName = "-";
                if (farm.getFieldId() != null && fieldMap.containsKey(farm.getFieldId())) {
                    Field bindField = fieldMap.get(farm.getFieldId());
                    farmFieldName = bindField == null ? "-" : firstNonEmpty(bindField.getName(), "未命名田块");
                }
                String workDate = farm.getWorkDate() == null ? "-" : String.valueOf(farm.getWorkDate());
                String operator = firstNonEmpty(farm.getOperatorName(), "-");
                return farmFieldName + "｜" + workDate + "｜" + operator;
            case "crop":
                Crop crop = cropMap.get(bizId);
                if (crop == null) {
                    return "作物信息暂不可读";
                }
                String cropName = firstNonEmpty(crop.getName(), "未命名作物");
                String variety = StringUtils.hasText(crop.getVariety()) ? crop.getVariety().trim() : null;
                return StringUtils.hasText(variety) ? (cropName + "｜" + variety) : cropName;
            case "seed":
                SeedBatch seed = seedMap.get(bizId);
                if (seed == null) {
                    return "批次信息暂不可读";
                }
                String batchCode = firstNonEmpty(seed.getBatchCode(), "未命名批次");
                String cropType = StringUtils.hasText(seed.getCropType()) ? seed.getCropType().trim() : "";
                String varietyName = StringUtils.hasText(seed.getVarietyName()) ? seed.getVarietyName().trim() : "";
                String cropLabel = StringUtils.hasText(cropType) && StringUtils.hasText(varietyName)
                        ? cropType + "·" + varietyName
                        : firstNonEmpty(cropType, varietyName, "");
                return StringUtils.hasText(cropLabel) ? (batchCode + "｜" + cropLabel) : batchCode;
            case "export":
                ExportTemplate export = exportMap.get(bizId);
                if (export == null) {
                    return "模板信息暂不可读";
                }
                String templateName = firstNonEmpty(export.getTemplateName(), "未命名模板");
                String exportModule = firstNonEmpty(export.getModuleKey(), "-");
                return templateName + "｜" + exportModule;
            case "auth":
                AppUser user = userMap.get(bizId);
                if (user == null) {
                    return "用户信息暂不可读";
                }
                String userName = firstNonEmpty(user.getRealName(), user.getNickName(), "未命名用户");
                String phone = firstNonEmpty(user.getPhone(), "-");
                return userName + "｜" + phone;
            case "company_info":
                return firstNonEmpty(companyInfoMap.get(bizId), "公司信息暂不可读");
            case "company_product":
                return firstNonEmpty(companyProductMap.get(bizId), "公司产品信息暂不可读");
            case "company_honor":
                return firstNonEmpty(companyHonorMap.get(bizId), "公司荣誉信息暂不可读");
            default:
                return "业务信息暂不可读";
        }
    }

    private Map<Long, Field> toFieldMap(Set<Long> ids) {
        Map<Long, Field> out = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        for (Field row : fieldService.listByIds(ids)) {
            if (row != null && row.getId() != null) {
                out.put(row.getId(), row);
            }
        }
        return out;
    }

    private Map<Long, FarmRecord> toFarmRecordMap(Set<Long> ids) {
        Map<Long, FarmRecord> out = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        for (FarmRecord row : farmRecordService.listByIds(ids)) {
            if (row != null && row.getId() != null) {
                out.put(row.getId(), row);
            }
        }
        return out;
    }

    private Map<Long, Crop> toCropMap(Set<Long> ids) {
        Map<Long, Crop> out = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        for (Crop row : cropService.listByIds(ids)) {
            if (row != null && row.getId() != null) {
                out.put(row.getId(), row);
            }
        }
        return out;
    }

    private Map<Long, SeedBatch> toSeedBatchMap(Set<Long> ids) {
        Map<Long, SeedBatch> out = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        for (SeedBatch row : seedBatchService.listByIds(ids)) {
            if (row != null && row.getId() != null) {
                out.put(row.getId(), row);
            }
        }
        return out;
    }

    private Map<Long, ExportTemplate> toExportTemplateMap(Set<Long> ids) {
        Map<Long, ExportTemplate> out = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        for (ExportTemplate row : exportTemplateService.listByIds(ids)) {
            if (row != null && row.getId() != null) {
                out.put(row.getId(), row);
            }
        }
        return out;
    }

    private Map<Long, AppUser> toUserMap(Set<Long> ids) {
        Map<Long, AppUser> out = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        for (AppUser row : appUserService.listByIds(ids)) {
            if (row != null && row.getId() != null) {
                out.put(row.getId(), row);
            }
        }
        return out;
    }

    private Map<Long, String> toCompanyInfoMap(Set<Long> ids) {
        return querySimpleSummaryMap(
                ids,
                "SELECT `id`,`company_name`,`copyright` FROM `company_info` WHERE `deleted`=0 AND `id` IN (%s)",
                row -> firstNonEmpty(
                        trimMapText(row, "company_name"),
                        trimMapText(row, "copyright"),
                        "公司信息暂不可读"
                )
        );
    }

    private Map<Long, String> toCompanyProductMap(Set<Long> ids) {
        return querySimpleSummaryMap(
                ids,
                "SELECT `id`,`name`,`description` FROM `company_product` WHERE `deleted`=0 AND `id` IN (%s)",
                row -> {
                    String name = firstNonEmpty(trimMapText(row, "name"), "未命名公司产品");
                    String description = trimMapText(row, "description");
                    return StringUtils.hasText(description) ? (name + "｜" + description) : name;
                }
        );
    }

    private Map<Long, String> toCompanyHonorMap(Set<Long> ids) {
        return querySimpleSummaryMap(
                ids,
                "SELECT `id`,`name` FROM `company_honor` WHERE `deleted`=0 AND `id` IN (%s)",
                row -> firstNonEmpty(trimMapText(row, "name"), "未命名公司荣誉")
        );
    }

    private Map<Long, String> querySimpleSummaryMap(Set<Long> ids, String sqlTemplate, java.util.function.Function<Map<String, Object>, String> summaryBuilder) {
        Map<Long, String> out = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        List<Long> orderedIds = new ArrayList<>(ids);
        String placeholders = String.join(",", Collections.nCopies(orderedIds.size(), "?"));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(String.format(sqlTemplate, placeholders), orderedIds.toArray());
        for (Map<String, Object> row : rows) {
            Long id = toNullableLong(row == null ? null : row.get("id"));
            if (id == null) {
                continue;
            }
            out.put(id, firstNonEmpty(summaryBuilder.apply(row), "业务信息暂不可读"));
        }
        return out;
    }

    private String trimMapText(Map<String, Object> row, String key) {
        if (row == null || !StringUtils.hasText(key)) {
            return null;
        }
        Object value = row.get(key);
        return firstNonEmpty(value == null ? null : String.valueOf(value));
    }

    private Long toNullableLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String firstNonEmpty(String... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 为引用统计补充业务标签（如田块名、作物名、用户名）。
     * 这里做批量查询，避免对每个引用项逐条查库。
     */
    private void enrichBizLabels(MediaAssetReferenceStats stats) {
        if (stats == null || stats.getRefs() == null || stats.getRefs().isEmpty()) {
            return;
        }
        Set<Long> fieldIds = new HashSet<>();
        Set<Long> cropCategoryIds = new HashSet<>();
        Set<Long> cropVarietyIds = new HashSet<>();
        Set<Long> seedBatchIds = new HashSet<>();
        Set<Long> exportTemplateIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();

        for (MediaAssetReferenceStats.RefItem item : stats.getRefs()) {
            if (item == null || item.getBizId() == null) {
                continue;
            }
            String mk = String.valueOf(item.getModuleKey() == null ? "" : item.getModuleKey()).trim().toLowerCase(Locale.ROOT);
            if ("field".equals(mk)) {
                fieldIds.add(item.getBizId());
                continue;
            }
            if ("crop_category".equals(mk)) {
                cropCategoryIds.add(item.getBizId());
                continue;
            }
            if ("crop_variety".equals(mk)) {
                cropVarietyIds.add(item.getBizId());
                continue;
            }
            if ("seed".equals(mk)) {
                seedBatchIds.add(item.getBizId());
                continue;
            }
            if ("export".equals(mk)) {
                exportTemplateIds.add(item.getBizId());
                continue;
            }
            if ("auth".equals(mk)) {
                userIds.add(item.getBizId());
            }
        }

        Map<Long, String> fieldLabelMap = new HashMap<>();
        if (!fieldIds.isEmpty()) {
            for (Field row : fieldService.listByIds(fieldIds)) {
                if (row == null || row.getId() == null) continue;
                String name = StringUtils.hasText(row.getName()) ? row.getName().trim() : ("田块#" + row.getId());
                fieldLabelMap.put(row.getId(), name);
            }
        }

        Map<Long, String> cropCategoryLabelMap = new HashMap<>();
        if (!cropCategoryIds.isEmpty()) {
            for (Crop row : cropService.listByIds(cropCategoryIds)) {
                if (row == null || row.getId() == null) continue;
                String name = StringUtils.hasText(row.getName()) ? row.getName().trim() : ("作物分类#" + row.getId());
                cropCategoryLabelMap.put(row.getId(), name);
            }
        }

        Map<Long, String> cropVarietyLabelMap = new HashMap<>();
        if (!cropVarietyIds.isEmpty()) {
            for (Crop row : cropService.listByIds(cropVarietyIds)) {
                if (row == null || row.getId() == null) continue;
                String cropName = StringUtils.hasText(row.getName()) ? row.getName().trim() : "";
                String varietyName = StringUtils.hasText(row.getVariety()) ? row.getVariety().trim() : "";
                String label = StringUtils.hasText(varietyName) ? (cropName + "/" + varietyName) : (StringUtils.hasText(cropName) ? cropName : ("作物品种#" + row.getId()));
                cropVarietyLabelMap.put(row.getId(), label);
            }
        }

        Map<Long, String> seedBatchLabelMap = new HashMap<>();
        if (!seedBatchIds.isEmpty()) {
            for (SeedBatch row : seedBatchService.listByIds(seedBatchIds)) {
                if (row == null || row.getId() == null) continue;
                String code = StringUtils.hasText(row.getBatchCode()) ? row.getBatchCode().trim() : ("批次#" + row.getId());
                String crop = StringUtils.hasText(row.getCropType()) ? row.getCropType().trim() : "";
                String variety = StringUtils.hasText(row.getVarietyName()) ? row.getVarietyName().trim() : "";
                String detail = "";
                if (StringUtils.hasText(crop) && StringUtils.hasText(variety)) {
                    detail = crop + "/" + variety;
                } else if (StringUtils.hasText(crop)) {
                    detail = crop;
                } else if (StringUtils.hasText(variety)) {
                    detail = variety;
                }
                seedBatchLabelMap.put(row.getId(), StringUtils.hasText(detail) ? (code + "（" + detail + "）") : code);
            }
        }

        Map<Long, String> exportTemplateLabelMap = new HashMap<>();
        if (!exportTemplateIds.isEmpty()) {
            for (ExportTemplate row : exportTemplateService.listByIds(exportTemplateIds)) {
                if (row == null || row.getId() == null) continue;
                String name = StringUtils.hasText(row.getTemplateName()) ? row.getTemplateName().trim() : ("模板#" + row.getId());
                exportTemplateLabelMap.put(row.getId(), name);
            }
        }

        Map<Long, String> userLabelMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (AppUser row : appUserService.listByIds(userIds)) {
                if (row == null || row.getId() == null) continue;
                String name = StringUtils.hasText(row.getRealName()) ? row.getRealName().trim() : row.getNickName();
                if (!StringUtils.hasText(name)) {
                    name = "用户#" + row.getId();
                }
                userLabelMap.put(row.getId(), name);
            }
        }

        for (MediaAssetReferenceStats.RefItem item : stats.getRefs()) {
            if (item == null || item.getBizId() == null) {
                continue;
            }
            String mk = String.valueOf(item.getModuleKey() == null ? "" : item.getModuleKey()).trim().toLowerCase(Locale.ROOT);
            Long bizId = item.getBizId();
            String label;
            switch (mk) {
                case "field":
                    label = fieldLabelMap.getOrDefault(bizId, "田块#" + bizId);
                    break;
                case "crop_category":
                    label = cropCategoryLabelMap.getOrDefault(bizId, "作物分类#" + bizId);
                    break;
                case "crop_variety":
                    label = cropVarietyLabelMap.getOrDefault(bizId, "作物品种#" + bizId);
                    break;
                case "seed":
                    label = seedBatchLabelMap.getOrDefault(bizId, "批次#" + bizId);
                    break;
                case "export":
                    label = exportTemplateLabelMap.getOrDefault(bizId, "模板#" + bizId);
                    break;
                case "auth":
                    label = userLabelMap.getOrDefault(bizId, "用户#" + bizId);
                    break;
                case "farm":
                    label = "农事记录#" + bizId;
                    break;
                case "amap":
                    label = "高德业务#" + bizId;
                    break;
                case "system":
                    label = "系统业务#" + bizId;
                    break;
                default:
                    label = "业务#" + bizId;
                    break;
            }
            item.setBizLabel(label);
        }
    }
    /** 绑定资源创建人信息。 */
    private void bindCreator(MediaAsset row, AppUser user) {
        if (user == null) {
            return;
        }
        row.setCreatedByUserId(user.getId());
        if (StringUtils.hasText(user.getRealName())) {
            row.setCreatedByName(user.getRealName());
            return;
        }
        row.setCreatedByName(user.getNickName());
    }

    /**
     * 根据用户类型推导来源类型。
     * 规则：admin -> admin_upload，miniapp -> miniapp_upload，其余 -> system_upload。
     */
    private String resolveSourceType(AppUser user) {
        String userType = normalizeUserType(user);
        if ("admin".equals(userType)) {
            return SOURCE_ADMIN_UPLOAD;
        }
        if ("miniapp".equals(userType)) {
            return SOURCE_MINIAPP_UPLOAD;
        }
        return SOURCE_SYSTEM_UPLOAD;
    }

    /** 来源值归一化，兼容历史 operator_upload 与空值。 */
    private String normalizeSourceType(String sourceType) {
        String text = String.valueOf(sourceType == null ? "" : sourceType).trim().toLowerCase(Locale.ROOT);
        if (SOURCE_OPERATOR_UPLOAD.equals(text) || SOURCE_MINIAPP_UPLOAD.equals(text)) {
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

    /** 归一化用户类型。 */
    private String normalizeUserType(AppUser user) {
        if (user == null || !StringUtils.hasText(user.getUserType())) {
            return "";
        }
        return user.getUserType().trim().toLowerCase(Locale.ROOT);
    }

    /** 归一化 moduleKey，空串返回 null。 */
    private String normalizeModuleKey(String moduleKey) {
        return StringUtils.hasText(moduleKey) ? moduleKey.trim() : null;
    }

    /** 归一化文件类型，默认 file。 */
    private String normalizeFileType(String fileType) {
        return AssetDomainConstants.normalizeFileType(fileType);
    }

    /** 归一化审核状态，非法值回退为 approved。 */
    private String normalizeReviewStatus(String reviewStatus) {
        return AssetDomainConstants.normalizeReviewStatus(reviewStatus);
    }

    /** 解析严格来源资源的回收站最短保留天数。 */
    private int resolveStrictSourcePurgeRetainDays(MediaAssetPolicyConfig policy) {
        if (policy == null || policy.getStrictSourcePurgeRetainDays() == null || policy.getStrictSourcePurgeRetainDays() < 1) {
            return 7;
        }
        return policy.getStrictSourcePurgeRetainDays();
    }

    /**
     * 上传权限兜底校验。
     * 统一要求：上传接口必须登录，且账号类型为 admin 或 miniapp。
     */
    private Result<Void> validateUploadPermission(AppUser uploadUser) {
        if (uploadUser == null) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), "请先登录后上传资源");
        }
        String userType = normalizeUserType(uploadUser);
        if (!"admin".equals(userType) && !"miniapp".equals(userType)) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), "当前用户类型不允许上传资源");
        }
        return null;
    }

    /** 按策略校验类型、单文件大小、当日次数与当日容量。 */
    private Result<Void> validateUploadPolicy(String sourceType, String fileType, long incomingSizeBytes, MediaAssetPolicyConfig policy) {
        String normalizedSourceType = normalizeSourceType(sourceType);
        if (!SOURCE_MINIAPP_UPLOAD.equals(normalizedSourceType) && !SOURCE_ADMIN_UPLOAD.equals(normalizedSourceType)) {
            return null;
        }
        MediaAssetPolicyConfig config = policy == null ? mediaAssetPolicyConfigService.getOrInit() : policy;
        Set<String> allowedTypes = mediaAssetPolicyConfigService.resolveAllowedFileTypes(config, normalizedSourceType);
        String normalizedFileType = normalizeFileType(fileType);
        if (!allowedTypes.contains(normalizedFileType)) {
            return Result.failure(
                    ErrorCode.VALIDATION_ERROR.getCode(),
                    "当前策略不允许上传该类型文件，可上传类型：" + String.join("/", allowedTypes)
            );
        }

        int singleMaxMb = resolveSingleFileMaxMb(config, normalizedSourceType);
        if (singleMaxMb > 0 && incomingSizeBytes > (long) singleMaxMb * MB) {
            return Result.failure(
                    ErrorCode.VALIDATION_ERROR.getCode(),
                    "单文件大小超限，当前上限为" + singleMaxMb + "MB"
            );
        }

        MediaAssetUploadUsage usage = mediaAssetService.queryDailyUsage(normalizedSourceType, LocalDate.now());
        long usageCount = usage == null || usage.getUploadCount() == null ? 0L : usage.getUploadCount();
        long usageSizeBytes = usage == null || usage.getUploadSizeBytes() == null ? 0L : usage.getUploadSizeBytes();

        int dailyLimit = resolveDailyUploadLimit(config, normalizedSourceType);
        if (dailyLimit > 0 && usageCount + 1 > dailyLimit) {
            return Result.failure(
                    ErrorCode.VALIDATION_ERROR.getCode(),
                    "今日上传次数已达上限（" + dailyLimit + "）"
            );
        }

        int dailySizeMb = resolveDailyUploadSizeMb(config, normalizedSourceType);
        if (dailySizeMb > 0 && usageSizeBytes + Math.max(0L, incomingSizeBytes) > (long) dailySizeMb * MB) {
            return Result.failure(
                    ErrorCode.VALIDATION_ERROR.getCode(),
                    "今日上传容量超限，当前上限为" + dailySizeMb + "MB"
            );
        }
        return null;
    }

    /**
     * 根据策略写入审核相关字段。
     * requireReview=1 时进入 pending，否则直接 approved。
     */
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

    /** 读取每日上传次数限制。 */
    private int resolveDailyUploadLimit(MediaAssetPolicyConfig config, String sourceType) {
        if (config == null) {
            return 0;
        }
        if (SOURCE_ADMIN_UPLOAD.equals(sourceType)) {
            return config.getAdminDailyUploadLimit() == null ? 0 : Math.max(0, config.getAdminDailyUploadLimit());
        }
        return config.getMiniappDailyUploadLimit() == null ? 0 : Math.max(0, config.getMiniappDailyUploadLimit());
    }

    /** 读取每日上传容量限制（MB）。 */
    private int resolveDailyUploadSizeMb(MediaAssetPolicyConfig config, String sourceType) {
        if (config == null) {
            return 0;
        }
        if (SOURCE_ADMIN_UPLOAD.equals(sourceType)) {
            return config.getAdminDailyUploadSizeMb() == null ? 0 : Math.max(0, config.getAdminDailyUploadSizeMb());
        }
        return config.getMiniappDailyUploadSizeMb() == null ? 0 : Math.max(0, config.getMiniappDailyUploadSizeMb());
    }

    /** 读取单文件大小限制（MB）。 */
    private int resolveSingleFileMaxMb(MediaAssetPolicyConfig config, String sourceType) {
        if (config == null) {
            return 0;
        }
        if (SOURCE_ADMIN_UPLOAD.equals(sourceType)) {
            return config.getAdminSingleFileMaxMb() == null ? 0 : Math.max(0, config.getAdminSingleFileMaxMb());
        }
        return config.getMiniappSingleFileMaxMb() == null ? 0 : Math.max(0, config.getMiniappSingleFileMaxMb());
    }

    /** 读取是否需要审核标记。 */
    private int resolveRequireReview(MediaAssetPolicyConfig config, String sourceType) {
        if (config == null) {
            return 0;
        }
        if (SOURCE_ADMIN_UPLOAD.equals(sourceType)) {
            return config.getAdminRequireReview() != null && config.getAdminRequireReview() == 1 ? 1 : 0;
        }
        return config.getMiniappRequireReview() != null && config.getMiniappRequireReview() == 1 ? 1 : 0;
    }

    /** 归一化文件夹路径并兼容历史路径别名。 */
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

    /**
     * 按来源归一化目录：
     * miniapp（含历史 operator_upload）统一进入“小程序上传图片”目录，不允许自定义目录。
     */
    private String normalizeFolderPathBySource(String folderPath, String sourceType) {
        String normalizedSourceType = normalizeSourceType(sourceType);
        if (SOURCE_MINIAPP_UPLOAD.equals(normalizedSourceType) || SOURCE_OPERATOR_UPLOAD.equals(normalizedSourceType)) {
            return MINIAPP_UPLOAD_IMAGE_FOLDER_PATH;
        }
        return normalizeFolderPath(folderPath);
    }

    /** 是否系统保护目录。 */
    private boolean isSystemProtectedFolder(String folderPath) {
        String path = normalizeFolderPath(folderPath);
        return DEFAULT_FOLDER_PATH.equals(path) || MINIAPP_UPLOAD_IMAGE_FOLDER_PATH.equals(path);
    }

    /**
     * 加载目录管理列表。
     * 同时合并目录注册表与资源占用统计，并计算每个目录是否可删除。
     */
    private List<FolderManageResp> loadFolderManageRows(String keyword) {
        String keywordText = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : "";
        Map<String, FolderManageResp> byPath = new LinkedHashMap<>();

        List<Map<String, Object>> folderRows = jdbcTemplate.queryForList(
                "SELECT folder_path AS folderPath, remark AS remark, protected_flag AS protectedFlag, " +
                        "created_at AS createdAt, updated_at AS updatedAt " +
                        "FROM media_asset_folder WHERE deleted=0 ORDER BY folder_path ASC"
        );
        for (Map<String, Object> row : folderRows) {
            if (row == null) {
                continue;
            }
            String path = normalizeFolderPath(String.valueOf(row.get("folderPath") == null ? "" : row.get("folderPath")));
            FolderManageResp item = byPath.computeIfAbsent(path, FolderManageResp::new);
            String remark = String.valueOf(row.get("remark") == null ? "" : row.get("remark")).trim();
            if (StringUtils.hasText(remark)) {
                item.setRemark(remark);
            }
            item.setProtectedFlag(toInt(row.get("protectedFlag")) == 1 ? 1 : 0);
            String createdAt = String.valueOf(row.get("createdAt") == null ? "" : row.get("createdAt")).trim();
            String updatedAt = String.valueOf(row.get("updatedAt") == null ? "" : row.get("updatedAt")).trim();
            if (StringUtils.hasText(createdAt)) {
                item.setCreatedAt(createdAt);
            }
            if (StringUtils.hasText(updatedAt)) {
                item.setUpdatedAt(updatedAt);
            }
        }

        List<Map<String, Object>> usageRows = jdbcTemplate.queryForList(
                "SELECT IFNULL(NULLIF(TRIM(folder_path),''),'" + DEFAULT_FOLDER_PATH + "') AS folderPath, " +
                        "COUNT(1) AS totalCount, " +
                        "SUM(CASE WHEN source_type IN ('miniapp_upload','operator_upload') THEN 1 ELSE 0 END) AS miniappAssetCount, " +
                        "SUM(CASE WHEN source_type='admin_upload' THEN 1 ELSE 0 END) AS adminAssetCount, " +
                        "SUM(CASE WHEN source_type='system_upload' OR source_type IS NULL OR TRIM(source_type)='' THEN 1 ELSE 0 END) AS systemAssetCount " +
                        "FROM media_asset WHERE deleted=0 GROUP BY IFNULL(NULLIF(TRIM(folder_path),''),'" + DEFAULT_FOLDER_PATH + "')"
        );
        for (Map<String, Object> row : usageRows) {
            if (row == null) {
                continue;
            }
            String path = normalizeFolderPath(String.valueOf(row.get("folderPath") == null ? "" : row.get("folderPath")));
            FolderManageResp item = byPath.computeIfAbsent(path, FolderManageResp::new);
            item.setTotalAssetCount(Math.max(0L, item.getTotalAssetCount()) + toLong(row.get("totalCount")));
            item.setMiniappAssetCount(Math.max(0L, item.getMiniappAssetCount()) + toLong(row.get("miniappAssetCount")));
            item.setAdminAssetCount(Math.max(0L, item.getAdminAssetCount()) + toLong(row.get("adminAssetCount")));
            item.setSystemAssetCount(Math.max(0L, item.getSystemAssetCount()) + toLong(row.get("systemAssetCount")));
        }

        FolderManageResp defaultItem = byPath.computeIfAbsent(DEFAULT_FOLDER_PATH, FolderManageResp::new);
        defaultItem.setProtectedFlag(1);
        if (!StringUtils.hasText(defaultItem.getRemark())) {
            defaultItem.setRemark("默认文件夹（受保护）");
        }
        FolderManageResp miniappItem = byPath.computeIfAbsent(MINIAPP_UPLOAD_IMAGE_FOLDER_PATH, FolderManageResp::new);
        miniappItem.setProtectedFlag(1);
        if (!StringUtils.hasText(miniappItem.getRemark())) {
            miniappItem.setRemark("小程序上传图片");
        }
        FolderManageResp fieldImageItem = byPath.computeIfAbsent(FIELD_IMAGE_FOLDER_PATH, FolderManageResp::new);
        fieldImageItem.setRemark(FIELD_IMAGE_FOLDER_REMARK);
        FolderManageResp cropCoverItem = byPath.computeIfAbsent(CROP_COVER_FOLDER_PATH, FolderManageResp::new);
        if (!StringUtils.hasText(cropCoverItem.getRemark())) {
            cropCoverItem.setRemark(CROP_COVER_FOLDER_REMARK);
        }

        List<FolderManageResp> out = new ArrayList<>();
        for (FolderManageResp item : byPath.values()) {
            if (item == null || !StringUtils.hasText(item.getFolderPath())) {
                continue;
            }
            boolean deletable = true;
            String disabledReason = null;
            if (DEFAULT_FOLDER_PATH.equals(item.getFolderPath())) {
                deletable = false;
                disabledReason = "默认文件夹不可删除";
            } else if (MINIAPP_UPLOAD_IMAGE_FOLDER_PATH.equals(item.getFolderPath())) {
                deletable = false;
                disabledReason = "小程序上传图片文件夹不可删除";
            } else if (item.getMiniappAssetCount() > 0) {
                deletable = false;
                disabledReason = "包含小程序上传资源的文件夹不可删除";
            } else if (item.getTotalAssetCount() > 0) {
                deletable = false;
                disabledReason = "Folder still contains assets";
            } else if (item.getProtectedFlag() == 1) {
                deletable = false;
                disabledReason = "System protected folder";
            }
            item.setDeletable(deletable);
            item.setDeleteDisabledReason(disabledReason);
            if (StringUtils.hasText(keywordText) && !item.getFolderPath().toLowerCase(Locale.ROOT).contains(keywordText)) {
                continue;
            }
            out.add(item);
        }
        out.sort(Comparator.comparing(FolderManageResp::getFolderPath, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /** 按路径获取目录管理条目。 */
    private FolderManageResp findFolderRow(String folderPath) {
        String normalizedPath = normalizeFolderPath(folderPath);
        List<FolderManageResp> rows = loadFolderManageRows(normalizedPath);
        for (FolderManageResp row : rows) {
            if (row != null && normalizedPath.equals(row.getFolderPath())) {
                return row;
            }
        }
        FolderManageResp out = new FolderManageResp(normalizedPath);
        out.setProtectedFlag(isSystemProtectedFolder(normalizedPath) ? 1 : 0);
        out.setDeletable(!isSystemProtectedFolder(normalizedPath));
        return out;
    }

    /** 查询目录资源占用（总数 + 严格来源数量）。 */
    private FolderUsageStats queryFolderUsage(String folderPath) {
        String normalizedPath = normalizeFolderPath(folderPath);
        FolderUsageStats stats = new FolderUsageStats();
        List<Map<String, Object>> rows;
        if (FIELD_IMAGE_FOLDER_PATH.equals(normalizedPath)) {
            rows = jdbcTemplate.queryForList(
                    "SELECT COUNT(1) AS totalCount, " +
                            "SUM(CASE WHEN source_type IN ('miniapp_upload','operator_upload') THEN 1 ELSE 0 END) AS miniappCount " +
                            "FROM media_asset WHERE deleted=0 AND IFNULL(NULLIF(TRIM(folder_path),''),'" + DEFAULT_FOLDER_PATH + "') IN (?,?,?)",
                    FIELD_IMAGE_FOLDER_PATH,
                    LEGACY_FIELD_IMAGE_FOLDER_PATH,
                    LEGACY_FIELD_IMAGE_FOLDER_PATH_V2
            );
        } else if (CROP_COVER_FOLDER_PATH.equals(normalizedPath)) {
            rows = jdbcTemplate.queryForList(
                    "SELECT COUNT(1) AS totalCount, " +
                            "SUM(CASE WHEN source_type IN ('miniapp_upload','operator_upload') THEN 1 ELSE 0 END) AS miniappCount " +
                            "FROM media_asset WHERE deleted=0 AND IFNULL(NULLIF(TRIM(folder_path),''),'" + DEFAULT_FOLDER_PATH + "') IN (?,?)",
                    CROP_COVER_FOLDER_PATH,
                    LEGACY_CROP_COVER_FOLDER_PATH
            );
        } else {
            rows = jdbcTemplate.queryForList(
                    "SELECT COUNT(1) AS totalCount, " +
                            "SUM(CASE WHEN source_type IN ('miniapp_upload','operator_upload') THEN 1 ELSE 0 END) AS miniappCount " +
                            "FROM media_asset WHERE deleted=0 AND IFNULL(NULLIF(TRIM(folder_path),''),'" + DEFAULT_FOLDER_PATH + "')=?",
                    normalizedPath
            );
        }
        if (!rows.isEmpty() && rows.get(0) != null) {
            Map<String, Object> row = rows.get(0);
            stats.totalAssetCount = toLong(row.get("totalCount"));
            stats.miniappAssetCount = toLong(row.get("miniappCount"));
        }
        return stats;
    }

    /** 新增或更新目录注册表记录。 */
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

    /**
     * 按目录构造数据库命名锁键。
     *
     * <p>使用 sha256 前缀做定长键，避免目录过长超过数据库锁名长度限制。</p>
     */
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

    /** 获取目录级数据库命名锁。 */
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

    /** 释放目录级数据库命名锁。 */
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

    /**
     * 原子删除目录记录。
     *
     * <p>删除时再次校验目录无资源，避免“先查后删”窗口被并发写入击穿。</p>
     */
    private int markFolderDeletedIfNoAssets(String normalizedPath) {
        if (FIELD_IMAGE_FOLDER_PATH.equals(normalizedPath)) {
            return jdbcTemplate.update(
                    "UPDATE media_asset_folder SET deleted=1 " +
                            "WHERE folder_path IN (?,?,?) AND deleted=0 AND IFNULL(protected_flag,0)=0 " +
                            "AND NOT EXISTS (" +
                            "SELECT 1 FROM media_asset ma " +
                            "WHERE ma.deleted=0 AND IFNULL(NULLIF(TRIM(ma.folder_path),''),?) IN (?,?,?)" +
                            ")",
                    FIELD_IMAGE_FOLDER_PATH,
                    LEGACY_FIELD_IMAGE_FOLDER_PATH,
                    LEGACY_FIELD_IMAGE_FOLDER_PATH_V2,
                    DEFAULT_FOLDER_PATH,
                    FIELD_IMAGE_FOLDER_PATH,
                    LEGACY_FIELD_IMAGE_FOLDER_PATH,
                    LEGACY_FIELD_IMAGE_FOLDER_PATH_V2
            );
        }
        if (CROP_COVER_FOLDER_PATH.equals(normalizedPath)) {
            return jdbcTemplate.update(
                    "UPDATE media_asset_folder SET deleted=1 " +
                            "WHERE folder_path IN (?,?) AND deleted=0 AND IFNULL(protected_flag,0)=0 " +
                            "AND NOT EXISTS (" +
                            "SELECT 1 FROM media_asset ma " +
                            "WHERE ma.deleted=0 AND IFNULL(NULLIF(TRIM(ma.folder_path),''),?) IN (?,?)" +
                            ")",
                    CROP_COVER_FOLDER_PATH,
                    LEGACY_CROP_COVER_FOLDER_PATH,
                    DEFAULT_FOLDER_PATH,
                    CROP_COVER_FOLDER_PATH,
                    LEGACY_CROP_COVER_FOLDER_PATH
            );
        }
        return jdbcTemplate.update(
                "UPDATE media_asset_folder SET deleted=1 " +
                        "WHERE folder_path=? AND deleted=0 AND IFNULL(protected_flag,0)=0 " +
                        "AND NOT EXISTS (" +
                        "SELECT 1 FROM media_asset ma " +
                        "WHERE ma.deleted=0 AND IFNULL(NULLIF(TRIM(ma.folder_path),''),?)=?" +
                        ")",
                normalizedPath,
                DEFAULT_FOLDER_PATH,
                normalizedPath
        );
    }

    /** 删除临时写入但未落库成功的本地文件。 */
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

    /** 安全转 long，失败返回 0。 */
    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return 0L;
        }
        try {
            return Long.parseLong(text);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    /** 安全转 int，失败返回 0。 */
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
        } catch (Exception ignored) {
            return 0;
        }
    }

    /** 通过文件名后缀推断资源类型。 */
    private String inferTypeByName(String fileName) {
        String lower = String.valueOf(fileName).toLowerCase(Locale.ROOT);
        if (lower.endsWith(AssetDomainConstants.EXT_JPG)
                || lower.endsWith(AssetDomainConstants.EXT_JPEG)
                || lower.endsWith(AssetDomainConstants.EXT_PNG)
                || lower.endsWith(AssetDomainConstants.EXT_WEBP)
                || lower.endsWith(AssetDomainConstants.EXT_GIF)) {
            return AssetDomainConstants.FILE_TYPE_IMAGE;
        }
        return AssetDomainConstants.FILE_TYPE_FILE;
    }

    /**
     * 解析展示文件名：
     * displayName > originalName > fallbackName，并尽量保留原扩展名。
     */
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

    /**
     * 构建对外可访问 URL。
     * 优先使用配置的固定 baseUrl，否则基于请求头（含反向代理头）推导。
     */
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

    /** 拼接上传文件的公开路径。 */
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

    /** 取代理头中的第一个值（逗号分隔场景）。 */
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

    /** 规范化 Host 文本，移除协议和路径片段。 */
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

    /** 判断 host 是否已携带端口。 */
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

    /** 向 host 追加非默认端口（兼容 IPv4/IPv6）。 */
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

    /** 删除本地上传文件（仅处理 /uploads/ 下路径）。 */
    private void deleteLocalFile(String url) {
        if (!StringUtils.hasText(url)) {
            return;
        }
        int idx = url.indexOf("/uploads/");
        if (idx < 0) {
            return;
        }
        String relative = url.substring(idx + "/uploads/".length());
        File f = Paths.get(uploadDir, relative).toFile();
        if (f.exists()) {
            f.delete();
        }
    }

    @Data
    public static class LinkCreateReq {
        /** 展示文件名。 */
        private String fileName;
        /** 文件 URL（外链）。 */
        private String fileUrl;
        /** 文件类型：image/file。 */
        private String fileType;
        /** 目标目录。 */
        private String folderPath;
        /** 绑定模块键。 */
        private String moduleKey;
        /** 绑定业务主键。 */
        private Long bizId;
        /** 资源备注。 */
        private String remark;
    }

    @Data
    public static class FolderCreateReq {
        /** 文件夹路径。 */
        private String folderPath;
        /** 文件夹备注。 */
        private String remark;
    }

    @Data
    public static class AssetUpdateReq {
        @NotBlank(message = "文件名不能为空")
        private String fileName;
        /** 新目录路径。 */
        private String folderPath;
        /** 新备注。 */
        private String remark;
    }

    @Data
    public static class FolderManageResp {
        /** 目录路径。 */
        private String folderPath;
        /** 目录备注。 */
        private String remark;
        /** 目录资源总数。 */
        private long totalAssetCount;
        /** miniapp（含历史 operator_upload）资源数。 */
        private long miniappAssetCount;
        /** admin 来源资源数。 */
        private long adminAssetCount;
        /** system 来源资源数。 */
        private long systemAssetCount;
        /** 系统保护标记：1 是。 */
        private int protectedFlag;
        /** 是否允许删除。 */
        private boolean deletable;
        /** 不可删除原因。 */
        private String deleteDisabledReason;
        /** 注册创建时间。 */
        private String createdAt;
        /** 注册更新时间。 */
        private String updatedAt;

        public FolderManageResp() {
        }

        public FolderManageResp(String folderPath) {
            this.folderPath = folderPath;
        }
    }

    private static class FolderUsageStats {
        /** 目录总资源数。 */
        private long totalAssetCount;
        /** 目录内 miniapp（含历史 operator_upload）资源数。 */
        private long miniappAssetCount;
    }

    @Data
    public static class AssetReferenceDetailItem {
        /** 模块编码。 */
        private String moduleKey;
        /** 模块中文名。 */
        private String moduleName;
        /** 业务主键。 */
        private Long bizId;
        /** 面向删除确认的简略信息。 */
        private String summary;
    }

    @Data
    public static class ReorderReq {
        @NotEmpty(message = "编号列表不能为空")
        /** 排序后的资源 id 列表。 */
        private List<Long> ids;
    }

    @Data
    public static class AssetDeleteReq {
        /** 锁定资源删除前的解锁密码。 */
        private String unlockPassword;
    }

    @Data
    public static class ReviewReq {
        /** 审核状态。 */
        private String reviewStatus;
        /** 审核备注。 */
        private String reviewRemark;
        /** 提交审核时页面看到的更新时间，用于冲突保护。 */
        private String expectedUpdatedAt;
    }

    @Data
    public static class AssetLockReq {
        /** 目标锁定状态：1 锁定，0 解除。 */
        private Integer locked;
        /** 解除资源锁时输入的全局密码。 */
        private String unlockPassword;
        /** 锁定备注。 */
        private String lockRemark;
    }
}

