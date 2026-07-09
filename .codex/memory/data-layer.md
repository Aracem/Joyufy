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
