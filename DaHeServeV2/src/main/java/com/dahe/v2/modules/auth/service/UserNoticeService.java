package com.dahe.v2.modules.auth.service;

import com.dahe.v2.common.Result;
import com.dahe.v2.modules.auth.dto.AdminNoticeManageDTO;
import com.dahe.v2.modules.user.model.AppUser;

import java.util.Map;

/**
 * 用户通知应用服务。
 */
public interface UserNoticeService {

    /**
     * 安全写入单条通知。
     */
    void pushNoticeSafe(Long userId, String title, String content, String noticeType);

    /**
     * 新申请/重申请时向后台管理员广播审核通知。
     */
    void pushReviewApplyNoticeToAdmins(AppUser applicant, boolean reApply);

    /**
     * 向具备指定后台路由权限的管理员发送通知。
     */
    void pushAdminRouteNoticeSafe(String routeCode, String title, String content, String noticeType);

    /**
     * 查询用户通知分页数据。
     */
    Map<String, Object> queryUserNotices(Long userId, String noticeType, Boolean unreadOnly, long page, long pageSize);

    /**
     * 将指定通知标记为已读。
     */
    boolean markRead(Long userId, Long noticeId);

    /**
     * 将当前用户全部未读通知标记为已读。
     */
    long markAllRead(Long userId);

    /**
     * 删除当前用户自己的通知。
     */
    boolean deleteUserNotice(Long userId, Long noticeId);

    /**
     * 后台消息任务分页。
     */
    Map<String, Object> pageNoticeTasks(String keyword, String noticeType, String targetType, String dispatchStatus, long page, long pageSize);

    /**
     * 创建后台手工消息任务，并异步派发。
     */
    Result<Map<String, Object>> createManualNoticeTask(Long operatorUserId, String operatorName, AdminNoticeManageDTO.CreateReq req);

    /**
     * 删除消息任务，同时隐藏已派发收件箱记录。
     */
    Result<Void> deleteNoticeTask(Long taskId, String expectedUpdatedAt, Long operatorUserId);

    /**
     * 查询消息派发配置。
     */
    Map<String, Object> getNoticeDispatchConfig();

    /**
     * 更新消息派发配置。
     */
    Result<Void> updateNoticeDispatchConfig(Long operatorUserId, String operatorName, AdminNoticeManageDTO.ConfigReq req);
}
