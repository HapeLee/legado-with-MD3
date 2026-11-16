package io.legado.app.ui.book.read

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

private const val AI_FAB_SIZE_DP = 56f
private const val AI_FAB_EDGE_DP = 0f
private const val AI_FAB_TOP_LIMIT_DP = 72f
private const val AI_FAB_BOTTOM_GAP_DP = 96f

/**
 * 阅读页 AI 悬浮球（应用内浮层，不申请 SYSTEM_ALERT_WINDOW）。
 *
 * - 可拖动，松手吸附到屏幕左 / 右边缘；贴边静止数秒后半隐藏（只露出半颗球），点击可恢复。
 * - 点击展开半屏面板：并列「AI 对话」与已启用技能列表；技能只显示名称，不带描述 / 正文。
 * - 面板从底部弹出，高度约为屏幕一半；展开时圆标浮到面板顶边，仍可点击收起。
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
    val maxX = (screenWidthDp - AI_FAB_SIZE_DP - AI_FAB_EDGE_DP).coerceAtLeast(0f)
    val minY = AI_FAB_TOP_LIMIT_DP
    val maxY = (screenHeightDp - AI_FAB_SIZE_DP - AI_FAB_BOTTOM_GAP_DP).coerceAtLeast(minY)

    // 位置为圆标左上角的 dp 坐标，初始贴右下（略高于底部中央的听书胶囊）。
    var offsetX by remember { mutableFloatStateOf(maxX) }
    var offsetY by remember { mutableFloatStateOf(maxY) }
    LaunchedEffect(maxX, maxY) {
        offsetX = offsetX.coerceIn(0f, maxX)
        offsetY = offsetY.coerceIn(minY, maxY)
    }

    var dragging by remember { mutableStateOf(false) }
    var docked by remember { mutableIntStateOf(0) } // -1 左, 1 右, 0 未贴边
    var collapsed by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    // 贴边静止数秒后自动半隐藏，交互中（拖动 / 面板展开）不折叠。
    LaunchedEffect(docked, collapsed, dragging, expanded) {
        if (docked != 0 && !collapsed && !dragging && !expanded) {
            delay(3000)
            collapsed = true
        }
    }

    // 半隐藏时把球心推到屏幕边线上，只露出半颗。
    val displayXTarget = when {
        collapsed && docked == 1 -> screenWidthDp - AI_FAB_SIZE_DP / 2f
        collapsed && docked == -1 -> -AI_FAB_SIZE_DP / 2f
        else -> offsetX
    }
    // 面板展开时球浮到半屏面板顶边，保持可见可点。
    val displayYTarget = if (expanded) {
        (screenHeightDp / 2f - AI_FAB_SIZE_DP - 8f).coerceAtLeast(minY)
    } else {
        offsetY
    }
    val displayX by animateFloatAsState(
        targetValue = displayXTarget,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "aiFabX",
    )
    val displayY by animateFloatAsState(
        targetValue = displayYTarget,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "aiFabY",
    )
    val fabScale by animateFloatAsState(
        targetValue = if (expanded) 1f else 0.9f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "aiFabScale",
    )

    Box(modifier = modifier.fillMaxSize()) {
        // 半屏技能面板，从底部弹出。
        AnimatedVisibility(
            visible = expanded,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it },
            exit = slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { it },
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = LegadoTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp,
                tonalElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height((screenHeightDp / 2f).dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    AppText(
                        text = stringResource(R.string.ai_quick_panel_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
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
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            maxLines = 1,
                        )
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(skills, key = { it.id }) { skill ->
                                // 只展示技能名称，不展示描述与正文。
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

        // 可拖动圆标：点击展开 / 收起面板；半隐藏时点击先恢复完整显示。
        FloatingActionButton(
            onClick = {
                if (collapsed) {
                    collapsed = false
                } else {
                    expanded = !expanded
                }
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = displayX.dp, y = displayY.dp)
                .scale(fabScale)
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
                            // 就近吸附到左右边缘。
                            docked = if (offsetX < screenWidthDp / 2f) -1 else 1
                            offsetX = if (docked < 0) 0f else maxX
                            offsetY = offsetY.coerceIn(minY, maxY)
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        // dragAmount 是 px；density.density 是 px/dp，除一下得到 dp 增量。
                        offsetX = (offsetX + dragAmount.x / density.density).coerceIn(0f, maxX)
                        offsetY = (offsetY + dragAmount.y / density.density).coerceIn(minY, maxY)
                        docked = 0
                    }
                },
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
