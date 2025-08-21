# DaHeAppV2 全量细节文档（UTF-8）

> 适用范围：仅 `DaHeAppV2`。  
> 目的：提供“可独立完成整套 UI 设计”的单一资料源。  
> 编码声明：本文档以 UTF-8 输出；源码读取按 UTF-8 进行。

## 1. 项目基线

### 1.1 技术与运行
- 框架：`uni-app` + `Vue 3`
- 组件库：`tdesign-uniapp@^0.6.3`
- 路由与页面配置：`pages.json`
- 网络层：`utils/request.js`
- 认证与权限：`utils/auth.js`
- 可访问性（老人模式）：`utils/accessibility.js`
- 定位与天气：`utils/amap.js`

### 1.2 全局页面结构
- 全部页面使用 `navigationStyle: custom`（自绘顶部导航）。
- TabBar 仅 2 项：
1. `pages/home/index`（首页）
2. `pages/my/index`（我的）

### 1.3 主题设计令牌（当前代码）
来自 `static/tdesign-theme.scss`、`static/theme.scss`。

- 主色：`--dh-color-brand: #2f7d45`
- 主色按下：`--dh-color-brand-press: #256639`
- 主色浅底：`--dh-color-brand-light: #e8f3eb`
- 页面背景：`--dh-color-bg: #f3f5f2`
- 卡片背景：`--dh-color-surface: #ffffff`
- 边框：`--dh-color-border: #dbe4dc`
- 主文本：`--dh-color-text: #1f2b22`
- 次文本：`--dh-color-text-sub: #5f6d63`
- 危险色：`--dh-color-danger: #d74b4b`
- 圆角：`12rpx / 16rpx / 22rpx`
- 阴影：`--dh-shadow-soft`、`--dh-shadow-brand`
- 字体：`SF Pro + PingFang SC + 微软雅黑` 回退链

## 2. 信息架构与路由地图

### 2.1 路由清单

| 路由 | 模块 | 权限 | 路由参数 |
|---|---|---|---|
| `pages/auth/login` | 登录申请 | 无 | - |
| `pages/home/index` | 首页 | 已审批用户 | - |
| `pages/home/record-list` | 农事记录列表 | 已审批用户 | `fieldId`、`cycleId`（可选） |
| `pages/home/record-detail` | 农事记录详情 | 已审批用户 | `id` |
| `pages/home/record-edit` | 新建/编辑农事记录 | 已审批用户 | `id`、`fieldId`、`stepId`（可选） |
| `pages/field/index` | 田块列表 | 已审批用户 | - |
| `pages/field/detail` | 田块详情 | 已审批用户 | `id` |
| `pages/seed/index` | 种子批次列表 | 已审批用户 | `console=1`（可选） |
| `pages/seed/batch-detail` | 批次详情 | 已审批用户 | `id` |
| `pages/seed/batch-edit` | 新增/编辑批次 | 主管/管理员 | `id`（可选） |
| `pages/seed/test-edit` | 新增/编辑检测 | 已审批用户（通常从批次详情进入） | `batchId`、`testId`（可选） |
| `pages/seed/settings` | 检测规则 | 已审批用户可看，管理员可改 | - |
| `pages/my/index` | 我的 | 已审批用户 | - |
| `pages/my/settings` | 设置 | 已审批用户 | - |
| `pages/my/notices` | 消息通知 | 已审批用户 | - |
| `pages/my/help` | 帮助中心 | 已审批用户 | - |
| `pages/my/about` | 关于我们 | 已审批用户 | - |
| `pages/my/console` | 控制台入口 | `canConsole=1` | - |
| `pages/my/user-manage` | 账号审核与角色 | 管理员 | - |
| `pages/my/field-manage` | 田块定位与种植计划 | `canConsole=1` | - |
| `pages/company-intro/index` | 企业介绍 | 已审批用户 | - |

### 2.2 主要导航关系
- 首页 -> 农事记录新增 / 种子批次列表 / 田块列表 / 农事记录列表 / 田块详情
- 田块列表 -> 田块详情
- 田块详情 -> 农事记录列表 / 农事记录详情 / 农事记录新增（带步骤）
- 农事记录列表 -> 农事详情 / 农事新增
- 农事详情 -> 农事编辑
- 种子列表 -> 批次详情 -> 检测新增/编辑
- 我的 -> 设置/通知/帮助/关于/企业介绍/控制台
- 控制台 -> 用户管理 / 田块管理 / 种子管理 / 检测规则

## 3. 角色与权限模型

