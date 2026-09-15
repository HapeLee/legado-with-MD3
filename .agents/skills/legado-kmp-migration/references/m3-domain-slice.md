# M3 Domain Sink Template (`:core:data` by-domain split)

Use this reference for any slice that moves one `:core:data` repository into the
`domain/<x>` (pure) + `data/<x>` (data) module pair. Four slices (M3-1 … M3-4) walked this
exact template; treat the steps as a checklist, not a suggestion.

## What M3 is

`:core:data` holds every domain's entities, DAOs and repositories at once. M3 splits it **by
domain**, one repository per slice, into a module pair that already exists:

- `domain/rules` — **pure**: domain models + ports (`interface`). No Room, no `android.*`, no
  JVM file handles, no Gson. Purity is enforced by **import prefix** (G2), not by module type —
  so a platform `expect fun` is fine; see the default-value note in step 1.
- `data/rules` — **data**: `XxxRepositoryImpl` (takes the **DAO**) + `XxxMapper` + `XxxMapperTest`.

No new module per slice, and no facade / typealias / deprecation shim left behind in `:core:data`.

## Picking the next slice

Rank candidates by **real Feature consumer > existing test guardrail > no platform contract >
callers that die with the slice**. What is left of the rule domain, as measured:

| Candidate | Feature consumer | Guardrail | Status |
|---|---|---|---|
| `RuleSub` | none — only the `:app` RSS page (`ui/rss/subscription`) | **none** | **done (M3-5)** — the mapper test shipped with the slice |
| `TagGroupRule` | yes — tagrules group page + group-manage sheet | partial | **done (M3-6)** — the duplicated matcher was merged first (see below), then the repository sank |
| `AiPromptPreset` | none — `:app` reading AI delegate (`ui/book/read`) | **none** | **done (M4-1)** — first non-rules domain; needs its **own module pair**, see below |
| `AiMemory` | none — `:app` `AiToolRepository` (AI tool calls) | **none** | **done (M4-2)** — reuses the M4-1 module pair; port keeps only **3 of 8** methods; **needs an Impl behaviour test**, not just a mapper test (see below) |

A zero-guardrail candidate is not disqualified, but then the mapper test is **part of the
slice, written before the move** — not a bonus added afterwards.

**Merge duplicated semantics before moving.** `TagGroupRule` was the one candidate whose matching
logic had two live copies: `:core:data`'s `TagGroupRuleApplier` (full sweep, in a transaction) and
`:app`'s `applyTagGroupRulesForBook` (single book, called from `Book.save()`). The two could only
be kept in sync by a comment. A one-line delegation —
`applyTagGroupRulesForBook(book) = runBlocking { applier.applyToBook(book) }` — removed the mirror
*before* the module move, so the slice ends with exactly one implementation. The deleted path had
**zero** tests, so the delegation shipped with a new `:app` case pinning both of its properties:
single-book scope, and **no DB write** (`applyToBook` calls the shared private helper with
`persist = false`; `Book.save()` persists afterwards). Budget for that guardrail, and declare it —
it lands in the **main** test set (`BASELINE_MAIN` 712 → 713).

Note the direction of the merge: the mirror was on the `:app` side, so the shared implementation
stays where its dependencies already are (`:core:data`, which also owns `Book` / `BookGroup` /
`BookGroupMutationRepository`). Moving it out would have created a cycle
(`BookGroupMutationRepository` → `TagGroupRuleApplier` → back).

**A new domain needs its own module pair.** `domain/rules` + `data/rules` are named after the
*rules* domain — do not pour a second domain into them. `AiPromptPreset` (M4-1) got
`domain/ai` + `data/ai`, and the two registrations are a **pair**: `include ':domain:ai'` in
`settings.gradle`, **and** `"domain/ai" to "pure"` / `"data/ai" to "data"` in the root
`build.gradle.kts` `kmpModuleTypes` map (an unregistered KMP module is treated as `pure` by G2).
G4 tolerates **no new region**, so the new modules' sources must contain zero `appCtx` / `appDb` /
`GSON` / `coreProvider` hits — the gate reporting "zero change" is the evidence.

**Port naming: keep the existing contract name.** The rules ports are `XxxRepository`, but this
repo also has 60+ `XxxGateway` contracts under `core:data`'s `domain/gateway` (the AI domain
alone has `AiArtifactGateway` / `AiMemoryGateway` / `AiChatGateway` …). When the slice is really
*"an existing contract sinks, with its payload type swapped to the domain model"*, keep the name:
renaming makes it churn and forces the injected property to be renamed too. The
"only methods that have a caller" rule applies either way — `AiPromptPresetGateway.savePreset`
had zero callers and died with the slice.

Also check whether the entity's compatibility surface is already sealed. `TxtTocRule` looked
expensive (custom GSON `JsonDeserializer`) until the M1-3y contract was located: the cost had
already been paid, and the slice only added `.map { it.toDomain() }` at the contract end.

