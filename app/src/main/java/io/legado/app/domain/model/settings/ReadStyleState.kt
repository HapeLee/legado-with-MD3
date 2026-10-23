package io.legado.app.domain.model.settings

import androidx.compose.runtime.Stable

data class ReadStyleState(
    val items: List<ReadStyleItem> = emptyList(),
    val selectedIndex: Int = 0,
    val shareLayout: Boolean = false,
    /**
     * 排版配置的变更序号，每次发布递增。
     *
     * 排版底座 `ReadBookConfig.Config` 是可变全局、无 flow，本状态只投影了
     * items/selectedIndex/shareLayout 三项。没有本字段时，「只改了字号/行距/下划线」这类
     * 变更不会让 data class 的相等性发生变化，`StateFlow` 会把发射当成重复值丢掉，
     * 订阅方永远收不到通知。
     */
    val revision: Long = 0L,
)

@Stable
data class ReadStyleItem(
    val id: String,
    val name: String,
    val bgType: Int,
    val bgValue: String,
    val bgTypeNight: Int,
    val bgValueNight: String,
    val bgTypeEInk: Int,
    val bgValueEInk: String,
    val textColor: Int,
    val textColorNight: Int,
    val textColorEInk: Int,
)

/**
 * 列表前这么多份是内置预设：不可删、不可拖、不可插到它们前面
 * （`applyPreset(index)` 按这些下标取数据，配置缺斤少两时也靠它们补齐）。
 *
 * 放在领域模型而不是 data 层的 store：UI（主题列表的管理模式）也要用它判断"预设钉死"，
 * 而架构守卫只允许网关实现提到那个 store。
 */
const val READ_STYLE_PRESET_COUNT = 5
