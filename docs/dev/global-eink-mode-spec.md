# 全局电纸书模式（Global E-Ink Mode）

## 1. 概述与边界

全局电纸书模式提供位于「我的」首页（`MyScreen`、`Web 服务` 下方）的常驻一键开关，并在首次安装引导页「主题样式」步骤（`OnboardingScreen`）提供同款初始化入口。开启后在运行时统一应用电纸书主题（`appTheme == "4"`）并关闭全应用动画与实时模糊；关闭后无损恢复用户原有的主题、模糊与动画偏好。

### 边界约定

- **限定入口**：
  - 常驻入口：`MyScreen` 顶部卡片、`Web 服务` 正下方展示「电纸书模式」开关（带电纸书设备矢量图标 `AppIcons.EInkReader`）。
  - 首次引导入口：`OnboardingScreen` 第 4 步「主题样式」卡片底部提供同款开关，开启后即时禁用上方明暗模式与主题色块选择且不覆写其持久化值。
  - 设置首页不重复展示该开关。
- **不改写底层偏好**：开启时不覆盖持久化存储中的 `appTheme`、模糊与动画配置，避免退出时丢失用户原设定。
- **独立边界**：
  - 外观页（`ThemeConfigScreen`）仍直接读写 `ThemeSettingsGateway` / `AppShellSettingsGateway` 原始持久化值，不添加额外提示徽章或屏蔽遮罩。
  - 漫画二值化（`MangaSettings.enableEInk`）与阈值保持独立控制，不与全局电纸书模式联动。
  - 废弃的 `设置 → 实验室 → 墨水屏显示`（`LabSettings.eInkDisplay`）已移除。

---

## 2. 数据与持久化契约

| 层级 | 位置 | 契约 |
| :--- | :--- | :--- |
| 偏好键 | `constant/PreferKey.kt` | `const val eInkGlobalMode = "eInkGlobalMode"` |
| 领域模型 | `domain/model/settings/AppShellSettings.kt` | `val eInkGlobalMode: Boolean = false`；`NavigationAnimationStyle.None("none")` |
| 仓库映射 | `data/repository/FeatureSettingsRepositories.kt` | `toAppShellSettings()` / `toPrefMap()` 读写 `PreferKey.eInkGlobalMode` |
| 备份配置 | `help/storage/BackupConfig.kt` | `PreferKey.eInkGlobalMode` 纳入 `themePrefKeys` |
| 写入方式 | `MyViewModel.kt`、`OnboardingViewModel.kt` | 经 `AppShellSettingsGateway.update { it.copy(eInkGlobalMode = enabled) }` 原子提交 |

---

## 3. 运行时覆写规则

### 3.1 单一派生点：`AppUiConfiguration.applyRuntimeOverrides()`

`AppUiConfigurationRepository` 在 `Preferences.toAppUiConfiguration(...)` 中统一调用 `applyRuntimeOverrides()`。当 `appShell.eInkGlobalMode == true` 时，对外发布的 `AppUiConfiguration` 派生以下有效值：

| 维度 | 底层原始字段 | 有效覆写值 |
| :--- | :--- | :--- |
| 应用主题 | `theme.appTheme` | `"4"`（电纸书主题） |
| 导航动画 | `appShell.navigationAnimationStyle` | `NavigationAnimationStyle.None` |
| 文字动画 | `appShell.textAnimationLevel` | `TextAnimationLevel.Off` |
| 预测性返回 | `appShell.predictiveBackEnabled` | `false` |
| 共享元素 | `appShell.readerSharedElementEnabled` | `false` |
| 滑动动画 | `appShell.swipeAnimation` | `false` |
| 模糊 / 液态玻璃 | `theme.enableBlur`、`enableProgressiveBlur`、`mochiTopBarLiquidGlass`、`mochiScrollEdgeBlurEnabled`、`miuixTopBarLiquidGlass`、`appShell.useFloatingBottomBarLiquidGlass` | 全部 `false` |
| 深浅色与字体 | `appShell.themeMode`、`appShell.fontScale`、`theme.appFontPath` | **保留用户原值** |

