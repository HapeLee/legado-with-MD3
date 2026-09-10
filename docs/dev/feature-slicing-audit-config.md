# feature-slicing-audit: ui/config 域依赖审计（2026-09-10）

> 前置审计，判定 `app/src/main/java/io/legado/app/ui/config/` 下各子域能否独立成
> `:feature:xxx`（Android library + Compose）模块，还是属 platform island 留 app。
>
> 背景教训（2026-09-10 bookmark 回退）：审计不能只看 Screen 的 `io.legado.app.ui.*`
> import 是否以 `widget/components` 开头，要**确认组件/主题实体的源码模块归属**
> （core:ui 已下沉的源码在 `core/ui/...`，app 私有仍在 `app/...`）。本表结论均经
> 「全仓 `grep -rln "fun X("` 查实体定义 + 逐文件核对 `io.legado.app.*` import」双查验证。

## 判定依据

一个 UI 域要能模块化，Screen/VM/Contract 里所有 `io.legado.app.*` 依赖必须落在：
`:core:{platform,model,data,designsystem,ui,viewmodel}`、`:core:data` 的
`domain.{gateway,model}`/`data.{entities,repository}`，或已下沉到 `:core:data` 的 `help.*`。
致命阻塞 = 直连 app 私有对象：`model.*`（ReadBook/LocalBook/BookCover/CacheBook 等引擎单例）、
未下沉的 `help.*`（config/http/storage/DefaultData）、`ui.main.*`/`ui.book.read.*` 跨域 UI、
app 私有 `service.*`/`lib.*`、以及 app 私有 `utils.*`（此类属软阻塞，可下沉 `:core:platform`）。

## config 下通用观察（非阻塞层）

- 所有 `ui.widget.components.*` 与 `ui.theme.*`（含 `ThemeEngine`、`ThemeResolver`、
  `LegadoTheme`、`adaptiveContentPadding`、`dialog.ColorPickerSheet`、`filePicker.FilePickerSheet`）
  **都在 core:ui** → 组件/主题不是 config 域的障碍。
- `GSON` 在 core:data、`EventBus`/`PreferKey`/`AppLog` 在 core:model、`BookGroupDao` 在 core:data → 非阻塞。
- **通用模式**：多数域的 Screen/VM/Contract 已下沉到 domain/core:ui，真正 app 私有胶水
  往往集中在 **RouteScreen / 个别 BottomSheet**（`ThemeStore`、`WebService`、`lib.dialogs.selector`、
  `help.*`）。切法通常是「Screen/VM/Contract 进 feature 模块，RouteScreen 与平台副作用留
  app host，或下沉 `:core:platform`」。

## 各子域分级

### A 级：可直接整域迁移（零 app 私有依赖）

| 子域 | 文件/行数/R.string | 阻塞 | 迁移路径 |
|---|---|---|---|
| **translation** | 3 文件 / 174 行 / 9 条 | **0 硬阻塞、0 app 私有** | 最干净候选。依赖全在 core：`TranslationSettingsGateway`(core:data)、`TranslationConstants`(core:model)、`TranslationSettings`(core:data domain.model.settings)、ui.theme/components(core:ui)。`TranslationConfigRouteScreen` 就在 `TranslationConfigScreen.kt` 内且干净。VM 是纯 `ViewModel`，只依赖一个 gateway。**与 tagrules Stage B 完全同构，直接整域迁 `:feature:translation`。** |

### B 级：需先抽单一契约 / 留 RouteScreen 胶水在 host

