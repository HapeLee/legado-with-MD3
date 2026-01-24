package io.legado.app.ui.login

//import io.legado.app.lib.theme.primaryColor
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.graphics.Rect
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatSpinner
import androidx.core.view.setPadding
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.script.rhino.runScriptWithContext
import io.legado.app.R
import io.legado.app.base.BaseBottomSheetDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.rule.RowUi
import io.legado.app.data.entities.rule.RowUi.Type
import io.legado.app.databinding.DialogLoginBinding
import io.legado.app.databinding.ItemFilletTextBinding
import io.legado.app.databinding.ItemSelectorSingleBinding
import io.legado.app.databinding.ItemSourceEditBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.widget.text.TextInputLayout
import io.legado.app.utils.GSON
import io.legado.app.utils.applyTint
import io.legado.app.utils.dpToPx
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isTrue
import io.legado.app.utils.openUrl
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setLayout
import io.legado.app.utils.setSelectionSafely
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import kotlin.math.abs


class SourceLoginDialog : BaseBottomSheetDialogFragment(R.layout.dialog_login) {

    private val binding by viewBinding(DialogLoginBinding::bind)
    private val viewModel by activityViewModels<SourceLoginViewModel>()
    private var lastClickTime: Long = 0
    private var oKToClose = false
    private var rowUis: List<RowUi>? = null
    private var rowUiName = arrayListOf<String>()
    private var hasChange = false
    private val sourceLoginJsExtensions by lazy {
        SourceLoginJsExtensions(
            activity as AppCompatActivity,
            viewModel.source,
            viewModel.bookType,
            callback = object : SourceLoginJsExtensions.Callback {
                override fun upUiData(data: Map<String, String?>?) {
                    activity?.runOnUiThread { // 在主线程中更新 UI
                        handleUpUiData(data)
                    }
                }

                override fun reUiView() {
                    activity?.runOnUiThread {
                        handleReUiView()
                    }
                }
            }
        )
    }

