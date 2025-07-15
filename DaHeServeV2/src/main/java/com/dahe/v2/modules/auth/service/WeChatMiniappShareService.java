package com.dahe.v2.modules.auth.service;

/**
 * 小程序分享能力服务。
 *
 * <p>职责：</p>
 * <p>1. 输出小程序卡片分享配置；</p>
 * <p>2. 生成并缓存默认小程序码，供前端展示与保存。</p>
 */
public interface WeChatMiniappShareService {

    ShareConfigPayload getShareConfig();

    ShareQrCodePayload getShareQrCode();

    class ShareConfigPayload {
        private String title;
        private String path;
        private String imageUrl;

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
    }

    class ShareQrCodePayload extends ShareConfigPayload {
        private String mimeType;
        private String base64;

        public String getMimeType() {
            return mimeType;
        }

        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }

        public String getBase64() {
            return base64;
        }

        public void setBase64(String base64) {
            this.base64 = base64;
        }
    }
}
