# DaHeServeV2 `company` 模块解读（最终版）
- 更新时间：2026-03-05
- 模块路径：`src/main/java/com/dahe/v2/modules/company`
- 对应待改进文档：`docs/backend-module-review/modules/company-待改进的点.md`

## 1. 模块职责
`company` 模块负责企业介绍数据的聚合读写，覆盖四类实体：
1. `company_info`（企业基础信息）
2. `company_product`（产品）
3. `company_honor`（荣誉）
4. `company_contact`（联系方式）

## 2. 本轮优化重点（2026-03-05）
1. 控制层按端拆分，保持路径不变：
- 新增 `CompanyPublicController`：`/api/v2/public/company-intro`
- 新增 `CompanyAdminController`：`/api/v2/admin/company-intro/**`
- 删除原单体 `CompanyIntroController`

2. 后台鉴权接入统一路由模型：
- `CompanyAdminController` 增加 `@AdminMenuCode("/company-intro")`
- 模块内不写角色硬编码判断

3. 写链路数据校验增强：
- 新增/升级 `CompanyIntroValueNormalizer`，统一校验规则：
  - 文本长度校验；
  - URL 格式校验（支持 `http(s)` 或 `/` 开头路径）；
  - 联系方式类型白名单：`address/phone/email/website`；
  - 按类型校验联系方式内容（邮箱/电话/网站）。

4. 企业基础信息单例语义收敛：
- `upsertInfo` 保存后主动清理多余历史记录（逻辑删除），确保仅保留一条主记录。

5. 事务边界补齐：
- `CompanyInfo/Product/Honor/Contact` 写服务的增改删和启停操作均补充事务注解。

## 3. 当前代码结构
1. 控制层：
- `CompanyPublicController`
- `CompanyAdminController`

2. 门面层：
- `CompanyIntroFacadeService`
- `CompanyIntroFacadeServiceImpl`

3. 应用服务层：
- `CompanyIntroQueryServiceImpl`（只读聚合）
- `CompanyInfoCommandServiceImpl`
- `CompanyProductCommandServiceImpl`
- `CompanyHonorCommandServiceImpl`
- `CompanyContactCommandServiceImpl`
- `CompanyIntroValueNormalizer`
- `CompanyIntroMapper`

4. DTO：
- `CompanyIntroCommand`
- `CompanyIntroDTO`

## 4. API兼容性
本轮属于内部重构，所有对外接口路径保持不变，前端调用方式不变。

## 5. 验证结果
执行：`mvn -q -DskipTests compile`
结果：通过。
