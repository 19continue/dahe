package com.dahe.v2.modules.assets.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dahe.v2.modules.assets.domain.AssetDomainConstants;
import com.dahe.v2.modules.assets.mapper.MediaAssetMapper;
import com.dahe.v2.modules.assets.model.MediaAsset;
import com.dahe.v2.modules.assets.model.MediaAssetReferenceStats;
import com.dahe.v2.modules.assets.model.MediaAssetStats;
import com.dahe.v2.modules.assets.model.MediaAssetUploadUsage;
import com.dahe.v2.modules.assets.service.MediaAssetReferenceService;
import com.dahe.v2.modules.assets.service.MediaAssetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.Serializable;
import java.util.ArrayList;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 资源服务实现。
 * 负责资源分页、重排、业务绑定、统计、回收站流转与上传用量统计。
 */
@Service
public class MediaAssetServiceImpl extends ServiceImpl<MediaAssetMapper, MediaAsset> implements MediaAssetService {

    private static final Logger log = LoggerFactory.getLogger(MediaAssetServiceImpl.class);

    private static final String SOURCE_ADMIN_UPLOAD = AssetDomainConstants.SOURCE_ADMIN_UPLOAD;
    private static final String SOURCE_MINIAPP_UPLOAD = AssetDomainConstants.SOURCE_MINIAPP_UPLOAD;
    private static final String SOURCE_OPERATOR_UPLOAD = AssetDomainConstants.SOURCE_OPERATOR_UPLOAD;
    private static final String DEFAULT_FOLDER_PATH = AssetDomainConstants.DEFAULT_FOLDER_PATH;
    private static final String LEGACY_DEFAULT_FOLDER_PATH = AssetDomainConstants.LEGACY_DEFAULT_FOLDER_PATH;
    private static final String FIELD_IMAGE_FOLDER_PATH = AssetDomainConstants.FIELD_IMAGE_FOLDER_PATH;
    private static final String LEGACY_FIELD_IMAGE_FOLDER_PATH = AssetDomainConstants.LEGACY_FIELD_IMAGE_FOLDER_PATH;
    private static final String LEGACY_FIELD_IMAGE_FOLDER_PATH_V2 = AssetDomainConstants.LEGACY_FIELD_IMAGE_FOLDER_PATH_V2;
    private static final String CROP_COVER_FOLDER_PATH = AssetDomainConstants.CROP_COVER_FOLDER_PATH;
    private static final String LEGACY_CROP_COVER_FOLDER_PATH = AssetDomainConstants.LEGACY_CROP_COVER_FOLDER_PATH;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MediaAssetReferenceService mediaAssetReferenceService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Override
    public boolean save(MediaAsset entity) {
        boolean saved = super.save(entity);
        if (saved && entity != null && entity.getId() != null) {
            mediaAssetReferenceService.syncAssetReference(entity.getId());
        }
        return saved;
    }

    @Override
    public boolean updateById(MediaAsset entity) {
        boolean updated = super.updateById(entity);
        if (updated && entity != null && entity.getId() != null) {
            mediaAssetReferenceService.syncAssetReference(entity.getId());
        }
        return updated;
    }

    @Override
    public boolean removeById(Serializable id) {
        if (id instanceof Number) {
            mediaAssetReferenceService.deleteByAssetId(((Number) id).longValue());
        }
        return super.removeById(id);
    }

