# DaHeServeV2 `meta` 模块解读（总版）
- 更新时间：2026-03-05
- 模块路径：`src/main/java/com/dahe/v2/modules/meta`

## 1. 模块定位
`meta` 模块承担两类能力：
1. 元数据选项查询：地域、作物、品种、作物树等前端筛选数据。
2. 术语词典管理：术语映射的分页、维护、全量替换、同步元信息。

## 2. 分层结构（本轮重构后）
1. 协议层
- `MetaOptionController`
- `AdminTerminologyDictController`
- 只处理请求参数、响应封装、边界校验。

2. 查询服务层
- `MetaOptionQueryService` + `MetaOptionQueryServiceImpl`
- 承接元数据选项查询逻辑，控制层不再拼装查询细节。

3. 词典服务层
- `TerminologyDictService` + `TerminologyDictServiceImpl`
- 承接词典写规则：新增、更新、全量替换（增量收敛）。

## 3. 本轮关键改造
1. 词典全量替换安全化
- `replace-all` 改为“增量写入 + 差异删除”。
- 事务内按 `source_text` 做新增/更新/删除，避免“先删后插”空窗。

2. 唯一冲突语义稳定化
- 词典写链路统一捕获唯一键冲突并转为业务提示“源术语已存在”。
- 控制层不直接感知数据库异常细节。

3. 元数据接口边界显式化
- `MetaOptionController` 显式登录校验，无登录返回 `UNAUTHORIZED`。

4. 控制层瘦身
- 元数据查询逻辑整体下沉 `MetaOptionQueryServiceImpl`。
- 移除无用依赖 `SeedBatchService`。

## 4. 数据模型
核心表：`terminology_dict`
- 关键字段：`source_text/target_text/sort_order/deleted`。
- 唯一约束：`(source_text, deleted)`。

## 5. API 概览
1. 元数据选项（`/api/v2/meta/options`）
- `/townships`
- `/provinces`
- `/cities`
- `/districts`
- `/crops`
- `/varieties`
- `/crop-tree`
- `/variety-groups`

2. 词典后台（`/api/v2/admin/terminology-dict`）
- `GET /`
- `GET /all`
- `GET /sync-meta`
- `POST /`
- `PUT /{id}`
- `DELETE /{id}`
- `POST /batch-delete`
- `POST /replace-all`

## 6. 与其它模块关系
1. `field`
- 地域选项数据来源。

2. `crop`
- 作物/品种树数据来源。

3. `auth`
- 鉴权通过统一路由链路接管；本模块不写散落角色判断。

## 7. 维护约束
1. 元数据聚合查询统一写在 `MetaOptionQueryService`。
2. 词典写规则统一写在 `TerminologyDictService`。
3. `replace-all` 禁止回退到先删后插模式。
4. 词典唯一冲突必须返回稳定业务语义，禁止透传数据库错误。
