plugins {
    id("legado.kmp.compose")
}

// `:core:designsystem` 是**真 CMP 模块**：Compose 依赖在 `commonMain`，一份 UI 代码跨
// android / desktop（将来加 iOS 只加 target，不加源集）。CMP 装回的时机由「有没有真实
// 消费方」决定——见下方"切片规则"。
//
// 历史：这里曾挂过 android/desktop 共享的 `composeMain` 中间源集放设计 token，但那套
// token 一直没有真实消费方（AGENTS.md 禁止无调用方抽象），已随 token 一并删除，CMP 也
// 一并撤掉。`composeMain` 是「commonMain 必须零 Compose」时代的过渡形态；M0-1 之后
// `checkSharedPurity` 按模块类型分策，本模块登记为 **cmp**，Compose 直接进 commonMain。
// 版本配方（已验证）：Kotlin 2.4.10 ↔ CMP `org.jetbrains.compose` **1.12.0**
// （要求 AGP 9.1.1+ / compileSdk 37）。
//
// 切片规则（`M1-2` 起）：**只承载有真实消费方的东西**。
//   1. 迁进来的一定是当前已被某个 Feature（首个是 `:feature:tagrules`）消费的
//      theme / 组件 / 资源；拒绝预建空 token 和"将来可能用到"的组件。
//   2. 包名沿用 `io.legado.app.ui.*`（与 `:core:ui` 同命名空间），因此把组件从
//      `:core:ui` 搬进来时**调用方 import 零改动**，只需消费方已依赖本模块。
//   3. 平台 SDK（`android.*`）一律不进 commonMain——需要它的留在 `:core:ui`，
//      或抽窄契约后进共享层。
//
// 另注：本仓的间距/形态是**引擎条件式**的（Miuix 12dp / Material3 16dp，见 `AdaptivePadding.kt`
// 里的 `AdaptiveSpacing`），不是一套常量刻度。所以共享层先落"当前引擎"这个语义
// （`ComposeEngine`），而不是先把数字抽象成 token——否则就是把真实语义抹平的假抽象。

