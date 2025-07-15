package com.dahe.v2.modules.auth.role.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dahe.v2.modules.auth.role.mapper.AdminRoleMapper;
import com.dahe.v2.modules.auth.role.model.AdminRole;
import com.dahe.v2.modules.auth.role.service.AdminRoleService;
import com.dahe.v2.modules.auth.support.AuthRoutePermissionResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminRoleServiceImpl extends ServiceImpl<AdminRoleMapper, AdminRole> implements AdminRoleService {

    /**
     * 角色编码最大长度，避免超长编码影响索引与前端展示。
     */
    private static final int MAX_ROLE_CODE_LEN = 32;

    private final ObjectMapper objectMapper;
    private final AuthRoutePermissionResolver authRoutePermissionResolver;

    public AdminRoleServiceImpl(
            ObjectMapper objectMapper,
            AuthRoutePermissionResolver authRoutePermissionResolver
    ) {
        this.objectMapper = objectMapper;
        this.authRoutePermissionResolver = authRoutePermissionResolver;
    }

    @Override
    public String normalizeRoleCode(String roleCode) {
        String raw = String.valueOf(roleCode == null ? "" : roleCode)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_');
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length(); i += 1) {
            char c = raw.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') {
                out.append(c);
            }
        }
        String normalized = out.toString();
        if (!StringUtils.hasText(normalized) || normalized.length() > MAX_ROLE_CODE_LEN) {
            return null;
        }
        char first = normalized.charAt(0);
        if (!(first >= 'a' && first <= 'z')) {
            return null;
        }
        return normalized;
    }

    @Override
    public String normalizeInheritRole(String inheritRoleCode) {
        String normalized = normalizeRoleCode(inheritRoleCode);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        // 继承目标必须是“当前可用角色”，禁用角色不允许被继承。
        AdminRole role = findByRoleCode(normalized, false);
        return role == null ? null : normalized;
    }

    @Override
    public boolean wouldCreateInheritanceCycle(String roleCode, String inheritRoleCode) {
        String current = normalizeRoleCode(roleCode);
        String target = normalizeRoleCode(inheritRoleCode);
        if (!StringUtils.hasText(current) || !StringUtils.hasText(target)) {
            return false;
        }
        if (current.equals(target)) {
            return true;
        }
        Set<String> visited = new LinkedHashSet<String>();
        String cursor = target;
        int depth = 0;
        while (StringUtils.hasText(cursor) && depth < 64) {
            if (!visited.add(cursor)) {
                return true;
            }
            if (current.equals(cursor)) {
                return true;
            }
            AdminRole role = findByRoleCode(cursor, true);
            if (role == null) {
                return false;
            }
            cursor = normalizeRoleCode(role.getInheritRoleCode());
            depth += 1;
        }
        return false;
    }

    @Override
    public AdminRole findByRoleCode(String roleCode, boolean includeDisabled) {
        String normalized = normalizeRoleCode(roleCode);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        LambdaQueryWrapper<AdminRole> qw = new LambdaQueryWrapper<AdminRole>();
        qw.eq(AdminRole::getRoleCode, normalized);
        if (!includeDisabled) {
            qw.eq(AdminRole::getEnabled, 1);
        }
        qw.last("limit 1");
        return this.getOne(qw, false);
    }

    @Override
    public String resolveEffectiveRoleCode(String roleCode) {
        String normalized = normalizeRoleCode(roleCode);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        AdminRole role = findByRoleCode(normalized, true);
        return role == null ? null : normalized;
    }

    @Override
    public List<String> resolveMenuPermissions(String roleCode) {
        String normalized = normalizeRoleCode(roleCode);
        if (!StringUtils.hasText(normalized)) {
            return defaultMenuPermissions();
        }
        AdminRole role = findByRoleCode(normalized, true);
        String source = role == null ? null : role.getMenuPermissionsJson();
        String inheritRoleCode = role == null ? null : role.getInheritRoleCode();
        return fromMenuPermissionsJson(source, inheritRoleCode);
    }

    @Override
    public List<RoleOption> listRoleOptions(boolean includeDisabled) {
        LambdaQueryWrapper<AdminRole> qw = new LambdaQueryWrapper<AdminRole>();
        if (!includeDisabled) {
            qw.eq(AdminRole::getEnabled, 1);
        }
        qw.orderByAsc(AdminRole::getSortOrder)
                .orderByDesc(AdminRole::getIsSystem)
                .orderByAsc(AdminRole::getCreatedAt)
                .orderByAsc(AdminRole::getId);
        List<AdminRole> rows = this.list(qw);
        List<RoleOption> out = new ArrayList<RoleOption>();
        for (AdminRole row : rows) {
            if (row == null) {
                continue;
            }
            RoleOption option = new RoleOption();
            option.setId(row.getId());
            option.setRoleCode(row.getRoleCode());
            option.setRoleName(row.getRoleName());
            option.setInheritRoleCode(normalizeRoleCode(row.getInheritRoleCode()));
            option.setEnabled(row.getEnabled());
            option.setIsSystem(row.getIsSystem());
            option.setSortOrder(row.getSortOrder());
            out.add(option);
        }
        return out;
    }

    @Override
    public Page<AdminRole> pageRoles(String keyword, Integer enabled, long page, long pageSize) {
        Page<AdminRole> p = new Page<AdminRole>(page, pageSize);
        LambdaQueryWrapper<AdminRole> qw = new LambdaQueryWrapper<AdminRole>();
        if (StringUtils.hasText(keyword)) {
            String key = keyword.trim();
            qw.and(w -> w.like(AdminRole::getRoleName, key)
                    .or().like(AdminRole::getRoleCode, key)
                    .or().like(AdminRole::getDescription, key));
        }
        if (enabled != null) {
            qw.eq(AdminRole::getEnabled, enabled == 0 ? 0 : 1);
        }
        qw.orderByAsc(AdminRole::getSortOrder)
                .orderByDesc(AdminRole::getIsSystem)
                .orderByAsc(AdminRole::getCreatedAt)
                .orderByAsc(AdminRole::getId);
        return this.page(p, qw);
    }

    @Override
    public List<String> listMenuPermissionCodes() {
        return authRoutePermissionResolver.listAdminMenuCodes();
    }

    @Override
    public String toMenuPermissionsJson(List<String> menuPermissions, String inheritRoleCode) {
        List<String> sanitized = sanitizeMenuPermissions(menuPermissions, inheritRoleCode);
        try {
            return objectMapper.writeValueAsString(sanitized);
        } catch (Exception e) {
            return "[]";
        }
    }

    @Override
    public List<String> fromMenuPermissionsJson(String text, String inheritRoleCode) {
        List<String> parsed = new ArrayList<String>();
        if (StringUtils.hasText(text)) {
            try {
                List<String> source = objectMapper.readValue(String.valueOf(text), new TypeReference<List<String>>() {
                });
                if (source != null) {
                    parsed.addAll(source);
                }
            } catch (Exception ignore) {
                // fall back to defaults
            }
        }
        return sanitizeMenuPermissions(parsed, inheritRoleCode);
    }

    @Override
    public String buildRoleCodeFromName(String roleName) {
        String source = String.valueOf(roleName == null ? "" : roleName).trim().toLowerCase(Locale.ROOT);
        StringBuilder ascii = new StringBuilder();
        for (int i = 0; i < source.length(); i += 1) {
            char c = source.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                ascii.append(c);
            }
        }
        String base = ascii.toString();
        if (!StringUtils.hasText(base)) {
            base = "role_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }
        if (base.length() > MAX_ROLE_CODE_LEN) {
            base = base.substring(0, MAX_ROLE_CODE_LEN);
        }
        if (!(base.charAt(0) >= 'a' && base.charAt(0) <= 'z')) {
            base = "role_" + base;
            if (base.length() > MAX_ROLE_CODE_LEN) {
                base = base.substring(0, MAX_ROLE_CODE_LEN);
            }
        }

        String candidate = normalizeRoleCode(base);
        if (!StringUtils.hasText(candidate)) {
            candidate = "role_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }
        if (!existsRoleCode(candidate)) {
            return candidate;
        }

        int seq = 2;
        while (seq < 1000) {
            String suffix = String.valueOf(seq);
            String next = candidate;
            int maxPrefixLen = Math.max(1, MAX_ROLE_CODE_LEN - suffix.length());
            if (next.length() > maxPrefixLen) {
                next = next.substring(0, maxPrefixLen);
            }
            next = next + suffix;
            String normalized = normalizeRoleCode(next);
            if (StringUtils.hasText(normalized) && !existsRoleCode(normalized)) {
                return normalized;
            }
            seq += 1;
        }
        return "role_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private boolean existsRoleCode(String roleCode) {
        String normalized = normalizeRoleCode(roleCode);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        long count = this.lambdaQuery().eq(AdminRole::getRoleCode, normalized).count();
        return count > 0;
    }

    /**
     * 权限清洗规则：
     * 1. 仅保留权限目录中已注册的菜单 key；
     * 2. 去重并保留输入顺序；
     * 3. 合并继承链权限；
     * 4. 至少保留默认权限（默认 `/dashboard`）。
     */
    private List<String> sanitizeMenuPermissions(List<String> menuPermissions, String inheritRoleCode) {
        Set<String> out = new LinkedHashSet<String>();
        out.addAll(resolveInheritedMenuPermissions(inheritRoleCode));
        List<String> source = menuPermissions == null ? Collections.<String>emptyList() : menuPermissions;
        for (String key : source) {
            String text = String.valueOf(key == null ? "" : key).trim();
            if (!StringUtils.hasText(text)) {
                continue;
            }
            if (isSupportedPermissionKey(text)) {
                out.add(text);
            }
        }
        if (out.isEmpty()) {
            out.addAll(defaultMenuPermissions());
        }
        if (!out.contains(AuthRoutePermissionResolver.MENU_DASHBOARD)) {
            out.add(AuthRoutePermissionResolver.MENU_DASHBOARD);
        }
        return new ArrayList<String>(out);
    }

    private Set<String> resolveInheritedMenuPermissions(String inheritRoleCode) {
        Set<String> out = new LinkedHashSet<String>();
        String normalized = normalizeRoleCode(inheritRoleCode);
        if (!StringUtils.hasText(normalized)) {
            return out;
        }
        Set<String> visited = new LinkedHashSet<String>();
        String cursor = normalized;
        int depth = 0;
        while (StringUtils.hasText(cursor) && depth < 64) {
            if (!visited.add(cursor)) {
                break;
            }
            // 仅从启用角色继承权限，禁用角色不参与权限扩散。
            AdminRole role = findByRoleCode(cursor, false);
            if (role == null) {
                break;
            }
            out.addAll(parseOwnMenuPermissions(role.getMenuPermissionsJson()));
            cursor = normalizeRoleCode(role.getInheritRoleCode());
            depth += 1;
        }
        return out;
    }

    private Set<String> parseOwnMenuPermissions(String text) {
        Set<String> out = new LinkedHashSet<String>();
        if (!StringUtils.hasText(text)) {
            return out;
        }
        try {
            List<String> source = objectMapper.readValue(String.valueOf(text), new TypeReference<List<String>>() {
            });
            if (source == null || source.isEmpty()) {
                return out;
            }
            for (String key : source) {
                String item = String.valueOf(key == null ? "" : key).trim();
                if (isSupportedPermissionKey(item)) {
                    out.add(item);
                }
            }
            return out;
        } catch (Exception ignore) {
            return out;
        }
    }

    /**
     * 权限 key 校验策略：
     * 1. 优先使用目录内定义（推荐）；
     * 2. 允许保留“自定义路径权限”以支持模块增量接入，格式：`/[a-z0-9/_-]+`。
     */
    private boolean isSupportedPermissionKey(String key) {
        String text = String.valueOf(key == null ? "" : key).trim();
        if (!StringUtils.hasText(text)) {
            return false;
        }
        if (authRoutePermissionResolver.supportsAdminMenuCode(text)) {
            return true;
        }
        if (!text.startsWith("/")) {
            return false;
        }
        if (text.length() > 120) {
            return false;
        }
        for (int i = 0; i < text.length(); i += 1) {
            char c = text.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '/'
                    || c == '-'
                    || c == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private List<String> defaultMenuPermissions() {
        List<String> defaults = authRoutePermissionResolver.defaultAdminMenuCodes();
        if (defaults == null || defaults.isEmpty()) {
            return Collections.singletonList(AuthRoutePermissionResolver.MENU_DASHBOARD);
        }
        return defaults;
    }
}
