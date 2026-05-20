---
name: ui-layer
description: Screen ↔ ViewModel map, the CompositionLocal i18n pattern, and the key shared components
metadata:
  type: project
---

# UI layer

## Screen ↔ ViewModel map

All under `composeApp/src/commonMain/kotlin/com/aracem/joyufy/ui/`.

| Folder | Screen / dialog | ViewModel |
|---|---|---|
| `dashboard/` | `DashboardScreen.kt` (wealth chart + accounts grid + analysis card) | `DashboardViewModel.kt` (wealth, monthly/annual summaries, drag-to-reorder) |
| `account/` | `AccountDetailScreen.kt`, `CreateAccountDialog.kt`, `AddTransactionDialog.kt`, `AddSnapshotDialog.kt` | `AccountDetailViewModel.kt` (takes `accountId: Long` as Koin factory param), `CreateAccountViewModel.kt` |
| `settings/` | `SettingsScreen.kt` (incl. Cloud Sync section) | `SettingsViewModel.kt` |
| `backup/` | (no screen — just events) | `BackupViewModel.kt` |
| `drive/` | (no screen — Settings section consumes it) | `DriveViewModel.kt` (Koin `single` because OAuth state must survive screen changes) |

`App.kt` is the top-level orchestrator: it holds `currentScreen`, the dark-mode and language state, hooks the global snackbar, and listens to `BackupViewModel.event` + `DriveViewModel.event` to surface success/error messages no matter which screen triggered them.

Navigation is a `sealed interface Screen { Dashboard; AccountDetail(accountId); Settings }` switched via `AnimatedContent` — no nav-graph library, no back-stack persistence.

## i18n via CompositionLocal

No `stringResource()`. The pattern is:

1. `ui/strings/Strings.kt` — `data class Strings(val foo: String, val bar: String, …)` with every string the app needs as a typed property; `val LocalStrings = compositionLocalOf { StringsEn }`.
2. `ui/strings/StringsEn.kt` and `StringsEs.kt` — full instances of the data class.
3. `App.kt` reads `prefsRepo.getLanguage()` (`""` = system, `"en"`, `"es"`) and wraps the tree in `CompositionLocalProvider(LocalStrings provides chosenStrings)`.
4. Inside any composable: `val strings = LocalStrings.current`.
5. For non-composable helpers (e.g. `buildRecentWeeks(...)`), pass the required string fields as parameters.

Language switch is instant — no app restart needed because the `CompositionLocalProvider` value changes and triggers recomposition.

When adding a new string: add the property to `Strings.kt`, add the value to BOTH `StringsEn` and `StringsEs`. The compiler will catch missing translations because the data class enforces it.

## Shared components

Path: `ui/components/`.

| Component | Role |
|---|---|
| `WealthChart.kt` | Canvas-based chart, supports AREA and BARS modes. Toggle stored in `ChartRangePreference` singleton so both Dashboard and AccountDetail share the user's last choice. |
| `Sidebar.kt` | Collapsible left sidebar with the account list + reorder mode + theme toggle. |
| `AccountCard.kt` | Account row in the dashboard grid with the colour stripe. |
| `AccountLogo.kt` | Three variants: SVG logo (bank presets), coloured circle with initials, cash icon. |
| `ConfettiBurst.kt` + `ConfettiOverlay.kt` | Easter-egg confetti on 10 clicks of the version label in Settings. |
| `MissingSnapshotBanner.kt` | Yellow banner on Dashboard when investment accounts have no current-week snapshot. |
| `UpdateBanner.kt` | Banner that surfaces when `update/UpdateChecker.kt` finds a newer GitHub release. |

## `ChartRangePreference` singleton

`ui/dashboard/ChartRangePreference.kt`. Plain `object` (not in Koin — it's a UI preference, not data). Holds the last chosen chart range (`1W / 1M / 3M / 6M / YTD / 1Y`) and the chart mode (`AREA / BARS`) in a `MutableStateFlow`. Both the Dashboard chart and the AccountDetail chart read from it so a change on one updates the other.

## Animation rule

Every visibility or size change goes through `AnimatedVisibility` / `animate*AsState` / `AnimatedContent`. No abrupt show/hide. Tween defaults around 280ms. See [[conventions]].

See [[architecture]] for the StateFlow + ViewModel pattern, [[domain]] for the data the UI displays.
