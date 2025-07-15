# `miniapp` API 迁移清单（批次6，2026-03-06）

## 1. 迁移目标

1. 小程序端请求统一迁移到 `/api/v2/miniapp/**`。
2. 小程序账号全面下线历史共享路径（避免继续调用旧 API）。
3. 不影响 admin 后台接口与权限链路。
4. 后端尽量保持小程序既有响应结构，降低前端改造成本。

## 2. 后端迁移结果（DaHeServeV2）

### 2.1 新增 miniapp 入口控制器

1. `GET|PUT /api/v2/miniapp/fields/**`
2. `GET|POST|PUT|DELETE /api/v2/miniapp/farm-records/**`
3. `GET|POST|PUT|DELETE /api/v2/miniapp/seed-batches/**`
4. `GET|PUT /api/v2/miniapp/seed-settings`
5. `GET /api/v2/miniapp/meta/options/**`
6. `GET /api/v2/miniapp/dynamic-configs/current`
7. `GET /api/v2/miniapp/farm-process/templates*`
8. `GET /api/v2/miniapp/public/company-intro`
9. `GET|PUT /api/v2/miniapp/console/users/**`

### 2.2 旧共享路径下线策略

1. 在 `AuthApiRouteAuthorizationService` 中，非 admin 用户访问共享路径统一拒绝。
2. 拒绝提示：`共享接口已下线，请升级到 /api/v2/miniapp/**`。
3. admin 用户共享路径访问策略保持不变，避免误伤后台。

### 2.3 响应结构兼容策略

1. miniapp 新入口复用现有领域控制器/service 结果结构。
2. 前端可继续按原有 `Result<T>` + 分页结构解析，无需整体重写。

## 3. 前端迁移结果（DaHeAppV2）

### 3.1 已迁移路径族

1. `/fields/**` -> `/miniapp/fields/**`
2. `/farm-records/**` -> `/miniapp/farm-records/**`
3. `/seed-batches/**` -> `/miniapp/seed-batches/**`
4. `/seed-settings` -> `/miniapp/seed-settings`
5. `/meta/options/**` -> `/miniapp/meta/options/**`
6. `/dynamic-configs/current` -> `/miniapp/dynamic-configs/current`
7. `/farm-process/templates*` -> `/miniapp/farm-process/templates*`
8. `/public/company-intro` -> `/miniapp/public/company-intro`
9. `/admin/users/**` -> `/miniapp/console/users/**`

### 3.2 自检结果

1. 代码扫描未发现小程序页面继续调用旧路径。
2. 发现并修复 1 处误替换路径（`field/detail` 的 recent-records 路径）。

### 3.3 本轮补充（2026-03-06）

1. 田块更新接口 `PUT /miniapp/fields/{id}` 支持提交 `cropVarietyGroups`（结构化作物品种）。
2. 小程序展示层统一走 `utils/crop-variety.js`，优先读取 `cropVarietyGroups`，旧字段仅兜底。
3. 后端已修复“未显式携带作物字段时误清空作物数据”的问题。

## 4. 迁移状态总览

1. 后端 miniapp 入口扩展：已完成
2. 小程序前端路径迁移：已完成
3. 小程序旧共享路径下线：已完成（账号维度）
4. admin 后台兼容：已保持
5. 后端编译验证：通过（`mvn -DskipTests compile`）
