package com.dahe.v2.modules.session.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("token_session")
public class TokenSession {

    /** 主键 ID。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联用户 ID。 */
    private Long userId;

    /** 登录用户类型（admin/miniapp）。 */
    private String userType;

    /** 登录业务场景（如 admin_console、field_record、seed_test）。 */
    private String loginScene;

    /** 设备标识（前端上报）。 */
    private String deviceId;

    /** 设备名称（前端上报）。 */
    private String deviceName;

    /** 客户端 IP（服务端解析）。 */
    private String clientIp;

    /** 客户端 User-Agent。 */
    private String userAgent;

    /** 访问令牌（全局唯一）。 */
    private String accessToken;

    /** 过期时间。 */
    private LocalDateTime expiresAt;

    /** 会话状态：1=有效，0=失效。 */
    private Integer status;

    /** 创建时间。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
