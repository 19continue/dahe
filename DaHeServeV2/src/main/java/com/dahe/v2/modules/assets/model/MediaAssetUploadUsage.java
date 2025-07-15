package com.dahe.v2.modules.assets.model;

import lombok.Data;

import java.time.LocalDate;

/**
 * 单日上传用量快照。
 * 用于策略校验（次数/容量限额）与后台可视化展示。
 */
@Data
public class MediaAssetUploadUsage {

    /** 来源类型：admin_upload/miniapp_upload。 */
    private String sourceType;

    /** 统计日期。 */
    private LocalDate usageDate;

    /** 当日上传次数。 */
    private Long uploadCount;

    /** 当日上传总字节数。 */
    private Long uploadSizeBytes;
}
