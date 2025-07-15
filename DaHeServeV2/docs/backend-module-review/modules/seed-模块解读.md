# DaHeServeV2 `seed` 模块解读（总版）
- 更新时间：2026-03-05
- 模块路径：`src/main/java/com/dahe/v2/modules/seed`

## 1. 模块定位
`seed` 模块负责两条核心业务链路：
1. 种子批次管理（分页、详情、增删改、启停）。
2. 批次检测管理（检测记录增删改查、样本规则计算、发芽率计算）。

## 2. 分层结构
1. 协议层
- `SeedBatchController`
- `SeedRuleController`
- 负责请求参数接收、命令对象转换、统一结果返回。

2. 门面服务层
- `SeedAdminFacadeService`
- `SeedAdminFacadeServiceImpl`
- 统一编排批次/检测/规则全链路业务。

3. 领域支撑组件
- `SeedSampleCalculator`：样本数、发芽数、发芽率计算。
- `SeedQueryNormalizer`：查询参数与启停参数归一化。
- `SeedDynamicMetaAssembler`：动态 schema 元数据回填。
- `SeedDynamicSchemaSupport`：动态配置解析与 extraJson 校验。

## 3. 本轮核心改造
1. 检测写入幂等化
- `SeedAdminCommand.TestUpsert` 新增 `requestKey`。
- `createTest` 先按 `(batchId, requestKey)` 查询，命中即返回已有记录。
- 并发下若命中唯一键冲突，捕获后回查已有记录并返回。

2. 数据库约束补齐
- `seed_quality_test` 新增列：`request_key`。
- 新增唯一约束：`uk_seed_test_batch_request_key(batch_id, request_key)`。
- `SchemaBootstrapRunner` 同步支持历史库自动补齐列与索引。

3. 检测指标边界收口
- 新增 `purity/moisture` 区间校验（0~100）。
- 统一抛出 `SeedServiceException(VALIDATION_ERROR)`，返回明确业务错误。

4. 自动化测试补齐
- 新增 `SeedAdminFacadeServiceImplTest`。
- 覆盖幂等命中、重复提交通路、边界校验与 requestKey 持久化。

## 4. 关键模型
1. `SeedBatch`
- 批次主数据，含动态配置关联（`formConfigId/extraJson`）。

2. `SeedQualityTest`
- 检测记录，新增 `requestKey` 作为幂等标识。

3. `SeedQualityRule`
- 样本规则配置，驱动样本数/发芽率计算逻辑。

## 5. API 清单（/api/v2/seed-batches）
1. `GET /api/v2/seed-batches`
2. `POST /api/v2/seed-batches`
3. `GET /api/v2/seed-batches/{id}`
4. `PUT /api/v2/seed-batches/{id}`
5. `DELETE /api/v2/seed-batches/{id}`
6. `PUT /api/v2/seed-batches/{id}/enabled`
7. `GET /api/v2/seed-batches/{id}/tests`
8. `GET /api/v2/seed-batches/{batchId}/tests/{testId}`
9. `POST /api/v2/seed-batches/{id}/tests`
10. `PUT /api/v2/seed-batches/{batchId}/tests/{testId}`
11. `DELETE /api/v2/seed-batches/{batchId}/tests/{testId}`

补充说明：`POST /{id}/tests` 新增可选字段 `requestKey`（非破坏性升级）。

## 6. 可维护性约束
1. 检测写入统一通过门面服务，控制层不得拼装业务规则。
2. 幂等去重优先使用 `requestKey`，避免重复写入。
3. 所有质量指标边界校验在 service 收口，避免散落在 controller。
4. 动态 schema 相关解析仅由 seed 支撑组件处理，其他层禁止重复实现。
