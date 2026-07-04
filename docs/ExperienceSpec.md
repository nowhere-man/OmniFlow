# OmniFlow Experience Spec

## Product Principles

- Consumer app first, bookkeeping tool second.
- Desired feeling: refined, light, flowing, calm, clear, pleasant.
- Anti-direction: no finance-software feel, no admin dashboard feel, no spreadsheet feel, no default chart skin, no template SaaS dashboard.
- Visual quality and smooth interaction are acceptance criteria, not polish.

## Visual Direction

- Brand: compact wordmark, dark foreground mark, teal primary, green income, red expense, blue assets, amber risk.
- Typography: system sans, zero letter-spacing, large page titles only for primary screens.
- Space: desktop uses dense but breathable panels; mobile uses bottom navigation and single-column task flow.
- States: empty states are calm and actionable; loading uses local panel feedback; success uses restrained green; failures use structured error kind.
- Privacy: balance hiding, screenshot warning, and app-lock entry should be first-class UI states.

## Motion Spec

- Page transition: 220ms, translateY 8px to 0, opacity 0 to 1.
- Card/list hover: 140ms color/background transition.
- Chart transition: ECharts animated updates for trend, category, tag, and ranking changes.
- Import stages: select file, parse, rule match, dedup preview, confirm import.
- Reduced motion: global CSS honors `prefers-reduced-motion`.
- Long tasks: import, search, chart reload, sync restore, and rule reapply show local progress.

## Chart Narrative

- Trend answers change over time.
- Category answers structure.
- Tags answer life scenes.
- Ranking answers what mattered most.
- Comparison answers why this period feels different.
- Tooltips, legends, colors, currency, percentages, and date ranges share the same theme.
- Charts must handle empty data, very small data, large amounts, long Chinese labels, excluded transactions, and cross-month ranges.

## Core Journeys

- First launch: default ledger, default cash account, privacy-first data language, import entry, empty state.
- Daily open: dashboard shows account balance, income, expense, net cash flow, trend, and notable spending.
- Import: choose file, parse, apply rules, detect duplicates, edit preview, select rows, confirm.
- Chart exploration: dashboard to chart page, range switching, category/tag/ranking drilldown.
- Search: combine account, keyword, amount, date, tag, category, and see backend summary.
- Mobile quick add: bottom nav, single-column form, touch-sized controls.
- Privacy/sync: WebDAV key prompt, backup/restore feedback, local-vs-remote warning.

## Cross-Platform Acceptance

- Desktop: resize, keyboard reachable controls, file picker, drag/drop-ready import surface, chart interaction, system dark mode.
- Mobile: bottom navigation, one-handed controls, touch feedback, responsive tables, immersive charts.
- Browser: local service, LAN opt-in, Docker deployment, responsive upload/download, authentication boundary.
- Large data: test 10k, 50k, 100k transactions for list, search, chart, and import preview.
- Weak devices: no blank long task; show progress or skeleton.

## Visual Regression Matrix

- Screens: Dashboard, Charts, Import Preview, Transactions, Search, Settings, Mobile Home.
- Themes: light and dark.
- Viewports: phone, small tablet, large tablet, laptop, wide desktop.
- States: normal, empty, loading, success, error.
- Motion recordings: page transition, chart switch, import confirm, list update, number change.
