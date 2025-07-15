package com.dahe.v2.modules.assets.staticasset.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.modules.assets.staticasset.model.MiniappStaticAsset;
import com.dahe.v2.modules.user.model.AppUser;
import org.springframework.web.multipart.MultipartFile;

public interface MiniappStaticAssetService {

    Page<MiniappStaticAsset> pageAssets(String keyword, long page, long pageSize);

    MiniappStaticAsset upload(MultipartFile file, String storageName, String displayName, String remark, AppUser currentUser, javax.servlet.http.HttpServletRequest request);

    MiniappStaticAsset updateMeta(Long id, String displayName, String remark, AppUser currentUser);

    void deleteAsset(Long id, String superAdminPassword, AppUser currentUser);
}
