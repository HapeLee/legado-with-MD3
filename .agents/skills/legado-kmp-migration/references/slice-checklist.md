# KMP/CMP Slice Checklist

Read this reference for implementation plans, extraction work, scaffolding, or reviews.

## Before change

- Scope names exact packages/files and one target boundary.
- Existing behavior is captured by tests or a written manual parity list.
- Current callers and module edges are known.
- Imports/dependencies are classified as common-ready, contract-needed, platform-island, or unknown.
  **Run `scripts/portability-triage.py <files…>` rather than reading one file's imports** — it walks
  the whole `io.legado.app.ui.*` closure across `:core:ui` + `:core:designsystem` and marks
  `android.*` / JVM / `R` / out-of-bounds violations plus required extra module deps.
  **It covers two of three dimensions; a clean report is not sufficient to promote a component.**
  **Nothing may be called common-ready from imports alone — three dimensions must be checked:**
  1. `android.*` / `java.*` imports in the file — **plus a short list of Android-only members of
     `androidx.compose.ui.*` that carry no `android.` prefix** and are therefore invisible to an
     import-prefix scan: `platform.LocalContext`, `platform.LocalConfiguration`,
     `platform.LocalResources`, `viewinterop.AndroidView`, and `res.stringResource` /
     `res.dimensionResource` / `res.vectorResource`. Measured by unpacking the CMP desktop
     artifacts, so the negatives are trustworthy too: `ui.res.painterResource`,
     `LocalInspectionMode`, `LocalView`, `LocalLayoutDirection`, `LocalTextStyle`,
     `LocalClipboardManager`, `LocalClipboard` and `LocalUriHandler` **all exist on desktop** —
     do not flag them. M1-3i: a naive scan produced 47 hits, of which several were these false
     positives; the exact list is what made a 25-file batch reviewable. `portability-triage.py`
     now carries it, and it **strips comments before scanning** — KDoc in this repo deliberately
     names the Android API it avoided (`AppDensity.kt` says "does *not* read Android's
     `LocalConfiguration`"), which a full-text search reports as a hard dependency.
  2. out-of-bounds `io.legado.app.*` imports (note `domain.model.*` = `:core:model` is legal);
  2b. **same-package siblings — the structural blind spot of every import-based scan.** Kotlin
     top-level declarations in the same package are visible to each other **without an import**, so
     a closure walk that follows `import` lines never sees them and the file is reported `ok`.
     M1-3j: `SearchBar.kt` → sibling `AppDenseTextField.kt`. M1-3k: `lazylist/LazyList.kt` →
     sibling `lazylist/VerticalFastScroller.kt` (26 KB, itself blocked by Android-only
     `Modifier.systemGestureExclusion()`). Both passed the scan and failed the desktop compile.
     `portability-triage.py` now treats same-package declarations as closure edges and prints them
     in a dedicated section — **read that section**, and prefer moving/blocking a whole
     same-package family in one slice rather than a single file out of it.
     Also read the script's per-seed verdict: **a seed is un-movable if *any* closure member is
     `HARD`, even when the seed's own line says `ok`** (`card/GlassCard.kt` was misread this way:
     its own imports were clean, but it pulls `AppContainerBackground.kt` which does
     `BitmapFactory`/`NinePatch` nine-patch decoding — **resolved in M1-3p** by sinking exactly that
     step into the `:core:platform` `NinePatchLoader` contract; the Coil pipeline above it was
     already portable, so `GlassCard` / `CheckboxItem` / `AppCheckbox` all moved).
  3. **whether the *same library API* is present *and visible* in a non-Android source set** — two
     distinct sub-cases that lead to **opposite conclusions**:
     - **absent from the source set**: e.g. `androidx.compose.ui.platform.LocalConfiguration` only
       exists in `ui`'s `androidMain` → the code cannot be shared as-is; prefer finding another
       public entry point for the same fact (see `AppDensity` → `LocalDensity.current.fontScale`)
       or introduce a narrow contract.
     - **present in `commonMain` but `internal`**: e.g. CMP material3 **1.9.0**'s expressive family
       (`ExperimentalMaterial3ExpressiveApi` / `MaterialExpressiveTheme` / `MotionScheme` /
       `rememberBottomSheetState` / `BottomSheetDefaults.modalWindowInsets`), which fails with
       `Cannot access '…': it is internal in file`. **This is a version problem, not a platform
       one** — it is fixed by raising the dependency, not by leaving the component on Android.
       Measured release points: expressive trio public from **1.10.0-alpha05**, `modalWindowInsets`
       from **1.11.0-alpha07**, `rememberBottomSheetState` from **1.12.0-alpha03**. Do **not**
       conclude "Android-only" from this error. (Material2's `ExperimentalMaterialApi` *is* genuinely
       absent here — that one should simply have its `@OptIn` deleted.)
     Check by compiling the non-Android target, not by reasoning. **`javap` is not a valid
     judgement**: Kotlin `internal` top-level declarations still compile to `public` JVM methods
     (visibility lives in `@Metadata` and names are not mangled), so 1.9.0 and 1.12.0-alpha03 give
     byte-identical `javap` output while only the former fails to compile.
  Ask "what is this component's *transitive closure*", not "does this file look portable": a file
  with zero `android.*` can still be blocked by its dependency's source-set split.
