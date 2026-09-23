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
| **translation** ✅ **已完成（M5-2d，2026-09-23，见末尾实录）** | 3 文件 / 174 行 / 9 条 | **0 硬阻塞、0 app 私有**（实测确认） | 最干净候选。依赖全在 core：`TranslationSettingsGateway`(core:data)、`TranslationConstants`(core:model)、`TranslationSettings`(core:data domain.model.settings)、ui.theme/components(core:ui)。`TranslationConfigRouteScreen` 就在 `TranslationConfigScreen.kt` 内且干净。VM 是纯 `ViewModel`，只依赖一个 gateway。**与 tagrules Stage B 完全同构，直接整域迁 `:feature:translation`。** |

### B 级：需先抽单一契约 / 留 RouteScreen 胶水在 host

| 子域 | 文件/行数 | 阻塞 | 迁移路径 |
|---|---|---|---|
| **customTheme** | 4 文件 / 487 行 / 15 条 | 仅 `CustomThemeRouteScreen.kt` import `lib.theme.ThemeStore`（app 私有主题引擎），用于 `ApplyLegacyPrimarySeed` effect | Contract/Screen/VM 干净可进 `:feature:customTheme`；`ThemeStore.editTheme(context).primaryColor(color).apply()` 这段副作用把 `ThemeStore` 下沉 core 或封装成 app host handler（注入）。RouteScreen 需同步处理。 |
| **labConfig** ✅ M5-2a | 4 文件 / 209 行 / 12 条 | 0 硬阻塞（仅系统分享 Intent，feature 内合法） | **已完成（2026-09-22）**，且是 `ui/config/*` 里**第一个**迁进 `:feature:settings` 的子域。见末尾实录。 |
| **otherConfig** | 6 文件 / 1157 行 / 71 条 | VM/Screen/Contract 干净；但 RouteScreen 依赖 app 私有 `service.WebService`(HARD) + `utils.{SystemUtils,restart,takePersistablePermissionSafely}`；DirectLinkUploadBottomSheet 依赖 `lib.dialogs.selector`(HARD) + clip/toast utils | 抽平台胶水（WebService 重启、权限、selector、clip/toast）到 `:core:platform`/host，Screen+VM 进 feature。工作量中等。 |
| **backupConfig** | 5 文件 / 1144 行 / 58 条 | VM/Contract/Sheets 干净（仅 app utils 软阻塞）；Screen 直连 `help.storage.ImportOldData`(HARD) | 把 `ImportOldData` 下沉 core:data 或封 host。需先抽契约。 |
| **ai** | 15 文件 / 2470 行 / 106 条 | ⚠️ **原判「0 硬阻塞，仅 `toastOnUi` 软阻塞」已被 2026-09-23 实测修正**。Screen 层确实干净，但**三个 ViewModel 自己就带着阻塞**：`AiProviderEditViewModel`(352) / `AiPromptConfigViewModel`(249) / `AiSummaryConfigViewModel`(124) 都直接 `import io.legado.app.R`（在 VM 里读文案）、`splitties.init.appCtx`；`AiPromptConfigViewModel` 还用 `utils.toastOnUi`；`AiModelEditViewModel` / `AiProviderEditViewModel` 用 `utils.GSON` | **不是最干净候选**。数据/编排层（domain gateway + domain.model）确实规范，但 VM 层把「文案 + 全局 context + Toast + JSON 门面」都带进来了 ⇒ 迁这一域要先解决「VM 里的文案与 appCtx 怎么去」。最小的一片是 `ai/summary`（3 文件 350 行），它的 VM 只用 `R` + `appCtx`（**无 GSON**）。 |
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

至此 translation 页的前置资产齐了。

### M5-2d：translation 页本体迁进 `:feature:settings/translation/`

**形态**：与 labConfig 同构——`Contract` / `ViewModel` / `Screen` 进 `commonMain`
（只改包名 + `R.string.*` → `Res.string.*`），`TranslationConfigRouteScreen` 留 `:app`。

⚠️ **与 labConfig 的差异**：本页**没有**平台动作。`TranslationConfigEffect` 是**空的**
sealed interface（迁移前就是这样：VM 备了 `effects` 流但没有分支），所以宿主那侧没有
Effect 要收——`MainNavGraph` 的 entry 里只剩下「取 VM、收 state、接导航回调」。
**Route 保留纯粹是为了 `koinViewModel()` 不进共享层**，不是为了隔离平台动作。

迁移前的 A 级判定（零 app 私有依赖）实测成立：VM 只依赖 `TranslationSettingsGateway`
（`:core:data` 端口），Screen 只多一个 `TranslationConstants`（`:core:model`）⇒ **零新增契约**。
它之所以排在三片资产上提之后才做，纯粹是因为 UI 组件当时还在 `:core:ui`。

**新增 3 条用例**（迁移前零测试）：初值来自 gateway 的 `currentSettings`、
`SetProvider` 经唯一 uiState 入口下发、另两个 Intent 各自映射到自己的字段
（第三条防的是 `when` 分支复制粘贴串行——`onIntent` 的 `when` 对 sealed interface
穷举，所以「漏处理」由编译期兜住，但「处理错字段」不会）。

**死资源**：9 条里 7 条变零引用 ⇒ 删除；保留 `translation_config`
（`ConfigNavScreen` 的条目标题）与 `ai_config`（AI 配置页在用）。

**验证**：四门禁全绿（无需下调基线）+ 新模块 desktop 编译与 `testAndroidHostTest`
（**6 例 0 失败** = lab 3 + translation 3）+ `:app` 编译/单测/打包；
计数 **713 → 716 / 1208 → 1211**（各 +3）；资源 **40/40 逐字一致**（删副本前跑的）。
`lintAppDebug` **errors 仍 5**；warnings 78 → **95**，增量**全部**是 `GradleDependency`
（"a newer version … is available"，38 条）——它是 lint 联网查询的结果、**非确定性**，
与本次改动无关；已确认报告里没有任何一条指向 `feature/settings` 或新迁的文件。

**未验证**：页面的渲染与交互无自动化覆盖——下拉选择（provider / 目标语言）、
滑块的「默认值 / 范围 / 步进」三参数、以及 `provider == PROVIDER_APP_AI` 时
才出现的「应用内 AI」跳转条目。需真机冒烟。

### M5-3a-pre / M5-3b：customTheme 页

**M5-3a-pre（资产）**：页面用了 `ColorPickerSheet`，它还在 `:core:ui` ⇒ 按先例上提
（182 行 + 4 条文案）。依赖链很浅：`AppTextField` / `MediumTonalButton` /
`AppModalBottomSheet` / `AppText` 都已在 designsystem，Miuix 的 `ColorPicker` 系列在
`miuix-ui`（有 desktop 变体）⇒ **不撞** `miuix-preference`，与 `SliderSettingItem` 同形。

**M5-3b（页面）**：`Contract` / `ViewModel` / `Screen` 进 `:feature:settings/customtheme/`，
`RouteScreen` 的两个 Effect 留宿主：
`ApplyLegacyPrimarySeed` → `ThemeStore.editTheme(context).primaryColor(…).apply()`，
`SettingsUpdateFailed` → Toast。这与前两页都不同——**本页是三页里唯一「Route 留下是因为
真有平台动作」的**（translation 只为了 `koinViewModel()`，labConfig 是 `ACTION_SEND`）。

#### 两处不在编译期暴露的坑

1. **`stringArrayResource` 返回类型变了**：Android 返回 `Array<String>`，CMP 返回
   `List<String>` ⇒ 调用点要加 `.toTypedArray()`（本仓第一次把 `string-array` 搬进
   composeResources）。6 个 array 的覆盖情况与 `:app` 一致：`paletteStyle` /
   `customContrast` / `materialVersion` 在 `arrays.xml`（zh-rCN 有覆盖），
   三个 `*_value` 只在默认语言的 `array_values.xml`（靠 fallback）。
2. **`Integer.toHexString` 在 `commonMain` 不存在**：它和 `Int.toString(16)` 有一处
   实质差异——对负数给**无符号**十六进制（`0xFF000000.toInt()` ⇒ `"ff000000"`），
   而 `toString(16)` 会给 `"-1000000"`。颜色值确实可能为负 ⇒ 必须用
   `.toUInt().toString(16)` 才逐字等价。**编译不会报**，只会让颜色显示错。

#### 死资源：无

15 条 string 与 6 个 array 在 `:app` 侧**全部仍在被其它页面引用**
（themeManage 等共用）⇒ **不删副本**。这是本批第一片没有死资源清理的。

#### 验证

四门禁全绿 + 新模块 desktop 编译与 `testAndroidHostTest`（**9 例** = lab 3 +
translation 3 + customTheme 3）+ `:app` 编译/单测/打包；计数 **716 → 719 / 1211 → 1214**；
资源 **72/72 逐字一致**；lint errors 未增加。

#### 未验证

深度自定义/种子色两套 UI 的切换、6 个下拉的实际取值、颜色选择器在 Miuix 下的外观、
以及「改种子色后旧主题引擎是否真的跟上」（`ThemeStore` 那条路径）。需真机冒烟。

### M5-4a-pre / M5-4b：ai/summary 页（ai 域第一片）

**M5-4a-pre（资产）**：`InputSettingItem`（133 行 + `confirm` 等 3 条文案）上提。
形态与 `SliderSettingItem` / `ColorPickerSheet` 同形（Miuix 用 `miuix-ui`，不撞契约）。
本次**删掉了 `:core:ui` 的 `edit` / `text_default` / `confirm` 副本** —— 随着两个
settingItem 先后迁走，它们在 `:core:ui` 内已零引用（与 M5-2c 那次"保留"相反，
因为那时 `InputSettingItem.kt` 还在用）。

**M5-4b（页面）**：本批第一个「**VM 自己带着平台依赖**」的页——迁移前 VM 直接
`appCtx.getString(R.string.x)` 拼提示（3 处）。照 M5-1c 的 about 先例改成
**发枚举 + UI 侧查表**（`AiSummaryMessages.kt`，只有它 import `Res`）。

