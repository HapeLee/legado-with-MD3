# M3 Domain Sink Template (`:core:data` by-domain split)

Use this reference for any slice that moves one `:core:data` repository into the
`domain/<x>` (pure) + `data/<x>` (data) module pair. Four slices (M3-1 … M3-4) walked this
exact template; treat the steps as a checklist, not a suggestion.

## What M3 is

`:core:data` holds every domain's entities, DAOs and repositories at once. M3 splits it **by
domain**, one repository per slice, into a module pair that already exists:

- `domain/rules` — **pure**: domain models + ports (`interface`). No Room, no `android.*`, no
  `io.legado.app.core.platform`, no Gson.
- `data/rules` — **data**: `XxxRepositoryImpl` (takes the **DAO**) + `XxxMapper` + `XxxMapperTest`.

No new module per slice, and no facade / typealias / deprecation shim left behind in `:core:data`.

## Picking the next slice

Rank candidates by **real Feature consumer > existing test guardrail > no platform contract >
callers that die with the slice**. What is left of the rule domain, as measured:

| Candidate | Feature consumer | Guardrail | What blocks it |
|---|---|---|---|
| `RuleSub` | none — only the `:app` RSS page (`ui/rss/subscription`) | **none** | add a characterization test as part of the slice |
| `TagGroupRule` | yes | partial | `TagGroupRuleApplier` and `BookExtensions.applyTagGroupRulesForBook` duplicate the semantics — merge first |

A zero-guardrail candidate is not disqualified, but then the mapper test is **part of the
slice, written before the move** — not a bonus added afterwards.

Also check whether the entity's compatibility surface is already sealed. `TxtTocRule` looked
expensive (custom GSON `JsonDeserializer`) until the M1-3y contract was located: the cost had
already been paid, and the slice only added `.map { it.toDomain() }` at the contract end.

## The template

1. **Domain model** — `domain/<x>/src/commonMain/kotlin/io/legado/app/domain/<x>/Xxx.kt`
   - Field-for-field copy of the entity, and **every field is `var`**. These models are fed
     through `JsonCodec` (Gson) by reflection on import / paste / single-object import, and a
     `final` field behaves differently on JVM vs ART with **no unit test covering it**.
   - `equals` / `hashCode` key on the **primary key only** (usually `id`; `DictRule` uses
     `name: String`). Consequence: mapper tests must assert **every** field — never `==` alone.
   - No default value that needs a platform function. `RuleSub.id` / `RuleSub.update` default
     to `systemTimeMillis()` (`:core:platform`), which a pure model cannot reference; the
     entity stays the creation entry point.
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
| M3-4 | `TxtTocRule` | pending |

Milestone-level blocker behind these slices is `data:database` (the single Room owner):
`entities/BaseSource.kt` alone carries 23 `coreProvider` hits, and `:app` still has 8 files
importing the `ReplaceRule` entity plus 8 direct `replaceRuleDao` call sites.
