package com.dahe.v2.modules.assets.policy.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资源策略配置实体（全局单例行，默认 id=1）。
 * 说明：
 * 1. miniapp/admin 维度分别控制上传次数、容量、单文件大小和审核开关。
 * 2. operator 字段为历史兼容字段，当前与 miniapp 策略保持同步。
 */
@Data
@TableName("media_asset_policy_config")
public class MediaAssetPolicyConfig {

    @TableId(type = IdType.INPUT)
    /** 主键，固定使用 1。 */
    private Long id;

    /** 小程序端：每日上传次数上限。0 表示不限。 */
    private Integer miniappDailyUploadLimit;

    /** 小程序端：每日上传总容量上限（MB）。0 表示不限。 */
    private Integer miniappDailyUploadSizeMb;

    /** 小程序端：单文件大小上限（MB）。0 表示不限。 */
    private Integer miniappSingleFileMaxMb;

    /** 小程序端允许类型，逗号分隔，如 image,file。 */
    private String miniappAllowedFileTypes;

    /** 小程序端是否需要审核：1 需要，0 不需要。 */
    private Integer miniappRequireReview;

    /** 后台端：每日上传次数上限。0 表示不限。 */
    private Integer adminDailyUploadLimit;

    /** 后台端：每日上传总容量上限（MB）。0 表示不限。 */
    private Integer adminDailyUploadSizeMb;

    /** 后台端：单文件大小上限（MB）。0 表示不限。 */
    private Integer adminSingleFileMaxMb;

    /** 后台端允许类型，逗号分隔，如 image,file。 */
    private String adminAllowedFileTypes;

    /** 后台端是否需要审核：1 需要，0 不需要。 */
    private Integer adminRequireReview;

    /** 历史字段：操作员每日上传次数上限（兼容迁移保留）。 */
    private Integer operatorDailyUploadLimit;

    /** 历史字段：操作员每日上传容量上限 MB（兼容迁移保留）。 */
    private Integer operatorDailyUploadSizeMb;

    /** 历史字段：操作员单文件上限 MB（兼容迁移保留）。 */
    private Integer operatorSingleFileMaxMb;

    /** 历史字段：操作员允许类型（兼容迁移保留）。 */
    private String operatorAllowedFileTypes;

    /** 历史字段：操作员审核开关（兼容迁移保留）。 */
    private Integer operatorRequireReview;

    /** 严格来源（小程序）资源回收站最短保留天数。 */
    private Integer strictSourcePurgeRetainDays;

    /** 全局资源锁密码摘要。 */
    private String lockPasswordHash;

    /** 全局资源锁密码更新时间。 */
    private LocalDateTime lockPasswordUpdatedAt;

    /** 全局资源锁密码更新人编号。 */
    private Long lockPasswordUpdatedByUserId;

    /** 全局资源锁密码更新人名称。 */
    private String lockPasswordUpdatedByName;

    /** 策略备注。 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    /** 创建时间。 */
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
