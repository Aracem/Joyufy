---
name: data-layer
description: Codex mirror of durable Joyufy data-layer notes
metadata:
  type: project
---

# Data Layer Notes

- Backup import/cloud restore is destructive and must preserve IDs for all persisted entities: accounts, transactions, and investment snapshots. Use `insertAccountWithId`, `insertTransactionWithId`, and `insertSnapshotWithId`.
- Settings "delete all data" and backup import should call `deleteAllAccounts`, not `getAllAccounts().forEach(deleteAccount)`, so archived account rows are purged too.
- Account edit persistence must include the `type` column. `CreateAccountViewModel.saveEdit` may run a destructive cross-family migration before `AccountRepository.updateAccount`.
- Transfer edit/delete sibling lookup should match related account, origin account, amount, and opposite leg type, then choose the nearest date. Matching only by account pair can touch the wrong sibling when multiple transfers exist between the same accounts.
- Transactions include `review_status` (`REVIEWED`, `NEEDS_REVIEW`, `DRAFT`) and optional `import_batch`. Manual rows default to `REVIEWED`; bank-statement import commits valid rows as `DRAFT`. Backup, undo, and ID-preserving restore paths must preserve both fields.
- Bank-statement imports support CSV, TSV, OFX, ING text exports, and ING semicolon CSV conversions with Excel-style preamble rows such as `Tabla 1` before the real `F. VALOR` header. Duplicate detection blocks rows using account + local date + type + amount cents + normalized description, both against existing DB rows and repeated rows in the same import batch.
