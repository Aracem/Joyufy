# Material redesign progress

Branch: `redesign/joyufy`. Preserve the existing uncommitted work; do not touch `main`.

## In progress

- Baseline inventory started. Existing screens include dashboard, account detail, transaction entry, ledger, import, categorization rules and settings. Existing transaction entry already has type chips, category suggestions with free text, and a date picker with manual input; these still need full flow verification.
- Introduced coordinated Material light/dark palettes, semantic tonal surface tokens, rounder shapes, sidebar surface, and initial tonal dashboard sections. Updated transaction-entry selection and focus colors to use the active theme.
- `:composeApp:compileKotlinDesktop` passed after the initial palette update; `:composeApp:desktopTest` passed after the transaction and dashboard changes (Gradle wrapper workflow 8f4b7249f07cdafbb35525d31ba68c6d; wrapper logs cleaned). Existing deprecation warnings remain. No visual or manual workflow verification yet.

- Added a monthly spending entry card and detail dialog backed by active bank/cash transactions. It supports previous/next month, year boundaries, all categories including uncategorized, previous-month values and empty or unavailable comparison states. Transfers are excluded. Five focused tests cover the calculation, including year and calendar boundaries. `:composeApp:desktopTest` passed after those changes; `:composeApp:compileKotlinDesktop` passed after adding the tonal wealth hero (workflow 76d7fb1df50d56025de65d1d3ef36ac1, logs cleaned).

- Updated navigation, account detail, ledger, dashboard and settings to use theme-aware primary and positive/negative semantic colors, including matching destructive-button foregrounds. Checked representative token pair contrast mathematically (all sampled ratios >= 4.5:1); visual and full contrast audit remain.
- Made sidebar quick-add visible without hovering, added compact ledger rows and wrapping filter controls, and localized categorization rules and language controls. These changes need manual keyboard/narrow-window validation.
- Transaction entry displays its source account, uses its real account ID for matching scoped categorization rules, scrolls when space is constrained, and converts Material date picker values as UTC calendar dates. Two date conversion tests pass.
- `:composeApp:desktopTest` passed after these changes and the responsive ledger/dashboard updates (workflow 3064270b72b9e863a3873a581bce8c14; wrapper logs cleaned). A compilation error in an intermediate ledger edit was corrected and subsequent compilation and tests passed. No manual UI validation yet.

- Applied Material theme roles to account creation and snapshot dialogs, chart lines and tooltip changes, import review, restore actions and status banners; account dialogs now constrain their height and scroll. Localized update notices and chart empty states.
- `:composeApp:desktopTest` passed after these changes (workflow c90aa772113df8e49283aed80e97af9a; wrapper logs cleaned). Existing compiler deprecation warnings remain.
- Launched `:composeApp:run` with a temporary `user.home`, pre-seeded empty preferences and disabled cloud autosync; an isolated database and a window were created. macOS denied System Events accessibility access (`-1728`) and window capture returned `could not create image from window`, so no screenshot, interactive workflow or visual inspection was obtained. The test process was terminated deliberately; Gradle reported exit 143 for that reason. No personal database was opened.

## Remaining

- Complete the screen-by-screen inventory and redesign, including responsive layouts, interactive states and all dialogs.
- Manually verify monthly detail and its placement in both themes, with narrow windows and keyboard; retain annual analysis.
- Verify transaction, snapshot, ledger, rules, import, backup/restore, settings and cloud workflows with isolated data. Add meaningful tests for new behavior.
- Run integrated desktop compilation/tests and manually inspect both themes, languages, keyboard and window sizes. Record actual results and gaps.

## Next iteration

- Continue the screen-by-screen Material redesign and use isolated fixtures for workflow verification. Visual and keyboard verification needs a permitted desktop inspection mechanism; the local app can be run with an isolated home as documented above.

## Resumption check — 2026-11-12

- Desktop compilation and tests passed together via Gradle workflow `350493138a146c461f7b58647e38fdaf` before the subsequent edits; wrapper-owned logs were cleaned. Existing deprecation warnings remain.
- The monthly overview card now shows a loading indicator instead of fabricated zero values while its summary is unavailable. This small follow-up has not yet been compiled.
- Additional changes appeared in `App.kt`, `AccountCard.kt`, and `CsvImportDialog.kt` during this session, outside this iteration. Preserved them; inspect and coordinate the active writer before further edits or validation. The acceptance matrix and manual checks above remain incomplete.

## Monthly state and money validation — 2026-11-12

- Fixed the overview's indefinite loading state for a month with no activity. The monthly card now distinguishes loading, an empty month and a load failure; the detail also displays a failure message instead of spinning indefinitely. UI behavior still needs manual verification.
- Transaction and snapshot entry and account creation now reject non-finite numbers. CSV import also rejects overflowing amounts before commit. Added parser tests and an in-memory import regression test that verifies an overflowing row is rejected and no transaction is written.
- `:composeApp:desktopTest` passed after the monthly and entry changes. The focused `:composeApp:desktopTest --tests com.aracem.joyufy.ui.importer.CsvImportViewModelTest` passed after the CSV fix (workflow e58deb1c46457a52fa88e863618e8965, wrapper logs cleaned). Existing deprecation warnings remain.
- Prior note about concurrent edits referred to changes visible during resumption; no child agent is active in this thread. All existing changes remain preserved. Full screen-by-screen, visual, accessibility and workflow acceptance remain open.

