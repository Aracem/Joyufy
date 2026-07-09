---
name: conventions
description: Project-wide product and code conventions distilled from CLAUDE.md — EUR-only, no auth, dark-first, animation rule
metadata:
  type: project
---

# Conventions

## Product rules

- **EUR-only.** No multi-currency. Amounts are stored as `Double` (REAL in SQLite) without a currency column. If multi-currency ever lands, every aggregate query needs revisiting.
- **No authentication.** Single-user, local app. Don't add login screens, user IDs, or "current user" indirection.
- **Three account types**: BANK, INVESTMENT, CASH. Bank/Cash use the transaction model; Investment uses weekly snapshots. See [[domain]].
- **Weekly cadence for investment snapshots**, never daily. Week key is the local Monday.
- **The dashboard is the home.** Total wealth headline + chart + analysis card + accounts grid. Other screens slide in from the dashboard.

## Visual style

Dark-first (light mode exists). Reference apps: Revolut, TradeRepublic, Notion.

### Color tokens (single source: `ui/theme/Color.kt`)

| Token | Hex | Use |
|---|---|---|
| `Dark_Background` | `#0F0F0F` | App background |
| `Dark_SurfaceDefault` | `#1A1A1A` | Cards, sidebar |
| `Dark_SurfaceRaised` | `#242424` | Hover / pressed surfaces |
| `Dark_Border` | `#2E2E2E` | Dividers, outlines |
| `Accent` | `#7B6EF6` | Primary action, focus rings |
| `Positive` | `#34C77B` | Income, positive deltas |
| `Negative` | `#F25C5C` | Expense, negative deltas, destructive actions |

Account palette (`AccountPalette` in the same file): 12 fixed colours, plus a free hex picker for everything else.

### Typography

System font stack with Inter as the preferred face. **Tabular figures** (`fontFeatureSettings = "tnum"`) on every monetary amount so digits line up vertically.

### Animation rule

Never snap. Every visibility or size change goes through one of:
- `AnimatedVisibility` for show/hide
- `animate*AsState` (Float, Color, Dp) for property tweens
- `AnimatedContent` for swapping composables

Default tween is ~280ms with the standard easing. Larger transitions (screen swaps) can use longer durations but should still feel snappy.

This applies to **everything** including dialogs, snackbars, expand/collapse, and conditionally-shown rows. A previous code-review flag from the user: "no abrupt show/hide".

## Code conventions

- **Spanish in product-facing strings via `Strings.kt`**; English in code identifiers and comments. Domain enums use English (`SALARY`, `GROCERIES`) with Spanish labels for display.
- **No `var` in `@Composable` parameters**; hoist state.
- **No `runBlocking` in production paths** except the close-time auto-sync in `Main.kt`, which is explicitly bounded by `withTimeoutOrNull(5_000)`.
- **Single global snackbar** owned by `App.kt`; ViewModels publish events, App consumes them.
- **Comments are sparse.** Only write a comment when the *why* isn't obvious from the code. Don't restate what the code does.
- **No `LiveData` / `mutableStateOf` inside ViewModels** — always `StateFlow` / `MutableStateFlow`. See [[architecture]].

## Agent memory coordination

When durable memory is created or updated, mirror the relevant information between Codex memory and Claude memory. Use the matching scope: global/cross-project preferences belong in global memory; Joyufy-specific architecture, domain, workflow, or convention notes belong in `.claude/memory/`. Never persist secrets, tokens, credentials, or transient command output.

## Future hooks (not implemented yet)

The data layer is designed to be source-agnostic:
- A future per-account "remote provider" could populate weekly investment snapshots automatically (Indexa, DeGiro, TradeRepublic).
- The model is single-user; if multi-profile lands later, it should slot in at the DB level (add a `profileId` column) rather than at the UI.
- CSV/OFX import is a planned alternative to manual bank-transaction entry.

Don't pre-build for these. Mention them when relevant and otherwise leave them alone.
