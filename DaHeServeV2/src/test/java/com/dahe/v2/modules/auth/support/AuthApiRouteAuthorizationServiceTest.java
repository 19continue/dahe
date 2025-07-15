package com.dahe.v2.modules.auth.support;

import com.dahe.v2.modules.user.model.AppUser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;

class AuthApiRouteAuthorizationServiceTest {

    private final AuthRoutePermissionResolver authRoutePermissionResolver = buildResolverMock();
    private final AuthApiRouteAuthorizationService service =
            new AuthApiRouteAuthorizationService(authRoutePermissionResolver);

    private AuthRoutePermissionResolver buildResolverMock() {
        AuthRoutePermissionResolver resolver = Mockito.mock(AuthRoutePermissionResolver.class);
        Mockito.when(resolver.resolveAdminPermissions(Mockito.anyString(), Mockito.eq("/api/v2/admin/roles")))
                .thenReturn(Collections.singletonList("/roles"));
        Mockito.when(resolver.resolveAdminPermissions(Mockito.anyString(), Mockito.eq("/api/v2/admin/not-exists-module/list")))
                .thenReturn(Collections.singletonList("/not-exists-module"));
        Mockito.when(resolver.resolveAdminPermissions(Mockito.anyString(), Mockito.eq("/api/v2/admin/amap/regions/provinces")))
                .thenReturn(Collections.singletonList("/field-manage"));
        Mockito.when(resolver.resolveSharedWritePermissions(Mockito.anyString(), Mockito.eq("/api/v2/no-rule-module/save")))
                .thenReturn(Collections.singletonList("/no-rule-module"));
        return resolver;
    }

    @Test
    void adminRoute_shouldPassWhenMenuPermissionMatched() {
        AppUser user = new AppUser();
        user.setUserType("admin");
        user.setIsSuperAdmin(0);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/admin/roles");
        AuthContext.bindUser(request, user);
        AuthContext.bindMenuPermissions(request, Collections.singletonList("/roles"));

        String denied = service.authorize(request, user);
        Assertions.assertNull(denied);
    }

    @Test
    void adminRoute_shouldRejectWhenUserLacksInferredPermission() {
        AppUser user = new AppUser();
        user.setUserType("admin");
        user.setIsSuperAdmin(0);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/admin/not-exists-module/list");
        AuthContext.bindUser(request, user);
        AuthContext.bindMenuPermissions(request, Collections.singletonList("/roles"));

        String denied = service.authorize(request, user);
        Assertions.assertTrue(denied != null && denied.contains("缺少接口访问权限"));
    }

    @Test
    void amapRegionRoute_shouldRejectWhenUserOnlyHasAmapAuditMenu() {
        AppUser user = new AppUser();
        user.setUserType("admin");
        user.setIsSuperAdmin(0);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/admin/amap/regions/provinces");
        AuthContext.bindUser(request, user);
        AuthContext.bindMenuPermissions(request, Collections.singletonList("/amap-audit"));

        String denied = service.authorize(request, user);
        Assertions.assertTrue(denied != null && denied.contains("缺少接口访问权限"));
    }

    @Test
    void sharedUpload_shouldRejectMiniappUserAfterSharedApiOffline() {
        AppUser user = new AppUser();
        user.setUserType("miniapp");
        user.setCanConsole(0);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v2/files/upload");
        AuthContext.bindUser(request, user);
        AuthContext.bindMenuPermissions(request, null);

        String denied = service.authorize(request, user);
        Assertions.assertTrue(denied != null && denied.contains("共享接口已下线"));
    }

    @Test
    void sharedUpload_shouldAllowAdminWhenPermissionMatched() {
        AppUser user = new AppUser();
        user.setUserType("admin");
        user.setIsSuperAdmin(0);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v2/files/upload");
        AuthContext.bindUser(request, user);
        AuthContext.bindMenuPermissions(request, Collections.singletonList("/assets"));

        String denied = service.authorize(request, user);
        Assertions.assertNull(denied);
    }

    @Test
    void sharedWrite_shouldRejectWhenUserLacksInferredPermission() {
        AppUser user = new AppUser();
        user.setUserType("admin");
        user.setIsSuperAdmin(0);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v2/no-rule-module/save");
        AuthContext.bindUser(request, user);
        AuthContext.bindMenuPermissions(request, Collections.singletonList("/roles"));

        String denied = service.authorize(request, user);
        Assertions.assertTrue(denied != null && denied.contains("缺少接口访问权限"));
    }

    @Test
    void adminRoute_shouldAllowSuperAdminWhenMappingMissing() {
        AppUser user = new AppUser();
        user.setUserType("admin");
        user.setIsSuperAdmin(1);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/admin/not-exists-module/list");
        AuthContext.bindUser(request, user);
        AuthContext.bindMenuPermissions(request, Collections.emptyList());

        String denied = service.authorize(request, user);
        Assertions.assertNull(denied);
    }

    @Test
    void sharedWrite_shouldAllowSuperAdminWhenNoStrategyConfigured() {
        AppUser user = new AppUser();
        user.setUserType("admin");
        user.setIsSuperAdmin(1);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v2/no-rule-module/save");
        AuthContext.bindUser(request, user);
        AuthContext.bindMenuPermissions(request, Collections.emptyList());

        String denied = service.authorize(request, user);
        Assertions.assertNull(denied);
    }
}
