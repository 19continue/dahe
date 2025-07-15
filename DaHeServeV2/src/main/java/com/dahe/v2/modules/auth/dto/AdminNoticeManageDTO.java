package com.dahe.v2.modules.auth.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 后台消息通知管理 DTO。
 */
public final class AdminNoticeManageDTO {

    private AdminNoticeManageDTO() {
    }

    @Data
    public static class CreateReq {
        @NotBlank(message = "消息标题不能为空")
        private String title;
        @NotBlank(message = "消息内容不能为空")
        private String content;
        private String noticeType;
        @NotBlank(message = "发送对象不能为空")
        private String targetType;
        private String routeCode;
        private List<String> targetRoleCodes;
        private List<Long> targetUserIds;
    }

    @Data
    public static class DeleteReq {
        private String expectedUpdatedAt;
    }

    @Data
    public static class ConfigReq {
        private Boolean autoPurgeEnabled;
        private Integer retainDays;
    }
}
