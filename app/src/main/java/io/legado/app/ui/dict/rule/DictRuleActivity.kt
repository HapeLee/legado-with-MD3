package io.legado.app.ui.dict.rule

import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity
import io.legado.app.feature.dict.rule.DictRuleRouteScreen

class DictRuleActivity : BaseComposeActivity() {

    @Composable
    override fun Content() {
        DictRuleRouteScreen(onBackClick = { finish() })
    }

}
