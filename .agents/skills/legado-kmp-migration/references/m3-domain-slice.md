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
| `AiArtifact` | none — `:app` AI tool repo + reader AI delegate | **none** | **done (M4-3)** — first `Flow` port method; first *added* (not moved) port method; `:core:data` turned out to be a consumer too |
| `AiChatConversation` / `AiChatMessage` | yes — the `:app` AI chat feature (VM + sheets + use case) | **none** | **done (M4-4)** — heaviest implementation so far (5 branches); **two entities ⇒ one mapper file each**; surfaced a latent **serialization-plugin** bug in `:core:model` (see below) |

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

**A `Flow` port method forces a coroutines dependency in the pure module.** Up to M4-2 every
`domain/ai` port method was `suspend`, which the stdlib alone covers — so `domain/ai` compiled
with only `:core:platform`. M4-3 added `observeBookArtifacts(...): Flow<List<AiArtifact>>`, and
`Flow` lives in `kotlinx-coroutines-core` ⇒ the module needs
`implementation(libs.kotlinx.coroutines.core)` (`domain/rules` declares it for the same reason).
The implementation side then **must map inside the stream** — `dao.observeX().map { it.toDomainList() }`
— not return the DAO's `Flow<List<Entity>>` unchanged: the type system rejects the latter, but the
deeper point is the mapping must run **on every emission**, not once. Test the mapping by
driving the fake DAO's flow, not by calling `first()`.

**Sink `withContext` verbatim — including its absence.** M4-1 / M4-2 implementations each wrapped
every method in `withContext(Dispatchers.IO)`, so the shared implementations copied it. M4-3's
repository was **bare DAO delegation** (Room `suspend` DAOs schedule themselves), so the new
implementation imports no `Dispatchers` at all. M4-4 went back the other way: its repository
wrapped *every* non-`Flow` method, and it matters most there — the branch logic chains several
DAO calls (read siblings → rewrite each → insert the new row → bump the conversation timestamp),
and none of that should run on the caller's dispatcher. "Align with the previous slice" would
have silently added or dropped a dispatch hop. Decide per slice by reading the code being moved.

⚠️ **A `@Serializable` class only gets its serializer if *its own module* applies the
serialization compiler plugin.** This is not a migration step so much as a trap that the move
*creates*, and M4-4 hit it live. `AiMessageParts.kt` (`@Serializable sealed interface
AiMessagePart` + six subclasses + `AiMessagePartJson`) was created in `:app`, which applies
`org.jetbrains.kotlin.plugin.serialization`. Commit `86c7428d24` **moved** it into `:core:model`
— and `:core:model` never applied the plugin. The upstream plugin does not generate serializers
for a downstream module's classes, and `legado.kmp.library` does not carry it.

The failure mode is what makes it dangerous, so learn the two signatures:

1. **It compiles cleanly.** `@Serializable` without the plugin is not a compile error; you just
   get no generated code. Confirm by looking for artifacts: with the plugin,
   `build/classes/kotlin/<target>/...` contains `X$$serializer.class` for each `@Serializable`
   class. Absent ⇒ broken, regardless of a green build.
2. **It fails at runtime, and one half fails *silently*.** `encode` throws
   `SerializationException: Serializer for subclass 'X' is not found in the polymorphic scope of
   'Base'`. `decode` — if it wraps `runCatching { … }.getOrElse { emptyList() }`, as
   `AiMessagePartJson` does — swallows that exception and returns an **empty list**. So a chat
   message's parts decode to nothing: no crash, no log, content quietly gone.

The reason it survived from `86c7428d24` until M4-4 is the general lesson: **this path had zero
test coverage**, and the containing module was not in the test-count baseline at all, so nobody
would have noticed the guardrail's absence either. When a move crosses a *plugin/config* boundary
rather than just a package boundary, add the check and the test as part of the move:

- Add `alias(libs.plugins.kotlin.serialization)` to the destination module (and sweep for other
  modules holding `@Serializable` without it — `grep -rl '@Serializable' <mod>/src` × check its
  `build.gradle.kts`).
- Add a round-trip test **in the module that now owns the classes**, and wire that module into
  `tools/count-test-results.py` in the same slice, so the guardrail cannot be deleted unnoticed.
