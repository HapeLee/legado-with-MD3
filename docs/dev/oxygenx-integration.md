# OxygenX 组件分派接入

2026-11-18。通过现有 App* 分派组件增加 Oxygen 引擎，保留业务页面调用、设置持久化、其他引擎行为及宿主窗口契约。

计划：固定 0.0.1 分发包 → 主题 / 字体 / locals / 局部覆盖 → 基础控件与设置行 → 顶栏 / 导航 / 菜单 / 弹层 → 双产品编译、组件回归与真机验证。

依赖来源：vendor/oxygenx 中保存本地构建的非官方、experimental Maven 压缩包及原始构建信息，manifest.json 记录 SHA-256 和 dirty=true。Gradle 校验后解压到 .gradle/oxygenx，避免 clean 删除依赖，不依赖开发机外部路径或尚不存在的 GitHub Release；后续正式发布可替换为已校验的远端制品。不得把此开发快照描述为来自已发布 tag。

适配原则：Oxygen 专用控件进入 App* 分支，通用 ListItem / 日期或数字选择器保留 M3；没有对应能力的控件保留明确的 Material 实现。AppScaffold 继续承载原有背景、overlay 与 Insets；不强换业务状态类型。OxygenTheme 仅在选定引擎下接管过冲。

## 已接入的分派

| 范围 | 实现 |
| --- | --- |
| 引擎设置 / 全局主题 | oxygen 持久值与 OxygenX 选项；沿用原设置 Intent / Gateway |
| 配色 / 字体 | 同源 M3 + Oxygen 色板，OxygenTypography 与 LegadoTypography 映射；透明浅色明确使用宿主明暗状态 |
| 局部覆盖 | ProvideColorSchemeOverride 同步 Oxygen；popup 仍沿用宿主既有主题重放入口 |
| AppIcons | 27 个操作 / 播放器属性与五个目的地显式分派，128 个 Outlined / wght=300 符号 |
| AppText / AppIcon | Oxygen 下读取桥接 locals，支持更近层的 M3 provider；其他引擎保持原实现 |
| 输入框 / 搜索中的输入框 | AppTextField 的 state / String 重载进入 Oxygen，保留变换、IME、slots 与错误语义；标签映射为固定标签 |
| 基础交互 | Primary / SecondaryButton、AppIconButton、AdaptiveSwitch / TinySwitch、AppSlider、AppRadioButton、AppCheckbox、ActionChip / ToggleChip |
| 页面组件 | 普通 / 大顶栏与滚动行为、顶栏静态操作按钮、AppTabRow、AppNavigationBar / Item、线性 / 环形 / loading 进度、向量 FAB |
| 容器与弹层 | 卡片底层 Surface、RoundDropdownMenu / Item（覆盖 AppChoiceDropdown / AppActionMenu 共享路径）、AppAlertDialog |
| 下拉刷新 | 无自定义指示器偏移时走 Oxygen；topPadding 非零时保留原 M3 行为 |

OxygenX 新增 NavigationBar 的 RowScope 槽位 API，保留原列表 API，避免在业务页面收集或重建导航模型。M3 Scaffold / Card 可能覆盖根级内容 locals，因此实际 Oxygen 分派边界重新提供单向桥接，按钮禁用态和输入槽位样式仍能传给 AppText / AppIcon。

## 自定义主题色开关（2026-11-18）

OxygenX 与 Miuix / COUI 共用现有“自定义主题色”设置（useMiuixMonet），不新增存储字段或修改已保存的主题选择。

- 关闭：全局使用 OxygenX 原生 lightColorScheme / darkColorScheme，保留精确的开关、禁用、按压等扩展颜色；M3 与 Legado 宿主角色从同一默认色板映射。旧的自定义 / 透明主题、种子色、底栏主次色不会继续覆盖默认系列。
- 开启：恢复已保存的主题选择，包括系统动态色、自定义主色与夜间颜色；显式页面局部色板覆盖仍生效。
- 纯黑、字体与明暗模式继续独立生效。关闭开关时隐藏配色列表与自定义颜色入口，开关本身可在 OxygenX 设置页操作。

