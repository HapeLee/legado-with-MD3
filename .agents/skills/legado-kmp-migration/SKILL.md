---
name: legado-kmp-migration
description: Plan, implement, or review Legado Gradle modularization and Kotlin/Compose Multiplatform migration slices. Use for build-logic, module boundaries, commonMain extraction, expect/actual or platform interfaces, CMP feature sharing, KMP migration gates, capability matrices, and related scaffolding. Do not use for an ordinary Android-only Compose screen migration unless it also changes a multiplatform or Gradle boundary.
---

# Legado KMP/CMP Migration

## Purpose

Move one verified boundary toward KMP/CMP without weakening Android behavior. Treat
platform-specific implementations as valid architecture, keep the Android app shippable after every
slice, and use baselines as ratchets rather than waivers.

Before acting, read repository `AGENTS.md` and `docs/dev/kmp-cmp-modernization.md`. For an
implementation or review, also read [references/slice-checklist.md](references/slice-checklist.md).

## Select the mode

- **Plan:** produce a dependency inventory, target seam, capability impact, phases, gates and
  rollback point. Do not create the full target module tree.
- **Extract:** move a narrow set of models/rules/contracts behind adapters, migrate callers, run
  both common and Android verification, then lower relevant baselines.
- **Build logic:** add or change convention plugins and dependency rules using one representative
  module before broad rollout.
- **CMP feature:** share state/reducer/UI only where platform effects and navigation have explicit
  host boundaries.
- **Scaffold:** generate only conventions proven by at least two accepted manual examples; require
  dry-run and no-overwrite behavior.
- **Review:** report behavior and boundary findings before recommending changes. Do not edit unless
  fixes were requested.

## Required workflow

1. Define the slice and evidence.
    - Name exact source files/packages, current callers, intended target module/source set, and
      behavior that must not change.
    - Separate current facts from planned modules and task names.
    - Inspect Gradle files, version catalog, relevant tests, and imports rather than assuming an API
      is multiplatform.
    - For Feature work, read `docs/dev/feature-first-structure.md` and distinguish package
      colocation, Android module extraction, and KMP conversion as separate stages.

2. Classify every dependency.
    - `common-ready`: Kotlin/common library API with a non-Android compile target.
    - `contract-needed`: behavior can be represented by a narrow interface and platform
      implementation.
    - `platform-island`: lifecycle, service, reader rendering, Rhino/JVM or another capability that
      should remain platform-specific.
    - `unknown`: verify against primary documentation or a compile PoC before designing around it.

3. Choose the smallest seam.
    - Prefer stable values, pure rules, gateways and use cases before storage, engines and UI.
    - Prefer ordinary interfaces plus DI over `expect/actual`; reserve `expect/actual` for platform
      primitives that every target must provide statically.
    - Keep platform assembly in the app/Koin composition root.
    - Do not change storage, network, navigation, DI and UI technology in the same slice.
    - Create Feature `api/impl` only when a real Gradle boundary and caller require it.

4. Establish gates before moving code.
    - Add characterization/contract tests at the old boundary.
    - Use report → freeze baseline → blocking for a new rule.
    - Never raise a platform-import or dependency baseline to make a migration pass.

5. Implement additively.
    - Introduce contract/module and Android adapter first.
    - Migrate a bounded caller set.
    - Delete the old entry only after no callers remain; otherwise document its removal condition.
    - Keep app-host composition, navigation runtime and platform effects outside shared UI.
    - Preserve the canonical `io.legado.app.feature.<name>` package when promoting `:app` code to
      `:feature:<name>` so module extraction does not require another conceptual reorganization.

6. Verify and report.
    - Always retain the repository G0 Android gates.
    - For common code, run the actual `commonTest`, metadata, and selected non-Android target
      compile tasks that exist in the changed project.
    - For adapters, run contract and Android behavior tests. For reader/rule/service changes,
      include parity or performance evidence proportional to risk.
    - Report exact commands, dependency/baseline deltas, capability changes, rollback path, and
      unverified targets.

## Non-negotiable boundaries

- No Android, JDK-only, Room DAO/entity, resource ID, `File`, URI, service, Activity or View type in
  shared public contracts.
- No core → feature, Feature `api` → Feature, or Feature `impl` → another Feature `impl` dependency.
- No silent no-op implementation for an unsupported target; model the capability explicitly.
- Report target support by evidence level: compile, contract-test, smoke, package, and
  release-ready. Do not collapse them into one supported/unsupported flag.
- No generic dumping-ground module and no empty architecture-shaped modules.
- Do not make Compose reader replacement a KMP prerequisite; share render models while allowing the
  Android renderer to remain specialized.
- Preserve current rule-script, import/export, database, settings and reader semantics until
  dedicated tests authorize a behavior change.

## Relationship to Android Compose work

Use `legado-compose-migration` for Android-only View→Compose work and `legado-compose-review` for
Android Compose review. Combine them with this skill only when the same bounded change crosses a
KMP/CMP or Gradle-module boundary.
