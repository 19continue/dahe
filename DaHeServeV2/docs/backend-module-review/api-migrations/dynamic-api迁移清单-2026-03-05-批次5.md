# dynamic API 迁移清单（批次5）
- 更新时间：2026-03-05
- 模块：`dynamic`

## 结论
本轮无路由变更，属于“行为增强 + 数据约束增强”，前端无需改路径，但需关注新错误提示语义。

## 后端变更
1. 删除接口增强引用保护
- `DELETE /api/v2/admin/dynamic-configs/{id}`
- `DELETE /api/v2/admin/seed-dynamic-configs/{id}`
- 新行为：若配置被业务数据引用，返回校验失败并带引用计数。

2. 创建/更新 schema 合同校验增强
- `POST/PUT /api/v2/admin/dynamic-configs*`
- `POST/PUT /api/v2/admin/seed-dynamic-configs*`
- 新行为：schema 不合法将返回更明确的 `VALIDATION_ERROR`。

3. 数据库约束
- 新增唯一键：`uk_dynamic_module_scene_version(module_key, scene_key, version_no)`。

## 前端迁移状态（DaHeAdminV2）
1. 当前状态：无需代码改动即可继续工作。
2. 建议优化：
- 对删除失败提示直接展示后端 message（可显示具体引用来源）。
- 对 schema 提交失败提示保留原文，便于配置人员定位字段问题。

## 迁移标记
- `后端`：已完成
- `前端`：可选优化（未阻塞）
