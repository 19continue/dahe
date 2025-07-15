package com.dahe.v2.modules.meta.service;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

public interface MetaOptionQueryService {

    List<String> listTownships(String keyword, String province, String city, String district);

    List<String> listProvinces(String keyword);

    List<String> listCities(String keyword, String province);

    List<String> listDistricts(String keyword, String province, String city);

    List<String> listCrops(String keyword);

    List<String> listVarieties(String cropName, String keyword);

    List<CropTreeItem> listCropTree(String keyword);

    List<VarietyGroupItem> listVarietyGroups(String keyword);

    @Data
    class CropTreeItem {
        private Long categoryId;
        private String categoryName;
        private String categoryImageUrl;
        private Integer categorySortOrder;
        private List<CropVarietyItem> varieties = new ArrayList<CropVarietyItem>();
    }

    @Data
    class CropVarietyItem {
        private Long id;
        private Long categoryId;
        private String name;
        private String imageUrl;
        private Integer sortOrder;
    }

    @Data
    class VarietyGroupItem {
        private String cropType;
        private List<String> varieties = new ArrayList<String>();
    }
}
