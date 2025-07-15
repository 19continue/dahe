package com.dahe.v2.modules.field.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.assets.model.MediaAsset;
import com.dahe.v2.modules.assets.service.MediaAssetService;
import com.dahe.v2.modules.auth.support.AuthContext;
import com.dahe.v2.modules.crop.model.Crop;
import com.dahe.v2.modules.crop.service.CropService;
import com.dahe.v2.modules.farm.model.FarmRecord;
import com.dahe.v2.modules.farm.model.FarmRecordGroupStats;
import com.dahe.v2.modules.farm.process.model.FarmProcessStep;
import com.dahe.v2.modules.farm.process.model.FarmProcessTemplate;
import com.dahe.v2.modules.farm.process.service.FarmProcessStepService;
import com.dahe.v2.modules.farm.process.service.FarmProcessTemplateService;
import com.dahe.v2.modules.farm.process.support.StepFormSchemaResolver;
import com.dahe.v2.modules.farm.service.FarmRecordService;
import com.dahe.v2.modules.field.cycle.model.FieldCropCycle;
import com.dahe.v2.modules.field.cycle.service.FieldCropCycleService;
import com.dahe.v2.modules.field.model.Field;
import com.dahe.v2.modules.field.model.FieldCropVarietyGroup;
import com.dahe.v2.modules.field.model.FieldPageQuery;
import com.dahe.v2.modules.field.service.FieldCropVarietyGroupCodec;
import com.dahe.v2.modules.field.service.FieldService;
import com.dahe.v2.modules.miniapp.search.service.MiniappSearchIndexService;
import com.dahe.v2.modules.user.model.AppUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v2/fields")
@Validated
    /**
 * 田块控制器。
 * 职责覆盖三条主链路：
 * 1. 田块主数据管理（列表/详情/新增/编辑/删除/启停/排序）；
 * 2. 田块种植计划管理（计划列表、新增、更新、删除、切换当前计划）；
 * 3. 田块流程视图聚合（模板步骤、完成进度、最近农事记录）。
 */
public class FieldController {

    /**
     * “当前位置自动匹配田块”的默认搜索半径。
     * 这个接口用于新增记录页的自动预选，半径应明显小于“附近田块”的 20km。
     */
    private static final double CURRENT_FIELD_MATCH_RADIUS_KM = 2D;

    /**
     * 与田块中心点足够接近时，直接认为已经站在该田块附近。
     */
    private static final double CURRENT_FIELD_MIN_RADIUS_METERS = 15D;
    private static final String LOCATION_UNKNOWN_TEXT = "未知";
    private static final String LOCATION_ABNORMAL_TEXT = "位置异常";
    private static final String CURRENT_FIELD_RELATION_TEXT = "您正处于该田块中";

    /**
     * 只有一个候选田块时，允许更宽一点的匹配距离。
     */
    private static final double CURRENT_FIELD_MAX_RADIUS_METERS = 180D;

    /**
     * 多候选场景下，最近田块与第二近田块的最小差值。
     * 用于避免相邻田块过密时误选。
     */
    private static final double CURRENT_FIELD_RADIUS_RELAX_FACTOR = 1.15D;

    /**
     * 多候选场景下允许自动命中的最大距离。
     */

    private final FieldService fieldService;
    private final CropService cropService;
    private final FarmProcessTemplateService farmProcessTemplateService;
    private final FarmProcessStepService farmProcessStepService;
    private final StepFormSchemaResolver stepFormSchemaResolver;
    private final FarmRecordService farmRecordService;
    private final FieldCropCycleService fieldCropCycleService;
    private final ObjectMapper objectMapper;
    private final MiniappSearchIndexService miniappSearchIndexService;
    private final MediaAssetService mediaAssetService;

    public FieldController(
            FieldService fieldService,
            CropService cropService,
            FarmProcessTemplateService farmProcessTemplateService,
            FarmProcessStepService farmProcessStepService,
            StepFormSchemaResolver stepFormSchemaResolver,
            FarmRecordService farmRecordService,
            FieldCropCycleService fieldCropCycleService,
            ObjectMapper objectMapper,
            MiniappSearchIndexService miniappSearchIndexService,
            MediaAssetService mediaAssetService
    ) {
        this.fieldService = fieldService;
        this.cropService = cropService;
        this.farmProcessTemplateService = farmProcessTemplateService;
        this.farmProcessStepService = farmProcessStepService;
        this.stepFormSchemaResolver = stepFormSchemaResolver;
        this.farmRecordService = farmRecordService;
        this.fieldCropCycleService = fieldCropCycleService;
        this.objectMapper = objectMapper;
        this.miniappSearchIndexService = miniappSearchIndexService;
        this.mediaAssetService = mediaAssetService;
    }

