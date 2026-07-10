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

Remaining:
- Expand ledger bulk actions with move-account and mark-reviewed once the domain has a reviewed/draft state.
- Add undo affordances to account-detail single transaction deletes, snapshot deletes, and account archive/delete where safe.
- Full CSV/OFX import preview UI with editable rows, column mapping, validation, duplicate hints, and commit/cancel.

Product ideas:
- CSV/OFX import with mapping, editable preview, duplicate detection, and rollback-safe commit.
- Categorization rules engine.
- Recurring transactions and scheduled transfers.
- Monthly category budgets with burn-rate and warnings.
- Cash-flow forecast.
- Savings goals.
- Investment snapshot annotations and contribution/performance split.
- Investment performance metrics.
- Broker/import adapters for common investment providers.
- Archived account restore/permanent delete management.
- Cloud backup history and timestamp restore.
- Conflict-aware cloud restore/merge.
- CSV/PDF report export.
- Tags, notes, and receipt references.
- Split transactions.