- The round-trip test's real job is to be the *only* thing standing between this config and
  silent data loss; when it goes red, suspect the plugin before touching the JSON logic.
  `host/desktop` still holds one `@Serializable` file without the plugin — deliberately deferred
  and documented in `DesktopRoute.kt`, so leave it alone and read that note first.

**One mapper file per entity, because of JVM platform declaration clash.** M4-4 sunk two
entities at once. Two `internal fun List<XEntity>.toDomainList()` declarations — even in the same
file, even in different files of the same module — erase to the same JVM signature on one facade
class and fail to compile. M4-3 could park its single `toDomainList` inside `*RepositoryImpl.kt`;
M4-4 could not, so each entity got `<Entity>Mapper.kt` owning its own mapper + collection
overload. This also matches the existing `data/rules` layout (six domains ⇒ six files, one
`toDomainList` each). Before adding a second collection mapper, check the facade:
`grep -rn 'fun List<.*>\.\(toDomain\|toEntity\)' <module>/src`.

**A port method may be *added*, not just moved — when a caller still holds the DAO.** M4-3 was
the first slice where a `:app` class (`AiToolRepository`) called `aiArtifactDao.queryArtifacts`
directly. The choice was either (a) let `:app` do `artifact.toEntity()` and keep the DAO, or
(b) widen the port with `queryArtifacts` so the caller drops the DAO. (b) wins: it keeps mapping
inside the data layer and took `AiToolRepository` from "1 DAO + 3 gateways" to "0 DAOs + 4
gateways". A constructor-injected DAO is **not** a G4 hit (only `appDb.`-anchored access is), so
this widening is gate-neutral. Widen only when a real caller exists — never pre-emptively.

**`:core:data` can itself be a consumer of the port you are sinking.** Every earlier slice's
consumers were in `:app` or `feature/*`. M4-3 broke that: `:core:data`'s own
`domain/usecase/AiTaskManager.kt` imports the gateway and the entity. After deleting the old
gateway the build failed in `:core:data`, not `:app`. Two consequences:
- Before deleting anything, grep the whole repo (`core/*/src`, not just `app/src`) for the
  contract and the entity.
- The fix may need a **new dependency edge in the opposite direction**: `:core:data` now has
  `implementation(project(":domain:ai"))`. That direction is legal (G1 only forbids
  core→feature/host; AGENTS.md's target graph puts domain above data abstractions). It does
  **not** mean the feature or use case should move — `AiTaskManager` is an application-scoped
  use-case orchestrator, not a repository, and is out of scope for a data-domain slice.
- Do **not** opportunistically swap its `System.currentTimeMillis()` for `systemTimeMillis()`.
  It compiles (the module already has a desktop target), the swap is behavioural noise, and a
  slice should change one boundary. (M4-2's swap was different: that file was *being moved* into
  commonMain of a shared module where the platform call genuinely is unavailable.)

## The template

1. **Domain model** — `domain/<x>/src/commonMain/kotlin/io/legado/app/domain/<x>/Xxx.kt`
   - Field-for-field copy of the entity. In the **rules** domain every field is `var` — those
     models are fed through `JsonCodec` (Gson) by reflection on import / paste / single-object
     import, and a `final` field behaves differently on JVM vs ART with **no unit test covering
     it**. But do not generalise: the AI domain's entities are all-`val` (no reflective
     round-trip), and `AiPromptPreset` / `AiMemory` / `AiArtifact` copied that. Follow the entity
     in front of you.
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
| M4-2 | `AiMemory` | `3f9fb54d16` |
| M4-3 | `AiArtifact` | `f81c4813c8` |
| M4-4 | `AiChatConversation` / `AiChatMessage` | `c5fd859da1` |
| M4-5a + M4-5b | *(platform prerequisites: `Digest.md5` + `nameUuidFromBytes`)* | `a113b9130d` |
| M4-5c | `AiProviderProfile` / `AiModelProfile` / `AiTaskPreset` | pending |

Per-slice test counts in `:data:ai`: M4-1 7, M4-2 11 (7 mapper + 4 impl), M4-3 16
(9 mapper + 7 impl), M4-4 35 (8 + 9 mapper + 18 impl), M4-5c 74 (9 + 10 + 9 mapper + 46 impl)
⇒ directory total **143**.