## Offscreen monthly UI verification — 2026-11-12

- Added `MonthlyCardUiTest` using `ImageComposeScene` with controlled data, no live database or desktop permissions. Four tests pass: empty month and open action in both themes; loading/error without fabricated amounts; previous-month action with future navigation disabled; and Tab/Enter focus and activation.
- Rendered and inspected temporary 480px-wide light and dark images of the monthly card and a sample detail dialog. Text and category rows were visible in those fixtures; these are not full-application screenshots and do not verify all data, languages, resizing, focus restoration or interaction states. Temporary rendering code and images were removed after inspection.
- Focused `:composeApp:desktopTest --tests com.aracem.joyufy.ui.dashboard.MonthlyCardUiTest` passed all four tests (workflow 3bd71e8b9bfa5d3f5cd8573ff37cf2b5; wrapper logs cleaned). Existing macOS automation and window-capture restrictions still prevent full-app manual inspection in this session.

## Icon, responsive summary and snapshot details — 2026-11-12

- Replaced desktop app icons with a face-only, stylized wire-haired dachshund on the Material blue/lilac tile, per user direction. `icon.svg` is the source; generated PNG, multi-resolution ICO (16–256 px) and ICNS. Inspected the source render and 32 px raster; platform packaging and Dock appearance remain to be checked.
- Monthly summary values stack below 380 dp to reduce clipping in narrow content areas. The existing monthly UI test passed after an initial test-worker EOF; the retry passed. Visual inspection of the new narrow layout remains open.
- Snapshot annotations are progressively disclosed. Existing annotations and inferred flows open the section automatically; invalid optional values reopen it and display a section error. A Compose UI test verifies inferred deposits survive collapsing and saving.
- Gradle workflow `b943217e63891a3ab99950cf4fe28f99`: desktop compilation and full desktop tests passed after the source changes; focused snapshot and monthly UI tests passed. Existing deprecation warnings remain. The icon was regenerated after the full run; the SVG, PNG, ICO and ICNS were inspected for format and size but installer packaging was not run.
- Remaining: complete the screen/dialog inventory, isolated workflow exercise, both-language and both-theme accessibility, focus and narrow/wide visual acceptance. Desktop window capture and accessibility permission failures documented above still limit full-app manual verification.

## Icon refinement from Joyu reference — 2026-11-12

- Refined the app icon after user feedback: face only, based on the original dark tile and white line-art treatment, with Joyu's floppy dark ears, salt-and-pepper coat marks, tan brows and long tan wire-haired muzzle/beard. No photo or generated bitmap; `icon.svg` is the hand-drawn source.
- Regenerated `icon.png` (1024 px), multi-resolution `icon.ico` (16–256 px) and `icon.icns`; inspected the full-size and 32 px renders. Installer and live Dock verification are still pending.
- Follow-up design direction from the user's Joyu photo: replaced the front-only portrait with the preferred original-style J plus right-facing profile, using warm eyebrow/muzzle/beard accents. The earlier face-only version is superseded. All platform raster assets have been regenerated from the updated SVG.

## User-provided Joyu artwork crop — 2026-11-12

- User clarified to use their supplied illustrated J + Joyu artwork directly, square it, and remove the JOYU wordmark. Replaced the desktop icon with a square crop that keeps the full J and portrait and excludes the lower lettering. The source image is embedded in `icon.svg`; generated 1024px PNG, multi-resolution Windows ICO and macOS ICNS.
- Inspected the rendered square PNG; `git diff --check` passes and all icon file formats were identified. Installer/Dock verification remains pending. The separate hand-drawn icon drafts were removed; the live app uses the supplied artwork crop.

## Categorization rules and packaging follow-up — 2026-11-12

- Updated the standalone categorization-rules route to use a centered, scrollable Material surface, tonal rule cards, a clear empty state, disabled blank submissions, and visible progress while applying. Rule matching and persistence semantics are unchanged.
- `:composeApp:compileKotlinDesktop :composeApp:desktopTest` passed (workflow `2a12728c3fb96a0c5117bfcb7ff8992f`). Existing Kotlin deprecation warnings remain. `git diff --check` passed before the run.
- Extended the in-memory backup restore fixture with a paired transfer and assertions for both restored `relatedAccountId` links. Focused test `:composeApp:desktopTest --tests com.aracem.joyufy.data.repository.JoyufyDataIntegrityTest.backupImportPreservesIdsAndRemovesExistingRows` passed (workflow `ddc0551a7d10c2b26526dc875b9cd45c`).
- Latest icon packaging evidence: `:composeApp:createDistributable` passed and the generated `Joyufy.app` includes a valid `Contents/Resources/Joyufy.icns`. `:composeApp:packageDmg` failed twice in an external packaging step; the wrapper returned only “External tool execution failed”, with no actionable diagnostic. DMG creation is still unverified.
- The current icon source is the user's supplied square J + Joyu artwork crop with the JOYU wordmark removed; this supersedes the earlier hand-drawn icon entries above. PNG, ICNS, and ICO assets are regenerated. Live Dock appearance remains unverified.
- Full manual app inspection remains blocked by the recorded macOS accessibility/window-capture failure. Remaining acceptance work includes walkthroughs for account/ledger/rules/import/backup/restore/cloud/settings workflows, English/Spanish and both themes, and narrow/wide keyboard-focused interaction. Do not treat the redesign as complete.
- Next: continue isolated workflow verification and add targeted tests at the highest practical seam for unverified data/restore and rule flows; retry packaging only after identifying a concrete external-tool cause. Never use live financial data.

