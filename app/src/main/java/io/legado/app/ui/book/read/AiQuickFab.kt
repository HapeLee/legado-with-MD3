package io.legado.app.ui.book.read

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay

private const val AI_FAB_SIZE_DP = 64f
private const val AI_FAB_TOP_LIMIT_DP = 72f
private const val AI_FAB_BOTTOM_GAP_DP = 96f
private const val AI_FAB_EDGE_SNAP_DP = 72f

/**
 * 阅读页 AI 悬浮球（应用内浮层，不申请 SYSTEM_ALERT_WINDOW）。
 *
 * - 正圆 64dp；拖动时关闭动画（snap）做到跟手，松手再 tween 吸附到左右边缘。
 * - 贴边静止数秒后半隐藏（只露出半颗），点一下恢复。
 * - 点击展开第一版样式的二级菜单：紧贴在球上方的小卡片，列出「AI 对话」与已启用技能（已排除书源编写）。
 */
@Composable
fun AiQuickFab(
    skills: ImmutableList<AiQuickSkillUi>,
    onOpenChat: () -> Unit,
    onOpenSkill: (skillId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthDp = configuration.screenWidthDp.toFloat()
    val screenHeightDp = configuration.screenHeightDp.toFloat()
    val half = AI_FAB_SIZE_DP / 2f
    // 位置是球心的 dp 坐标。允许球心贴到屏幕边线（半隐藏时露半颗）。
    val minCenterX = 0f
    val maxCenterX = screenWidthDp
    val minCenterY = AI_FAB_TOP_LIMIT_DP + half
    val maxCenterY = (screenHeightDp - AI_FAB_BOTTOM_GAP_DP - half).coerceAtLeast(minCenterY)

    // 球心初始在右下角。
    var centerX by remember { mutableFloatStateOf(maxCenterX - half) }
    var centerY by remember { mutableFloatStateOf(maxCenterY) }
    LaunchedEffect(maxCenterX, maxCenterY) {
        centerX = centerX.coerceIn(minCenterX, maxCenterX)
        centerY = centerY.coerceIn(minCenterY, maxCenterY)
    }

    var dragging by remember { mutableStateOf(false) }
    var docked by remember { mutableIntStateOf(0) } // -1 左, 1 右, 0 未贴边
    var collapsed by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    // 贴边静止 3 秒后自动半隐藏，交互中（拖动 / 菜单展开）不折叠。
    LaunchedEffect(docked, collapsed, dragging, expanded) {
        if (docked != 0 && !collapsed && !dragging && !expanded) {
            delay(3000)
            collapsed = true
        }
    }

    // 半隐藏进度：0=只露半颗，1=完整显示。
    val expansion by animateFloatAsState(
        targetValue = if (collapsed) 0f else 1f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "aiFabExpansion",
    )

    // 展示用球心 X：贴边折叠时推到屏幕边线上，正好露半颗；拖动时 snap 跟手。
    val displayCenterXTarget = when {
        collapsed && docked == 1 -> screenWidthDp.toFloat()
        collapsed && docked == -1 -> 0f
        else -> centerX
    }
    val displayCenterX by animateFloatAsState(
        targetValue = displayCenterXTarget,
        animationSpec = if (dragging) snap() else tween(240, easing = FastOutSlowInEasing),
        label = "aiFabX",
    )
    val displayCenterY by animateFloatAsState(
        targetValue = centerY,
        animationSpec = if (dragging) snap() else tween(240, easing = FastOutSlowInEasing),
        label = "aiFabY",
    )

    // 球左上角 = 球心 - 半径。
    val leftDp = displayCenterX - half
    val topDp = displayCenterY - half

    Box(modifier = modifier.fillMaxSize()) {
        // 二级菜单（第一版样式）：紧贴在球上方的小卡片，横向根据贴边方向避让。
        AnimatedVisibility(
            visible = expanded,
            modifier = Modifier.offset(x = menuX(leftDp, docked, screenWidthDp).dp, y = (topDp - 8f).dp),
            enter = fadeIn(tween(160)) + scaleIn(tween(200), initialScale = 0.9f),
            exit = fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.9f),
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = LegadoTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp,
                tonalElevation = 3.dp,
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    AppText(
                        text = stringResource(R.string.ai_quick_panel_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    SmallPlainButton(
                        onClick = {
                            expanded = false
                            onOpenChat()
                        },
                        text = stringResource(R.string.ai_chat),
                        contentDescription = stringResource(R.string.ai_chat),
                    )
                    if (skills.isNotEmpty()) {
                        AppText(
                            text = stringResource(R.string.ai_quick_skills),
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                            maxLines = 1,
                        )
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(skills, key = { it.id }) { skill ->
                                SmallPlainButton(
                                    onClick = {
                                        expanded = false
                                        onOpenSkill(skill.id)
                                    },
                                    text = skill.name,
                                    contentDescription = skill.name,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 正圆悬浮球：拖动手势 + 点击展开/收起/从半隐藏恢复。
        FloatingActionButton(
            onClick = {
                if (collapsed) {
                    collapsed = false
                } else {
                    expanded = !expanded
                }
            },
            modifier = Modifier
                .offset(x = leftDp.dp, y = topDp.dp)
                .scale(0.9f + 0.1f * expansion)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            dragging = true
                            collapsed = false
                            expanded = false
                        },
                        onDragCancel = { dragging = false },
                        onDragEnd = {
                            dragging = false
                            // 就近吸附到左右边缘；贴边即标记折叠（半隐藏）。
                            docked = if (centerX < screenWidthDp / 2f) -1 else 1
                            centerX = if (docked < 0) minCenterX else maxCenterX
                            centerY = centerY.coerceIn(minCenterY, maxCenterY)
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        val dx = dragAmount.x / density.density
                        val dy = dragAmount.y / density.density
                        centerX = (centerX + dx).coerceIn(minCenterX, maxCenterX)
                        centerY = (centerY + dy).coerceIn(minCenterY, maxCenterY)
                        docked = 0
                    }
                },
            shape = CircleShape,
            containerColor = LegadoTheme.colorScheme.primaryContainer,
            contentColor = LegadoTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = stringResource(R.string.ai_quick_fab),
            )
        }
    }
}

/** 让二级菜单横向避让球：靠近右边缘时向左展开，否则向右，并夹在屏幕内。 */
private fun menuX(fabLeftDp: Float, docked: Int, screenWidthDp: Float): Float {
    val menuWidth = 220f
    val gap = 8f
    val preferLeft = docked == 1 || fabLeftDp + gap + menuWidth > screenWidthDp
    val raw = if (preferLeft) fabLeftDp - menuWidth - gap else fabLeftDp + AI_FAB_SIZE_DP + gap
    return raw.coerceIn(8f, (screenWidthDp - menuWidth - 8f).coerceAtLeast(8f))
}
