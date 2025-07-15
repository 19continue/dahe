package com.dahe.v2.modules.auth.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 后台用户管理 DTO。
 */
public final class AdminUserManageDTO {

    private AdminUserManageDTO() {
    }

    @Data
    public static class ApproveReq {
        private Boolean approve;
        private String roleCode;
        private Boolean canConsole;
        private String rejectReason;
        private String expectedUpdatedAt;
    }

    @Data
    public static class RoleReq {
        private String roleCode;
        private Boolean canConsole;
        private String expectedUpdatedAt;
    }

    @Data
    public static class EnabledReq {
        private Boolean enabled;
        private String expectedUpdatedAt;
    }

    @Data
    public static class MiniappStatusReq {
        @NotBlank(message = "小程序用户状态不能为空")
        private String status;
        private String expectedUpdatedAt;
    }

    @Data
    public static class DeleteReq {
        private String expectedUpdatedAt;
    }

    @Data
    public static class AdminCreateReq {
        @NotBlank(message = "登录账号不能为空")
        private String loginName;
        @NotBlank(message = "登录密码不能为空")
        private String password;
        @NotBlank(message = "真实姓名不能为空")
        private String realName;
        private String nickName;
        private String phone;
        private String roleCode;
        private String avatarUrl;
    }

    @Data
    public static class ResetPasswordReq {
        @NotBlank(message = "新密码不能为空")
        private String newPassword;
    }
}
