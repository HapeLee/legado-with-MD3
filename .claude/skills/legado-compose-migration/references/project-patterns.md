# Legado Compose Migration Project Patterns

## Existing Shape

- App module: `app`.
- Main source root: `app/src/main/java/io/legado/app`.
- UI package: `ui/...`, usually grouped by feature.
- Data package: `data/dao`, `data/entities`, `data/repository`.
- Domain package: `domain/gateway`, `domain/model`, `domain/repository`, `domain/usecase`.
- DI: `di/appModule.kt`, using Koin `singleOf`, `viewModelOf`, and parameterized `viewModel { ... }`.
- Main navigation: `ui/main/MainActivity.kt`, using Navigation3 `NavKey`, `rememberNavBackStack`, `entryProvider`, and `NavDisplay`, plus legacy route extras such as `EXTRA_START_ROUTE`.
- Base Compose host: `base/BaseComposeActivity.kt`, which wraps `Content()` in `AppTheme`, configures system bars, locale/font, background image, and LiveBus recreation events.
- Compose dependencies are already enabled in `app/build.gradle.kts`; do not add new UI libraries unless the target screen truly requires one.
- `kotlinx.collections.immutable` is available for Compose-facing screen state. Prefer `ImmutableList`, `ImmutableSet`, and `ImmutableMap` at `UiState` boundaries when collection state is passed to composables and changes often.

## Files to Inspect Before Migrating

Inspect these as local examples, not as APIs to copy blindly:

- `ui/book/info/BookInfoActivity.kt`
- `ui/book/info/BookInfoContract.kt`
- `ui/book/info/BookInfoViewModel.kt`
- `ui/book/info/BookInfoScreen.kt`
- `ui/book/search/SearchContract.kt`
- `ui/book/search/SearchViewModel.kt`
- `ui/book/search/SearchScreen.kt`
- `ui/main/MainActivity.kt`
- `ui/main/MainScreen.kt`
- `ui/widget/components/AppScaffold.kt`
- `ui/widget/components/list/ListScaffold.kt`
- `ui/theme/LegadoTheme.kt`
- `ui/theme/AppTheme.kt`

## Recommended Feature Layout

For a migrated feature, prefer colocated files:

```text
ui/<area>/<feature>/
  FeatureActivity.kt       # only for legacy Intent compatibility or full-screen migration host
  FeatureContract.kt
  FeatureViewModel.kt
  FeatureScreen.kt
  FeatureSheets.kt        # only if sheets are substantial
  FeatureDialogs.kt       # only if dialogs are substantial
```

Use the smallest set of files. Do not split files just to match this shape.

## MVI and UDF Rules

Use this shape for behavior-heavy screens:

```kotlin
@Stable
data class FeatureUiState(
    val isLoading: Boolean = false,
    val items: ImmutableList<Item> = persistentListOf(),
    val dialog: FeatureDialog? = null,
    val sheet: FeatureSheet = FeatureSheet.None,
)

sealed interface FeatureIntent {
    data object BackPressed : FeatureIntent
    data object Refresh : FeatureIntent
    data class ItemClick(val id: Long) : FeatureIntent
}

sealed interface FeatureEffect {
    data object Finish : FeatureEffect
    data class OpenDetail(val id: Long) : FeatureEffect
}
```

ViewModel rules:

- Keep `_uiState = MutableStateFlow(FeatureUiState())` private and expose `uiState = _uiState.asStateFlow()`.
- Keep `_effects = MutableSharedFlow<FeatureEffect>(extraBufferCapacity = 8)` private and expose `effects = _effects.asSharedFlow()`.
- Prefer `fun onIntent(intent: FeatureIntent)` as the UI entry point.
- Treat `FeatureIntent` as an MVI/user-action type. When Android `Intent` launch/extras are also involved, name variables and explanations clearly enough that the two concepts are not confused.
- Use `_uiState.update { it.copy(...) }` for state changes.
- Emit one-shot work through effects, not booleans in state.
- Keep cached current entities private in the ViewModel when existing project behavior needs mutation or incremental sync, but publish immutable render state.
- Annotate `UiState` data classes with `@Stable` to give the Compose compiler the strongest stability guarantee. This is especially important when `UiState` fields include collections or entity types that come from non-Compose modules.
- For collection-heavy `UiState`, convert DAO/repository `List`/`Set`/`Map` values to `kotlinx.collections.immutable` at the ViewModel/UI-state boundary with `toImmutableList()`, `toImmutableSet()`, or `toImmutableMap()`. Do not force repository, DAO, or domain APIs to use persistent collections unless the domain contract truly benefits.
- Keep internal flow pipelines free to use normal Kotlin collections for sorting, grouping, and persistence work; the immutable collection rule is primarily for values exposed to Compose.
- Use existing `BaseViewModel.execute { ... }.onSuccess { ... }.onError { ... }` when the surrounding ViewModel already uses that pattern.

Activity rules:

