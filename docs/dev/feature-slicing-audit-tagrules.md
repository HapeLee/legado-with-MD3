# `tagrules` 依赖审计（M1-1）与 M1-3 进度

为 M1-3「消除 `tagrules` 的 Provider/base 依赖并转 CMP」做前置：先把调用闭包和平台能力点列全，
再给 import/export/reducer 立 characterization 测试。本文只做审计与测试，**不改生产行为**。

## 1. 结论摘要

`:feature:tagrules` 共 7 个文件 1363 行（Stage B 已完成，包名 `io.legado.app.feature.tagrules.*`）。
真正阻塞 CMP 的只有五类，且**都在边界上**：

| 阻塞项 | 出现位置 | 处置（M1-3） | 状态 |
| --- | --- | --- | --- |
| `android.app.Application` | 两个 ViewModel 构造（经 `BaseRuleViewModel`） | VM 不再持有 Application；需要的 `context.getString` 改为契约 | **已完成**（M1-3b） |
| core Provider 委托 | `ClipboardProvider` 2、`ToasterProvider` 4（仅 group 侧） | 改为注入的 clipboard/toast 契约（M2 删 Provider 的前哨） | **已完成**（M1-3a） |
| Android 资源 `R.string.*` | 26 个键，VM 与 Screen 都用 | CMP 资源（`Res`）或由宿主传文案 | **VM 侧已完成**（M1-3b，VM 零 `R`）；Screen 侧待办 |
| 文件选择/导出（SAF） | `rememberLauncherForActivityResult` ×2、`FilePickerSheet` ×2、`android.net.Uri` | 抽「选导入源 / 选导出目标」能力契约 | **已完成**（M1-3c：`:core:platform` 的 `DocumentPicker` + `:core:ui` 的 `rememberDocumentPicker()`） |
| `GSON` 门面 | 两个 VM 的 `generateJson`/`parseImportRules`/`copyRule`/`pasteRule` | 换成已有 JSON 编解码契约 | **已完成**（M1-3a） |

数据与列表编排（`:core:data` Repository）**不需要先动**：它已在模块内，属于 data 类型依赖，转 CMP 时
可随模块一起进 `commonMain`。原来承载编排的 `BaseRuleViewModel` 已被 `RuleTransferUseCase` 取代
（M1-3b），两个 VM 现在是普通 `androidx.lifecycle.ViewModel`。

M1-3a 之后，两个规则 VM 的 `main` 源集里 `gson` 与 `coreProvider` 两类 legacy 计数都已归零
（基线里对应 4 条已删除）。

## 2. 文件清单

| 文件 | 行 | 职责 | 平台点 |
| --- | --- | --- | --- |
| `group/TagGroupRuleContract.kt` | 64 | UiState/Intent/Effect | — |
| `group/TagGroupRuleEditSheet.kt` | 151 | 新增/编辑弹层 | `R.string`、`LocalContext` |
| `group/TagGroupRuleViewModel.kt` | 234 | 列表/选择/排序/导入导出/同步 | Application、`R`、Gateway + **注入的 `Clipboard`/`Toaster`** |
| `highlight/HighlightTagRuleContract.kt` | 68 | UiState/Intent/Effect | — |
| `highlight/HighlightTagRuleEditSheet.kt` | 177 | 新增/编辑弹层 | `R.string` |
| `highlight/HighlightTagRuleScreen.kt` | 368 | 主页面（含导入/导出入口） | FilePicker、`LocalClipboard`、`R.string` |
| `highlight/HighlightTagRuleViewModel.kt` | 224 | 同 group，无同步 | Application、**注入的 `Clipboard`** |

注：**group 侧没有 Screen 在模块内**——`GroupManageSheet`（`:app`）直接消费 `TagGroupRuleEditSheet`
与 `TagGroupRuleViewModel`；highlight 侧的 Route Screen 已迁入模块。

## 3. 依赖分类

### common-ready

`kotlinx.coroutines.*`、`kotlinx.collections.immutable.*`、
`io.legado.app.data.entities.{TagGroupRule,HighlightTagRule}`、
`io.legado.app.data.repository.*`、`io.legado.app.domain.gateway.BookGroupMutationGateway`、
纯状态契约 `ListUiState`/`SelectableItem`/`InteractionState`。

### contract-needed（窄接口 + 平台实现）

| 依赖 | 现状 | 目标 | 状态 |
| --- | --- | --- | --- |
| 剪贴板 | **已注入 `:core:platform` 的 `Clipboard`** | 同左 | **完成** |
| 轻提示 | group **已注入 `Toaster`**；highlight 走 `_effects` | 统一为 Effect（highlight 形态已是目标形态） | **完成**（group 保持 Toast 语义，改由 Screen/调用方决定形态是后续项） |
| 导入/导出 IO | `RuleTransferPlatform`（`readImportSource`/`writeExport`，目标用 `String`） | **已抽好**，Android 实现 `AndroidRuleTransferPlatform` 在 `:app` | — |
| 上传 | `UploadRepository`（接口） | 保持 | — |
| 规则应用 | `BookGroupMutationGateway`（接口） | 保持 | — |
| 文件选择 | **已抽 `DocumentPicker`**（`openDocument` / `createDocument`，结果用不透明引用字符串） | 同左；Android 实现是 `:core:ui` 的 `rememberDocumentPicker()` | **完成**（M1-3c） |
| 序列化 | **已换 `:core:platform` 的 `JsonCodec`** | 同左 | **完成** |

### platform-island

`android.app.Application`、`android.net.Uri`、`androidx.activity.result.*`、`LocalContext`、
`LocalClipboard`、`R.string.*`——都留在 Android 宿主或通过上面的契约进入共享层。

M1-3c 之后的实际分布：`androidx.activity.result.*` 与 `LocalContext` 已**全部退出本模块**，
只剩 `:core:ui` 的 `rememberDocumentPicker()`（Android 专用实现）与 Route 持有；`Uri` 不再被本模块
代码引用（只在 `AndroidRuleTransferPlatform` 内部出现）。本模块内剩余的 Android 面是
`FilePickerSheet`（`:core:ui`，内部用 `android.webkit.MimeTypeMap`）与 `R.string.*` / `LocalClipboard`。

## 4. 调用闭包

Koin（`:app` 的 `appModule.kt`）：

```kotlin
single<UploadRepository> { DirectLinkUploadRepository() }              // :app
single<RuleTransferPlatform> { AndroidRuleTransferPlatform(androidContext()) }  // :app
single<Clipboard> { AndroidPlatformCapabilities.clipboard(androidContext()) }   // :app（M1-3a）
single<Toaster> { AndroidPlatformCapabilities.toaster(androidContext()) }       // :app（M1-3a）
singleOf(::TagGroupRuleRepository)      // :core:data
singleOf(::HighlightTagRuleRepository)  // :core:data
single<BookGroupMutationGateway> { BookGroupMutationRepository(get(), get()) }
viewModelOf(::TagGroupRuleViewModel)
viewModelOf(::HighlightTagRuleViewModel)
```

外部调用方只有三处：

- `ui/main/MainNavGraph.kt` → `HighlightTagRuleRouteScreen`
- `ui/main/bookshelf/GroupManageSheet.kt` → `TagGroupRuleEditSheet` / `TagGroupRuleIntent` / `TagGroupRuleViewModel`
- `di/appModule.kt` → 上述绑定

## 5. 关键行为语义（测试已锁定的部分）

- **导入分类**：`findOldRule` 命中且 `hasChanged` 为 true → `Update`；未命中 → `New`；命中且未变 → `Existing`；
  `isSelected` 默认 = 非 `Existing`。导入文本先 `trim()` 再交给 `RuleTransferPlatform`。
- **`hasChanged` 字段集**：group = `groupName` + `pattern`；highlight = `title` + `pattern` + `enabled`。
  改其它字段（如 `order`）不算变化。
- **导入落库**：只保存勾选的条目，group 侧随后调用 `BookGroupMutationGateway` 应用到所有书，最后状态回 `Idle`。
- **导出**：空选择只提示不写；否则 `generateJson` 后写目标，失败上报 `导出失败: <原因>`；
  `writeExport` 静默跳过的语义由平台实现保证（见 `RuleTransferPlatform` KDoc）。
- **排序**：`filterData` 结果按 `order` 升序；搜索/分组过滤命中 `groupName|title` 或 `pattern`（忽略大小写）。
- **展示名**：`displayName = groupName/title.ifBlank { pattern }`。

## 6. 目标 seam（M1-3 建议顺序）

1. ~~剪贴板/提示改为注入契约~~（**M1-3a 完成**，顺带删掉 `tagrules` 的 Provider 引用，与 M2 同向）。
2. ~~`Application`/`context.getString`/`R` 退出 ViewModel~~（**M1-3b 完成**）。
3. ~~GSON → JSON 编解码契约~~（**M1-3a 完成**）。
4. ~~文件选择能力契约化，Screen 只收回调~~（**M1-3c 完成**）。
5. 以上完成后，`tagrules` 的 Screen/VM/Contract 才能整体进 `commonMain`（模块登记为 `cmp`）。
6. 剩余的 `commonMain` 阻塞（M1-3c 之后重测，M1-3e 再重测）：`FilePickerSheet`（`:core:ui`，内含
   `android.webkit.MimeTypeMap`）必须 CMP 化或改由宿主提供；Screen/Sheet 的 `R.string.*`
   与 `LocalClipboard` 需换成 CMP 资源/契约。
   **M1-3d/M1-3e 之后**，`FilePickerSheet` 的**前置依赖链已清空**——它 → `OptionSheet` →
   `NormalCard`/`AppIcon`/`AppText`/`AppModalBottomSheet` 所需的 theme 符号
   （`LegadoTheme`/`LocalLegadoThemeColors`/`ProvideAppContentColor`/`ProvideAppDensity`/
   `ThemeResolver`/`LocalAppUiConfiguration`）已全部搬进 `:core:designsystem/commonMain` 并在
   desktop 目标编译通过。所以 `FilePickerSheet` 搬迁时的**真实阻塞只剩两处**：`MimeTypeMap`
   （Android-only API，需要替代或由宿主注入）与 `R.string.*`；组件实现（`OptionSheet` 等）本身
   也要随之搬。

回滚点：第 1 步可独立回滚（Provider 仍在），第 2 步起需整片回滚；每一步都不改导入/导出的磁盘语义。

第 2 步不是「改一行」：`BaseRuleViewModel` 的构造函数吃 `Application`（继承 `AndroidViewModel`），
要真正把 `Application`/`R` 赶出 VM，必须让 `tagrules` 不再继承它——即把共享编排抽成无 UI 的
`RuleTransferUseCase`，并给两个 Feature VM 各自的列表/选择状态（M1 计划明确要求「不再抽基类」）。
`BaseRuleViewModel` 仍被 `replacerules` / `dict` / `txttocrules` / `:app` 的 `TocViewModel`、
`RssSourceViewModel` 使用，不能在这条切片里一起删。M1-3b 已按此执行。

## 7. characterization 测试

`feature/tagrules/src/test/java/io/legado/app/feature/tagrules/TagRulesImportExportCharacterizationTest.kt`
（Robolectric + 内存 Room，真实 GSON）：

- 导入 JSON 数组 → New/Update/Existing 三分类与默认勾选；单对象 → 一条；非法文本 → `Error`；
- 导入文本 `trim` 后交给平台；
- 导出：选中项序列化后写入目标；空选择只提示不写；写入失败上报原因；
- 落库：只保存勾选项，group 侧触发规则应用，结束回 `Idle`；
- reducer：搜索过滤 + `order` 排序、`displayName` 回退、`hasChanged` 字段集；
- （M1-3a 新增）复制/粘贴走注入的 `Clipboard`/`Toaster`，**在没有安装任何全局 Provider 的 JVM 里**
  也一样通过——这条用例就是「Provider 引用已消失」的证明；
- （M1-3a 新增）`JsonCodec.toJson` 与 app 侧 `GSON.toJson` 对两个规则实体**字节级等值**，
  锁住导出文件格式。

## 8. 未验证风险

- 真机上的 SAF 导入/导出路径（`AndroidRuleTransferPlatform`）与内置规则导入不在本次测试范围。
- `writeExport` 静默跳过（目标打不开仍报「导出成功」）是既有语义，测试只做契约级断言。
- 桌面/iOS 目标尚未编译过本模块；CMP 化后的资源与文件选择能力需另立 PoC。
- group 侧 `syncGroups` 的成功文案原走 `context.getString(R.string.tag_group_sync_complete)`，
  使该 VM 经 `BaseRuleViewModel` 持有 `Application`。**M1-3b 已消除**：改发
  `TagGroupRuleEffect.SyncGroupsCompleted`，由 `GroupManageSheet` 解析 `stringResource` 后 toast，
  四种语言的 `values-*/strings.xml` 继续生效。

## 9. M1-3a 实录（2026-09-10）

范围：**只去静态平台入口，不搬 source set**。一次只改一个风险维度。

改动：

| 文件 | 改动 |
| --- | --- |
| `group/TagGroupRuleViewModel.kt` | 构造注入 `Clipboard` + `Toaster`；`GSON` → `JsonCodec`（`toJson`/`decodeList`/`fromJsonObject`） |
| `highlight/HighlightTagRuleViewModel.kt` | 构造注入 `Clipboard`；`GSON` → `JsonCodec` |
| `app/platform/AndroidPlatformCapabilities.kt`（新） | `Clipboard`/`Toaster` 的 Android 适配工厂，行为对齐 `Context.getClipText/sendToClip/toastOnUi` |
| `app/help/PlatformServices.kt` | 两个适配器移出，`install()` 改用上面的工厂（`appCtx` 不变） |
| `app/di/appModule.kt` | `single<Clipboard>` / `single<Toaster>`，`viewModelOf` 不变（按类型解析参数） |
| `gradle/architecture/legacy-baseline.txt` | 删除 4 条已归零的条目（tagrules 的 `gson`×2、`coreProvider`×2） |

两个坑：

1. **`di` 不能新增 `import io.legado.app.help.**`**：`checkLegacyArchitecture` 对
   `app/main/io/legado/app/di` 的 `legacyHelp` 有一条只降不升的基线。所以实现不能留在
   `help.PlatformServices`，必须放到 legacy 区之外的 `io.legado.app.platform`（该新区域零计数，
   不触发「新区域必须为零」）。
2. `JsonCodec.fromJsonObject` / `decodeList` 内部吞异常并返回 `null`，而 app 侧
   `GSON.fromJsonObject().getOrThrow()` 是抛异常。为保持「非法文本 → `格式不正确`」的既有语义，
   调用处必须显式 `?: throw Exception("格式不正确")`。

验证：

- `checkSharedPurity`、`checkModuleDependencies`、`verifyConfigArchitecture`、
  `checkLegacyArchitecture` 四门禁全绿；
- `:feature:tagrules:testDebugUnitTest` 14 用例 0 失败；`:core:platform:desktopTest` 绿；
- `:app:compileAppDebugKotlin` 绿（Koin 图按类型解析新参数）；
- **变异验证**：把 `pasteRule` 里的 `clipboard.getText()` 换回
  `ClipboardProvider.current.getText()` → 新用例以 `IllegalStateException`（Provider 未安装）变红，
  还原即绿——确认用例不是空转。

## 10. M1-3b 实录（2026-09-10）

范围：**拆掉 `BaseRuleViewModel` 依赖 + 把共享编排搬出 `base` 包**。仍不搬 source set、不动 SAF。

### 改动

| 文件 | 改动 |
| --- | --- |
| `core/viewmodel/.../core/rules/RuleTransferUseCase.kt`（新包） | 从 `BaseRuleViewModel` 抽出导入/导出/上传的无 UI 编排；`RuleEntitySpec<Entity>` 收拢实体语义（序列化/变化判定/查旧值/落库） |
| `core/viewmodel/.../core/rules/RuleTransferEvent.kt`（新） | 流程事件类型（形状同 `BaseRuleEvent`，但不住 `base` 包，理由见下） |
| `core/viewmodel/.../core/rules/{RuleTransferPlatform,BuiltInRulesImporter}.kt` | 自 `io.legado.app.base.rules` 整体搬包，KDoc 记录搬因 |
| `group/TagGroupRuleViewModel.kt` | `BaseRuleViewModel` → `ViewModel`；不再吃 `Application`；3 个状态流 + `RuleTransferUseCase` 组合；`TagGroupRuleTransferSpec` 承载实体语义 |
| `highlight/HighlightTagRuleViewModel.kt` | 同上（无 `bookGroupMutationGateway`，`persist` 只落库） |
| `group/TagGroupRuleContract.kt` | `TagGroupRuleEffect` 从空接口改为 `SyncGroupsCompleted` |
| `app/ui/main/bookshelf/GroupManageSheet.kt` | 收集 group VM 的 effect，用 `stringResource` 解析文案后 toast |
| `highlight/HighlightTagRuleScreen.kt` | `events` 类型 `BaseRuleEvent` → `RuleTransferEvent` |
| `app/di/appModule.kt`、`TocViewModel`、`RssSourceViewModel`、`dict`/`replacerules`/`txttocrules` 的 VM、两个测试 | 跟随搬包改 import |
| `gradle/architecture/legacy-baseline.txt` | 9 条 `legacyBase` 下调（3 条归零删除） |

结果：`tagrules` 两个生产区域的 `legacyBase` 计数 **2/3 → 0/0**，`main` 源集里
`gson`、`coreProvider`、`legacyBase` 三类 legacy 计数全部为零。

### 三个坑

1. **`checkLegacyArchitecture` 是「实际值必须等于基线」的双向棘轮**——不仅「新增」报错，
   `found < allowed` 也报错并要求同步下调基线；且**新区域必须为零**。最初把 `RuleTransferUseCase`
   放在与被替换基类同包的 `io.legado.app.base.rules`，立刻以「新区域出现 `import io.legado.app.base.**`」
   被拦下。
   正确解法不是登记新区域（那等于放宽），而是**让共享能力搬离 `base` 包**：
   `RuleTransferPlatform`/`BuiltInRulesImporter`/`RuleTransferUseCase`/`RuleTransferEvent` 一起住进
   `io.legado.app.core.rules`。这样 Feature 引用共享编排不再触碰那条棘轮，同时把它从 2/3 推到 0。
2. **app 侧 Android 实现不能跟着搬目录**：`AndroidRuleTransferPlatform` / `AndroidBuiltInRulesImporter`
   带 `io.legado.app.help.http.*` 与 `help.DefaultData` 依赖，搬到任何新目录都会让该新区域的
   `legacyHelp` 从零变正而被拦。它们因此留在 `app/.../base/rules/`（report-only 区），只加一行
   指向新包的 import。**接口已下沉、实现仍在 `:app`** 的过渡态是刻意的，记为 M1-3c 之后的清理项。
3. **`withContext(Dispatchers.IO)` 包裹容易在改写中丢**：原基类的 `autoApplyRules()` 是把
   `bookGroupMutationGateway.applyTagGroupRulesToAllBooks()` 包在 `Dispatchers.IO` 里的，而
   `viewModelScope` 默认跑 Main。抽出 `applyRules()` 时先写成裸调用，等于把「批量写库 + 重算书架分组」
   挪到主线程；已改回带 `withContext`，并在 KDoc 里写明这是行为等价要求。

### 验证

- 四门禁（`checkSharedPurity` / `checkModuleDependencies` / `verifyConfigArchitecture` /
  `checkLegacyArchitecture`）全绿，基线 326 → **323 条**；
- `:core:viewmodel:testDebugUnitTest` **19 用例**（新增 `RuleTransferUseCaseTest` 11 个个例，
  与既有 `BaseRuleViewModelTransferTest` 8 个并存）、`:feature:tagrules:testDebugUnitTest` **14 用例**、
  `:core:designsystem:desktopTest` 12 用例，0 失败；
- `:app:compileAppDebugKotlin` 绿（Koin `viewModelOf` 按类型解析去掉 `Application` 后的构造）；
- **变异验证**：把 `isSelected = status != ImportStatus.Existing` 改成 `= true` →
  「默认只勾选非已有」用例立刻变红（`expected:<[false, true, true]>`），还原即绿。
- 两个 VM 的构造参数已无 `Application`；`tagrules` 生产代码 `grep '^import io.legado.app.base\.'` 为空。

## 11. M1-3c 实录（2026-09-10）

**目标**：文件选择能力契约化，Screen 只收回调。

### 新增的两块

| 位置 | 形态 | 说明 |
| --- | --- | --- |
| `:core:platform` `commonMain` / `DocumentPicker.kt` | 接口（44 行） | `openDocument(mimeTypes, onResult)` / `createDocument(fileName, onResult)` |
| `:core:ui` / `platform/AndroidDocumentPicker.kt` | `@Composable fun rememberDocumentPicker(): DocumentPicker`（67 行） | SAF `OpenDocument` / `CreateDocument("application/json")` |

### 三个设计决定

1. **结果用不透明引用字符串，不用 `Uri`**。AGENTS.md 明令共享契约不得暴露 `Context`/`File`/`Uri`；
   Android 侧传 `uri.toString()`（`content://...`），共享层**不解释内容**，只把它交给
   `RuleTransferPlatform.readImportSource` / `writeExport`——那两个入口本来就要处理
   URL / URI / 纯文本三种形态。这使 Screen 里原本的 `contentResolver.openInputStream` 得以删除：
   `Uri.readText(context)` 内部就是 `contentResolver.openInputStream` + UTF-8 解码，**语义等价**。
2. **结果走回调而非 `suspend`**。Activity Result 的 launcher 必须在 Composition 里注册
   （依赖 `ActivityResultRegistryOwner`），所以持有 launcher 的只能是 Route，无法由 app 侧单例提供。
   做成 `suspend` 需要额外的桥接状态，收益不抵复杂度。代价是 `rememberDocumentPicker()` 内部要
   暂存「最近一次调用的回调」并在 launcher 返回后立即清空，以兑现「最多回调一次」的承诺。
3. **导出 MIME 固定 `application/json`，不做参数化**。迁移前四个 Screen 写的都是
   `ActivityResultContracts.CreateDocument("application/json")`；为尚未出现的格式加参数属于凭空抽象。

### 边界变化

- `tagrules` 生产代码不再出现 `androidx.activity.result.*`、`LocalContext`、`android.net.Uri`。
- Route 承担 launcher 注册（`HighlightTagRuleRouteScreen`），Screen 只收
  `onPickImportSource(mimeTypes)` / `onPickExportTarget(fileName)` 两个回调，**可以脱离 Android 编译**。
- 契约是为四个规则页（`tagrules` / `dict` / `replacerules` / `txttocrules`）共用的逐字重复代码而抽，
  本次只迁 `tagrules`；其余三页是后续可独立回滚的切片。

### 验证

- 四门禁全绿（基线维持 **323 条**，本切片不涉及 legacy 计数变化）；
- `:feature:tagrules:testDebugUnitTest` **15 用例**（新增「导入源是 SAF 选中的 URI 时由平台解析出规则文本」）、
  `:core:viewmodel:testDebugUnitTest` 19、`:core:designsystem:desktopTest` 12，0 失败；
