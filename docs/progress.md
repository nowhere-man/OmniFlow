# OmniFlow 实现进度

更新时间：2026-07-12

本文档记录当前工作区的实际实现状态；需求与验收范围仍以 `docs/1.Requirements.md`、`docs/2.Architecture.md`、`docs/3.TechDesign.md` 和 `docs/4.Goal.md` 为准。

## 当前结论

- 文档定义的首版共享业务、Android、iOS 和 macOS 源码功能已实现。
- Android Debug APK 已成功生成。
- Apple 共享层已通过 iOS Simulator 与 macOS ARM64 Kotlin/Native 源编译，Swift 源码通过语法解析；最终 framework 链接和 App 构建仍受本机未安装完整 Xcode 限制。
- v0.2.1 修复了账本删除余额、备份校验、导入来源保留、软删除订单去重、统计口径和 Apple UI 一致性问题。

## 已实现范围

### 共享 KMP

- SQLDelight 领域存储、迁移、软删除、账户余额校准与交易余额联动。
- 账本、账户、分类、标签、规则、提醒和应用偏好管理。
- 首页、日期明细、搜索、统计、同比/环比、年度账单、一级分类到二级分类下钻和统一账户资产分布。
- 支付宝、微信、京东、美团、建设银行和青子账单解析；规则、分类记忆、绝对/疑似去重、预览编辑、批量操作和单事务入账。
- 全量快照备份/恢复、保留策略、自动延迟备份、iCloud/WebDAV 适配与同步状态。
- 青子兼容 JSON 全量及按日期/交易 ID 增量导出。
- 规则优先级重排由共享仓储在单个数据库事务内完成。

### Android

- 五项主导航：首页、统计、记账、搜索、更多。
- 首页日历/筛选/日期明细，统计范围与账本筛选、趋势交互、排行编辑、分类下钻、资产分布和年度账单。
- 交易新增、编辑、删除、标签、二级分类、数字键盘和不计入收支。
- 完整管理页、提醒与通知、导入预览/批量编辑、青子导出、WebDAV 备份恢复、应用锁和外观偏好。
- AndroidKeyStore AES-GCM 保存 WebDAV 密码，并迁移旧明文凭据。
- 统一 SVG 图标、Launcher 图标和 Android 备份排除规则。

### iOS 与 macOS

- Xcode 双 Target 工程、Kotlin/Native bridge 与 SwiftUI 五项主导航。
- iOS 原生页面与 macOS Sidebar、菜单、快捷键、设置窗口和桌面布局。
- 首页日历/日期明细、统计交互与分类下钻、交易编辑、搜索、完整管理、提醒、导入/导出、iCloud/WebDAV、Keychain、应用锁和通知。
- Apple 平台 XLSX/XLS 解析桥与统一 SVG 资源。

## 验证记录

- 通过：`./gradlew :shared:jvmTest`。
- 通过：`./gradlew :androidApp:compileDebugKotlin :androidApp:assembleDebug :androidApp:lintDebug`。
- 通过：`./gradlew :shared:compileKotlinMacosX64`。
- 通过：Swift 5.7 全部 Swift 源码语法解析。
- 通过：iOS/macOS plist、entitlement 和 Xcode project `plutil` 校验。
- 通过：`git diff --check` 与未实现标记扫描。
- Android APK：`androidApp/build/outputs/apk/debug/androidApp-debug.apk`。
- SideStore Source：`sidestore-source.json`。

## 环境限制

- 当前机器只选择了 Xcode Command Line Tools。Kotlin/Native 源编译和 Swift 类型检查已通过，但 Apple framework 最终链接、iOS/macOS App 构建与真机/模拟器运行需要安装并选择完整 Xcode。

## 首版外范围

- 抖音 PDF 账单导入。
- 撤销上一次导入。
- 预算、多币种、复杂 RRULE、法定节假日顺延和工作日规则。
