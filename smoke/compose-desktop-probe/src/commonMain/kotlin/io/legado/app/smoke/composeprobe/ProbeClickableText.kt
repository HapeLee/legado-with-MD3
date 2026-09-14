package io.legado.app.smoke.composeprobe

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 探针用的最小可交互界面：点一次按钮把文案从 [IDLE_TEXT] 换成 [CLICKED_TEXT]。
 *
 * 选它而不是一个静态 `Text`，是因为要同时证明四件事：能渲染、能接收输入事件、
 * 能因状态变化而重组、能断言重组后的结果。这四件正是 M1-4「展示同一 Feature」的
 * 最小前提——静态渲染证明不了界面真的活着。
 */
const val IDLE_TEXT = "probe-idle"

const val CLICKED_TEXT = "probe-clicked"

@Composable
fun ProbeClickableText() {
    var clicked by remember { mutableStateOf(false) }
    Text(if (clicked) CLICKED_TEXT else IDLE_TEXT)
    Button(onClick = { clicked = true }) {
        Text("probe-button")
    }
}
