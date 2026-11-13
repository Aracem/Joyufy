# Joyufy complete redesign — implementation contract

## Objective

Deliver a complete, cohesive Material 3 redesign of the existing Compose Desktop application in **both light and dark**, using [material-studio.html](material-studio.html) and its **Material 3** and **Material 3 light** options as the visual reference. Preserve every existing workflow and financial data invariant. The HTML uses fictional data and illustrative controls; it is a design reference, not evidence that behavior exists or a specification to copy sample values into production.

Use the same layout, component hierarchy and interaction model in both themes, with coordinated blue and lilac tonal surfaces, rounded Material controls, clear typography and meaningful accents. Retain Trade Republic's clarity with slightly richer charts. Favor opaque, solid colors over transparency; use transparency only where functionally appropriate, such as a modal scrim. Keep the interface lively without visual noise. Preserve English and Spanish, desktop accessibility, and existing user preferences.

## Design tokens and visual direction

These prototype colors are reference anchors; adjust individual tokens as needed for contrast and legibility while preserving the relationship between themes. Define reusable semantic tokens and Material color schemes rather than scattering literals throughout screens. Account colors remain independent of income, expense and warning semantics.

- **Dark:** background `#14151E`, sidebar `#202337`, regular surface `#282B37`, hero `#28324B`, monthly surface `#303A52`, composition surface `#3A304D`, primary action `#B8CDFF` with `#132B59` text; amber warning surface `#50452E` with `#FFDFAC` text.
- **Light:** background `#F8F8FF`, sidebar `#EDF0FB`, regular surface `#F0EFF8`, hero `#E2EAFE`, monthly surface `#D9E6FB`, composition surface `#EADFF7`, primary action `#345D9C` with white text; amber warning surface `#F5E6C4` with `#604920` text.
- Use a coherent sans-serif type scale with tabular figures for monetary values; provide reliable fallbacks, readable hierarchy and layouts that accommodate translations and text scaling. Avoid the Studio editorial serif treatment.
- Follow Material 3 component patterns: generous rounded cards and dialogs, pill-shaped primary and segmented controls, tonal selection states, clear input labels, and visible hover, keyboard focus, disabled and error states. Keep density appropriate for a desktop finance application.
- Layout: expanded sidebar and collapsible rail, adaptive content margins and sensible content width. At narrow desktop sizes reflow cards, controls and tables instead of clipping. Preserve existing desktop navigation and shortcuts.
- Verify text and interactive contrast in both themes; never rely on color or hover alone to communicate status. Maintain semantic distinction for positive, negative and warning states.

## Prototype navigation map

The HTML includes Overview, Transactions, Rules, Bank/Cash account detail, Investment detail, and General / Accounts / Data / Cloud / About settings. It illustrates transaction, snapshot, account, filters, import, rule, bulk actions, data quality, backup, backfill, destructive confirmation, cloud and appearance dialogs. Range and chart-mode controls, the monthly navigation, and transaction type/category/date controls are interactive. Month comparisons use clearly labelled sample data; actual transaction persistence is not simulated. The prototype intentionally uses representative sample content and does not implement real persistence, validation, graphs, matching or destructive actions. It is a visual specification; production acceptance requires the actual existing behavior below.

## Screen and workflow requirements

### Application shell

- Preserve sidebar account navigation, account order and reorder mode, account creation, quick add, collapse/expand, theme switch, settings and existing keyboard shortcuts. Show current destination clearly. Preserve the command palette and all existing routes including categorization rules.
- Support an empty account list and a long account list, keyboard navigation and focus restoration after dialogs. Keep the current responsive desktop behavior.

### Overview

- Put total net worth and selected-period absolute/percentage movement above the principal chart. Support all existing ranges and area/bar modes, account visibility controls and presets, per-account breakdown, hover details and chart comparison behavior. No illustrative value may be hard-coded.
- Follow the chart with missing-snapshot actions, composition, a “This month” cash-flow summary and period comparison. Provide an explicit action to open monthly analysis and preserve the existing annual analysis. Preserve links to uncategorized transactions and pending snapshot tasks, update notices, onboarding actions, export/import access and the existing easter egg.
- Financial semantics: include all supported accounts, do not count transfer legs as income/expenses, and preserve current wealth and investment calculations. Use actual state and established formatters.

### Monthly analysis

