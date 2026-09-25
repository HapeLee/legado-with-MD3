package io.legado.app.ui.widget.components.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.data.repository.BookRepository
import io.legado.app.domain.gateway.PrivateAccessGateway
import io.legado.app.domain.gateway.PrivateContentGateway
import io.legado.app.domain.model.PrivateBookFacts
import io.legado.app.domain.model.PrivateUnlockTarget
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import splitties.init.appCtx

/**
 * 阅读器入口的私密闸门。
 *
 * 私密判定原先只存在于书架卡片与书籍详情页两个 UI 点，于是任何**绕过这两个点**直接进
 * 阅读器的路径（阅读记录、通知栏继续阅读、深链、文件关联、导入完成、音频播放页…）
 * 都能在未授权状态下读到正文。这里把它收敛成阅读器路由入口这一处的唯一闸门，
 * 而不是给每个入口各打一个补丁——后者必然漏。
 */
class PrivateReadGateViewModel(
    private val privateContentGateway: PrivateContentGateway,
    private val privateAccessGateway: PrivateAccessGateway,
    private val bookRepository: BookRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivateReadGateUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<PrivateReadGateEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    /** 已判定过的入参，避免重组时重复查库 */
    private var resolvedKey: String? = null
    private var hasResolved = false

    init {
        viewModelScope.launch {
            privateAccessGateway.state.collect { access ->
                _uiState.update { it.copy(access = access) }
            }
        }
        viewModelScope.launch {
            privateAccessGateway.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    /**
     * 判定一次。
     *
     * bookUrl 为空时按**阅读器自己的回退规则**（`BookRepository.getLastReadBook`）解析目标：
     * 闸门必须与阅读器看到同一本书，否则会出现"放行了 A、打开的却是 B"。
     */
    fun resolve(bookUrl: String?) {
        if (hasResolved && resolvedKey == bookUrl) return
        hasResolved = true
        resolvedKey = bookUrl
        viewModelScope.launch {
            val url = bookUrl?.takeIf { it.isNotEmpty() }
                ?: bookRepository.getLastReadBook()?.bookUrl
            val facts = url?.let { privateContentGateway.factsOf(it) }
                ?: PrivateBookFacts(isPrivate = false, groupMask = 0L)
            _uiState.update { it.copy(isChecking = false, bookUrl = url, facts = facts) }
        }
    }

    /**
     * 应用内密码解锁。
     *
     * 成功后不需要任何"继续打开"的回调：授权集合变化会让 [PrivateReadGateUiState.isLocked]
     * 自然翻假，内容分支自己就接上了。
     */
    fun submitPassword(password: String) {
        val target = _uiState.value.target ?: return
        if (password.isEmpty()) return
        viewModelScope.launch {
            val verified = runCatching {
                privateAccessGateway.verifyPassword(password, target)
            }.getOrDefault(false)
            if (!verified) {
                _effects.tryEmit(
                    PrivateReadGateEffect.ShowMessage(
                        appCtx.getString(R.string.private_unlock_password_error)
                    )
                )
            }
        }
    }

    /** 没有本地密码就没有解锁路径：只能去设置页设一个 */
    fun openLocalPasswordSettings() {
        _effects.tryEmit(PrivateReadGateEffect.NavigateToLocalPasswordSettings)
    }

    /**
     * 离开阅读器即撤销这次的一次性授权。
     *
     * 授权范围是"这一次阅读"而不是"这个会话"：不撤的话，回到书架卡片仍是解锁态，
     * 与"每次打开都要验证"的语义不符。
     */
    fun revokeGrant(bookUrl: String) {
        privateAccessGateway.revoke(PrivateUnlockTarget.Book(bookUrl))
    }
}
