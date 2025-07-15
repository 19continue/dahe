package com.dahe.v2.modules.auth.role.service.impl;

import com.dahe.v2.modules.auth.support.AuthRoutePermissionResolver;
import com.dahe.v2.modules.auth.role.model.AdminRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

class AdminRoleServiceImplTest {

    @Test
    void wouldCreateInheritanceCycle_shouldReturnTrue_whenParentChainContainsSelf() {
        AdminRoleServiceImpl service = Mockito.spy(new AdminRoleServiceImpl(new ObjectMapper(), buildResolverMock()));

        AdminRole lead = new AdminRole();
        lead.setRoleCode("lead");
        lead.setInheritRoleCode("manager");

        AdminRole manager = new AdminRole();
        manager.setRoleCode("manager");
        manager.setInheritRoleCode("director");

        AdminRole director = new AdminRole();
        director.setRoleCode("director");
        director.setInheritRoleCode("lead");

        doReturn(lead).when(service).findByRoleCode(eq("lead"), anyBoolean());
        doReturn(manager).when(service).findByRoleCode(eq("manager"), anyBoolean());
        doReturn(director).when(service).findByRoleCode(eq("director"), anyBoolean());

        boolean cycle = service.wouldCreateInheritanceCycle("lead", "manager");
        Assertions.assertTrue(cycle);
    }

    @Test
    void resolveMenuPermissions_shouldMergeInheritedAndSelfPermissions() {
        AdminRoleServiceImpl service = Mockito.spy(new AdminRoleServiceImpl(new ObjectMapper(), buildResolverMock()));

        AdminRole child = new AdminRole();
        child.setRoleCode("child");
        child.setInheritRoleCode("parent");
        child.setMenuPermissionsJson("[\"/users\"]");

        AdminRole parent = new AdminRole();
        parent.setRoleCode("parent");
        parent.setInheritRoleCode(null);
        parent.setMenuPermissionsJson("[\"/roles\"]");

        doReturn(child).when(service).findByRoleCode(eq("child"), anyBoolean());
        doReturn(parent).when(service).findByRoleCode(eq("parent"), anyBoolean());

        List<String> permissions = service.resolveMenuPermissions("child");
        Assertions.assertTrue(permissions.contains("/users"));
        Assertions.assertTrue(permissions.contains("/roles"));
        Assertions.assertTrue(permissions.contains("/dashboard"));
    }

    @Test
    void toMenuPermissionsJson_shouldKeepCustomPermissionKey() {
        AdminRoleServiceImpl service = new AdminRoleServiceImpl(new ObjectMapper(), buildResolverMock());
        String json = service.toMenuPermissionsJson(java.util.Arrays.asList("/report-center"), null);
        List<String> permissions = service.fromMenuPermissionsJson(json, null);

        Assertions.assertTrue(permissions.contains("/report-center"));
        Assertions.assertTrue(permissions.contains("/dashboard"));
    }

    private AuthRoutePermissionResolver buildResolverMock() {
        AuthRoutePermissionResolver resolver = Mockito.mock(AuthRoutePermissionResolver.class);
        Mockito.when(resolver.defaultAdminMenuCodes()).thenReturn(java.util.Collections.singletonList("/dashboard"));
        Mockito.when(resolver.supportsAdminMenuCode(Mockito.anyString())).thenReturn(false);
        return resolver;
    }
}