回归先复现了默认色板不一致、残留透明模式与开关缺失三个失败场景，再修复。最终自动化结果记录在下方。

开关增量验证：OxygenEngineTest 13 项、原有 AppThemeEngineTest 12 项、ThemeConfigCouiThemeContractTest 12 项，共 37 项零失败；双产品 Kotlin 编译、Debug APK 和随构建执行的架构门禁通过。覆盖完整原生色板相等、明暗切换、恢复已保存自定义主次色、动态取色、纯黑与透明残留、默认模式下种子色回退、设置页开关事件和配色列表门控。设置页预览已更新为默认系列色并目视检查。日志为 build/oxygenx-default-colors-before.log（修复前 3 项失败）及 build/oxygenx-default-colors-final.log（修复后通过）；本次未重复上一轮双产品全量 Lint，也未进行真机验证。

## 明确保留的宿主实现

AppScaffold 的背景图、overlay、Insets 与采样所有权不变；AppModalBottomSheet 继续使用原有宿主窗口 / 返回 / 手势配置。M3 的 ListItem / 日期选择、三态复选框、竖向滑块、全屏搜索建议、任意内容 FAB 及应用自定义浮动底栏仍保留原能力，不假装与 Oxygen 的同名 API 完全等价。Oxygen 控件逐步替换外观，并不要求删除所有 M3 依赖。

不改业务页面参数、导航、DI、数据与阅读器行为。新增枚举后，现有 when 的 Material 兼容路径显式包含 Oxygen；这些兼容分支与上述原生分派在代码中分别列出，不改变旧引擎选择。

## 验证

- OxygenX：spotlessCheck、Desktop / Wasm、API 门禁、Android signed release 与全平台 Maven 发布通过；Desktop 108 项 / 零失败，14 对双语文档通过。导航槽位 API 已补 catalog 和双语说明。
- 目标应用首轮：core / comic Debug 编译、Core APK、6 项 Oxygen 回归与原有 12 项 AppThemeEngineTest 通过。
- 最终轮：OxygenEngineTest 10 项 + AppThemeEngineTest 12 项全部通过；包括设置行单次回调、导航分派、错误无障碍语义与无模糊采样时保持实色。Core / Comic Debug 编译、双产品 APK、verifyConfigArchitecture 与双产品 lint 全部通过。Lint 保留原基线和非阻断警告，未放宽门禁。
- 设置页组合预览位于 app/build/outputs/oxygenx-preview.png，已目视检查；由 Robolectric Native Graphics 渲染，包含真实 App 顶栏、状态输入、滑块、设置开关和两类按钮，不冒充真机截图。
- 当前 ADB 无设备；尚未进行本轮真机视觉、IME、预测式返回或性能实测，不以 Robolectric 或构建通过替代。

首次组合门禁的复用 Gradle 进程报告元空间不足，Lint 阶段失败；未当作通过。最终轮使用 --no-daemon --max-workers=1 --no-parallel 和一次性的 MaxMetaspaceSize=2g 复查，不修改项目常驻 JVM 配置。

日志：build/oxygenx-integration-tests.log、build/oxygenx-integration-verify.log、build/oxygenx-final-verification.log、build/oxygenx-final-fresh.log；均为本地忽略产物。没有自动提交 / 推送或创建 Release。

缓存路径收尾验证：解压位置移至 .gradle/oxygenx 后，715 份文件（含完成标记）与已验收的原缓存逐字节一致；重新执行 Core 的 AAR 元数据 / 重复类检查和双产品 Kotlin 编译全部通过，见 build/oxygenx-repository-cache-verify.log。UI 与库制品字节未改变，没有重复运行已通过的 UI / Lint 分析。

## AppIcons 分派与统一图标（2026-11-18）

