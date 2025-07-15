package com.dahe.v2.modules.field.cycle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dahe.v2.modules.field.cycle.model.FieldCropCycle;

import java.util.List;

public interface FieldCropCycleService extends IService<FieldCropCycle> {

    /** 查询田块全部计划（只读，不触发写库归一化）。 */
    List<FieldCropCycle> listByFieldId(Long fieldId);

    /** 查询田块当前计划（只读，不触发写库归一化）。 */
    FieldCropCycle findCurrentCycle(Long fieldId);

    /** 将指定计划设置为当前计划，同时清理同田块其他 current 标记。 */
    boolean setCurrentCycle(Long fieldId, Long cycleId);

    /** 写路径显式归一化：修正状态/current 并同步田块摘要。 */
    void reconcileFieldCycleStatus(Long fieldId);

    /** 新增计划并执行一致性收口（事务）。 */
    FieldCropCycle saveCycleAndReconcile(FieldCropCycle cycle, boolean setCurrent);

    /** 更新计划并执行一致性收口（事务）。 */
    FieldCropCycle updateCycleAndReconcile(FieldCropCycle cycle, boolean setCurrent);

    /** 删除计划并执行一致性收口（事务）。 */
    boolean removeCycleAndReconcile(Long fieldId, Long cycleId);
}
