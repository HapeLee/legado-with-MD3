package io.legado.app.smoke.rhinoprobe

class RhinoRuleEngineDesktopTest : RuleEngineContractTest() {
    override fun createEngine(): RuleEngine = RhinoRuleEngine()
}