- `:app:compileAppDebugKotlin` 绿；
- **变异验证**：把 `RuleTransferUseCase.importSource` 里的 `transferPlatform.readImportSource(text.trim())`
  改成 `text.trim()`（即「跳过平台直接解析」）→ 新增用例与另一条 trim 用例立刻变红
  （`15 tests completed, 2 failed`），还原即绿。

### 仍未解决（下一个切片）

- `FilePickerSheet` 在 `:core:ui` 且内部用 `android.webkit.MimeTypeMap`，**Screen 因此还不能进
  `commonMain`**；要登记 `cmp` 必须先把它 CMP 化或改由宿主提供。
- ~~`AppModalBottomSheet` / `OptionSheet` 因 CMP material3 expressive API 阻塞~~ **已解除（M1-3g）**：
  1.9.0 报的 `it is internal in file` 是**版本问题**，不是平台问题——符号就在 `commonMain`，
  只是被上游标成了 `internal`。升 `composeMultiplatformMaterial3` 到 1.12.0-alpha03 后即可用。
- Screen/Sheet 的 `R.string.*`（26 个键）与 `LocalClipboard` 同样待换 CMP 资源/契约。
  逐项台账与处置方向见 §12。
- 真机 SAF 路径未验证（导出到 Downloads、从 Downloads 导入）。

### 踩到的坑（工具侧，非设计侧）

- **Kotlin 块注释可嵌套**：KDoc 里写 `["*/*"]` 会让 `/*` 再开一层注释，随后的 `*/` 只关掉内层，
  整个注释不闭合、后续声明被吞，报错落在正文行上（`Expecting member declaration`）而看不出是注释问题。
  写 MIME 通配时不要原样写进注释。
- SAF 的 `OpenDocument` **不接受空数组**，会抛 `IllegalArgumentException`；契约声明「空数组 = 不过滤」，
  翻译责任在 Android 实现层（`ifEmpty { arrayOf("*/*") }`）。

## 12. M1-3d / M1-3e / M1-3f 后的依赖台账（2026-09-10）

把 tagrules 的 19 条 `io.legado.app.ui.*` import 逐个溯源到定义文件，再算**传递闭包**
（符号级，索引覆盖 `:core:ui` + `:core:designsystem`），得到下面这张判定表——它是下一个切片的依据。

**判定维度有三个，缺一个就会误判**（`AppModalBottomSheet` 就是这么被漏掉的）：

| 维度 | 判据 |
| --- | --- |
| `android.*` / `java.*` | 正则扫 import |
| `io.legado.app.*` 越界 | 只允许 `ui.*`；`domain.model.*` = `:core:model` 是 legal 的，**不是**阻塞 |
| **依赖 API 在非 Android 源集是否「存在且可见」** | 上两类干净也可能编不过，要分两种子情况：<br>① **源集里不存在**——如 `LocalConfiguration` 只在 `ui` 的 `androidMain`，**迁不走**；<br>② **符号在 `commonMain` 却是 `internal`**——如 CMP material3 1.9.0 的 expressive 系列，报 `Cannot access '…': it is internal in file`。这种情况**升级依赖就能解决**，别误判成「Android 独占」 |

⚠️ **`javap` 不是判据**：Kotlin 的 `internal` 顶层声明在 JVM 字节码里**仍是 `public`**（可见性在
`@Metadata`，且不 mangle 方法名）——实测 1.9.0 与 1.12.0-alpha03 的 `javap` 输出**逐字相同**，
但前者跨模块编译必失败。真判据只有两个：**上游源码的可见性修饰符** + **实际编译非 Android 目标**。

### 已进共享层（tagrules 侧 import 零改动）

`LegadoTheme` / `adaptiveContentPadding` / `AdaptiveSwitch`（M1-2、M1-3d，`IconSwitch.kt` 早已在
designsystem）、`AppText`、`AppIcons`（**M1-3f**）、`ListUiState` / `InteractionState` /
`SelectableItem`（`list/ListUiState.kt`）、`BaseImportUiState` / `ImportStatus`
（`importComponents/ImportState.kt`）。

### 可直接机械搬（文件本身与传递闭包都 0 `android.*`、0 `R`）

| 文件 | tagrules 要的符号 |
| --- | --- |
| `alert/AppAlertDialog.kt` | `AppAlertDialog` |
| `button/series/MediumTonalButton.kt` | `MediumTonalButton` |
| `button/series/SmallPlainButton.kt` | `SmallPlainButton` |
| `AppTextField.kt` | `AppTextField` |
| `DraggableSelectionHandler.kt` | `DraggableSelectionHandler` |
| `lazylist/LazyList.kt` | `FastScrollLazyColumn`（**M1-3v 已搬**，见 §25） |
| `menuItem/RoundDropdownMenuItem.kt` | `RoundDropdownMenuItem` |
| `card/GlassCard.kt` | （tagrules 不直接用，但已改委托共享的 `AppCardSurface`） |

这批是「下一个纯搬动切片」的候选：`card/AppCardSurface.kt` 已把卡片基元铺好，没有新 seam 要做。

⚠️ 本表是 M1-3f 时的判定，其中 `AppAlertDialog` **当时是欠判的**——它用了
`@ExperimentalMaterial3ExpressiveApi`，在 1.9.0 下编不过 desktop（三分法脚本看不见「可见性」这一层）。
**要等 M1-3g 升级后才真正可搬。**

### 仍阻塞，且阻塞类别决定做法

| 文件 | 阻塞 | 处置方向 |
| --- | --- | --- |
| ~~`util/PlainTextClipEntry.kt`~~ | ~~`android.content.ClipData`~~ **已解除并搬入 `commonMain`**（M1-3o） | 已完成：`ClipEntry` 构造下沉为**住在 `:core:designsystem`** 的 `PlainTextClipEntryFactory` 窄契约（`ClipEntry` 是 Compose `expect class`，`:core:platform` 零 Compose 装不下）。⚠️ 原判「换 `ClipboardProvider` 即可」**是错的**——那个 `setText` 会弹 toast（见 §18） |
| ~~`AppFloatingActionButton.kt`~~ | ~~`R`~~ **已解除**（M1-3j 走 CMP 资源，已搬入 `commonMain`） | 已完成 |
| `card/SelectionItemCard.kt`（`ReorderableSelectionItem`）、`SelectionBottomBar.kt`（`ActionItem`）、`rules/RuleListScaffold.kt` | `R`（M1-3l 后**主题链已解除**） | **原判「只因 `R` 卡住」是欠判的**：闭包里都带 `theme/OpaqueColorScheme.kt` → `ThemeEngine` → `android.content.Context`/`android.os.Build`。该主题链已由 **M1-3l 的配色平台契约解决**（三个文件都进 `commonMain`）。现状：~~`SelectionBottomBar` 只剩 `R`~~ **已搬入 `commonMain`（M1-3m）**；`SelectionItemCard` 的九宫格阻塞已由 **M1-3p 的 `NinePatchLoader` 契约**解除（`GlassCard` / `AppCheckbox` / `CheckboxItem` 同批搬入），**M1-3q 已搬入 `commonMain`**——`R.string.edit` 走 M1-3j 的 CMP 资源配方（`edit` 补进 4 个语言文件），`sh.calvin.reorderable` 实测是 KMP 制品（`reorderable-jvm` 变体），非阻塞；`RuleListScaffold` 另拖 `list/ListScaffold.kt` + `topbar/*`（`statusBarsIgnoringVisibility` —— desktop 侧不存在；`DynamicTopAppBar` 的 6 条 `R.string.*` + `SearchBar`）。⚠️ **原判「经 `animation/InteractiveHighlight` 卡 `RuntimeShader`」已证伪**——它是把「闭包」当成「直接依赖」的误判，见 **§21** |
| ~~`filePicker/FilePickerSheet.kt`~~ | ~~`R` + `android.webkit.MimeTypeMap`~~ **已解除并搬入 `commonMain`**（M1-3n） | 已完成：MIME 推断下沉为 `:core:platform` 的 `MimeTypeResolver` 窄契约，6 条 `R.string` 走 CMP 资源 |
| `importComponents/ImportComponents.kt` | `R` + `android.annotation.SuppressLint` + `:core:platform` | R 同上；`@SuppressLint` 可以删掉再编译看是否仍需要 |
| ~~`menuItem/RoundDropdownMenu.kt`~~ | ~~`rememberOpaqueColorScheme` → `ThemeEngine.getColorScheme(context)`~~ **已解除并搬入**（M1-3l） | 已完成：它是「配色方案工厂」切片唯一被完全解锁的文件 |
| `modalBottomSheet/AppModalBottomSheet.kt` + `OptionSheet.kt` | ~~expressive 是 `internal`~~ **已解除**（M1-3g 升 material3 到 1.12.0-alpha03） | 可搬进 `commonMain`，不应再按「留 Android 侧 / `expect/actual`」处理 |

**R 策略已于 M1-3j 定案**（不再是待决项）：走 CMP 的 `org.jetbrains.compose.resources`
（`src/commonMain/composeResources/values*/` + 生成的 `Res.string.*`），已打通并实测。
它与 `:core:ui` 的「库侧 `res/values*/strings.xml` 只放默认值、app 侧同名覆盖」策略**不兼容**
（`composeResources` 打进 assets，**不参与 Android 资源合并**，app 覆盖不到），所以共享层必须
自带全部支持语言目录。**每个文件搬动时按需补条目，不要一次性把所有 R 键都搬过去。**
机制、convention 改法与坑见 `cmp-module-convention.md` §7。

⚠️ 但**解了 R 不等于能搬完**：原拟随本切片搬的另外 4 个文件已实测受阻并**退回 `:core:ui`**
——`SearchBar.kt`（依赖同包 `AppDenseTextField`）、`topbar/TopBarButton.kt`（同包
`GlassTopAppBarDefaults`/`topBarLiquidGlassEnabled`）、`ReorderableConfigList.kt`
（coil3 + `sh.calvin.reorderable`）、`player/PlayerTocPage.kt`
（`kotlinx.collections.immutable` + model 类型）。它们**不是纯资源阻塞**，
属维度②（留在原模块的同包兄弟）与平台库依赖 ⇒ 各自另开切片。

## 13. M1-3h / M1-3i / M1-3j 实录（2026-09-10 ~ 09-11）

### M1-3h：弹层族进 `commonMain`

`modalBottomSheet/{AppModalBottomSheet,OptionSheet}.kt` 搬入 `:core:designsystem/commonMain`
（不再是平台岛）。附带两件事：

- 新增 `menuItem/LocalUseMiuixWindowPopup.kt`——`RoundDropdownMenu.kt` 带 `android.*` 搬不动，
  但其中的 `LocalUseMiuixWindowPopup` 两边都要用 ⇒ 按「**同名包声明下沉**」只把该声明放进共享层。
- 新引入显式依赖 `org.jetbrains.compose.animation:animation`（别名
  `compose-multiplatform-animation`），因为 `Modifier.animateContentSize` 属 animation 而非
  foundation；按「依赖只列实际用到的」约定显式声明。

同因解锁 `ThemeComponents.kt` 的 `MaterialThemeWrapper`（同一组 expressive 符号），
是「`AppTheme` 全栈进 `commonMain`」的前置。

### M1-3i：25 个原子组件进 `commonMain`

`button/`(4) + `button/series/`(13) + `divider/`(3) + `progressIndicator/`(3) +
`title/SmallTitle.kt` + `SectionTitle.kt`。**本切片零新增依赖**（这族只用 Miuix 与
`androidx.compose.material.icons`）。搬前用「文件内零 Android-only API + 引用闭包全在
designsystem」两条机械判据筛过，跨模块闭包边只有 `ToggleChip → card.NormalCard`
（已在 `card/AppCardSurface.kt`）。

顺带修了 `.agents/skills/legado-kmp-migration/scripts/portability-triage.py` 的**漏检缺口**：
它此前不认识「Android-only 的 Compose 符号」（`platform.LocalContext` /
`platform.LocalConfiguration` / `res.stringResource` 等），会把这些文件误判成可搬。
现内置 `ANDROID_ONLY_COMPOSE` 表 + `strip_comments`
（去注释后再扫，否则 KDoc 里「**不读** `LocalConfiguration`」这类说明文字会被当成真实依赖
——`AppDensity.kt` 就是）。

### M1-3j：CMP 多平台资源打通（R 策略定案）

机制与坑见 `cmp-module-convention.md` §7。本片实际搬入 3 个文件
（`AppFloatingActionButton.kt` / `ReorderAccessibility.kt` / `reader/ReaderMenuActionSquare.kt`），
另 4 个已退回（见上）。

**教训：`R` 从来不是唯一阻塞。** 该批候选里 4/7 在「资源已解决」之后仍编不过，根因是
**维度②（同包兄弟文件引用）**——静态扫描看不见「不带 import 的同包引用」，
只有编译非 Android 目标才暴露。**判「能不能搬」必须跑 desktop 编译，别只看扫描结果。**

## 14. M1-3k 实录（2026-09-11）：§12 剩余「可直接机械搬」的收敛

§12 的候选表已随 M1-3i 过期（`button/series/MediumTonalButton` / `SmallPlainButton` 已随 25 件原子件
一起搬走）。重扫后真正可搬的是 **4 个**：

| 文件 | 结果 |
| --- | --- |
| `alert/AppAlertDialog.kt` | ✅ 已搬（M1-3f 曾因 expressive 欠判，M1-3g 升级后解除） |
| `AppTextField.kt` | ✅ 已搬（430 行；只需 Miuix `TextField`/`InputField`） |
| `DraggableSelectionHandler.kt` | ✅ 已搬（零内部依赖） |
| `menuItem/RoundDropdownMenuItem.kt` | ✅ 已搬（Miuix `MiuixIcons` + `miuix-ui`） |
| `lazylist/LazyList.kt` | ⏸ 当时退回：同包 `lazylist/VerticalFastScroller.kt` 用了 Android-only 的 `Modifier.systemGestureExclusion()`。**M1-3v 已解并搬入**，见 §25 |
| `card/GlassCard.kt` | ❌ 未动：闭包挂 `AppContainerBackground.kt`（九宫格 `BitmapFactory`/`NinePatch`） |

**零新增依赖**：4 个文件的闭包边全在 designsystem（`theme/*`、`button.PrimaryButton`/
`SecondaryButton`、`text.AppText`、`icon.AppIcon`），第三方只有 Miuix，而
`miuix-ui` + `miuix-icons` 已声明。

### 两个新发现

1. **`Modifier.systemGestureExclusion()` 是 Android 独有的 foundation API**（desktop 侧
   `SystemGestureExclusionKt` 整个类都不存在）。这是「Android-only 但既不是 `android.*`
   也不是 `R`」的第三族，此前只覆盖了 `androidx.compose.ui.*`。已补进
   `portability-triage.py` 的 `ANDROID_ONLY_COMPOSE`（含 `excludeFromSystemGesture`、
   `preferKeepClear`、`WindowInsets.*IgnoringVisibility`、`WindowInsets.is*Visible`、
   `imeAnimationSource/Target`）；**同时明确 `ime` / `tappableElement` / `captionBar` /
   `waterfall` / `safeDrawing` / `*Padding()` 都有 desktop 实现，不要误杀**。
2. **`R` 之外还有主题链**：`SelectionItemCard` / `SelectionBottomBar` / `RuleListScaffold`
   原判「只因 `R` 卡住」是欠判的——闭包都经过 `theme/OpaqueColorScheme.kt` → `ThemeEngine`
   → `android.content.Context`/`android.os.Build`。真正的下一个阻塞是**「配色方案工厂」窄契约**，
   与 `menuItem/RoundDropdownMenu.kt` 是同一件事，应合成一个切片做。

### 工具修正（本片顺带）

`portability-triage.py` 补了两处，都是 M1-3j / M1-3k 连续两次踩到的：

- **同包兄弟边**：原脚本只顺 `import` 走闭包，而 Kotlin 同包顶层声明**不需要 import**
  ⇒ 这类依赖完全不出现，文件被误判 `ok`（M1-3j 的 `SearchBar`、M1-3k 的 `LazyList` 都是）。
  现已把「同包另一个文件里的顶层声明」也当作闭包边，并单列一段输出。
- **种子结论**：闭包里只要有一个 HARD，种子就搬不动——即使种子自身那行写着 `ok`
  （`GlassCard` 就是这么被误读的）。现在直接给每个种子 `可搬` / `阻塞 ← 原因`。

## 15. M1-3l 实录（2026-09-11）：配色平台契约，theme 引擎进 `commonMain`

§14 的结论「下一个真阻塞是配色方案工厂窄契约」在本片落地。

### 搬了什么（17 个文件 + 1 个新契约）

`:core:ui` 的 theme 闭包整体进 `:core:designsystem/commonMain`（`git mv`，包名不变）：

| 文件 | 说明 |
| --- | --- |
| `theme/BaseColorScheme.kt` | 抽象基类（`lightScheme`/`darkScheme`） |
| `theme/CustomColorScheme.kt` | material-kolor 的 `dynamicColorScheme(seed…)` |
| `theme/ThemeEngine.kt` | 工厂本体：预定义表 + Dynamic/Custom 解析 + AMOLED/透明变换 |
| `theme/OpaqueColorScheme.kt` | `rememberOpaqueColorScheme()`（独立窗口用） |
| `theme/colorScheme/*.kt` ×12 | GR / Lemon / WH / Elink / Sora / August / Carlotta / Koharu / Yuuka / Phoebe / Mujika / Transparent |

**零新增依赖**：material3、material-kolor、`core:model` 都已在 designsystem。

### 契约设计：先证明「哪条分支不可达」，再决定契约面

`ThemeEngine` 里只有两处 `android.*`：`dynamicLight/DarkColorScheme(context)` + `Build.VERSION.SDK_INT`，
以及 `Context.primaryColor`。但**「把 Context 换成契约」并不等于「行为会变」**——先逐个调用点
查了上下文实际有没有被用到：

| 调用点 | mode | `context` 是否真的参与计算 |
| --- | --- | --- |
| `AppTheme` | `themeSettings.appTheme` | 否——`customSeedColor` 传的是非空 `Int`（`ThemeSettings.customPrimary: Int = 0`）⇒ elvis 不触发；`Dynamic` 时才用 |
| `ThemeConfigScreen` ×2 | 同上 | 否——同上，传的也是非空 `Int` |
| `OpaqueColorScheme` | 恒为 `Transparent` + `forceOpaque = true` | **否**——`resolveMode` 把 `Transparent` 归一到 `WH`，落进「预定义配色」分支，`Dynamic`/`Custom` 都不可达 |

⇒ `ThemeSeedColors.primaryColor(context)` 其实**是死代码**。结论：换契约的语义风险 ≈ 0，
但**契约还是要做**，因为 `context` 参数本身把 `ThemeEngine` 钉死在 Android 侧。两个单方法
`fun interface`（沿用 `BigDataStore`/`SourceRuntime` 的 install 手法）：

```kotlin
fun interface ThemeSeedColorProvider { fun primaryColor(): Int }        // 未注入 ⇒ requireNotNull 抛异常
fun interface DynamicColorSchemeProvider { fun colorScheme(darkTheme: Boolean): ColorScheme? }
object DynamicColorSchemes { /* provider?.colorScheme(darkTheme) */ }   // 未注入 ⇒ null，回落 GR
```

**两者的失败语义刻意不同**：回退主色缺失是**配置错误**（该值来自 app 的偏好存储）；
而「没有动态取色」在 desktop/iOS 上是**正常状态**（那些平台本就没有系统调色板）⇒ 返回 `null`
并回落预定义配色，与 Android 12 以下走同一条路径。要求所有非 Android 宿主都注入一个
「永不命中」的实现才肯编译，是仪式而不是约束。

### 踩到的坑

1. **G4 门禁把新区域的全局 `appCtx` 判为 blocking**。第一版实现把 `installAndroidThemePlatform()`
   放在 `app/ui/theme/` 下、直接引用 `splitties.init.appCtx`，`checkLegacyArchitecture` 立刻拦：
   `app/main/io/legado/app/ui/theme：全局 Context 直连（splitties appCtx）首次出现 1 处`。
   门禁的检测口径是 `^import splitties.init.appCtx$`（按 import，不按用法）。**正确响应是把
   `Context` 显式传参**（`installAndroidThemePlatform(this)`，调用点只有 `Application.onCreate`），
   而不是往基线里登记——门禁的意图正是「新代码不许再新增隐藏的全局耦合」，显式传参本来也更干净。
2. **同名不同包**：`org.jetbrains.compose.resources.stringResource` 与
   `androidx.compose.ui.res.stringResource` 简单名完全一样。`ANDROID_ONLY_COMPOSE` 只按符号名
   扫全文，于是把 M1-3j 搬进 designsystem 的 `ReorderAccessibility.kt` 误报成阻塞。
   现改为 **import 溯源**（`cmp_resource_symbols`）：该简单名从 CMP 包导入就不算阻断。
   实测 CMP 1.12.0 的 `components-resources` 提供 `stringResource` / `vectorResource` /
   `painterResource` / `imageResource` / `pluralStringResource` / `stringArrayResource`，
   **没有** `dimensionResource`（后者仍属 android-only）。

### 验证

干净重建：四门禁 + `:app:compileAppDebugKotlin` + `:core:designsystem:desktopTest` +
`:core:viewmodel:testDebugUnitTest` + `:feature:tagrules:testDebugUnitTest` + `testAppDebugUnitTest`
+ `:app:assembleAppDebug`。单测用例数与基线逐字一致（689）。

### 本片解锁了什么

- `menuItem/RoundDropdownMenu.kt` —— 唯一被**完全**解锁的文件，已搬入 `commonMain`。
- `SelectionBottomBar.kt` —— 主题链解除后**只剩 `R`**，可按 M1-3j 配方补 `Res.string` 条目后搬。
- `SelectionItemCard.kt` / `RuleListScaffold.kt` —— 主题链解除，但各自另有真实阻塞（九宫格
  `AppContainerBackground`；`topbar/*` + `RuntimeShader`），见 §12 表。

**下一个切片建议**：`SelectionBottomBar`（纯 `R` 补条目，机械）或 `filePicker/FilePickerSheet`
的 `MimeTypeMap` 契约。
## 16. M1-3m 实录（2026-09-11）：`SelectionBottomBar` 进 `commonMain`（CMP 资源配方第二次复用）

§15 判定「只剩 `R`，按 M1-3j 配方补条目即可搬」的那一个文件，本片落地。这是 M1-3j 的 CMP
资源配方**首次被复用** ⇒ 同时验证了「按需补条目」这条约定在第二次使用时是否还有摩擦（结论：没有）。

### 改动（5 个文件，零新增依赖）

| 文件 | 改动 |
| --- | --- |
| `core/ui/…/widget/components/SelectionBottomBar.kt` | `git mv` → `core/designsystem/src/commonMain/…`；包名 `io.legado.app.ui.widget.components` 不变 ⇒ 消费方（`TocScreen` / `ListScaffold`）import 零改动 |
| `composeResources/values{,-zh-rCN,-zh-rHK,-zh-rTW}/strings.xml` | 各 +3 条：`select_all` / `invert_selection` / `more_menu` |

