package com.dahe.v2.modules.auth.support;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class AuthRoutePermissionResolverTest {

    @Test
    void listAdminMenuCodes_shouldUseRouteScanAndSkipAdminAuthPrefix() throws Exception {
        AuthRoutePermissionResolver resolver = new AuthRoutePermissionResolver(mockHandlerMappingProvider());
        resolver.reload();

        List<String> codes = resolver.listAdminMenuCodes();
        Assertions.assertTrue(codes.contains("/dashboard"));
        Assertions.assertTrue(codes.contains("/roles"));
        Assertions.assertTrue(codes.contains("/farm-step-dynamic-configs"));
        Assertions.assertFalse(codes.contains("/auth"));
    }

    @Test
    void resolveAdminPermissions_shouldPreferAdminMenuCodeAnnotation() throws Exception {
        AuthRoutePermissionResolver resolver = new AuthRoutePermissionResolver(mockHandlerMappingProvider());
        resolver.reload();

        List<String> permissions = resolver.resolveAdminPermissions("POST", "/api/v2/admin/dynamic-configs");
        Assertions.assertEquals(1, permissions.size());
        Assertions.assertEquals("/farm-step-dynamic-configs", permissions.get(0));
    }

    @Test
    void resolveAdminPermissions_shouldUseFieldManageForAmapRegionRoute() throws Exception {
        AuthRoutePermissionResolver resolver = new AuthRoutePermissionResolver(mockHandlerMappingProvider());
        resolver.reload();

        List<String> permissions = resolver.resolveAdminPermissions("GET", "/api/v2/admin/amap/regions/provinces");
        Assertions.assertEquals(1, permissions.size());
        Assertions.assertEquals("/field-manage", permissions.get(0));
    }

    private RequestMappingHandlerMapping mockHandlerMapping() throws Exception {
        RequestMappingHandlerMapping mapping = Mockito.mock(RequestMappingHandlerMapping.class);
        DummyController bean = new DummyController();
        Map<RequestMappingInfo, HandlerMethod> out = new LinkedHashMap<RequestMappingInfo, HandlerMethod>();

        Method roles = DummyController.class.getDeclaredMethod("roles");
        out.put(
                RequestMappingInfo.paths("/api/v2/admin/roles").methods(RequestMethod.GET).build(),
                new HandlerMethod(bean, roles)
        );

        Method dynamic = DummyController.class.getDeclaredMethod("dynamicCreate");
        out.put(
                RequestMappingInfo.paths("/api/v2/admin/dynamic-configs").methods(RequestMethod.POST).build(),
                new HandlerMethod(bean, dynamic)
        );

        Method adminAuth = DummyController.class.getDeclaredMethod("adminAuthMe");
        out.put(
                RequestMappingInfo.paths("/api/v2/admin/auth/me").methods(RequestMethod.GET).build(),
                new HandlerMethod(bean, adminAuth)
        );

        Method amapRegions = DummyController.class.getDeclaredMethod("amapRegionProvince");
        out.put(
                RequestMappingInfo.paths("/api/v2/admin/amap/regions/provinces").methods(RequestMethod.GET).build(),
                new HandlerMethod(bean, amapRegions)
        );

        Mockito.when(mapping.getHandlerMethods()).thenReturn(out);
        return mapping;
    }

    private ObjectProvider<RequestMappingHandlerMapping> mockHandlerMappingProvider() throws Exception {
        @SuppressWarnings("unchecked")
        ObjectProvider<RequestMappingHandlerMapping> provider = Mockito.mock(ObjectProvider.class);
        RequestMappingHandlerMapping mapping = mockHandlerMapping();
        Mockito.when(provider.getIfAvailable()).thenReturn(mapping);
        return provider;
    }

    private static class DummyController {
        @GetMapping("/api/v2/admin/roles")
        public void roles() {
        }

        @PostMapping("/api/v2/admin/dynamic-configs")
        @AdminMenuCode("/farm-step-dynamic-configs")
        public void dynamicCreate() {
        }

        @GetMapping("/api/v2/admin/auth/me")
        public void adminAuthMe() {
        }

        @GetMapping("/api/v2/admin/amap/regions/provinces")
        @AdminMenuCode("/field-manage")
        public void amapRegionProvince() {
        }
    }
}
