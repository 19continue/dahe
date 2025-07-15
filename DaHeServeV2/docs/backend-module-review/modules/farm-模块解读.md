# DaHeServeV2 `farm` 模块解读（总版）

- 更新时间：2026-03-04
- 模块路径：`src/main/java/com/dahe/v2/modules/farm`

## 1. 模块职责

1. 农事记录：分页、详情、新增、更新、删除、分组统计、执行人信息。
2. 流程模板：模板与步骤管理、排序、动态参数 schema 解析。
3. 记录策略：编辑窗口与“创建者可改删”规则管理。

## 2. 关键入口

1. `controller/FarmRecordController.java`
- 记录主链路、记录级可编辑/可删除权限回填、状态联动。

2. `process/controller/FarmProcessController.java`
- 流程模板与步骤管理（已注解鉴权）。

3. `policy/controller/RecordPolicyController.java`
- 记录策略管理（已注解鉴权）。

## 3. 鉴权模型（本轮已更新）

1. `farm-process`
- 使用 `@FarmProcessManagePermission`（菜单权限：`/process-templates`）。

2. `record-policy`
- 使用 `@FarmRecordPolicyManagePermission`（菜单权限：`/record-policy`）。

3. `farm-record`
- 已移除固定角色码传参判断。
- 写接口权限改为：
  - 后台：必须有 `PERM_/farm-records-manage`；
  - 小程序：`user_type=miniapp` 可进入写链路，具体可改删由记录策略判定。
- 执行人分配改为：
  - 小程序用户强制绑定本人；
  - 后台可按业务指定执行人。

## 4. 记录写链路

1. 参数校验
- 步骤 schema 校验：`StepFormSchemaResolver + StepFormSchemaValidator`。

2. 记录保存
- 自动回填 `cycleId`（未传时取田块当前计划）。
- 保存后绑定资源（`MediaAssetService.bindAssetsToBiz`）。
- 保存后联动田块/计划状态。

3. 事务边界
- `create/update/delete` 均已加事务，写记录、资源绑定、状态联动同事务回滚。

## 5. 记录级权限判定

`resolvePermission` 规则：

1. 后台用户且有菜单权限：可编辑、可删除。
2. 小程序用户：必须同时满足
- 是记录创建者（`operator_user_id` 或名称匹配）；
- 在策略编辑窗口内；
- 策略允许对应操作（`allow_operator_update/delete`）。

## 6. 跨模块关系

1. `field` / `field.cycle`
- 记录写入后驱动田块状态与计划状态变化。

2. `assets`
- 农事记录图片通过资源模块做业务绑定。

3. `auth`
- 使用统一鉴权链路，后台走菜单权限，小程序走用户类型语义。

## 7. 本轮鉴权清理结论

1. `FarmRecordController` 已移除 `hasAnyRole` 与 `admin/supervisor/operator` 固定角色权限分支。
2. 小程序侧“是否有控制台”仅用于身份展示，不作为角色判定。
3. 本模块已满足“后台角色可扩展、小程序无角色鉴权”的当前要求。

## 8. 关联文档

1. `docs/backend-module-review/modules/farm-待改进的点.md`
2. `docs/backend-module-review/modules/auth-模块解读.md`
