package com.dahe.v2.modules.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 认证模块配置聚合。
 *
 * <p>将散落在各服务中的 @Value 配置收敛，便于集中维护与后续扩展。</p>
 */
@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /**
     * 小程序允许登录的业务场景，逗号分隔。
     */
    private String miniappLoginScenes = "field_record,seed_test,asset_upload,trace_query,task_center";

    /**
     * 当微信 code2Session 失败时，是否允许回退到 mock openId。
     */
    private boolean allowMockOpenIdFallback = true;

    /**
     * 用于识别“看起来像后台账号”的 openId 前缀。
     */
    private List<String> adminOpenIdDetectionPrefixes = Arrays.asList("mock_admin_", "mock_supervisor_", "admin_");

    /**
     * 后台账号自动生成 openId 的前缀。
     */
    private String adminOpenIdGeneratePrefix = "admin_";

    private Wechat wechat = new Wechat();
    private MiniappShare miniappShare = new MiniappShare();

    public String getMiniappLoginScenes() {
        return miniappLoginScenes;
    }

    public void setMiniappLoginScenes(String miniappLoginScenes) {
        this.miniappLoginScenes = miniappLoginScenes;
    }

    public boolean isAllowMockOpenIdFallback() {
        return allowMockOpenIdFallback;
    }

    public void setAllowMockOpenIdFallback(boolean allowMockOpenIdFallback) {
        this.allowMockOpenIdFallback = allowMockOpenIdFallback;
    }

    public List<String> getAdminOpenIdDetectionPrefixes() {
        return adminOpenIdDetectionPrefixes;
    }

    public void setAdminOpenIdDetectionPrefixes(List<String> adminOpenIdDetectionPrefixes) {
        this.adminOpenIdDetectionPrefixes = adminOpenIdDetectionPrefixes;
    }

    public String getAdminOpenIdGeneratePrefix() {
        return adminOpenIdGeneratePrefix;
    }

    public void setAdminOpenIdGeneratePrefix(String adminOpenIdGeneratePrefix) {
        this.adminOpenIdGeneratePrefix = adminOpenIdGeneratePrefix;
    }

    public Wechat getWechat() {
        return wechat;
    }

    public void setWechat(Wechat wechat) {
        this.wechat = wechat;
    }

    public MiniappShare getMiniappShare() {
        return miniappShare;
    }

    public void setMiniappShare(MiniappShare miniappShare) {
        this.miniappShare = miniappShare;
    }

    public static class Wechat {

        private boolean enabled = false;
        private String appId;
        private String appSecret;
        private String code2sessionUrl = "https://api.weixin.qq.com/sns/jscode2session";
        private String accessTokenUrl = "https://api.weixin.qq.com/cgi-bin/token";
        private String wxacodeUrl = "https://api.weixin.qq.com/wxa/getwxacodeunlimit";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getAppSecret() {
            return appSecret;
        }

        public void setAppSecret(String appSecret) {
            this.appSecret = appSecret;
        }

        public String getCode2sessionUrl() {
            return code2sessionUrl;
        }

        public void setCode2sessionUrl(String code2sessionUrl) {
            this.code2sessionUrl = code2sessionUrl;
        }

        public String getAccessTokenUrl() {
            return accessTokenUrl;
        }

        public void setAccessTokenUrl(String accessTokenUrl) {
            this.accessTokenUrl = accessTokenUrl;
        }

        public String getWxacodeUrl() {
            return wxacodeUrl;
        }

        public void setWxacodeUrl(String wxacodeUrl) {
            this.wxacodeUrl = wxacodeUrl;
        }
    }

    public static class MiniappShare {
        private String title = "大禾种业";
        private String path = "/pages/auth/login?entry=share";
        private String imageUrl = "/static/images/farm.png";
        private String qrPage = "pages/auth/login";
        private String qrScene = "entry=share";

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public void setImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
        }

        public String getQrPage() {
            return qrPage;
        }

        public void setQrPage(String qrPage) {
            this.qrPage = qrPage;
        }

        public String getQrScene() {
            return qrScene;
        }

        public void setQrScene(String qrScene) {
            this.qrScene = qrScene;
        }
    }
}
