package com.dahe.v2.modules.amap.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("amap_api_audit")
public class AmapApiAudit {
    /** 主键 ID。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 记录日期（按天聚合统计使用）。 */
    private LocalDate recordDate;
    /** 操作用户 ID。 */
    private Long userId;
    /** 操作人名称（快照）。 */
    private String operatorName;
    /** 业务场景标识，如 geocode/weather 等。 */
    private String bizScene;
    /** 计费分类：weather/location。 */
    private String apiType;
    /** 调用的开放平台接口路径。 */
    private String apiPath;
    /** 请求来源，如 backend-proxy。 */
    private String requestSource;
    /** 成功标记：1 成功，0 失败。 */
    private Integer successFlag;
    /** 耗时（毫秒）。 */
    private Integer costMs;
    /** 错误信息。 */
    private String errorMessage;
    /** 逻辑删除标记。 */
    @TableLogic
    private Integer deleted;
    /** 创建时间。 */
    private LocalDateTime createdAt;
}
