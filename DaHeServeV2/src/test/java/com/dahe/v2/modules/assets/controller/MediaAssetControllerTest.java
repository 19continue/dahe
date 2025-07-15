package com.dahe.v2.modules.assets.controller;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

class MediaAssetControllerTest {

    @Test
    void buildPublicUrl_shouldAppendForwardedPortWhenForwardedHostHasNoPort() throws Exception {
        MediaAssetController controller = new MediaAssetController(null, null, null, null, null, null, null, null, null, null,null,null,null);
        ReflectionTestUtils.setField(controller, "uploadPublicBaseUrl", "");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("127.0.0.1");
        request.setServerPort(3100);
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "files.example.com");
        request.addHeader("X-Forwarded-Port", "8443");

        String url = invokeBuildPublicUrl(controller, request, "/api/v2/uploads/202602/demo.jpg");

        Assertions.assertEquals("https://files.example.com:8443/api/v2/uploads/202602/demo.jpg", url);
    }

    @Test
    void buildPublicUrl_shouldKeepForwardedHostPortWhenHostAlreadyContainsPort() throws Exception {
        MediaAssetController controller = new MediaAssetController(null, null, null, null, null, null, null, null, null, null,null,null,null);
        ReflectionTestUtils.setField(controller, "uploadPublicBaseUrl", "");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("127.0.0.1");
        request.setServerPort(3100);
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "files.example.com:9443");
        request.addHeader("X-Forwarded-Port", "8443");

        String url = invokeBuildPublicUrl(controller, request, "/api/v2/uploads/202602/demo.jpg");

        Assertions.assertEquals("https://files.example.com:9443/api/v2/uploads/202602/demo.jpg", url);
    }

    private String invokeBuildPublicUrl(MediaAssetController controller, HttpServletRequest request, String path) throws Exception {
        Method method = MediaAssetController.class.getDeclaredMethod("buildPublicUrl", HttpServletRequest.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(controller, request, path);
    }
}
