package com.dahe.v2.modules.assets.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 媒体资源实体。
 * 统一承载图片/文件的上传、审核、回收站与业务绑定状态。
 */
@Data
@TableName("media_asset")
public class MediaAsset {

    @TableId(type = IdType.ASSIGN_ID)
    /** 资源主键。 */
    private Long id;

    /** 展示文件名。 */
    private String fileName;

    /** 文件访问地址（可为公网 URL 或外部链接）。 */
    private String fileUrl;

    /** 资源类型：image/file。 */
    private String fileType;

    /** 资源所在逻辑文件夹路径。 */
    private String folderPath;

    /** 来源类型：admin_upload/miniapp_upload/system_upload。 */
    private String sourceType;

    /** 回收站标记：0 正常，1 回收站。 */
    private Integer recycleFlag;

    /** 移入回收站时间。 */
    private LocalDateTime recycledAt;

    /** 执行回收操作的用户 ID。 */
    private Long recycledByUserId;

    /** 审核状态：pending/approved/rejected。 */
    private String reviewStatus;

    /** 审核时间。 */
    private LocalDateTime reviewedAt;

    /** 审核人用户 ID。 */
    private Long reviewedByUserId;

    /** 审核备注（驳回原因等）。 */
    private String reviewRemark;

    /** 业务模块键，如 field/crop/export/auth。 */
    private String moduleKey;

    /** 业务主键（模块内实体 ID）。 */
    private Long bizId;

    /** 排序号（越小越靠前）。 */
    private Integer sortOrder;

    /** 文件体积（字节）。 */
    private Long sizeBytes;

    /** 创建人用户 ID。 */
    private Long createdByUserId;

    /** 创建人显示名。 */
    private String createdByName;

    /** 资源锁标记：0 未锁定，1 已锁定。 */
    private Integer lockedFlag;

    /** 删除前校验的资源锁密码摘要。 */
    private String lockPasswordHash;

    /** 资源锁备注，用于说明锁定原因。 */
    private String lockRemark;

    /** 最近一次调整资源锁的时间。 */
    private LocalDateTime lockUpdatedAt;

    /** 最近一次调整资源锁的用户 ID。 */
    private Long lockUpdatedByUserId;

    /** 最近一次调整资源锁的用户名称。 */
    private String lockUpdatedByName;

    /** 业务备注。 */
    private String remark;

    @TableLogic
    /** 逻辑删除标记（MyBatis-Plus）。 */
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    /** 创建时间。 */
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