新增 `oxygenx-icons-material` BOM 管理依赖；`AppIcons` 的 27 个操作 / 播放器属性与五个主目的地均显式分派 Oxygen，不再从默认 Material 分支取图标。其他引擎保留原映射，COUI 的行首图标隐藏规则不变。Oxygen 底栏保持 Outlined / FILL=0，选中态交给颜色和指示器。

图标统一 Google Material Symbols Outlined / opsz=24 / wght=300 / FILL=0 / GRAD=0，Apache-2.0。源库原有 100 个扩展符号保留，加入 14 个核心重导出和 14 个应用缺口，共 128 个独立矢量；核心 14 个由 Rounded 统一为 Outlined，`OxygenIcons` 原有名称保留，两个模块复用同一对象。核心组件不需要引入全部扩展图标。方向性返回、列表、书架图标使用 autoMirror；上一首 / 下一首保持方向。

更新本地未发布 0.0.1 Maven 快照，manifest SHA-256 为 `bbc7d0c0a232fe6f347aced8e424362dfe1894de439ccbb55f466f344f91378a`。Gradle 已校验并在 `.gradle/oxygenx/<sha>/maven` 准备依赖；仍无远端 Release。

本轮自动化通过：Core 39 项（AppIconsOxygenTest 2、OxygenEngineTest 13、AppThemeEngineTest 12、ThemeConfigCouiThemeContractTest 12），Comic 的 AppIconsOxygenTest 2 项，全部零失败 / 零跳过。覆盖全部入口、五引擎切换、两种底栏选中态、实际 AppIcon 的 RTL 像素渲染与 32 个图标的完整尺寸。自动渲染预览 app/build/outputs/oxygenx-app-icons.png 已目视检查。

Core / Comic Kotlin 编译、Debug APK、AAR 元数据与重复类检查、verifyConfigArchitecture 通过；未重复旧轮全量 Lint或真机验证。APK 为 app/build/outputs/apk/appCore/debug/app-app-core-arm64-v8a-debug.apk 与 app/build/outputs/apk/appComic/debug/app-app-comic-arm64-v8a-debug.apk。

库侧 Desktop 113 项、Wasm、API check、Android signed release、Maven publication、两张新明暗图集与 14 对文档通过。核心 API 不删除，扩展 API 新增 28 个属性；API 仍 experimental。

日志：build/oxygenx-app-icons-final.log（测试 + 双产品打包）、build/oxygenx-app-icons-preview-verify.log（完整尺寸预览与最终测试复核）。首次未限制类文件扫描的运行启动大量无关 JVM，已中止且未计入通过；最终运行使用忽略目录下的 build/oxygenx-icon-test-scope.init.gradle，在 forkEvery=1 前限定上述四个 Core 测试类及一个 Comic 类，保留每类独立 JVM 的隔离，不改项目测试配置。

## Dropdown 经典 M3 样式（2026-11-18）

Oxygen 原生菜单改为独立 4dp 圆角、3dp 投影、无描边，默认 14sp / 20sp 标签、48dp 最小行高、16dp 左右内距和 12dp 图标间距；宽度默认 112–280dp，长文字换行、长菜单滚动，定位支持 RTL 与窗口边缘回退。颜色继续随 Oxygen 默认 / 自定义主题色开关变化。

RoundDropdownMenu 在原生内容槽内重新建立 Oxygen → M3 locals；RoundDropdownMenuItem 的文字、前后图标各自在原生槽位内桥接，避免 AppText / AppIcon 沿用外层字号或颜色。禁用态使用禁用色，启用时保留显式文字色；contentPadding、interactionSource 实际传入新增重载。库原有 API 签名保留。

自动化：Core 42 项、Comic 5 项全部零失败 / 零跳过，包括每个产品 3 个 OxygenDropdownMenuTest；覆盖实际 Popup 明暗渲染、14sp / 禁用图标 locals、自定义内距和交互源、选中关闭一次、40 项末项滚动可达。Core / Comic Kotlin 编译、Debug APK、AAR 元数据 / 重复类检查、verifyConfigArchitecture 通过。未重复全量 Lint、手机或性能验收。

