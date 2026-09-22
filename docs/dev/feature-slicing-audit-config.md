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
| **labConfig** ✅ M5-2a | 4 文件 / 209 行 / 12 条 | 0 硬阻塞（仅系统分享 Intent，feature 内合法） | **已完成（2026-09-22）**，且是 `ui/config/*` 里**第一个**迁进 `:feature:settings` 的子域。见末尾实录。 |
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
2. **labConfig ✅ 已完成（M5-2a，2026-09-22，见 §「M5-2a 实录」）** 或 customTheme
   （B 级小切，处理单一 RouteScreen 胶水点）。
3. **ai**（大域，需先把 toastOnUi 换成 core:platform Toaster）。
4. otherConfig / backupConfig（抽契约后）。
5. C 级全部暂缓，除非配套抽象立项。

> 与 2026-09-10 结论一致：config 域里 UI 组件/主题已非障碍；障碍集中在「model 引擎 + 主模块
> ui.main + help.*」。translation 是当前 repo 里经过完整审计、零 app 私有依赖的最干净候选。

## M5-2a 实录：`labConfig` → `:feature:settings`

**为什么是 labConfig 而不是 translation**：上面第 1 位推荐的是 translation（A 级），本片选了
第 2 位的 labConfig。判据是**依赖闭包而不是洁净度**：

- labConfig 的两个依赖**已经在共享层**——`LabSettingsGateway`（`:core:data` 的端口）与
  `LocalPageEstimateMetrics`（`:feature:reader:core` 的 object）⇒ **零新增契约**；
- translation 同样干净，但它要自带 9 条 `R.string` 与 `TranslationConstants` 的桥接，
  体量与 labConfig 相当而形态更"正"（它才是 A 级）。本片要的是**先把「域级模块 + 按页分片」
  这个形态跑通一次**，故取最小且已无契约成本的那个。**下一片应是 translation**。

### 模块形态：域级模块，按页分片填充

`feature-catalog.md` 把整个 `ui/config/*`（78 文件）归到一个 `feature/settings` 域。
一次搬完不现实（各子域依赖闭包差异大：`themeConfig` 撞 `ui.main.*`、`coverConfig` 撞
`BookCover`…），故**模块取域级 `:feature:settings`，内容按子页逐片迁入**：

```text
feature/settings/src/commonMain/kotlin/io/legado/app/feature/settings/
  lab/    # M5-2a：第一个子页（实验室页）
  # 后续：translation/ appearance/ reading/ backup/ …
```

第一批只有 `lab/`（4 文件 209 行，其中 `LabConfig.kt` 是 0 行空文件）。

### 意外工作（M5-2a-pre）：上提 `ClickableSettingItem`

本片规划时漏算的一件：实验室页用了 `ClickableSettingItem`，而它还在 `:core:ui`
（Android-only）。按 M1-3x-pre 的先例 `git mv` 到 `:core:designsystem/commonMain`
（**包名不变** ⇒ 36 个调用方 import 零改动）。

它的 Miuix 分支撞上和 `SwitchSettingItem` 当初**同一条**约束：`miuix-preference` 是本仓
用到的 miuix 制品里**唯一没有 desktop 变体**的那个（版本目录只有 `miuix-preference-android`）。
⇒ 沿用 M1-3t 的窄契约：给 `MiuixPreferenceRenderer` 加 `arrowPreference(...)`，
Android 实现留 `:core:ui`，由 `:app` 的 `PlatformServices.install()` 注入（注入点本来就在，
契约扩展后自动生效，**无需改 `:app`**）。未注入 ⇒ 落 Material3，与 `SwitchSettingItem` 同一判据。

⚠️ **契约扩展被既有契约测试抓住了**：`MiuixPreferenceRendererContractTest` 的匿名探针
编译失败（缺 `arrowPreference`）。这是**它该有的反应**——扩展契约时所有实现方（含测试探针）
必须显式跟上。已补探针实现；不为新方法加用例，因为该测试验的是宿主语义（未注入 ⇒ null），
不是渲染结果。

### 行为等价清单（逐条对照，本片有测试覆盖 3 条，其余靠编译+文案逐字一致）

