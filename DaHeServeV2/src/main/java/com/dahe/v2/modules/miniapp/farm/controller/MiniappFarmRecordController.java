package com.dahe.v2.modules.miniapp.farm.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.farm.controller.FarmRecordController;
import com.dahe.v2.modules.farm.model.FarmRecord;
import com.dahe.v2.modules.farm.model.FarmRecordImageView;
import com.dahe.v2.modules.farm.model.FarmRecordGroupStats;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.time.LocalDateTime;
import java.util.List;

/**
 * 小程序端农事记录接口入口。
 */
@RestController
@RequestMapping("/api/v2/miniapp/farm-records")
@Validated
public class MiniappFarmRecordController {

    private final FarmRecordController farmRecordController;

    public MiniappFarmRecordController(FarmRecordController farmRecordController) {
        this.farmRecordController = farmRecordController;
    }

    @GetMapping
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
        return farmRecordController.page(request, fieldId, cycleId, mineOnly, township, startDate, endDate, page, pageSize);
    }

    @GetMapping("/grouped")
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
        return farmRecordController.grouped(request, fieldId, cycleId, mineOnly, fieldIdList, township, startDate, endDate, maxGroups);
    }

    @GetMapping("/{id}")
    public Result<FarmRecord> detail(@PathVariable Long id, HttpServletRequest request) {
        return farmRecordController.detail(id, request);
    }

    @GetMapping("/{id}/images")
    public Result<List<FarmRecordImageView>> images(@PathVariable Long id, HttpServletRequest request) {
        return farmRecordController.miniappImages(id, request);
    }

    @PostMapping
    public Result<FarmRecord> create(
            @RequestBody @Validated FarmRecordController.FarmRecordCreateReq req,
            HttpServletRequest request
    ) {
        return farmRecordController.create(req, request);
    }

    @PutMapping("/{id}")
    public Result<FarmRecord> update(
            @PathVariable Long id,
            @RequestBody @Validated FarmRecordController.FarmRecordUpdateReq req,
            HttpServletRequest request
    ) {
        return farmRecordController.update(id, req, request);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        return farmRecordController.delete(id, request);
    }
}