- Prefer `MainActivity` as the owner of Compose-first navigation. Add a Navigation3 route/key and `entryProvider` entry there for new destinations.
- Extend `BaseComposeActivity` for migrated Activity screens only when a standalone Activity remains necessary.
- Keep a legacy Activity for a migrated screen only when unreworked View code still calls it through `Intent`; in that case, make the Activity a thin compatibility host that translates extras/results and delegates to Compose state/effects.
- Keep Activity Result launchers, file openers, clipboard, permission APIs, Android DialogFragments, and framework navigation in the Activity.
- In `Content()`, collect state with `collectAsStateWithLifecycle().value`.
- Collect effects in `LaunchedEffect(Unit) { viewModel.effects.collectLatest { ... } }`.
- Pass only `state` and callbacks into the screen.
- Edge-to-edge: Since target SDK ≥ 35, edge-to-edge is enforced. In `BaseComposeActivity`, `enableEdgeToEdge()` is called before `setContent`. Ensure screen-level composables apply `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)` on the outermost non-scaffold container, or rely on Material 3 `Scaffold` (which handles `WindowInsets` automatically). Do not use manual `statusBarsPadding()` / `navigationBarsPadding()` on top of scaffold-level insets — double-padding is a common pitfall.

Navigation rules:

- For new Compose-first destinations, add the route to `MainActivity` instead of adding a new Activity.
- Follow `MainActivity`'s current route style: serializable `NavKey` routes for in-process navigation and launcher `Intent` helper methods only when external or legacy callers need a stable entry point.
- Keep legacy extra names stable when replacing an old Activity entry point.
- When a compatibility Activity is retained, let it translate old `Intent` inputs/results; do not make it the source of truth for navigation or business behavior.
- Predictive back: Navigation 3 enables predictive back by default on Android 14+. New composable destinations work with it automatically. When a screen needs back confirmation (unsaved changes, selection mode, add-to-shelf prompt), use `BackHandler(enabled = ...) { onIntent(FeatureIntent.BackPressed) }` to intercept and let the ViewModel decide — don't bypass ViewModel back logic.

Screen rules:

- Make `FeatureScreen(state, onIntent, ...)` stateless for business state.
- Use `remember` and `rememberSaveable` only for local UI affordances such as menu expansion, scroll state, transient animation state, and text field drafts when committing through an intent.
- Use `rememberSaveable` for user-driven transient targets that should survive recreation, such as a delete-confirmation item ID or URL. Store only the stable ID in saved state and resolve the current entity from `UiState`.
- Use `BackHandler` to route back actions through `FeatureIntent.BackPressed` when ViewModel decisions matter.
- Keep expensive derived values in `remember(key)` or ViewModel state when they depend on repository data.
- Prefer `LazyColumn`, `LazyRow`, stable keys, and existing fast-scroller/list components for adapter migrations. For heterogeneous lazy lists or grids, provide `contentType` in addition to `key`.
- In long-lived `LaunchedEffect` collectors, wrap changing callbacks, `Context`-dependent operations, or lambdas from parents with `rememberUpdatedState` when the effect should not restart.
- Hoist state only to the lowest common owner that reads and writes it. Move branch-only ViewModel lookup and Flow collection into the branch or a small child composable instead of collecting at a high-level route.
- Avoid UI-state feedback loops such as `LaunchedEffect(uiState.items) { viewModel.pruneSelection(...) }`. Prefer deriving consistency inside the ViewModel with `combine(...)`, or reduce the state as part of the flow that produces the data.

## Current Compose Performance Notes

