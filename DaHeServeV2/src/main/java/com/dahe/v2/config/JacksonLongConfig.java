package com.dahe.v2.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 序列化配置。
 *
 * <p>统一将 Long / long 序列化为字符串，避免前端（尤其 JS）在大整数场景出现精度丢失。</p>
 */
@Configuration
public class JacksonLongConfig {

    /**
     * 注册 Long 转字符串序列化器。
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonLongToStringCustomizer() {
        return builder -> {
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
        };
    }
}
