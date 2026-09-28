package io.legado.app.ui.book.readaloud.casting

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.ui.theme.LegadoTheme

/**
 * 角色在配音页的档次。官方人物档案里存的是 `male_lead` 这类英文键，
 * 这一页要把男女主一眼挑出来，所以按它分三档画整张卡。
 */
enum class CastRoleTier {
    Lead,
    Supporting,
    Unassigned,
}

@Stable
data class CastRoleCardVisuals(
    val containerColor: Color,
    val border: BorderStroke?,
    val elevation: Dp,
)

fun castRoleTierOf(role: String): CastRoleTier = when (role) {
    BookCharacterProfile.ROLE_MALE_LEAD, BookCharacterProfile.ROLE_FEMALE_LEAD -> CastRoleTier.Lead
    BookCharacterProfile.ROLE_MALE_SUPPORTING,
    BookCharacterProfile.ROLE_FEMALE_SUPPORTING,
    -> CastRoleTier.Supporting

    else -> CastRoleTier.Unassigned
}

/**
 * 角色键的中文名。
 *
 * 官方那四个键是英文常量，档案里存的就是它们本身；直接显示出来就是用户看到的 `female_lead`。
 * 未知值返回 null，调用方保留原样，方便暴露真正的新增取值。
 */
@StringRes
fun castRoleLabelRes(role: String): Int? = when (role) {
    BookCharacterProfile.ROLE_MALE_LEAD -> R.string.voice_role_male_lead
    BookCharacterProfile.ROLE_FEMALE_LEAD -> R.string.voice_role_female_lead
    BookCharacterProfile.ROLE_MALE_SUPPORTING -> R.string.voice_role_male_supporting
    BookCharacterProfile.ROLE_FEMALE_SUPPORTING -> R.string.voice_role_female_supporting
    else -> null
}

/**
 * 整卡外观，不是文字标签的外观：男女主拿主色渐变描边＋投影＋主色底，
 * 男女配只有淡底＋细描边，其余（旁白、没有角色）保持默认。
 *
 * 取色全部走 LegadoTheme，深色下 primaryContainer / secondaryContainer 会自动换成对应的一套。
 */
@Composable
fun castRoleCardVisuals(tier: CastRoleTier): CastRoleCardVisuals {
    val colors = LegadoTheme.colorScheme
    return when (tier) {
        CastRoleTier.Lead -> CastRoleCardVisuals(
            containerColor = lerp(colors.surfaceContainerLow, colors.primaryContainer, 0.32f),
            border = BorderStroke(
                width = 1.5.dp,
                brush = Brush.linearGradient(listOf(colors.primary, colors.tertiary)),
            ),
            elevation = 4.dp,
        )

        CastRoleTier.Supporting -> CastRoleCardVisuals(
            containerColor = lerp(colors.surfaceContainerLow, colors.secondaryContainer, 0.18f),
            border = BorderStroke(width = 1.dp, color = colors.outlineVariant),
            elevation = 0.dp,
        )

        CastRoleTier.Unassigned -> CastRoleCardVisuals(
            containerColor = colors.surfaceContainerLow,
            border = null,
            elevation = 0.dp,
        )
    }
}