- `R.string.*` → `Res.string.*`，`stringResource` 由 `androidx.compose.ui.res` 换成
  `org.jetbrains.compose.resources`，删掉 `import io.legado.app.core.ui.R`。
- 三个条目**逐字节核对**（`:core:ui` 与 `app` 两侧都比过）：`values` 为
  `Select all` / `Invert Selection` / `More menu`；`zh-rCN` `全选`/`反选`/`更多菜单`；
  `zh-rHK` `全選`/`反選`/`更多菜單`；`zh-rTW` `全選`/`反選`/`更多選單`。四个语言六处全部一致
  ⇒ **无用户可见变化**。
- 闭包（triage 复跑：32 文件，种子 `可搬`）只有 `theme/*`（M1-3l 已搬）、`AppIcon`/`AppIcons`、
  `AppText`、`RoundDropdownMenu`/`RoundDropdownMenuItem`（M1-3l 已搬）——**全部已在 designsystem**。
- 顺带实证一条 M1-3k 记过的正面清单：`WindowInsets.navigationBars` / `.tappableElement` / `.ime`
  / `.union` / `Modifier.windowInsetsPadding` 在 desktop **都有实现**——不是「没被扫出来」，
  而是真的编得过（`:core:designsystem:compileKotlinDesktop` 通过是判据）。

### 工具坑（新，与 M1-3l 那个不是同一个）

M1-3l 记的是「**Write** 产出 LF」；本片发现 **`Edit` 也会**：对 `SelectionBottomBar.kt`
连发 3 个 Edit（片段不同、有顺序依赖）后，文件从 `215 CRLF + 1 裸 LF` 变成**纯 LF**。
⇒ 行尾体检不能只盯「本次新建的文件」，**凡是被 Edit 碰过的都要查**（本片 1 个文件转回 CRLF）。

⚠️ 但**不要无脑全转 CRLF**：`composeResources/values*/strings.xml` 自 M1-3j 建目录起**就是 LF**
（已随 M1-3j 提交），改它要**保持 LF**。**判据是文件当前的行尾，不是扩展名**——本片 4 个 XML
的 diff 因此恰好是 3 行新增，没有全文件行尾翻转。

### 验证（干净重建后全部通过）

- `./gradlew clean` → 四门禁全绿 + `:app:compileAppDebugKotlin` + `:app:assembleAppDebug`，
  **BUILD SUCCESSFUL, 320 tasks / 316 executed**。
- 单测 **689**（app 635 + designsystem 20 + viewmodel 19 + tagrules 15），**与基线逐字一致**，
  0 失败 0 错误；另跑 `:core:data:desktopTest`（81）⇒ 合计 770。
- 生成物核对：`String0.commonMain.kt` **7 条 × 4 语言** `ResourceItem` 齐全；
  APK 内 `assets/composeResources/io.legado.app.core.designsystem.res/values*/strings.commonMain.cvr`
  4 个（默认 250B、三个中文 222B）。
- `git diff --cached --check` 干净。

### 相邻的剩余阻塞（不变）

`SelectionItemCard`（九宫格 `card/AppContainerBackground.kt` 的 `BitmapFactory`/`NinePatch`）、
`RuleListScaffold`（`topbar/*` + `list/ListScaffold.kt` + `animation/InteractiveHighlight.kt` 的
`RuntimeShader`）、`FilePickerSheet` 的 `android.webkit.MimeTypeMap` 契约。

## 17. M1-3n 实录（2026-09-11）：`FilePickerSheet` 进 `commonMain`，MIME 推断下沉为窄契约

§12 表里最后一类「平台 API 型」阻塞（不是 `R`、也不是主题链）在本片解决。

### 阻塞拆解：两个都不难，但要分开做

| 阻塞 | 处置 |
| --- | --- |
| 6 条 `R.string`（`select_operation` / `sys_folder_picker` / `sys_file_picker` / `multi_select_items` / `manual_input` / `upload_url`） | 按 M1-3j 配方补 `composeResources/values*/strings.xml`（4 语言 × 6 条，值与 `:core:ui`、`app` 两侧逐字节核对一致 ⇒ 无用户可见变化） |
| `android.webkit.MimeTypeMap` | 下沉为 `:core:platform` 的 `MimeTypeResolver` 窄契约 |

### 契约设计：先量清输入域，再决定契约面

改造前先把**全部 22 个调用点**的 `allowExtensions` 实参扫了一遍，只有三种：
`null`（9 处）、`arrayOf("json")`（5 处）、`arrayOf("json", "txt")`（7 处，写法有换行差异）。
再对函数体：`"*"` → 全通配、`"txt"`/`"xml"` → `text/*` 是**特判**，其余才查平台表——
所以 `"json"` 是**唯一真正走平台查表的值**，实际产出只有三种。

这一步很关键：它排除了「干脆把表抄进共享层」这个看起来更省事的选项。`MimeTypeMap` 是一张
上千条的扩展名表，硬编码 `json → application/json` 在今天的调用点上等价，但**明天有人传
`"epub"` 就会静默退化成 `application/octet-stream`**（Android 原本认得它）——是行为变化，
不是重构。所以仍然走契约。

```kotlin
// :core:platform/commonMain
fun interface MimeTypeResolver { fun mimeTypeOf(extension: String): String? }

object MimeTypeResolverProvider {
    fun install(resolver: MimeTypeResolver)
    fun uninstall()
    val isInstalled: Boolean
    val current: MimeTypeResolver   // 未注入 ⇒ 「一律 null」的兜底，永不抛
}
```

**失败语义沿用 M1-3l 的判据（「缺失是否合理」）**：未注入 ≠ 配置错误。desktop / iOS 没有系统
文件选择器、也就没有 MIME 表，属**正常状态** ⇒ `current` 返回「一律 `null`」的解析器，调用方
本就把 `null` 翻译成 `application/octet-stream`，与 Android 上「未知扩展名」是同一条路径。
（对比 `ThemeSeedColors` 那种「缺了就是配错了」才抛异常。）

Android 实现放 **app** 的非 legacy 包：`io.legado.app.platform.AndroidPlatformCapabilities
.mimeTypeResolver()`，由 `PlatformServices.install()` 注入——与 `clipboard` / `toaster`
同一组工厂、同一处安装。**它不需要 `Context`**（`MimeTypeMap` 是进程级单例），所以没有
M1-3l 那种「显式传 Context 还是登记基线」的门禁问题。

### 新增模块依赖（1 条）

`:core:designsystem` 加 `implementation(project(":core:platform"))`。方向无环
（`:core:platform` 只依赖 `:modules:rhino` + KMP 制品），G1/G2 均通过。

### 顺带补上第一批 characterization 测试（本片唯一一次基线变动）

`typesOfExtensions` 此前**零测试覆盖**（它是 `private`，且平台调用不可测）。改契约后第一次
可测 ⇒ 顺手把它提成 `internal` 并新增 `FilePickerSheetMimeTest`（10 用例，desktopTest）：

- 入参三态（`null` / 空数组 / `["*"]`）⇒ 全通配；
- `txt` / `xml` 走特判且**不去问解析器**（用记录型 stub 断言 `asked` 为空）；
- `json` 查表命中 / 查表落空 ⇒ `application/octet-stream`；
- `["json", "txt"]` 两分支混用；`["txt","xml"]` 去重；
- **未安装解析器**时不崩、退化为 `application/octet-stream`。

**基线因此从 689 变为 699**（designsystem 20 → 30），并非回归。
变异验证：把回落的 `application/octet-stream` 改成 `text/plain`，2 个用例立刻变红。

### 踩到的坑

1. **Kotlin 块注释可嵌套**：KDoc 里写 MIME 通配字面量同时引入 `*/`（提前闭合注释）与
   `/*`（再开一层），`compileKotlinDesktop` 抛一片 `Expecting a top level declaration`。
   同名序列在**字符串字面量**里完全合法。判据是**注释上下文**，不是全文扫描。
   详见 `cmp-module-convention.md` §7.9。
2. **XML 追加位置**：脚本把新条目追加在文件末尾 ⇒ 出现两个 `</resources>`。要插在**已有闭合
   标签之前**，并用 `xml.dom.minidom.parse()` 校验。
3. **（方法论纠正）行尾与 diff 无关**：本片实测推翻了此前「必须保持 CRLF」的记录——
   `core.autocrlf=true` 下 blob 一律存 LF，LF/CRLF/MIXED 三种工作区行尾算出的 blob 相同、
   无内容 diff。详见 `cmp-module-convention.md` §7.6 与 `topics/env-pitfalls.md` §B。

### 验证（干净重建后全部通过）

- `./gradlew clean` → 四门禁全绿 + `:app:compileAppDebugKotlin` + `:app:assembleAppDebug`，
  **BUILD SUCCESSFUL, 322 tasks（317 executed）**。
- 主验证集 **699**（app 635 + designsystem 30 + viewmodel 19 + tagrules 15），0 失败 0 错误；
  另跑 `:core:data:desktopTest`（81）、`:core:platform:desktopTest`（76）⇒ 全量 856。
- 生成物：访问器 **13 条 × 4 语言**；APK 内 4 个 `.cvr`（默认 528B、三个中文 484B）。
- `git diff --cached --check` 干净。

### 剩余阻塞

`SelectionItemCard` / `GlassCard`（九宫格 `AppContainerBackground` 的 `BitmapFactory`/`NinePatch`）、
`RuleListScaffold`（`topbar/*` + `animation/InteractiveHighlight.kt` 的 `RuntimeShader`）。
（`util/PlainTextClipEntry` 已于 **M1-3o** 解除并搬入，见 §18；九宫格一条已于 **M1-3p** 解除，
`GlassCard` / `AppCheckbox` / `CheckboxItem` 同批搬入，见 §19。）之后才是 tagrules 的
Screen/VM/Contract 进 `commonMain` 并登记 `cmp`。
## 18. M1-3o 实录（2026-09-11）：`plainTextClipEntry` 进 `commonMain`，`ClipEntry` 构造下沉为窄契约

§12 表里 `util/PlainTextClipEntry.kt` 原写的处置方向是「`:core:platform` 已有 `ClipboardProvider`，
换窄契约即可」。**动手前先量语义，发现这个方向是错的**——本片主要记录的就是「为什么不能换」。

### 先量「到底差在哪」：`ClipboardProvider.setText` 会弹提示

`plainTextClipEntry` 的 14 个调用点全部长这样（app 4 个文件 + 4 个 feature，共 9 个文件）：

```kotlin
if (result == SnackbarResult.ActionPerformed && url != null) {
    clipboardManager.setClipEntry(plainTextClipEntry("url", url))   // clipboardManager = LocalClipboard.current
}
```

即 **Snackbar 的「复制链接」动作**。而 `:core:platform` 的 `Clipboard.setText` 内部走
`Context.sendToClip`，会 `longToastOnUi(R.string.copy_complete)`——**换过去等于给这 14 个调用点
凭空多弹一次 toast**。`Clipboard` 的 KDoc 自己写着「需要只复制不提示的场景应另立能力，
不要改这里」⇒ 合并是行为变化，不是重构，本片不做。

### 真正卡住的只有一件事：`ClipEntry` 在共享层没有构造器

把 CMP 1.12.0 的源码翻了一遍（`org.jetbrains.compose.ui:ui:1.12.0` 的 `-sources.jar`）：

| 目标 | `ClipEntry` |
| --- | --- |
| common | `expect class ClipEntry { val clipMetadata: ClipMetadata }`——**无构造器** |
| android | `actual class ClipEntry(val clipData: ClipData)` |
| desktop | `actual class ClipEntry @ExperimentalComposeUiApi constructor(nativeClipEntry: Any)` |

而 `LocalClipboard` / `Clipboard.setClipEntry(...)` 本身**在 commonMain 就有**（desktop 也提供）。
所以「搬不动」的只有**构造**这一步。

顺带排除一条看起来更省事的路：旧 API `ClipboardManager.setText(AnnotatedString)` 不需要平台类型
（`LocalClipboardManager` 也在 common），但 CMP 1.12.0 里 `ClipboardManager` 与
`LocalClipboardManager` 都已标注「Use Clipboard instead, which supports suspend functions.」
弃用，不采用。

### 契约面与落点

```kotlin
// :core:designsystem/commonMain，包 io.legado.app.ui.util（与 helper 同包）
fun interface PlainTextClipEntryFactory { fun plainText(label: String, text: String): ClipEntry }

object PlainTextClipEntryProvider {   // install / uninstall / isInstalled / current
```

- **落点由类型决定，不按惯例**：本仓平台契约默认放 `:core:platform`，但那是**零 Compose** 的
  纯 KMP 模块，装不下一个返回 `ClipEntry` 的方法 ⇒ 契约住在 `:core:designsystem`。这是第一个
  「契约住在 Compose 模块」的案例，见 `cmp-module-convention.md` §8.5。
- **失败语义 = 抛异常**（判据同 `ClipboardProvider`）：「复制」是用户主动动作，宿主没接上就该炸，
  而不是让用户点了「复制链接」却没反应。对比 `MimeTypeResolverProvider` 的「一律 null」——
  那里「没有 MIME 表」在 desktop 是正常状态。判据仍是「缺失是否合理」，不是一刀切。
- **零调用点改动**：`git mv` 保持包名 `io.legado.app.ui.util`，`plainTextClipEntry(label, text)`
  作为同包门面继续存在、签名不变 ⇒ 9 个文件的 import 与 14 处调用**一行没动**。
- Android 实现在 app 的 `AndroidPlatformCapabilities.plainTextClipEntryFactory()`
  （`ClipEntry(ClipData.newPlainText(label, text))`，与迁移前**逐字等价**、**不需要 `Context`**），
  由 `PlatformServices.install()` 注入——与 `clipboard` / `toaster` / `mimeTypeResolver` 同一处。

### 测试：只锁得住两条语义（基线又一次 +3）

`ClipEntry` 造不出来 ⇒ **成功路径在 `commonTest` 里根本无法断言**（这本身就是这个 seam 存在的
原因）。新增 `PlainTextClipEntryTest`（3 用例）锁能锁的：

1. 未注入 ⇒ 抛 `IllegalStateException`，且信息里指明是哪个注入点；
2. install / uninstall 与 `isInstalled` 同步；
3. `plainTextClipEntry` 把 `label` / `text` **原样透传**给宿主工厂——用哨兵异常 `ProbeStop`
   让工厂记录完参数后立刻抛出，于是断言不必持有 `ClipEntry` 实例。

**主验证集 699 → 702**（designsystem 30 → 33）。变异验证：把 helper 里的实参调换成
`plainText(text, label)` ⇒ `helperForwardsLabelAndTextVerbatim` 精确变红（1/3）。

### 验证（干净重建后全部通过）

- `./gradlew clean` → 四门禁全绿 + `:app:compileAppDebugKotlin` + `:app:assembleAppDebug`，
  **BUILD SUCCESSFUL, 322 tasks（317 executed）**。
- 主验证集 **702**（app 635 + designsystem 33 + viewmodel 19 + tagrules 15），0 失败 0 错误；
  另跑 `:core:data:desktopTest`（81）、`:core:platform:desktopTest`（76）⇒ 全量 859。
- `core:designsystem/commonMain` 仍零 `android.*`（只出现在 KDoc 里）。
- `git diff --cached --check` 干净；因改写幅度大，`PlainTextClipEntry.kt` 被记成 A+D（非 rename），
  可用 `git log --follow` 追溯（与 M1-3n 的 `FilePickerSheet.kt` 同）。

### 剩余阻塞

`SelectionItemCard` / `GlassCard`（九宫格 `AppContainerBackground` 的 `BitmapFactory`/`NinePatch`）、
`RuleListScaffold`（`topbar/*` + `animation/InteractiveHighlight.kt` 的 `RuntimeShader`）。
之后才是 tagrules 的 Screen/VM/Contract 进 `commonMain` 并登记 `cmp`。
（上述九宫格一条已于 **M1-3p** 解除并搬入，见 §19；`SelectionItemCard` 已于 **M1-3q** 搬入，
见 §20。）

## 19. M1-3p 实录（2026-09-11）：九宫格下沉为窄契约，`GlassCard` / `AppCheckbox` / `CheckboxItem` 进 `commonMain`

§12 表里 `card/SelectionItemCard.kt` 与 `card/GlassCard.kt` 共用的那条阻塞
（`AppContainerBackground.kt` 的 `BitmapFactory`/`NinePatch`）在本片落地。

### 搬了什么（4 个文件搬入，1 个新契约 + 1 个新测试）

| 文件 | 改动 |
| --- | --- |
| `core/ui/…/widget/components/AppContainerBackground.kt` | `git mv` → `:core:designsystem/commonMain`；`loadNinePatch` 改走契约，`LocalContext` / `LocalConfiguration` / `koinInject` 换成跨平台等价物（见下表） |
| `core/ui/…/widget/components/card/GlassCard.kt` | `git mv` → designsystem；删掉历史遗留的 material2 `@OptIn` |
| `core/ui/…/widget/components/checkBox/AppCheckbox.kt` | `git mv` → designsystem，**逐字未改**（`CheckboxItem` 的传递依赖） |
| `core/ui/…/widget/components/checkBox/CheckboxItem.kt` | `git mv` → designsystem，**逐字未改** |
| `core/platform/…/NinePatchLoader.kt` | 新增契约 + Provider（`fun interface` + install/uninstall/isInstalled/current） |
| `core/platform/…/commonTest/NinePatchLoaderContractTest.kt` | 新增 3 用例 |

包名全部不变（`io.legado.app.ui.*` 是共享命名空间）⇒ `app` / `core:ui` / `feature:*` 侧
**一行 import 都没动**（`GlassCard` 被 **59 个文件**引用、`CheckboxItem` 12 个、
`AppCheckbox` 6 个）。`designsystem` 新增 **1 个依赖**：`libs.coil.compose`。
（`checkBox/CheckboxGroupContainer.kt` **没有搬**：全仓零消费方，搬进共享层只会变成死代码；
本片的取舍是「只搬有真实消费方的东西」优先于「把目录搬空」。）

### 契约设计：为什么落 `:core:platform`（与 §18 的 `ClipEntry` 恰好相反）

```kotlin
// :core:platform/commonMain
fun interface NinePatchLoader { fun load(path: String): Any? }
object NinePatchLoaderProvider { /* install / uninstall / isInstalled / current */ }
```

- **落点仍由类型决定**（§8.5 的规则），而这里的返回类型是**不透明 payload `Any?`**——正是
  图像加载器 `data` 形参的类型（`coil3.ImageRequest.Builder.data` 收的就是 `Any?`）。
  没有 Compose 类型 ⇒ 按惯例进 `:core:platform`。这一片与 §18 构成一组对照：
  **同一个「平台能力」问题，是契约的返回类型决定了它住哪个模块**（`ClipEntry` 是 CMP 的
  `expect class` ⇒ 只能住 designsystem；`Any?` ⇒ 可以住 `:core:platform`）。
- **为什么不把类型写实**：Android 侧真正要交出去的是 `NinePatchDrawable`，desktop / iOS 没有
  对应物；一旦写进契约，`:core:platform` 立刻装不下（就是 §18 的教训）。
- **三种 `null` 是同一条路径**：非 `.9.png`、解析失败、平台没有九宫格能力 ⇒ 调用方一律回落成
  「按原路径交给 Coil」。**未注入 = 一律 null、不抛异常**，判据同 `MimeTypeResolver`
  （「这个平台没有九宫格」是正常状态，不是配置错误）。
- **本片唯一的行为差异**就在这条回落路径上：desktop 上背景图会按普通位图缩放，而不是按
  九宫格拉伸。Android 侧逐字等价（`.9.png` 前缀判断、`runCatching`、`NinePatch.isNinePatchChunk`
  校验全部原样搬进 `AndroidPlatformCapabilities.ninePatchLoader()`）。

### 为什么没有把「整层绘制」下沉成契约

另外两条路都量过，都更差：

- **契约返回 `Modifier`**（`produceState` + Coil 全段留在 Android 侧）：契约方法必须是
  `@Composable`，`fun interface` + lambda 的 SAM 实现指望不上；而且会把**跨平台**的 Coil 管线
  整段推进 `app` ⇒ desktop 连普通背景图都画不出来，是能力缩水。
- **契约返回 `Painter`**：Android 侧得先把 `NinePatchDrawable` 光栅化成位图才能塞进
  `BitmapPainter`，并且会把 Coil 的异步 painter（crossfade / 内存缓存）换成一次性 suspend
  加载——那是**行为变化**，不是重构。

⇒ 取「最窄的一步」：只把真正平台相关的那一步（九宫格解码）切出去，其余留在共享层。

### 搬动时换掉的两处平台绑定（以及为什么等价）

| 原来 | 换成 | 依据 |
| --- | --- | --- |
| `LocalContext.current` | `coil3.compose.LocalPlatformContext.current` | coil3 自带的多平台替代；Android 上返回的就是同一个 `Context`。实测 `coil-compose-core-jvm` 里有 `LocalPlatformContext` |
| `LocalConfiguration.current.screenWidthDp / screenHeightDp` | `LocalWindowInfo.current.containerSize` | `WindowInfo.containerSize` 在 CMP 1.12.0 的 `commonMain`（非实验 API，已核对 `-sources.jar`）。它只用作 Coil 的**解码目标尺寸**：Android 侧由 `WindowMetricsCalculator` 算出窗口像素、desktop 侧是 `component.sizeInPx`——多窗口下比「屏幕尺寸」更贴合，绘制结果不变 |
| `imageLoader = koinInject<ImageLoader>()` | **删掉该参数** | `App` 实现 `coil3.SingletonImageLoader.Factory` 且 `newImageLoader(context) = get()`（即 Koin 里那个单例）⇒ 走 `SingletonImageLoader` 拿到的是**同一个实例**，crossfade / 各 Decoder / `CoverInterceptor` 一字不差。这样 **designsystem 不必引入 Koin**（它是纯 KMP 库、但把 DI 拉进设计系统是无谓的方向改变） |

搬动时还补了一个原来没有的守卫：`containerSize` 为 `0`（窗口尚未布局、平台 impl 的初值）时
**不设**显式请求尺寸，交给 Coil 自己的约束尺寸——原实现读 `screenWidthDp` 恒为正，
换成 `containerSize` 后不加守卫可能把图画成 1×1。

### 踩到的坑

1. **`git mv` 不会替你建目标父目录**。搬进 designsystem 里还不存在的 `checkBox/` 时报
   `fatal: renaming '…' failed: No such file or directory`（`card/` 因为已有 `AppCardSurface.kt`
   所以没暴露问题）。先 `mkdir -p` 再 `git mv`。
2. **顺手核查了 `@OptIn(ExperimentalMaterialApi::class)`**：`GlassCard` 上挂着 material2 的
   opt-in，但文件里一处 material2 API 都没用（`BorderStroke` 属 foundation）⇒ 按 M1-3f 的既定
   处理**删掉**，不要为了保住这行给 designsystem 加 material2 依赖。

### 验证（干净重建后全部通过）

- `./gradlew clean` → 四门禁全绿 + `:app:compileAppDebugKotlin` + `:app:assembleAppDebug`，
  **BUILD SUCCESSFUL, 360 tasks（335 executed）**。
