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

## CMP module setup (repository-specific)

- Convention `legado.kmp.compose` (in `build-logic`) = `legado.kmp.library` + `org.jetbrains.compose`
  (1.12.0) + Compose compiler, with Compose runtime/foundation added to **`commonMain`** directly.
  Do NOT reintroduce the `composeMain` intermediate source set — that was the
  "commonMain-must-be-Compose-free" era, which now only applies to `pure`/`data` modules.
- Applying that convention REQUIRES registering the module as `"cmp"` in the root
  `build.gradle.kts` `CheckSharedPurityTask.kmpModuleTypes`. The pair is mandatory; G2
  (`checkSharedPurity`) rejects Compose imports in `commonMain` without it.
- When moving a component `:core:ui` → `:core:designsystem`, **keep the package name**
  (`io.legado.app.ui.*` is a shared namespace), so consumer imports change by zero lines — the
  consumer just has to depend on `:core:designsystem`.
- Only libraries with a desktop/JVM variant may enter `commonMain`; decide from the artifact's
  `.module` metadata, never from "the group name says androidx". Check **Google Maven as well as
  Maven Central** (`dl.google.com/dl/android/maven2`) — androidx multiplatform variants are not on
  Central. Known answers:
  - **material3** → `org.jetbrains.compose.material3:material3` (real KMP; its **android variant
    delegates to** `androidx.compose.material3:material3`, so on Android you end up on Google's
    build). The raw `androidx.compose.material3:material3` coordinate ships only android + empty
    `jvmStubs`/`nativeStubs` variants — do not put it in `commonMain`.
  - **The version is NOT the plugin version**: the plugin pins material3 via
    `ComposeBuildConfig.composeMaterial3Version` (currently `1.9.0` = latest *stable*; 1.10+ exist
    only as alphas). The convention derives its compose versions from `ComposeBuildConfig` and
    asserts the version catalog matches — never hand-write these numbers.
  - **How to declare**: `implementation(compose.material3)` does resolve but is
    `@Deprecated("Specify dependency directly")`; `compose.dependencies.material3` does **not**
    resolve from a module that only applies the convention (no type-safe accessor is generated for
    transitively applied plugins). Use direct coordinates.
  - **icons**: use the repo's `androidx.compose.material:material-icons-extended:1.7.8` (it has a
    jvm variant; Google's publication stops there). CMP's own `material-icons-*` is frozen at 1.7.3
    and deprecated — neither path grows, so new icons mean migrating to Material Symbols.
  - **Miuix**: reference **without** the `-android` suffix, and `basic.Switch` lives in `miuix-ui`,
    not `miuix-core`.
- CMP `*-metadata` artifacts need network on first resolution (`--offline` fails with
  "No cached version available for offline mode"); offline works after one successful resolve.
- Full recipe, dependency table and measurements: `docs/dev/cmp-module-convention.md`.

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

## Legacy ratchet (repository-specific)

- `checkLegacyArchitecture` freezes per-directory counts over `gradle/architecture/legacy-baseline.txt`;
  **decreases must be lowered in the same change**, and any directory that first shows up in the
  report must have zero counts.
- The same ratchet covers `import io.legado.app.base.**` (`legacyBase`), and it is **bidirectional**:
  additions fail, *undeclared decreases* also fail, and a first-time directory must be zero. So when a
  shared flow has to leave a `base`-package superclass (`BaseRuleViewModel` → `RuleTransferUseCase`),
  do **not** park the new class next to the one it replaces: the new directory immediately shows
  `import io.legado.app.base.**` and is blocked. Move the whole shared layer to a neutral package
  (`io.legado.app.core.rules`) instead — the Feature's base count then drops to zero and the baseline
  is lowered, the only direction the ratchet allows.
- Conversely, an Android implementation that depends on `help.*` (e.g. `AndroidRuleTransferPlatform`,
  which needs `help.http.okHttpClient`) **cannot** be relocated into a fresh directory: the new area
  would show `legacyHelp` counts and fail. Leave it in its existing report-only area and only add an
  import for the relocated interface. "Contract moved down, implementation still in `:app`" is an
  accepted transition state, not a defect to force-fix in the same slice.
- When extracting a use case out of a ViewModel, re-check every `withContext(Dispatchers.IO)` wrapper
  you pass through. `viewModelScope` runs on Main, so dropping a wrapper silently moves batch DB
  writes / re-indexing onto the UI thread while all tests still pass.
- `app/main/io/legado/app/di` already carries a `legacyHelp` baseline, so **the composition root may
  not add `import io.legado.app.help.**`**. A new Android platform adapter that DI must reference
  goes outside the legacy areas (e.g. `io.legado.app.platform`) — moving it into `help` to reuse
  an existing object is not an option.
- Keep one adapter definition and let both paths use it (e.g.
  `AndroidPlatformCapabilities.clipboard(context)` consumed by `PlatformServices.install(...)` for
  the legacy Provider and by the Koin module for constructor injection). Two copies of the same
  behaviour drift.
- `:core:platform`'s `JsonCodec` swallows malformed JSON and returns `null`, while the app-side
  `GSON.fromJsonObject(...).getOrThrow()` throws. When swapping in `JsonCodec`, restore the previous
  failure semantics explicitly (`?: throw Exception(...)`) instead of silently changing the error
  path.

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
