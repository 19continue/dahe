# DaHeServeV2 `seed` 待改进的点（总版）
- 更新时间：2026-03-05
- 对应解读文档：`docs/backend-module-review/modules/seed-模块解读.md`

## 模块目标
确保种子检测链路在“可回归、可去重、可扩展”三个维度稳定运行，避免重复写入和隐性回归。

## 本轮已完成（P0）
1. 服务层自动化测试补齐（已完成）
- 新增 `SeedAdminFacadeServiceImplTest`。
- 覆盖核心场景：
  - 幂等键命中直接返回已有记录。
  - 并发重试触发唯一键冲突时回读已有记录。
  - 纯度边界校验。
  - 幂等键持久化。

2. 检测写入幂等保护（已完成）
- `createTest` 新增 `requestKey` 幂等键机制。
- 同批次同 `requestKey` 重复请求将返回已有记录，不重复写库。
- 数据库新增唯一约束：`uk_seed_test_batch_request_key(batch_id, request_key)`。
- 增加运行期兼容自举（`SchemaBootstrapRunner`）以自动补齐列和索引。

3. 检测指标区间约束（已完成）
- `purity/moisture` 统一校验为 `0~100`。
- 异常统一返回 `VALIDATION_ERROR` 语义，避免“写入后失败”的脏数据风险。

## 仍待处理（P1）
1. DTO 独立文件化
- 现状：部分请求 DTO 仍在 controller 内部类。
- 建议：迁移到模块独立 DTO 文件，进一步瘦身控制层。

2. 动态 schema 短缓存
- 现状：高频查询下存在重复解析。
- 建议：在配置版本不变场景增加短缓存，减少重复开销。

3. 错误语义跨模块统一
- 现状：seed 已有 `SeedServiceException`，但仍可进一步抽象统一异常规范。
- 建议：后续按全项目统一异常体系收口。

## 可排期优化（P2）
1. 批次/检测快照审计增强。
2. 批量导入导出能力增强。
