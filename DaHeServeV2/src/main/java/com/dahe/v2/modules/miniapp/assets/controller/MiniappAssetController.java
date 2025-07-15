package com.dahe.v2.modules.miniapp.assets.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.assets.model.MediaAsset;
import com.dahe.v2.modules.assets.service.MediaAssetService;
import com.dahe.v2.modules.assets.service.MediaAssetUploadFacadeService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Min;

/**
 * 小程序资源接口。
 *
 * <p>用于承载小程序端资源读取与上传请求，避免继续写入非 miniapp 模块控制器。</p>
 */
@RestController
@RequestMapping("/api/v2/miniapp")
@Validated
public class MiniappAssetController {

    private final MediaAssetService mediaAssetService;
    private final MediaAssetUploadFacadeService mediaAssetUploadFacadeService;

    public MiniappAssetController(
            MediaAssetService mediaAssetService,
            MediaAssetUploadFacadeService mediaAssetUploadFacadeService
    ) {
        this.mediaAssetService = mediaAssetService;
        this.mediaAssetUploadFacadeService = mediaAssetUploadFacadeService;
    }

    /**
     * 小程序可见资源分页（严格来源仅展示审核通过）。
     */
    @GetMapping("/assets")
    public Result<Page<MediaAsset>> pageAssets(
            @RequestParam(required = false) String moduleKey,
            @RequestParam(required = false) String fileType,
            @RequestParam(required = false) Long bizId,
            @RequestParam(required = false) String folderPath,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false, defaultValue = "0") Integer recycleFlag,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) long pageSize
    ) {
        return Result.success(mediaAssetService.pageAssets(
                null,
                moduleKey,
                fileType,
                bizId,
                folderPath,
                sourceType,
                recycleFlag,
                null,
                reviewStatus,
                true,
                page,
                pageSize
        ));
    }

    /**
     * 小程序上传文件。
     */
    @PostMapping("/files/upload")
    public Result<MediaAsset> upload(
            HttpServletRequest request,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String moduleKey,
            @RequestParam(required = false) Long bizId,
            @RequestParam(required = false) String folderPath,
            @RequestParam(required = false) String displayName,
            @RequestParam(required = false) String remark
    ) {
        return mediaAssetUploadFacadeService.uploadForMiniapp(
                request,
                file,
                moduleKey,
                bizId,
                folderPath,
                displayName,
                remark
        );
    }
}
