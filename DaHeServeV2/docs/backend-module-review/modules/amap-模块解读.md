# DaHeServeV2 `amap` 模块解读（最终版）
- 更新时间：2026-03-06
- 模块路径：`src/main/java/com/dahe/v2/modules/amap`
- 关联模块：`modules/miniapp/amap`（小程序入口）

## 1. 模块职责
`amap` 模块负责高德能力接入与运维控制，职责分为四层：
1. 开放平台调用层：统一高德 HTTP 请求、QPS 节流、错误规整。
2. 缓存层：本地有界 LRU + Redis 双层缓存，降低额度消耗。
3. 配额层：按真实远程调用计费，并收敛为 `weather` / `location` 两类。
4. 审计层：记录调用来源、成功率、耗时、错误、趋势与清理能力。

## 2. 路由边界（最终）
1. 小程序接口：`/api/v2/miniapp/amap/**`
2. 后台接口：`/api/v2/admin/amap/**`
3. 旧共享接口：`/api/v2/amap/**` 已完全下线

## 3. 关键目录与职责
1. `controller/AmapAdminController.java`
- 后台配置、体检、审计查询/清理、后台区划查询入口。

2. `service/impl/AmapOpenApplicationService.java`
- 小程序 AMap 业务编排（地址、区划、天气快照、审计记录）。

3. `service/impl/AmapOpenServiceImpl.java`
- 统一高德远程调用、双层缓存读写、QPS 节流、真实计费落点。

4. `service/impl/AmapQuotaConfigServiceImpl.java`
- 配额主配置维护（天气/位置两类）、缓存策略与审计保留策略维护。

5. `service/impl/AmapApiAuditServiceImpl.java`
- 审计分页查询、趋势概览、按保留策略清理、自动清理触发。

6. `service/impl/AmapHealthCheckServiceImpl.java`
- 健康检查编排（key 校验、区划能力、天气能力）下沉到 service。

## 4. 后台接口清单（`/api/v2/admin/amap`）
1. `GET /quota`：读取配额与缓存/审计策略配置
2. `PUT /quota`：更新配额、缓存策略、审计保留策略
3. `POST /quota/recharge`：配额充值
4. `POST /key/verify`：校验高德 key
5. `POST /health/check`：一键体检
6. `GET /regions/provinces`：省级区划（`@AdminMenuCode("/field-manage")`）
7. `GET /regions/cities`：市级区划（`@AdminMenuCode("/field-manage")`）
8. `GET /regions/districts`：区县区划（`@AdminMenuCode("/field-manage")`）
9. `GET /regions/townships`：乡镇区划（`@AdminMenuCode("/field-manage")`）
10. `GET /audits`：审计分页（支持 `apiType`、`requestSource` 过滤）
11. `GET /audits/overview`：审计趋势与命中率概览
12. `POST /audits/purge`：按保留策略清理审计日志
13. `DELETE /audits/{id}`：删除单条审计
14. `POST /audits/batch-delete`：批量删除审计

## 5. 小程序接口清单（`/api/v2/miniapp/amap`）
1. `POST /audit`
2. `GET /address/tips`
3. `GET /address/regeo`
4. `GET /address/geocode`
5. `GET /regions/provinces`
6. `GET /regions/cities`
7. `GET /regions/districts`
8. `GET /regions/townships`
9. `GET /weather/snapshot`

## 6. 计费与缓存核心机制
1. 计费锚点：
- 仅在 `AmapOpenServiceImpl#doGetJson` 远程成功调用后计费。
- 缓存命中不触发远程调用，不计额度。

2. 计费分类：
- `weather`：天气接口
- `location`：位置/城市/区划接口

3. 双层缓存：
- 本地缓存：有界 LRU（容量可配置）
- Redis 缓存：TTL + 抖动（减少雪崩）
- 区划缓存：支持 stale 兜底，降低限流时可用性风险
- 天气缓存：默认 1 小时，可后台调参

4. 审计来源标签：
- `backend-proxy`：纯远程
- `backend-proxy-cache:*`：纯缓存
- `backend-proxy-mixed:*`：同次业务流程同时出现缓存与远程

## 7. 真实用量持久化模型（最终）
1. `amap_usage_daily`
- 只保留最近 7 天。
- 用于展示近 7 天真实高德远程调用趋势。

2. `amap_usage_monthly`
- 每月每类计费口径一条记录。
- 记录 `weather/location` 的月累计真实用量与告警是否已发送。

3. 月限额口径
- 后台配置只保留：
  - `天气月限额`
  - `位置月限额`
- 位置额度统一覆盖逆地理、位置查询、区域查询等全部位置类官方调用。

4. 后台展示口径
- 总调用量：后端代理调用总量，包含缓存命中。
- 真实高德用量：只统计真正发往高德官方的远程调用。
- 月度明细：后台展示最近 12 个月天气/位置真实用量与告警状态。

## 8. 审计日志膨胀治理
1. 配置项：
- `auditAutoPurgeEnabled`
- `auditRetainDays`

2. 执行方式：
- 审计查询链路触发自动清理检查。
- 后台支持手动清理接口即时执行。

3. 数据库支持：
- `amap_api_audit` 增加 `api_type` 字段与索引 `idx_amap_audit_type_date`。

## 9. 前端对接状态
1. `DaHeAdminV2/src/views/AmapAudit.vue`
- 配置区可折叠，默认收起。
- 审计区域占主视图，新增分类/来源过滤、趋势概览、月度真实用量表、清理按钮。

2. 小程序端
- 统一通过后端接口访问 AMap，不在小程序端配置高德 key。

## 10. 验证记录
1. 后端编译：`mvn -DskipTests compile` 通过。
2. 定向测试：`AmapOpenApplicationServiceTest`、`AuthRoutePermissionResolverTest`、`AuthApiRouteAuthorizationServiceTest` 通过。
3. 后台构建：`npm run build` 通过。

## 11. 本轮新增能力
1. 新增 `amap_usage_daily` 日表。
- 用于记录“真实请求到高德官方”的日级用量。
- 不依赖审计日志，因此日志清理后真实用量仍可追溯。

2. 后台高德运维页现有两套口径并行展示：
- 总调用量：包含缓存命中的后端代理调用量。
- 真实高德用量：仅统计真正发到高德官方的请求量。

3. 新增 `amap_usage_monthly` 月表。
- 用于长期保留天气/位置两类真实高德月用量。
- 告警只按“月 + 计费分类”发送一次，避免同月反复推送。

4. 后台新增高德缓存清理能力：
- 接口：`POST /api/v2/admin/amap/cache/clear`
- 行为：清理本地运行时缓存与 Redis 高德缓存。

## 12. 本轮收口
1. 后台高德运维页不再展开展示逐月真实用量列表，只保留：
- 当月天气真实用量
- 当月位置真实用量
- 总真实用量
- 缓存命中率
2. `amap_usage_monthly` 仍保留在后端，用于月限额治理、长期告警与累计统计，不再作为后台主视图逐月列表直接展示。
3. 月限额口径继续只保留两类：
- 天气月限额
- 位置月限额
4. 位置类月用量统一覆盖逆地理、位置查询、区域查询等所有真实高德位置调用。
