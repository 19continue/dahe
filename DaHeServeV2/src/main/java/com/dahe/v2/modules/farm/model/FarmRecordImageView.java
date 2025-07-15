package com.dahe.v2.modules.farm.model;

import lombok.Data;

/**
 * 农事记录图片展示模型。
 *
 * <p>同一条图片绑定记录会被不同端以不同可见性展示：
 * admin 端可查看全部状态的预览，小程序端只直接查看审核通过的图片，
 * 待审核资源返回提示信息，驳回资源对小程序端隐藏。</p>
 */
@Data
public class FarmRecordImageView {

    /** 资源 ID。 */
    private Long id;

    /** 资源文件名。 */
    private String fileName;

    /** 可直接预览的文件地址；对小程序待审核/驳回资源可为空。 */
    private String fileUrl;

    /** 审核状态：pending/approved/rejected。 */
    private String reviewStatus;

    /** 审核状态文案。 */
    private String reviewStatusText;

    /** 审核备注。 */
    private String reviewRemark;

    /** 当前端是否可直接预览。 */
    private Boolean canPreview;

    /** 当前端展示提示。 */
    private String hintMessage;
}