- 主验证集 **702**（app 635 + designsystem 33 + viewmodel 19 + tagrules 15）**逐字不变**——
  本片没有动共享层的可测语义；`:core:platform:desktopTest` **76 → 79**（+3 用例），
  另跑 `:core:data:desktopTest`（81）⇒ 全量 **862**。
- `core:designsystem/commonMain` 仍零 `android.*`（只出现在 KDoc 里）。
- 变异验证：把 Provider 的未注入兜底从「一律 `null`」改成返回空串 ⇒
  `uninstalledFallsBackToNull` 与 `uninstallClearsDelegate` 精确变红（2/3），已还原并复绿。

### 剩余阻塞（本片解除后的现状）

- ~~`SelectionItemCard`（`ReorderableSelectionItem`）只剩两条~~ ⇒ **M1-3q 已搬入**（见 §20）。
  ⚠️ `card/GlassCard.kt` 之前被并列在「阻塞」里，现在它自己和 `CheckboxItem` / `AppCheckbox`
  都已搬完。
- ~~`RuleListScaffold` 卡 `animation/InteractiveHighlight.kt` 的 `RuntimeShader`，
  「剩下的唯一真平台契约」~~ ⇒ **已证伪**（见 **§21**）：`RuleListScaffold` 的编译闭包根本
  不触及 `InteractiveHighlight`。它的真实阻塞是 `list/ListScaffold.kt` +
  `topbar/DynamicTopAppBar.kt`（`R` + `SearchBar`）+ `topbar/Glass*TopAppBar.kt` 的
  `WindowInsets.statusBarsIgnoringVisibility`（desktop 侧不存在）——**没有一条是平台能力契约**。
- 之后才是 tagrules 的 Screen/VM/Contract 进 `commonMain` 并登记 `cmp`。

## 20. M1-3q 实录（2026-09-11）：`SelectionItemCard` 进 `commonMain`（CMP 资源第三次复用）

§12 表里最后一个「只差资源」的卡片组件落地。它（`SelectionItemCard` /
`SelectionItemCardContent` / `ReorderableSelectionItem`）被 **26 个文件**引用，
因为包名不变（`io.legado.app.ui.widget.components.card` 是共享命名空间），
消费方 import **一行都没动**。

### 拆开后的两条阻塞其实都不算阻塞

| 原判 | 复查结论 |
| --- | --- |
| `R.string.edit` / `R.string.more_menu` | **纯资源问题** ⇒ 走 M1-3j 的 CMP 资源配方。`more_menu` 已由 M1-3m 补过，本片只补 `edit` |
| `sh.calvin.reorderable` | **不是平台库**：`reorderable-3.1.0.module` 含 `reorderable-jvm` 变体，是 KMP 制品 ⇒ 直接加进 designsystem |
| `AppContainerBackground`（九宫格） | M1-3p 已解除 |

⚠️ 之前的 §12 把 `reorderable` 和「平台库」并列（「coil3 / sh.calvin.reorderable /
kotlinx.collections.immutable」），**那是连带误判**——`coil3` 在 M1-3p 也证实了是 KMP。
判断依据只有一个：**Maven 的 `.module` 里有没有非 Android 变体**，与「是不是图形/拖拽库」无关。

### 补资源要覆盖全部 4 个语言文件

`composeResources/` 下的字符串**不参与 Android 资源合并**，缺哪个语言就哪个语言回落默认
（英文）。`edit` 按 `:core:ui/src/main/res/values*` 的原值逐字搬入：
`Edit` / `编辑`（`zh-rCN`）/ `編輯`（`zh-rHK`）/ `編輯`（`zh-rTW`）。
`:core:ui` 侧的同名资源**保留**（`GroupManageBottomSheet` / `InputSettingItem` /
`SliderSettingItem` / `ImportComponents` 仍在用 Android `R`），不删。

### 踩到的坑：trailing lambda 绑不到非末位参数

原实现写的是：

```kotlin
ListItem(modifier = …, supportingContent = …, colors = …) { AppText(title) }
```

`headlineContent` 在 `ListItem` 签名里是**第一个**参数。这种「括号外 lambda」在
**Android 目标上能编译**（Kotlin 把 trailing lambda 解析给它），但在 **desktop 目标上**报
`None of the following candidates is applicable: No value passed for parameter 'headlineContent'`
——KMP 的两个目标对同一段代码的解析不一致。修法是写成**显式命名参数**
`headlineContent = { … }`，两边都成立，且语义本就是它（括号里的是标题，`supportingContent`
另传）。已在代码里留注释警告不要改回 trailing lambda。

### 验证

见「M1-3q」切片（与 §19 同一套：干净重建 + 四门禁 + 全量单测）。

## 21. M1-3q 复核（2026-09-11）：`RuntimeShader` 作为「唯一真契约」是误判

### 结论先行

1. **`RuleListScaffold` 不需要 `RuntimeShader`** —— 原判把「闭包」当成了「直接依赖」。
2. **`RuntimeShader` 有跨平台替代**（两条路，见下），所以即便将来真要搬也**不是无解**。

### 证伪过程

| 环节 | 事实 |
| --- | --- |
| `RuleListScaffold` 对 topbar 的依赖 | **只有一个类型引用** `GlassTopAppBarScrollBehavior`，且只作为 `bottomContent` lambda 的接收者类型**透传**给 `ListScaffold`。它不调用任何 topbar 组件 |
| `InteractiveHighlight` 的 import 方 | 只有 3 个文件：`FloatingBottomBar.kt`、`reader/ReaderMenuGlass.kt`、`topbar/TopBarLiquidGlass.kt` |
| `topbar/TopBarLiquidGlass.kt` 是什么 | **不是组件**，是 `internal fun Modifier.topBarLiquidGlass(shape)`，且带 `if (Build.VERSION.SDK_INT < TIRAMISU) return this` 守卫 |
| 谁调 `Modifier.topBarLiquidGlass` | 只有 `topbar/TopBarButton.kt` |
| topbar 四件套是否引用 `TopBarButton` | **都引用，但 grep 不到**——`TopBarActionsRow` /
  `miuixTopBarSlotPadding` 就定义在 `TopBarButton.kt` 里，**同包调用不需要 import**。
  ⚠️ 本行是复核时写错的（只扫了 import），M1-3r 真搬时才暴露，见 §22 |

⇒ `RuleListScaffold` **自身**的编译闭包不触及 `InteractiveHighlight` / `RuntimeShader`
（它只碰 `GlassTopAppBarScrollBehavior` 一个类型）。但**把 topbar 全家桶搬进 `commonMain`
就会碰到**：`Glass*TopAppBar` 同包调用 `TopBarActionsRow` ⇒ `TopBarButton` ⇒
`topBarLiquidGlass` ⇒ `InteractiveHighlight` ⇒ `RuntimeShader`。这条链在 M1-3r 由
`LiquidGlassEffects` 契约断开，见 §22。
（顺带：`TopBarLiquidGlass(` 作为组件形式的调用**全仓零处**；别处同名出现都是设置项
`menuTopBarLiquidGlass` / `readMenuTopBarLiquidGlassButtons`，与这个 Modifier 无关。）

### `RuleListScaffold` 的真实阻塞（重新列）

1. `list/ListScaffold.kt` + `list/ListUiState`（还在 `:core:ui`）。
2. `topbar/DynamicTopAppBar.kt`：6 条 `R.string.*`（`list_loading_title` / `list_selected_count` /
   `cancel_select` / `back` / `search` / `more_menu`）+ `SearchBar`（还在 `:core:ui`）。
3. `topbar/GlassTopAppBar.kt` 与 `GlassMediumFlexibleTopAppBar.kt` 的
   `WindowInsets.statusBarsIgnoringVisibility` —— **foundation 的 Android-only 成员**
   （`cmp-module-convention.md` 的实测表已标 `❌`），desktop 侧没有。
4. `RuleListScaffold` 自身的 5 条 `R.string.*`（`add` / `delete` / `sure_del` / `ok` / `cancel`）。

⇒ 都是「资源 + 同包兄弟 + 一处 Android-only insets」，**没有一条是平台能力契约**。

### `RuntimeShader` 的跨平台替代（若将来真要搬 `InteractiveHighlight`）

在 CMP 1.12.0 的 `ui-graphics` 三个变体制品里实测确认：

- `expect class Shader` 在 **commonMain**（`commonMain/androidx/compose/ui/graphics/Shader.kt`）。
- `abstract class ShaderBrush : Brush` 在 **commonMain**（`commonMain/.../Brush.kt:829`）。
- `RuntimeShader` 本身 **commonMain 没有**（三个变体的 sources 全量 grep 为空）。
- 但 **skikoMain** 里 `actual class Shader` 直接包 `org.jetbrains.skia.Shader`，并暴露两个
  **公开**扩展：`fun SkShader.asComposeShader(): Shader` 与 `val Shader.skiaShader: SkShader`
  （`skikoMain/androidx/compose/ui/graphics/SkiaShader.skiko.kt`）。

于是有两条路：

| 方案 | 做法 | 代价 |
| --- | --- | --- |
| **A 平台 shader 工厂（行为等价）** | commonMain 契约返回 `Shader`；Android 用 `RuntimeShader(AGSL)`，desktop 用 Skia `RuntimeEffect.makeForShader(SkSL)` 再 `.asComposeShader()` | 需要**两份 shader 源码**（AGSL 的 `layout(color) uniform half4` 在 SkSL 不存在）。契约住 designsystem —— `Shader` 是 Compose 的 `expect class`，与 M1-3o 的 `ClipEntry` 同一判据 |
| **B 原生 `RadialGradientShader` 近似（零契约）** | 本 shader 只做「以 `position` 为心、`radius` 为半径的径向高光，`smoothstep(radius, radius*0.5, dist)`」。等价写法：`RadialGradientShader(center = position, radius = radius, colors = [color, color, transparent], colorStops = [0f, 0.5f, 1f])` | 边界从 smoothstep 的 S 曲线变成**线性插值** ⇒ 像素级不同、视觉接近。属**降级**不是等价，按 AGENTS.md 必须显式建模，不得静默伪装成跨平台支持 |

附带发现：shader 源码里的 `uniform float2 size;` **声明了但 `main` 从未使用**（实际只用了
`position` / `radius` / `color`）——将来真要重写时这个 uniform 可以直接去掉。

### 纪律（这次是旧记录自己犯了）

本文档反复强调「量清调用点前不要先定抽象面」，而 §12 那条
「`RuleListScaffold` 经 `animation/InteractiveHighlight` 卡 `RuntimeShader`」正是**把闭包当直接
依赖**写下的，并据此被当成了「剩下的唯一真平台契约」——对 `RuleListScaffold` **自身**这个
结论仍然成立（它只碰 `GlassTopAppBarScrollBehavior` 一个类型）。

但本节在纠正它时**自己又犯了对称的错**：用「import 里没有 `TopBarButton`」证明「四件套不
引用 `TopBarButton`」，而**同包调用根本不需要 import**。真正的教训是两条：

1. **写进阻塞表之前，先 grep 出 `InteractiveHighlight` 的全部 import 方**——只有 3 个；
2. **判断「A 是否引用 B」不能只看 import**——同包、同文件的调用没有任何 import 痕迹。
   要扫就扫**传递闭包**（`portability-triage.py`），不要只扫一跳。


## 22. M1-3r 实录（2026-09-12）：topbar 全家桶进 `commonMain`，两条平台契约

### 搬了什么

`git mv` 11 个文件进 `:core:designsystem/commonMain`（包名不变 ⇒ 消费方 import 零改动）：

- `widget/components/topbar/`：`DynamicTopAppBar` / `GlassTopAppBar` / `GlassSmallTopAppBar` /
  `GlassMediumFlexibleTopAppBar` / `MiuixScrollBehavior` / `TopBarButton`
- `theme/`：`GlassDefaults` / `HazeStyle` / `hazeStyle/HazeLegado`
- `widget/components/`：`SearchBar`、`text/AnimatedText`

新增两个文件（都是契约）：`topbar/StatusBarInsets.kt`、`topbar/LiquidGlassEffects.kt`。

### 两条契约，以及为什么都住 designsystem

判据一直是「**契约签名里有没有平台/Compose 类型**」（M1-3o 定下的）：

| 契约 | 签名里的类型 | 归属 |
| --- | --- | --- |
| `StatusBarInsets` | `WindowInsets`（Compose foundation） | `:core:designsystem` |
| `LiquidGlassEffects` | `Modifier`（Compose ui） | `:core:designsystem` |

**`LiquidGlassEffects` 是 §21 那条链的真实落点。** 复核时判断「四件套不引用 `TopBarButton`」
只看 import，漏了同包调用（已回改 §21）。真实链条是：

```
Glass*TopAppBar ──同包──> TopBarActionsRow / miuixTopBarSlotPadding   （定义在 TopBarButton.kt）
                 └─> topBarLiquidGlass ─> InteractiveHighlight ─> android.graphics.RuntimeShader
```

所以 `RuntimeShader`（AGSL）**确实**是这条链上唯一的真平台能力，旧记录没判错，错的是
「`RuleListScaffold` 自身被它卡住」这个归因。契约面只留两个能力：

- `enabled()`：这个平台此刻是否支持（Android 还要看 `LocalTopBarBackdrop` 与 SDK ≥ 33）；
- `Modifier.liquidGlass(shape)`。

未注入 ⇒ `enabled() == false`、`liquidGlass` 原样返回 `this`。这不是「静默降级伪装」：
**关闭液态玻璃本来就是原实现的合法状态**（API < 33 时如此），desktop 确实没有这套着色器。

**`StatusBarInsets` 的存在是为了保住 Android 的一处真实行为。** 原实现用 Android 独有的
`WindowInsets.statusBarsIgnoringVisibility`（不管状态栏当前是否可见都返回真实高度），目的是
**顶栏高度恒定**，避免从隐藏了状态栏的界面（阅读器）返回时顶栏内容重排——`GlassTopAppBar`
里的注释写明了这是有意为之。直接换成 commonMain 的 `WindowInsets.statusBars` 会让 Android
出现真实的行为差异（隐藏期间返回 0 ⇒ 顶栏跳动）。所以把「取哪个 inset」下沉为契约：
Android 注入 `statusBarsIgnoringVisibility`（**与迁移前逐字一致**），其它平台不注入、
回落 `statusBars`（desktop 恒为零，语义正确）。

### 踩坑（四条）

1. **CMP 资源访问器是扩展属性，必须逐个 import。** 现象极具误导性：生成的
   `String0.commonMain.kt` 里明明有 `internal val Res.string.back`，源码也
   `import io.legado.app.core.designsystem.res.Res`，编译却报 `Unresolved reference 'back'`。
   真因是 `Res.string` 在 `Res.kt` 里是**空的** `object string`，所有条目都是**扩展属性**，
   而扩展属性不 import 就不进作用域。所以除了 `Res`，还要
   `import io.legado.app.core.designsystem.res.back` 等逐个导入（已有组件就是这么写的）。
   排查手法：写一个探针文件同时引用「老资源名」和「新资源名」——两个都报 unresolved
   ⇒ 不是「生成没刷新」，是根本没进作用域。
2. **`@Composable` 方法不能靠 SAM 生成。** `fun interface StatusBarInsets { @Composable fun get() }`
   配 `StatusBarInsets { WindowInsets.statusBars }` 编译不过；两侧都改显式 `object`。
   （`MimeTypeResolver` 那种非 composable 的 `fun interface` 仍可 SAM。）
3. **`statusBars` / `statusBarsIgnoringVisibility` 两边都是 `@ExperimentalLayoutApi`，
   且 getter 都是 `@Composable`**（Android 走 `WindowInsetsHolder.current()`、skiko 走
   `LocalPlatformWindowInsets.current`）。所以契约方法必须是 `@Composable`，两侧都要 opt-in。
4. **`HazeStyle.kt` 的 `@OptIn(ExperimentalHazeMaterialsApi::class)` 是空 opt-in。**
   它只调 haze-core 的 `hazeSource` / `hazeEffect` / `HazeProgressive`，没有任何 materials API
   （materials 的真实用户 `reader/ReaderMenuEffects.kt` 仍留在 `:core:ui`）。一开始为此加了
   `libs.haze.materials` 依赖，删掉那个注解后**本切片零新增依赖**。

### 验证

- 干净重建（`./gradlew clean`）后四门禁全绿，`:core:designsystem:compileKotlinDesktop`
  （desktop 目标是判据）与 `:app:compileAppDebugKotlin`、`:app:assembleAppDebug` 全过。
- 新增 `TopBarPlatformContractsTest`（4 例，`desktopTest`）锁两个 Provider 的宿主语义：
  未注入不抛异常且**回落是稳定实例**、install/uninstall 可往返。
  设计system 33 → 37 ⇒ 主验证集 **702 → 706**、全量 **862 → 866**。
  ⚠️ 两条契约的方法全是 `@Composable`，**取值行为在 `commonTest` 里无法断言**
  （没有 Compose 测试运行时），只能锁宿主语义——已在测试文件的 KDoc 里写明这个边界。
  变异验证：把 `FALLBACK` / `DISABLED` 改成每次新建的 `get()` ⇒ 4 例**全部**变红。
- 补进 4 个语言文件的 7 条文案（`back` / `cancel_select` / `list_loading_title` /
  `list_selected_count` / `search` / `search_placeholder` / `more_menu`）已与
  `app/src/main/res/values*/strings.xml` 的原值**逐个比对一致**（含 `…` 与 `%1$d/%2$d`）。

### 下一步（M1-3s）—— 已完成，实录见 §23

`RuleListScaffold` 剩下的阻塞只剩「资源 + 同包兄弟」：

1. `list/ListScaffold.kt` + `list/ListUiState`（还在 `:core:ui`）；
2. `RuleListScaffold` 自身的 5 条 `R.string.*`（`add` / `delete` / `sure_del` / `ok` / `cancel`）。

topbar 那条链已经通了，`DynamicTopAppBar` 的 6 条 `R.string.*` 与 `statusBarsIgnoringVisibility`
都已解决。

## 23. M1-3s 实录（2026-09-12）：`AppScaffold` / `ListScaffold` / `RuleListScaffold` 进 `commonMain`

### 台账漏了最大的那个文件

§22 的「下一步」写的是「只剩 `list/ListScaffold.kt` + `list/ListUiState` 与 5 条 `R.string.*`」。
实际跑闭包扫描后才发现：**`ListScaffold` 明文 import 了 `:core:ui` 的 `AppScaffold`**（跨包 import，
不是同包兄弟），而 `:core:designsystem` 不可能反向依赖 `:core:ui` ⇒ `AppScaffold` 必须先搬。
它是本片消费方最多的文件（**58 个文件 / 114 处引用**），但**包名不变 ⇒ 调用点一行没动**。

（教训与 §21/§22 同源：**判据必须是闭包扫描，不是读台账**。台账是人写的，闭包是机器算的。）

顺带纠正两处过期记录：`list/ListUiState.kt` 其实早已在 `commonMain`；`:core:ui` 的 `rules/`
只剩 `RuleEditSheet.kt`，它不引用 `RuleListScaffold`。

### 搬了什么（3 个文件）

| 文件 | 处理 |
| --- | --- |
| `widget/components/AppScaffold.kt` | 只断开 `koinInject<ImageLoader>()` |
| `widget/components/list/ListScaffold.kt` | `R.string.add` → `Res.string.add` |
| `widget/components/rules/RuleListScaffold.kt` | 5 条 `R.string.*` → `Res.string.*` |

闭包扫描（`portability-triage.py`，55 个文件）里**除三个种子自身外全部 `ok`**：两个
CompositionLocal（`LocalHazeState` / `LocalTopBarBackdrop`）与 `responsiveHazeSource` 早由
M1-3d / M1-3r 落在共享层，`hasBackgroundImage` 在 `:core:model`——**没有新契约，也没有新模块依赖**。

### `AppScaffold` 的 Koin 依赖：删掉，而不是加依赖

`BackgroundImageContent` 有两处 `AsyncImage(imageLoader = org.koin.compose.koinInject())`。
可以给 designsystem 加 `libs.koin.compose`，但 **M1-3p 已经验证过不需要**：`App` 实现
`coil3.SingletonImageLoader.Factory`、`newImageLoader(context)` 就是 `get()`（返回 Koin 里那个单例），
省略该参数后 `SingletonImageLoader` 给到的是**同一个实例**，crossfade / Decoder /
`CoverInterceptor` 配置一字不差。所以删参数、不加依赖——与 `AppContainerBackground` 同一配方。

### 维度 3 实测（本片的风险全在这里）

`AppScaffold` 是唯一同时用到 miuix 壳、kyant backdrop、Coil 与 material3 的文件，四者都得在
非 Android 源集里存在：

| 符号 | 载体 | 结论 |
| --- | --- | --- |
| `MiuixScaffold` / `FabPosition` | `miuix-ui-desktop-0.9.3.jar` | ✓ 类存在 |
| `layerBackdrop` / `rememberLayerBackdrop` / `rememberCombinedBackdrop` | `backdrop-desktop.jar`（`backdrops/LayerBackdropKt`、`CombinedBackdropKt`） | ✓ |
| `AsyncImage`（去掉 `imageLoader` 后） | `coil-compose`（KMP） | ✓ |
| `ScaffoldDefaults` / `contentColorFor` / `animateFloatingActionButton` / `FloatingToolbarDefaults.ScreenOffset` | CMP material3 | ✓ |

判据仍是 `:core:designsystem:compileKotlinDesktop` 实际编译通过，不是读 import。

### 资源：5 条 × 4 语言

`add` / `delete` / `sure_del` / `ok` / `cancel`。**三处（`app`、`:core:ui`、CMP 资源）逐字节一致**，
包括两处容易写错的差异：

- `sure_del` 在 `values-zh-rCN` 是**半角** `是否确认删除?`，在 `values-zh-rHK` / `zh-rTW` 是
  **全角** `是否確認刪除？`；
- `add` 在 `zh-rTW` 是「新增」，`zh-rCN` / `zh-rHK` 是「添加」。

产物级验证照 M1-3r 的配方：解码 `.cvr`（`string|<name>|<base64>`）与源 XML 全等、新 5 条与
`app/src/main/res/values*` 逐字节一致 ⇒ **25 × 4 = 100 条全过**。

⚠️ **坑**：`build/` 下有多份 `.cvr` 副本，**旧 Android 构建的 `assets/` 那份是陈的**（只有 20 条）
只有 `resourceGenerator/preparedResources/` 那份是本次生成的。比对时必须按 mtime 挑，否则会误判成
「资源没生成」。

### 验证

干净重建（`./gradlew clean`，不用 `rm -rf */build`）后：

- 四门禁 + `:core:designsystem:compileKotlinDesktop` + `:app:compileAppDebugKotlin` 全绿；
- `:core:designsystem:desktopTest` 37、`:core:viewmodel:testDebugUnitTest` 19、
  `:feature:tagrules:testDebugUnitTest` 15、`testAppDebugUnitTest` 635 ⇒ **主验证集 706，与 M1-3r
  逐字一致**（本片没动共享层可测语义）；