## macOS desktop inspection resumed — 2026-11-12

- Confirmed the current inspection process can automate System Events and macOS reports screen capture access as available. The previous capture failure was caused by the launched Compose JVM using its default `user.home`; `./gradlew -Duser.home=...` changed only the Gradle process.
- Added no repository launcher/configuration changes for this. A temporary Gradle init script sets `user.home` on the `:composeApp:run` `JavaExec` task and prints the effective app property before launch. The run output confirmed `/tmp/joyufy-inspection.hay9Ie`; its preference file disables Drive auto-sync. The isolated database started empty.
- Caution: the first retry before adding the init script opened the live dashboard. The process was stopped immediately before any data-entry or destructive UI action. Source inspection confirms launch-time Drive behavior downloads and compares a backup, then asks before applying; close-time upload did not run because the process was force-stopped. No local financial-data changes are known. The screenshot was removed. Continue to use the JavaExec init-script override for all desktop inspections.
- In the isolated app, inspected the empty dashboard in dark and light themes, General settings in English and Spanish, Data settings in Spanish, and categorization rules in light/Spanish at wide and 1100×800 windows. The first rules screenshot exposed that `fillMaxSize().widthIn(max = 960.dp)` bypassed the intended width cap. Changed this to `fillMaxHeight().widthIn(max = 960.dp)`; the retest showed the rules content centered at the intended max width and reflowing without clipping at 1100×800.
- `:composeApp:compileKotlinDesktop :composeApp:desktopTest` passed after the width fix (workflow `4245e67ef129854ca8a7fa3a21be7498`). Kotlin deprecation warnings remain. The isolated `:composeApp:run` also exited successfully after closing the app normally.
- Still outstanding: inspect remaining routes and dialogs in both themes/languages, exercise workflows with isolated fixtures, and verify focus behavior across the wider app. This visual pass covered empty states only; it does not complete the acceptance matrix.

## Isolated workflow and category localization — 2026-11-12

- Resumed the app with the verified Gradle `JavaExec` override and the disposable home `/tmp/joyufy-workflow-check.RIdZDC`. The temporary account and expense remained isolated. Inspected the wide dark account detail and dashboard screenshots; no live financial database was opened. The app exited normally with `:composeApp:run` status 0 (workflow `2b29650c469e4bc6aea6b5c7e5652e31`).
- Category suggestion labels appeared in Spanish in English mode. Added English/Spanish display labels for built-in categories and a shared display mapper used by transaction entry, account history, monthly analysis and the global ledger. Stored category values, filtering and rule matching remain canonical and unchanged; custom categories remain free-form. Added UI coverage for selecting a localized suggestion and preserving the canonical stored value, alongside the existing free-text case.
- The wide dashboard screenshot showed negative net worth in neutral text. It now uses the negative semantic color while retaining the existing positive-balance easter egg.
- Focused `TransactionDialogUiTest` passed. Full `:composeApp:compileKotlinDesktop :composeApp:desktopTest` passed after the category-localization and balance-color changes (workflow `2b29650c469e4bc6aea6b5c7e5652e31`). Existing kotlinx-datetime and Material API deprecation warnings remain. An intermediate test compile failure was caused by an assertion calling `.firstOrNull()` on a single semantics node; corrected, then the focused suite passed.
- `git diff --check` passed. The desktop screenshots were temporary inspection artifacts and are not retained.
- Remaining: full screen/dialog traversal in both themes and languages, narrow/wide keyboard and focus checks, and isolated end-to-end account/transfer/snapshot/import/backup/restore/cloud workflows. The manual click attempt did not navigate to monthly analysis; the automated monthly Compose tests cover empty/loading/error and month navigation, but they do not replace the route-level desktop check. Packaging DMG diagnostics and live Dock appearance also remain open; `createDistributable` and packaged ICNS were already verified in the earlier entry.


## Settings visual follow-up — 2026-11-13

- Resumed the existing `:composeApp:run` session using the disposable home `/tmp/joyufy-workflow-check.RIdZDC`; no live financial data was opened. The account list still contains the temporary `Workflow Check Bank` fixture. No account was created, deleted, or otherwise changed during this pass.
- Visually inspected Settings → Data, Cloud, About, and General in the running desktop app. Data shows backup export/import, bank statement import, categorization rules, and investment-flow backfill; backfill correctly reports no changes and disables Preview for this fixture. Cloud correctly reports disconnected/ready/manual-sync status and offers Connect Google Drive. No OAuth connection or sync was started. About displays `Joyufy v1.4.0`; source inspection confirms it currently provides the version easter egg only, with no About-specific update-check action. General language controls switched successfully to Spanish and the screen localized.
- A click-coordinate mismatch opened the blank New account dialog while navigating away from Settings. It was left empty and unsubmitted; the app was closed with Cmd+Q. No data mutation resulted. The theme toggle did not visibly change during this pass, so the Settings-level theme switch remains unverified here.
- The existing Gradle workflow `1aaef5c7a8d089a6e572ce6542832896` completed successfully after the app closed: `:composeApp:run` exited 0, and its init script confirmed the isolated `user.home`.
- Screenshots were temporary under `/tmp/joyufy-*.png`; they are removed after this note. No source implementation changed in this iteration.

