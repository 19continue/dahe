package com.dahe.v2.modules.auth.support;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 统一解析“API 路由 -> 后台菜单权限码”。
 *
 * <p>核心目标：</p>
 * <p>1. 权限码来源以后端路由为准，新增模块无需改中心常量；</p>
 * <p>2. 路由与菜单码不一致时，支持在控制器上通过 {@link AdminMenuCode} 显式声明；</p>
 * <p>3. 为 auth 领域提供单一入口：菜单码列表、路由授权码解析、权限码支持性判定。</p>
 */
@Component
public class AuthRoutePermissionResolver {

    public static final String MENU_DASHBOARD = "/dashboard";

    private static final String API_PREFIX = "/api/v2";
    private static final String API_ADMIN_PREFIX = API_PREFIX + "/admin";
    private static final String API_ADMIN_AUTH_PREFIX = API_ADMIN_PREFIX + "/auth";
    private static final String API_MINIAPP_PREFIX = API_PREFIX + "/miniapp";

    private final ObjectProvider<RequestMappingHandlerMapping> handlerMappingProvider;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private volatile List<RouteRule> routeRules = Collections.emptyList();
    private volatile List<String> adminMenuCodes = Collections.singletonList(MENU_DASHBOARD);
    private volatile Set<String> adminMenuCodeSet = Collections.singleton(MENU_DASHBOARD);

    public AuthRoutePermissionResolver(
            @Qualifier("requestMappingHandlerMapping") ObjectProvider<RequestMappingHandlerMapping> handlerMappingProvider
    ) {
        this.handlerMappingProvider = handlerMappingProvider;
    }

    /**
     * 在 Spring MVC 完成初始化后构建路由权限缓存，避免在 Bean 创建阶段触发循环依赖。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        reload();
    }

    /**
     * 重建路由权限缓存。
     *
     * <p>当前实现为启动时构建，预留显式刷新入口，便于后续做热更新或测试场景重载。</p>
     */
    public synchronized void reload() {
        RequestMappingHandlerMapping handlerMapping = handlerMappingProvider.getIfAvailable();
        if (handlerMapping == null) {
            this.routeRules = Collections.emptyList();
            this.adminMenuCodes = Collections.singletonList(MENU_DASHBOARD);
            this.adminMenuCodeSet = Collections.singleton(MENU_DASHBOARD);
            return;
        }

        List<RouteRule> nextRules = new ArrayList<RouteRule>();
        LinkedHashSet<String> nextAdminCodes = new LinkedHashSet<String>();
        nextAdminCodes.add(MENU_DASHBOARD);

        for (java.util.Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod handlerMethod = entry.getValue();
            if (info == null || handlerMethod == null) {
                continue;
            }
            List<String> explicitCodes = resolveExplicitMenuCodes(handlerMethod);
            Set<String> methods = resolveMethods(info);
            Set<String> patterns = resolvePatterns(info);
            for (String rawPattern : patterns) {
                String pattern = normalizePath(rawPattern);
                if (!StringUtils.hasText(pattern)) {
                    continue;
                }
                RouteDomain domain = resolveDomain(pattern);
                if (domain == RouteDomain.UNKNOWN) {
                    continue;
                }
                if (domain == RouteDomain.ADMIN && isAdminAuthPath(pattern)) {
                    continue;
                }
                List<String> menuCodes = explicitCodes;
                if (menuCodes.isEmpty()) {
                    menuCodes = inferMenuCodes(domain, pattern);
                }
                if (domain == RouteDomain.ADMIN && !menuCodes.isEmpty()) {
                    nextAdminCodes.addAll(menuCodes);
                }
                nextRules.add(new RouteRule(domain, pattern, methods, menuCodes));
            }
        }

        nextRules.sort(RouteRule.SPECIFIC_FIRST);
        List<String> sortedCodes = new ArrayList<String>(nextAdminCodes);
        sortedCodes.sort(String::compareTo);

        this.routeRules = Collections.unmodifiableList(nextRules);
        this.adminMenuCodes = Collections.unmodifiableList(sortedCodes);
        this.adminMenuCodeSet = Collections.unmodifiableSet(new LinkedHashSet<String>(sortedCodes));
    }

    /**
     * 返回“后台角色可分配”的菜单权限码清单（仅路径码，无中文文案）。
     */
    public List<String> listAdminMenuCodes() {
        ensureInitialized();
        return adminMenuCodes;
    }

