package io.legado.app.feature.settings.reading

import androidx.annotation.StringRes
import io.legado.app.R

/** Presentation metadata only: these IDs are independent of preference/storage keys. */
enum class ReadSettingSection(@param:StringRes val titleRes: Int) {
    Screen(R.string.screen_settings),
    PageControl(R.string.page_control),
    Other(R.string.other),
}

enum class ReadSetting(
    val id: String,
    @param:StringRes val titleRes: Int,
    val section: ReadSettingSection,
    @param:StringRes val descriptionRes: Int? = null,
    @param:StringRes val keywordsRes: Int? = null,
) {
    Orientation("reading.orientation", R.string.screen_direction, ReadSettingSection.Screen),
    KeepLight("reading.keep_light", R.string.keep_light, ReadSettingSection.Screen),
    HideStatusBar("reading.hide_status_bar", R.string.pt_hide_status_bar, ReadSettingSection.Screen),
    HideNavigationBar("reading.hide_navigation_bar", R.string.pt_hide_navigation_bar, ReadSettingSection.Screen),
    DisplayCutouts("reading.display_cutouts", R.string.padding_display_cutouts, ReadSettingSection.Screen),
    TitleBar("reading.title_bar", R.string.title_bar_mode, ReadSettingSection.Screen),
    MenuOpacity("reading.menu_opacity", R.string.menu_alpha, ReadSettingSection.Screen),
    BodyToLineHeight("reading.body_line_height", R.string.read_body_to_lh, ReadSettingSection.Screen),
    ChangeAllSources("reading.change_all_sources", R.string.read_change_all, ReadSettingSection.Screen, R.string.read_change_all_s),
    FullJustify("reading.full_justify", R.string.text_full_justify, ReadSettingSection.Screen),
    BottomJustify("reading.bottom_justify", R.string.text_bottom_justify, ReadSettingSection.Screen),
    SpecialStyle("reading.special_style", R.string.adapt_special_style, ReadSettingSection.Screen),
    ChineseLayout("reading.chinese_layout", R.string.use_zh_layout, ReadSettingSection.Screen),
    EyeProtection("reading.eye_protection", R.string.eye_protection, ReadSettingSection.Screen,
        keywordsRes = R.string.settings_search_eye_keywords),
    BrightnessMode("reading.brightness_mode", R.string.show_brightness_view, ReadSettingSection.Screen,
        keywordsRes = R.string.settings_search_brightness_keywords),
    BrightnessPosition("reading.brightness_position", R.string.brightness_bar_position, ReadSettingSection.Screen,
        R.string.settings_search_brightness_condition),
    Underline("reading.underline", R.string.use_underline, ReadSettingSection.Screen),
    SliderMode("reading.slider_mode", R.string.read_slider_mode, ReadSettingSection.PageControl),
    DoublePage("reading.double_page", R.string.double_page_horizontal, ReadSettingSection.PageControl),
    ProgressBar("reading.progress_bar", R.string.progress_bar_behavior, ReadSettingSection.PageControl),
    MouseWheel("reading.mouse_wheel", R.string.mouse_wheel_page, ReadSettingSection.PageControl),
    VolumeKeys("reading.volume_keys", R.string.volume_key_page, ReadSettingSection.PageControl,
        keywordsRes = R.string.settings_search_volume_keywords),
    VolumeKeysWhilePlaying("reading.volume_keys_playing", R.string.volume_key_page_on_play, ReadSettingSection.PageControl),
    LongPressKeys("reading.long_press_keys", R.string.key_page_on_long_press, ReadSettingSection.PageControl),
    TouchSlop("reading.touch_slop", R.string.page_touch_slop_title, ReadSettingSection.PageControl),
    SliderVibration("reading.slider_vibration", R.string.enable_slider_vibrator, ReadSettingSection.Other),
    TocSheet("reading.toc_sheet", R.string.use_new_toc_sheet, ReadSettingSection.Other),
    TocSplitLength("reading.toc_split_length", R.string.no_toc_split_length_title, ReadSettingSection.Other),
    SelectionVibration("reading.selection_vibration", R.string.enable_select_vibrator, ReadSettingSection.Other),
    AutoChangeSource("reading.auto_change_source", R.string.auto_change_source, ReadSettingSection.Other),
    ThemeReminder("reading.theme_reminder", R.string.auto_switch_theme_reminder_title, ReadSettingSection.Other,
        R.string.auto_switch_theme_reminder_desc),
    ReadingAnchor("reading.anchor", R.string.reading_anchor, ReadSettingSection.Other, R.string.reading_anchor_summary),
    AloudDetachReminder("reading.aloud_detach_reminder", R.string.read_aloud_detach_reminder, ReadSettingSection.Other,
        R.string.read_aloud_detach_reminder_summary),
    SelectText("reading.select_text", R.string.selectText, ReadSettingSection.Other),
    ScrollAnimation("reading.scroll_animation", R.string.no_anim_scroll_page, ReadSettingSection.Other),
    ImageClick("reading.image_click", R.string.click_image_way, ReadSettingSection.Other),
    OptimizeRender("reading.optimize_render", R.string.enable_optimize_render, ReadSettingSection.Other),
    ClickRegions("reading.click_regions", R.string.click_regional_config, ReadSettingSection.Other,
        keywordsRes = R.string.settings_search_touch_keywords),
    DisableBack("reading.disable_back", R.string.disable_return_key, ReadSettingSection.Other),
    PageKeys("reading.page_keys", R.string.custom_page_key, ReadSettingSection.Other,
        keywordsRes = R.string.settings_search_key_keywords),
    TitleAddition("reading.title_addition", R.string.show_read_title_addition, ReadSettingSection.Other),
    MenuIcons("reading.menu_icons", R.string.show_menu_icon, ReadSettingSection.Other);

    /** Searching must never enable a prerequisite on the user's behalf. */
    fun navigationTarget(brightnessMode: String): ReadSetting =
        if (this == BrightnessPosition && brightnessMode != "2") BrightnessMode else this

    companion object {
        fun fromId(id: String?): ReadSetting? = entries.firstOrNull { it.id == id }
    }
}
