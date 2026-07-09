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

