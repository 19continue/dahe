# DaHeServeV2 `dynamic` 模块解读（总版）
- 更新时间：2026-03-05
- 模块路径：`src/main/java/com/dahe/v2/modules/dynamic`

## 1. 模块定位
`dynamic` 模块负责动态表单配置治理，核心是“模块 + 场景”的 schema 版本化管理。

主要职责：
1. 动态配置 CRUD（通用接口 + 种子专用接口）。
2. 当前生效配置读取（按版本号倒序）。
3. schema 合同校验与归一化。
4. 版本冲突治理与删除引用保护。

## 2. 分层结构（本轮重构后）
1. 协议层：`DynamicFormConfigController`
- 只负责参数接收、命令对象转换、统一异常映射。
- 不再承载 schema 解析与核心写规则。

2. 服务层：`DynamicFormConfigService` + `DynamicFormConfigServiceImpl`
- 统一承接动态配置写链路。
- 负责 schema 校验、版本号分配、冲突重试、引用保护删除。

3. 命令对象：`DynamicFormConfigCommand.Upsert`
- 解耦 controller 请求模型与 service 写模型。

## 3. 核心能力
1. 版本唯一治理
- 库级唯一键：`uk_dynamic_module_scene_version(module_key, scene_key, version_no)`。
- 启动期自举：
  - 修正空值和格式；
  - 历史重复版本自动顺延；
  - 再落唯一索引。
- 写入期冲突重试：自动版本模式下重试分配版本号。

2. schema 合同校验
- 结构必须是数组对象。
- 每项字段至少规范化到：`label/key/type/required/options`。
- `type` 受白名单约束。
- `options` 结构标准化为 `{label,value}`，并去重。
- `key` 自动清洗、补全、去重，避免下游冲突。

3. 删除引用保护
- 删除前统计外部引用（farm/seed 三张业务表）。
- 有引用即拒绝删除并返回引用计数。

## 4. 数据模型
核心表：`dynamic_form_config`
- 关键字段：`module_key/scene_key/schema_json/status/version_no`。
- 新增关键约束：`uk_dynamic_module_scene_version`。

## 5. API 概览
1. 公共查询
- `GET /api/v2/dynamic-configs/current`

2. 通用后台配置
- `GET /api/v2/admin/dynamic-configs`
- `GET /api/v2/admin/dynamic-configs/{id}`
- `POST /api/v2/admin/dynamic-configs`
- `PUT /api/v2/admin/dynamic-configs/{id}`
- `DELETE /api/v2/admin/dynamic-configs/{id}`

3. 种子专用配置
- `GET /api/v2/admin/seed-dynamic-configs`
- `GET /api/v2/admin/seed-dynamic-configs/{id}`
- `POST /api/v2/admin/seed-dynamic-configs`
- `PUT /api/v2/admin/seed-dynamic-configs/{id}`
- `DELETE /api/v2/admin/seed-dynamic-configs/{id}`

## 6. 与其它模块关系
1. `farm`
- 步骤配置通过 `form_config_id` 关联动态配置。

2. `seed`
- 批次与检测配置通过 `form_config_id` 关联动态配置。

3. `auth`
- 路由鉴权通过菜单码统一收口，dynamic 控制器不写散落权限逻辑。

## 7. 维护约束
1. schema 规则只能在 service 收口，controller 禁止重复实现。
2. 删除配置必须先做引用检查。
3. 新增场景时必须遵守“版本唯一 + schema 合同”两条硬约束。
4. 变更 `schema_json` 结构时必须补充对应测试。
