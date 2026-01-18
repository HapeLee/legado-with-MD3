package io.legado.app.ui.dict.rule

//import io.legado.app.lib.theme.primaryColor
import android.annotation.SuppressLint
import android.os.Bundle
import androidx.compose.runtime.Composable
import io.legado.app.R
import io.legado.app.base.BaseComposeActivity
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.help.DirectLinkUpload
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.association.ImportDictRuleDialog
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.theme.AppTheme
import io.legado.app.utils.ACache
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.readText
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.toastOnUi

class DictRuleActivity : BaseComposeActivity() {

    private val importRecordKey = "dictRuleUrls"

    private val importDoc = registerForActivityResult(HandleFileContract()) {
        kotlin.runCatching {
            it.uri?.readText(this)?.let {
                showDialogFragment(
                    ImportDictRuleDialog(it)
                )
            }
        }.onFailure {
            toastOnUi("readTextError:${it.localizedMessage}")
        }
    }
    private val exportResult = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            alert(R.string.export_success) {
                if (uri.toString().isAbsUrl()) {
                    setMessage(DirectLinkUpload.getSummary())
                }
                val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                    editView.hint = getString(R.string.path)
                    editView.setText(uri.toString())
                }
                customView { alertBinding.root }
                okButton {
                    sendToClip(uri.toString())
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //observeDictRuleData()
    }

    @Composable
    override fun Content() {
        AppTheme {
            DictRuleScreen(onBackClick = { finish() })
        }
    }

    @SuppressLint("InflateParams")
    private fun showImportDialog() {
        val aCache = ACache.get(cacheDir = false)
        val cacheUrls: MutableList<String> = aCache
            .getAsString(importRecordKey)
            ?.splitNotBlank(",")
            ?.toMutableList() ?: mutableListOf()
        alert(titleResource = R.string.import_on_line) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "url"
                editView.setFilterValues(cacheUrls)
                editView.delCallBack = {
                    cacheUrls.remove(it)
                    aCache.put(importRecordKey, cacheUrls.joinToString(","))
                }
            }
            customView { alertBinding.root }
            okButton {
                val text = alertBinding.editView.text?.toString()
                text?.let {
                    if (it.isAbsUrl() && !cacheUrls.contains(it)) {
                        cacheUrls.add(0, it)
                        aCache.put(importRecordKey, cacheUrls.joinToString(","))
                    }
                    showDialogFragment(
                        ImportDictRuleDialog(it)
                    )
                }
            }
            cancelButton()
        }
    }
}
