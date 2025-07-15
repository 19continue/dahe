package com.dahe.v2.modules.field.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dahe.v2.modules.field.model.Field;
import com.dahe.v2.modules.field.model.FieldPageQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
/** 田块数据访问层。 */
public interface FieldMapper extends BaseMapper<Field> {

    long countPageByQuery(@Param("query") FieldPageQuery query);

    List<Field> selectPageByQuery(@Param("query") FieldPageQuery query);

    int refreshLocationPoint(@Param("id") Long id);
}