⚠️ **契约必须分成两个 Effect**，否则会丢语义：`save()` 失败时原文是
`error.message ?: getString(ai_config_save_failed)` —— 「**有**异常文案就用它，
**没有**才回落资源文案」。合成枚举的一个参数会丢失这个 fallback ⇒
`ShowMessage(AiSummaryMessage)`（资源）+ `ShowRawMessage(text)`（运行期）。
4 条新增用例里有 2 条专门钉这条。

另一个值得记的点：**本页的提示不走宿主**。Screen 自己收 Effect 并显示 **Snackbar**
（迁移前就是这样），所以宿主那侧只剩「取 VM、收 state」——与 translation 同形。
这与 labConfig（宿主解释 `ACTION_SEND`）、customTheme（宿主调 `ThemeStore` + Toast）
又不一样：**同一个 Feature 域里，Route 留下的理由已经出现三种**。

**G4 随之下调**：`appCtx|app/main/io/legado/app/ui/config/ai/summary` **1 → 0**（条目删除）
—— 删掉 VM 里那处 `appCtx` 后该目录归零，**门禁主动拦下并要求下调**，棘轮生效。

**死资源**：17 条里 6 条删除（`ai_chapter_summary_config` 等），其余仍被其它 ai 页面共用。
另有 10 条在 `:app` 的 zh-rHK/TW 里本来就没有（靠 fallback 到默认语言），与迁移前一致。

**验证**：四门禁全绿 + 新模块 desktop 编译与 `testAndroidHostTest`（**13 例** = lab 3 +
translation 3 + customTheme 3 + aiSummary 4）+ `:app` 编译/单测/打包 + 全模块测试；
计数 **719 → 723 / 1214 → 1218**；资源 **120/120 逐字一致**（删副本前跑）。

**未验证**：Snackbar 的实际弹出（含 `localizedText()` 在真实资源表下的取值）、
滑块与输入项的交互、编辑提示词弹层。需真机冒烟。

### M5-4c：ai/prompt 页（本域最重的一页）

**两处结构性改动**（都不是机械搬迁）：

1. **`AiPromptTaskItem` 不再存 Android 资源 id**。迁移前它是 `nameResId: Int` /
   `descResId: Int`，Screen 用 `stringResource(item.nameResId)` 取文案 —— **资源句柄泄漏进
   UI 状态**，而 CMP 的资源不是 `Int`。⇒ 改成 `task: AiPromptTask` 枚举，文案由
   `AiPromptMessages.kt` 查表（`displayName()` / `description()` 是 `@Composable`，
   `defaultPrompt()` 是 `suspend`——**默认提示词要写回 gateway，VM 必须拿到实际字符串**，
   所以这里 VM 允许 import `Res`，与 ai/summary 的「VM 零资源」不同）。
   这条**编译期不报**（`Int` 在 commonMain 合法），只有跑起来才会发现取不到文案。

2. **保存成功的提示不走 Effect**：迁移前是 `appCtx.toastOnUi(...)`（**Toast**），而
   `ShowMessage` 在 Screen 里是 **Snackbar** ⇒ 为保住这个差异，那一条改由 VM 注入的
   `Toaster`（`:core:platform` 既有契约）直发。其余提示照旧走 Effect + Snackbar。

**⚠️ 本片最有价值的发现：aapt2 与 CMP 对字符串转义的展开层数不同**

提示词里含 `\"replacement\"` / `reader\'s`。同一个 XML 片段，**aapt2 会把 `\"` / `\'`
展开成裸字符，而 CMP 的资源生成器不展开**，原样留下反斜杠 ⇒ 用户会在提示词里看到多余的反斜杠，
**而 `commonMain` 照样编译通过**。`verify-compose-resources.py` 报 **7 条不一致**，实测修正
13 处后 **226/226 逐字一致**。
⇒ 搬运这类文案时**必须在 CMP 侧写裸字符**（XML 元素文本里的 `"` / `'` 无需转义）；
并且**以那个脚本为准，不是以编译器为准**。

**G4 随之下调**：`appCtx|app/main/io/legado/app/ui/config/ai/prompt` **1 → 0**（条目删除）。

**死资源**：37 条里 33 条删除（8 个任务类型 ×3 的文案 + 页面提示），保留
`ai_prompt_config` / `confirm` / `cancel` 与 `ai_prompt_default_bookshelf_auto_group`（别处仍在用）。

**验证**：四门禁全绿（G4 按下调）+ `:app` 编译/单测/打包 + 全模块测试；
计数 **723 / 1218 零偏离**（本片未新增用例，见下）；资源 **226/226 逐字一致**。

**未新增用例（与前面几片的差异，需记一笔）**：本页 VM 现在会调
`getString(Res.string.*)`（默认提示词要写回 gateway），构造 VM 就会触碰 CMP 资源运行时。

**⇒ M5-4d 探针已把这个前提量出来了（2026-09-23，做法同 M1-4 的桌面探针）：结论是「不可读」。**

在 `androidHostTest` 里 `getString(Res.string.confirm)` 抛

```
MissingResourceException: Missing resource with path:
  composeResources/io.legado.app.feature.settings.res/values/strings.commonMain.cvr.
  Android context is not initialized.
```

即使 `@Config(application = Application::class)` 也一样 —— CMP 资源的 Android 实现要一个
**已初始化**的 Context，Robolectric 宿主不满足。探针验证完即删除（不留一个必然红的测试），
结论写进 skill。

**这条的后果超出「测试写不了」，它是一条架构判据**：

- **只在展示用的文案 ⇒ VM 发枚举、UI 侧查表**（`AboutMessage` / `AiSummaryMessage` 的做法）。
  这样 VM 可构造 ⇒ **可测**。这不是风格偏好，是可测性差异。
- **VM 真的要把字符串当数据用**（`AiPromptConfigViewModel` 把默认提示词写回 gateway）⇒
  仍然**不要**在 VM 里调 `getString`，而是**把值注入进去**。

### M5-4e：把这条判据落地，补回 ai/prompt 的用例

新增 `AiPromptStringSource`（可注入的资源字符串来源），VM 构造参数从「自己 `getString`」
改成注入：

| VM 需要的字符串 | 迁移前的取法 | 现在 |
|---|---|---|
| 8 个任务类型的默认提示词（写回 gateway） | `appCtx.getString(meta.defaultPromptResId)` | `strings.defaultFor(task)` |
| 「保存成功」（发 **Toast**） | `appCtx.toastOnUi(R.string.ai_config_saved_success)` | `toaster.toast(strings.savedMessage())` |

生产实现 `composeResourcePromptStrings()` 读本模块 composeResources，由 `:app` 的 Koin
module 绑定（与其它平台能力同一装配方式）。**测试实现给假值** ⇒ VM 重新可构造。

**补回 4 条用例**（M5-4c 欠下的）：

1. `init` 的**取值优先级**：gateway 里有已存提示词就用它、没有才用默认值
   ——写反了会让用户的自定义提示词每次进页面被重置；
2. 保存成功走 **Toast**（`Toaster`）而非 Effect/Snackbar（本页与 ai/summary 的差异）；
3. 失败路径 fallback：有异常文案用它，没有才回落资源里的「保存失败」；
4. 重置单个用默认提示词并提示成功。

**验证**：四门禁全绿 + `:feature:settings:testAndroidHostTest`（**17 例** = lab 3 +
translation 3 + customTheme 3 + aiSummary 4 + aiPrompt 4）+ `:app` 编译/单测/打包 +
全模块测试；计数 **723 → 727 / 1218 → 1222**；资源未变（**226/226**）。

### M5-5a：ai 主入口页（本域最干净的一个 VM）

`AiConfig`（Screen 201 / VM 107 / Contract 51）迁进 `:feature:settings/ai/`。

它是本批**最干净**的 VM：唯一依赖 `AiProfileGateway`，**不碰** `R` / `appCtx` / `GSON` /
`toastOnUi` ⇒ 可测性没有障碍（不像 ai/prompt 要 M5-4e 那样注入字符串来源）。

两条提示文案是**硬编码英文**（`"Default AI model saved"` / `"Failed to save default AI model"`）
——迁移前就如此，**原样保留**（改成资源会让 4 个语言的文案发生变化，那是另一个决策）。
因此 `AiConfigEffect.ShowMessage` 携带的是裸 `String` 而非枚举，与本域另两页不同。

**新增 4 条用例**（迁移前零测试）：

- 模型按 provider 归组，且**所属 provider 未知的「孤儿」模型被过滤掉**
  （否则下拉面板会出现没有 provider 名可显示的分组）；
- **「当前模型」的取值优先级**：先看默认的**翻译**预设指向哪个模型，没有预设才退到
  「第一个模型」——写反了会让主页面显示的当前模型与翻译页实际用的不一致；
- 没有默认翻译预设时退到第一个模型；
- 设为默认的两条硬编码英文提示逐字。

⚠️ **写用例时被纠正了一个既有语义**：`modelCount` 是**原始**模型数（**含**孤儿），
而 `models.size` 是过滤后的可展示项，两者**本来就不等**。我起初按相等断言，被测试纠正；
差异已钉进用例注释（不是本片引入的，保持等价）。

**死资源**：17 条里 11 条删除；其余 6 条仍被尚未迁移的 `AiProviderEdit` / `AiModelEdit` 使用。

**验证**：四门禁全绿（本目录无 G4 条目需下调——主域 VM 本来就不用 `appCtx`）+
`:feature:settings:testAndroidHostTest`（**21 例**）+ `:app` 编译/单测/打包 + 全模块测试；
计数 **727 → 731 / 1222 → 1226**；资源 **164/164 逐字一致**（删副本前跑）。

### M5-5b：AiModelEdit 页

`AiModelEdit`（Screen 239 / VM 167 / Contract 48）迁进 `:feature:settings/ai/`。

