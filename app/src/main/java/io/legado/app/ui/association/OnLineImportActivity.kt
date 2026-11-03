package io.legado.app.ui.association

import android.os.Bundle
import androidx.activity.viewModels
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.databinding.ActivityTranslucenceBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.main.MainActivity
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.viewbindingdelegate.viewBinding
import java.util.Locale

/**
 * 网络一键导入
 * 格式: legado://import/{path}?src={url}
 */
class OnLineImportActivity :
    VMBaseActivity<ActivityTranslucenceBinding, OnLineImportViewModel>() {

    override val binding by viewBinding(ActivityTranslucenceBinding::inflate)
    override val viewModel by viewModels<OnLineImportViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.successLive.observe(this) {
            when (it.first) {
                "bookSource" -> openComposeBookSourceImport(it.second)
                "rssSource" -> showDialogFragment(
                    ImportRssSourceDialog(it.second, true)
                )
                "replaceRule" -> showDialogFragment(
                    ImportReplaceRuleDialog(it.second, true)
                )
                "httpTts" -> showDialogFragment(
                    ImportHttpTtsDialog(it.second, true)
                )
                "theme" -> showDialogFragment(
                    ImportThemeDialog(it.second, true)
                )
                "txtRule" -> showDialogFragment(
                    ImportTxtTocRuleDialog(it.second, true)
                )
                "dictRule" -> showDialogFragment(
                    ImportDictRuleDialog(it.second, true)
                )
            }
        }
        viewModel.errorLive.observe(this) {
            finallyDialog(getString(R.string.error), it)
        }
        intent.data?.let {
            val url = it.getQueryParameter("src")
            if (url.isNullOrEmpty()) {
                finish()
                return
            }
            // 气泡包单独前置：兼容 ARC 的 `legado://import/bubblePackage` 与遗留 `legado:///bubble`，
            // 两者 path 大小写不固定，直接进气泡包导入，不能落到 determineType 的 readConfig 分支
            if (isBubbleImportPath(it.path)) {
                importBubblePack(url)
                return
            }
            when (it.path) {
                "/bookSource" -> openComposeBookSourceImport(url)

                "/rssSource" -> showDialogFragment(
                    ImportRssSourceDialog(url, true)
                )

                "/replaceRule" -> showDialogFragment(
                    ImportReplaceRuleDialog(url, true)
                )
                "/textTocRule" -> showDialogFragment(
                    ImportTxtTocRuleDialog(url, true)
                )
                "/httpTTS" -> showDialogFragment(
                    ImportHttpTtsDialog(url, true)
                )
                "/dictRule" -> showDialogFragment(
                    ImportDictRuleDialog(url, true)
                )
                "/theme" -> showDialogFragment(
                    ImportThemeDialog(url, true)
                )

                "/readConfig" -> viewModel.getBytes(url) { bytes ->
                    viewModel.importReadConfig(bytes, this::finallyDialog)
                }
                "/addToBookshelf" -> showDialogFragment(
                    AddToBookshelfDialog(url, true)
                )
                "/importonline" -> when (it.host) {
                    "booksource" -> openComposeBookSourceImport(url)
                    "rsssource" -> showDialogFragment(
                        ImportRssSourceDialog(url, true)
                    )
                    "replace" -> showDialogFragment(
                        ImportReplaceRuleDialog(url, true)
                    )
                    "bubble", "bubblepack" -> importBubblePack(url)
                    else -> {
                        viewModel.determineType(url, this::finallyDialog)
                    }
                }
                else -> viewModel.determineType(url, this::finallyDialog)
            }
        }
    }

    /** ARC 的 `legado://import/bubblePackage` 与遗留 `legado:///bubble` 都导向气泡包导入。 */
    private fun isBubbleImportPath(path: String?): Boolean = when (path?.lowercase(Locale.ROOT)) {
        "/bubblepack", "/bubblepackage", "/bubble" -> true
        else -> false
    }

    private fun importBubblePack(url: String) {
        viewModel.getBytes(url) { bytes ->
            viewModel.importBubblePack(bytes, this::finallyDialog)
        }
    }

    private fun finallyDialog(title: String, msg: String) {
        alert(title, msg) {
            okButton()
            onDismiss {
                finish()
            }
        }
    }

    private fun openComposeBookSourceImport(source: String) {
        startActivity(MainActivity.createBookSourceManageIntent(this, source))
        finish()
    }

}
