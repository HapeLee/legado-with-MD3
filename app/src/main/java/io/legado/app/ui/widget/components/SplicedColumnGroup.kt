package io.legado.app.ui.widget.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.title.AdaptiveTitle
import top.yukonga.miuix.kmp.basic.Card as MiuixCard

/**
 * Settings Group Container by https://github.com/wxxsfxyzm/InstallerX-Revived
 * Edit by @Kudomaga
 * @param title the title of the group
 * @param content a list of composable that will be displayed in the group
 * @param modifier Modifier
 */
@Composable
fun SplicedColumnGroup(
    modifier: Modifier = Modifier,
    title: String = "",
    content: @Composable ColumnScope.() -> Unit,
) {
    val composeEngine = LegadoTheme.composeEngine
    Column(modifier = modifier.padding(top = 8.dp, bottom = 8.dp)) {
        if (title.isNotEmpty()) {
            AdaptiveTitle(
                text = title,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
        }

        if (ThemeResolver.isMiuixEngine(composeEngine)) {
            MiuixCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    content()
                }
            }

        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .clip(RoundedCornerShape(16.dp)),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                content()
            }
        }
    }
}