---
name: legado-compose-migration
description: Create or migrate a Legado Android screen with Jetpack Compose while preserving behavior and following the repository's Feature ownership, UDF, navigation, DI, inset, and compatibility boundaries. Use for XML/View/RecyclerView/DialogFragment migrations and new Android Compose destinations. Use legado-compose-review instead for review-only requests, and legado-kmp-migration when the change crosses a multiplatform or Gradle boundary.
---

# Legado Compose Migration

## Purpose

Deliver one working Android UI surface without importing View-era ownership into new Compose code.
For migrations, preserve observable behavior before cleanup. For new screens, use the repository's
current Feature-first and UDF conventions.

Before editing, read `AGENTS.md`, the relevant part of `docs/dev/feature-first-structure.md`, the
current implementation and one or two nearby examples that exercise the same behavior. Read
[references/project-patterns.md](references/project-patterns.md) for repository-specific placement,
state and host details; examples are evidence, not templates to copy blindly.

## Decide the shape

- **New destination:** prefer a `MainActivity` Navigation 3 destination. Do not create a standalone
  Activity without an external-entry or platform lifecycle reason.
- **Migrated destination:** retain an Activity only as a thin compatibility host when existing
  callers require stable Android Intent extras/results or a framework contract.
- **Partial migration:** keep mature View rendering or platform integrations as explicit islands
  when replacing them would add behavioral risk unrelated to the request.
- **Multiplatform boundary:** stop treating this as only an Android screen migration and also use
  `legado-kmp-migration`.

## Workflow

1. Bound the surface and success criteria. Record inputs, navigation/results, empty/loading/error
   states, actions, dialogs/sheets, persistence, back behavior and external effects that must
   remain.
2. Read the smallest complete slice: UI, state owner, host/route, DI, resources, data/use-case
   calls,
   and any old implementation needed as a behavior reference.
3. Choose the lowest state owner that reads and writes each value. Put durable business/render state
   in `UiState`; keep purely local focus, expansion, animation and draft state in the UI when no
   higher owner needs it. Route Feature/business actions through the Feature intent type. A direct
   user action that only requests host navigation may use a host callback. Dialog/sheet visibility
   is UI state; Android framework dialogs, permissions, file pickers and launches stay in the host.
4. Keep Screen/Content free of repository, DAO, network, storage and service access. New
   presentation
   code uses Repository/Gateway/UseCase boundaries. Preserve an existing violation only when the
   task is explicitly UI-only, do not spread it, and record the remaining debt.
5. Wire navigation, DI and compatibility behavior at the host boundary. Keep public extras, result
   codes and deep-link semantics stable unless the user requested an API change.
6. Delete XML, adapters, bindings and resources only after references and compatibility callers are
   gone.
7. Verify in proportion to risk and report both passed evidence and unverified manual/device paths.

## Project constraints

- New ownership lives under `io.legado.app.feature.<name>`; legacy `ui/...` is a migration source,
  not the destination for a second implementation.
- Use ViewModel-owned read-only `StateFlow` and one `onIntent` dispatcher for Feature/business
  actions, as required by `AGENTS.md`. When a Feature has best-effort transient effects, expose the
  project's `SharedFlow(extraBufferCapacity = 16)` shape. Buffer capacity does not make emissions
  durable while no UI is collecting: navigation/result/destructive completion and other
  consistency-critical ViewModel outcomes must reduce to state or use an explicit acknowledgement
  protocol instead of relying on an effect stream.
- Annotate Compose-facing `UiState` and UI item models with `@Stable` only while honoring the
  annotation's contract: public mutable properties and unobservable mutation make it incorrect.
  Use immutable collections at the rendering boundary; do not change repository/data collections
  mechanically.
- On Android, collect `Flow` UI state with `collectAsStateWithLifecycle` unless a documented
  lifecycle requirement calls for something else. A `LaunchedEffect` collector is scoped to the
  composition, not automatically to `STARTED`/`RESUMED`. Long-lived effects must use appropriate
  keys and `rememberUpdatedState` for changing values that should not restart them.
- Reuse project theme and components when their behavior fits. A wrapper's existence is not enough
  reason to use it when semantics, accessibility or host requirements differ.
- Insets are an ownership decision. Inspect `Scaffold` `contentWindowInsets`, whether content
  consumes `innerPadding`, nested scaffolds/sheets and IME behavior. Do not assume that using
  `Scaffold` alone proves edge-to-edge correctness, and do not double-apply system-bar padding.
- Preserve predictive-back behavior. Use ordinary `BackHandler` only for binary interception; use
  `PredictiveBackHandler` when custom UI needs gesture progress. If Navigation 3 transitions are
  customized, define and verify predictive-pop behavior as well as the normal pop transition.
- The app targets API 37, where orientation, aspect-ratio and resizability restrictions no longer
  protect layouts on large screens. New or substantially migrated destinations must tolerate
  compact, medium and expanded resizable windows, rotation and recreation. Base layout decisions on
  the current app window, not physical-device assumptions; do not add an adaptive library or
  multi-pane layout unless the screen's behavior benefits from it.
- Prefer Material/Foundation interactions for their semantics, focus and keyboard behavior. Custom
  controls and gestures must provide appropriate role/state/action semantics, keyboard/D-pad access
  and an adequate touch target.
- Keep localized user-facing text in resources and retain fallback behavior.

## Verification

- Kotlin-only presentation/host changes: run `:app:compileAppDebugKotlin` using the repository's
  Gradle wrapper syntax for the current shell.
- Resource, manifest, XML/binding deletion or packaging changes: run `:app:assembleAppDebug`.
- State/business changes: run focused ViewModel/use-case tests or add a characterization seam.
- Navigation/Intent/results/insets/back: manually or instrumentedly exercise every retained entry
  path that compilation cannot prove.
- New or substantially changed layouts: exercise representative compact and expanded resizable
  windows, rotation/recreation and IME interaction rather than validating only one portrait phone.
- Always run `git diff --check`.

Use actual task names from this checkout; do not invent tasks from this skill.
