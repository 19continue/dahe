2026-03-11 23:25:00 +08:00 | DaHeServeV2 | main | 7d49566 | fix(admin-assets): tighten notice state and asset lock flow
- 后台/小程序个人消息已读与删除链路补上持久化结果校验，避免前端误判成功后刷新仍显示未读
- 资源锁彻底收口到全局密码：设置密码移到资源上传策略，解除资源锁与删除到回收站都要求输入全局密码
- 新增 MyBatis 审计字段自动填充，并为历史资源/策略数据补齐 created_at、updated_at；回收站彻底删除时同步删除服务器文件
2026-03-11 23:25:00 +08:00 | DaHeAdminV2 | main | 4bcc4ec | refactor(admin): compact actions and split blacklist handling
- 后台通知中心继续只预览最新 3 条未读消息，消息通知站已读/删除后会主动同步头部未读数
- 资源库将全局密码管理移到“资源上传策略”，锁定资源按钮改为“解除资源锁”，并收紧操作栏宽度与按钮间距
- 小程序用户审核页新增“黑名单用户”弹窗入口，审核主列表只保留待审核/已驳回处理流
2026-03-12 00:35:00 +08:00 | DaHeServeV2 | main | 0eca33e | feat(amap): switch quota governance to monthly usage model
- 高德真实用量改为“近 7 天日表 + 月表”模型，后台额度配置切到天气月限额、位置月限额
- `quota` 响应新增最近 12 个月真实用量明细，并按阈值向具备高德权限的后台管理员推送告警
- 资源删除确认摘要去掉 `#ID` 式回退，回收站彻底删除时同步清理资源引用快照
2026-03-12 00:35:00 +08:00 | DaHeAdminV2 | main | 90dc05b | refactor(admin): show monthly amap usage and grouped asset references
- 高德运维页新增月度真实用量表，展示最近 12 个月天气/位置真实用量与告警状态
- 资源删除确认按模块分组显示中文业务摘要，不再把不可读 ID 暴露给管理员
2026-03-12 01:35:00 +08:00 | DaHeServeV2 | main | d7903f4 | feat(assets-auth-amap): wire asset references and notice retention config
- 资源删除确认改为按模块分页加载引用摘要，单次最多返回 5 条，并补齐田块、企业信息、企业产品、企业荣誉、作物等图片资源引用绑定链路
- 高德 quota 响应收口为“总真实用量 + 当月真实用量 + 缓存命中率”，不再下发逐月真实用量列表
- 消息发布新增“自动消息清理开关 + 在库保留天数”配置链路，暂不实现自动清理任务
2026-03-12 01:35:00 +08:00 | DaHeAdminV2 | main | 4a1197b | feat(admin): refine asset references and notice retention settings
- 资源删除确认弹窗改为按模块分组分页加载引用信息，支持“点击加载更多”，不再一次性展示全部引用
- 高德运维页收口为当月真实用量展示，同时保留缓存命中率与总代理调用量
- 消息发布页新增消息保留配置卡片，可配置是否开启自动消息清理及在库保留天数
2026-03-12 01:55:00 +08:00 | DaHeServeV2 | main | 4ebb35f | fix(assets): backfill reference snapshot for legacy media
- 启动自举阶段会重建 `media_asset_reference`，并补回田块、企业信息、企业产品、企业荣誉、作物等历史图片引用
- 旧数据不需要重新编辑，部署重启后即可参与资源删除确认与引用摘要查询
2026-03-12 18:29:00 +08:00 | DaHeServeV2 | main | 94b8c92 | fix(assets): improve url-based asset rebinding
- 资源 URL 绑定在比对前统一去除 query/hash，并增加 `/uploads/相对路径` 兜底匹配，解决换绑时绝对/相对地址混用导致的引用追踪丢失
- 继续复用 `bindAssetsToBiz` 的解绑/重绑语义，不新增第二套资源引用流程
2026-03-12 18:29:00 +08:00 | DaHeAdminV2 | main | a2a0831 | refactor(admin): split miniapp review status views
- 小程序用户审核页主列表固定只看待审核用户，新增“已驳回用户”“已被回收资格用户”“黑名单用户”三个状态弹窗
- 小程序用户管理页固定只看已通过用户，不再混入已驳回、资格已回收、黑名单状态，降低审核与管理冲突面

2026-03-12 21:05:00 +08:00 | DaHeServeV2 | main | 149cde8 | feat(assets): add dedicated miniapp static asset management
- 新增 miniapp_static_asset 独立表、后台接口与 /assets/** 静态映射，小程序静态资源不再与普通资源库共表，也不走回收站
- 小程序静态资源仅超级管理员可管理，删除时要求输入当前超级管理员登录密码，并同步删除服务器真实文件
- 小程序用户头像上传链路已补资源引用绑定；启动自举会回填历史用户头像资源引用，避免误删
2026-03-12 21:05:00 +08:00 | DaHeAdminV2 | main | 28a1cba | feat(admin): add super-admin miniapp static asset page
- 后台新增“小程序静态资源”页，支持按固定保存文件名称上传、查看稳定 URL、编辑展示名称/备注、输入超级管理员密码永久删除
- 路由与侧边菜单均已限制为仅超级管理员可见可进，不污染普通角色的菜单权限配置
- 2026-03-12 DaHeAdminV2 `fc4b026` `fix(admin): avoid asset route refresh conflict and add error pages`2025-09-05 11:20:00 +08:00 | DaHeV2 | main | pending | chore(repo): prepare root github repository
- 初始化根目录 Git 仓库，补充 README、.gitignore 与 .gitattributes，统一说明后台、小程序、后端三端结构
- 将 DaHeServeV2、DaHeAdminV2、DaHeAppV2 以普通源码目录纳入根仓库，避免上传为不可用的内层 Git 指针
- 清理后端 application.yml 中的真实凭据默认值，并忽略证书、临时 SQL 转储、本地环境文件、依赖与构建产物
