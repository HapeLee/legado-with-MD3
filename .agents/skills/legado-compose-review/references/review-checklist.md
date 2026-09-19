# Legado Compose Review Checklist

Use only the sections relevant to the scoped surface.

## Context

- Confirm the canonical Feature owner and whether any legacy `ui/...` file is a compatibility owner.
- Read Screen/Content, ViewModel/state contract, route/Activity, DI and directly used domain/data
  contracts.
- Read old XML/View/adapters only when needed for parity or a live caller.
- Identify all entry points: MainActivity route, Intent/deep link, notification/widget/service or
  Activity Result.

## Behavior and state

- Is there one durable state owner, or can ViewModel, Activity, `remember`, adapter and repository
  copies diverge?
- Is each state hoisted only to the lowest owner that needs to read and write it? Local expansion,
  focus or animation state need not be forced into a ViewModel; business state must not be copied
  into local Compose state.
- Can loading, empty, error, selection, query, sorting and dialog/sheet states be reconstructed
  after
  recreation where required?
- Are one-shot effects consumed once without becoming persistent replayable flags?
- Can a ViewModel-produced effect be emitted while the route has no active collector? If losing it
  would leave navigation, results or durable business state inconsistent, model/acknowledge it as
  state rather than relying on `SharedFlow` buffering.
- Are success, failure, cancellation and stale-response ordering handled?
- Do `LaunchedEffect` keys match the intended lifetime and avoid duplicate loads/navigation/toasts?
- Is `Flow`-backed Android UI state collected with `collectAsStateWithLifecycle`, or is there a
  documented lifecycle reason for another collector?
- Are mutable collections/entities exposed in a way that invalidates a claimed `@Stable` contract?
- With Strong Skipping, are unstable parameters recreated on each update and compared by identity?
  Ask for compiler/measurement evidence before treating ordinary recomposition as a defect.
- Do lazy items use stable identity where insert/remove/reorder can attach state to the wrong row?

## Architecture and ownership

- Screen/Content renders state and emits semantic actions; it does not access DAO, repository,
  network, storage, service or global application state.
- New ViewModel code uses Gateway/Repository/UseCase boundaries and does not add DAO or old global
  access.
- Host owns navigation, framework launchers, permissions, Context/URI work and root lifecycle.
- A retained Activity translates compatibility inputs/results without becoming a second business or
  navigation owner.
- Feature code does not depend on another Feature implementation.
- New files do not extend a legacy package after a canonical Feature owner exists.

## Navigation and compatibility

- New in-app destinations are registered in the current MainActivity graph unless an external-entry
  requirement justifies an Activity.
- Existing extras, result codes, deep links and callers preserve semantics.
- Multiple entry paths initialize equivalent state and converge on the same owner.
- Nested UI requests navigation through callbacks/effects rather than mutating the root stack.
- Pure UI navigation can call a host callback directly. Check rapid taps and lifecycle state; use a
  lifecycle-aware guard such as `dropUnlessResumed` where supported rather than inventing a
  ViewModel effect solely to move the navigation call.
- Back interception covers real selection/unsaved state without breaking predictive back or host
  result delivery.
- Custom gesture-progress UI uses `PredictiveBackHandler`; customized Navigation 3 pop transitions
  also define/verify predictive-pop behavior.

## UI, Insets and accessibility

- Trace `Scaffold` `contentWindowInsets`, inner padding consumption, nested scaffolds/sheets, system
  bars and IME. Check for both overlap and double padding.
- User-facing strings retain localization and fallback behavior.
- Project components/theme are used when their semantics fit; wrappers are not applied mechanically.
- Touch targets, focus order, semantics/content descriptions and keyboard behavior fit the control.
- Custom pointer input has an accessible semantic action and keyboard/D-pad alternative; standard
  Material/Foundation interactions are preferred when their behavior fits.
- Lists/images preserve existing cache, loading and accessibility behavior where relevant.
- Removed XML/binding/menu resources have no remaining references.

## Verification and output

Tie each finding to evidence and the smallest fix. Recommend only checks that can observe the risk:

- compilation for Kotlin wiring;
- assemble for resources, manifest, XML/binding and packaging;
- focused unit tests for reducers/ViewModels/use cases;
- route plus retained Intent/result path checks for navigation compatibility;
- device/emulator checks for Insets, IME, accessibility, predictive back and rendering performance.

Use P0/P1/P2/P3 from the skill. If there are no findings, explicitly list what was not executed or
observed.
