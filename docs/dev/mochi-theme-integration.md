# Mochi 主题接入边界

## 配置与主题

主题风格使用 `AppShellSettings.composeEngine` 的持久化字符串：`material`、`miuix`、`mochi`。
`ThemeResolver.resolveComposeEngine` 在 UI 边界解析为 `ComposeEngine`；未知值回退 Material，
保留原始设置值和大小写兼容。

Mochi 使用 Maven Central 的 `io.github.feice11:mochi-compose:0.2.0`，以及同版本 `mochi-ios` 的材质配方。
不启用 iOS 整套外观、字体、导航宿主，不引入 Nothing 或图标包。

`AppTheme` 仍负责解析配色、深浅色、纯黑、自定义颜色和字体。Mochi 分支复用
`MaterialThemeWrapper`，再由 `ProvideMochiTheme` 把已解析的颜色角色和字体映射给 `MochiTheme`。
保留 Material 主题，使尚未适配的 Material 组件与 Mochi 控件使用同一套配色。没有第二个主题控制器
或设置状态所有者。局部 `ProvideColorSchemeOverride` 同步更新两套主题。

主题内容通过 `movableContentOf` 保持组合身份，切换风格时保留内容中的 `remember` 状态。
系统栏图标仍由现有宿主管理，Mochi 的 `systemBarAppearance` 为 false。

## 组件覆盖

| 公共入口 | Mochi 实现 |
|---|---|
| `PrimaryButton` / `SecondaryButton` | Mochi Filled / Outlined 按钮 |
| `AdaptiveSwitch` / `TinySwitch` | Mochi 开关 |
| `AppSlider` | Mochi 滑块 |
| `SwitchSettingItem` | 保留现有设置行布局，使用 Mochi 开关；整行只暴露一个切换动作 |
| `SliderSettingItem` / 紧凑滑块设置项 | 保留展开、输入、默认值和提交行为，通过 `AppSlider` 使用 Mochi 滑块 |
| `AppAlertDialog` | 原生 Mochi Dialog；保留自定义内容、确认禁用状态和调用方关闭时机 |
| `RoundDropdownMenu` / `RoundDropdownMenuLazy` | 原生 Mochi Popover；适配现有独立触发器，长菜单仍使用 LazyColumn |
| `RoundDropdownMenuItem` | G2 外形、Mochi 按压形变，保留 InteractionSource、选中与禁用语义 |
| `GlassTopAppBar` / `GlassSmallTopAppBar` | 原生 Mochi TitleBar |
| `GlassMediumFlexibleTopAppBar` | Mochi TitleBar + 大标题，接入现有嵌套滚动与折叠状态 |
| 顶栏按钮 / 合并按钮组 | Mochi BarButton / BarIconButton / BarGroup；长按按钮保留 combinedClickable 并使用原生玻璃表面 |
| 首页普通 / 悬浮底栏 | 原生 Mochi TabBar；保留目的地顺序、自定义图标、标签策略和重复点击回调 |
| `AppModalBottomSheet` | G2 外形，保留 Material 宿主的自定义 Insets 和禁用手势契约 |

`MochiControls` 适配层补齐键盘焦点和操作、禁用语义、最小交互尺寸及滑块无障碍步进与提交回调。
控件名称沿用调用方的本地化文字或无障碍标签。

其他组件在枚举分派中明确与 Material 共用现有实现，包括 Scaffold 布局宿主、
输入框、卡片、通用图标按钮、选择控件和进度指示器。悬浮底栏进入听书展开状态时，
仍由既有紧凑导航和播放控制变形宿主接管。不能把这个切片称为全部组件已迁移。
现有 Navigation 3、Insets、预测返回和阅读器 View 渲染宿主继续承担原职责。
菜单使用原生 Popover 的弹簧与形变；现有分离式菜单契约没有原生 Dropdown 的共享按钮形变。
MochiNavHost 的跨页面共享顶栏动画也不适用于当前 Navigation 3 宿主。

## 依赖兼容与可选效果

工程保持 Compose 1.13.0-beta01、Backdrop 2.0.1、Capsule 2.1.3 和 MaterialKolor 5.0.1，
不为接入 Mochi 降级现有依赖。

