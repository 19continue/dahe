package com.dahe.v2.modules.auth.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.auth.dto.AdminRoleManageDTO;
import com.dahe.v2.modules.auth.role.service.AdminRoleService;

import java.util.List;
import java.util.Map;

/**
 * 后台角色管理应用服务。
 */
public interface AdminRoleManageService {

    Page<Map<String, Object>> page(String keyword, Integer enabled, long page, long pageSize);

    List<AdminRoleService.RoleOption> options(boolean includeDisabled);

    List<String> menuCodes();

    Result<Map<String, Object>> create(AdminRoleManageDTO.CreateReq req);

    Result<Map<String, Object>> update(Long id, AdminRoleManageDTO.UpdateReq req);

    Result<Map<String, Object>> updateEnabled(Long id, AdminRoleManageDTO.EnabledReq req);

    Result<Void> remove(Long id);
}
