package io.legado.app.ui.book.readaloud.cast

import android.app.Application
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.help.readaloud.cast.CastSyntaxStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 四个可配置的标记符号位。 */
enum class CastSymbolField { MARK_START, MARK_END, POOL_START, POOL_END }

/** 多角色识别页状态：符号是配置而不是输入草稿，改一位就整组校验后落库。 */
data class MultiRoleRecognitionUiState(
    val config: CastSyntaxStore.Config = CastSyntaxStore.Config(),
    val preview: String = "",
    /** 最近一次保存失败的原因；0 = 无错误。 */
    val errorRes: Int = 0,
)

sealed interface MultiRoleRecognitionIntent {
    data class SetSymbol(val field: CastSymbolField, val value: String) :
        MultiRoleRecognitionIntent

    data object Reset : MultiRoleRecognitionIntent
}

/**
 * 多角色识别设置：自定义正文角色标记的包裹符号与声音池分隔符号。
 *
 * 读写收口在 [CastSyntaxStore]（UI 不碰 SharedPreferences，架构护栏）。
 * 校验是整组维度的（空 / 超长 / 互相重复），所以每一位改动都和当前配置合并后
 * 再校验：合法即落库并刷新预览，非法保留原值并给出原因。
 */
class MultiRoleRecognitionViewModel(
    application: Application,
) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(MultiRoleRecognitionUiState())
    val uiState = _uiState.asStateFlow()

    init {
        execute {
            val config = CastSyntaxStore.config()
            _uiState.update {
                it.copy(config = config, preview = CastSyntaxStore.preview(config))
            }
        }
    }

    fun onIntent(intent: MultiRoleRecognitionIntent) {
        when (intent) {
            is MultiRoleRecognitionIntent.SetSymbol -> save(
                _uiState.value.config.with(intent.field, intent.value)
            )

            MultiRoleRecognitionIntent.Reset -> save(CastSyntaxStore.Config())
        }
    }

    private fun save(config: CastSyntaxStore.Config) {
        execute {
            val error = CastSyntaxStore.save(config)
            if (error == CastSyntaxStore.ValidationError.NONE) {
                val saved = CastSyntaxStore.config()
                _uiState.update {
                    it.copy(
                        config = saved,
                        preview = CastSyntaxStore.preview(saved),
                        errorRes = 0,
                    )
                }
            } else {
                _uiState.update { it.copy(errorRes = error.stringRes()) }
            }
        }
    }

    private fun CastSyntaxStore.ValidationError.stringRes(): Int = when (this) {
        CastSyntaxStore.ValidationError.EMPTY -> R.string.cast_symbol_empty
        CastSyntaxStore.ValidationError.TOO_LONG -> R.string.cast_symbol_too_long
        CastSyntaxStore.ValidationError.DUPLICATED -> R.string.cast_symbol_duplicated
        CastSyntaxStore.ValidationError.NONE -> 0
    }
}

private fun CastSyntaxStore.Config.with(
    field: CastSymbolField,
    value: String,
): CastSyntaxStore.Config = when (field) {
    CastSymbolField.MARK_START -> copy(markStart = value.trim())
    CastSymbolField.MARK_END -> copy(markEnd = value.trim())
    CastSymbolField.POOL_START -> copy(poolStart = value.trim())
    CastSymbolField.POOL_END -> copy(poolEnd = value.trim())
}
