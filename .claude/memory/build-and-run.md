---
name: build-and-run
description: Gradle commands, version source-of-truth, libs.versions.toml convention, packaging targets
metadata:
  type: project
---

# Build & run

## Common Gradle commands

```bash
./gradlew :composeApp:run                 # Run the desktop app in dev
./gradlew :composeApp:compileKotlinDesktop # Just compile (fastest sanity check)
./gradlew :composeApp:packageDmg          # macOS installer
./gradlew :composeApp:packageMsi          # Windows installer
./gradlew :composeApp:packageDeb          # Linux installer
./gradlew :composeApp:packageDistributionForCurrentOS  # Whatever fits this host
./gradlew clean
```

There are no JUnit/UI tests yet, so there's no `:test` task that does anything useful.

## Version source-of-truth

Single string `val appVersion = "1.2.1"` near the top of `composeApp/build.gradle.kts`. This drives:

1. `packageVersion = appVersion` in the `nativeDistributions` block (the installer version).
2. The `generateAppVersion` task that writes `AppVersion.kt` into `build/generated/appversion/commonMain/kotlin/` before compilation. The generated `object AppVersion { const val NAME = "1.2.1" }` is what Settings shows in the footer.

**To bump the version**: change `appVersion`, commit, tag, package. Never edit `AppVersion.kt` directly — it's regenerated.

## libs.versions.toml convention

All dependency coordinates live in `gradle/libs.versions.toml`. `build.gradle.kts` only references them as `libs.xxx`. When adding a new library:

1. Add a `[versions]` entry if it's new.
2. Add a `[libraries]` entry with `module = "..."` and `version.ref = "..."`.
3. Reference as `libs.your.new.lib` in `build.gradle.kts`.

Hyphens in the toml become dots in the Kotlin accessor (`ktor-client-cio` → `libs.ktor.client.cio`).

## Source-set layout

```
composeApp/src/
├── commonMain/
│   ├── kotlin/com/aracem/joyufy/...
│   └── sqldelight/com/aracem/joyufy/db/JoyufyDatabase.sq
└── desktopMain/
    ├── kotlin/com/aracem/joyufy/...
    └── resources/icon.{icns,ico,png}
```

`commonMain` is the default — every new file goes there unless it needs JVM-only APIs ([[architecture]] § commonMain vs desktopMain).

## Plugins applied

`kotlin.multiplatform`, `compose.multiplatform`, `compose.compiler`, `kotlin.serialization`, `sqldelight`. The first three pin to the same `kotlin` version (`2.1.0`); SQLDelight and Compose Multiplatform have their own version refs.

`-Xexpect-actual-classes` is enabled in `kotlin.compilerOptions.freeCompilerArgs` to allow `expect class` without warnings (still experimental in Kotlin).

## Where build output goes

- Compiled classes: `composeApp/build/classes/...`
- Generated SQLDelight code: `composeApp/build/generated/sqldelight/...`
- Generated `AppVersion.kt`: `composeApp/build/generated/appversion/commonMain/kotlin/`
- Native installers: `composeApp/build/compose/binaries/main/`

All in `.gitignore`.
