---
name: legado-compose-migration
description: Guide Legado Android UI migration from XML/View/RecyclerView/DialogFragment screens to Jetpack Compose and Material 3, and guide new Compose-first screens using standard Android architecture. Use when Codex is asked to create, migrate, rewrite, review, or plan a Legado screen, Activity, Fragment, dialog, adapter, navigation destination, or settings page, especially when MainActivity navigation, MVI/UDF, StateFlow, Koin ViewModels, domain/usecase boundaries, or existing Compose component conventions matter.
---

# Legado Compose Migration

## Overview

Migrate one UI surface at a time, or create one new Compose destination at a time. Preserve behavior first for migrations; for newly created screens, prefer standard modern Android architecture over mixed legacy patterns. Use the project's existing `MainActivity` navigation, `BaseComposeActivity` compatibility hosts, `*Screen`, `*Contract`, `*ViewModel`, `StateFlow`, `SharedFlow`, Koin, theme, and widget patterns.

Before editing, inspect the target View implementation if one exists, `MainActivity` navigation when adding a destination, and at least two nearby migrated Compose screens. For concrete project patterns, read `references/project-patterns.md`.

## Workflow

1. State assumptions and success criteria.
   - Name the exact screen or destination being created/migrated.
   - For migrations, define behavior that must remain unchanged: inputs, result codes, navigation, menu actions, dialogs/sheets, list selection, refresh, persistence, and event bus behavior.
   - For new screens, define the route owner, UI state, events/effects, domain/usecase boundary, and verification target.
   - If the target mixes UI and business logic heavily, keep the migration surgical and defer deeper domain cleanup unless needed.

2. Map the old surface.
   - For migrations, read the Activity/Fragment, XML layouts, adapters, menu XML, dialogs, result launchers, and ViewModel.
   - For new screens, read `MainActivity`, nearby route screens, the relevant ViewModel/usecase/repository patterns, and shared UI components.
   - List UI state, user intents, one-shot effects, and external side effects.
   - Identify reusable Compose components under `ui/widget/components` before creating new components.

3. Choose the minimal migration shape.
   - For new Compose-first screens, add a `MainActivity` navigation destination instead of creating a standalone Activity.
   - Use `BaseComposeActivity` for full-screen Activity migrations only when a legacy Activity must remain as an entry point.
   - Keep existing Activity Result APIs, `Intent` extras, permission flows, and Android framework calls in the host/compatibility Activity.
   - If an unreworked View screen still starts the migrated screen with `Intent`, keep the old Activity only as a compatibility host that parses legacy extras and delegates to the Compose screen or `MainActivity` route boundary.
   - Put renderable state in `UiState`, user actions in `Intent`, and one-shot navigation/framework work in `Effect`.
   - For new screens, use standard Android/Compose architecture: UDF/MVI-style state hoisting, lifecycle-aware Flow collection, ViewModel-owned state, repository/usecase boundaries, and UI free of business logic.
   - Use mixed legacy patterns only as an integration boundary for unreworked View screens or existing framework contracts.
   - Use existing repositories/usecases when they already fit; introduce new domain/usecase classes when a new screen needs clean business boundaries or when a migration would otherwise duplicate or entangle business logic.

4. Implement by layers.
   - Create or update `FeatureContract.kt` first for `UiState`, `Intent`, `Effect`, dialog/sheet models, and menu action enums.
   - Update `FeatureViewModel.kt` to expose `uiState: StateFlow<UiState>` and `effects: SharedFlow<Effect>`, with a single `onIntent(...)` entry point unless the existing feature has a simpler established pattern.
   - Create `FeatureScreen.kt` as a stateless route-level composable: `state`, callbacks, and `onIntent`.
   - Wire new Compose destinations through `MainActivity` route handling; update a retained Activity only when legacy `Intent` compatibility is required.
   - Collect state with `collectAsStateWithLifecycle()` and collect effects in `LaunchedEffect(Unit)` from the route or compatibility host.
   - Register new ViewModels in `di/appModule.kt` with `viewModelOf(::FeatureViewModel)` unless parameters require `viewModel { ... }`.

5. Remove only obsolete migration artifacts.
   - Delete XML layouts, adapters, menu resources, binding fields, and imports only when the migrated screen no longer references them.
   - Do not refactor unrelated View screens or shared utilities.

6. Verify.
   - Prefer the smallest Gradle check that compiles the touched app code, usually `.\gradlew.bat :app:compileAppDebugKotlin`.
   - If resources, manifests, or XML deletion are involved, run `.\gradlew.bat :app:assembleAppDebug` when feasible.
   - For behavior-heavy changes, add or update focused tests only where the project already has a practical test seam.

## Boundaries

- Keep Compose functions side-effect-light. Use `LaunchedEffect` for collecting effects and use callbacks for user actions.
- Do not pass `Activity`, `View`, binding objects, or mutable domain entities deep into composables unless an existing local pattern requires it.
- Prefer project theme/components: `LegadoTheme`, `AppScaffold`, `ListScaffold`, `AppAlertDialog`, `AppModalBottomSheet`, `RoundDropdownMenu`, top bar helpers, setting items, cover components, and list utilities.
- Prefer `StateFlow`/`SharedFlow` over `LiveData` for newly migrated Compose surfaces.
- When using `FeatureIntent` for MVI user actions, distinguish it from Android `Intent` extras and launch APIs in names, comments, and explanations where both appear.
- For new Compose-first screens, do not copy View-era shortcuts such as UI logic in Activity/Fragment, direct binding-like mutable UI state, adapter-owned state, or Activity-context business operations.
- Keep one-off Android actions out of `UiState`: navigation, file opening, clipboard, dialogs implemented as Android DialogFragments, result launchers, permission requests, and callbacks that require host context should be `Effect`s handled by `MainActivity` route handling or a compatibility Activity.
- Prefer `MainActivity` navigation for new Compose destinations. Treat standalone Activities for migrated screens as legacy entry points only when existing View code still depends on `Intent` navigation.
- Keep existing Chinese string resources and localization behavior; add strings to resources when user-facing text is new.

## Reference

Read `references/project-patterns.md` when implementing or reviewing a migration. It contains project-specific examples, file placement rules, state/effect conventions, and verification commands.