图集为 app/build/outputs/oxygenx-dropdown-light.png 与 oxygenx-dropdown-dark.png，来自 Robolectric Native Graphics，已目视检查。库侧 Desktop 122 项、Wasm、API、签名 catalog、独立 Maven 消费示例与 15 对双语文档通过。日志为 build/oxygenx-dropdown-final-snapshot.log；临时 class include 范围在忽略目录的 build/oxygenx-icon-test-scope.init.gradle，保留每类独立 JVM。

最终 0.0.1 本地未发布快照 SHA-256：`33544012670bd221120bc4909fb36d588147e2463debf0b68f0da97f288a9fc1`。末次仅修正文档表述，重新发布的 8 个 Android AAR 与已验收制品逐字节一致；新缓存与 AAR 元数据检查见 build/oxygenx-dropdown-metadata-final.log。

## M3 字体语义与菜单动效（2026-11-18）

ProvideOxygenTheme 直接从当前 MaterialTheme.typography.toOxygenTypography() 投影 15 个基础角色，保留完整 TextStyle 与逐角色字体设置；停止重写宿主 LocalLegadoTypography。原映射把 titleSmall 指向旧 titleMedium，导致书源标题等文本与 Material 分支不一致；现在沿用宿主原有 M3 / Legado 角色。

OxygenTypographyTest 使用 BookSourceScreen → ReorderableSelectionItem 实际共用的 SelectionItemCardContent，在 Material / Oxygen 间切换并读取真实 TextLayoutResult；标题均为 titleSmall 的 14sp / 20sp，全部 Legado 字体角色一致。另覆盖自定义 17sp titleSmall / 31sp headlineMedium / labelSmall 字距与字体保留。预览 app/build/outputs/oxygenx-source-typography.png 为原生软件渲染，已复核。

菜单默认支持基于锚点的 0.8→1 缩放和独立透明度通道；关闭后保留至两通道结束，快速反向从当前值接续。原 DropdownMenu 签名保留，新重载提供 motion / fadeMotion。库侧虚拟帧验证进退、反向、保留至结束及不逐帧重组；仍为 draft，M3 类比不等于真机动效校准。

自动化：库侧 Desktop 129 项 / Wasm / API / 15 对文档 / 独立 Maven 消费示例通过；6 张文字基线按新字体复核更新，滑块与图标 6 张未改。应用 Core 44 项、Comic 7 项全部零失败 / 零跳过，双产品编译、APK、架构 / AAR 元数据 / 重复类门禁通过。保持逐类独立 JVM，通过忽略目录的定向 class include 控制扫描范围。本轮未重复全量宿主 Lint、手机或性能验证。

最终本地 0.0.1 快照 SHA-256：`a90a0dc9b479763624af47586bbe11e7050a5550a03565769f11ae82a7ee1744`；日志 build/oxygenx-material-type-menu-final.log。没有远端发布。

## 顶栏直接使用 M3 字体角色（2026-11-18 最新决定）

用户决定不为旧字号添加专用覆写。上一轮尚未发布的 expandedAppBarTitle / collapsedAppBarTitle 及其专用 YAML 令牌已删除。普通 TopAppBar 直接使用 titleMedium（默认 16sp / 24sp），LargeTopAppBar / PagerTopBar 展开时使用 displayMedium（45sp / 52sp），收拢到 titleMedium。采用 Compose TextStyle 插值，保留两个主题角色各自的完整字体属性。

ProvideOxygenTheme 继续投影当前 MaterialTheme 的 15 个角色，不添加独立配方；LocalLegadoTypography 与书源 titleSmall 14sp / 20sp 保持不变。OxygenTypographyTest 使用实际 GlassTopAppBar / GlassMediumFlexibleTopAppBar 的 TextLayoutResult 验证普通 16、展开 45、收拢 16，Material titleLarge 仍为 22sp。原生软件渲染预览为 app/build/outputs/oxygenx-appbar-titles.png。

