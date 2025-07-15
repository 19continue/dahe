# DaHeServeV2 后端总文档（最终版）
- 更新时间：2026-03-11
- 用途：记录全局约束、模块文档目录、API 迁移文档目录与当前整体状态。
- 说明：模块细节不在本文件展开，统一以对应模块文档为准。

## 1. 全局约束（执行口径）
1. 最高约束文档：`V2项目要求与规范(必读).md`。
2. 鉴权由 `auth` 模块统一承载，业务模块不写散落式权限判断。
3. `controller` 只处理协议层，业务编排统一下沉到 `service`。
4. 后台与小程序接口严格分离：
- 后台：`/api/v2/admin/**`
- 小程序：`/api/v2/miniapp/**`
5. 非 `miniapp` 模块不得新增小程序接口。
6. API 变更必须同步维护迁移清单与前端对接状态。
7. 后端数据库变更必须落到对应 `schema-*.sql` 文件，不允许只停留在本地库。

## 2. 模块文档目录
1. `docs/backend-module-review/modules/auth-模块解读.md`
2. `docs/backend-module-review/modules/auth-待改进的点.md`
3. `docs/backend-module-review/modules/session-模块解读.md`
4. `docs/backend-module-review/modules/session-待改进的点.md`
5. `docs/backend-module-review/modules/miniapp-模块解读.md`
6. `docs/backend-module-review/modules/miniapp-待改进的点.md`
7. `docs/backend-module-review/modules/user-模块解读.md`
8. `docs/backend-module-review/modules/user-待改进的点.md`
9. `docs/backend-module-review/modules/assets-模块解读.md`
10. `docs/backend-module-review/modules/assets-待改进的点.md`
11. `docs/backend-module-review/modules/farm-模块解读.md`
12. `docs/backend-module-review/modules/farm-待改进的点.md`
13. `docs/backend-module-review/modules/field-模块解读.md`
14. `docs/backend-module-review/modules/field-待改进的点.md`
15. `docs/backend-module-review/modules/seed-模块解读.md`
16. `docs/backend-module-review/modules/seed-待改进的点.md`
17. `docs/backend-module-review/modules/crop-模块解读.md`
18. `docs/backend-module-review/modules/crop-待改进的点.md`
19. `docs/backend-module-review/modules/dynamic-模块解读.md`
20. `docs/backend-module-review/modules/dynamic-待改进的点.md`
21. `docs/backend-module-review/modules/meta-模块解读.md`
22. `docs/backend-module-review/modules/meta-待改进的点.md`
23. `docs/backend-module-review/modules/export-模块解读.md`
24. `docs/backend-module-review/modules/export-待改进的点.md`
25. `docs/backend-module-review/modules/company-模块解读.md`
26. `docs/backend-module-review/modules/company-待改进的点.md`
27. `docs/backend-module-review/modules/amap-模块解读.md`
28. `docs/backend-module-review/modules/amap-待改进的点.md`
29. `docs/backend-module-review/modules/oplog-模块解读.md`
30. `docs/backend-module-review/modules/oplog-待改进的点.md`

## 3. API 迁移文档目录
1. `docs/backend-module-review/api-migrations/auth-api迁移清单-2026-03-03.md`
2. `docs/backend-module-review/api-migrations/auth-api迁移清单-2026-03-11-批次7.md`
3. `docs/backend-module-review/api-migrations/session-api迁移清单-2026-03-03-批次1.md`
4. `docs/backend-module-review/api-migrations/assets-api迁移清单-2026-03-03-批次3.md`
5. `docs/backend-module-review/api-migrations/company-api迁移清单-2026-03-04-批次4.md`
6. `docs/backend-module-review/api-migrations/company-api迁移清单-2026-03-05-批次5.md`
7. `docs/backend-module-review/api-migrations/seed-api迁移清单-2026-03-04-批次4.md`
8. `docs/backend-module-review/api-migrations/dynamic-api迁移清单-2026-03-05-批次5.md`
9. `docs/backend-module-review/api-migrations/meta-api迁移清单-2026-03-05-批次5.md`
10. `docs/backend-module-review/api-migrations/crop-api迁移清单-2026-03-05-批次5.md`
11. `docs/backend-module-review/api-migrations/export-api迁移清单-2026-03-05-批次5.md`
12. `docs/backend-module-review/api-migrations/amap-api迁移清单-2026-03-05-批次5.md`
13. `docs/backend-module-review/api-migrations/oplog-api迁移清单-2026-03-05-批次5.md`
14. `docs/backend-module-review/api-migrations/miniapp-api迁移清单-2026-03-06-批次6.md`

