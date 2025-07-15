package com.dahe.v2.modules.field.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dahe.v2.modules.field.model.Field;
import com.dahe.v2.modules.field.model.FieldPageQuery;

import java.util.List;

public interface FieldService extends IService<Field> {

    /**
     * 分页查询田块。
     * 支持关键词、阶段、作物、地域、启用状态等多条件组合过滤。
     */
    Page<Field> pageFields(FieldPageQuery query);

    /**
     * 按当前位置查询附近田块。
     * 定义统一为“给定半径内田块”，当前小程序使用 20km 半径。
     */
    Page<Field> pageNearbyFields(
            String keyword,
            Double latitude,
            Double longitude,
            double radiusKm,
            boolean includeDisabled,
            long page,
            long pageSize
    );

    /** 获取下一个排序号（当前最大 sort_order + 1）。 */
    int nextSortOrder();

    /** 按传入 ID 顺序重排田块。 */
    void reorder(List<Long> ids);

    void refreshLocationPoint(Long fieldId);
}

