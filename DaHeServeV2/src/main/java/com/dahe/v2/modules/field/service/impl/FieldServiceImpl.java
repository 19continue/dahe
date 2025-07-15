package com.dahe.v2.modules.field.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dahe.v2.modules.field.mapper.FieldMapper;
import com.dahe.v2.modules.field.model.Field;
import com.dahe.v2.modules.field.model.FieldPageQuery;
import com.dahe.v2.modules.field.service.FieldCropVarietyGroupCodec;
import com.dahe.v2.modules.field.service.FieldService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
/**
 * 田块服务实现。
 * 负责田块分页检索、排序号分配与顺序重排。
 */
public class FieldServiceImpl extends ServiceImpl<FieldMapper, Field> implements FieldService {

    private static final String SORT_DEFAULT = "default";
    private static final String SORT_DISTANCE = "distance";
    private static final String SORT_AREA = "area";
    private static final String SORT_ASC = "asc";
    private static final String SORT_DESC = "desc";

    @Override
    public Page<Field> pageFields(FieldPageQuery query) {
        FieldPageQuery safeQuery = normalizePageQuery(query);
        FieldPageQuery selectQuery = applyDistanceScope(copyQuery(safeQuery));
        Page<Field> out = new Page<>(safeQuery.getPage(), safeQuery.getPageSize(), 0L);
        long total = baseMapper.countPageByQuery(safeQuery);
        out.setTotal(total);
        if (total <= 0L) {
            out.setRecords(new ArrayList<>());
            return out;
        }
        List<Field> rows = baseMapper.selectPageByQuery(selectQuery);
        applyDistancePresentation(rows);
        out.setRecords(rows);
        return out;
    }

    @Override
    public Page<Field> pageNearbyFields(
            String keyword,
            Double latitude,
            Double longitude,
            double radiusKm,
            boolean includeDisabled,
            long page,
            long pageSize
    ) {
        if (!isValidCoordinate(latitude, longitude)) {
            Page<Field> empty = new Page<>(page, pageSize, 0L);
            empty.setRecords(new ArrayList<>());
            return empty;
        }
        double safeRadiusKm = normalizeRadiusKm(radiusKm);
        double latitudeDelta = safeRadiusKm / 111D;
        double cosLat = Math.cos(Math.toRadians(latitude));
        double longitudeDelta = safeRadiusKm / (111D * Math.max(Math.abs(cosLat), 0.1D));

        FieldPageQuery query = new FieldPageQuery();
        query.setKeyword(normalizeText(keyword));
        query.setIncludeDisabled(includeDisabled);
        query.setLatitude(latitude);
        query.setLongitude(longitude);
        query.setSortBy(SORT_DISTANCE);
        query.setSortDirection(SORT_ASC);
        query.setPage(page);
        query.setPageSize(pageSize);
        applyDistanceWindow(query, safeRadiusKm * 1000D, latitudeDelta, longitudeDelta);
        return pageFields(query);
    }

    @Override
    /** 返回下一条可用排序号。 */
    public int nextSortOrder() {
        Field top = this.lambdaQuery()
                .select(Field::getSortOrder)
                .orderByDesc(Field::getSortOrder)
                .last("limit 1")
                .one();
        int base = top == null || top.getSortOrder() == null ? 0 : top.getSortOrder();
        return base + 1;
    }

