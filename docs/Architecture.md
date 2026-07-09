# OmniFlow Architecture

本文档描述目标架构，不以当前实现为约束。需求与验收分别见 `docs/Requirements.md` 和 `docs/Goal.md`。

## 1. 架构结论

OmniFlow 定位为一个**纯客户端应用**，支持 macOS、iOS 和 Android。为了实现高效的代码复用，核心业务逻辑（客户端的“后端”）采用 **Kotlin Multiplatform (KMP)** 构建共享领域层 (Shared Domain Layer)。

本应用**不建设传统的集中式云后端**，远端仅作为用户自有云盘（如 WebDAV、iCloud）的加密同步存储，不承载任何业务逻辑。

选择该架构的原因：

- **极简与纯粹**：专注客户端体验，避免引入服务器运维、账号体系和中心化数据库，符合隐私至上的极客记账理念。
- **高效的跨端开发体验**：KMP 在 Android 端无缝互操作 (原生 Kotlin)，在 Apple 端通过 K/N 编译为 Framework。
- **成熟的移动端基建**：Kotlin 生态拥有成熟的跨端库，如 `SQLDelight` (本地 SQLite 存储)、`kotlinx.coroutines` (并发控制) 和 `kotlinx.serialization` (数据序列化)。

## 2. 总体结构

```text
               远端自有云盘 (仅加密同步，无业务逻辑)
                 (WebDAV / iCloud)
                              ▲
                              │ Sync Adapter
               ┌──────────────┴───────────────┐
               │  Shared Domain Layer (KMP)   │
               │                              │
               │ - Domain Models              │
               │ - Business Logic (Rules)     │
               │ - Sync & Encryption          │
               │ - Local Storage (SQLDelight) │
               └───────┬──────────────┬───────┘
                       │              │
      Apple Framework  │              │ Native Kotlin
      (配合 SKIE 优化)   │              │
                       ▼              ▼
         ┌─────────────┴──┐        ┌──┴─────────────┐
         │ Apple Platforms│        │    Android     │
         │ (iOS + macOS)  │        │                │
         │    SwiftUI     │        │Jetpack Compose │
         └────────────────┘        └────────────────┘
```

## 3. 职责划分

### Platform UI (平台表现层)

平台端专注于提供最佳的原生体验，不承载核心业务逻辑：

- **iOS/macOS**: 使用 SwiftUI，遵循 Apple Human Interface Guidelines。
- **Android**: 使用 Kotlin + Jetpack Compose + Material 3 Design。
- 负责：导航路由、页面布局、表单交互、动画效果、原生图表渲染 (Swift Charts / Compose Canvas)、系统集成 (文件选择、通知、生物识别)。
- 平台端只与 KMP 暴露的 Interface/ViewModel 层交互，不直接拼写 SQL，不处理业务规则。

### Shared Domain Layer (KMP 共享领域层)

这是客户端的核心（即客户端的后端），包含所有跨平台业务逻辑：

- **Domain Model**: 核心实体定义（账本、交易、分类、规则等）。
- **Data Layer (Repository)**: 统一管理数据读写，封装 SQLDelight 操作。
- **业务用例 (Use Cases)**: 账单解析与归一化、分类规则匹配、重复账单检测引擎。
- **离线与同步 (Sync)**: 冲突解决、加密 payload 生成，以及与各种云盘（WebDAV/iCloud）的同步交互。

## 4. Module 与架构分层

在 KMP Shared 内部，推荐遵循 Clean Architecture 的分层设计：

### 1. 表现层适配 (Presentation / ViewModels)
- 暴露平台渲染所需的纯数据状态 (StateFlow)，屏蔽底层业务复杂性。
- Apple 端配合 **SKIE** 插件，将 Kotlin 的协程和 Flow 完美转换为 Swift 的 `async/await` 和 `AsyncSequence`，提供最极致的 iOS 接入体验。