- 另加 `:core:data:desktopTest` 81、`:core:platform:desktopTest` 79 ⇒ **全量 866 不变**；
- `:app:assembleAppDebug` 通过（顺带刷新 Android assets 里那份陈的 `.cvr`）；
- 注释体检：改动过的 3 个 `.kt` + `core/designsystem/src/commonMain` 递归 95 个文件全净。

### 下一步

`RuleListScaffold` 的阻塞已清零。剩下的是把 **`feature:tagrules` 的 Screen / VM / Contract
（含两个 `*EditSheet`）搬进 `commonMain`**，并把该模块登记为 `cmp`。

⚠️ 开工后对 7 个文件做闭包扫描，发现这句「剩下的是」低估了：真正的阻塞在它们消费的
**:core:ui 组件**上，而 `:core:ui` 是 Android library，CMP 的 `commonMain` 不能反向依赖它。
本回合先啃最容易的第 1 组，实录见 §24。

## 24. M1-3t 实录（2026-09-12）：导入/设置组件闭包进 `commonMain`，`MiuixPreferenceRenderer` 新契约

### 起点：把「tagrules 最后一段」拆开

§23 收尾时写的是「剩下 7 个文件搬进 commonMain」。真开工前对这 7 个文件逐个符号定位，
拓扑依赖它们的 **4 组前置**（`:core:ui` 组件 / `:core:viewmodel` 符号 / 平台 PATH picker /
fast scroller），只有第 1 组是本回合啃的：

| 组 | 内容 | 状态 |
| --- | --- | --- |
| 1 | `ImportComponents.kt` 及其传递闭包（都在 `:core:ui`） | **本回合，见下** |
| 2 | `:core:viewmodel` 的 `core/rules` 5 文件（本身零平台 import，只是住错模块） | 待办 |
| 3 | `AndroidDocumentPicker.kt`（`androidx.activity` ActivityResult） | ~~待办~~ **M1-3v 复核：不是前置**——只在 Route 里调用，Route 本就该留 `androidMain`（见 §25） |
| 4 | `LazyList.kt` 的 `FastScrollLazyColumn`（`VerticalFastScroller` 的 `systemGestureExclusion`） | ~~已知硬阻塞~~ **M1-3v 已解**（expect/actual 原语，见 §25） |

第 2 组顺手量化过了：`:core:viewmodel` 只有 11 个生产文件 / 1029 行，**仅 `base/` 两个文件碰
android**（`BaseViewModel` / `BaseRuleViewModel`），其余（`core/rules` 5 个、`help/coroutine`
4 个、`core/viewmodel/DebugFlags`）都已 KMP-clean ⇒ 未来 KMP 化这条路很干净。

### 搬了什么（5 个文件 / 858 行）

| 文件 | 行数（迁入后） | 处理 |
| --- | --- | --- |
| `importComponents/ImportComponents.kt` | 419 | `LocalConfiguration` → `LocalWindowInfo`；`R.string.*` 14 条 → CMP 资源 |
| `widget/components/SplicedColumnGroup.kt` | 161 | 原样搬 |
| `card/SettingCard.kt` | 52 | 删一条空 material2 opt-in |
| `settingItem/SettingItem.kt` | 197 | `ListItem` trailing lambda → 显式 `headlineContent` |
| `settingItem/SwitchSettingItem.kt` | 62 | Miuix 分支改走新契约 |

包名沿用 `io.legado.app.ui.widget.components.**` ⇒ **消费方 import 零改动**。

### 闭包必须按「标识符」扫，不能按 import

第一次按 import 扫只算出 1 个文件，改正则后 29 个，再排除误命中（`VerticalFastScroller.kt`
的顶层声明在待搬文件里一处都没出现）。最终 858 行 / 5 文件是**标识符级扫描**（剥注释后
tokenize 再取顶层声明名）的结果——因为 `SwitchSettingItem` 用到的 `SettingItem` 是**同包
兄弟，没有 import 行**。

（教训与 §21 / §22 / §23 同源，这是第四次：**只有声明名能收敛闭包，import 不能**。以后每轮
直接上标识符扫描，别从 import 起手。）

### 唯一的新依赖点是 miuix 里唯一没有 desktop 变体的制品

`SwitchSettingItem` 的 Miuix 分支原先直接调 `top.yukonga.miuix.kmp.preference.SwitchPreference`。
翻 Gradle 缓存里全部 24 个 miuix jar 实测：`miuix-ui` / `miuix-core` / `miuix-icons` /
`miuix-shader` / `miuix-squircle` 都有 `*-desktop` 对位，**只有 preference 是孤零零的
`miuix-preference-android`**；解 `miuix-ui-desktop` 验证过它连 `preference/` 包都没有。
⇒ 整条依赖走不了编译，必须抽契约。

新契约 `MiuixPreferenceRenderer` 住 `:core:designsystem`（签名里没有平台类型，但它是替 miuix
`preference` 渲染留的 seam，跟使用它的组件放在一起更好读）；Android 实现 `AndroidMiuixPreferenceRenderer`
留在 `:core:ui`（那里才看得见 `miuix-preference-android`），在 `PlatformServices.install()`
注入。

**失败语义是本次最需要论证的一点**：`current` 返回 **nullable**，未注入时给 `null`。判据是
「那条分支在其它平台能不能走到」——`LocalComposeEngine` 默认值就是 Material3，而 Miuix 引擎
只由 Android 侧 `:core:ui` 那一处提供 ⇒ **desktop 上 Miuix 分支不可达**。所以既不用「空实现」
糊过去（那等于在根本没有该制品的平台上假装支持），也不用「缺失即抛」把不可达路径武装成
地雷：调用方 `if (renderer != null && isMiuixEngine(engine))` 落到原本就存在的 Material3 路径。
与 M1-3r 的 `LiquidGlassEffects` 同一类判据——**先证明分支不可达，再定契约面**。

### 零新增依赖

`SettingCard.kt` 带着 `@OptIn(ExperimentalMaterial3Api::class)`（`androidx.compose.material`，
material2）。核验后是**空 opt-in**：文件只用 material3 / foundation / miuix / 同包 `GlassCard`。
删掉 ⇒ 不需要 `libs.compose.material` ⇒ 本切片除已有的 miuix-ui / material3 / CMP 资源外
**零新依赖**（同 M1-3r 删 haze materials 空 opt-in 的配方）。

### `LocalConfiguration` → `LocalWindowInfo`（3 处）

原 `Modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.8f)` 三处。
`LocalConfiguration` 在 CMP 的 desktop 里不存在 ⇒ 换 `LocalWindowInfo.containerSize`（像素）
+ `LocalDensity` 转 dp，抽成私有助手 `maxWindowHeightDp()`（配方同 M1-3p 的
`AppContainerBackground`）。

⚠️ 行为差异写在 KDoc 里：**窗口尚未完成布局时 `containerSize` 可能是 0**，那一帧会把内容压成零
高；布局后立刻重组为正确值。Android 的 `screenHeightDp` 来自 Configuration，初值即正确
⇒ 差异只在首帧。这是显式记录的行为差异，不是静默变化。

### 资源：12 条 × 4 语言（另复用 2 条）

新增到 `core/designsystem/src/commonMain/composeResources/values*/strings.xml`：
`select_count` / `loading` / `import_status_update` / `import_status_new` /
`import_status_existing` / `import_status_error` / `import_action` / `history_label` /
`error` / `edit_not_supported` / `details` / `deselect_all`。值**逐字节取自**
`app/src/main/res/values*/strings.xml`，含几处容易写错的差异：`历史记录:`（zh-rCN 半角冒号）
vs `歷史：`/`歷史記錄：`、`載入中…` vs `加載中…`、`全不选` vs `取消全選`。
另有 `ok` / `cancel` 原先读的是 **`android.R.string`**，一并改读 CMP 资源（CMP 资源里早已有）。

产物级核对（同 M1-3s 的配方）：4 语言各 **37 条**，桌面 `processedResources` 与
`preparedResources/commonMain` 两份产物一致，且**与源 XML 逐字节相等**；新增 12 条 × 4 =
**48 项与 `app` 原值逐字节一致**（`37 × 4` = 148 条与源一致，异常 0）。

⚠️ 解析 `.cvr` 的两个小坑：首行是 `version:0` 头，且**有些条目的 base64 省略了 padding**
（`TW92ZSBkb3du`）。直接 `line.split("|")` 会 `ValueError`，pad 到 4 的倍数即可。

### G4 基线：`ImportJsonEditorProvider` 跟着文件换区域

`ImportComponents.kt` 里有全仓**唯一**的 `ImportJsonEditorProvider.current` 使用点（2 计数）。
文件搬进 designsystem 后 G4 报两行：旧 key 归零要求下调、新区域要求为零。评估了两条路：

- 改成入参：会把这 1 处单点债务摊到 8 个调用方，**总量更大**，且与棘轮「只降不升」的方向相悖；
- designsystem 基线一向是 0，为一条马上要还的债开第一条例外不划算。

⇒ 走门禁允许的「经评审后显式登记」：**删除归零的旧 key、新增同计数的新 key**（计数不变，只是换
区域），并在 `legacy-baseline.txt` 里写明这是路径迁移而非新增债、以及「清 `ImportJsonEditor`
静态 Provider 属 Backlog 第 8 项，届时本条应连带归零删除」。

### 验证

干净重建（`./gradlew clean`）后：

- 四门禁（`checkSharedPurity` / `checkModuleDependencies` / `verifyConfigArchitecture` /
  `checkLegacyArchitecture`）+ `:core:designsystem:compileKotlinDesktop` +
  `:app:compileAppDebugKotlin` 全绿；
- `:app:assembleAppDebug` 通过；
- 用例数：`:core:designsystem:desktopTest` **37 → 39**（新增
  `MiuixPreferenceRendererContractTest` 2 例）、`testAppDebugUnitTest` 635、
  `:core:viewmodel:testDebugUnitTest` 19、`:feature:tagrules:testDebugUnitTest` 15
  ⇒ **主验证集 706 → 708**；加 `:core:data:desktopTest` 81、`:core:platform:desktopTest` 79
  ⇒ **全量 866 → 868**（有意变更，见下）；
- 资源产物核对 **148/148 与源一致**、**48/48 与 `app` 逐字节一致**。

**变异验证**（有意变更必须做）：把 `current` 改成「未注入返回空实现对象」+「每次访问新建一层包装」
两个变异各上一次 ⇒ 用例 `absentRendererExposesNullSoCallerFallsBackToMaterial3`（第 38 行断言）
与 `installAndUninstallRoundTripKeepsIdentity`（第 50 行 `assertSame`）**双双转红**，恢复后复绿。
这条正好锁住了「降级必须显式、不能静默」这个刻意选择的失败语义。

### 本回合两个新坑

1. **脚本插函数插进了注解归属**：往 `fun SourceInputDialog(` 前插私有助手时，锚点切在了原本
   属于它的 `@Composable` 之后 ⇒ 助手拿到两个 `@Composable`（`This annotation is not
   repeatable`），而 `SourceInputDialog` 丢了自己的 ⇒ 连锁报
   `Functions which invoke @Composable functions must be marked with the @Composable annotation`。
   **机器改写务必插入后复读该区域**，尤其是「注释/注解 → 函数」的边界。
2. **`ListItem` trailing lambda 第二次踩了同一个坑**：`ListItem(...) { AppText(title) }` 的
   `headlineContent` 是首参——**M1-3q 在计划里已经记过这一条**（plan 第 707 行），本回合
   `SettingItem.kt` 又撞了一次（desktop 报 `None of the following candidates is applicable`
   + `Argument type mismatch: actual type is '() -> Unit', but 'Dp' was expected`）。
   建议以后每个切片收尾直接 grep 一遍 `^\s*ListItem\(` 检查尾巴上有没有 trailing lambda。

### 下一步

第 1 组清零。**剩下 3 组仍是 `tagrules` 本体（7 文件）的前置**：`:core:viewmodel` 的
`core/rules` 5 文件（最干净，先做）、`AndroidDocumentPicker`、
`FastScrollLazyColumn`（已知硬阻塞，需单独决策）。

> **2026-09-13 M1-3v 追记**：这 3 组至此全部处置完——第 2 组由 M1-3u 完成；第 3 组复核后
> 判为「**不是前置**」（Route 留 `androidMain` 即可）；第 4 组由 M1-3v 用 expect/actual
> 原语解除。`tagrules` 本体 7 文件的依赖面已清空，只剩模块转制本身的机械量。见 **§25**。

## 25. M1-3v 实录（2026-09-13）：`lazylist/*` 进 `commonMain`，`systemGestureExclusion` 下沉为平台原语

### 动机：`tagrules` 本体的依赖面重新核算（不是凭台账）

§24 列了 4 组前置。M1-3u 清掉第 2 组后，本回合先做一次**逐符号定位**，结论与台账有出入：

| 组 | 台账说法 | 实测 |
| --- | --- | --- |
| 1 | `:core:ui` 组件闭包 | ✅ M1-3t 已清 |
| 2 | `:core:viewmodel` 的 `core/rules` | ✅ M1-3u 已清 |
| 3 | `AndroidDocumentPicker` | ❌ **不是前置**——`rememberDocumentPicker()` 只在 `HighlightTagRuleRouteScreen` 里调用，而 Route 本来就该留 `androidMain`（它还要用 Android-only 的 `org.koin.androidx.compose.koinViewModel`）。纯 Screen 只收 `onPickImportSource` / `onPickExportTarget` 两个回调 |
| 4 | `FastScrollLazyColumn` | ✅ **已解，本切片** |

手法是对 7 个文件的 45 个 `io.legado.*` 符号逐个建「符号 → 定义文件 → 源集」映射。
⚠️ `portability-triage.py` 的 `ROOTS` 原本只含 `core/ui/src/main` 与
`core/designsystem/src/commonMain` ⇒ **`feature/*` 的种子直接报「找不到」**
（旧笔记里 `tools/portability-triage.py` 的路径也写错了，真实位置是
`.agents/skills/legado-kmp-migration/scripts/`）。**本切片顺手补上了**：新增
`_discover_roots()` 按 glob 自动发现全仓生产代码根，并刻意排除 `app/src`、`build-logic`、
`androidMain`/`desktopMain`（住着共享契约的 Android actual，带 `java.*`，会把「用的是
commonMain 的 expect」误报成种子搬不动）与各 `*Test`。补完后脚本**本身**给出了同一结论，
比手工映射更硬：

```
  可搬   group/TagGroupRuleContract.kt
  阻塞   group/TagGroupRuleEditSheet.kt          ← [compose-android-only:stringResource]
  可搬   group/TagGroupRuleViewModel.kt
  可搬   highlight/HighlightTagRuleContract.kt
  阻塞   highlight/HighlightTagRuleEditSheet.kt  ← [compose-android-only:stringResource]
  阻塞   highlight/HighlightTagRuleScreen.kt     ← 自身 + 上面那个 EditSheet
  可搬   highlight/HighlightTagRuleViewModel.kt
```

⇒ **7 个文件里 5 个纯可搬；剩下 2 个的阻塞项只有 `androidx.compose.ui.res.stringResource`
一个**（CMP 有同名替代 `org.jetbrains.compose.resources.stringResource`）。
回归验证脚本改动无副作用：`core/ui/.../InteractiveHighlight.kt` 仍正确报
`android.graphics.RuntimeShader`，已搬的 `lazylist/*` 报可搬。

结果：除 `rememberDocumentPicker` 与 `FastScrollLazyColumn` 外，**其余全部已在 `commonMain`**
——designsystem 组件 17 个、`:core:data` 实体与仓储、`:core:platform` 三契约、
`:core:model` 的 `isJson*`、`sh.calvin.reorderable`。**"还差很多组件"是台账给人的错觉。**

### 唯一的真阻塞：`Modifier.systemGestureExclusion()`

`lazylist/VerticalFastScroller.kt`（525 行，`SubcomposeLayout` + `draggable` 手写快速滚动条）
里有两处 `Modifier.systemGestureExclusion()`。判定不靠"看起来像平台库"，靠解制品：

```
unzip -l org.jetbrains.compose.foundation:foundation-desktop:1.12.0.jar | grep -i exclu
  → （空）
```

即**整个 desktop 制品没有这个符号**（Android 侧最终转发 `View.setSystemGestureExclusionRects`）。
全仓也只有这 2 个调用点（`grep -rn systemGestureExclusion --include=*.kt` 只命中本文件）。

### 契约形态：expect/actual，而不是 Provider 注入

⚠️ 本切片唯一需要判断力的一步，判据**不是"沿用上次的写法"**：

| | `StatusBarInsets` / `LiquidGlassEffects`（M1-3r） | `systemGestureExclusionCompat`（M1-3v） |
| --- | --- | --- |
| 有真实的可替换实现？ | 有（Android 注入具体实现） | 没有第三个实现 |
| 有失败语义？ | 有（未注入 ⇒ 关液态玻璃 / 回落 `statusBars`） | 没有 |
| 能注入？ | 能（`PlatformServices.install()`） | **不能**——零状态的 `Modifier` 工厂不是可注入的值 |

所以用 `expect fun Modifier.systemGestureExclusionCompat(): Modifier`：androidMain 转发原生、
desktopMain 恒等。**恒等不是"静默降级伪装"**——非 Android 平台不存在"系统手势拦截"这个
对手方，不声明排除区就是正确语义。符合 AGENTS.md「`expect/actual` 只用于真正的平台原语」
（仓内先例：`JsonCodec` / `PlatformTime` / `Collator`）。

### 搬了什么

`git mv` 两个文件（包名不变 ⇒ 消费方 import 零改动）：

- `widget/components/lazylist/LazyList.kt` —— `ScrollbarLazyColumn` / `FastScrollLazyColumn` /
  `FastScrollLazyVerticalGrid`
- `widget/components/lazylist/VerticalFastScroller.kt`

消费方 5 个：`app` + `feature/{dict,tagrules,replacerules,txttocrules}`，**全都已声明
`:core:designsystem`**。新增 3 个契约文件 ⇒ `:core:designsystem` **首次出现 `androidMain` /
`desktopMain` 源集**（此前只有 commonMain / commonTest）。**零新增依赖**：`foundation` 由
convention 提供，其 android 变体解析到的就是 AndroidX foundation。

### 验证

- `clean` 后四门禁 + `:core:designsystem:compileKotlinDesktop`（desktop 是判据）+
  `:core:viewmodel:compileKotlinDesktop` + `:app:compileAppDebugKotlin` + 6 个测试任务 +
  `:app:assembleAppDebug` 全绿。
- 用例计数 **708 / 868 与基线逐字不变**（纯搬迁切片，无有意变更）。
- G4 基线未动：`lazylist` 在 `gradle/architecture/legacy-baseline.txt` 里没有 key。
- 一个既有警告未变：`checkDesktopMainComposeLibrariesCompatibility` 报
  `androidx.compose.ui:ui-desktop:1.6.0 \--- org.jetbrains.skiko:skiko:0.7.7 -> 0.150.1`。
  本切片零依赖变更，与该警告无关。

### 为什么没给这条 expect/actual 配测试

M1-3r 给两个 Provider 配了 4 例契约测试，是因为它们有**宿主语义**可锁（未注入不抛异常、
回落是稳定实例、install/uninstall 往返）。这里没有状态、没有注入点、没有失败语义：
desktop 侧唯一可断言的事实是"返回 `this`"——为一句同义反复新建 `desktopTest` 源集、并把
基线从 708 抬到 709，正是不该做的"为架构完整而造的抽象"。**判据是"有没有行为可锁"，
不是"承接前例的仪式"。**

### 下一步（M1-3w）—— 本片即 §26 已执行

`feature:tagrules` 本体转 KMP/CMP，工作已全部变成机械量：

1. 7 个文件拆源集：`commonMain` = 纯 Screen / VM ×2 / Contract ×2 / EditSheet ×2；
   `androidMain` = `HighlightTagRuleRouteScreen`（从 `HighlightTagRuleScreen.kt` 拆出去）；
   `androidHostTest` = `TagRulesImportExportCharacterizationTest`（Robolectric + Room）。
   （脚本判定 5 个纯可搬、2 个只差 `stringResource` ⇒ 与上面的手工核算一致。）
2. 31 条文案 × 4 语言从 `src/main/res/values*/` 搬进 `src/commonMain/composeResources/values*/`，
   `R.string.x` → `Res.string.x`（**每个名字都要单独 import**——M1-3r 的坑）。
   **已核对：31 条文案在模块与 `app` 两侧逐字相同（4 语言 × 31 条全查）**，所以这次搬家
   不改任何用户可见文案——Android 资源合并覆盖的语义由"值本来就一样"兜住。
3. 模块登记 `"feature/tagrules" to "cmp"`。


---

## 26. M1-3w 实录（2026-09-13）：`feature:tagrules` 转 KMP/CMP —— 首个 CMP Feature 模块

§25 已把依赖面清空，本片是执行。**一个切片把 7 个源文件 + 31 条文案 × 4 语言一次过**，
终点是 `:app` 侧 import 零改动、用户可见文案零变化。

### 拆成什么样

| 源集 | 文件 | 说明 |
| --- | --- | --- |
| `commonMain` | `group/{TagGroupRuleContract, TagGroupRuleViewModel, TagGroupRuleEditSheet}`、`highlight/{HighlightTagRuleContract, HighlightTagRuleViewModel, HighlightTagRuleEditSheet, HighlightTagRuleScreen}` | 7 个文件里的 6.5 个 |
| `androidMain` | `highlight/HighlightTagRuleRouteScreen.kt` | Route 从 `HighlightTagRuleScreen.kt` **拆出来**的新文件 |
| `androidHostTest` | `TagRulesImportExportCharacterizationTest.kt` | Robolectric + 内存 Room |
| `composeResources` | `values*/strings.xml` × 4 | 从 `src/main/res/values*/` 搬入 |

Route/Screen 的切分**不是为迁移新造的**：`HighlightTagRuleRouteScreen` 本来就只做两件事——
`koinViewModel()` 取 VM、`rememberDocumentPicker()` 注册 SAF launcher（两者都是 Android-only），
然后把回调交给纯 Screen。过去两个函数同居一个文件，现在按源集分开，仅此而已。

### 依赖侧的四个坑（都不是「读 import」能看出来的）

本片零新增**模块间**依赖，但新增了 4 条外部依赖，每条都踩了一个 KMP DSL / 制品解析的坑：

| 坑 | 现象 | 解法 |
| --- | --- | --- |
| KMP 源集依赖块没有 `platform()` | `implementation(platform(libs.koin.bom))` 报 `Unresolved reference 'platform'`——`sourceSets.*.dependencies {}` 由 `KotlinDependencyHandler` 提供，**不**继承 Gradle 的 `DependencyHandler` | 写 `project.dependencies.platform(libs.koin.bom)` |
| 无版本 alias 在 KMP 模块里断链 | `libs.koin.compose`（`koin-androidx-compose`）在版本目录里**无版本号**（版本一直由 Koin BOM 提供），KMP 的 androidMain 配置下解析成「no version specified」 | 显式引 Koin BOM（同上） |
| 传递依赖解析到本地缓存没有的版本 | `lifecycle-runtime-compose-android` 传递 `androidx.navigationevent:navigationevent-compose` → 解析到 **1.0.2**，本机只有 1.1.2 / 1.2.0-alpha04 | 在 `androidMain` 声明 `libs.androidx.navigationevent.compose`（app 侧本就为「预测式返回崩溃」抬到 1.2.0-alpha04），**版本对齐**而非直接使用 |
| lifecycle 的 KMP 坐标有两套 | `org.jetbrains.androidx.lifecycle:…:2.9.6` 的元数据 `requires androidx.lifecycle:…:2.9.4`，而本机只有 2.11.0 的 `-desktop` jar ⇒ 要联网拉 2.9.4 | 直接用 **`androidx.lifecycle:lifecycle-viewmodel` / `lifecycle-runtime-compose`（2.11.0）**：androidx 自己的 KMP 发布就带 `lifecycle-viewmodel-desktop` / `lifecycle-runtime-compose-desktop`，与 Android 侧同号 |