`AppUiConfiguration.diffFrom(...)` 基于派生结果计算差异：当原 `appTheme != "4"` 时自动触发 `themeChanged = true`，同步通知遗留窗口/View 刷新；当原 `appTheme == "4"` 时仅即时切换运行时动画与视觉标志，无需重建 Activity。

### 3.2 非 `LocalAppUiConfiguration` 消费点对齐

以下位置不直接读取 `LocalAppUiConfiguration`，均已对齐运行时有效 E-Ink 状态：

- `help/config/AppConfig.kt`：`isEInkMode` = `shell.eInkGlobalMode || theme.appTheme == "4"`
- `help/config/ReadStyleResolver.kt`：`isEInkModeCompat` = `shell.eInkGlobalMode || theme.appTheme == "4"`（驱动阅读页正文自动切换 `ReadStyleMode.EInk` 白底黑字并保留原日/夜背景）
- `ui/config/readConfig/ReadConfig.kt`：`isEInkMode` = `shell.eInkGlobalMode || theme.appTheme == "4"`
- `ui/book/searchContent/SearchContentViewModel.kt`：订阅 `AppUiConfigurationGateway.configuration`，取 `theme.appTheme == "4"`
- `feature/reader/legacy/LegacyReaderPaginationStyleFactory.kt`：读取 `AppUiConfigurationGateway.currentConfiguration.theme.appTheme == "4"`

---

## 4. 全局零动画分层

1. **Navigation 3 路由转场（`MainNavigationTransition.kt`）**
   - `navigationPreset(...)` 命中 `NavigationAnimationStyle.None` 时直接返回 `EnterTransition.None togetherWith ExitTransition.None`。
   - `NavigationPageScene` 在 `None` 档位下不附加 `sidePushSurface` 或 `scalePageCorners`。
2. **Compose 运行时与交互反馈（`BaseComposeActivity.kt`、`AppTheme.kt`）**
   - `BaseComposeActivity` 通过 `window.decorView.compositionContext = window.decorView.createLifecycleAwareWindowRecomposer(coroutineContext = durationScale, lifecycle = lifecycle)` 注入动态 `MotionDurationScale`：开启时 `scaleFactor = 0f`，关闭时恢复缓存的系统 `ANIMATOR_DURATION_SCALE`（默认 `1f`），切换即时生效且无需重启 Activity。
   - `AppTheme.kt` 在所有主题引擎（Material / Miuix / Mochi / Oxygen / Coui）内层提供 `LocalIndication provides NoIndication`，消除点击水波纹。
3. **传统 Activity 切换（`BaseComposeActivity.kt`、`BaseActivity.kt`）**
   - 开启时在 `onCreate` 与 `finish()` 中应用零过渡动画（API 34+ 使用 `overrideActivityTransition(..., 0, 0)`，低版本回退 `overridePendingTransition(0, 0)`）。

---

## 5. UI 规范与回归测试

- **入口规范**：
  - **「我的」首页常驻入口**：在顶部卡片内、`Web 服务` 正下方放置「电纸书模式」`SwitchSettingItem`，配自定义电纸书设备矢量图标（`AppIcons.EInkReader`）；设置首页不重复展示。
  - **首次安装引导页入口**：在 `OnboardingScreen`「主题样式」卡片的主题色块下方以分割线隔开，放置同款「电纸书模式」开关行（复用 `AppIcons.EInkReader` 与 `AdaptiveSwitch`），开启时上方明暗模式与主题色块呈禁用态。
- **电纸书导航栏描边**：当生效主题为电纸书（`appTheme == "4"`）时，主界面搜索按钮与活跃 Tab 指示器不使用实心/灰底背景色，改用 `1.5.dp` `outline` 外圈描边并去除阴影，确保墨水屏高对比清晰可辨。
- **核心回归测试**：
  - `AppUiConfigurationEInkOverrideTest`：覆盖开启时覆写、保留深浅色/字体、关闭时无损还原与 `diffFrom` 语义。
  - `AppShellSettingsMappingTest`、`LabSettingsMappingTest`：覆盖偏好键原子读写与旧实验室字段清理。
  - `SettingsSearchCatalogIntegrityTest`、`ExtendedSettingsSearchTest`：保证设置搜索目录与页面锚点双向一致。
