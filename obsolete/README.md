<div align="center">
  <h1>OmniFlow</h1>
  <p><strong>本地优先的现代化全能记账本 —— 将杂乱无章的散落账单，汇聚成清晰可见的财务流。</strong></p>

  <p>
    <img src="https://img.shields.io/badge/Tauri-2.0-blue?style=flat-square&logo=tauri" alt="Tauri" />
    <img src="https://img.shields.io/badge/React-19-61DAFB?style=flat-square&logo=react" alt="React" />
    <img src="https://img.shields.io/badge/Rust-🦀-orange?style=flat-square&logo=rust" alt="Rust" />
    <img src="https://img.shields.io/badge/Fluent_UI-0F6CBD?style=flat-square&logo=microsoft" alt="Fluent UI" />
    <img src="https://img.shields.io/badge/SQLite-003B57?style=flat-square&logo=sqlite" alt="SQLite" />
  </p>
</div>

---

## 💡 为什么开发 OmniFlow？

在移动支付高度普及的今天，我们几乎所有的交易行为都发生在指尖。但随之而来的是**极度碎片化的财务数据**：
- **手动记账太累**：每花一笔钱都要打开 App 手动录入，难以坚持，很容易漏记、错记。
- **数据散落各处**：资产被割裂在支付宝、微信支付、建设银行、京东、美团等不同的孤岛中，很难知道自己到底“花了多少，还剩多少”。
- **隐私焦虑**：市面上的记账软件往往要求注册登录并将财务数据上传至云端，敏感的个人资产信息面临隐私泄露风险。
- **UI 老旧繁杂**：多数传统记账工具操作繁琐，视觉设计停留在几年前，且充满了推销和社区广告。

**OmniFlow** 旨在打破这些壁垒：它是一个完全**本地化运行**的桌面应用，通过一键导入各个平台的原始对账单，在本地自动进行去重、解析、规则清洗，并以极具现代感和交互美感的图表呈现。你的资产，仅在你的设备中流淌。

---

## ✨ 核心特性

- 🔒 **本地优先，隐私极致**：不强制依赖任何云端服务，所有底层数据库 (SQLite) 和数据分析全在本地处理。支持高级 AES-256-GCM 本地加密与自建 WebDAV 加密同步。
- 📥 **多源账单一键导入**：内置了强大的规则引擎。目前支持直导 **微信支付 (XLSX)**、**支付宝 (CSV)**、**建设银行 (XLS)**、**京东/美团** 等多平台的原生账单。
- 🧹 **智能去重与自动清洗**：无需担心多次导入重复账单！独创的基于单号的“精准去重”与基于容差的“模糊去重”引擎，让账单干干净净。
- 🎨 **现代化美学界面**：使用 Fluent UI、原生 CSS 与 Lucide Icons 打造一体化工作台，配合 ECharts 提供高级可视化体验，告别枯燥的数字列表。
- 🔍 **全能搜索与聚合视图**：毫秒级响应的全局检索，通过金额、商家、备注、标签等多维度，一秒找回曾经的交易记录。

---

## 🚀 本地运行与开发指南

### 环境依赖
确保你已经安装了以下开发环境：
- [Node.js](https://nodejs.org/) (推荐 v18+)
- [pnpm](https://pnpm.io/)
- [Rust](https://www.rust-lang.org/tools/install) (cargo)

### 快速启动

1. **克隆仓库**
   ```bash
   git clone git@github.com:nowhere-man/OmniFlow.git
   cd OmniFlow
   ```

2. **安装前端依赖**
   ```bash
   pnpm install
   ```

3. **启动开发服务器并运行 Tauri 客户端**
   ```bash
   npm run tauri dev
   ```

---

## 🤝 参与贡献

欢迎大家参与到 OmniFlow 的开发中来！无论是一个小小的 Bug 修复、完善文档，还是新增平台账单解析器，我们都非常欢迎。

1. Fork 本仓库
2. 创建你的特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交你的更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 发起一个 Pull Request

---

## 📄 许可证

本项目基于 [MIT License](./LICENSE) 开源。你可以自由地学习、修改和分发，但请保留原作者声明。
