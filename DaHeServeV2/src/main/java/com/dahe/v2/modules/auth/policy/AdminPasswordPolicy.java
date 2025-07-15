package com.dahe.v2.modules.auth.policy;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 后台账号登录名与密码策略。
 *
 * <p>职责：统一约束账号密码格式，避免规则散落在 controller/service。</p>
 */
@Component
public class AdminPasswordPolicy {

    /**
     * 归一化登录名：去空白并转小写。
     */
    public String normalizeLoginName(String raw) {
        String value = String.valueOf(raw == null ? "" : raw).trim().toLowerCase(Locale.ROOT);
        return StringUtils.hasText(value) ? value : null;
    }

    /**
     * 登录名规则：4~32 位，仅允许小写字母、数字、点、下划线、中划线。
     */
    public boolean isValidLoginName(String loginName) {
        String value = normalizeLoginName(loginName);
        if (!StringUtils.hasText(value)) {
            return false;
        }
        if (value.length() < 4 || value.length() > 32) {
            return false;
        }
        for (int i = 0; i < value.length(); i += 1) {
            char c = value.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '.'
                    || c == '_'
                    || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /**
     * 密码规则：
     * 1. 长度 8~64；
     * 2. 至少包含 1 个字母和 1 个数字。
     */
    public boolean isStrongPassword(String password) {
        String value = String.valueOf(password == null ? "" : password);
        if (value.length() < 8 || value.length() > 64) {
            return false;
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < value.length(); i += 1) {
            char c = value.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                hasLetter = true;
            } else if (c >= '0' && c <= '9') {
                hasDigit = true;
            }
            if (hasLetter && hasDigit) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算密码哈希（BCrypt）。
     */
    public String encodePassword(String rawPassword, PasswordEncoder passwordEncoder) {
        if (!StringUtils.hasText(rawPassword) || passwordEncoder == null) {
            return null;
        }
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * 校验密码是否匹配。
     */
    public boolean matches(String rawPassword, String passwordHash, PasswordEncoder passwordEncoder) {
        if (!StringUtils.hasText(rawPassword) || !StringUtils.hasText(passwordHash) || passwordEncoder == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, passwordHash);
    }
}

