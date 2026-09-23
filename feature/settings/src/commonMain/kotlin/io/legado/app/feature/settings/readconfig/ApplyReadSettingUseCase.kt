package io.legado.app.feature.settings.readconfig

/**
 * Applies runtime reader changes after a setting has entered the effective settings snapshot.
 *
 * M5-11a：从 `:app` 的 `ui/config/readConfig` 迁来。**迁移前零测试** —— 它直接调
 * `ReadBook` / `ReadConfigUpdateBus` / `postEvent`，在 `:app` 里没法单测。
 * 本片把四个平台/阅读器直连收成 [ReadConfigApplyPlatform] 之后，这张 `when` 映射表
 * （**唯一的可测逻辑**）第一次可以被断言：见 `ApplyReadSettingUseCaseTest`。
 *
 * ⚠️ 映射表**逐字保留**：哪个 intent 对应哪一组动作不是可以顺手整理的地方
 * （改错就表现为「改了设置、当前页没反应」或「无谓地重排整本书」）。
 */
class ApplyReadSettingUseCase(
    private val platform: ReadConfigApplyPlatform,
) {

    operator fun invoke(intent: ReadConfigIntent) {
        when (intent) {
            is ReadConfigIntent.HideStatusBarChanged,
            is ReadConfigIntent.HideNavigationBarChanged -> platform.updateSystemUiAndStyle()

            is ReadConfigIntent.ReadMenuBlurAlphaChanged,
            is ReadConfigIntent.ReadSliderModeChanged,
            is ReadConfigIntent.ShowReadTitleAdditionChanged,
            is ReadConfigIntent.ShowMenuIconChanged -> platform.updateActionBar()

            is ReadConfigIntent.TextFullJustifyChanged,
            is ReadConfigIntent.TextBottomJustifyChanged,
            is ReadConfigIntent.UseZhLayoutChanged,
            is ReadConfigIntent.DoubleHorizontalPageChanged -> platform.reloadContent()

            is ReadConfigIntent.ProgressBarBehaviorChanged -> platform.updateSeekBar()

            is ReadConfigIntent.PageTouchSlopChanged -> platform.updatePageSlopSquare()

            is ReadConfigIntent.NoAnimScrollPageChanged -> platform.updatePageAnim(animate = false)

            // useUnderline 进了 RenderStyle 快照，改完必须重建并重绘，否则朗读/搜索
            // 高亮线要等下一次样式变更才生效
            is ReadConfigIntent.UseUnderlineChanged -> platform.invalidateTextPage()

            // 迁移前 `updateStyle()` = `upPageAnim(true)` + `loadContent(false)`，两步顺序保留
            is ReadConfigIntent.OptimizeRenderChanged -> {
                platform.updatePageAnim(animate = true)
                platform.reloadContent()
            }
            else -> Unit
        }
    }
}