- Open the detail from the overview’s “This month” summary. Show the selected month, income, expenses and net cash flow, plus spending by category with amounts and a comparison to the preceding month.
- Provide previous/next month navigation and prevent navigating beyond the current month; support year boundaries. For the earliest available month show an explicit unavailable comparison rather than fabricating a zero. Retain the existing monthly and annual analysis data and category breakdown.
- Exclude account-to-account transfer legs from income, expenses and category totals. Include uncategorized spending explicitly so category amounts reconcile to total expenses. Describe incomplete current-month comparisons accurately and provide useful empty/loading/error states.
- Any action to show transactions for a category or month must actually apply the corresponding filter; an unfiltered ledger link must be labelled accordingly.

### Account detail

- Retain account editing, balance, period change, range/mode controls and account history. Show balances in neutral text; reserve positive/negative colors for changes and negative values.
- Bank/cash: retain transaction creation, edit, delete/undo, transfers, grouping, running balance, search and all filters, plus statement import.
- Investment: retain weekly snapshot create/edit/delete/undo; deposits, withdrawals, fees, dividends and notes; contribution-adjusted gain, market performance, TWR approximation, flow inference and per-period return. Do not imply the approximation is an exact return.
- Preserve warning and confirmation for account type changes across transaction/snapshot families, including destructive migration behavior.

### Transaction ledger and review

- Retain account, type, date, category, amount, text and existing preset filters; grouped rows and account context; edit/delete and transfer pairing.
- Preserve review inbox counters, imported draft and review status, data-quality checks, selection and bulk category/move/review/delete, transfer-aware deletes and undo. Surface review actions contextually without hiding them behind hover alone.
- On smaller windows allow horizontal scrolling or adaptive columns while keeping amount, date and transaction identifiable.

### Categorization rules

- Preserve current rule creation, editing, deletion, enabling, ordering, matching conditions, category assignment, application/preview behavior, import integration and validation exactly as supported by the code. Do not promise rule conditions or retroactive actions the app does not currently support without separately implementing and testing them.

### Entry, import and dialogs

- Use clearly selected Expense / Income / Transfer buttons. When entry starts from an account, use that account as the source without showing a redundant account selector; when opened globally, require account selection. Keep the source visible as context and preserve edit behavior. A transfer always requires a separate valid destination.
- Provide a category selector with suggested/recent categories and a custom free-text option. Provide a date picker as well as manual entry. Preserve templates, validation and edit mode. Transfer submission requires a valid destination; preserve transfer pair invariants.
- Snapshot entry supports existing annotation fields and validation. Account creation/edit keeps presets, colors, logos and type-change confirmation.
- Statement import retains CSV/TSV/OFX/ING text support, file selection, mappings, editable preview, validation, duplicate blocking, draft status, commit/cancel and errors. Display file/row counts and errors before committing. Binary XLSX is out of scope.
- Destructive operations retain explicit confirmation, restore previews, IDs, relationships and undo where currently provided. Never use the prototype's sample Continue buttons as authorization to alter data.

### Settings and data

- General: theme and language, all current preferences.
- Accounts: all current management, archive/delete and undo behavior.
- Data: backup export/import and difference preview, statement import, data quality, flow backfill preview and danger zone.
- Cloud: Drive connection, status, sync and auto-sync, restore comparison/confirmation, errors and disconnect; retain existing OAuth behavior and backup semantics.
- About: current version, update check and existing informational actions.

## UX improvements to implement within the redesign

1. Group the overview around wealth, attention, composition and recent activity; open a dedicated monthly detail for category spending and month-to-month comparison. Reduce simultaneous secondary chart controls while keeping all functions discoverable.
2. Make the current period and comparison context explicit beside every change; never present investment deposits as market gain.
3. Use consistent dialog hierarchy: title, purpose, fields, validation near the field, cancel and primary action. Remember focus; use progressive disclosure for optional snapshot annotations.
4. Keep review, duplicate and import warnings actionable and readable, including empty, loading, error, disabled, undo and confirmation states.
5. Ensure keyboard reachability, visible focus, semantic labels and readable contrast; avoid relying on color or hover to convey meaning.

These are UX changes, not permission to introduce budgets, forecasts, new financial calculations, data migrations, or external integrations.

## Loop execution contract

**Operating context:** work in the existing `redesign/joyufy` checkout visible in the user's IDE. The checkout contains uncommitted redesign work: inspect and preserve it, build on it where appropriate, and do not reset, stash, overwrite unrelated edits or change `main`. Never commit or push without fresh explicit user permission. The loop may make ordinary implementation and design decisions autonomously and must not pause for routine approval.

**Iteration:** inspect the affected existing behavior and reference, select the next incomplete milestone, implement a coherent slice, run its focused checks, and review the result against this contract. Record completed work, commands/results, remaining gaps and the next action in a durable local progress note so a subsequent iteration can resume. Fix failures and continue across milestones without waiting for approval. After three unsuccessful attempts at the same blocker, record the evidence and stop for that blocker rather than spinning. Do not declare completion while requirements or meaningful checks remain outstanding.

