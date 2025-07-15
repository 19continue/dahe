package com.dahe.v2.modules.field.model;

import lombok.Data;

@Data
public class FieldPageQuery {

    private String keyword;
    private String status;
    private String cropType;
    private String cropTypeJsonToken;
    private String cycleCropTypeJsonToken;
    private String province;
    private String city;
    private String district;
    private String township;
    private String cropVariety;
    private String cropVarietyJsonToken;
    private String cycleCropVarietyJsonToken;
    private Integer enabled;
    private boolean includeDisabled;

    private String sortBy;
    private String sortDirection;
    private Double latitude;
    private Double longitude;

    private Double minLatitude;
    private Double maxLatitude;
    private Double minLongitude;
    private Double maxLongitude;
    private Double radiusMeters;
    private String queryPointWkt;
    private String envelopeWkt;

    private long page = 1L;
    private long pageSize = 10L;
    private long offset = 0L;
}
