# DaHeServeV2 `miniapp` 模块解读（总版）

- 更新时间：2026-03-06
- 模块路径：`src/main/java/com/dahe/v2/modules/miniapp`
- 文档目标：明确小程序端接口只落在 `miniapp` 模块，旧共享路径仅保留后台兼容能力。

## 1. 模块职责

`miniapp` 模块是小程序端唯一协议入口层：

1. 承接并稳定提供 `/api/v2/miniapp/**`。
2. 对接既有领域服务，保持小程序端响应结构兼容。
3. 将小程序与后台接口边界从路由层固化，避免后续混写。

不在本模块内的职责：

1. 认证核心策略与会话管理：`auth`、`session` 模块。
2. 领域数据持久化：各业务模块（`field/farm/seed/...`）。

## 2. 代码结构

### 2.1 控制器入口

1. `auth/controller/MiniappAuthController.java`
- `/api/v2/miniapp/auth/**`
- 登录、会话、当前用户信息、通知读写。

2. `assets/controller/MiniappAssetController.java`
- `/api/v2/miniapp/assets`、`/api/v2/miniapp/files/upload`
- 小程序资源分页与上传。

3. `amap/controller/MiniappAmapController.java`
- `/api/v2/miniapp/amap/**`
- 地址、区划、天气、审计入口。

4. `field/controller/MiniappFieldController.java`
- `/api/v2/miniapp/fields/**`
- 田块、计划、流程、最近农事。

5. `farm/controller/MiniappFarmRecordController.java`
- `/api/v2/miniapp/farm-records/**`
- 农事记录分页/分组/详情/增改删。

6. `seed/controller/MiniappSeedController.java`
- `/api/v2/miniapp/seed-batches/**`、`/api/v2/miniapp/seed-settings`
- 种子批次、检测、规则读写。

7. `meta/controller/MiniappMetaOptionController.java`
- `/api/v2/miniapp/meta/options/**`
- 省市区乡、作物品种、树形选项。

8. `dynamic/controller/MiniappDynamicFormConfigController.java`
- `/api/v2/miniapp/dynamic-configs/current`
- 动态表单配置读取。

9. `farm/process/controller/MiniappFarmProcessController.java`
- `/api/v2/miniapp/farm-process/templates*`
- 流程模板查询。

10. `company/controller/MiniappCompanyPublicController.java`
- `/api/v2/miniapp/public/company-intro`
- 小程序企业介绍。

11. `console/controller/MiniappConsoleUserController.java`
- `/api/v2/miniapp/console/users/**`
- 小程序控制台用户审核与管理（仅 `canConsole=1`）。

### 2.2 miniapp 鉴权边界

1. `ApiAuthInterceptor` 统一校验 token、用户状态、用户类型。
2. `AuthApiRouteAuthorizationService` 统一判定路由域：
- `/api/v2/miniapp/**` 仅允许小程序用户；
- `/api/v2/admin/**` 仅允许后台用户；
- 历史共享路径对小程序账号统一拒绝（提示迁移到 `/api/v2/miniapp/**`）。

## 3. 当前 API 边界结论

1. 小程序前端已迁移到 `miniapp` 前缀请求。
2. 旧共享路径对小程序账号已下线，不再允许继续调用。
3. 后台 admin 路由不受本轮迁移影响。

## 4. 可维护性约束

1. 新增小程序接口必须落在 `modules/miniapp`。
2. `controller` 仅做协议层；业务编排继续下沉到既有 `service` 体系。
3. API 变更必须同步维护迁移清单和前端迁移状态。
