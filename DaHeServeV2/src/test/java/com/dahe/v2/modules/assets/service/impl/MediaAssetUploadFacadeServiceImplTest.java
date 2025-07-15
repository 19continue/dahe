package com.dahe.v2.modules.assets.service.impl;

import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.auth.support.AuthContext;
import com.dahe.v2.modules.assets.service.MediaAssetContentValidationService;
import com.dahe.v2.modules.assets.service.MediaAssetService;
import com.dahe.v2.modules.assets.policy.service.MediaAssetPolicyConfigService;
import com.dahe.v2.modules.user.model.AppUser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 上传门面权限分流回归测试。
 */
class MediaAssetUploadFacadeServiceImplTest {

    @Test
    void uploadForAdmin_shouldRejectMiniappUser() {
        MediaAssetUploadFacadeServiceImpl service = createService();
        MockHttpServletRequest request = new MockHttpServletRequest();
        AuthContext.bindUser(request, buildUser(1001L, "miniapp"));
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2, 3});

        Result<?> result = service.uploadForAdmin(request, file, "farm", 1L, "/默认", "a.png", null);

        Assertions.assertEquals(ErrorCode.UNAUTHORIZED.getCode(), result.getCode());
        Assertions.assertEquals("仅后台用户可通过该接口上传资源", result.getMessage());
    }

    @Test
    void uploadForMiniapp_shouldRejectAdminUser() {
        MediaAssetUploadFacadeServiceImpl service = createService();
        MockHttpServletRequest request = new MockHttpServletRequest();
        AuthContext.bindUser(request, buildUser(1002L, "admin"));
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2, 3});

        Result<?> result = service.uploadForMiniapp(request, file, "farm", 1L, "/默认", "a.png", null);

        Assertions.assertEquals(ErrorCode.UNAUTHORIZED.getCode(), result.getCode());
        Assertions.assertEquals("仅小程序用户可通过该接口上传资源", result.getMessage());
    }

    private MediaAssetUploadFacadeServiceImpl createService() {
        MediaAssetService mediaAssetService = Mockito.mock(MediaAssetService.class);
        MediaAssetPolicyConfigService policyConfigService = Mockito.mock(MediaAssetPolicyConfigService.class);
        MediaAssetContentValidationService contentValidationService = Mockito.mock(MediaAssetContentValidationService.class);
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        return new MediaAssetUploadFacadeServiceImpl(
                mediaAssetService,
                policyConfigService,
                contentValidationService,
                jdbcTemplate
        );
    }

    private AppUser buildUser(Long id, String userType) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUserType(userType);
        user.setNickName("tester");
        return user;
    }
}
