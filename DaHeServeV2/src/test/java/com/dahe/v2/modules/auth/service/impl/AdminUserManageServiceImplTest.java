package com.dahe.v2.modules.auth.service.impl;

import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.auth.domain.AuthDomainConstants;
import com.dahe.v2.modules.auth.dto.AdminUserManageDTO;
import com.dahe.v2.modules.auth.policy.AdminPasswordPolicy;
import com.dahe.v2.modules.auth.policy.AdminOpenIdPolicy;
import com.dahe.v2.modules.auth.policy.AuthUserPolicy;
import com.dahe.v2.modules.auth.role.model.AdminRole;
import com.dahe.v2.modules.auth.role.service.AdminRoleService;
import com.dahe.v2.modules.auth.service.UserNoticeService;
import com.dahe.v2.modules.miniapp.search.service.MiniappSearchIndexService;
import com.dahe.v2.modules.session.service.TokenSessionService;
import com.dahe.v2.modules.user.model.AppUser;
import com.dahe.v2.modules.user.service.AppUserService;
import com.dahe.v2.modules.user.service.UserDomainService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserManageServiceImplTest {

    @Mock
    private AppUserService appUserService;

    @Mock
    private AdminRoleService adminRoleService;

    @Mock
    private UserNoticeService userNoticeService;

    @Mock
    private AdminOpenIdPolicy adminOpenIdPolicy;

    @Mock
    private AdminPasswordPolicy adminPasswordPolicy;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenSessionService tokenSessionService;

    @Mock
    private UserDomainService userDomainService;

    @Mock
    private MiniappSearchIndexService miniappSearchIndexService;

    private AdminUserManageServiceImpl newService() {
        AuthUserPolicy authUserPolicy = new AuthUserPolicy(adminRoleService);
        return new AdminUserManageServiceImpl(
                appUserService,
                adminRoleService,
                userNoticeService,
                tokenSessionService,
                authUserPolicy,
                adminOpenIdPolicy,
                adminPasswordPolicy,
                passwordEncoder,
                userDomainService);
    }

    @Test
    void approve_shouldRejectAdminUser() {
        AdminUserManageServiceImpl service = newService();
        AppUser target = new AppUser();
        target.setId(101L);
        target.setUserType("admin");
        target.setRoleCode("supervisor");
        when(appUserService.getById(101L)).thenReturn(target);

        AdminUserManageDTO.ApproveReq req = new AdminUserManageDTO.ApproveReq();
        req.setApprove(true);
        req.setCanConsole(true);

        Result<AppUser> result = service.approve(101L, req);

        Assertions.assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), result.getCode());
        Assertions.assertEquals("仅小程序用户可审核", result.getMessage());
        verify(userDomainService, never()).applyMiniappReviewDecision(any(AppUser.class), anyBoolean(), any(), any());
    }

    @Test
    void updateEnabled_shouldRejectDisableForPendingMiniappUser() {
        AdminUserManageServiceImpl service = newService();
        AppUser target = new AppUser();
        target.setId(102L);
        target.setUserType("miniapp");
        target.setStatus("pending");
        target.setEnabled(1);
        when(appUserService.getById(102L)).thenReturn(target);

        AdminUserManageDTO.EnabledReq req = new AdminUserManageDTO.EnabledReq();
        req.setEnabled(false);

        Result<AppUser> result = service.updateEnabled(102L, req);

        Assertions.assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), result.getCode());
        Assertions.assertEquals("仅已审核通过的小程序用户可禁用", result.getMessage());
        verify(userDomainService, never()).applyEnabled(any(AppUser.class), anyBoolean());
    }

    @Test
    void updateEnabled_shouldRejectDisableForSuperAdmin() {
        AdminUserManageServiceImpl service = newService();
        AppUser target = new AppUser();
        target.setId(103L);
        target.setUserType("admin");
        target.setStatus(null);
        target.setEnabled(1);
        target.setIsSuperAdmin(1);
        when(appUserService.getById(103L)).thenReturn(target);

        AdminUserManageDTO.EnabledReq req = new AdminUserManageDTO.EnabledReq();
        req.setEnabled(false);

        Result<AppUser> result = service.updateEnabled(103L, req);

        Assertions.assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), result.getCode());
        Assertions.assertEquals("超级管理员不可禁用", result.getMessage());
        verify(userDomainService, never()).applyEnabled(any(AppUser.class), anyBoolean());
    }

    @Test
    void createAdminUser_shouldPersistAdminBoundaryFields() {
        AdminUserManageServiceImpl service = newService();
        when(appUserService.findByWxOpenId("admin_test_1001")).thenReturn(null);
        when(adminPasswordPolicy.normalizeLoginName(anyString())).thenReturn("admin_test_1001");
        when(adminPasswordPolicy.isValidLoginName("admin_test_1001")).thenReturn(true);
        when(adminPasswordPolicy.isStrongPassword(anyString())).thenReturn(true);
        when(adminPasswordPolicy.encodePassword(anyString(), eq(passwordEncoder))).thenReturn("hashed_pwd");
        when(appUserService.findAdminByLoginName("admin_test_1001")).thenReturn(null);
        when(adminRoleService.normalizeRoleCode(anyString())).thenAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            return arg == null ? null : String.valueOf(arg).trim().toLowerCase();
        });
        AdminRole role = new AdminRole();
        role.setRoleCode("supervisor");
        role.setEnabled(1);
        when(adminRoleService.findByRoleCode("supervisor", false)).thenReturn(role);

        AdminUserManageDTO.AdminCreateReq req = new AdminUserManageDTO.AdminCreateReq();
        req.setLoginName("admin_test_1001");
        req.setPassword("Admin@12345");
        req.setRealName("测试管理员");
        req.setNickName("管理A");
        req.setPhone("13800001111");
        req.setRoleCode("supervisor");
        req.setAvatarUrl("https://example.com/admin.png");

        AppUser created = new AppUser();
        created.setId(5001L);
        created.setWxOpenId("admin_test_1001");
        created.setUserType("admin");
        created.setStatus(null);
        created.setRoleCode("supervisor");
        created.setCanConsole(1);
        created.setEnabled(1);
        created.setIsSuperAdmin(0);
        created.setAvatarSource("admin");
        when(userDomainService.createAdminUser(any(UserDomainService.AdminCreateCommand.class))).thenReturn(created);

        Result<AppUser> result = service.createAdminUser(req);

        Assertions.assertEquals(Result.SUCCESS_CODE, result.getCode());
        ArgumentCaptor<UserDomainService.AdminCreateCommand> captor = ArgumentCaptor.forClass(UserDomainService.AdminCreateCommand.class);
        verify(userDomainService).createAdminUser(captor.capture());
        UserDomainService.AdminCreateCommand saved = captor.getValue();
        Assertions.assertEquals("admin_test_1001", saved.getWxOpenId());
        Assertions.assertEquals("admin_test_1001", saved.getLoginName());
        Assertions.assertEquals("hashed_pwd", saved.getPasswordHash());
        Assertions.assertEquals("测试管理员", saved.getRealName());
        Assertions.assertEquals("管理A", saved.getNickName());
        Assertions.assertEquals("13800001111", saved.getPhone());
        Assertions.assertEquals("supervisor", saved.getRoleCode());
        Assertions.assertEquals(Boolean.TRUE, saved.getCanConsole());
        Assertions.assertEquals("https://example.com/admin.png", saved.getAvatarUrl());
        Assertions.assertEquals("admin", result.getData().getUserType());
    }

    @Test
    void updateEnabled_shouldUseUserDomainService() {
        AdminUserManageServiceImpl service = newService();
        AppUser target = new AppUser();
        target.setId(104L);
        target.setUserType("admin");
        target.setEnabled(1);
        when(appUserService.getById(104L)).thenReturn(target);
        when(userDomainService.applyEnabled(target, true)).thenReturn(target);

        AdminUserManageDTO.EnabledReq req = new AdminUserManageDTO.EnabledReq();
        req.setEnabled(true);
        Result<AppUser> result = service.updateEnabled(104L, req);

        Assertions.assertEquals(Result.SUCCESS_CODE, result.getCode());
        verify(userDomainService).applyEnabled(target, true);
    }

    @Test
    void updateRole_shouldLockSuperAdminToSuperAdminRole() {
        AdminUserManageServiceImpl service = newService();
        AppUser target = new AppUser();
        target.setId(105L);
        target.setUserType("admin");
        target.setIsSuperAdmin(1);
        target.setRoleCode("legacy_role");
        when(appUserService.getById(105L)).thenReturn(target);
        when(adminRoleService.normalizeRoleCode(anyString())).thenAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            return arg == null ? null : String.valueOf(arg).trim().toLowerCase();
        });
        AdminRole superRole = new AdminRole();
        superRole.setRoleCode(AuthDomainConstants.ROLE_CODE_SUPER_ADMIN);
        superRole.setEnabled(1);
        when(adminRoleService.findByRoleCode(AuthDomainConstants.ROLE_CODE_SUPER_ADMIN, false)).thenReturn(superRole);
        when(userDomainService.applyAdminRoleAndConsole(eq(target), eq(AuthDomainConstants.ROLE_CODE_SUPER_ADMIN), eq(Boolean.TRUE)))
                .thenReturn(target);

        AdminUserManageDTO.RoleReq req = new AdminUserManageDTO.RoleReq();
        req.setRoleCode(AuthDomainConstants.ROLE_CODE_SUPER_ADMIN);
        req.setCanConsole(true);

        Result<AppUser> result = service.updateRole(105L, req);
        Assertions.assertEquals(Result.SUCCESS_CODE, result.getCode());
        verify(userDomainService).applyAdminRoleAndConsole(target, AuthDomainConstants.ROLE_CODE_SUPER_ADMIN, true);
    }
}