### 3.1 角色字段
- `roleCode`: `admin` / `supervisor` / `operator`
- `status`: `pending` / `approved` / `rejected`
- `enabled`: `1` 启用，`0` 停用
- `canConsole`: `1` 可进控制台

### 3.2 权限函数（`utils/auth.js`）
- `isApprovedUser()`：控制核心页面准入
- `canManageSeedBatch()`：`admin/supervisor` 可管理批次
- `canUseConsole()`：控制台准入
- `isAdminUser()`：管理员专属页面和操作

### 3.3 页面准入规则
- 未审批/未登录：强制 `reLaunch('/pages/auth/login')`
- 控制台页面：无 `canConsole` 则 toast 后返回
- 用户管理页面：非管理员禁止访问

## 4. 本地存储与会话

| Key | 用途 |
|---|---|
| `dahe.v2.baseUrl` | 后端地址配置 |
| `dahe.v2.accessToken` | JWT Token |
| `dahe.v2.authUser` | 当前用户对象 |
| `dahe.v2.mockOpenId` | 登录演示身份 mock |
| `dahe.v2.elderMode` | 老人模式开关 |
| `dahe.v2.notifications` | 消息提醒开关 |
| `dahe.v2.homeGuideSeen` | 首页引导是否已展示 |
| `dahe.v2.operatorName` | 农事记录默认操作员 |

## 5. 核心数据实体（面向 UI）

### 5.1 用户 `User`
- `id`
- `realName`、`nickName`、`phone`
- `avatarUrl`、`wxAvatarUrl`、`avatarSource`
- `userType`、`roleCode`、`status`、`enabled`
- `canConsole`
- `applyReason`、`rejectReason`

### 5.2 田块 `Field`
- 基础：`id`、`name`、`areaMu`、`status/stage`
- 作物：`cropType`、`cropVariety`
- 区域：`province`、`city`、`district`、`township`
- 定位：`locationLat`、`locationLng`、`locationDesc`、`formattedAddress`
- 视觉：`coverImageUrl`
- 扩展：`cropVarietyGroups`、`cropsJson`、`currentPlanName`

### 5.3 种植计划 `Cycle`
- `id`、`cycleName/planName`
- `status`（active/completed）
- `isCurrent`
- `startDate`、`endDate`
- `planMode`
- `cropsJson`（作物+品种+模板）
- `templateIdsJson`

### 5.4 流程 `Process`
- `templateName`
- `selectedCycleId`
- `selectedSegmentKey`
- `steps[]`：`id`、`stepName`、`sortOrder`、`requirementDesc`、`formSchema`
- `segments[]`：`segmentKey`、`segmentName`、`steps[]`

### 5.5 农事记录 `FarmRecord`
- `id`、`fieldId`、`cycleId`、`stepId`
- `workDate`
- `operatorName`
- `notes`
- 天气：`weather`、`temperature`、`humidity`、`windDirection`、`windPower`、`weatherReportTime`、`weatherLocation`
- 动态字段：`extraJson`
- 图片：通过 `imageAssetIds` 关联 `assets`
- 权限：`canEdit`、`canDelete`

### 5.6 资源 `Asset`
- `id`、`fileName`、`fileUrl`
- `moduleKey`（field/farm/seed/auth/...）

### 5.7 种子批次 `SeedBatch`
- `id`、`batchCode`
- `cropType`、`varietyName`
- `productionDate`、`remark`
- `enabled`
- 动态配置：`formConfigId`、`formSchema`、`extraJson`

### 5.8 检测记录 `SeedTest`
- `id`、`batchId`
- `testDate`、`testType`
- `sampleCount`、`germinationCount`、`germinationRate`
- `purity`、`moisture`、`thousandGrainWeight`
- `conclusion`（pass/warn/fail）
- `testerName`、`remark`
- 动态配置：`formConfigId`、`formSchema`、`extraJson`

### 5.9 检测规则 `SeedSetting`
- `fixedSampleSize`（1 固定 / 0 可编辑）
- `defaultSampleSize`
- `remark`

### 5.10 通知 `Notice`
- `id`、`title`、`content`
- `noticeType`（review/status/system）
- `isRead`
- `createdAt`

## 6. 接口契约地图（按域）

### 6.1 认证与用户
- `POST /miniapp/auth/login`：登录申请/登录
- `GET /miniapp/auth/me`：获取当前用户
- `POST /miniapp/auth/logout`：退出
- `GET /miniapp/auth/me/notices`：我的通知分页
- `PUT /miniapp/auth/me/notices/:id/read`：标记已读

