# DaHeServeV2 `miniapp` 模块待改进的点（总版）

- 更新时间：2026-03-06
- 对应解读文档：`docs/backend-module-review/modules/miniapp-模块解读.md`

## P0（优先）

1. 消除 controller-to-controller 依赖
- 现状：`MiniappFieldController`、`MiniappFarmRecordController`、`MiniappSeedController` 等仍复用原控制器方法做协议转发。
- 风险：控制层耦合高，后续参数演进时修改点分散。
- 建议：抽取 miniapp 应用服务（`service`/`service/impl`），控制器仅调用 service，逐步去除控制器互调。

2. 旧共享 API 物理下线计划
- 现状：鉴权层已对小程序账号拒绝旧共享路径，admin 仍可用。
- 风险：共享路由长期保留会增加认知负担与误调用概率。
- 建议：待 admin 端确认全部迁移完成后，分批删除旧共享控制器映射并在迁移清单标注下线日期。

## P1（近期）

1. 小程序接口集成测试补齐
- 重点场景：
  - miniapp 用户访问旧共享路径必须被拒绝；
  - miniapp 访问 `/miniapp/**` 必须成功；
  - admin 访问 `/admin/**` 不受影响。

2. 小程序专属 DTO 逐步独立
- 现状：部分接口仍复用后台 DTO/控制器入参。
- 建议：按模块迁移节奏逐步拆分，避免端侧耦合。

## 本轮已完成

1. 小程序端主要业务 API 已迁移到 `/api/v2/miniapp/**`。
2. 新增 miniapp 入口控制器：`field/farm/seed/meta/dynamic/farm-process/company/console`。
3. 小程序前端（DaHeAppV2）调用路径已切换到 miniapp 前缀。
4. 鉴权层已下线小程序账号对旧共享接口的访问（保留 admin 兼容）。
