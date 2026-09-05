package io.legado.app.smoke.rhinoprobe

class RhinoRuleEngineAndroidHostTest : RuleEngineContractTest() {
    override fun createEngine(): RuleEngine = RhinoRuleEngine()
}
