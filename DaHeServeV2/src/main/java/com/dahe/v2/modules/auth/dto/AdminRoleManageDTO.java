package com.dahe.v2.modules.auth.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * 后台角色管理请求 DTO。
 */
public class AdminRoleManageDTO {

    @Data
    public static class CreateReq {
        @NotBlank(message = "角色名称不能为空")
        private String roleName;
        private String description;
        private String inheritRoleCode;
        private List<String> menuPermissions;
        private Integer sortOrder;
        private Integer enabled;
    }

    @Data
    public static class UpdateReq {
        @NotBlank(message = "角色名称不能为空")
        private String roleName;
        private String description;
        private String inheritRoleCode;
        private List<String> menuPermissions;
        private Integer sortOrder;
    }

    @Data
    public static class EnabledReq {
        private Integer enabled;
    }
}
