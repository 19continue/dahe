package com.dahe.v2.modules.farm.policy.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dahe.v2.modules.farm.policy.model.RecordPolicyConfig;

/** 农事记录权限策略服务接口。 */
public interface RecordPolicyConfigService extends IService<RecordPolicyConfig> {

    /** 获取策略配置；不存在时自动初始化默认值。 */
    RecordPolicyConfig getOrInit();
}