M4-5c closes the AI domain, so the next sink target is no longer an AI entity. The blocking
item is `data:database` (Room's sole owner): `entities/BaseSource.kt` carries 23 `coreProvider`
references on its own, so moving it trips G4's "a new area must be zero" rule.

### When a mapper test is not enough: the Impl behaviour test

M3-1～M4-1 implementations are **pure DAO delegation** — each method body is a single
`dao.xxx()` call — so the mapper equivalence baseline is sufficient. M4-2's
`AiMemoryRepositoryImpl` is not: `upsert` overwrites `updatedAt` with the current time
before writing, and `getForPrompt` concatenates global + conversation memories with a
blank-id short circuit. None of that lives in the mapper, so **no mapper case can catch its
removal**. The test decision rule:

- If the Impl contains a `copy(`, a conditional branch, or a composition of several DAO
  calls ⇒ add `XxxRepositoryImplTest` next to the mapper test.
- **Also add one when the port has a `Flow` method that the mapper test cannot reach.** M4-3's
  `AiArtifactRepositoryImpl` is pure delegation by the rule above, so it looked like a
  mapper-test-only slice. It is not: `observeBookArtifacts` maps *inside* the stream, and
  `AiArtifactMapperTest` only exercises `toDomain` / `toEntity` / `toDomainList` — it never
  drives the flow. Replacing `.map { it.toDomainList() }` with `.map { it as List<AiArtifact> }`
  **compiles** (one unchecked-cast warning) and passes all nine mapper cases, yet throws
  `ClassCastException` on every real emission. Drive the fake DAO's flow with **several
  emissions** and collect them all — a single `first()` only proves the first one mapped.
- The general form of the rule: **the mapper test only covers the functions it calls.** Before
  declaring "mapper test is enough", list the port's methods and ask which ones no test
  executes. A method with no coverage is where the silent breakage will be.
- Use a hand-written fake DAO (plain Kotlin class implementing the Room `@Dao` interface;
  it never touches the Room runtime) and `runBlocking`, **not** `runTest` — the module only
  depends on `kotlinx.coroutines.core`, and a test file is not a reason to add a new
  test dependency.
- Pick inputs that **discriminate implementations** (blank vs non-blank conversation id;
  multiple elements to pin ordering), not merely 'returns something non-empty'.
- **One Mapper file per entity**, even when several entities are sunk behind one port. M4-5c
  moved three entities that share a single `AiProfileGateway`, and put each in its own file:
  two `List<XEntity>.toDomainList()` extension functions in the same file erase to the same
  JVM facade signature ⇒ *platform declaration clash*. Self-check with
  `grep -rn 'fun List<.*>\.\(toDomain\|toEntity\)'`.

**Companion-object constants on the entity are a second, invisible copy.** `AiArtifact` (M4-3)
is the first sunk entity with a `companion object` four `STATUS_*` constants. The DAO's `@Query`
string-interpolates the **entity's** `STATUS_SUCCESS` (it becomes a SQL literal), while `:app`
switch branches were migrated to the **domain model's** constants. Both definitions coexist and
nothing fails to compile if their values drift — the "successful artifacts" query and the UI
branch would simply desync. So the domain model gets its own `companion object` with the same
values, and the mapper test carries a case asserting each constant **against the entity's** and
against its literal (`0/1/2/3`). Do this for any entity whose constants are referenced by colour
of code rather than by a shared declaration.

Milestone-level blocker behind these slices is `data:database` (the single Room owner):
`entities/BaseSource.kt` alone carries 23 `coreProvider` hits, and `:app` still has 8 files
importing the `ReplaceRule` entity plus 8 direct `replaceRuleDao` call sites.

### Reaching the platform from a `commonMain`-only module

`data/<domain>` has **only** `commonMain`. When the implementation you are moving used an
`:app` facility, the move fails at compile time and you need a shared-layer equivalent.
M4-5c hit two distinct shapes:

- **`GSON` → `JsonCodec`.** The `io.legado.app.utils.GSON` facade lives in
  `core/data/src/androidMain` and is unreachable from `commonMain`; `:core:platform`'s
  `JsonCodec` (`expect object`, with android + desktop actuals) exposes the same Gson
  configuration. Decide by **comparing the configurations line by line**, not by the name:
  `JsonCodec` matches `INITIAL_GSON` exactly (the `MapDeserializerDoubleAsIntFix` type adapter,
  the `Int`/`String` deserializers, `ToNumberPolicy.LONG_OR_DOUBLE`, `disableHtmlEscaping`,
  `setPrettyPrinting`); the only delta is the seven **rule-type** deserializers `GSON`
  registers, and they cannot apply to this domain's payloads (`AiGenerationParams`,
  `AiTaskRuntimeOptions`, a plain `Map`). Swap only the call sites (`toJson` /
  `fromJsonObject` / `decodeAnyMap`) and behaviour stays identical.
