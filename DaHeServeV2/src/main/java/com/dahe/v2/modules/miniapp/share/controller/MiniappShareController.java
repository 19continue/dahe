package com.dahe.v2.modules.miniapp.share.controller;

import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.auth.service.WeChatMiniappShareService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序分享接口。
 *
 * <p>分享能力归属于 miniapp 业务域，但应由已登录小程序用户访问，因此不再挂到 `/miniapp/auth/**`。</p>
 */
@RestController
@RequestMapping("/api/v2/miniapp/share")
public class MiniappShareController {

    private final WeChatMiniappShareService weChatMiniappShareService;

    public MiniappShareController(WeChatMiniappShareService weChatMiniappShareService) {
        this.weChatMiniappShareService = weChatMiniappShareService;
    }

    @GetMapping("/config")
    public Result<WeChatMiniappShareService.ShareConfigPayload> config() {
        return Result.success(weChatMiniappShareService.getShareConfig());
    }

    @GetMapping("/qrcode")
    public Result<WeChatMiniappShareService.ShareQrCodePayload> qrcode() {
        try {
            return Result.success(weChatMiniappShareService.getShareQrCode());
        } catch (IllegalStateException ex) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), ex.getMessage());
        }
    }
}
