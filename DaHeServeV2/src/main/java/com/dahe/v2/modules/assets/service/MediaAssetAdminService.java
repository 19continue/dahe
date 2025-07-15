package com.dahe.v2.modules.assets.service;

import com.dahe.v2.common.Result;
import com.dahe.v2.modules.assets.model.MediaAsset;
import com.dahe.v2.modules.user.model.AppUser;

/**
 * 资源后台治理服务。
 *
 * <p>说明：</p>
 * <p>1. 收口“删除、审核、彻底删除、资源锁”这类后台治理动作；</p>
 * <p>2. controller 只负责入参和响应，具体规则放在 service；</p>
 * <p>3. 资源锁、驳回入回收站等资产治理规则统一在这里维护。</p>
 */
public interface MediaAssetAdminService {

    /** 移入回收站。 */
    Result<Void> recycleAsset(Long id, String unlockPassword, AppUser operator);

    /** 从回收站恢复。 */
    Result<Void> restoreAsset(Long id);

    /** 提交审核结果。 */
    Result<MediaAsset> reviewAsset(Long id, String reviewStatus, String reviewRemark, String expectedUpdatedAt, AppUser operator);

    /** 彻底删除资源。 */
    Result<Void> purgeAsset(Long id, String unlockPassword, AppUser operator);

    /** 设置或更新资源锁。 */
    Result<MediaAsset> updateAssetLock(Long id, Integer locked, String unlockPassword, String lockRemark, AppUser operator);
}