库侧 Desktop 131 项 / Wasm / API / Spotless、签名 catalog、独立 Maven 消费示例与 15 对文档通过；12 张基线未变，另有实际 UI 用例验证两个角色使用不同字体族 / 字号 / 行高 / 字重 / 字距时端点正确。库旧字体构造与 JVM copy 兼容桥保留，仅收回上一轮未发布的配方 API。

宿主 Core 45 项、Comic 8 项定向回归零失败 / 零跳过，双产品 APK 和架构门禁通过。末次清理旧字号源码注释后，以最终快照再次通过双产品 6 项字体验证、双 APK、AAR 元数据 / 重复类 / 架构门禁。日志为 build/oxygenx-appbar-m3-final.log 与 oxygenx-appbar-m3-final-snapshot.log。最终未发布 0.0.1 快照 SHA-256：`759d5821fd19afd4c1c2c2e70f6256190c2809337561780dd7e124fdc6442684`。本轮按用户范围只做自动验证，未重复全量宿主 Lint、手机或性能验收。


## 滚动边界与下拉刷新

OxygenX 依据嵌套滚动后实际留下的纵向位移起弹簧，首次触发忽略半像素以内的余量。惯性已经产生边界位移时才传入同向余速。刷新距离只计算手势下拉，松手时在惯性前判定；顶栏未展开时，下拉优先交给顶栏。

Oxygen 的 AppPullToRefresh 统一使用库组件，传入实时顶栏门控，使用 indicatorTopPadding 保留浮动顶栏下的指示器位置。Core / Comic 的 OxygenPullToRefreshTest 覆盖带 40dp 偏移的实际分派，确认顶栏先展开、惯性不计入拉距、实际下拉只刷新一次。

库侧 134 项 Desktop 检查（含真实 LazyColumn 中段甩动及触顶逐帧对照）、Wasm、API、独立 Maven 消费、签名 catalog 和双语文档通过。宿主 Core 46 项、Comic 9 项定向测试，双 Debug APK 与架构门禁通过。本轮为自动化验证。日志为 build/oxygenx-scroll-boundary-final.log，同步的本地依赖 SHA-256 为 `b672d0de653cf7f98e186e947543f974bd8005c57b0c88f1bc17a4b8860931de`。


## 顶栏操作间距与书架搜索波纹

带长按的书架搜索原先经宿主 SeriesButton 渲染，和 catalog 的 TopAppBarAction 使用不同尺寸及波纹。Oxygen 顶栏图标、长按操作和菜单锚点共用库的 TopAppBarAction，默认 48dp 触摸目标和 36dp 无边界波纹半径。普通、大、横划顶栏的操作槽使用同一 8dp 默认间距。库保留原重载，长按重载透传回调与无障碍操作名。

Desktop 136 项检查通过，含三种顶栏的位置检测，以及使用实际按压像素验证波纹扩展到 48dp 触摸目标外。Wasm、API、签名 catalog 与独立 Maven 消费通过。宿主 Core 47 项 / Comic 10 项定向测试通过，覆盖书架同款搜索和菜单锚点的间距，以及点击、长按和菜单展开。Core / Comic Debug APK 和架构门禁通过。本轮为自动化验证，日志为 build/oxygenx-topbar-actions-final.log。同步的本地依赖 SHA-256 为 `523e3e8143bced8c9635977b7e2adb4a583feb429bb53cfde2632a27bb1dca5c`。


