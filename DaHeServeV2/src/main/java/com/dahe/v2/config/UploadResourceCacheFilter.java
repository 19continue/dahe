package com.dahe.v2.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriUtils;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Adds cache headers and conditional request handling for uploaded static resources.
 *
 * <p>Only /uploads/** is handled.
 * Business APIs are not affected.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
public class UploadResourceCacheFilter extends OncePerRequestFilter {

    private static final String UPLOAD_PREFIX = "/uploads/";
    private static final String STATIC_ASSET_PREFIX = "/assets/";

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.miniapp-static-assets.dir:/assets}")
    private String miniappStaticAssetDir;

    @Value("${app.upload.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${app.upload.cache.max-age-seconds:604800}")
    private long cacheMaxAgeSeconds;

    @Value("${app.upload.cache.immutable:true}")
    private boolean cacheImmutable;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!cacheEnabled) {
            return true;
        }
        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            return true;
        }
        String uri = request.getRequestURI();
        return !uri.startsWith(UPLOAD_PREFIX) && !uri.startsWith(STATIC_ASSET_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Path filePath = resolveFilePath(request.getRequestURI());
        if (filePath == null || !Files.isRegularFile(filePath)) {
            filterChain.doFilter(request, response);
            return;
        }

        long lastModified = Files.getLastModifiedTime(filePath).toMillis();
        if (lastModified < 0) {
            lastModified = 0L;
        }
        long fileSize = Files.size(filePath);
        String etag = buildWeakEtag(lastModified, fileSize);

        response.setHeader(HttpHeaders.CACHE_CONTROL, buildCacheControlValue());
        response.setHeader(HttpHeaders.ETAG, etag);
        response.setDateHeader(HttpHeaders.LAST_MODIFIED, lastModified);

        if (isNotModified(request, etag, lastModified)) {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Path resolveFilePath(String requestUri) {
        String relativePath;
        if (requestUri.startsWith(UPLOAD_PREFIX)) {
            relativePath = requestUri.substring(UPLOAD_PREFIX.length());
            return resolvePathWithinRoot(uploadDir, relativePath);
        }
        if (requestUri.startsWith(STATIC_ASSET_PREFIX)) {
            relativePath = requestUri.substring(STATIC_ASSET_PREFIX.length());
            return resolvePathWithinRoot(miniappStaticAssetDir, relativePath);
        }
        return null;
    }

    private Path resolvePathWithinRoot(String rootDir, String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            return null;
        }
        String decoded;
        try {
            decoded = UriUtils.decode(relativePath, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        Path root = Paths.get(rootDir).toAbsolutePath().normalize();
        Path resolved = root.resolve(decoded).normalize();
        if (!resolved.startsWith(root)) {
            return null;
        }
        return resolved;
    }

    private String buildWeakEtag(long lastModified, long fileSize) {
        return "W/\"" + Long.toHexString(lastModified) + "-" + Long.toHexString(fileSize) + "\"";
    }

    private String buildCacheControlValue() {
        long safeMaxAge = Math.max(0, cacheMaxAgeSeconds);
        if (cacheImmutable) {
            return "public, max-age=" + safeMaxAge + ", immutable";
        }
        return "public, max-age=" + safeMaxAge + ", must-revalidate";
    }

    private boolean isNotModified(HttpServletRequest request, String etag, long lastModified) {
        String ifNoneMatch = request.getHeader(HttpHeaders.IF_NONE_MATCH);
        if (etagMatches(ifNoneMatch, etag)) {
            return true;
        }
        long ifModifiedSince = request.getDateHeader(HttpHeaders.IF_MODIFIED_SINCE);
        if (ifModifiedSince < 0) {
            return false;
        }
        long normalizedLastModified = (lastModified / 1000) * 1000;
        return normalizedLastModified <= ifModifiedSince;
    }

    private boolean etagMatches(String ifNoneMatch, String etag) {
        if (!StringUtils.hasText(ifNoneMatch)) {
            return false;
        }
        String expected = normalizeEtag(etag);
        String[] candidates = ifNoneMatch.split(",");
        for (String candidate : candidates) {
            String token = candidate == null ? "" : candidate.trim();
            if ("*".equals(token)) {
                return true;
            }
            if (token.equals(etag)) {
                return true;
            }
            if (normalizeEtag(token).equals(expected)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeEtag(String etag) {
        if (etag == null) {
            return "";
        }
        String value = etag.trim();
        if (value.regionMatches(true, 0, "W/", 0, 2)) {
            value = value.substring(2).trim();
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
