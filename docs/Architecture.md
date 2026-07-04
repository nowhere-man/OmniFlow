# OmniFlow Architecture

## North Star

OmniFlow is a local-first consumer finance app. The core product promise is: a user can open the app, understand this month's money state within 10 seconds, and continue naturally into charts, import review, search, and details.

## Chosen Route

The current implementation uses React Web as the primary UI, Tauri for macOS and Windows desktop packaging, and Rust as the local core. This route is kept because it gives the shortest path to desktop and browser reuse while keeping SQLite, import parsers, rules, deduplication, statistics, WebDAV encryption, and future HTTP APIs in one Rust domain layer.

Flutter and React Native remain mobile candidates. They should share API contracts, data vocabulary, design tokens, and chart semantics with the React Web product, but they should not be assumed to reuse desktop web layouts.

## Module Boundaries

- `src-tauri/src/core`: business logic that is independent of Tauri UI concerns.
- `src-tauri/src/core/import_pipeline.rs`: parser selection, DB-backed rules, dedup preview, confirm import, pending-confirmation matching.
- `src-tauri/src/core/rule_engine.rs`: rule matching and action mutation on `RawTransaction`.
- `src-tauri/src/core/dedup_engine.rs`: absolute and fuzzy duplicate detection.
- `src-tauri/src/core/stats_engine.rs`: trend, comparison, tag, ranking, dashboard and asset summaries.
- `src-tauri/src/core/periodic.rs`: due periodic bill detection and pending-confirmation matching.
- `src-tauri/src/core/sync_engine.rs`: encrypted WebDAV upload and restore.
- `src-tauri/src/ports`: storage and crypto interfaces.
- `src-tauri/src/adapters`: SQLite and AES implementations.
- `src-tauri/src/commands`: thin Tauri command adapters.
- `src/tauri-adapter`: typed frontend API wrappers.
- `src/features`: product views. Views call typed adapters, not raw SQL or Rust internals.

## Browser Delivery

Browser mode should reuse the same React Web app. The Rust core should be callable through either Tauri commands in desktop mode or an HTTP API in browser/server mode.

Local private mode binds only to `127.0.0.1` by default and uses the local SQLite path under the app data directory. Remote deployment mode runs a Rust service in a container, stores SQLite and encrypted backups in mounted volumes, and must require an initialization password or access token before data is reachable.

## Security Boundary

- WebDAV payloads are encrypted locally before upload.
- WebDAV passwords, encryption keys, app-lock secrets, and browser tokens must not be stored as plaintext.
- LAN/browser exposure is opt-in and must show the difference between local private mode and remote deployment mode.
- Remote browser mode must define CORS, CSRF, session expiry, and second confirmation for destructive operations.

## Data And Sync Paths

- SQLite is the source of truth.
- Import files become `RawTransaction` values, then preview rows, then confirmed `Transaction` rows.
- Rules and deduplication run before preview; absolute duplicates are locked out of import.
- Periodic bills create pending confirmations. Matching imported real transactions confirms them.
- WebDAV stores encrypted database backups at `ominiflow.db.enc`.

## Account Asset Semantics

`get_assets_overview` and `get_dashboard_summary.net_assets` use current account balances, not transaction-derived cash flow. Credit accounts use their stored balance and credit metadata; UI should label this as account balance overview, not full net worth unless liabilities are modeled completely.
