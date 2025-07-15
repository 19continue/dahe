package com.dahe.v2.modules.auth.role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dahe.v2.modules.auth.role.model.AdminRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 后台角色表 Mapper。
 */
@Mapper
public interface AdminRoleMapper extends BaseMapper<AdminRole> {
}