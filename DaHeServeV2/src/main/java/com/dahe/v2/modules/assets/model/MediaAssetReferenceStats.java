package com.dahe.v2.modules.assets.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 资源业务引用统计。
 * 用于后台查看“某模块某业务实体绑定了多少资源”。
 */
@Data
public class MediaAssetReferenceStats {

    /** 引用分组总数（moduleKey + bizId 的组合数）。 */
    private long totalRefs;

    /** 引用明细。 */
    private List<RefItem> refs = new ArrayList<>();

    @Data
    public static class RefItem {
        /** 业务模块键。 */
        private String moduleKey;
        /** 业务主键。 */
        private Long bizId;
        /** 业务展示名（由 controller 补充）。 */
        private String bizLabel;
        /** 绑定资源总数。 */
        private long assetCount;
        /** 图片数量。 */
        private long imageCount;
        /** 文件数量。 */
        private long fileCount;
        /** 最近一次绑定资源创建时间。 */
        private String latestCreatedAt;
    }
}