**唯一的非机械改动**是 ai 域两处 `GSON` 之一：
`GSON.fromJson(json, AiGenerationParams::class.java)` → `JsonCodec.fromJsonObject(json, AiGenerationParams::class)`
（`:core:platform` 的 `expect object`，平台原语、无需注入）。

⚠️ **顺带修掉一个空安全差异**：`GSON.fromJson` 在 Kotlin 里是**平台类型**，返回 null 时
`runCatching{}.getOrDefault(...)` **兜不住**（null 不是异常）⇒ 后面 `params.temperature` 会 NPE。
`JsonCodec.fromJsonObject` 的返回类型是显式的 `T?`，所以改成 `getOrNull() ?: AiGenerationParams()`。
对正常 JSON 行为等价，把潜在 NPE 变成明确的回落值；用例 2 专门钉「非法 JSON 回落而不崩」。

**另一个跨文件的坑**：`formatTokenLimit` 原来定义在 `AiModelEditScreen.kt` 里且是 `internal`，
而**同包的 `AiProviderEditScreen` 也在用它** ⇒ 迁走后 `:app` 编译不过。
处理：在 `:app` 留一份**临时副本** `ai/TokenLimitFormat.kt`，注释里写明
「`AiProviderEdit` 迁走（ai 域收官）后删除」。

**G4 随之下调**：`gson|app/main/io/legado/app/ui/config/ai` **2 → 1**
（剩下的 1 处在尚未迁移的 `AiProviderEditViewModel`）。

**新增 4 条用例**（迁移前零测试）：`defaultParamsJson` 经 `JsonCodec` 反序列化进状态
（钉本片的实质改动）/ 非法 JSON 回落默认参数而不崩 / **`initialized` 之后流刷新不覆盖用户
正在编辑的字段**（写反了会让用户输入的字在保存前被抹掉）/ 保存成功发「提示 + 返回」并回写
`modelProfileId`。

**死资源**：14 条里 3 条删除；其余 11 条仍被未迁的 `AiProviderEditScreen` 使用。

**验证**：四门禁全绿（G4 按下调）+ `:feature:settings:testAndroidHostTest`（**25 例**）+
`:app` 编译/单测/打包 + 全模块测试；计数 **731 → 735 / 1226 → 1230**；
资源 **176/176 逐字一致**（删副本前跑）。

### M5-5c：AiProviderEdit 页（ai 域收官）

`AiProviderEdit`（Screen 384 / VM 352 / Contract 98）迁进 `:feature:settings/ai/` ——
**ai 域最后一页**，也是本域里平台依赖最集中的一页（搬迁前同时用 `R` / `appCtx` / `GSON`）。

**两处非机械改动**：

1. **`GSON.fromJson` → `JsonCodec.fromJsonObject`** —— ai 域两处 `GSON` 的最后一处。
   同 M5-5b 顺带修掉空安全差异（`GSON.fromJson` 是平台类型、null 时 `getOrDefault` 兜不住）。
2. **三处 `appCtx.getString(R.string.*)` → 注入的 `AiProviderStringSource`**。那是「测试连接」
   的结果提示（取到几个模型 / 失败原因）。沿用 M5-4e 为 ai/prompt 建立的注入模式——
   M5-4d 的探针已量出**不能**改成在 VM 里 `getString`（会让 VM 不可测）。

其余提示保持**硬编码英文**（"AI model saved" / "AI provider saved" / "No models found" /
"Fetched and saved N models" …）——迁移前就如此。

**G4 同时下调两条**：`appCtx|…/ui/config/ai` **1 → 0** 与 `gson|…/ui/config/ai` **1 → 0**
（条目删除）。这是本批第一次一片内同时归零两个维度。

#### ai 域收官：`ui/config/ai` 整个目录清空

四片（M5-5a 主入口 / M5-5b modelEdit / M5-5c providerEdit / M5-4b-4c summary+prompt）
搬完后，`:app/src/main/java/io/legado/app/ui/config/ai` **不再有文件**。

顺带清掉一处跨片遗留：M5-5b 为未迁的 `AiProviderEditScreen` 在 `:app` 留了一份
`formatTokenLimit` 的**临时副本** `ai/TokenLimitFormat.kt`（因为原函数是 `internal` 且同包）
—— 本片把最后一页也迁走后，那份副本**已删**；共享层里
`AiModelEditScreen.kt` 的 `internal fun formatTokenLimit` 与 `AiProviderEditScreen.kt` 同包，
直接可见。

**新增 5 条用例**（迁移前零测试）：

- `init` 填充 provider 字段，且 `initialized` 之后流刷新**不覆盖**用户正在编辑的字段；
- 测试连接「0 个模型」用注入的文案；
- 测试连接「N 个模型」把 `count` 传进**格式参数**；
- 失败时拼成 `"兜底文案: 详情"`（含 `error.message` 为空时只发兜底文案的分支）；
- `defaultParamsJson` 经 `JsonCodec` 反序列化进模型列表。

前三条钉的是本片的注入改动：假文案源给的是**带标记的假值**（`[no-models]` /
`[with-models:3]` / `[failed]`），所以「VM 真的走了注入路径」是被断言验证的，
而不是"能编译就行"。

**死资源**：28 条里 24 条删除；保留 `hide_password` / `show_password` / `ok` / `delete`
（`:app` 别处仍在用）。

**验证**：四门禁全绿（G4 按上述下调两条）+ `:feature:settings:testAndroidHostTest`
（**30 例**）+ `:app` 编译/单测/打包 + 全模块测试；计数 **735 → 740 / 1230 → 1235**；
资源 **276/276 逐字一致**（删副本前跑）。

**未验证**：页面的渲染与四个对话框的交互（API Key 的可见性切换、协议/预设两个下拉的联动、
模型编辑表单、两个删除确认）以及「测试连接 / 同步模型」两条真实的网络路径。需真机冒烟。

**下一步**：`otherConfig` / `backupConfig`（B 级，需先抽 `WebService` / `ImportOldData` 胶水），
或 `themeConfig`（撞 `ui.main.*`，成本更高）。

### M5-6a：删除 3 个零引用的 `@Deprecated` 兼容壳

`ui/config` 剩 52 文件里，有 3 个是**设置下沉到 gateway 时留下的过渡壳**，共 109 行：

| 文件 | 行数 | 代理的 gateway |
|---|---|---|
| `importBookConfig/ImportBookConfig.kt` | 14 | `ImportBookSettingsGateway` |
| `readMangaConfig/ReadMangaConfig.kt` | 39 | `MangaSettingsGateway` |
| `bookshelfConfig/BookshelfConfig.kt` | 56 | `BookshelfSettingsGateway` |

三者都带 `@Deprecated("使用 XxxSettingsGateway.currentSettings 读取，通过 update() 写入")`，
调用方早已全部改走 gateway ⇒ **全仓零引用**（脚本扫 `.kt`/`.java`/`.xml`/`.kts` 确认）。

**为什么删而不是迁**：它们的依赖已全在共享层，`git mv` 进 `:feature:settings` 做得到——
但那只是**把死代码换个地方放**。`@Deprecated` + 零引用 ⇒ 直接删。

⚠️ **一处生成物副作用**：git 跟踪的
`app/src/appNoR8/generated/baselineProfiles/{baseline,startup}-prof.txt` 里有
`BookshelfConfig` 的旧条目（`baseline-prof.txt:22730-22743`）。那是上次在设备上跑
baseline profile 任务生成的记录，**不手改**——类不存在时该编译器会忽略这些条目，
下次在设备上重跑任务即自动消失。

### M5-6b：`ConfigNavScreen` → `:feature:settings/nav/`

`:feature:settings` 里**第一个不是子页面迁移**的成员：它是**设置域首页**（9 项导航列表），
原来住在 `ui/config` 的根包下。它迁进来标志着模块从「子页面的集合」变成「域」。

- **零硬阻塞**：9 个导航动作全是 `() -> Unit` 回调 ⇒ 宿主侧**无需改 `MainNavGraph` 的
  entry**（它本来就已写成 9 个回调形态），只换一行 import。
- 无 VM、无 Effect、无 `R.string` 以外的资源 ⇒ 本模块里平台依赖最少的一页。
- 10 条文案：7 条新增、3 条（`ai_config` / `translation_config` / `lab_setting`）此前
  因为**这一页还在 `:app`** 而保留在 `:app`、也早已在 `:feature:settings` 里。**本片无死资源**：
  7 条在 `:app` 侧仍被其他未迁子页引用（`theme_setting` 2 处 / `other_setting` 5 处 /
  `read_config` 4 处 / `cover_config` 4 处 / `backup_restore` 4 处 / `download_cache_config` 1 处 /
  `setting` 12 处）。
- **`ConfigTag` 留在 `:app`**：`ui/config/ConfigTag.kt`（12 行常量）只被 `:app` 的 `MainIntent`
  用来把 deep-link 的路由 tag 映射成 `MainRouteConst`——纯 `:app` 内部路由细节，本页用不到。

**本片不加测试，理由记录在此**：这是一个**无状态、无 ViewModel** 的纯组合函数（9 个按钮 +
9 个回调）。`:feature:settings` **没有 Compose UI 测试基建**（`ui-test` 不在任何共享模块的
build 文件里；本模块 8 个测试文件全是 ViewModel 测试），要给它写测试就得先引入
`org.jetbrains.compose.ui:ui-test` —— 与 M2-8 拒绝 `koin-test` 同一条判据：
**不在接线片里顺带引入测试依赖**（独立风险维度）。宁可写明「未加测试及原因」，
也不写一个只是把源码再断言一遍的假测试。

验证：`:app:compileAppDebugKotlin` + 四门禁 + `:feature:settings` 测试（30 例）+ `:app` 单测/打包
+ 全模块测试全绿；计数 **740 / 1235 零偏离**（本片无用例增减）；资源 **208/208 逐字一致**；
`lintAppDebug` 仍 **5 errors / 95 warnings**。

**未验证**：首页列表的渲染，以及 9 条导航的实际跳转（点击「外观」是否真的到外观页）。
需真机冒烟。