### 6.2 田块与农事
- `GET /fields`：田块列表（支持关键字、阶段、作物、品种、乡镇、includeDisabled）
- `GET /fields/:id`：田块详情
- `PUT /fields/:id`：田块更新
- `GET /fields/:id/process`：田块当前流程
- `GET /fields/:id/cycles`：计划列表
- `POST /fields/:id/cycles`：新增计划
- `PUT /fields/:id/cycles/:cycleId/current`：切当前计划
- `GET /fields/:id/farm-records/recent`：田块最近农事
- `GET /farm-records`：农事记录分页
- `GET /farm-records/:id`：农事详情
- `POST /farm-records`：新增农事
- `PUT /farm-records/:id`：编辑农事
- `DELETE /farm-records/:id`：删除农事

### 6.3 种子与检测
- `GET /seed-batches`
- `GET /seed-batches/:id`
- `POST /seed-batches`
- `PUT /seed-batches/:id`
- `GET /seed-batches/:id/tests`
- `GET /seed-batches/:id/tests/:testId`
- `POST /seed-batches/:id/tests`
- `PUT /seed-batches/:id/tests/:testId`
- `DELETE /seed-batches/:id/tests/:testId`
- `GET /seed-settings`
- `PUT /seed-settings`

### 6.4 控制台/管理
- `GET /admin/users/pending-count`
- `GET /admin/users`
- `PUT /admin/users/:id/approve`
- `PUT /admin/users/:id/role`
- `PUT /admin/users/:id/enabled`

### 6.5 元数据与动态配置
- `GET /meta/options/crops`
- `GET /meta/options/varieties`
- `GET /meta/options/townships`
- `GET /meta/options/variety-groups`
- `GET /meta/options/crop-tree`
- `GET /dynamic-configs/current`（`moduleKey` + `sceneKey`）
- `GET /farm-process/templates`

### 6.6 文件与资源
- `POST /files/upload`
- `GET /assets`

### 6.7 高德代理（优先后端代理，失败回退直连）
- `GET /amap/weather/snapshot`
- `GET /amap/address/tips`
- `GET /amap/address/regeo`
- `GET /amap/address/geocode`
- `GET /amap/regions/provinces`
- `GET /amap/regions/cities`
- `GET /amap/regions/districts`
- `GET /amap/regions/townships`
- `POST /amap/audit`

## 7. 页面详细规格

## 7.1 登录页 `pages/auth/login`
- 目标：微信登录申请 + 审批状态反馈
- UI区块：
1. 顶部标题
2. 申请表单（姓名、昵称、头像、手机号、申请说明）
3. 演示身份快速选择
4. 提交按钮、重新查询按钮
5. 当前状态卡片
- 关键交互：
1. 调 `uni.login` 获取 code，失败时 fallback code
2. `POST /miniapp/auth/login`
3. 已审批则保存 token + 用户，跳首页
4. 头像支持微信授权和本地上传
- 校验：姓名必填

## 7.2 首页 `pages/home/index`
- 目标：总览入口 + 快捷操作
- UI区块：
1. 动态透明头部（滚动渐变）
2. 欢迎与统计卡（今日记录/田块总数/批次总数/最近更新时间）
3. 位置与天气
4. 功能入口（添加农事、种子质量、田块分布）
5. 辅助按钮（老人模式切换、刷新天气）
6. 农事总览卡、种子总览卡
7. 常用田块网格
8. 附近田块列表
- 数据：`/fields/common`、`/fields/nearby`、`/fields`、`/farm-records`、`/seed-batches`
- 特殊：首次进入弹引导；天气定位依赖后端地图代理接口

## 7.3 农事记录列表 `pages/home/record-list`
- 参数：`fieldId`、`cycleId`（可选）
- UI区块：田块筛选 + 记录列表 + 新建按钮
- 卡片信息：步骤标题、时间、田块、操作员、编辑权限标签
- 行为：分页加载，支持预带田块和计划

## 7.4 农事记录详情 `pages/home/record-detail`
- 参数：`id`
- UI区块：
1. 基本信息卡（时间、田块、操作员、天气全量）
2. 备注卡
3. 步骤参数卡（`extraJson` 按 schema 显示）
4. 图片网格
5. 编辑/删除按钮
- 权限：`canEdit`、`canDelete` 控制按钮与行为