| 子域 | 文件/行数 | 阻塞 | 迁移路径 |
|---|---|---|---|
| **customTheme** | 4 文件 / 487 行 / 15 条 | 仅 `CustomThemeRouteScreen.kt` import `lib.theme.ThemeStore`（app 私有主题引擎），用于 `ApplyLegacyPrimarySeed` effect | Contract/Screen/VM 干净可进 `:feature:customTheme`；`ThemeStore.editTheme(context).primaryColor(color).apply()` 这段副作用把 `ThemeStore` 下沉 core 或封装成 app host handler（注入）。RouteScreen 需同步处理。 |
| **labConfig** | 4 文件 / 209 行 / 12 条 | 0 硬阻塞（仅系统分享 Intent，feature 内合法） | 可直接迁，作为最小试点。 |
| **otherConfig** | 6 文件 / 1157 行 / 71 条 | VM/Screen/Contract 干净；但 RouteScreen 依赖 app 私有 `service.WebService`(HARD) + `utils.{SystemUtils,restart,takePersistablePermissionSafely}`；DirectLinkUploadBottomSheet 依赖 `lib.dialogs.selector`(HARD) + clip/toast utils | 抽平台胶水（WebService 重启、权限、selector、clip/toast）到 `:core:platform`/host，Screen+VM 进 feature。工作量中等。 |
| **backupConfig** | 5 文件 / 1144 行 / 58 条 | VM/Contract/Sheets 干净（仅 app utils 软阻塞）；Screen 直连 `help.storage.ImportOldData`(HARD) | 把 `ImportOldData` 下沉 core:data 或封 host。需先抽契约。 |
| **ai** | 15 文件 / 2470 行 / 106 条 | **0 硬阻塞**；仅 `AiPromptConfigViewModel` 用 app 私有 `utils.toastOnUi`（软，换 `:core:platform` Toaster） | 最干净的大域候选；结构最规范（全 domain gateway + domain.model）。体量大（106 条 R.string ×4 语言），适合作为「大域验证」而非首批。 |
| **bookshelfConfig / readMangaConfig / importBookConfig** | 已 `@Deprecated` facade | 依赖全在 core:data/domain | 体量过小/已废弃（被 gateway 取代），可顺手迁或删。 |

### C 级：platform island 留 app（硬阻塞，需先抽象 core 契约）

| 子域 | 硬阻塞根因 |
|---|---|
| **readConfig** | 直连 `model.ReadBook` 引擎、`ui.book.read.*`（EyeProtectionUiState/ClickActionConfigSheet/EyeProtectionConfigSheet/ReadConfigUpdateBus）跨域 UI 与总线、`help.config.{AppConfigStore,compatDsBoolean}` |
| **themeConfig**（11 文件 3400 行） | 重度耦合 `ui.main.*`（Launcher0..W/MainDestination/mainDestinationIcon 启动图标+主导航）、`help.config.TagColorGenerator`、`help.{LauncherIconHelp,ThemeConfigStore}` |
| **themeManage** | VM/Screen/Route 直连 `help.config.{SavedTheme,ThemePackageManager}`（主题包引擎） |
| **coverConfig** | `CoverConfigViewModel` 直连 `model.BookCover` + `help.DefaultData` |
| **downloadCacheConfig** | VM/Screen 直连 `model.{CacheBook,ImageProvider}` + `help.http.*` |

C 级若要迁，前提是先把 ReadBook/书签展示/封面生成/主题包/HTTP 缓存等抽象成 core 契约——
与 bookmark 页撞 island 组件同性质，**当前不该迁**。

## 根文件

- `ConfigNavScreen.kt`（97 行）：仅 core:ui 组件 + lambda 路由，干净，应留 app 作为各子域
  RouteScreen 的装配点（或未来 `:feature:config` 宿主）。
- `ConfigTag.kt`（11 行）：纯字符串常量，无 app 依赖。

## 推荐切片顺序

1. **translation**（首选，A 级，成本最低，验证「config 子域也能整域迁」）。
2. **labConfig 或 customTheme**（B 级小切，处理单一 RouteScreen 胶水点）。
3. **ai**（大域，需先把 toastOnUi 换成 core:platform Toaster）。
4. otherConfig / backupConfig（抽契约后）。
5. C 级全部暂缓，除非配套抽象立项。

> 与 2026-09-10 结论一致：config 域里 UI 组件/主题已非障碍；障碍集中在「model 引擎 + 主模块
> ui.main + help.*」。translation 是当前 repo 里经过完整审计、零 app 私有依赖的最干净候选。