**下一步**：`otherConfig` / `backupConfig`（各需先抽 1–2 个平台契约：
`WebService` 状态 / `Permissions` / `ImportOldData`），或 `themeConfig`（撞 `ui.main.*`
的 10 个 `Launcher*` 图标资源，成本更高）。

### M5-7：`downloadCacheConfig` → `:feature:settings/downloadcache/`

剩余子域里最小的一块（4 文件 / 420 行），也是**第一次需要新抽平台契约**（前几片
`lab` / `translation` / `ai` 的依赖恰好都已在共享层）。

#### 新契约 `DownloadCachePlatform`（5 个成员）

迁移前 VM + Screen 有 **5 处**平台直连，互不同源，但都服务于「让用户看到并清掉各类缓存」：

| 迁移前 | 为什么不能进 commonMain |
|---|---|
| `CacheBook.maxDownloadConcurrency` | 下载引擎（`:app` 的 `model.CacheBook`）的并发上限 |
| `getHttpCacheSize(HttpCacheType.COVER/MANGA)` | 走 `appCtx.cacheDir` 遍历文件系统 |
| `clearHttpCache(HttpCacheType.COVER/MANGA)` | 直接 `okHttpClient.cache?.delete()` |
| `FileUtils.delete(appCtx.cacheDir.absolutePath)` + `externalCacheDir?.deleteRecursively()` | `Context` + `java.io.File` |
| `ImageProvider.bitmapLruCache.resize(ImageProvider.cacheSize)` | `android.graphics.Bitmap` 的 LruCache |

两条边界判断：

1. **`maxDownloadConcurrency` 走契约而不是在共享层写 `const val 8`。** 它是纯数值、看着能搬，
   但那样**引擎与共享层各有一份上限**，日后引擎放宽会静默漂移（表现为「滑块拉不到底」，
   没人会报）。实现侧转发 `CacheBook.maxDownloadConcurrency`，唯一真源留在引擎。
2. **`clearCacheDirectories` 与 `ClearBookCacheUseCase` 不合并**：条目清理由 `:core:data`
   的 use case 负责，契约只做它之后那步「把缓存目录本身也删掉」。迁移前这两步就在同一个
   `when` 分支里前后相邻 —— 但合并会让 use case 失去独立性。

**`HttpCacheKind` 而非搬 `HttpCacheType`**：共享层只留「是哪一种」两值枚举，不带
`dirName` / `maxSize`（存储与策略细节）。

#### 一处结构变更：UI state 新增 `maxDownloadConcurrency`

迁移前 **Screen 自己**读 `CacheBook.maxDownloadConcurrency`（3 处：当前值夹取、`defaultValue`、
`valueRange` 上界）。共享层的 composable 拿不到 `:app` 的 `CacheBook`，故由 VM 从契约填进 state
（另一条路是宿主把值当参数传，那会让 `MainNavGraph` 也依赖平台契约）。

该字段**刻意不给默认值**：给 `= 8` 等于在共享层写死第二真源 —— 与上面第 1 条同一个理由，
编译器会拦住漏填。

#### G4 基线：两根棘轮同时下调 + 一个「净变化为零」的上调

```
appCtx    |…/ui/config/downloadCacheConfig| 1 → 0   （条目删除）
legacyHelp|…/ui/config/downloadCacheConfig| 3 → 0   （条目删除）
legacyHelp|…/platform                    | 2 → 5   ← 上调，但净变化为零（5 = 2 + 3）
legacyNaming|…/ui/config/downloadCacheConfig| 1 → 0 （条目删除）
legacyNaming|…/platform                  | 2 → 3   ← 上调，净变化为零（3 = 2 + 1）
```

`platform`（`io.legado.app.platform`）是「共享契约的 Android 适配」的指定住所，
**不在 `reportOnlyAreas`** ⇒ 出现「新增」是硬失败。先例是 M5-1c（about 的三个契约同样把
`ui/about` 的 `legacyHelp` 搬进 `platform` 并上调基线）。本次照同一模式处理，
并在基线文件里逐条注释「同一段平台逻辑换住所，不是新增债」。

#### ⚠️ 踩到一个门禁盲点：带别名的 import 逃过计数

契约方法 `clearHttpCache(kind)` 与 `help.http` 的顶层 `clearHttpCache(type)` **同名**，
类体内直接调会解析到自己的成员 ⇒ 无限递归。常规解法是
`import io.legado.app.help.http.clearHttpCache as clearOkHttpCache`。

但门禁的 `legacyHelp` 规则是 `^import io\.legado\.app\.help\.[A-Za-z0-9_.]+$`（**行尾锚定**），
别名形式**不匹配** ⇒ 实测 `platform` 只 +2 而非 +3，**凭空少记一笔**。

本片的意义就是「把耦合从 ui 层搬进 platform 并记账」，记账少一笔就失去意义。故**改用
顶层私有函数**（类外没有成员遮蔽，`clearHttpCache(...)` 正常解析到 `help.http`），
import 保持最朴素的形式、如实计数。盲点本身已记进
`legacy-architecture-report.md` §7「已知局限」，附带修复方向（规则行尾放宽为
`(?:\s+as\s+[A-Za-z0-9_]+)?$`，属独立切片）。

#### 两处非逐字改动（已注释说明）

1. **`launch(Dispatchers.IO)` → `launch`**：调度器下沉到实现侧（契约标 `suspend`，
   Android 实现内部 `withContext(IO)`），与 `AndroidAboutDiagnostics` 同一写法。
2. **`loadCacheSizes` 里两次取大小从 `_uiState.update { }` 的 lambda 内挪到外面**：
   契约里是 `suspend`，而 `update { }` 的 lambda 不是挂起上下文。语义不变
   （迁移前也是先封面后漫画），新写法反而少在原子更新里做两次阻塞 IO。

#### 新增 7 条用例（迁移前零测试）

`平台并发上限进 state 并用于夹取`（含上下界与区间内）/ `缓存字节→MB 换算` /
`改图片缓存大小同时写设置并让平台重新分配` / `清封面缓存调平台并归零` /
`清书缓存是「先 use case 清条目、再平台清目录」两步且不动 OkHttp 缓存` /
`收缩数据库只走 use case` / `设置流变化刷新 state`。

⚠️ 用例重心是**契约交互**（断言调了平台的哪个方法、按什么参数），不只断言「状态变了」
—— 本片的实质改动就是接线，只断言状态抓不到接错。

#### 验证

- 四门禁全绿（G4 按上述五条处理）+ `:feature:settings` 的 desktop 编译与
  `testAndroidHostTest`（**37 例 0 失败**）+ `:app` 编译/单测/打包 + 全模块测试
- 计数 **740 → 747 / 1235 → 1242**（+7），零失败
- 资源 **308/308 逐字一致**；死资源 12 条删除（保留 `threads_num_title` / `pre_download` /
  `bitmap_cache_size` / `user_agent` / `clear_cache` / `sure_del` / `other_setting` 等 15 条 ——
  在 `:app` 侧仍被其他未迁子页引用）
- `lintAppDebug` 仍 **5 errors / 95 warnings**

#### 未验证

页面的渲染与四个确认对话框的交互，以及**四条真实平台路径**（清 OkHttp 缓存、
清缓存目录、`ImageProvider` 重分配、收缩数据库）。需真机冒烟。

**下一步**：`backupConfig`（5 文件 / 1144 行；需抽 `Permissions` / `ImportOldData`）或
`otherConfig`（1157 行；需抽 `WebService` 状态 / `Permissions` / 最后 1 处 `GSON`）。

### M5-8a：`otherConfig` 的**逻辑层**（Contract + VM）→ `:feature:settings/otherconfig/`

本片是**按层分片**，不是按页整体迁移 —— 这是一个刻意的粒度选择，理由在下面第一段。

#### 为什么拆成两片

先做的勘察发现：`otherConfig` 的六个文件里，**VM(374) 和 Screen(253) 只依赖 `R`**，
而平台耦合集中在 `OtherConfigRouteScreen`(146，权限/SAF/重启/WebService) 与
`DirectLinkUploadBottomSheet`(215，剪贴板/GSON/文件选择)。同时 Screen 需要
**54 条文案 + 4 个 `string-array`**。

而那个数组有个坑：`default_app_variant` 的 4 个条目是 **`@string/*` 间接引用**，
而共享层的数组约定是**纯字面量**（`paletteStyle` 那批），需要先展开成各语言的实际文本。
⇒ 把「逻辑迁移」（本片）与「页面 + 资源迁移」（下一片）分开，各是一个风险维度。

#### 本片只处理两处平台耦合（其余前置工作先前已做完）

`OtherConfigViewModel` 需要的八个依赖里**七个本来就是共享契约**
（`OtherSettingsGateway` / `ReadAloudSettingsGateway` / `AppLocaleGateway` /
`DownloadCacheSettingsGateway` / `DirectLinkSettingsGateway` / `LocalPasswordGateway` /
`OtherConfigSystemGateway`）——都是先前切片抽出来的。所以只剩：

| 迁移前 | 迁移后 | 说明 |
|---|---|---|
| `OtherConfigMessage.resId: Int?`（`@StringRes`） | `res: OtherConfigMessageRes?` 枚举 + 表 | 沿用 `AboutMessage.localizedText()` 的模式；表只在 `OtherConfigMessageText.kt` 里 import `Res`，公开契约零资源依赖 |
| `AppLog.put(msg, throwable)` | `AppLogStore.put(msg, throwable)` | `:core:platform` 的共享实现。迁移前那行是**两参形式**（不带 `toast`）⇒ 不含「轻提示」与「Logcat 直投」，行为等价 |
| `R.string.clear_webview_data_success` 等 3 处 | `OtherConfigMessageRes.*` | 三处分支各映射一个枚举值 |

#### ⚠️ 门禁拦下了一个「顺手改好」的念头

`OtherConfigRouteScreen` 渲染消息时原本是 `Toast.makeText(...)`。迁移顺手把它换成共享的
`Toaster` 很自然（前面几片都这么干），但门禁直接报：

