package com.dahe.v2.modules.assets.policy.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dahe.v2.modules.assets.domain.AssetDomainConstants;
import com.dahe.v2.modules.assets.policy.mapper.MediaAssetPolicyConfigMapper;
import com.dahe.v2.modules.assets.policy.model.MediaAssetPolicyConfig;
import com.dahe.v2.modules.assets.policy.service.MediaAssetPolicyConfigService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 资源策略配置服务实现。
 * 设计目标：
 * 1. 始终保证策略行存在（id=1）；
 * 2. 兼容历史 operator 字段并与 miniapp 策略保持同义；
 * 3. 统一做策略归一化，避免控制器重复兜底。
 */
@Service
public class MediaAssetPolicyConfigServiceImpl
        extends ServiceImpl<MediaAssetPolicyConfigMapper, MediaAssetPolicyConfig>
        implements MediaAssetPolicyConfigService {

    public static final long DEFAULT_ID = 1L;
    public static final int DEFAULT_MINIAPP_DAILY_UPLOAD_LIMIT = 500;
    public static final int DEFAULT_MINIAPP_DAILY_UPLOAD_SIZE_MB = 2048;
    public static final int DEFAULT_MINIAPP_SINGLE_FILE_MAX_MB = 30;
    public static final int DEFAULT_MINIAPP_REQUIRE_REVIEW = 0;
    public static final int DEFAULT_ADMIN_DAILY_UPLOAD_LIMIT = 0;
    public static final int DEFAULT_ADMIN_DAILY_UPLOAD_SIZE_MB = 0;
    public static final int DEFAULT_ADMIN_SINGLE_FILE_MAX_MB = 0;
    public static final int DEFAULT_ADMIN_REQUIRE_REVIEW = 0;
    public static final int DEFAULT_STRICT_SOURCE_PURGE_RETAIN_DAYS = 7;
    public static final String DEFAULT_MINIAPP_ALLOWED_FILE_TYPES =
            AssetDomainConstants.FILE_TYPE_IMAGE + "," + AssetDomainConstants.FILE_TYPE_FILE;
    public static final String DEFAULT_ADMIN_ALLOWED_FILE_TYPES =
            AssetDomainConstants.FILE_TYPE_IMAGE + "," + AssetDomainConstants.FILE_TYPE_FILE;
    private static final String SOURCE_ADMIN_UPLOAD = AssetDomainConstants.SOURCE_ADMIN_UPLOAD;
    private static final String SOURCE_MINIAPP_UPLOAD = AssetDomainConstants.SOURCE_MINIAPP_UPLOAD;
    private static final String SOURCE_OPERATOR_UPLOAD = AssetDomainConstants.SOURCE_OPERATOR_UPLOAD;

    private static final Set<String> SUPPORTED_FILE_TYPES = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(AssetDomainConstants.FILE_TYPE_IMAGE, AssetDomainConstants.FILE_TYPE_FILE))
    );

    @Override
    /**
     * 获取并初始化全局策略。
     * - 不存在：创建默认策略；
     * - 已存在：做一次归一化修复并回写（仅当字段有漂移）。
     */
    public synchronized MediaAssetPolicyConfig getOrInit() {
        MediaAssetPolicyConfig row = this.getById(DEFAULT_ID);
        if (row == null) {
            row = defaultRow();
            this.save(row);
            return row;
        }
        boolean dirty = normalizeRow(row);
        if (dirty) {
            this.updateById(row);
        }
        return row;
    }

    @Override
    /**
     * 解析指定来源允许上传的类型集合。
     * 返回集合顺序稳定（LinkedHashSet），便于前端直接展示。
     */
    public Set<String> resolveAllowedFileTypes(MediaAssetPolicyConfig row, String sourceType) {
        String normalizedSourceType = normalizeSourceType(sourceType);
        String normalized;
        if (SOURCE_ADMIN_UPLOAD.equals(normalizedSourceType)) {
            normalized = normalizeAllowedFileTypes(row == null ? null : row.getAdminAllowedFileTypes());
        } else {
            normalized = normalizeAllowedFileTypes(row == null ? null : row.getMiniappAllowedFileTypes());
        }
        if (!StringUtils.hasText(normalized)) {
            return new LinkedHashSet<>(SUPPORTED_FILE_TYPES);
        }
        Set<String> out = new LinkedHashSet<>();
        for (String token : normalized.split(",")) {
            String item = String.valueOf(token).trim().toLowerCase(Locale.ROOT);
            if (SUPPORTED_FILE_TYPES.contains(item)) {
                out.add(item);
            }
        }
        if (out.isEmpty()) {
            out.addAll(SUPPORTED_FILE_TYPES);
        }
        return out;
    }

    /** 构建默认策略行。 */
    private MediaAssetPolicyConfig defaultRow() {
        MediaAssetPolicyConfig row = new MediaAssetPolicyConfig();
        row.setId(DEFAULT_ID);
        row.setMiniappDailyUploadLimit(DEFAULT_MINIAPP_DAILY_UPLOAD_LIMIT);
        row.setMiniappDailyUploadSizeMb(DEFAULT_MINIAPP_DAILY_UPLOAD_SIZE_MB);
        row.setMiniappSingleFileMaxMb(DEFAULT_MINIAPP_SINGLE_FILE_MAX_MB);
        row.setMiniappAllowedFileTypes(DEFAULT_MINIAPP_ALLOWED_FILE_TYPES);
        row.setMiniappRequireReview(DEFAULT_MINIAPP_REQUIRE_REVIEW);
        row.setAdminDailyUploadLimit(DEFAULT_ADMIN_DAILY_UPLOAD_LIMIT);
        row.setAdminDailyUploadSizeMb(DEFAULT_ADMIN_DAILY_UPLOAD_SIZE_MB);
        row.setAdminSingleFileMaxMb(DEFAULT_ADMIN_SINGLE_FILE_MAX_MB);
        row.setAdminAllowedFileTypes(DEFAULT_ADMIN_ALLOWED_FILE_TYPES);
        row.setAdminRequireReview(DEFAULT_ADMIN_REQUIRE_REVIEW);
        row.setOperatorDailyUploadLimit(DEFAULT_MINIAPP_DAILY_UPLOAD_LIMIT);
        row.setOperatorDailyUploadSizeMb(DEFAULT_MINIAPP_DAILY_UPLOAD_SIZE_MB);
        row.setOperatorSingleFileMaxMb(DEFAULT_MINIAPP_SINGLE_FILE_MAX_MB);
        row.setOperatorAllowedFileTypes(DEFAULT_MINIAPP_ALLOWED_FILE_TYPES);
        row.setOperatorRequireReview(DEFAULT_MINIAPP_REQUIRE_REVIEW);
        row.setStrictSourcePurgeRetainDays(DEFAULT_STRICT_SOURCE_PURGE_RETAIN_DAYS);
        row.setRemark("default");
        return row;
    }

    /**
     * 归一化策略字段。
     * 说明：当字段为空、负值或历史脏值时修复为可执行状态，并返回是否发生变更。
     */
    private boolean normalizeRow(MediaAssetPolicyConfig row) {
        if (row == null) {
            return false;
        }
        boolean dirty = false;
        // 历史 operator 配置作为 miniapp 的回填来源，保证线上升级兼容。
        int legacyDailyLimit = row.getOperatorDailyUploadLimit() == null ? DEFAULT_MINIAPP_DAILY_UPLOAD_LIMIT : Math.max(0, row.getOperatorDailyUploadLimit());
        int legacyDailySize = row.getOperatorDailyUploadSizeMb() == null ? DEFAULT_MINIAPP_DAILY_UPLOAD_SIZE_MB : Math.max(0, row.getOperatorDailyUploadSizeMb());
        int legacySingleMax = row.getOperatorSingleFileMaxMb() == null ? DEFAULT_MINIAPP_SINGLE_FILE_MAX_MB : Math.max(0, row.getOperatorSingleFileMaxMb());
        int legacyRequireReview = row.getOperatorRequireReview() != null && row.getOperatorRequireReview() == 1 ? 1 : 0;
        String legacyAllowedTypes = normalizeAllowedFileTypes(row.getOperatorAllowedFileTypes());

        if (row.getMiniappDailyUploadLimit() == null || row.getMiniappDailyUploadLimit() < 0) {
            row.setMiniappDailyUploadLimit(legacyDailyLimit);
            dirty = true;
        }
        if (row.getMiniappDailyUploadSizeMb() == null || row.getMiniappDailyUploadSizeMb() < 0) {
            row.setMiniappDailyUploadSizeMb(legacyDailySize);
            dirty = true;
        }
        if (row.getMiniappSingleFileMaxMb() == null || row.getMiniappSingleFileMaxMb() < 0) {
            row.setMiniappSingleFileMaxMb(legacySingleMax);
            dirty = true;
        }
        String miniappAllowedTypes = normalizeAllowedFileTypes(row.getMiniappAllowedFileTypes());
        if (!Objects.equals(miniappAllowedTypes, row.getMiniappAllowedFileTypes())) {
            row.setMiniappAllowedFileTypes(miniappAllowedTypes);
            dirty = true;
        }
        Integer miniappRequireReview = row.getMiniappRequireReview();
        int normalizedMiniappRequireReview = miniappRequireReview != null && miniappRequireReview == 1 ? 1 : 0;
        if (miniappRequireReview == null || miniappRequireReview != normalizedMiniappRequireReview) {
            row.setMiniappRequireReview(normalizedMiniappRequireReview);
            dirty = true;
        }
        if (row.getAdminDailyUploadLimit() == null || row.getAdminDailyUploadLimit() < 0) {
            row.setAdminDailyUploadLimit(DEFAULT_ADMIN_DAILY_UPLOAD_LIMIT);
            dirty = true;
        }
        if (row.getAdminDailyUploadSizeMb() == null || row.getAdminDailyUploadSizeMb() < 0) {
            row.setAdminDailyUploadSizeMb(DEFAULT_ADMIN_DAILY_UPLOAD_SIZE_MB);
            dirty = true;
        }
        if (row.getAdminSingleFileMaxMb() == null || row.getAdminSingleFileMaxMb() < 0) {
            row.setAdminSingleFileMaxMb(DEFAULT_ADMIN_SINGLE_FILE_MAX_MB);
            dirty = true;
        }
        String adminAllowedTypes = normalizeAllowedFileTypes(row.getAdminAllowedFileTypes());
        if (!Objects.equals(adminAllowedTypes, row.getAdminAllowedFileTypes())) {
            row.setAdminAllowedFileTypes(adminAllowedTypes);
            dirty = true;
        }
        Integer adminRequireReview = row.getAdminRequireReview();
        int normalizedAdminRequireReview = adminRequireReview != null && adminRequireReview == 1 ? 1 : 0;
        if (adminRequireReview == null || adminRequireReview != normalizedAdminRequireReview) {
            row.setAdminRequireReview(normalizedAdminRequireReview);
            dirty = true;
        }
        if (row.getOperatorDailyUploadLimit() == null || row.getOperatorDailyUploadLimit() < 0) {
            row.setOperatorDailyUploadLimit(row.getMiniappDailyUploadLimit());
            dirty = true;
        }
        if (row.getOperatorDailyUploadSizeMb() == null || row.getOperatorDailyUploadSizeMb() < 0) {
            row.setOperatorDailyUploadSizeMb(row.getMiniappDailyUploadSizeMb());
            dirty = true;
        }
        if (row.getOperatorSingleFileMaxMb() == null || row.getOperatorSingleFileMaxMb() < 0) {
            row.setOperatorSingleFileMaxMb(row.getMiniappSingleFileMaxMb());
            dirty = true;
        }
        if (!Objects.equals(legacyAllowedTypes, row.getOperatorAllowedFileTypes())) {
            row.setOperatorAllowedFileTypes(legacyAllowedTypes);
            dirty = true;
        }
        if (row.getOperatorRequireReview() == null || row.getOperatorRequireReview() != legacyRequireReview) {
            row.setOperatorRequireReview(legacyRequireReview);
            dirty = true;
        }
        if (row.getStrictSourcePurgeRetainDays() == null || row.getStrictSourcePurgeRetainDays() < 1) {
            row.setStrictSourcePurgeRetainDays(DEFAULT_STRICT_SOURCE_PURGE_RETAIN_DAYS);
            dirty = true;
        }
        return dirty;
    }

    /** 归一化允许类型串，仅保留 image/file。 */
    private String normalizeAllowedFileTypes(String value) {
        Set<String> out = new LinkedHashSet<>();
        if (StringUtils.hasText(value)) {
            String[] rows = value.split(",");
            for (String row : rows) {
                String item = String.valueOf(row).trim().toLowerCase(Locale.ROOT);
                if (SUPPORTED_FILE_TYPES.contains(item)) {
                    out.add(item);
                }
            }
        }
        if (out.isEmpty()) {
            out.addAll(SUPPORTED_FILE_TYPES);
        }
        return String.join(",", out);
    }

    /** 来源归一化：operator 视同 miniapp。 */
    private String normalizeSourceType(String sourceType) {
        String text = String.valueOf(sourceType == null ? "" : sourceType).trim().toLowerCase(Locale.ROOT);
        if (SOURCE_OPERATOR_UPLOAD.equals(text)) {
            return SOURCE_MINIAPP_UPLOAD;
        }
        if (SOURCE_ADMIN_UPLOAD.equals(text)) {
            return SOURCE_ADMIN_UPLOAD;
        }
        if (SOURCE_MINIAPP_UPLOAD.equals(text)) {
            return SOURCE_MINIAPP_UPLOAD;
        }
        return SOURCE_MINIAPP_UPLOAD;
    }
}