    @Override
    /** 按请求给定 ID 顺序逐条更新 sortOrder。 */
    public void reorder(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        int order = 1;
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            Field row = new Field();
            row.setId(id);
            row.setSortOrder(order++);
            this.updateById(row);
        }
    }

    @Override
    public void refreshLocationPoint(Long fieldId) {
        if (fieldId == null) {
            return;
        }
        baseMapper.refreshLocationPoint(fieldId);
    }

    private boolean isValidCoordinate(Double latitude, Double longitude) {
        return latitude != null
                && longitude != null
                && !Double.isNaN(latitude)
                && !Double.isNaN(longitude)
                && Math.abs(latitude) <= 90D
                && Math.abs(longitude) <= 180D
                && !(latitude == 0D && longitude == 0D);
    }

    private double normalizeRadiusKm(double radiusKm) {
        if (Double.isNaN(radiusKm) || radiusKm <= 0D) {
            return 20D;
        }
        return Math.min(radiusKm, 50D);
    }

    private FieldPageQuery normalizePageQuery(FieldPageQuery source) {
        FieldPageQuery query = source == null ? new FieldPageQuery() : source;
        query.setKeyword(normalizeText(query.getKeyword()));
        query.setStatus(normalizeText(query.getStatus()));
        query.setCropType(normalizeText(query.getCropType()));
        query.setProvince(normalizeText(query.getProvince()));
        query.setCity(normalizeText(query.getCity()));
        query.setDistrict(normalizeText(query.getDistrict()));
        query.setTownship(normalizeText(query.getTownship()));
        query.setCropVariety(normalizeText(query.getCropVariety()));
        query.setSortBy(normalizeSortBy(query.getSortBy(), query.getLatitude(), query.getLongitude()));
        query.setSortDirection(normalizeSortDirection(query.getSortDirection(), query.getSortBy()));
        query.setCropTypeJsonToken(FieldCropVarietyGroupCodec.buildJsonLikeToken("cropType", query.getCropType()));
        query.setCycleCropTypeJsonToken(FieldCropVarietyGroupCodec.buildJsonLikeToken("name", query.getCropType()));
        query.setCropVarietyJsonToken(FieldCropVarietyGroupCodec.buildJsonLikeToken("cropVariety", query.getCropVariety()));
        query.setCycleCropVarietyJsonToken(FieldCropVarietyGroupCodec.buildJsonLikeToken("variety", query.getCropVariety()));
        long safePage = Math.max(1L, query.getPage());
        long safePageSize = Math.max(1L, query.getPageSize());
        query.setPage(safePage);
        query.setPageSize(safePageSize);
        query.setOffset((safePage - 1L) * safePageSize);
        if (!isValidCoordinate(query.getLatitude(), query.getLongitude())) {
            query.setLatitude(null);
            query.setLongitude(null);
            query.setQueryPointWkt(null);
            query.setEnvelopeWkt(null);
            query.setRadiusMeters(null);
            if (SORT_DISTANCE.equals(query.getSortBy())) {
                query.setSortBy(SORT_DEFAULT);
                query.setSortDirection(SORT_ASC);
            }
        } else {
            query.setQueryPointWkt(buildPointWkt(query.getLongitude(), query.getLatitude()));
        }
        return query;
    }

    private FieldPageQuery applyDistanceScope(FieldPageQuery query) {
        if (query == null || !SORT_DISTANCE.equals(query.getSortBy())) {
            return query;
        }
        if (query.getRadiusMeters() != null || !isValidCoordinate(query.getLatitude(), query.getLongitude())) {
            return query;
        }
        long requiredCount = Math.max(1L, query.getOffset() + query.getPageSize());
        double[] radiusKmSteps = new double[]{2D, 5D, 10D, 20D, 40D, 80D, 160D, 320D, 640D};
        for (double radiusKm : radiusKmSteps) {
            FieldPageQuery scoped = copyQuery(query);
            double latitudeDelta = radiusKm / 111D;
            double cosLat = Math.cos(Math.toRadians(query.getLatitude()));
            double longitudeDelta = radiusKm / (111D * Math.max(Math.abs(cosLat), 0.1D));
            applyDistanceWindow(scoped, radiusKm * 1000D, latitudeDelta, longitudeDelta);
            long count = baseMapper.countPageByQuery(scoped);
            if (count >= requiredCount) {
                return scoped;
            }
        }
        return query;
    }

    private void applyDistanceWindow(FieldPageQuery query, double radiusMeters, double latitudeDelta, double longitudeDelta) {
        if (query == null || !isValidCoordinate(query.getLatitude(), query.getLongitude())) {
            return;
        }
        query.setMinLatitude(query.getLatitude() - latitudeDelta);
        query.setMaxLatitude(query.getLatitude() + latitudeDelta);
        query.setMinLongitude(query.getLongitude() - longitudeDelta);
        query.setMaxLongitude(query.getLongitude() + longitudeDelta);
        query.setRadiusMeters(radiusMeters);
        query.setEnvelopeWkt(buildEnvelopeWkt(
                query.getMinLongitude(),
                query.getMinLatitude(),
                query.getMaxLongitude(),
                query.getMaxLatitude()
        ));
    }

    private FieldPageQuery copyQuery(FieldPageQuery source) {
        FieldPageQuery copy = new FieldPageQuery();
        copy.setKeyword(source.getKeyword());
        copy.setStatus(source.getStatus());
        copy.setCropType(source.getCropType());
        copy.setCropTypeJsonToken(source.getCropTypeJsonToken());
        copy.setCycleCropTypeJsonToken(source.getCycleCropTypeJsonToken());
        copy.setProvince(source.getProvince());
        copy.setCity(source.getCity());
        copy.setDistrict(source.getDistrict());
        copy.setTownship(source.getTownship());
        copy.setCropVariety(source.getCropVariety());
        copy.setCropVarietyJsonToken(source.getCropVarietyJsonToken());
        copy.setCycleCropVarietyJsonToken(source.getCycleCropVarietyJsonToken());
        copy.setEnabled(source.getEnabled());
        copy.setIncludeDisabled(source.isIncludeDisabled());
        copy.setSortBy(source.getSortBy());
        copy.setSortDirection(source.getSortDirection());
        copy.setLatitude(source.getLatitude());
        copy.setLongitude(source.getLongitude());
        copy.setMinLatitude(source.getMinLatitude());
        copy.setMaxLatitude(source.getMaxLatitude());
        copy.setMinLongitude(source.getMinLongitude());
        copy.setMaxLongitude(source.getMaxLongitude());
        copy.setRadiusMeters(source.getRadiusMeters());
        copy.setQueryPointWkt(source.getQueryPointWkt());
        copy.setEnvelopeWkt(source.getEnvelopeWkt());
        copy.setPage(source.getPage());
        copy.setPageSize(source.getPageSize());
        copy.setOffset(source.getOffset());
        return copy;
    }

    private void applyDistancePresentation(List<Field> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (Field row : rows) {
            if (row == null) {
                continue;
            }
            Double distanceMeters = row.getDistanceMeters();
            if (distanceMeters != null && Double.isFinite(distanceMeters) && distanceMeters >= 0D) {
                row.setDistanceText(formatDistanceText(distanceMeters));
            } else {
                row.setDistanceText(null);
            }
        }
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private String normalizeSortBy(String sortBy, Double latitude, Double longitude) {
        String value = normalizeText(sortBy);
        if (!StringUtils.hasText(value)) {
            return SORT_DEFAULT;
        }
        String lowered = value.toLowerCase(Locale.ROOT);
        if (SORT_AREA.equals(lowered)) {
            return SORT_AREA;
        }
        if (SORT_DISTANCE.equals(lowered) && isValidCoordinate(latitude, longitude)) {
            return SORT_DISTANCE;
        }
        return SORT_DEFAULT;
    }

    private String normalizeSortDirection(String sortDirection, String sortBy) {
        String value = normalizeText(sortDirection);
        if (StringUtils.hasText(value)) {
            String lowered = value.toLowerCase(Locale.ROOT);
            if (SORT_DESC.equals(lowered)) {
                return SORT_DESC;
            }
            if (SORT_ASC.equals(lowered)) {
                return SORT_ASC;
            }
        }
        if (SORT_AREA.equals(sortBy)) {
            return SORT_DESC;
        }
        return SORT_ASC;
    }

    private String formatDistanceText(double distanceMeters) {
        if (Double.isNaN(distanceMeters) || distanceMeters < 0D) {
            return "";
        }
        if (distanceMeters < 1000D) {
            return Math.round(distanceMeters) + "m";
        }
        return String.format(Locale.ROOT, "%.1fkm", distanceMeters / 1000D);
    }

    private String buildPointWkt(Double longitude, Double latitude) {
        if (!isValidCoordinate(latitude, longitude)) {
            return null;
        }
        return String.format(Locale.ROOT, "POINT(%f %f)", longitude, latitude);
    }

    private String buildEnvelopeWkt(Double minLongitude, Double minLatitude, Double maxLongitude, Double maxLatitude) {
        if (minLongitude == null || minLatitude == null || maxLongitude == null || maxLatitude == null) {
            return null;
        }
        return String.format(
                Locale.ROOT,
                "POLYGON((%f %f,%f %f,%f %f,%f %f,%f %f))",
                minLongitude, minLatitude,
                maxLongitude, minLatitude,
                maxLongitude, maxLatitude,
                minLongitude, maxLatitude,
                minLongitude, minLatitude
        );
    }
}

