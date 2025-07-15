package com.dahe.v2.modules.auth.support;

import com.dahe.v2.modules.user.model.AppUser;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 统一 API 路由授权服务。
 *
 * <p>目标：将权限判定集中在 auth 模块，业务模块不再在 controller 中编写角色/权限判断逻辑。</p>
 *
 * <p>为什么要做成“路由授权中心”：</p>
 * <p>1. 防止权限规则散落在各 controller，导致维护困难和漏鉴权；</p>
 * <p>2. 新增模块时只要遵循路由命名约定，就能自动接入授权；</p>
 * <p>3. 授权拒绝原因可统一输出，前端提示和审计日志可统一收敛。</p>
 *
 * <p>总决策顺序（面试可直接复述）：</p>
 * <p>1. 识别请求所属域：admin / miniapp / shared；</p>
 * <p>2. 校验端侧身份边界；</p>
 * <p>3. 解析该路由所需菜单权限（显式映射优先，推断兜底）；</p>
 * <p>4. 超级管理员走兜底放行；普通后台用户按权限匹配；</p>
 * <p>5. 小程序用户仅按 miniapp 规则与 canConsole 判定。</p>
 * <p>规则分层：</p>
 * <p>1. `/api/v2/admin/**`：仅后台用户可访问，按角色菜单权限授权；</p>
 * <p>2. `/api/v2/miniapp/**`：仅小程序用户可访问；</p>
 * <p>3. 历史共享路径：按「HTTP 方法 + 路径」做细粒度授权；</p>
 * <p>4. 默认策略：读接口放行，写接口要求可解析到菜单权限，否则拒绝。</p>
 *
 * <p>新模块接入规范：</p>
 * <p>1. 后台接口统一放在 `/api/v2/admin/{module}/**`；</p>
 * <p>2. 小程序接口统一放在 `/api/v2/miniapp/{module}/**`；</p>
 * <p>3. 只要路由段与菜单 key 命名遵循约定，新增模块无需再写 controller 鉴权代码。</p>
 */
@Component
public class AuthApiRouteAuthorizationService {

