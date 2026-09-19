---
name: legado-kmp-migration
description: Plan, implement, or review Legado KMP-first modularization across shared domain, data, and presentation code with independently chosen Android, Desktop, Windows-native, and optional iOS renderers. Use for Gradle boundaries, commonMain extraction, platform capabilities, native or process bridges, selective CMP, target gates, and legacy-owner removal. Do not use for an Android-only View-to-Compose rewrite unless it also changes a multiplatform or Gradle boundary.
---

# Legado KMP-first Migration

## Purpose

Move one verified responsibility toward a reusable KMP boundary without weakening Android behavior
or forcing every host to use Compose. Optimize for shared behavior and explicit platform contracts,
not maximum shared UI percentage.

Valid renderers include Android Compose/View islands, Compose Desktop with Material or Fluent,
Windows-native WinUI 3, and optional SwiftUI/UIKit. CMP is one renderer technology, not the
architecture root.

Before acting, read repository `AGENTS.md`, `docs/dev/kmp-cmp-modernization.md`, and the relevant
part of `docs/dev/feature-first-structure.md`.

Read supporting references only when applicable:

- Implementation or review: [references/slice-checklist.md](references/slice-checklist.md).
- Multiple renderers, WinUI 3, SwiftUI/UIKit, native export or IPC:
  [references/renderer-host-strategy.md](references/renderer-host-strategy.md).

## Choose the work mode

- **Architecture/plan:** inventory current dependencies and consumers, then choose the sharing
  boundary, renderer strategy, targets, gates and rollback point.
- **Pure KMP extraction:** move stable models, rules, ports, use cases, reducers or stores into
  Compose-free `commonMain`.
- **Data/runtime extraction:** separate domain contracts from storage, network, files and rule
  engines while retaining compatibility semantics under contract tests.
- **Renderer boundary:** split renderer-neutral presentation from Material/Miuix, Fluent, WinUI 3,
  SwiftUI/UIKit or specialized reader UI.
- **Selective CMP:** share a Compose Screen/resource set only when real hosts deliberately choose
  the same renderer and interaction model.
- **Native host bridge:** expose a small, versioned API through an Apple framework, Windows DLL/C
  ABI, or process/IPC boundary; do not export the internal Kotlin object graph.
- **Build logic:** prove a target/convention/gate change with one representative module before
  broader rollout.
- **Review:** report findings and unverified targets first; edit only when fixes or plans were
  requested.

## Decisions required before implementation

1. **What is shared?**
    - Default candidates are domain values, business rules, repository ports, use cases,
      serialization models, state snapshots, commands and reducers.
    - Screens, design-system components, navigation renderers, platform ViewModel owners, resource
      handles, lifecycle and OS integration are not shared by default.
    - A pure presentation API must not expose Compose annotations/types, Material/Fluent types,
      Android resources, Koin, Room, or platform SDK objects.

2. **Who renders it?**
    - Name each committed host and renderer separately; “Desktop” is not a renderer.
    - Different renderers may consume the same presentation contract while owning distinct layouts,
      resources, navigation and accessibility.
    - Do not invent universal UI wrappers across unrelated design systems. Share semantic values
      only
      when their meaning is genuinely common.

3. **How does a non-Kotlin host consume it?**
    - JVM Compose hosts can depend on KMP/JVM modules directly.
    - SwiftUI/UIKit needs a deliberately exported Apple framework facade.
    - WinUI 3 needs either a Kotlin/Native `mingwX64` DLL with a narrow C ABI or a separately
      packaged
      JVM process reached through a versioned local IPC protocol.
    - Keep rich Kotlin types internal. Define DTO/state snapshots, commands, errors, cancellation,
      callback threads, allocation ownership and disposal at the bridge.

4. **What evidence supports the claim?**
    - Distinguish metadata/compile, contract-test, host smoke, package and release-ready evidence.
    - Android plus JVM compilation does not prove iOS or Windows Native readiness.
    - Do not document a module, target, task or host as current until it exists in this repository.

## Workflow

1. Bound one slice: exact files, callers, behavior, intended owner/source set, host impact and
   rollback point.
2. Classify dependencies as `common-ready`, `contract-needed`, `renderer-specific`,
   `platform-island`, or `unknown`. Imports are only a first pass; compile every claimed target.
3. Establish characterization or contract tests before moving behavior. Preserve storage, script
   ABI, serialization, error, cancellation, ordering and threading semantics.
4. Choose the smallest seam. Prefer interfaces plus constructor injection; use `expect/actual` only
   for true platform primitives or a documented case where injection cannot provide the boundary.
5. Implement additively, migrate a bounded caller set, then delete the old owner when no callers
   remain. Do not keep internal `Help/Utils/Base/Provider` facades for import compatibility.
6. Run actual repository gates and target tasks. Never infer task support from the target design.
7. Report behavior evidence, dependency/baseline delta, capability changes, exact commands,
   rollback path and unverified hosts/devices.

## Boundary rules

- Pure KMP domain/presentation does not depend on Compose, AndroidX ViewModel, Room entities/DAO,
  Koin, platform resources, `File`/URI, JVM-only libraries or renderer types.
- Renderer modules may depend on presentation; presentation never depends on a renderer.
- Each host owns its composition root, root navigation/window lifecycle, platform effects,
  implementation selection and packaging.
- Unsupported capabilities are explicit; a successful no-op is not an implementation.
- Feature-to-Feature implementation dependencies remain forbidden. Create `api/impl` only for a
  real Gradle consumer; split renderers by responsibility instead of generic layering names.
- Do not change storage, network, DI, navigation and UI technology in one slice.
- Historical baselines only decrease. A new source set starts at zero and cannot justify raising a
  baseline.

## Verification minimums

- Always retain the affected Android G0 gates.
- Pure KMP: common tests plus every claimed target compile.
- Data/runtime: adapter contracts and compatibility checks proportional to storage, serialization,
  cancellation and error risks.
- CMP renderer: target compile plus rendered UI/semantics smoke and runtime dependency alignment.
- WinUI 3 DLL: native link, generated API review, consumer smoke, memory/disposal/error/thread tests
  and package loading.
- WinUI 3 IPC: protocol compatibility, startup/shutdown/reconnect, local-access policy,
  cancellation and installer smoke.
- iOS native UI: framework export plus a Swift consumer compile/observation/lifecycle smoke.
- Always run `git diff --check`; use a clean rebuild after cross-module moves.

## Review output

List findings by impact with tight file/line evidence and the smallest credible fix:

- P0/P1: behavior or data loss, ABI/storage incompatibility, broken target, lifecycle/thread/
  cancellation defect, or package/runtime failure.
- P2: renderer leakage into presentation, illegal dependency, false capability, untested bridge,
  state duplication, baseline relaxation, or unnecessary abstraction.
- P3: convention, naming, documentation, graph or template drift.

If there are no findings, state which host, package, device, renderer, interop and performance paths
remain unverified.
