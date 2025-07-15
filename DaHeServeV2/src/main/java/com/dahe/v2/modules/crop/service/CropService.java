package com.dahe.v2.modules.crop.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dahe.v2.modules.crop.model.Crop;

import java.util.List;
import java.util.Map;

/**
 * 作物层级服务接口。
 */
public interface CropService extends IService<Crop> {

    /** 分页查询分类/品种节点。 */
    Page<Crop> pageCrops(String keyword, String nodeType, Long parentId, long page, long pageSize);

    /** 读取全部分类节点，并在必要时修复历史层级数据。 */
    List<Crop> listCategories();

    /** 计算指定范围（节点类型 + 父节点）的下一个排序号。 */
    int nextSortOrder(String nodeType, Long parentId);

    /** 在指定范围内按 id 列表重排。 */
    void reorder(List<Long> ids, String nodeType, Long parentId);

    /** 读取单个分类下的全部品种，兼容层级化数据与历史平铺数据。 */
    List<Crop> listVarietiesByCategory(Long categoryId);

    /** 批量读取分类下的全部品种，兼容层级化数据与历史平铺数据。 */
    Map<Long, List<Crop>> listVarietiesByCategories(List<Crop> categories);
}

