package com.dahe.v2.modules.assets.policy.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dahe.v2.modules.assets.policy.model.MediaAssetPolicyConfig;

import java.util.Set;

/**
 * 资源策略配置服务接口。
 */
public interface MediaAssetPolicyConfigService extends IService<MediaAssetPolicyConfig> {

    /** 获取全局策略配置，不存在时自动创建默认配置。 */
    MediaAssetPolicyConfig getOrInit();

    /** 解析某来源允许上传的文件类型集合。 */
    Set<String> resolveAllowedFileTypes(MediaAssetPolicyConfig row, String sourceType);
}
