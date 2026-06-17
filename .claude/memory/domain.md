---
name: domain
description: Joyufy domain models — Account, Transaction, InvestmentSnapshot — and the invariants that any code touching them must respect
metadata:
  type: project
---

# Domain

Pure Kotlin in `composeApp/src/commonMain/kotlin/com/aracem/joyufy/domain/model/`. No Compose, no SQLDelight, no platform imports (one exception: `Account.color` is a `androidx.compose.ui.graphics.Color` because every read site is a composable — it leaks for ergonomics, but it's the only impurity).

## Models

### `Account`
```
id: Long, name: String, type: AccountType,
color: Color, logoUrl: String?, position: Int, createdAt: Long
```
`AccountType = BANK | INVESTMENT | CASH`.

- **BANK / CASH** → balance is computed from transactions (`getAccountBalance` SQL query).
- **INVESTMENT** → balance is the latest `InvestmentSnapshot.totalValue`; transactions on this account only exist as the income/expense legs of transfers in/out.

### `Transaction`
```
id, accountId, type: TransactionType,
amount: Double, category: String?, description: String?,
relatedAccountId: Long?, date: Long (epoch ms)
```
`TransactionType = INCOME | EXPENSE | TRANSFER`. The string-typed `category` accepts either a `TransactionCategory.label` (suggestion) or free text — the enum is purely a suggestion list, not a constraint.

### `InvestmentSnapshot`
```
id, accountId, totalValue: Double, weekDate: Long (epoch ms, Monday of the week)
```
DB enforces `UNIQUE (accountId, weekDate)` — one snapshot per account per week. Inserts use `INSERT OR REPLACE`.

### `BankPreset`
Static catalogue of ~34 known banks with their logo asset. Not persisted — used to auto-fill the colour/logo when the user types a name that matches.

## Invariants

### Transfers are stored as TWO transactions
A transfer of €100 from account A → account B writes:
- `Transaction(accountId=A, type=EXPENSE, amount=100, relatedAccountId=B)`
- `Transaction(accountId=B, type=INCOME,  amount=100, relatedAccountId=A)`

Why this matters:
- **Per-account balance** is correct (A: -100, B: +100, net 0). No special case.
- **Analysis aggregations (monthly/annual income/expense)** MUST filter `relatedAccountId == null` to exclude both legs of any transfer — otherwise transfers inflate both income and expense totals.
- **Editing or deleting** one leg must mirror the change on the sibling. Use `getRelatedTransfer` (in `JoyufyDatabase.sq`) to find the sibling.

### Investment delta in analysis
Naive change = `latestSnapshot - previousSnapshot`. But a deposit INTO an investment account looks like a snapshot increase that isn't profit. So:

```
investmentDelta = rawDelta - capitalIn + capitalOut
```

where `capitalIn` / `capitalOut` come from TRANSFER transactions targeting the investment account in the same period.

### One investment snapshot per week
Week key is the Monday of the week at 00:00 in the user's local timezone, expressed as epoch ms. Two writes in the same week REPLACE rather than duplicate (DB-level constraint + `INSERT OR REPLACE` query).

Banner on `App.kt` startup (`MissingSnapshotBanner`) lists investment accounts that are missing the current week's snapshot, driven by the `getInvestmentAccountsMissingThisWeek` query.

### Account type change

Editing an account's type works across all three values, but crossing the boundary between the **transaction-based family** (`BANK`, `CASH`) and the **snapshot-based family** (`INVESTMENT`) is destructive and requires user confirmation:

- `BANK`/`CASH` → `INVESTMENT`: the current balance is collapsed into a single synthetic `InvestmentSnapshot` for this week, then every `Transaction` belonging to the account is deleted. Transfer legs on *other* accounts that pointed to this one are left untouched (they remain valid `Transaction`s on those other accounts with their original `relatedAccountId`).
- `INVESTMENT` → `BANK`/`CASH`: every snapshot for the account is deleted. Any transactions on the account (typically transfer deposits into the investment) are kept.
- `BANK` ↔ `CASH`: no data change, just the type field; no confirmation dialog.

The plan is computed by `CreateAccountViewModel.planTypeChange` and shown as a hard warning with row counts before any mutation. Applied atomically inside `saveEdit` so a partial state is impossible.

See [[data-layer]] for the SQL backing these rules.