    /**
     * 路由授权解析：admin 域路由。
     */
    public List<String> resolveAdminPermissions(String method, String path) {
        ensureInitialized();
        String normalizedMethod = normalizeMethod(method);
        String normalizedPath = normalizePath(path);
        RouteRule matched = matchFirst(RouteDomain.ADMIN, normalizedMethod, normalizedPath);
        if (matched != null && !matched.getMenuCodes().isEmpty()) {
            return matched.getMenuCodes();
        }
        return inferMenuCodes(RouteDomain.ADMIN, normalizedPath);
    }

    /**
     * 路由授权解析：shared 写接口（用于后台用户授权判断）。
     */
    public List<String> resolveSharedWritePermissions(String method, String path) {
        ensureInitialized();
        String normalizedMethod = normalizeMethod(method);
        String normalizedPath = normalizePath(path);
        RouteRule matched = matchFirst(RouteDomain.SHARED, normalizedMethod, normalizedPath);
        if (matched != null && !matched.getMenuCodes().isEmpty()) {
            return matched.getMenuCodes();
        }
        return inferMenuCodes(RouteDomain.SHARED, normalizedPath);
    }

    /**
     * 判断某个菜单码是否在当前路由目录内可识别。
     */
    public boolean supportsAdminMenuCode(String menuCode) {
        ensureInitialized();
        String normalized = normalizeMenuCode(menuCode);
        return StringUtils.hasText(normalized) && adminMenuCodeSet.contains(normalized);
    }

    /**
     * 返回后台默认权限码。
     */
    public List<String> defaultAdminMenuCodes() {
        return Collections.singletonList(MENU_DASHBOARD);
    }

    private void ensureInitialized() {
        if (routeRules != null && !routeRules.isEmpty()) {
            return;
        }
        reload();
    }

    private RouteRule matchFirst(RouteDomain domain, String method, String path) {
        if (domain == null || !StringUtils.hasText(path)) {
            return null;
        }
        for (RouteRule rule : routeRules) {
            if (!rule.matches(domain, method, path, pathMatcher)) {
                continue;
            }
            return rule;
        }
        return null;
    }

