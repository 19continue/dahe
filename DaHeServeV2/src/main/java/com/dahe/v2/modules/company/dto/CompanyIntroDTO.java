package com.dahe.v2.modules.company.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 企业介绍聚合响应 DTO。
 */
@Data
public class CompanyIntroDTO {

    /** 企业基础信息。 */
    private CompanyInfoItem companyInfo;

    /** 企业产品列表。 */
    private List<ProductItem> products = new ArrayList<>();

    /** 企业荣誉列表。 */
    private List<HonorItem> honors = new ArrayList<>();

    /** 企业联系方式列表。 */
    private List<ContactItem> contacts = new ArrayList<>();

    @Data
    public static class CompanyInfoItem {
        /** 主键。 */
        private Long id;
        /** 企业名称。 */
        private String companyName;
        /** logo 地址。 */
        private String logo;
        /** banner 地址。 */
        private String banner;
        /** 企业介绍。 */
        private String introduction;
        /** 企业使命。 */
        private String mission;
        /** 版权文案。 */
        private String copyright;
        /** 排序号。 */
        private Integer sortOrder;
        /** 状态：1 启用，0 停用。 */
        private Integer status;
    }

    @Data
    public static class ProductItem {
        /** 主键。 */
        private Long id;
        /** 产品名称。 */
        private String name;
        /** 产品描述。 */
        private String description;
        /** 产品图片地址。 */
        private String image;
        /** 排序号。 */
        private Integer sortOrder;
        /** 状态：1 启用，0 停用。 */
        private Integer status;
    }

    @Data
    public static class HonorItem {
        /** 主键。 */
        private Long id;
        /** 荣誉名称。 */
        private String name;
        /** 荣誉图片地址。 */
        private String image;
        /** 排序号。 */
        private Integer sortOrder;
        /** 状态：1 启用，0 停用。 */
        private Integer status;
    }

    @Data
    public static class ContactItem {
        /** 主键。 */
        private Long id;
        /** 联系方式类型。 */
        private String contactType;
        /** 联系方式标签。 */
        private String contactLabel;
        /** 联系方式内容。 */
        private String contactValue;
        /** 排序号。 */
        private Integer sortOrder;
        /** 状态：1 启用，0 停用。 */
        private Integer status;
    }
}
