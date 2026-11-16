package io.legado.app.ui.book.read

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.collections.immutable.ImmutableList

/**
 * 阅读页 AI 悬浮小窗。
 *
 * 收起态是一个小圆标，展开态在其上方弹出面板：并列提供「开始对话」与已启用技能列表。
 * 复用听书胶囊的顺序动画思路，但只做应用内浮层（不申请 SYSTEM_ALERT_WINDOW），
 * 并固定在右下角，避免和底部中央的听书胶囊 / 阅读胶囊组抢占同一位置。
 */
@Composable
fun AiQuickFab(
    skills: ImmutableList<AiQuickSkillUi>,
    onOpenChat: () -> Unit,
    onOpenSkill: (skillId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    // 收起态把圆标缩到 0.86 倍，展开时恢复；用同一进度驱动面板出现，观感连贯。
    val fabScale by animateFloatAsState(
        targetValue = if (expanded) 1f else 0.86f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "aiFabScale",
    )

    Box(modifier = modifier.fillMaxSize()) {
        // 面板与圆标作为一个整体锚在右下角；面板在圆标上方展开。
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 88.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedVisibility(
                visible = expanded,
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
                        Spacer(Modifier.padding(vertical = 4.dp))
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
                            LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
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

            FloatingActionButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.scale(fabScale),
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
}
