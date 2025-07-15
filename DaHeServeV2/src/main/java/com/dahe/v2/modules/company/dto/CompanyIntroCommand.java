package com.dahe.v2.modules.company.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 企业介绍模块写操作命令 DTO。
 */
public final class CompanyIntroCommand {

    private CompanyIntroCommand() {
    }

    @Data
    public static class CompanyInfoUpsert {
        private Long id;

        @NotBlank(message = "公司名称不能为空")
        private String companyName;

        private String logo;
        private String banner;
        private String introduction;
        private String mission;
        private String copyright;
        private Integer sortOrder;
        private Integer status;
    }

    @Data
    public static class ProductUpsert {
        @NotBlank(message = "产品名称不能为空")
        private String name;
        private String description;
        private String image;
        private Integer sortOrder;
        private Integer status;
    }

    @Data
    public static class HonorUpsert {
        @NotBlank(message = "荣誉名称不能为空")
        private String name;
        private String image;
        private Integer sortOrder;
        private Integer status;
    }

    @Data
    public static class ContactUpsert {
        @NotBlank(message = "联系方式类型不能为空")
        private String contactType;
        @NotBlank(message = "联系方式标签不能为空")
        private String contactLabel;
        @NotBlank(message = "联系方式内容不能为空")
        private String contactValue;
        private Integer sortOrder;
        private Integer status;
    }

    @Data
    public static class Enabled {
        @NotNull(message = "启用状态不能为空")
        private Boolean enabled;
    }
}
