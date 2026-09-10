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
| 文件选择/导出（SAF） | `rememberLauncherForActivityResult` ×2、`FilePickerSheet` ×2、`android.net.Uri` | 抽「选导入源 / 选导出目标」能力契约 | 待办（M1-3c） |
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
| `highlight/HighlightTagRuleScreen.kt` | 369 | 主页面（含导入/导出入口） | SAF、FilePicker、`LocalClipboard`、`R.string` |
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
| 文件选择 | `FilePickerSheet` + `ActivityResultContracts` | 尚无契约，需新增能力接口 | 待办 |
| 序列化 | **已换 `:core:platform` 的 `JsonCodec`** | 同左 | **完成** |

### platform-island

`android.app.Application`、`android.net.Uri`、`androidx.activity.result.*`、`LocalContext`、
`LocalClipboard`、`R.string.*`——都留在 Android 宿主或通过上面的契约进入共享层。

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
4. 文件选择能力契约化，Screen 只收回调（**M1-3c**）。
5. 以上完成后，`tagrules` 的 Screen/VM/Contract 才能整体进 `commonMain`（模块登记为 `cmp`）。

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
