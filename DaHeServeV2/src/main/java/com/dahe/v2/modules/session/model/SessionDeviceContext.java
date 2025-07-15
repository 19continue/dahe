package com.dahe.v2.modules.session.model;

import lombok.Data;

/**
 * 会话设备上下文。
 *
 * <p>用于在会话创建时携带设备与客户端来源信息，支撑审计与风控。</p>
 */
@Data
public class SessionDeviceContext {

    /** 前端设备标识（建议持久化）。 */
    private String deviceId;

    /** 前端设备名称（如 admin-web / android / ios）。 */
    private String deviceName;

    /** 服务端解析的客户端 IP。 */
    private String clientIp;

    /** 客户端 User-Agent。 */
    private String userAgent;
}
