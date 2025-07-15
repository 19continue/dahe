# DaHeServeV2 `oplog` 模块待改进的点（最终版）
- 更新时间：2026-03-05
- 当前状态：已完成权限声明补齐、撤销分发重构、失败原因落库。

## P0
1. 撤销能力仍高度依赖 `JdbcTemplate` 直接写表，建议逐步下沉到各业务模块 service，减少跨表耦合。
2. 撤销 payload 仍缺少版本号字段，后续结构演进时兼容成本高，建议增加 `payloadVersion`。

## P1
1. 增加 `oplog` 模块自动化测试，重点覆盖：
   1. 链式撤销顺序校验。
   2. 各 `undoType` 恢复分支。
   3. 撤销失败时 `undo_fail_reason` 回写。
2. 将 `restore_update` 字段白名单拆分为可配置注册机制，降低硬编码维护成本。

## P2
1. 日志字段脱敏策略可进一步统一（尤其 `query_string` 和 `result_message`）。
2. 结合前端需求，考虑在列表中展示 `undo_fail_reason`，提升排障效率。

## 已解决（本轮）
1. `OperationLogController` 缺少菜单权限声明的问题已解决。
2. 撤销失败无结构化原因的问题已解决（新增 `undo_fail_reason`）。
3. 撤销分发逻辑硬编码链过长的问题已解决（`switch` 分发 + 常量收口）。
