# OmniFlow Agent Rules

These rules apply to the entire repository.

## 1. Cross-platform changes

- When changing UI or interaction on Android, iOS, or macOS, first decide whether the behavior is product-wide or platform-specific.
- Product-wide behavior must be updated on every affected client in the same change.
- Do not copy a mobile UI to desktop merely for visual consistency. Platform-specific navigation, gestures, windowing, menus, toolbars, density, and input behavior may differ.
- State explicitly when a change is intentionally platform-specific and why the other clients are unaffected.

## 2. Platform design standards

- Android UI must follow Material Design 3 and Android navigation/back conventions.
- iOS UI must follow Apple HIG, native navigation, sheets, controls, accessibility, safe areas, and current iOS visual conventions.
- macOS UI must follow desktop patterns: `NavigationSplitView`, stable selection, native sidebars, toolbars, menus, keyboard shortcuts, settings scenes, pointer interaction, resizable windows, and appropriate information density.
- Prefer native platform controls and semantic colors/materials before custom chrome.
- Liquid Glass must complement system structure. Do not paint over native sidebars, toolbars, sheets, or scroll-edge effects with opaque custom backgrounds.

## 3. Shared implementation

- Business rules, persistence, formatting rules, validation, import/export, synchronization, and other platform-neutral behavior belong in `shared`.
- Reuse common models and services across clients. Do not independently reimplement the same rule in Android and Apple code.
- Apple UI shared by iOS and macOS should reuse shared views or focused components where the interaction is genuinely common.
- Shared behavior does not require identical presentation. Keep platform-native containers and interaction patterns.
- Before adding a helper or dependency, search for an existing implementation. Prefer the standard library, platform APIs, and installed dependencies.

## 4. UI quality and consistency

- Inspect the current implementation and any supplied screenshot before editing. Do not infer the intended layout from a single symptom.
- Maintain a clear hierarchy through typography, size, weight, spacing, color, and placement. Do not give every label or button equal emphasis.
- Selected controls must remain legible: selected foreground/icon color must contrast with the selected background in both light and dark mode.
- All custom accent colors must come from the active theme. Avoid accidental system blue and decorative hard-coded tints.
- Use semantic colors for income, expense, errors, destructive actions, and secondary text. Theme color is not a substitute for semantic meaning.
- Keep related controls compact, aligned, and visually grouped. Avoid oversized filters, excessive empty space, clipped text, unnecessary cards, and repeated headings.
- Use one consistent component for the same action within a client, including add buttons, list/card switches, filter controls, and detail rows.
- Icons must match the action or domain, use the current icon catalog, and follow the active theme where appropriate.
- Category display names must use `一级分类-二级分类`; omit the separator when no secondary category exists.
- Date text must use the product-specified format, stay on one line, and represent the actual selected range.

## 5. Interaction rules learned from prior corrections

- Back navigation returns to the previous level. “Press again to exit” is only valid at the Android root, never inside a child page.
- Do not make swipe-back or back gestures immediately exit the app from a nested screen.
- Tapping a transaction opens a detail screen first. Edit must prefill the transaction editor; delete must require confirmation.
- Calendar day selection shows that day’s transactions and summary only. Do not add unrelated monthly cards, add buttons, or ledger controls to the day detail.
- Calendar “全部” shows the day’s net income or net expense, not expense only. Requested whole-number summaries must not introduce decimals.
- List/card display switching must remain available wherever specified and must preserve the requested default.
- Search filters must remain compact and results must include category icons.
- Statistics range labels and current-period actions must update correctly for week, month, year, and custom day ranges without wrapping.
- Transaction entry must be completable without unnecessary scrolling: compact note, minute-precision default time, clear amount hierarchy, usable keypad, primary/secondary category selection, and reorder feedback.
- Do not remove user-created icon resources when changing defaults. Default category icons and selectable custom icons are separate requirements.
- Empty or placeholder pages are not acceptable for exposed features such as data management, iCloud, or WebDAV.

## 6. Scope and implementation discipline

- Convert every request into observable acceptance criteria before editing.
- Fix root causes and inspect sibling call sites. Avoid page-by-page patches when a shared state, style, formatter, or service is responsible.
- Make surgical changes. Do not reformat, rename, or refactor unrelated code.
- Do not add speculative features or abstractions. Delete or reuse before adding code.
- Preserve existing user changes and unrelated dirty-worktree files.
- Use `apply_patch` for manual file edits.
- Do not compile after every small edit. Finish the requested batch first; compile when the user asks, before release, or when verification is required.

## 7. Verification

- Verify every affected platform, not only the platform where the request originated.
- For shared logic changes, run the relevant shared tests and migration checks.
- For Android release work, run shared JVM tests, SQLDelight migration verification, Android release lint, and the requested build.
- For Apple work, verify both iOS and macOS targets. A macOS-only check is not evidence that iOS-specific APIs compile.
- Check light/dark mode, every theme color, selected/unselected states, empty/data/error states, navigation, text truncation, and small-screen/window layouts when relevant.
- Do not claim completion from source inspection alone when the requirement is visual or interactive.
- Never commit private bill samples, credentials, signing material, or other personal data. Tests that use local private fixtures must handle their absence explicitly in CI.

## 8. GitHub issues, Git, tags, releases, and CI

- When resolving GitHub issues, first obtain the complete current open-issue list and read each issue body, comments, edits, and relevant attachments before changing code.
- Prefer a structured GitHub connector or authenticated CLI/API. If an unauthenticated API returns `404` for a private repository, do not conclude that the repository or issues are missing; use the user's already signed-in Chrome session for read-only issue retrieval.
- Refresh the complete open-issue list before the final completion audit because new issues may be created while work is in progress.

- Do not commit, push, tag, or publish unless the user requests it.
- Before creating or moving a tag, inspect the commit diff from the previous release tag to the target commit.
- Prepare release notes with exactly these product-facing sections and show them to the user for the GitHub Release page:

  - `## What's Changed` — new user-visible capabilities and behavior.
  - `## What's Updated` — fixes, refinements, compatibility, UI polish, and internal improvements worth noting.

- Release notes must describe the actual tag diff, not conversation memory, and must omit private/internal details that do not help users.
- Push the commit before moving or creating the tag. Confirm the remote branch and dereferenced tag point to the intended commit.
- Observe all GitHub Actions jobs through completion. If any job fails, inspect its exact logs, fix the root cause, push again, update the tag when required, and repeat until every required build and release job succeeds.
- Confirm the final Release contains the expected Android APK, unsigned iOS IPA, macOS arm64 DMG, and macOS x86_64 DMG.

## 9. Completion standard

- “Implemented” means the requested behavior exists in source; “completed” additionally requires the relevant builds, tests, runtime/UI checks, remote tag, Actions jobs, and release assets to be verified.
- Before handoff, audit each explicit requirement against authoritative evidence. Missing or indirect evidence means the work is not complete.
