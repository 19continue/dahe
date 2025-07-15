# DaHeServeV2 `crop` 模块解读（总版）
- 更新时间：2026-03-05
- 模块路径：`src/main/java/com/dahe/v2/modules/crop`

## 1. 模块定位
`crop` 模块负责作物分类与品种的分层管理，核心是两类节点：
1. `category`：作物分类（如玉米、小麦）。
2. `variety`：分类下的品种（通过 `parent_id` 关联分类）。

模块对外提供：
1. 分类/品种分页查询。
2. 分类-品种树查询。
3. 分类/品种新增、更新、重排、删除。
4. 分类或品种改名后，对 `field` 与 `seed_batch` 的名称字段同步。

## 2. 分层结构（本轮重构后）
1. 协议层
- `CropController`
- 仅做参数接收、命令对象组装、统一异常映射。

2. 业务门面层
- `CropAdminFacadeService` + `CropAdminFacadeServiceImpl`
- 收口 create/update/delete/reorder/tree 的业务编排与跨表同步。

3. 领域服务层
- `CropService` + `CropServiceImpl`
- 提供分页、排序号计算、重排等基础能力。

## 3. 本轮关键改造
1. 控制层瘦身
- 历史 `CropController` 中的大量业务逻辑下沉到 `CropAdminFacadeServiceImpl`。
- 控制层不再直接操作 `JdbcTemplate` 或写复杂分支。

2. 写链路事务化
- `create/update/delete` 统一在门面服务内事务编排。
- 分类改名、品种改名与跨表同步在同一事务链路执行，不再吞异常。

3. 删除前引用保护
- 删除分类或品种前，检查 `farm_process_template.crop_id` 引用。
- 若存在引用，返回明确提示“作物已被流程模板引用，无法删除”。

4. 唯一性落地到数据库
- 新增唯一约束：
  - `uk_crop_category_name(node_type, name, deleted)`
  - `uk_crop_variety_parent(node_type, parent_id, variety, deleted)`
- 启动自举新增去重逻辑，先清理重复历史数据再落约束。

5. 新鉴权对齐
- `CropController` 新增 `@AdminMenuCode("/crop-manage")`。
- 共享路由授权规则显式收口：`/api/v2/crops/**` 仅后台可访问，小程序拒绝访问。

## 4. 数据模型与约束
核心表：`crop`
1. 关键字段：`node_type`、`parent_id`、`name`、`variety`、`sort_order`、`deleted`。
2. 基础索引：`idx_crop_node_type`、`idx_crop_parent`。
3. 新增唯一索引：`uk_crop_category_name`、`uk_crop_variety_parent`。

## 5. API 概览
统一前缀：`/api/v2/crops`
1. `GET /`：分页查询分类或品种。
2. `GET /tree`：分类-品种树。
3. `POST /`：新增分类或品种。
4. `PUT /{id}`：更新分类或品种。
5. `POST /reorder`：重排。
6. `DELETE /{id}`：删除分类或品种（含引用保护）。

## 6. 跨模块关系
1. `field`：分类/品种改名后同步 `field.crop_type`、`field.crop_variety`。
2. `seed`：分类/品种改名后同步 `seed_batch.crop_type`、`seed_batch.variety_name`。
3. `farm`：删除前检查 `farm_process_template.crop_id` 引用。
4. `auth`：通过路由权限模型控制后台访问，不在 controller 写散落鉴权逻辑。

## 7. 维护约束
1. 控制层禁止新增业务编排，统一进入 `CropAdminFacadeService`。
2. 分类/品种唯一性必须依赖数据库约束 + 业务提示双重保障。
3. 跨表同步失败必须中断事务，禁止吞异常。
4. 删除前必须做模板引用检查，禁止直接物理/逻辑删除。
