package com.dahe.v2.modules.farm.policy.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dahe.v2.modules.farm.policy.mapper.RecordPolicyConfigMapper;
import com.dahe.v2.modules.farm.policy.model.RecordPolicyConfig;
import com.dahe.v2.modules.farm.policy.service.RecordPolicyConfigService;
import org.springframework.stereotype.Service;

/** 农事记录权限策略服务实现。 */
@Service
public class RecordPolicyConfigServiceImpl extends ServiceImpl<RecordPolicyConfigMapper, RecordPolicyConfig>
        implements RecordPolicyConfigService {

    /** 单行配置固定 ID。 */
    private static final long DEFAULT_ID = 1L;
    /** 默认编辑窗口（小时）。 */
    private static final int DEFAULT_WINDOW_HOURS = 48;

    @Override
    /** 获取策略；若缺失或字段不完整则自动补齐默认值。 */
    public synchronized RecordPolicyConfig getOrInit() {
        RecordPolicyConfig row = this.getById(DEFAULT_ID);
        if (row == null) {
            row = new RecordPolicyConfig();
            row.setId(DEFAULT_ID);
            row.setEditWindowHours(DEFAULT_WINDOW_HOURS);
            row.setAllowOperatorUpdate(1);
            row.setAllowOperatorDelete(1);
            row.setRemark("default");
            this.save(row);
            return row;
        }
        boolean dirty = false;
        if (row.getEditWindowHours() == null || row.getEditWindowHours() < 0) {
            row.setEditWindowHours(DEFAULT_WINDOW_HOURS);
            dirty = true;
        }
        if (row.getAllowOperatorUpdate() == null) {
            row.setAllowOperatorUpdate(1);
            dirty = true;
        }
        if (row.getAllowOperatorDelete() == null) {
            row.setAllowOperatorDelete(1);
            dirty = true;
        }
        if (dirty) {
            this.updateById(row);
        }
        return row;
    }
}