**Milestones and completion checks:**

1. **Baseline and design system.** Inventory routes, workflows, current changes, theme selection and test commands; map each screen and dialog to the milestones below. Establish shared Material 3 light/dark schemes, typography, shapes and reusable controls. Verify theme switching, readable contrast, keyboard focus and adaptive shell at wide and narrow desktop widths.
2. **Overview and analysis.** Redesign the shell, overview, wealth chart, account breakdown and attention states. Implement the monthly category detail and previous-month comparison described above; retain annual analysis and existing chart modes and controls. Verify month/year boundaries, empty months and transfer exclusions against real data fixtures.
3. **Accounts and entry.** Redesign bank, cash and investment detail, transaction entry and snapshot flows. Verify global and account-context entry, type buttons, custom categories, date entry/picker, transfers, editing, deletion, undo and snapshot annotations.
4. **Ledger, import and rules.** Redesign transactions, review, filtering, bulk operations, import and categorization rules. Verify invalid and duplicate import rows, draft review, paired transfers and current rule behavior with isolated fixtures.
5. **Settings and data.** Redesign every settings section and associated dialogs while preserving preferences, account management, backup/restore, cloud operations, data quality and existing confirmations. Verify restore IDs and relationships and exercise destructive previews using isolated data only.
6. **Integration and polish.** Traverse every screen and dialog in light and dark, English and Spanish, at wide and narrow desktop sizes. Exercise keyboard navigation and focus, empty/loading/error/confirmation states and key workflows. Resolve visual inconsistencies and regressions; check off the acceptance matrix below and report any verification that could not be performed.

**Verification:** run focused tests for changed behavior as each milestone lands; use in-memory or temporary isolated databases and never the user's live financial data. Run `./gradlew :composeApp:desktopTest` from the repository root for integrated desktop tests and `./gradlew :composeApp:compileKotlinDesktop` for compilation. If environment dependencies prevent a check, diagnose and document the exact failure and any alternative evidence; do not represent it as passed. Inspect the running app where the environment supports it. New behavior warrants meaningful tests at the highest practical existing seam, especially financial calculations, transfer pairs, restore IDs, import duplicates and review metadata.

**Acceptance matrix:**

| Outcome | Evidence required |
| --- | --- |
| Material 3 redesign across the whole app, coordinated light and dark | Walk all routes and dialogs in both themes; inspect wide/narrow layout, contrast, selection, focus and states. |
| Existing functionality and financial semantics preserved | Exercise the scenarios below and run affected desktop tests; verify no transfer double-counting or data loss in isolated fixtures. |
| Monthly analysis and improved transaction entry complete | Exercise month navigation across a year boundary and empty months; verify category totals and transfer exclusions; exercise global/account entry, type, category and date controls. |
| Backup, import, rules, settings and cloud flows retained | Verify each existing route and confirmation; test data changes with isolated fixtures, including restore relationships and duplicate imports. |
| Accessible, localized desktop experience | Navigate with keyboard in English and Spanish, both themes and narrow/wide windows; verify labels, focus, error feedback and readable text. |
| Delivery ready | Compile and run relevant desktop tests; record actual manual checks, failures and remaining gaps. |

**Stop conditions:** stop only when all acceptance criteria and applicable checks pass, a genuinely blocking product or technical decision cannot be resolved from this contract, or the same blocker has resisted three attempts. Never treat this document as authorization to commit or push. Report a blocker or incomplete verification explicitly; do not call the redesign complete on the strength of the HTML prototype alone. Creating this document does not start or schedule the loop.

## Suggested evaluation scenarios

- New user creates each account type and sees sensible empty states.
- User creates, edits and deletes a transfer; both legs and undo remain consistent.
- User imports a file with valid, invalid and duplicate rows; only valid nonduplicates are committed as drafts.
- User updates an investment with annotations and sees contribution-adjusted metrics and a stale-snapshot task resolve.
- User opens monthly analysis, switches across a year boundary, checks category totals and compares against a prior month with and without data. Transfer legs do not inflate expenses.
- User opens transaction entry from an account and globally, switches types, selects a category or types a custom one, and chooses a date.
- User filters and bulk-reviews ledger rows, performs a transfer-aware delete and undoes it.
- User exports a backup, previews a restore and restores IDs and relationships in an isolated test database.
- User navigates all settings, checks cloud state, and previews destructive actions without executing them.
- User operates key workflows in Spanish and English, dark and light, with keyboard and narrow desktop windows.
