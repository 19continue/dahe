package com.dahe.v2.modules.assets.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dahe.v2.modules.assets.domain.AssetDomainConstants;
import com.dahe.v2.modules.assets.model.MediaAsset;
import com.dahe.v2.modules.assets.service.MediaAssetBindingService;
import com.dahe.v2.modules.assets.service.MediaAssetService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 资源绑定协调服务实现。
 */
@Service
public class MediaAssetBindingServiceImpl implements MediaAssetBindingService {

    private final MediaAssetService mediaAssetService;
    private final JdbcTemplate jdbcTemplate;

    public MediaAssetBindingServiceImpl(MediaAssetService mediaAssetService, JdbcTemplate jdbcTemplate) {
        this.mediaAssetService = mediaAssetService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void bindByUrls(String moduleKey, Long bizId, Collection<String> fileUrls) {
        if (!StringUtils.hasText(moduleKey) || bizId == null || bizId <= 0) {
            return;
        }
        Set<String> normalizedUrls = normalizeUrls(fileUrls);
        List<Long> assetIds = resolveAssetIdsByUrls(normalizedUrls);
        mediaAssetService.bindAssetsToBiz(moduleKey.trim(), bizId, assetIds, null, true, true, false);
    }

    @Override
    public void clearBinding(String moduleKey, Long bizId) {
        if (!StringUtils.hasText(moduleKey) || bizId == null || bizId <= 0) {
            return;
        }
        mediaAssetService.bindAssetsToBiz(moduleKey.trim(), bizId, new ArrayList<Long>(), null, true, true, false);
    }

    private Set<String> normalizeUrls(Collection<String> fileUrls) {
        Set<String> out = new LinkedHashSet<String>();
        if (fileUrls == null || fileUrls.isEmpty()) {
            return out;
        }
        for (String fileUrl : fileUrls) {
            String normalized = normalizeUrl(fileUrl);
            if (StringUtils.hasText(normalized)) {
                out.add(normalized);
            }
        }
        return out;
    }

    private List<Long> resolveAssetIdsByUrls(Set<String> fileUrls) {
        if (fileUrls == null || fileUrls.isEmpty()) {
            return new ArrayList<Long>();
        }
        Set<String> relativePaths = new LinkedHashSet<String>();
        for (String fileUrl : fileUrls) {
            String relativePath = resolveUploadRelativePath(fileUrl);
            if (StringUtils.hasText(relativePath)) {
                relativePaths.add(relativePath);
            }
        }
        List<MediaAsset> rows = mediaAssetService.list(new LambdaQueryWrapper<MediaAsset>()
                .in(MediaAsset::getFileUrl, fileUrls)
                .eq(MediaAsset::getRecycleFlag, 0)
                .eq(MediaAsset::getDeleted, 0)
                .eq(MediaAsset::getFileType, AssetDomainConstants.FILE_TYPE_IMAGE));
        Set<Long> assetIds = new LinkedHashSet<Long>();
        Set<String> matchedRelativePaths = new HashSet<String>();
        for (MediaAsset row : rows) {
            if (row != null && row.getId() != null) {
                assetIds.add(row.getId());
                String relativePath = resolveUploadRelativePath(row.getFileUrl());
                if (StringUtils.hasText(relativePath)) {
                    matchedRelativePaths.add(relativePath);
                }
            }
        }
        relativePaths.removeAll(matchedRelativePaths);
        if (!relativePaths.isEmpty()) {
            assetIds.addAll(resolveAssetIdsByRelativePaths(relativePaths));
        }
        return new ArrayList<Long>(assetIds);
    }

    private Set<Long> resolveAssetIdsByRelativePaths(Set<String> relativePaths) {
        Set<Long> out = new LinkedHashSet<Long>();
        if (relativePaths == null || relativePaths.isEmpty()) {
            return out;
        }
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT `id`,`file_url` FROM `media_asset` WHERE `deleted`=0 AND `recycle_flag`=0 ")
                .append("AND `file_type`=? AND (");
        List<Object> args = new ArrayList<Object>();
        args.add(AssetDomainConstants.FILE_TYPE_IMAGE);
        int index = 0;
        for (String relativePath : relativePaths) {
            if (!StringUtils.hasText(relativePath)) {
                continue;
            }
            if (index++ > 0) {
                sql.append(" OR ");
            }
            sql.append("LOWER(`file_url`) LIKE ?");
            args.add("%/uploads/" + relativePath.toLowerCase(Locale.ROOT));
        }
        if (index <= 0) {
            return out;
        }
        sql.append(")");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            Object id = row.get("id");
            if (id instanceof Number) {
                out.add(((Number) id).longValue());
            }
        }
        return out;
    }

    private String normalizeUrl(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return "";
        }
        String normalized = fileUrl.trim();
        int hashIndex = normalized.indexOf('#');
        if (hashIndex >= 0) {
            normalized = normalized.substring(0, hashIndex);
        }
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        return normalized.trim();
    }

    private String resolveUploadRelativePath(String fileUrl) {
        String normalized = normalizeUrl(fileUrl);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        int index = lower.indexOf("/uploads/");
        if (index < 0) {
            return null;
        }
        String relative = normalized.substring(index + "/uploads/".length());
        relative = relative.replace('\\', '/').trim();
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        return StringUtils.hasText(relative) ? relative : null;
    }
}
