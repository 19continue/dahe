package com.dahe.v2;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * DaHe V2 后端服务启动入口。
 *
 * <p>职责：</p>
 * <p>1) 启动 Spring Boot 容器；</p>
 * <p>2) 扫描 modules 下所有 MyBatis Mapper 接口。</p>
 */
@SpringBootApplication
@MapperScan("com.dahe.v2.modules.**.mapper")
public class DaHeApplicationV2 {

    /**
     * 应用主函数。
     */
    public static void main(String[] args) {
        SpringApplication.run(DaHeApplicationV2.class, args);
    }
}

