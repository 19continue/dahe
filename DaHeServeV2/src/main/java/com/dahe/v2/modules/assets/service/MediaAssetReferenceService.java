package com.dahe.v2.modules.assets.service;

import java.util.List;
import java.util.Map;

/**
 * 资源当前有效引用服务。
 *
 * <p>只记录“当前仍在生效”的资源绑定，不保留历史版本。</p>
 */
public interface MediaAssetReferenceService {

    /**
     * 启动期重建全部当前有效引用。
     */
    void rebuildAllCurrentReferences();

    /**
     * 同步单个资源的当前有效引用。
     */
    void syncAssetReference(Long assetId);

    /**
     * 删除某个资源的引用记录。
     */
    void deleteByAssetId(Long assetId);

    /**
     * 查询资源引用详情。
     */
    List<Map<String, Object>> listReferenceDetails(Long assetId, long offset, int limit);

    /**
     * 查询资源引用总数。
     */
    long countReferenceDetails(Long assetId);
}
