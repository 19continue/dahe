package com.dahe.v2.modules.assets.staticasset.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小程序静态资源实体。
 *
 * <p>该表只承载“可控 URL 的小程序运行时静态资源”，与业务资源库分开管理。</p>
 */
@Data
@TableName("miniapp_static_asset")
public class MiniappStaticAsset {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 人可读展示名称。 */
    private String displayName;

    /** 固定 URL 使用的文件名主体（不含后缀）。 */
    private String storageName;

    /** 存储扩展名，形如 .png。 */
    private String fileExt;

    /** 对外访问地址。 */
    private String fileUrl;

    /** 文件类型：image/file。 */
    private String fileType;

    /** 文件大小（字节）。 */
    private Long sizeBytes;

    /** 说明性备注。 */
    private String remark;

    /** 创建人用户 ID。 */
    private Long createdByUserId;

    /** 创建人名称。 */
    private String createdByName;

    /** 最后更新人用户 ID。 */
    private Long updatedByUserId;

    /** 最后更新人名称。 */
    private String updatedByName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