## 7.5 农事记录编辑 `pages/home/record-edit`
- 参数：`id`（编辑）、`fieldId`、`stepId`
- 目标：统一承载新建/编辑、动态字段、图片、天气
- UI区块（实际为多卡片结构）：
1. 引导卡（4步提示 + 快捷动作）
2. 田块选择（`FieldSelector`）
3. 田块摘要（封面、计划名、区域、作物标签）
4. 步骤选择（`StepSelector`）
5. 步骤提醒
6. 动态字段（来自 `selectedStep.formSchema`）
7. 操作员
8. 时间选择（`DateTimeSelector`）
9. 可选补充折叠面板（天气 / 图片 / 备注）
10. 提交区
- 动态字段支持类型：`text/number/textarea/date/time/select/location`
- 图片：最多9张，先上传资产，再提交 `imageAssetIds`
- 校验：
1. 必选田块
2. 若有步骤列表必须选步骤
3. 动态必填字段必须完整
- 自动能力：
1. 自动就近田块
2. 自动天气
3. 动态字段定位填充

## 7.6 田块列表 `pages/field/index`
- UI区块：
1. 搜索
2. 乡镇筛选
3. 作物品种筛选
4. 阶段 Chips
5. 田块卡片列表
- 卡片信息：封面、名称、阶段、面积、作物品种、省市区/乡镇、地址

## 7.7 田块详情 `pages/field/detail`
- 参数：`id`
- UI区块：
1. 田块信息卡
2. 种植计划横向切换
3. 作物分段横向切换
4. 流程进度纵向链路
5. 农事时间轴（分日期分组）
- 行为：
1. 切计划后重拉流程与时间轴
2. 点步骤可直接创建该步骤记录

## 7.8 种子列表 `pages/seed/index`
- 参数：`console=1`（显示管理模式提示）
- UI区块：
1. 关键字搜索
2. 作物筛选 + 清空
3. 品种筛选 + 清空
4. 管理动作（新增批次）
5. 统计卡（总数/生产日期完整数/备注完整数）
6. 批次列表
- 卡片信息：批次号、品种、启用状态、作物、生产日期、备注

## 7.9 批次详情 `pages/seed/batch-detail`
- 参数：`id`
- UI区块：
1. 批次基础信息
2. 批次动态字段
3. 检测记录列表
4. 操作区（编辑批次/新增检测）
- 管理权限用户可编辑和删除检测

## 7.10 批次编辑 `pages/seed/batch-edit`
- 参数：`id`（编辑）
- 权限：`canManageSeedBatch`
- UI区块：
1. 批次号
2. 作物选择
3. 品种选择
4. 生产日期 + 快捷（今天/昨天/清空）
5. 动态扩展字段（`sceneKey=batch_fields`）
6. 备注
7. 提交按钮
- 校验：批次号/作物/品种/动态必填

## 7.11 检测编辑 `pages/seed/test-edit`
- 参数：`batchId`、`testId`
- UI区块：
1. 检测日期 + 快捷（今天/昨天/本周一）
2. 检测类型
3. 样本数（受规则控制是否只读）
4. 发芽数量
5. 自动芽率展示
6. 纯度/水分/千粒重
7. 动态字段（`sceneKey=test_fields`）
8. 结论
9. 检测员
10. 备注
11. 提交按钮
- 校验：
1. 必须有 `batchId`
2. 样本数有效
3. 发芽数有效且不大于样本数
4. 动态必填完整

## 7.12 检测规则 `pages/seed/settings`
- 权限：所有已审批用户可看；仅管理员可编辑
- UI区块：固定样本开关、默认样本数、备注、保存

## 7.13 我的首页 `pages/my/index`
- UI区块：
1. 用户资料卡（头像、状态、角色、操作员）
2. 控制台入口（有权限才显示）
3. 企业介绍/设置
4. 通知/帮助/关于
5. 退出登录

## 7.14 设置页 `pages/my/settings`
- UI区块：
1. 接口地址编辑
2. 接口自检
3. 清缓存
4. 消息提醒开关
6. 老人模式开关
7. 重置首页引导

## 7.15 通知页 `pages/my/notices`
- UI区块：通知卡片列表 + 全部已读
- 行为：分页加载；点击未读即标记；支持全部标记已读

## 7.16 帮助页 `pages/my/help`
- UI区块：FAQ 折叠 + 快速引导动作

## 7.17 关于页 `pages/my/about`
- UI区块：产品定位、技术架构、版本

## 7.18 控制台入口 `pages/my/console`
- UI区块：管理入口卡片
1. 账号审核与角色（admin）
2. 田块定位与种植计划
3. 种子批次管理
4. 种子检测规则（admin）

## 7.19 用户管理 `pages/my/user-manage`
- 权限：管理员
- UI区块：
1. 搜索 + 状态筛选 + 可用性筛选
2. 待审核统计
3. 用户卡片
4. 审核动作（通过/通过+控制台/驳回）
5. 已审核动作（控制台开关、禁用开关）

