package io.legado.app.ui.dict.rule

//import io.legado.app.lib.theme.primaryColor
import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity

class DictRuleActivity : BaseComposeActivity() {

    @Composable
    override fun Content() {
        DictRuleRouteScreen(onBackClick = { finish() })
    }

}
