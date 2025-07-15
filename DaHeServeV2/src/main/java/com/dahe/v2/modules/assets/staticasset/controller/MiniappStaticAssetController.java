package com.dahe.v2.modules.assets.staticasset.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.assets.staticasset.model.MiniappStaticAsset;
import com.dahe.v2.modules.assets.staticasset.service.MiniappStaticAssetService;
import com.dahe.v2.modules.assets.staticasset.service.MiniappStaticAssetServiceException;
import com.dahe.v2.modules.auth.policy.AuthUserPolicy;
import com.dahe.v2.modules.auth.support.AdminMenuCode;
import com.dahe.v2.modules.auth.support.AuthContext;
import com.dahe.v2.modules.user.model.AppUser;
import lombok.Data;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

/**
 * 小程序静态资源管理接口。
 *
 * <p>该模块只给超级管理员使用，避免和普通业务资源混在同一套表与回收流程里。</p>
 */
@RestController
@RequestMapping("/api/v2/admin/miniapp-static-assets")
@Validated
@AdminMenuCode("/asset-policy")
public class MiniappStaticAssetController {

    private final MiniappStaticAssetService miniappStaticAssetService;
    private final AuthUserPolicy authUserPolicy;

    public MiniappStaticAssetController(MiniappStaticAssetService miniappStaticAssetService, AuthUserPolicy authUserPolicy) {
        this.miniappStaticAssetService = miniappStaticAssetService;
        this.authUserPolicy = authUserPolicy;
    }

    @GetMapping
    public Result<Page<MiniappStaticAsset>> page(
            HttpServletRequest request,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) long pageSize
    ) {
        AppUser currentUser = AuthContext.getCurrentUser(request);
        if (!authUserPolicy.isSuperAdmin(currentUser)) {
            return Result.failure(401, "仅超级管理员可管理小程序静态资源");
        }
        return Result.success(miniappStaticAssetService.pageAssets(keyword, page, pageSize));
    }

    @PostMapping("/upload")
    public Result<?> upload(
            HttpServletRequest request,
            @RequestPart("file") MultipartFile file,
            @RequestParam String storageName,
            @RequestParam(required = false) String displayName,
            @RequestParam(required = false) String remark
    ) {
        try {
            AppUser currentUser = AuthContext.getCurrentUser(request);
            return Result.success(miniappStaticAssetService.upload(file, storageName, displayName, remark, currentUser, request));
        } catch (MiniappStaticAssetServiceException ex) {
            return Result.failure(ex.getCode(), ex.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<?> updateMeta(HttpServletRequest request, @PathVariable Long id, @RequestBody UpdateReq req) {
        try {
            AppUser currentUser = AuthContext.getCurrentUser(request);
            return Result.success(miniappStaticAssetService.updateMeta(id, req.getDisplayName(), req.getRemark(), currentUser));
        } catch (MiniappStaticAssetServiceException ex) {
            return Result.failure(ex.getCode(), ex.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id, @RequestBody DeleteReq req) {
        try {
            AppUser currentUser = AuthContext.getCurrentUser(request);
            miniappStaticAssetService.deleteAsset(id, req == null ? null : req.getPassword(), currentUser);
            return Result.success(null);
        } catch (MiniappStaticAssetServiceException ex) {
            return Result.failure(ex.getCode(), ex.getMessage());
        }
    }

    @Data
    public static class UpdateReq {
        private String displayName;
        private String remark;
    }

    @Data
    public static class DeleteReq {
        @NotBlank(message = "请输入超级管理员密码")
        private String password;
    }
}