- **A platform capability with no fallback ⇒ constructor parameter injection.**
  `stableModelId` reproduces `java.util.UUID.nameUUIDFromBytes`, whose MD5 is a platform
  primitive. Per AGENTS.md the dispatch is by "is there a sensible fallback": none ⇒ inject
  the parameter (`AiProfileRepositoryImpl(dao, digest: Digest)`, so omitting it is a *compile*
  error); some ⇒ an injection point whose `current` is nullable; display-only ⇒ a
  CompositionLocal with a default. The algorithm itself stays in `:core:platform` as a plain
  top-level function (`nameUuidFromBytes(bytes, digest)`) — **not** `expect/actual`, because
  its body contains no platform API; only its dependency does.

⚠️ When the value you compute is **persisted**, look for a compatibility boundary and pin
it. `stableModelId`'s output lands in `ai_model_profiles.id` and is queried, so its byte
semantics are frozen: `toString().replace("-", "")` and the MD5 algorithm cannot change even
though MD5 is cryptographically broken — a change shows up as "the model list is empty after
upgrading". Freeze it with a hard-coded vector, and add a case asserting the digest **input**
(`"$providerId:$modelId"`) so a separator change cannot slip through.

### Mutation verification: run it alone, and make it restore on any exit

Every slice closes with N rounds of "mutate the source → the new tests must go red → restore →
green". The rounds are only meaningful if the harness is trustworthy, and M4-4 produced two
rules worth carrying forward. The reusable scripts live in `C:/Users/www13/legado-verify/` as
`m4-3-mutate.py` / `m4-4-mutate.py`.

**Never run it concurrently with another Gradle build.** It rewrites a source file, runs a
Gradle test task, then restores — so for the duration of each round the working tree is
deliberately broken. A second build touching the same module (a full verification set, a
compile probe) reads the mutated file and fails. M4-4 lost time to exactly this: a full
verification run reported **7 failing tests in `:data:ai`**, all `title` coming back empty,
which looked like a real regression in the slice — the source was fine; it was the mutation
leaked into a concurrently-running build. They also race on the same output directory, so each
can clobber the other's `build/test-results/*.xml` and make counts meaningless. Guard the
script with a lock file, and if you ever see a small cluster of same-symptom failures in one
module, check `git diff` on the file before believing them.

**Restore must survive being killed.** Because the working tree is broken *by design* between
mutate and restore, a `KeyboardInterrupt` / `SIGTERM` / crash in that window leaves the mutation
on disk — and a *new module's* files are untracked, so `git diff` shows nothing and can't warn
you. M4-4's round-1 `title = ""` survived a kill and would have been committed. So:

- Keep an explicit pending-changes map and `try/finally` restore per round (as before).
- **Also** restore from an `atexit` hook and from `SIGINT`/`SIGTERM` handlers. `finally` alone
  does not run when the process is signalled.
- Re-assert every anchor hits **exactly once** before starting (a symmetric `toDomain`/`toEntity`
  pair makes each field name appear twice — anchor on the function signature to pick a direction,
  and when the anchor spans several field lines, include the lines *between* the signature and
  the field you target, since they must match verbatim).
- Put the whole file's line endings back on restore: write CRLF (repo-wide policy), and read
  with `newline=''` then normalise `\r\n` → `\n` before comparing, or every `\n` anchor misses.

