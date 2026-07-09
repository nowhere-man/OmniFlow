# OmniFlow Project Structure

本文档定义代码目录结构、分层规则和文件命名规范。
架构决策见 `docs/Architecture.md`，产品需求见 `docs/Requirements.md`。

---

## 顶层结构

```
OmniFlow/
├── .github/
│   └── workflows/
│       └── release.yml          ← CI/CD 构建与发布
├── docs/                        ← 所有文档
├── examples/                    ← 各平台账单样例（解析器测试 fixture）
│   ├── 支付宝.csv
│   ├── 微信.xlsx
│   ├── 京东.csv
│   ├── 美团.csv
│   ├── 建设银行.xls
│   └── 青子记账.json
├── shared/                      ← KMP 共享模块（所有平台的业务核心）
├── androidApp/                  ← Android 独立工程
├── appleApp/                    ← Apple 统一工程（iOS + macOS 同一个 Xcode 工程）
├── build.gradle.kts             ← 根 Gradle 配置
├── settings.gradle.kts          ← 模块声明
└── gradle.properties
```

---

## shared/ — KMP 共享模块

所有平台共用的业务逻辑、数据模型、数据库和解析器。
ViewModel 不在此处，保留在各平台侧。

### 包名根路径
`com.omniflow.shared`

### 目录结构

```
shared/
├── src/
│   ├── commonMain/
│   │   └── kotlin/com/omniflow/shared/
│   │       ├── domain/
│   │       │   ├── model/              ← 纯数据实体，无任何框架依赖
│   │       │   ├── repository/         ← Repository 抽象接口
│   │       │   └── usecase/            ← 业务用例（按功能模块分子目录）
│   │       ├── data/
│   │       │   ├── local/              ← SQLDelight 数据库访问
│   │       │   ├── repository/         ← Repository 接口实现
│   │       │   └── sync/               ← Sync Adapter 及 WebDAV 实现
│   │       ├── parser/
│   │       │   ├── csv/                ← CSV 解析（支付宝/京东/美团，完全共享）
│   │       │   └── spreadsheet/        ← XLSX/XLS 接口（expect/actual 分平台实现）
│   │       └── di/
│   │           └── SharedModule.kt     ← Koin DI 模块声明
│   │
│   ├── androidMain/
│   │   └── kotlin/com/omniflow/shared/
│   │       ├── data/local/
│   │       │   └── DatabaseFactory.kt  ← Android SQLite 驱动
│   │       └── parser/
│   │           ├── csv/
│   │           │   └── CsvDecoder.kt   ← GBK 解码（JVM Charset）
│   │           └── spreadsheet/
│   │               └── SpreadsheetParser.kt  ← Apache POI 实现
│   │
│   └── appleMain/
│       └── kotlin/com/omniflow/shared/
│           ├── data/local/
│           │   └── DatabaseFactory.kt  ← iOS/macOS SQLite 驱动
│           └── parser/
│               ├── csv/
│               │   └── CsvDecoder.kt   ← GBK 解码（Foundation GB18030）
│               └── spreadsheet/
│                   └── SpreadsheetParser.kt  ← CoreXLSX（XLSX）+ 极简 BIFF8（XLS）
│
└── sqldelight/
    └── com/omniflow/shared/db/
        ├── Ledger.sq
        ├── Account.sq
        ├── Transaction.sq
        ├── Category.sq
        ├── Tag.sq
        ├── Rule.sq
        ├── CategoryMemory.sq       ← 历史分类记忆
        ├── ImportSession.sq        ← 导入会话（入账后清除）
        ├── Reminder.sq
        └── SyncMeta.sq             ← 同步元数据（device_id, change_id 等）
```

---

### domain/model/ — 核心实体

| 文件 | 说明 |
|------|------|
| `Ledger.kt` | 账本（id, name, is_deleted, created_at） |
| `Account.kt` | 账户（全局，id, name, type, balance） |
| `Transaction.kt` | 交易（id, ledger_id, account_id, category_id, amount, type, date, merchant, note, tags, is_excluded, external_source, external_id, deleted_at） |
| `Category.kt` | 分类（id, ledger_id, parent_id, name, type=income/expense） |
| `Tag.kt` | 标签（id, ledger_id, name） |
| `Rule.kt` | 规则（id, ledger_id, conditions, actions, priority） |
| `CategoryMemory.kt` | 历史分类记忆（key 字段 → category_id） |
| `RawTransaction.kt` | 解析器输出的标准中间格式（进入规则/去重引擎前） |
| `ImportPreviewItem.kt` | 导入预览页的单条明细（含去重状态、规则命中状态） |
| `Reminder.kt` | 提醒（type=repayment/subscription, repeat_rule） |
| `SyncState.kt` | 同步状态枚举（idle, syncing, success, error） |
| `AppError.kt` | 统一错误模型（code, message, recoverable） |
| `ChartData.kt` | 图表数据模型（KMP 输出，平台端直接渲染） |

---

### domain/usecase/ — 业务用例

