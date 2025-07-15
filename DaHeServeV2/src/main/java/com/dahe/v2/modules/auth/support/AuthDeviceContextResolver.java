package com.dahe.v2.modules.auth.support;

import com.dahe.v2.modules.auth.dto.AuthPortalDTO;
import com.dahe.v2.modules.session.model.SessionDeviceContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;

/**
 * 登录设备上下文解析器。
 *
 * <p>统一收敛登录请求中的设备信息清洗逻辑，避免控制器与服务层散落硬编码。</p>
 */
@Component
public class AuthDeviceContextResolver {

    /**
     * 解析设备上下文：
     * 1. 前端字段优先；
     * 2. 缺失字段由服务端请求头补齐；
     * 3. 所有文本都做长度裁剪，防止异常长字符串入库。
     */
    public SessionDeviceContext resolve(AuthPortalDTO.DeviceContext reqContext, HttpServletRequest request) {
        SessionDeviceContext out = new SessionDeviceContext();
        if (reqContext != null) {
            out.setDeviceId(trimToLength(reqContext.getDeviceId(), 64));
            out.setDeviceName(trimToLength(reqContext.getDeviceName(), 128));
            out.setUserAgent(trimToLength(reqContext.getUserAgent(), 512));
        }
        if (!StringUtils.hasText(out.getUserAgent()) && request != null) {
            out.setUserAgent(trimToLength(request.getHeader("User-Agent"), 512));
        }
        out.setClientIp(trimToLength(resolveClientIp(request), 64));
        if (!StringUtils.hasText(out.getDeviceName())) {
            out.setDeviceName("unknown-device");
        }
        return out;
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            String[] items = forwarded.split(",");
            if (items.length > 0 && StringUtils.hasText(items[0])) {
                return items[0].trim();
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String trimToLength(String raw, int maxLen) {
        String text = String.valueOf(raw == null ? "" : raw).trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLen));
    }
}
