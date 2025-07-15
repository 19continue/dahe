package com.dahe.v2.modules.miniapp.field.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.farm.model.FarmRecord;
import com.dahe.v2.modules.field.controller.FieldController;
import com.dahe.v2.modules.field.cycle.model.FieldCropCycle;
import com.dahe.v2.modules.field.model.Field;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Min;
import java.util.List;
import java.util.Map;

/**
 * 小程序端田块接口入口。
 *
 * <p>仅负责路由分离与协议转发，业务逻辑复用 field 模块现有实现。</p>
 */
@RestController
@RequestMapping("/api/v2/miniapp/fields")
@Validated
public class MiniappFieldController {

    private final FieldController fieldController;

    public MiniappFieldController(FieldController fieldController) {
        this.fieldController = fieldController;
    }

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
        return fieldController.page(
                keyword, stage, status, cropType, province, city, district, township, cropVariety, enabled,
                sortBy, sortDirection, latitude, longitude, includeDisabled, page, pageSize
        );
    }

    @GetMapping("/common")
    public Result<Page<Field>> common(
            HttpServletRequest request,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(defaultValue = "false") boolean includeDisabled,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) long pageSize
    ) {
        return fieldController.commonFields(request, keyword, latitude, longitude, includeDisabled, page, pageSize);
    }

    @GetMapping("/nearby")
    public Result<Page<Field>> nearby(
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(defaultValue = "false") boolean includeDisabled,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) long pageSize
    ) {
        return fieldController.nearbyFields(keyword, latitude, longitude, radiusKm, includeDisabled, page, pageSize);
    }

    @GetMapping("/current-match")
    public Result<Field> currentMatch(
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(defaultValue = "false") boolean includeDisabled
    ) {
        return fieldController.currentMatchedField(latitude, longitude, radiusKm, includeDisabled);
    }

    @GetMapping("/{id}")
    public Result<Field> detail(
            @PathVariable Long id,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude
    ) {
        return fieldController.detail(id, latitude, longitude);
    }

    @PutMapping("/{id}")
    public Result<Field> update(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody @Validated FieldController.FieldUpdateReq req
    ) {
        return fieldController.update(request, id, req);
    }

    @GetMapping("/{id}/cycles")
    public Result<List<FieldController.CycleItem>> cycles(@PathVariable Long id) {
        return fieldController.cycles(id);
    }

    @GetMapping("/cycles/by-fields")
    public Result<Map<Long, List<FieldController.CycleItem>>> cyclesByFields(
            @RequestParam(required = false) String fieldIds
    ) {
        return fieldController.cyclesByFields(fieldIds);
    }

    @PostMapping("/{id}/cycles")
    public Result<FieldCropCycle> createCycle(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody @Validated FieldController.CycleCreateReq req
    ) {
        return fieldController.createCycle(request, id, req);
    }

    @PutMapping("/{id}/cycles/{cycleId}")
    public Result<FieldCropCycle> updateCycle(
            HttpServletRequest request,
            @PathVariable Long id,
            @PathVariable Long cycleId,
            @RequestBody @Validated FieldController.CycleUpdateReq req
    ) {
        return fieldController.updateCycle(request, id, cycleId, req);
    }

    @PutMapping("/{id}/cycles/{cycleId}/current")
    public Result<Void> setCurrentCycle(
            HttpServletRequest request,
            @PathVariable Long id,
            @PathVariable Long cycleId
    ) {
        return fieldController.setCurrentCycle(request, id, cycleId);
    }

    @DeleteMapping("/{id}/cycles/{cycleId}")
    public Result<Void> deleteCycle(
            HttpServletRequest request,
            @PathVariable Long id,
            @PathVariable Long cycleId
    ) {
        return fieldController.deleteCycle(request, id, cycleId);
    }

    @GetMapping("/{id}/process")
    public Result<FieldController.FieldProcessResp> fieldProcess(
            @PathVariable Long id,
            @RequestParam(required = false) Long cycleId
    ) {
        return fieldController.fieldProcess(id, cycleId);
    }

    @GetMapping("/{id}/farm-records/recent")
    public Result<List<FarmRecord>> recentFarmRecords(
            @PathVariable Long id,
            @RequestParam(required = false) Long cycleId,
            @RequestParam(required = false) String stepIds,
            @RequestParam(defaultValue = "8") @Min(1) int limit
    ) {
        return fieldController.recentFarmRecords(id, cycleId, stepIds, limit);
    }
}
