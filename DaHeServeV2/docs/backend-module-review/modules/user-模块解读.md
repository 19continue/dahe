# DaHeServeV2 `user` 模块解读（总版）

- 更新时间：2026-03-03
- 模块路径：`src/main/java/com/dahe/v2/modules/user`
- 文档目标：沉淀用户主数据模型与查询能力，并明确其在认证、鉴权、业务模块中的复用边界。

## 1. 模块职责

`user` 模块在当前版本中定位为“用户主数据域层”，主要承担三类职责：

1. 用户实体映射
- 维护 `user` 表字段与 Java 实体 `AppUser` 的映射关系。
- 承载用户身份、审核状态、角色、启停与头像来源等核心属性。

2. 用户查询服务
- 提供按 `wxOpenId` 精确查找能力。
- 提供后台用户分页查询能力（关键字、审核状态、用户类型、启停状态）。

3. 用户领域写规则服务
- 提供 `UserDomainService`，统一收口用户关键写入规则：
  - 小程序登录建档/补丁更新与审核态迁移；
  - 审核通过/驳回字段变更；
  - 角色与控制台能力变更；
  - 启停变更；
  - 后台账号创建默认值落库。

说明：
- 当前 `user` 模块不直接暴露 Controller 接口。
- 认证与管理端入口（`AdminAuthController`、`MiniappAuthController`、`AdminUserController`）发起用户写操作，但字段级规则已下沉到 `user` 模块领域写服务统一处理。

## 2. 代码结构与职责映射

- `model/AppUser.java`
  - `user` 表实体，定义用户主字段、逻辑删除、时间自动填充规则。
- `mapper/AppUserMapper.java`
  - 继承 `BaseMapper<AppUser>`，提供通用 CRUD。
- `domain/UserDomainConstants.java`
  - 用户域常量目录（`userType/status/role/avatarSource`）。
- `service/AppUserService.java`
  - 定义用户查询域接口（openId 查找 + 分页查询重载）。
- `service/impl/AppUserServiceImpl.java`
  - 具体查询实现：条件拼装、排序、分页执行、openId 重复告警。
- `service/UserDomainService.java`
  - 用户领域写服务接口（关键写入规则收口）。
- `service/impl/UserDomainServiceImpl.java`
  - 领域写规则实现（事务化写入 + 默认值策略）。

## 3. 数据模型与 SQL

核心表定义位于：`src/main/resources/db/schema-auth.sql`

1. 主表：`user`
- 主键：`id`（BIGINT，雪花 ID）。
- 唯一键：`uk_user_wx_open_id`（`wx_open_id` 唯一）。
- 状态字段：
  - `status`：小程序审核状态（`pending/approved/rejected`）。
  - `enabled`：账号启停（1/0）。
  - `user_type`：账号类型（`miniapp/admin`）。
  - `is_super_admin`：超管标记（1/0）。
- 角色与能力：`role_code`、`can_console`。
- 头像相关：`avatar_url`、`wx_avatar_url`、`avatar_source`。
- 审核辅助：`apply_reason`、`reject_reason`。
- 通用字段：`deleted`、`created_at`、`updated_at`。

2. 索引设计
- `idx_user_status`、`idx_user_role`、`idx_user_type`、`idx_user_enabled`、`idx_user_status_type`。
- 支撑典型查询：审核池分页、用户类型过滤、启停筛选。

## 4. 核心方法解读

### 4.1 `findByWxOpenId`

入口：`AppUserServiceImpl.findByWxOpenId(String wxOpenId)`

流程：
1. 空值短路：`wxOpenId` 为空直接返回 `null`。
2. 条件构建：`eq(wx_open_id, wxOpenId)`。
3. 首次查询：`limit 2`，检测是否存在重复数据。
4. 若检测到重复 openId：
  - 追加 `count` 查询得到重复数量；
  - 写错误日志（openId、重复数量、前两条用户 ID）。
