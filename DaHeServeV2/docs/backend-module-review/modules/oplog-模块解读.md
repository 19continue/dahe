# DaHeServeV2 `oplog` 模块解读（最终版）
- 更新时间：2026-03-05
- 模块路径：`src/main/java/com/dahe/v2/modules/oplog`

## 1. 模块职责
`oplog` 模块负责后台操作日志审计与可逆操作撤销：
1. 记录后台写操作请求的关键审计信息。
2. 为可撤销操作保存快照（undo payload）。
3. 提供日志分页查询能力。
4. 提供按日志执行撤销能力，并处理链式撤销约束。

## 2. 当前结构
1. `OperationLogInterceptor`：请求拦截、快照采集、日志写入。
2. `ApiResultTraceAdvice`：捕获统一 `Result` 的 `code/message`。
3. `OperationLogController`：后台日志查询与撤销接口。
4. `OperationLogServiceImpl`：撤销业务与链式撤销校验。
5. `OperationLog` + `OperationLogMapper`：实体与持久化映射。

## 3. 本轮优化点
1. 控制器权限声明补齐：`OperationLogController` 新增 `@AdminMenuCode("/operation-logs")`。
2. 撤销状态治理增强：
   1. 新增持久化字段 `undo_fail_reason`，记录失败原因。
   2. 撤销失败时回写 `failed + failReason`，成功后清空 `failReason`。
3. 撤销分发重构：
   1. 引入撤销类型与状态常量，消除散落硬编码。
   2. 将长 `if-else` 分发改为 `switch` 分发，提高可维护性。
4. 错误语义统一：常见撤销错误收口为常量，减少重复文本。

## 4. 数据模型更新
`operation_log` 新增字段：
1. `undo_fail_reason VARCHAR(500)`：撤销失败原因。

同时已同步：
1. `src/main/resources/db/schema-oplog.sql`
2. `SchemaBootstrapRunner` 自举补列逻辑
3. `OperationLog` 实体字段映射

## 5. 核心流程
### 5.1 日志写入
1. 拦截器识别写请求（`POST/PUT/PATCH/DELETE`）。
2. 采集目标对象与撤销快照。
3. 请求完成后组装日志实体并落库。

### 5.2 撤销执行
1. 校验日志合法性（存在、成功、可撤销）。
2. 校验链式撤销顺序（同目标对象必须先撤销最新日志）。
3. 按 `undoType` 分发恢复动作。
4. 成功：`undo_status=applied`；失败：`undo_status=failed` 且记录 `undo_fail_reason`。

## 6. API（保持不变）
1. `GET /api/v2/admin/operation-logs`
2. `POST /api/v2/admin/operation-logs/{id}/undo`

## 7. 本轮验证
1. `mvn -q -DskipTests compile` 已通过。
