---
name: ui-backlog
description: Joyufy UI/UX improvement backlog, including completed dashboard iteration and remaining ideas
metadata:
  type: project
---

# UI Backlog

## Implemented

1. Dashboard chart tooltip/crosshair: hover shows exact date, total wealth, per-account breakdown, and absolute + percentage movement.
2. Dashboard empty state: first-run actions to create Bank, Investment, or Cash accounts directly.
3. Explicit chart mode control: visible Area/Bars selector instead of icon-only toggle.
4. Net worth composition view: allocation bar and grouped account percentages by Bank, Investment, and Cash.
5. Chart visibility presets: All, Liquid only, Investments only, plus Custom state when manual account toggles diverge.
6. Missing snapshot task card: stale investment accounts show last snapshot date and a direct Update value action.
7. Account detail sticky balance/action header while scrolling transactions.
8. Account detail richer filters: date range, category, amount range, transaction type, and transfers only.
9. Transaction and snapshot rows hide edit/delete actions until hover/focus while reserving layout space.
10. Transaction dialog date picker with manual dd/MM/yyyy entry fallback.
11. Transaction dialog disables submit for transfers until a destination account is selected.

## Remaining UI/UX ideas

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
