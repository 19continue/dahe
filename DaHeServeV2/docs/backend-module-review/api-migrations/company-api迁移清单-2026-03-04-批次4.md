# company API 迁移清单（批次4）
- 日期：2026-03-04
- 模块：`company`
- 结论：本批次无 URL 变更，无请求/响应字段变更；仅后端内部架构重构（控制层瘦身、业务下沉应用服务）。

## 1. 接口迁移状态
| 接口 | 迁移状态 | 说明 |
| --- | --- | --- |
| `GET /api/v2/public/company-intro` | 已完成 | 路径不变，逻辑迁移到 `CompanyIntroApplicationService` |
| `GET /api/v2/admin/company-intro` | 已完成 | 路径不变，逻辑迁移到 `CompanyIntroApplicationService` |
| `PUT /api/v2/admin/company-intro/info` | 已完成 | 路径不变，请求体改为独立命令 DTO（字段不变） |
| `GET /api/v2/admin/company-intro/products` | 已完成 | 路径不变 |
| `POST /api/v2/admin/company-intro/products` | 已完成 | 路径不变 |
| `PUT /api/v2/admin/company-intro/products/{id}` | 已完成 | 路径不变 |
| `PUT /api/v2/admin/company-intro/products/{id}/enabled` | 已完成 | 路径不变 |
| `DELETE /api/v2/admin/company-intro/products/{id}` | 已完成 | 路径不变 |
| `GET /api/v2/admin/company-intro/honors` | 已完成 | 路径不变 |
| `POST /api/v2/admin/company-intro/honors` | 已完成 | 路径不变 |
| `PUT /api/v2/admin/company-intro/honors/{id}` | 已完成 | 路径不变 |
| `PUT /api/v2/admin/company-intro/honors/{id}/enabled` | 已完成 | 路径不变 |
| `DELETE /api/v2/admin/company-intro/honors/{id}` | 已完成 | 路径不变 |
| `GET /api/v2/admin/company-intro/contacts` | 已完成 | 路径不变 |
| `POST /api/v2/admin/company-intro/contacts` | 已完成 | 路径不变 |
| `PUT /api/v2/admin/company-intro/contacts/{id}` | 已完成 | 路径不变 |
| `PUT /api/v2/admin/company-intro/contacts/{id}/enabled` | 已完成 | 路径不变 |
| `DELETE /api/v2/admin/company-intro/contacts/{id}` | 已完成 | 路径不变 |

## 2. 前端对接影响
1. 无需修改请求路径
2. 无需修改请求参数与响应字段
3. 如前端对错误提示有依赖，保持原有 `NOT_FOUND` 语义即可

## 3. 后续动作
1. 后端后续若拆分 `CompanyPublicController`/`CompanyAdminController`，仍保持现有 API 路径不变
2. 若引入字段级校验增强，需在此文档追加“字段校验策略变化”记录