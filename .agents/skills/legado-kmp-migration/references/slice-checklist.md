# KMP/CMP Slice Checklist

Read this reference for implementation plans, extraction work, scaffolding, or reviews.

## Before change

- Scope names exact packages/files and one target boundary.
- Existing behavior is captured by tests or a written manual parity list.
- Current callers and module edges are known.
- Imports/dependencies are classified as common-ready, contract-needed, platform-island, or unknown.
- The chosen non-Android target and actual Gradle verification tasks are known.
- A rollback point exists and does not require reverting unrelated work.

## Contract quality

- Public types express domain values, not Android/JVM/storage implementation details.
- Error, cancellation, threading, transaction, ordering and serialization semantics are explicit.
- Capability absence is representable and visible to callers.
- An ordinary interface is used unless `expect/actual` provides a concrete static benefit.
- New abstractions have a real caller and reduce a measurable dependency.

## Module graph

- App host owns implementation aggregation, DI and navigation graph aggregation.
- Core does not import Feature.
- Feature API does not import another Feature.
- Feature implementation uses other Feature APIs only.
- Shared modules do not depend on platform implementations.
- Platform implementations are injected from Koin/app composition roots.
- Gradle `api` exposure is intentional; other dependencies use `implementation`.
- No module is created only to match the target diagram.
- Package colocation, Android Gradle module extraction, and KMP/CMP conversion are separate
  reviewable stages; the slice does not combine all three by default.

## Source sets and platform code

- `commonMain` is compiled by at least one non-Android target.
- JVM+Android-only sharing has an explicit source-set/module owner and verified IDE/consumer
  behavior; it is not mislabeled as common.
- Platform files are in the narrowest relevant source set.
- Android services, Room/Context/URI/resources, notifications and renderer code stay Android-side.
- Rhino/JS behavior stays behind a capability boundary until another target has a compatible
  implementation.
- Shared Compose code emits callbacks/effects; host navigation and platform launchers stay outside.

## Gates

- G0 Android test/lint/architecture/debug gates pass.
- Common tests and actual metadata/target compile tasks pass.
- Capability status distinguishes compile, contract-test, smoke, package, and release-ready
  evidence.
- Adapter contract tests cover success, failure and cancellation where relevant.
- Serialization/database changes have forward/backward or migration evidence.
- Reader/rule/service changes have parity and, when relevant, real-device performance evidence.
- Reduced historical violations lower their baseline in the same change.
- `git diff --check` passes.

## Scaffolding

- At least two accepted manual examples prove the convention.
- Dry-run is available and default execution refuses overwrites.
- Existing graphs/DI are not replaced wholesale.
- Only necessary files are generated; no empty layers.
- Template fixtures or compile tests cover generator changes.
- Generated committed source is reviewed like handwritten code.

## Review output

List findings by impact:

- P0/P1: behavior/data loss, incompatible rule or storage semantics, broken actual,
  lifecycle/thread/cancellation defects, reader regression.
- P2: illegal dependency, platform leakage, false capability, state duplication, baseline
  relaxation, or unnecessary cross-platform abstraction.
- P3: convention, naming, documentation, graph or template drift.

For each finding, give a tight file/line reference, concrete impact, and smallest credible fix. If
no finding exists, state remaining unverified platform, device or performance risks.