⚠️ 第 4 条尤其反直觉：**「`androidx.lifecycle` 只有 android 产物」是错的**——它有 `-desktop`
变体，只是按 KMP 惯例以**独立 artifact id**（`lifecycle-viewmodel-desktop`）发布，光看
`lifecycle-viewmodel/2.11.0/` 目录里的 `.aar` 会得出错误结论。判据只能是「解析 desktop 配置」。

### 资源：APK 内实测 124/124 逐字一致

`R.string.x` → `Res.string.x`（`Res` 包名 `io.legado.app.feature.tagrules.res`，由 convention
按模块 path 推导），**每个名字单独 import**（M1-3r 的坑）。改完后 `:app` 侧那 31 条
`io.legado.app.R.string.*` 引用仍全部解析得了——因为 `app/src/main/res/values*/strings.xml`
**自带这 31 条的副本**（Stage B 时就是按「模块只放默认值、app 覆盖」设计的），所以删掉本模块的
Android res 不产生悬空引用。

最强证据不是读源码，是**解 APK**：

```
assets/composeResources/io.legado.app.feature.tagrules.res/values{,-zh-rCN,-zh-rHK,-zh-rTW}/strings.commonMain.cvr
  → 4 个语言目录都在（composeResources **不参与 Android 资源合并**，共享层必须自带全部语言）
  → 解码 .cvr（`version:0
string|<name>|<base64>`）逐条比对 app/src/main/res 同名条目：
     4 × 31 = 124 条，**不一致 0**
```

CMP 资源在 APK 总计 8 个条目（designsystem 的 4 条 + tagrules 的 4 条），说明 convention 里的
`androidResources.enable = true` 确实把 `composeResources/` 打进了 assets。

### ⚠️ 新坑：`clean` 与后续任务写在同一条命令里，配置期失败 ⇒ `clean` 也不执行

本片第一次跑的是 `./gradlew clean :feature:tagrules:compileKotlinDesktop`，**在配置期**
就因为依赖解析失败（当时坐标是 `org.jetbrains.androidx.lifecycle`，要拉 2.9.4）。Gradle 的
执行顺序是「先配置全图，再执行任务」⇒ `clean` 从未运行，但**构建目录里留着上一版
（Android library 形态）的产物**，包括 `build/test-results/testDebugUnitTest/`。

后果是隐蔽的：`tools/count-test-results.py` 按目录读 XML，**读到的是旧任务名的旧结果**，
数字照样显示 708 / 868「与基线一致」——它证明的是「旧结果还在」，不是「新构建跑够了」。

⇒ 规矩：**要 `clean` 就跑单独的 `./gradlew clean`，确认输出里有 `:xxx:clean` 再跑验证**。
已把这条记进 `.workbuddy/memory/topics/env-pitfalls.md`。同时更新了
`count-test-results.py` 的模块→任务映射：`tagrules` / `designsystem` 都改成
**`testAndroidHostTest`**（KMP 模块不再产出 `testDebugUnitTest`；两者跑的是同一批
`commonTest`，改用名字与实际执行的任务对齐）。

### 验证

- **先单独 `./gradlew clean`**（输出确认 `:feature:tagrules:clean` 执行）再跑全量：
  - 四门禁（`checkSharedPurity` / `checkModuleDependencies` / `verifyConfigArchitecture` /
    `checkLegacyArchitecture`）全过——`"feature/tagrules" to "cmp"` 登记生效；
  - `:feature:tagrules:compileKotlinDesktop`（desktop 是「CMP commonMain 只用了有 desktop 变体的
    库」的判据）+ `:core:designsystem:compileKotlinDesktop` + `:core:viewmodel:compileKotlinDesktop`；
  - `:app:compileAppDebugKotlin` + `:app:assembleAppDebug`（`RouteScreen` 在 androidMain，
    `:app` 照旧 import 得到）；
  - 6 个测试任务全绿；`count-test-results.py` **708 / 868 与基线逐字不变**。
- `git diff --check` 与 `git diff --cached --check` 均无输出。
- G4 基线未动（tagrules 不在 `legacy-baseline.txt` 里）。
- 文案等价性：见上「APK 内实测 124/124 逐字一致」。

**本片是纯搬迁切片**：无有意行为变更，故不做变异验证。

### 下一步

`feature:tagrules` 已是 CMP 模块，M1-3 「把干净 UI 页切成 Feature 模块」这条 Stage B 线
在 tagrules 上走完了。接下来同样是机械量：`feature/{replacerules, txttocrules, dict}` 转 CMP
（它们与 M1-3 系列共享同一批 designsystem 组件与 CMP 资源配方），再进 M1-4 的
「最小 Desktop host 主路径」——那才是第一次真正验证「同一 Feature 跨端跑起来」，
本片至今所有 desktop 证据都止于**编译**。

---

## 27. M1-3x 实录（2026-09-13）：`:feature:replacerules` 转 CMP（附前置切片 M1-3x-pre）

§26 的收尾段预判「replacerules 是同样机械的一段」，实际动手后是**两片**：一片上提 UI 资产
（M1-3x-pre），一片转模块（M1-3x）。原因见 §10.1——本片把那条前置判据**撞实了**。

### 起点：`replacerules` 是本批最脏的一个

`tagrules` 转 CMP 时，`commonMain` 里没有一行需要商量。`replacerules` 不是：

| 障碍 | 住在哪 | 处理 |
| --- | --- | --- |
| VM 继承 `BaseRuleViewModel`（吃 `android.app.Application` 的 `AndroidViewModel`） | `:core:viewmodel/androidMain` | 不再继承；导入/导出/上传下沉到 M1-3b 抽好的 `RuleTransferUseCase` |
| `android.net.Uri`（`exportToUri`） | VM 签名 | 契约本来就是 `String`（`RuleTransferPlatform.writeExport(targetUri: String, …)`），迁移前是 `parse` 完又 `toString()` 绕了一圈 |
| `GSON`（`:core:data/androidMain` 的 JVM 门面） | VM 体 | → `:core:platform` 的 `JsonCodec` |
| `ReplaceAnalyzer` 的旧格式分支依赖 `com.jayway.jsonpath`（JVM 三方库） | `io.legado.app.help` | 抽成平台契约 `ReplaceRuleImportCompat`，Android 实现直接委托 `ReplaceAnalyzer` |
| `ClipboardProvider` / `ToasterProvider` 静态委托 | VM 体 / Route | → 注入的 `Clipboard` / `Toaster`（`single<Clipboard>` / `single<Toaster>` 已存在） |

### M1-3x-pre：4 件 UI 资产从 `:core:ui` 上提到 `:core:designsystem/commonMain`

`:core:ui` 是 **Android-only** 模块，`cmp` Feature 的 `commonMain` 看不见它。逐个符号查
**声明文件所在的模块**后，卡住的是 4 件（`AppTabRow` / `GroupManageBottomSheet` /
`rules/RuleEditSheet` / `contentProcess/ContentProcessUiState`）：

- 3 件是 §10.1 的典型误判：包名看着像「基础组件」，声明其实还在 `:core:ui`；
- 第 4 件（`ContentProcessUiState`）是**它自己的依赖面**问题——它用了
  `kotlinx.collections.immutable`，而 `:core:designsystem` 此前**零依赖**
  （首次给它加 `implementation(libs.kotlinx.collections.immutable)`，KDoc 里那句
  「designsystem 已有该依赖」是错的，已改）。

上提纪律与 M1-3i…v 一致：**包名不变**（消费方 import 零改动）、`:core:ui` 侧因搬走而变死的
同名资源一并删（8 条）、文案四语言逐字取自原 Android res（搬前脚本比对
`designsystem == core:ui == app` 三方一致）。`RuleEditSheet` / `GroupManageBottomSheet`
顺带把 `R.string.*` 换成 `Res.string.*`（designsystem 的 CMP 资源包名
`io.legado.app.core.designsystem.res`），新增 10 条文案 × 4 语言。

### 拆成什么样

| 源集 | 文件 |
| --- | --- |
| `commonMain` | `ReplaceRuleContract`、`ReplaceRuleViewModel`、`ReplaceRuleScreen`、`ReplaceRuleImportCompat`、`ReplaceEditRoute`、`edit/{ReplaceEditContract, ReplaceEditViewModel, ReplaceEditScreen}` |
| `androidMain` | `ReplaceRuleRouteScreen`、`edit/ReplaceEditRouteScreen`（两个 Route 都是新拆文件） |
| `androidHostTest` | `ReplaceRuleStateTest`（从 `:app/src/test` **搬进来**） |
| `composeResources` | `values*/strings.xml` × 4（52 条文案，从 `src/main/res/values*/` 搬入） |

两个 Route 必须留 `androidMain` 的理由与 tagrules 同：`koinViewModel()`
（`org.koin.androidx.compose`）+ `rememberDocumentPicker()`（SAF 的 ActivityResult launcher
必须在 Composition 里注册）。Route/Screen 分离是既有设计，本片只是把它们按源集拆开。

`:app` 侧除了 DI 与两个调用方 import，**屏幕 import 零改动**。

### 三处**有意**的收窄（写进 VM 的 KDoc，免得被当成漏搬）

1. **`exportToUri(Uri)` → `transfer.export(String)`**：见上表，本来就是绕路。
2. **`GSON` → `JsonCodec`**：两者配置严格对齐（`MapDeserializerDoubleAsIntFix` +
   `LONG_OR_DOUBLE` + prettyPrinting + disableHtmlEscaping），且 `ReplaceRule` 在 app 侧 `GSON`
   里**没有**注册自定义 deserializer ⇒ 解析/序列化逐字不变。
3. **旧格式导入走平台契约**：共享层先用 `JsonCodec` 解标准格式，解不出（或数组里任一条
   `pattern` 为空）才回落到 `ReplaceRuleImportCompat`。**已知差异只落在畸形输入**，共两条，
   都写进了 KDoc：坏 JSON 的异常文案从 jsonpath 的英文变成中文「格式不正确」（失败语义不变）；
   数组里混进 JSON `null` 时 `JsonCodec.decodeList` 的 `filterNotNull` 会丢掉它而**导入成功**
   （迁移前 jsonpath 抛错 ⇒ 整体失败）——这是**宽松化**，只影响畸形备份。

### G4 棘轮：两条战线，都不是「放宽基线」

转 CMP 后源集从 `main` 变成 `commonMain`/`androidMain`，门禁同时报三种账
（详见 §10.2）：

- **旧账要销**：5 条 `feature/replacerules/main/...`（gson×2、legacyHelp、legacyBase、
  coreProvider）已不存在，删；两个 `legacyHelp` 区域（`help/storage` 21→20、`ui/association` 35→34）
  因调用方少 1 处而下调。
- **新区域必须为零**：`feature/replacerules/androidMain/edit` 首次出现 2 处 `coreProvider`
  ⇒ 把 `ToasterProvider.current` 换成 `koinInject<Toaster>()`（Koin 里 `single<Toaster>`
  与 Provider 路径共用同一组工厂）。
- **搬依赖到干净包名**：`ReplaceAnalyzer` 从 `io.legado.app.help` 搬到
  `io.legado.app.data.rules`（连同 `ReplaceAnalyzerTest`，两个旧调用方 import 同步改），
  `AndroidReplaceRuleImportCompat` 从 `io.legado.app.base.rules` 搬到
  `io.legado.app.domain.gateway` —— 这样新区域不产生 `legacyHelp`/`legacyBase` 欠账。

### 两个新坑（都是编译期才现，静态检查抓不到）

| 坑 | 现象 | 解法 |
| --- | --- | --- |
| `combine` 的类型化重载**最多 5 个流** | 6 参 `combine(a, b, c, d, e, f) { … }` 全部退化成 `(Array<T>) -> R` 的 vararg 重载；6 个流类型互异 ⇒ `T` 推成 `Any` ⇒ transformer 签名对不上，报「期望 `suspend (Array<Any>) -> R`」，并**级联**出一堆 `Unresolved reference`（`bookState.bookUrl` 等）与错误的返回类型推断 | 拆成**两级嵌套** `combine`（5 流 → base，再 `combine(base, bookState)`），与迁移前 `super.uiState` + `_bookState` 的结构**同构**；发射语义等价 |
| `when` 分支用 `data class` 的构造名会**不是分支** | `ReplaceRuleIntent.ToggleImportAll -> …`（漏了 `is`）被当成**伴生对象引用**，不算覆盖该分支 ⇒ 报「`when` 必须穷尽」+「`intent.isSelected` 未解析」 | 写 `is ReplaceRuleIntent.ToggleImportAll -> …`。同一片里 `data class` 分支**全部**要带 `is`，`data object` 才不带 |

⚠️ 第 1 条的教训不止于本仓库：**编译器为「多流合并」报错时，报错点常常离真正的病灶很远**
（类型推导崩塌后，下游全是 `Unresolved reference`）。先怀疑「流个数是否超过重载上限」，
再去看那些下游报错。

顺带修掉一处自己引入的写法错误：`parseImportRules` 里单对象分支漏了 `listOf(...)` 包装，
导致 `when` 的分支公共类型退化成 `Any`（`List<ReplaceRule>` 与 `ReplaceRule` 的共同超类型），
报「返回类型不匹配」。迁移前的实现是 `listOf(ReplaceAnalyzer.jsonToReplaceRule(text).getOrThrow())`，
对照原实现就能发现。

### 资源：APK 内实测 208/208 逐字一致

`R.string.x` → `Res.string.x`（52 条，每个名字单独 import），`composeResources` 四语言齐全。
本片把 tagrules 那次临时写的核对逻辑固化成脚本：

```bash
python tools/verify-compose-resources.py feature/replacerules \
    io.legado.app.feature.replacerules.res --apk app/build/outputs/apk/.../*.apk
```

它解 `assets/composeResources/<ns>/values*/*.cvr`（`version:0` + `string|<name>|<base64>`），
做三件事：① 四个语言目录是否齐全；② 各语言目录的 key 集合是否与默认目录一致（缺翻译是
**静默**的）；③ 与 `app/src/main/res/values*/strings.xml` 同名条目**逐字**比对。
先用 tagrules 验证脚本本身（复现 §26 的 124/124），再对 replacerules 下结论。

### 验证

- **先单独 `./gradlew clean`** 再跑全量：
  - 四门禁（`checkSharedPurity` / `checkModuleDependencies` / `verifyConfigArchitecture` /
    `checkLegacyArchitecture`）全过——`"feature/replacerules" to "cmp"` 登记生效；
  - `:core:designsystem:compileKotlinDesktop`（M1-3x-pre 新加 immutable 依赖后仍可编译）+
    `:core:viewmodel:compileKotlinDesktop` + `:feature:tagrules:compileKotlinDesktop` +
    `:feature:replacerules:compileKotlinDesktop`；
  - `:app:compileAppDebugKotlin` + `:app:assembleAppDebug`；
  - **7 个测试任务**全绿（比 §26 多一个 `:feature:replacerules:testAndroidHostTest`）；
    `count-test-results.py` **708 / 868 与基线逐字不变**。
- `git diff --check` 无输出。
- 文案等价性：见上「APK 内实测 208/208」。

⚠️ **用例数的分布变化（有意）**：`ReplaceRuleStateTest`（2 个用例）从 `:app/src/test`
搬进 `:feature:replacerules/src/androidHostTest` —— Feature 的测试跟着 Feature 走，
与 tagrules 同法。于是 **app 635 → 633、replacerules 0 → 2**，**合计仍是 708**。
`tools/count-test-results.py` 的 `RESULT_DIRS` 已加本模块映射；这是**归属调整，不是用例增减**。
若只盯「合计不变」会漏掉这类搬家，故在此显式记录。

### 下一步

`feature/{txttocrules, dict}` 用同一配方转 CMP（它们卡的是已上提的 `RuleEditSheet`，
即 M1-3x-pre 已经把前置清掉了）。之后进 M1-4「最小 Desktop host 主路径」——至今所有
desktop 证据仍止于**编译**。

## §28 M1-3y：`feature:txttocrules` 转 CMP（2026-09-13）

第三个转 CMP 的 Feature。文件数与 tagrules 相当（3 个源文件），但**平台面比 tagrules 宽**：
它比 tagrules 多一个「导入/导出/内置规则导入」的完整流程，因此踩到几条 §26/§27 没暴露的东西。

### 切片结构

| 源集 | 内容 |
| --- | --- |
| `commonMain` | `TxtTocRuleContract`（未改）+ `TxtRuleScreen`（纯 Screen）+ `TxtTocRuleViewModel`（重写）+ 新增 `TxtTocRuleImportCompat`（平台契约） |
| `androidMain` | `TxtRuleRouteScreen`（新拆 Route） |
| `composeResources` | 26 条文案 × 4 语言（**zh-rHK / zh-rTW 各合法少 3 条**，见下） |
| 测试 | **无**（`TxtTocRuleDeserializerTest` 测的是 `:core:data` 的实体反序列化，住 `app/src/test/.../data/entities`，不随本模块走） |

Route / Screen 的拆法与 `tagrules` / `replacerules` 同形：Route 拿 `koinViewModel()` 与
`rememberDocumentPicker()`，Screen 只收回调。**迁移前 `TxtRuleRouteScreen` 与 `TxtRuleScreen`
本来就同居一个文件，只是过去 Screen 自己内联了 `rememberLauncherForActivityResult` 与
`context.contentResolver.openInputStream`。**

### 与 replacerules 的关键差异：契约面由「实体的 JSON 兼容逻辑在哪」决定

`replacerules` 的平台契约（`ReplaceRuleImportCompat`）只覆盖**旧 jsonpath 格式**——标准 JSON
能在共享层用 `JsonCodec` 直接解（`ReplaceRule` 在 app 侧 `GSON` 里没有自定义 deserializer）。

`txttocrules` **不是这样**：`TxtTocRule.chapterRule` 需要兼容旧版本备份里的键名 `rule`。
原先这靠实体的 `@SerializedName(value = "chapterRule", alternate = ["rule"])`，但那是
`com.google.gson` 的注解（JVM 三方库，进不了 commonMain）；M1 下沉实体时改成 app 侧 `GSON`
门面注册的 `txtTocRuleJsonDeserializer`。而共享层的 `JsonCodec` **不含**任何 rule 类型的自定义
deserializer（`JsonCodec.kt` 的 Android 实现里明说「不含 app 侧 `GSON` 额外注册的 6 个 rule 类型
deserializer」）。

⇒ 结论：**标准 JSON 也必须走平台**。判据不是「输入是不是旧格式」，而是
**「该实体的兼容逻辑是否已被搬成 `GSON` 门面上的 deserializer」**。若答案是「是」，
共享层的 `JsonCodec` 必然不等价，契约就得整体覆盖而不是只兜旧格式。

实现上，解析函数落在 **`core:data/src/androidMain/kotlin/io/legado/app/utils/GsonExtensions.kt`**
（与 `GSON` 门面**同包**，引用它无需 import），契约实现
`AndroidTxtTocRuleImportCompat` 只做转接、自己**不** import `GSON`。

> ⚠️ 这不是风格选择，是 G4 棘轮约束：`gson` 规则以 `^import io\.legado\.app\.utils\.GSON$`
> 锚定并做**目录级**计数。往任何「尚未登记 gson 计数」的新目录（如 `core/data/androidMain/
> .../data/entities`，或 `app/.../domain/gateway`）加一处 `GSON` import，都会产生
> 「新区域首次出现」而直接失败；而**在已 import 它的文件里加函数不涨计数**（同一文件
> 只会匹配一次 import 行）。

### VM 内文案：`context.getString` 不能改成字面量

迁移前 VM 有三处 `context.getString(R.string.x)`（`import_built_in_rules` / `clipboard_empty` /
`invalid_format`）。这几条是**本地化**的（四语言都有值），若为了「共享层没有 Context」而写成
中文字面量，非简体用户会看到中文——属**可见回退**。

解法：用 CMP 的 `org.jetbrains.compose.resources.getString(Res.string.x)`。它是 **suspend**，
所以：
- 已在协程里的（`importBuiltInRules` 的 `viewModelScope.launch(Dispatchers.IO)`）直接调；
- 同步函数里的（`pasteRule(): TxtTocRule?`）抽了个 `emitMessage(res)` 私有辅助，在
  `viewModelScope.launch { _effects.emit(ShowMessage(getString(res))) }` 里解析后再发。
  用户可见时机不变（`tryEmit` 与 `emit` 在这里都是 fire-and-forget）。

对照：`replacerules` 里唯一的 VM 文案「成功导入 N 条规则」是**新造**的（原由基类
`_eventChannel` 发出），所以那里写字面量没有回退问题。**判据是「这条文案原来是本地化的吗」**，
不是「VM 里能不能用 Res」。

### Screen 内联 toast：加 `onShowToast` 回调

`RuleEditSheet` 的 `onSave` 里有三条校验（name 空 / chapterRule 空 / 正则非法），
迁移前直接 `ToasterProvider.current.toast(...)`。`ToasterProvider` 是 G4 `coreProvider` 盯的
静态委托，shared 层不能用；而 `RuleEditSheet` 在 `:core:designsystem`（Public API，不能为
这一个 Feature 改签名）。

解法：`TxtRuleScreen` 增加 `onShowToast: (String) -> Unit` 参数，由 Android Route 传
`{ msg -> koinInject<Toaster>().toast(msg) }`。**toast 仍是 toast**（不是 snackbar）——
`TxtTocRuleEffect.ShowMessage` 那条走 `snackbarHostState`，与迁移前一致，两者没混。

### `BaseRuleEvent` → `RuleTransferEvent`

迁移前 Screen 的 `events: Flow<BaseRuleEvent>` 来自 `BaseRuleViewModel._eventChannel`。
`io.legado.app.base.**` 是 G4 `legacyBase` 盯的命名空间，搬进 `commonMain` 会是
**新区域首次出现** ⇒ 直接失败。

解法：改用 `RuleTransferUseCase.events`（`Flow<RuleTransferEvent>`，`io.legado.app.core.rules`）。
`RuleTransferEvent.ShowSnackbar` 的字段（`message` / `actionLabel` / `url`）与 `BaseRuleEvent`
**逐字相同**，`when` 分支只换类型名。这也正是 `tagrules` / `replacerules` 的既有形态——
它们的基线里**没有**任何条目（全清零），就是靠这一步。

### 文案等价性：`zh-rHK` / `zh-rTW` 合法地少 3 条