## Next iteration

- Continue the isolated route walkthrough and verify Settings theme switching through a more reliable control path. Cover backup export/import preview, statement import, and the categorization-rules route/dialogs; exercise restore and financial mutations only against the disposable database. Then return to account/ledger/dialog workflows and narrow-window keyboard/focus checks.
- Recheck the monthly previous/next interaction in the running app. The month calculations and boundary behavior have focused automated coverage, but a desktop route-level interaction remains outstanding.
- The About screen currently exposes only the version label and its existing easter egg. Confirm whether there is an existing update-check action elsewhere before deciding whether any change is needed; do not invent an update service.
- Keep the redesign incomplete until the full acceptance matrix is adequately verified. DMG packaging remains unverified after two non-actionable external-tool failures; `createDistributable` and the packaged ICNS were already verified.

## Chart regression and resumed desktop inspection — 2026-11-13

- Fixed negative-only wealth chart scaling so zero stays visible and the y-axis remains ascending. Compact negative labels retain their sign and magnitude. Added `WealthChartRangeTest` coverage for negative-only, mixed, and positive ranges plus compact negative labels. Focused chart tests and the integrated `:composeApp:compileKotlinDesktop :composeApp:desktopTest` run passed; existing deprecation warnings remain.
- Confirmed the corrected negative history visually in the isolated desktop app: axis labels descend from `0` to about `-42` without duplicated values. Reviewed the Spanish transaction ledger, account detail, monthly comparison, and current-month card against the disposable bank fixture. Monthly previous/next behavior and the unavailable prior-month state remain covered by calculation/UI tests; route-level monthly navigation is still open.
- The previously active isolated app run closed normally (`:composeApp:run` exit 0, workflow `639b0dbefe46cf20536e10bebc19d190`). A fresh isolated run also closed normally (exit 0, workflow `61f75123fa24816acd3b2fe834365a13`). `user.home` was confirmed as `/tmp/joyufy-workflow-check.RIdZDC`; no live financial database was opened and no financial data was changed.
- In the fresh run, inspected the wide dashboard in Spanish in both dark and light themes. The chart, composition, comparison, monthly card, and review summary remained visible. Tab displayed a visible focus indicator on the sidebar brand. Automation did not reliably activate the settings/rules route, so rule application was not exercised in this pass.
- `git diff --check` passed before this continuation. No source changes were made in this continuation; the chart fix and test are already in the working tree from the prior iteration.
- Still open: narrow-window and keyboard traversal, the rules apply flow, account creation/type changes, transfer edit/delete/undo, investment snapshot flows, statement import with valid/invalid/duplicate drafts, backup export/restore preview, destructive-action previews, and full light/dark English/Spanish walkthrough. The acceptance matrix remains incomplete. Do not mark the redesign complete.

## Next iteration

- Resume on the existing `redesign/joyufy` checkout. Use only the disposable home `/tmp/joyufy-workflow-check.RIdZDC` for desktop runs and preserve the existing fixture. First investigate reliable foreground route navigation and narrow-window resizing, then continue the isolated workflow matrix (rules, imports, transfers, snapshots, backup/restore previews).
- Run the integrated desktop checks after any further source changes. Keep DMG packaging and Dock appearance as open delivery checks; retry packaging only with a concrete diagnosis for the external-tool failure.

## Narrow dashboard inspection — 2026-11-13

- Resized the isolated desktop window to approximately 1100×800 and inspected the light Spanish dashboard. The wealth card and chart controls reflowed within the narrower width; remaining dashboard sections continue below in the vertical layout. No visible clipping appeared in the chart/card area. Tab focus was visibly indicated in the preceding wide run.
- The isolated `:composeApp:run` exited 0 after Cmd+Q (workflow `f78de9c72138e03f50ac85d9c1c6cf7f`). No financial fixture rows were changed.
- Settings/rules and deep workflow checks remain unverified in this continuation. Continue the acceptance matrix; this narrow dashboard view alone does not establish responsive behavior for ledger tables or dialogs.

## Import categorization rule regression — 2026-11-13

- Added a `CsvImportViewModelTest` using an isolated temporary preferences directory and in-memory SQLDelight repositories. It verifies a case-insensitive saved rule fills a blank matching import category while a category supplied by the statement is preserved.
- Focused test `:composeApp:desktopTest --tests com.aracem.joyufy.ui.importer.CsvImportViewModelTest.categorizationRulesFillOnlyBlankImportedCategories --no-configuration-cache` passed. Integrated `:composeApp:compileKotlinDesktop :composeApp:desktopTest --no-configuration-cache` also passed (workflow `86389e34ecb3570f182a3d6feae1cdf8`). Gradle reports existing deprecated-feature warnings; no task failures.
- `git diff --check` passed before the run. Workflow logs were removed by `gradle-workflow finish`.
- Still open: route-level categorization rule creation/application/deletion, import preview and duplicate review, account creation and account-family type changes, transfer edit/delete/undo, investment snapshot annotation and undo, backup export/restore preview, settings destructive previews, ledger narrow-width behavior, and full keyboard/localization/theme walkthrough. The design acceptance criteria remain incomplete.