```
usecase/
├── ledger/
│   ├── CreateLedgerUseCase.kt
│   ├── GetLedgersUseCase.kt
│   └── DeleteLedgerUseCase.kt
├── account/
│   ├── CreateAccountUseCase.kt
│   └── GetAccountsUseCase.kt
├── transaction/
│   ├── AddTransactionUseCase.kt
│   ├── EditTransactionUseCase.kt
│   ├── DeleteTransactionUseCase.kt
│   └── GetTransactionsUseCase.kt
├── import/
│   ├── CreateImportSessionUseCase.kt   ← 选择文件 → 创建会话
│   ├── ParseBillFileUseCase.kt         ← 解析 → RawTransaction 列表
│   ├── ApplyRulesUseCase.kt            ← 规则匹配
│   ├── ApplyDedupeUseCase.kt           ← 去重检测
│   ├── GetImportPreviewUseCase.kt      ← 生成预览列表
│   ├── EditPreviewItemUseCase.kt       ← 用户在预览页修改单条
│   └── CommitImportUseCase.kt          ← 确认入账
├── rule/
│   ├── CreateRuleUseCase.kt
│   ├── UpdateRuleUseCase.kt
│   ├── DeleteRuleUseCase.kt
│   └── ReorderRulesUseCase.kt
├── analytics/
│   ├── GetTrendChartUseCase.kt         ← 收支趋势
│   ├── GetCategoryChartUseCase.kt      ← 分类占比
│   ├── GetAccountSummaryUseCase.kt     ← 账户资产概览
│   ├── GetPeriodCompareUseCase.kt      ← 同环比
│   └── GetTagSummaryUseCase.kt         ← 标签汇总
├── search/
│   └── SearchTransactionsUseCase.kt    ← 多维度组合搜索 + 聚合
├── reminder/
│   ├── CreateReminderUseCase.kt
│   ├── ToggleReminderUseCase.kt        ← 暂停/恢复
│   └── GetUpcomingRemindersUseCase.kt
├── sync/
│   ├── ConfigureSyncUseCase.kt         ← 配置 WebDAV 地址和凭据
│   ├── TriggerSyncUseCase.kt
│   └── GetSyncStatusUseCase.kt
└── interop/
    ├── ImportQingziUseCase.kt          ← 青子记账 JSON 导入
    └── ExportQingziUseCase.kt          ← 导出为青子记账 JSON
```

---

### data/sync/ — 同步适配器

```
sync/
├── SyncAdapter.kt              ← 接口定义（upload / download / list）
├── ChangeLog.kt                ← 变更日志数据模型
├── SyncEngine.kt               ← 核心同步引擎（冲突解决、加密）
└── webdav/
    └── WebDavSyncAdapter.kt    ← WebDAV 实现（Ktor HTTP Client）
```

未来新增 iCloud / S3 只需新增对应的 `Adapter` 实现，`SyncEngine` 不改变。

---

## androidApp/ — Android 工程

### 包名根路径
`com.omniflow.android`

```
androidApp/
├── src/main/
│   ├── kotlin/com/omniflow/android/
│   │   ├── OmniFlowApp.kt              ← Application 类，初始化 Koin
│   │   ├── MainActivity.kt
│   │   └── ui/
│   │       ├── navigation/
│   │       │   ├── AppNavHost.kt       ← NavHost 根节点
│   │       │   └── Routes.kt           ← 路由常量
│   │       ├── component/              ← 全局复用 Compose 组件
│   │       │   ├── AmountText.kt
│   │       │   ├── CategoryChip.kt
│   │       │   └── EmptyState.kt
│   │       ├── ledger/
│   │       │   ├── LedgerListScreen.kt
│   │       │   └── LedgerViewModel.kt
│   │       ├── transaction/
│   │       │   ├── TransactionListScreen.kt
│   │       │   ├── TransactionDetailScreen.kt
│   │       │   ├── AddTransactionScreen.kt
│   │       │   └── TransactionViewModel.kt
│   │       ├── import/
│   │       │   ├── ImportScreen.kt
│   │       │   ├── ImportPreviewScreen.kt
│   │       │   └── ImportViewModel.kt
│   │       ├── analytics/
│   │       │   ├── AnalyticsScreen.kt
│   │       │   └── AnalyticsViewModel.kt
│   │       ├── search/
│   │       │   ├── SearchScreen.kt
│   │       │   └── SearchViewModel.kt
│   │       ├── reminder/
│   │       │   ├── ReminderListScreen.kt
│   │       │   └── ReminderViewModel.kt
│   │       └── settings/
│   │           ├── SettingsScreen.kt
│   │           └── SyncSettingsScreen.kt
│   └── res/
│       └── values/
│           └── strings.xml
├── build.gradle.kts
└── proguard-rules.pro
```

---

## appleApp/ — Apple 工程（iOS + macOS）

一个 Xcode 工程，包含两个 Target：`OmniFlow-iOS` 和 `OmniFlow-macOS`。
大部分 SwiftUI 代码放在 `Shared/`，被两个 Target 共同引用。