```
app/main/io/legado/app/ui/config/otherConfig：core Provider 静态委托首次出现 2 处；
新区域必须为零，或经评审后在基线中显式登记
```

`ToasterProvider` 是 **service locator**，用它会让 `ui/config/otherConfig` 成为 `coreProvider`
的**新区域**（import 行 + 调用点 = 2 处）。棘轮的意图正是「新代码不要引入新的静态委托」
⇒ **改回 `Toast.makeText`**（同一文件下一段的 `permission_not_required` 提示本来就是它，
保持一致）。这是门禁在设计上赢了直觉的一次。

#### ⚠️ 一处我该先发现的重复：`:app` 里已有一份 VM 测试

动 VM 之前我只查了**目标模块**的测试目录（`feature/settings`），没查源模块 ——
而 `:app` 早已有 `app/src/test/.../ui/config/otherConfig/OtherConfigViewModelTest.kt`（**5 例**）。
它是在完整构建那一步才暴露的（编译不过），此时我已新写了 8 例。

处理方式：**合并**成一份并删掉 `:app` 那份。原用例有几条我漏掉的覆盖 ——
语言变更**同时**刷新 `uiState`、两条失败消息**排队**且逐条确认、直接链接规则的**成功**路径
（写 `savedRule` + 关闭弹层）。合并后 9 例。

⇒ 计数因此是 **净 +4**（9 新 − 5 旧），不是 +9。教训已写进 skill checklist：
**迁 VM 前先 `grep 类名` 找它已有的测试**。

#### 另一处：我自己写错的断言（测试抓到了）

「直接链接规则必填缺失」那条用例我原本断言「弹层保持打开」，但**测试里根本没先打开弹层**
⇒ 断言在 `activeOverlay == null` 上假通过。改成先 `ShowOverlay(DirectLinkUpload)` 再确认，
断言才有意义（钉「校验失败时弹层不能关，成功路径才 `copy(activeOverlay = null)`」）。
这类「断言恒真」的测试比没有测试更糟 —— 它会让人以为该分支被覆盖了。

#### 验证

- 四门禁全绿（**G4 无需任何基线变动** —— VM 带走的 `R.string` / `AppLog` 都不在门禁口径内）
- `:feature:settings` 的 desktop 编译与 `testAndroidHostTest`（**46 例 0 失败**）
+ `:app` 编译/单测/打包 + 全模块测试
- 计数 **747 → 751 / 1242 → 1246**（净 +4），零失败
- 资源 **272/272 逐字一致**；死资源 3 条删除（`clear_webview_data_success` / `_failed` /
  `complete_required_information`）
- `lintAppDebug` 仍 **5 errors / 95 warnings**

#### 本片后 `ui/config/otherConfig` 的剩余

`OtherConfigScreen.kt`（253，54 条文案 + 4 个数组）与两个平台文件
（`OtherConfigRouteScreen.kt` 146、`DirectLinkUploadBottomSheet.kt` 215）仍在 `:app`。
**下一片**：迁 Screen（重点是把 `default_app_variant` 的 `@string/*` 展开成字面量）。
`RouteScreen` 与 `DirectLinkUploadBottomSheet` 是**宿主壳**（`rememberLauncherForActivityResult`
/ `selector` / 剪贴板），按现状留 `:app` —— 与 lab / ai 的 host 拆分同一判据。

### M5-8b：`OtherConfigScreen` → `:feature:settings/otherconfig/`（otherConfig 页面收官）

51 条文案 ×4 语言 + 4 个 `string-array`。承接上一段的判断：`OtherConfigRouteScreen`（146）
与 `DirectLinkUploadBottomSheet`（215）**留在 `:app`** —— 这是「页面本体归共享层、平台壳留宿主」
的既定拆分。

#### 数组迁移的四个坑（都不是搬运）

1. **`stringArrayResource` 在 CMP 返回 `List<String>`**（androidx 返回 `Array<String>`）⇒
   每个调用点要多一次 `.toTypedArray()`。同模块 `CustomThemeScreen` 已是此写法。
2. **显示数组逐语言本地化，`*_value` 数组只放默认 `values/`** —— 本模块既有约定
   （`paletteStyle`/`paletteStyle_value`）。资源系统按**整体**回落到 `values/`，故配对不受影响。
   真正的不变量是「**各语言显示数组长度 == 默认值数组长度**」——不相等会让下拉框
   **静默选错值**（编译器与 lint 都不报）。已用脚本逐语言核对（4/4 全 OK）。
3. **`default_app_variant` 的条目是 `@string/*` 间接引用**，Android 会**逐项、逐语言**解析，
   而 CMP 数组表达不了这点 ⇒ 迁移时展开成字面量。展开会**把当前的回落行为固化**：
   第 3 项 `@string/all_version` 只在 `values/` 与 `values-zh-rCN/` 定义 ⇒ zh-rHK/zh-rTW
   **真的显示英文 `All Version`**。逐字保留，并把原因写进 arrays.xml 的 XML 注释
   （改它属于产品文案决策，不是迁移决策）。
4. **同名可以既是 string 又是 array**（`language` 两者都有）。生成的访问器是
   `internal val Res.string.language` / `Res.array.language`（同包），实测**一条不加别名的
   `import …res.language` 能同时引入两者**，无需别名。

⚠️ `verify-compose-resources.py` **只比对 `strings.xml`**，数组不在覆盖内 ⇒ 数组要手工/脚本核，
并在片报里说明（本片用脚本核了条目内容与配对长度）。

#### 一处保真取舍：结构逐字保留

迁移后的 Screen **保留了原文那两处缩进瑕疵**（`SplicedColumnGroup {` 后实体少缩进一层）。
理由：这样 `git mv` 出来的 diff 只反映语义改动（package / `Res` / `.toTypedArray()`），
reviewer 不必在格式噪音里找差异。已在文件头 KDoc 写明。

#### 验证

- 四门禁全绿（**G4 无需基线变动**）
- `:app` 编译/单测/打包 + 全模块测试；计数 **751 / 1246 零偏离**（本片无测试增减 ——
  `OtherConfigScreen` 是**无 VM 的纯组合函数**，与 M5-6b 的 `ConfigNavScreen` 同一判据：
  本模块没有 Compose UI 测试基建，不为它引入新测试依赖）
- 资源 **464/464 逐字一致**；死资源 2 条删除（`auto_check_update_on_start_title` / `_summary`
  —— 其余 50 条在 `:app` 别处仍被引用）
- `lintAppDebug` 仍 **5 errors / 95 warnings**

#### 未验证

页面渲染与 4 个下拉的**实际取值**（尤其 zh-rHK/zh-rTW 下 `default_app_variant` 显示
`All Version` 这一既有行为），以及权限申请、SAF 选目录、WebService 重启、清 WebView 后重启
四条宿主路径。需真机冒烟。

**`ui/config/otherConfig` 至此只剩 2 个宿主壳文件**，两者都按「平台壳留 `:app`」的判据保留。

**下一步**：`backupConfig`（1144 行，需抽 `Permissions` / `ImportOldData`）、
`themeManage`（1138 行，需 `SavedTheme` / `ThemePackageManager`）、或
`coverConfig`（1534 行，但**需先下沉整条相册存储链** —— `CoverAlbumGateway`/`Repository`/
`UseCase` 全在 `:app`，且 `CoverAlbumImageInput` 的 `java.io.InputStream` 要重新设计，
是比前几片大的一档）。

### M5-9a：`backupConfig` 的**逻辑层** → `:feature:settings/backup/`

沿用 otherConfig 的按层分片（M5-8a/8b）。**勘察阶段的最大发现**：`backupConfig` 其实
**已经有干净的宿主壳** —— `BackupConfigScreen.kt` 一个文件里同时放着 `BackupConfigRouteScreen`
（4 个 launcher + `PermissionsCompat` + `ImportOldData`，即平台壳）与页面本体，
两者职责已经分开。这使它的形态比 `themeManage` / `coverConfig` 干净得多。

#### 三处平台耦合 → 两个新契约 + 两处内联

| 迁移前 | 迁移后 |
|---|---|
| `BackupConfigDialog.Loading(@StringRes titleRes)` + `BackupConfigEffect.ShowMessage(@StringRes messageRes, argument)` + VM 里 11 处 `R.string.*` | `BackupConfigText` 枚举（11 值）+ `BackupConfigText.kt` 里**一张映射表派生两个适配器**（`@Composable localized()` 给对话框标题、`suspend localizedText(argument)` 给宿主收 Effect） |
| `io.legado.app.help.storage.BackupConfig` 的**四组**「忽略集」（4 × keys/titles/`HashMap`/`save`） | `BackupIgnoreStore` 契约 + `BackupIgnoreKind` 四值枚举（语义已逐条核过 `BackupConfig.kt`：恢复/备份 × 配置项/数据库表） |
| `Uri.parse(uri).toString()`（恢复本地备份） | 直接用 `uri` —— 宿主本来就传 `uri.toString()`，而 `Uri.parse(s).toString() == s` |
| `String?.isContentScheme()`（`utils/StringExtensions.kt`，实现即 `startsWith("content://")`） | 内联 + 注明出处（纯单行谓词，搬一行比多开一个契约划算） |

⚠️ **4 个 `saveXxx` 刻意不合并**：它们**并不对称** —— `saveIgnoreItems` /
`saveBackupIgnoreItems` 各写**两组**并**关弹层**，而 `saveDbIgnoreItems` /
`saveBackupDbIgnoreItems` 只写**一组**且**不关弹层**。合并会静默改掉弹层关闭时机，
已写成测试断言钉住。
（4 个同形 loader 则收敛成一个 kind 参数化版本 —— 契约本身就是 kind 化的，不收敛反而怪。）

#### ⚠️ 坑一：按行删文案会破坏 `strings.xml`

