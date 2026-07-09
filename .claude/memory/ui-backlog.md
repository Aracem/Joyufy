---
name: ui-backlog
description: Joyufy UI/UX improvement backlog, including completed dashboard iteration and remaining ideas
metadata:
  type: project
---

# UI Backlog

## Implemented in dashboard iteration

1. Dashboard chart tooltip/crosshair: hover shows exact date, total wealth, per-account breakdown, and absolute + percentage movement.
2. Dashboard empty state: first-run actions to create Bank, Investment, or Cash accounts directly.
3. Explicit chart mode control: visible Area/Bars selector instead of icon-only toggle.
4. Net worth composition view: allocation bar and grouped account percentages by Bank, Investment, and Cash.
5. Chart visibility presets: All, Liquid only, Investments only, plus Custom state when manual account toggles diverge.

## Remaining UI/UX ideas

6. Improve missing snapshot banner into a task card with each stale investment account, last snapshot date, and direct "Update value".
7. Account detail: add sticky balance/action header while scrolling transactions.
8. Account detail: add richer filters: date range, category, amount range, transaction type, transfers only.
9. Transaction rows: hide edit/delete until hover/focus to reduce visual noise while preserving keyboard accessibility.
10. Transaction dialog: replace free-text date input with a date picker plus manual entry fallback.
11. Transaction dialog: disable submit for transfers until destination account is selected.
12. Category input: show recent custom categories first, with frequency or last-used ordering.
13. Settings: make Cloud Sync status more scannable with connected account, last sync, auto-sync state, and conflict state as status chips.
14. Restore-from-cloud dialog: show a clearer diff table with cloud timestamp vs local state before destructive restore.
15. Danger zone: visually isolate destructive actions and require typed confirmation for "Delete all data".
16. Sidebar: add compact active account indicators and quick account actions on hover.
17. Sidebar reorder mode: make drag handles visible only when reorder mode is enabled.
18. Add keyboard shortcuts: new transaction, new snapshot, search, settings, back to dashboard.
19. Accessibility pass: tooltips for icon buttons, stronger focus rings, content descriptions, and predictable tab order.
20. Light mode polish: dedicated contrast and surface review rather than relying only on token inversion.

## Product feature ideas

- CSV/OFX bank statement import with column mapping and category rules.
- Recurring transactions for salary, rent, subscriptions, and scheduled transfers.
- Monthly category budgets with burn-rate and forecast.
- Investment snapshot annotations separating deposits, withdrawals, and performance.
