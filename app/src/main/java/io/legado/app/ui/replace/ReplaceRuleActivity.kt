package io.legado.app.ui.replace

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import io.legado.app.base.AppTheme
import io.legado.app.base.BaseComposeActivity

class ReplaceRuleActivity : BaseComposeActivity() {

    @Composable
    override fun Content() {
        AppTheme {
            ReplaceRuleScreen(onBackClick = { finish() })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

}
