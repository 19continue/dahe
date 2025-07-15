# DaHeServeV2 `export` 模块解读（最终版）
- 更新时间：2026-03-05
- 模块路径：`src/main/java/com/dahe/v2/modules/export`
- 对应待改进文档：`docs/backend-module-review/modules/export-待改进的点.md`

## 1. 模块职责
`export` 模块负责后台导出能力，覆盖两条业务链路：
1. 农事记录 CSV 导出：`/api/v2/admin/exports/farm-records.csv`
2. 种子检测 CSV 导出：`/api/v2/admin/exports/seed-tests.csv`

同时提供两类配置能力：
1. 导出模板管理：`export_template`
2. 导出字段词典管理：`export_field_dict`

## 2. 本轮重构重点（2026-03-05）
1. 控制层瘦身：
- `ExportController` 仅处理 HTTP 参数与响应，不再承载导出编排细节。
- 导出业务编排下沉到 `ExportCsvFacadeServiceImpl`。

2. 导出编排收口：
- 新增 `ExportCsvCommand` 承载导出查询参数，避免 controller 参数散落。
- 新增 `ExportCsvFacadeService` 统一导出门面。
- 保留原有导出行为：模板优先、词典列头、动态字段融合、UTF-8 BOM 输出。

3. 模板配置校验增强：
- `ExportTemplateService.normalizeFieldsJson` 落地强校验：
  - 必须是字符串数组；
  - 自动去重且保序；
  - 编码只允许字母/数字/下划线；
  - 单字段编码长度 <= 64；
  - 字段数量 <= 200。

4. 错误提示可维护化：
- 模板管理唯一键冲突 `uk_export_template_ver` 返回业务提示；
- 词典管理唯一键冲突 `uk_export_field_code` 返回业务提示；
- 数据表缺失场景返回可执行脚本提示，不再泛化为“内部错误”。

5. SQL 脚本修复：
- 重写 `schema-export.sql`，修复乱码与非法引号；
- 保证默认词典、默认模板初始化脚本可直接执行。

## 3. 当前代码结构
1. 控制层：
- `controller/ExportController`：导出入口（瘦控制层）
- `controller/ExportTemplateAdminController`：模板管理
- `controller/ExportDictionaryController`：字段词典管理

2. 服务层：
- `service/ExportCsvFacadeService` + `impl/ExportCsvFacadeServiceImpl`
- `service/ExportTemplateService` + `impl/ExportTemplateServiceImpl`
- `service/ExportFieldDictService` + `impl/ExportFieldDictServiceImpl`
- `service/ExportServiceException`

3. 配置与模型：
- `model/ExportTemplate` / `model/ExportFieldDict`
- `resources/db/schema-export.sql`

## 4. 鉴权接入方式
导出入口使用 `@AdminMenuCode("/exports")`，鉴权由 auth 模块统一路由鉴权处理，本模块不写角色硬编码判断。

## 5. API 兼容性结论
本轮无对外 API 路径/方法变更，属于内部重构与校验增强。
- `DaHeAdminV2` 无需改动调用代码；
- 仅建议前端补充对新增校验提示文案的展示（可选，不影响调用）。

## 6. 验证结果
执行：`mvn -q -DskipTests compile`
结果：通过。
