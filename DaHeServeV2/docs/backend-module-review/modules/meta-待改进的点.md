# DaHeServeV2 `meta` 待改进的点（总版）
- 更新时间：2026-03-05
- 对应解读文档：`docs/backend-module-review/modules/meta-模块解读.md`

## 模块目标
确保元数据与术语词典链路在并发写入、权限边界、服务分层上可长期维护。

## 本轮已完成（P0）
1. `replace-all` 清空窗口风险治理（已完成）
- 由“先删后插”改为“增量写入 + 差异删除”。
- 在事务内按 `source_text` 做新增/更新/删除收敛，避免全表先清空窗口。

2. 词典唯一冲突语义统一（已完成）
- 服务层新增 `createRow/updateRow/replaceAllRows`，统一捕获唯一键冲突并返回稳定业务提示“源术语已存在”。
- 避免并发写入时直接抛数据库异常给前端。

3. 元数据选项接口登录边界显式化（已完成）
- `MetaOptionController` 显式校验登录态（无用户返回 `UNAUTHORIZED`）。
- 避免上层策略调整时出现边界不清。

## 本轮已完成（P1）
1. 职责拆分（已完成）
- 新增 `MetaOptionQueryService` + `MetaOptionQueryServiceImpl`。
- 元数据选项查询逻辑从 controller 下沉到 service，控制层仅保留协议与鉴权。

2. 死依赖清理（已完成）
- 移除 `MetaOptionController` 未使用的 `SeedBatchService` 依赖。

## 仍待处理（P1）
1. 词典同步版本策略增强
- 现状：版本仍为 `count + latestUpdatedAt + latestId`。
- 建议：后续考虑哈希摘要或单调版本号字段。

2. 查询上限配置化
- 现状：选项查询仍使用固定上限（5000）。
- 建议：抽为配置项并结合监控调优。

## 可排期优化（P2）
1. 元数据选项短缓存。
2. 文本归一化工具继续抽象复用。
3. 补齐词典全量替换与并发场景自动化测试。
