package com.dahe.v2.modules.assets.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dahe.v2.modules.assets.model.MediaAsset;
import org.apache.ibatis.annotations.Mapper;

/**
 * 资源表 Mapper。
 * 仅承载 MyBatis-Plus 通用 CRUD，复杂聚合在 service 层通过 Wrapper/JdbcTemplate 处理。
 */
@Mapper
public interface MediaAssetMapper extends BaseMapper<MediaAsset> {
}
