package com.dahe.v2.modules.assets.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 资源总览统计。
 * 同时提供全局汇总和按 module_key 维度的拆分数据。
 */
@Data
public class MediaAssetStats {

    /** 正常资源总数（不含回收站）。 */
    private long totalCount;

    /** 图片数量。 */
    private long imageCount;

    /** 文件数量。 */
    private long fileCount;

    /** 资源总大小（字节）。 */
    private long totalSizeBytes;

    /** 按模块聚合统计。 */
    private List<ModuleStat> moduleStats = new ArrayList<>();

    @Data
    public static class ModuleStat {
        /** 模块键。 */
        private String moduleKey;
        /** 该模块资源总数。 */
        private long totalCount;
        /** 该模块图片数量。 */
        private long imageCount;
        /** 该模块文件数量。 */
        private long fileCount;
        /** 该模块资源总大小（字节）。 */
        private long totalSizeBytes;
    }
}
