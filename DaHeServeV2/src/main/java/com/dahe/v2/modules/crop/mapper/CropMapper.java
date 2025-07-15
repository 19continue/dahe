package com.dahe.v2.modules.crop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dahe.v2.modules.crop.model.Crop;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作物层级数据 Mapper。
 */
@Mapper
public interface CropMapper extends BaseMapper<Crop> {
}

