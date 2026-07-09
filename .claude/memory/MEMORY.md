# Joyufy — Project Memory

Architecture, invariants and conventions that travel with the code. Read the topic file when its area is touched.

- [Architecture](architecture.md) — stack, layers, MVVM + StateFlow, commonMain vs desktopMain
- [Domain](domain.md) — Account / Transaction / InvestmentSnapshot models and the transfer-pair invariant
- [Data layer](data-layer.md) — repositories, SQLDelight schema, aggregate queries, DB file location
- [UI layer](ui-layer.md) — screen ↔ ViewModel map, i18n via CompositionLocal, key components
- [UI backlog](ui-backlog.md) — proposed dashboard/account/settings UX improvements and implementation status
- [Cloud sync](cloud-sync.md) — Google Drive OAuth2 flow, auto-sync timing, token storage
- [Build & run](build-and-run.md) — Gradle tasks, version source-of-truth, libs.versions.toml
- [Conventions](conventions.md) — EUR-only, no auth, dark-first colors, animation rule
