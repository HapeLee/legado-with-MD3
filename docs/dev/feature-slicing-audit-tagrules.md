# `tagrules` 依赖审计（M1-1）

为 M1-3「消除 `tagrules` 的 Provider/base 依赖并转 CMP」做前置：先把调用闭包和平台能力点列全，
再给 import/export/reducer 立 characterization 测试。本文只做审计与测试，**不改生产行为**。

## 1. 结论摘要

`:feature:tagrules` 共 7 个文件 1363 行（Stage B 已完成，包名 `io.legado.app.feature.tagrules.*`）。
真正阻塞 CMP 的只有五类，且**都在边界上**：

| 阻塞项 | 出现位置 | 处置（M1-3） |
| --- | --- | --- |
| `android.app.Application` | 两个 ViewModel 构造（经 `BaseRuleViewModel`） | VM 不再持有 Application；需要的 `context.getString` 改为契约 |
| core Provider 委托 | `ClipboardProvider` 2、`ToasterProvider` 4（仅 group 侧） | 改为注入的 clipboard/toast 契约（M2 删 Provider 的前哨） |
| Android 资源 `R.string.*` | 26 个键，VM 与 Screen 都用 | CMP 资源（`Res`）或由宿主传文案 |
| 文件选择/导出（SAF） | `rememberLauncherForActivityResult` ×2、`FilePickerSheet` ×2、`android.net.Uri` | 抽「选导入源 / 选导出目标」能力契约 |
| `GSON` 门面 | 两个 VM 的 `generateJson`/`parseImportRules`/`copyRule`/`pasteRule` | 换成已有 JSON 编解码契约 |

数据与列表编排（`:core:data` Repository + `BaseRuleViewModel`）**不需要先动**：它们已在模块内，
属于 data 类型依赖，转 CMP 时可随模块一起进 `commonMain`。

## 2. 文件清单

| 文件 | 行 | 职责 | 平台点 |
| --- | --- | --- | --- |
| `group/TagGroupRuleContract.kt` | 64 | UiState/Intent/Effect | — |
| `group/TagGroupRuleEditSheet.kt` | 151 | 新增/编辑弹层 | `R.string`、`LocalContext` |
| `group/TagGroupRuleViewModel.kt` | 234 | 列表/选择/排序/导入导出/同步 | Application、Provider、`R`、GSON、Gateway |
| `highlight/HighlightTagRuleContract.kt` | 68 | UiState/Intent/Effect | — |
| `highlight/HighlightTagRuleEditSheet.kt` | 177 | 新增/编辑弹层 | `R.string` |
| `highlight/HighlightTagRuleScreen.kt` | 369 | 主页面（含导入/导出入口） | SAF、FilePicker、`LocalClipboard`、`R.string` |
| `highlight/HighlightTagRuleViewModel.kt` | 224 | 同 group，无同步 | Application、`ClipboardProvider`、GSON |

注：**group 侧没有 Screen 在模块内**——`GroupManageSheet`（`:app`）直接消费 `TagGroupRuleEditSheet`
与 `TagGroupRuleViewModel`；highlight 侧的 Route Screen 已迁入模块。

## 3. 依赖分类

### common-ready

`kotlinx.coroutines.*`、`kotlinx.collections.immutable.*`、
`io.legado.app.data.entities.{TagGroupRule,HighlightTagRule}`、
`io.legado.app.data.repository.*`、`io.legado.app.domain.gateway.BookGroupMutationGateway`、
纯状态契约 `ListUiState`/`SelectableItem`/`InteractionState`。

### contract-needed（窄接口 + 平台实现）

| 依赖 | 现状 | 目标 |
| --- | --- | --- |
| 剪贴板 | `ClipboardProvider.current`（静态委托） | 注入的 clipboard 契约 |
| 轻提示 | `ToasterProvider.current.toast`（group）；highlight 走 `_effects` | 统一为 Effect（highlight 形态已是目标形态） |
| 导入/导出 IO | `RuleTransferPlatform`（`readImportSource`/`writeExport`，目标用 `String`） | **已抽好**，Android 实现 `AndroidRuleTransferPlatform` 在 `:app` |
| 上传 | `UploadRepository`（接口） | 保持 |
| 规则应用 | `BookGroupMutationGateway`（接口） | 保持 |
| 文件选择 | `FilePickerSheet` + `ActivityResultContracts` | 尚无契约，需新增能力接口 |
| 序列化 | `GSON` + `isJsonArray/fromJsonArray/fromJsonObject` | 换成 JSON 编解码契约 |

### platform-island

`android.app.Application`、`android.net.Uri`、`androidx.activity.result.*`、`LocalContext`、
`LocalClipboard`、`R.string.*`——都留在 Android 宿主或通过上面的契约进入共享层。

## 4. 调用闭包

Koin（`:app` 的 `appModule.kt`）：

```kotlin
single<UploadRepository> { DirectLinkUploadRepository() }              // :app
single<RuleTransferPlatform> { AndroidRuleTransferPlatform(androidContext()) }  // :app
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

1. 剪贴板/提示改为注入契约（顺带删掉 `tagrules` 的 Provider 引用，与 M2 同向）。
2. `Application`/`context.getString`/`R` 退出 ViewModel：文案走契约或由宿主提供。
3. GSON → JSON 编解码契约。
4. 文件选择能力契约化，Screen 只收回调。
5. 以上完成后，`tagrules` 的 Screen/VM/Contract 才能整体进 `commonMain`（模块登记为 `cmp`）。

回滚点：第 1 步可独立回滚（Provider 仍在），第 2 步起需整片回滚；每一步都不改导入/导出的磁盘语义。

## 7. 本次补的 characterization 测试

`feature/tagrules/src/test/java/io/legado/app/feature/tagrules/TagRulesImportExportCharacterizationTest.kt`
（Robolectric + 内存 Room，真实 GSON）：

- 导入 JSON 数组 → New/Update/Existing 三分类与默认勾选；单对象 → 一条；非法文本 → `Error`；
- 导入文本 `trim` 后交给平台；
- 导出：选中项序列化后写入目标；空选择只提示不写；写入失败上报原因；
- 落库：只保存勾选项，group 侧触发规则应用，结束回 `Idle`；
- reducer：搜索过滤 + `order` 排序、`displayName` 回退、`hasChanged` 字段集。

## 8. 未验证风险

- 真机上的 SAF 导入/导出路径（`AndroidRuleTransferPlatform`）与内置规则导入不在本次测试范围。
- `writeExport` 静默跳过（目标打不开仍报「导出成功」）是既有语义，测试只做契约级断言。
- 桌面/iOS 目标尚未编译过本模块；CMP 化后的资源与文件选择能力需另立 PoC。