Mochi 0.2.0 声明的 Backdrop 1.0.6 会解析为工程的 2.0.1。其玻璃路径仍引用接收
`android.graphics.RenderEffect` 的旧 `BackdropEffectScope.effect`，而 2.x 接收 Compose
`RenderEffect`。`app/mochi-compat.gradle` 注册可缓存 AAR artifact transform，
仅对固定版本 AAR 中 `GlassPipelineKt` 的这一个旧方法调用改写，转向
`MochiBackdropCompat.effect`，后者把 Android RenderEffect 转成 Compose RenderEffect 再交给 Backdrop 2。
转换严格要求恰好命中一次；升级 Mochi 时必须重新审查，不能直接拓宽匹配规则。
测试、D8、R8 使用同一份转换后的产物，不降级 Backdrop、不用 `dontwarn` 隐藏错误。
兼容逻辑使用 Groovy applied script，避开当前 Lint/FIR 分析 Kotlin applied script 生成类时的崩溃；
不关闭 Lint，也不新增抑制或放宽基线。

顶栏按钮样式选择「液态玻璃」或开启悬浮底栏液态玻璃时，API 33+ 启用 Mochi 的真实材质；
渐进模糊开关控制 scroll-edge 材质。较低系统或没有来源时走库的纯色回退。
G2 外形与原生按钮、菜单、Dialog、TabBar 的弹簧和按压动画无需单独开启。

`AppScaffold` 只录制内容与背景图，不把顶底栏录进它们自己的玻璃源。
首页浮栏使用单独的分页内容来源，避免采到自身；页面外打开的弹层通过主题内注册表取得
打开时下方的来源。`disableContentSampling` 同时关闭 Mochi、Haze 和 Backdrop 的内容采样，
显式登记空来源，避免 WebView 页面的浮层误采到其他页面。玻璃材质仍属于上游 Beta 能力，
需要真机 GPU、滚动和叠层性能验证。

后续扩展组件时，在公共组件内部增加真实的 Mochi 分支，保留原有参数、状态和无障碍行为；
业务页面继续调用公共入口。未接入的能力应保留明确的共用实现，不增加静默空实现。

## 验证入口

`MochiControlsTest` 使用实际 Mochi 主题和控件，覆盖 API 26 的基础交互，以及 API 35 的按钮和
开关触摸／键盘操作、禁用状态、设置行单次切换、滑块拖动／无障碍步进与提交。
`AppThemeEngineTest` 覆盖运行时风格和深浅色切换、内容状态保留、自定义颜色、局部配色和字体映射。
现有设置映射、无障碍和 Insets 回归继续保留；Insets 用例已包含 Mochi 的共用宿主路径。
`MochiGlassCompatibilityTest` 在 API 31 / 35 实际调用发布包的玻璃选色管线，防止旧 ABI 回归。
`MochiOverlaysAndBarsTest` 覆盖确认关闭所有权、原生菜单选择和长列表、隐藏标签时底栏名称与
重复点击 / 键盘，以及禁用内容采样的来源隔离。
顶栏还覆盖紧凑宽度、1.8 倍字体、多操作按钮的标题留白、折叠和隐藏视觉文字时的语义名称。
Mochi 长按动画使用 Compose 测试时钟；不要把按住期间持续逐帧更新的用例加入旧的手动
`ShadowPausedLooper.idleFor` 测试，否则暂停时钟可能无法推进到松手。

定向测试可通过下面的命令运行；两个变体分别指定测试筛选参数：

```powershell
.\gradlew.bat :app:testAppCoreDebugUnitTest --tests '*Mochi*Test' --tests '*AppThemeEngineTest' :app:testAppComicDebugUnitTest --tests '*Mochi*Test' --tests '*AppThemeEngineTest' --max-workers=1 --no-parallel --no-configuration-cache
.\gradlew.bat :app:assembleAppCoreDebug :app:assembleAppComicDebug :app:assembleAppCoreRelease --max-workers=1 --no-parallel --no-configuration-cache
```

完整编译多个变体时，单 worker 参数用于限制验证进程的并行开销，不修改工程的全局内存配置。
JVM 语义／交互测试和构建不能替代真机 TalkBack、窗口行为与渲染性能实测。
