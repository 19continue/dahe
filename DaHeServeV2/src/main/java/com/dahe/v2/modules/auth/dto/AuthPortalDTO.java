package com.dahe.v2.modules.auth.dto;

import com.dahe.v2.modules.auth.domain.AuthDomainConstants;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.Map;

/**
 * 认证门户 DTO 定义。
 *
 * <p>将认证请求/响应结构从控制器中抽离，便于服务层复用与后续版本演进。</p>
 */
public final class AuthPortalDTO {

    private AuthPortalDTO() {
    }

    @Data
    public static class MiniappLoginReq {
        private String code;
        @NotBlank(message = "登录场景不能为空")
        private String loginScene;
        /** 设备上下文（前端采集，服务端会补齐 IP/UA）。 */
        private DeviceContext deviceContext;
        private String nickName;
        @NotBlank(message = "真实姓名不能为空")
        private String realName;
        private String phone;
        private String applyReason;
        private String avatarUrl;
        private String wxAvatarUrl;
        private String avatarSource;
    }

    @Data
    public static class MiniappEntryReq {
        @NotBlank(message = "登录凭证不能为空")
        private String code;
        @NotBlank(message = "登录场景不能为空")
        private String loginScene;
        /** 设备上下文（前端采集，服务端会补齐 IP/UA）。 */
        private DeviceContext deviceContext;
    }

    @Data
    public static class AdminLoginReq {
        @NotBlank(message = "登录账号不能为空")
        private String loginName;
        @NotBlank(message = "登录密码不能为空")
        private String password;
        /** 设备上下文（前端采集，服务端会补齐 IP/UA）。 */
        private DeviceContext deviceContext;
    }

    @Data
    public static class DeviceContext {
        /** 设备标识（前端持久化生成）。 */
        private String deviceId;
        /** 设备名称（如 Web-Chrome、iPhone 15 Pro）。 */
        private String deviceName;
        /** 原始 UA（前端传入，可为空）。 */
        private String userAgent;
    }

    @Data
    public static class AvatarReq {
        @NotBlank(message = "头像地址不能为空")
        private String avatarUrl;
        private String avatarSource;
    }

    @Data
    public static class ProfileReq {
        private String nickName;
        private String realName;
        private String phone;
    }

    @Data
    public static class ChangePasswordReq {
        @NotBlank(message = "当前密码不能为空")
        private String oldPassword;
        @NotBlank(message = "新密码不能为空")
        private String newPassword;
    }

    @Data
    public static class SessionReq {
        private String accessToken;
    }

    @Data
    public static class LoginResp {
        private Boolean approved;
        private String loginStatus;
        private String message;
        private String accessToken;
        private String tokenExpiresAt;
        private Map<String, Object> user;

        public static LoginResp guest(String message) {
            LoginResp resp = new LoginResp();
            resp.setApproved(false);
            resp.setLoginStatus(AuthDomainConstants.LOGIN_STATUS_GUEST);
            resp.setMessage(message);
            return resp;
        }
    }
}
