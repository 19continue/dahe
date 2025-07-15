package com.dahe.v2.modules.session.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dahe.v2.modules.session.model.TokenSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * token 会话表 Mapper。
 */
@Mapper
public interface TokenSessionMapper extends BaseMapper<TokenSession> {
}