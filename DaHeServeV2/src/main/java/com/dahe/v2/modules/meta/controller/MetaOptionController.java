package com.dahe.v2.modules.meta.controller;

import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.auth.support.AuthContext;
import com.dahe.v2.modules.meta.service.MetaOptionQueryService;
import com.dahe.v2.modules.user.model.AppUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/v2/meta/options")
public class MetaOptionController {

    private final MetaOptionQueryService metaOptionQueryService;

    public MetaOptionController(MetaOptionQueryService metaOptionQueryService) {
        this.metaOptionQueryService = metaOptionQueryService;
    }

    @GetMapping("/townships")
    public Result<List<String>> townships(
            HttpServletRequest request,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String district
    ) {
        if (!requireLogin(request)) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
        }
        return Result.success(metaOptionQueryService.listTownships(keyword, province, city, district));
    }

    @GetMapping("/provinces")
    public Result<List<String>> provinces(
            HttpServletRequest request,
            @RequestParam(required = false) String keyword
    ) {
        if (!requireLogin(request)) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
        }
        return Result.success(metaOptionQueryService.listProvinces(keyword));
    }

    @GetMapping("/cities")
    public Result<List<String>> cities(
            HttpServletRequest request,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String province
    ) {
        if (!requireLogin(request)) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
        }
        return Result.success(metaOptionQueryService.listCities(keyword, province));
    }

    @GetMapping("/districts")
    public Result<List<String>> districts(
            HttpServletRequest request,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String city
    ) {
        if (!requireLogin(request)) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
        }
        return Result.success(metaOptionQueryService.listDistricts(keyword, province, city));
    }

    @GetMapping("/crops")
    public Result<List<String>> crops(
            HttpServletRequest request,
            @RequestParam(required = false) String keyword
    ) {
        if (!requireLogin(request)) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
        }
        return Result.success(metaOptionQueryService.listCrops(keyword));
    }

    @GetMapping("/varieties")
    public Result<List<String>> varieties(
            HttpServletRequest request,
            @RequestParam(required = false) String cropName,
            @RequestParam(required = false) String keyword
    ) {
        if (!requireLogin(request)) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
        }
        return Result.success(metaOptionQueryService.listVarieties(cropName, keyword));
    }

    @GetMapping("/crop-tree")
    public Result<List<MetaOptionQueryService.CropTreeItem>> cropTree(
            HttpServletRequest request,
            @RequestParam(required = false) String keyword
    ) {
        if (!requireLogin(request)) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
        }
        return Result.success(metaOptionQueryService.listCropTree(keyword));
    }

    @GetMapping("/variety-groups")
    public Result<List<MetaOptionQueryService.VarietyGroupItem>> varietyGroups(
            HttpServletRequest request,
            @RequestParam(required = false) String keyword
    ) {
        if (!requireLogin(request)) {
            return Result.failure(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
        }
        return Result.success(metaOptionQueryService.listVarietyGroups(keyword));
    }

    private boolean requireLogin(HttpServletRequest request) {
        AppUser currentUser = AuthContext.getCurrentUser(request);
        return currentUser != null;
    }
}