| 行为 | 迁移前 | 迁移后 | 判据 |
|---|---|---|---|
| 设置读写 | VM 经 `LabSettingsGateway` | 同一 VM，逐字 | **3 条新增用例**：唯一 uiState 入口 / 导出不写设置 / 计数初值 |
| 导出诊断 | `ACTION_SEND` 分享 | Effect 由 `:app` entry 收，同一 `Intent` 构造 | 逐字搬运（含 `shareTitle`） |
| 分享标题 | `R.string.lab_page_estimate_diagnostics_share_title` | 同一条（**留在 `:app`**：只有这一处用，搬进共享层会成为零引用条目） | 同一表达式 |
| 引擎分流 | `ClickableSettingItem` 内 `isMiuixEngine` 二选一 | 同上，Miuix 分支经契约 | 契约实现与迁移前 `ArrowPreference(...)` 逐字等价 |
| 11 条页面文案 | `R.string.*` | `Res.string.*` | `verify-compose-resources.py`：4/4 逐字一致（`:app` 侧只剩 `lab_setting` 同名可比对，其余 10 条已随页面迁走） |

### 死资源

`:app` 侧 10 条 lab 文案 ×4 语言变零引用 ⇒ 删除。
**保留 2 条**：`lab_setting`（`ConfigNavScreen` 的条目标题仍在用）、
`lab_page_estimate_diagnostics_share_title`（`MainNavGraph` 的分享标题仍在用）。

### 验证

- `clean` 后：四门禁全绿（**无需下调 G4 基线**）；`:feature:settings` 的
  `compileKotlinDesktop` + `testAndroidHostTest`（**3 例 0 失败**）；
  `:core:designsystem:compileKotlinDesktop` / `:core:ui:compileDebugKotlin` / `:app` 编译+
  单测+`assembleAppDebug` 全绿
- 计数 **主集 710 → 713 / 全量 1205 → 1208**（各 +3 = 本模块新增用例），**0 失败**
- 资源：settings 4/4 逐字一致 + 10 条×4 语言"已下线"；回归 about 64/64
- `lintAppDebug` 仍 **5 errors / 78 warnings**（filtered warnings 246 → 245，因删了 40 行资源）

### 未验证

实验室页的**渲染与交互**无自动化覆盖：开关的联动显隐（`lab_enabled` → `lab_display` 组）、
`eInkDisplay` 开启后的提示文本、Miuix 引擎下 `ArrowPreference` 的实际外观、
以及「导出诊断 → 系统分享弹层」这一整条运行时路径。需真机冒烟。

### M5-2b / M5-2c：translation 页的三个前置资产上提

`ui/config/translation` 用了三个还在 `:core:ui`（Android-only）的组件，故在迁页面之前
先按 M1-3x-pre 的先例逐个上提（都是 `git mv`，**包名不变** ⇒ 调用方 import 零改动）：

| 片 | 资产 | 行数 | 撞到的约束 | 处置 |
|---|---|---|---|---|
| M5-2a-pre | `ClickableSettingItem` | 55 | `miuix-preference` 无 desktop 变体 | `MiuixPreferenceRenderer` 加 `arrowPreference` |
| M5-2b | `AppSlider.kt`（`sliderAccessibility`） | 62 | 无（只依赖 theme） | 直接搬 |
| M5-2b | `DropdownListSettingItem` | 87 | 同上，`OverlaySpinnerPreference` | 契约加 `overlaySpinnerPreference` |
| M5-2c | `SliderSettingItem` | 268 | **要带资源**：用的是 `:core:ui` 自己的 `R` | 4 条文案 ×4 语言搬进 designsystem 的 composeResources |

**契约面刻意不出现 miuix 类型**：`DropdownItem` → `List<String>`、
`startAction: (@Composable () -> Unit)?` → `imageVector: ImageVector?`
（否则共享层签名会绑死在一个 Android-only 制品上）。

⚠️ **契约被扩展了两次，两次都被 `MiuixPreferenceRendererContractTest` 的匿名探针抓住**
（缺新方法 ⇒ 编译失败）。这是它该有的反应：扩展契约时**所有**实现方（含测试探针）必须
显式跟上。不为新方法加用例——该测试验的是宿主语义（未注入 ⇒ null），不是渲染结果。

`SliderSettingItem` 与另外两个不同：**它的 Miuix 分支用 `miuix-ui` 的
`BasicComponent`/`Slider`/`TextField`（**有** desktop 变体），不走 `MiuixPreferenceRenderer`。
`:core:ui` 那 4 条文案的副本**不删**——`InputSettingItem.kt` 仍在用 `edit` 与 `text_default`。

至此 translation 页的前置资产齐了，下一片可以迁页面本体。
