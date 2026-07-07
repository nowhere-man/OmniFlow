# OmniFlow Architecture

本文档描述目标架构，不以当前实现为约束。需求与验收分别见 `docs/Requirements.md` 和 `docs/Goal.md`。

## 1. 架构结论

OmniFlow 的后端应实现为嵌入在 App 内的 **Rust shared core**。iOS、Android、macOS 都调用同一套 Rust core；远端第一阶段只作为加密同步存储，不承载业务逻辑。

不优先建设传统云后端。首版不需要 HTTP 业务后端、Postgres、MySQL、服务器端查询、服务器端统计或服务器端账单解析。

选择该架构的原因：

- 记账数据高度隐私，本地优先和端到端加密比中心化明文服务更合适。
- 核心复杂度集中在导入解析、规则、去重、统计、同步和加密，这些能力应在一处维护。
- 用户接受 Apple 与 Android 两套 UI，因此跨端复用的重点应是业务核心，而不是 UI。
- 移动端需要离线可用；无网络时记账、导入、搜索和统计分析不能依赖服务器。
- 远端存储只做加密同步，可以降低运维、安全和账号体系成本。

## 2. 总体结构

```text
iOS SwiftUI          Android Kotlin/Compose          macOS SwiftUI
     │                         │                          │
     └────────────── UniFFI / FFI Interface ──────────────┘
                               │
                         Rust Shared Core
                               │
 ┌─────────────────────────────┼─────────────────────────────┐
 │ Domain Model                │ Import Pipeline              │
 │ Ledger / Account / Txn      │ Parsers / Preview / Commit   │
 ├─────────────────────────────┼─────────────────────────────┤
 │ Rules + Dedupe              │ Analytics                    │
 │ Ledger rules / Memory       │ Chart data / Search / SQL    │
 ├─────────────────────────────┴─────────────────────────────┤
 │ SQLite Storage / Migration / Reminder / Sync Metadata      │
 ├─────────────────────────────────────────────────────────────┤
 │ Sync Module / Encryption / SyncAdapter Interface            │
 └─────────────────────────────────────────────────────────────┘
                               │
                      WebDAV encrypted object store
                               │
                Future Adapter: iCloud / S3 / self-hosted
```

## 3. 职责划分

### Platform UI

平台端负责体验，不负责业务核心：

- iOS/macOS 使用 SwiftUI；Android 使用 Kotlin + Jetpack Compose + Material 3。
- 负责导航、页面布局、表单交互、动画、图表渲染、系统文件选择、通知、生物识别和平台安全能力。
- 负责调用 FFI Interface 并把 Rust core 的结果映射为平台 UI 状态。
- 不直接访问 SQLite。
- 不重复实现账单解析、规则、去重、统计、同步或加密逻辑。

### Rust Shared Core

Rust core 是正式后端，负责所有跨平台一致的业务能力：

- 数据模型与不变量：账本、全局账户、账本内分类、账本内标签、交易、导入预览、账本内规则、历史分类记忆、提醒与周期事项、同步状态。
- SQLite 存储、schema migration、事务一致性、软删除、索引和查询性能。
- 支付宝、微信、京东、美团、建设银行、青子记账解析。
- 导入流水线：选择目标账本 -> 解析 -> 账本内规则匹配 -> 历史分类记忆兜底 -> 去重检测 -> 预览编辑 -> 确认入账。
- 规则引擎、历史分类记忆、去重引擎、高级搜索、统计聚合和图表数据模型。
- 提醒与周期事项：还款提醒、订阅提醒、下次触发时间、本地通知所需数据。
- 本地优先同步、冲突处理、同步元数据、远端加密 payload。
- 类型化错误、进度事件和可恢复状态。

### Remote Storage

远端第一阶段只是加密同步存储：

- 首选 WebDAV。
- WebDAV 服务端只能看到加密 payload 和必要的对象元数据。
- 远端不执行查询、统计、导入解析、规则匹配或去重。
- 未来新增 iCloud、S3、自建存储时，应作为新的 SyncAdapter，不改变业务 Module 的 Interface。

## 4. Module 与 Interface

Rust core 应设计成少量深 Module：Interface 小，Implementation 深。平台端和测试都通过同一组 Interface 使用能力。

### 外部 Seam：FFI Interface

FFI Interface 应面向用例，不面向数据库表。平台端应调用「完成一个业务动作」的 Interface，而不是组合大量表级 CRUD。

建议的外部 Interface 类型：

- App lifecycle：打开数据库、执行 migration、读取应用配置。
- Ledger and account：管理账本、账户、分类、标签等基础数据。
- Transaction use cases：新增、编辑、删除、批量更新交易。
- Import use cases：创建导入会话、生成预览、修改预览项、确认入账。
- Rule use cases：在账本内创建规则、测试规则、按优先级应用到导入预览。
- Reminder use cases：管理还款提醒、订阅提醒、暂停/恢复提醒、计算下次触发时间。
- Search use cases：按组合条件查询交易并返回聚合结果。
- Analytics use cases：按时间范围和维度生成图表数据模型。
- Sync use cases：配置 WebDAV、执行同步、返回同步状态和进度事件。

FFI Interface 的基本要求：

- 返回类型化 `Result<T, AppError>` 或等价错误模型。
- 错误包含稳定错误码、用户可读消息、可恢复性标记。
- 长任务必须支持进度事件或可轮询状态，例如导入、解析、同步。
- 大批量数据应分页或流式返回，避免一次跨 FFI 传输过多对象。
- Interface 返回平台渲染需要的数据模型，不返回内部数据库行结构。

### 内部 Module

Rust core 内部可以拆成以下 Module：

