package com.dahe.v2.modules.miniapp.meta.controller;

import com.dahe.v2.common.Result;
import com.dahe.v2.modules.meta.service.MetaOptionQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序端元数据选项接口。
 */
@RestController
@RequestMapping("/api/v2/miniapp/meta/options")
public class MiniappMetaOptionController {

    private final MetaOptionQueryService metaOptionQueryService;

    public MiniappMetaOptionController(MetaOptionQueryService metaOptionQueryService) {
        this.metaOptionQueryService = metaOptionQueryService;
    }

    @GetMapping("/townships")
    public Result<List<String>> townships(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String district
    ) {
        return Result.success(metaOptionQueryService.listTownships(keyword, province, city, district));
    }

    @GetMapping("/provinces")
    public Result<List<String>> provinces(@RequestParam(required = false) String keyword) {
        return Result.success(metaOptionQueryService.listProvinces(keyword));
    }

    @GetMapping("/cities")
    public Result<List<String>> cities(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String province
    ) {
        return Result.success(metaOptionQueryService.listCities(keyword, province));
    }

    @GetMapping("/districts")
    public Result<List<String>> districts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String city
    ) {
        return Result.success(metaOptionQueryService.listDistricts(keyword, province, city));
    }

    @GetMapping("/crops")
    public Result<List<String>> crops(@RequestParam(required = false) String keyword) {
        return Result.success(metaOptionQueryService.listCrops(keyword));
    }

    @GetMapping("/varieties")
    public Result<List<String>> varieties(
            @RequestParam(required = false) String cropName,
            @RequestParam(required = false) String keyword
    ) {
        return Result.success(metaOptionQueryService.listVarieties(cropName, keyword));
    }

    @GetMapping("/crop-tree")
    public Result<List<MetaOptionQueryService.CropTreeItem>> cropTree(
            @RequestParam(required = false) String keyword
    ) {
        return Result.success(metaOptionQueryService.listCropTree(keyword));
    }

    @GetMapping("/variety-groups")
    public Result<List<MetaOptionQueryService.VarietyGroupItem>> varietyGroups(
            @RequestParam(required = false) String keyword
    ) {
        return Result.success(metaOptionQueryService.listVarietyGroups(keyword));
    }
}