`restore_fail_with_error` 的值里有**字面换行**（`復原失敗` 与 `%1$s</string>` 分两行），
按行删只删掉首行、留下孤立的 `%1$s</string>` ⇒ XML 破。**Kotlin 编译照样过**，
要到 `:app:mergeAppDebugResources` 才报「元素类型 resources 必须由匹配的结束标记终止」。
⇒ 改为**按元素删**（DOTALL 正则），并且**删完必须逐文件 `ET.parse` 校验** —— 前几片都做了这步，
本片漏做，代价是完整验证跑了两分钟才暴露。已写进 checklist。

#### ⚠️ 坑二：弃用壳会「藏住」棘轮该看的耦合

`ui/config/backupConfig` 里有个**同名零引用**的弃用壳 `object BackupConfig`，为避开它，
VM 调真身时只能写**全限定名** `io.legado.app.help.storage.BackupConfig.ignoreConfig[...]`。
而门禁规则是 **import 锚定**的 ⇒ 那些耦合**一处都没被计入**（`legacy-architecture-report` §7 提到过
「全限定写法不在统计内」，这是它第一个真实受害者）。

本片把耦合换成契约后，实现侧写的是朴素 import ⇒ `legacyHelp|…/platform` **5 → 6**。
**净债务没变，是账本变准了**，已按此在基线与报告里注释；反过来若为压住这个数字改回 FQN，
就是刻意利用盲点掩盖耦合（M5-7 已立判据）。

顺带：那个弃用壳确认**全仓零引用**（连 `import` 与同包裸用都查过）⇒ 删除（20 行）。

#### 新增 10 条用例（迁移前零测试）

四组的 kind **配对**（keys/titles/checked 不串组）/ `saveIgnoreItems` 写两组 + 关弹层 /
`saveDbIgnoreItems` 只写一组 + **不关弹层** / `saveBackupIgnoreItems` 写两组 + 关弹层 /
`saveBackupDbIgnoreItems` 只写一组 + **不关** / 勾选只改内存不碰 store /
普通路径发 `RequestStoragePermission` 且此时**不该**有 Loading 对话框 /
`content://` 路径**跳过**权限直接进 `Loading(BackingUp)` /
`RequestLocalRestore` 清弹层并发 picker / `TestWebDav` 与 `RequestNetworkRestore` 各进对应 Loading。

⚠️ **只钉同步可观测的行为**：VM 里 `launch(Dispatchers.IO)` 之后再 `withContext(Main)` 的尾巴
（`performBackup` / `restoreLocal` / `testWebDav` 的结果与终态）在 Robolectric 下要跨真实线程池
+ 主 looper 才看得到，硬测会变成靠 `idle()` 轮询的脆弱写法 ⇒ 结果分支留给真机冒烟。

#### 验证

- 四门禁全绿（G4 按上述那一处「账本变准」上调 `platform` 的 `legacyHelp` 5 → 6）
- `:feature:settings` 的 desktop 编译与 `testAndroidHostTest`（**56 例 0 失败**）
+ `:app` 编译/单测/打包 + 全模块测试
- 计数 **751 → 761 / 1246 → 1256**（+10），零失败
- 资源 **472/472 逐字一致**（500 − 7 条 ×4 语言退役）；死资源 7 条删除
- `lintAppDebug` **5 errors / 95 warnings**（filtered 245 → 244、23 → 21）

#### 未验证

页面渲染、4 个 launcher 与 `PermissionsCompat` 的实际申请流程、`ImportOldData` 导入旧数据，
以及 `performBackup` / `restoreLocal` / `testWebDav` 三条 IO 尾巴的**结果分支**。需真机冒烟。

**`ui/config/backupConfig` 至此剩 2 个 `:app` 文件**（`BackupConfigScreen.kt` 的
RouteScreen + 页面本体、`BackupRestoreOptionSheets.kt`）—— 下一片迁页面本体时**要先把这个
文件拆成宿主壳与页面两部分**。

**下一步**：`backupConfig` 页面本体（先拆文件）、`themeManage`（需 `SavedTheme` /
`ThemePackageManager` 契约，但 `SavedTheme` 携带 `ThemePackageManifest` ⇒ 与 `coverConfig`
撞同一条存储链）、或 `readConfig` / `themeConfig`（后者最重）。

### M5-9b-pre：`CardTabRow` 上提到 `:core:designsystem/commonMain`

**纯搬运，包名不变**（`io.legado.app.ui.widget.components.tabRow`）⇒ 10 处 `:app` 消费方
import 零改动；`:core:ui` 本就 `implementation(project(":core:designsystem"))`，故删掉源文件
对它也是透明的。

这不是临时起意：`AppTabRow.kt` 的 KDoc 当初就写着「同目录的 `CardTabRow.kt` 刻意**没有**
跟着搬：它目前只有 `:app` 的 10 处消费方，没有非 Android 消费者。**等真出现时按同一配方再搬**」。
M5-9b 让那个前提出现了（`backupConfig` 的 `IgnoreItemsSheet` 用 `CardTabRow` 做两个页签），
于是按预告的配方搬，并同步更新那句 KDoc 为「已兑现」。

该文件零 Android 依赖（只用 Compose foundation/runtime + designsystem 的
`LegadoTheme` / `NormalCard` / `AppText`）⇒ 零源码改动、零新增依赖。

### M5-9b：`backupConfig` 页面本体 + 两个选项 sheet → `:feature:settings/backup/`

承接 M5-9a。**勘察阶段的关键发现**：`:app` 的 `BackupConfigScreen.kt` 一个文件里同时放着
**宿主壳**（80–150 行）与 **5 个 UI composable**（152–末行），而且两者边界干净 ——
18 处平台用法**全部**落在宿主壳那 70 行内，本体只依赖 `R` / 数组 / designsystem 组件。
⇒ 按职责拆开：壳留 `:app`（顺手改名 `BackupConfigRouteScreen.kt` 与函数同名，
与其余宿主壳命名一致），本体搬进共享层。

| 迁移前 | 迁移后 |
|---|---|
| `BackupConfigScreen.kt` 里的 5 个 composable（`BackupConfigScreen` / `BackupConfigSheets` / `IgnoreItemsSheet` / `BackupConfigDialogs` / `ConfirmDialog`） | `:feature:settings/backup/BackupConfigScreen.kt` |
| `BackupRestoreOptionSheets.kt`（`BackupOptionSheet` / `RestoreOptionSheet`） | `:feature:settings/backup/BackupRestoreOptionSheets.kt` |

**`ConfirmDialog` 的可见性刻意没动**：它迁移前是 `public` 而只被同文件用 → 本片**不**顺手改成
`private`。可见性属 API 表面，改它不该夹在一次搬迁里（即便当前看起来无害）；真要收紧应由
专门的清理切片做。已在 KDoc 写明。

#### ⚠️ 一个跨文件消费方：`HomeScreen` 也用这两个 sheet

`BackupOptionSheet` / `RestoreOptionSheet` 除了 `backupConfig` 自己，还被
`:app` 的 `HomeScreen`（备份/恢复入口）使用 —— 我事先只查了这两个 composable 自身声明与
文案，漏了这个消费方，是编译那一步才暴露的（`Unresolved reference 'BackupOptionSheet'`）。
⇒ 迁一个「看似只在页面内用」的 composable 前，**按符号名全仓搜使用者**，别只看本目录。
（与 M5-8a「VM 测试漏查源模块」是同一类疏漏，已并入 checklist 那条。）

#### 数组：`backup_sync_mode` 与 M5-8b 的 `default_app_variant` 同一形态

`:app` 里它只在默认 `values/` 定义、条目是 `@string/*` **间接引用**（Android 逐项按语言解析），
而共享层的数组约定是纯字面量 ⇒ 展开成解析后的结果、**4 个语言都写**；
机器值数组 `backup_sync_mode_value` 只放默认 `values/`。细节在 arrays.xml 的 XML 注释里。

#### 死资源 12 条，但 29 条**看着能删其实不能**

扫描时 41 条里有 29 条显示「仍在用」，其中最意外的是
**`res/xml/pref_config_backup.xml`** —— 一个遗留的 Android `PreferenceScreen` 定义，
仍然引用着 `auto_check_new_backup_s` / `sub_dir` / `web_dav_url` / `restore_ignore` /
`backup_path` / `sync_book_progress_t` 等。另有 `OnboardingScreen`（`web_dav_url`）与
`ClickActionConfigSheet`（`sync_book_progress_t`）。
⇒ **删文案前必须全仓（含 `res/xml/*.xml`）核引用**，只查 Kotlin 会误删。

#### ⚠️ 顺手修掉 M5-9a 留下的一例**竞态测试**

本片跑完整验证时，M5-9a 新增的 `恢复网络备份与测试连接都先进对应对话框` **挂了** ——
而它在 M5-9a 当时的全量跑里是**通过**的：

```
expected:<Loading(title=Loading)> but was:<null>
```

根因：`testWebDav` / `loadNetworkBackups` 都是「**先同步**设 `Loading` 对话框、**再**
`launch(Dispatchers.IO)`」，而 IO 尾巴会 `withContext(Main)` 把 `activeDialog` 清成 `null`。
那两处断言前面各插了一次 `idle()` ⇒ 放行了前一个操作的尾巴 ⇒ 断言变成看运气。

修法：**断言放在任何 `idle()` 之前**（只钉同步那一半），并把那一例拆成两例
（「测试连接先进测试中对话框」+「请求网络恢复先进通用加载对话框并关弹层」），
保证一例内不会有两个 IO 操作的尾巴互相穿。然后用 `--rerun-tasks` **连跑 3 次**确认稳定
（一次绿说明不了竞态）。教训写进 checklist：这类竞态最恶劣之处是**首次绿**恰好把它藏住了。

#### 验证

- 四门禁全绿（**G4 无需基线变动** —— 宿主壳留在 `:app`，它的 `ImportOldData` /
  `Permissions` / `isContentScheme` 引用原地不动）
