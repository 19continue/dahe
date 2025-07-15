package com.dahe.v2.modules.auth.policy;

import com.dahe.v2.modules.auth.role.model.AdminRole;
import com.dahe.v2.modules.auth.role.service.AdminRoleService;
import com.dahe.v2.modules.user.model.AppUser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthUserPolicyTest {

    @Mock
    private AdminRoleService adminRoleService;

    @Test
    void canIssueSession_shouldAllowApprovedMiniappUser() {
        AuthUserPolicy policy = new AuthUserPolicy(adminRoleService);
        AppUser user = new AppUser();
        user.setUserType("miniapp");
        user.setStatus("approved");
        user.setEnabled(1);

        Assertions.assertTrue(policy.canIssueSession(user));
    }

    @Test
    void canIssueSession_shouldRejectPendingMiniappUser() {
        AuthUserPolicy policy = new AuthUserPolicy(adminRoleService);
        AppUser user = new AppUser();
        user.setUserType("miniapp");
        user.setStatus("pending");
        user.setEnabled(1);

        Assertions.assertFalse(policy.canIssueSession(user));
        Assertions.assertEquals("账号未审核通过", policy.resolveSessionDeniedMessage(user));
    }

    @Test
    void canIssueSession_shouldAllowAdminWithValidRole() {
        AuthUserPolicy policy = new AuthUserPolicy(adminRoleService);
        AppUser user = new AppUser();
        user.setUserType("admin");
        user.setRoleCode("supervisor");
        user.setEnabled(1);

        AdminRole role = new AdminRole();
        role.setRoleCode("supervisor");
        when(adminRoleService.normalizeRoleCode(eq("supervisor"))).thenReturn("supervisor");
        when(adminRoleService.findByRoleCode(eq("supervisor"), anyBoolean())).thenReturn(role);

        Assertions.assertTrue(policy.canIssueSession(user));
    }

    @Test
    void canIssueSession_shouldAllowSuperAdminWhenRoleMissing() {
        AuthUserPolicy policy = new AuthUserPolicy(adminRoleService);
        AppUser user = new AppUser();
        user.setUserType("admin");
        user.setIsSuperAdmin(1);
        user.setEnabled(1);
        user.setRoleCode(null);

        Assertions.assertTrue(policy.canIssueSession(user));
        Assertions.assertNull(policy.validateAdminRole(user));
    }
}