5. 返回排序后的首条用户（兼容历史行为，不中断主流程）。

用途：
- 登录建档与登录态恢复（`MiniappAuthController` / `AdminAuthController`）。
- 后台创建账号时 openId 冲突校验（`AdminUserController`）。

### 4.2 `pageUsers`（三组重载）

最终收敛到：
`pageUsers(String keyword, String status, String userType, Integer enabled, long page, long pageSize)`

筛选规则：
1. `keyword`（非空时）
- 在 `nickName/realName/phone/wxOpenId` 上做模糊匹配（OR 关系）。
2. `status`（非空时）
- 精确匹配审核状态。
3. `userType`（非空时）
- 精确匹配账号类型。
4. `enabled`（非空时）
- 精确匹配启停状态。
5. 排序
- `createdAt desc, id desc`，优先返回最新创建用户并保证翻页稳定。

用途：
- 后台用户管理分页接口：`/api/v2/admin/users`（`AdminUserController.page`）。
- 农事模块执行人列表筛选复用：`FarmRecordController`。

## 5. 跨模块调用关系

1. `auth`/`miniapp` 模块
- `AdminAuthController`、`MiniappAuthController`：登录、会话验证、个人资料读取依赖 `AppUserService`。
- `AuthPortalServiceImpl`：小程序登录写入调用 `UserDomainService.upsertMiniappUserFromLogin`。
- `AdminUserManageServiceImpl`：审核、角色变更、启停、后台账号创建调用 `UserDomainService` 收口写规则。
- `ApiAuthInterceptor`：根据 session 读取用户并做状态门禁。

2. `assets` 模块
- 资源权限窗口、上传者身份判断中复用用户信息。

3. `farm` 模块
- 农事记录执行人候选池使用 `pageUsers`（限定 `approved + miniapp + enabled=1`）。

## 6. 领域语义边界

`AppUser` 中关键字段语义需统一遵循：

1. `status`
- 仅对 `miniapp` 用户生效；`admin` 用户一般置空。

2. `userType`
- 决定登录入口与鉴权策略：
  - `miniapp`：需审核通过才可进入受保护接口。
  - `admin`：需角色有效且账号启用。

3. `enabled`
- 所有账号通用开关，禁用后会话不可用。

4. `roleCode`
- `admin` 账号角色来源于 `auth/role`；`miniapp` 常见为 `operator`。

## 7. 本轮新增注释覆盖

- `model/AppUser.java`
- `mapper/AppUserMapper.java`
- `service/AppUserService.java`
- `service/impl/AppUserServiceImpl.java`
- `domain/UserDomainConstants.java`
- `service/UserDomainService.java`
- `service/impl/UserDomainServiceImpl.java`

## 8. P0 落地更新（2026-03-03）

1. 关键写入规则已收口到 `UserDomainService`
- `AuthPortalServiceImpl` 与 `AdminUserManageServiceImpl` 不再直接散写 `status/roleCode/canConsole/enabled` 关键字段。

2. 用户域常量已中心化
- 新增 `UserDomainConstants` 作为用户语义常量来源；
- `AuthDomainConstants` 对应常量改为引用 `UserDomainConstants`，减少跨模块字面量漂移。

3. openId 重复数据告警已补齐
- `findByWxOpenId` 对重复数据会记录错误日志并带重复数量，避免“静默取首条”无痕问题。

## 9. 回归建议

1. openId 查询稳定性
- 校验空 openId、正常 openId、重复脏数据（多行）场景行为是否符合预期。

2. 分页筛选组合
- 覆盖 `keyword/status/userType/enabled` 的组合查询，验证总数与排序正确性。

3. 跨模块兼容
- 验证 `AdminUserController`、`AdminAuthController`、`MiniappAuthController`、`FarmRecordController` 对 `pageUsers` 的调用结果不回归。

4. 逻辑删除隔离
- 验证逻辑删除用户不会被默认查询链路返回。
