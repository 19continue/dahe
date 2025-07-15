package com.dahe.v2.modules.auth.policy;

import com.dahe.v2.modules.auth.config.AuthProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 小程序登录场景策略。
 *
 * <p>负责场景值标准化与白名单校验，减少业务层重复解析逻辑。</p>
 */
@Component
public class MiniappLoginScenePolicy {

    private final Set<String> allowedScenes;

    public MiniappLoginScenePolicy(AuthProperties authProperties) {
        this.allowedScenes = parseAllowedScenes(authProperties == null ? null : authProperties.getMiniappLoginScenes());
    }

    public String normalizeScene(String loginScene) {
        String raw = String.valueOf(loginScene == null ? "" : loginScene).trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') {
                sb.append(c);
            }
        }
        String normalized = sb.toString();
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    public boolean isAllowed(String scene) {
        String normalized = normalizeScene(scene);
        return StringUtils.hasText(normalized) && !allowedScenes.isEmpty() && allowedScenes.contains(normalized);
    }

    public Set<String> getAllowedScenes() {
        return Collections.unmodifiableSet(allowedScenes);
    }

    private Set<String> parseAllowedScenes(String rawConfig) {
        Set<String> out = new HashSet<String>();
        String[] rows = String.valueOf(rawConfig == null ? "" : rawConfig).split(",");
        for (String row : rows) {
            String item = normalizeScene(row);
            if (StringUtils.hasText(item)) {
                out.add(item);
            }
        }
        return out;
    }
}