- `:core:designsystem:compileKotlinDesktop` + `:core:ui` + `:app` 编译 + 全模块测试
- `:feature:settings` **57 例 0 失败**（拆分竞态用例后 +1）
- 计数 **761 → 762 / 1256 → 1257**，零偏离
- 资源 **634/634 逐字一致**；死资源 12 条删除
- `lintAppDebug` 仍 **5 errors / 95 warnings**

#### 未验证

页面的渲染与 6 个 sheet/dialog 的交互（两处 `FilePickerSheet`、两个 `OptionSheet`、
恢复文件列表、两个忽略项页签、WebDAV 认证与密码可见性、两个 Loading 对话框），
以及宿主壳那 4 个 launcher 与 `PermissionsCompat` 的**真实申请流程**。需真机冒烟。

**`ui/config/backupConfig` 至此只剩 1 个文件**（`BackupConfigRouteScreen.kt` —— 宿主壳，
按「平台壳留 `:app`」的判据保留）。

**下一步**：`readConfig`（7 文件 / 1149 行，但被 `ui/book/*` 的
`ClickActionConfigSheet` / `EyeProtectionConfigSheet` 挡住 —— 要么先迁那两个 sheet，
要么它们随宿主壳留在 `:app`）、`themeManage`（与 `coverConfig` 撞同一条存储链）、
或 `themeConfig`（11 文件 / 3388 行，撞 `ui.main.*` 的 10 个 `Launcher*` 图标，最重）。

### M5-10a-pre：`TimePickerDialog` 上提到 `:core:designsystem/commonMain`

与 M5-9b-pre 搬 `CardTabRow` 同一配方、同一触发条件（**共享层出现第一个消费者**：
`EyeProtectionConfigSheet` 迁进 `:feature:settings` 后需要它）。包名不变 ⇒ 两处消费方
（`ThemeConfigScreen`（`:app`）与本片迁入的 sheet）import 零改动。

⚠️ **但它不是零改动搬运** —— `CardTabRow` 是，这个不是。三处 JVM/Android 专用写法必须改写：

| 迁移前 | 迁移后 | 依据 |
|---|---|---|
| `String.format(Locale.ROOT, "%02d:%02d", h, m)` | `padStart(2, '0')` 拼接 | `Locale.ROOT` 的用意就是**恒定输出 ASCII 数字**（否则阿拉伯语地区出 `٢٢:٠٧`）；`Int.toString()` 本就恒为 ASCII ⇒ 语义一致 |
| `Character.digit(char, 10)` | `Char.digitToIntOrNull(10)` | 原实现**刻意**接受非拉丁数字（见下） |
| `R.string.ok / cancel` | designsystem 自己的 `Res` | 该模块四语言都已有这两条，无需新增文案 |

**这两条改写有既存单测兜底**：`:core:ui` 里原有一份 `TimePickerDialogTest`，它用
`java.util.Locale.setDefault("ar")` 包住断言，钉的正是「`formatTimeValue` 恒输出 ASCII」，
并且断言 `parseTimeNumber("٢٢") == 22`（**接受阿拉伯-印度数字**）。该测试随实现迁到本模块
`commonTest`（`java.util` 进不去，`setDefault` 那层包装随之去掉 —— 新实现按构造就 locale-free，
那条性质恒真，但输出形状仍被断言）。

实测结论：`digitToIntOrNull(10)` 在 JVM 上委托 `Character.digit`，**非拉丁数字语义保住了**
（3 例全绿）。⚠️ 其它平台（native/JS）的数字表可能只认 ASCII —— 见下方未验证。

**计数 +3 不是新增覆盖**：那份测试原在 `:core:ui/src/test`（JVM 专用源集），**不在
`tools/count-test-results.py` 的模块清单里**、从未被计入；迁到 designsystem 的 `commonTest`
后进了分母 ⇒ 762 → **765**。它此前并非没跑 —— CI 的 `verify.yml` 明确执行
`:core:ui:testDebugUnitTest`。**是可见性增加，不是用例增加**，基线注释里写明了。

### M5-10a：`EyeProtectionConfigSheet` → `:feature:settings/readconfig/`

`readConfig` 页的前置件之一。**唯一改动**是资源访问（`R.string.*` → `Res.string.*`，11 条，
无数组），结构逐字保留。

落位时的一个判断：它原住 `ui/book/read/sheet/`（阅读菜单那侧），但**本页与阅读菜单共用同一份**，
且它的全部字段都落在 `ThemeSettings` 上 ⇒ 既不是阅读器私有、也不是设置页私有。本片放
`readconfig/`（与将迁来的 `ReadConfigScreen` 同包），阅读器侧（`:app` 的 `ReadBookScreen`）
改为 import 本文件。⚠️ 这是**临时归属**，将来若有 `:feature:reader` 应重新划分。

死资源 2 条（`eye_protection_intensity` / `_summary`）；其余 9 条在 `:app` 侧仍被
`ThemeConfigScreen` / `GlobalThemePage` / `MoreConfigSheet` 引用。

⚠️ **一处工具口径存疑**：`verify-compose-resources.py` 这次报 622/622，
但按「feature 与 `:app` 同名条目」应有 631 对（新增 9 条非死文案在两个源文件里都存在，
且中间产物 `strings.commonMain.cvr` 实测已含 `eye_protection_start_time`）。
**没查出原因**（该脚本的分母逻辑见其 §③）。为了不留验证缺口，改用**直接按 key 逐字 diff**
（11 条 × 4 语言）作为本片的资源断言 —— 结论一致 ✅，且能明确区分「值不一致」与
「`:app` 侧已删」。工具本身的这个分母差异待独立排查。

#### ⚠️ 战略结论：`readConfig` 不是一个普通切片，而是四个移植问题的集合

勘察后才看清，`readConfig` 页本体（471 行）引用四个 `:app` 专属符号，**每个都需一个策略决定**：

| 符号 | 阻塞点 |
|---|---|
| `ClickActionConfigSheet`(230) | ① 用 `androidx.activity.compose.BackHandler`（**共享层零先例**）；② 自己 `koinInject()` 仓储，而 `:feature:settings` **刻意没有 koin 依赖**（build 文件注明「没有任何调用方」）⇒ 要么加 koin、要么改成参数注入（改签名，两个消费方） |
| `PageKeySheet`(121) | 用 `android.view.KeyEvent` 的 `nativeKeyEvent.keyCode` ⇒ 需 CMP 化的按键映射 |
| `EyeProtectionConfigSheet`(133) | ✅ 本片已解决（上提 `TimePickerDialog` 即可） |
| `CanvasRecorderFactory`(31) | 用 `android.os.Build` + 具体 `CanvasRecorder*Impl` ⇒ 需窄契约 |

另有 `ApplyReadSettingUseCase`(62) 依赖 `EventBus` / `ReadConfigUpdateBus` 待核。

⇒ 结论：**`readConfig` 应拆成多片、且前两片的性质是「把阅读器栈跨端化」而不是「迁设置页」**。
而 `ui/config` 其余尾巴同样重（`themeConfig` 3388 行撞 10 个 `Launcher*` 图标；
`themeManage` / `coverConfig` 撞 `ThemePackageManager`(1254 行，深依赖 `Context`/`Uri`/
`AppCompatDelegate`) 与 `BookCover`(`Bitmap`/`Drawable`) 那条存储链）。

**建议**：`ui/config` 的剩余项已进入「每片都要先做跨端化或抽重契约」的区间，
下一轮宜先与使用者确认优先级（是继续啃这块，还是转去别的域），而不是默认继续按文件数挑最小的。

#### 验证

- 四门禁全绿（**G4 无需基线变动**：两个上提都不改变 help/命名耦合的归属）
- `:core:designsystem` 的 desktop/android 编译与 `testAndroidHostTest`（**42 例 0 失败**，
  含迁入的 3 例）+ `:core:ui` 编译 + `:feature:settings` 编译 + `:app` 编译/单测/打包
- 全模块测试通过；计数 **762 → 765 / 1257 → 1260**（+3 = 可见性，见上）
- 资源：11 条 × 4 语言**直接逐字 diff 一致** ✅；designsystem **216/216** 一致 ✅
- 死资源 2 条删除（`:app` 四个文件均可解析）
- `lintAppDebug` 仍 **5 errors / 94 warnings**

#### 未验证

- 时间选择对话框的渲染与交互（`TimePickerDialog` 上提后**同一份代码**在两端跑，但本片只编译+单测，
  没跑 UI）。
- ⚠️ `parseTimeNumber` 的非拉丁数字语义**仅在 JVM 上验证**（`commonTest` 跑在
  `testAndroidHostTest`）。若将来编 native/JS，需重跑这份测试确认数字表差异。
- 护眼 sheet 在两个入口（阅读设置页 / 阅读菜单）的渲染与两个时间选择器的实际交互。需真机冒烟。

**下一步**：见上方「战略结论」—— 建议先定优先级。

### M5-11a：`readConfig` 逻辑层 → `:feature:settings/readconfig/`

按 M5-8a/M5-9a 的节奏（**先逻辑层、后页面**）：`ReadConfigContract`(118) + `ReadConfigViewModel`(225)
+ `ApplyReadSettingUseCase`(62) 迁进共享层。VM 与契约**逐字**保留 —— 它们的依赖本来就全在共享层
（两个 gateway + `ReadSettings` / `ThemeSettings`）。真正要处理的是另外两件。

#### 一、`EyeProtectionUiState` 必须上提（读者与设置页共用）

它是 `@Stable` data class，原声明在 `:app` 的 `ReadBookContract.kt` 里（与阅读器契约同文件），
被**阅读器**（`ReadBookContract` / `ReadStyleDelegate`）与**设置页**（`ReadConfigContract` / VM）共用
⇒ 必须落在双方都能看见的地方。本片放进设置页契约（与 `ReadConfigUiState` 同文件），阅读器侧改
import —— 与 M5-9b 里 `:app` 的 `HomeScreen` 改 import 特征模块的 `BackupOptionSheet` 是同一处境。

⚠️ **临时归属**（KDoc 已注明）：护眼本质属于「外观」，阅读菜单只是入口之一；将来若有
`:feature:reader`，这类「读者与设置共用」的 UI 状态应重新划分。