- **Converting a whole Feature to CMP: first resolve where its `io.legado.app.ui.**` symbols are *declared*.**
  `:core:ui` is an Android-only module, while component families move into `:core:designsystem`
  `commonMain` one slice at a time — so a Feature that reads clean can still be hard-blocked by one to
  four leftovers. M1-3w's `tagrules` happened to miss them; M1-3x's `replacerules` (and, waiting behind
  it, `txttocrules` and `dict`) needed `AppTabRow`, `GroupManageBottomSheet`, `rules/RuleEditSheet` and
  `contentProcess/ContentProcessUiState` promoted first — a separate **pre-slice** whose only job is the
  residency move. The verdict is **the module that declares the symbol**, never how foundational the
  package name looks (`io.legado.app.ui.theme.*` exists in both modules). A failed lookup is not proof
  of cleanliness: same-package siblings, extension receivers and `when` positions never appear on an
  import line.
- **A new source set or package directory starts at zero on the G4 legacy ratchet.** Promoting a file
  into `commonMain`/`androidMain` turns legal imports into violations; the stale `<module>/main/...`
  entries must be deleted and any reduced counts lowered in the same change, and a raised baseline is
  not an acceptable fix. Re-home the dependency into a clean package instead
  (`ReplaceAnalyzer` → `io.legado.app.data.rules`, M1-3x) and replace `XxxProvider.current` with the
  injected contract (`koinInject<Toaster>()` inside an Android Route).
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
- **A missing Koin binding does not fail the build.** `viewModelOf(::XxxViewModel)` resolves
  constructor parameters **at runtime**, so deleting `single<SomeContract> { … }` leaves
  `:app:compileAppDebugKotlin` green — measured in M5-1c-3 by removing
  `single<BundledTextReader>` (BUILD SUCCESSFUL). Consequence: a `single<>` wiring slice cannot be
  proven by compilation alone — read the wiring, and report the runtime path (open the screen once)
  as smoke-required.
  **As of M2-8 there is a host graph-creation test** (`:app`'s `AppModuleGraphTest`), and its shape
  is worth copying:
  - **Do not use `checkModules()`.** `io.insert-koin:koin-test:4.2.2` does not resolve in this
    environment. The zero-dependency替代 is to iterate the `single<Interface> { … }` bindings and
    call `koin.get<T>()` on each (the list is derived from `appModule`; it must be extended by hand
    when a binding is added).
  - **Assert `NoDefinitionFoundException`, not "no exception".** Most bindings legitimately fail to
    *instantiate* under Robolectric (missing `AppConfigStore.init(...)`, splitties `appCtx`,
    `AppConfig.initialize`'s later steps). Treating those as failures makes the test red at a place
    unrelated to binding correctness. Checking the cause chain for `NoDefinitionFoundException`
    still catches both a missing binding and a missing *transitive* dependency, which is the actual
    failure mode. Print the environment-class failures without asserting on them.
  - **Host = `Application::class`, not the project's `App`.** Using `App` makes Robolectric run
    `App.onCreate`, which dies at `LocalConfig`'s static init before reaching Koin. With a plain
    `Application` you must call `AppConfigStore.init(app)` and `app.injectAsAppCtx()` yourself.
  - `@Config(application = Application::class, sdk = [35])` — Robolectric rejects the project's
    `targetSdk = 37` (`targetSdkVersion=37 > maxSdkVersion=36`).
- Gradle `api` exposure is intentional; other dependencies use `implementation`.
- No module is created only to match the target diagram.
- Package colocation, Android Gradle module extraction, and KMP/CMP conversion are separate
  reviewable stages; the slice does not combine all three by default.

## Source sets and platform code

- `commonMain` is compiled by at least one non-Android target.
- JVM+Android-only sharing has an explicit source-set/module owner and verified IDE/consumer
  behavior; it is not mislabeled as common.
- Platform files are in the narrowest relevant source set.
- Android services, Room/Context/URI, notifications and renderer code stay Android-side.
  (Android **resource ids** stay Android-side too, but a string *literal* needed by shared UI goes to
  `commonMain/composeResources` — see the CMP resources bullet below.)
- Rhino/JS behavior stays behind a capability boundary until another target has a compatible
  implementation.
- Shared Compose code emits callbacks/effects; host navigation and platform launchers stay outside.
- **Seam pattern for "portable component whose decoration is platform-rendered": use a nullable
  slot, not a boolean.** `AppCardSurface(itemBackground: Modifier?)` — `null` means "no such layer"
  (the plain variant) and non-null means "stack a layer and apply this modifier" (the Android
  variant passes `Modifier.appContainerBackground(Item)`). The shared layer never learns a platform
  type, and the platform side does not duplicate the surface logic. A `useItemBackground: Boolean`
  would have forced either a `CompositionLocal` (silent no-op when unprovided) or a public
  low-level API.
- **Extract theme/policy resolution into a pure function when two sides must agree.** Card
  corner-radius/border override lived inline in a private composable; hoisting it to
  `resolveCardDecoration(theme: ThemeSettings, isDark: Boolean, …)` gave one definition for both
  callers *and* made it unit-testable without a Compose runtime (a `CompositionLocal` reader is
  not). Prefer this over `expect/actual` and over duplicating the logic.
- **When a whole shared subsystem is one or two platform calls away from moving, split it into
  single-method `fun interface` contracts injected by the host** — the repo's existing pattern
  (`BigDataStore`, `SourceRuntime`, `JsExtProvider`, `ThemeSeedColors`). M1-3l: `ThemeEngine`
  touched `android.*` in exactly two places (dynamic colour via
  `dynamicLight/DarkColorScheme(context)` + `Build.VERSION.SDK_INT`, and `Context.primaryColor`)
  while blocking **four** widget files; two contracts and a `git mv` of 17 files later, the whole
  theme closure is in `commonMain` with zero new dependencies. Three rules:
  1. **Before widening or narrowing the contract, prove which branches are reachable.** Refactoring
     `Context` into a contract is not by itself a behaviour change — check each call site for whether
     the context actually participates. In M1-3l `ThemeSeedColors.primaryColor(context)` turned out
     to be **dead code** (all three call sites pass a non-null `Int`; the fourth goes through
     `forceOpaque = true`, which normalises `Transparent` to `WH`, so its `Custom` branch is
     unreachable). That measurement is what turns "risky" into "documented as unobservable".
  2. **Choose the failure semantics per contract by whether absence is legitimate.** A missing seed
     colour is a misconfiguration ⇒ `requireNotNull(...)` with an explicit message. Missing dynamic
     colour is the *normal* state on desktop/iOS ⇒ return `null` and fall back, rather than demanding
     every non-Android host inject a never-matching implementation.
  3. **The Android implementation may have to live in `:app`, not `:core:ui`.** Here the fallback
     seed colour reads `lib.theme.ThemeStore`, which `:core:ui` cannot depend on. Install it from
     `Application.onCreate` and keep the contract in the shared layer.
  4. **The contract's module is decided by the contract's TYPES, not by habit.** `:core:platform`
     is pure-KMP with zero Compose, so a method returning a Compose type cannot live there.
     M1-3o: `plainTextClipEntry` needed `androidx.compose.ui.platform.ClipEntry`, which is a CMP
     `expect class` with **no constructor in common** (Android wraps `ClipData`, desktop wraps an
     AWT `Transferable`) ⇒ the `PlainTextClipEntryFactory` + `PlainTextClipEntryProvider` seam had
     to be hosted in `:core:designsystem`, while the Android adapter stayed in
     `AndroidPlatformCapabilities` and the injection point stayed in `PlatformServices.install()`.
     Before choosing a home, grep the contract signature for Compose types.
     M1-3p is the mirror image: `NinePatchLoader.load(path): Any?` returns an **opaque payload**
     (literally the image loader's `data` parameter type) precisely so that no platform type leaks
     into the signature ⇒ it *can* live in `:core:platform`. Keep platform types out of the
     contract and the default home works; write one in and you are forced into the Compose module.
  5. **Sink the narrowest platform step, not the whole rendering layer.** For
     `AppContainerBackground`, "make the contract return a `Modifier`" would have required a
     `@Composable` contract method (no SAM lambda implementation) and pushed the *portable* Coil
     pipeline into `:app`; "make it return a `Painter`" would have rasterised the nine-patch and
     dropped Coil's crossfade/caching — a behaviour change. Only `BitmapFactory`/`NinePatch` is
     actually platform-bound, so that is all the contract covers.
  6. **Deleting an injected dependency can be equivalent to keeping it.** The old code passed
     `imageLoader = koinInject<ImageLoader>()`; `App` implements `SingletonImageLoader.Factory`
     and `newImageLoader(context) = get()` (the same Koin singleton), so dropping the parameter and
     letting Coil resolve `SingletonImageLoader` yields **the same instance** — and keeps Koin out
     of `:core:designsystem`. Verify this by reading the host's `ImageLoader` wiring, not by
     assuming.
  See `docs/dev/cmp-module-convention.md` §8 and §8.5.

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
- **`git mv` does not create the destination's parent directory.** Moving into a subdirectory the
  shared module does not have yet fails with
  `fatal: renaming '…' failed: No such file or directory` (M1-3p: `checkBox/` — the sibling
  `card/` already existed, which hides the problem). `mkdir -p` the destination first, then
  `git mv`; verify with `git status --short` that the pair is recorded as `R` and not `D`+`??`.
- **A shared declaration can be sunk into the shared layer's same-named package** when the file
  holding it cannot move. `:core:ui` → `:core:designsystem` is one-way, so a declaration both sides
  need has nowhere to live if its file still carries `android.*` (M1-3h: `LocalUseMiuixWindowPopup`
  sat in `:core:ui`'s `menuItem/RoundDropdownMenu.kt`, which reaches `Context` via
  `rememberOpaqueColorScheme` → `ThemeEngine`). Move **only the declaration** into the shared layer
  under the **identical package**: the copy left behind still resolves it from the same package
  **with no import at all**. Cross-module same-package visibility is an existing fact here, not a new
  mechanism (`core:ui`'s `ui/theme` files already reference designsystem's `LocalLegadoThemeColors`
  / `LocalAppUiConfiguration` without imports), and it does not create duplicate declarations.
- **After the move, diff the imports against the declared dependencies.** A move introduces deps that
  were previously satisfied by the old module's own set. M1-3h: `AppModalBottomSheet` uses
  `Modifier.animateContentSize`, which belongs to `org.jetbrains.compose.animation:animation` (not
  `foundation`). Foundation happens to pull it in transitively, but the repo's rule is "declare only
  what you actually use, and declare all of it" — add the alias (`compose-multiplatform-animation`)
  rather than leaning on the transitive closure.
- **Switching an android-only module to CMP coordinates buys nothing.** CMP's foundation/ui/runtime
  are *forwarders* on Android exactly like material3 (`foundation-android` just `requires`
  `androidx.compose.foundation:foundation`), so the resolved graph is byte-identical, while version
  control moves to CMP's lockstep release train. Some artifacts (`ui-viewbinding`,
  `material-icons-extended`, `constraintlayout-compose`, `accompanist-webview`, LyricViewX, Miuix
  `-android`) have no CMP coordinate at all, so the BOM cannot be retired anyway. Coordinate
  switching follows — never precedes — making a module genuinely multiplatform.
- Only libraries with a desktop/JVM variant may enter `commonMain`; decide from the artifact's
  `.module` metadata, never from "the group name says androidx". Check **Google Maven as well as
  Maven Central** (`dl.google.com/dl/android/maven2`) — androidx multiplatform variants are not on
  Central. Known answers:
  - **material3** → `org.jetbrains.compose.material3:material3` (real KMP; its **android variant
    delegates to** `androidx.compose.material3:material3`, so on Android you end up on Google's
    build). The raw `androidx.compose.material3:material3` coordinate ships only android + empty
    `jvmStubs`/`nativeStubs` variants — do not put it in `commonMain`.
  - **The version is NOT the plugin version, and material3 is an explicit pin — not a derived one.**
    Compose runtime/foundation come from `ComposeBuildConfig` (never hand-written). material3 is the
    one exception: the plugin constant `composeMaterial3Version` means "latest *stable*" and sits at
    `1.9.0`, which marks the expressive family `internal`. The convention overrides it with an
    explicit, documented, asserted constant `MATERIAL3_PIN` (currently `1.12.0-alpha03`) in
    `build-logic/.../LegadoKmpComposeConventionPlugin.kt`. Two rules follow: the pin must stay
    `>=` the plugin constant, and **the catalog value and `MATERIAL3_PIN` must move together** — the
    config-phase assertion fails otherwise. Do not "fix" a version mismatch by editing only one side.
  - **How to declare**: `implementation(compose.material3)` does resolve but is
    `@Deprecated("Specify dependency directly")`; `compose.dependencies.material3` does **not**
    resolve from a module that only applies the convention (no type-safe accessor is generated for
    transitively applied plugins). Use direct coordinates.
  - **icons**: use the repo's `androidx.compose.material:material-icons-extended:1.7.8` (it has a
    jvm variant; Google's publication stops there). CMP's own `material-icons-*` is frozen at 1.7.3
    and deprecated — neither path grows, so new icons mean migrating to Material Symbols.
  - **Miuix**: reference **without** the `-android` suffix, and `basic.Switch` lives in `miuix-ui`,
    not `miuix-core`.
    ⚠️ **`miuix-preference` is the one miuix artifact with no desktop variant** (the version catalog
    only has `miuix-preference-android`; `miuix-ui`/`-core`/`-icons`/`-shader`/`-squircle` all have
    `*-desktop` counterparts). Any component whose Miuix branch renders a `preference` must go
    through the `MiuixPreferenceRenderer` contract (designsystem `commonMain`) with the Android
    implementation left in `:core:ui`. Measured twice: `SwitchSettingItem` (M1-3t) and
    `ClickableSettingItem` (M5-2a-pre).
    ⚠️ **Extending that contract breaks `MiuixPreferenceRendererContractTest`, and that is correct
    behaviour.** Its anonymous probe fails to compile when a method is added. Every implementor —
    including the test probe — must be updated explicitly. Don't weaken the contract to avoid it.
- **Android-only Compose members exist in `androidx.compose.foundation` too, not just `ui`.**
  Measured (M1-3k) and also absent on desktop: `Modifier.systemGestureExclusion()` (this one is
  why `lazylist/VerticalFastScroller.kt` cannot move), `excludeFromSystemGesture()`,
  `preferKeepClear()`, `WindowInsets.{statusBars,navigationBars,systemBars,captionBar,
  tappableElement}IgnoringVisibility`, `WindowInsets.isImeVisible` / `isTappableElementVisible` /
  `areNavigationBarsVisible`, `WindowInsets.imeAnimationSource` / `imeAnimationTarget`.
  Conversely, these **do** exist on desktop and must not be flagged: `WindowInsets.ime` /
  `.tappableElement` / `.captionBar` / `.waterfall` / `.safeDrawing`, and
  `Modifier.{ime,navigationBars,statusBars,systemBars}Padding()`.
  ⚠️ **Do not derive this list from a class-name diff.** `WindowInsetsPadding_androidKt` ↔
  `WindowInsetsPadding_skikoKt` and `WindowInsets_androidKt` ↔ `WindowInsets_notMobileKt` are
  `expect/actual` pairs with identical members — a class-level diff of the desktop jar against the
  android aar wrongly condemns 12 working members. The only reliable method is: diff class paths →
  `javap` the `*_androidKt` survivors for public member names → **prove each name absent from the
  desktop jar** (`grep -rl --binary-files=text -w <name>`). Match with `\.name` / `name(` so a
  same-named local variable (`val isImeVisible = …` exists in
  `feature/replacerules/edit/ReplaceEditScreen.kt`) is not a false hit. All of this is baked into
  `portability-triage.py`'s `ANDROID_ONLY_COMPOSE` (both the positives and the negatives).
- **`R.string.*` is not a permanent blocker — the replacement is CMP resources.** Shared components
  under `commonMain` cannot use `androidx.compose.ui.res.stringResource` (absent on desktop). The
  repo's answer (M1-3j) is `org.jetbrains.compose.components:components-resources` with
  `src/commonMain/composeResources/values{,-zh-rCN,-zh-rHK,-zh-rTW}/strings.xml` and generated
  `Res.string.*` accessors. Five non-obvious facts:
  1. The resource class package is set by the convention from the module path
     (`:core:designsystem` → `io.legado.app.core.designsystem.res`). It **must** be set explicitly —
     the project has no `group`, so CMP's default name degenerates.
  2. `resources` is **not a property of `ComposeExtension`** but a nested extension
     (`ResourcesExtension`, registered via `ExtensionAware`). Use
     `extensions.configure<ComposeExtension> { extensions.configure<ResourcesExtension> { … } }` in
     `build-logic`, or the `compose.resources { }` DSL in a module. `compose.resources.foo = …`
     does not compile.
  3. AGP's `androidLibrary` target does **not** process resources by default, so the CMP convention
     must enable it (`targets.withType<KotlinMultiplatformAndroidLibraryTarget>().configureEach { androidResources.enable = true }`)
     or Android will throw "resource not found" at runtime.
  4. `composeResources` is packaged as **assets** and does **not** participate in Android resource
     merging — the app's same-named `strings.xml` cannot override it. Therefore the shared layer must
     ship every supported locale directory (never defaults only), each value must be byte-compared
     against the existing Android resource before the move, and entries should be added **per moved
     file**, not as a bulk copy of every `R` key. Adding one key therefore means editing **all four**
     `strings.xml` in the same commit (M1-3q added `edit`: `Edit` / `编辑` / `編輯` / `編輯`); a
     missing locale silently falls back to the default (English) with no warning anywhere.
  5. **Verify the compiled artifact, not just the source XML.** `strings.xml` -> `.cvr` is a one-way
     compile step, and a stale `.cvr` is easy to mistake for "the resource was never generated".
     A `.cvr` is **plain text**: a leading `version:0`, then one line per entry as
     `string|<name>|<base64>`. Decode it and assert set-equality against the source XML, plus
     byte-equality of the newly added keys against the Android `values*/strings.xml`.
     WARNING: **`build/` holds four copies and only one is from the current run.**
     `generated/compose/resourceGenerator/preparedResources/commonMain/` is fresh;
     `.../assembledResources/desktopMain/` and `processedResources/desktop/main/` are from the last
     desktop build; `generated/assets/copyAndroidMainComposeResourcesToAndroidAssets/` and
     `intermediates/assets/.../mergeAndroidMainAssets/` are from the **last Android build** and are
     NOT refreshed by a desktop-only build. Pick the file by mtime (`:app:assembleAppDebug` refreshes
     the Android pair). M1-3s nearly concluded "the new strings never reached the artifact" from the
     stale `assets/` copy — 20 entries there vs 25 in `preparedResources/`.
  ⚠️ **Same simple name, different package.** `org.jetbrains.compose.resources.stringResource` and
  `androidx.compose.ui.res.stringResource` are spelled identically but only the former is available on
  desktop. Any check that matches on the *symbol name* misjudges this in both directions —
  `portability-triage.py` reported the already-moved `ReorderAccessibility.kt` as blocked. The only
  valid test is **import provenance**: which package did this simple name come from. Measured for CMP
  1.12.0's `components-resources`: `stringResource`, `vectorResource`, `painterResource`,
  `imageResource`, `pluralStringResource`, `stringArrayResource` exist; **`dimensionResource` does
  not** (no `dimen` support) and stays Android-only.
  ⚠️ **`verify-compose-resources.py` distinguishes two directions of key-set drift (M5-1c-3).**
  "`:app` has it, `composeResources` does not" = the module **failed to migrate** a string ⇒ hard
  failure. "composeResources has it, `:app` does not" = the `:app` copy was **retired** as a dead
  resource after the move (this repo deletes resources left dead by the current change) ⇒ reported
  with count and names, not a failure, and excluded from the byte-comparison denominator. The
  original undifferentiated check went permanently red after any legitimate cleanup. Consequence:
  retired entries stop being byte-verified, so **run the script once before deleting the `:app`
  copies** and quote that result (about: 140/140 before; 64/64 + 19 retired × 4 languages after).
  ⚠️ **aapt2 expands `\"` / `\'` inside string bodies; the Compose Resources generator does NOT**
  (measured M5-4c on the AI prompt templates: 7 entries red, all containing `\"replacement\"` or
  `reader\'s`). Copying the line verbatim therefore ships a **visible backslash** to users while
  `commonMain` still compiles ⇒ **the byte-comparison script is the gate, not the compiler**.
  Fix: write the bare character in `composeResources` (`"` / `'` need no escaping inside XML text).
  Do not diagnose this through the script's own `repr` output — it prints the source line already
  escaped, which made a single `\"` look like `\\\"` and cost M5-4c a round. Count the actual code
  points instead.
  ⚠️ **A VM that calls `getString(Res.string.*)` becomes UNTESTABLE in `androidHostTest`.**
  Measured (M5-4d probe, same style as the M1-4 desktop probe): `getString` throws
  `MissingResourceException: Missing resource with path: .../strings.commonMain.cvr. Android context
  is not initialized.` under Robolectric, even with `@Config(application = Application::class)`.
  ⇒ Constructing such a VM in a test fails before any assertion runs.
  **Architectural consequence, not just a test detail:** emit an enum and localise on the UI side
  (`AboutMessage.localizedText()`, `AiSummaryMessage.localizedText()`) whenever the string is only
  *displayed* — that keeps the VM constructible and therefore testable. Only let a VM read `Res`
  when it genuinely needs the string as **data** (e.g. `AiPromptConfigViewModel` writes default
  prompts back to a gateway); in that case prefer **injecting the strings/named values** into the VM
  over calling `getString` inside it, and say so in the slice notes.
  A batch rewrite script must delete old import lines *before* inserting the new ones, or import
  order breaks the diff.
  ⚠️ **The symptom of a *stale* old import is misleading (M2-5).** When a batch script inserts the
  new import but leaves the old `import io.legado.app.utils.X` line, the compiler reports
  `Unresolved reference 'X'` — **not** "unresolved reference 'utils'". That reads exactly like "the
  new package did not take effect", and the natural (wrong) next move is to re-check the new
  package or the Gradle dependency. Always grep the file for the old import line first. Note that
  a caller in a **same-named package in another module** (`:app` has its own
  `io.legado.app.utils`) never had an import at all — it resolved the symbol by package — so for
  those callers the migration is *insert an import that did not exist before*.
  ⚠️ **Line endings are a non-issue here — do not spend time on them** (measured 2026-09-11).
  `core.autocrlf=true` means the repo **stores LF blobs**; CRLF in the working tree is produced by
  the smudge filter on checkout. Converting a file to LF / CRLF / MIXED all hash to the *same*
  blob (`git hash-object --path=<f> --stdin`) and produce *no* content diff after `git add` — an LF
  file only leaves a `warning: LF will be replaced by CRLF` and a stat-level phantom `M` that
  `git update-index --refresh <f>` clears. Earlier guidance here ("must preserve CRLF or the diff
  explodes") was a **misdiagnosis**: its evidence, `git show HEAD:<f> | grep -c $'\r$'`, is run
  through the smudge filter and therefore always reports CRLF. Use `git cat-file blob HEAD:<f>`
  if you ever really need to inspect blob endings. `Edit`/`Write` emitting LF is harmless.
  What *does* break: **import order**, and inserting strings after the existing `</resources>`
  (producing a double closing tag — validate with an XML parser).
- ⚠️ **Kotlin block comments nest — never write MIME/glob literals inside a KDoc.** M1-3n: a KDoc
  describing `typesOfExtensions` mentioned the wildcard MIME values, and `compileKotlinDesktop` threw
  a wall of `Expecting a top level declaration`. Both `*/` (closes the KDoc early) and `/*` (opens a
  nested comment, so the real `*/` only closes the inner one) appear inside those literals.
  The same literal is perfectly legal inside a **string literal** — the check is
  *comment-context-aware*, not a plain full-text grep (a naive grep also flags the string
  occurrences and drowns you in noise). Run `scripts/check-kotlin-comments.py [paths…]` after any
  KDoc edit: it reports exactly four conditions (nested `/*`, `*/` underflow, unbalanced EOF, and a
  `/**` KDoc closed early with text left on the line) and is silent on clean code — verified across
  all 95 `commonMain` files with zero false positives, and it pinpoints the M1-3n failure at the
  exact line while the compiler only pointed ~30 lines below.
  Recipe and pitfalls: `docs/dev/cmp-module-convention.md` §7.
- **"Resource blocked" ≠ "movable".** M1-3j promoted 3 of 7 resource-blocked candidates; the other 4
  failed on **dimension 2** — un-imported same-package siblings that stayed in `:core:ui`
  (`SearchBar` → `AppDenseTextField`; `TopBarButton` → `GlassTopAppBarDefaults`) or libraries that
  were assumed platform-only. ⚠️ **"looks like a platform library" is not a verdict — check the
  Maven `.module` for a non-Android variant.** `coil3` (M1-3p) and `sh.calvin.reorderable` (M1-3q)
  were both listed here as platform-only and both turned out to be plain KMP artifacts
  (`coil-compose-jvm`, `reorderable-jvm`). Static import scans are blind to same-package references
  too, so **the desktop compile is the only verdict**.
- **A trailing lambda cannot reach a non-last parameter on every target.** M1-3q moved a
  `ListItem(modifier = …, supportingContent = …, colors = …) { AppText(title) }` whose
  `headlineContent` is the **first** parameter. That parses on the Android target and fails on the
  desktop one: `None of the following candidates is applicable: No value passed for parameter
  'headlineContent'`. Always write the named argument (`headlineContent = { … }`) when the
  composable lambda is not the last parameter — the two targets must agree, and the named form also
  documents which slot you meant.
- **`combine` has typed overloads for at most 5 flows — a 6-flow call silently becomes `Array<Any>`.**
  M1-3x: rewriting the Feature ViewModel (it no longer extends a base class, so the previous two-level
  composition had to be re-expressed) a single 6-argument `combine(a, b, c, d, e, f) { … }` was written.
  The compiler picks the `(Array<T>) -> R` vararg overload instead of reporting an arity error; with six
  **heterogeneous** flows `T` is inferred as `Any` and the error surfaces as
  `expected suspend (Array<Any>) -> R`, which then **cascades**: every `bookState.bookUrl`-style access
  becomes `Unresolved reference`, `filter { … }.toImmutableList()` infers an element type of `Any`, and
  the declared return type no longer matches. **Signal: seeing `Array<Any>` in an error means count the
  flows first.** Fix by **nesting two `combine` levels** (5 flows → base, then `combine(base, sixth)`).
  Emission semantics are identical (nothing is emitted until every source has emitted once), and the
  pre-migration code was already two-level — do not flatten it for "one less level".
- **A `when` branch naming a `data class` constructor is a companion-object reference, not a branch.**
  M1-3x: `ReplaceRuleIntent.ToggleImportAll -> …` (missing `is`) produced both
  "`when` expression must be exhaustive" and "unresolved `isSelected`". Every `data class` branch needs
  `is X`; only `data object` branches omit it. While there, re-check **each** branch of a `when` that
  returns a value against the original: one branch re-wrapped to `listOf(…)` was forgotten, so the
  branches' least upper bound degenerated to `Any` (`List<ReplaceRule>` vs `ReplaceRule`) and the
  function reported a return-type mismatch.
- **Verify moved string resources by decoding the packaged `.cvr`, not by reading the source XML.**
  Use `python tools/verify-compose-resources.py <modulePath> <cmpResourcePackage> [--apk <apk>]`
  (added in M1-3x, after M1-3w did it ad hoc). It checks three things the source tree cannot: ① all four
  language directories exist — `composeResources` does **not** join Android resource merging, so a
  missing language directory degrades **silently**; ② each language's key set matches the default one
  (missing translations are equally silent); ③ every entry is byte-identical to the same-named entry in
  `app/src/main/res/values*/strings.xml`. It pads base64 (some `.cvr` entries omit padding). **Run it
  first on a module whose result is already known** (tagrules ⇒ 124/124) to validate the tool, then on
  the new module. Also assert the APK actually contains the module's four `.cvr` entries — a module
  whose resources never got packaged would otherwise "pass" an empty comparison.
  ⚠️ **Compare at the runtime layer, not the source layer (fixed in M5-1c-2).** `.cvr` stores the
  *decoded* text, while the source XML may hold **literal Java-style escapes**: this repo's
  `about_description` is written as literal `\u3000\u3000`, which aapt2 expands to U+3000 — and CMP's
  generator expands it too (measured: the `.cvr` holds UTF-8 `E3 80 80`). Comparing raw XML therefore
  reported 3 **false** differences (`cmp = '\u3000'` real char vs `app = '\\u3000'` backslash + `u3000`).
  The script now applies `android_unescape()` to the `:app` side (`\uXXXX`, `\n`, `\t`, `\r`, `\'`,
  `\"`, `\\`, `\0`; unknown escapes left alone) ⇒ about went from a false `137/140` to `140/140`,
  tagrules stayed `124/124`. **Source text equality is not runtime equality** — when you write the new
  `strings.xml`, never encode a newline as `\n` (it would be read as a literal backslash + n); use the
  `&#10;` character reference.
- CMP `*-metadata` artifacts need network on first resolution (`--offline` fails with
  "No cached version available for offline mode"); offline works after one successful resolve.
- Full recipe, dependency table and measurements: `docs/dev/cmp-module-convention.md`.

## Tests

- **A migrated page is not automatically a tested page.** ViewModels are where the testable logic
  lives; migrating one without adding coverage for the branches you touched is a finding.
- **A stateless screen with no ViewModel gets no test — and you must not add a test dependency to
  get one.** Measured in M5-6b (`ConfigNavScreen`: 9 `ClickableSettingItem`s wired to 9 `() -> Unit`
  callbacks, zero state). `:feature:settings` has **no Compose UI test infrastructure** (`ui-test`
  appears in no shared module's build file; all 8 of its test files are ViewModel tests), so any
  screen test would require pulling in `org.jetbrains.compose.ui:ui-test` first. Same judgement as
  `koin-test` in M2-8: **do not pull a test dependency in alongside a wiring change** — it is a
  separate risk dimension. Report "no test added, and why" instead of fabricating one that merely
  re-asserts the source.

## Gates

- G0 Android test/lint/architecture/debug gates pass.
- **After a cross-module file move, rebuild from clean before verifying** — incremental compilation can
  leave stale classes behind and report a false green. Run a **standalone `./gradlew clean`** and confirm
  the module's `:xxx:clean` actually appears; do **not** put `clean` on the same command line as the
  verification tasks (M1-3w: a configuration-phase failure meant `clean` never ran, and the stale
  `test-results/testDebugUnitTest/` directory made the count script report "708/868, unchanged"). Also
  run `assembleAppDebug`, not just the Kotlin compile: package level is where a duplicated class left
  behind by a move surfaces. For a pure move, quote the test count **per module** against the pre-change
  baseline (current: `app 633 + designsystem 39 + viewmodel 19 + tagrules 15 + replacerules 2 = 708`)
  so a silently dropped test cannot hide inside a green run — and update the mapping in
  `tools/count-test-results.py` for any module whose test task name or ownership changed, otherwise the
  totals can stay "correct" while a whole module's results are never read (M1-3x moved 2 cases from
  `:app` into `feature/replacerules`, which is a redistribution, not a change in coverage).
- Common tests and actual metadata/target compile tasks pass.
- Capability status distinguishes compile, contract-test, smoke, package, and release-ready
  evidence.
- Adapter contract tests cover success, failure and cancellation where relevant.
- Serialization/database changes have forward/backward or migration evidence.
- Reader/rule/service changes have parity and, when relevant, real-device performance evidence.
- Reduced historical violations lower their baseline in the same change.
- `git diff --check` passes.
- **A mutation run overwrites `build/test-results/*/TEST-*.xml` with the failing state. After
  restoring the source you must re-run that test task before quoting counts from the reports** —
  otherwise a plain XML tally reports phantom failures. (Gradle may not print anything on a green
  test task; read the report's `timestamp`/`failures` attributes to confirm it re-ran.)
- A long-red gate that is *not* in the current verification set (e.g. `lintAppDebug` with its five
  pre-existing app-owned errors) must be reported as pre-existing with its unchanged error count —
  never presented as caused by, or fixed by, the slice.

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
- **A *new directory* may not add `import splitties.init.appCtx` either** (`appCtx` = "global Context
  coupling", detected by import, not by usage). M1-3l: an Android platform implementation placed in
  the new `app/main/io/legado/app/ui/theme` failed G4 with "首次出现 1 处；新区域必须为零". The right
  response is **not** to register it in the baseline — pass the `Context` explicitly
  (`installAndroidThemePlatform(this)` from `Application.onCreate`) so the dependency is visible at
  the call site. Registering it would defeat the ratchet's whole purpose.
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
