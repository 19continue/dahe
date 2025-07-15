package com.dahe.v2.modules.auth.role.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("admin_role")
public class AdminRole {

    /** 主键 ID。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 角色编码（唯一），统一由服务层规范化后写入。 */
    private String roleCode;

    /** 角色名称（给管理端展示）。 */
    private String roleName;

    /** 角色说明。 */
    private String description;

    /** 继承角色编码（当前版本仅做保留字段与基础校验）。 */
    private String inheritRoleCode;

    /** 菜单权限 JSON 数组（如 [\"/dashboard\",\"/users\"]）。 */
    private String menuPermissionsJson;

    /** 排序值，越小越靠前。 */
    private Integer sortOrder;

    /** 启用状态：1=启用，0=禁用。 */
    private Integer enabled;

    /** 系统角色标记：1=系统内置，0=自定义。 */
    private Integer isSystem;

    /** 逻辑删除标记。 */
    @TableLogic
    private Integer deleted;

    /** 创建时间。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
