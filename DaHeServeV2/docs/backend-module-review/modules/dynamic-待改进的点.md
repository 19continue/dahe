# DaHeServeV2 `dynamic` 待改进的点（总版）
- 更新时间：2026-03-05
- 对应解读文档：`docs/backend-module-review/modules/dynamic-模块解读.md`

## 模块目标
把动态配置治理从“可用”升级为“可控”：版本唯一、删除安全、schema 合同可验证。

## 本轮已完成（P0）
1. 版本唯一约束（已完成）
- 新增唯一键：`uk_dynamic_module_scene_version(module_key, scene_key, version_no)`。
- 新增启动兼容自举：对历史重复版本做顺延修复后再建唯一键。
- `createConfig` 支持自动版本冲突重试，降低并发写冲突失败率。

2. 删除引用保护（已完成）
- 删除配置前检查引用：
  - `farm_process_step.form_config_id`
  - `seed_batch.form_config_id`
  - `seed_quality_test.form_config_id`
- 被引用时拒绝删除并返回具体引用统计。

3. Schema 合同校验增强（已完成）
- 校验 `schema_json` 必须为数组，元素必须为对象。
- 强化字段规则：`label/key/type/required/options` 归一化。
- 新增类型白名单与 option 结构校验，防止非法 schema 入库。
- `key` 自动规范化并去重，避免运行期字段冲突。

## 本轮已完成（P1）
1. 通用/种子写链路收口（已完成）
- 控制层写逻辑统一改为命令对象 `DynamicFormConfigCommand.Upsert`。
- 核心规则下沉到 `DynamicFormConfigService`，控制层只做协议转换。

## 仍待处理（P1）
1. 发布态治理
- 现状：仍允许同场景多条 `enabled` 并存。
- 建议：增加“发布/下线”语义，约束单场景单有效版本。

2. 数据库异常识别精度
- 现状：已对关键唯一键和表缺失做提示，但仍可进一步基于 SQLState 精细分类。

## 可排期优化（P2）
1. `current` 查询短缓存（按 module+scene+status）。
2. 配置 diff 审计增强（字段级变更摘要）。
3. 继续补齐并发与跨模块集成测试。
