# Legado Android Compose Project Patterns

Read this reference while implementing a screen. Confirm every referenced API against the current
checkout because the repository is mid-migration.

## Ownership and placement

- Android application module: `:app`.
- Canonical new Feature package: `io.legado.app.feature.<name>`.
- `ui/...` is legacy/migration territory. Keep only explicit compatibility owners there after a
  Feature establishes its canonical package.
- `MainActivity` owns new in-app Navigation 3 destinations and the root graph.
- A retained Activity translates stable external/legacy Intent inputs and results; it is not a
  second presentation or navigation owner.
- App-level Koin aggregation remains in the host. Use the repository's existing `viewModelOf` or
  parameterized `viewModel` convention rather than adding a second DI pattern.

A Feature may contain Contract, ViewModel, Route, Screen, components, dialogs, sheets and
presentation models, but create only the files its behavior needs. Directory shape is not an
acceptance criterion.

## State and effects

For a behavior-heavy new screen, the repository expects:

- `@Stable` Compose-facing `UiState` and UI item models;
- immutable collections at the Compose rendering boundary;
- private `MutableStateFlow`, exposed as read-only `StateFlow`;
- when best-effort transient effects are actually needed, private
  `MutableSharedFlow(extraBufferCapacity = 16)`, exposed as read-only `SharedFlow`;
- one `onIntent` dispatcher for user actions;
- host actions expressed as effects or callbacks.

`@Stable` is an assertion, not a magic optimization. Every public property must remain stable and
changes observed by Compose. Wrap unstable data only when the wrapper has correct equality and
mutation semantics. Use measurement/compiler reports before inventing performance abstractions.

Keep source-of-truth business state in the ViewModel. Local `remember`/`rememberSaveable` is
suitable
for UI affordances and restorable drafts/IDs, not a second copy of repository state. Avoid
UI-to-ViewModel feedback loops; restore consistency in the reducer or flow that produces the state.

Classify host actions by delivery semantics:

- A user click whose only meaning is UI navigation can call a host callback directly; use a
  lifecycle-aware rapid-click guard such as `dropUnlessResumed` where the available Lifecycle
  version supports it.
- A ViewModel outcome that must survive a missing collector becomes state with an acknowledgement
  or another explicit durable protocol.
- Snackbar/toast/haptic feedback that is intentionally best-effort may use the Feature effect
  stream. Document the loss behavior rather than assuming `extraBufferCapacity` solves it.

## Host boundaries

Keep these at the route/host unless the project already has a tested abstraction:

- navigation and result delivery;
- permission and Activity Result launchers;
- file/document pickers;
- Android framework dialogs and services;
- Context-dependent clipboard, URI and external-app operations.

Reusable Screen functions receive state and semantic callbacks. They do not know an Activity,
binding, DAO, application singleton or root back stack.

## Lists, lifecycle and recomposition

- Use stable keys when item identity survives insertion, removal or reorder; use `contentType` when
  heterogeneous reuse matters.
- Collect Android route state with `collectAsStateWithLifecycle` unless another lifecycle is
  deliberate and documented. Apply the same delivery analysis to effect collectors; composition
  lifetime and host `RESUMED` state are not interchangeable.
- Key `LaunchedEffect` by the lifetime it represents. Use `rememberUpdatedState` for changing
  callbacks captured by an effect that should not restart.
- Derive cheap display values in composition; memoize or move work only when its cost/lifetime
  warrants it.
- Prefer immutable Compose-facing collections, while leaving temporary computations and data-layer
  APIs in their natural collection types.
- Strong Skipping is enabled by default on modern Kotlin/Compose compiler versions. Unstable
  parameters are compared by identity, so avoid needless instance churn, but do not add wrappers or
  `@Stable` solely from intuition. The repository requires the annotation on UI state/item types;
  those types must actually satisfy its equality and observable-mutation contract.

## Insets and back

Trace which layer owns each inset. For a Material 3 `Scaffold`, inspect its configured
`contentWindowInsets` and verify that content applies/consumes the provided padding correctly.
Sheets, dialogs, IME and nested scaffolds may need separate treatment. Visual/manual evidence is
required; the presence of `Scaffold`, `safeDrawing` or padding modifiers alone proves nothing.

Navigation 3 integration does not remove the need to verify custom back interception, selection
mode, unsaved changes and retained Activity entry points. Route back actions through the same owner
that decides whether leaving is allowed. Use `PredictiveBackHandler` when gesture progress drives
UI;
when customizing `NavDisplay` transitions, also supply/test predictive-pop behavior.

## Adaptive layouts and accessibility

Because this app targets API 37, large-screen orientation, aspect-ratio and resizability
restrictions
cannot be used as a compatibility fallback. Treat the current app window as dynamic across rotation,
fold/unfold, split-screen and desktop windowing.

- Test compact and expanded widths for new or substantially migrated destinations.
- Use current window metrics/window size classes for layout decisions; do not branch on a physical
  device category or assume portrait.
- Preserve important input/draft/selection state across recreation with the appropriate local
  saveable state or `SavedStateHandle`, based on the state owner and size.
- Do not stretch a phone layout indefinitely. Adopt list-detail/supporting panes or adaptive
  navigation only when the Feature benefits; avoid adding a library merely to satisfy a checklist.
- Prefer standard interactive components/modifiers. Custom pointer input needs semantic actions,
  focus/keyboard access and a usable touch target.

## Architecture boundary

New UI and ViewModels do not add DAO, `appDb`, network-client or old preference access. Use existing
Gateway/Repository/UseCase contracts, or add the smallest real boundary with an actual caller.

For a strictly UI-only migration, an existing presentation-layer violation may remain to avoid
combining architecture and UI rewrites. Freeze rather than duplicate it, document it, and do not
describe the screen as fully modernized until the boundary is corrected.

## Verification selection

- Kotlin-only: `:app:compileAppDebugKotlin`.
- Resources/manifest/XML/generated binding/package: `:app:assembleAppDebug`.
- Changed state transitions: focused unit tests with success/failure/cancellation cases as relevant.
- Navigation or compatibility: exercise the MainActivity route and every retained Intent/result
  entry.
- Insets, IME, accessibility and predictive back: device/emulator evidence where risk warrants it.
- New/substantially changed destinations: representative compact and expanded window checks plus
  rotation/recreation; add targeted adaptive UI tests when layout branching is meaningful.

Use `AGENTS.md` for the canonical full verification set and exact wrapper command.

## Current official references

- [State hoisting](https://developer.android.com/develop/ui/compose/state-hoisting)
- [Lifecycle-aware Compose collection](https://developer.android.com/topic/libraries/architecture/lifecycle)
- [UI event delivery](https://developer.android.com/topic/architecture/ui-layer/events)
- [Strong Skipping](https://developer.android.com/develop/ui/compose/performance/stability/strongskipping)
- [Compose side effects](https://developer.android.com/develop/ui/compose/side-effects)
- [Material Insets](https://developer.android.com/develop/ui/compose/system/material-insets)
- [Predictive back](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture)
- [Adaptive layouts for resizable apps](https://developer.android.com/develop/adaptive-apps/guides/app-orientation-aspect-ratio-resizability)
- [Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
