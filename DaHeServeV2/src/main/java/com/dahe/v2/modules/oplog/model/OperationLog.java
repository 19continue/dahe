package com.dahe.v2.modules.oplog.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("operation_log")
/**
 * 系统操作日志实体。
 * 对应 `operation_log` 表，记录写操作审计轨迹与可撤销元数据。
 */
public class OperationLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String userType;

    private String roleCode;

    private String operatorName;

    private String operationType;

    private String targetModule;

    private Long targetId;

    private String httpMethod;

    private String apiPath;

    private String queryString;

    private Integer resultCode;

    private String resultMessage;

    private Integer successFlag;

    private Integer costMs;

    private String clientIp;

    private String undoType;

    private String undoPayloadJson;

    private String undoStatus;

    private String undoFailReason;

    private LocalDateTime undoAppliedAt;

    private Long undoAppliedByUserId;

    private LocalDateTime createdAt;

    /** 链式撤销判定结果（仅用于列表展示，不落库）。 */
    @TableField(exist = false)
    private Boolean chainUndoAllowed;

    /** 当前目标对象可撤销链路中的最新日志 ID（仅用于展示，不落库）。 */
    @TableField(exist = false)
    private Long chainLatestUndoLogId;
}
