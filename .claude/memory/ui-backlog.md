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
12. Category input ranks previously used categories by frequency and recency before static presets.
13. Settings Cloud Sync status chips show connection, sync activity/conflict, auto-sync mode, and last sync.
14. Restore-from-cloud dialog shows cloud timestamp, local-state context, and a table of added/removed/modified counts.
15. Danger zone isolates destructive actions and requires typed confirmation for Delete all data.
16. Sidebar account rows show stronger active indicators and quick add action on hover.
17. Sidebar reorder handles are only visible in reorder mode.
18. Keyboard shortcuts: Cmd/Ctrl+N transaction, Cmd/Ctrl+U snapshot, Cmd/Ctrl+F search, Cmd/Ctrl+, settings, Cmd/Ctrl+1 dashboard.
19. Accessibility pass: tooltip icon buttons, focus borders on transaction/snapshot rows, and stronger content descriptions.
20. Light mode contrast/surface tokens reviewed and adjusted.
21. Account detail month grouping for bank/cash transactions, with sticky month subtotal headers and per-transaction running balance.
22. Command palette: Cmd/Ctrl+K launcher for navigation, account creation, backup import/export, account opening, and account-detail quick actions.
23. Dashboard "Needs review" row: low-priority compact row below the main dashboard content, hidden when all counts are zero; pending snapshots open the existing update flow, while uncategorized opens the global transaction ledger filtered to missing categories.
24. Dashboard comparison mode: current month vs previous month for income, expenses, investment movement, and net.
25. Settings information architecture: General, Accounts, Data, Cloud, and About tabs.
26. Transaction templates: non-persistent quick presets for common manual entries in the transaction dialog.
27. Global transaction ledger: cross-account Transactions route with account/type/search filters, preset filters, sidebar entry, command palette action, and dashboard uncategorized deep-link.
28. Transaction review inbox: ledger counters for uncategorized transactions, possible duplicates, stale investment snapshots, and imported drafts.
29. Bulk transaction selection: ledger multi-select supports category updates and transfer-aware delete.
30. Data quality panel: detects missing categories, empty descriptions, possible duplicates, broken transfer pairs, unusual amounts, stale accounts, and stale investment snapshots.
31. Undo affordance: ledger bulk delete shows snackbar undo and restores deleted transaction rows with original IDs.
32. Local backup import preview: manual JSON import shows the backup-vs-local diff table before destructive restore.

## Remaining UI/UX ideas

1. Expand ledger bulk actions with move-account and mark-reviewed once the domain has a reviewed/draft state.
2. Add undo affordances to account-detail single transaction deletes, snapshot deletes, and account archive/delete where safe.
3. Full CSV/OFX import preview UI: editable row table, column mapping, row-level validation, duplicate hints, and commit/cancel step before writes.

## Product feature ideas

1. CSV/OFX bank statement import with column mapping, editable preview, duplicate detection, and rollback-safe commit.
2. Categorization rules engine: match by description/payee/amount/account and auto-assign category, type, or tags during import/manual entry.
3. Recurring transactions for salary, rent, subscriptions, cash withdrawals, and scheduled transfers, with generated pending instances.
4. Monthly category budgets with burn-rate, remaining amount, and overspend warnings.
5. Cash-flow forecast: projected month-end balances using recurring transactions, budgets, and expected investment contributions.
6. Savings goals: target amount/date, linked accounts, progress, and suggested monthly contribution.
7. Investment snapshot annotations separating deposits, withdrawals, fees, dividends, and market performance.
8. Investment performance metrics: contribution-adjusted gain, time-weighted return approximation, and per-account performance chart.
9. Broker/import adapters: CSV import presets for Indexa, DeGiro, Trade Republic, and similar providers before any API/scraping work.
10. Archived account management: list archived accounts, restore them, or permanently delete with typed confirmation.
11. Cloud backup history: keep recent cloud/local backup versions and allow restore to a chosen timestamp instead of only latest.
12. Conflict-aware cloud restore: merge or selectively keep local/cloud entities instead of full destructive replace.
13. Export reports: CSV/PDF monthly summary, category breakdown, net-worth history, and tax/investment movement report.
14. Tags and notes: free-form tags plus richer notes/receipt reference for transactions, independent from category.
15. Split transactions: one bank transaction distributed across multiple categories.
