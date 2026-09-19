# KMP Slice Checklist

Use this reference for implementation plans, extraction work, scaffolding or reviews. The default
path is renderer-neutral KMP. CMP checks apply only after a shared Compose renderer has been chosen;
they are not instructions to convert every Feature or host to Compose.

## Before change

- Scope names exact files/packages, current callers, one boundary and one rollback point.
- Existing behavior is captured by tests or a written parity list.
- The target owner, source set and consumers are real rather than copied from the target diagram.
- Dependencies are classified as common-ready, contract-needed, renderer-specific,
  platform-island or unknown.
- Actual Gradle target and verification task names have been inspected.

## Contract quality

- Public types express domain values instead of Android/JVM/storage/rendering details.
- Error, cancellation, threading, transaction, ordering, serialization and ownership semantics are
  explicit where they cross the boundary.
- Capability absence is visible to callers.
- An ordinary interface is used unless `expect/actual` provides a concrete static benefit.
- Every abstraction has a real caller and removes a measurable dependency.
- Native or IPC exports are smaller and more stable than the internal Kotlin API.

## Module and source-set graph

- The app host owns implementation aggregation, DI, navigation/window lifecycle and packaging.
- Core does not import Feature; Feature does not import another Feature implementation.
- Shared modules do not depend on platform implementations or renderer modules.
- Gradle `api` exposure is intentional; otherwise use `implementation`.
- Package colocation, Android module extraction, KMP conversion and renderer sharing remain separate
  changes unless evidence requires combining them.
- `commonMain` is compiled by at least one non-Android target before being called shared.
- JVM+Android-only sharing has an honest owner and is not mislabeled as common.
- Platform files live in the narrowest relevant source set.
- Android services, Context/URI/resources and notifications remain Android-side.
- Rhino/JS behavior remains behind a capability boundary until another target has a compatible,
  tested implementation.

## Renderer and host checks

- Presentation contracts contain no Compose, Material/Miuix, Fluent, WinUI, SwiftUI or platform
  resource types.
- A renderer owns its resources, accessibility, layout and navigation presentation.
- Shared Compose UI emits semantic callbacks/effects; the host owns platform launchers.
- A CMP Screen has at least two intentional consumers or another documented product reason.
- WinUI/iOS bridges define versioning, lifecycle, cancellation, error and memory ownership.
- A sidecar protocol defines startup, readiness, shutdown, crash recovery, upgrade and local-access
  behavior.

## Gates

- Affected Android unit/lint/architecture/package gates pass.
- Common tests and actual metadata/target compile tasks pass.
- Capability status distinguishes compile, contract-test, smoke, package and release-ready.
- Adapter tests cover success, failure and cancellation where relevant.
- Serialization/database changes include backward/forward or migration evidence.
- Reader/rule/service changes include parity and, where relevant, real-device performance evidence.
- Reduced historical violations lower their baseline in the same change.
- `git diff --check` passes.

## Scaffolding

- At least two accepted manual examples prove a convention before it is generated.
- Dry-run is available and default execution refuses overwrites.
- Existing graph/DI files are amended safely rather than replaced wholesale.
- Only necessary files are generated; no empty layers or speculative targets.
- Generator behavior has fixture, snapshot or compilation evidence.

## Review output

For each finding give a tight file/line reference, concrete impact and smallest credible fix:

- P0/P1: behavior/data loss, incompatible rule/storage/ABI semantics, broken target, lifecycle,
  thread, cancellation, native ownership or packaging defects.
- P2: illegal dependency, renderer/platform leakage, false capability, state duplication, baseline
  relaxation or unnecessary abstraction.
- P3: convention, naming, documentation, graph or template drift.

If no issue is found, state the remaining unverified host, device, package, interop and performance
risks.
