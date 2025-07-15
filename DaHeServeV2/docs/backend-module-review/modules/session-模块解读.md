# DaHeServeV2 `session` 模块解读（总版）

- 更新时间：2026-03-03
- 模块路径：`src/main/java/com/dahe/v2/modules/session`
- 文档目标：解释 token 会话模型、生命周期与上下游调用关系，作为登录链路稳定性的基础说明。

## 1. 模块职责

`session` 模块只负责 token 会话记录管理，不负责用户审批、角色判断、权限判定。具体职责：

1. 创建会话（签发 token、设置有效期、落库）。
2. 查询有效会话（状态有效且未过期）。
3. 失效会话（登出后置 `status=0`）。

## 2. 代码结构

- `model/TokenSession.java`
  - 会话实体，映射 `token_session` 表。
- `mapper/TokenSessionMapper.java`
  - MyBatis-Plus 基础 Mapper。
- `service/TokenSessionService.java`
  - 会话服务接口。
- `service/impl/TokenSessionServiceImpl.java`
  - 会话核心实现。

## 3. 数据表模型（`token_session`）

依据 `schema-auth.sql`：

- `id`：主键。
- `user_id`：用户 ID。
- `user_type`：登录用户类型（`admin/miniapp`）。
- `login_scene`：登录业务场景（如 `admin_console`、`task_center`）。
- `device_id` / `device_name`：前端设备标识与设备名称。
- `client_ip` / `user_agent`：客户端来源信息。
- `access_token`：唯一 token（唯一索引 `uk_token_access_token`）。
- `expires_at`：会话过期时间。
- `status`：1 有效，0 失效。
- `created_at` / `updated_at`：审计时间。
- 索引：`idx_token_user`、`idx_token_user_type`、`idx_token_scene`、`idx_token_device_id`、`idx_token_expires`。

## 4. 方法语义

## 4.1 `findValidByToken(String token)`

过滤条件：

1. `access_token = token`
2. `status = 1`
3. `expires_at >= now()`

语义：只返回“当前时刻可用”的会话记录。

## 4.2 `createSession(Long userId, String userType, String loginScene, SessionDeviceContext deviceContext, int validDays)`

行为：

1. 生成新 token（`IdUtil.fastSimpleUUID()`）。
2. `status=1`。
3. 写入 `userType/loginScene` 与设备上下文（设备标识、设备名、IP、UA）。
4. `expires_at = now + max(1, validDays)`。
5. 保存并返回会话实体。

设计意图：

- 防止调用方传入 0/负数导致会话立即过期。

## 4.3 `invalidateToken(String token)`

行为：

1. 按 token 查询 `status=1` 记录。
2. 命中后将 `status` 更新为 0。
3. 未命中直接返回（幂等）。

设计意图：

- 软失效保留历史，便于审计与问题排查。

## 4.4 `invalidateByUserId(Long userId, String userType, String loginScene)`

行为：

1. 按 `userId` 查询当前有效会话（可选附加 `userType/loginScene` 维度）。
2. 批量更新 `status=0`（软失效）。
3. 同步清理 Redis 会话缓存。

用途：

- 当前用户“全端下线”（`logout-all`）。
- 管理端“强制指定用户下线”。

## 5. 上下游调用关系

上游调用方（目前）：

- `auth/AdminAuthController`
  - 后台登录签发会话、会话校验、登出失效。
- `miniapp/MiniappAuthController`
  - 小程序登录签发会话、会话校验、登出失效。
- `auth/ApiAuthInterceptor`
  - 每次受保护请求都查询 token 是否有效。
- `assets/MediaAssetController`
  - 某些上传/访问链路复用 token 会话校验。

结论：

- `session` 是后端“登录后访问控制”的基础设施模块。

## 6. 生命周期与状态流转

状态机可简化为：

1. `createSession`：创建 `status=1`。
2. 运行期：`findValidByToken` 按 `status+expires_at` 判活。
3. `invalidateToken`：主动退登将 `status=0`。
4. 被动过期：不改状态，但因 `expires_at < now` 不再被判定为有效。

## 7. 边界与注意点

1. 当前实现允许同一用户存在多个并行 token（多端并发登录）。
2. 同时支持“当前 token 下线”与“全端下线”两种模式。
3. 过期 token 不会被定时清理，依赖查询条件过滤。
4. token 采用随机 UUID，未加设备指纹等附加安全上下文。

## 8. 本轮注释补充

本轮为以下文件补充了详细注释：

- `model/TokenSession.java`
- `mapper/TokenSessionMapper.java`
- `service/TokenSessionService.java`
- `service/impl/TokenSessionServiceImpl.java`

## 9. 回归建议

1. 创建会话：`validDays=0/-1` 时应自动修正为 1 天。
2. 失效会话：重复登出同 token 不应报错（幂等）。
3. 过期会话：`findValidByToken` 应返回 null。
4. 并发登录：同用户多 token 同时存在时应均可被独立校验。

## 10. 最近一次重构更新（2026-03-03）

本轮围绕“会话可接入 Redis、并记录登录来源”完成升级：

1. 会话模型扩展
- `token_session` 新增字段：
  - `user_type`：`admin/miniapp`
  - `login_scene`：登录场景（如 `admin_console`、`field_record`）
- 新增索引：
  - `idx_token_user_type`
  - `idx_token_scene`

2. 会话服务接口升级
- `createSession` 增强为：
  - `createSession(Long userId, String userType, String loginScene, int validDays)`
- 旧签名保留默认实现，兼容历史调用。

3. Redis 会话缓存能力（可配置）
- 配置项：
  - `app.session.redis-enabled`（当前默认已启用）
  - `app.session.redis-key-prefix`（默认 `dahe:v2:session:`）
- 行为策略：
  1. 查询：优先读 Redis，未命中回源 DB。
  2. 创建：DB 落库后写 Redis（TTL=会话剩余秒数）。
  3. 失效：先删 Redis，再将 DB `status` 置 0。
- 可靠性策略：Redis 异常仅日志告警，DB 路径兜底。

4. 当前环境配置（2026-03-03）
- `spring.redis.host=47.96.74.224`
- `spring.redis.port=6379`
- `spring.redis.password` 已配置
- 说明：该配置用于会话缓存接入验证与后续联调。
5. 自举与脚本同步
- `schema-auth.sql` 与 `SchemaBootstrapRunner` 均已补充 `token_session` 新字段/索引兼容逻辑。

## 11. 批次1落地补充（2026-03-03）

1. 新增认证接口（按端）
- `POST /api/v2/admin/auth/logout-all`
- `POST /api/v2/miniapp/auth/logout-all`

2. 新增管理端会话治理接口
- `POST /api/v2/admin/users/{id}/sessions/revoke`

3. 前端迁移状态
- `DaHeAdminV2`：登录已携带 `deviceContext`；退出已迁移到 `/admin/auth/logout-all`。
- `DaHeAdminV2`：用户管理/用户审核已接入 `/admin/users/{id}/sessions/revoke`（支持单条与批量强制下线）。
- `DaHeAppV2`：登录已补齐 `loginScene` 与 `deviceContext`；退出已迁移到 `/miniapp/auth/logout-all`。
