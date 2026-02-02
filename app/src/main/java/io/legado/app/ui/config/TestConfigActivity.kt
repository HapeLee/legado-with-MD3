package io.legado.app.ui.config

import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity
import io.legado.app.ui.config.otherConfig.OtherConfigScreen

class TestConfigActivity : BaseComposeActivity() {
    @Composable
    override fun Content() {
        OtherConfigScreen(onBackClick = { finish() })
    }

}