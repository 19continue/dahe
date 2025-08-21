# DaHeAppV2

DaHe V2 小程序（uni-app），在保持原有视觉风格的基础上，对功能与结构进行重设计。

核心模块：
- 农事管理
- 田块分布
- 种子质量

后端接口：默认对接 `DaHeServeV2` (`/api/v2/**`)。

## UI 组件库（微信小程序端）

当前接入：`tdesign-uniapp`（`easycom` 自动按需解析）。

- 依赖安装：`npm i tdesign-uniapp -S`
- 已接入页面：
  - `pages/home/record-edit`
  - `pages/seed/batch-edit`
  - `pages/seed/test-edit`
- 注册方式：在 `pages.json` 的 `easycom.custom` 中配置 `^t-(.*)` -> `tdesign-uniapp/$1/$1.vue`。
- 全局样式：在 `App.vue` 引入 `tdesign-uniapp/common/style/theme/index.css`。