顶栏间距复核以原 catalog 横划顶栏的双按钮排布为准，移除额外的 8dp 后，三种顶栏的默认图标中心距从 56dp 恢复到 48dp。书架的 Search / MoreVert 均为 24dp 图标，后者轮廓较窄，因此按图标中心和实际渲染复核。Core / Comic 各 1 项实际视图回归通过，检查图标位置、点按、长按与菜单展开，双 Debug APK 和架构门禁通过。预览为 app/build/outputs/oxygenx-bookshelf-actions.png。最新本地快照 SHA-256 为 `9ea86f7702b6e43246bba6a35f9bdfbaa78131d9d5a0b0c7dfe243b72edfaa63`，日志为 build/oxygenx-topbar-optical-final.log。


## 复核并恢复迁移前的折叠字号

M3 迁移把普通与折叠标题从原 type.appbar.title（18sp / 24sp）换成 titleMedium（16sp / 24sp），catalog 和宿主同步受到影响。恢复原顶栏令牌后，展开标题用 M3 displayMedium，普通与折叠标题同为 18sp / 24sp。

Core / Comic 各 4 项定向测试，双 APK 与架构门禁通过。OxygenTypographyTest 读取实际标题布局，并核对 Material titleMedium 16sp 与 titleLarge 22sp。普通、折叠标题的原生渲染在 app/build/outputs/oxygenx-appbar-titles-collapsed.png。日志为 build/oxygenx-collapsed-title-final.log。本地快照 SHA-256 为 `589e2d6cf581efc68099e46689584df3c5d1d5ef35a764fd49aae5ca6b73761e`。


## 普通和折叠顶栏采用 M3 强调标题

Oxygen 普通与折叠顶栏使用实际 Material3 titleLargeEmphasized（默认 22sp / 28sp、500，普通 titleLarge 为 400）。Android M3 1.5.0-beta01 的旧 Typography() 构造会用普通样式填入强调角色。Oxygen 分支从支持 fontFamily 的构造取默认强调样式，带上宿主字体后传入库适配器。catalog 使用相同默认指标，展开态使用 displayMedium。

Core / Comic 各 4 项定向验证通过，包括实际布局字号、行高、字重与强调角色自定义样式传递。两份 Debug APK 与架构门禁通过。原生预览位于 app/build/outputs/oxygenx-appbar-titles-collapsed.png，日志为 build/oxygenx-emphasized-title-final-verified.log。本地快照 SHA-256 为 `0388eacd1ad090685d69d70f94126cfbe37f8f38f7a0a6f79d458ac5b55d5216`。


## 常规顶栏字重

普通和折叠标题直接使用 M3 titleLarge（默认 22sp / 28sp、400），展开使用 displayMedium。宿主回到共用 M3 排版投影，继承实际 titleLarge 的字体及样式。Core / Comic 各 3 项字体验证通过，测试读取实际字重并复核折叠态原生渲染。双 Debug APK、各自的 APK 分包完整性和架构门禁通过。Comic 首次增量打包失败，移开未完成的产物后重建成功。日志为 build/oxygenx-regular-title-final.log 和 build/oxygenx-regular-title-comic-retry.log。本地快照 SHA-256 为 `b6a255b76a9abb44422481f50d03c084de54c366e5ff02697128b112653c8228`。


## 按 LE2100 设置页校准顶栏

普通和折叠顶栏使用 74dp 高度，18sp / 24sp 标题，字体和字重由宿主 M3 titleLarge 提供。紧凑导航图形用 16dp 画布，宿主 TopBarNavigationButton 读取槽位尺寸，导航触摸目标为 48dp，紧凑内容中心下移 4dp。展开标题沿用 displayMedium，书架操作和菜单沿用既有尺寸。

LE2100 同机复拍栏高为 208px，catalog 折叠「设置」墨迹 96×45px，系统 95×45px。Core / Comic 各 5 项字形、按钮与刷新回归通过，双 Debug APK 和架构门禁通过。日志为 build/oxygenx-le2100-calibration-final.log。本地快照 SHA-256 为 `a0cc6eefbf5902a70a30204466479c9aa19c26cfc031e28d239aedb0949be2c3`。
