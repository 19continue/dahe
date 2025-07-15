# DaHeServeV2 `amap` 模块待改进的点（最终版）
- 更新时间：2026-03-06
- 结论：本轮要求内的待改进项已全部落地，无阻断性遗留项。

## 1. 本轮要求与落地结果
1. `admin` / `miniapp` 接口严格分离：已完成。
- 小程序：`/api/v2/miniapp/amap/**`
- 后台：`/api/v2/admin/amap/**`
- 旧共享：`/api/v2/amap/**` 已下线

2. 额度统计改为“真实高德远程调用次数”：已完成。
- 计费累加从审计层移除。
- 改为在 `AmapOpenServiceImpl#doGetJson` 成功远程调用后累加。
- 缓存命中不进入远程调用路径，不计费。

3. 计费分类收敛为两类：已完成。
- `weather`
- `location`（含位置/城市/区划相关能力）

4. 缓存命中可审计、可识别来源：已完成。
- `request_source` 统一支持：
  - `backend-proxy`
  - `backend-proxy-cache:{source}`
  - `backend-proxy-mixed:{source...}`
- 审计列表支持 `apiType`、`requestSource` 过滤。

5. 缓存策略进入后台配置：已完成。
- 后台可配：Redis 开关、缓存 key 前缀、区划 TTL/兜底 TTL、天气 TTL、本地缓存容量。
- 保存配置后调用 `refreshRuntimeConfig`，运行时生效。

6. 后台页面布局调整（配置收起、审计优先）：已完成。
- 配置区支持“展开/收起”。
- 审计日志区域作为主视图，支持趋势与清理操作。

7. 审计日志膨胀治理：已完成。
- 增加“自动清理开关 + 保留天数”配置（后台可调）。
- 提供手动清理接口：`POST /api/v2/admin/amap/audits/purge`。
- 审计表增加关键索引，保证清理/查询成本可控。

8. 业务下沉与可维护性：已完成。
- 小程序 AMap 业务编排下沉至 `AmapOpenApplicationService`。
- 控制层仅保留参数校验与响应组装，不承载核心编排逻辑。

## 2. 本轮新增验证
1. 后端编译：`mvn -DskipTests compile` 通过。
2. 定向测试：`AmapOpenApplicationServiceTest`、`AuthRoutePermissionResolverTest`、`AuthApiRouteAuthorizationServiceTest` 通过。
3. 后台构建：`DaHeAdminV2` 执行 `npm run build` 通过。

## 3. 长期治理建议（非阻断）
1. 审计归档分层：
- 在线表保留近 N 天（如 90 天）。
- 历史表按月归档（或导出对象存储），减轻在线库压力。

2. 大表进一步治理：
- 当审计数据量持续增长时，可引入按月分区（MySQL Partition）或冷热分表策略。

3. 审计采样策略：
- 对高频成功请求可按比例采样，失败请求全量保留。
- 采样比例可做成后台配置项，按场景启用。

