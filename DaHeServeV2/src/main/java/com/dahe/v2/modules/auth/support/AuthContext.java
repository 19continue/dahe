package com.dahe.v2.modules.auth.support;

import com.dahe.v2.modules.auth.domain.AuthDomainConstants;
import com.dahe.v2.modules.user.model.AppUser;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import javax.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

    /**
 * 鉴权请求上下文工具。
 *
 * <p>当前项目的主鉴权模型：</p>
 * <p>1. 后台用户：userType=admin，权限由“角色 -> 菜单权限”驱动；</p>
 * <p>2. 小程序用户：userType=miniapp，不参与后台角色授权，仅依据审核状态与 canConsole；</p>
 * <p>3. 控制器层优先使用菜单权限注解/菜单权限判断，不再新增固定角色码判断。</p>
 */
public final class AuthContext {

    /**
     * 当前请求绑定的登录用户对象（由 {@link ApiAuthInterceptor} 在鉴权成功后写入）。
     */
    public static final String ATTR_CURRENT_USER = "dahe.v2.currentUser";
    /**
     * 当前请求生效的角色编码。
     * 对后台用户来说，该值用于表达“本次请求最终生效的角色”，避免后续业务直接依赖原始入库字段。
     */
    public static final String ATTR_EFFECTIVE_ROLE_CODE = "dahe.v2.currentEffectiveRoleCode";
    /**
     * 当前请求允许访问的菜单权限列表。
     * 该信息由角色系统解析后写入，供后续控制层按需读取。
     */
    public static final String ATTR_MENU_PERMISSIONS = "dahe.v2.currentMenuPermissions";

    private AuthContext() {
    }

    /**
     * 统一解析访问令牌，兼容两种请求头：
     * 1. Authorization: Bearer xxx
     * 2. X-Access-Token: xxx
     * 3. multipart/form-data 中的 accessToken（用于小程序上传兜底）
     */
    public static String resolveToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String token = request.getHeader("Authorization");
        if (!StringUtils.hasText(token)) {
            token = request.getHeader("X-Access-Token");
        }
        if (!StringUtils.hasText(token)) {
            token = request.getParameter("accessToken");
        }
        if (!StringUtils.hasText(token)) {
            return null;
        }
        token = token.trim();
        if (token.toLowerCase().startsWith("bearer ")) {
            token = token.substring(7).trim();
        }
        return token;
    }

    public static void bindUser(HttpServletRequest request, AppUser user) {
        if (request == null) {
            return;
        }
        request.setAttribute(ATTR_CURRENT_USER, user);
    }

    public static AppUser getCurrentUser(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object attr = request.getAttribute(ATTR_CURRENT_USER);
        if (attr instanceof AppUser) {
            return (AppUser) attr;
        }
        return null;
    }

    /**
     * 绑定当前请求的生效角色。空值会主动清除属性，避免脏数据串用。
     */
    public static void bindEffectiveRoleCode(HttpServletRequest request, String roleCode) {
        if (request == null) {
            return;
        }
        if (!StringUtils.hasText(roleCode)) {
            request.removeAttribute(ATTR_EFFECTIVE_ROLE_CODE);
            return;
        }
        request.setAttribute(ATTR_EFFECTIVE_ROLE_CODE, roleCode.trim().toLowerCase());
    }

    public static String getEffectiveRoleCode(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object attr = request.getAttribute(ATTR_EFFECTIVE_ROLE_CODE);
        if (attr == null) {
            return null;
        }
        String role = String.valueOf(attr).trim().toLowerCase();
        return StringUtils.hasText(role) ? role : null;
    }

    /**
     * 绑定菜单权限列表，并对空白权限项进行过滤。
     */
    public static void bindMenuPermissions(HttpServletRequest request, List<String> menuPermissions) {
        if (request == null) {
            return;
        }
        if (menuPermissions == null || menuPermissions.isEmpty()) {
            request.removeAttribute(ATTR_MENU_PERMISSIONS);
            return;
        }
        List<String> out = new ArrayList<>();
        for (String item : menuPermissions) {
            String key = String.valueOf(item == null ? "" : item).trim();
            if (StringUtils.hasText(key)) {
                out.add(key);
            }
        }
        if (out.isEmpty()) {
            request.removeAttribute(ATTR_MENU_PERMISSIONS);
            return;
        }
        request.setAttribute(ATTR_MENU_PERMISSIONS, out);
    }

    @SuppressWarnings("unchecked")
    public static List<String> getMenuPermissions(HttpServletRequest request) {
        if (request == null) {
            return Collections.emptyList();
        }
        Object attr = request.getAttribute(ATTR_MENU_PERMISSIONS);
        if (!(attr instanceof List)) {
            return Collections.emptyList();
        }
        List<?> rows = (List<?>) attr;
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (Object row : rows) {
            String key = String.valueOf(row == null ? "" : row).trim();
            if (StringUtils.hasText(key)) {
                out.add(key);
            }
        }
        return out;
    }

    /**
     * 判断当前请求是否具备任一目标菜单权限。
     *
     * <p>约束：</p>
     * <p>1. 仅后台用户参与菜单权限判断；</p>
     * <p>2. 超级管理员默认放行；</p>
     * <p>3. 目标权限为空时返回 false。</p>
     */
    public static boolean hasAnyMenuPermission(HttpServletRequest request, String... menuKeys) {
        if (menuKeys == null || menuKeys.length == 0) {
            return false;
        }
        AppUser user = getCurrentUser(request);
        if (!isAdminUser(user)) {
            return false;
        }
        if (user != null && user.getIsSuperAdmin() != null && user.getIsSuperAdmin() == 1) {
            return true;
        }
        Set<String> expected = new HashSet<>();
        for (String key : menuKeys) {
            String normalized = String.valueOf(key == null ? "" : key).trim();
            if (StringUtils.hasText(normalized)) {
                expected.add(normalized);
            }
        }
        if (expected.isEmpty()) {
            return false;
        }
        List<String> granted = getMenuPermissions(request);
        if (granted == null || granted.isEmpty()) {
            return false;
        }
        for (String item : granted) {
            String key = String.valueOf(item == null ? "" : item).trim();
            if (StringUtils.hasText(key) && expected.contains(key)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAdminUser(AppUser user) {
        if (user == null) {
            return false;
        }
        String userType = String.valueOf(user.getUserType() == null ? "" : user.getUserType()).trim().toLowerCase(Locale.ROOT);
        return AuthDomainConstants.USER_TYPE_ADMIN.equals(userType);
    }
}
