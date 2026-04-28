# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build / Test / Run

```bash
# Assemble all variants
./gradlew assembleAppRelease

# Assemble without R8 (for crash debugging — no minification/shrinking)
./gradlew assembleAppNoR8

# Debug build
./gradlew assembleAppDebug

# Run unit tests (JVM, local)
./gradlew test

# Run a single test class
./gradlew test --tests "io.legado.app.model.cache.CacheDownloadQueueTest"

# Run connected Android tests
./gradlew connectedAndroidTest

# Lint
./gradlew lint

# Update Cronet (after changing CronetVersion in gradle.properties)
./gradlew app:downloadCronet
```

The project uses JDK 21 for development (set in `build.gradle.kts` via `jvmToolchain`). CI uses JDK 17 for building.

Gradle properties: 8 GB heap, configuration cache disabled (`gradle.properties:31`), non-transitive R classes, precise resource shrinking enabled.

## Architecture

This is a Material Design 3 fork of [Legado](https://github.com/gedoor/legado). `app/src/main/java/io/legado/app/` uses **Clean Architecture** with three layers:

| Layer | Package | Role |
|---|---|---|
| Data | `data/` | Room DB (`AppDatabase`, version 85, ~22 DAOs, ~25 entities), repository implementations |
| Domain | `domain/` | Gateway interfaces, use cases (14), domain models — no framework dependencies |
| UI | `ui/` | Jetpack Compose screens, Navigation 3 routes, ViewModels |

Additional top-level packages:
- **`help/`** — Infrastructure "glue": HTTP (OkHttp + Cronet), book content processing, backup/WebDAV, JS engine, config
- **`model/`** — Runtime state coordinators (not entities): `ReadBook`, `AudioPlay`, `CacheBook`, `BookCover`, etc.
- **`service/`** — Android foreground/background services (audio playback, TTS, download, web server)
- **`web/`** — Embedded HTTP server (NanoHTTPD) for remote bookshelf/source editing
- **`lib/`** — Third-party library wrappers (MOBI parser, WebDAV client, legacy View theme system, cronet)
- **`base/`** — Abstract Activity/Fragment/ViewModel base classes
- **`utils/`** — Extension functions and utility classes (~70 files)

Modules: `:app`, `:modules:book` (epub/TXT parsing, namespace `me.ag2s`), `:modules:rhino` (Rhino JS wrapper, namespace `com.script`). There is also a Vue 3 web frontend in `modules/web/` (pnpm, separate from the Android build).

## Dependency Injection (Koin)

Two modules loaded in `App.onCreate()`:

```kotlin
startKoin {
    modules(appDatabaseModule, appModule)
}
```

- **`di/appDatabaseModule.kt`** — Singleton `AppDatabase` + factory bindings for all 22 DAOs
- **`di/appModule.kt`** — Singletons (repositories, use cases, gateways, Coil `ImageLoader`), `viewModelOf` / `viewModel { }` for all ViewModels, some parameterized definitions

Gateways are bound to their repository implementations explicitly (e.g., `single<LocalBookGateway> { LocalBookRepository(get()) }`), not through `singleOf`.

## Navigation

Uses **Jetpack Navigation 3** (`androidx.navigation3`) with type-safe `@Serializable` sealed interfaces for route keys:

```kotlin
@Serializable
private sealed interface MainRoute : NavKey
@Serializable
private data object MainRouteHome : MainRoute
@Serializable
private data class MainRouteCache(val groupId: Long) : MainRoute
```

`MainActivity` holds a single `NavDisplay` with `entryProvider { ... }` defining all composable entries. `Launcher0` through `LauncherW` extend `MainActivity` to provide multiple launcher icon alias entries. Separate activities handle the reader (`ReadBookActivity` — still View-based), book info, source management, replace rules, file manager, QR scanner, etc.

## Theme System

A multi-engine theming system in `ui/theme/`:

1. **Material 3 Expressive** (default): Uses `MaterialExpressiveTheme` with `MotionScheme.expressive()`
2. **Miuix** (alternative): Uses `top.yukonga.miuix.kmp` theming engine

14 theme modes (`AppThemeMode` enum) — Dynamic (Monet), 12 named presets, Custom (MaterialKolor seed-color generation), Transparent. `CustomColorScheme` wraps `com.materialkolor` with configurable `PaletteStyle` (TonalSpot, Neutral, Vibrant, Expressive, Rainbow, etc.) and `ColorSpec` (2021 vs 2025).

Legacy View-based theme still exists in `lib/theme/` (used by non-migrated screens like `ReadBookActivity`).

## Hybrid Compose + View

The app is mid-migration from Views to Compose. View-based screens (reader, book info, source management) coexist with Compose screens (main tabs, settings, search, RSS, cache management). XML layouts, `viewBinding`, and traditional Activities are still heavily used. The `viewBinding` build feature is enabled but Compose screens are the target.

## Rhino JavaScript Engine

Book sources, RSS sources, and HTTP TTS use JavaScript rules. `initRhino()` in `App.kt` registers `NativeBaseSource` wrappers for `BookSource`, `RssSource`, `HttpTTS` (writable JS objects) and `ReadOnlyJavaObject` wrappers for rule entities. Rule parsing logic lives in `help/source/` and `model/analyzeRule/`.

## Important Constraints

- **Do not update jsoup** beyond 1.16.2 — a breaking change in newer versions (see [jsoup#2017](https://github.com/jhy/jsoup/pull/2017)) affects `AnalyzeByJSoup.kt` and the JsoupXpath library
- **Do not update hutool** beyond 5.8.22 — pinned in `libs.versions.toml:42`
- Package name discrepancy: code namespace is `io.legado.app` but `applicationId` is `io.legato.kazusa`
- Min SDK 26, target SDK 37, compile SDK 37
- Release builds enable R8 minification + resource shrinking; `noR8` variant disables both for crash debugging
- APK is split by ABI (`armeabi-v7a`, `arm64-v8a`, plus universal)
- Firebase Analytics and Performance are included; `google-services` plugin applied

## Web Frontend

Located in `modules/web/` — a Vue 3 + TypeScript + Vite project for remote bookshelf and source editing. Must connect to the app's built-in HTTP server (started via `WebService` in the main activity settings). Commands:

```bash
cd modules/web
pnpm install
pnpm dev       # dev server
pnpm build     # production build
```

Set `VITE_API` in `.env.development` to the app's web service IP.
