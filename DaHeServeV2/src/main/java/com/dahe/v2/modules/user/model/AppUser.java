package com.dahe.v2.modules.user.model;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class AppUser {

    /**
     * 用户主键（雪花 ID）。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 微信 openId（公众号/小程序侧用户唯一标识）。
     */
    private String wxOpenId;

    /**
     * 微信 unionId（同一开放平台下跨应用统一标识）。
     */
    private String wxUnionId;

    /**
     * 用户昵称（展示名）。
     */
    private String nickName;

    /**
     * 真实姓名（实名信息或后台录入）。
     */
    private String realName;

    /**
     * 手机号。
     */
    private String phone;

    /**
     * 后台登录账号（仅 admin 用户使用）。
     */
    private String loginName;

    /**
     * 后台登录密码哈希（BCrypt）。
     */
    @JsonIgnore
    private String passwordHash;

    /**
     * 审核状态/业务状态（如 pending、approved、rejected 等，具体值由业务约定）。
     */
    private String status;

    /**
     * 角色编码（与权限模块角色 code 关联）。
     */
    private String roleCode;

    /**
     * 是否可访问管理端控制台（1 可访问，0 不可访问）。
     */
    private Integer canConsole;

    /**
     * 用户类型（用于区分不同来源或身份类型）。
     */
    private String userType;

    /**
     * 当前生效头像地址（系统统一对外展示）。
     */
    private String avatarUrl;

    /**
     * 微信原始头像地址（保留来源数据）。
     */
    private String wxAvatarUrl;

    /**
     * 头像来源（例如 wx、manual 等）。
     */
    private String avatarSource;

    /**
     * 启用状态（1 启用，0 禁用）。
     */
    private Integer enabled;

    /**
     * 是否为超级管理员（1 是，0 否）。
     */
    private Integer isSuperAdmin;

    /**
     * 申请原因（用户申请加入/提权时填写）。
     */
    private String applyReason;

    /**
     * 驳回原因（审核驳回时记录）。
     */
    private String rejectReason;

    /**
     * 回收站标记（1 已回收，0 正常）。
     */
    private Integer recycleFlag;

    /**
     * 移入回收站时间。
     */
    private LocalDateTime recycledAt;

    /**
     * 执行回收操作的用户 ID。
     */
    private Long recycledByUserId;

    /**
     * 回收备注。
     */
    private String recycleRemark;

    /**
     * 逻辑删除标记（1 已删除，0 未删除）。
     */
    @TableLogic
    private Integer deleted;

    /**
     * 创建时间（插入时自动填充）。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间（插入/更新时自动填充）。
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