## Settings keyboard navigation follow-up — 2026-11-13

- Tried opening Settings from the isolated dashboard by cycling keyboard focus and by coordinate clicks. The app window was visible but the automation did not move the route; this leaves route-level keyboard navigation unverified. No fixture rows changed. The run closed normally with `:composeApp:run` exit 0 (workflow `93cfdd99eb85636540047903b3db76a6`).
- Continue the acceptance matrix by improving foreground/focus control or by adding a route-level Compose test harness. Do not mark accessibility or route acceptance complete based only on the dashboard Tab indicator.

## Categorization rule application test seam — 2026-11-13

- Extracted the categorization-rule application operation used by the rules screen into `CategorizationRuleApplier.kt` so it can be exercised against the existing repository layer.
- Added an in-memory SQLDelight test proving matching uncategorized transactions are updated, the first matching rule wins, nonmatching transactions remain uncategorized, and existing categories are preserved.
- Focused `:composeApp:desktopTest --tests com.aracem.joyufy.ui.settings.CategorizationRuleApplierTest` passed. Integrated `:composeApp:compileKotlinDesktop :composeApp:desktopTest --no-configuration-cache` passed (workflow `24e36abd508b685c5a57f8f599752c85`). Existing Kotlin/Gradle deprecation warnings remain.
- This verifies the rule application data operation; manual route-level creation, deletion, and progress display remain open along with the wider workflow matrix.

## Account creation type regression — 2026-11-13

- Added an in-memory `CreateAccountViewModelTest` that creates Bank, Cash, and Investment accounts. It confirms only Bank/Cash receive their entered opening balance as an income transaction and that Investment creation starts without a transaction or snapshot.
- Focused `:composeApp:desktopTest --tests com.aracem.joyufy.ui.account.CreateAccountViewModelTest` passed. Integrated `:composeApp:compileKotlinDesktop :composeApp:desktopTest --no-configuration-cache` passed (workflow `435e22539ddb27ef9d789b3dc9dd05ec`). The test's coroutine opt-in warnings were fixed; only the existing Gradle deprecation warning remains.
- Account creation behavior is verified at the ViewModel/repository seam. The Create Account dialog's visual focus and account type interaction still need route-level inspection.

## Transfer deletion and undo regression — 2026-11-13

- Added an in-memory `AccountDetailTransferUndoTest` that deletes one transfer through the account-detail ViewModel, verifies its matching sibling is removed while a separate transfer between the same accounts remains, then undoes the action.
- The undo assertions verify both original IDs, reciprocal account links, `DRAFT` review status, and `importBatch` are restored.
- Focused `:composeApp:desktopTest --tests com.aracem.joyufy.ui.account.AccountDetailTransferUndoTest` passed. Integrated `:composeApp:compileKotlinDesktop :composeApp:desktopTest --no-configuration-cache` passed (workflow `0423f18c5800d3ab0056cf16c9739ebf`). Existing Gradle deprecation warnings remain.
- The account-detail pair delete/undo data path is covered. Manual edit and visual undo snackbar checks remain open.

## Transfer editing regression — 2026-11-13

- Added in-memory account-detail regressions for editing a transfer from the destination account and converting a transfer into an ordinary expense.
- Destination-side editing verifies the source remains an expense, destination remains an income, reciprocal account links are preserved, and amount, category, description, and date update on both legs. Conversion verifies the sibling leg is removed and the edited destination-side expense has no transfer link.
- Focused `:composeApp:desktopTest --tests com.aracem.joyufy.ui.account.AccountDetailTransferUndoTest --no-configuration-cache` passed (Gradle workflow `36adc8e32adc2e4c2d83b4b3a19b4543`). Existing Gradle deprecation warnings remain.
- `:composeApp:compileKotlinDesktop :composeApp:desktopTest --no-configuration-cache` passed (workflow `36adc8e32adc2e4c2d83b4b3a19b4543`); `git diff --check` passed. Existing Gradle deprecation warnings remain.
- Manual account/transfer editing and undo-snackbar traversal remain unverified, along with the rest of the route, localization/theme, accessibility and delivery acceptance matrix.

## Investment snapshot edit and undo regression — 2026-11-13

- Added an in-memory account-detail regression that edits the snapshot value, week, deposits, withdrawals, fees, dividends, and note, then deletes and undoes the deletion.
- The test verifies the update keeps the snapshot ID and undo restores the full edited snapshot with every annotation intact.
- Focused `:composeApp:desktopTest --tests com.aracem.joyufy.ui.account.AccountDetailSnapshotUndoTest --no-configuration-cache` and integrated `:composeApp:compileKotlinDesktop :composeApp:desktopTest --no-configuration-cache` passed (workflow `42ff8a2acbdef4f0e2b541a96b03e245`). `git diff --check` passed; existing Gradle deprecation warnings remain.
- Snapshot dialog annotation entry has existing UI coverage for inferred flows and collapse/save. Manual snapshot edit/delete/undo traversal and the stale-task resolution UI remain to be checked; other route, localization/theme, accessibility and delivery acceptance gaps remain.

