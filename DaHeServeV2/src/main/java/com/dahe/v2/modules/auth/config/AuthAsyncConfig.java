package com.dahe.v2.modules.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * auth 模块异步执行配置。
 *
 * <p>通知派发属于典型的“非关键链路写入”，需要与业务主请求解耦，避免审核、资料提交等请求
 * 被大量消息投递拖慢。</p>
 */
@Configuration
@EnableAsync
public class AuthAsyncConfig {

    @Bean("authNoticeExecutor")
    public Executor authNoticeExecutor() {
        /*
         * 通知派发专用线程池。
         *
         * 为什么要单独配：
         * 1. 把通知异步任务与其他潜在异步任务隔离；
         * 2. 能单独控制并发度和排队长度；
         * 3. 出问题时线程名前缀更容易排查。
         *
         * 当前参数偏保守，说明这个线程池的目标是“避免阻塞主请求”，
         * 而不是把通知系统做成超大吞吐的消息平台。
         */
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 线程名前缀要清晰，线上排查时一眼就能看出这是通知线程。
        executor.setThreadNamePrefix("auth-notice-");
        // 常驻核心线程数，保证平时少量通知也能立即被处理。
        executor.setCorePoolSize(2);
        // 峰值时最多扩到 4 个线程，避免无限制抢占机器资源。
        executor.setMaxPoolSize(4);
        // 队列容量控制待处理通知任务的堆积上限。
        executor.setQueueCapacity(200);
        // 非核心线程空闲 60 秒后回收。
        executor.setKeepAliveSeconds(60);
        // 应用关闭时不强等通知线程池慢慢清空，避免拖慢停机。
        executor.setWaitForTasksToCompleteOnShutdown(false);
        // 让线程池配置真正生效。
        executor.initialize();
        return executor;
    }
}
