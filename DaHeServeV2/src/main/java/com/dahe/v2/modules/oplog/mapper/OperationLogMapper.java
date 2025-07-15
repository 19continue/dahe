package com.dahe.v2.modules.oplog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dahe.v2.modules.oplog.model.OperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/** 操作日志数据访问层。 */
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
