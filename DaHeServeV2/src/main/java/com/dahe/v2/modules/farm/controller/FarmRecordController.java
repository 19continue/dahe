package com.dahe.v2.modules.farm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.assets.domain.AssetDomainConstants;
import com.dahe.v2.modules.assets.model.MediaAsset;
import com.dahe.v2.modules.assets.service.MediaAssetService;
import com.dahe.v2.modules.auth.support.AuthContext;
import com.dahe.v2.modules.farm.model.FarmRecord;
import com.dahe.v2.modules.farm.model.FarmRecordImageView;
import com.dahe.v2.modules.farm.model.FarmRecordGroupStats;
import com.dahe.v2.modules.farm.process.model.FarmProcessStep;
import com.dahe.v2.modules.farm.process.service.FarmProcessStepService;
import com.dahe.v2.modules.farm.process.support.StepFormSchemaResolver;
import com.dahe.v2.modules.farm.process.support.StepFormSchemaValidator;
import com.dahe.v2.modules.farm.policy.model.RecordPolicyConfig;
import com.dahe.v2.modules.farm.policy.service.RecordPolicyConfigService;
import com.dahe.v2.modules.farm.service.FarmRecordService;
import com.dahe.v2.modules.field.cycle.model.FieldCropCycle;
import com.dahe.v2.modules.field.cycle.service.FieldCropCycleService;
import com.dahe.v2.modules.field.model.Field;
import com.dahe.v2.modules.field.service.FieldService;
import com.dahe.v2.modules.user.model.AppUser;
import com.dahe.v2.modules.user.service.AppUserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

    /**
 * 农事记录控制器。
 * 提供记录分页、分组统计、详情、新增、更新、删除以及操作员选择能力。
 */
@RestController
@RequestMapping("/api/v2/farm-records")
@Validated
public class FarmRecordController {

    private final FarmRecordService farmRecordService;
    private final FarmProcessStepService farmProcessStepService;
    private final FieldService fieldService;
    private final FieldCropCycleService fieldCropCycleService;
    private final StepFormSchemaResolver stepFormSchemaResolver;
    private final StepFormSchemaValidator stepFormSchemaValidator;
    private final RecordPolicyConfigService recordPolicyConfigService;
    private final MediaAssetService mediaAssetService;
    private final AppUserService appUserService;
    private final ObjectMapper objectMapper;

    public FarmRecordController(
            FarmRecordService farmRecordService,
            FarmProcessStepService farmProcessStepService,
            FieldService fieldService,
            FieldCropCycleService fieldCropCycleService,
            StepFormSchemaResolver stepFormSchemaResolver,
            StepFormSchemaValidator stepFormSchemaValidator,
            RecordPolicyConfigService recordPolicyConfigService,
            MediaAssetService mediaAssetService,
            AppUserService appUserService,
            ObjectMapper objectMapper
    ) {
        this.farmRecordService = farmRecordService;
        this.farmProcessStepService = farmProcessStepService;
        this.fieldService = fieldService;
        this.fieldCropCycleService = fieldCropCycleService;
        this.stepFormSchemaResolver = stepFormSchemaResolver;
        this.stepFormSchemaValidator = stepFormSchemaValidator;
        this.recordPolicyConfigService = recordPolicyConfigService;
        this.mediaAssetService = mediaAssetService;
        this.appUserService = appUserService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    /** 分页查询农事记录。 */
    public Result<Page<FarmRecord>> page(
            HttpServletRequest request,
            @RequestParam(required = false) Long fieldId,
            @RequestParam(required = false) Long cycleId,
            @RequestParam(defaultValue = "false") boolean mineOnly,
            @RequestParam(required = false) String township,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) long pageSize
    ) {
        Set<Long> fieldIdScope = resolveFieldIdScopeByTownship(township);
        if (fieldIdScope != null && fieldIdScope.isEmpty()) {
            return Result.success(new Page<>(page, pageSize));
        }
        RecordOwnerScope ownerScope = resolveRecordOwnerScope(request, mineOnly);
        if (mineOnly && ownerScope.emptyResult) {
            return Result.success(new Page<>(page, pageSize));
        }
        Page<FarmRecord> rows = farmRecordService.pageRecords(
                fieldId,
                cycleId,
                ownerScope.operatorUserId,
                ownerScope.operatorName,
                startDate,
                endDate,
                fieldIdScope,
                page,
                pageSize
        );
        if (rows != null && rows.getRecords() != null) {
            enrichFieldNames(rows.getRecords());
            enrichStepNames(rows.getRecords());
            enrichExtraLabelMaps(rows.getRecords());
            for (FarmRecord row : rows.getRecords()) {
                applyRecordPermissions(row, request);
            }
        }
        return Result.success(rows);
    }

