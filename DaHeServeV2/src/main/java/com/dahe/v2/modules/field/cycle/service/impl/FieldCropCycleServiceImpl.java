package com.dahe.v2.modules.field.cycle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dahe.v2.modules.farm.mapper.FarmRecordMapper;
import com.dahe.v2.modules.farm.model.FarmRecord;
import com.dahe.v2.modules.field.cycle.mapper.FieldCropCycleMapper;
import com.dahe.v2.modules.field.cycle.model.FieldCropCycle;
import com.dahe.v2.modules.field.model.FieldCropVarietyGroup;
import com.dahe.v2.modules.field.cycle.service.FieldCropCycleService;
import com.dahe.v2.modules.field.model.Field;
import com.dahe.v2.modules.field.service.FieldCropVarietyGroupCodec;
import com.dahe.v2.modules.field.service.FieldService;
import com.dahe.v2.modules.miniapp.search.service.MiniappSearchIndexService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
/**
 * 田块种植计划服务实现。
 * 负责计划状态归一化（active/completed/current 一致性）以及与田块摘要信息联动。
 */
public class FieldCropCycleServiceImpl extends ServiceImpl<FieldCropCycleMapper, FieldCropCycle>
        implements FieldCropCycleService {

    private final FieldService fieldService;
    private final ObjectMapper objectMapper;
    private final FarmRecordMapper farmRecordMapper;
    private final MiniappSearchIndexService miniappSearchIndexService;

    public FieldCropCycleServiceImpl(
            FieldService fieldService,
            ObjectMapper objectMapper,
            FarmRecordMapper farmRecordMapper,
            MiniappSearchIndexService miniappSearchIndexService
    ) {
        this.fieldService = fieldService;
        this.objectMapper = objectMapper;
        this.farmRecordMapper = farmRecordMapper;
        this.miniappSearchIndexService = miniappSearchIndexService;
    }

    @Override
    /** 查询田块计划列表；只做内存归一化，不在读路径写库。 */
    public List<FieldCropCycle> listByFieldId(Long fieldId) {
        if (fieldId == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<FieldCropCycle> qw = new LambdaQueryWrapper<>();
        qw.eq(FieldCropCycle::getFieldId, fieldId)
                .orderByDesc(FieldCropCycle::getIsCurrent)
                .orderByDesc(FieldCropCycle::getStartDate)
                .orderByDesc(FieldCropCycle::getId);
        List<FieldCropCycle> rows = this.list(qw);
        applyReadOnlyNormalization(rows);
        return rows;
    }

    @Override
    /** 获取当前计划：优先 current+active，回退到最新 active（只读，不写库）。 */
    public FieldCropCycle findCurrentCycle(Long fieldId) {
        if (fieldId == null) {
            return null;
        }
        List<FieldCropCycle> rows = listByFieldId(fieldId);
        for (FieldCropCycle row : rows) {
            if (row != null
                    && "active".equalsIgnoreCase(String.valueOf(row.getStatus()))
                    && row.getIsCurrent() != null
                    && row.getIsCurrent() == 1) {
                return row;
            }
        }
        for (FieldCropCycle row : rows) {
            if (row != null && "active".equalsIgnoreCase(String.valueOf(row.getStatus()))) {
                return row;
            }
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /** 切换当前计划，并同步田块作物摘要与阶段。 */
    public boolean setCurrentCycle(Long fieldId, Long cycleId) {
        if (fieldId == null || cycleId == null) {
            return false;
        }
        reconcileFieldCycleStatus(fieldId);
        FieldCropCycle target = this.getById(cycleId);
        if (target == null || !fieldId.equals(target.getFieldId())) {
            return false;
        }
        if ("completed".equalsIgnoreCase(target.getStatus())) {
            return false;
        }
        LambdaQueryWrapper<FieldCropCycle> clearQw = new LambdaQueryWrapper<>();
        clearQw.eq(FieldCropCycle::getFieldId, fieldId)
                .eq(FieldCropCycle::getIsCurrent, 1);
        List<FieldCropCycle> currents = this.list(clearQw);
        for (FieldCropCycle row : currents) {
            row.setIsCurrent(0);
            this.updateById(row);
        }
        target.setIsCurrent(1);
        target.setStatus("active");
        boolean updated = this.updateById(target);
        if (updated) {
            syncFieldSummaryByCurrentPlan(fieldId, true, target);
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reconcileFieldCycleStatus(Long fieldId) {
        normalizeFieldCycleStatus(fieldId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FieldCropCycle saveCycleAndReconcile(FieldCropCycle cycle, boolean setCurrent) {
        if (cycle == null || cycle.getFieldId() == null) {
            return null;
        }
        this.save(cycle);
        if (cycle.getId() == null) {
            return null;
        }
        if (setCurrent) {
            setCurrentCycle(cycle.getFieldId(), cycle.getId());
        } else {
            reconcileFieldCycleStatus(cycle.getFieldId());
        }
        return this.getById(cycle.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FieldCropCycle updateCycleAndReconcile(FieldCropCycle cycle, boolean setCurrent) {
        if (cycle == null || cycle.getId() == null || cycle.getFieldId() == null) {
            return null;
        }
        boolean updated = this.updateById(cycle);
        if (!updated) {
            return null;
        }
        if (setCurrent) {
            setCurrentCycle(cycle.getFieldId(), cycle.getId());
        } else {
            reconcileFieldCycleStatus(cycle.getFieldId());
        }
        return this.getById(cycle.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeCycleAndReconcile(Long fieldId, Long cycleId) {
        if (fieldId == null || cycleId == null) {
            return false;
        }
        FieldCropCycle cycle = this.getById(cycleId);
        if (cycle == null || !fieldId.equals(cycle.getFieldId())) {
            return false;
        }
        if (countFarmRecordsByCycleId(cycleId) > 0) {
            return false;
        }
        boolean removed = this.removeById(cycleId);
        if (!removed) {
            return false;
        }
        reconcileFieldCycleStatus(fieldId);
        return true;
    }

    /** 删除前引用校验：计划仍被农事记录引用时禁止删除。 */
    private long countFarmRecordsByCycleId(Long cycleId) {
        if (cycleId == null) {
            return 0L;
        }
        LambdaQueryWrapper<FarmRecord> qw = new LambdaQueryWrapper<>();
        qw.eq(FarmRecord::getCycleId, cycleId);
        return farmRecordMapper.selectCount(qw);
    }

    /**
     * 读路径状态归一化：仅修正返回对象，不落库。
     * 规则与写路径一致：过期 completed、current 仅保留一个 active、无 current 时补一个 active。
     */
    private void applyReadOnlyNormalization(List<FieldCropCycle> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now();
        rows.sort(Comparator
                .comparing((FieldCropCycle x) -> x.getStartDate() == null ? LocalDate.MIN : x.getStartDate(), Comparator.reverseOrder())
                .thenComparing(FieldCropCycle::getId, Comparator.reverseOrder()));

        boolean hasActive = false;
        boolean currentActiveSeen = false;
        for (FieldCropCycle row : rows) {
            if (row == null) {
                continue;
            }
            String normalized = normalizeStatus(row, today);
            row.setStatus(normalized);
            if ("active".equalsIgnoreCase(normalized)) {
                hasActive = true;
            }
            if (!"active".equalsIgnoreCase(normalized) && row.getIsCurrent() != null && row.getIsCurrent() == 1) {
                row.setIsCurrent(0);
                continue;
            }
            if ("active".equalsIgnoreCase(normalized) && row.getIsCurrent() != null && row.getIsCurrent() == 1) {
                if (!currentActiveSeen) {
                    currentActiveSeen = true;
                } else {
                    row.setIsCurrent(0);
                }
            }
        }

        if (hasActive && !currentActiveSeen) {
            for (FieldCropCycle row : rows) {
                if (row != null && "active".equalsIgnoreCase(String.valueOf(row.getStatus()))) {
                    row.setIsCurrent(1);
                    break;
                }
            }
        }
    }

    /**
     * 统一修正某田块下计划状态：
     * 1. 过期计划置 completed；
     * 2. current 只允许一个且必须是 active；
     * 3. 无 current 时自动挑选一个 active 计划；
     * 4. 最终同步田块状态与作物摘要。
     */
    private void normalizeFieldCycleStatus(Long fieldId) {
        LambdaQueryWrapper<FieldCropCycle> qw = new LambdaQueryWrapper<>();
        qw.eq(FieldCropCycle::getFieldId, fieldId)
                .orderByDesc(FieldCropCycle::getStartDate)
                .orderByDesc(FieldCropCycle::getId);
        List<FieldCropCycle> rows = this.list(qw);
        if (rows.isEmpty()) {
            syncFieldSummaryByCurrentPlan(fieldId, false, null);
            return;
        }

        LocalDate today = LocalDate.now();
        rows.sort(Comparator
                .comparing((FieldCropCycle x) -> x.getStartDate() == null ? LocalDate.MIN : x.getStartDate(), Comparator.reverseOrder())
                .thenComparing(FieldCropCycle::getId, Comparator.reverseOrder()));

        boolean hasActive = false;
        boolean currentActiveSeen = false;
        for (FieldCropCycle row : rows) {
            String normalized = normalizeStatus(row, today);
            boolean changed = false;
            if (!normalized.equalsIgnoreCase(String.valueOf(row.getStatus()))) {
                row.setStatus(normalized);
                changed = true;
            }
            if ("active".equalsIgnoreCase(normalized)) {
                hasActive = true;
            }
            if ("completed".equalsIgnoreCase(normalized) && row.getIsCurrent() != null && row.getIsCurrent() == 1) {
                row.setIsCurrent(0);
                changed = true;
            } else if (!"active".equalsIgnoreCase(normalized) && row.getIsCurrent() != null && row.getIsCurrent() == 1) {
                row.setIsCurrent(0);
                changed = true;
            } else if ("active".equalsIgnoreCase(normalized) && row.getIsCurrent() != null && row.getIsCurrent() == 1) {
                if (!currentActiveSeen) {
                    currentActiveSeen = true;
                } else {
                    row.setIsCurrent(0);
                    changed = true;
                }
            }
            if (!StringUtils.hasText(row.getPlanMode())) {
                row.setPlanMode("single");
                changed = true;
            }
            if (changed) {
                this.updateById(row);
            }
        }

        if (hasActive && !currentActiveSeen) {
            for (FieldCropCycle row : rows) {
                if (!"active".equalsIgnoreCase(String.valueOf(row.getStatus()))) {
                    continue;
                }
                row.setIsCurrent(1);
                this.updateById(row);
                currentActiveSeen = true;
                break;
            }
        }
        FieldCropCycle currentCycle = null;
        if (hasActive) {
            for (FieldCropCycle row : rows) {
                if ("active".equalsIgnoreCase(String.valueOf(row.getStatus()))
                        && row.getIsCurrent() != null && row.getIsCurrent() == 1) {
                    currentCycle = row;
                    break;
                }
            }
            if (currentCycle == null) {
                for (FieldCropCycle row : rows) {
                    if ("active".equalsIgnoreCase(String.valueOf(row.getStatus()))) {
                        currentCycle = row;
                        break;
                    }
                }
            }
        }
        syncFieldSummaryByCurrentPlan(fieldId, hasActive, currentCycle);
    }

    /** 按结束日期和状态字段归一化计划状态。 */
    private String normalizeStatus(FieldCropCycle row, LocalDate today) {
        if (row == null) {
            return "active";
        }
        if (row.getEndDate() != null && row.getEndDate().isBefore(today)) {
            return "completed";
        }
        String status = row.getStatus() == null ? "" : row.getStatus().trim().toLowerCase();
        if ("completed".equals(status)) {
            return "completed";
        }
        return "active";
    }

    /**
     * 根据“是否存在有效计划 + 当前计划内容”回写田块摘要。
     * 包括：stage/status、cropType、cropVariety、cropVarietyGroupsJson。
     */
    private void syncFieldSummaryByCurrentPlan(Long fieldId, boolean hasActivePlan, FieldCropCycle currentCycle) {
        if (fieldId == null) {
            return;
        }
        Field field = fieldService.getById(fieldId);
        if (field == null) {
            return;
        }
        boolean changed = false;
        String nextStage = resolveStageByCurrentPlan(hasActivePlan, currentCycle, field.getStatus());
        if (!Objects.equals(normalizeNullable(field.getStatus()), normalizeNullable(nextStage))) {
            field.setStatus(nextStage);
            changed = true;
        }
        if (!hasActivePlan) {
            if (StringUtils.hasText(field.getCropType())) {
                field.setCropType(null);
                changed = true;
            }
            if (StringUtils.hasText(field.getCropVariety())) {
                field.setCropVariety(null);
                changed = true;
            }
            if (StringUtils.hasText(field.getCropVarietyGroupsJson())) {
                field.setCropVarietyGroupsJson(null);
                changed = true;
            }
        } else {
            List<FieldCropVarietyGroup> groups = FieldCropVarietyGroupCodec.fromCycleCropsJson(
                    objectMapper,
                    currentCycle == null ? null : currentCycle.getCropsJson()
            );
            FieldCropVarietyGroupCodec.CropSummary summary = FieldCropVarietyGroupCodec.toLegacySummary(groups);
            String nextCropType = summary.getCropType();
            String nextCropVariety = summary.getCropVariety();
            String nextGroupsJson = FieldCropVarietyGroupCodec.toFieldJson(objectMapper, groups);
            String currCropType = normalizeNullable(field.getCropType());
            String currCropVariety = normalizeNullable(field.getCropVariety());
            String currGroupsJson = normalizeNullable(field.getCropVarietyGroupsJson());
            if (!Objects.equals(currCropType, normalizeNullable(nextCropType))) {
                field.setCropType(nextCropType);
                changed = true;
            }
            if (!Objects.equals(currCropVariety, normalizeNullable(nextCropVariety))) {
                field.setCropVariety(nextCropVariety);
                changed = true;
            }
            if (!Objects.equals(currGroupsJson, normalizeNullable(nextGroupsJson))) {
                field.setCropVarietyGroupsJson(nextGroupsJson);
                changed = true;
            }
        }
        if (changed) {
            fieldService.updateById(field);
            miniappSearchIndexService.syncField(fieldService.getById(fieldId));
        }
    }

    /** 推导田块阶段：无计划 idle，休耕计划 fallow，其余尽量延续当前阶段。 */
    private String resolveStageByCurrentPlan(boolean hasActivePlan, FieldCropCycle currentCycle, String currentStage) {
        if (!hasActivePlan) {
            return "idle";
        }
        String planMode = normalizePlanModeValue(currentCycle == null ? null : currentCycle.getPlanMode());
        if ("fallow".equals(planMode)) {
            return "fallow";
        }
        String normalizedCurrent = normalizeStageValue(currentStage);
        if (normalizedCurrent == null || "idle".equals(normalizedCurrent) || "fallow".equals(normalizedCurrent)) {
            return "sowing";
        }
        return normalizedCurrent;
    }

    /** 归一化计划模式文本。 */
    private String normalizePlanModeValue(String value) {
        if (!StringUtils.hasText(value)) {
            return "single";
        }
        String raw = value.trim().toLowerCase();
        if ("fallow".equals(raw) || "休耕".equals(raw)) {
            return "fallow";
        }
        return raw;
    }

    /** 归一化田块阶段文本（中英文别名统一）。 */
    private String normalizeStageValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String raw = value.trim().toLowerCase();
        switch (raw) {
            case "sowing":
            case "播种":
            case "播种阶段":
                return "sowing";
            case "growing":
            case "生长":
            case "生长阶段":
                return "growing";
            case "harvesting":
            case "收获":
            case "收获阶段":
                return "harvesting";
            case "idle":
            case "空闲":
            case "空闲阶段":
                return "idle";
            case "fallow":
            case "休耕":
            case "休耕阶段":
                return "fallow";
            default:
                return null;
        }
    }

    /** 将任意对象转为去空白字符串，空值返回 null。 */
    private String normalizeNullable(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
