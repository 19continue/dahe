# DaHeServeV2 `field` 待改进的点（总版）
- 更新时间：2026-03-06
- 对应解读文档：`docs/backend-module-review/modules/field-模块解读.md`

## 本轮已完成（P0）
1. 作物品种字段结构化落地（已完成）
- `field` 新增 `crop_variety_groups_json`。
- 后端实体新增 `cropVarietyGroupsJson/cropVarietyGroups`。
- 新增统一编解码器，避免散落式字符串拼接。

2. 查询与回填链路兼容（已完成）
- 查询侧兼容旧字段和结构化 JSON。
- `page/detail` 响应统一回填 `cropVarietyGroups`，保障三端显示一致。

3. 更新误清空问题修复（已完成）
- `PUT /fields/{id}` 仅在显式携带作物字段时才覆盖。
- 前端仅修改基础字段时不会清空已有作物品种信息。

4. 三端展示统一（已完成）
- 管理端：`CropPairTags` 优先读 `cropVarietyGroups`。
- 小程序：新增统一解析工具 `utils/crop-variety.js`，页面共用。

## 仍待处理（P1）
1. 历史数据回填治理
- 现状：历史库仍可能只有旧 `crop_type/crop_variety`。
- 建议：补一次离线迁移脚本，将历史数据回填到 `crop_variety_groups_json`。

2. 自动化测试补齐
- 现状：缺少“多作物对齐/更新不误清空/回退解析”的单元与集成测试。
- 建议：为编解码器与关键接口增加测试用例矩阵。

3. 结构化查询性能优化
- 现状：JSON like 查询在数据量上升后会退化。
- 建议：后续引入明细表或可索引字段，逐步替换 JSON 模糊查询。

## 可排期优化（P2）
1. 字段写入审计补全（记录作物组合变更前后值）。
2. 前端筛选项改为后端下发结构化枚举，减少重复解析逻辑。