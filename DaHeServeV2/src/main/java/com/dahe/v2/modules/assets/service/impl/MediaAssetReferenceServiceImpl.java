package com.dahe.v2.modules.assets.service.impl;

import com.dahe.v2.modules.assets.service.MediaAssetReferenceService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 资源当前有效引用服务实现。
 */
@Service
public class MediaAssetReferenceServiceImpl implements MediaAssetReferenceService {

    private final JdbcTemplate jdbcTemplate;

    public MediaAssetReferenceServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void rebuildAllCurrentReferences() {
        jdbcTemplate.update("DELETE FROM `media_asset_reference`");
        jdbcTemplate.update(
                "INSERT INTO `media_asset_reference` (`id`,`asset_id`,`module_key`,`biz_id`,`created_at`,`updated_at`) " +
                        "SELECT UUID_SHORT(), `id`, TRIM(`module_key`), `biz_id`, NOW(), NOW() " +
                        "FROM `media_asset` " +
                        "WHERE `deleted`=0 AND `recycle_flag`=0 AND `biz_id` IS NOT NULL " +
                        "AND NULLIF(TRIM(`module_key`),'') IS NOT NULL"
        );
    }

    @Override
    @Transactional
    public void syncAssetReference(Long assetId) {
        if (assetId == null || assetId <= 0) {
            return;
        }
        deleteByAssetId(assetId);
        jdbcTemplate.update(
                "INSERT INTO `media_asset_reference` (`id`,`asset_id`,`module_key`,`biz_id`,`created_at`,`updated_at`) " +
                        "SELECT UUID_SHORT(), `id`, TRIM(`module_key`), `biz_id`, NOW(), NOW() " +
                        "FROM `media_asset` " +
                        "WHERE `id`=? AND `deleted`=0 AND `recycle_flag`=0 AND `biz_id` IS NOT NULL " +
                        "AND NULLIF(TRIM(`module_key`),'') IS NOT NULL",
                assetId
        );
    }

    @Override
    @Transactional
    public void deleteByAssetId(Long assetId) {
        if (assetId == null || assetId <= 0) {
            return;
        }
        jdbcTemplate.update("DELETE FROM `media_asset_reference` WHERE `asset_id`=?", assetId);
    }

    @Override
    public List<Map<String, Object>> listReferenceDetails(Long assetId, long offset, int limit) {
        if (assetId == null || assetId <= 0) {
            return Collections.emptyList();
        }
        long safeOffset = Math.max(0L, offset);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return jdbcTemplate.queryForList(
                "SELECT `asset_id` AS assetId, `module_key` AS moduleKey, `biz_id` AS bizId, `created_at` AS createdAt " +
                        "FROM `media_asset_reference` WHERE `asset_id`=? ORDER BY `created_at` ASC, `id` ASC LIMIT ? OFFSET ?",
                assetId,
                safeLimit,
                safeOffset
        );
    }

    @Override
    public long countReferenceDetails(Long assetId) {
        if (assetId == null || assetId <= 0) {
            return 0L;
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM `media_asset_reference` WHERE `asset_id`=?",
                Long.class,
                assetId
        );
        return count == null ? 0L : count;
    }
}