## Ledger bulk review and transfer undo regression — 2026-11-13

- Added in-memory ledger ViewModel coverage for bulk marking imported draft transactions as reviewed and for deleting a selected transfer with its sibling leg.
- The transfer test confirms an unrelated pair remains untouched and undo restores original IDs, reciprocal account links, draft status and import batch metadata.
- Focused `:composeApp:desktopTest --tests com.aracem.joyufy.ui.ledger.TransactionLedgerViewModelTest --no-configuration-cache` and integrated `:composeApp:compileKotlinDesktop :composeApp:desktopTest --no-configuration-cache` passed (workflow `4fca030c808f8873338dd3bd40775616`). Existing Gradle deprecation warnings remain; `git diff --check` passed.
- Ledger filtering, compact/narrow layout, and manual route interaction remain to be verified, alongside import preview and the broader accessibility/localization/theme walkthrough.

## Mixed statement import preview regression — 2026-11-13

- Added an isolated importer scenario combining a duplicate of an existing transaction, one invalid date, two repeated statement rows, and one valid unique expense.
- The preview blocks all four invalid/duplicate rows; committing writes only the unique row as a `DRAFT` and leaves the pre-existing duplicate untouched.
- Focused `:composeApp:desktopTest --tests com.aracem.joyufy.ui.importer.CsvImportViewModelTest --no-configuration-cache` and integrated `:composeApp:compileKotlinDesktop :composeApp:desktopTest --no-configuration-cache` passed (workflow `4d1736353fb57afa6eeaebfd50a7f409`). `git diff --check` passed; existing Gradle deprecation warnings remain.
- Import dialog visual/keyboard traversal and file-picker review remain open, along with remaining route and accessibility/theme/localization acceptance checks.

## Account-delete referential integrity — 2026-11-13

- A new in-memory Settings undo regression exposed that the desktop SQLite connection did not enable declared foreign-key constraints. Hard deletion therefore left account-owned transaction rows instead of cascading, and did not clear sibling transfer references as the schema specifies.
- Desktop driver creation now enables `PRAGMA foreign_keys = ON` for both normal and corrupt-database recreation paths. The regression enables the same pragma and verifies account deletion removes owned rows/snapshots and undo restores account, snapshot annotations, transfer pair IDs/links, draft status and import batch.
- Focused `:composeApp:desktopTest --tests com.aracem.joyufy.ui.settings.SettingsAccountDeleteUndoTest --no-configuration-cache` and integrated `:composeApp:compileKotlinDesktop :composeApp:desktopTest --no-configuration-cache` passed (workflow `df27b7eefe1ee246e30ad301f8407939`). `git diff --check` passed; existing Kotlin and Gradle deprecation warnings remain.
- Settings delete/archive confirmation and undo UI, backup preview, cloud, and the remaining route/accessibility/localization/theme checks still need verification.

## Isolated settings, restore preview and narrow ledger walkthrough — 2026-11-13

- Launched the desktop app with `/tmp/joyufy-workflow.init.gradle`; the app reported the disposable `user.home` `/tmp/joyufy-workflow-check.RIdZDC`. The run exited normally (status 0) after Cmd+Q (workflow `546d2e1ec7e2cb6f085ce9ae39ba7aa4`). The temporary backup preview file and screenshots were removed. The disposable financial fixture was not restored or otherwise mutated; theme/language were returned to Spanish/light and Drive auto-sync remained disabled.
- Visually inspected the dashboard in Spanish/light; Settings → Data, Accounts, Cloud, About; the categorization rules route; and the global ledger in English/dark at a resized 1100×800-point window. Ledger filters, review inbox and both fixture rows fit without horizontal clipping at that width. The dashboard chart rendered negative wealth with an ascending zero-inclusive axis.
- Opened and canceled the delete-all confirmation, account-delete confirmation, and local backup restore preview. The restore preview accurately showed +1/-1 account, -2 transactions, and +1 investment snapshot for a temporary JSON fixture. No destructive action was confirmed. Inspected the cloud disconnected/ready/manual-sync state; no OAuth or sync was started. Settings appearance and language switches visibly updated the interface. Tab showed a focus highlight on dashboard content, but complete keyboard traversal remains unverified.
- Manual checks still outstanding: transaction/snapshot create-edit-delete-and-undo interactions, statement import preview in the dialog, full English/Spanish × light/dark traversal of all routes, complete keyboard/focus review, and package DMG/Dock verification. The app walkthrough is partial, so redesign acceptance is not complete.

## Chart axis precision and account/month walkthrough — 2026-11-13