### 2. 领域层 (Domain)
- **Entities**: 纯粹的数据类实体。
- **Use Cases / Interactors**: 单一职责的业务逻辑。例如 `ImportTransactionsUseCase`, `ApplyLedgerRulesUseCase`。

### 3. 数据层 (Data)
- **Local Data Source**: 基于 `SQLDelight` 的 SQLite 数据库操作。
- **Sync Adapter**: 封装各类协议（WebDAV 等）的上传下载逻辑。

## 5. 数据存储与同步

### 纯本地 SQLite (Local-First)
- 采用 **SQLDelight** 驱动的跨端 SQLite 作为 Source of Truth。
- Schema 设计采用软删除（Tombstone，即保留 `is_deleted` 和 `updated_at` 字段）和 UUID，以支持无冲突的离线创建和多设备同步。
- Schema 的建表和升级 (Migration) 完全依靠 SQLDelight 的 `.sqm` 文件自动生成并校验跨端代码。

### 本地优先的云端同步
- 同步模型：所有写入操作均记录 Change Log，异步打包为加密 Snapshot 或增量 Pack，上传至用户指定的 WebDAV/iCloud。
- 因为远端没有真实的“服务器数据库”，解决多端冲突的逻辑（如 Last-Writer-Wins）完全由客户端 KMP 层在下载到最新同步包时进行处理。

## 6. 图表与分析

图表的计算与渲染进行合理分工：
- **KMP 层 (数据聚合)**：负责提供各维度的聚合数据模型和统计指标口径。直接利用 SQLDelight 在 SQLite 执行聚合查询，输出标准的图表数据集 (ChartData Model)。
- **Platform UI (可视化)**：拿到 KMP 输出的 ChartData Model 后，Android 端使用 Compose 渲染，iOS/macOS 使用原生 Swift Charts 渲染。

## 7. 账单解析的实现策略

**整体方案：expect/actual 分平台实现，不引入任何 C 库。**

KMP 在 commonMain 定义统一的解析接口，Android 和 Apple 各自用平台最自然的方式实现：

```kotlin
// commonMain
expect class CsvParser() {
    fun parseAlipay(bytes: ByteArray): List<RawTransaction>   // GBK
    fun parseUtf8Bom(bytes: ByteArray): List<RawTransaction>  // 京东/美团
}

expect class SpreadsheetParser() {
    fun parseXlsx(bytes: ByteArray): List<RawTransaction>     // 微信
    fun parseXls(bytes: ByteArray): List<RawTransaction>      // 建设银行 BIFF8
}
```

### CSV 解析（支付宝 GBK / 京东 / 美团）

CSV 本身的行列解析逻辑在 commonMain 共享，只有**字符集转换**需要 expect/actual：

| 平台 | GBK 解码方案 |
|------|-------------|
| Android | `String(bytes, Charset.forName("GBK"))` —— JVM 原生支持 |
| Apple (iOS/macOS) | `String(data:encoding:)` 配合 `CFStringConvertEncodingToNSStringEncoding(CFStringEncodings.GB_18030_2000)` —— **Apple Foundation 原生支持 GB18030（GBK 超集），已验证可用，无需任何第三方库** |

UTF-8 BOM（京东/美团）两端都原生支持，去掉 BOM 头后直接解析，无需 expect/actual。

### XLSX 解析（微信）

| 平台 | 方案 |
|------|------|
| Android | Apache POI（行业标准，XLSX 读取最稳定）|
| Apple (iOS/macOS) | **CoreXLSX**（纯 Swift 库，无 C 依赖，解析 XLSX 的 zip+xml 结构）|

### XLS 解析（建设银行 BIFF8）

已对真实 CCB.xls 文件分析（152 条数据行）：
- **全部字段均为纯文本字符串（ctype=1）**，无数字类型、无日期序列号、无公式、无合并单元格
- 金额以带千分位字符串存储（如 `"2,000.00"` / `"-19.33"`），去逗号后直接转 float
- 日期为 `"20260601"` 格式纯字符串，直接解析
- 结构固定：前 3 行元数据，第 4 行表头，第 5 行起数据

