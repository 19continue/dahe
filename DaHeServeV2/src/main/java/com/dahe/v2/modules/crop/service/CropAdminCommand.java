package com.dahe.v2.modules.crop.service;

import lombok.Data;

import java.util.List;

/**
 * crop 后台写链路命令对象。
 *
 * <p>用于隔离 controller 请求体与 service 编排入参，避免业务参数在多层间散落。</p>
 */
public final class CropAdminCommand {

    private CropAdminCommand() {
    }

    @Data
    public static class PageQuery {
        private String keyword;
        private String nodeType;
        private Long parentId;
        private long page;
        private long pageSize;
    }

    @Data
    public static class Create {
        private String name;
        private String variety;
        private String nodeType;
        private Long parentId;
        private String imageUrl;
    }

    @Data
    public static class Update {
        private String name;
        private String variety;
        private Long parentId;
        private String imageUrl;
        private Integer sortOrder;
    }

    @Data
    public static class Reorder {
        private List<Long> ids;
        private String nodeType;
        private Long parentId;
    }
}
