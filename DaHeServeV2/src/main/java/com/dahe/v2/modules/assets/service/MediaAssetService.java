package com.dahe.v2.modules.assets.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dahe.v2.modules.assets.model.MediaAsset;
import com.dahe.v2.modules.assets.model.MediaAssetReferenceStats;
import com.dahe.v2.modules.assets.model.MediaAssetStats;
import com.dahe.v2.modules.assets.model.MediaAssetUploadUsage;

import java.time.LocalDate;
import java.util.List;

/**
 * 资源领域服务接口。
 */
public interface MediaAssetService extends IService<MediaAsset> {

    /**
     * 分页查询资源列表。
     *
     * @param strictSourceApprovedOnly 是否只展示“严格来源（小程序）且审核通过”的资源
     */
    Page<MediaAsset> pageAssets(
            String keyword,
            String moduleKey,
            String fileType,
            Long bizId,
            String folderPath,
            String sourceType,
            Integer recycleFlag,
            Integer lockedFlag,
            String reviewStatus,
            boolean strictSourceApprovedOnly,
            long page,
            long pageSize
    );

    /** 计算下一个排序号。 */
    int nextSortOrder();

    /** 按给定 id 顺序重排资源。 */
    void reorder(List<Long> ids);

    /** 将资源绑定到指定业务实体。 */
    void bindAssetsToBiz(
            String moduleKey,
            Long bizId,
            List<Long> assetIds,
            Long currentUserId,
            boolean manageAll,
            boolean unbindMissing,
            boolean recycleUnboundMissing
    );

    List<MediaAsset> listBizAssets(String moduleKey, Long bizId, String fileType);

    /** 资源总览统计。 */
    MediaAssetStats stats();

    /** 资源业务引用统计。 */
    MediaAssetReferenceStats referenceStats();

    /** 软删除到回收站。 */
    boolean markRecycled(Long id, Long operatorUserId);

    /** 从回收站恢复。 */
    boolean restoreFromRecycle(Long id);

    /** 彻底删除资源记录。 */
    boolean purgeAsset(Long id);

    /** 查询文件夹路径列表。 */
    List<String> listFolderPaths(Integer recycleFlag);

    /** 查询指定来源在某天的上传用量。 */
    MediaAssetUploadUsage queryDailyUsage(String sourceType, LocalDate date);
}
