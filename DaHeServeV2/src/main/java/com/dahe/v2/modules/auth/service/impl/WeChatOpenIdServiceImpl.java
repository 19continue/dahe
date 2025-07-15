package com.dahe.v2.modules.auth.service.impl;

import com.dahe.v2.modules.auth.config.AuthProperties;
import com.dahe.v2.modules.auth.domain.AuthMessageCatalog;
import com.dahe.v2.modules.auth.service.WeChatOpenIdService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.SocketTimeoutException;

@Service
public class WeChatOpenIdServiceImpl implements WeChatOpenIdService {

    private static final Logger log = LoggerFactory.getLogger(WeChatOpenIdServiceImpl.class);
    private static final int CONNECT_TIMEOUT_MS = 2500;
    private static final int READ_TIMEOUT_MS = 3500;

    private final RestTemplate restTemplate;
    private final boolean wechatEnabled;
    private final String appId;
    private final String appSecret;
    private final String code2SessionUrl;
    private final ObjectMapper objectMapper;

    public WeChatOpenIdServiceImpl(AuthProperties authProperties, ObjectMapper objectMapper) {
        AuthProperties.Wechat wechat = authProperties == null ? null : authProperties.getWechat();
        this.wechatEnabled = wechat != null && wechat.isEnabled();
        this.appId = String.valueOf(wechat == null || wechat.getAppId() == null ? "" : wechat.getAppId()).trim();
        this.appSecret = String.valueOf(wechat == null || wechat.getAppSecret() == null ? "" : wechat.getAppSecret()).trim();
        this.code2SessionUrl = String.valueOf(wechat == null || wechat.getCode2sessionUrl() == null ? "" : wechat.getCode2sessionUrl()).trim();
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public ResolveResult resolve(String code) {
        String loginCode = String.valueOf(code == null ? "" : code).trim();
        if (!StringUtils.hasText(loginCode)) {
            return ResolveResult.failure(AuthMessageCatalog.MINIAPP_WECHAT_CODE_REQUIRED);
        }
        if (!wechatEnabled) {
            return ResolveResult.failure(AuthMessageCatalog.MINIAPP_WECHAT_DISABLED);
        }
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(appSecret) || !StringUtils.hasText(code2SessionUrl)) {
            log.warn("WeChat code2session skipped due to missing config");
            return ResolveResult.failure(AuthMessageCatalog.MINIAPP_WECHAT_CONFIG_MISSING);
        }
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(code2SessionUrl)
                    .queryParam("appid", appId)
                    .queryParam("secret", appSecret)
                    .queryParam("js_code", loginCode)
                    .queryParam("grant_type", "authorization_code")
                    .build(true)
                    .toUri();
            ResponseEntity<String> resp = restTemplate.getForEntity(uri, String.class);
            String bodyText = String.valueOf(resp == null || resp.getBody() == null ? "" : resp.getBody()).trim();
            if (!StringUtils.hasText(bodyText)) {
                log.warn("WeChat code2session returned empty body");
                return ResolveResult.failure(AuthMessageCatalog.MINIAPP_WECHAT_UNAVAILABLE);
            }
            JsonNode body = parseWechatResponse(bodyText);
            if (body == null || body.isMissingNode()) {
                log.warn("WeChat code2session returned non-json body, body={}", truncate(bodyText));
                return ResolveResult.failure(AuthMessageCatalog.MINIAPP_WECHAT_UNAVAILABLE);
            }
            JsonNode errCode = body.get("errcode");
            String errCodeText = errCode == null || errCode.isNull() ? "" : errCode.asText("").trim();
            if (StringUtils.hasText(errCodeText) && !"0".equals(errCodeText)) {
                String errMsg = body.path("errmsg").asText("").trim();
                log.warn("WeChat code2session failed, errcode={}, errmsg={}", errCodeText, errMsg);
                return ResolveResult.failure(resolveWechatErrorMessage(errCodeText));
            }
            String openId = body.path("openid").asText("").trim();
            if (!StringUtils.hasText(openId)) {
                log.warn("WeChat code2session succeeded without openid, body={}", truncate(bodyText));
                return ResolveResult.failure(AuthMessageCatalog.MINIAPP_WECHAT_UNAVAILABLE);
            }
            return ResolveResult.success(openId);
        } catch (ResourceAccessException e) {
            if (isTimeout(e)) {
                log.warn("WeChat code2session request timed out, err={}", String.valueOf(e.getMessage()));
                return ResolveResult.failure(AuthMessageCatalog.MINIAPP_WECHAT_TIMEOUT);
            }
            log.warn("WeChat code2session request unavailable, err={}", String.valueOf(e.getMessage()));
            return ResolveResult.failure(AuthMessageCatalog.MINIAPP_WECHAT_UNAVAILABLE);
        } catch (Exception e) {
            log.warn("WeChat code2session request failed, err={}", String.valueOf(e.getMessage()));
            return ResolveResult.failure(AuthMessageCatalog.MINIAPP_WECHAT_UNAVAILABLE);
        }
    }

    private JsonNode parseWechatResponse(String bodyText) {
        try {
            return objectMapper.readTree(bodyText);
        } catch (Exception e) {
            log.warn("WeChat code2session response parse failed, err={}", String.valueOf(e.getMessage()));
            return null;
        }
    }

    private String resolveWechatErrorMessage(String errCode) {
        if ("40029".equals(errCode)) {
            return AuthMessageCatalog.MINIAPP_WECHAT_CODE_INVALID;
        }
        if ("40163".equals(errCode)) {
            return AuthMessageCatalog.MINIAPP_WECHAT_CODE_USED;
        }
        if ("-1".equals(errCode)) {
            return AuthMessageCatalog.MINIAPP_WECHAT_BUSY;
        }
        return AuthMessageCatalog.MINIAPP_WECHAT_UNAVAILABLE;
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            String message = String.valueOf(current.getMessage()).toLowerCase();
            if (message.contains("timed out") || message.contains("timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String truncate(String text) {
        String value = String.valueOf(text == null ? "" : text).trim();
        if (value.length() <= 180) {
            return value;
        }
        return value.substring(0, 180) + "...";
    }
}