**A surviving mutation is a finding about your test, not just about the code.** M4-4 got 7/8 and
the failure was informative: mutating `partsJson = partsJson` → `partsJson = partsJson.trim()`
stayed **green**. The test meant to guard that field did exist and was well argued — "use a
string that is not valid JSON, so a stray `decode`/`encode` would normalise it" — but its literal
was `"not-a-json-at-all { unbalanced"`, which has **no leading or trailing whitespace**. So it
discriminated against decode/encode but not against `trim()`. Widening the fixture to
`"  not-a-json-at-all { unbalanced  "` made the same round go red with no production change.

The generalised rule, which belongs next to "pick inputs that discriminate implementations":
**when a round survives, the input failed to separate the mutant from the original — go widen the
fixture, do not weaken or drop the round.** Concretely, ask what *minimal* edit your assertion
would tolerate, and make the input sit exactly on that boundary. For a `String` field that is
"copied verbatim", that means whitespace at both ends plus a non-normalisable shape; for a
`List`, that means more than one element in a non-sorted order; for a nullable field, `null`
(not just a non-null value).

Also note which rounds describe *implementation* logic rather than mapping — M4-4's rounds 6–8
(`branchIndex` source, the sibling-deselect loop, delete ordering) are only catchable because an
`Impl` behaviour test exists. If every mutation you can think of targets mapper fields, that is
itself a signal that the slice's risky logic is untested.

### The green that isn't: `UP-TO-DATE` test tasks and JUnit 4's `void` rule

M4-5c's first verification pass reported `:data:ai:desktopTest` **green**, and it was false: the
task was `UP-TO-DATE`, so not a single test executed. A clean rebuild went red. Two rules follow.

- **A green test task means "not failed", never "ran".** Use `clean` (cross-module moves need it
  anyway) or `--rerun-tasks` before believing a test result — and always for a slice that adds a
  test class, since the new class may not have been compiled when the earlier green was recorded.
- **JUnit 4 requires `@Test` methods to return `void`.** Kotlin's expression-bodied
  `fun x() = runBlocking { ... }` infers its return type from the lambda's last expression, and
  `assertFailsWith` returns the exception while `assertNotNull` returns `T` — so the class is
  rejected with `InvalidTestClassError` and the report shows **one** `initializationError` with
  **zero** real cases. Symptom to recognise: `tests=1 fail=1` for a class you wrote dozens of
  cases in. Fix by making the last statement return `Unit` (`assertTrue` / `assertEquals`), or by
  using a block body (`fun x() { runBlocking { ... } }`). `assertTrue` returning `Unit` is why
  most of these files are accidentally fine.

And when an `Impl` test assertion fails, **check the expectation against the pre-migration
source before touching the implementation**: `git show HEAD:<old-path>` works even while the
file is deleted in the working tree, and settles the question in seconds. In M4-5c,
`setDefaultModel`'s returned config carries the **preset's** id, not the model's — the verbatim
move was correct and the freshly written assertion was wrong.


## Lifting a UI component into `:core:designsystem` (M5)

Domain sinking is not the only move — M5 also lifts UI components from `:core:ui` (Android-only)
into `:core:designsystem` (`commonMain`, registered `cmp`). Criteria measured in M5-1a:

- **Mechanical gates for "can move verbatim"** (same as M1-3i): zero `android.*`, zero `R.`,
  zero Android-only Compose API (`LocalContext` / `LocalConfiguration` /
  `androidx.compose.ui.res.*` / `AndroidView`); closure entirely inside designsystem.
  material3 **Expressive** APIs (`labelSmallEmphasized`) do exist in desktop CMP material3 1.9.0
  — verify by compiling, do not assume.
- **Keep the package name** ⇒ every caller's import stays untouched, provided the caller already
  depends on `:core:designsystem` (`:app` and `:core:ui` both do). Use `git mv` so git records a
  rename and review sees it.
- **Do not assume the G4 baseline must be lowered.** `TextCard` carries no counted
  GSON/coreProvider pattern, so the gate passed untouched. Run the gate first.
- **When a verbatim move adds no tests**, replace ordinary mutation testing with a
  **consumer-resolution mutation**: temporarily move the shared copy away and check that
  `:core:ui` / `:app` fail with `Unresolved reference 'X'`. That proves the N callers really
  resolve to the shared copy instead of a leftover duplicate — and it is far cheaper than
  fabricating a test.