    private static final String API_PREFIX = "/api/v2";
    private static final String API_ADMIN_PREFIX = API_PREFIX + "/admin";
    private static final String API_MINIAPP_PREFIX = API_PREFIX + "/miniapp";
    private static final Set<String> WRITE_METHODS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "POST", "PUT", "PATCH", "DELETE"
    )));

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final AuthRoutePermissionResolver authRoutePermissionResolver;
    private final List<SharedRouteRule> sharedRouteRules;

    public AuthApiRouteAuthorizationService(AuthRoutePermissionResolver authRoutePermissionResolver) {
        this.authRoutePermissionResolver = authRoutePermissionResolver;
        this.sharedRouteRules = buildSharedRouteRules();
    }

    /**
     * 执行路由授权，返回 null 表示通过，返回非空字符串表示拒绝原因。
     *
     * <p>注意：这里不负责 token/账号状态校验，这些前置工作由 `ApiAuthInterceptor` 完成。</p>
     */
    public String authorize(HttpServletRequest request, AppUser user) {
        if (request == null || user == null) {
            return "未登录或会话已失效";
        }
        String path = normalizePath(request.getRequestURI());
        String method = normalizeMethod(request.getMethod());

        if (isPathUnderPrefix(path, API_ADMIN_PREFIX)) {
            return authorizeAdminRoute(request, user, path);
        }
        if (isPathUnderPrefix(path, API_MINIAPP_PREFIX)) {
            return authorizeMiniappRoute(user);
        }
        return authorizeSharedRoute(request, user, method, path);
    }

    /**
     * admin 路由授权：用户类型校验 + 菜单权限校验。
     *
     * <p>超级管理员在本层直接放行，避免被路由映射缺失误伤。</p>
     */
    private String authorizeAdminRoute(HttpServletRequest request, AppUser user, String path) {
        if (!AuthContext.isAdminUser(user)) {
            return "仅后台账号可访问该接口";
        }
        if (isSuperAdmin(user)) {
            return null;
        }
        PermissionResolution resolution = resolveAdminRouteMenuPermissions(request.getMethod(), path);
        if (!resolution.isMapped()) {
            return "后台接口未配置权限映射，请联系管理员";
        }
        List<String> requiredMenuPermissions = resolution.getPermissions();
        if (requiredMenuPermissions.isEmpty()) {
            return null;
        }
        if (hasAnyMenuPermission(request, requiredMenuPermissions)) {
            return null;
        }
        return "缺少接口访问权限";
    }

    /**
     * miniapp 路由授权：仅小程序账号可访问。
     */
    private String authorizeMiniappRoute(AppUser user) {
        if (AuthContext.isAdminUser(user)) {
            return "后台账号不可访问小程序接口";
        }
        return null;
    }

    /**
     * 共享路由授权：
     * 1) 先匹配显式规则；
     * 2) 未命中显式规则时，读请求放行；
     * 3) 写请求按默认映射授权。
     *
     * <p>设计意图：在历史接口尚未完成前缀拆分前，保证授权有“最小可控默认值”。</p>
     */
    private String authorizeSharedRoute(HttpServletRequest request, AppUser user, String method, String path) {
        if (!AuthContext.isAdminUser(user)) {
            return "共享接口已下线，请升级到 /api/v2/miniapp/**";
        }

        for (SharedRouteRule rule : sharedRouteRules) {
            if (!rule.matches(pathMatcher, method, path)) {
                continue;
            }
            return authorizeSharedByRule(request, user, rule);
        }

        boolean writeMethod = WRITE_METHODS.contains(method);
        if (!writeMethod) {
            return null;
        }

        if (isSuperAdmin(user)) {
            return null;
        }
        PermissionResolution resolution = resolveSharedWriteMenuPermissions(method, path);
        if (!resolution.isMapped()) {
            return "共享写接口未配置授权策略，请迁移到 /api/v2/admin/** 或补充 auth 路由规则";
        }
        if (resolution.getPermissions().isEmpty()) {
            return null;
        }
        if (hasAnyMenuPermission(request, resolution.getPermissions())) {
            return null;
        }
        return "缺少接口访问权限";
    }

    /**
     * 显式共享规则判定（仅后台账号）。
     */
    private String authorizeSharedByRule(HttpServletRequest request, AppUser user, SharedRouteRule rule) {
        if (isSuperAdmin(user)) {
            return null;
        }
        if (rule.getAdminMenuPermissions().isEmpty()) {
            return null;
        }
        if (hasAnyMenuPermission(request, rule.getAdminMenuPermissions())) {
            return null;
        }
        return "缺少接口访问权限";
    }

    /**
     * admin 路由 -> 菜单权限映射：
     * 1) 先走显式前缀映射（用于不规则路由）；
     * 2) 再走约定推断（`{segment}` -> 目录 key 候选）；
     * 3) 若目录未注册但候选 key 合法，则作为自定义权限 key 使用；
     * 4) 仍无法推断时返回未映射，由上层拒绝访问，避免漏鉴权。
     */
    private PermissionResolution resolveAdminRouteMenuPermissions(String method, String path) {
        List<String> inferred = authRoutePermissionResolver.resolveAdminPermissions(method, path);
        if (inferred == null || inferred.isEmpty()) {
            return PermissionResolution.unmapped();
        }
        return PermissionResolution.mapped(inferred);
    }

    /**
     * 共享写接口 -> 菜单权限映射（后台用户）。
     */
    private PermissionResolution resolveSharedWriteMenuPermissions(String method, String path) {
        List<String> inferred = authRoutePermissionResolver.resolveSharedWritePermissions(method, path);
        if (inferred == null || inferred.isEmpty()) {
            return PermissionResolution.unmapped();
        }
        return PermissionResolution.mapped(inferred);
    }

    private List<SharedRouteRule> buildSharedRouteRules() {
        List<SharedRouteRule> out = new ArrayList<SharedRouteRule>();
        out.add(SharedRouteRule.of(
                Collections.singleton("GET"),
                "/api/v2/farm-records/operator-options",
                Collections.singletonList("/farm-records-manage")
        ));
        out.add(SharedRouteRule.of(
                Collections.singleton("GET"),
                "/api/v2/farm-records/operator-detail/**",
                Collections.singletonList("/farm-records-manage")
        ));
        out.add(SharedRouteRule.of(
                new HashSet<String>(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE")),
                "/api/v2/crops/**",
                Collections.singletonList("/crop-manage")
        ));
        out.add(SharedRouteRule.of(
                WRITE_METHODS,
                "/api/v2/farm-records/**",
                Collections.singletonList("/farm-records-manage")
        ));
        out.add(SharedRouteRule.of(
                WRITE_METHODS,
                "/api/v2/seed-batches/**",
                Collections.singletonList("/seed-manage")
        ));
        out.add(SharedRouteRule.of(
                new HashSet<String>(Arrays.asList("PUT", "PATCH")),
                "/api/v2/seed-settings",
                Collections.singletonList("/seed-rules")
        ));
        out.add(SharedRouteRule.of(
                Collections.singleton("POST"),
                "/api/v2/files/upload",
                Collections.singletonList("/assets")
        ));
        out.add(SharedRouteRule.of(
                WRITE_METHODS,
                "/api/v2/fields/**/cycles",
                Collections.singletonList("/field-cycles")
        ));
        out.add(SharedRouteRule.of(
                WRITE_METHODS,
                "/api/v2/fields/**/cycles/**",
                Collections.singletonList("/field-cycles")
        ));
        return Collections.unmodifiableList(out);
    }

    private boolean hasAnyMenuPermission(HttpServletRequest request, List<String> menuPermissions) {
        if (menuPermissions == null || menuPermissions.isEmpty()) {
            return true;
        }
        return AuthContext.hasAnyMenuPermission(request, menuPermissions.toArray(new String[0]));
    }

    private boolean isPathUnderPrefix(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private boolean isSuperAdmin(AppUser user) {
        return user != null && user.getIsSuperAdmin() != null && user.getIsSuperAdmin() == 1;
    }

    private String normalizePath(String rawPath) {
        String path = String.valueOf(rawPath == null ? "" : rawPath).trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(path)) {
            return "/";
        }
        if (!path.startsWith("/")) {
            return "/" + path;
        }
        return path;
    }

    private String normalizeMethod(String rawMethod) {
        return String.valueOf(rawMethod == null ? "" : rawMethod).trim().toUpperCase(Locale.ROOT);
    }

    private static final class PermissionResolution {
        private final boolean mapped;
        private final List<String> permissions;

        private PermissionResolution(boolean mapped, List<String> permissions) {
            this.mapped = mapped;
            this.permissions = permissions == null
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<String>(permissions));
        }

        private static PermissionResolution mapped(List<String> permissions) {
            return new PermissionResolution(true, permissions);
        }

        private static PermissionResolution unmapped() {
            return new PermissionResolution(false, Collections.<String>emptyList());
        }

        private boolean isMapped() {
            return mapped;
        }

        private List<String> getPermissions() {
            return permissions;
        }
    }

    private static final class SharedRouteRule {
        private final Set<String> methods;
        private final String pathPattern;
        private final List<String> adminMenuPermissions;

        private SharedRouteRule(
                Set<String> methods,
                String pathPattern,
                List<String> adminMenuPermissions
        ) {
            this.methods = methods == null
                    ? Collections.<String>emptySet()
                    : Collections.unmodifiableSet(new HashSet<String>(methods));
            this.pathPattern = pathPattern;
            this.adminMenuPermissions = adminMenuPermissions == null
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<String>(adminMenuPermissions));
        }

        private static SharedRouteRule of(
                Set<String> methods,
                String pathPattern,
                List<String> adminMenuPermissions
        ) {
            return new SharedRouteRule(methods, pathPattern, adminMenuPermissions);
        }

        private boolean matches(AntPathMatcher matcher, String method, String path) {
            if (!methods.isEmpty() && !methods.contains(method)) {
                return false;
            }
            return matcher.match(pathPattern, path);
        }

        private List<String> getAdminMenuPermissions() {
            return adminMenuPermissions;
        }
    }
}
