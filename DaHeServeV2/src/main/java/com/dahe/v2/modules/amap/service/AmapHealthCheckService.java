package com.dahe.v2.modules.amap.service;

import java.util.Map;

/**
 * 高德健康检查服务。
 */
public interface AmapHealthCheckService {

    /** 执行 key/逆地理/天气健康检查。 */
    Map<String, Object> runHealthCheck(String appKey, Double longitude, Double latitude);
}
