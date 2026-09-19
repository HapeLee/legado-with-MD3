---
name: legado-compose-review
description: Review existing Legado Android Compose screens, routes, ViewModels, contracts, dialogs, sheets, navigation, and compatibility hosts for concrete behavior, lifecycle, state ownership, architecture, inset, accessibility, and maintainability risks. Use for review or audit requests. Do not rewrite code unless fixes are requested; use legado-compose-migration for implementation and legado-kmp-migration for multiplatform or Gradle-boundary findings.
---

# Legado Compose Review

## Purpose

Find concrete defects and risky drift before proposing rewrites. Judge the code against current
repository behavior and constraints, not a preferred template.

Read `AGENTS.md`, the scoped files and
[references/review-checklist.md](references/review-checklist.md). Consult an old View implementation
only when it remains a caller or behavior baseline.

## Review workflow

1. Define the exact surface and whether the user asked for review only or fixes. Review-only work
   does not authorize edits.
2. Inspect the smallest complete behavior slice: Screen, state owner/contract, route/host, DI,
   relevant repositories/use cases, resources and compatibility entry points.
3. Trace state and effects end to end. Look for divergent owners, replayed one-shot work, lifecycle
   mistakes, lost events, unstable identity and error/cancellation paths before style issues.
4. Trace navigation/results, Insets/IME, back, accessibility and platform launchers through the real
   host. Do not infer correctness from the presence of a particular API.
5. Report findings first with tight line references, concrete impact and the smallest credible fix.
   Separate compatibility-preserving fixes from optional redesign.
6. If no finding exists, say so and list the important unverified runtime/device paths.

## Severity

- **P0/P1:** crash, data/behavior loss, broken navigation/result/entry compatibility, unsafe
  lifecycle or concurrency, repeated destructive effect, inaccessible/obscured critical content.
- **P2:** state ownership that can diverge, business/data access in UI, platform leakage, incorrect
  stability contract, recurring recomposition/performance issue, or architecture drift with a real
  maintenance cost.
- **P3:** localized convention, naming, testability or cleanup issue without demonstrated behavior
  or structural impact.

Do not promote a missing annotation, different file split or wrapper choice to P2 without explaining
the observable or architectural consequence.

## Boundaries

- Do not require every screen to have the same number of files or abstractions.
- Allow thin compatibility Activities and mature View/platform islands when they have a current
  caller and one clear owner.
- New Compose presentation must not add DAO, network, storage or service access; report existing
  debt without turning a review into an unauthorized domain rewrite.
- Treat `@Stable` as a contract that can itself be wrong, not as proof of stability or performance.
- Strong Skipping is enabled by default with modern Kotlin/Compose compiler versions, so an
  unstable parameter is not automatically a performance defect. Check identity churn, actual
  recomposition evidence and compiler/benchmark data. A missing project-required annotation is P3
  unless it causes a concrete architectural or runtime issue; an incorrect `@Stable` promise can be
  P2 because it can suppress required updates.
- Android `Flow` state should normally be collected with `collectAsStateWithLifecycle`. A
  composition-scoped collector is not automatically lifecycle-aware merely because it uses
  `LaunchedEffect`.
- Review each ViewModel-originated `SharedFlow` effect by delivery semantics. Buffer capacity does
  not make an event durable while no UI is collecting. Navigation, result delivery, payments,
  destructive completion and other consistency-critical outcomes should normally reduce to state
  or use an explicit acknowledgement protocol; best-effort transient feedback may remain an effect.
- UI-only behavior should stay at the lowest UI owner. A direct user navigation callback handled by
  the host is valid and often preferable to a ViewModel round trip; guard rapid/replayed navigation
  with lifecycle-aware mechanisms such as `dropUnlessResumed` when the available Lifecycle version
  supports it.
- Insets and predictive back require ownership and runtime-path analysis; `Scaffold`, padding APIs,
  Navigation 3 or `BackHandler` alone do not prove correctness.
- Use `PredictiveBackHandler` when custom UI needs gesture progress; use ordinary `BackHandler` only
  when binary interception is sufficient. When Navigation 3 transitions are customized, inspect
  `predictivePopTransitionSpec` as well as normal pop behavior.
- Reusing a project component is preferred only when its behavior, semantics and accessibility fit.
- Prefer Material/Foundation interaction APIs because they provide semantics, focus and input
  behavior. Custom gestures/components must preserve role, state/action semantics, keyboard/D-pad
  access and an adequate touch target.

## Current official baseline

When library behavior matters, verify it against the repository version and current official docs:

- [Strong Skipping](https://developer.android.com/develop/ui/compose/performance/stability/strongskipping)
  and [stability contracts](https://developer.android.com/develop/ui/compose/lifecycle).
- [Lifecycle-aware Compose collection](https://developer.android.com/topic/libraries/architecture/lifecycle).
- [State hoisting](https://developer.android.com/develop/ui/compose/state-hoisting).
- [UI event delivery](https://developer.android.com/topic/architecture/ui-layer/events).
- [Material 3 Insets](https://developer.android.com/develop/ui/compose/system/material-insets)
  and [Compose Insets](https://developer.android.com/develop/ui/compose/system/insets-ui).
- [Predictive back](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture)
  and [Navigation 3 transitions](https://developer.android.com/guide/navigation/navigation-3/animate-destinations).
- [Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
  and [semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics).

When implementation is requested, also read the migration skill's project patterns.
