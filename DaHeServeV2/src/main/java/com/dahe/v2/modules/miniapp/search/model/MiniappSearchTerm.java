package com.dahe.v2.modules.miniapp.search.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("miniapp_search_term")
public class MiniappSearchTerm {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String sceneKey;

    private String entityType;

    private Long entityId;

    private String termType;

    private String typeLabel;

    private String label;

    private String valueText;

    private String searchText;

    private String searchCompact;

    private String pinyinFull;

    private String pinyinInitials;

    private String termKeyHash;

    private Integer sortWeight;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
