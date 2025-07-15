# 会话 API 迁移清单（2026-03-03，批次1）

- 适用项目：`DaHeServeV2`、`DaHeAdminV2`、`DaHeAppV2`
- 批次范围：`session + auth/miniapp` 第一批联动优化
- 目标：落地会话设备上下文、全端下线能力，并完成前端迁移

## 1. 路由迁移与新增

| 类型 | 路由 | 状态 | 说明 |
| --- | --- | --- | --- |
| 新增 | `POST /api/v2/admin/auth/logout-all` | 已完成（后端+前端） | 后台当前用户全端下线 |
| 新增 | `POST /api/v2/miniapp/auth/logout-all` | 已完成（后端+前端） | 小程序当前用户全端下线 |
| 新增 | `POST /api/v2/admin/users/{id}/sessions/revoke` | 已完成（后端+前端） | 管理员强制指定用户下线 |
| 保留 | `POST /api/v2/admin/auth/logout` | 已保留 | 单 token 下线（兼容） |
| 保留 | `POST /api/v2/miniapp/auth/logout` | 已保留 | 单 token 下线（兼容） |

## 2. 登录请求参数迁移

1. `POST /api/v2/admin/auth/login`
- 新增参数：`deviceContext`
- 字段：
  - `deviceId`
  - `deviceName`
  - `userAgent`
- 迁移状态：已完成（`DaHeAdminV2`）

2. `POST /api/v2/miniapp/auth/login`
- 必填参数：`loginScene`（本批前端补齐）
- 新增参数：`deviceContext`
- 字段：
  - `deviceId`
  - `deviceName`
  - `userAgent`
- 迁移状态：已完成（`DaHeAppV2`）

## 3. 后端会话模型升级

`token_session` 新增字段：

1. `device_id`
2. `device_name`
3. `client_ip`
4. `user_agent`

兼容性说明：

1. 旧会话记录不受影响，新增字段允许为空；
2. 启动自举会自动补齐结构；
3. 单表脚本已新增：`src/main/resources/db/table-token_session.sql`。

## 4. 前端迁移清单（已完成）

1. `DaHeAdminV2`
- 登录：`/admin/auth/login` 已携带 `deviceContext`
- 退出：已切换为调用 `/admin/auth/logout-all`
- 用户管理/用户审核：已接入 `/admin/users/{id}/sessions/revoke`（单条 + 批量强制下线）

2. `DaHeAppV2`
- 登录：`/miniapp/auth/login` 已补齐 `loginScene + deviceContext`
- 退出：已切换为调用 `/miniapp/auth/logout-all`

## 5. 联调验证点

1. 登录后检查 `token_session` 中设备字段是否入库。
2. 调用 `logout-all` 后，旧 token 调 `session/validate` 应返回游客态。
3. 管理员调用 `sessions/revoke` 后，被下线用户已签发 token 应全部失效。
