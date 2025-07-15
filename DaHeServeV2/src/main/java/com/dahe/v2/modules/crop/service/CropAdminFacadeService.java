package com.dahe.v2.modules.crop.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.modules.crop.model.Crop;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * crop 后台门面服务。
 *
 * <p>收口 controller 的业务编排与跨表同步逻辑。</p>
 */
public interface CropAdminFacadeService {

    Page<Crop> page(CropAdminCommand.PageQuery query);

    List<TreeCategoryItem> tree(String keyword);

    Crop create(CropAdminCommand.Create command);

    Crop update(Long id, CropAdminCommand.Update command);

    void reorder(CropAdminCommand.Reorder command);

    boolean delete(Long id);

    @Data
    class TreeCategoryItem {
        private Long id;
        private String name;
        private String imageUrl;
        private Integer sortOrder;
        private List<TreeVarietyItem> varieties = new ArrayList<TreeVarietyItem>();
    }

    @Data
    class TreeVarietyItem {
        private Long id;
        private Long parentId;
        private String name;
        private String imageUrl;
        private Integer sortOrder;
    }
}
