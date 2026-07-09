---
name: testing
description: Codex mirror of Joyufy test setup notes
metadata:
  type: project
---

# Testing Notes

- Desktop tests live in `composeApp/src/desktopTest/` and run with `./gradlew :composeApp:desktopTest --no-configuration-cache`.
- `JoyufyDataIntegrityTest` creates a fresh in-memory SQLDelight `JdbcSqliteDriver` per test, so it never touches the real desktop database at `~/.joyufy/joyufy.db`.
- Current coverage focuses on the high-risk data paths: backup restore ID preservation, account type migration, transfer sibling matching, and total wealth aggregation.
