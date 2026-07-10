# OmniFlow Context

OmniFlow is a personal multi-platform accounting app. This glossary keeps product terms stable across requirements, design, and implementation docs.

## Language

**Main Navigation**:
The primary app-level navigation surfaces: Home, Analytics, Add Transaction, Search, and More.
_Avoid_: Root tabs, top-level pages

**More**:
A main navigation destination that exposes the Net Asset Overview and data management, and groups secondary management modules including Settings, Import, Export, Rules, Reminders, Ledgers, Accounts, Assets, Category Management, and Tag Management.
_Avoid_: Settings, Misc

**Net Asset Overview**:
The global account-based summary shown in More, separate from any ledger scope.
_Avoid_: Ledger Balance

**RMB Amount**:
The only monetary currency supported in the first version. Amounts, balances, assets, and liabilities are all expressed in Chinese yuan.
_Avoid_: Foreign Currency Amount

**Home**:
The main ledger overview surface. It combines ledger selection, month navigation, calendar-based income/expense overview, and transaction details grouped by day.
_Avoid_: Transaction List

**Home Ledger Scope**:
The selected scope for Home data: all ledgers or one ledger. It applies consistently to the calendar, monthly summary, and transaction details.
_Avoid_: Multi-ledger selection

**Date Transaction Detail**:
The selected day's complete transaction detail, opened from the Home calendar without replacing the Home transaction detail. It initially uses the Home Ledger Scope, but its scope can be changed independently.
_Avoid_: Filtered Home

**Transaction Detail Display Mode**:
The shared card or list presentation selection used by both Home transaction details and Date Transaction Detail.
_Avoid_: Per-panel display mode

**Analytics Ledger Scope**:
The selected scope for Statistics data: all ledgers or one ledger. It is independent from the Home Ledger Scope.
_Avoid_: Home Ledger Scope

**Statement Table**:
An annual table of monthly income, expense, and balance, including the annual total.
_Avoid_: Transaction List

**Search Query**:
An ad hoc transaction query scoped to all ledgers or one ledger, with optional keyword and field filters. It is not saved and does not affect other pages.
_Avoid_: Saved Filter

**Add Transaction**:
The main manual entry surface. It uses income/expense switching, primary category icon selection, secondary category shortcuts, account/tag controls, note/date fields, and a numeric keypad to create transactions quickly.
_Avoid_: Transaction Form

**Primary Category Icon**:
A required icon from the project's bundled Lucide SVG icon set, assigned to a primary category. A secondary category may have no icon.
_Avoid_: Category image

**Category Deletion**:
The removal of a category from future transaction selection. Existing transactions retain their category and historical analysis remains unchanged.
_Avoid_: Historical Transaction Recategorization

**Account Deletion**:
The removal of an account from future transaction selection and Net Asset Overview. Existing transactions retain their account and remain queryable.
_Avoid_: Historical Transaction Reassignment

**Ledger Deletion**:
The archival of a ledger and its categories, tags, rules, and transactions. Archived ledger data is absent from default queries but remains in backups.
_Avoid_: Permanent Ledger Erasure

**Default Ledger**:
An optional preferred ledger for manual transactions that have no explicitly supplied ledger. It can be unset.
_Avoid_: Required Ledger

**Application Preference**:
A restorable, non-sensitive user choice that affects application behavior or appearance.
_Avoid_: Device Permission

**Restorable App State**:
All business data and non-sensitive application preferences included in a backup. WebDAV credentials, biometric authorization, and notification permission are device-only state.
_Avoid_: Device Security State

**Backup Restore**:
The confirmed replacement of the current device's Restorable App State with one selected backup. It never merges local and backup data.
_Avoid_: Backup Merge