本模块 26 条文案里，`chapter_rule` / `import_built_in_rules` / `volume_rule` 在
**模块 res 与 `:app` res 的 `zh-rHK` / `zh-rTW` 里都缺**（逐条比对确认过；只有 `values` 与
`zh-rCN` 齐全）。Android 资源合并时这两条会回落到默认 `values`；CMP 同样按 qualifier 回落
⇒ **原样保留缺失**才是等价迁移，补齐反而会让港台用户从英文变中文（可见变更）。

这暴露了核对脚本 `tools/verify-compose-resources.py` 第 ② 项判据的缺陷：它原先要求
「各语言 key 集合彼此一致」，对这条会误报。已改为 **「与 `:app` 对应语言的 key 集合一致」**
（基准 = 本模块全部 key ∩ `:app` 该语言实际有的 key）。`txttocrules` 98/98、
`tagrules` 124/124、`replacerules` 208/208、`designsystem` 188/188 四个模块回归通过。

### 一个编译期小坑

`JsonCodec.fromJsonObject(json, KClass)` 返回的是**可空值 `T?`**（不是
`GSON.fromJsonObject<T>()` 的 `Result<T>`）。照抄 replacerules 的 `.getOrThrow()` 会报
「Cannot infer type for type parameter 'T'」+「Null cannot be a value of a non-null type」。
正确写法是 `?: throw Exception("格式不正确")`（失败语义与原来的 `Result.getOrThrow()` 等价）。

### 验证

- **先单独 `./gradlew clean`** 再跑全量：四门禁 + 5 个 desktop 编译器
  （designsystem / viewmodel / tagrules / replacerules / **txttocrules**）+
  `:app:compileAppDebugKotlin` + `:app:assembleAppDebug` + 7 个测试任务。
- `tools/count-test-results.py`：**708 / 868 与基线逐字不变**（本模块无测试，不涉及归属调整）。
- 文案等价性：`98/98`（26+26+23+23）与 `:app` 同名条目逐字一致；四模块回归全绿。
- `git diff --check` 无输出。

### 下一步

`feature/dict` 转 CMP（同配方；查询面板是 platform island，需先勘边界），
然后 M1-4「最小 Desktop host 主路径」。

---

## §29 M1-3z（2026-09-13）：`feature:dict` 转 KMP/CMP —— 本批最干净的一个

第四个也是最后一个转 CMP 的 Feature。勘察结论比预估好：**「查询面板是 platform island」这条
担心的点根本不在模块里**——`feature/dict` 只装 `rule/` 子域（`DictRuleScreen`/`Contract`/
`ViewModel` 三个文件），查询弹窗（`DictActivity`/`DictSheet`/`DictViewModel`）本来就在 `:app`，
不在本模块。所以本片是纯机械切片，没有 island 边界要勘。

### 切片

| 源集 | 内容 |
| --- | --- |
| `commonMain` | `DictRuleContract`（未改）+ 纯 `DictRuleScreen` + 重写的 `DictRuleViewModel` |
| `androidMain` | 新拆 `DictRuleRouteScreen`（`koinViewModel()` + `rememberDocumentPicker()`） |
| `composeResources` | 19 条文案 ×4 语言 |

根登记 `"feature/dict" to "cmp"`；G4 删 3 条旧账（`gson` 1 / `legacyBase` 2 / `coreProvider` 3），
新区域零欠账，**未放宽基线**。本模块无模块级测试。

### 唯一需要判断的问题：要不要平台契约？—— 不要

前两片各自加了一个 `ImportCompat` 平台契约，判据不同：

- `replacerules` 要：旧格式靠 `jsonpath`（JVM 三方库）。
- `txttocrules` 要：标准 JSON 也解不了 —— `TxtTocRule` 的旧键名兼容已从
  `@SerializedName(alternate=)` 搬成 **app 侧 `GSON` 门面注册的 deserializer**，共享层看不见。
- `dict` **不要**：`GSON` 门面一共只注册 7 个自定义 deserializer
  （`Explore`/`Search`/`BookInfo`/`Toc`/`Content`/`Review`/`TxtTocRule`），**不含 `DictRule`**；
  实体本身也没有 `@SerializedName(alternate=)`。而 `JsonCodec` 的 GSON 配置与 `INITIAL_GSON`
  **逐字一致**（`MapDeserializerDoubleAsIntFix` + `LONG_OR_DOUBLE` + prettyPrinting +
  disableHtmlEscaping）⇒ 对 `DictRule` 完全等价，直接换即可。

⇒ 判据被这三片收敛成一句话：**看「实体的兼容逻辑是否落在共享层看不见的地方」**
（门面 deserializer / JVM-only 库），而不是看「有没有旧格式」。

### 行为保持的三处

1. **VM 不再继承 `BaseRuleViewModel`**（吃 `android.app.Application`）：导入/导出/上传下沉
   `RuleTransferUseCase`，列表/搜索/选择留在 VM。**`uiState` 仍用 5 流 `combine`**
   —— 与父类逐字一致，`groupFilter` 那一路本 VM 从不写，直接不带。
2. **VM 剪贴板注入化**：`ClipboardProvider.current`（静态 Provider）→ 构造注入
   `Clipboard`（`:core:platform`）。这也是那 3 条 `coreProvider` 清零的原因。
   ⚠️ **别误伤 Screen 里的 `LocalClipboard.current`**：那是
   `androidx.compose.ui.platform` 的 **CMP 共享 API**（不是 Provider），迁移前就是它，
   `replacerules` 的 Screen 同样是这个用法 ⇒ 保留不动。判据是**有没有 `Provider` 后缀**。
3. **`system.currentTimeMillis()` → `systemTimeMillis()`**。

`BaseRuleEvent` → `RuleTransferEvent`（同 §28 的理由：`legacyBase` 盯 `io.legado.app.base.**`）。

### 本片的新坑：CMP 资源包名不含子包

`feature/dict` 的源集目录是 `.../feature/dict/rule/`，我按 Kotlin 包名推成
`io.legado.app.feature.dict.rule.res`，编译报 `Unresolved reference 'Res'`。
真相在 convention：`legado.kmp.compose` 的资源包名 = **模块 Gradle path + `.res`**
（`io.legado.app.feature.dict.res`），**不带 `rule`**——因为资源在
`commonMain/composeResources/` 根下，不在 `rule/` 子目录里。
Kotlin 包目录与资源包名是两套东西，别互相推。

### 验证

- 单独 `./gradlew clean` 后跑全量：四门禁 + **6 个** desktop 编译器（新增 `:feature:dict`）
  + `:app:compileAppDebugKotlin` + `:app:assembleAppDebug` + 7 个测试任务。
- `tools/count-test-results.py`：**708 / 868 与基线逐字不变**（本模块无测试，无归属调整）。
- 文案等价性：`76/76`（19×4）与 `:app` 同名条目逐字一致；解 APK 复核四语言齐全。
- `git diff --check` 无输出。

### 下一步

四个 Feature 全部 CMP 化完毕（`tagrules` / `replacerules` / `txttocrules` / `dict`），
M1-3 这条线走完。进 **M1-4「最小 Desktop host 主路径」** —— 那是第一次真正验证
「同一 Feature 跨端跑起来」；至今所有 desktop 证据仍止于**编译**。

---

## §30 M1-4（2026-09-13）：最小 Desktop host 主路径 —— desktop 证据第一次越过「能编译」

主计划第 7 项：「展示同一 Feature，验证 Koin graph、ViewModel lifecycle、resources
和一条数据路径」。本片按用户选定的范围执行：**`feature:dict` + 真实 Room desktop 库 +
headless UI 测试**。

### 先探针，再投入（D5）

UI 测试这条工具链本仓从没跑过，所以先建 `smoke/compose-desktop-probe` 证明三件事：
`compose.uiTestJUnit4` 有 desktop 变体、`compose.desktop.currentOs`（Skiko）能在非
application 模块用、`runComposeUiTest` 在本机能跑完渲染。探针渲染一个「点按钮改文案」的最小
界面，一次通过。

⚠️ **两个版本陷阱**：

1. CMP 1.12.0 废弃了 `compose.runtime` / `compose.foundation` / `compose.material3` 这些
   accessor（要求直写坐标），且 `compose.uiTestJUnit4` **已不存在** ⇒ 必须显式加
   `org.jetbrains.compose.ui:ui-test-junit4` 到版本目录（ref `composeMultiplatform`）。
2. `runComposeUiTest` 有 v1 / v2 两代。v1 已废弃且**默认立即执行协程**；v2 默认
   `StandardTestDispatcher`，更贴近生产。对「VM 状态流经 Flow 到界面」这条链路，v1 的
   立即执行会掩盖「首帧还没收到数据」这类时序问题 ⇒ 从一开始就按 v2 写。

### 真正的阻塞：主题组装还留在 Android-only 的 `:core:ui`

探针过了，换真 Screen 立刻炸：

```text
java.lang.IllegalStateException: No ColorScheme provided
  at io.legado.app.ui.theme.LegadoThemeKt.LocalLegadoColorScheme$lambda$0(LegadoTheme.kt:118)
  at ...AppModalBottomSheet(AppModalBottomSheet.kt:62)
  at ...FilePickerSheet(FilePickerSheet.kt:39)
  at ...DictRuleScreen(DictRuleScreen.kt:153)
```

根因不是我包错了主题，而是**缺一层资产**：`:core:designsystem` 的组件读
`LegadoTheme.colorScheme`，背后是 `LocalLegadoColorScheme`，默认值是
`error("No ColorScheme provided")`；而提供它的 `ThemeComponents` **在 `:core:ui`**
——一个 Android-only 模块。

⇒ 这就是「四个 Feature 已全部转 CMP」之后仍然过不去的一道坎：**编译证据到此为止，
渲染需要主题，而主题还留在 Android 侧**。`compileKotlinDesktop` 全绿证明不了这件事。

### 解法：上提两个纯映射函数（M1-3x-pre 的同型切片）

- `ColorScheme.toLegadoColorScheme()`（含带参数的重载）在 `ThemeColorSchemeOverride.kt`
  里，但**是纯映射**，只碰 `ColorScheme` / `Color`；
- `Typography.kt` 整个文件的 import 全是跨平台的（material3 / `ui.text.font` / miuix
  `TextStyles`）⇒ 直接 `git mv`。

两者都搬进 `core/designsystem/src/commonMain/kotlin/io/legado/app/ui/theme/`，**包名不变**
（`io.legado.app.ui.theme`）⇒ `:core:ui` 侧调用方 import 零改动（同包调用本就无 import）。

desktop 侧因此能用 `DesktopTheme` 自己搭出语义色：`MaterialTheme` 打底 + 两个映射函数补
Local。⚠️ 这是**最小可用**主题，不是 `AppTheme` 等价物：无自定义字体（`withFont(null)`）、
无 Miuix 引擎切换、无动态取色。把 `AppTheme` 整体搬进共享层是独立切片。

### desktop host 的组装

`host:desktop` 的 `desktopTest` 里做四件事：

1. 用 `Room.databaseBuilder<AppDatabase>(name = …).setDriver(BundledSQLiteDriver())` 建真库
   （写法照抄已验证的 `smoke/room-kmp-probe`）；
2. 起 Koin（`desktopHostModule`），解析出 `DictRuleViewModel` 的四个依赖；
3. 插一条 `DictRule`，渲染 `DictRuleScreen`；
4. 断言界面上真的出现这条规则名。

**平台能力缺失一律显式建模**（AGENTS.md）：`UploadRepository` 调用即抛
`UnsupportedOperationException`（不是返回空串）；`Clipboard` 是进程内的，KDoc 写明
「不是系统剪贴板」；`RuleTransferPlatform` 的 URL 分支同样抛异常。只有 `writeExport`
按契约**静默吞异常**——那是迁移前 `openOutputStream` 返回 null 的既有语义，不能改。

### 变异验证（不能省）

UI 测试最容易假绿。把插入的规则名改成与断言不一致 ⇒ 测试**必须失败**（实测失败）。
这才证明「数据真的从 Room 流到了界面那一行」，而不是节点树碰巧对上。

### 验证

- 单独 `./gradlew clean` 后跑全量：四门禁 + 8 个 desktop 编译器 + `:app:compileAppDebugKotlin`
  + `:app:assembleAppDebug` + 9 个测试任务（新增 `:host:desktop:desktopTest` 与
  `:smoke:compose-desktop-probe:desktopTest`）。
- `tools/count-test-results.py`：主验证集 **708 → 709**、全量 **868 → 870**（新增 1 + 1 例，
  属有意变更，已同步基线并写进提交文案）。
- `git diff --check` 无输出。

## §31 M1-4b（2026-09-14）：Nav3 在 desktop 上的边界 —— 「能共享到哪一层」的实测答案

### 目标与范围

M1-4 明写的未做项是「Nav3 在 desktop 上的验证（本片只渲染单个 Screen，没有导航图）」。
本片回答一个问题：**Nav3 里哪些部分能跨平台共享，哪些不能**——用实测，不用推测。

- 做：制品边界实测（Gradle Module Metadata + `javap` 解 jar）、desktop 侧薄导航宿主、
  entry 级 `ViewModelStore`、两个目的地的导航 UI 测试 + 变异验证。
- 不做（明确排除）：iOS host（本机 Windows 无法编译 Kotlin/Native iOS，做不了验证）、
  `AppTheme` 整体上提（独立切片）、NavDisplay 的动画/scene strategy 复刻、跨 Feature 导航
  （原因见 `cmp-module-convention.md` §13.6）、返回栈的 saveable 序列化恢复。

### 关键情报：制品矩阵（实测）

| 制品 | desktop | 证据 |
|---|---|---|
| `navigation3-runtime` | ✅ 真 jar（`nav3-runtime-desktop-1.2.0-beta01.jar`，149 KB） | 可 `javap` 出 `NavBackStack`/`NavEntry` |
| `navigation3-ui` | ⚠️ 只有 `navigation3-ui-jvmstubs`（115 KB） | `NavDisplay` **只有三个 metadata 工厂**，没有 `@Composable` 本体 |
| `lifecycle-viewmodel-navigation3` | ❌ 只有 `-android` 变体 | `ls` 缓存目录实测 |
| `lifecycle-viewmodel`（KMP） | ✅ `ViewModelStore` 与 `clear()` 均 public | `javap` 实测 |

两个容易踩的坑：

1. **不能只看 `.module`**：缓存的 `.module` 里有 `desktopApiElements-published` 记录，
   但对应的 jar 从未下载过；而 `navigation3-ui` 的 desktop 变体记录指向的是 jvmStubs。
   直接 `curl` 下 jar 再 `javap`，比"猜 API → 编译试错"快一个数量级。
2. **`ViewModel.clear()` 是 internal**（`clear$lifecycle_viewmodel` 的 mangle 形式），
   但 `ViewModelStore.clear()` 是 public ⇒ 清理必须经 store 触发。

### 落地形态

`host/desktop/src/commonMain/.../nav/`（`:host:desktop` 是 android + desktop 双 target 的
KMP 模块 ⇒ 这两个文件被两个 target 编译，"导航契约不依赖 Android 类型"有编译期证据）：

- `DesktopRoute`：`sealed interface … : NavKey`，无参 `data object`。**刻意不加 `@Serializable`**
  ——desktop 不走 `rememberNavBackStack`（`NavBackStack` 构造是 public），
  序列化恢复留待 nav key 上提三端契约时再补。
- `DesktopNavHost`：`backStack.last()` → `entryProvider(key)` → `NavEntry.Content()`，
  外加两件 NavDisplay 的活：
  - `rememberSaveableStateHolder()` + `SaveableStateProvider(key)`（runtime 里的
    `SaveableStateHolderNavEntryDecorator` 的 `decorate`/`onPop` 是 internal，只能自己接）；
  - `destination -> ViewModelStore` 映射，entry 离栈时 `store.clear()`（复刻
    `rememberViewModelStoreNavEntryDecorator()`）。
- `desktopEntryViewModel { koin.get<…>() }`：entry 作用域取 VM 的入口（替代 `koinViewModel()`）。

desktopMain 侧：`DesktopApp`（根组合 + `desktopEntryProvider` + 两个目的地）、
`DesktopRuleHomeScreen`（host 入口页，宿主的真实职责）、`DictRulesEntry`（真实 Feature 屏 +
真实 Room 数据 + 真实 Koin 图）。host 的 `composeResources`（3 条文案 × 4 语言）是本片新增，
不是从 `:app` 搬迁 ⇒ 不走 `verify-compose-resources.py` 的等价性判据。

平台能力缺失一律显式：文件选择器在 desktop 不可用 ⇒ 提示"桌面端暂不支持文件选择"，
不静默空实现。

### 测试与变异验证

`DesktopNavigationHostTest`（2 例）：

- `navigatesBetweenHomeAndDictRules`：入口页 → **点真实按钮** → 词典页（断言 Room 里那条
  规则的文本可见、入口页 tag 消失）→ `removeLastOrNull()` → 入口页回来、词典页文本消失。
  屏别断言用 `testTag`，不依赖 locale 文案（CI 机器语言环境不确定）。
- `clearsEntryViewModelWhenEntryLeavesBackStack`：进 entry 时 `viewModelScope.isActive`，
  离栈后断言它**变成 false**（等 `snapshotFlow` 的 collector 跑完，不是同步假设）。

变异验证：把 `backStack.lastOrNull()` 改成 `first()`、并把离栈清理改成 `if (false)`
⇒ 两个用例**都失败**（`3 tests completed, 2 failed`），随后还原。
这一步证明断言挂在真实导航语义上，而不是节点树碰巧对上了。

### 验证

- 先单独 `./gradlew clean`，再跑：四门禁（`checkSharedPurity` / `checkModuleDependencies` /
  `verifyConfigArchitecture` / `checkLegacyArchitecture`）+ `:app:compileAppDebugKotlin` +
  `:app:assembleAppDebug` + `:app:testAppDebugUnitTest` + designsystem / viewmodel / tagrules /
  replacerules 的 `testAndroidHostTest` + `:core:data:desktopTest` + `:core:platform:desktopTest` +
  **`:host:desktop:assemble`**（含 `compileAndroidMain`——新代码住 `commonMain`，两个 target 都要编）
  + `:host:desktop:desktopTest` + `:smoke:compose-desktop-probe:desktopTest`，`--continue`。
  结果：**BUILD SUCCESSFUL**（402 tasks）。
- 四个门禁另用 `--rerun` **单独重跑一遍**，确认不是 UP-TO-DATE 造成的假绿 → 全部通过。
- `tools/count-test-results.py`：主验证集 709 → **711**、全量 870 → **872**
  （`host:desktop` 1 → 3 例；属有意变更，基线已同步）。
- `git diff --check` 无输出。

⚠️ 过程记录：clean 后的**第一次**全量运行失败，但不是代码问题——用户级
`D:/Android/.gradle/gradle.properties` 写死代理 `localhost:7897`，当时该端口没有服务，
`:host:desktop:compileAndroidMain` 因此下不动 `navigationevent-android` / `core-ktx`。
补上依赖后同一验证集全绿。**别把这种日志当成代码回归。**

### 已知差异（记账，本片不解决）

- `:host:desktop` 的 **android** target 解析到 `navigationevent-android:1.1.1`（nav3 传递而来），
  而 `:app` 侧显式抬到 `1.2.0-alpha04`（修预测式返回崩溃）。host 的 android 变体目前**只做编译
  验证、不参与产品**，故不构成行为风险；**若将来 host 要产出 Android 产物，必须与 `:app` 对齐**。
- desktop 没有 `NavDisplay`，所以入场/退场动画、predictive back、scene strategy、同栈结果回传
  这些"观感与手势"层面的行为在本片**完全没有验证**——不是"验证通过"，是"没有这个东西"。

### 结论

**Nav3 可共享的是 runtime（key / back stack / entry / entryProvider）；不可共享的是 UI
（NavDisplay 在非 Android 平台只有 metadata 存根）与 entry 级 VM decorator（Android-only，
但可用 KMP 的 `ViewModelStore` 自行补齐）。** 三端共享导航图的形态因此是：
共享 runtime 状态机 + 各平台自带（或共享自建）渲染层，而不是"照抄 MainNavGraph"。

## §32 M2-1（2026-09-14）：`ImportJsonEditorProvider` 退役 —— 共享层最后一个 service locator

### 目标与范围

来源：主计划「随后执行」第 8 条（按 M2 表逐一删除 9 个 core Provider，**先处理会阻塞现有四个
Feature 的 clipboard/toast/import**）。M1-3a 已把 clipboard/toast 改成构造注入并已被四个 Feature
消费；**import 是剩下唯一还住在 CMP 共享层里的静态委托**。

- 做：`:core:designsystem` 的 `BatchImportDialog` 改**参数注入**；删 `:core:platform` 的
  `ImportJsonEditorProvider`；8 个调用点补注入；desktop 侧显式 unsupported；G4 基线下调。
- 不做（明确排除）：**换实现**。用 kotlinx.serialization 复刻 `GsonImportJsonEditor` 的字段拆解
  需要先建立「字段顺序 / 数值回写规则 / `prettyPrinting` 文本」逐分支等价的证据，属独立切片。

### 为什么它排在 M2 第一批

`ImportJsonEditorProvider` 全仓只有 3 处引用：定义（`:core:platform`）、安装（`:app` 的
`PlatformServices.install()`）、以及**唯一的使用点**——`core/designsystem/commonMain` 的
`ImportComponents.kt`（私有组件 `BatchImportJsonEditContent` 里的 `.current`）。

也就是说：**CMP 共享层里的通用 UI 组件库直接读了全局单例**。这与 M2 的完成判据
（「共享模块零 service locator」）正面冲突；更糟的是它是公共 API `BatchImportDialog` 的**隐藏
前置条件**——调用方不注册就崩（`current` 的语义是 `error(...)`，不是返回 null），而签名上
完全看不出来。

### 形态：公共 Composable 的能力走参数，不走 CompositionLocal

| 方案 | 改动面 | 问题 |
|---|---|---|
| 换成 `LocalImportJsonEditor` CompositionLocal | designsystem 内部 + 各宿主提供点 | 仍是隐式依赖，只是把「全局单例」降级成「作用域单例」；`BatchImportDialog` 的 API 上看不出它需要什么 |
| **参数注入（本片采用）** | 8 个调用点显式传 | 依赖进签名；**漏传 = 编译错误** |

选参数注入的理由：`BatchImportDialog` 是 designsystem 的公共 API，把需要的能力列进签名本身就是
文档；而且「漏传编译不过」比「运行时 `error()`」早一个数量级被发现。
（`LocalComposeEngine` 的先例不构成反例：那是引擎语义、本就有明确回落值；`ImportJsonEditor`
没有可回落的默认实现。）

### 落地形态（8 个调用点 + 两侧绑定）

| 调用方 | 拿实现的方式 |
|---|---|
| 4 个 CMP Feature 的 **Screen**（commonMain） | 新增 `importJsonEditor: ImportJsonEditor`（放在 `importState` 之后），转手交给 `BatchImportDialog` |
| 4 个 CMP Feature 的 **Route**（androidMain） | `koinInject<ImportJsonEditor>()`——Route 的既有职责就是「装配平台能力」 |
| 4 个 **app 屏**（`HighlightRuleConfigSheet` / `CloudTtsScreen` / `BookSourceScreen` / `RssSourceScreen`） | `koinInject()`（`:app` 里 `ImageLoader` / `CoverSettingsGateway` 等已有同款先例） |
| **`host:desktop`** | `desktopHostModule` 绑定 `DesktopImportJsonEditor`，`DesktopApp` 用 `koin.get()` 取 |

