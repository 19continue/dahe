package com.dahe.v2.modules.amap.service.impl;

import com.dahe.v2.common.Result;
import com.dahe.v2.modules.amap.model.AmapApiAudit;
import com.dahe.v2.modules.amap.service.AmapApiAuditService;
import com.dahe.v2.modules.amap.service.AmapOpenService;
import com.dahe.v2.modules.amap.service.AmapQuotaConfigService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class AmapOpenApplicationServiceTest {

    @Test
    void provinceOptions_shouldRecordCacheSourceAndNotIncreaseQuotaInAuditLayer() {
        AmapQuotaConfigService quotaService = Mockito.mock(AmapQuotaConfigService.class);
        AmapApiAuditService auditService = Mockito.mock(AmapApiAuditService.class);
        AmapOpenService openService = Mockito.mock(AmapOpenService.class);

        Mockito.when(quotaService.resolveBoundAppKey()).thenReturn("test-key");
        Mockito.when(auditService.save(Mockito.any(AmapApiAudit.class))).thenReturn(true);
        Mockito.when(openService.districtSearchWithMeta(Mockito.anyString(), Mockito.anyString(), Mockito.anyInt(), Mockito.isNull(), Mockito.anyInt(), Mockito.anyInt()))
                .thenReturn(AmapOpenService.AmapApiResult.cached(mockProvinceDistrictData(), "redis"));

        AmapOpenApplicationService service = new AmapOpenApplicationService(quotaService, auditService, openService);
        Result<List<AmapOpenApplicationService.RegionOptionItem>> result =
                service.provinceOptions(new MockHttpServletRequest("GET", "/api/v2/miniapp/amap/regions/provinces"), null, 20);

        Assertions.assertEquals(Result.SUCCESS_CODE, result.getCode());
        Assertions.assertNotNull(result.getData());
        Assertions.assertFalse(result.getData().isEmpty());

        ArgumentCaptor<AmapApiAudit> captor = ArgumentCaptor.forClass(AmapApiAudit.class);
        Mockito.verify(auditService, Mockito.atLeastOnce()).save(captor.capture());
        AmapApiAudit lastAudit = captor.getValue();
        Assertions.assertEquals("location", lastAudit.getApiType());
        Assertions.assertTrue(String.valueOf(lastAudit.getRequestSource()).contains("backend-proxy-cache:redis"));
        Mockito.verify(quotaService, Mockito.never()).increaseUsageByBillingCategory(Mockito.anyString(), Mockito.anyInt());
    }

    @Test
    void weatherSnapshot_shouldRecordWeatherCacheSourceAndNotIncreaseQuotaInAuditLayer() {
        AmapQuotaConfigService quotaService = Mockito.mock(AmapQuotaConfigService.class);
        AmapApiAuditService auditService = Mockito.mock(AmapApiAuditService.class);
        AmapOpenService openService = Mockito.mock(AmapOpenService.class);

        Mockito.when(quotaService.resolveBoundAppKey()).thenReturn("test-key");
        Mockito.when(auditService.save(Mockito.any(AmapApiAudit.class))).thenReturn(true);
        Mockito.when(openService.reverseGeocode(Mockito.anyString(), Mockito.anyDouble(), Mockito.anyDouble()))
                .thenReturn(mockRegeoData());
        Mockito.when(openService.weatherLiveWithMeta(Mockito.anyString(), Mockito.eq("110000")))
                .thenReturn(AmapOpenService.AmapApiResult.cached(mockWeatherData(), "redis"));

        AmapOpenApplicationService service = new AmapOpenApplicationService(quotaService, auditService, openService);
        Result<AmapOpenApplicationService.WeatherSnapshotResp> result =
                service.weatherSnapshot(new MockHttpServletRequest("GET", "/api/v2/miniapp/amap/weather/snapshot"), 116.397470, 39.908823);

        Assertions.assertEquals(Result.SUCCESS_CODE, result.getCode());
        Assertions.assertNotNull(result.getData());
        Assertions.assertEquals("晴", result.getData().getWeather());

        ArgumentCaptor<AmapApiAudit> captor = ArgumentCaptor.forClass(AmapApiAudit.class);
        Mockito.verify(auditService, Mockito.atLeast(2)).save(captor.capture());
        List<AmapApiAudit> rows = captor.getAllValues();
        AmapApiAudit weatherAudit = rows.get(rows.size() - 1);
        Assertions.assertEquals("weather", weatherAudit.getApiType());
        Assertions.assertTrue(String.valueOf(weatherAudit.getRequestSource()).contains("backend-proxy-cache:redis"));
        Mockito.verify(quotaService, Mockito.never()).increaseUsageByBillingCategory(Mockito.anyString(), Mockito.anyInt());
    }

    private Map<String, Object> mockProvinceDistrictData() {
        Map<String, Object> province = new LinkedHashMap<>();
        province.put("name", "山东省");
        province.put("level", "province");
        province.put("adcode", "370000");
        province.put("districts", Arrays.asList());

        Map<String, Object> country = new LinkedHashMap<>();
        country.put("name", "中国");
        country.put("level", "country");
        country.put("districts", Arrays.asList(province));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("districts", Arrays.asList(country));
        return data;
    }

    private Map<String, Object> mockRegeoData() {
        Map<String, Object> addressComponent = new LinkedHashMap<>();
        addressComponent.put("province", "北京市");
        addressComponent.put("city", "北京市");
        addressComponent.put("district", "东城区");
        addressComponent.put("township", "景山街道");
        addressComponent.put("adcode", "110000");
        addressComponent.put("streetNumber", new LinkedHashMap<String, Object>());

        Map<String, Object> regeo = new LinkedHashMap<>();
        regeo.put("formatted_address", "北京市东城区");
        regeo.put("addressComponent", addressComponent);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("regeocode", regeo);
        return data;
    }

    private Map<String, Object> mockWeatherData() {
        Map<String, Object> live = new LinkedHashMap<>();
        live.put("weather", "晴");
        live.put("temperature", "18");
        live.put("humidity", "20");
        live.put("winddirection", "北");
        live.put("windpower", "2");
        live.put("reporttime", "2026-03-06 09:00:00");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("lives", Arrays.asList(live));
        return data;
    }
}
