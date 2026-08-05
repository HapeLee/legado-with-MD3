# Legado Compose Review Checklist

## Files to Read

For a feature review, inspect the smallest complete slice:

- `FeatureScreen.kt` and any `FeatureSheets.kt` / `FeatureDialogs.kt`.
- `FeatureViewModel.kt`.
- `FeatureContract.kt` if present.
- Host route in `MainActivity.kt` or retained `FeatureActivity.kt`.
- Koin registration in `di/appModule.kt`.
- Repositories/usecases used by the feature.
- Old View caller or XML/adapters only when still used for compatibility or behavior comparison.

Use current examples as references:

- `ui/main/MainActivity.kt` for Navigation3 route ownership.
- `ui/book/info/BookInfoContract.kt`, `BookInfoViewModel.kt`, `BookInfoScreen.kt` for behavior-heavy MVI/UDF.
- `ui/book/search/SearchContract.kt`, `SearchViewModel.kt`, `SearchScreen.kt` for route-level search state.
- `ui/widget/components/...` and `ui/theme/...` for shared UI conventions.

## Architecture Checks

Flag issues when:

- A new Compose-first screen is implemented as a standalone Activity instead of a `MainActivity` destination without a compatibility reason.
- A retained Activity contains feature business logic instead of acting as a thin Android `Intent` compatibility host.
- Business rules, repository calls, DAO calls, persistence writes, or service orchestration live in composables.
- ViewModel exposes mutable state directly, exposes `MutableStateFlow`, or lets UI mutate domain objects.
- UI state is split across Activity fields, composable `remember` state, adapter state, and ViewModel state in a way that can diverge.
- One-shot events such as navigation, result codes, file opening, permission requests, or clipboard writes are represented as persistent `UiState` fields that can replay incorrectly.
- MVI `FeatureIntent` user actions are confused with Android `Intent` launch/extras.
- Domain/usecase boundaries are bypassed in new screens when a meaningful business action exists.
- A migration introduces new domain abstractions for a single trivial UI action without reducing real complexity.

## UDF and State Checks

Flag issues when:

- `Screen` functions own business state instead of receiving `state` and callbacks.
- `remember` / `rememberSaveable` stores source-of-truth data that should survive process or route recreation through ViewModel state.
- User-driven transient state that should survive recreation, such as a pending delete-confirmation target, is held with plain `remember` instead of `rememberSaveable` or ViewModel state.
- `LaunchedEffect` keys are unstable or cause repeated data loading, duplicate navigation, duplicate toasts, or repeated service calls.
- A long-lived `LaunchedEffect` collector captures parent callbacks or context-dependent values that can change without using `rememberUpdatedState`, unless the effect is intentionally keyed to restart.
- Flows are collected without lifecycle awareness in UI routes where `collectAsStateWithLifecycle()` should be used.
- List items lack stable keys where mutation, selection, or animation can make state attach to the wrong row.
- Heterogeneous lazy lists or grids provide `key` but omit `contentType`, reducing composition reuse quality when headers, rows, ads, loading items, or expanded content mix in the same lazy layout.
- Selection, search query, sorting, filtering, loading, and error states are not represented in a single coherent `UiState`.
- Derived values are recomputed expensively on every recomposition instead of living in ViewModel state or `remember(key)`.
- UI effects write list-derived consistency back into the ViewModel, such as pruning selection from `LaunchedEffect(uiState.items)`. Prefer deriving this in the ViewModel with `combine(...)` or reducing it when the data flow updates.
- State is hoisted higher than the lowest common owner, such as a top-level route collecting a child ViewModel flow only needed inside a conditional branch or one popup menu.

## Compose Stability and Collection Checks

Flag issues when:

- Collection-heavy `UiState` exposed to Compose uses ordinary Kotlin `List`, `Set`, or `Map` in hot paths. Kotlin 2.x strong skipping is helpful but does not make standard Kotlin collections stable to Compose.
- DAO or repository lists are passed directly through `UiState` to deep composables without conversion to `ImmutableList` / `ImmutableSet` / `ImmutableMap` or a stable UI model wrapper.
- Review recommendations imply replacing every internal collection. Keep the finding scoped to Compose-facing render state; temporary accumulators, sorting inputs, Room DAO signatures, and domain APIs can remain normal collections unless they are the actual recomposition boundary.
- Mutable collections such as `ArrayList`, `MutableList`, or mutable maps are stored in Compose state or ViewModel `UiState`.
- Entity classes from non-Compose modules are passed deeply through composables and cause visible recomposition churn; prefer stable UI render models if measurement or code shape shows this matters.

## Navigation and Compatibility Checks

Flag issues when:

- New Compose destinations skip `MainActivity` Navigation3 route registration.
- Legacy Android `Intent` extras or result codes change without an explicit compatibility plan.
- A migrated screen is reachable both through `MainActivity` and a retained Activity with inconsistent state initialization.
- Navigation is performed directly inside nested composables instead of through callbacks/effects.
- Activity Result launchers, file pickers, permission requests, or Android DialogFragments are hidden inside reusable UI composables.
- Back behavior bypasses ViewModel decisions when unsaved changes, selection mode, add-to-shelf prompts, or confirmation dialogs exist.

## UI and Project Convention Checks

Flag issues when:

- Raw Material components ignore `LegadoTheme` or existing shared components where the project already has a wrapper.
- A screen recreates shared UI already present under `ui/widget/components`.
- User-facing text is hardcoded instead of using string resources, except for temporary debug-only text.
- Compose UI keeps ViewBinding, adapter, or XML assumptions after migration.
- Dialogs and bottom sheets use inconsistent project components when `AppAlertDialog`, `AppModalBottomSheet`, or existing option sheets fit.
- Image loading bypasses existing Coil cover/image helpers where cover behavior, cache, SVG, or GIF handling matters.

## Clean Architecture Checks

For new screens, expect:

- Compose renders state and emits user actions.
- ViewModel owns state, intent handling, and effect emission.
- Usecases own reusable business actions.
- Repositories mediate data access.
- DAOs remain behind repositories unless adding a boundary would be disproportionate and local project patterns already allow direct use.

For migrated screens, allow pragmatic intermediate code only when:

- It preserves behavior.
- It is isolated behind a clear compatibility boundary.
- It does not spread View-era assumptions into new Compose-first code.

## Output Template

Use this shape unless the user asks for another format:

```text
Findings
- [P1] Title
  file:line
  Impact: ...
  Fix: ...

Open Questions
- ...

Notes
- No issues found in ... / Tests not run ...
```

When using Codex inline review comments, emit one directive per finding with tight line ranges:

```text
::code-comment{title="[P2] Keep route state in ViewModel" body="..." file="/absolute/path/FeatureScreen.kt" start=42 end=45 priority=2 confidence=0.8}
```

## Verification Suggestions

Recommend verification based on the reviewed change:

- Kotlin-only review/fix: `.\gradlew.bat :app:compileAppDebugKotlin`.
- Resource/XML/manifest impact: `.\gradlew.bat :app:assembleAppDebug`.
- Navigation or compatibility changes: manually open both `MainActivity` route and any retained legacy Android `Intent` entry point.
- Behavior-heavy ViewModel changes: add or run focused tests only where the project has a practical test seam.