    private fun handleReUiView() {
        val source = viewModel.source ?: return
        val loginUiStr = source.loginUi ?: return
        val codeStr = loginUiStr.let {
            when {
                it.startsWith("@js:") -> it.substring(4)
                it.startsWith("<js>") -> it.substring(4, it.lastIndexOf("<"))
                else -> null
            }
        }
        if (codeStr != null) {
            hasChange = true
            lifecycleScope.launch(Main) {
                val loginUiJson = evalUiJs(codeStr)
                rowUis = loginUi(loginUiJson)
                binding.flexbox.removeAllViews()
                rowUiBuilder(source, rowUis)
            }
        } else {
            rowUis = loginUi(loginUiStr)
            binding.flexbox.removeAllViews()
            rowUiBuilder(source, rowUis)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun handleUpUiData(data: Map<String, String?>?) {
        hasChange = true
        if (data == null) {
            val newLoginInfo: MutableMap<String, String> = mutableMapOf()
            rowUis?.forEachIndexed { index, rowUi ->
                val default = rowUi.default
                when (val rowView = binding.root.findViewById<View>(index + 1000)) {
                    is TextInputLayout -> {
                        newLoginInfo[rowUi.name] = default ?: ""
                        rowView.editText?.setText(default ?: "")
                    }

                    is TextView -> {
                        when (rowUi.type) {
                            Type.button -> {
                                rowView.text = rowUi.viewName ?: rowUi.name
                            }

                            Type.toggle -> {
                                val char = default ?: run {
                                    val chars =
                                        rowUi.chars?.filterNotNull() ?: listOf("chars is null")
                                    chars.getOrNull(0) ?: ""
                                }
                                newLoginInfo[rowUi.name] = char
                                val name = rowUi.viewName ?: rowUi.name
                                val left = rowUi.style?.layout_justifySelf != "right"
                                rowView.text = if (left) char + name else name + char
                            }
                        }
                    }

                    is LinearLayout -> {
                        val chars = rowUi.chars?.filterNotNull() ?: listOf("chars", "is null")
                        val index = chars.indexOf(default)
                        newLoginInfo[rowUi.name] = default ?: run {
                            chars.getOrNull(0) ?: ""
                        }
                        rowView.findViewById<AppCompatSpinner>(R.id.sp_type)
                            ?.setSelectionSafely(index)
                    }
                }
            }
            viewModel.loginInfo = newLoginInfo
            return
        }
        val loginInfo = viewModel.loginInfo
        data.forEach { (key, value) ->
            val index = rowUiName.indexOf(key)
            if (index != -1) {
                when (val rowView = binding.root.findViewById<View>(index + 1000)) {
                    is TextInputLayout -> {
                        val value = value ?: run {
                            val rowUi = rowUis?.getOrNull(index) ?: return@forEach
                            rowUi.default ?: ""
                        }
                        rowView.editText?.setText(value)
                    }

                    is TextView -> {
                        val rowUi = rowUis?.getOrNull(index) ?: return@forEach
                        when (rowUi.type) {
                            Type.button -> {
                                rowView.text = value ?: key
                            }

                            Type.toggle -> {
                                val char = value ?: run {
                                    val chars =
                                        rowUi.chars?.filterNotNull() ?: listOf("chars is null")
                                    chars.getOrNull(0) ?: ""
                                }
                                loginInfo[rowUi.name] = char
                                val name = rowUi.viewName ?: rowUi.name
                                val left = rowUi.style?.layout_justifySelf != "right"
                                rowView.text = if (left) char + name else name + char
                            }
                        }
                    }

                    is LinearLayout -> {
                        val rowUi = rowUis?.getOrNull(index) ?: return@forEach
                        val items = rowUi.chars?.filterNotNull() ?: listOf("chars", "is null")
                        val index = items.indexOf(value)
                        loginInfo[rowUi.name] = value ?: run {
                            items.getOrNull(0) ?: ""
                        }
                        rowView.findViewById<AppCompatSpinner>(R.id.sp_type)
                            ?.setSelectionSafely(index)
                    }
                }
            } else {
                loginInfo[key] = value ?: ""
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    suspend fun evalUiJs(jsStr: String): String? = withContext(IO) {
        val source = viewModel.source ?: return@withContext null
        val loginJS = source.getLoginJs() ?: ""
        val result = rowUis?.let {
            getLoginData(it)
        } ?: viewModel.loginInfo.toMutableMap()
        try {
            runScriptWithContext {
                source.evalJS("$loginJS\n$jsStr") {
                    put("result", result)
                    put("book", viewModel.book)
                    put("chapter", viewModel.chapter)
                }.toString()
            }
        } catch (e: Exception) {
            AppLog.put(source.getTag() + " loginUi err:" + (e.localizedMessage ?: e.toString()), e)
            null
        }
    }

    fun loginUi(json: String?): List<RowUi>? {
        return GSON.fromJsonArray<RowUi>(json).onFailure {
            it.printOnDebug()
        }.getOrNull()
    }

    @SuppressLint("SetTextI18n", "ClickableViewAccessibility")
    private fun rowUiBuilder(source: BaseSource, rowUis: List<RowUi>?) {
        val loginInfo = viewModel.loginInfo
        rowUiName.clear()
        rowUis?.forEachIndexed { index, rowUi ->
            val type = rowUi.type
            val name = rowUi.name
            val viewName = rowUi.viewName
            val action = rowUi.action
            rowUiName.add(name)
            when (type) {
                Type.text -> ItemSourceEditBinding.inflate(
                    layoutInflater,
                    binding.root,
                    false
                ).let {
                    val editText = it.editText
                    binding.flexbox.addView(it.root)
                    rowUi.style().apply {
                        when (this.layout_justifySelf) {
                            "center" -> editText.gravity = Gravity.CENTER
                            "flex_end" -> editText.gravity = Gravity.END
                        }
                        apply(it.root)
                    }
                    it.root.id = index + 1000
                    if (viewName == null) {
                        it.textInputLayout.hint = name
                    } else if (viewName.length in 3..19 && viewName.first() == '\'' && viewName.last() == '\'') {
                        it.textInputLayout.hint = viewName.substring(1, viewName.length - 1)
                    } else {
                        it.textInputLayout.hint = name
                        execute {
                            evalUiJs(viewName)
                        }.onSuccess { n ->
                            if (n.isNullOrEmpty()) {
                                it.textInputLayout.hint = "null"
                            } else {
                                it.textInputLayout.hint = n
                            }
                        }.onError { _ ->
                            it.textInputLayout.hint = "err"
                        }
                    }
                    editText.setText(loginInfo[name])
                    action?.let { jsStr ->
                        var content: String? = null
                        editText.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                            if (hasFocus) {
                                content = editText.text.toString()
                            } else {
                                val reContent = editText.text.toString()
                                if (content != reContent) {
                                    execute {
                                        evalUiJs(jsStr)
                                    }.onSuccess { result ->
                                        if (result.isTrue()) {
                                            loginInfo[name] = reContent
                                            hasChange = true
                                        }
                                    }.onError { e ->
                                        AppLog.put("LoginUI Text $name JavaScript error", e)
                                    }
                                }
                            }
                        }
                        editText.viewTreeObserver.addOnGlobalLayoutListener {
                            if (!editText.hasFocus()) {
                                return@addOnGlobalLayoutListener
                            }
                            val rect = Rect()
                            binding.root.getWindowVisibleDisplayFrame(rect)
                            val screenHeight = binding.root.height
                            val keypadHeight = screenHeight - rect.bottom
                            if (abs(keypadHeight) < screenHeight / 5) {
                                editText.clearFocus()
                            }
                        }
                    }
                }

                Type.password -> ItemSourceEditBinding.inflate(
                    layoutInflater,
                    binding.root,
                    false
                ).let {
                    val editText = it.editText
                    binding.flexbox.addView(it.root)
                    rowUi.style().apply {
                        when (this.layout_justifySelf) {
                            "center" -> editText.gravity = Gravity.CENTER
                            "flex_end" -> editText.gravity = Gravity.END
                        }
                        apply(it.root)
                    }
                    it.root.id = index + 1000
                    if (viewName == null) {
                        it.textInputLayout.hint = name
                    } else if (viewName.length in 3..19 && viewName.first() == '\'' && viewName.last() == '\'') {
                        it.textInputLayout.hint = viewName.substring(1, viewName.length - 1)
                    } else {
                        it.textInputLayout.hint = name
                        execute {
                            evalUiJs(viewName)
                        }.onSuccess { n ->
                            if (n.isNullOrEmpty()) {
                                it.textInputLayout.hint = "null"
                            } else {
                                it.textInputLayout.hint = n
                            }
                        }.onError { _ ->
                            it.textInputLayout.hint = "err"
                        }
                    }
                    editText.inputType =
                        InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_CLASS_TEXT
                    editText.setText(loginInfo[name])
                    action?.let { jsStr ->
                        var content: String? = null
                        editText.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                            if (hasFocus) {
                                content = editText.text.toString()
                            } else {
                                val reContent = editText.text.toString()
                                if (content != reContent) {
                                    execute {
                                        evalUiJs(jsStr)
                                    }.onSuccess { result ->
                                        if (result.isTrue()) {
                                            loginInfo[name] = reContent
                                            hasChange = true
                                        }
                                    }.onError { e ->
                                        AppLog.put("LoginUI Text $name JavaScript error", e)
                                    }
                                }
                            }
                        }
                        editText.viewTreeObserver.addOnGlobalLayoutListener {
                            if (!editText.hasFocus()) {
                                return@addOnGlobalLayoutListener
                            }
                            val rect = Rect()
                            binding.root.getWindowVisibleDisplayFrame(rect)
                            val screenHeight = binding.root.height
                            val keypadHeight = screenHeight - rect.bottom
                            if (abs(keypadHeight) < screenHeight / 5) {
                                editText.clearFocus()
                            }
                        }
                    }
                }

                Type.select -> ItemSelectorSingleBinding.inflate(
                    layoutInflater,
                    binding.root,
                    false
                ).let {
                    if (viewName == null) {
                        it.spName.text = name
                    } else if (viewName.length in 3..19 && viewName.first() == '\'' && viewName.last() == '\'') {
                        it.spName.text = viewName.substring(1, viewName.length - 1)
                    } else {
                        it.spName.text = name
                        execute {
                            evalUiJs(viewName)
                        }.onSuccess { n ->
                            if (n.isNullOrEmpty()) {
                                it.spName.text = "null"
                            } else {
                                it.spName.text = n
                            }
                        }.onError { _ ->
                            it.spName.text = "err"
                        }
                    }
                    val chars = rowUi.chars?.filterNotNull() ?: listOf("chars", "is null")
                    val adapter = ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_list_item_1,
                        chars
                    )
                    val selector = it.spType
                    selector.adapter = adapter
                    val infoV = loginInfo[name]
                    val char = if (infoV.isNullOrEmpty()) {
                        hasChange = true
                        rowUi.default ?: chars[0]
                    } else {
                        infoV
                    }
                    loginInfo[name] = char
                    val i = chars.indexOf(char)
                    selector.setSelectionSafely(i)
                    selector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        var isInitializing = true
                        override fun onItemSelected(
                            parent: AdapterView<*>?,
                            view: View?,
                            position: Int,
                            id: Long
                        ) {
                            if (isInitializing) { //忽略初始化选择
                                isInitializing = false
                                return
                            }
                            hasChange = true
                            loginInfo[name] = chars[position]
                            if (action != null) {
                                execute {
                                    handleButtonClick(source, action, name, rowUis, false)
                                }.onError { e ->
                                    AppLog.put("LoginUI Select $name JavaScript error", e)
                                }
                            }
                        }

                        override fun onNothingSelected(parent: AdapterView<*>?) {
                        }
                    }
                    binding.flexbox.addView(it.root)
                    rowUi.style().apply {
                        when (this.layout_justifySelf) {
                            "flex_start" -> selector.gravity = Gravity.START
                            "flex_end" -> selector.gravity = Gravity.END
                        }
                        apply(it.root)
                    }
                    it.root.id = index + 1000
                }

                Type.button -> ItemFilletTextBinding.inflate(
                    layoutInflater,
                    binding.root,
                    false
                ).let {
                    binding.flexbox.addView(it.root)
                    rowUi.style().apply {
                        when (this.layout_justifySelf) {
                            "flex_start" -> it.textView.gravity = Gravity.START
                            "flex_end" -> it.textView.gravity = Gravity.END
                        }
                        apply(it.root)
                    }
                    it.root.id = index + 1000
                    it.textView.text = viewName ?: name
                    it.textView.setPadding(16.dpToPx())

                    it.root.setOnClickListener {
                        val upTime = System.currentTimeMillis()
                        if (upTime - lastClickTime < 200) return@setOnClickListener
                        lastClickTime = upTime
                        handleButtonClick(source, action, name, rowUis, false)
                    }

                    it.root.setOnLongClickListener {
                        handleButtonClick(source, action, name, rowUis, true)
                        true
                    }
                }

                Type.toggle -> ItemFilletTextBinding.inflate(
                    layoutInflater,
                    binding.root,
                    false
                ).let { itBinding ->
                    var newName = name
                    var left = true
                    binding.flexbox.addView(itBinding.root)

                    rowUi.style().apply {
                        when (this.layout_justifySelf) {
                            "flex_start" -> itBinding.textView.gravity = Gravity.START
                            "flex_end" -> itBinding.textView.gravity = Gravity.END
                            "right" -> left = false
                        }
                        apply(itBinding.root)
                    }

                    itBinding.root.id = index + 1000
                    val chars = rowUi.chars?.filterNotNull() ?: listOf("chars is null")
                    val infoV = loginInfo[name]
                    var char = if (infoV.isNullOrEmpty()) {
                        hasChange = true
                        rowUi.default ?: chars[0]
                    } else {
                        infoV
                    }
                    loginInfo[name] = char

                    val updateText = { currentChar: String, displayName: String ->
                        itBinding.textView.text =
                            if (left) currentChar + displayName else displayName + currentChar
                    }

                    if (viewName == null) {
                        updateText(char, name)
                    } else if (viewName.length in 3..19 && viewName.first() == '\'' && viewName.last() == '\'') {
                        val n = viewName.substring(1, viewName.length - 1)
                        rowUi.viewName = n
                        newName = n
                        updateText(char, n)
                    } else {
                        updateText(char, name)
                        execute {
                            evalUiJs(viewName)
                        }.onSuccess { n ->
                            if (!n.isNullOrEmpty()) {
                                rowUi.viewName = n
                                newName = n
                                updateText(char, n)
                            }
                        }
                    }

                    itBinding.textView.setPadding(16.dpToPx())

                    val toggleAction = { isLong: Boolean ->
                        val currentIndex = chars.indexOf(char)
                        val nextIndex = (currentIndex + 1) % chars.size
                        char = chars.getOrNull(nextIndex) ?: ""
                        hasChange = true
                        loginInfo[name] = char
                        updateText(char, newName)
                        handleButtonClick(source, action, name, rowUis, isLong)
                    }

                    itBinding.root.setOnClickListener {
                        val upTime = System.currentTimeMillis()
                        if (upTime - lastClickTime < 200) return@setOnClickListener
                        lastClickTime = upTime
                        toggleAction(false)
                    }

                    itBinding.root.setOnLongClickListener {
                        toggleAction(true)
                        true
                    }
                }
            }
        }
    }

    private fun buttonUi(source: BaseSource, rowUis: List<RowUi>?) {
        rowUiBuilder(source, rowUis)
        binding.toolBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_ok -> {
                    oKToClose = true
                    val loginData = getLoginData(rowUis, true)
                    login(source, loginData)
                }

                R.id.menu_show_login_header -> alert {
                    setTitle(R.string.login_header)
                    source.getLoginHeader()?.let { loginHeader ->
                        setMessage(loginHeader)
                        positiveButton(R.string.copy_text) {
                            appCtx.sendToClip(loginHeader)
                        }
                    }
                }

                R.id.menu_del_login_header -> source.removeLoginHeader()
                R.id.menu_log -> showDialogFragment<AppLogDialog>()
            }
            return@setOnMenuItemClickListener true
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val source = viewModel.source ?: return
        val loginUiStr = source.loginUi ?: return
        val codeStr = loginUiStr.let {
            when {
                it.startsWith("@js:") -> it.substring(4)
                it.startsWith("<js>") -> it.substring(4, it.lastIndexOf("<"))
                else -> null
            }
        }
        if (codeStr != null) {
            lifecycleScope.launch(Main) {
                val loginUiJson = evalUiJs(codeStr)
                rowUis = loginUi(loginUiJson)
                buttonUi(source, rowUis)
            }
        } else {
            rowUis = loginUi(loginUiStr)
            buttonUi(source, rowUis)
        }
        binding.toolBar.title = getString(R.string.login_source, source.getTag())
        binding.toolBar.inflateMenu(R.menu.source_login)
        binding.toolBar.menu.applyTint(requireContext())
    }

