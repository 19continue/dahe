package com.dahe.v2.modules.auth.service;

/**
 * 通知异步派发服务。
 */
public interface NoticeDispatchService {

    /**
     * 异步派发指定任务。
     */
    void dispatchTaskAsync(Long taskId);
}
