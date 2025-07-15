package com.dahe.v2.config;

import com.dahe.v2.modules.auth.support.ApiAuthInterceptor;
import com.dahe.v2.modules.oplog.support.OperationLogInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web MVC 全局配置。
 *
 * <p>职责：注册拦截器、统一 CORS 策略、暴露本地上传目录静态访问映射。</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ApiAuthInterceptor apiAuthInterceptor;
    private final OperationLogInterceptor operationLogInterceptor;
    private final String corsAllowedOriginPatterns;
    private final String uploadDir;
    private final String miniappStaticAssetDir;

    public WebMvcConfig(
            ApiAuthInterceptor apiAuthInterceptor,
            OperationLogInterceptor operationLogInterceptor,
            @Value("${app.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}") String corsAllowedOriginPatterns,
            @Value("${app.upload.dir:uploads}") String uploadDir,
            @Value("${app.miniapp-static-assets.dir:/assets}") String miniappStaticAssetDir
    ) {
        this.apiAuthInterceptor = apiAuthInterceptor;
        this.operationLogInterceptor = operationLogInterceptor;
        this.corsAllowedOriginPatterns = corsAllowedOriginPatterns;
        this.uploadDir = uploadDir;
        this.miniappStaticAssetDir = miniappStaticAssetDir;
    }

    /**
     * 注册后端统一拦截器链。
     *
     * <p>执行顺序按注册顺序：
     * 先做鉴权上下文绑定，再做操作日志采集。</p>
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiAuthInterceptor)
                .addPathPatterns("/api/v2/**")
                .excludePathPatterns(
                        "/api/v2/public/**",
                        "/api/v2/miniapp/public/**",
                        "/api/v2/admin/auth/**",
                        "/api/v2/miniapp/auth/entry",
                        "/api/v2/miniapp/auth/apply",
                        "/api/v2/miniapp/auth/wechat-login"
                );
        registry.addInterceptor(operationLogInterceptor)
                .addPathPatterns("/api/v2/**");
    }

    /**
     * 配置跨域规则。
     *
     * <p>允许源通过配置项注入，默认放开本机开发地址；允许携带 Cookie/凭据。</p>
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] patterns = splitCsv(corsAllowedOriginPatterns);
        registry.addMapping("/**")
                .allowedOriginPatterns(patterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 配置上传文件静态访问。
     *
     * <p>将本地 `uploadDir` 映射为 `/uploads/**` 与 `/api/v2/uploads/**` 两个访问路径。</p>
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        String location = uploadPath.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/uploads/**", "/api/v2/uploads/**")
                .addResourceLocations(location);
        Path staticAssetPath = Paths.get(miniappStaticAssetDir).toAbsolutePath().normalize();
        String staticLocation = staticAssetPath.toUri().toString();
        if (!staticLocation.endsWith("/")) {
            staticLocation = staticLocation + "/";
        }
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(staticLocation);
    }

    /**
     * 解析逗号分隔配置为 origin pattern 数组，并提供安全兜底值。
     */
    private String[] splitCsv(String text) {
        if (text == null) {
            return new String[]{"http://localhost:*", "http://127.0.0.1:*"};
        }
        String[] raw = text.split(",");
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String row : raw) {
            if (row == null) {
                continue;
            }
            String item = row.trim();
            if (!item.isEmpty()) {
                out.add(item);
            }
        }
        if (out.isEmpty()) {
            return new String[]{"http://localhost:*", "http://127.0.0.1:*"};
        }
        return out.toArray(new String[0]);
    }
}
