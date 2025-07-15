package com.dahe.v2.modules.company.service.impl;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * company 模块输入归一化与数据校验组件。
 */
@Component
public class CompanyIntroValueNormalizer {

    private static final Set<String> CONTACT_TYPE_WHITELIST = new HashSet<String>(
            Arrays.asList("address", "phone", "email", "website")
    );
    private static final Pattern HTTP_URL = Pattern.compile("^https?://.+", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+()\\-\\s]{6,32}$");

    /**
     * 状态归一化，仅允许 0/1。
     */
    public int normalizeStatus(Integer status, int fallback) {
        if (status == null) {
            return fallback;
        }
        return status == 0 ? 0 : 1;
    }

    /**
     * 非负整数归一化。
     */
    public Integer normalizeNonNegative(Integer value, Integer fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(0, value);
    }

    /**
     * 文本基础归一化：
     * 1. 去空白
     * 2. 处理 undefined/null 字符串
     * 3. 统一返回 null 或有效文本
     */
    public String normalizeText(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String value = text.trim();
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if ("undefined".equals(lower) || "null".equals(lower)) {
            return null;
        }
        return value;
    }

    /**
     * 文本字段归一化并校验长度。
     */
    public String normalizeLimitedText(String text, int maxLen, String fieldLabel, boolean required) {
        String value = normalizeText(text);
        if (!StringUtils.hasText(value)) {
            if (required) {
                throw new IllegalArgumentException(fieldLabel + "不能为空");
            }
            return null;
        }
        if (value.length() > maxLen) {
            throw new IllegalArgumentException(fieldLabel + "长度不能超过" + maxLen);
        }
        return value;
    }

    /**
     * URL 字段归一化并校验：
     * 允许 http/https 绝对地址，或以 / 开头的站内资源路径。
     */
    public String normalizeUrl(String text, int maxLen, String fieldLabel) {
        String value = normalizeLimitedText(text, maxLen, fieldLabel, false);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.startsWith("/")) {
            return value;
        }
        if (!HTTP_URL.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldLabel + "格式不正确，必须为 http(s) 地址或 / 开头路径");
        }
        return value;
    }

    /**
     * 联系方式类型归一化并校验白名单。
     */
    public String normalizeContactType(String contactType) {
        String value = normalizeLimitedText(contactType, 32, "联系方式类型", true);
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!CONTACT_TYPE_WHITELIST.contains(normalized)) {
            throw new IllegalArgumentException("联系方式类型仅支持 address/phone/email/website");
        }
        return normalized;
    }

    /**
     * 按联系方式类型校验联系方式内容格式。
     */
    public void validateContactValue(String contactType, String contactValue) {
        if (!StringUtils.hasText(contactType) || !StringUtils.hasText(contactValue)) {
            return;
        }
        if ("email".equals(contactType) && !EMAIL.matcher(contactValue).matches()) {
            throw new IllegalArgumentException("邮箱格式不正确");
        }
        if ("phone".equals(contactType) && !PHONE.matcher(contactValue).matches()) {
            throw new IllegalArgumentException("联系电话格式不正确");
        }
        if ("website".equals(contactType) && !HTTP_URL.matcher(contactValue).matches()) {
            throw new IllegalArgumentException("网站地址必须为 http(s) 开头");
        }
    }
}