- A disposable investment snapshot exposed repeated `1k` labels on a narrow positive chart range. Axis labels now derive precision from the tick interval. Added regressions for large balances with small differences and a single positive snapshot; an initial precision rule still produced duplicates, so it was tightened to preserve distinct ticks.
- Focused `WealthChartRangeTest` and integrated `:composeApp:compileKotlinDesktop :composeApp:desktopTest --no-configuration-cache` passed (workflow `5cf7887929553170216a8e5bf6580c90`). A final isolated Spanish/light visual pass showed distinct labels on both the dashboard and one-snapshot account chart.
- Visually inspected account creation, the investment empty state, snapshot creation and annotation disclosure, snapshot edit with deposit/note annotations, delete confirmation and the snackbar undo. The confirmation currently says the snapshot deletion cannot be undone even though the snackbar provides Undo; this copy mismatch is a follow-up fix. Manual transfer destination selection showed the only other account, but transfer persistence was not verified from this walkthrough.
- Opened monthly analysis from the dashboard. November 2026 was identified as incomplete, showed the October comparison, and blocked forward navigation; October showed no earlier comparison for the available fixture. Cross-year route navigation is still not manually exercised; calculation and UI tests cover year boundaries.
- The app closed normally (Gradle `:composeApp:run` exit 0). The disposable database was returned to one account, two original transactions and zero investment snapshots. Wrapper-owned logs were removed; local screenshots were removed. No live data was opened.
- Still incomplete: rule apply flow, entry commit/edit for transactions and transfers, statement import dialog, backup export, full route walkthrough across both languages/themes, keyboard traversal, narrow dialog/ledger checks, and DMG/Dock verification. Do not mark acceptance complete.

## Snapshot delete confirmation copy — 2026-11-13

- Updated the English and Spanish snapshot confirmation text to explain that the next notification provides Undo, matching the existing undo snackbar and snapshot restoration behavior.
- `:composeApp:compileKotlinDesktop :composeApp:desktopTest --no-configuration-cache` passed (workflow `86301f433bfea7fcad354546a276bc4d`); existing Kotlin and Gradle deprecation warnings remain. Visual dialog wording was observed before the change; the revised copy is compile-verified but not reopened in the desktop app.

## Isolated categorization rule walkthrough and transaction delete copy — 2026-11-13

- Resumed the isolated desktop app with `/tmp/joyufy-workflow-check.RIdZDC`. In Settings → Data → Categorization rules, removed the temporary `Rule Walkthrough → Food` rule; the remaining `octoberfixture → Groceries` rule is preserved. The new transaction flow had already verified that a blank-category purchase matching the temporary rule received `Food`; the temporary transaction was deleted and its Undo snackbar observed.
- The app exited normally; isolated `:composeApp:run` finished with exit 0 (workflow `333898727588c9c03fbdf778c17a607f`). The disposable SQLite database was checked read-only and contains the original one account, two transactions, and zero investment snapshots. Temporary categorization preferences were confirmed removed. Wrapper logs were cleaned with `gradle-workflow finish`.
- Updated English and Spanish transaction-delete confirmation copy to match the available Undo notification, as was done for snapshot deletion. Integrated `:composeApp:compileKotlinDesktop :composeApp:desktopTest --no-configuration-cache` passed (workflow `aba7ef5cbcf77ce8fcce682f33ffc970`); only existing Kotlin and Gradle deprecation warnings remain. `git diff --check` passed.
- Still open: statement import dialog/file picker, backup export, successful transfer UI persistence/edit/delete/undo, complete English/Spanish × light/dark route traversal, keyboard/focus restoration, narrow dialogs/ledger, cross-year monthly UI navigation, and DMG/Dock verification. Acceptance remains incomplete.

## Import/export walkthrough and desktop foreign-key connection fix — 2026-11-13

- In the isolated Spanish/light desktop app, the statement file picker opened from Settings → Data. A temporary ING text export parsed to one valid row, zero errors and zero duplicates; the preview was canceled without import. Backup export succeeded to `/tmp/joyufy_backup.json`; JSON counts matched the disposable fixture (one account, two transactions, zero snapshots).
- Created a temporary Cash account and transfer through the UI. The transfer persisted both reciprocal legs; the source balance changed by €8.50 and the destination balance increased by €8.50. Account deletion and its Undo notification also worked, and Undo restored the same account and transfer IDs.
- Deleting the temporary Cash account left both transfer legs in the disposable database. Root cause: SQLDelight's file-backed JDBC driver opens thread-local connections, while the prior `PRAGMA foreign_keys=ON` ran only on startup's connection. The factory now supplies SQLite's `foreign_keys=true` connection property so each worker-thread connection enforces the schema.
- Added `DatabaseDriverFactoryTest`, which creates a disposable file database through the production factory, inserts a reciprocal transfer and deletes one account through IO-dispatched repositories. It verifies the account-owned leg is cascaded and the surviving leg has its related-account reference cleared. Focused `:composeApp:desktopTest --tests com.aracem.joyufy.data.db.DatabaseDriverFactoryTest --no-configuration-cache` passed (workflow `bfc07c109dd334bf5da617066bbb44d6`); existing Kotlin/Gradle deprecation warnings remain. `git diff --check` passed.
- After the UI run exited normally (workflow `bbf28df0e19c7a260eee9cde61ef59c8`, exit 0), removed only the two named test-created transfer rows from the disposable database and verified it is again one account, two original transactions and zero snapshots. The backup and CSV preview are temporary files outside the repository.
- Pending: retest account deletion in the UI with the worker-thread fix; transaction edit/delete/undo UI, backup restore execution, rules retroactive apply UI, complete language/theme/keyboard/narrow-route walkthrough, cross-year month UI navigation, and DMG/Dock. The account-delete confirmation currently claims the deletion is permanent despite its Undo snackbar and needs corrected copy. Acceptance remains incomplete.

