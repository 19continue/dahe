package com.dahe.v2.modules.auth.policy;

import com.dahe.v2.modules.auth.config.AuthProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 管理员 openId 策略。
 *
 * <p>收敛 openId 的识别与生成规则，避免在业务服务中散写前缀字符串。</p>
 */
@Component
public class AdminOpenIdPolicy {

    private static final String DEFAULT_GENERATE_PREFIX = "admin_";

    private final List<String> detectPrefixes;
    private final String generatePrefix;

    public AdminOpenIdPolicy(AuthProperties authProperties) {
        this.detectPrefixes = normalizePrefixes(authProperties == null ? null : authProperties.getAdminOpenIdDetectionPrefixes());
        this.generatePrefix = normalizeGeneratePrefix(authProperties == null ? null : authProperties.getAdminOpenIdGeneratePrefix());
    }

    /**
     * 判断 openId 是否符合后台账号命名特征。
     */
    public boolean isAdminOpenIdLike(String openId) {
        String normalizedOpenId = normalize(openId);
        if (!StringUtils.hasText(normalizedOpenId) || detectPrefixes.isEmpty()) {
            return false;
        }
        for (String prefix : detectPrefixes) {
            if (normalizedOpenId.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 生成后台账号 openId。
     */
    public String generateAdminOpenId() {
        return generatePrefix + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    private List<String> normalizePrefixes(List<String> prefixes) {
        List<String> out = new ArrayList<String>();
        if (prefixes == null || prefixes.isEmpty()) {
            out.add(DEFAULT_GENERATE_PREFIX);
            return out;
        }
        for (String raw : prefixes) {
            String item = normalize(raw);
            if (StringUtils.hasText(item)) {
                out.add(item);
            }
        }
        if (out.isEmpty()) {
            out.add(DEFAULT_GENERATE_PREFIX);
        }
        return out;
    }

    private String normalizeGeneratePrefix(String prefix) {
        String item = normalize(prefix);
        return StringUtils.hasText(item) ? item : DEFAULT_GENERATE_PREFIX;
    }

    private String normalize(String value) {
        return String.valueOf(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
    }
}
