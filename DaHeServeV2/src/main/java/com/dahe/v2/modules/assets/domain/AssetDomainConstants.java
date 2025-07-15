package com.dahe.v2.modules.assets.domain;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * assets 领域常量。
 *
 * <p>统一收口资源类型、来源类型、审核状态与目录字面量，避免散落硬编码。</p>
 */
public final class AssetDomainConstants {

    private AssetDomainConstants() {
    }

    // 文件类型
    public static final String FILE_TYPE_IMAGE = "image";
    public static final String FILE_TYPE_FILE = "file";

    // 审核状态
    public static final String REVIEW_STATUS_PENDING = "pending";
    public static final String REVIEW_STATUS_APPROVED = "approved";
    public static final String REVIEW_STATUS_REJECTED = "rejected";

    // 来源类型
    public static final String SOURCE_ADMIN_UPLOAD = "admin_upload";
    public static final String SOURCE_MINIAPP_UPLOAD = "miniapp_upload";
    public static final String SOURCE_OPERATOR_UPLOAD = "operator_upload";
    public static final String SOURCE_SYSTEM_UPLOAD = "system_upload";

    // 目录常量
    public static final String DEFAULT_FOLDER_PATH = "/默认";
    public static final String LEGACY_DEFAULT_FOLDER_PATH = "/default";
    public static final String MINIAPP_UPLOAD_IMAGE_FOLDER_PATH = "/小程序上传图片";
    public static final String FIELD_IMAGE_FOLDER_PATH = "/田块封面";
    public static final String LEGACY_FIELD_IMAGE_FOLDER_PATH = "/field";
    public static final String LEGACY_FIELD_IMAGE_FOLDER_PATH_V2 = "/田块图片";
    public static final String FIELD_IMAGE_FOLDER_REMARK = "田块封面（田块封面、田块卡片与详情展示）";
    public static final String CROP_COVER_FOLDER_PATH = "/作物封面";
    public static final String LEGACY_CROP_COVER_FOLDER_PATH = "/crop";
    public static final String CROP_COVER_FOLDER_REMARK = "作物封面（作物分类与品种封面）";

    // 通用单位
    public static final long MB = 1024L * 1024L;

    // 扩展名白名单（按可识别格式）
    public static final String EXT_JPG = ".jpg";
    public static final String EXT_JPEG = ".jpeg";
    public static final String EXT_PNG = ".png";
    public static final String EXT_GIF = ".gif";
    public static final String EXT_WEBP = ".webp";
    public static final String EXT_BMP = ".bmp";

    public static final Set<String> IMAGE_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            EXT_JPG,
            EXT_JPEG,
            EXT_PNG,
            EXT_GIF,
            EXT_WEBP,
            EXT_BMP
    )));

    /**
     * 是否严格来源（小程序上传及其历史兼容来源）。
     */
    public static boolean isStrictSourceType(String sourceType) {
        String normalized = normalizeLower(sourceType);
        return SOURCE_MINIAPP_UPLOAD.equals(normalized) || SOURCE_OPERATOR_UPLOAD.equals(normalized);
    }

    /**
     * 来源归一化：operator/source 空值等兼容到标准来源。
     */
    public static String normalizeSourceType(String sourceType) {
        String text = normalizeLower(sourceType);
        if (SOURCE_MINIAPP_UPLOAD.equals(text) || SOURCE_OPERATOR_UPLOAD.equals(text)) {
            return SOURCE_MINIAPP_UPLOAD;
        }
        if (SOURCE_ADMIN_UPLOAD.equals(text)) {
            return SOURCE_ADMIN_UPLOAD;
        }
        return SOURCE_SYSTEM_UPLOAD;
    }

    /**
     * 审核状态归一化：仅保留 pending/rejected，其余回退 approved。
     */
    public static String normalizeReviewStatus(String reviewStatus) {
        String value = normalizeLower(reviewStatus);
        if (REVIEW_STATUS_PENDING.equals(value)) {
            return REVIEW_STATUS_PENDING;
        }
        if (REVIEW_STATUS_REJECTED.equals(value)) {
            return REVIEW_STATUS_REJECTED;
        }
        return REVIEW_STATUS_APPROVED;
    }

    /**
     * 文件类型归一化：仅保留 image，其余回退 file。
     */
    public static String normalizeFileType(String fileType) {
        String value = normalizeLower(fileType);
        if (FILE_TYPE_IMAGE.equals(value)) {
            return FILE_TYPE_IMAGE;
        }
        return FILE_TYPE_FILE;
    }

    private static String normalizeLower(String raw) {
        return String.valueOf(raw == null ? "" : raw).trim().toLowerCase(Locale.ROOT);
    }
}