## Account-delete Undo copy and retest attempt — 2026-11-13

- Updated English and Spanish account-delete confirmation copy to explain that the deletion can be undone from the following notification, matching the Settings undo behavior.
- Retried the isolated desktop run with the corrected per-connection foreign-key setting and `/tmp/joyufy-workflow-check.RIdZDC` as `user.home`. The app process started with the correct isolated home, but System Events exposed no Java windows and a full-screen capture was black. Stopped the run through the Gradle workflow wrapper (exit 130); this is an environment/UI visibility limitation, not a Gradle task failure. No delete action was attempted.
- Read-only fixture counts after the run remain 1 account, 2 transactions, and 0 investment snapshots. The UI retest of account deletion remains outstanding; its data-path regression test and prior UI Undo flow are recorded above.
- Continue with acceptance evidence obtainable through isolated tests and source/UI tests while retrying desktop inspection only when a usable window is available. Other open gaps: transaction and transfer UI edit/delete/undo, restore execution, retroactive rules UI, month navigation across years, full bilingual/theme/keyboard walkthrough, narrow dialogs/ledger, DMG and Dock appearance. Do not mark acceptance complete.

## Narrow import preview UI regression — 2026-11-13

- Added Compose semantics coverage at 420×700 for the bank-statement preview in English/dark and Spanish/light. It checks localized row/error/duplicate/valid counts, blocks commit when no rows are valid, and exercises cancel and valid commit actions.
- Focused `:composeApp:desktopTest --tests com.aracem.joyufy.ui.importer.CsvImportDialogUiTest --no-configuration-cache` passed. Integrated `:composeApp:compileKotlinDesktop :composeApp:desktopTest --no-configuration-cache` passed (workflow `2db7bb0cb9baeb9b49b13fd7da562848`); only the existing Gradle deprecation warning remains. `git diff --check` passed before the run. Wrapper logs were removed with `gradle-workflow finish`.
- This headless UI test verifies semantics and actions, not pixel-level clipping; app-wide screen inspection remains unavailable while macOS returns a black capture and no accessible windows. Restore execution and relationship integrity, CSV parsing/duplicates, rules application, transaction and transfer edit/delete/undo data paths, and monthly boundary calculations have existing tests. Route-level rules, entry workflows, restore preview/apply, narrow ledger/dialog visuals, full bilingual/theme/keyboard traversal, DMG and Dock appearance remain open. Do not mark redesign acceptance complete.

## Transaction edit dialog regression — 2026-11-13

- Added Compose semantics coverage for edit mode: the existing amount, date and description are prefilled; saving changed values preserves the expense type and stored category while submitting the new date and description.
- Focused `:composeApp:desktopTest --tests com.aracem.joyufy.ui.account.TransactionDialogUiTest --no-configuration-cache` passed. The first assertion exposed the expected locale-specific decimal comma (`12,50`) and was corrected. Integrated `:composeApp:compileKotlinDesktop :composeApp:desktopTest --no-configuration-cache` passed with both new UI test slices (workflow `64668b3d2481cf8cce3fa2252d117e93`); only the existing Gradle deprecation warning remains. Wrapper logs were cleaned with `gradle-workflow finish`.
- The transaction edit controls are now headlessly exercised, but app-wide visual/keyboard traversal remains open. See the acceptance gaps in the preceding entry; redesign acceptance remains incomplete.
- A follow-up contrast calculation across 20 representative Material and semantic text/container pairs in both schemes ranged from 4.77:1 to 15.83:1. This is a sample token check, not a full rendered contrast audit.
- Push was attempted after commit `cdb2466`, but SSH returned `Permission denied (publickey)`. The configured personal key is rejected, while the available GitHub CLI identity has read-only permission on `Aracem/Joyufy`; commits remain local until write access is available.

## Local restore preview dialog regression — 2026-11-13

- Added headless Compose semantics coverage for the local backup restore confirmation. It verifies the Spanish diff summary shows account, transaction, and snapshot changes, cancel leaves restore untouched, and the restore callback fires only when its explicit confirmation button is clicked. The dialog was exposed as module-internal so the test can render the production composable directly.
- Focused `:composeApp:desktopTest --tests com.aracem.joyufy.ui.LocalImportPreviewDialogUiTest --no-configuration-cache` passed. Integrated `:composeApp:compileKotlinDesktop :composeApp:desktopTest --no-configuration-cache` passed (workflow `b50664f855af6e5ca76eff09c75d57b6`); existing Kotlin/Gradle deprecation warnings remain. Wrapper logs were cleaned with `gradle-workflow finish`.
- This test covers preview semantics and the confirmation boundary without applying a backup. ID/relationship-preserving restore is covered by repository tests; an actual isolated restore via the settings workflow and the rest of the route/theme/language/keyboard and packaging checks remain open. Do not mark acceptance complete.