```
appleApp/
├── OmniFlow.xcodeproj/
│
├── Shared/                         ← 被 iOS 和 macOS 两个 Target 共用
│   ├── App/
│   │   └── OmniFlowApp.swift       ← @main 入口（条件编译区分平台）
│   ├── DI/
│   │   └── KoinStarter.swift       ← 初始化 KMP 共享层的 Koin
│   │
│   ├── ViewModel/                  ← 平台原生 ViewModel（@Observable）
│   │   ├── LedgerViewModel.swift
│   │   ├── TransactionViewModel.swift
│   │   ├── ImportViewModel.swift
│   │   ├── AnalyticsViewModel.swift
│   │   ├── SearchViewModel.swift
│   │   ├── ReminderViewModel.swift
│   │   └── SyncViewModel.swift
│   │
│   ├── UI/
│   │   ├── Component/              ← 跨平台复用的 SwiftUI 组件
│   │   │   ├── AmountView.swift
│   │   │   ├── CategoryBadge.swift
│   │   │   └── EmptyStateView.swift
│   │   ├── Ledger/
│   │   │   └── LedgerListView.swift
│   │   ├── Transaction/
│   │   │   ├── TransactionListView.swift
│   │   │   ├── TransactionDetailView.swift
│   │   │   └── AddTransactionView.swift
│   │   ├── Import/
│   │   │   ├── ImportView.swift
│   │   │   └── ImportPreviewView.swift
│   │   ├── Analytics/
│   │   │   └── AnalyticsView.swift
│   │   ├── Search/
│   │   │   └── SearchView.swift
│   │   ├── Reminder/
│   │   │   └── ReminderListView.swift
│   │   └── Settings/
│   │       ├── SettingsView.swift
│   │       └── SyncSettingsView.swift
│   │
│   └── Extension/
│       └── KMPExtensions.swift     ← KMP 类型到 Swift 的辅助扩展
│
├── iOS/                            ← iOS 专属（仅 OmniFlow-iOS Target）
│   ├── Navigation/
│   │   └── TabNavigation.swift     ← TabView 底部导航
│   ├── Assets.xcassets
│   └── Info.plist
│
└── macOS/                          ← macOS 专属（仅 OmniFlow-macOS Target）
    ├── Navigation/
    │   └── SidebarNavigation.swift ← NavigationSplitView 侧边栏
    ├── MenuCommands.swift           ← 菜单栏命令（⌘N 新建、⌘, 设置等）
    ├── Assets.xcassets
    └── Info.plist
```

---

## 分层依赖规则

```
Platform UI (Android / Apple)
    │
    │  只能调用 Use Case 和 Repository 接口
    ▼
domain/usecase/
    │
    │  只能依赖 domain/model 和 domain/repository（接口）
    ▼
domain/model + domain/repository（接口）
    ▲
    │  实现接口，不反向依赖 domain/usecase
data/repository/  +  data/local/  +  data/sync/
```

**禁止的依赖方向：**
- `domain/` 不得 import `data/` 或任何平台代码
- `data/` 不得 import `domain/usecase/`
- Platform UI 不得直接 import `data/` 层（必须通过 Use Case）
- Platform UI 不得直接拼写 SQL 或调用 SQLDelight Query

---

## 文件命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| Kotlin Entity | PascalCase + 无后缀 | `Transaction.kt` |
| Kotlin Use Case | PascalCase + `UseCase` | `AddTransactionUseCase.kt` |
| Kotlin Repository 接口 | PascalCase + `Repository` | `TransactionRepository.kt` |
| Kotlin Repository 实现 | PascalCase + `RepositoryImpl` | `TransactionRepositoryImpl.kt` |
| SQLDelight schema | PascalCase + `.sq` | `Transaction.sq` |
| Swift View | PascalCase + `View` | `TransactionListView.swift` |
| Swift ViewModel | PascalCase + `ViewModel` | `TransactionViewModel.swift` |
| Android Screen | PascalCase + `Screen` | `TransactionListScreen.kt` |
| Android ViewModel | PascalCase + `ViewModel` | `TransactionViewModel.kt` |

---

## 关键约定

1. **UUID 主键**：所有实体使用 UUID v4 字符串作为主键，禁止使用数据库自增 ID。
2. **软删除**：Ledger / Account / Transaction / Category / Rule 等实体保留 `deleted_at` 字段（nullable），查询默认过滤已删除。
3. **账本范围**：所有业务查询（交易列表、统计、搜索）默认以 `ledger_id` 为边界，不允许跨账本查询。
4. **后台线程**：解析器和 Sync 操作在 `Dispatchers.IO` 或 K/N 等价线程上执行，Use Case 通过协程调用，不阻塞主线程。
5. **错误模型**：所有 Use Case 返回 `Result<T>` 或 `Flow<Result<T>>`，错误通过 `AppError` 传递，不抛未捕获异常到 UI 层。
