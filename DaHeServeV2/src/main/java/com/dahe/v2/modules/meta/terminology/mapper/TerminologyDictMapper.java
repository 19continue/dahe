package com.dahe.v2.modules.meta.terminology.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dahe.v2.modules.meta.terminology.model.TerminologyDictEntry;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/** 术语词典数据访问层。 */
public interface TerminologyDictMapper extends BaseMapper<TerminologyDictEntry> {
}
