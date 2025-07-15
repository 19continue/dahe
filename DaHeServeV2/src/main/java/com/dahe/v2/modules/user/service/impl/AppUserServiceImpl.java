package com.dahe.v2.modules.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dahe.v2.modules.auth.domain.AuthDomainConstants;
import com.dahe.v2.modules.user.mapper.AppUserMapper;
import com.dahe.v2.modules.user.model.AppUser;
import com.dahe.v2.modules.user.service.AppUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
public class AppUserServiceImpl extends ServiceImpl<AppUserMapper, AppUser> implements AppUserService {

    private static final Logger log = LoggerFactory.getLogger(AppUserServiceImpl.class);

    @Override
    public AppUser findByWxOpenId(String wxOpenId) {
        // 空 openId 直接视为无效查询，避免生成无意义 SQL。
        if (!StringUtils.hasText(wxOpenId)) {
            return null;
        }
        String normalizedOpenId = wxOpenId.trim();
        List<AppUser> rows = this.lambdaQuery()
                .eq(AppUser::getDeleted, 0)
                .eq(AppUser::getRecycleFlag, 0)
                .eq(AppUser::getWxOpenId, normalizedOpenId)
                .orderByAsc(AppUser::getId)
                .last("limit 2")
                .list();
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        if (rows.size() > 1) {
            long duplicateCount = this.lambdaQuery()
                    .eq(AppUser::getDeleted, 0)
                    .eq(AppUser::getRecycleFlag, 0)
                    .eq(AppUser::getWxOpenId, normalizedOpenId)
                    .count();
            log.error("Duplicate wxOpenId detected, wxOpenId={}, duplicateCount={}, firstUserId={}, secondUserId={}",
                    normalizedOpenId,
                    duplicateCount,
                    rows.get(0).getId(),
                    rows.get(1).getId());
        }
        return rows.get(0);
    }

    @Override
    public AppUser findAdminByLoginName(String loginName) {
        if (!StringUtils.hasText(loginName)) {
            return null;
        }
        String normalized = String.valueOf(loginName).trim().toLowerCase(Locale.ROOT);
        return this.lambdaQuery()
                .eq(AppUser::getDeleted, 0)
                .eq(AppUser::getRecycleFlag, 0)
                .eq(AppUser::getUserType, AuthDomainConstants.USER_TYPE_ADMIN)
                .eq(AppUser::getLoginName, normalized)
                .orderByAsc(AppUser::getId)
                .last("limit 1")
                .one();
    }

    @Override
    public Page<AppUser> pageUsers(String keyword, String status, long page, long pageSize) {
        return pageUsers(keyword, status, null, null, 0, page, pageSize);
    }

    @Override
    public Page<AppUser> pageUsers(String keyword, String status, String userType, long page, long pageSize) {
        return pageUsers(keyword, status, userType, null, 0, page, pageSize);
    }

    @Override
    public Page<AppUser> pageUsers(String keyword, String status, String userType, Integer enabled, long page, long pageSize) {
        return pageUsers(keyword, status, userType, enabled, 0, page, pageSize);
    }

    @Override
    public Page<AppUser> pageUsers(String keyword, String status, String userType, Integer enabled, Integer recycleFlag, long page, long pageSize) {
        Page<AppUser> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<AppUser> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            // 关键字做多字段模糊匹配，覆盖昵称、实名、手机号与 openId。
            qw.and(w -> w.like(AppUser::getNickName, keyword)
                    .or().like(AppUser::getRealName, keyword)
                    .or().like(AppUser::getPhone, keyword)
                    .or().like(AppUser::getLoginName, keyword)
                    .or().like(AppUser::getWxOpenId, keyword));
        }
        if (StringUtils.hasText(status)) {
            qw.eq(AppUser::getStatus, status);
        }
        if (StringUtils.hasText(userType)) {
            qw.eq(AppUser::getUserType, userType);
        }
        if (enabled != null) {
            qw.eq(AppUser::getEnabled, enabled);
        }
        if (recycleFlag != null) {
            qw.eq(AppUser::getRecycleFlag, recycleFlag == 1 ? 1 : 0);
        }
        // 新用户优先展示；追加 id 作为二级排序确保翻页稳定。
        qw.orderByDesc(AppUser::getCreatedAt).orderByDesc(AppUser::getId);
        return this.page(p, qw);
    }
}