绑定侧：
- `:app` 的 `appModule` 加 `single<ImportJsonEditor> { GsonImportJsonEditor() }`；
  `PlatformServices` 删掉 `importJsonEditor` 字段与 `ImportJsonEditorProvider.install(...)` 调用
  ——Provider 注入路径整体退役，不是"留着不用"。
- `host:desktop` 的 `DesktopImportJsonEditor` **三个方法都显式抛 `UnsupportedOperationException`**：
  Android 实现依赖 Gson 门面（只在 `:core:data/androidMain`），desktop 复刻要先证明等价。
  可达性论证：desktop 的导入流程本身走不通（文件选择器不可用 + `DesktopRuleTransferPlatform`
  对 URL/URI 抛异常）⇒ `importState` 进不了 `Success` ⇒ 编辑面板在 desktop 上不可达。

### G4 基线（棘轮只降不升）

| 条目 | 前 | 后 |
|---|---|---|
| `coreProvider\|core/designsystem/commonMain/io/legado/app/ui/widget/components/importComponents` | 2 | **删除（0）** |
| `coreProvider\|app/main/io/legado/app/help` | 8 | 7 |

总条目 **328 → 327**。剩余 `coreProvider` 6 条里两块最大的债：
`core/data/.../entities`（45，`BaseSource` / `BaseBook` 的 key-value / cookie / runtime / bigdata
依赖）与 `core/ui/.../widget/components`（2，`LoadMoreFooter` 的 `ClipboardProvider.current`）。
前者按 M2 表的 `KeyValueStoreProvider` / `CookieStoreProvider` / `SourceRuntimeProvider` /
`BigDataStoreProvider` 四条分别处理；后者等 `LoadMoreFooter` 随页面迁进 designsystem 时一起做
（单独搬一个组件只为零掉 2 计数不划算）。

### 验证

- 先单独 `./gradlew clean` 语义的作用域下跑四门禁 + 全部受影响编译：4 个 Feature 的
  `compileKotlinDesktop` 与 `compileAndroidMain`、`:core:designsystem:compileKotlinDesktop`、
  `:host:desktop:compileKotlinDesktop` + `:host:desktop:assemble`（含 android target）、
  `:app:compileAppDebugKotlin` → **BUILD SUCCESSFUL**。
- **「8 个调用点都传了参数」由编译保证**：参数没有默认值，漏一个就是编译错误。这比逐个
  写断言更结实，也是选参数注入而非 CompositionLocal 的直接收益。
- 用例基线 **711 → 712 / 872 → 873**（`host:desktop` 3 → 4）：新增 `DesktopImportJsonEditorTest`
  把「desktop 显式不支持」钉成**可执行断言**。理由：`ImportJsonEditor` 三个方法都有「看似合理」
  的降级返回值（`fieldsOf` 返回 null 的语义是「该对象不可编辑」），所以「desktop 没做」
  很容易被顺手写成"返回 null 让它别崩"——那会把**平台缺口**伪装成**内容不可编辑**。
- 变异验证：删掉 `desktopHostModule` 的 `single<ImportJsonEditor>` 绑定 ⇒
  `navigatesBetweenHomeAndDictRules` 与 `rendersRulesFromDatabaseOnDesktop` 两例以
  `NoDefinitionFoundException` 失败（`4 tests completed, 2 failed`），随后还原。证明这两条
  测试真的经过这条注入路径，而不是"碰巧不需要这个绑定"。
  （`DesktopImportJsonEditorTest` 不经过 Koin——它直接对 object 断言，所以变异下仍绿，
  这是预期行为，说明两类测试各自锁的是不同的事。）

## §33 M2-2（2026-09-14）：`BigDataStoreProvider` 退役 —— 计划外的一条路更好走

### 目标与范围

来源：主计划「随后执行」第 8 条，M2 表里 `core/data/.../entities` 那 45 条债的 `BigDataStore`
一族（9 条：`BaseBook` / `BaseRssArticle` / `BookChapter` 各 2 处 `.current`）。

- 做：让 `BigDataStoreProvider` 消失；保证**既有用户数据仍能读回**；G4 基线下调。
- 不做：`BaseSource` 的 `KeyValueStore` / `CookieStore` / `SourceRuntime` 三条（同区域，独立切片）。

### 计划里的方案为什么不可行（先侦察，再动手）

M2 表对这条的既定方案是「`SourceVariableRepository` 注入 UseCase / **Room entity 变纯数据**」。
侦察后三条硬约束把它封死了：

| 约束 | 证据 |
|---|---|
| entity **进不了 DI** | `BigDataStore` 的调用方是 `BaseBook` / `BaseRssArticle` / `BookChapter` 的**实例方法**，entity 由 Room 构造 |
| 方法名是**书源 JS 兼容面** | `AnalyzeUrl` 里 `bindings["book"] = ruleData as? Book`，脚本直接调 `book.putVariable(...)` ⇒ 签名与存在性都不能动 |
| 规则求值入口**拿不到 DI** | `WebBook` / `BookList` / `BookContent` / `Rss` / `RssParserByRule` 全是 `object` 单例 |

⇒ 「实体变纯数据 + 上游装配」在本仓**没有装配点**：全仓 66 处 `WebBook.*` 调用都要改签名才能把
Repository 递进去。这不是"难度大"，是**没有可行的落点**。

### 采用的形态：实现下沉共享层 + 平台原语

| 层 | 内容 |
|---|---|
| `core/data/commonMain` | `object RuleDataFileStore : BigDataStore` —— **全部路径语义**（`book`/`rss` 分区、`bookUrl.txt`/`origin.txt` 标记、MD5 目录） |
| `core/platform`（新） | `expect object RuleDataStorage` —— **只有原语**：`rootDir` / `md5` / `readText` / `writeText` / `exists` / `delete` / `list` |
| `:app` | `App.onCreate` 设 `RuleDataStorage.rootDir`；清理逻辑搬进新 `object RuleDataCleaner`（因为要问 `appDb`） |
| `host:desktop` | `desktopHostModule(...)` 构造体里设 `rootDir`（"起 graph 即就绪"） |

关键判据：**`RuleDataStorage` 不认识 `book` / `rss` / `bookUrl.txt`**。布局是共享层知识，一旦漏进
actual，每个平台各存一份、各漂各的。

`androidMain` 与 `desktopMain` 各一份内容相同的 `actual object`（`diff` 完全相同），沿用
`JvmFileSystem` / `JcaDigest` 的既有先例——`actual` 的定义域就是各自 source set，共享不了，
"两份相同"比"多一个中间层只为省重复"更简单。

### 兼容面：为什么必须用已知向量而不是自证

`RuleDataFileStore` 的路径规则是从 `:app/help/RuleBigDataHelp` 移植的，而**磁盘上已经有用户数据了**：
`<externalFiles>/ruleData/book/<md5(bookUrl)>/<md5(key)>.txt`。路径一旦变形就是静默丢数据。

MD5 逐字节对齐的链条：`MD5Utils.md5Encode(str) = digest("MD5", str.orEmpty().toByteArray()).toHexString()`，
其中 `HEX_CHARS = "0123456789abcdef"`（**小写**）、`toByteArray()` 默认 UTF-8、无盐。
⇒ 原语实现必须照抄这四条，且 `getBookVariable(bookUrl, key: String?)` 的 `key` 可空，
`md5Encode` 走 `orEmpty()` —— 新实现显式写 `key.orEmpty()` 并留注释，不"顺手特判"。

测试用硬编码向量 `md5("abc") = 900150983cd24fb0d6963f7d28e17f72` 拼期望路径，
**不拿被测函数自己的 md5 去算期望值**（那是自证）。

### 本片的新坑

1. **KMP 模块的 android 编译任务名是 `compileAndroidMain`**，不是 `compileDebugKotlinAndroid`
   （后者是 Android library 插件、且有 build type 变体时的名字）。本次第一条编译探测命令
   就因为这个任务名不存在而 "Selection failed"，白跑一轮。桌面侧仍是 `compileKotlinDesktop`。
2. **Windows 文件系统大小写不敏感 ⇒ 路径用例守不住"hex 大小写"**。变异把 `hexChars` 改成大写后，
   6 例里**只有**直接断言 hex 串的那例失败，四条路径用例全绿（`File(root,"book/900150983cd24fb0…")
.isFile` 在 Windows 上对 `900150983CD24FB0…` 也返回 true）。
   所以"钉住大小写"必须由一条**断言字符串**的用例承担；路径用例负责布局，不负责大小写。
   另做了 `book`→`books` 的路径级变异，3 例失败 ⇒ 布局用例在 Windows 上确实是活的。
3. **中文测试方法名里带空格必须用反引号**：`fun md5 是标准小写十六进制()` 是语法错误，
   要么 `` fun `md5 是标准小写十六进制`() ``，要么去掉空格。本仓 desktopTest 的惯例是反引号
   （见 `ReadSettingsGatewayCoverageTest`）。
4. **删掉实现类会连带触发一串别的门禁下调**：删 `RuleBigDataHelp` 门面后，除了预期的
   `coreProvider|…entities` 45→36，还连带报了 `appCtx|…/help` 10→9、
   `legacyHelp|…/help/book` 2→1、`legacyNaming` 三条。G4 的棘轮会把**所有**下降都报出来，
   一次性全部下调即可（不能只调自己预期的那条）。

### G4 基线（棘轮只降不升）

| 条目 | 前 | 后 |
|---|---|---|
| `coreProvider\|core/data/commonMain/io/legado/app/data/entities` | 45 | **36** |
| `coreProvider\|app/main/io/legado/app` | 2 | **删除（0）** |
| `appCtx\|app/main/io/legado/app/help` | 10 | 9 |
| `legacyHelp\|app/main/io/legado/app/help/book` | 2 | 1 |
| `legacyNaming\|app/main/io/legado/app` | 6 | 5 |
| `legacyNaming\|app/main/io/legado/app/help` | 23 | 21 |
| `legacyNaming\|app/main/io/legado/app/help/book` | 10 | 9 |

`coreProvider` 条目 **5→4**，基线数据行 311→310。**没有新增任何键**——本片零新增 area，
这是"实现下沉共享层"相对"新建 Repository/UseCase"的直接好处。

一条值得记的交叉验证：`appDb|app/main/io/legado/app/help` 的计数**没有变**（门禁未报）。
`RuleBigDataHelp.clearInvalid()` 用的 2 处 `appDb` 原样搬进了 `RuleDataCleaner`——
门禁没报变化，正好反证「搬过去的是同样的两处，没有多也没有少」。

### 验证

- 先单独 `./gradlew clean`（跨模块移动必须干净重建）。
- 四门禁 `--rerun`：`checkSharedPurity` / `checkModuleDependencies` / `verifyConfigArchitecture` /
  `checkLegacyArchitecture` → 全绿。
- 全量集：`compileAndroidMain` × 2（platform/data）+ `compileKotlinDesktop` × 2 +
  `:app:compileAppDebugKotlin` + `:app:testAppDebugUnitTest` + 四个 Feature 的
  `testAndroidHostTest` + `:core:data:desktopTest` / `:core:data:testAndroidHostTest` +
  `:core:platform:desktopTest` + `:host:desktop:desktopTest` + `:host:desktop:assemble` +
  `:smoke:compose-desktop-probe:desktopTest` + `:app:assembleAppDebug`
  → **BUILD SUCCESSFUL，403 actionable tasks**。
- 用例基线 **712 → 712 / 873 → 877**。差额 `+4` 的来源逐条可解释：`core:data` 的
  `BigDataStoreTest` **5→3**（删掉 `未安装时显式失败而不是静默返回空` 与 `安装后可读回同一实现`
  ——`BigDataStoreProvider` 已经不存在，这两个用例失去被测对象），新增
  `RuleDataFileStoreDesktopTest` **6 例**。主验证集 712 不变（`core:data` 的 desktopTest
  只在全量集计数）。**这是有意的增减，不是回退**：新用例守的是「既有数据能否读回」这个
  比原用例更强的性质。
- 变异验证（两轮，均随后还原并用 `grep -rn MUTATION` 确认无残留）：
  - `hexChars` 改大写 ⇒ `md5 是标准小写十六进制` 失败（`6 tests completed, 1 failed`）。
  - `DIR_BOOK = "book"` → `"books"` ⇒ 3 例失败（`85 tests completed, 3 failed`）。
- `python tools/count-test-results.py`：主验证集 712 / 全量 877，双 OK。
- `git diff --check` 无空白问题。

### 下一步

`entities` 区域还剩 36 条，全是 `BaseSource` 的 `KeyValueStore` / `CookieStore` /
`SourceRuntime` / `SymmetricCrypto` / `Logger` 一族。它与本片的关键差别：**`BaseSource` 的消费方
多在 `:app` 的 `model/analyzeRule` 与 `service` 里，那里 `object` 单例较少、有装配点**，
所以 M2 表的「上游装配」对那三条可能真的可行——下次先侦察再定形态，不要默认照搬本片的原语方案。
（`SymmetricCrypto` 已有 `SymmetricCryptoProvider` 且 `PageEstimate` 之类已在用，是本区域突破口。）

## §34 M2-3（2026-09-14）：`SymmetricCryptoProvider` 退役 —— `entities` 那 36 条里唯一真正能退休的一条

### 起点：先侦察，否掉「照搬 M2-2」

§33 结尾留了一句「下次先侦察再定形态」。侦察结论如下——`entities` 那 36 条**全部**来自
`BaseSource`，按符号是 `KeyValueStore` 15 / `SourceRuntime` 9 / `Logger` 5 / `CookieStore` 4 /
`SymmetricCrypto` 3（另有 `app/help` 区域 7 条：5 行 `install` + `ClipboardProvider`/`ToasterProvider`）。
判定的问题不是「写法对不对」，而是「实现住哪、`:core:platform` 够不够得着」：

| 契约 | `:app` 侧实现 | 实现真正的依赖 | 能否做成 `:core:platform` 原语 |
|---|---|---|---|
| `SymmetricCrypto` | `help/crypto/SymmetricCryptoAndroid` | JCA + `CryptoUtils`（实际只用到 `kotlin.io.encoding.Base64` 与 `MessageDigest`） | ✅ 本模块自足 |
| `Logger` | `constant/AppLog` | `LogUtils` / `BuildConfig` / `appCtx.toastOnUi` / `OtherSettingsGateway` | ❌ 见「下一步」 |
| `KeyValueStore` | `help/CacheManager` | `appDb.cacheDao`（Room 的 `caches` 表）+ `ACache` | ❌ 拿不到 `AppDatabase` 实例 |
| `CookieStore` | `help/http/CookieStore` | okhttp `CookieManager` + `appDb.cookieDao` + WebView | ❌ 同上 |
| `SourceRuntime` | `PlatformServices` 内联 | `SharedJsScope` / `ConcurrentRateLimiter` / `BookSourceExtensions` / `AppConst.androidId` | ❌ 同上 |

`expect object` 的 actual 只能住在**声明它的那个模块**里，而 `:core:platform` 看不见 `:app`。
于是「能不能原语化」等价于「这个实现能不能由 `:core:platform` 自己写出来」。上表只有
`SymmetricCrypto` 是「能」——这也是**M2 表里最后一条不需要先动基础设施就能清掉的 Provider**。

### 结论：改原语，这次 AGENTS.md 的判据真的成立

M2 表原写「使用方所属 domain port + platform implementation」。推翻它，因为
「**无第三实现且语义恒等 ⇒ `expect/actual` 原语**」在这条上完全成立：

- android 与 desktop **都是 JVM**，`javax.crypto` 两边都在，语义恒等；
- 除 `SymmetricCryptoAndroid` 外没有第三个实现，也没有「宿主必须替换」的余地。

当年判成「接口 + 注入」的理由是「实现依赖 `:app` 的 `isHex`/`hexToByteArray`/`toBase64`」。核了一遍
`CryptoUtils`：这几个工具只用到 `kotlin.io.encoding.Base64`（**stdlib，Kotlin 2.2 起已稳定**）与
`MessageDigest`（JVM 自带）。**那个判断建立在一个没核实的假设上**，这也是本片最值得记的一条：
「实现依赖 `:app`」这句话要查到底，不能停在 import 的表象。

### 落地形态

- `core/platform/commonMain` 新增 `CryptoCodecs.kt`（`internal`）：小写 hex 编/解码、标准 Base64
  编/解码（剥空白 + URL-safe 回落）、`isHexText`。**为什么本模块自己留一份**：`:core:model` 已经
  依赖 `:core:platform`，反向复用 `io.legado.app.utils.isHex()` 会构成依赖环，
  `checkModuleDependencies` 会拦。
- `SymmetricCrypto` 改为 `expect object`（`encryptBase64` / `decryptStr`），`interface` 与
  `SymmetricCryptoProvider` 一起删除；androidMain + desktopMain 各一份**内容完全相同**的 JCA
  actual（`diff` 已确认），先例 `JvmFileSystem` / `JcaDigest` / `RuleDataStorage`。
- 顺手把 `RuleDataStorage` 两份 actual 里手写的 hex 循环换成 `encodeHexLower()`：同一个模块里同一段
  编码抄两遍没有理由。`RuleDataFileStoreDesktopTest` 的 `md5("abc")` 硬编码向量回归验证了它。
- 消费方只动三处：`BaseSource`（import + 2 个调用点）、`PlatformServices`（删匿名实现 + install 行 +
  两个 import）、`App.kt` 的一句注释。

### 兼容面：这次守的是「用户会不会被登出」

`BaseSource.getLoginInfo` / `putLoginInfo` 把用户登录信息 AES 加密后写进 `caches` 表的
`userInfo_<sourceKey>`。密文格式一变，所有人已存的登录态直接解不开。锁定下来的约定：

- `algorithm` 不含 `/` 时补 `/ECB/PKCS5Padding`；密钥算法名取 `/` 前段；
- 明文 UTF-8；密文标准 Base64（带 padding）；
- 解密**先判**「整串是 `0-9a-fA-F` 且长度为偶数 ⇒ 按 hex 解」，否则 Base64（剥空白 + URL-safe
  回落）。**顺序不能反**，反了 hex 分支就是死代码。

**刻意不移植的三条**（都无调用方）：`setIv` / `iv` 字段、`key == null` 时随机生成密钥、
DES/DESede 超长密钥截断。第三条原本打算照抄，被 `openssl` 3 与 Node 22 **双双拒绝 DES**
（`ERR_OSSL_EVP_UNSUPPORTED`）提醒了一次：留一条既没有测试、也没有调用方的分支，等于把一个将来会踩的
坑藏在「看起来很忠实」里。

### 验证

- 编译：`compileAndroidMain` + `compileKotlinDesktop`（platform / data）+ `:app:compileAppDebugKotlin`
  → 全绿。
- 契约测试重写：原 `SymmetricCryptoContractTest` 2 例测的是 Provider 的 install/uninstall
  （被测对象已删除），换成 **7 例**对原语本身的测试；文件住 `commonTest` ⇒ android 与 desktop
  **各跑一遍、同时覆盖两份 actual**（`:core:platform:testAndroidHostTest` 81 / `desktopTest` 84，
  均 0 失败 0 跳过）。
- **向量不是自证**：期望密文由 `openssl enc -aes-128-ecb` 与 Node
  `crypto.createCipheriv('aes-128-ecb')` 两个**独立于被测代码**的实现算出并互相比对一致
  （16 字节块下 PKCS#7 ≡ JCE 的 PKCS5Padding），再硬编码进测试。密钥取 ASCII
  `"0123456789abcdef"`，刻意复刻 `SourceRuntime.androidId().encodeToByteArray(0, 16)` 的真实形状
  ——16 个字符的 UTF-8 字节，而不是把这 16 个 hex 字符解码成 8 字节。
- 变异验证两轮（均随后还原，`grep -rn MUTATION` 无残留）：
  - 断掉 `decryptStr` 的 hex 分支 ⇒ **精确 1 例**失败（只有 hex 那例红）。
  - `encodeBase64` 换成 URL-safe 表 ⇒ 2 例失败。**注意**：`abc` / `hunter2` 两条向量**没红**
    ——它们的密文恰好落在两套字母表的重合区；真正识破字母表变化的是含 `+`/`/` 的那条中文向量。
    这与 §33 的「Windows 文件系统大小写不敏感」是同一类教训：**覆盖要挑能区分实现的输入，
    不能只看条数**。
- 四门禁 `--rerun` 全绿；全量集 **BUILD SUCCESSFUL**（任务清单见提交文案）。
- 用例 **712→712 / 877→882**：主验证集不变；全量 +5 = `core:platform` 的
  `SymmetricCryptoContractTest` **2→7**（原 2 例失去被测对象、新 7 例守兼容面）。
  这是**有意变更**，`tools/count-test-results.py` 的 `BASELINE_ALL` 已同步 882。
- `git diff --check` 无输出。

### G4

`coreProvider|…entities` **36→33**、`coreProvider|app/…/help` **7→6**；删掉
`import io.legado.app.help.crypto.SymmetricCryptoAndroid` 连带 `legacyHelp|app/…/help` **35→34**。
三条全是下降、**零新增区域**，基线数据行 **310 不变**（`coreProvider` 条目仍是 4 条）。

### 下一步：剩下的 33 条为什么不能照搬这一片

侦察已经把卡点定位到**两类可达性**，都不是写法问题：

1. **实现需要 host 的实例，而平台模块够不着。** `KeyValueStore` / `CookieStore` /
   `SourceRuntime.clearExploreKindsCache` 都要 `AppDatabase`（`caches` / `cookies` / 探索缓存三张表），
   okhttp `CookieManager` 也在 `:app`。`RuleDataStorage` 之所以能做成原语，是因为它只需要**一个路径**
   这类**配置**，host 一设就完事；`AppDatabase` / `CookieManager` 是**服务**，不是配置。要清掉它们，
   先得让 `:core:data` 自己拥有库实例（顺带把 `:app` 的 `AppDb` 全局收掉——里程碑级前置），
   否则就只剩「在共享层新加一个持有者」，而它与项目「共享模块零 service locator」的目标直接冲突，
   **必须显式决策，不能默认**。
2. **`Logger` 的实现是同名同义的 `:app` `constant.AppLog`。** 它有 **300+ 调用方**，且站内日志界面
   （`AppLogSheet`）与 `TtsCacheViewModel` 都读 `AppLog.logs`。改成 `android.util.Log` 会让 `BaseSource`
   那几条日志**静默从站内日志消失**（用户排查书源时正是靠它）；而 `debug` 那一档还受 `recordLog`
   开关约束（默认 **false**），原语要复刻这个开关就得再把一个 `:app` 设置读进共享层。可行方向是把
   `AppLog` 的**环形缓冲**搬进共享层、`:app` 的 `AppLog` 退化成薄包装——那是独立一片，且要连带
   验证日志界面。