## The template

1. **Domain model** — `domain/<x>/src/commonMain/kotlin/io/legado/app/domain/<x>/Xxx.kt`
   - Field-for-field copy of the entity, and **every field is `var`**. These models are fed
     through `JsonCodec` (Gson) by reflection on import / paste / single-object import, and a
     `final` field behaves differently on JVM vs ART with **no unit test covering it**.
   - `equals` / `hashCode` and field mutability: **copy whatever the entity does — do not infer
     it from the previous slice.** Five of the six rule entities override `equals`/`hashCode` to
     compare the **primary key only**; `RuleSub` overrides neither, so its comparison is the
     data-class **all-field** one, and its `id` is a `val` where the others are `var`.
     Consequence for step 5: with key-only comparison a mapper test **must** assert every field —
     a whole-object `assertEquals` passes even when a field is dropped. With all-field comparison
     that assertion is valid and strictly stronger, and you additionally need a **negative** case
     (two models differing only in e.g. `name` must not be equal) so nobody later "aligns" the
     model with the other five and silently forks the semantics.
   - Copy the entity's default **verbatim, even when it calls a platform function**.
     `RuleSub.id` / `RuleSub.update` — and every other rule model's `id` — default to
     `systemTimeMillis()` from `:core:platform`. That is allowed in a pure module: G2's pure
     policy keys on **import prefixes** (`android.*`, `java.io.File`, `kotlin.jvm.*`,
     `androidx.*`, jsoup / gson / rhino / okhttp); `io.legado.app.core.platform` is not on
     that list, and `domain/rules` has had `implementation(":core:platform")` since M3-1.
     Do **not** "purify" the model by inlining a clock call or dropping the default — that
     silently changes id generation. There is no "the entity stays the creation entry point"
     rule to preserve here.
   - KDoc the traps the entity hides: surprising defaults (`serialNumber = -1`, not `0`),
     field naming (`enable`, not `enabled`), nullability (`example: String? = null`).

2. **Domain port** — `domain/<x>/.../XxxRepository.kt`
   - `interface` with plain methods, carrying **only the methods that have a caller**. Methods
     with zero callers are deleted with the slice (`flowSearch`, `enabled`,
     `RuleSubDao.maxOrder` all went this way).
   - Any `runBlocking` on the old repository becomes **`suspend`**. `compileCommonMainKotlinMetadata`
     is SKIPPED for every module, so a JVM-only synchronous call survives in `commonMain`
     unnoticed until the module gains a desktop target. Callers are already `suspend` + IO, so
     the change is behaviour-preserving.
   - Document in-place mutation in KDoc (`saveOrder` sets `serialNumber = index + 1`, starting
     at **1** — index 0 never appears).

3. **Mapper** — `data/<x>/.../XxxMapper.kt`
   - `fun XxxEntity.toDomain(): Xxx` + `fun Xxx.toEntity(): XxxEntity`, every field assigned
     explicitly. No `copy()`-style shortcuts and no normalisation of nullable fields
     (`example = null` must not become `""`).

4. **Impl** — `data/<x>/.../XxxRepositoryImpl.kt`
   - Takes the **DAO** as a constructor parameter (`TxtTocRuleDao`), not `AppDatabase`. Yes, it
     still reaches into `:core:data` during the transition, and needs an explicit `:core:model`
     dependency (`splitNotBlank` is not transitive).
   - Method bodies move verbatim; only the entity↔domain mapping changes.
   - ⚠️ `vararg T` inside a function body is `Array<out T>`: supply **both** collection overloads
     (`List<Xxx>.toEntityArray()` and `Array<out Xxx>.toEntityArray()`).

5. **Mapper test** — `data/<x>/src/commonTest/.../XxxMapperTest.kt`
   - One case per field in both directions, plus: the nullable field passing `null` through,
     round-trip losslessness, the default-value set, "equality keys on the primary key only",
     and ordering preserved across every collection overload.
   - Baseline accounting: a pure file move must leave the **main test set byte-identical**;
     added cases land under `data:rules` and must be declared both in
     `tools/count-test-results.py` (`BASELINE_ALL`) and in the commit message.

6. **Delete the old repository** — `git rm core/data/.../data/repository/XxxRepository.kt`.
   No facade, no typealias, no deprecation shim.

7. **Migrate the consumers**
   - CMP Feature `commonMain`: entity → domain model, repository → port. The entity must stop
     appearing in the Feature's public API, and `build.gradle.kts` gains
     `api(project(":domain:<x>"))`.
   - `:app` consumers: imports, plus `DefaultData.xxx.map { it.toDomain() }` in preview / data
     paths.
   - Host DI (`appModule.kt`): replace `singleOf(::XxxRepository)` with an explicit binding
     `single<XxxRepository> { XxxRepositoryImpl(get<XxxDao>()) }`.
   - **Compatibility-contract boundary**: when the format owner is the GSON facade
     (`TxtTocRule`), the mapping belongs in the contract impl —
     `parseTxtTocRules(json).map { it.toDomain() }` — and nothing above it changes.
   - **Never move a `JsonDeserializer` into the domain model.** `Restore.kt` deserialises
     backups **as entities**, so a key promotion moved onto the model (`rule` → `chapterRule`)
     would silently drop the legacy key. The backup format does not change in M3.

