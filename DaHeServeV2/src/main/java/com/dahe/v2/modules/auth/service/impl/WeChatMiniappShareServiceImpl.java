package com.dahe.v2.modules.auth.service.impl;

import com.dahe.v2.modules.auth.config.AuthProperties;
import com.dahe.v2.modules.auth.service.WeChatMiniappShareService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class WeChatMiniappShareServiceImpl implements WeChatMiniappShareService {

    private static final Logger log = LoggerFactory.getLogger(WeChatMiniappShareServiceImpl.class);
    private static final long ACCESS_TOKEN_SKEW_MILLIS = Duration.ofMinutes(5).toMillis();
    private static final long QR_CODE_TTL_MILLIS = Duration.ofMinutes(10).toMillis();
    private static final int CONNECT_TIMEOUT_MS = 2500;
    private static final int READ_TIMEOUT_MS = 5000;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final boolean wechatEnabled;
    private final String appId;
    private final String appSecret;
    private final String accessTokenUrl;
    private final String wxacodeUrl;
    private final AuthProperties.MiniappShare shareConfig;
    private final Object accessTokenLock = new Object();
    private final Object qrCodeLock = new Object();

    private volatile String cachedAccessToken;
    private volatile long cachedAccessTokenExpireAt;
    private volatile String cachedQrCodeBase64;
    private volatile long cachedQrCodeExpireAt;

    public WeChatMiniappShareServiceImpl(AuthProperties authProperties) {
        AuthProperties.Wechat wechat = authProperties == null ? null : authProperties.getWechat();
        this.wechatEnabled = wechat != null && wechat.isEnabled();
        this.appId = String.valueOf(wechat == null || wechat.getAppId() == null ? "" : wechat.getAppId()).trim();
        this.appSecret = String.valueOf(wechat == null || wechat.getAppSecret() == null ? "" : wechat.getAppSecret()).trim();
        this.accessTokenUrl = String.valueOf(wechat == null || wechat.getAccessTokenUrl() == null ? "" : wechat.getAccessTokenUrl()).trim();
        this.wxacodeUrl = String.valueOf(wechat == null || wechat.getWxacodeUrl() == null ? "" : wechat.getWxacodeUrl()).trim();
        this.shareConfig = authProperties == null ? new AuthProperties.MiniappShare() : authProperties.getMiniappShare();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public ShareConfigPayload getShareConfig() {
        ShareConfigPayload out = new ShareConfigPayload();
        out.setTitle(readShareTitle());
        out.setPath(readSharePath());
        out.setImageUrl(readShareImageUrl());
        return out;
    }

    @Override
    public ShareQrCodePayload getShareQrCode() {
        ShareQrCodePayload out = new ShareQrCodePayload();
        out.setTitle(readShareTitle());
        out.setPath(readSharePath());
        out.setImageUrl(readShareImageUrl());
        out.setMimeType("image/png");
        out.setBase64(resolveShareQrCodeBase64());
        return out;
    }

    private String resolveShareQrCodeBase64() {
        long now = System.currentTimeMillis();
        String cached = cachedQrCodeBase64;
        if (StringUtils.hasText(cached) && now < cachedQrCodeExpireAt) {
            return cached;
        }
        synchronized (qrCodeLock) {
            now = System.currentTimeMillis();
            cached = cachedQrCodeBase64;
            if (StringUtils.hasText(cached) && now < cachedQrCodeExpireAt) {
                return cached;
            }
            String accessToken = resolveAccessToken();
            byte[] imageBytes = requestWxacodeBytes(accessToken);
            String next = Base64.getEncoder().encodeToString(imageBytes);
            cachedQrCodeBase64 = next;
            cachedQrCodeExpireAt = now + QR_CODE_TTL_MILLIS;
            return next;
        }
    }

    private String resolveAccessToken() {
        long now = System.currentTimeMillis();
        String cached = cachedAccessToken;
        if (StringUtils.hasText(cached) && now < cachedAccessTokenExpireAt) {
            return cached;
        }
        synchronized (accessTokenLock) {
            now = System.currentTimeMillis();
            cached = cachedAccessToken;
            if (StringUtils.hasText(cached) && now < cachedAccessTokenExpireAt) {
                return cached;
            }
            ensureWechatConfigured();
            try {
                URI uri = UriComponentsBuilder.fromHttpUrl(accessTokenUrl)
                        .queryParam("grant_type", "client_credential")
                        .queryParam("appid", appId)
                        .queryParam("secret", appSecret)
                        .build(true)
                        .toUri();
                ResponseEntity<Map> response = restTemplate.getForEntity(uri, Map.class);
                Map body = response == null ? null : response.getBody();
                if (body == null || body.isEmpty()) {
                    throw new IllegalStateException("微信 access_token 返回为空");
                }
                Object errCode = body.get("errcode");
                if (errCode != null && !"0".equals(String.valueOf(errCode))) {
                    throw new IllegalStateException("微信 access_token 获取失败：" + String.valueOf(body.get("errmsg")));
                }
                String accessToken = String.valueOf(body.get("access_token") == null ? "" : body.get("access_token")).trim();
                if (!StringUtils.hasText(accessToken)) {
                    throw new IllegalStateException("微信 access_token 返回为空");
                }
                long expiresInSeconds = 7200L;
                Object expiresIn = body.get("expires_in");
                if (expiresIn instanceof Number) {
                    expiresInSeconds = Math.max(((Number) expiresIn).longValue(), 600L);
                }
                cachedAccessToken = accessToken;
                cachedAccessTokenExpireAt = now + Math.max(1000L, expiresInSeconds * 1000L - ACCESS_TOKEN_SKEW_MILLIS);
                return accessToken;
            } catch (IllegalStateException ex) {
                throw ex;
            } catch (Exception ex) {
                log.warn("Resolve miniapp share access token failed, err={}", String.valueOf(ex.getMessage()));
                throw new IllegalStateException("微信分享配置不可用，请稍后重试");
            }
        }
    }

    private byte[] requestWxacodeBytes(String accessToken) {
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(wxacodeUrl)
                    .queryParam("access_token", accessToken)
                    .build(true)
                    .toUri();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("scene", readQrScene());
            payload.put("page", readQrPage());
            payload.put("check_path", false);
            payload.put("env_version", "release");
            payload.put("width", 430);
            payload.put("auto_color", true);
            ResponseEntity<byte[]> response = restTemplate.postForEntity(uri, new HttpEntity<>(payload, headers), byte[].class);
            byte[] body = response == null ? null : response.getBody();
            if (body == null || body.length == 0) {
                throw new IllegalStateException("微信小程序码返回为空");
            }
            if (looksLikeJson(body)) {
                Map<String, Object> errorBody = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
                Object errCode = errorBody.get("errcode");
                if (errCode != null && !"0".equals(String.valueOf(errCode))) {
                    throw new IllegalStateException("微信小程序码生成失败：" + String.valueOf(errorBody.get("errmsg")));
                }
            }
            return body;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Generate miniapp share qrcode failed, err={}", String.valueOf(ex.getMessage()));
            throw new IllegalStateException("小程序码生成失败，请稍后重试");
        }
    }

    private boolean looksLikeJson(byte[] body) {
        if (body == null || body.length == 0) {
            return false;
        }
        String text = new String(body, StandardCharsets.UTF_8).trim();
        return text.startsWith("{") && text.endsWith("}");
    }

    private void ensureWechatConfigured() {
        if (!wechatEnabled) {
            throw new IllegalStateException("微信分享配置未启用");
        }
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(appSecret)) {
            throw new IllegalStateException("微信分享配置未完成");
        }
        if (!StringUtils.hasText(accessTokenUrl) || !StringUtils.hasText(wxacodeUrl)) {
            throw new IllegalStateException("微信分享接口地址未配置");
        }
    }

    private String readShareTitle() {
        String value = shareConfig == null ? null : shareConfig.getTitle();
        return StringUtils.hasText(value) ? value.trim() : "大禾种业";
    }

    private String readSharePath() {
        String value = shareConfig == null ? null : shareConfig.getPath();
        return StringUtils.hasText(value) ? value.trim() : "/pages/auth/login?entry=share";
    }

    private String readShareImageUrl() {
        String value = shareConfig == null ? null : shareConfig.getImageUrl();
        return StringUtils.hasText(value) ? value.trim() : "/static/images/farm.png";
    }

    private String readQrPage() {
        String value = shareConfig == null ? null : shareConfig.getQrPage();
        return StringUtils.hasText(value) ? value.trim() : "pages/auth/login";
    }

    private String readQrScene() {
        String value = shareConfig == null ? null : shareConfig.getQrScene();
        return StringUtils.hasText(value) ? value.trim() : "entry=share";
    }
}
