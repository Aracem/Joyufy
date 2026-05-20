---
name: architecture
description: Stack, layer responsibilities and the commonMain/desktopMain split for the Joyufy Compose Desktop app
metadata:
  type: project
---

# Architecture

## Stack
- **Kotlin Multiplatform** (currently only `jvm("desktop")` target — Android/iOS slots reserved)
- **Compose Multiplatform** 1.7.3 for UI
- **SQLDelight** 2.0.2 for typed SQL (single database `JoyufyDatabase`)
- **Koin** 4.0.0 for DI
- **Ktor** 2.3.12 client for the Google Drive REST API (desktopMain only)
- **kotlinx.serialization** for backup JSON and Drive token responses

Root package: `com.aracem.joyufy`.

## Layers (top → bottom)

```
ui/screen        Compose @Composable functions, no business logic
ui/viewmodel     StateFlow<UiState> + suspend handlers; injected via Koin
data/repository  Maps SQLDelight rows ↔ domain models, exposes Flow / suspend
data/db          DatabaseDriverFactory (expect/actual) + JoyufyDatabase
domain/model     Pure Kotlin data classes + enums (no Compose, no SQLDelight)
```

The dependency rule is one-way: `ui` → `data` → `domain`. `domain` depends on nothing.

## MVVM + StateFlow pattern
Every ViewModel exposes a single `StateFlow<UiState>` (and sometimes a separate `event: StateFlow<Event>` for one-shot UI events like snackbars). Screens collect via `collectAsState()`. Mutations go through suspend functions on the ViewModel that update an internal `MutableStateFlow`. No LiveData, no Compose `mutableStateOf` inside ViewModels.

Backup and Drive ViewModels use a `sealed interface Event { Idle; Loading; Success; Error }` for snackbars — `App.kt` is the single global consumer, so any screen can trigger an event and the snackbar surfaces.

## commonMain vs desktopMain

Most code lives in `commonMain`. `desktopMain` contains only the things that need a JVM API:

| Concern | commonMain | desktopMain |
|---|---|---|
| `DatabaseDriverFactory` | `expect class` | `actual` (`JdbcSqliteDriver`) |
| `GoogleDriveRepository` | interface | `GoogleDriveRepositoryImpl` (Ktor + `Desktop.browse()` + `ServerSocket`) |
| Koin `provideDriveRepository` | `expect fun` | `actual fun` |
| File pickers | declarations in `ui/FilePicker.kt` (expect-like, currently only desktop has them) | swing/awt `FileDialog` |
| App entry point | `App()` composable | `Main.kt` with `application { Window { ... } }` |

When adding a feature that touches platform APIs, follow the existing pattern: declare the surface in `commonMain` (interface or `expect`), implement it in `desktopMain`, wire through Koin.

## DI graph
`di/Koin.kt` is the single source of truth for the object graph. Two modules:
- `dataModule` — `single { }` for the database, all repositories, `PreferencesRepository`, `GoogleDriveRepository`.
- `viewModelModule` — `factory { }` for one-shot ViewModels (Dashboard, CreateAccount, AccountDetail with parameter, Backup, Settings), `single { }` for `DriveViewModel` (because it must survive across screens to keep the OAuth state).

Inject in composables via `koinInject()`. Never reach into `GlobalContext` from inside Compose — only `Main.kt` does that to wire the close-time auto-sync.

See [[domain]], [[data-layer]], [[ui-layer]], [[cloud-sync]].
