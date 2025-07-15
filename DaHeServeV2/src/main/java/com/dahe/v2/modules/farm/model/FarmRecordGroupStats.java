package com.dahe.v2.modules.farm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** 农事记录分组统计视图。 */
@Data
public class FarmRecordGroupStats {

    /** 田块 ID。 */
    private Long fieldId;

    /** 种植计划 ID。 */
    private Long cycleId;

    /** 田块名称。 */
    private String fieldName;

    /** 种植计划名称。 */
    private String cycleName;

    /** 组内记录数。 */
    private Long recordCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    /** 组内最新作业时间。 */
    private LocalDateTime latestWorkDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    /** 组内最早作业时间。 */
    private LocalDateTime earliestWorkDate;

    /** 最新记录 ID。 */
    private Long latestRecordId;

    /** 最新步骤 ID。 */
    private Long latestStepId;

    /** 最新步骤名称。 */
    private String latestStepName;

    /** 最新执行人。 */
    private String latestOperatorName;

    /** 最新天气。 */
    private String latestWeather;

    /** 最新天气位置。 */
    private String latestWeatherLocation;

    /** 最新湿度。 */
    private String latestHumidity;

    /** 最新风向。 */
    private String latestWindDirection;

    /** 最新风力。 */
    private String latestWindPower;

    /** 最新天气发布时间。 */
    private String latestWeatherReportTime;

    /** 最新备注。 */
    private String latestNotes;

    /** 最新动态参数 JSON。 */
    private String latestExtraJson;
}
