package com.dahe.v2.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dahe.v2.modules.user.model.AppUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {
    // 当前模块使用 MyBatis-Plus 通用 CRUD 能力，暂未扩展自定义 SQL。
}
