# Technology Spikes

## React Web + Tauri + Local/Container Service

Result: selected for the current product baseline.

- Visual quality: proven with the rebuilt React shell, dashboard, charts, import preview, search, and settings.
- Animation: CSS motion tokens plus ECharts transitions are sufficient for the current desktop/browser product.
- Charts: ECharts ecosystem is strong for trend, category, tag, comparison, and ranking.
- Desktop: Tauri packages macOS and Windows while keeping Rust core local.
- Browser: same React UI can run against future Rust HTTP APIs.
- Docker: Rust service can expose the same core through HTTP with SQLite mounted as a volume.
- Risk: mobile should not be treated as a simple scaled desktop web view.

## Flutter Multi-Platform

Result: not selected for this codebase baseline.

- Strength: mobile and desktop native feel can be excellent.
- Concern: browser deployment, Docker/server access, complex chart ecosystem, and Rust integration add cost.
- Integration options: FFI, platform channel, or local HTTP API.
- Current decision: revisit only after React Web validates the product language and API contracts.

## React Web + React Native Mobile

Result: viable future split.

- Strength: web/browser path stays clear, mobile can get native navigation and file flows.
- Sharing: domain vocabulary, API contracts, design tokens, and chart semantics.
- Risk: two UI codebases need strict design governance.

## Decision

Continue with React Web + Tauri + Rust core. Keep the Rust core free of Tauri-only assumptions so a local HTTP service and Docker/browser deployment can reuse the same import, rule, search, stats, sync, and periodic billing logic.
