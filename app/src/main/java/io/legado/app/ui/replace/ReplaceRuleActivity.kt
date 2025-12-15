package io.legado.app.ui.replace

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import io.legado.app.base.AppTheme

class ReplaceRuleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                ReplaceRuleScreen(onBackClick = { finish() })
            }
        }
    }

}
