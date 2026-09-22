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

**下一步**：`ai` 域的剩下两页 —— `AiModelEdit`（239/167/48，用 `GSON` 反序列化
`AiGenerationParams`，可换共享层的 `JsonCodec`）与 `AiProviderEdit`（384/352/98，另用
`appCtx.getString` 3 处发测试连接的提示）。

**下一步**：`ai` 主域（9 文件，VM 用 `GSON` + `appCtx`，最重）或转去
`otherConfig` / `backupConfig`（需先抽 `WebService` / `ImportOldData` 胶水）。
