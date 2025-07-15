# DaHeServeV2 `field` 模块解读（总版）
- 更新时间：2026-03-06
- 模块路径：`src/main/java/com/dahe/v2/modules/field`

## 1. 模块职责
`field` 模块负责三类能力：
1. 田块主数据管理（分页、详情、新增、编辑、启停、排序）。
2. 田块种植计划管理（计划增删改、切换当前计划、状态归一化）。
3. 田块流程聚合视图（计划+流程模板+步骤+农事记录）。

## 2. 关键数据模型
1. `field`
- 旧兼容字段：`crop_type`、`crop_variety`。
- 新结构化字段：`crop_variety_groups_json`（JSON 数组，元素为 `{cropType,cropVariety}`）。
- 运行态响应字段：`cropVarietyGroups`（不落库）。

2. `field_crop_cycle`
- 计划字段：`crops_json`、`template_ids_json`。
- 当前计划变更会回写田块作物摘要。

## 3. 核心实现
1. 统一编解码器：`FieldCropVarietyGroupCodec`
- 负责三种来源互转：`cropsJson`、`cropVarietyGroupsJson`、旧 `cropType/cropVariety`。
- 统一去空、去重、字符串回退、JSON token 构造（用于查询兼容）。

2. 控制层回填策略：`FieldController`
- `page/detail` 响应时优先从“当前计划”提取结构化作物组合。
- 无当前计划时回退读取 `cropVarietyGroupsJson`，再回退旧字段。
- 对外响应稳定包含：`cropType`、`cropVariety`、`cropVarietyGroups`。

3. 计划同步策略：`FieldCropCycleServiceImpl`
- 计划写入后同步 `field` 的 `status/cropType/cropVariety/cropVarietyGroupsJson`。
- 无有效计划时清空作物摘要字段，避免脏数据残留。

4. 查询兼容：`FieldServiceImpl`
- `cropType/cropVariety/keyword` 查询同时兼容旧字段与 `cropVarietyGroupsJson`。

## 4. 接口语义（本轮重点）
1. 新增田块 `POST /api/v2/fields`
- 支持结构化入参 `cropVarietyGroups`。
- 未传结构化时，自动兼容旧 `cropType/cropVariety`。

2. 编辑田块 `PUT /api/v2/fields/{id}`
- 仅在请求中“显式携带作物字段”时才覆盖作物数据。
- 未携带作物字段时保留原值，避免前端更新基础信息时误清空作物品种。

3. 小程序入口 `PUT /api/v2/miniapp/fields/{id}`
- 与主链路一致，支持 `cropVarietyGroups`。

## 5. 三端协同约定
1. 后端：统一返回 `cropVarietyGroups`，并保留旧字段兼容。
2. 管理端：`CropPairTags` 优先使用 `cropVarietyGroups`，旧字段兜底。
3. 小程序：统一通过 `utils/crop-variety.js` 解析和展示，避免页面各自拼接。

## 6. 新增模块/接口必须遵守
1. 结构化字段优先，旧拼接字段只做兼容。
2. 业务编排放在 `service`，`controller` 仅做协议处理与调用。
3. 涉及字段语义变更时，必须同步更新三端展示工具与模块文档。