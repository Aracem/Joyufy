# Joyufy

**Personal finance tracker for desktop.**
Built for anyone who wants a clear view of their total wealth — no bank connections, no subscriptions, no data in the cloud.

[![Release](https://img.shields.io/github/v/release/Aracem/Joyufy?style=flat-square&color=7B6EF6&label=latest)](https://github.com/Aracem/Joyufy/releases/latest)
[![Platform](https://img.shields.io/badge/platform-macOS%20·%20Windows%20·%20Linux-lightgrey?style=flat-square)](https://github.com/Aracem/Joyufy/releases/latest)
[![License](https://img.shields.io/github/license/Aracem/Joyufy?style=flat-square)](LICENSE)

---

## Download

→ **[Download the latest release](https://github.com/Aracem/Joyufy/releases/latest)**

| Platform | Format |
|---|---|
| macOS | `.dmg` |
| Windows | `.msi` |
| Linux | `.deb` |

---

## Features

- **Total wealth** — sum of all accounts in real time with an evolution chart
- **Bank & cash accounts** — transaction tracking with categories
- **Investments** — weekly market value tracking (Indexa, DeGiro, Trade Republic…)
- **Monthly summary** — income, expenses and top spending categories for the current month
- **Annual analysis** — monthly net bar chart with year navigation and drill-down
- **Local backup** — export and import all your data as JSON

---

## macOS installation

On first launch, macOS will show a security warning because the app is not signed with an Apple certificate. To open it:

1. Right-click `Joyufy.app` → **Open**
2. In the dialog, click **Open Anyway**

Or from Terminal:
```bash
xattr -cr /Applications/Joyufy.app
```

This only needs to be done once.

---

## Stack

Kotlin · Compose Multiplatform · SQLDelight · Koin

Native desktop app (macOS / Windows / Linux). All data is stored locally.

---

## Development

```bash
./gradlew run                 # run in development
./gradlew packageDmg          # build macOS installer
./gradlew packageMsi          # build Windows installer
./gradlew packageDeb          # build Linux installer
```
