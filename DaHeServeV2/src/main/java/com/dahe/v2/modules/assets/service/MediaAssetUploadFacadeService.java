package com.dahe.v2.modules.assets.service;

import com.dahe.v2.common.Result;
import com.dahe.v2.modules.assets.model.MediaAsset;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

/**
 * 资源上传编排门面。
 *
 * <p>职责：统一收口 admin/miniapp 上传流程，控制器仅保留参数接收与响应返回。</p>
 */
public interface MediaAssetUploadFacadeService {

    /** 后台用户上传资源。 */
    Result<MediaAsset> uploadForAdmin(
            HttpServletRequest request,
            MultipartFile file,
            String moduleKey,
            Long bizId,
            String folderPath,
            String displayName,
            String remark
    );

    /** 小程序用户上传资源。 */
    Result<MediaAsset> uploadForMiniapp(
            HttpServletRequest request,
            MultipartFile file,
            String moduleKey,
            Long bizId,
            String folderPath,
            String displayName,
            String remark
    );
}
