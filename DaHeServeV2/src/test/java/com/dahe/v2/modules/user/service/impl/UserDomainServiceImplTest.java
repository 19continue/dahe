package com.dahe.v2.modules.user.service.impl;

import com.dahe.v2.modules.assets.service.MediaAssetBindingService;
import com.dahe.v2.modules.miniapp.search.service.MiniappSearchIndexService;
import com.dahe.v2.modules.user.model.AppUser;
import com.dahe.v2.modules.user.service.AppUserService;
import com.dahe.v2.modules.user.service.UserDomainService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDomainServiceImplTest {

    @Mock
    private AppUserService appUserService;
    @Mock
    private MediaAssetBindingService mediaAssetBindingService;

    @Test
    void upsertMiniappUserFromLogin_shouldCreatePendingUser() {
        UserDomainServiceImpl service = new UserDomainServiceImpl(appUserService,mediaAssetBindingService);
        when(appUserService.findByWxOpenId("wx_u_1001")).thenReturn(null);

        UserDomainService.MiniappLoginCommand command = new UserDomainService.MiniappLoginCommand();
        command.setWxOpenId("wx_u_1001");
        command.setNickName("小张");
        command.setRealName("张三");
        command.setPhone("13800001111");
        command.setApplyReason("申请加入");
        command.setWxAvatarUrl("https://wx/avatar.png");

        UserDomainService.MiniappLoginUpsertResult result = service.upsertMiniappUserFromLogin(command);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isReviewApplied());
        Assertions.assertFalse(result.isReApply());
        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserService).save(captor.capture());
        AppUser created = captor.getValue();
        Assertions.assertEquals("wx_u_1001", created.getWxOpenId());
        Assertions.assertEquals("pending", created.getStatus());
        Assertions.assertNull(created.getRoleCode());
        Assertions.assertEquals("miniapp", created.getUserType());
        Assertions.assertEquals(Integer.valueOf(1), created.getEnabled());
    }

    @Test
    void upsertMiniappUserFromLogin_shouldResetRejectedToPending() {
        UserDomainServiceImpl service = new UserDomainServiceImpl(appUserService,mediaAssetBindingService);
        AppUser existed = new AppUser();
        existed.setId(2001L);
        existed.setWxOpenId("wx_u_2001");
        existed.setStatus("rejected");
        existed.setRejectReason("资料不完整");
        when(appUserService.findByWxOpenId("wx_u_2001")).thenReturn(existed);

        UserDomainService.MiniappLoginCommand command = new UserDomainService.MiniappLoginCommand();
        command.setWxOpenId("wx_u_2001");
        command.setApplyReason("补充后重试");

        UserDomainService.MiniappLoginUpsertResult result = service.upsertMiniappUserFromLogin(command);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isReviewApplied());
        Assertions.assertTrue(result.isReApply());
        verify(appUserService).updateById(existed);
        Assertions.assertEquals("pending", existed.getStatus());
        Assertions.assertNull(existed.getRejectReason());
    }

    @Test
    void applyMiniappReviewDecision_shouldPersistApprovedState() {
        UserDomainServiceImpl service = new UserDomainServiceImpl(appUserService,mediaAssetBindingService);
        AppUser target = new AppUser();
        target.setId(3001L);
        target.setStatus("pending");

        AppUser out = service.applyMiniappReviewDecision(target, true, true, null);

        verify(appUserService).updateById(target);
        Assertions.assertSame(target, out);
        Assertions.assertEquals("approved", out.getStatus());
        Assertions.assertNull(out.getRoleCode());
        Assertions.assertEquals(Integer.valueOf(1), out.getCanConsole());
    }

    @Test
    void createAdminUser_shouldApplyAdminDefaults() {
        UserDomainServiceImpl service = new UserDomainServiceImpl(appUserService,mediaAssetBindingService);
        when(appUserService.save(any(AppUser.class))).thenReturn(true);

        UserDomainService.AdminCreateCommand command = new UserDomainService.AdminCreateCommand();
        command.setWxOpenId("admin_u_4001");
        command.setLoginName("admin_u_4001");
        command.setPasswordHash("hashed_pwd");
        command.setRealName("管理员A");
        command.setNickName("AdminA");
        command.setPhone("13900001111");
        command.setRoleCode("manager");
        command.setCanConsole(true);
        command.setAvatarUrl("https://example.com/a.png");

        AppUser user = service.createAdminUser(command);

        verify(appUserService).save(user);
        Assertions.assertEquals("admin_u_4001", user.getWxOpenId());
        Assertions.assertEquals("admin_u_4001", user.getLoginName());
        Assertions.assertEquals("hashed_pwd", user.getPasswordHash());
        Assertions.assertEquals("admin", user.getUserType());
        Assertions.assertNull(user.getStatus());
        Assertions.assertEquals(Integer.valueOf(1), user.getEnabled());
        Assertions.assertEquals(Integer.valueOf(0), user.getIsSuperAdmin());
        Assertions.assertEquals("admin", user.getAvatarSource());
    }
}