8. **Close the ratchets**
   - G4 counts `import io.legado.app.utils.GSON` **per directory**. Deleting that import from a
     file lowers the count, so the same entry in `gradle/architecture/legacy-baseline.txt` must
     be lowered in the same change. A raised baseline is never an acceptable fix.
   - Update `tools/count-test-results.py` comments and `BASELINE_ALL`, then state the delta in
     the commit message.

## Verification recipe

```bash
./gradlew clean                     # standalone — see the SKILL.md warning about chaining
./gradlew checkSharedPurity checkModuleDependencies verifyConfigArchitecture \
  checkLegacyArchitecture :app:compileAppDebugKotlin testAppDebugUnitTest assembleAppDebug --continue
./gradlew :data:rules:desktopTest :domain:rules:testAndroidHostTest   # per affected module
./gradlew :app:assembleAppDebug
python tools/count-test-results.py  # main set byte-identical, all-set +N
git diff --check
```

- Cross-module moves and dependency-graph changes **require** a clean rebuild.
- ⚠️ **Always exclude `:smoke:room-kmp-probe:testAndroidHostTest`** from root-level test runs
  (`-x :smoke:room-kmp-probe:testAndroidHostTest`). That module's `RoomKmpProbeTest` is an
  **abstract** base class whose only concrete subclass lives in `desktopTest`, so
  `androidHostTest` discovers zero tests and Gradle 9's `failOnNoDiscoveredTests` fails the
  build. Nothing to do with your slice (`smoke/` is untouched), but it turns a green run into
  `BUILD FAILED`. Excluding it: 335 tasks `BUILD SUCCESSFUL`.
- One FAILED task fails the whole build even though `--continue` keeps executing the rest ⇒
  grep `Task .* FAILED` instead of trusting the last line. The test-count script is unaffected
  (result directories are still written).
- Mutation-verify the mapper test: 3 targeted mutations, each turning exactly one case red,
  then restore via `try/finally`. ⚠️ The pattern string must include the **function signature
  prefix** — matching `) = TxtTocRule(` against an expression-bodied mapper finds **0 hits**,
  the script skips that mutation, and a green run proves nothing.
- `tools/count-test-results.py` reports "empty result dirs / count not trustworthy" when only
  some modules were run. Run the full set first — M3-2 reported a phantom −39 this way.
- New/changed source files are **CRLF** in this repo (Write emits LF): normalise before staging.

## Measured slices

| Slice | Domain | Commit |
|---|---|---|
| M3-1 | `ReplaceRule` | `fb64d2be95` |
| M3-2 | `HighlightTagRule` | `9d21369475` |
| M3-3 | `DictRule` | `df00c585d8` |
| M3-4 | `TxtTocRule` | `8a0b7cd1c9` |
| M3-5 | `RuleSub` | `0555a0acc0` |
| M3-6 | `TagGroupRule` | `5406f151fb` |
| M4-1 | `AiPromptPreset` | `00e841ed46` |
| M4-2 | `AiMemory` | pending |

### When a mapper test is not enough: the Impl behaviour test

M3-1～M4-1 implementations are **pure DAO delegation** — each method body is a single
`dao.xxx()` call — so the mapper equivalence baseline is sufficient. M4-2's
`AiMemoryRepositoryImpl` is not: `upsert` overwrites `updatedAt` with the current time
before writing, and `getForPrompt` concatenates global + conversation memories with a
blank-id short circuit. None of that lives in the mapper, so **no mapper case can catch its
removal**. The test decision rule:

- If the Impl contains a `copy(`, a conditional branch, or a composition of several DAO
  calls ⇒ add `XxxRepositoryImplTest` next to the mapper test.
- Use a hand-written fake DAO (plain Kotlin class implementing the Room `@Dao` interface;
  it never touches the Room runtime) and `runBlocking`, **not** `runTest` — the module only
  depends on `kotlinx.coroutines.core`, and a test file is not a reason to add a new
  test dependency.
- Pick inputs that **discriminate implementations** (blank vs non-blank conversation id;
  multiple elements to pin ordering), not merely 'returns something non-empty'.

Milestone-level blocker behind these slices is `data:database` (the single Room owner):
`entities/BaseSource.kt` alone carries 23 `coreProvider` hits, and `:app` still has 8 files
importing the `ReplaceRule` entity plus 8 direct `replaceRuleDao` call sites.