    private List<String> resolveExplicitMenuCodes(HandlerMethod handlerMethod) {
        List<String> methodLevel = normalizeMenuCodes(readAnnotationValues(
                AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), AdminMenuCode.class)
        ));
        if (!methodLevel.isEmpty()) {
            return methodLevel;
        }
        return normalizeMenuCodes(readAnnotationValues(
                AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), AdminMenuCode.class)
        ));
    }

    private String[] readAnnotationValues(AdminMenuCode annotation) {
        return annotation == null ? new String[0] : annotation.value();
    }

    private List<String> normalizeMenuCodes(String[] values) {
        if (values == null || values.length == 0) {
            return Collections.emptyList();
        }
        return normalizeMenuCodes(Arrays.asList(values));
    }

    private List<String> normalizeMenuCodes(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> out = new LinkedHashSet<String>();
        for (String value : values) {
            String code = normalizeMenuCode(value);
            if (StringUtils.hasText(code)) {
                out.add(code);
            }
        }
        if (out.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<String>(out));
    }

    private Set<String> resolvePatterns(RequestMappingInfo info) {
        LinkedHashSet<String> out = new LinkedHashSet<String>();
        if (info == null) {
            return out;
        }
        PathPatternsRequestCondition pathPatternsCondition = info.getPathPatternsCondition();
        if (pathPatternsCondition != null) {
            out.addAll(pathPatternsCondition.getPatternValues());
        }
        if (info.getPatternsCondition() != null) {
            out.addAll(info.getPatternsCondition().getPatterns());
        }
        return out;
    }

    private Set<String> resolveMethods(RequestMappingInfo info) {
        if (info == null || info.getMethodsCondition() == null || info.getMethodsCondition().getMethods().isEmpty()) {
            return Collections.emptySet();
        }
        LinkedHashSet<String> out = new LinkedHashSet<String>();
        for (RequestMethod method : info.getMethodsCondition().getMethods()) {
            if (method != null) {
                out.add(method.name());
            }
        }
        return out;
    }

    private List<String> inferMenuCodes(RouteDomain domain, String path) {
        String inferred;
        if (domain == RouteDomain.ADMIN) {
            inferred = inferByPrefix(path, API_ADMIN_PREFIX);
        } else if (domain == RouteDomain.SHARED) {
            inferred = inferByPrefix(path, API_PREFIX);
        } else {
            inferred = null;
        }
        if (!StringUtils.hasText(inferred)) {
            return Collections.emptyList();
        }
        return Collections.singletonList(inferred);
    }

    private String inferByPrefix(String path, String prefix) {
        if (!StringUtils.hasText(path) || !StringUtils.hasText(prefix)) {
            return null;
        }
        String normalizedPath = normalizePath(path);
        String normalizedPrefix = normalizePath(prefix);
        if (!isPathUnderPrefix(normalizedPath, normalizedPrefix)) {
            return null;
        }
        String suffix = normalizedPath.length() > normalizedPrefix.length()
                ? normalizedPath.substring(normalizedPrefix.length())
                : "";
        if (suffix.startsWith("/")) {
            suffix = suffix.substring(1);
        }
        if (!StringUtils.hasText(suffix)) {
            return null;
        }
        int slashIndex = suffix.indexOf('/');
        String segment = slashIndex >= 0 ? suffix.substring(0, slashIndex) : suffix;
        segment = String.valueOf(segment == null ? "" : segment).trim();
        if (!StringUtils.hasText(segment) || segment.startsWith("{")) {
            return null;
        }
        return normalizeMenuCode("/" + segment);
    }

    private RouteDomain resolveDomain(String path) {
        if (isPathUnderPrefix(path, API_ADMIN_PREFIX)) {
            return RouteDomain.ADMIN;
        }
        if (isPathUnderPrefix(path, API_MINIAPP_PREFIX)) {
            return RouteDomain.MINIAPP;
        }
        if (isPathUnderPrefix(path, API_PREFIX)) {
            return RouteDomain.SHARED;
        }
        return RouteDomain.UNKNOWN;
    }

    private boolean isAdminAuthPath(String path) {
        return isPathUnderPrefix(path, API_ADMIN_AUTH_PREFIX);
    }

    private boolean isPathUnderPrefix(String path, String prefix) {
        if (!StringUtils.hasText(path) || !StringUtils.hasText(prefix)) {
            return false;
        }
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private String normalizeMenuCode(String code) {
        String text = String.valueOf(code == null ? "" : code).trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        if (!text.startsWith("/")) {
            text = "/" + text;
        }
        while (text.contains("//")) {
            text = text.replace("//", "/");
        }
        if (text.length() > 1 && text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        for (int i = 0; i < text.length(); i += 1) {
            char c = text.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '/'
                    || c == '-'
                    || c == '_';
            if (!ok) {
                return null;
            }
        }
        return text;
    }

    private String normalizePath(String rawPath) {
        String path = String.valueOf(rawPath == null ? "" : rawPath).trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(path)) {
            return "/";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        while (path.contains("//")) {
            path = path.replace("//", "/");
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private String normalizeMethod(String rawMethod) {
        return String.valueOf(rawMethod == null ? "" : rawMethod).trim().toUpperCase(Locale.ROOT);
    }

    private enum RouteDomain {
        UNKNOWN,
        ADMIN,
        MINIAPP,
        SHARED
    }

    private static final class RouteRule {
        private static final Comparator<RouteRule> SPECIFIC_FIRST = (a, b) -> {
            int scoreB = b.specificityScore();
            int scoreA = a.specificityScore();
            if (scoreA != scoreB) {
                return Integer.compare(scoreB, scoreA);
            }
            return b.pathPattern.compareTo(a.pathPattern);
        };

        private final RouteDomain domain;
        private final String pathPattern;
        private final Set<String> methods;
        private final List<String> menuCodes;

        private RouteRule(RouteDomain domain, String pathPattern, Set<String> methods, List<String> menuCodes) {
            this.domain = domain == null ? RouteDomain.UNKNOWN : domain;
            this.pathPattern = pathPattern;
            this.methods = methods == null
                    ? Collections.<String>emptySet()
                    : Collections.unmodifiableSet(new LinkedHashSet<String>(methods));
            this.menuCodes = menuCodes == null
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<String>(menuCodes));
        }

        private boolean matches(RouteDomain targetDomain, String method, String path, AntPathMatcher matcher) {
            if (targetDomain != domain) {
                return false;
            }
            if (!methods.isEmpty() && !methods.contains(method)) {
                return false;
            }
            return matcher.match(pathPattern, path);
        }

        private List<String> getMenuCodes() {
            return menuCodes;
        }

        private int specificityScore() {
            int score = 0;
            String pattern = String.valueOf(pathPattern == null ? "" : pathPattern);
            score += pattern.length() * 10;
            score -= countChar(pattern, '*') * 30;
            score -= countChar(pattern, '{') * 25;
            if (pattern.indexOf('*') < 0 && pattern.indexOf('{') < 0) {
                score += 200;
            }
            if (!methods.isEmpty()) {
                score += 20;
            }
            return score;
        }

        private int countChar(String text, char target) {
            int count = 0;
            for (int i = 0; i < text.length(); i += 1) {
                if (text.charAt(i) == target) {
                    count += 1;
                }
            }
            return count;
        }
    }
}
