# company API迁移清单（2026-03-05，批次5）

## 1. 结论
本批次 `company` 为内部重构，外部 API 路径与请求参数保持不变。

## 2. 后端变更摘要
1. 控制层由单控制器拆分为：
- `CompanyPublicController`
- `CompanyAdminController`

2. 后台控制器增加 `@AdminMenuCode("/company-intro")`，接入统一菜单鉴权。
3. 写服务新增事务边界与字段校验规则（长度、URL、联系方式格式）。
4. `company_info` 写链路新增“保存后单例收敛”（清理重复记录）。

## 3. API兼容性核对
以下路径均保持不变：
1. `GET /api/v2/public/company-intro`
2. `GET /api/v2/admin/company-intro`
3. `PUT /api/v2/admin/company-intro/info`
4. `GET/POST/PUT/DELETE /api/v2/admin/company-intro/products*`
5. `GET/POST/PUT/DELETE /api/v2/admin/company-intro/honors*`
6. `GET/POST/PUT/DELETE /api/v2/admin/company-intro/contacts*`

## 4. 前端迁移状态（DaHeAdminV2）
1. API 路径迁移：不需要。
2. 参数结构迁移：不需要。
3. 建议动作（可选）：直接展示后端新的字段校验提示文案。

## 5. 状态
`company`：已完成迁移核对，标记为“无需前端代码变更”。
