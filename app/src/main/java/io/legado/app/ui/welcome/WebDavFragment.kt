package io.legado.app.ui.welcome

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.content.edit
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.FragmentWebdavAuthBinding
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppWebDav
import io.legado.app.help.AppWebDav.testWebDav
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.storage.Restore
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.utils.gone
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import kotlin.coroutines.coroutineContext

class WebDavFragment : BaseFragment(R.layout.fragment_webdav_auth) {

    private var _binding: FragmentWebdavAuthBinding? = null
    private val binding get() = _binding!!
    private var restoreJob: Job? = null
    private val waitDialog by lazy { WaitDialog(requireContext()) }

    private val restoreDoc = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            waitDialog.setText("恢复中…")
            waitDialog.show()
            val task = Coroutine.async {
                Restore.restore(appCtx, uri)
            }.onFinally {
                waitDialog.dismiss()
            }
            waitDialog.setOnCancelListener {
                task.cancel()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWebdavAuthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        val urls = listOf(
            "https://dav.jianguoyun.com/dav/",
            "https://webdav.aliyundrive.com/",
            "https://soya.infini-cloud.net/dav/"
        )

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            urls
        )

        binding.editUrl.setAdapter(adapter)

        binding.editAccount.setText(prefs.getString(PreferKey.webDavAccount, ""))
        binding.editPassword.setText(prefs.getString(PreferKey.webDavPassword, ""))
        binding.editPassword.inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        binding.etPassword.setText(LocalConfig.password ?: "")

        binding.etPassword.doAfterTextChanged {
            LocalConfig.password = it.toString()
        }

        binding.btnCheck.setOnClickListener {
            val config = WebDavConfig(
                url = binding.editUrl.text.toString(),
                account = binding.editAccount.text.toString(),
                password = binding.editPassword.text.toString()
            )
            saveWebDavConfig(config)
            lifecycleScope.launch {
                testWebDav()
            }
        }

        binding.btnRestore.setOnClickListener {
            val config = WebDavConfig(
                url = binding.editUrl.text.toString(),
                account = binding.editAccount.text.toString(),
                password = binding.editPassword.text.toString()
            )
            lifecycleScope.launch {
                try {
                    binding.progressRestore.visible()
                    binding.btnRestore.text = ""
                    saveWebDavConfig(config)
                    AppWebDav.upConfig()
                    restore()
                } finally {
                    binding.progressRestore.gone()
                    binding.btnRestore.text = "获取备份"
                }
            }
        }

    }

    fun saveWebDavConfig(config: WebDavConfig, onSaved: (() -> Unit)? = null) {
        PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
            putString(PreferKey.webDavUrl, config.url)
            putString(PreferKey.webDavAccount, config.account)
            putString(PreferKey.webDavPassword, config.password)
        }

        onSaved?.invoke()
    }

    fun restore() {
        waitDialog.setText(R.string.loading)
        waitDialog.setOnCancelListener {
            restoreJob?.cancel()
        }
        waitDialog.show()
        Coroutine.async {
            restoreJob = coroutineContext[Job]
            showRestoreDialog(requireContext())
        }.onError {
            AppLog.put("恢复备份出错WebDavError\n${it.localizedMessage}", it)
            if (context == null) {
                return@onError
            }
            alert {
                setTitle(R.string.restore)
                setMessage("WebDavError\n${it.localizedMessage}\n将从本地备份恢复。")
                okButton {
                    restoreFromLocal()
                }
                cancelButton()
            }
        }.onFinally {
            waitDialog.dismiss()
        }
    }

    private suspend fun showRestoreDialog(context: Context) {
        val names = withContext(IO) { AppWebDav.getBackupNames() }
        if (AppWebDav.isJianGuoYun && names.size > 700) {
            context.toastOnUi("由于坚果云限制列出文件数量，部分备份可能未显示，请及时清理旧备份")
        }
        if (names.isNotEmpty()) {
            coroutineContext.ensureActive()
            withContext(Main) {
                context.selector(
                    title = context.getString(R.string.select_restore_file),
                    items = names
                ) { _, index ->
                    if (index in names.indices) {
                        restoreWebDav(names[index])
                    }
                }
            }
        } else {
            throw NoStackTraceException("Web dav no back up file")
        }
    }

    private fun restoreWebDav(name: String) {
        waitDialog.setText("恢复中…")
        waitDialog.show()
        val task = Coroutine.async {
            AppWebDav.restoreWebDav(name)
        }.onError {
            AppLog.put("WebDav恢复出错\n${it.localizedMessage}", it)
            appCtx.toastOnUi("WebDav恢复出错\n${it.localizedMessage}")
        }.onFinally {
            waitDialog.dismiss()
        }
        waitDialog.setOnCancelListener {
            task.cancel()
        }
    }

    private fun restoreFromLocal() {
        restoreDoc.launch {
            title = getString(R.string.select_restore_file)
            mode = HandleFileContract.FILE
            allowExtensions = arrayOf("zip")
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        waitDialog.dismiss() // 确保释放
        _binding = null
    }

}

data class WebDavConfig(
    val url: String,
    val account: String,
    val password: String
)