## 7.20 田块管理 `pages/my/field-manage`
- 权限：控制台用户
- 核心目标：
1. 维护田块省市区/乡镇/阶段/定位/封面
2. 查看与切换种植计划
3. 创建新计划并绑定流程模板
- UI区块：
1. 田块选择 + 刷新 + 保存
2. 作物与区域信息展示
3. 省/市/区/乡联动选择
4. 定位信息（可一键用当前位置）
5. 封面图库选择
6. 现有计划列表（可切当前）
7. 新增计划表单（年份、模式、多作物、多模板、日期快捷）
- 校验：
1. 计划名称必填
2. 非休耕模式至少一条作物
3. 每条作物必须能匹配模板

## 7.21 企业介绍 `pages/company-intro/index`
- UI区块：公司简介、联系方式（点击复制）

## 8. 共享组件规格（重点）

## 8.1 田块选择组件 `components/field-selector/field-selector.vue`
- Props：
1. `value`
2. `fields`
3. `planMap`
4. `loading`
5. `title`
6. `placeholder`
- Emits：
1. `input(id)`
2. `change(field)`
- 内部功能：
1. 搜索（名称/乡镇/地址/计划名/作物标签）
2. Tab：全部/附近/常用
3. 乡镇Tag过滤
4. 附近田块固定定义为 20km 内田块，并按距离排序
5. 常用田块固定定义为当前用户已提交农事记录中的高频田块
6. 卡片显示封面、计划、面积、地址、作物+品种标签
- 说明：已包含“田块显示作物及品种”能力。

## 8.2 步骤选择组件 `components/step-selector/step-selector.vue`
- Props：`steps`、`segments`、`segmentValue`、`value`、`templateName`、`loading`
- Emits：
1. `segment-change(segment)`
2. `change(step)`
- 功能：
1. 分段切换
2. 步骤卡显示排序、生长阶段、模板名、步骤描述
3. 回填已选步骤

## 8.3 时间选择组件 `components/date-time-selector/date-time-selector.vue`
- Props：`date`、`time`
- Emits：`date-change`、`time-change`、`change({date,time})`
- 功能：
1. 统一日期+时间面板
2. 组件内部 footer 快捷：仅“现在”按钮
3. 点击“现在”直接写入当前日期时间并关闭弹层
- 说明：当前实现已满足“只保留现在快捷项且放组件内部”。

## 9. 动态表单与配置机制

- 来源接口：`GET /dynamic-configs/current`
- 场景：
1. `moduleKey=seed, sceneKey=batch_fields`
2. `moduleKey=seed, sceneKey=test_fields`
3. 农事步骤字段：来自流程步骤的 `formSchema`
- Schema通用字段：
1. `key`
2. `label`
3. `type`
4. `required`
5. `defaultValue`
6. `placeholder`
7. `options`
8. `unit`
- 存储：统一写入 `extraJson`

## 10. 状态反馈与错误处理规范（现状）

- 加载：骨架屏、`加载中...` 文案
- 空态：`暂无...` / `No matching...`
- 关键操作确认：删除类用 `showModal`
- 成功反馈：`showToast(icon=success)`
- 网络失败：`request.js` 统一提示；所有 baseUrl 失败后弹“接口连接失败”指导
- 未授权：清理 token + 跳转登录

## 11. 设计重构必须保持的行为约束

1. 不改接口字段与提交结构（尤其 `extraJson`、`imageAssetIds`、`cropsJson`、`templateIdsJson`）。
2. 不改权限门禁逻辑（`isApprovedUser/canUseConsole/isAdminUser/canManageSeedBatch`）。
3. 不移除老人模式开关与字号放大适配。
4. 不移除定位与天气自动填充能力。
5. 不移除动态表单能力。
6. 农事记录编辑仍需支持“先上传图片资产，再提交记录”。
7. 田块选择组件必须持续展示作物与品种标签。
8. 时间选择组件保持“组件内部仅现在快捷”。

## 12. 现状问题与设计前置校对

1. `pages/home/record-edit.vue`、`components/field-selector/field-selector.vue`、`components/date-time-selector/date-time-selector.vue` 存在部分中文文案乱码（源码本身）。
2. 首页仍包含 emoji 图标风格，和其余页面的 tdesign 风格不完全一致。
3. 页面风格已部分升级到 token 化，但仍有旧色值硬编码并存。

## 13. 设计交付建议（按本文件即可执行）

1. 先做全局设计系统：色板、字号、间距、圆角、阴影、状态色。
2. 再做页面模板：列表页模板、详情页模板、编辑页模板、设置页模板。
3. 对三大复杂组件单独出交互稿：田块选择、步骤选择、时间选择。
4. 最后逐页套版并回归“接口字段+权限+状态”一致性。
