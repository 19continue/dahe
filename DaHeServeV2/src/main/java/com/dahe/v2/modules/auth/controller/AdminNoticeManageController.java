package com.dahe.v2.modules.auth.controller;

import com.dahe.v2.common.Result;
import com.dahe.v2.modules.auth.dto.AdminNoticeManageDTO;
import com.dahe.v2.modules.auth.service.UserNoticeService;
import com.dahe.v2.modules.auth.support.AdminMenuCode;
import com.dahe.v2.modules.auth.support.AuthContext;
import com.dahe.v2.modules.user.model.AppUser;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Min;
import java.util.Map;

/**
 * 后台消息通知治理控制器。
 *
 * <p>只负责消息任务的协议层：分页、创建、删除。真正的派发由异步服务完成，
 * 避免管理端请求被通知投递阻塞。</p>
 */
@RestController
@RequestMapping("/api/v2/admin/messages")
@AdminMenuCode("/messages")
@Validated
public class AdminNoticeManageController {

    private final UserNoticeService userNoticeService;

    public AdminNoticeManageController(UserNoticeService userNoticeService) {
        this.userNoticeService = userNoticeService;
    }

    @GetMapping
    public Result<Map<String, Object>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String noticeType,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String dispatchStatus,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) long pageSize
    ) {
        return Result.success(userNoticeService.pageNoticeTasks(keyword, noticeType, targetType, dispatchStatus, page, pageSize));
    }

    @GetMapping("/config")
    public Result<Map<String, Object>> config() {
        return Result.success(userNoticeService.getNoticeDispatchConfig());
    }

    @PostMapping
    public Result<Map<String, Object>> create(
            HttpServletRequest request,
            @RequestBody @Validated AdminNoticeManageDTO.CreateReq req
    ) {
        AppUser currentUser = AuthContext.getCurrentUser(request);
        Long operatorUserId = currentUser == null ? null : currentUser.getId();
        String operatorName = currentUser == null ? null : resolveOperatorName(currentUser);
        return userNoticeService.createManualNoticeTask(operatorUserId, operatorName, req);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody(required = false) AdminNoticeManageDTO.DeleteReq req
    ) {
        AppUser currentUser = AuthContext.getCurrentUser(request);
        Long operatorUserId = currentUser == null ? null : currentUser.getId();
        return userNoticeService.deleteNoticeTask(id, req == null ? null : req.getExpectedUpdatedAt(), operatorUserId);
    }

    @PostMapping("/config")
    public Result<Void> updateConfig(
            HttpServletRequest request,
            @RequestBody(required = false) AdminNoticeManageDTO.ConfigReq req
    ) {
        AppUser currentUser = AuthContext.getCurrentUser(request);
        Long operatorUserId = currentUser == null ? null : currentUser.getId();
        return userNoticeService.updateNoticeDispatchConfig(operatorUserId, resolveOperatorName(currentUser), req);
    }

    private String resolveOperatorName(AppUser currentUser) {
        if (currentUser == null) {
            return null;
        }
        String realName = normalize(currentUser.getRealName());
        if (realName != null) {
            return realName;
        }
        String nickName = normalize(currentUser.getNickName());
        if (nickName != null) {
            return nickName;
        }
        return normalize(currentUser.getLoginName());
    }

    private String normalize(String text) {
        String value = String.valueOf(text == null ? "" : text).trim();
        return value.isEmpty() ? null : value;
    }
}
