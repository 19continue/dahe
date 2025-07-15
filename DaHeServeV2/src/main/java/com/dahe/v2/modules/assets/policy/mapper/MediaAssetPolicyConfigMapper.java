package com.dahe.v2.modules.assets.policy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dahe.v2.modules.assets.policy.model.MediaAssetPolicyConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 资源策略配置 Mapper。
 * 该表设计为单行配置（id=1），由 service 层负责初始化与归一化。
 */
@Mapper
public interface MediaAssetPolicyConfigMapper extends BaseMapper<MediaAssetPolicyConfig> {
}