    /** 分页查询田块，并补齐当前计划作物摘要与地址展示字段。 */
    @GetMapping
    public Result<Page<Field>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cropType,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String township,
            @RequestParam(required = false) String cropVariety,
            @RequestParam(required = false) Integer enabled,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDirection,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(defaultValue = "false") boolean includeDisabled,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) long pageSize
    ) {
        Page<Field> result = fieldService.pageFields(buildFieldPageQuery(
                keyword,
                stage,
                status,
                cropType,
                province,
                city,
                district,
                township,
                cropVariety,
                enabled,
                includeDisabled,
                sortBy,
                sortDirection,
                latitude,
                longitude,
                page,
                pageSize
        ));
        for (Field field : result.getRecords()) {
            applyNearbyPresentationFields(field);
            applyLocationPresentation(field, latitude, longitude, true);
        }
        return Result.success(result);
    }

    /**
     * 小程序新增记录页的“自动选择我所在田块”入口。
     * 只有在最近田块足够近、且候选关系足够明确时才返回命中结果；
     * 如果判断不够稳定，则返回 null，交由用户手动选择。
     */
    public Result<Field> currentMatchedField(
            Double latitude,
            Double longitude,
            Double radiusKm,
            boolean includeDisabled
    ) {
        if (!isValidCoordinate(latitude, longitude)) {
            return Result.success(null);
        }
        Page<Field> page = fieldService.pageNearbyFields(
                null,
                latitude,
                longitude,
                normalizeCurrentMatchRadiusKm(radiusKm),
                includeDisabled,
                1,
                8
        );
        List<Field> candidates = page == null || page.getRecords() == null
                ? Collections.emptyList()
                : page.getRecords();
        for (Field field : candidates) {
            applyNearbyPresentationFields(field);
        }
        Field matched = pickCurrentMatchedField(candidates);
        if (matched != null) {
            applyLocationPresentation(matched, latitude, longitude, true);
        }
        return Result.success(matched);
    }

    /** 查询田块详情。 */
    @GetMapping("/{id}")
    public Result<Field> detail(
            @PathVariable Long id,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude
    ) {
        Field f = fieldService.getById(id);
        if (f == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        applyCurrentPlanCropSummary(f);
        normalizeAddressFields(f);
        applyLocationPresentation(f, latitude, longitude, true);
        return Result.success(f);
    }

    /**
     * 小程序“常用田块”查询入口。
     * 定义统一为：当前小程序用户已提交农事记录中，使用频次更高的田块。
     */
    public Result<Page<Field>> commonFields(
            HttpServletRequest request,
            String keyword,
            Double latitude,
            Double longitude,
            boolean includeDisabled,
            long page,
            long pageSize
    ) {
        Page<Field> emptyPage = new Page<>(page, pageSize, 0);
        AppUser currentUser = request == null ? null : AuthContext.getCurrentUser(request);
        String operatorName = resolveUserDisplayName(currentUser);
        if (currentUser == null || (currentUser.getId() == null && !StringUtils.hasText(operatorName))) {
            emptyPage.setRecords(Collections.emptyList());
            return Result.success(emptyPage);
        }

        List<FarmRecordGroupStats> groupedRows = farmRecordService.listGroupedRecords(
                null,
                null,
                currentUser.getId(),
                operatorName,
                null,
                null,
                null,
                200
        );
        if (groupedRows == null || groupedRows.isEmpty()) {
            emptyPage.setRecords(Collections.emptyList());
            return Result.success(emptyPage);
        }

        List<Long> orderedFieldIds = groupedRows.stream()
                .map(FarmRecordGroupStats::getFieldId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (orderedFieldIds.isEmpty()) {
            emptyPage.setRecords(Collections.emptyList());
            return Result.success(emptyPage);
        }

        Map<Long, Field> fieldMap = fieldService.listByIds(orderedFieldIds).stream()
                .filter(Objects::nonNull)
                .filter(field -> field.getId() != null)
                .collect(Collectors.toMap(Field::getId, field -> field, (left, right) -> left));
        String safeKeyword = normalizeQueryText(keyword);
        List<Field> orderedFields = new ArrayList<>();
        for (Long fieldId : orderedFieldIds) {
            Field field = fieldMap.get(fieldId);
            if (field == null) {
                continue;
            }
            if (!includeDisabled && Objects.equals(field.getEnabled(), 0)) {
                continue;
            }
            applyCurrentPlanCropSummary(field);
            normalizeAddressFields(field);
            applyLocationPresentation(field, latitude, longitude, true);
            if (StringUtils.hasText(safeKeyword) && !matchesFieldKeyword(field, safeKeyword)) {
                continue;
            }
            orderedFields.add(field);
        }
        if (isValidCoordinate(latitude, longitude)) {
            orderedFields.sort(Comparator.comparing(
                    field -> {
                        Double distanceMeters = field == null ? null : field.getDistanceMeters();
                        if (distanceMeters != null && Double.isFinite(distanceMeters) && distanceMeters >= 0D) {
                            return distanceMeters;
                        }
                        return Double.MAX_VALUE;
                    }
            ));
        }
        return Result.success(sliceFieldPage(orderedFields, page, pageSize));
    }

    /**
     * 小程序“附近田块”查询入口。
     * 定义统一为：以当前位置为圆心、20km 半径内的田块，按距离从近到远排序。
     */
    public Result<Page<Field>> nearbyFields(
            String keyword,
            Double latitude,
            Double longitude,
            Double radiusKm,
            boolean includeDisabled,
            long page,
            long pageSize
    ) {
        if (!isValidCoordinate(latitude, longitude)) {
            Page<Field> emptyPage = new Page<>(page, pageSize, 0);
            emptyPage.setRecords(Collections.emptyList());
            return Result.success(emptyPage);
        }
        double safeRadiusKm = normalizeNearbyRadiusKm(radiusKm);
        Page<Field> result = fieldService.pageNearbyFields(
                normalizeQueryText(keyword),
                latitude,
                longitude,
                safeRadiusKm,
                includeDisabled,
                page,
                pageSize
        );
        for (Field field : result.getRecords()) {
            applyCurrentPlanCropSummary(field);
            normalizeAddressFields(field);
            applyLocationPresentation(field, latitude, longitude, true);
        }
        return Result.success(result);
    }

    /** 新增田块（仅 admin/supervisor）。 */
    @PostMapping
    public Result<Field> create(HttpServletRequest request, @RequestBody @Validated FieldCreateReq req) {
        validateFieldCoordinates(req.getLocationLat(), req.getLocationLng());
        Long currentUserId = resolveCurrentUserId(request);
        Field f = new Field();
        f.setName(req.getName());
        f.setAreaMu(req.getAreaMu());
        applyFieldCropGroups(f, resolveRequestCropGroups(req.getCropVarietyGroups(), req.getCropType(), req.getCropVariety()));
        f.setProvince(normalizeAddressText(req.getProvince()));
        f.setCity(normalizeAddressText(req.getCity()));
        f.setDistrict(normalizeAddressText(req.getDistrict()));
        f.setTownship(normalizeAddressText(req.getTownship()));
        f.setFormattedAddress(normalizeAddressText(req.getFormattedAddress()));
        f.setStatus(normalizeFieldStage(req.getStage(), req.getStatus(), "idle"));
        f.setEnabled(resolveEnabled(req.getEnabled(), true));
        f.setLocationLat(req.getLocationLat());
        f.setLocationLng(req.getLocationLng());
        f.setLocationDesc(req.getLocationDesc());
        f.setCoverImageUrl(req.getCoverImageUrl());
        f.setRemark(req.getRemark());
        f.setSortOrder(fieldService.nextSortOrder());
        normalizeAddressFields(f);

        fieldService.save(f);
        fieldService.refreshLocationPoint(f.getId());
        syncFieldCoverAssetBinding(f.getId(), req.getCoverImageUrl(), req.getCoverImageAssetId(), currentUserId);
        miniappSearchIndexService.syncField(fieldService.getById(f.getId()));
        return Result.success(f);
    }

    /** 更新田块（仅 admin/supervisor）。 */
    @PutMapping("/{id}")
    public Result<Field> update(HttpServletRequest request, @PathVariable Long id, @RequestBody @Validated FieldUpdateReq req) {
        Field f = fieldService.getById(id);
        if (f == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        validateFieldCoordinates(req.getLocationLat(), req.getLocationLng());
        Long currentUserId = resolveCurrentUserId(request);

        f.setName(req.getName());
        f.setAreaMu(req.getAreaMu());
        if (hasCropPayload(req)) {
            applyFieldCropGroups(f, resolveRequestCropGroups(req.getCropVarietyGroups(), req.getCropType(), req.getCropVariety()));
        }
        f.setProvince(normalizeAddressText(req.getProvince()));
        f.setCity(normalizeAddressText(req.getCity()));
        f.setDistrict(normalizeAddressText(req.getDistrict()));
        f.setTownship(normalizeAddressText(req.getTownship()));
        f.setFormattedAddress(normalizeAddressText(req.getFormattedAddress()));
        String nextStage = normalizeFieldStage(req.getStage(), req.getStatus(), f.getStatus());
        f.setStatus(StringUtils.hasText(nextStage) ? nextStage : "idle");
        f.setEnabled(resolveEnabled(req.getEnabled(), f.getEnabled() == null || f.getEnabled() == 1));
        f.setLocationLat(req.getLocationLat());
        f.setLocationLng(req.getLocationLng());
        f.setLocationDesc(req.getLocationDesc());
        f.setCoverImageUrl(req.getCoverImageUrl());
        f.setRemark(req.getRemark());
        normalizeAddressFields(f);

        fieldService.updateById(f);
        fieldService.refreshLocationPoint(f.getId());
        syncFieldCoverAssetBinding(f.getId(), req.getCoverImageUrl(), req.getCoverImageAssetId(), currentUserId);
        miniappSearchIndexService.syncField(fieldService.getById(f.getId()));
        return Result.success(f);
    }

    /** 删除田块（仅 admin）。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        syncFieldCoverAssetBinding(id, null, null, resolveCurrentUserId(request));
        boolean ok = fieldService.removeById(id);
        if (ok) {
            miniappSearchIndexService.removeField(id);
        }
        return ok ? Result.success(null) : Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
    }

    /** 切换田块启用状态（仅 admin）。 */
    @PutMapping("/{id}/enabled")
    public Result<Field> updateEnabled(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody @Validated FieldEnabledReq req
    ) {
        Field row = fieldService.getById(id);
        if (row == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        if (req.getEnabled() == null) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "启用状态不能为空");
        }
        row.setEnabled(req.getEnabled() ? 1 : 0);
        fieldService.updateById(row);
        miniappSearchIndexService.syncField(row);
        applyCurrentPlanCropSummary(row);
        normalizeAddressFields(row);
        return Result.success(row);
    }

    /** 批量重排田块顺序（仅 admin/supervisor）。 */
    @PostMapping("/reorder")
    public Result<Void> reorder(HttpServletRequest request, @RequestBody @Validated FieldReorderReq req) {
        fieldService.reorder(req.getIds());
        return Result.success(null);
    }

    /** 地图点位查询：返回轻量字段以支撑地图聚合渲染。 */
    @GetMapping("/map/points")
    public Result<Page<FieldMapPoint>> mapPoints(
            @RequestParam(required = false) Integer enabled,
            @RequestParam(defaultValue = "false") boolean includeDisabled,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "200") @Min(1) long pageSize
    ) {
        Page<Field> p = fieldService.pageFields(buildFieldPageQuery(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                enabled,
                includeDisabled,
                null,
                null,
                null,
                null,
                page,
                pageSize
        ));
        for (Field field : p.getRecords()) {
            applyCurrentPlanCropSummary(field);
            normalizeAddressFields(field);
        }
        Page<FieldMapPoint> out = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        out.setRecords(p.getRecords().stream().map(FieldMapPoint::from).collect(Collectors.toList()));
        return Result.success(out);
    }

    /** 查询指定田块下全部计划。 */
    @GetMapping("/{id}/cycles")
    public Result<List<CycleItem>> cycles(@PathVariable Long id) {
        Field field = fieldService.getById(id);
        if (field == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        List<FieldCropCycle> cycles = fieldCropCycleService.listByFieldId(id);
        return Result.success(cycles.stream().map(this::toCycleItem).collect(Collectors.toList()));
    }

    /** 按 fieldIds 批量查询计划，便于前端列表按田块分组回显。 */
    @GetMapping("/cycles/by-fields")
    public Result<Map<Long, List<CycleItem>>> cyclesByFields(
            @RequestParam(required = false) String fieldIds
    ) {
        List<Long> idList = parseIdCsv(fieldIds);
        if (idList.isEmpty()) {
            return Result.success(Collections.emptyMap());
        }
        Set<Long> exists = fieldService.listByIds(idList).stream()
                .filter(Objects::nonNull)
                .map(Field::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, List<CycleItem>> out = new LinkedHashMap<>();
        for (Long fieldId : idList) {
            if (!exists.contains(fieldId)) {
                out.put(fieldId, Collections.emptyList());
                continue;
            }
            List<FieldCropCycle> cycles = fieldCropCycleService.listByFieldId(fieldId);
            out.put(fieldId, cycles.stream().map(this::toCycleItem).collect(Collectors.toList()));
        }
        return Result.success(out);
    }

    /** 计划后台分页检索（含地域过滤、计划模式过滤、作物关键字过滤）。 */
    @GetMapping("/cycles/all")
    public Result<Page<CycleAdminItem>> allCycles(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String planMode,
            @RequestParam(required = false) String cropType,
            @RequestParam(required = false) String cropVariety,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String township,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) long pageSize
    ) {
        Set<Long> fieldIdScope = resolveFieldIdScopeByRegion(province, city, district, township);
        if (fieldIdScope != null && fieldIdScope.isEmpty()) {
            return Result.success(new Page<>(page, pageSize));
        }
        LambdaQueryWrapper<FieldCropCycle> qw = new LambdaQueryWrapper<>();
        if (fieldIdScope != null) {
            qw.in(FieldCropCycle::getFieldId, fieldIdScope);
        }
        if (StringUtils.hasText(keyword)) {
            qw.like(FieldCropCycle::getCycleName, keyword.trim());
        }
        if (StringUtils.hasText(status)) {
            qw.eq(FieldCropCycle::getStatus, status.trim().toLowerCase(Locale.ROOT));
        }
        if (StringUtils.hasText(planMode)) {
            qw.eq(FieldCropCycle::getPlanMode, normalizePlanMode(planMode));
        }
        String safeCropType = normalizeQueryText(cropType);
        if (StringUtils.hasText(safeCropType)) {
            qw.like(FieldCropCycle::getCropsJson, buildCropJsonToken("name", safeCropType));
        }
        String safeCropVariety = normalizeQueryText(cropVariety);
        if (StringUtils.hasText(safeCropVariety)) {
            qw.like(FieldCropCycle::getCropsJson, buildCropJsonToken("variety", safeCropVariety));
        }
        qw.orderByDesc(FieldCropCycle::getUpdatedAt).orderByDesc(FieldCropCycle::getId);

        Page<FieldCropCycle> raw = fieldCropCycleService.page(new Page<>(page, pageSize), qw);
        List<FieldCropCycle> records = raw.getRecords();
        Set<Long> fieldIds = records.stream()
                .map(FieldCropCycle::getFieldId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Field> fieldMap = fieldIds.isEmpty()
                ? Collections.emptyMap()
                : fieldService.listByIds(fieldIds).stream()
                .filter(Objects::nonNull)
                .filter(x -> x.getId() != null)
                .collect(Collectors.toMap(Field::getId, x -> x, (a, b) -> a));

        Page<CycleAdminItem> out = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        out.setRecords(records.stream()
                .map(row -> toCycleAdminItem(row, fieldMap.get(row.getFieldId())))
                .collect(Collectors.toList()));
        return Result.success(out);
    }

    /** 基于省市区乡镇过滤可见田块范围，供计划后台查询复用。 */
    private Set<Long> resolveFieldIdScopeByRegion(String province, String city, String district, String township) {
        String safeProvince = normalizeQueryText(province);
        String safeCity = normalizeQueryText(city);
        String safeDistrict = normalizeQueryText(district);
        String safeTownship = normalizeQueryText(township);
        if (!StringUtils.hasText(safeProvince)
                && !StringUtils.hasText(safeCity)
                && !StringUtils.hasText(safeDistrict)
                && !StringUtils.hasText(safeTownship)) {
            return null;
        }
        LambdaQueryWrapper<Field> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(safeProvince)) {
            qw.like(Field::getProvince, safeProvince);
        }
        if (StringUtils.hasText(safeCity)) {
            qw.like(Field::getCity, safeCity);
        }
        if (StringUtils.hasText(safeDistrict)) {
            qw.like(Field::getDistrict, safeDistrict);
        }
        if (StringUtils.hasText(safeTownship)) {
            qw.like(Field::getTownship, safeTownship);
        }
        List<Field> rows = fieldService.list(qw);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptySet();
        }
        return rows.stream()
                .map(Field::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /** 构造 cropsJson 的模糊检索 token（形如 "name":"玉米"）。 */
    private String buildCropJsonToken(String key, String value) {
        String k = String.valueOf(key == null ? "" : key).trim();
        String v = String.valueOf(value == null ? "" : value).trim();
        if (!StringUtils.hasText(k) || !StringUtils.hasText(v)) {
            return "";
        }
        return "\"" + k + "\":\"" + v + "\"";
    }

    /** 新增田块计划（仅 admin/supervisor）。 */
    @PostMapping("/{id}/cycles")
    public Result<FieldCropCycle> createCycle(HttpServletRequest request, @PathVariable Long id, @RequestBody @Validated CycleCreateReq req) {
        Field field = fieldService.getById(id);
        if (field == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        String planMode = normalizePlanMode(req.getPlanMode());
        List<Long> requestTemplateIds = parseLongList(req.getTemplateIdsJson());
        List<CycleCropBinding> cropBindings = normalizeCycleCropBindings(req.getCropsJson(), requestTemplateIds);
        if (!"fallow".equals(planMode) && cropBindings.isEmpty()) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "至少需要一条作物配置");
        }
        if (!"fallow".equals(planMode)) {
            for (CycleCropBinding binding : cropBindings) {
                if (!StringUtils.hasText(binding.getName())) {
                    return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "作物名称不能为空");
                }
                if (binding.getTemplateId() == null || binding.getTemplateId() <= 0L) {
                    return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "每条作物配置必须绑定一个流程模板");
                }
            }
        }
        List<Long> finalTemplateIds = collectTemplateIds(cropBindings, requestTemplateIds);
        if (!"fallow".equals(planMode) && finalTemplateIds.isEmpty()) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "模板编号列表不能为空");
        }
        if (!"fallow".equals(planMode)) {
            String bindError = validateTemplateBindingCompatibility(cropBindings, finalTemplateIds);
            if (bindError != null) {
                return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), bindError);
            }
        }
        FieldCropCycle cycle = new FieldCropCycle();
        cycle.setFieldId(id);
        cycle.setCycleName(req.getCycleName());
        cycle.setCropsJson(writeCycleCropsJson(cropBindings));
        cycle.setTemplateIdsJson(writeLongListJson(finalTemplateIds));
        cycle.setPlanMode(planMode);
        cycle.setStartDate(req.getStartDate());
        cycle.setEndDate(req.getEndDate());
        cycle.setStatus(normalizeCycleStatus(req.getStatus(), req.getEndDate()));
        cycle.setIsCurrent(req.getIsCurrent() != null && req.getIsCurrent() ? 1 : 0);
        FieldCropCycle saved = fieldCropCycleService.saveCycleAndReconcile(cycle, cycle.getIsCurrent() != null && cycle.getIsCurrent() == 1);
        return Result.success(saved == null ? cycle : saved);
    }

    /** 更新田块计划（仅 admin/supervisor）。 */
    @PutMapping("/{id}/cycles/{cycleId}")
    public Result<FieldCropCycle> updateCycle(
            HttpServletRequest request,
            @PathVariable Long id,
            @PathVariable Long cycleId,
            @RequestBody @Validated CycleUpdateReq req
    ) {
        Field field = fieldService.getById(id);
        if (field == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        FieldCropCycle cycle = fieldCropCycleService.getById(cycleId);
        if (cycle == null || !Objects.equals(id, cycle.getFieldId())) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }

        String planMode = normalizePlanMode(req.getPlanMode());
        List<Long> requestTemplateIds = parseLongList(req.getTemplateIdsJson());
        List<CycleCropBinding> cropBindings = normalizeCycleCropBindings(req.getCropsJson(), requestTemplateIds);
        if (!"fallow".equals(planMode) && cropBindings.isEmpty()) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "至少需要一条作物配置");
        }
        if (!"fallow".equals(planMode)) {
            for (CycleCropBinding binding : cropBindings) {
                if (!StringUtils.hasText(binding.getName())) {
                    return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "作物名称不能为空");
                }
                if (binding.getTemplateId() == null || binding.getTemplateId() <= 0L) {
                    return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "每条作物配置必须绑定一个流程模板");
                }
            }
        }
        List<Long> finalTemplateIds = collectTemplateIds(cropBindings, requestTemplateIds);
        if (!"fallow".equals(planMode) && finalTemplateIds.isEmpty()) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "模板编号列表不能为空");
        }
        if (!"fallow".equals(planMode)) {
            String bindError = validateTemplateBindingCompatibility(cropBindings, finalTemplateIds);
            if (bindError != null) {
                return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), bindError);
            }
        }

        cycle.setCycleName(req.getCycleName());
        cycle.setCropsJson(writeCycleCropsJson(cropBindings));
        cycle.setTemplateIdsJson(writeLongListJson(finalTemplateIds));
        cycle.setPlanMode(planMode);
        cycle.setStartDate(req.getStartDate());
        cycle.setEndDate(req.getEndDate());
        cycle.setStatus(normalizeCycleStatus(req.getStatus(), req.getEndDate()));
        cycle.setIsCurrent(req.getIsCurrent() != null && req.getIsCurrent() ? 1 : 0);
        FieldCropCycle updated = fieldCropCycleService.updateCycleAndReconcile(cycle, cycle.getIsCurrent() != null && cycle.getIsCurrent() == 1);
        return Result.success(updated == null ? fieldCropCycleService.getById(cycle.getId()) : updated);
    }

    /** 将指定计划切换为当前计划（仅 admin/supervisor）。 */
    @PutMapping("/{id}/cycles/{cycleId}/current")
    public Result<Void> setCurrentCycle(HttpServletRequest request, @PathVariable Long id, @PathVariable Long cycleId) {
        Field field = fieldService.getById(id);
        if (field == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        boolean ok = fieldCropCycleService.setCurrentCycle(id, cycleId);
        if (!ok) {
            return Result.failure(ErrorCode.VALIDATION_ERROR.getCode(), "该田块下未找到对应种植计划");
        }
        return Result.success(null);
    }

    /** 删除田块计划（仅 admin/supervisor）。 */
    @DeleteMapping("/{id}/cycles/{cycleId}")
    public Result<Void> deleteCycle(HttpServletRequest request, @PathVariable Long id, @PathVariable Long cycleId) {
        Field field = fieldService.getById(id);
        if (field == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        FieldCropCycle cycle = fieldCropCycleService.getById(cycleId);
        if (cycle == null || !Objects.equals(cycle.getFieldId(), id)) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        boolean removed = fieldCropCycleService.removeCycleAndReconcile(id, cycleId);
        if (!removed) {
            return Result.failure(
                    ErrorCode.VALIDATION_ERROR.getCode(),
                    "计划正在被农事记录引用，无法删除"
            );
        }
        return Result.success(null);
    }

    /**
     * 查询田块流程视图。
     * 返回内容包含：计划列表、模板步骤、步骤完成度、当前步骤、分段信息。
     */
    @GetMapping("/{id}/process")
    public Result<FieldProcessResp> fieldProcess(@PathVariable Long id, @RequestParam(required = false) Long cycleId) {
        Field field = fieldService.getById(id);
        if (field == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }

        List<FieldCropCycle> cycles = fieldCropCycleService.listByFieldId(id);
        FieldCropCycle selectedCycle = pickCycle(cycles, cycleId);

        CycleCropBinding primaryCrop = resolvePrimaryCrop(selectedCycle, field);
        Crop crop = resolveCropEntity(primaryCrop == null ? null : primaryCrop.getName(), primaryCrop == null ? null : primaryCrop.getVariety());

        List<Long> templateIds = resolveTemplateIds(selectedCycle, crop == null ? null : crop.getId());
        List<FarmProcessTemplate> templates = templateIds.isEmpty() ? Collections.emptyList() : farmProcessTemplateService.listByIds(templateIds);
        Map<Long, FarmProcessTemplate> templateMap = templates.stream().collect(Collectors.toMap(FarmProcessTemplate::getId, x -> x, (a, b) -> a));

        List<FarmProcessStep> steps = templateIds.isEmpty()
                ? Collections.emptyList()
                : farmProcessStepService.listByTemplateIds(templateIds);
        stepFormSchemaResolver.applyResolvedSchemas(steps);

        steps.sort(Comparator
                .comparing((FarmProcessStep s) -> templateIds.indexOf(s.getTemplateId()))
                .thenComparing(s -> s.getSortOrder() == null ? 0 : s.getSortOrder())
                .thenComparing(FarmProcessStep::getId));

        FieldProcessResp resp = new FieldProcessResp();
        resp.setFieldId(field.getId());
        resp.setFieldName(field.getName());
        resp.setCropType(resolveCropTypeLabel(field.getCropType(), selectedCycle));
        if (crop != null) {
            resp.setCropId(crop.getId());
        }

        if (!templates.isEmpty()) {
            resp.setTemplateId(templates.get(0).getId());
            resp.setTemplateName(templates.get(0).getTemplateName());
        }

        resp.setSelectedCycleId(selectedCycle == null ? null : selectedCycle.getId());
        resp.setCycles(cycles.stream().map(this::toCycleItem).collect(Collectors.toList()));

        List<ProcessStepItem> stepItems = steps.stream().map(step -> ProcessStepItem.from(step, templateMap.get(step.getTemplateId()))).collect(Collectors.toList());
        Map<Long, Long> doneCountMap = new HashMap<>();
        Map<Long, LocalDateTime> lastWorkDateMap = new HashMap<>();
        if (!steps.isEmpty()) {
            List<Long> stepIds = steps.stream().map(FarmProcessStep::getId).collect(Collectors.toList());
            LambdaQueryWrapper<FarmRecord> rw = new LambdaQueryWrapper<>();
            rw.eq(FarmRecord::getFieldId, id)
                    .in(FarmRecord::getStepId, stepIds)
                    .orderByDesc(FarmRecord::getWorkDate);
            if (selectedCycle != null) {
                rw.eq(FarmRecord::getCycleId, selectedCycle.getId());
            }
            List<FarmRecord> records = farmRecordService.list(rw);
            for (FarmRecord record : records) {
                if (record.getStepId() == null) {
                    continue;
                }
                doneCountMap.merge(record.getStepId(), 1L, Long::sum);
                if (!lastWorkDateMap.containsKey(record.getStepId())) {
                    lastWorkDateMap.put(record.getStepId(), record.getWorkDate());
                }
            }
        }

        int currentStepIndex = -1;
        for (int i = 0; i < stepItems.size(); i++) {
            ProcessStepItem item = stepItems.get(i);
            long doneCount = doneCountMap.getOrDefault(item.getId(), 0L);
            item.setDoneCount(doneCount);
            item.setLastWorkDate(lastWorkDateMap.get(item.getId()));
            if (currentStepIndex < 0 && doneCount == 0L) {
                currentStepIndex = i;
            }
        }
        if (currentStepIndex < 0 && !stepItems.isEmpty()) {
            currentStepIndex = stepItems.size() - 1;
        }
        if (currentStepIndex >= 0 && currentStepIndex < stepItems.size()) {
            ProcessStepItem currentStep = stepItems.get(currentStepIndex);
            currentStep.setCurrent(true);
            resp.setCurrentStepId(currentStep.getId());
            resp.setCurrentStepIndex(currentStepIndex);
        }

        resp.setSteps(stepItems);
        List<PlanSegmentItem> segments = buildPlanSegments(field, selectedCycle, templateIds, stepItems);
        resp.setSegments(segments);
        if (!segments.isEmpty()) {
            resp.setSelectedSegmentKey(segments.get(0).getSegmentKey());
        }
        return Result.success(resp);
    }

    /** 查询田块最近农事记录（默认最近 8 条）。 */
    @GetMapping("/{id}/farm-records/recent")
    public Result<List<FarmRecord>> recentFarmRecords(
            @PathVariable Long id,
            @RequestParam(required = false) Long cycleId,
            @RequestParam(required = false) String stepIds,
            @RequestParam(defaultValue = "8") @Min(1) int limit
    ) {
        Field field = fieldService.getById(id);
        if (field == null) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        Set<Long> stepIdScope = new LinkedHashSet<>(parseIdCsv(stepIds));
        List<FarmRecord> records = farmRecordService.listRecentByField(id, cycleId, stepIdScope, limit);
        if (records.isEmpty()) {
            return Result.success(records);
        }
        Set<Long> recordStepIds = records.stream()
                .map(FarmRecord::getStepId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, String> stepNameMap = recordStepIds.isEmpty()
                ? Collections.emptyMap()
                : farmProcessStepService.listByIds(recordStepIds).stream()
                .filter(step -> step != null && step.getId() != null)
                .collect(Collectors.toMap(FarmProcessStep::getId, FarmProcessStep::getStepName, (left, right) -> left));
        for (FarmRecord record : records) {
            record.setFieldName(field.getName());
            if (record.getStepId() != null) {
                record.setStepName(stepNameMap.get(record.getStepId()));
            }
        }
        return Result.success(records);
    }

    /** 解析计划绑定模板列表：优先计划配置，其次模板默认配置。 */
    private List<Long> resolveTemplateIds(FieldCropCycle cycle, Long cropId) {
        List<Long> fromCycle = parseLongList(cycle == null ? null : cycle.getTemplateIdsJson());
        if (cycle != null) {
            List<CropSegmentConfig> configs = parseCropSegments(cycle, fromCycle);
            LinkedHashSet<Long> dedup = new LinkedHashSet<>();
            for (CropSegmentConfig cfg : configs) {
                if (cfg == null || cfg.getTemplateIds() == null) {
                    continue;
                }
                for (Long id : cfg.getTemplateIds()) {
                    if (id != null && id > 0L) {
                        dedup.add(id);
                    }
                }
            }
            if (!dedup.isEmpty()) {
                return new ArrayList<>(dedup);
            }
        }
        if (!fromCycle.isEmpty()) {
            return fromCycle;
        }
        FarmProcessTemplate template = farmProcessTemplateService.findDefaultTemplate(cropId);
        if (template == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(template.getId());
    }

    /** 用当前计划作物配置回填田块展示字段（含结构化作物组合）。 */
    private void applyCurrentPlanCropSummary(Field field) {
        if (field == null || field.getId() == null) {
            return;
        }
        FieldCropCycle currentCycle = fieldCropCycleService.findCurrentCycle(field.getId());
        if (currentCycle == null) {
            attachFieldCropGroupsFromStored(field);
            return;
        }
        List<FieldCropVarietyGroup> groups = FieldCropVarietyGroupCodec.fromCycleCropsJson(objectMapper, currentCycle.getCropsJson());
        if (groups.isEmpty()) {
            field.setCropType(null);
            field.setCropVariety(null);
            field.setCropVarietyGroupsJson(null);
            field.setCropVarietyGroups(Collections.emptyList());
            return;
        }
        applyFieldCropGroups(field, groups);
    }

    /** 将结构化作物组合写回到田块对象（仅内存回填，不落库）。 */
    private void applyFieldCropGroups(Field field, List<FieldCropVarietyGroup> groupsInput) {
        if (field == null) {
            return;
        }
        List<FieldCropVarietyGroup> groups = FieldCropVarietyGroupCodec.normalize(groupsInput);
        if (groups.isEmpty()) {
            field.setCropType(null);
            field.setCropVariety(null);
            field.setCropVarietyGroupsJson(null);
            field.setCropVarietyGroups(Collections.emptyList());
            return;
        }
        FieldCropVarietyGroupCodec.CropSummary summary = FieldCropVarietyGroupCodec.toLegacySummary(groups);
        field.setCropType(summary.getCropType());
        field.setCropVariety(summary.getCropVariety());
        field.setCropVarietyGroupsJson(FieldCropVarietyGroupCodec.toFieldJson(objectMapper, groups));
        field.setCropVarietyGroups(groups);
    }

    /** 回填存量字段中的结构化组合（先 JSON，后旧字符串回退）。 */
    private void attachFieldCropGroupsFromStored(Field field) {
        if (field == null) {
            return;
        }
        List<FieldCropVarietyGroup> groups = FieldCropVarietyGroupCodec.fromFieldJson(objectMapper, field.getCropVarietyGroupsJson());
        if (groups.isEmpty()) {
            groups = FieldCropVarietyGroupCodec.fromLegacyTexts(field.getCropType(), field.getCropVariety());
        }
        if (groups.isEmpty()) {
            field.setCropVarietyGroups(Collections.emptyList());
            return;
        }
        FieldCropVarietyGroupCodec.CropSummary summary = FieldCropVarietyGroupCodec.toLegacySummary(groups);
        field.setCropType(summary.getCropType());
        field.setCropVariety(summary.getCropVariety());
        field.setCropVarietyGroups(groups);
    }

    /** 解析新增/编辑入参的作物组合，兼容旧 `cropType/cropVariety`。 */
    private List<FieldCropVarietyGroup> resolveRequestCropGroups(
            List<FieldCropVarietyGroup> requestGroups,
            String cropType,
            String cropVariety
    ) {
        List<FieldCropVarietyGroup> groups = FieldCropVarietyGroupCodec.normalize(requestGroups);
        if (!groups.isEmpty()) {
            return groups;
        }
        return FieldCropVarietyGroupCodec.fromLegacyTexts(cropType, cropVariety);
    }

    /**
     * 判断更新请求是否显式携带了作物信息。
     * 仅在显式传入时才覆盖存量字段，避免前端未传该字段时误清空。
     */
    private boolean hasCropPayload(FieldUpdateReq req) {
        return req != null
                && (req.getCropVarietyGroups() != null
                || req.getCropType() != null
                || req.getCropVariety() != null);
    }

    /** 选择计划中的主作物；无计划时回退田块旧字段。 */
    private CycleCropBinding resolvePrimaryCrop(FieldCropCycle cycle, Field fallbackField) {
        List<CycleCropBinding> rows = normalizeCycleCropBindings(cycle == null ? null : cycle.getCropsJson(), parseLongList(cycle == null ? null : cycle.getTemplateIdsJson()));
        for (CycleCropBinding row : rows) {
            if (row != null && StringUtils.hasText(row.getName())) {
                return row;
            }
        }
        List<FieldCropVarietyGroup> fallbackGroups = FieldCropVarietyGroupCodec.fromFieldJson(
                objectMapper,
                fallbackField == null ? null : fallbackField.getCropVarietyGroupsJson()
        );
        if (fallbackGroups.isEmpty() && fallbackField != null) {
            fallbackGroups = FieldCropVarietyGroupCodec.fromLegacyTexts(fallbackField.getCropType(), fallbackField.getCropVariety());
        }
        if (fallbackGroups.isEmpty()) {
            return null;
        }
        FieldCropVarietyGroup first = fallbackGroups.get(0);
        CycleCropBinding fallback = new CycleCropBinding();
        fallback.setName(readText(first.getCropType()));
        fallback.setVariety(readText(first.getCropVariety()));
        return fallback;
    }

    /** 按“分类+品种”组合解析作物实体，兼容旧数据回退查询。 */
    private Crop resolveCropEntity(String cropType, String cropVariety) {
        String safeCropType = readText(cropType);
        String safeCropVariety = readText(cropVariety);
        if (!StringUtils.hasText(safeCropType)) {
            return null;
        }

        if (StringUtils.hasText(safeCropVariety)) {
            LambdaQueryWrapper<Crop> varietyQw = new LambdaQueryWrapper<>();
            varietyQw.eq(Crop::getNodeType, "variety")
                    .eq(Crop::getName, safeCropType)
                    .eq(Crop::getVariety, safeCropVariety)
                    .orderByAsc(Crop::getSortOrder)
                    .orderByDesc(Crop::getCreatedAt)
                    .last("limit 1");
            Crop variety = cropService.getOne(varietyQw, false);
            if (variety != null) {
                return variety;
            }
        }

        LambdaQueryWrapper<Crop> categoryQw = new LambdaQueryWrapper<>();
        categoryQw.eq(Crop::getNodeType, "category")
                .eq(Crop::getName, safeCropType)
                .orderByAsc(Crop::getSortOrder)
                .orderByDesc(Crop::getCreatedAt)
                .last("limit 1");
        Crop category = cropService.getOne(categoryQw, false);
        if (category != null) {
            return category;
        }

        LambdaQueryWrapper<Crop> varietyByNameQw = new LambdaQueryWrapper<>();
        varietyByNameQw.eq(Crop::getNodeType, "variety")
                .eq(Crop::getName, safeCropType)
                .orderByAsc(Crop::getSortOrder)
                .orderByDesc(Crop::getCreatedAt)
                .last("limit 1");
        Crop firstVariety = cropService.getOne(varietyByNameQw, false);
        if (firstVariety != null) {
            return firstVariety;
        }

        LambdaQueryWrapper<Crop> legacyQw = new LambdaQueryWrapper<>();
        legacyQw.eq(Crop::getName, safeCropType)
                .eq(StringUtils.hasText(safeCropVariety), Crop::getVariety, safeCropVariety)
                .orderByAsc(Crop::getSortOrder)
                .orderByDesc(Crop::getCreatedAt)
                .last("limit 1");
        return cropService.getOne(legacyQw, false);
    }

    /** 校验“作物配置”与“流程模板绑定作物”是否一致。 */
    private String validateTemplateBindingCompatibility(List<CycleCropBinding> cropBindings, List<Long> templateIds) {
        if (cropBindings == null || cropBindings.isEmpty() || templateIds == null || templateIds.isEmpty()) {
            return null;
        }
        Map<Long, FarmProcessTemplate> templateMap = farmProcessTemplateService.listByIds(templateIds).stream()
                .filter(Objects::nonNull)
                .filter(x -> x.getId() != null)
                .collect(Collectors.toMap(FarmProcessTemplate::getId, x -> x, (a, b) -> a));
        if (templateMap.isEmpty()) {
            return "流程模板不存在，请刷新后重试";
        }
        Set<Long> bindCropIds = templateMap.values().stream()
                .map(FarmProcessTemplate::getCropId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Crop> bindCropMap = bindCropIds.isEmpty()
                ? Collections.emptyMap()
                : cropService.listByIds(bindCropIds).stream()
                .filter(Objects::nonNull)
                .filter(x -> x.getId() != null)
                .collect(Collectors.toMap(Crop::getId, x -> x, (a, b) -> a));

        for (CycleCropBinding binding : cropBindings) {
            if (binding == null || binding.getTemplateId() == null || binding.getTemplateId() <= 0L) {
                continue;
            }
            FarmProcessTemplate template = templateMap.get(binding.getTemplateId());
            if (template == null) {
                return "存在无效流程模板，请重新选择";
            }
            Crop bindCrop = bindCropMap.get(template.getCropId());
            if (!isTemplateMatched(binding, bindCrop)) {
                String cropName = readText(binding.getName());
                String variety = readText(binding.getVariety());
                String cropLabel = StringUtils.hasText(variety) ? (cropName + " · " + variety) : cropName;
                return "作物「" + cropLabel + "」与所选流程模板不匹配";
            }
        }
        return null;
    }

    /** 判断单条作物绑定是否允许使用指定模板。 */
    private boolean isTemplateMatched(CycleCropBinding binding, Crop templateBindCrop) {
        if (binding == null || templateBindCrop == null) {
            return true;
        }
        Crop selectedCrop = resolveCropEntity(binding.getName(), binding.getVariety());
        String selectedCategoryName = selectedCrop == null ? readText(binding.getName()) : readText(selectedCrop.getName());
        String selectedVarietyName = selectedCrop == null ? readText(binding.getVariety()) : readText(selectedCrop.getVariety());
        Long selectedCategoryId = null;
        if (selectedCrop != null) {
            String selectedType = String.valueOf(selectedCrop.getNodeType() == null ? "" : selectedCrop.getNodeType()).trim().toLowerCase(Locale.ROOT);
            if ("variety".equals(selectedType)) {
                selectedCategoryId = selectedCrop.getParentId();
            } else if ("category".equals(selectedType)) {
                selectedCategoryId = selectedCrop.getId();
            }
        }

        String templateType = String.valueOf(templateBindCrop.getNodeType() == null ? "" : templateBindCrop.getNodeType()).trim().toLowerCase(Locale.ROOT);
        String templateCategoryName = readText(templateBindCrop.getName());
        String templateVarietyName = readText(templateBindCrop.getVariety());
        Long templateCategoryId = "variety".equals(templateType) ? templateBindCrop.getParentId() : templateBindCrop.getId();

        boolean sameCategory = isSameCategory(selectedCategoryId, selectedCategoryName, templateCategoryId, templateCategoryName);
        if (!sameCategory) {
            return false;
        }

        // 选了品种：允许“品种专属模板”或“作物通用模板”。
        if (StringUtils.hasText(selectedVarietyName)) {
            if (!StringUtils.hasText(templateVarietyName)) {
                return true;
            }
            return isSameText(selectedVarietyName, templateVarietyName);
        }

        // 未选品种：仅允许作物通用模板，避免误选其他品种专属模板。
        return !StringUtils.hasText(templateVarietyName);
    }

    /** 分类优先按 ID 比较，缺 ID 时回退按名称比较。 */
    private boolean isSameCategory(Long leftId, String leftName, Long rightId, String rightName) {
        if (leftId != null && rightId != null) {
            return Objects.equals(leftId, rightId);
        }
        return isSameText(leftName, rightName);
    }

    /** 忽略大小写的文本相等比较。 */
    private boolean isSameText(String left, String right) {
        String l = readText(left);
        String r = readText(right);
        if (!StringUtils.hasText(l) || !StringUtils.hasText(r)) {
            return false;
        }
        return l.equalsIgnoreCase(r);
    }

    /** 归一化 cropsJson 为强类型绑定列表，补齐 templateId 回退策略。 */
    private List<CycleCropBinding> normalizeCycleCropBindings(String cropsJson, List<Long> fallbackTemplateIds) {
        if (!StringUtils.hasText(cropsJson)) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(cropsJson, new TypeReference<List<Map<String, Object>>>() {});
            List<CycleCropBinding> out = new ArrayList<>();
            for (int i = 0; i < raw.size(); i++) {
                Map<String, Object> row = raw.get(i);
                if (row == null) {
                    continue;
                }
                String cropName = readText(row.get("name"));
                String cropVariety = readText(row.get("variety"));
                if (!StringUtils.hasText(cropName) && !StringUtils.hasText(cropVariety)) {
                    continue;
                }
                List<Long> templateIds = parseLongList(row.get("templateIds"));
                if (templateIds.isEmpty() && row.get("templateId") != null) {
                    templateIds = parseLongList(Collections.singletonList(row.get("templateId")));
                }
                Long templateId = templateIds.isEmpty() ? null : templateIds.get(0);
                if ((templateId == null || templateId <= 0L) && fallbackTemplateIds != null && !fallbackTemplateIds.isEmpty()) {
                    if (i < fallbackTemplateIds.size()) {
                        templateId = fallbackTemplateIds.get(i);
                    } else if (fallbackTemplateIds.size() == 1) {
                        templateId = fallbackTemplateIds.get(0);
                    }
                }

                CycleCropBinding binding = new CycleCropBinding();
                binding.setName(cropName);
                binding.setVariety(cropVariety);
                if (templateId != null && templateId > 0L) {
                    binding.setTemplateId(templateId);
                }
                out.add(binding);
            }
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** 汇总作物绑定中的模板 ID，保持顺序并去重。 */
    private List<Long> collectTemplateIds(List<CycleCropBinding> cropBindings, List<Long> fallbackTemplateIds) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (CycleCropBinding row : cropBindings) {
            if (row == null || row.getTemplateId() == null || row.getTemplateId() <= 0L) {
                continue;
            }
            ids.add(row.getTemplateId());
        }
        if (ids.isEmpty() && fallbackTemplateIds != null) {
            for (Long id : fallbackTemplateIds) {
                if (id != null && id > 0L) {
                    ids.add(id);
                }
            }
        }
        return new ArrayList<>(ids);
    }

    /** 将作物绑定写回标准 cropsJson。 */
    private String writeCycleCropsJson(List<CycleCropBinding> cropBindings) {
        if (cropBindings == null || cropBindings.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CycleCropBinding binding : cropBindings) {
            if (binding == null || !StringUtils.hasText(binding.getName())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", binding.getName().trim());
            if (StringUtils.hasText(binding.getVariety())) {
                row.put("variety", binding.getVariety().trim());
            }
            if (binding.getTemplateId() != null && binding.getTemplateId() > 0L) {
                row.put("templateId", binding.getTemplateId());
                row.put("templateIds", Collections.singletonList(binding.getTemplateId()));
            }
            rows.add(row);
        }
        if (rows.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (Exception e) {
            return null;
        }
    }

    /** 将模板 ID 列表序列化为 JSON。 */
    private String writeLongListJson(List<Long> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (Exception e) {
            return null;
        }
    }

    /** 从计划列表中挑选“本次展示计划”（指定 ID > current active > current > active > 第一条）。 */
    private FieldCropCycle pickCycle(List<FieldCropCycle> cycles, Long cycleId) {
        if (cycles == null || cycles.isEmpty()) {
            return null;
        }
        if (cycleId != null) {
            for (FieldCropCycle c : cycles) {
                if (cycleId.equals(c.getId())) {
                    return c;
                }
            }
        }
        for (FieldCropCycle c : cycles) {
            if ("active".equalsIgnoreCase(c.getStatus()) && c.getIsCurrent() != null && c.getIsCurrent() == 1) {
                return c;
            }
        }
        for (FieldCropCycle c : cycles) {
            if (c.getIsCurrent() != null && c.getIsCurrent() == 1) {
                return c;
            }
        }
        for (FieldCropCycle c : cycles) {
            if ("active".equalsIgnoreCase(c.getStatus())) {
                return c;
            }
        }
        return cycles.get(0);
    }

    /** 解析 JSON 数组字符串为 Long 列表。 */
    private List<Long> parseLongList(String jsonText) {
        if (!StringUtils.hasText(jsonText)) {
            return Collections.emptyList();
        }
        try {
            List<Object> raw = objectMapper.readValue(jsonText, new TypeReference<List<Object>>() {});
            List<Long> ids = new ArrayList<>();
            for (Object item : raw) {
                if (item == null) continue;
                if (item instanceof Number) {
                    ids.add(((Number) item).longValue());
                } else {
                    String s = String.valueOf(item).trim();
                    if (s.isEmpty()) continue;
                    ids.add(Long.parseLong(s));
                }
            }
            return ids;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** 解析逗号分隔 ID 字符串并去重。 */
    private List<Long> parseIdCsv(String text) {
        if (!StringUtils.hasText(text)) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        String[] parts = text.split(",");
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            String raw = part.trim();
            if (raw.isEmpty()) {
                continue;
            }
            try {
                Long value = Long.parseLong(raw);
                if (value != null && value > 0L) {
                    ids.add(value);
                }
            } catch (Exception ignored) {
            }
        }
        return new ArrayList<>(ids);
    }

    /** 从计划 cropsJson 提取作物名文本，作为展示标题。 */
    private String resolveCropTypeLabel(String fallbackCropType, FieldCropCycle cycle) {
        if (cycle == null || !StringUtils.hasText(cycle.getCropsJson())) {
            return fallbackCropType;
        }
        try {
            List<Map<String, Object>> rows = objectMapper.readValue(cycle.getCropsJson(), new TypeReference<List<Map<String, Object>>>() {});
            List<String> names = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                if (row == null) continue;
                Object nameObj = row.get("name");
                if (nameObj == null) continue;
                String n = String.valueOf(nameObj).trim();
                if (!n.isEmpty()) {
                    names.add(n);
                }
            }
            if (!names.isEmpty()) {
                return String.join("/", names);
            }
        } catch (Exception ignored) {
        }
        return fallbackCropType;
    }

    /** 构建分段流程：将计划作物配置映射到对应模板步骤集合。 */
    private List<PlanSegmentItem> buildPlanSegments(
            Field field,
            FieldCropCycle cycle,
            List<Long> templateIds,
            List<ProcessStepItem> stepItems
    ) {
        if (stepItems == null || stepItems.isEmpty()) {
            return Collections.emptyList();
        }
        List<CropSegmentConfig> configs = parseCropSegments(cycle, templateIds);
        if (configs.isEmpty()) {
            PlanSegmentItem fallback = new PlanSegmentItem();
            fallback.setSegmentKey("segment-1");
            fallback.setSegmentName(resolveFallbackSegmentName(field, cycle, 1));
            fallback.setTemplateIds(templateIds);
            fallback.setTemplateNames(resolveSegmentTemplateNames(stepItems));
            fallback.setSteps(stepItems.stream().map(ProcessStepItem::copyOf).collect(Collectors.toList()));
            fallback.setDoneStepCount((int) stepItems.stream().filter(x -> x.getDoneCount() != null && x.getDoneCount() > 0).count());
            return Collections.singletonList(fallback);
        }

        List<PlanSegmentItem> out = new ArrayList<>();
        for (int i = 0; i < configs.size(); i++) {
            CropSegmentConfig cfg = configs.get(i);
            List<Long> segmentTemplateIds = cfg.getTemplateIds().isEmpty() ? templateIds : cfg.getTemplateIds();
            List<ProcessStepItem> segmentSteps = stepItems.stream()
                    .filter(step -> segmentTemplateIds.contains(step.getTemplateId()))
                    .map(ProcessStepItem::copyOf)
                    .collect(Collectors.toList());
            if (segmentSteps.isEmpty() && i == 0) {
                segmentSteps = stepItems.stream().map(ProcessStepItem::copyOf).collect(Collectors.toList());
            }
            if (segmentSteps.isEmpty()) {
                continue;
            }

            PlanSegmentItem segment = new PlanSegmentItem();
            int segmentNo = i + 1;
            segment.setSegmentKey("segment-" + segmentNo);
            segment.setCropName(cfg.getName());
            segment.setCropVariety(cfg.getVariety());
            segment.setSegmentName(resolveSegmentName(cfg, field, segmentNo));
            segment.setTemplateIds(segmentTemplateIds);
            segment.setTemplateNames(resolveSegmentTemplateNames(segmentSteps));
            segment.setSteps(segmentSteps);
            segment.setDoneStepCount((int) segmentSteps.stream().filter(x -> x.getDoneCount() != null && x.getDoneCount() > 0).count());
            out.add(segment);
        }
        if (!out.isEmpty()) {
            return out;
        }
        return Collections.emptyList();
    }

    /** 解析计划中的作物分段配置，并尽可能补齐模板绑定。 */
    private List<CropSegmentConfig> parseCropSegments(FieldCropCycle cycle, List<Long> cycleTemplateIds) {
        if (cycle == null || !StringUtils.hasText(cycle.getCropsJson())) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> rows = objectMapper.readValue(cycle.getCropsJson(), new TypeReference<List<Map<String, Object>>>() {});
            List<CropSegmentConfig> out = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                Map<String, Object> row = rows.get(i);
                if (row == null) {
                    continue;
                }
                CropSegmentConfig cfg = new CropSegmentConfig();
                cfg.setName(readText(row.get("name")));
                cfg.setVariety(readText(row.get("variety")));
                List<Long> mappedTemplateIds = parseLongList(row.get("templateIds"));
                if (mappedTemplateIds.isEmpty() && row.get("templateId") != null) {
                    mappedTemplateIds = parseLongList(Collections.singletonList(row.get("templateId")));
                }
                if (mappedTemplateIds.isEmpty() && cycleTemplateIds != null && i < cycleTemplateIds.size()) {
                    mappedTemplateIds = Collections.singletonList(cycleTemplateIds.get(i));
                }
                if (mappedTemplateIds.isEmpty() && cycleTemplateIds != null && cycleTemplateIds.size() == 1) {
                    mappedTemplateIds = Collections.singletonList(cycleTemplateIds.get(0));
                }
                cfg.setTemplateIds(mappedTemplateIds);
                if (StringUtils.hasText(cfg.getName()) || StringUtils.hasText(cfg.getVariety()) || !mappedTemplateIds.isEmpty()) {
                    out.add(cfg);
                }
            }
            return out;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    /** 生成分段兜底名称。 */
    private String resolveFallbackSegmentName(Field field, FieldCropCycle cycle, int segmentNo) {
        if (field != null && StringUtils.hasText(field.getCropType())) {
            return field.getCropType() + "分段" + segmentNo;
        }
        if (cycle != null && StringUtils.hasText(cycle.getCycleName())) {
            return cycle.getCycleName() + "分段" + segmentNo;
        }
        return "分段" + segmentNo;
    }

    /** 生成分段展示名称（作物名+品种优先）。 */
    private String resolveSegmentName(CropSegmentConfig cfg, Field field, int segmentNo) {
        if (cfg == null) {
            return resolveFallbackSegmentName(field, null, segmentNo);
        }
        String name = cfg.getName() == null ? "" : cfg.getName().trim();
        String variety = cfg.getVariety() == null ? "" : cfg.getVariety().trim();
        if (StringUtils.hasText(name) && StringUtils.hasText(variety)) {
            return name + "·" + variety;
        }
        if (StringUtils.hasText(name)) {
            return name;
        }
        if (StringUtils.hasText(variety)) {
            return variety;
        }
        return resolveFallbackSegmentName(field, null, segmentNo);
    }

    /** 收集分段内涉及的模板名称（去重）。 */
    private List<String> resolveSegmentTemplateNames(List<ProcessStepItem> steps) {
        if (steps == null || steps.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        for (ProcessStepItem step : steps) {
            if (step == null || !StringUtils.hasText(step.getTemplateName())) {
                continue;
            }
            String templateName = step.getTemplateName().trim();
            if (templateName.isEmpty() || names.contains(templateName)) {
                continue;
            }
            names.add(templateName);
        }
        return names;
    }

    /** 宽松解析 Long 列表（支持字符串、数组、数字对象）。 */
    private List<Long> parseLongList(Object raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        if (raw instanceof String) {
            return parseLongList((String) raw);
        }
        if (raw instanceof Collection) {
            List<Long> out = new ArrayList<>();
            for (Object item : (Collection<?>) raw) {
                if (item == null) {
                    continue;
                }
                if (item instanceof Number) {
                    out.add(((Number) item).longValue());
                    continue;
                }
                String text = String.valueOf(item).trim();
                if (text.isEmpty()) {
                    continue;
                }
                try {
                    out.add(Long.parseLong(text));
                } catch (Exception ignored) {
                }
            }
            return out;
        }
        if (raw instanceof Number) {
            return Collections.singletonList(((Number) raw).longValue());
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return Collections.singletonList(Long.parseLong(text));
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    /** 对任意对象做文本归一化。 */
    private String readText(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        return text.isEmpty() ? null : text;
    }

    /** 将计划实体转换为前端列表项。 */
    private CycleItem toCycleItem(FieldCropCycle cycle) {
        CycleItem item = new CycleItem();
        item.setId(cycle.getId());
        item.setCycleName(cycle.getCycleName());
        item.setPlanId(cycle.getId());
        item.setPlanName(cycle.getCycleName());
        String planMode = normalizePlanMode(cycle.getPlanMode());
        item.setPlanMode(planMode);
        item.setPlanModeText(resolvePlanModeText(planMode));
        item.setPlanYear(resolvePlanYear(cycle));
        item.setStatus(normalizeCycleStatus(cycle.getStatus(), cycle.getEndDate()));
        item.setIsCurrent(cycle.getIsCurrent());
        item.setStartDate(cycle.getStartDate());
        item.setEndDate(cycle.getEndDate());
        item.setCropsJson(cycle.getCropsJson());
        item.setTemplateIdsJson(cycle.getTemplateIdsJson());
        item.setCropsText(resolveCropTypeLabel(null, cycle));
        return item;
    }

    /** 将计划+田块组合转换为后台分页项。 */
    private CycleAdminItem toCycleAdminItem(FieldCropCycle cycle, Field field) {
        CycleAdminItem item = new CycleAdminItem();
        item.setId(cycle.getId());
        item.setFieldId(cycle.getFieldId());
        item.setFieldName(field == null ? null : field.getName());
        item.setTownship(field == null ? null : field.getTownship());
        List<FieldCropVarietyGroup> groups = field == null
                ? Collections.emptyList()
                : resolveRequestCropGroups(
                FieldCropVarietyGroupCodec.fromFieldJson(objectMapper, field.getCropVarietyGroupsJson()),
                field.getCropType(),
                field.getCropVariety()
        );
        FieldCropVarietyGroupCodec.CropSummary summary = FieldCropVarietyGroupCodec.toLegacySummary(groups);
        item.setCropType(summary.getCropType());
        item.setCropVariety(summary.getCropVariety());
        item.setCropVarietyGroups(groups);
        item.setCycleName(cycle.getCycleName());
        String mode = normalizePlanMode(cycle.getPlanMode());
        item.setPlanMode(mode);
        item.setPlanModeText(resolvePlanModeText(mode));
        item.setPlanYear(resolvePlanYear(cycle));
        item.setStatus(normalizeCycleStatus(cycle.getStatus(), cycle.getEndDate()));
        item.setIsCurrent(cycle.getIsCurrent());
        item.setStartDate(cycle.getStartDate());
        item.setEndDate(cycle.getEndDate());
        item.setCropsText(resolveCropTypeLabel(null, cycle));
        item.setCropsJson(cycle.getCropsJson());
        item.setTemplateIdsJson(cycle.getTemplateIdsJson());
        item.setUpdatedAt(cycle.getUpdatedAt());
        return item;
    }

    /** 计划状态归一化：结束日期已过或状态为 completed 时统一 completed。 */
    private String normalizeCycleStatus(String status, LocalDate endDate) {
        if (endDate != null && endDate.isBefore(LocalDate.now())) {
            return "completed";
        }
        String s = StringUtils.hasText(status) ? status.trim().toLowerCase(Locale.ROOT) : "";
        if ("completed".equals(s)) {
            return "completed";
        }
        return "active";
    }

    /** 计划模式归一化，兼容中英文输入。 */
    private String normalizePlanMode(String planMode) {
        String raw = StringUtils.hasText(planMode) ? planMode.trim().toLowerCase(Locale.ROOT) : "";
        if ("rotation".equals(raw) || "轮作".equals(raw)) {
            return "rotation";
        }
        if ("intercropping".equals(raw) || "间作".equals(raw)) {
            return "intercropping";
        }
        if ("relay".equals(raw) || "套作".equals(raw)) {
            return "relay";
        }
        if ("mixed".equals(raw) || "混作".equals(raw)) {
            return "mixed";
        }
        if ("fallow".equals(raw) || "休耕".equals(raw)) {
            return "fallow";
        }
        if ("custom".equals(raw) || "自定义".equals(raw)) {
            return "custom";
        }
        return "single";
    }

    /** 计划模式中文文案。 */
    private String resolvePlanModeText(String planMode) {
        String mode = normalizePlanMode(planMode);
        if ("rotation".equals(mode)) {
            return "轮作";
        }
        if ("intercropping".equals(mode)) {
            return "间作";
        }
        if ("relay".equals(mode)) {
            return "套作";
        }
        if ("mixed".equals(mode)) {
            return "混作";
        }
        if ("fallow".equals(mode)) {
            return "休耕";
        }
        if ("custom".equals(mode)) {
            return "自定义";
        }
        return "单作";
    }

    /** 计划年份推导：优先 startDate，其次 endDate。 */
    private Integer resolvePlanYear(FieldCropCycle cycle) {
        if (cycle == null) {
            return null;
        }
        if (cycle.getStartDate() != null) {
            return cycle.getStartDate().getYear();
        }
        if (cycle.getEndDate() != null) {
            return cycle.getEndDate().getYear();
        }
        return null;
    }

    /** 查询参数文本归一化（空串/undefined/null 视作 null）。 */
    private String normalizeQueryText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        if (text.isEmpty()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if ("undefined".equals(lower) || "null".equals(lower)) {
            return null;
        }
        return text;
    }

    /** 归一化启用状态输入，仅保留 0/1。 */
    private Integer normalizeEnabled(Integer enabled) {
        if (enabled == null) {
            return null;
        }
        return enabled == 0 ? 0 : 1;
    }

    /** 根据布尔开关与默认值得到 0/1 启用标识。 */
    private Integer resolveEnabled(Boolean enabled, boolean fallback) {
        if (enabled == null) {
            return fallback ? 1 : 0;
        }
        return enabled ? 1 : 0;
    }

    /** 地址文本归一化。 */
    private String normalizeAddressText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    /** 归一化田块地址与阶段字段，补齐展示地址。 */
    private void normalizeAddressFields(Field field) {
        if (field == null) {
            return;
        }
        String normalizedStage = normalizeFieldStage(field.getStage(), field.getStatus(), "idle");
        field.setStatus(normalizedStage);
        field.setStage(normalizedStage);
        field.setProvince(normalizeAddressText(field.getProvince()));
        field.setCity(normalizeAddressText(field.getCity()));
        field.setDistrict(normalizeAddressText(field.getDistrict()));
        field.setTownship(normalizeAddressText(field.getTownship()));
        field.setFormattedAddress(normalizeAddressText(field.getFormattedAddress()));
        field.setLocationDesc(normalizeAddressText(field.getLocationDesc()));
        String addressPath = joinAddress(field.getProvince(), field.getCity(), field.getDistrict(), field.getTownship());
        if (!StringUtils.hasText(field.getFormattedAddress()) && StringUtils.hasText(field.getLocationDesc())) {
            field.setFormattedAddress(field.getLocationDesc());
        }
        if (!StringUtils.hasText(field.getFormattedAddress()) && StringUtils.hasText(addressPath)) {
            field.setFormattedAddress(addressPath);
        }
    }

    /** 拼接地址路径（省-市-区-乡镇）。 */
    private String joinAddress(String... parts) {
        if (parts == null || parts.length == 0) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (String part : parts) {
            String text = normalizeAddressText(part);
            if (StringUtils.hasText(text)) {
                values.add(text);
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        return String.join("-", values);
    }

    /** 常用田块关键词匹配，和小程序搜索提示保持同一语义。 */
    private boolean matchesFieldKeyword(Field field, String keyword) {
        if (field == null || !StringUtils.hasText(keyword)) {
            return true;
        }
        String safeKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        List<String> labels = resolveRequestCropGroups(
                FieldCropVarietyGroupCodec.fromFieldJson(objectMapper, field.getCropVarietyGroupsJson()),
                field.getCropType(),
                field.getCropVariety()
        ).stream().map(group -> {
            String cropType = normalizeQueryText(group == null ? null : group.getCropType());
            String cropVariety = normalizeQueryText(group == null ? null : group.getCropVariety());
            if (StringUtils.hasText(cropType) && StringUtils.hasText(cropVariety)) {
                return cropType + " " + cropVariety;
            }
            return StringUtils.hasText(cropType) ? cropType : cropVariety;
        }).filter(StringUtils::hasText).collect(Collectors.toList());
        List<String> tokens = new ArrayList<>(Arrays.asList(
                normalizeQueryText(field.getName()),
                normalizeQueryText(field.getFormattedAddress()),
                normalizeQueryText(field.getLocationDesc()),
                normalizeQueryText(field.getProvince()),
                normalizeQueryText(field.getCity()),
                normalizeQueryText(field.getDistrict()),
                normalizeQueryText(field.getTownship()),
                normalizeQueryText(field.getCropType()),
                normalizeQueryText(field.getCropVariety()),
                String.join(" ", labels)
        ));
        tokens = tokens.stream().filter(StringUtils::hasText).collect(Collectors.toList());
        String haystack = String.join(" ", tokens).toLowerCase(Locale.ROOT);
        return haystack.contains(safeKeyword);
    }

    /** 将内存列表切片成 Page，供“常用田块”这类排序结果直接返回。 */
    private Page<Field> sliceFieldPage(List<Field> source, long page, long pageSize) {
        List<Field> safeSource = source == null ? Collections.emptyList() : source;
        Page<Field> out = new Page<>(page, pageSize, safeSource.size());
        if (safeSource.isEmpty()) {
            out.setRecords(Collections.emptyList());
            return out;
        }
        long safeCurrent = Math.max(1L, page);
        long safeSize = Math.max(1L, pageSize);
        int fromIndex = (int) Math.min((safeCurrent - 1L) * safeSize, safeSource.size());
        int toIndex = (int) Math.min(safeSource.size(), fromIndex + safeSize);
        out.setRecords(new ArrayList<>(safeSource.subList(fromIndex, toIndex)));
        return out;
    }

    /** 当前小程序用户展示名，用于和农事记录执行人做同口径匹配。 */
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

    /** 校验经纬度是否有效。 */
    private boolean isValidCoordinate(Double latitude, Double longitude) {
        return latitude != null
                && longitude != null
                && !Double.isNaN(latitude)
                && !Double.isNaN(longitude)
                && Math.abs(latitude) <= 90D
                && Math.abs(longitude) <= 180D
                && !(latitude == 0D && longitude == 0D);
    }

    /** 统一附近田块半径，当前业务固定为 20km，保留入参仅为了接口可读性。 */
    private double normalizeNearbyRadiusKm(Double radiusKm) {
        return 20D;
    }

    private double normalizeCurrentMatchRadiusKm(Double radiusKm) {
        if (radiusKm == null || Double.isNaN(radiusKm) || radiusKm <= 0D) {
            return CURRENT_FIELD_MATCH_RADIUS_KM;
        }
        return Math.min(radiusKm, 5D);
    }

    private FieldPageQuery buildFieldPageQuery(
            String keyword,
            String stage,
            String status,
            String cropType,
            String province,
            String city,
            String district,
            String township,
            String cropVariety,
            Integer enabled,
            boolean includeDisabled,
            String sortBy,
            String sortDirection,
            Double latitude,
            Double longitude,
            long page,
            long pageSize
    ) {
        FieldPageQuery query = new FieldPageQuery();
        query.setKeyword(normalizeQueryText(keyword));
        query.setStatus(normalizeFieldStage(stage, status));
        query.setCropType(normalizeQueryText(cropType));
        query.setProvince(normalizeQueryText(province));
        query.setCity(normalizeQueryText(city));
        query.setDistrict(normalizeQueryText(district));
        query.setTownship(normalizeQueryText(township));
        query.setCropVariety(normalizeQueryText(cropVariety));
        query.setEnabled(normalizeEnabled(enabled));
        query.setIncludeDisabled(includeDisabled);
        query.setSortBy(normalizeFieldSortBy(sortBy));
        query.setSortDirection(normalizeFieldSortDirection(sortDirection));
        query.setLatitude(isValidCoordinate(latitude, longitude) ? latitude : null);
        query.setLongitude(isValidCoordinate(latitude, longitude) ? longitude : null);
        query.setPage(page);
        query.setPageSize(pageSize);
        return query;
    }

    private void applyNearbyPresentationFields(Field field) {
        applyCurrentPlanCropSummary(field);
        normalizeAddressFields(field);
    }

    private void applyLocationPresentation(Field field, Double latitude, Double longitude, boolean allowCurrentMatchLabel) {
        if (field == null || !isValidCoordinate(latitude, longitude)) {
            clearLocationPresentation(field);
            return;
        }
        Double fieldLatitude = field.getLocationLat();
        Double fieldLongitude = field.getLocationLng();
        if (hasMissingCoordinate(fieldLatitude, fieldLongitude)) {
            markLocationUnknown(field);
            return;
        }
        if (hasCoordinateAnomaly(fieldLatitude, fieldLongitude)) {
            markLocationAbnormal(field);
            return;
        }
        double distanceMeters = calculateDistanceMeters(latitude, longitude, fieldLatitude, fieldLongitude);
        if (!Double.isFinite(distanceMeters) || distanceMeters < 0D) {
            clearLocationPresentation(field);
            return;
        }
        field.setDistanceMeters(distanceMeters);
        field.setDistanceText(formatDistanceText(distanceMeters));
        if (allowCurrentMatchLabel && distanceMeters <= resolveCurrentFieldMatchRadiusMeters(field)) {
            field.setCurrentMatched(Boolean.TRUE);
            field.setRelationType("inside");
            field.setRelationText(CURRENT_FIELD_RELATION_TEXT);
            return;
        }
        field.setCurrentMatched(Boolean.FALSE);
        field.setRelationType(null);
        field.setRelationText(null);
    }

    private void markLocationUnknown(Field field) {
        if (field == null) {
            return;
        }
        field.setDistanceMeters(null);
        field.setDistanceText(LOCATION_UNKNOWN_TEXT);
        field.setCurrentMatched(Boolean.FALSE);
        field.setRelationType(null);
        field.setRelationText(null);
    }

    private void markLocationAbnormal(Field field) {
        if (field == null) {
            return;
        }
        field.setDistanceMeters(null);
        field.setDistanceText(LOCATION_ABNORMAL_TEXT);
        field.setCurrentMatched(Boolean.FALSE);
        field.setRelationType(null);
        field.setRelationText(null);
    }

    private void clearLocationPresentation(Field field) {
        if (field == null) {
            return;
        }
        field.setDistanceMeters(null);
        field.setDistanceText(null);
        field.setCurrentMatched(Boolean.FALSE);
        field.setRelationType(null);
        field.setRelationText(null);
    }

    private String formatDistanceText(double distanceMeters) {
        if (!Double.isFinite(distanceMeters) || distanceMeters < 0D) {
            return null;
        }
        if (distanceMeters < 1000D) {
            return Math.round(distanceMeters) + "m";
        }
        return String.format(Locale.ROOT, "%.1fkm", distanceMeters / 1000D);
    }

    private double calculateDistanceMeters(double latitude1, double longitude1, double latitude2, double longitude2) {
        double earthRadius = 6371000D;
        double latitudeDelta = Math.toRadians(latitude2 - latitude1);
        double longitudeDelta = Math.toRadians(longitude2 - longitude1);
        double a = Math.sin(latitudeDelta / 2D) * Math.sin(latitudeDelta / 2D)
                + Math.cos(Math.toRadians(latitude1))
                * Math.cos(Math.toRadians(latitude2))
                * Math.sin(longitudeDelta / 2D)
                * Math.sin(longitudeDelta / 2D);
        double c = 2D * Math.atan2(Math.sqrt(a), Math.sqrt(1D - a));
        return earthRadius * c;
    }

    private boolean hasCoordinateAnomaly(Double latitude, Double longitude) {
        boolean latitudePresent = latitude != null;
        boolean longitudePresent = longitude != null;
        if (latitudePresent != longitudePresent) {
            return true;
        }
        if (!latitudePresent) {
            return false;
        }
        return !isValidCoordinate(latitude, longitude);
    }

    private boolean hasMissingCoordinate(Double latitude, Double longitude) {
        return latitude == null && longitude == null;
    }
    private Field pickCurrentMatchedField(List<Field> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        Field nearest = candidates.get(0);
        double nearestMeters = nearest == null || nearest.getDistanceMeters() == null
                ? Double.MAX_VALUE
                : nearest.getDistanceMeters();
        if (!Double.isFinite(nearestMeters)) {
            return null;
        }
        if (nearestMeters <= resolveCurrentFieldMatchRadiusMeters(nearest)) {
            return nearest;
        }
        return null;
    }

    private String normalizeFieldSortBy(String sortBy) {
        String value = normalizeQueryText(sortBy);
        if (!StringUtils.hasText(value)) {
            return "default";
        }
        String lowered = value.toLowerCase(Locale.ROOT);
        if ("distance".equals(lowered) || "area".equals(lowered)) {
            return lowered;
        }
        return "default";
    }

    private String normalizeFieldSortDirection(String sortDirection) {
        String value = normalizeQueryText(sortDirection);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String lowered = value.toLowerCase(Locale.ROOT);
        if ("desc".equals(lowered)) {
            return "desc";
        }
        return "asc";
    }

    private double resolveCurrentFieldMatchRadiusMeters(Field field) {
        if (field == null || field.getAreaMu() == null || !Double.isFinite(field.getAreaMu()) || field.getAreaMu() <= 0D) {
            return CURRENT_FIELD_MIN_RADIUS_METERS;
        }
        double areaSquareMeters = field.getAreaMu() * 666.6667D;
        double radiusMeters = Math.sqrt(areaSquareMeters / Math.PI) * CURRENT_FIELD_RADIUS_RELAX_FACTOR;
        if (!Double.isFinite(radiusMeters) || radiusMeters <= 0D) {
            return CURRENT_FIELD_MIN_RADIUS_METERS;
        }
        return Math.min(Math.max(radiusMeters, CURRENT_FIELD_MIN_RADIUS_METERS), CURRENT_FIELD_MAX_RADIUS_METERS);
    }

    private void validateFieldCoordinates(Double latitude, Double longitude) {
        boolean latitudePresent = latitude != null;
        boolean longitudePresent = longitude != null;
        if (!latitudePresent && !longitudePresent) {
            return;
        }
        if (latitudePresent != longitudePresent || !isValidCoordinate(latitude, longitude)) {
            throw new IllegalArgumentException("田块经纬度格式不正确，请检查后重试");
        }
    }

    private Long resolveCurrentUserId(HttpServletRequest request) {
        AppUser currentUser = request == null ? null : AuthContext.getCurrentUser(request);
        return currentUser == null ? null : currentUser.getId();
    }

    private void syncFieldCoverAssetBinding(Long fieldId, String coverImageUrl, Long coverImageAssetId, Long currentUserId) {
        if (fieldId == null || fieldId <= 0) {
            return;
        }
        List<Long> assetIds = resolveFieldCoverAssetIds(fieldId, coverImageUrl, coverImageAssetId);
        mediaAssetService.bindAssetsToBiz("field", fieldId, assetIds, currentUserId, true, true, false);
    }

    private List<Long> resolveFieldCoverAssetIds(Long fieldId, String coverImageUrl, Long coverImageAssetId) {
        Set<Long> out = new LinkedHashSet<>();
        if (coverImageAssetId != null && coverImageAssetId > 0) {
            out.add(coverImageAssetId);
        }
        String normalizedUrl = normalizeQueryText(coverImageUrl);
        if (!StringUtils.hasText(normalizedUrl)) {
            return new ArrayList<>(out);
        }
        for (MediaAsset row : mediaAssetService.listBizAssets("field", fieldId, "image")) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (normalizedUrl.equals(normalizeQueryText(row.getFileUrl()))) {
                out.add(row.getId());
                return new ArrayList<>(out);
            }
        }
        MediaAsset matched = mediaAssetService.lambdaQuery()
                .eq(MediaAsset::getFileUrl, normalizedUrl)
                .eq(MediaAsset::getRecycleFlag, 0)
                .eq(MediaAsset::getDeleted, 0)
                .eq(MediaAsset::getFileType, "image")
                .orderByDesc(MediaAsset::getUpdatedAt)
                .orderByDesc(MediaAsset::getId)
                .last("LIMIT 1")
                .one();
        if (matched != null && matched.getId() != null) {
            out.add(matched.getId());
        }
        return new ArrayList<>(out);
    }

    /** 田块阶段归一化入口。 */
    private String normalizeFieldStage(String stage, String status) {
        return normalizeFieldStage(stage, status, null);
    }

    /** 田块阶段归一化（兼容中英文别名）。 */
    private String normalizeFieldStage(String stage, String status, String fallback) {
        List<String> candidates = Arrays.asList(stage, status);
        for (String candidate : candidates) {
            String text = normalizeQueryText(candidate);
            if (!StringUtils.hasText(text)) {
                continue;
            }
            String raw = text.trim().toLowerCase(Locale.ROOT);
            if ("sowing".equals(raw) || "播种".equals(raw) || "播种阶段".equals(raw)) {
                return "sowing";
            }
            if ("growing".equals(raw) || "生长".equals(raw) || "生长阶段".equals(raw)) {
                return "growing";
            }
            if ("harvesting".equals(raw) || "收获".equals(raw) || "收获阶段".equals(raw)) {
                return "harvesting";
            }
            if ("fallow".equals(raw) || "休耕".equals(raw) || "休耕阶段".equals(raw)) {
                return "fallow";
            }
            if ("idle".equals(raw) || "空闲".equals(raw) || "空闲阶段".equals(raw)) {
                return "idle";
            }
        }
        String safeFallback = normalizeQueryText(fallback);
        if (StringUtils.hasText(safeFallback)) {
            return normalizeFieldStage(safeFallback, null, null);
        }
        return null;
    }

    @Data
    /** 地图点位简化视图。 */
    public static class FieldMapPoint {
        private Long id;
        private String name;
        private Double locationLat;
        private Double locationLng;
        private String status;
        private String stage;
        private String cropType;
        private String coverImageUrl;

        public static FieldMapPoint from(Field f) {
            FieldMapPoint p = new FieldMapPoint();
            p.setId(f.getId());
            p.setName(f.getName());
            p.setLocationLat(f.getLocationLat());
            p.setLocationLng(f.getLocationLng());
            p.setStatus(f.getStatus());
            p.setStage(f.getStage());
            p.setCropType(f.getCropType());
            p.setCoverImageUrl(f.getCoverImageUrl());
            return p;
        }
    }

    @Data
    /** 田块流程聚合响应。 */
    public static class FieldProcessResp {
        private Long fieldId;
        private String fieldName;
        private Long cropId;
        private String cropType;
        private Long templateId;
        private String templateName;
        private Long selectedCycleId;
        private List<CycleItem> cycles;
        private Long currentStepId;
        private Integer currentStepIndex;
        private List<ProcessStepItem> steps;
        private List<PlanSegmentItem> segments;
        private String selectedSegmentKey;
    }

    @Data
    /** 流程步骤展示项（含完成度与是否当前步骤）。 */
    public static class ProcessStepItem {
        private Long id;
        private Long templateId;
        private String templateName;
        private String stepName;
        private Integer sortOrder;
        private String growthStage;
        private String requirementDesc;
        private Long formConfigId;
        private String formSchema;
        private Long doneCount;
        private LocalDateTime lastWorkDate;
        private Boolean current;

        public static ProcessStepItem from(FarmProcessStep step, FarmProcessTemplate template) {
            ProcessStepItem item = new ProcessStepItem();
            item.setId(step.getId());
            item.setTemplateId(step.getTemplateId());
            item.setTemplateName(template == null ? null : template.getTemplateName());
            item.setStepName(step.getStepName());
            item.setSortOrder(step.getSortOrder());
            item.setGrowthStage(step.getGrowthStage());
            item.setRequirementDesc(step.getRequirementDesc());
            item.setFormConfigId(step.getFormConfigId());
            item.setFormSchema(step.getFormSchema());
            return item;
        }

        public static ProcessStepItem copyOf(ProcessStepItem source) {
            ProcessStepItem item = new ProcessStepItem();
            item.setId(source.getId());
            item.setTemplateId(source.getTemplateId());
            item.setTemplateName(source.getTemplateName());
            item.setStepName(source.getStepName());
            item.setSortOrder(source.getSortOrder());
            item.setGrowthStage(source.getGrowthStage());
            item.setRequirementDesc(source.getRequirementDesc());
            item.setFormConfigId(source.getFormConfigId());
            item.setFormSchema(source.getFormSchema());
            item.setDoneCount(source.getDoneCount());
            item.setLastWorkDate(source.getLastWorkDate());
            item.setCurrent(source.getCurrent());
            return item;
        }
    }

    @Data
    /** 流程分段展示项。 */
    public static class PlanSegmentItem {
        private String segmentKey;
        private String segmentName;
        private String cropName;
        private String cropVariety;
        private List<Long> templateIds;
        private List<String> templateNames;
        private Integer doneStepCount;
        private List<ProcessStepItem> steps;
    }

    @Data
    /** 计划中的单条作物绑定。 */
    private static class CycleCropBinding {
        private String name;
        private String variety;
        private Long templateId;
    }

    @Data
    /** 作物分段配置（用于流程分段展示）。 */
    private static class CropSegmentConfig {
        private String name;
        private String variety;
        private List<Long> templateIds = Collections.emptyList();
    }

    @Data
    /** 田块计划列表项。 */
    public static class CycleItem {
        private Long id;
        private String cycleName;
        private Long planId;
        private String planName;
        private String planMode;
        private String planModeText;
        private Integer planYear;
        private String status;
        private Integer isCurrent;
        private LocalDate startDate;
        private LocalDate endDate;
        private String cropsText;
        private String cropsJson;
        private String templateIdsJson;
    }

    @Data
    /** 新增计划请求体。 */
    public static class CycleCreateReq {
        @NotBlank(message = "计划名称不能为空")
        private String cycleName;

        private String cropsJson;

        private String templateIdsJson;

        private LocalDate startDate;

        private LocalDate endDate;

        private String status;

        private Boolean isCurrent;

        private String planMode;
    }

    @Data
    /** 更新计划请求体。 */
    public static class CycleUpdateReq {
        @NotBlank(message = "计划名称不能为空")
        private String cycleName;

        private String cropsJson;

        private String templateIdsJson;

        private LocalDate startDate;

        private LocalDate endDate;

        private String status;

        private Boolean isCurrent;

        private String planMode;
    }

    @Data
    /** 后台计划分页项。 */
    public static class CycleAdminItem {
        private Long id;
        private Long fieldId;
        private String fieldName;
        private String township;
        private String cropType;
        private String cropVariety;
        private List<FieldCropVarietyGroup> cropVarietyGroups;
        private String cycleName;
        private String planMode;
        private String planModeText;
        private Integer planYear;
        private String status;
        private Integer isCurrent;
        private LocalDate startDate;
        private LocalDate endDate;
        private String cropsText;
        private String cropsJson;
        private String templateIdsJson;
        private LocalDateTime updatedAt;
    }

    @Data
    /** 新增田块请求体。 */
    public static class FieldCreateReq {
        @NotBlank(message = "田块名称不能为空")
        private String name;

        @NotNull(message = "面积(亩)不能为空")
        private Double areaMu;

        private String cropType;

        private String cropVariety;

        private List<FieldCropVarietyGroup> cropVarietyGroups;

        private String province;

        private String city;

        private String district;

        private String township;

        private String formattedAddress;

        private String status;
        private String stage;
        private Boolean enabled;
        private Double locationLat;
        private Double locationLng;
        private String locationDesc;
        private String coverImageUrl;
        private Long coverImageAssetId;
        private String remark;
    }

    @Data
    /** 更新田块请求体。 */
    public static class FieldUpdateReq {
        @NotBlank(message = "田块名称不能为空")
        private String name;

        @NotNull(message = "面积(亩)不能为空")
        private Double areaMu;

        private String cropType;

        private String cropVariety;

        private List<FieldCropVarietyGroup> cropVarietyGroups;

        private String province;

        private String city;

        private String district;

        private String township;

        private String formattedAddress;

        private String status;
        private String stage;
        private Boolean enabled;
        private Double locationLat;
        private Double locationLng;
        private String locationDesc;
        private String coverImageUrl;
        private Long coverImageAssetId;
        private String remark;
    }

    @Data
    /** 田块启停请求体。 */
    public static class FieldEnabledReq {
        @NotNull(message = "启用状态不能为空")
        private Boolean enabled;
    }

    @Data
    /** 田块重排请求体。 */
    public static class FieldReorderReq {
        @NotEmpty(message = "编号列表不能为空")
        private List<Long> ids;
    }
}

