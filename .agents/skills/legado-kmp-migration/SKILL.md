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
For a `:core:data` by-domain split (`domain/<x>` + `data/<x>`), also read
[references/m3-domain-slice.md](references/m3-domain-slice.md) — the checked template M3-1…M3-4 walked.

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
    - Run `scripts/portability-triage.py <files…>` to get the **transitive closure** with
      `android.*` / JVM / out-of-bounds `io.legado.app.*` violations and required extra module
      deps marked, instead of eyeballing one file's imports.
    - Run `scripts/check-kotlin-comments.py [paths…]` after editing KDoc/comments. **Kotlin block
      comments nest**, so writing a wildcard / glob / regex literal inside a comment (`*/` closes it
      early, `/*` opens another level) breaks compilation with errors reported *far below* the real
      culprit — M1-3n burned two build cycles on this. The script is comment-context aware, so the
      same literal inside a string literal is correctly ignored. Zero output = clean.
    - `common-ready`: Kotlin/common library API with a non-Android compile target.
    - `contract-needed`: behavior can be represented by a narrow interface and platform
      implementation.
    - `platform-island`: lifecycle, service, reader rendering, Rhino/JVM or another capability that
      should remain platform-specific.
    - `unknown`: verify against primary documentation or a compile PoC before designing around it.
    - **The triage script covers only two of three dimensions.** The third — whether the *same
      library API* is **present and visible** in the non-Android source set — is invisible to import
      scanning and only surfaces on `compileKotlinDesktop`. Never promote a component to `commonMain`
      on a clean triage report alone. Two sub-cases, opposite fixes:
      - `Unresolved reference` → the symbol is **absent** from that source set: the code really cannot
        be shared as-is (find another public entry point for the same fact, or add a contract).
      - `Cannot access '…': it is internal in file` → the symbol **is** in `commonMain` but the
        dependency version marks it `internal`: **raise the dependency**, do not call it
        "Android-only". (Measured for CMP material3 expressive: public from 1.10.0-alpha05.)
      - `javap` cannot tell these apart — Kotlin `internal` still emits `public` JVM methods.
    - **Before converting a Feature to CMP, audit its `io.legado.app.ui.**` imports against
      `:core:designsystem/commonMain` — not against "does it look like a Compose component".**
      `:core:ui` is an **Android-only** module, so any symbol still living there is a hard blocker for
      `commonMain`. Component families move into designsystem one slice at a time, so a Feature that
      looks clean can still be blocked by 1–4 leftovers (`AppTabRow`, `GroupManageBottomSheet`,
      `RuleEditSheet`, `contentProcess/ContentProcessUiState` blocked the whole
      `replacerules`/`txttocrules`/`dict` wave — M1-3x-pre). Resolve residency mechanically: for every
      import, find the file that *declares* the symbol under each `core/*/src`, and require that no
      declaring file sits in `core/ui`. A failed lookup is **not** proof of cleanliness — same-package
      siblings, extension receivers and `when` positions hide usage from a plain import grep.
    - **A new source set or package directory starts at zero on the G4 legacy ratchet, and the gate
      will not accept a raised baseline.** `gradle/architecture/legacy-baseline.txt` counts
      `<category>|<module>/<sourceSet>/<packageDir>|<count>` **exactly**, for `io.legado.app.help.**` /
      `base.**` / `utils.GSON` / `splitties.init.appCtx` / `data.appDb` imports and for `XxxProvider`
      statics. Consequences when promoting code into `commonMain`/`androidMain`:
      ① stale `<module>/main/...` entries must be deleted and reduced counts lowered in the same
      change; ② a new file importing a legacy facade fails even when the same import was legal one
      directory over; ③ the fix is to **re-home the dependency into a clean package** (the
      `RuleTransferUseCase` → `io.legado.app.core.rules` precedent — M1-3x did the same for
      `ReplaceAnalyzer` → `io.legado.app.data.rules`), never to register the increase;
      ④ replace `XxxProvider.current` with the injected contract (`koinInject<Toaster>()` inside an
      Android Route), which is also where M2 is heading.
    - **Adding a new *external* dependency to a KMP module** hits four traps invisible to import
      scanning (all measured while converting the first Feature module, M1-3w):
      ① the source-set `dependencies {}` block is a `KotlinDependencyHandler` with **no `platform()`**
      — write `project.dependencies.platform(libs.someBom)`;
      ② a versionless version-catalog alias (version supplied by a BOM, e.g. `koin-compose`) resolves
      to "no version specified" in the new configuration — declare that BOM in the same source set;
      ③ a transitively resolved version may be absent from the local/offline cache (align it
      explicitly, the same way the app already pins `navigationevent`);
      ④ take `lifecycle` from the **`androidx.lifecycle` KMP coordinates** (they publish `-desktop`
      variants as separate artifact ids — looking at the module directory's single `.aar` misleads),
      not from `org.jetbrains.androidx.lifecycle`.
      Details: `docs/dev/cmp-module-convention.md` §9.
    - **Before assuming `JsonCodec` is a drop-in for `GSON` on an entity, find out *where that
      entity's `@SerializedName(alternate = …)` shim went*.** `ReplaceRule` had none, so M1-3x could
      say "the standard JSON parses in the shared layer, only the legacy jsonpath format falls back to
      the platform". `TxtTocRule` is the counter-example (M1-3y): its `chapterRule` legacy key (`rule`)
      was turned into a `JsonDeserializer` **registered on the app-side `GSON` facade**, and
      `JsonCodec`'s Android impl deliberately registers none of those. The trigger is therefore not
      "is the input an old format" but "does the compatibility logic sit on a facade the shared layer
      cannot see" — if yes, the contract must cover **all** formats. Put the parsing function beside
      that facade (`core/data/src/androidMain/.../utils/GsonExtensions.kt`): same package ⇒ no
      `import io.legado.app.utils.GSON`, and the G4 `gson` rule counts imports per file, so this adds
      **zero** ratchet points. The `:app` contract impl then only delegates and never imports `GSON`
      itself.
    - **Do not replace a ViewModel's localised strings with literals.** `context.getString(R.string.x)`
      is translated in all four languages; dropping to a Chinese literal is a visible regression for
      non-simplified users. Use CMP's `org.jetbrains.compose.resources.getString(Res.string.x)` — it is
      **suspend**, so resolve it inside the coroutine that emits the effect (a `viewModelScope.launch`
      for the synchronous `pasteRule()`-style callers). Only messages the old base class already
      hard-coded may stay literals.
    - **A Screen-owned toast cannot use `XxxProvider.current`.** When a sheet validates inline and the
      design-system component (e.g. `RuleEditSheet`) must not grow a per-Feature parameter, add
      `onShowToast: (String) -> Unit` to the pure Screen and let the Android Route pass
      `koinInject<Toaster>().toast(it)`. Keep it a toast (the effect-driven path keeps using the
      snackbar host) so the user-visible behaviour is unchanged.
    - **`BaseRuleViewModel`'s business events must become `RuleTransferEvent`.** Carrying
      `events: Flow<BaseRuleEvent>` into `commonMain` is a `legacyBase` import in a new source set ⇒
      immediate G4 failure. `RuleTransferUseCase.events` (`Flow<RuleTransferEvent>`) has a
      field-identical `ShowSnackbar`, so it is a type-name swap in the `when` — which is why the
      `tagrules`/`replacerules` baselines are empty.
    - `JsonCodec.fromJsonObject(json, KClass)` returns a **nullable `T?`**, not the
      `GSON.fromJsonObject<T>()` `Result<T>`. Copying a `.getOrThrow()` from a GSON call site fails
      with "Cannot infer type for type parameter 'T'"; write `?: throw Exception(…)` to keep the
      "parse failure ⇒ import state `Error`" semantics.
    - **Answer "does this entity need a platform import contract?" by checking the `GSON` facade's
      `registerTypeAdapter` list, not just the entity class.** Three rule pages, three different
      answers: `ReplaceRule` → contract for the legacy jsonpath format only; `TxtTocRule` → contract
      for **all** formats (compat moved onto the facade); `DictRule` → **no contract at all**
      (`GSON` registers only 7 rule deserializers and `DictRule` is not one of them, and the entity
      has no `alternate` shim ⇒ `JsonCodec` is byte-equivalent). Reading only the entity source is
      what makes the `TxtTocRule` case a trap: the shim was *moved*, not deleted.
      Details: `docs/dev/cmp-module-convention.md` §11.
    - **The CMP resource package is `<module gradle path> + ".res"` and ignores source-set
      subpackages.** `:feature:dict` keeps its sources in `commonMain/.../feature/dict/**rule**/` but
      the resources sit at `commonMain/composeResources/` root, so the class is
      `io.legado.app.feature.dict.res` — **without `.rule`**. Deriving it from the Kotlin package
      fails with `Unresolved reference 'Res'`. Kotlin package dirs and the CMP resource package are
      two unrelated namespaces.
    - **Only `XxxProvider.current` is a `coreProvider` hit — `LocalClipboard.current` is not.**
      `androidx.compose.ui.platform.LocalClipboard` is a CMP shared API and was already used before
      the migration; the same Screen pattern in `replacerules` keeps it too. The judgment is the
      `Provider` suffix, not the word "clipboard". Do not "clean up" the composition-local access
      while doing a provider sweep.
    - **A shared-library Composable that needs a platform capability takes it as a parameter.**
      When retiring a global provider whose only consumer lives in a shared module (M2-1:
      `ImportJsonEditorProvider`, consumed by designsystem's `BatchImportDialog`), add a parameter
      with **no default value** and bind the implementation in each host's composition root
      (`appModule` / `desktopHostModule`). A missing argument becomes a compile error — stronger and
      earlier than the provider's runtime `error()`; swapping in a `LocalXxx` would only narrow the
      implicit lookup, not remove it. Reserve `LocalXxx` for capabilities that have a meaningful
      fallback value. Delete the old injection path entirely (including the `install(...)` call in
      `PlatformServices`): leaving it unused makes the next reader think two paths still exist.
    - **When the consumer cannot reach DI at all, sink the implementation into the shared layer and
      expose one `expect object` capability primitive.** M2-2 (`BigDataStoreProvider`): the callers
      were Room-constructed entity instance methods (`BaseBook` / `BaseRssArticle` / `BookChapter`),
      the method names were the **book-source JS ABI** (`AnalyzeUrl` binds `bindings["book"] = book`
      and scripts call `book.putVariable(...)`), and every rule-evaluation entry point
      (`WebBook` / `BookList` / `BookContent` / `Rss`) is an `object` singleton. Constructor injection
      and parameter injection are both physically blocked — the planned "room entity becomes pure
      data + repository injected upstream" had **no assembly point** (it would require changing the
      signature of ~66 `WebBook.*` call sites). The shape that works: the shared layer owns **all**
      semantics (`RuleDataFileStore` in `core/data/commonMain`, carrying the `book`/`rss` layout and
      marker-file rules) and depends only on `expect object RuleDataStorage` (`core/platform`) which
      exposes **only** `rootDir` / `md5` / `readText` / `writeText` / `exists` / `delete` / `list`.
      The primitive must not know business nouns like `book`, `rss` or `bookUrl.txt` — if layout leaks
      into `actual`, every platform keeps its own drifting copy. Two byte-identical JVM `actual`s
      (androidMain + desktopMain) are correct here, not duplication to refactor away: an `actual` is
      scoped to its source set, so the alternatives are "two identical copies" or "an extra layer to
      save the repetition". Configure the primitive from the host composition root and make it
      **ready as soon as the composition root is built** (Android `App.onCreate`, desktop inside
      `desktopHostModule(...)`'s body); an unset root must throw, not silently fall back to an empty
      directory. Capability logic that needs `appDb` stays in the host (`:app`'s new
      `RuleDataCleaner` consumed only the strategy-free `list*Owners` / `delete*Entry` primitives).
      When the payload is a **file path already present on user devices**, pin the layout with
      **known hash vectors** (`md5("abc") = 900150983cd24fb0d6963f7d28e17f72`) rather than computing
      expectations with the function under test — and note that a path-shape test **cannot** catch a
      hex-case change on Windows (NTFS is case-insensitive); that needs a separate assertion on the
      literal string. Mutation-verify both: uppercase hex fails 1 case, `book` → `books` fails 3.
      Details: `docs/dev/cmp-module-convention.md` §15, audit §33.

3. Choose the smallest seam.
    - Prefer stable values, pure rules, gateways and use cases before storage, engines and UI.
    - Prefer ordinary interfaces plus DI over `expect/actual`; reserve `expect/actual` for platform
      primitives that every target must provide statically. The exception is when the consumer cannot
      be reached by DI in the first place (see the `expect object` capability-primitive pattern above):
      then the primitive is the seam, but it must carry **only raw capability** — all semantics stay
      in the shared layer.
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
    - Run `./gradlew clean` as a **standalone command** before the verification set, and confirm the
      output contains `:the-module:clean`. Chained as `clean :some:task`, a **configuration-phase**
      failure means `clean` never runs: stale outputs from the previous module shape stay, and a
      results-counting script reading those directories reports "unchanged" while proving nothing.
      (Also: after a module becomes KMP, its host-test task is `testAndroidHostTest`;
      `testDebugUnitTest` no longer exists — update any task-name mapping.)
    - **Never guess a compile task name: a KMP module's Android compile task is `compileAndroidMain`,
      not `compileDebugKotlinAndroid`** (`compileKotlinDesktop` for desktop; metadata is
      `compileKotlinMetadata` / `compileCommonMainKotlinMetadata`). `compileDebugKotlinAndroid` is the
      *Android library plugin + build type* name; against a KMP module the build fails with
      `Selection failed / Cannot locate tasks that match ':core:platform:compileDebugKotlinAndroid'`,
      which reads like a missing module but is only a wrong name. List them first:
      `./gradlew :<module>:tasks --all | grep -i '^compile'`.
    - For common code, run the actual `commonTest`, metadata, and selected non-Android target
      compile tasks that exist in the changed project.
    - **CMP resource parity: the key-set judgment is "matches `:app` for the same language", not
      "all languages match each other".** A source res may legitimately omit entries in a language
      (Android falls back to the default `values`); `composeResources` must reproduce that omission
      rather than "fix" it — supplying the missing translation would *change* what HK/TW users see.
      `tools/verify-compose-resources.py` encodes this and decodes `.cvr` with base64 padding repair,
      comparing every value verbatim against `app/src/main/res`. Self-check it on a module whose
      result is already known before trusting a green run.
    - **Do not let desktop evidence stop at "it compiles".** `compileKotlinDesktop` going green
      says nothing about whether the screen can be *assembled, rendered and fed from a database*.
      The cheapest real evidence is a headless CMP UI test in `desktopTest`: build the Room
      database with `BundledSQLiteDriver`, start Koin, set the Feature's Screen as content, and
      assert on text that only exists if the data actually flowed from the DB to that row.
      Recipe and the CMP 1.12.0 traps (deprecated `compose.runtime` accessors, the removed
      `compose.uiTestJUnit4`, v1 vs v2 `runComposeUiTest`) are in
      `docs/dev/cmp-module-convention.md` §12.
    - **A UI test is worthless without a mutation check.** After it passes, break the premise (e.g.
      insert a record whose name differs from the asserted text) and confirm it **fails**.
      `assertIsDisplayed` can pass for the wrong reasons; only a failing mutation proves the
      assertion is wired to the real data path.
    - **"CMP-converted" does not mean "renderable on desktop".** `:core:designsystem` components
      read `LegadoTheme.colorScheme` → `LocalLegadoColorScheme`, whose default is
      `error("No ColorScheme provided")`, and the provider (`ThemeComponents`) lives in the
      Android-only `:core:ui`. So a Feature module can compile for desktop and still throw
      `IllegalStateException` the moment it renders. M1-4 fixed this by lifting the two *pure*
      mapping functions (`ColorScheme.toLegadoColorScheme`, all of `Typography.kt`) into
      `core/designsystem/commonMain` and supplying a minimal `DesktopTheme` from the host.
      When a screenshot/test crashes on a missing CompositionLocal, suspect this, not your theme call.
    - **Nav3 splits by layer, and its UI layer does not exist off Android.** Measured by `javap`
      on the published artifacts (not inferred): `navigation3-runtime` ships a real desktop jar
      plus iOS native variants — `NavKey` / `NavBackStack` / `NavEntry` / `entryProvider` are
      shared, and `NavBackStack(vararg)`'s constructor is **public**, so a non-Android host can own
      a back stack without `rememberNavBackStack` (which additionally needs
      `SavedStateConfiguration` + `@Serializable` keys — only needed for back-stack restoration).
      But `navigation3-ui`'s desktop variant is `navigation3-ui-jvmstubs`, which contains **only**
      `NavDisplay.transitionSpec` / `popTransitionSpec` / `predictivePopTransitionSpec`: there is
      **no `NavDisplay(…)` composable**, so that call does not even compile off Android. And
      `lifecycle-viewmodel-navigation3` is android-only. Entry-scoped ViewModels are still reachable
      off Android because the KMP `ViewModelStore` **and its `clear()`** are public, while
      `ViewModel.clear()` itself is internal (`clear$lifecycle_viewmodel`) — so a host must hold
      `destination -> ViewModelStore` and clear on pop. ⇒ A shared Nav3 graph means "shared runtime
      state machine + per-platform rendering layer", never "copy `MainNavGraph`".
      Always judge artifact availability from the **jar**, never from `.module` metadata:
      a `desktopApiElements-published` entry can point at stubs, and a cached `.module` does not
      mean the jar was ever fetched (`curl` it from `dl.google.com/dl/android/maven2/...`, then
      `javap`). Start with the thinnest possible two-destination host: cross-Feature navigation
      costs a whole second Feature's platform injection surface (e.g. a second JSON parser whose
      semantics cannot be proven equal to the GSON facade), which is not what a navigation probe
      is for.
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