- Domain Module：定义实体、值对象、状态流转和业务不变量。
- Storage Module：封装 SQLite、migration、事务、索引和查询。
- Import Module：封装文件识别、编码处理、平台解析器和标准化输出。
- Rule Module：封装账本内规则匹配、优先级和导入预览动作；规则不改派账本，不重跑历史交易。
- Category Memory Module：封装同账本历史交易/历史导入选择形成的分类记忆。
- Dedupe Module：封装绝对去重、模糊去重和疑似重复候选。
- Analytics Module：封装 SQL 聚合、指标口径和图表数据模型。
- Reminder Module：封装还款提醒、订阅提醒、重复规则和下次触发时间。
- Sync Module：封装 change log、冲突处理、同步状态、加密和 Adapter 调用。
- Security Module：封装加密、密钥派生、密钥轮换和平台密钥材料接入。

这些 Module 的内部实现可以继续拆分，但不应把内部结构泄漏给平台端。

## 5. 数据存储

每台设备使用本地 SQLite 作为主数据存储。

SQLite 由 Rust core 独占访问：

- 所有 schema migration 在 Rust core 中管理。
- 所有写入必须通过 Rust core 的用例 Interface。
- 复杂查询和统计聚合在 Rust core 中执行。
- 平台端不拼 SQL、不读取表、不自行维护统计口径。

核心表设计应支持：

- 软删除：交易、账本、账户、分类、标签、规则等重要实体保留 `deleted_at`。
- 多账本：交易必须归属账本，查询、搜索、统计默认以账本为范围。
- 账本内资源：分类、标签、规则、历史分类记忆都归属于账本。
- 全局资源：账户不归属于账本，交易引用全局账户。
- 导入预览：导入会话期间可保存预览状态；确认入账后不保留导入批次记录、原始解析明细快照或上传文件。
- 去重：保留 `external_source`、`external_id`、时间、金额、商户等去重依据。
- 账户余额：账户当前余额由用户手动维护，不要求从交易流水推导或精确平账。
- 提醒事项：提醒与周期事项独立存储，不引用账本、账户或交易。
- 同步元数据：记录 device id、change id、logical clock、sync status。

## 6. 同步与加密

同步采用本地优先模型。所有用户操作先写入本地 SQLite，再由 Sync Module 异步同步。

推荐同步模型：

- 每个设备有稳定 `device_id`。
- 每次业务写入在同一 SQLite transaction 中写入业务数据和 change log。
- change log 包含 entity type、entity id、operation、logical clock、timestamp、device id。
- Sync Module 上传本地未同步 change pack，下载远端未应用 change pack。
- 应用远端 change pack 必须幂等，重复下载不会产生重复交易。
- 冲突处理规则必须在 Rust core 内统一维护。
- 提醒与周期事项作为独立数据参与同步。

首版冲突策略建议：

- 不同实体的新建操作通过全局稳定 ID 避免冲突。
- 删除采用 tombstone，避免远端旧数据复活。
- 用户编辑同一字段冲突时采用可解释的 last-writer-wins，并保留冲突日志。
- 导入交易继续使用 `external_source + external_id` 绝对去重，缺少外部 ID 时使用模糊去重候选。
- 导入批次和原始文件不参与长期同步，因为确认入账后不保留。

加密要求：

- 上传到 WebDAV 的 change pack 或 snapshot 必须加密。
- WebDAV 服务端不能读取账本、交易、分类、标签、规则或统计内容。
- 加密密钥材料由平台安全能力保存，例如 Keychain 或 Android Keystore。
- Rust core 负责加密算法、payload 格式、版本号和解密失败错误。
- 本地数据库是否使用 SQLCipher 可作为后续安全增强，不作为首版必选项。

## 7. 图表与分析

Rust core 负责分析口径，平台端负责原生图表渲染。

Rust core 输出稳定的图表数据模型：

- 时间范围：天、周、月、年、自定义。
- 度量：收入、支出、净额、分类占比、账户当前余额概览、标签汇总、同环比。
- 展示辅助信息：单位、粒度、空状态、极值、可下钻维度。
- 过滤条件：账本、分类、标签、账户、商户、金额范围、时间范围。

平台端不得从交易明细重新计算统计口径。iOS/macOS 可使用 Swift Charts 或自定义绘制；Android 可使用 Compose 图表库或 Canvas。

## 8. 不采用的方案

当前不采用以下方案作为主线：

- 传统云后端优先：会过早引入账号、鉴权、服务器运维、明文数据安全和在线依赖。
- 平台端各自实现业务逻辑：会导致导入、规则、去重和统计口径在三端漂移。
- Flutter 或 React Native 作为主 UI：用户接受两套 UI，当前更重视平台体验和原生图表质量。
- WebView/ECharts 作为正式图表方案：与无 Web 正式目标和平台原生体验目标不一致。
- 远端数据库作为主数据源：会削弱离线能力并增加隐私与同步复杂度。
- 导入规则改派账本：会破坏导入前选择目标账本的上下文，并让分类、标签、规则解析变复杂。
- 周期事项自动记账：提醒模块首版保持独立，不自动生成交易或参与导入合并。

## 9. 后续需要细化

- FFI Interface 的具体类型定义、错误码和进度事件模型。
- SQLite schema、migration 策略和索引设计。
- 规则字段模板、动作模板、优先级 UI 和测试预览方式。
- 历史分类记忆的匹配字段、相似度策略和清理方式。
- change log 格式、远端对象布局和 WebDAV 目录结构。
- 加密 payload 格式、密钥派生、密钥恢复和多设备授权流程。
- 冲突处理的用户可见策略。
- 大文件导入、长耗时解析和同步的取消/恢复机制。