    @Override
    /**
     * 条件分页查询资源。
     * 说明：
     * 1. folder_path 与 source_type 做了历史兼容（如 legacy 路径、operator 来源）；
     * 2. strictSourceApprovedOnly=true 时，仅返回审核通过资源，避免严格来源数据泄漏。
     */
    public Page<MediaAsset> pageAssets(
            String keyword,
            String moduleKey,
            String fileType,
            Long bizId,
            String folderPath,
            String sourceType,
            Integer recycleFlag,
            Integer lockedFlag,
            String reviewStatus,
            boolean strictSourceApprovedOnly,
            long page,
            long pageSize
    ) {
        Page<MediaAsset> out = new Page<>(page, pageSize);
        LambdaQueryWrapper<MediaAsset> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(MediaAsset::getFileName, keyword.trim()).or().like(MediaAsset::getRemark, keyword.trim()));
        }
        if (StringUtils.hasText(moduleKey)) {
            qw.eq(MediaAsset::getModuleKey, moduleKey.trim());
        }
        if (StringUtils.hasText(fileType)) {
            qw.eq(MediaAsset::getFileType, fileType.trim());
        }
        if (bizId != null) {
            qw.eq(MediaAsset::getBizId, bizId);
        }
        if (StringUtils.hasText(folderPath)) {
            String normalizedFolderPath = normalizeFolderPath(folderPath);
            if (FIELD_IMAGE_FOLDER_PATH.equals(normalizedFolderPath)) {
                qw.and(w -> w.likeRight(MediaAsset::getFolderPath, FIELD_IMAGE_FOLDER_PATH)
                        .or()
                        .likeRight(MediaAsset::getFolderPath, LEGACY_FIELD_IMAGE_FOLDER_PATH)
                        .or()
                        .likeRight(MediaAsset::getFolderPath, LEGACY_FIELD_IMAGE_FOLDER_PATH_V2));
            } else if (CROP_COVER_FOLDER_PATH.equals(normalizedFolderPath)) {
                qw.and(w -> w.likeRight(MediaAsset::getFolderPath, CROP_COVER_FOLDER_PATH)
                        .or()
                        .likeRight(MediaAsset::getFolderPath, LEGACY_CROP_COVER_FOLDER_PATH));
            } else {
                qw.likeRight(MediaAsset::getFolderPath, normalizedFolderPath);
            }
        }
        if (StringUtils.hasText(sourceType)) {
            String normalizedSourceType = normalizeSourceType(sourceType);
            if (SOURCE_MINIAPP_UPLOAD.equals(normalizedSourceType)) {
                qw.and(w -> w.eq(MediaAsset::getSourceType, SOURCE_MINIAPP_UPLOAD)
                        .or()
                        .eq(MediaAsset::getSourceType, SOURCE_OPERATOR_UPLOAD));
            } else {
                qw.eq(MediaAsset::getSourceType, normalizedSourceType);
            }
        }
        if (recycleFlag != null) {
            qw.eq(MediaAsset::getRecycleFlag, recycleFlag);
        }
        if (lockedFlag != null) {
            qw.eq(MediaAsset::getLockedFlag, lockedFlag);
        }
        if (StringUtils.hasText(reviewStatus)) {
            qw.eq(MediaAsset::getReviewStatus, normalizeReviewStatus(reviewStatus));
        }
        if (strictSourceApprovedOnly) {
            qw.apply("COALESCE(NULLIF(TRIM(`review_status`),''),'approved') = 'approved'");
        }
        qw.orderByAsc(MediaAsset::getSortOrder).orderByDesc(MediaAsset::getCreatedAt).orderByDesc(MediaAsset::getId);
        return this.page(out, qw);
    }

    @Override
    /** 计算当前可用的下一个排序值。 */
    public int nextSortOrder() {
        LambdaQueryWrapper<MediaAsset> qw = new LambdaQueryWrapper<>();
        qw.eq(MediaAsset::getRecycleFlag, 0);
        qw.orderByDesc(MediaAsset::getSortOrder).last("limit 1");
        MediaAsset top = this.getOne(qw, false);
        if (top == null || top.getSortOrder() == null || top.getSortOrder() < 0) {
            return 1;
        }
        return top.getSortOrder() + 1;
    }

    @Override
    @Transactional
    /** 按传入 id 列表重排资源。未传到的资源会按原顺序追加在后。 */
    public void reorder(List<Long> ids) {
        List<MediaAsset> all = this.list(new LambdaQueryWrapper<MediaAsset>()
                .eq(MediaAsset::getRecycleFlag, 0)
                .orderByAsc(MediaAsset::getSortOrder)
                .orderByDesc(MediaAsset::getCreatedAt)
                .orderByDesc(MediaAsset::getId));
        if (all.isEmpty()) {
            return;
        }
        Map<Long, MediaAsset> byId = new HashMap<>();
        for (MediaAsset row : all) {
            if (row != null && row.getId() != null) {
                byId.put(row.getId(), row);
            }
        }
        List<MediaAsset> ordered = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id == null || used.contains(id)) {
                    continue;
                }
                MediaAsset row = byId.get(id);
                if (row == null) {
                    continue;
                }
                ordered.add(row);
                used.add(id);
            }
        }
        for (MediaAsset row : all) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (used.contains(row.getId())) {
                continue;
            }
            ordered.add(row);
        }

        // 统一重建连续排序，避免出现重复排序号或空洞。
        int order = 1;
        for (MediaAsset row : ordered) {
            if (row == null) {
                continue;
            }
            if (!Objects.equals(row.getSortOrder(), order)) {
                row.setSortOrder(order);
                this.updateById(row);
            }
            order += 1;
        }
    }

    @Override
    @Transactional
    /**
     * 批量绑定资源到业务实体。
     *
     * @param manageAll false 时仅允许操作“当前用户自己上传”的资源
     * @param unbindMissing true 时会解绑本次未传入的历史绑定资源
     */
    public void bindAssetsToBiz(
            String moduleKey,
            Long bizId,
            List<Long> assetIds,
            Long currentUserId,
            boolean manageAll,
            boolean unbindMissing,
            boolean recycleUnboundMissing
    ) {
        if (!StringUtils.hasText(moduleKey) || bizId == null || bizId <= 0) {
            return;
        }
        String normalizedModuleKey = moduleKey.trim();
        Set<Long> normalizedIds = normalizeIds(assetIds);

        LambdaQueryWrapper<MediaAsset> ownedQw = new LambdaQueryWrapper<>();
        ownedQw.eq(MediaAsset::getModuleKey, normalizedModuleKey)
                .eq(MediaAsset::getBizId, bizId)
                .eq(MediaAsset::getRecycleFlag, 0)
                .eq(MediaAsset::getFileType, AssetDomainConstants.FILE_TYPE_IMAGE);
        List<MediaAsset> boundRows = this.list(ownedQw);

        if (unbindMissing) {
            // 先处理解绑，确保“最终集合=传入集合”的语义成立。
            for (MediaAsset row : boundRows) {
                if (row == null || row.getId() == null) {
                    continue;
                }
                if (normalizedIds.contains(row.getId())) {
                    continue;
                }
                if (!manageAll && (currentUserId == null || !Objects.equals(currentUserId, row.getCreatedByUserId()))) {
                    continue;
                }
                if (recycleUnboundMissing) {
                    row.setRecycleFlag(1);
                    row.setRecycledAt(LocalDateTime.now());
                    row.setRecycledByUserId(currentUserId);
                    if (!StringUtils.hasText(row.getFolderPath())) {
                        row.setFolderPath(DEFAULT_FOLDER_PATH);
                    }
                    if (!StringUtils.hasText(row.getSourceType())) {
                        row.setSourceType(AssetDomainConstants.SOURCE_SYSTEM_UPLOAD);
                    }
                }
                row.setModuleKey(null);
                row.setBizId(null);
                this.updateById(row);
            }
        }

        if (normalizedIds.isEmpty()) {
            return;
        }

        // 再处理新绑定或重绑定。
        List<MediaAsset> pickedRows = this.listByIds(normalizedIds);
        for (MediaAsset row : pickedRows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (!isAssetBindable(row)) {
                continue;
            }
            if (!manageAll && (currentUserId == null || !Objects.equals(currentUserId, row.getCreatedByUserId()))) {
                continue;
            }
            if (Objects.equals(normalizedModuleKey, String.valueOf(row.getModuleKey()).trim())
                    && Objects.equals(bizId, row.getBizId())) {
                continue;
            }
            row.setModuleKey(normalizedModuleKey);
            row.setBizId(bizId);
            if (!StringUtils.hasText(row.getFileType())) {
                row.setFileType(AssetDomainConstants.FILE_TYPE_IMAGE);
            }
            this.updateById(row);
        }
    }

    @Override
    public List<MediaAsset> listBizAssets(String moduleKey, Long bizId, String fileType) {
        if (!StringUtils.hasText(moduleKey) || bizId == null || bizId <= 0) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<MediaAsset> qw = new LambdaQueryWrapper<>();
        qw.eq(MediaAsset::getModuleKey, moduleKey.trim())
                .eq(MediaAsset::getBizId, bizId)
                .eq(MediaAsset::getRecycleFlag, 0);
        if (StringUtils.hasText(fileType)) {
            qw.eq(MediaAsset::getFileType, AssetDomainConstants.normalizeFileType(fileType));
        }
        qw.orderByAsc(MediaAsset::getSortOrder)
                .orderByDesc(MediaAsset::getCreatedAt)
                .orderByDesc(MediaAsset::getId);
        return this.list(qw);
    }

    @Override
    /** 汇总资源总数、体积与按模块分布。 */
    public MediaAssetStats stats() {
        MediaAssetStats out = new MediaAssetStats();

        QueryWrapper<MediaAsset> totalQ = new QueryWrapper<>();
        totalQ.eq("recycle_flag", 0);
        totalQ.select("COUNT(1) AS totalCount", "COALESCE(SUM(size_bytes),0) AS totalSizeBytes");
        Map<String, Object> totalMap = this.getMap(totalQ);
        out.setTotalCount(toLong(totalMap == null ? null : totalMap.get("totalCount")));
        out.setTotalSizeBytes(toLong(totalMap == null ? null : totalMap.get("totalSizeBytes")));

        QueryWrapper<MediaAsset> typeQ = new QueryWrapper<>();
        typeQ.eq("recycle_flag", 0);
        typeQ.select("file_type AS fileType", "COUNT(1) AS cnt").groupBy("file_type");
        List<Map<String, Object>> typeRows = this.listMaps(typeQ);
        long imageCount = 0L;
        long fileCount = 0L;
        for (Map<String, Object> row : typeRows) {
            String fileType = String.valueOf(row.get("fileType") == null ? "" : row.get("fileType")).trim().toLowerCase();
            long cnt = toLong(row.get("cnt"));
            if (AssetDomainConstants.FILE_TYPE_IMAGE.equals(fileType)) {
                imageCount += cnt;
            } else {
                fileCount += cnt;
            }
        }
        out.setImageCount(imageCount);
        out.setFileCount(fileCount);

        QueryWrapper<MediaAsset> moduleQ = new QueryWrapper<>();
        moduleQ.eq("recycle_flag", 0);
        moduleQ.select(
                        "module_key AS moduleKey",
                        "COUNT(1) AS totalCount",
                        "COALESCE(SUM(size_bytes),0) AS totalSizeBytes",
                        "SUM(CASE WHEN file_type='image' THEN 1 ELSE 0 END) AS imageCount",
                        "SUM(CASE WHEN file_type='file' THEN 1 ELSE 0 END) AS fileCount"
                )
                .groupBy("module_key")
                .orderByAsc("module_key");
        List<Map<String, Object>> moduleRows = this.listMaps(moduleQ);
        List<MediaAssetStats.ModuleStat> stats = new ArrayList<>();
        for (Map<String, Object> row : moduleRows) {
            MediaAssetStats.ModuleStat item = new MediaAssetStats.ModuleStat();
            String moduleKey = String.valueOf(row.get("moduleKey") == null ? "" : row.get("moduleKey")).trim();
            item.setModuleKey(StringUtils.hasText(moduleKey) ? moduleKey : "");
            item.setTotalCount(toLong(row.get("totalCount")));
            item.setImageCount(toLong(row.get("imageCount")));
            item.setFileCount(toLong(row.get("fileCount")));
            item.setTotalSizeBytes(toLong(row.get("totalSizeBytes")));
            stats.add(item);
        }
        out.setModuleStats(stats);
        return out;
    }

    @Override
    /** 统计资源被业务实体引用的分组信息。 */
    public MediaAssetReferenceStats referenceStats() {
        MediaAssetReferenceStats out = new MediaAssetReferenceStats();

        QueryWrapper<MediaAsset> refQ = new QueryWrapper<>();
        refQ.select(
                        "module_key AS moduleKey",
                        "biz_id AS bizId",
                        "COUNT(1) AS assetCount",
                        "SUM(CASE WHEN file_type='image' THEN 1 ELSE 0 END) AS imageCount",
                        "SUM(CASE WHEN file_type='file' THEN 1 ELSE 0 END) AS fileCount",
                        "MAX(created_at) AS latestCreatedAt"
                )
                .eq("recycle_flag", 0)
                .isNotNull("biz_id")
                .groupBy("module_key", "biz_id")
                .orderByDesc("assetCount")
                .orderByAsc("module_key")
                .orderByAsc("biz_id");
        List<Map<String, Object>> refRows = this.listMaps(refQ);
        List<MediaAssetReferenceStats.RefItem> refs = new ArrayList<>();
        for (Map<String, Object> row : refRows) {
            MediaAssetReferenceStats.RefItem item = new MediaAssetReferenceStats.RefItem();
            String moduleKey = String.valueOf(row.get("moduleKey") == null ? "" : row.get("moduleKey")).trim();
            item.setModuleKey(StringUtils.hasText(moduleKey) ? moduleKey : "");
            item.setBizId(toNullableLong(row.get("bizId")));
            item.setAssetCount(toLong(row.get("assetCount")));
            item.setImageCount(toLong(row.get("imageCount")));
            item.setFileCount(toLong(row.get("fileCount")));
            Object latest = row.get("latestCreatedAt");
            item.setLatestCreatedAt(latest == null ? "" : String.valueOf(latest));
            refs.add(item);
        }
        out.setTotalRefs(refs.size());
        out.setRefs(refs);
        return out;
    }

    @Override
    @Transactional
    /** 将资源移入回收站，并清空业务绑定关系。 */
    public boolean markRecycled(Long id, Long operatorUserId) {
        if (id == null || id <= 0) {
            return false;
        }
        MediaAsset row = this.getById(id);
        if (row == null) {
            return false;
        }
        if (Objects.equals(row.getRecycleFlag(), 1)) {
            return true;
        }
        row.setRecycleFlag(1);
        row.setRecycledAt(LocalDateTime.now());
        row.setRecycledByUserId(operatorUserId);
        // 进入回收站的资源不能继续作为业务资源使用，必须解绑。
        row.setModuleKey(null);
        row.setBizId(null);
        if (!StringUtils.hasText(row.getFolderPath())) {
            row.setFolderPath(DEFAULT_FOLDER_PATH);
        }
        if (!StringUtils.hasText(row.getSourceType())) {
            row.setSourceType(AssetDomainConstants.SOURCE_SYSTEM_UPLOAD);
        }
        return this.updateById(row);
    }

    @Override
    @Transactional
    /** 从回收站恢复资源。 */
    public boolean restoreFromRecycle(Long id) {
        if (id == null || id <= 0) {
            return false;
        }
        MediaAsset row = this.getById(id);
        if (row == null) {
            return false;
        }
        row.setRecycleFlag(0);
        row.setRecycledAt(null);
        row.setRecycledByUserId(null);
        if (!StringUtils.hasText(row.getFolderPath())) {
            row.setFolderPath(DEFAULT_FOLDER_PATH);
        }
        if (!StringUtils.hasText(row.getSourceType())) {
            row.setSourceType(AssetDomainConstants.SOURCE_SYSTEM_UPLOAD);
        }
        return this.updateById(row);
    }

    @Override
    @Transactional
    /** 彻底删除资源记录（物理删除）。 */
    public boolean purgeAsset(Long id) {
        if (id == null || id <= 0) {
            return false;
        }
        MediaAsset row = this.getById(id);
        if (row == null) {
            return false;
        }
        mediaAssetReferenceService.deleteByAssetId(id);
        boolean removed = jdbcTemplate.update("DELETE FROM `media_asset` WHERE `id`=?", id) > 0;
        if (removed) {
            mediaAssetReferenceService.deleteByAssetId(id);
            deleteLocalUploadFile(row.getFileUrl());
        }
        return removed;
    }

    @Override
    /**
     * 查询文件夹路径列表。
     * 结果同时合并 media_asset 和 media_asset_folder 两侧数据，并补齐系统默认路径。
     */
    public List<String> listFolderPaths(Integer recycleFlag) {
        QueryWrapper<MediaAsset> qw = new QueryWrapper<>();
        qw.select("DISTINCT folder_path AS folderPath");
        if (recycleFlag != null) {
            qw.eq("recycle_flag", recycleFlag);
        }
        qw.orderByAsc("folder_path");
        List<Map<String, Object>> rows = this.listMaps(qw);
        List<String> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            String path = normalizeFolderPath(String.valueOf(row.get("folderPath") == null ? "" : row.get("folderPath")));
            if (!StringUtils.hasText(path)) {
                continue;
            }
            out.add(path);
        }
        try {
            // 兼容“目录先创建、还未上传资源”的场景：补充目录注册表中的路径。
            List<Map<String, Object>> folderRows = jdbcTemplate.queryForList(
                    "SELECT `folder_path` AS folderPath FROM `media_asset_folder` WHERE `deleted`=0 ORDER BY `folder_path` ASC"
            );
            for (Map<String, Object> row : folderRows) {
                if (row == null) {
                    continue;
                }
                String path = normalizeFolderPath(String.valueOf(row.get("folderPath") == null ? "" : row.get("folderPath")));
                if (!StringUtils.hasText(path)) {
                    continue;
                }
                out.add(path);
            }
        } catch (Exception ignored) {
            // 不阻断主流程：目录表不是强依赖。
        }
        if (!out.contains(DEFAULT_FOLDER_PATH)) {
            out.add(DEFAULT_FOLDER_PATH);
        }
        if (!out.contains(AssetDomainConstants.MINIAPP_UPLOAD_IMAGE_FOLDER_PATH)) {
            out.add(AssetDomainConstants.MINIAPP_UPLOAD_IMAGE_FOLDER_PATH);
        }
        out = new ArrayList<>(new LinkedHashSet<>(out));
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    @Override
    /** 统计指定来源某天的上传次数与容量。 */
    public MediaAssetUploadUsage queryDailyUsage(String sourceType, LocalDate date) {
        MediaAssetUploadUsage out = new MediaAssetUploadUsage();
        LocalDate usageDate = date == null ? LocalDate.now() : date;
        String normalizedSourceType = normalizeSourceType(sourceType);
        out.setUsageDate(usageDate);
        out.setSourceType(normalizedSourceType);
        out.setUploadCount(0L);
        out.setUploadSizeBytes(0L);

        QueryWrapper<MediaAsset> qw = new QueryWrapper<>();
        qw.select("COUNT(1) AS uploadCount", "COALESCE(SUM(size_bytes),0) AS uploadSizeBytes");
        if (SOURCE_MINIAPP_UPLOAD.equals(normalizedSourceType)) {
            qw.and(w -> w.eq("source_type", SOURCE_MINIAPP_UPLOAD).or().eq("source_type", SOURCE_OPERATOR_UPLOAD));
        } else {
            qw.eq("source_type", normalizedSourceType);
        }
        qw
                .ge("created_at", usageDate.atStartOfDay())
                .lt("created_at", usageDate.plusDays(1).atStartOfDay());
        Map<String, Object> row = this.getMap(qw);
        if (row != null && !row.isEmpty()) {
            out.setUploadCount(toLong(row.get("uploadCount")));
            out.setUploadSizeBytes(toLong(row.get("uploadSizeBytes")));
        }
        return out;
    }

    /** 安全地把对象转为 long，异常返回 0。 */
    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(text);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    /** 安全地把对象转为 Long，空或异常返回 null。 */
    private Long toNullableLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 过滤并去重资源 id。 */
    private Set<Long> normalizeIds(List<Long> ids) {
        Set<Long> out = new LinkedHashSet<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        for (Long id : ids) {
            if (id != null && id > 0) {
                out.add(id);
            }
        }
        return out;
    }

    /** 删除资源对应的本地上传文件，仅处理 /uploads/** 路径。 */
    private void deleteLocalUploadFile(String fileUrl) {
        String relative = resolveUploadRelativePath(fileUrl);
        if (!StringUtils.hasText(relative)) {
            return;
        }
        try {
            Path root = Paths.get(StringUtils.hasText(uploadDir) ? uploadDir : "uploads").toAbsolutePath().normalize();
            Path target = root.resolve(relative).normalize();
            if (!target.startsWith(root)) {
                return;
            }
            Files.deleteIfExists(target);
        } catch (Exception ex) {
            log.warn("Delete asset file failed, url={}, err={}", fileUrl, ex.getMessage());
        }
    }

    private String resolveUploadRelativePath(String fileUrl) {
        String url = String.valueOf(fileUrl == null ? "" : fileUrl).trim();
        if (!StringUtils.hasText(url)) {
            return null;
        }
        int index = url.indexOf("/uploads/");
        if (index < 0) {
            return null;
        }
        String relative = url.substring(index + "/uploads/".length());
        int queryIndex = relative.indexOf('?');
        if (queryIndex >= 0) {
            relative = relative.substring(0, queryIndex);
        }
        relative = relative.replace('\\', '/');
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        return StringUtils.hasText(relative) ? relative : null;
    }

    /** 规范化文件夹路径，并处理 legacy 路径别名。 */
    private String normalizeFolderPath(String raw) {
        String path = StringUtils.hasText(raw) ? raw.trim() : DEFAULT_FOLDER_PATH;
        path = path.replace('\\', '/');
        while (path.contains("//")) {
            path = path.replace("//", "/");
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (!StringUtils.hasText(path)) {
            return DEFAULT_FOLDER_PATH;
        }
        String normalizedLower = path.toLowerCase(Locale.ROOT);
        if (LEGACY_DEFAULT_FOLDER_PATH.equals(normalizedLower)) {
            return DEFAULT_FOLDER_PATH;
        }
        if (LEGACY_FIELD_IMAGE_FOLDER_PATH.equals(normalizedLower) || LEGACY_FIELD_IMAGE_FOLDER_PATH_V2.equals(path)) {
            return FIELD_IMAGE_FOLDER_PATH;
        }
        if (LEGACY_CROP_COVER_FOLDER_PATH.equals(normalizedLower)) {
            return CROP_COVER_FOLDER_PATH;
        }
        if (path.length() > 255) {
            return path.substring(0, 255);
        }
        return path;
    }

    /** 审核状态归一化：仅保留 pending/rejected，其余视为 approved。 */
    private String normalizeReviewStatus(String value) {
        return AssetDomainConstants.normalizeReviewStatus(value);
    }

    /** 来源归一化：operator 统一并入 miniapp。 */
    private String normalizeSourceType(String sourceType) {
        String text = String.valueOf(sourceType == null ? "" : sourceType).trim().toLowerCase();
        if (SOURCE_OPERATOR_UPLOAD.equals(text) || SOURCE_MINIAPP_UPLOAD.equals(text) || !StringUtils.hasText(text)) {
            return SOURCE_MINIAPP_UPLOAD;
        }
        if (SOURCE_ADMIN_UPLOAD.equals(text)) {
            return SOURCE_ADMIN_UPLOAD;
        }
        return text;
    }

    /** 仅允许“未回收且文件类型有效”的资源参与业务绑定；审核状态交给业务展示层控制。 */
    private boolean isAssetBindable(MediaAsset row) {
        if (row == null) {
            return false;
        }
        if (Objects.equals(row.getRecycleFlag(), 1)) {
            return false;
        }
        String fileType = AssetDomainConstants.normalizeFileType(row.getFileType());
        if (!AssetDomainConstants.FILE_TYPE_IMAGE.equals(fileType)) {
            return false;
        }
        return true;
    }
}
