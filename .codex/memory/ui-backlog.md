---
name: ui-backlog
description: Codex mirror of Joyufy UI/UX improvement backlog
metadata:
  type: project
---

# UI Backlog

Implemented:
- Dashboard chart tooltip/crosshair with exact date, total wealth, account breakdown, and absolute + percentage movement.
- First-run dashboard empty actions for Bank, Investment, and Cash accounts.
- Explicit Area/Bars chart mode selector.
- Net worth composition allocation view grouped by Bank, Investment, and Cash.
- Chart visibility presets: All, Liquid only, Investments only, Custom.
- Missing snapshot task card with stale accounts and direct update action.
- Sticky account-detail balance/action header.
- Richer account-detail filters.
- Lower-noise transaction row actions on hover/focus.
- Date picker in transaction dialog.
- Transfer submit disabled until destination is selected.
- Recent/frequent category ordering.
- Cloud Sync status chips in Settings.
- Clearer cloud restore diff table.
- Stronger danger-zone confirmation.
- Sidebar active indicators and hover actions.
- Discoverable sidebar reorder handles.
- Keyboard shortcuts.
- Accessibility pass.
- Light mode polish.
- Account detail month grouping with subtotals and running balances.
- Command palette.
- Dashboard low-priority "Needs review" row; uncategorized opens the global ledger filtered to missing categories.
- Dashboard period comparison mode.
- Settings tab split.
- Transaction templates.
- Global transaction ledger with account/type/search filters, preset filters, sidebar entry, command palette action, and dashboard uncategorized deep-link.
- Transaction review inbox with counters for uncategorized transactions, possible duplicates, stale investment snapshots, and imported drafts.
- Ledger bulk selection for category updates and transfer-aware delete.
- Data quality panel for missing categories, empty descriptions, possible duplicates, broken transfer pairs, unusual amounts, stale accounts, and stale snapshots.
- Ledger snackbar undo for bulk delete, restoring transactions with original IDs.
- Manual JSON backup import preview with local-vs-file diff before destructive restore.
- Transaction review metadata: REVIEWED / NEEDS_REVIEW / DRAFT; imported rows are DRAFT and can be marked reviewed from the ledger.
- Ledger bulk move-account for non-transfer rows and mark-reviewed.
- Account-detail undo for single transaction deletes, transfer-pair deletes, and snapshot deletes.
- Settings account delete/archive undo; delete undo restores owned rows and transfer legs that referenced the deleted account.
- Bank-statement import preview with CSV/TSV/OFX plus ING text export parsing, column mapping, editable draft rows, validation, blocking duplicate detection, and commit/cancel.
- Investment snapshot annotations for deposits, withdrawals, fees, dividends, and optional note.
- Investment performance metrics in account detail: contribution-adjusted gain, market performance, cash flows, time-weighted return approximation, and per-snapshot period return.

Remaining:
- Persisted review notes or "ignore duplicate warning" if users need to dismiss quality warnings without changing transaction data.
- Richer OFX coverage if real bank files expose variants beyond `STMTTRN` blocks.
- True binary `.xlsx` import if banks only provide spreadsheet files; current statement import is text-based.

Product ideas:
- Categorization rules engine.
- Recurring transactions and scheduled transfers.
- Monthly category budgets with burn-rate and warnings.
- Cash-flow forecast.
- Savings goals.
- Archived account restore/permanent delete management.
- Cloud backup history and timestamp restore.
- Conflict-aware cloud restore/merge.
- CSV/PDF report export.
- Tags, notes, and receipt references.
- Split transactions.

Discarded / low ROI:
- CSV import presets for investment providers such as Indexa, DeGiro, or Trade Republic. Weekly investment tracking is a single manual value, so importing a statement is usually slower than entering the snapshot. Reconsider only for stable public APIs with low maintenance overhead.
