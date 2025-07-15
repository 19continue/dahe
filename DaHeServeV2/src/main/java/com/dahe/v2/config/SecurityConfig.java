package com.dahe.v2.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 最小化配置。
 *
 * <p>项目鉴权由自定义 `ApiAuthInterceptor + AuthApiRouteAuthorizationService` 负责，
 * Security 层仅保留基础能力并放行所有请求。</p>
 */
@Configuration
public class SecurityConfig {

    /**
     * 构建安全过滤链：
     * 1) 开启 CORS；
     * 2) 关闭 CSRF（接口以 token 为主，非表单会话）；
     * 3) 所有请求放行，交由业务拦截器做权限判定。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors().and()
                .csrf().disable()
                .authorizeRequests()
                .anyRequest().permitAll();
        return http.build();
    }

    /**
     * 全局密码编码器（后台账号密码、后续密码重置等统一复用）。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
