package com.dahe.v2.modules.seed.service.impl;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * seed 查询参数归一化工具。
 */
@Component
public class SeedQueryNormalizer {

    public String normalizeQueryText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if ("undefined".equals(lower) || "null".equals(lower)) {
            return null;
        }
        return text;
    }

    public Integer normalizeEnabled(Integer enabled) {
        if (enabled == null) {
            return null;
        }
        return enabled == 0 ? 0 : 1;
    }

    public Integer resolveEnabled(Boolean enabled, boolean fallback) {
        if (enabled == null) {
            return fallback ? 1 : 0;
        }
        return enabled ? 1 : 0;
    }
}

