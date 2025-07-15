package com.dahe.v2.modules.auth.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.auth.dto.AdminRoleManageDTO;
import com.dahe.v2.modules.auth.role.service.AdminRoleService;
import com.dahe.v2.modules.auth.service.AdminRoleManageService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.Min;
import java.util.List;
import java.util.Map;

/**
 * 后台角色管理控制器。
 *
 * <p>控制层只负责协议适配，业务编排统一下沉到 {@link AdminRoleManageService}。</p>
 */
@RestController
@RequestMapping("/api/v2/admin/roles")
@Validated
public class AdminRoleController {

    private final AdminRoleManageService adminRoleManageService;

    public AdminRoleController(AdminRoleManageService adminRoleManageService) {
        this.adminRoleManageService = adminRoleManageService;
    }

    /** 角色分页查询（管理端角色列表主接口）。 */
    @GetMapping
    public Result<Page<Map<String, Object>>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer enabled,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) long pageSize
    ) {
        return Result.success(adminRoleManageService.page(keyword, enabled, page, pageSize));
    }

    /** 角色下拉选项（用于用户管理、角色编辑等场景）。 */
    @GetMapping("/options")
    public Result<List<AdminRoleService.RoleOption>> options(@RequestParam(defaultValue = "false") boolean includeDisabled) {
        return Result.success(adminRoleManageService.options(includeDisabled));
    }

    /** 菜单权限码列表（仅路径码）。 */
    @GetMapping("/menu-codes")
    public Result<List<String>> menuCodes() {
        return Result.success(adminRoleManageService.menuCodes());
    }

    /** 新建后台角色。 */
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody @Validated AdminRoleManageDTO.CreateReq req) {
        return adminRoleManageService.create(req);
    }

    /** 更新角色基础信息与菜单权限。 */
    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@PathVariable Long id, @RequestBody @Validated AdminRoleManageDTO.UpdateReq req) {
        return adminRoleManageService.update(id, req);
    }

    /** 启用/禁用角色。 */
    @PutMapping("/{id}/enabled")
    public Result<Map<String, Object>> updateEnabled(@PathVariable Long id, @RequestBody @Validated AdminRoleManageDTO.EnabledReq req) {
        return adminRoleManageService.updateEnabled(id, req);
    }

    /** 删除角色。 */
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        return adminRoleManageService.remove(id);
    }
}