#### 二、⚠️ 刻意**没有**把 `ConfigUpdateAction` 搬进共享层

`ApplyReadSettingUseCase` 迁移前直接调四个 `:app` 符号，其中 `ConfigUpdateAction` 是
`ui.book.read` 里的 `sealed interface`，被 **10 个 `:app` 文件**使用（`ReadBookViewModel` /
`ReadConfigUpdateDelegate` / `ReadStyleDelegate` / `ReadBookController` / `ThemeConfigStore` …）。

它是**阅读器**的类型：阅读器**收集并分发**这些动作，设置页只是**投递方**。搬进共享层会让共享层
反向依赖阅读器的概念 —— 与既有判据「契约发 Effect、宿主执行平台动作」相反。

⇒ 收成窄契约 `ReadConfigApplyPlatform`（**7 个方法，与迁移前的调用一一对应**），
Android 实现 `AndroidReadConfigApplyPlatform` 留在 `:app/platform/`，由它去拼具体的
`ConfigUpdateAction` 集合 / 调 `ReadBook`。与 M5-7 的 `DownloadCachePlatform` 同一形态。

```kotlin
updateSystemUiAndStyle()  // ReadConfigUpdateBus.post({UpdateSystemUi, UpdateStyle})
updateActionBar()         // postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
reloadContent()           // ReadBook.loadContent(false)
updateSeekBar()           // postEvent(EventBus.UP_SEEK_BAR, true)
updatePageSlopSquare()    // ReadConfigUpdateBus.post({UpdatePageSlopSquare})
updatePageAnim(animate)   // renderCallBack?.upPageAnim(animate)
invalidateTextPage()      // ReadConfigUpdateBus.post({InvalidateTextPage})
```

两处需要留意的等价改写：

- `upPageAnim()` 的迁移前是**无参调用**，而 `upPageAnim(upRecorder: Boolean = false)` ⇒
  契约显式传 `false`（`NoAnimScrollPageChanged`），`OptimizeRenderChanged` 传 `true`。
- `OptimizeRenderChanged` 迁移前是 `upPageAnim(true)` **然后** `loadContent(false)` 两步 ⇒
  契约保留这个顺序，并由用例断言顺序。

#### ⚠️ 一处 blanket 替换的误伤（当场发现并回退）

我把 `:app` 里 `import io.legado.app.ui.config.readConfig.*` 批量改成 feature 包时，误伤了两个
服务文件与 `MainNavGraph` —— 它们 import 的是 **`ReadConfig`（弃用门面）与 `ReadConfigRouteScreen`
（宿主壳）**，这两个**仍留在 `:app`**。编译前靠人工核对发现并回退。
⇒ 教训：批量改 import 前，先分清「哪些符号迁走了、哪些留下」。

另有一处编译才暴露的遗漏：`app/src/test/.../EyeProtectionTest.kt` 与**同包**的
`readConfig` 三个文件（`ReadConfigRouteScreen` / `ReadConfigScreen` / `PageKeySheet`）此前靠
「同包」直接引用契约类型、没有 import 行 ⇒ blanket 替换覆盖不到它们（补 import 解决）。

#### 新增 9 条用例（迁移前**零测试**）

`ApplyReadSettingUseCaseTest`：状态栏/导航栏 → 刷系统UI与样式；菜单四项 → 只刷操作栏；
排版四项 → 重新排版；进度条 → 只刷进度条；热区 → 只刷热区；下划线 → 文本页失效；
关动画 → `updatePageAnim(false)`；渲染优化 → **`pageAnim:true` 然后 `reloadContent`（顺序）**；
不在映射表里的改动（含护眼）→ **什么都不调**。

⚠️ 断言的是**调了平台的哪个方法**（假实现只记方法名），而不是「有没有副作用」——
接错线会被抓住。这张表不是可以顺手整理的地方：改错的表现是「改了设置、当前页没反应」
或「无谓地重排整本书」，评审里看不出来。

#### 验证

- 四门禁全绿（**G4 无需 baseline 变动**：`AndroidReadConfigApplyPlatform` 只 import
  `ReadBook` / `EventBus` / `ReadConfigUpdateBus` / `ConfigUpdateAction` / `postEvent`，
  不含 `*Utils` / `*Help` 类型名 ⇒ 不触 `legacyNaming`；也不含 `help.*` ⇒ 不触 `legacyHelp`）
- `:feature:settings` 编译 + 测试（**66 例 0 失败**）+ `:app` 编译/单测/打包 + 全模块测试
- 计数 **765 → 774 / 1260 → 1269**（+9），零偏离
- `lintAppDebug` 仍 **5 errors / 94 warnings**

#### 未验证

- ⚠️ `AndroidReadConfigApplyPlatform` 的七个方法**没有被任何测试执行** —— 它们只在真机
  上跑（需要正在运行的阅读器）。本片只保证「共享层调对了方法名」，**方法体里调的
  `ReadBook` / `ReadConfigUpdateBus` 是否还是原来那些动作**靠人工逐条对照（已写在
  KDoc 的注释里），没有自动化保证。
- 页面本身未迁（见 M5-10a 的战略结论）。

**下一步**：`readConfig` 页面本体 —— 前置是 `ClickActionConfigSheet`（`BackHandler` +
`koinInject` 两个策略决定）、`PageKeySheet`（`android.view.KeyEvent`）、
`CanvasRecorderFactory`（`android.os.Build`）。

### M5-11b：确立两条共享层跨端策略 + `PageKeySheet` 迁进 `:feature:settings/readconfig/`

上一轮说清了 `readConfig` 页面被三个移植问题挡住，其中 **`BackHandler` 与 `KeyEvent` 两条会
在阅读器栈反复用到** —— 所以本片先把这两条**策略**定下来，并顺带把用到 `KeyEvent` 的
`PageKeySheet`(121 行) 迁进去作为落地验证。

#### 策略一：按键事件 ✅ `event.type` + `event.key.nativeKeyCode`（已验证可用）

| 迁移前（Android 专用） | 迁移后（跨端） |
|---|---|
| `event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN` | `event.type == KeyEventType.KeyDown` |
| `android.view.KeyEvent.KEYCODE_BACK / KEYCODE_DEL` | 本文件两个局部常量（`4` / `67`） |

⚠️ **为什么是 `nativeKeyCode` 而不是 CMP 的 `Key.*` 语义常量**：这个设置存的是**数字 keyCode
的逗号分隔串**（如 `"21,22"`），阅读器按数字匹配 ⇒ 必须拿到数字，换成语义 Key 等于改存储格式
与匹配逻辑。`nativeKeyCode` 在 Android 上就是 `android.view.KeyEvent` 的 keyCode ✓
（桌面/其它端是各自编码 —— 本设置本质是手机翻页键配置，只在 Android 有意义，与迁移前一致）。

⚠️ 踩到一点：`key` 是**扩展属性**，必须 `import androidx.compose.ui.input.key.key`
（和 `type` 一样），否则报 `Unresolved reference 'key'`。已编译验证（desktop 目标）。

#### 策略二：返回键 ⚠️ **共享层用不了 `BackHandler`** ⇒ 由宿主按共享 state 接线

CMP 常见做法是 `androidx.compose.ui.backhandler.BackHandler`。本片**实测不可用**：

1. 在 designsystem 的 `commonMain` 写探针引用它 → `Unresolved reference 'backhandler'`；
2. 进而把 Gradle 缓存里所有 compose 的 jar 遍历一遍找 `backhandler` 条目 → **0 命中**。

⇒ 本项目的依赖集里根本没有这个类（不是版本差异，是坐标/产物里没有）。**策略定为**：
共享 composable **不**自带 `BackHandler`，只暴露 `onDismissRequest`；由**宿主**按共享 state
接线（宿主看得到 `viewModel.uiState`）：

```kotlin
// :app 的宿主壳
BackHandler(enabled = state.activeSheet == ReadConfigSheet.ClickActions) {
    viewModel.onIntent(ReadConfigIntent.DismissSheet)
}
```

这与既有判据「契约发 Effect、宿主执行平台动作」一致，且不需要为共享层引入新依赖
（也符合「不为架构完整留空配置」—— 探针确认完即删，不留空组件）。

#### `PageKeySheet` 迁移

5 条文案（`custom_page_key` / `prev_page_key` / `next_page_key` / `page_key_set_help` / `reset`），
**无死资源** —— 五条在 `:app` 侧全部仍被引用（其中一个引用方是 **`res/xml/pref_config_read.xml`**，
又是那个遗留 PreferenceScreen）。

⚠️ **顺带发现一处重复**：`:app` 里还有一个 `ui/book/read/sheet/PageKeyConfigSheet.kt`，
与本页面做的事情几乎一样（同样用 `prev_page_key` / `next_page_key` / `page_key_set_help`）——
和护眼 sheet 一样是「阅读菜单与设置页各一份」。将来迁阅读器栈时要一并处理
（要么共用一份，要么明确各自归属）。

#### 验证

- 四门禁全绿（**G4 无需 baseline 变动**）+ designsystem / `:feature:settings` desktop 编译
  + `:app` 编译/单测/打包 + 全模块测试
- 计数 **774 / 1269 零偏离**（本片纯 UI 迁移、无新增用例 —— 依 checklist「无 VM 的纯
  composable 不加测试」那条判据）
- lint **5 errors / 94 warnings**

#### 未验证

`PageKeySheet` 的按键捕获在**真机**上的行为（共享层写法只在 desktop 编译通过；
`nativeKeyCode` 的实际取值需要在 Android 上按一次键才能确认等于 `android.view.KeyEvent` 的
keyCode）。这是本片最需要冒烟的一点 —— 若不等，翻页键配置会静默失效。

**下一步**：`ClickActionConfigSheet`（`BackHandler` 策略已定 ⇒ 宿主接线即可；
剩 `koinInject` → 改成参数注入的决定），之后 `CanvasRecorderFactory`（窄契约），
再之后才是 `readConfig` 页面本体。
