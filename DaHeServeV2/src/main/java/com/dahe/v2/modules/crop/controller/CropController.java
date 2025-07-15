package com.dahe.v2.modules.crop.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.auth.support.AdminMenuCode;
import com.dahe.v2.modules.crop.model.Crop;
import com.dahe.v2.modules.crop.service.CropAdminCommand;
import com.dahe.v2.modules.crop.service.CropAdminFacadeService;
import com.dahe.v2.modules.crop.service.CropNotFoundException;
import com.dahe.v2.modules.crop.service.CropServiceException;
import lombok.Data;
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
import javax.validation.constraints.NotEmpty;
import java.util.List;
import java.util.function.Supplier;

/**
 * 作物管理控制器。
 *
 * <p>控制层仅负责协议转换，业务编排统一下沉到 {@link CropAdminFacadeService}。</p>
 */
@RestController
@RequestMapping("/api/v2/crops")
@Validated
@AdminMenuCode("/crop-manage")
public class CropController {

    private final CropAdminFacadeService cropAdminFacadeService;

    public CropController(CropAdminFacadeService cropAdminFacadeService) {
        this.cropAdminFacadeService = cropAdminFacadeService;
    }

    @GetMapping
    public Result<Page<Crop>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String nodeType,
            @RequestParam(required = false) Long parentId,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) long pageSize
    ) {
        CropAdminCommand.PageQuery query = new CropAdminCommand.PageQuery();
        query.setKeyword(keyword);
        query.setNodeType(nodeType);
        query.setParentId(parentId);
        query.setPage(page);
        query.setPageSize(pageSize);
        return execute(() -> cropAdminFacadeService.page(query));
    }

    @GetMapping("/tree")
    public Result<List<CropAdminFacadeService.TreeCategoryItem>> tree(
            @RequestParam(required = false) String keyword
    ) {
        return execute(() -> cropAdminFacadeService.tree(keyword));
    }

    @PostMapping
    public Result<Crop> create(@RequestBody @Validated CropCreateReq req) {
        CropAdminCommand.Create command = new CropAdminCommand.Create();
        command.setName(req.getName());
        command.setVariety(req.getVariety());
        command.setNodeType(req.getNodeType());
        command.setParentId(req.getParentId());
        command.setImageUrl(req.getImageUrl());
        return execute(() -> cropAdminFacadeService.create(command));
    }

    @PutMapping("/{id}")
    public Result<Crop> update(@PathVariable Long id, @RequestBody @Validated CropUpdateReq req) {
        CropAdminCommand.Update command = new CropAdminCommand.Update();
        command.setName(req.getName());
        command.setVariety(req.getVariety());
        command.setParentId(req.getParentId());
        command.setImageUrl(req.getImageUrl());
        command.setSortOrder(req.getSortOrder());
        return execute(() -> cropAdminFacadeService.update(id, command));
    }

    @PostMapping("/reorder")
    public Result<Void> reorder(@RequestBody @Validated CropReorderReq req) {
        CropAdminCommand.Reorder command = new CropAdminCommand.Reorder();
        command.setIds(req.getIds());
        command.setNodeType(req.getNodeType());
        command.setParentId(req.getParentId());
        return executeVoid(() -> {
            cropAdminFacadeService.reorder(command);
            return true;
        });
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        return executeVoid(() -> cropAdminFacadeService.delete(id));
    }

    private <T> Result<T> execute(Supplier<T> supplier) {
        try {
            return Result.success(supplier.get());
        } catch (CropNotFoundException ex) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ex.getMessage());
        } catch (CropServiceException ex) {
            return Result.failure(ex.getCode(), ex.getMessage());
        }
    }

    private Result<Void> executeVoid(Supplier<Boolean> supplier) {
        try {
            boolean ok = Boolean.TRUE.equals(supplier.get());
            if (ok) {
                return Result.success(null);
            }
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        } catch (CropNotFoundException ex) {
            return Result.failure(ErrorCode.NOT_FOUND.getCode(), ex.getMessage());
        } catch (CropServiceException ex) {
            return Result.failure(ex.getCode(), ex.getMessage());
        }
    }

    @Data
    public static class CropCreateReq {
        private String name;
        private String variety;
        private String nodeType;
        private Long parentId;
        private String imageUrl;
    }

    @Data
    public static class CropUpdateReq {
        private String name;
        private String variety;
        private Long parentId;
        private String imageUrl;
        private Integer sortOrder;
    }

    @Data
    public static class CropReorderReq {
        @NotEmpty(message = "编号列表不能为空")
        private List<Long> ids;
        private String nodeType;
        private Long parentId;
    }
}
