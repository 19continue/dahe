package com.dahe.v2.modules.amap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dahe.v2.modules.amap.model.AmapApiAudit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
/**
 * 高德 API 审计表 Mapper。
 */
public interface AmapApiAuditMapper extends BaseMapper<AmapApiAudit> {

    /** 物理删除历史审计日志（避免逻辑删除导致表体积持续膨胀）。 */
    @Delete("DELETE FROM `amap_api_audit` WHERE `record_date` < #{cutoffDate} LIMIT #{limit}")
    int purgeBeforeDate(@Param("cutoffDate") LocalDate cutoffDate, @Param("limit") int limit);

    /** 按日期+计费分类聚合调用来源，用于缓存命中趋势展示。 */
    @Select(
            "SELECT `record_date` AS recordDate, " +
                    "LOWER(COALESCE(NULLIF(TRIM(`api_type`),''),'unknown')) AS apiType, " +
                    "SUM(CASE WHEN `request_source` LIKE 'backend-proxy-cache:%' THEN 1 ELSE 0 END) AS pureCacheCount, " +
                    "SUM(CASE WHEN `request_source` LIKE 'backend-proxy-mixed:%' THEN 1 ELSE 0 END) AS mixedCount, " +
                    "SUM(CASE WHEN `request_source` = 'backend-proxy' THEN 1 ELSE 0 END) AS remoteCount, " +
                    "COUNT(*) AS totalCount " +
                    "FROM `amap_api_audit` " +
                    "WHERE `deleted` = 0 AND `record_date` BETWEEN #{startDate} AND #{endDate} " +
                    "GROUP BY `record_date`, LOWER(COALESCE(NULLIF(TRIM(`api_type`),''),'unknown')) " +
                    "ORDER BY `record_date` ASC"
    )
    List<Map<String, Object>> aggregateByDateAndApiType(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
