---
name: data-layer
description: Repository inventory, SQLDelight schema, aggregate queries, and where the DB file lives on disk
metadata:
  type: project
---

# Data layer

## Repository inventory

Path: `composeApp/src/commonMain/kotlin/com/aracem/joyufy/data/repository/` (plus `data/cloud/` for the Drive one).

| Repository | Role |
|---|---|
| `AccountRepository` | CRUD on `Account`. Maps SQLDelight rows → `domain.model.Account` (and the hex string → `Color`). |
| `TransactionRepository` | CRUD on `Transaction`. Includes `getRelatedTransfer()` for finding the sibling leg of a transfer. |
| `InvestmentSnapshotRepository` | CRUD on `InvestmentSnapshot`. `upsert` semantics via `INSERT OR REPLACE`. |
| `WealthRepository` | Read-only aggregator. Single source for "total wealth" (sum of latest investment snapshots + bank/cash balances). |
| `PreferencesRepository` | Reads/writes a JSON file at the OS-standard per-user app data dir (mac: `~/Library/Application Support/Joyufy/preferences.json`, win: `%APPDATA%\Joyufy\…`, linux: `$XDG_CONFIG_HOME/Joyufy/…` or `~/.config/Joyufy/…`). Writes are atomic (tmp file + `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`) so the data survives JVM exit. Auto-migrates from the legacy `java.util.prefs.Preferences` node `com/aracem/joyufy` on first run if the JSON file is absent. Stores app-level settings: dark mode, language, analysis card expanded state, Drive tokens, last sync timestamp, auto-sync toggle. |
| `BackupRepository` | Serialises the full DB to JSON (`JoyufyBackup { version, exportedAt, accounts[], transactions[], snapshots[] }`) and imports it back atomically (delete all → re-insert with original IDs to preserve FKs). |
| `GoogleDriveRepository` | Interface in commonMain, `GoogleDriveRepositoryImpl` in desktopMain. See [[cloud-sync]]. |

Every repository is a Koin `single { }` in `di/Koin.kt`.

## SQLDelight schema

File: `composeApp/src/commonMain/sqldelight/com/aracem/joyufy/db/JoyufyDatabase.sq`.
Generated package: `com.aracem.joyufy.db`.

Three tables: `Account`, `` `Transaction` `` (backticked because `Transaction` is a SQL keyword), `InvestmentSnapshot`. Foreign keys cascade on delete from `Account` so wiping an account drops its transactions and snapshots automatically.

`Transaction` also carries review/import metadata:
- `review_status`: `REVIEWED`, `NEEDS_REVIEW`, or `DRAFT`. Manual transactions default to `REVIEWED`; bank-statement imports are committed as `DRAFT` so the ledger can surface them for review.
- `import_batch`: optional batch identifier for imported rows. Preserve it in backup/export/undo paths.

`InvestmentSnapshot` carries snapshot annotation metadata:
- `deposit_amount`, `withdrawal_amount`, `fee_amount`, `dividend_amount`: optional EUR flow fields stored as non-negative `REAL` values with `0.0` defaults.
- `note`: optional free-form snapshot note.

Account-detail investment metrics use these fields:
- Contribution-adjusted gain = value change - deposits + withdrawals.
- Market performance = contribution-adjusted gain + fees - dividends.
- Time-weighted return is an approximation from compounded snapshot-to-snapshot contribution-adjusted returns.
- Manual snapshot annotation fields always win. For old snapshots where deposits/withdrawals/fees/dividends are all zero, account-detail performance may derive deposits/withdrawals from transfer transaction legs in the same snapshot week.
- Settings → Data exposes a guided "Backfill investment flows" action. It previews affected snapshots, then writes derived deposits/withdrawals only into snapshots whose amount annotation fields are all zero; existing manual annotations and notes are preserved.

Bank-statement imports currently support CSV, TSV, OFX, ING text exports, and ING semicolon CSV conversions with Excel-style preamble rows such as `Tabla 1` before the real `F. VALOR` header. Imported duplicate detection is blocking, not advisory: duplicate key = account + local date + type + amount cents + normalized description. The importer blocks matches against existing DB rows and repeated rows inside the same import batch.

### Important queries

| Query | Purpose |
|---|---|
| `getTotalWealth` | The Dashboard headline number. Sums `(latest InvestmentSnapshot per account) + (signed sum of BANK/CASH transactions)`. Both legs filter `is_archived = 0`. |
| `getAccountBalance` | Per-account balance for BANK/CASH. `COALESCE(SUM(CASE WHEN type='INCOME' THEN amount ELSE -amount END), 0)`. |
| `getRelatedTransfer` | Finds the sibling leg of a transfer (used when editing/deleting one leg, see [[domain]]). |
| `getInvestmentAccountsMissingThisWeek` | Drives the missing-snapshot banner on the Dashboard. Takes the current Monday epoch as parameter. |
| `getAllBankCashTransactions` / `getAllSnapshots` | Used by the Dashboard analysis card and account history feeds. |
| `insertAccountWithId` / `insertTransactionWithId` / `insertSnapshotWithId` | ID-preserving restore paths used only by backup import/cloud restore. Keep these in sync with backup DTO fields. |
| `deleteTransactionsForAccount` / `deleteSnapshotsForAccount` | Per-account wipes. Used by the destructive account type change in `CreateAccountViewModel.applyTypeChange` (see [[domain]] § Account type change). |
| `deleteAllAccounts` | Full account-table purge used by backup import and Settings "delete all data"; unlike `getAllAccounts()`, it also covers archived rows. |

If you add a column to a table, you also need to add an `ALTER TABLE … ADD COLUMN …` step in `DatabaseDriverFactory.openOrCreate()` (`desktopMain`) so existing installs migrate. SQLDelight does not auto-migrate.

## Where the DB lives

Desktop: `~/.joyufy/joyufy.db` (created on first launch by `DatabaseDriverFactory.createDriver()` in `desktopMain`).

The driver is `JdbcSqliteDriver` from `app.cash.sqldelight:sqlite-driver` + `org.xerial:sqlite-jdbc`. If the file exists but `Account` is missing, the factory deletes the file and recreates the schema (recovery from a corrupt install).

## Backup format

Serialised by `BackupRepository.export()` with `kotlinx.serialization.json` (`prettyPrint = true`):

```json
{
  "version": 1,
  "exportedAt": <epoch_ms>,
  "accounts":     [ { id, name, type, colorHex, logoUrl?, position, createdAt } ],
  "transactions": [ { id, accountId, type, amount, category?, description?, relatedAccountId?, date, reviewStatus, importBatch? } ],
  "snapshots":    [ { id, accountId, totalValue, weekDate, deposits, withdrawals, fees, dividends, note? } ]
}
```

`import()` is destructive: delete all transactions → delete all snapshots → delete all accounts → re-insert in the same order with original IDs (`insertAccountWithId`, `insertTransactionWithId`, `insertSnapshotWithId`) to preserve FK relationships and keep cloud diff idempotent. There is no schema migration on import; if `version` changes, that's where to branch.

Same JSON is the payload for [[cloud-sync]].
