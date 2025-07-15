# DaHeServeV2 `session` 模块待改进的点（总版）

- 更新时间：2026-03-03
- 对应解读文档：`docs/backend-module-review/modules/session-模块解读.md`

## P0（优先）

1. Redis 连接治理
- 现状：Redis 异常已降级到 DB，但缺少运行态告警。
- 建议：增加 Redis 失败率指标与告警阈值，防止长期静默降级。

## P1（近期）

1. 查询模型依赖数据库时间与应用时间一致
- 现状：使用 `LocalDateTime.now()` 与库中 `expires_at` 比较。
- 风险：时钟漂移可能导致误判。
- 建议：统一使用 UTC 存储与比较，或引入时钟源抽象。

2. 缺少会话清理策略
- 现状：过期记录长期保留。
- 风险：表持续膨胀，索引效率下降。
- 建议：增加定时归档/清理任务（按保留期删除）。

3. token 生成策略可进一步增强
- 现状：随机 UUID。
- 建议：评估更强随机源与长度策略，并对 token 泄露场景增加轮换机制。

## P2（优化）

1. 服务层单元测试不足
- 建议补充：
  - 有效/失效/过期场景测试；
  - 并发创建 token 的唯一性测试；
  - 幂等失效测试。

2. 缺少会话访问审计字段
- 建议：记录 `last_seen_at`，用于活跃会话治理与异常检测。

## 最近完成项（2026-03-03，批次1）

1. 已新增会话强制下线能力
- 服务层新增：`invalidateByUserId(userId)` 与按 `userType/loginScene` 维度批量失效。
- 认证端新增：`POST /api/v2/admin/auth/logout-all`、`POST /api/v2/miniapp/auth/logout-all`。
- 管理端新增：`POST /api/v2/admin/users/{id}/sessions/revoke`（强制指定用户下线）。

2. 已落地会话设备上下文
- `token_session` 新增：`device_id`、`device_name`、`client_ip`、`user_agent`。
- 登录会话创建链路已写入设备上下文。
- 已补齐脚本与启动自举兼容逻辑。