    private fun handleButtonClick(
        source: BaseSource,
        action: String?,
        name: String,
        rowUis: List<RowUi>,
        isLongClick: Boolean
    ) {
        lifecycleScope.launch(IO) {
            if (action.isAbsUrl()) {
                context?.openUrl(action!!)
            } else if (action != null) {
                // JavaScript
                val buttonFunctionJS = action
                val loginJS = source.getLoginJs() ?: return@launch
                kotlin.runCatching {
                    runScriptWithContext {
                        source.evalJS("$loginJS\n$buttonFunctionJS") {
                            put("java", sourceLoginJsExtensions)
                            put("result", getLoginData(rowUis))
                            put("book", viewModel.book)
                            put("chapter", viewModel.chapter)
                            put("isLongClick", isLongClick)
                        }
                    }
                }.onFailure { e ->
                    ensureActive()
                    AppLog.put("LoginUI Button $name JavaScript error", e)
                }
            }
        }
    }

    private fun getLoginData(
        rowUis: List<RowUi>?,
        save: Boolean = false
    ): MutableMap<String, String> {
        val loginData = hashMapOf<String, String>()
        rowUis?.forEachIndexed { index, rowUi ->
            when (rowUi.type) {
                Type.text, Type.password -> {
                    val rowView = binding.root.findViewById<View>(index + 1000)
                    ItemSourceEditBinding.bind(rowView).editText.text.let {
                        loginData[rowUi.name] =
                            it?.toString() ?: rowUi.default ?: "" //没文本的时候存空字符串,而不是删除loginInfo
                    }
                }
            }
        }
        if (save) {
            return viewModel.loginInfo.apply { putAll(loginData) }
        }
        return viewModel.loginInfo.toMutableMap().apply { putAll(loginData) }
    }

    private fun login(source: BaseSource, loginData: MutableMap<String, String>) {
        lifecycleScope.launch(IO) {
            if (loginData.isEmpty()) {
                source.removeLoginInfo()
                withContext(Main) {
                    dismiss()
                }
            } else if (source.putLoginInfo(GSON.toJson(loginData))) {
                try {
                    runScriptWithContext {
                        source.login()
                    }
                    context?.toastOnUi(R.string.success)
                    withContext(Main) {
                        dismiss()
                    }
                } catch (e: Exception) {
                    AppLog.put("登录出错\n${e.localizedMessage}", e)
                    context?.toastOnUi("登录出错\n${e.localizedMessage}")
                    e.printOnDebug()
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (!oKToClose && hasChange) {
            val loginInfo = viewModel.loginInfo
            if (loginInfo.isEmpty()) {
                viewModel.source?.removeLoginInfo()
            } else {
                viewModel.source?.putLoginInfo(GSON.toJson(loginInfo))
            }
        }
        super.onDismiss(dialog)
        activity?.finish()
    }

}