因此：

| 平台 | 方案 |
|------|------|
| Android | Apache POI（支持 BIFF8 XLS）|
| Apple (iOS/macOS) | **自实现极简 BIFF8 解析器**（纯 Swift，约 200 行）。因数据全为纯字符串，只需解析 SST（共享字符串表，record `0x00FC`）和 LabelSST record（`0x00FD`），不需要实现完整的 BIFF8 规范。无任何外部依赖。|

所有解析均在后台线程执行，不在主线程运行。

### 解析策略汇总

| 来源 | 格式 | KMP commonMain | Android | Apple |
|------|------|---------------|---------|-------|
| 支付宝 | CSV/GBK | 行列解析共享 | JVM Charset | Foundation GB18030 |
| 京东 | CSV/UTF-8 BOM | 完全共享 | 同左 | 同左 |
| 美团 | CSV/UTF-8 BOM | 完全共享 | 同左 | 同左 |
| 微信 | XLSX | 接口定义 | Apache POI | CoreXLSX |
| 建设银行 | XLS/BIFF8 | 接口定义 | Apache POI | 极简 BIFF8 解析器 |

## 8. Apple 端接入与 SKIE

Apple 端通过 Kotlin/Native 编译为 Framework 接入。**ViewModel 不放入 KMP 共享层**，保留在各平台侧（Android 使用 AAC ViewModel，Apple 使用 `@Observable` / `ObservableObject`）。KMP 只暴露 Repository 接口和 Use Case 的 suspend 函数 / Flow，由平台侧 ViewModel 调用。

**SKIE**（Touchlab / Skip Inc. 维护，开源）作为关键 Kotlin 编译器插件引入，负责把 KMP 的异步原语翻译为 Swift 习惯用法：

| Kotlin | Swift (经 SKIE 转换) |
|--------|--------------------|
| `suspend fun` | `async func` |
| `Flow<T>` | `AsyncSequence<T>` |
| `StateFlow<T>` | 可被 `for await` 消费的序列 |
| `Result<T>` / 抛异常 | `throws` |

没有 SKIE，Swift 端调用 KMP suspend 函数需手写 Combine/callback 桥接样板，维护成本高。因 ViewModel 在平台侧，SKIE 的作用范围仅限于「让 Swift ViewModel 能干净地 await KMP Use Case」，复杂度远低于「把整个 ViewModel 状态管理都暴露给 Swift」。

已知固有成本（非 SKIE 问题，是 K/N 本身）：
- 冷编译慢（分钟级 full build），建议业务逻辑优先在 Android 侧联调验证
- Framework 体积含 K/N 运行时（数 MB）
- Xcode 调试 KMP 层体验受限；对策是 KMP 层 Use Case 和解析器写完整单元测试（JVM 上运行，调试正常）

## 9. 同步密钥与青子记账互通

这两点容易被"数据存储与同步"和"账单解析"含糊吞掉，单列明确。

### 同步密钥管理
远端 payload 加密，密钥不由远端持有。方案：用户口令经 Argon2 派生为主密钥（或由平台 Keychain/Keystore 生成并保管随机密钥），KMP 层负责加解密，密钥材料只存本地安全存储。具体派生参数与存储位置待定（见 Goal 澄清队列），架构层先占位。

### 青子记账互通（独立于同步）
青子记账互通是**导入/导出转换模块**（JSON ↔ 领域模型），不是同步。它与 WebDAV 同步引擎是两件事：同步解决多设备一致，青子互通解决与外部 App 的数据迁移与长期双写。单列为 interop 模块，避免与 sync 的冲突解决逻辑耦合。映射规则（见 Goal §12）在 KMP 层实现，无法映射的字段丢弃并记警告，不阻断。

