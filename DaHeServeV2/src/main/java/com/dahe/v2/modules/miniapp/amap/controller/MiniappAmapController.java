package com.dahe.v2.modules.miniapp.amap.controller;

import com.dahe.v2.common.Result;
import com.dahe.v2.modules.amap.service.impl.AmapOpenApplicationService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.util.List;

/**
 * 小程序高德接口入口。
 *
 * <p>职责：将小程序端高德请求统一挂载到 `/api/v2/miniapp/amap/**`，避免与后台/共享路由混用。</p>
 */
@RestController
@RequestMapping("/api/v2/miniapp/amap")
@Validated
public class MiniappAmapController {

    private final AmapOpenApplicationService amapOpenService;

    public MiniappAmapController(AmapOpenApplicationService amapOpenService) {
        this.amapOpenService = amapOpenService;
    }

    @PostMapping("/audit")
    public Result<Void> reportAudit(HttpServletRequest request, @RequestBody AmapOpenApplicationService.AuditReq req) {
        return amapOpenService.reportAudit(request, req);
    }

    @GetMapping("/address/tips")
    public Result<List<AmapOpenApplicationService.AddressTipItem>> addressTips(
            HttpServletRequest request,
            @RequestParam String keywords,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Boolean cityLimit,
            @RequestParam(defaultValue = "10") @Min(1) @Max(30) int limit
    ) {
        return amapOpenService.addressTips(request, keywords, city, longitude, latitude, cityLimit, limit);
    }

    @GetMapping("/address/regeo")
    public Result<AmapOpenApplicationService.NormalizedAddress> reverseGeocode(
            HttpServletRequest request,
            @RequestParam Double longitude,
            @RequestParam Double latitude
    ) {
        return amapOpenService.reverseGeocode(request, longitude, latitude);
    }

    @GetMapping("/address/geocode")
    public Result<AmapOpenApplicationService.NormalizedAddress> geocode(
            HttpServletRequest request,
            @RequestParam String address,
            @RequestParam(required = false) String city
    ) {
        return amapOpenService.geocode(request, address, city);
    }

    @GetMapping("/regions/provinces")
    public Result<List<AmapOpenApplicationService.RegionOptionItem>> provinceOptions(
            HttpServletRequest request,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        return amapOpenService.provinceOptions(request, keyword, limit);
    }

    @GetMapping("/regions/cities")
    public Result<List<AmapOpenApplicationService.RegionOptionItem>> cityOptions(
            HttpServletRequest request,
            @RequestParam String province,
            @RequestParam(defaultValue = "80") @Min(1) @Max(200) int limit
    ) {
        return amapOpenService.cityOptions(request, province, limit);
    }

    @GetMapping("/regions/districts")
    public Result<List<AmapOpenApplicationService.RegionOptionItem>> districtOptions(
            HttpServletRequest request,
            @RequestParam String city,
            @RequestParam(defaultValue = "120") @Min(1) @Max(300) int limit
    ) {
        return amapOpenService.districtOptions(request, city, limit);
    }

    @GetMapping("/regions/townships")
    public Result<List<AmapOpenApplicationService.RegionOptionItem>> townshipOptions(
            HttpServletRequest request,
            @RequestParam String district,
            @RequestParam(defaultValue = "200") @Min(1) @Max(500) int limit
    ) {
        return amapOpenService.townshipOptions(request, district, limit);
    }

    @GetMapping("/weather/snapshot")
    public Result<AmapOpenApplicationService.WeatherSnapshotResp> weatherSnapshot(
            HttpServletRequest request,
            @RequestParam Double longitude,
            @RequestParam Double latitude
    ) {
        return amapOpenService.weatherSnapshot(request, longitude, latitude);
    }
}
