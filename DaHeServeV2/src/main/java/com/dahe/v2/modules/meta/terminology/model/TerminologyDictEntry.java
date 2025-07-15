package com.dahe.v2.modules.meta.terminology.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("terminology_dict")
/**
 * 术语词典实体。
 * 对应 `terminology_dict` 表，保存“源术语 -> 目标术语”的标准化映射。
 */
public class TerminologyDictEntry {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String sourceText;

    private String targetText;

    private Integer sortOrder;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