## 4. 当前整体状态（除模块细节外）
1. 鉴权框架已统一为 `auth` 模块承载的路由权限模型。
2. `amap` 模块已完成本轮全面优化：
- 计费改为真实远程调用口径（非后端请求口径）。
- 计费分类收敛为 `weather/location` 两类。
- 缓存策略可后台配置，审计支持过滤、概览与清理。
- 后台与小程序路由已彻底分离，旧共享路径已下线。
3. 前端 `DaHeAdminV2` 已完成 AMap 运维页对接迁移与布局升级。
4. 小程序 API 已完成批次6迁移：
- `DaHeAppV2` 请求统一到 `/api/v2/miniapp/**`；
- `field/farm/seed/meta/dynamic/farm-process/company/console` 已提供 miniapp 专属入口；
- 小程序账号访问旧共享路径已在鉴权层下线，admin 兼容保持不变。
5. `field` 模块已完成作物品种字段结构升级：
- 数据库新增 `crop_variety_groups_json`，后端统一输出 `cropVarietyGroups`；
- 查询、回填、更新链路兼容旧字段并支持结构化字段；
- 管理端与小程序已同步改造为“结构化优先、旧字段兜底”显示策略。
6. `assets` 模块已完成本轮升级：
- `media_asset` 已新增资源锁字段，普通管理员删除锁定资源前需密码解锁；
- 驳回审核已改为“驳回并进入回收站”，避免误操作直接丢失；
- 后台资源管理已拆为资源总览、资源库、资源审核、回收站、目录治理五类页面。
7. `auth` 模块已完成本轮升级：
- 小程序用户审核页与回收站页已拆开；
- 已通过用户支持移入回收站、恢复、彻底删除，并带并发冲突保护；
- 后台通知中心已接入个人通知收件箱与全部已读；
- 已新增 `/messages` 消息通知页，用于管理消息任务、异步派发与删除任务；
- 消息支持按后台路由权限、小程序用户状态、显式用户列表等对象定向投递。
8. 本轮继续补齐两类治理能力：
- `amap` 已切为“近 7 天日表 + 月表 + 月限额”模型，后台主视图只展示“总代理调用量”“当月真实高德用量”“缓存命中率”，并支持清空缓存；
- `assets` 新增资源引用表与删除前引用明细确认，删除提示按模块分页展示中文业务摘要；田块、企业信息、企业产品、企业荣誉、作物等图片引用会在增改删时同步维护；回收站彻底删除会同时删除本地文件、数据库资源记录与引用快照；
- `auth` 审核消息已扩展到具备控制台权限的小程序用户；`auth` 已补消息保留配置链路，但自动清理任务暂未启用。
9. `assets` 模块继续补齐了“小程序静态资源”专线：
- 新增 `miniapp_static_asset` 独立表，不再与普通资源库共表；
- 仅超级管理员可管理，删除时要求输入当前超级管理员登录密码；
- 文件固定保存到服务器真实目录 `/assets`，可通过指定保存文件名得到稳定 URL。
10. 小程序用户上传资源的引用追踪已延伸到用户头像链路：
- 登录/申请写资料、主动更新头像都会同步资源引用；
- 启动自举会补回历史用户头像资源引用，避免旧头像资源被误删。
