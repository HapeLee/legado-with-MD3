package io.legado.app.ui.dict

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.DictRule
import io.legado.app.data.repository.DictRuleRepository
import io.legado.app.help.coroutine.Coroutine

class DictViewModel(
    application: Application,
    private val dictRuleRepository: DictRuleRepository
) : BaseViewModel(application) {

    private var dictJob: Coroutine<String>? = null

    fun initData(onSuccess: (List<DictRule>) -> Unit) {
        execute {
            dictRuleRepository.getEnabled()
        }.onSuccess {
            onSuccess.invoke(it)
        }
    }

    fun dict(
        dictRule: DictRule,
        word: String,
        onFinally: (String) -> Unit
    ) {
        dictJob?.cancel()
        dictJob = execute {
            dictRule.search(word)
        }.onSuccess {
            onFinally.invoke(it)
        }.onError {
            onFinally.invoke(it.localizedMessage ?: "ERROR")
        }
    }


}