// M1-3v：`widget/components/lazylist/*`（`LazyList` / `VerticalFastScroller`）从 `:core:ui`
// 搬入 commonMain。它唯一的 Android-only 点是 `Modifier.systemGestureExclusion()`——快速滚动条
// 可拖动时声明那一小块不被系统返回手势抢走。实测解 CMP `foundation-desktop-1.12.0.jar`，
// 整个制品没有任何 `*Exclu*` 类（Android 侧最终转发 `View.setSystemGestureExclusionRects`）
// ⇒ 下沉为 `io.legado.app.ui.platform.systemGestureExclusionCompat()` 这一 **expect/actual
// 平台原语**。刻意**不用** `StatusBarInsets` / `LiquidGlassEffects` 的 Provider 注入写法：
// 那两个契约有真实的可替换实现与失败语义（未注入 ⇒ 关闭液态玻璃），而这里没有第三个实现、
// 也没有失败语义——非 Android 平台不存在"系统手势拦截"这个对手方，恒等返回 `this` 就是
// 正确语义，不是静默降级伪装；零状态的 `Modifier` 工厂也无法用 DI 表达。
//
// 本模块因此**首次出现 `androidMain` / `desktopMain` 源集**（此前只有 commonMain / commonTest）：
//   - `androidMain/.../SystemGestureExclusion.android.kt` → 转发原生扩展，与搬入前逐字等价；
//   - `desktopMain/.../SystemGestureExclusion.desktop.kt` → 恒等。
// 两者都不需要新增依赖：`foundation` 已由 `legado.kmp.compose` convention 提供，其 android
// 变体解析到的就是 AndroidX foundation（`systemGestureExclusion` 在那里）。
//
// 搬完的收益面：`FastScrollLazyColumn` / `ScrollbarLazyColumn` / `FastScrollLazyVerticalGrid`
// 的消费方共 5 个模块（`app` + `feature/{dict,tagrules,replacerules,txttocrules}`），全都已声明
// `:core:designsystem`，所以 import 零改动；同时卸掉这 4 个 Feature 模块转 CMP 时的一个硬阻塞。
//
// M1-3x-pre：`feature/{replacerules,txttocrules,dict}` 转 CMP 前的**最后一批 `:core:ui` 资产**
// 上提，共 4 个文件（包名不变 ⇒ `app` 侧 import 零改动）：
//   - `widget/components/tabRow/AppTabRow.kt`（replacerules 的分组页签）——本来就零 Android 依赖；
//     ⚠️ 同目录的 `CardTabRow.kt` **没搬**：它只有 `:app` 的 10 处消费方，没有非 Android 消费者。
//   - `widget/components/GroupManageBottomSheet.kt`（分组管理弹层）——4 条 `R.string.*` 换
//     `Res.string.*`（`group_manage` 本次新增，`edit`/`delete`/`ok` 已有）。
//   - `widget/components/rules/RuleEditSheet.kt`（`RuleEditSheet` / `RuleEditFields` /
//     `TestLineResult`，txttocrules + dict 的规则编辑弹层）——10 条换 `Res.string.*`
//     （`more_menu` 已有，其余 9 条新增）。
//   - `widget/components/contentProcess/ContentProcessUiState.kt`（纯 `@Stable` 状态类，
//     零代码改动）——它自己的 KDoc 就写着「等真有非 Android 消费者时再上提到共享层」，
//     replacerules 转 CMP 正是那个触发器。
// **零新增依赖**（4 个文件只用 Compose 标准库 + designsystem 自己的组件 + 已在的 miuix-ui）。
// 文案等价性：新增的 10 条 × 4 语言逐字取自 `:core:ui` 的 Android res，搬前脚本比对过
// `designsystem == core:ui == app`；`:core:ui` 侧因此变死的 8 条同名资源同步删除。
// 实录见 `docs/dev/feature-slicing-audit-tagrules.md` §27。

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                // 三件都必须是**有 desktop/jvm 变体的 KMP 制品**（commonMain 一份代码跨目标）：
                //   - material3 用 CMP 坐标：它本身就是 KMP 模块，android 变体内部再委托给
                //     `androidx.compose.material3:material3`，所以「android 上最终就是 AndroidX
                //     Material3」是 CMP 自动完成的，不需要按平台手写分支。版本见
                //     libs.versions.toml 的注（≠ 插件版本号，由 convention 断言）；
                //   - 图标用 androidx 1.7.8：Google 当年确实发布了它的 jvm 变体（只发到 1.7.8 这一档，
                //     1.9+ 只有 android）；CMP 自己的 icons 坐标冻在 1.7.3 且已 deprecate。
                //     两条路都不再更新——真要用新图标得迁 Material Symbols（见 cmp-module-convention.md）；
                //   - Miuix 取**不带 `-android` 后缀**的 KMP 模块（`basic.Switch` 在 miuix-ui 里，
                //     不在 miuix-core——踩过一次）。
                // M1-3h：`modalBottomSheet/AppModalBottomSheet.kt` 从 `:core:ui` 搬入（同一包名
                // `io.legado.app.ui.widget.components.modalBottomSheet`，消费方 import 零改动）。
                // 它用 `Modifier.animateContentSize`——那是 `androidx.compose.animation` 的顶层
                // 扩展，属 animation 制品（不是 foundation）。foundation 会传递带上，但按「依赖
                // 只列实际用到的」显式声明。选 CMP 坐标的理由同 material3：android 变体内部转发
                // 给 `androidx.compose.animation:animation`，版本由 CMP 交给 1.12.0 线，与
                // convention 提供的 runtime/foundation 同线。
                implementation(libs.compose.multiplatform.animation)
                implementation(libs.compose.multiplatform.material3)
                implementation(libs.compose.materialIcons)
                implementation(libs.miuix.ui)
                // M1-3f：`AppIcons` 从 `:core:ui` 搬入。它按引擎二选一返回图标，Miuix 分支用
                // `MiuixIcons` 与 `top.yukonga.miuix.kmp.icon.extended.*` —— 这两个只在
                // `miuix-icons` 里（不是 miuix-ui，也不是 miuix-core）。取不带 `-android`
                // 后缀的 KMP 坐标，0.9.3 实测有 desktopApiElements-published 变体。
                implementation(libs.miuix.icons)
                // M1-3d：`LegadoTheme.kt` 从 `:core:ui` 搬入（同一包名 `io.legado.app.ui.theme`，
                // 消费方 import 零改动）。它只依赖 CMP material3 + 下面三个库，无 `android.*`：
                // `HazeState` 来自 haze-core（materials 模块不需要）、`PaletteStyle` 来自
                // material-kolor、`Backdrop` 来自 kyant0:backdrop；`ColorSchemeMode` 来自 miuix-ui
                // （在 miuix-ui 里，不在 miuix-core——与 `basic.Switch` 同一个坑）。
                // 三者都已核对 Maven Central 的 .module 元数据，确认存在 jvm/desktop 变体：
                // haze→haze-jvm、material-kolor→material-kolor-jvm、backdrop→backdrop-desktop。
                implementation(libs.haze.core)
                // 注：**没有** `libs.haze.materials`。`HazeStyle.kt` 搬入时带着
                // `@OptIn(ExperimentalHazeMaterialsApi::class)`，但它实际只调 haze-core 的
                // `hazeSource`/`hazeEffect`/`HazeProgressive`，没有任何 materials API（materials
                // 的真实用户 `reader/ReaderMenuEffects.kt` 仍留在 `:core:ui`）。那个 opt-in 是
                // 早年用过 `HazeMaterials.*` 时留下的空壳，已随搬入一并删除 ⇒ 本切片零新增依赖。
                implementation(libs.material.kolor)
                implementation(libs.backdrop)
                // M1-3e：`LocalAppUiConfiguration.kt` 需要 `AppUiConfiguration`
                // （`:core:model` 的 commonMain，pure KMP，无 `android.*`）。
                // M1-3f：`CardDecoration.kt` 的纯决议函数直接以 `ThemeSettings` 为入参，
                // 同一依赖即覆盖。
                implementation(project(":core:model"))
                // M1-3n：`widget/components/filePicker/FilePickerSheet.kt` 从 `:core:ui` 搬入。
                // 它要把扩展名翻译成系统文件选择器要的 MIME，原先直接用 Android-only 的
                // `android.webkit.MimeTypeMap`。换成 `:core:platform` 的 `MimeTypeResolver`
                // 窄契约后，本模块不需要任何 `android.*`（Android 实现在 app 侧
                // `io.legado.app.platform.AndroidPlatformCapabilities.mimeTypeResolver()`）。
                // 依赖方向无环：`:core:platform` 只依赖 `:modules:rhino` + 若干 KMP 制品。
                implementation(project(":core:platform"))
                // M1-3i：`button/` + `button/series/` + `divider/` + `progressIndicator/` +
                // `title/SmallTitle.kt` + `SectionTitle.kt` 共 25 个原子组件从 `:core:ui` 搬入。
                // **本切片没有新增任何依赖**——这族只用 Miuix（上面的 miuix-ui）与
                // `androidx.compose.material.icons`（上面的 materialIcons，`Icons.Default.Check`
                // 属 icons-core）。搬前用「文件内零 Android-only API + 引用闭包全在 designsystem」
                // 两条机械判据筛过，跨模块闭包边只有 `ToggleChip → card.NormalCard`（已在
                // `card/AppCardSurface.kt`）。
                // M1-3j：CMP 多平台资源（`org.jetbrains.compose.resources`）。本轮搬入
                // 3 个「只因 Android `R.string` 受阻」的组件（`AppFloatingActionButton` /
                // `ReorderAccessibility` / `reader/ReaderMenuActionSquare`），它们的文案改由
                // `Res.string.*` 提供 ⇒ 替代 android-only 的
                // `androidx.compose.ui.res.stringResource`（desktop 不存在）。
                // 资源文件在 `src/commonMain/composeResources/values*/`（4 个语言）。
                // 版本与 CMP 插件严格同线（ref `composeMultiplatform`）。
                // ⚠️ 原拟的另外 4 个（`SearchBar` / `ReorderableConfigList` /
                // `topbar/TopBarButton` / `player/PlayerTocPage`）**已退回 `:core:ui`**：
                // 它们除 `R.string` 外还引用留在 `:core:ui` 的同包兄弟
                // （`AppDenseTextField` / `GlassTopAppBarDefaults` / `topBarLiquidGlassEnabled`）
                // 或平台库（coil3 / sh.calvin.reorderable / kotlinx.collections.immutable），
                // 不是纯资源阻塞 ⇒ 另开切片。
                implementation(libs.compose.multiplatform.resources)
                // M1-3p：`widget/components/AppContainerBackground.kt`（`Modifier.appContainerBackground`）
                // 从 `:core:ui` 搬入——原先卡住它的是 `BitmapFactory`/`NinePatch`（九宫格解码），
                // 那段已下沉为 `:core:platform` 的 `NinePatchLoader` 窄契约；剩下真正跨平台的
                // 加载管线（Coil）随之进共享层。coil3 是 KMP 制品（实测 Maven Central 有
                // `coil-compose-jvm`），desktop 侧同样能画背景图。
                // `LocalContext` 换成 coil3 自带的 `LocalPlatformContext`（Android 上就是同一个
                // `Context`）,`LocalConfiguration` 换成 CMP 的 `LocalWindowInfo.containerSize`。
                // 依赖方向无新增模块边：coil-compose 是外部制品，`:core:ui` 侧仍保留它自己的
                // coil 依赖（`AppBackground` 还在那边）。
                implementation(libs.coil.compose)
                // M1-3q：`widget/components/card/SelectionItemCard.kt`（`SelectionItemCard` /
                // `SelectionItemCardContent` / `ReorderableSelectionItem`）从 `:core:ui` 搬入。
                // 它引用的 `sh.calvin.reorderable.ReorderableItem` / `ReorderableLazyListState`
                // 是 KMP 制品（实测 Maven Central 的 `reorderable-3.1.0.module` 含
                // `reorderable-jvm` 变体），不是 Android-only。原先卡住它的另一项
                // `AppContainerBackground`（九宫格）已由 M1-3p 解除，剩 `R.string.edit`
                // ⇒ 沿用 M1-3j 的 CMP 资源配方（`edit` 已补进 4 个语言文件）。
                implementation(libs.reorderable)
                // M1-3x-pre：`widget/components/contentProcess/ContentProcessUiState.kt` 从 `:core:ui`
                // 搬入——`ContentProcessConfigUiState.items` 是 `ImmutableList<ContentProcessItemUi>`，
                // 所以本模块**首次**需要 `kotlinx-collections-immutable`。
                // 它是纯 KMP 制品（有 `-jvm` 变体），不带 Android/Compose 兼容面——与当初把它留在
                // `:core:ui` 的理由（「为 `@Stable` 引入 Androidx Compose BOM 会给 desktop target
                // 带来不必要的兼容面」）不冲突：那条针对的是 Compose BOM，不是这个集合库。
                // 类型不改回 `List` 是有意的：AGENTS.md 要求 Compose 渲染边界的集合用 immutable，
                // 且改成 `List` 会削弱 `@Stable` 的稳定性承诺。
                implementation(libs.kotlinx.collections.immutable)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