### Two platform facts that change slicing decisions

- **`miuix-blur` has no desktop variant** (only `miuix-blur-android`). Compile probe:
  `top.yukonga.miuix.kmp.blur.*` is **Unresolved** on desktop, while `kmp.utils.*` and
  `kmp.shader.isRenderEffectSupported` resolve fine. Any screen calling `textureBlur` /
  `layerBackdrop` directly is a **platform island** (same verdict as the `dict` query sheet):
  leave it in `:app`, and let the androidMain Route branch on
  `ThemeResolver.isMiuixEngine`, with **both branches sharing one ViewModel/Contract**.
  Checking this first can delete most of a slice's work — in M5-1 it removed ~1100 lines of
  `MiuixUtils` / `BgEffect*` that turned out to have only miuix-screen callers.
- **designsystem `commonMain` components cannot be UI-tested standalone on desktop.**
  `LegadoTheme.typography/colorScheme` are `staticCompositionLocalOf { error(...) }` and the
  only `provides` sites live in `:core:ui` (`ThemeComponents.kt` /
  `ThemeColorSchemeOverride.kt`). `runComposeUiTest` therefore fails with
  `IllegalStateException: No Typography provided`. Building a `LegadoTypography` (24 fields,
  no defaults) just to satisfy it would duplicate the whole theme for a test — forbidden.
  Accept that shared-layer UI verification **stops at compile + gates**, with rendering
  evidence owned by the Android side. (`smoke:compose-desktop-probe` passes only because it
  renders a self-made component that ignores `LegadoTheme`.)

### Choosing a slice: audit the closure, not the line count

`feature-catalog.md` described `ui/about` as "small boundary, good first relocation sample".
It is 7 files / 1442 lines with 4 `android.*` imports — but its dependency closure needs
**1.5x more** than the screen itself (`MarkdownBlock` 894 lines, `FileDoc` with 23 callers and
deep SAF coupling, plus three platform capabilities in the ViewModel). **Draw the dependency
closure before committing to a slice; do not pick pages by line count.** Catalog notes written
in an earlier phase are hypotheses, not measurements — correct them in place when disproved.

### Lifting a component that *does* touch platform APIs (M5-1b)

`MarkdownBlock` had three Android couplings. The audit predicted a new "silent clipboard"
contract. **The contract was not needed** — that is the lesson:

- **Grep the shared layer for an existing exit before designing a contract.** The silent
  clipboard write already existed: `LocalClipboard.setClipEntry(plainTextClipEntry(label, text))`
  (`PlainTextClipEntryFactory` in designsystem `ui/util`) is a pure write with no toast, exactly
  equivalent to `ClipData.newPlainText` + `setPrimaryClip`. It is **deliberately not merged**
  with `:core:platform`'s `Clipboard.setText`, which shows a "copied" toast — that one's own KDoc
  says a quiet variant should be a separate capability, and `plainTextClipEntry` *is* it.
- **Deleted `else` branches often reveal the callback already exists.** `Intent(ACTION_VIEW)`
  for external links was only a fallback — `onClickLink: ((String) -> Unit)?` was already a
  parameter, used for internal links. Likewise images: `LocalMarkdownImageHandlers.current.onClick`
  was already there. In both cases the fix is to drop the platform fallback, not to add an
  abstraction.
- **`R.drawable.*` → `Icons.Default.*`** is the cheap path for an icon with a material
  equivalent; only genuinely custom artwork needs `composeResources`.
- **`markdown-jvm` → `markdown`**: check the Maven Central `.module` file for
  `metadataApiElements` + the native variants before trusting a `-jvm` suffix. Version-catalog
  coordinates written for a JVM module can often just drop the suffix and become commonMain-safe.
- **`Clipboard.setClipEntry` is `suspend`** (new CMP API). A call inside `clickable { }` needs
  `rememberCoroutineScope()` + `launch`. Existing call sites hid this because they all sat in
  `LaunchedEffect`.
- Verification for such a move is the same as a verbatim one: compile + gates + clean full set +
  **consumer-resolution mutation** (removing the shared file made all 5 consumers fail with 13
  `Unresolved reference` errors).