- Kotlin 2.x + Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`) enables **strong skipping by default**. This means composable functions with unstable parameters CAN still be skipped if their arguments are equal by `equals()`. However, ordinary Kotlin `List`, `Set`, and `Map` are still **unstable types** — passing them as parameters prevents the compiler from inferring stability, so changing a list reference still triggers recomposition of callers even with strong skipping.
- Use `@Stable` on `UiState` data classes and UI model wrappers to give the Compose compiler the strongest stability guarantee. `@Stable` tells the compiler: "if `equals()` says two instances are the same, the UI hasn't changed." This is critical for data classes holding collections.
- Use `kotlinx.collections.immutable` (`ImmutableList`, `ImmutableSet`, `ImmutableMap`) for render state that crosses into Compose. These are recognized as stable by the Compose compiler. Do not mechanically replace every temporary collection, Room query result, or internal mutable accumulator.
- If data entities come from modules where the Compose compiler plugin is not applied, consider a `@Stable` UI model wrapper when the entity is passed deeply through composables and causes measurable recomposition cost.
- Treat `SnapshotStateList` / `SnapshotStateMap` as UI-owned mutable state, not as a default ViewModel `UiState` transport type.

## Clean Architecture Boundaries

This project is mid-migration, so use Clean Architecture pragmatically. For newly created screens, default to standard modern Android architecture and avoid mixed legacy patterns unless they are needed to interoperate with View screens that have not been rewritten.

- For new screens, keep UI, presentation, domain, and data boundaries explicit: Compose renders state, ViewModel owns state and intent handling, usecases hold reusable business actions, repositories mediate data access.
- For new screens, do not place business rules in Activity/Fragment or composables.
- For new screens, do not read DAOs directly from UI/presentation unless the project already has no reasonable repository/usecase boundary and adding one would be disproportionate.
- For migrations, use existing DAOs/repositories directly if the old ViewModel already does and the migration is UI-only.
- Prefer existing `domain/usecase` classes for reusable business actions such as cache, delete, group update, startup maintenance, reading progress, and WebDAV flows.
- Add a usecase when a new screen has meaningful business rules, when logic would otherwise be duplicated across features, or when a UI migration needs to extract meaningful business rules from an Activity/adapter.
- Keep UI-only formatting and Compose layout decisions out of domain.
- Keep entity-to-display mapping near the UI or ViewModel unless it is reused business language.

## Compose Components and Theme

Prefer project components before raw Material widgets:

- Scaffold/top bars: `AppScaffold`, `GlassTopAppBar...`, `DynamicTopAppBar`, `TopBarActionButton`, `TopBarNavigationButton`.
- Lists: `ListScaffold`, `TopFloatingStickyItem`, `VerticalFastScroller`, `SelectionBottomBar`.
- Dialog/sheet: `AppAlertDialog`, `AppModalBottomSheet`, `OptionSheet`, text list input components.
- Settings: `ClickableSettingItem`, `SwitchSettingItem`, `SliderSettingItem`, `ListSettingItem`, `SettingCard`.
- Buttons/chips: `AppIconButton`, `SmallIconButton`, `SmallTextButton`, `ToggleChip`, `AlertButton`.
- Covers/images: `CoilBookCover`, `BookshelfCover`, `buildCoverImageRequest`; use Coil for Compose image loading.
- Theme: `LegadoTheme.colorScheme`, `LegadoTheme.typography`, `ThemeResolver`, `ProvideThemeOverride` where existing feature behavior needs theme override.

Avoid introducing a second visual system. If a raw Material 3 component is used, style it from `LegadoTheme`.

## Migration Checklist

Before editing:

- Identify old XML layouts, ViewBinding fields, adapters, decorations, menu XML, dialogs, and result contracts.
- Capture behavior: empty/loading/error states, refresh, search, sort, selection, long click, swipe, menu actions, back behavior, result codes, events, and persisted preferences.
- Decide whether the task is UI-only or needs domain extraction.
- For new screens, explicitly decide the presentation/domain/data boundary before coding; do not default to legacy mixed architecture.

During editing:

- Create/update `Contract`, then `ViewModel`, then `Screen`, then `MainActivity` route or compatibility host.
- For new Compose destinations, wire navigation through `MainActivity` unless a legacy `Intent` caller must remain supported.
- When retaining an Activity for compatibility, keep it small: parse legacy extras, collect effects, bridge result codes, and avoid placing feature business logic there.
- Verify edge-to-edge: ensure the screen draws edge-to-edge without overlapping system bars. Use `Scaffold` or `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)`. Remove any `fitsSystemWindows`-based margin hacks from the old layout.
- Reuse existing string resources and add new resources for new user-facing text.
- Register the ViewModel in `di/appModule.kt`.
- Remove only resources made obsolete by this migration.
- Keep existing public intent extra names and result codes unless explicitly changing an API.

Verification:

- Run `.\gradlew.bat :app:compileAppDebugKotlin` for Kotlin-only changes.
- Run `.\gradlew.bat :app:assembleAppDebug` when resources, manifests, XML deletion, or generated bindings are affected.
- If a screen uses Room queries or migration-sensitive repositories, run the relevant existing Android/unit tests if practical.
- For manual verification, open the migrated screen and compare core flows against the old behavior list.

## Common Pitfalls

- Do not put navigation or Activity launchers inside composables.
- Do not store one-time navigation as nullable fields in `UiState` unless the existing local pattern already does; prefer `SharedFlow` effects.
- Do not keep adapter selection state split between old adapter and new Compose state.
- Do not delete XML/menu resources until all references and generated binding imports are gone.
- Do not add speculative domain layers for a single migrated screen.
- Do not use mixed architecture for new screens unless compatibility with unreworked View UI requires it.
- Do not make a migrated screen's retained Activity the primary navigation model when `MainActivity` can own the Compose destination.
- Do not bypass `BaseComposeActivity` unless the screen must remain inside a Fragment or legacy host.
- Do not replace existing event bus or service interactions as part of a UI migration unless required for correctness.
- Do not forget edge-to-edge: XML `fitsSystemWindows` does not carry over to Compose. Rely on `Scaffold` or `Modifier.windowInsetsPadding()` instead.
- Do not forget `@Stable` on `UiState` data classes — missing annotations cause unnecessary recomposition, especially when state contains collections.
