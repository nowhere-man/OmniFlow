# OmniFlow 实现进度

更新时间：2026-07-10

本文档记录当前工作区的实际实现状态，不以 `docs/4.Goal.md` 中的未勾选验收项替代代码事实。

## 已完成

### 工程与共享层

- 已建立 Gradle 多模块工程：根工程、`shared` KMP 模块和 `androidApp` Android 模块。
- `shared` 已配置 JVM 与 Android target，使用 SQLDelight、Coroutines、kotlinx-datetime 和序列化。
- 已建立 `SharedApp` 组合根；平台只通过 `HomeFacade`、`ManagementFacade`、`AnalyticsFacade`、`ImportWorkflow`、搜索和交易 Use Case 使用共享业务层。
- 已实现 JVM SQLite Driver 和 Android SQLite Driver 的创建入口。

### 领域模型与本地存储

- 已实现账本、账户、分类、标签、交易、规则、导入预览、搜索、统计、偏好等领域模型。
- 金额与余额均使用分单位的 `Money(Long)`，不使用浮点数。
- 已实现 SQLDelight schema：账本、账户及余额校准记录、分类、标签及交易标签关系、交易、规则、分类记忆、导入会话和预览项、应用偏好、提醒表、同步元数据表。
- 已实现账本、账户、分类、标签、交易、规则、分类记忆、导入会话、去重和初始数据的 SQLite Repository。
- 首次初始化会创建一个初始账本、预置一级分类和现金/支付宝/微信/京东/美团/抖音/建设银行账户；不会设置默认账本。
- 已实现账户余额混合模式：新增、编辑、删除交易会更新余额；手动校准会保存按天的余额与增量记录，且不创建交易。
- 已实现账本、账户、分类、标签及交易的软删除基础逻辑。

### 首页、搜索与统计业务

- 已实现首页 `HomeFacade`：按账本和月份查询月度收入、支出、结余、按日历日聚合的收支及按天倒序的交易分组。
- 首页日历筛选仅传给日历聚合，不影响月度汇总和交易明细。
- 已实现独立日期明细查询 `TransactionDetailQuery`。
- 已实现组合搜索：账本、关键词、收支类型、一级/二级分类、标签、账户、金额及日期范围；结果按时间倒序并汇总收入/支出。
- 已实现统计 `AnalyticsFacade`：收支汇总、上一个等长周期对比、趋势数据、收支排行榜、一级/二级分类占比、标签汇总、资产概览和年度账单表格。

### 手动交易与管理业务

- 已实现新增、编辑、删除交易 Use Case，并校验账本、账户、分类和正金额。
- 已实现账本、账户、分类、标签的新增、编辑、删除 Use Case。
- 已实现 `ManagementFacade` 的账本、账户、资产汇总、指定账本分类和标签的观察数据流。

### 导入与解析业务

- 已实现支付宝、京东、美团 CSV 解析；CSV 行列解析逻辑在 `commonMain`。
- 已实现微信 XLSX 和建设银行 XLS 解析；JVM 与 Android 均有 Apache POI 读取实现。
- 已实现青子记账 JSON 的核心实体解析，并且不把青子记账作为 `TransactionSource` 枚举值。
- 商户名、交易对方、商品说明等来源字段会去重并合并进备注；无值时不写入备注。
- 已实现导入格式识别、预览会话、规则优先匹配、分类记忆、绝对/疑似去重、逐项编辑、批量分类/排除和单事务确认入账。
- 确认入账时会创建缺失标签、写入交易与账户余额、写入分类记忆并清理导入会话。
- 已用真实样例手工验证解析记录数：支付宝 107、微信 86、京东 16、美团 18、建设银行 152、青子记账 4436。

### Android 平台骨架

- 已创建 Android Manifest、Application、MainActivity 和 Compose 依赖配置。
- 已创建 Android 平台 `OmniFlowViewModel`，通过 `SharedApp` 订阅首页和账本 Flow。
- 已实现 Android 五项底部导航骨架：首页、统计、记账、搜索、更多。
- 首页已实现月份前后切换、账本范围选择、日历全部/收入/支出筛选、按日倒序明细、列表/卡片切换和日期明细底部面板。

## 部分完成但尚未达到验收

- `Reminder.sq` 与 `SyncMeta.sq` 已建表，但没有提醒或同步业务实现。
- `AppPreference` 表和偏好键已存在；默认账本可读写，但首页账本范围、统计账本范围和明细展示模式尚未接入持久化。
- Android 首页已具备主要展示和交互骨架，但尚未完成月份选择器、日期面板内账本切换、交易笔数、交易编辑和新增交易入口。
- Android 统计、记账、搜索、更多仅有导航入口容器，尚未接入对应业务页面。
- Android 表格解析器已写入，但因本机没有 Android SDK，未执行 Android 编译或设备验证。
- 导入共享业务已完成，但各平台尚未实现系统文件选择、导入预览和确认入账界面。
- 规则的存储与导入执行已实现，规则管理页面尚未实现。

## 未完成

### 跨平台与界面

- Apple 工程、iOS UI、macOS Sidebar UI、SwiftUI ViewModel、Kotlin/Native Framework 和 SKIE 接入。
- Apple 的 GB18030 CSV、XLSX/XLS 平台解析实现。
- Android 的记账、统计、搜索、导入、规则、提醒、账本、账户、资产、分类、标签、设置和导出页面。
- 交易编辑、删除和不计入收支的原生交互界面。
- macOS 的窗口、菜单、快捷键和桌面信息密度适配。
- 项目内统一 SVG 图标资源及所有平台的图标映射。当前 Android 首页仅使用 Material 图标占位。

### 业务能力

- 首页和统计账本范围、明细展示模式的持久化。
- 默认账本的设置/取消入口。
- 提醒与周期事项、系统通知、应用锁、界面外观设置。
- iCloud/WebDAV Adapter、全量备份生成、备份记录保留、恢复覆盖、同步状态与重试。
- 青子记账兼容 JSON 导出及增量互通。
- SQLDelight schema migration 文件与升级路径。
- 导入来源选择、文件格式歧义处理和完整的用户可读进度/错误界面。

## 明确暂缓或不属于首版

- 抖音 PDF 账单导入暂不实现。
- 撤销上一次导入不属于首版范围。
- 预算、多币种、复杂 RRULE、法定节假日顺延和工作日规则不属于首版范围。

## 验证记录

- 已通过：`./gradlew --no-daemon --console=plain --max-workers=1 :shared:compileKotlinJvm`。
- 已通过：`git diff --check`。
- 未执行单元测试。当前按要求优先编写业务代码，未新增测试。
- 未验证 Android 编译、Android 运行、iOS 编译或 macOS 编译。

## 本机环境限制

- 未配置 `ANDROID_HOME` 或 `ANDROID_SDK_ROOT`，且本机不存在 Android SDK Platform 35；无法本地编译或运行 `androidApp`。
- 当前仅安装 Xcode Command Line Tools，未安装完整 Xcode；无法构建 iOS 或 macOS Target。

## 建议继续顺序

1. 完成 Android 手动记账页并接通新增、编辑、删除交易，验证账户余额、首页和统计自动刷新。
2. 完成 Android 搜索、统计、更多和导入预览页面，接通已有共享 Facade/Workflow。
3. 实现账本、账户、分类、标签、规则及设置的管理页面，并补齐偏好持久化。
4. 实现备份同步、提醒、青子记账导出和项目 SVG 资源。
5. 安装 Android SDK 与完整 Xcode 后，补齐 Apple 工程与平台实现并做三端验证。
