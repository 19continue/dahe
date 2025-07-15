package com.dahe.v2.modules.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dahe.v2.modules.user.model.AppUser;

public interface AppUserService extends IService<AppUser> {

    /**
     * 按微信 openId 查询用户。
     *
     * @param wxOpenId 微信 openId
     * @return 命中用户；未命中返回 null
     */
    AppUser findByWxOpenId(String wxOpenId);

    /**
     * 按后台登录账号查询后台用户。
     */
    AppUser findAdminByLoginName(String loginName);

    /**
     * 分页查询用户（基础版本：关键字 + 审核状态）。
     */
    Page<AppUser> pageUsers(String keyword, String status, long page, long pageSize);

    /**
     * 分页查询用户（增加 userType 过滤）。
     */
    Page<AppUser> pageUsers(String keyword, String status, String userType, long page, long pageSize);

    /**
     * 分页查询用户（完整版本：关键字 + 审核状态 + 用户类型 + 启用状态）。
     */
    Page<AppUser> pageUsers(String keyword, String status, String userType, Integer enabled, long page, long pageSize);

    /**
     * 分页查询用户（增加回收站过滤）。
     */
    Page<AppUser> pageUsers(String keyword, String status, String userType, Integer enabled, Integer recycleFlag, long page, long pageSize);
}