    @GetMapping("/operator-options")
    /** 查询可选执行人列表（仅后台且具备农事记录管理菜单权限）。 */
    public Result<Page<OperatorOption>> operatorOptions(
            HttpServletRequest request,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) long pageSize
    ) {
        Page<AppUser> raw = appUserService.pageUsers(keyword, "approved", "miniapp", 1, page, pageSize);
        Page<OperatorOption> out = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        out.setRecords(raw.getRecords().stream().map(this::toOperatorOption).collect(Collectors.toList()));
        return Result.success(out);
    }

    @GetMapping("/operator-detail/{userId}")
    /** 查询执行人详情（仅后台且具备农事记录管理菜单权限）。 */
    public Result<OperatorDetailResp> operatorDetail(
            HttpServletRequest request,
            @PathVariable Long userId
    ) {
        if (userId == null || userId <= 0) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "用户ID无效");
        }
        AppUser user = appUserService.getById(userId);
        if (user == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        return Result.success(toOperatorDetail(user));
    }

    @GetMapping("/grouped")
    /** 按田块+计划分组统计农事记录。 */
    public Result<List<FarmRecordGroupStats>> grouped(
            HttpServletRequest request,
            @RequestParam(required = false) Long fieldId,
            @RequestParam(required = false) Long cycleId,
            @RequestParam(defaultValue = "false") boolean mineOnly,
            @RequestParam(required = false) String fieldIdList,
            @RequestParam(required = false) String township,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate,
            @RequestParam(defaultValue = "200") @Min(1) int maxGroups
    ) {
        RecordOwnerScope ownerScope = resolveRecordOwnerScope(request, mineOnly);
        if (mineOnly && ownerScope.emptyResult) {
            return Result.success(Collections.emptyList());
        }
        Set<Long> fieldIdScope = resolveFieldIdScopeByTownship(township);
        Set<Long> requestedFieldIdScope = parseIdCsv(fieldIdList);
        if (!requestedFieldIdScope.isEmpty()) {
            if (fieldIdScope == null) {
                fieldIdScope = requestedFieldIdScope;
            } else {
                fieldIdScope = fieldIdScope.stream()
                        .filter(requestedFieldIdScope::contains)
                        .collect(Collectors.toSet());
            }
        }
        if (fieldIdScope != null && fieldIdScope.isEmpty()) {
            return Result.success(Collections.emptyList());
        }
        List<FarmRecordGroupStats> rows = farmRecordService.listGroupedRecords(
                fieldId,
                cycleId,
                ownerScope.operatorUserId,
                ownerScope.operatorName,
                startDate,
                endDate,
                fieldIdScope,
                maxGroups
        );
        if (rows == null || rows.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        Set<Long> fieldIds = rows.stream()
                .map(FarmRecordGroupStats::getFieldId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> cycleIds = rows.stream()
                .map(FarmRecordGroupStats::getCycleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> stepIds = rows.stream()
                .map(FarmRecordGroupStats::getLatestStepId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Field> fieldMap = fieldIds.isEmpty()
                ? Collections.emptyMap()
                : fieldService.listByIds(fieldIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Field::getId, x -> x, (a, b) -> a));
        Map<Long, FieldCropCycle> cycleMap = cycleIds.isEmpty()
                ? Collections.emptyMap()
                : fieldCropCycleService.listByIds(cycleIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(FieldCropCycle::getId, x -> x, (a, b) -> a));
        Map<Long, FarmProcessStep> stepMap = stepIds.isEmpty()
                ? Collections.emptyMap()
                : farmProcessStepService.listByIds(stepIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(FarmProcessStep::getId, x -> x, (a, b) -> a));

        for (FarmRecordGroupStats row : rows) {
            if (row == null) {
                continue;
            }
            Field field = row.getFieldId() == null ? null : fieldMap.get(row.getFieldId());
            if (field != null && StringUtils.hasText(field.getName())) {
                row.setFieldName(field.getName());
            }
            FieldCropCycle cycle = row.getCycleId() == null ? null : cycleMap.get(row.getCycleId());
            if (cycle != null && StringUtils.hasText(cycle.getCycleName())) {
                row.setCycleName(cycle.getCycleName());
            }
            FarmProcessStep step = row.getLatestStepId() == null ? null : stepMap.get(row.getLatestStepId());
            if (step != null && StringUtils.hasText(step.getStepName())) {
                row.setLatestStepName(step.getStepName());
            }
        }

        return Result.success(rows);
    }

    /** 解析逗号分隔 ID 列表。 */
    private Set<Long> parseIdCsv(String value) {
        if (!StringUtils.hasText(value)) {
            return Collections.emptySet();
        }
        LinkedHashSet<Long> out = new LinkedHashSet<>();
        String[] parts = value.split(",");
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            String raw = part.trim();
            if (raw.isEmpty()) {
                continue;
            }
            try {
                long id = Long.parseLong(raw);
                if (id > 0L) {
                    out.add(id);
                }
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    @GetMapping("/{id}")
    /** 查询农事记录详情。 */
    public Result<FarmRecord> detail(@PathVariable Long id, HttpServletRequest request) {
        FarmRecord r = farmRecordService.getById(id);
        if (r == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        enrichFieldNames(Collections.singletonList(r));
        enrichStepNames(Collections.singletonList(r));
        enrichExtraLabelMaps(Collections.singletonList(r));
        applyRecordPermissions(r, request);
        return Result.success(r);
    }

    @GetMapping("/{id}/images")
    /** 查询农事记录图片列表（后台视角：返回全部审核状态）。 */
    public Result<List<FarmRecordImageView>> images(@PathVariable Long id, HttpServletRequest request) {
        return Result.success(resolveRecordImageViews(id, false));
    }

    /** 查询农事记录图片列表（小程序视角：审核通过可见，待审核给提示，驳回隐藏）。 */
    public Result<List<FarmRecordImageView>> miniappImages(Long id, HttpServletRequest request) {
        return Result.success(resolveRecordImageViews(id, true));
    }

    @PostMapping
    /** 新增农事记录。 */
    @Transactional(rollbackFor = Exception.class)
    public Result<FarmRecord> create(@RequestBody @Validated FarmRecordCreateReq req, HttpServletRequest request) {
        /*
         * 农事记录新增入口。
         *
         * 这里的关键不是普通 CRUD，而是“步骤驱动的动态参数校验”：
         * - stepId 决定当前要使用哪一套表单 schema；
         * - extraJson 承载该步骤下的扩展业务字段；
         * - 保存前必须保证 extraJson 与 schema 匹配。
         *
         * 这样主表只保存稳定主干字段，步骤差异化参数放到 extraJson 中扩展。
         */
        Result<FarmRecord> invalid = validateStepPayload(req.getStepId(), req.getExtraJson());
        if (invalid != null) {
            return invalid;
        }
        // 执行人不是简单信任前端，而是按当前用户身份和权限重新解析。
        OperatorAssignment operator = resolveOperatorAssignment(req.getOperatorUserId(), req.getOperatorName(), request, null, null);
        FarmRecord r = new FarmRecord();
        // 主关联田块。
        r.setFieldId(req.getFieldId());
        // cycleId 优先用请求值，否则回退到当前种植周期。
        r.setCycleId(resolveCycleId(req.getFieldId(), req.getCycleId()));
        // stepId 决定 extraJson 应遵循哪套表单 schema。
        r.setStepId(req.getStepId());
        r.setWorkDate(req.getWorkDate());
        // 执行人信息来自后端解析结果。
        r.setOperatorName(operator.operatorName);
        r.setOperatorUserId(operator.operatorUserId);
        r.setNotes(req.getNotes());
        // 下面几项是记录发生时的天气快照。
        r.setWeather(req.getWeather());
        r.setTemperature(req.getTemperature());
        r.setWeatherLocation(req.getWeatherLocation());
        r.setHumidity(req.getHumidity());
        r.setWindDirection(req.getWindDirection());
        r.setWindPower(req.getWindPower());
        r.setWeatherReportTime(req.getWeatherReportTime());
        // extraJson 是动态表单扩展字段的承载位置。
        r.setExtraJson(req.getExtraJson());
        // 先落主记录。
        farmRecordService.save(r);
        // 再绑定图片资源。
        bindRecordImages(r.getId(), req.getImageAssetIds(), request);
        // 最后同步田块和周期状态。
        syncFieldAndCycleStatus(r);
        return Result.success(r);
    }

    @PutMapping("/{id}")
    /** 更新农事记录。 */
    @Transactional(rollbackFor = Exception.class)
    public Result<FarmRecord> update(@PathVariable Long id, @RequestBody @Validated FarmRecordUpdateReq req, HttpServletRequest request) {
        /*
         * 更新时与新增走同一套 schema 校验逻辑。
         *
         * 这能避免一种常见问题：
         * 新增时参数合法，但更新时绕过动态字段校验，最后把历史记录改成了不符合步骤定义的结构。
         */
        Result<FarmRecord> invalid = validateStepPayload(req.getStepId(), req.getExtraJson());
        if (invalid != null) {
            return invalid;
        }
        FarmRecord r = farmRecordService.getById(id);
        if (r == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        PermissionSnapshot permission = resolvePermission(r, request);
        if (!permission.canEdit) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), permission.reason);
        }
        // 更新时同样重新解析执行人，避免直接信任前端值。
        OperatorAssignment operator = resolveOperatorAssignment(req.getOperatorUserId(), req.getOperatorName(), request, r.getOperatorUserId(), r.getOperatorName());
        r.setFieldId(req.getFieldId());
        r.setCycleId(resolveCycleId(req.getFieldId(), req.getCycleId()));
        r.setStepId(req.getStepId());
        r.setWorkDate(req.getWorkDate());
        r.setOperatorName(operator.operatorName);
        r.setOperatorUserId(operator.operatorUserId);
        r.setNotes(req.getNotes());
        r.setWeather(req.getWeather());
        r.setTemperature(req.getTemperature());
        r.setWeatherLocation(req.getWeatherLocation());
        r.setHumidity(req.getHumidity());
        r.setWindDirection(req.getWindDirection());
        r.setWindPower(req.getWindPower());
        r.setWeatherReportTime(req.getWeatherReportTime());
        // 更新时 extraJson 也必须继续满足当前步骤 schema。
        r.setExtraJson(req.getExtraJson());
        farmRecordService.updateById(r);
        bindRecordImages(r.getId(), req.getImageAssetIds(), request);
        syncFieldAndCycleStatus(r);
        return Result.success(r);
    }

    @DeleteMapping("/{id}")
    /** 删除农事记录。 */
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        FarmRecord r = farmRecordService.getById(id);
        if (r == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        PermissionSnapshot permission = resolvePermission(r, request);
        if (!permission.canDelete) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), permission.reason);
        }
        boolean ok = farmRecordService.removeById(id);
        if (ok) {
            AppUser current = AuthContext.getCurrentUser(request);
            Long currentUserId = current == null ? null : current.getId();
            boolean manageAll = hasFarmRecordManagePermission(request);
            mediaAssetService.bindAssetsToBiz("farm", id, Collections.emptyList(), currentUserId, manageAll, true, true);
        }
        return ok ? Result.success(null) : Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
    }

    /** 根据步骤阶段同步田块状态与种植计划状态。 */
    private void syncFieldAndCycleStatus(FarmRecord record) {
        if (record == null || record.getFieldId() == null) {
            return;
        }
        Field field = fieldService.getById(record.getFieldId());
        if (field == null) {
            return;
        }

        String stage = resolveStage(record);
        String nextFieldStatus = null;
        String nextCycleStatus = null;

        if ("sowing".equals(stage)) {
            nextFieldStatus = "sowing";
            nextCycleStatus = "active";
        } else if ("growing".equals(stage)) {
            nextFieldStatus = "growing";
            nextCycleStatus = "active";
        } else if ("harvesting".equals(stage)) {
            nextFieldStatus = "harvesting";
            nextCycleStatus = "completed";
        } else if ("fallow".equals(stage)) {
            nextFieldStatus = "fallow";
            nextCycleStatus = "completed";
        } else if ("idle".equals(stage)) {
            nextFieldStatus = "idle";
        }

        if (StringUtils.hasText(nextFieldStatus) && !nextFieldStatus.equalsIgnoreCase(field.getStatus())) {
            field.setStatus(nextFieldStatus);
            fieldService.updateById(field);
        }

        Long cycleId = resolveCycleId(record.getFieldId(), record.getCycleId());
        if (cycleId == null) {
            return;
        }
        FieldCropCycle cycle = fieldCropCycleService.getById(cycleId);
        if (cycle == null || !record.getFieldId().equals(cycle.getFieldId())) {
            return;
        }

        boolean changed = false;
        if (StringUtils.hasText(nextCycleStatus) && !nextCycleStatus.equalsIgnoreCase(cycle.getStatus())) {
            cycle.setStatus(nextCycleStatus);
            changed = true;
        }
        if ("completed".equalsIgnoreCase(String.valueOf(cycle.getStatus())) && cycle.getIsCurrent() != null && cycle.getIsCurrent() == 1) {
            cycle.setIsCurrent(0);
            changed = true;
        }
        if (record.getWorkDate() != null) {
            LocalDate workDate = record.getWorkDate().toLocalDate();
            if (cycle.getStartDate() == null) {
                cycle.setStartDate(workDate);
                changed = true;
            }
            if ("completed".equalsIgnoreCase(cycle.getStatus()) && cycle.getEndDate() == null) {
                cycle.setEndDate(workDate);
                changed = true;
            }
            if ("active".equalsIgnoreCase(cycle.getStatus()) && cycle.getEndDate() != null) {
                cycle.setEndDate(null);
                changed = true;
            }
        }
        if (changed) {
            fieldCropCycleService.updateById(cycle);
        }
    }

    /** 解析记录对应生长阶段。 */
    private String resolveStage(FarmRecord record) {
        if (record == null) {
            return null;
        }
        if (record.getStepId() != null) {
            FarmProcessStep step = farmProcessStepService.getById(record.getStepId());
            if (step != null && StringUtils.hasText(step.getGrowthStage())) {
                return normalizeStage(step.getGrowthStage());
            }
        }
        String notes = StringUtils.hasText(record.getNotes()) ? record.getNotes().trim() : "";
        if (notes.contains("休耕") || notes.contains("轮休")) {
            return "fallow";
        }
        return null;
    }

    /** 归一化阶段值。 */
    private String normalizeStage(String stage) {
        String s = StringUtils.hasText(stage) ? stage.trim().toLowerCase(Locale.ROOT) : "";
        if ("sowing".equals(s)) {
            return "sowing";
        }
        if ("growing".equals(s)) {
            return "growing";
        }
        if ("harvest".equals(s) || "harvesting".equals(s)) {
            return "harvesting";
        }
        return s;
    }

    /** 解析有效计划 ID（优先请求值，其次当前计划）。 */
    private Long resolveCycleId(Long fieldId, Long reqCycleId) {
        if (reqCycleId != null) {
            return reqCycleId;
        }
        if (fieldId == null) {
            return null;
        }
        FieldCropCycle current = fieldCropCycleService.findCurrentCycle(fieldId);
        return current == null ? null : current.getId();
    }

    /** 根据步骤 schema 校验动态参数。 */
    private Result<FarmRecord> validateStepPayload(Long stepId, String extraJson) {
        /*
         * 步骤动态参数校验总入口。
         *
         * 执行顺序：
         * 1. 根据 stepId 查出当前农事步骤；
         * 2. 通过 resolver 解析最终 schema；
         * 3. 通过 validator 校验 extraJson。
         *
         * 这样 create/update 控制器本身不需要理解每个步骤的字段细节，
         * 只负责把“按步骤找 schema 并校验”这条总规则卡住。
         */
        if (stepId == null) {
            return null;
        }
        // 先拿到步骤定义，后面才能知道 extraJson 应按什么规则校验。
        FarmProcessStep step = farmProcessStepService.getById(stepId);
        if (step == null) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "步骤ID无效");
        }
        // schema 可能来自动态配置表，也可能来自步骤自带 formSchema，由 resolver 统一兜底。
        String resolvedSchema = stepFormSchemaResolver.resolveFormSchema(step);
        // 再按 schema 校验 extraJson。
        String err = stepFormSchemaValidator.validate(resolvedSchema, extraJson);
        if (StringUtils.hasText(err)) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), err);
        }
        return null;
    }

    /** 绑定记录图片资源。 */
    private void bindRecordImages(Long recordId, List<Long> imageAssetIds, HttpServletRequest request) {
        if (recordId == null || imageAssetIds == null) {
            return;
        }
        AppUser current = AuthContext.getCurrentUser(request);
        Long currentUserId = current == null ? null : current.getId();
        boolean manageAll = hasFarmRecordManagePermission(request);
        mediaAssetService.bindAssetsToBiz("farm", recordId, imageAssetIds, currentUserId, manageAll, true, true);
    }

    /** 组装农事记录图片展示列表。 */
    private List<FarmRecordImageView> resolveRecordImageViews(Long recordId, boolean miniappView) {
        if (recordId == null || recordId <= 0) {
            return Collections.emptyList();
        }
        List<MediaAsset> rows = mediaAssetService.listBizAssets("farm", recordId, AssetDomainConstants.FILE_TYPE_IMAGE);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream()
                .map(row -> toRecordImageView(row, miniappView))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private FarmRecordImageView toRecordImageView(MediaAsset row, boolean miniappView) {
        if (row == null || row.getId() == null) {
            return null;
        }
        String reviewStatus = AssetDomainConstants.normalizeReviewStatus(row.getReviewStatus());
        if (miniappView && AssetDomainConstants.REVIEW_STATUS_REJECTED.equals(reviewStatus)) {
            return null;
        }
        FarmRecordImageView out = new FarmRecordImageView();
        out.setId(row.getId());
        out.setFileName(row.getFileName());
        out.setReviewStatus(reviewStatus);
        out.setReviewRemark(StringUtils.hasText(row.getReviewRemark()) ? row.getReviewRemark().trim() : null);
        out.setReviewStatusText(resolveImageReviewStatusText(reviewStatus));
        if (miniappView) {
            if (AssetDomainConstants.REVIEW_STATUS_APPROVED.equals(reviewStatus)) {
                out.setFileUrl(row.getFileUrl());
                out.setCanPreview(true);
                out.setHintMessage(null);
            } else {
                out.setFileUrl(null);
                out.setCanPreview(false);
                out.setHintMessage("该图片正在审核中，审核通过后可查看");
            }
            return out;
        }
        out.setFileUrl(row.getFileUrl());
        out.setCanPreview(StringUtils.hasText(row.getFileUrl()));
        out.setHintMessage(resolveAdminImageHint(reviewStatus, out.getReviewRemark()));
        return out;
    }

    private String resolveImageReviewStatusText(String reviewStatus) {
        if (AssetDomainConstants.REVIEW_STATUS_PENDING.equals(reviewStatus)) {
            return "待审核";
        }
        if (AssetDomainConstants.REVIEW_STATUS_REJECTED.equals(reviewStatus)) {
            return "未通过";
        }
        return "已通过";
    }

    private String resolveAdminImageHint(String reviewStatus, String reviewRemark) {
        if (AssetDomainConstants.REVIEW_STATUS_PENDING.equals(reviewStatus)) {
            return "该图片待审核，审核通过后小程序端可见";
        }
        if (AssetDomainConstants.REVIEW_STATUS_REJECTED.equals(reviewStatus)) {
            if (StringUtils.hasText(reviewRemark)) {
                return "审核未通过：" + reviewRemark;
            }
            return "该图片审核未通过，仅后台可查看预览";
        }
        return null;
    }

    /** 解析并归一化执行人信息。 */
    private OperatorAssignment resolveOperatorAssignment(
            Long reqOperatorUserId,
            String reqOperatorName,
            HttpServletRequest request,
            Long fallbackOperatorUserId,
            String fallbackOperatorName
    ) {
        /*
         * 执行人解析规则：
         * 1. 小程序用户提交时，执行人永远锁定为当前登录人自己；
         * 2. 后台管理端才允许显式选择其他执行人；
         * 3. 如果前端没传执行人，则回退到旧值或当前登录人。
         *
         * 这也是防止小程序前端伪造“代他人录入记录”的关键权限点。
         */
        AppUser current = AuthContext.getCurrentUser(request);
        // 小程序用户始终绑定自己为执行人，避免前端伪造他人执行记录。
        if (isMiniappUser(current) && current != null) {
            return new OperatorAssignment(current.getId(), resolveUserDisplayName(current));
        }

        // 后台如果显式选了操作人，就按用户 id 回填真实显示名。
        Long pickedUserId = reqOperatorUserId != null && reqOperatorUserId > 0 ? reqOperatorUserId : null;
        if (pickedUserId != null) {
            AppUser selected = appUserService.getById(pickedUserId);
            if (selected != null) {
                return new OperatorAssignment(selected.getId(), resolveUserDisplayName(selected));
            }
        }

        // 如果没传用户 id，但传了一个名字文本，也允许保留这个名字。
        String pickedName = StringUtils.hasText(reqOperatorName) ? reqOperatorName.trim() : null;
        if (StringUtils.hasText(pickedName)) {
            return new OperatorAssignment(pickedUserId, pickedName);
        }

        // 更新场景下，再回退到旧值。
        if (fallbackOperatorUserId != null || StringUtils.hasText(fallbackOperatorName)) {
            return new OperatorAssignment(fallbackOperatorUserId, fallbackOperatorName);
        }

        // 最后兜底成当前登录人。
        if (current != null) {
            return new OperatorAssignment(current.getId(), resolveUserDisplayName(current));
        }

        return new OperatorAssignment(null, null);
    }

    /** 解析用户展示名。 */
    private String resolveUserDisplayName(AppUser user) {
        if (user == null) {
            return null;
        }
        if (StringUtils.hasText(user.getRealName())) {
            return user.getRealName().trim();
        }
        if (StringUtils.hasText(user.getNickName())) {
            return user.getNickName().trim();
        }
        if (user.getId() != null) {
            return "用户#" + user.getId();
        }
        return null;
    }

    /** 批量回填步骤名称。 */
    private void enrichStepNames(List<FarmRecord> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> stepIds = rows.stream()
                .map(FarmRecord::getStepId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (stepIds.isEmpty()) {
            return;
        }
        Map<Long, String> stepNameMap = farmProcessStepService.listByIds(stepIds).stream()
                .filter(Objects::nonNull)
                .filter(x -> x.getId() != null)
                .collect(Collectors.toMap(FarmProcessStep::getId, x -> x.getStepName(), (a, b) -> a));
        for (FarmRecord row : rows) {
            if (row == null || row.getStepId() == null) {
                continue;
            }
            String stepName = stepNameMap.get(row.getStepId());
            if (StringUtils.hasText(stepName)) {
                row.setStepName(stepName);
            }
        }
    }

    /** 批量回填田块名称。 */
    private void enrichFieldNames(List<FarmRecord> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> fieldIds = rows.stream()
                .map(FarmRecord::getFieldId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (fieldIds.isEmpty()) {
            return;
        }
        Map<Long, String> fieldNameMap = fieldService.listByIds(fieldIds).stream()
                .filter(Objects::nonNull)
                .filter(x -> x.getId() != null)
                .collect(Collectors.toMap(Field::getId, Field::getName, (a, b) -> a));
        for (FarmRecord row : rows) {
            if (row == null || row.getFieldId() == null) {
                continue;
            }
            String fieldName = fieldNameMap.get(row.getFieldId());
            if (StringUtils.hasText(fieldName)) {
                row.setFieldName(fieldName);
            }
        }
    }

    /** 批量回填动态字段标签映射。 */
    private void enrichExtraLabelMaps(List<FarmRecord> rows) {
        /*
         * extraJson 存的是机器字段和值，直接给前端展示并不友好。
         * 这里会把 schema 再解析一遍，补出：
         * - extraLabelMap：key -> 中文字段名
         * - extraValueLabelMap：value -> 中文选项名
         *
         * 这样前端详情页就不用自己反查 schema，也能把 extraJson 渲染成人能看懂的内容。
         */
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> stepIds = rows.stream()
                .map(FarmRecord::getStepId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (stepIds.isEmpty()) {
            return;
        }
        Map<Long, StepSchemaMeta> stepSchemaMetaMap = resolveStepSchemaMetaMap(stepIds);
        if (stepSchemaMetaMap.isEmpty()) {
            return;
        }
        for (FarmRecord row : rows) {
            if (row == null || row.getStepId() == null) {
                continue;
            }
            StepSchemaMeta meta = stepSchemaMetaMap.get(row.getStepId());
            if (meta == null) {
                continue;
            }
            if (meta.labelMap != null && !meta.labelMap.isEmpty()) {
                row.setExtraLabelMap(meta.labelMap);
            }
            if (meta.valueLabelMap != null && !meta.valueLabelMap.isEmpty()) {
                row.setExtraValueLabelMap(meta.valueLabelMap);
            }
        }
    }

    /** 批量解析步骤 schema 元数据。 */
    private Map<Long, StepSchemaMeta> resolveStepSchemaMetaMap(Set<Long> stepIds) {
        /*
         * 批量解析 schema 元数据，而不是每条记录单独查一步。
         * 这样列表页/详情页在做 extraJson 展示翻译时可以减少重复解析成本。
         */
        if (stepIds == null || stepIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // 批量查步骤定义，避免一条记录一条记录查。
        List<FarmProcessStep> steps = farmProcessStepService.listByIds(stepIds);
        if (steps == null || steps.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, StepSchemaMeta> out = new HashMap<>();
        // 一次性把动态表单配置预加载出来，后面循环里复用。
        Map<Long, com.dahe.v2.modules.dynamic.model.DynamicFormConfig> configMap = stepFormSchemaResolver.resolveConfigMap(steps);
        for (FarmProcessStep step : steps) {
            if (step == null || step.getId() == null) {
                continue;
            }
            // 这里用批量预加载出来的 configMap，避免循环中反复查 dynamic_form_config。
            String schemaJson = stepFormSchemaResolver.resolveFormSchema(step, configMap);
            StepSchemaMeta meta = parseSchemaMeta(schemaJson);
            if ((meta.labelMap != null && !meta.labelMap.isEmpty())
                    || (meta.valueLabelMap != null && !meta.valueLabelMap.isEmpty())) {
                out.put(step.getId(), meta);
            }
        }
        return out;
    }

    /** 解析步骤 schema 的 key/label 与选项映射。 */
    private StepSchemaMeta parseSchemaMeta(String schemaJson) {
        /*
         * schema 不只用于“写入时校验”，还用于“读取时翻译”。
         *
         * 例如 extraJson 里存的是：
         * {"fertilizerType":"compound"}
         *
         * 展示时需要把它翻译成：
         * 肥料类型：复合肥
         *
         * 因此这里把 schema 里的 key/label、option value/label 再提取一遍。
         */
        if (!StringUtils.hasText(schemaJson)) {
            return StepSchemaMeta.empty();
        }
        try {
            // schema 本质上是一组字段描述数组。
            List<Map<String, Object>> rows = objectMapper.readValue(schemaJson, new TypeReference<List<Map<String, Object>>>() {});
            Map<String, String> labelMap = new LinkedHashMap<>();
            Map<String, Map<String, String>> valueLabelMap = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                if (row == null) {
                    continue;
                }
                // key 是 extraJson 真正存储时的字段名。
                String key = row.get("key") == null ? "" : String.valueOf(row.get("key")).trim();
                if (!StringUtils.hasText(key)) {
                    continue;
                }
                // label 是给前端展示的中文字段名。
                String label = row.get("label") == null ? "" : String.valueOf(row.get("label")).trim();
                labelMap.putIfAbsent(key, StringUtils.hasText(label) ? label : key);
                // options 里的 value/label 也要提取出来，方便详情页翻译展示。
                Map<String, String> optionValueMap = parseOptionValueMap(row.get("options"));
                if (!optionValueMap.isEmpty()) {
                    valueLabelMap.put(key, optionValueMap);
                }
            }
            return new StepSchemaMeta(labelMap, valueLabelMap);
        } catch (Exception ignored) {
            return StepSchemaMeta.empty();
        }
    }

    @SuppressWarnings("unchecked")
    /** 解析 options 为 value->label 映射。 */
    private Map<String, String> parseOptionValueMap(Object optionsRaw) {
        if (!(optionsRaw instanceof List)) {
            return Collections.emptyMap();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Object optionRaw : (List<Object>) optionsRaw) {
            if (optionRaw == null) {
                continue;
            }
            if (optionRaw instanceof Map) {
                Map<String, Object> optionMap = (Map<String, Object>) optionRaw;
                String value = optionMap.get("value") == null ? "" : String.valueOf(optionMap.get("value")).trim();
                String label = optionMap.get("label") == null ? "" : String.valueOf(optionMap.get("label")).trim();
                if (!StringUtils.hasText(value) && StringUtils.hasText(label)) {
                    value = label;
                }
                String finalLabel = StringUtils.hasText(label) ? label : value;
                if (StringUtils.hasText(value)) {
                    out.putIfAbsent(value, finalLabel);
                }
                if (StringUtils.hasText(label)) {
                    out.putIfAbsent(label, label);
                }
                continue;
            }
            String text = String.valueOf(optionRaw).trim();
            if (StringUtils.hasText(text)) {
                out.putIfAbsent(text, text);
            }
        }
        return out;
    }

    /** 动态参数 schema 元数据容器。 */
    private static class StepSchemaMeta {
        private final Map<String, String> labelMap;
        private final Map<String, Map<String, String>> valueLabelMap;

        private StepSchemaMeta(Map<String, String> labelMap, Map<String, Map<String, String>> valueLabelMap) {
            this.labelMap = labelMap == null ? Collections.emptyMap() : labelMap;
            this.valueLabelMap = valueLabelMap == null ? Collections.emptyMap() : valueLabelMap;
        }

        private static StepSchemaMeta empty() {
            return new StepSchemaMeta(Collections.emptyMap(), Collections.emptyMap());
        }
    }

    /** 通过乡镇关键词反查田块范围。 */
    private Set<Long> resolveFieldIdScopeByTownship(String township) {
        if (!StringUtils.hasText(township)) {
            return null;
        }
        String text = township.trim();
        LambdaQueryWrapper<Field> fieldQw = new LambdaQueryWrapper<>();
        fieldQw.and(w -> w.like(Field::getProvince, text)
                .or()
                .like(Field::getCity, text)
                .or()
                .like(Field::getDistrict, text)
                .or()
                .like(Field::getTownship, text)
                .or()
                .like(Field::getFormattedAddress, text)
                .or()
                .like(Field::getLocationDesc, text));
        List<Field> townshipFields = fieldService.list(fieldQw);
        if (townshipFields == null || townshipFields.isEmpty()) {
            return Collections.emptySet();
        }
        return townshipFields.stream()
                .map(Field::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * 农事记录写接口权限校验：
     * 1. 后台用户必须拥有农事记录菜单权限；
     * 2. 小程序用户可进入记录写路径（具体可改删由记录策略二次判定）；
     * 3. 其他用户类型拒绝。
     */

    /** 管理端农事记录菜单权限。 */
    private boolean hasFarmRecordManagePermission(HttpServletRequest request) {
        return AuthContext.hasAnyMenuPermission(request, "/farm-records-manage");
    }

    /** 是否小程序用户。 */
    private boolean isMiniappUser(AppUser user) {
        if (user == null || !StringUtils.hasText(user.getUserType())) {
            return false;
        }
        return "miniapp".equalsIgnoreCase(user.getUserType().trim());
    }

    /** 小程序控制台能力标记。 */
    private boolean isMiniappConsoleUser(AppUser user) {
        return isMiniappUser(user) && user != null && Objects.equals(user.getCanConsole(), 1);
    }

    /** 解析“我的农事记录”查询范围。 */
    private RecordOwnerScope resolveRecordOwnerScope(HttpServletRequest request, boolean mineOnly) {
        if (!mineOnly) {
            return RecordOwnerScope.disabled();
        }
        AppUser user = request == null ? null : AuthContext.getCurrentUser(request);
        if (user == null) {
            return RecordOwnerScope.empty();
        }
        String operatorName = resolveUserDisplayName(user);
        if (user.getId() == null && !StringUtils.hasText(operatorName)) {
            return RecordOwnerScope.empty();
        }
        return new RecordOwnerScope(user.getId(), operatorName, false);
    }

    /** 计算并回填记录可编辑/可删除权限。 */
    private void applyRecordPermissions(FarmRecord record, HttpServletRequest request) {
        PermissionSnapshot p = resolvePermission(record, request);
        record.setCanEdit(p.canEdit);
        record.setCanDelete(p.canDelete);
    }

    /** 解析当前用户对记录的操作权限。 */
    private PermissionSnapshot resolvePermission(FarmRecord record, HttpServletRequest request) {
        PermissionSnapshot out = new PermissionSnapshot();
        out.canEdit = false;
        out.canDelete = false;
        out.reason = "权限不足";
        if (record == null) {
            return out;
        }
        AppUser user = request == null ? null : AuthContext.getCurrentUser(request);
        if (user == null) {
            return out;
        }
        if (AuthContext.isAdminUser(user) && hasFarmRecordManagePermission(request)) {
            out.canEdit = true;
            out.canDelete = true;
            out.reason = null;
            return out;
        }
        if (!isMiniappUser(user)) {
            out.reason = "仅小程序用户可按创建者策略操作";
            return out;
        }
        RecordPolicyConfig policy;
        try {
            policy = recordPolicyConfigService.getOrInit();
        } catch (Exception e) {
            policy = null;
        }
        boolean owner = record.getOperatorUserId() != null && user.getId() != null && user.getId().equals(record.getOperatorUserId());
        if (!owner && StringUtils.hasText(record.getOperatorName())) {
            String currentName = resolveUserDisplayName(user);
            owner = StringUtils.hasText(currentName) && currentName.trim().equalsIgnoreCase(record.getOperatorName().trim());
        }
        boolean inWindow = isWithinWindow(record.getWorkDate(), policy == null ? 48 : policy.getEditWindowHours());
        boolean allowUpdate = policy == null || policy.getAllowOperatorUpdate() == null || policy.getAllowOperatorUpdate() == 1;
        boolean allowDelete = policy == null || policy.getAllowOperatorDelete() == null || policy.getAllowOperatorDelete() == 1;

        out.canEdit = owner && inWindow && allowUpdate;
        out.canDelete = owner && inWindow && allowDelete;
        if (!owner) {
            out.reason = "仅记录创建者可编辑或删除";
        } else if (!inWindow) {
            out.reason = "已超过可编辑或删除时限";
        } else if (!allowUpdate && !allowDelete) {
            out.reason = "当前策略不允许创建者编辑或删除";
        } else {
            out.reason = null;
        }
        return out;
    }

    /** 判断记录是否处于可编辑时间窗口。 */
    private boolean isWithinWindow(LocalDateTime workDate, Integer windowHours) {
        if (workDate == null) {
            return true;
        }
        int hours = windowHours == null ? 48 : windowHours;
        if (hours <= 0) {
            return true;
        }
        LocalDateTime limit = LocalDateTime.now().minusHours(hours);
        return !workDate.isBefore(limit);
    }

    /** 权限快照。 */
    private static class PermissionSnapshot {
        private boolean canEdit;
        private boolean canDelete;
        private String reason;
    }

    /** 执行人解析结果。 */
    private static class OperatorAssignment {
        private final Long operatorUserId;
        private final String operatorName;

        private OperatorAssignment(Long operatorUserId, String operatorName) {
            this.operatorUserId = operatorUserId;
            this.operatorName = operatorName;
        }
    }

    /** 记录归属筛选范围。 */
    private static class RecordOwnerScope {
        private final Long operatorUserId;
        private final String operatorName;
        private final boolean emptyResult;

        private RecordOwnerScope(Long operatorUserId, String operatorName, boolean emptyResult) {
            this.operatorUserId = operatorUserId;
            this.operatorName = operatorName;
            this.emptyResult = emptyResult;
        }

        private static RecordOwnerScope disabled() {
            return new RecordOwnerScope(null, null, false);
        }

        private static RecordOwnerScope empty() {
            return new RecordOwnerScope(null, null, true);
        }
    }

    @Data
    public static class OperatorOption {
        /** 用户 ID。 */
        private Long id;
        /** 真实姓名。 */
        private String realName;
        /** 昵称。 */
        private String nickName;
        /** 手机号。 */
        private String phone;
        /** 角色编码。 */
        private String roleCode;
        /** 审核状态。 */
        private String status;
        /** 展示名。 */
        private String displayName;
        /** 展示补充信息。 */
        private String displayDesc;
    }

    @Data
    public static class OperatorDetailResp {
        /** 用户 ID。 */
        private Long id;
        /** 展示名。 */
        private String displayName;
        /** 真实姓名。 */
        private String realName;
        /** 昵称。 */
        private String nickName;
        /** 手机号。 */
        private String phone;
        /** 角色编码。 */
        private String roleCode;
        /** 角色名称。 */
        private String roleName;
        /** 审核状态。 */
        private String status;
        /** 是否可登录后台。 */
        private Integer canConsole;
    }

    @Data
    public static class FarmRecordCreateReq {
        @NotNull(message = "田块不能为空")
        /** 田块 ID。 */
        private Long fieldId;

        /** 计划 ID，可空（自动回填当前计划）。 */
        private Long cycleId;

        /** 步骤 ID。 */
        private Long stepId;

        @NotNull(message = "作业时间不能为空")
        @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        /** 作业时间。 */
        private LocalDateTime workDate;

        /** 执行人用户 ID。 */
        private Long operatorUserId;
        /** 执行人姓名（兜底）。 */
        private String operatorName;
        /** 备注。 */
        private String notes;
        /** 天气。 */
        private String weather;
        /** 温度。 */
        private String temperature;
        /** 天气位置。 */
        private String weatherLocation;
        /** 湿度。 */
        private String humidity;
        /** 风向。 */
        private String windDirection;
        /** 风力。 */
        private String windPower;
        /** 天气发布时间文本。 */
        private String weatherReportTime;
        /** 动态参数 JSON。 */
        private String extraJson;
        /** 图片资源 ID 列表。 */
        private List<Long> imageAssetIds;
    }

    @Data
    public static class FarmRecordUpdateReq {
        @NotNull(message = "田块不能为空")
        /** 田块 ID。 */
        private Long fieldId;

        /** 计划 ID，可空（自动回填当前计划）。 */
        private Long cycleId;

        /** 步骤 ID。 */
        private Long stepId;

        @NotNull(message = "作业时间不能为空")
        @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        /** 作业时间。 */
        private LocalDateTime workDate;

        /** 执行人用户 ID。 */
        private Long operatorUserId;
        /** 执行人姓名（兜底）。 */
        private String operatorName;
        /** 备注。 */
        private String notes;
        /** 天气。 */
        private String weather;
        /** 温度。 */
        private String temperature;
        /** 天气位置。 */
        private String weatherLocation;
        /** 湿度。 */
        private String humidity;
        /** 风向。 */
        private String windDirection;
        /** 风力。 */
        private String windPower;
        /** 天气发布时间文本。 */
        private String weatherReportTime;
        /** 动态参数 JSON。 */
        private String extraJson;
        /** 图片资源 ID 列表。 */
        private List<Long> imageAssetIds;
    }

    /** 用户实体转执行人选项。 */
    private OperatorOption toOperatorOption(AppUser row) {
        OperatorOption item = new OperatorOption();
        item.setId(row == null ? null : row.getId());
        item.setRealName(row == null ? null : row.getRealName());
        item.setNickName(row == null ? null : row.getNickName());
        item.setPhone(row == null ? null : row.getPhone());
        item.setRoleCode(row == null ? null : row.getRoleCode());
        item.setStatus(row == null ? null : row.getStatus());
        String displayName = resolveUserDisplayName(row);
        item.setDisplayName(displayName);
        String phone = row == null ? null : row.getPhone();
        String roleText = resolveRoleText(row);
        if (StringUtils.hasText(phone) && StringUtils.hasText(roleText)) {
            item.setDisplayDesc(phone + " / " + roleText);
        } else if (StringUtils.hasText(phone)) {
            item.setDisplayDesc(phone);
        } else {
            item.setDisplayDesc(roleText);
        }
        return item;
    }

    /** 用户实体转执行人详情。 */
    private OperatorDetailResp toOperatorDetail(AppUser row) {
        OperatorDetailResp out = new OperatorDetailResp();
        out.setId(row == null ? null : row.getId());
        out.setDisplayName(resolveUserDisplayName(row));
        out.setRealName(row == null ? null : row.getRealName());
        out.setNickName(row == null ? null : row.getNickName());
        out.setPhone(row == null ? null : row.getPhone());
        out.setRoleCode(row == null ? null : row.getRoleCode());
        out.setRoleName(resolveRoleText(row));
        out.setStatus(row == null ? null : row.getStatus());
        out.setCanConsole(row == null ? null : row.getCanConsole());
        return out;
    }

    /**
     * 用户身份文案：
     * 小程序端只区分“是否有控制台”，后台端统一展示为“后台用户”。
     */
    private String resolveRoleText(AppUser user) {
        if (user == null) {
            return null;
        }
        if (isMiniappConsoleUser(user)) {
            return "小程序管理员";
        }
        if (isMiniappUser(user)) {
            return "小程序用户";
        }
        if (AuthContext.isAdminUser(user)) {
            return "后台用户";
        }
        return "用户";
    }
}
