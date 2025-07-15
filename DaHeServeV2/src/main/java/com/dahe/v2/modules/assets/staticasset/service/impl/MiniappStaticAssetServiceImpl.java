package com.dahe.v2.modules.assets.staticasset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.modules.assets.domain.AssetDomainConstants;
import com.dahe.v2.modules.assets.service.MediaAssetContentValidationService;
import com.dahe.v2.modules.assets.staticasset.mapper.MiniappStaticAssetMapper;
import com.dahe.v2.modules.assets.staticasset.model.MiniappStaticAsset;
import com.dahe.v2.modules.assets.staticasset.service.MiniappStaticAssetService;
import com.dahe.v2.modules.assets.staticasset.service.MiniappStaticAssetServiceException;
import com.dahe.v2.modules.auth.policy.AuthUserPolicy;
import com.dahe.v2.modules.user.model.AppUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 小程序静态资源服务实现。
 */
@Service
public class MiniappStaticAssetServiceImpl implements MiniappStaticAssetService {

    private final MiniappStaticAssetMapper miniappStaticAssetMapper;
    private final MediaAssetContentValidationService mediaAssetContentValidationService;
    private final AuthUserPolicy authUserPolicy;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.miniapp-static-assets.dir:/assets}")
    private String miniappStaticAssetDir;

    @Value("${app.upload.public-base-url:}")
    private String uploadPublicBaseUrl;

    public MiniappStaticAssetServiceImpl(
            MiniappStaticAssetMapper miniappStaticAssetMapper,
            MediaAssetContentValidationService mediaAssetContentValidationService,
            AuthUserPolicy authUserPolicy,
            PasswordEncoder passwordEncoder
    ) {
        this.miniappStaticAssetMapper = miniappStaticAssetMapper;
        this.mediaAssetContentValidationService = mediaAssetContentValidationService;
        this.authUserPolicy = authUserPolicy;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Page<MiniappStaticAsset> pageAssets(String keyword, long page, long pageSize) {
        long current = Math.max(1L, page);
        long size = Math.max(1L, Math.min(pageSize, 100L));
        LambdaQueryWrapper<MiniappStaticAsset> wrapper = new LambdaQueryWrapper<MiniappStaticAsset>();
        if (StringUtils.hasText(keyword)) {
            String text = keyword.trim();
            wrapper.and(w -> w.like(MiniappStaticAsset::getDisplayName, text)
                    .or().like(MiniappStaticAsset::getStorageName, text)
                    .or().like(MiniappStaticAsset::getRemark, text));
        }
        wrapper.orderByDesc(MiniappStaticAsset::getUpdatedAt).orderByDesc(MiniappStaticAsset::getId);
        Page<MiniappStaticAsset> out = new Page<MiniappStaticAsset>(current, size);
        return miniappStaticAssetMapper.selectPage(out, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MiniappStaticAsset upload(MultipartFile file, String storageName, String displayName, String remark, AppUser currentUser, HttpServletRequest request) {
        ensureSuperAdmin(currentUser);
        if (file == null || file.isEmpty()) {
            throw new MiniappStaticAssetServiceException(ErrorCode.VALIDATION_ERROR.getCode(), "请选择要上传的文件");
        }
        String normalizedStorageName = normalizeStorageName(storageName);
        ensureStorageNameAvailable(normalizedStorageName, null);
        MediaAssetContentValidationService.DetectionResult detection = mediaAssetContentValidationService.validateForUpload(file, file.getOriginalFilename());
        String extension = String.valueOf(detection == null ? "" : detection.getStorageExtension()).trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(extension)) {
            extension = resolveOriginalExtension(file.getOriginalFilename());
        }
        if (!StringUtils.hasText(extension)) {
            throw new MiniappStaticAssetServiceException(ErrorCode.VALIDATION_ERROR.getCode(), "无法识别文件后缀");
        }
        Path root = resolveStaticRoot();
        String storedFileName = normalizedStorageName + extension;
        Path target = root.resolve(storedFileName).normalize();
        if (!target.startsWith(root)) {
            throw new MiniappStaticAssetServiceException(ErrorCode.VALIDATION_ERROR.getCode(), "非法文件名");
        }
        try {
            Files.createDirectories(root);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new MiniappStaticAssetServiceException(ErrorCode.INTERNAL_ERROR.getCode(), "保存静态资源失败");
        }
        MiniappStaticAsset entity = new MiniappStaticAsset();
        entity.setDisplayName(resolveDisplayName(displayName, normalizedStorageName));
        entity.setStorageName(normalizedStorageName);
        entity.setFileExt(extension);
        entity.setFileType(normalizeFileType(detection == null ? null : detection.getFileType()));
        entity.setFileUrl(buildPublicUrl(request, "/assets/" + storedFileName));
        entity.setSizeBytes(file.getSize());
        entity.setRemark(trimToNull(remark));
        entity.setCreatedByUserId(currentUser == null ? null : currentUser.getId());
        entity.setCreatedByName(resolveOperatorName(currentUser));
        entity.setUpdatedByUserId(currentUser == null ? null : currentUser.getId());
        entity.setUpdatedByName(resolveOperatorName(currentUser));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        miniappStaticAssetMapper.insert(entity);
        return miniappStaticAssetMapper.selectById(entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MiniappStaticAsset updateMeta(Long id, String displayName, String remark, AppUser currentUser) {
        ensureSuperAdmin(currentUser);
        MiniappStaticAsset row = requireAsset(id);
        row.setDisplayName(resolveDisplayName(displayName, row.getStorageName()));
        row.setRemark(trimToNull(remark));
        row.setUpdatedByUserId(currentUser == null ? null : currentUser.getId());
        row.setUpdatedByName(resolveOperatorName(currentUser));
        row.setUpdatedAt(LocalDateTime.now());
        miniappStaticAssetMapper.updateById(row);
        return miniappStaticAssetMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAsset(Long id, String superAdminPassword, AppUser currentUser) {
        ensureSuperAdmin(currentUser);
        String password = String.valueOf(superAdminPassword == null ? "" : superAdminPassword).trim();
        if (!StringUtils.hasText(password)) {
            throw new MiniappStaticAssetServiceException(ErrorCode.VALIDATION_ERROR.getCode(), "请输入超级管理员密码");
        }
        if (!StringUtils.hasText(currentUser == null ? null : currentUser.getPasswordHash())
                || !passwordEncoder.matches(password, currentUser.getPasswordHash())) {
            throw new MiniappStaticAssetServiceException(ErrorCode.VALIDATION_ERROR.getCode(), "超级管理员密码不正确");
        }
        MiniappStaticAsset row = requireAsset(id);
        deletePhysicalFile(row);
        miniappStaticAssetMapper.deleteById(id);
    }

    private void deletePhysicalFile(MiniappStaticAsset row) {
        if (row == null || !StringUtils.hasText(row.getStorageName()) || !StringUtils.hasText(row.getFileExt())) {
            return;
        }
        Path root = resolveStaticRoot();
        Path target = root.resolve(row.getStorageName().trim() + row.getFileExt().trim()).normalize();
        if (!target.startsWith(root)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            throw new MiniappStaticAssetServiceException(ErrorCode.INTERNAL_ERROR.getCode(), "删除服务器静态资源文件失败");
        }
    }

    private MiniappStaticAsset requireAsset(Long id) {
        if (id == null || id <= 0) {
            throw new MiniappStaticAssetServiceException(ErrorCode.VALIDATION_ERROR.getCode(), "资源不存在");
        }
        MiniappStaticAsset row = miniappStaticAssetMapper.selectById(id);
        if (row == null) {
            throw new MiniappStaticAssetServiceException(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        return row;
    }

    private void ensureSuperAdmin(AppUser currentUser) {
        if (!authUserPolicy.isSuperAdmin(currentUser)) {
            throw new MiniappStaticAssetServiceException(ErrorCode.UNAUTHORIZED.getCode(), "仅超级管理员可管理小程序静态资源");
        }
    }

    private void ensureStorageNameAvailable(String storageName, Long excludeId) {
        LambdaQueryWrapper<MiniappStaticAsset> wrapper = new LambdaQueryWrapper<MiniappStaticAsset>()
                .eq(MiniappStaticAsset::getStorageName, storageName);
        if (excludeId != null && excludeId > 0) {
            wrapper.ne(MiniappStaticAsset::getId, excludeId);
        }
        Long count = miniappStaticAssetMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new MiniappStaticAssetServiceException(ErrorCode.VALIDATION_ERROR.getCode(), "保存文件名已存在，请更换后重试");
        }
    }

    private String normalizeStorageName(String storageName) {
        String text = String.valueOf(storageName == null ? "" : storageName).trim();
        if (!StringUtils.hasText(text)) {
            throw new MiniappStaticAssetServiceException(ErrorCode.VALIDATION_ERROR.getCode(), "请填写保存文件名称");
        }
        if (text.contains(".")) {
            throw new MiniappStaticAssetServiceException(ErrorCode.VALIDATION_ERROR.getCode(), "保存文件名称不需要填写后缀");
        }
        if (text.length() > 64) {
            throw new MiniappStaticAssetServiceException(ErrorCode.VALIDATION_ERROR.getCode(), "保存文件名称长度不能超过64位");
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean valid = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '-'
                    || c == '_';
            if (!valid) {
                throw new MiniappStaticAssetServiceException(ErrorCode.VALIDATION_ERROR.getCode(), "保存文件名称仅支持字母、数字、中划线和下划线");
            }
        }
        return text;
    }

    private String resolveDisplayName(String displayName, String fallback) {
        String text = trimToNull(displayName);
        if (StringUtils.hasText(text)) {
            return text.length() > 120 ? text.substring(0, 120) : text;
        }
        return fallback;
    }

    private String trimToNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim();
    }

    private String normalizeFileType(String fileType) {
        String text = String.valueOf(fileType == null ? "" : fileType).trim().toLowerCase(Locale.ROOT);
        if (AssetDomainConstants.FILE_TYPE_IMAGE.equals(text)) {
            return AssetDomainConstants.FILE_TYPE_IMAGE;
        }
        return AssetDomainConstants.FILE_TYPE_FILE;
    }

    private String resolveOriginalExtension(String originalFileName) {
        String text = String.valueOf(originalFileName == null ? "" : originalFileName).trim();
        int index = text.lastIndexOf('.');
        if (index < 0 || index >= text.length() - 1) {
            return "";
        }
        return text.substring(index).trim().toLowerCase(Locale.ROOT);
    }

    private String resolveOperatorName(AppUser currentUser) {
        if (currentUser == null) {
            return "系统";
        }
        if (StringUtils.hasText(currentUser.getRealName())) {
            return currentUser.getRealName().trim();
        }
        if (StringUtils.hasText(currentUser.getNickName())) {
            return currentUser.getNickName().trim();
        }
        if (StringUtils.hasText(currentUser.getLoginName())) {
            return currentUser.getLoginName().trim();
        }
        return "管理员";
    }

    private Path resolveStaticRoot() {
        return Paths.get(miniappStaticAssetDir).toAbsolutePath().normalize();
    }

    private String buildPublicUrl(HttpServletRequest request, String path) {
        String p = path.startsWith("/") ? path : ("/" + path);
        String configuredBaseUrl = String.valueOf(uploadPublicBaseUrl == null ? "" : uploadPublicBaseUrl).trim();
        if (StringUtils.hasText(configuredBaseUrl)) {
            while (configuredBaseUrl.endsWith("/")) {
                configuredBaseUrl = configuredBaseUrl.substring(0, configuredBaseUrl.length() - 1);
            }
            return configuredBaseUrl + p;
        }
        if (request == null) {
            return p;
        }
        String scheme = firstForwardedValue(request.getHeader("X-Forwarded-Proto"));
        if (!StringUtils.hasText(scheme)) {
            scheme = request.getScheme();
        }
        String forwardedHost = firstForwardedValue(request.getHeader("X-Forwarded-Host"));
        if (StringUtils.hasText(forwardedHost)) {
            String normalizedForwardedHost = normalizeHost(forwardedHost);
            String forwardedPort = firstForwardedValue(request.getHeader("X-Forwarded-Port"));
            if (StringUtils.hasText(forwardedPort) && !hostContainsPort(normalizedForwardedHost)) {
                try {
                    int parsedForwardedPort = Integer.parseInt(forwardedPort);
                    normalizedForwardedHost = appendPort(normalizedForwardedHost, parsedForwardedPort, scheme);
                } catch (NumberFormatException ignored) {
                }
            }
            return scheme + "://" + normalizedForwardedHost + p;
        }
        String host = request.getServerName();
        int port = request.getServerPort();
        String forwardedPort = firstForwardedValue(request.getHeader("X-Forwarded-Port"));
        if (StringUtils.hasText(forwardedPort)) {
            try {
                port = Integer.parseInt(forwardedPort);
            } catch (NumberFormatException ignored) {
            }
        }
        boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80) || ("https".equalsIgnoreCase(scheme) && port == 443);
        String portPart = defaultPort ? "" : (":" + port);
        return scheme + "://" + host + portPart + p;
    }

    private String firstForwardedValue(String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            return "";
        }
        String[] segments = headerValue.split(",");
        if (segments.length == 0) {
            return "";
        }
        return String.valueOf(segments[0]).trim();
    }

    private String normalizeHost(String hostValue) {
        String host = String.valueOf(hostValue == null ? "" : hostValue).trim();
        if (!StringUtils.hasText(host)) {
            return "";
        }
        int schemeIndex = host.indexOf("://");
        if (schemeIndex >= 0 && schemeIndex + 3 < host.length()) {
            host = host.substring(schemeIndex + 3);
        }
        int slashIndex = host.indexOf('/');
        if (slashIndex >= 0) {
            host = host.substring(0, slashIndex);
        }
        return host.trim();
    }

    private boolean hostContainsPort(String host) {
        if (!StringUtils.hasText(host)) {
            return false;
        }
        if (host.startsWith("[")) {
            int rightBracket = host.indexOf(']');
            return rightBracket >= 0 && rightBracket < host.length() - 1 && host.charAt(rightBracket + 1) == ':';
        }
        return host.indexOf(':') > 0;
    }

    private String appendPort(String host, int port, String scheme) {
        boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80) || ("https".equalsIgnoreCase(scheme) && port == 443);
        if (defaultPort || port <= 0) {
            return host;
        }
        return host + ":" + port;
    }
}

