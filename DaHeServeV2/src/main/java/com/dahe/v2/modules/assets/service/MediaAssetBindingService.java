package com.dahe.v2.modules.assets.service;

import java.util.Collection;

/**
 * 资源与业务实体绑定协调服务。
 *
 * <p>用于那些主表只保存图片 URL、但仍然需要维护资源引用快照的模块，
 * 例如企业信息、作物分类等。</p>
 */
public interface MediaAssetBindingService {

    /**
     * 按资源 URL 解析并绑定到业务实体。
     */
    void bindByUrls(String moduleKey, Long bizId, Collection<String> fileUrls);

    /**
     * 清空指定业务实体的资源绑定。
     */
    void clearBinding(String moduleKey, Long bizId);
}
