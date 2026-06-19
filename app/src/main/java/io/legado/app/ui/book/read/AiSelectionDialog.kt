package io.legado.app.ui.book.read

import android.os.Build
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.ViewGroup
import android.view.textclassifier.TextClassifier
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogDictBinding
import io.legado.app.help.ai.AiChatService
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.ui.main.ai.AiChatMessage
import io.legado.app.utils.setLayout
import io.legado.app.utils.setMarkdown
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiSelectionDialog() : BaseDialogFragment(R.layout.dialog_dict) {

    constructor(prompt: String) : this() {
        arguments = Bundle().apply {
            putString("prompt", prompt)
        }
    }

    private val binding by viewBinding(DialogDictBinding::bind)
    private var markwon: Markwon? = null

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val prompt = arguments?.getString("prompt").orEmpty().trim()
        if (prompt.isBlank()) {
            dismissAllowingStateLoss()
            return
        }
        if (AppConfig.aiCurrentProvider?.baseUrl.isNullOrBlank() || AppConfig.aiCurrentModelConfig == null) {
            toastOnUi(R.string.ai_missing_config)
            dismissAllowingStateLoss()
            return
        }
        if (!AppConfig.aiAssistantEnabled) {
            toastOnUi(R.string.ai_not_enabled)
            dismissAllowingStateLoss()
            return
        }
        binding.tabLayout.setBackgroundColor(backgroundColor)
        binding.tabLayout.setSelectedTabIndicatorColor(accentColor)
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.ai_reply))
        binding.tvDict.movementMethod = LinkMovementMethod()
        binding.tvDict.text = getString(R.string.dynamic_loading)
        binding.rotateLoading.visible()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                withContext(IO) {
                    AiChatService.chatStream(
                        messages = listOf(
                            AiChatMessage(
                                role = AiChatMessage.Role.USER,
                                content = prompt
                            )
                        ),
                        onPartial = { partial ->
                            if (partial.isNotBlank()) {
                                binding.tvDict.post {
                                    binding.tvDict.text = partial
                                }
                            }
                        }
                    )
                }
            }.getOrElse { it.localizedMessage ?: it.toString() }
            binding.rotateLoading.inVisible()
            renderMarkdown(result)
        }
    }

    private fun renderMarkdown(content: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding.tvDict.setTextClassifier(TextClassifier.NO_OP)
        }
        val renderer = markwon ?: Markwon.builder(requireContext())
            .usePlugin(HtmlPlugin.create())
            .usePlugin(TablePlugin.create(requireContext()))
            .build()
            .also { markwon = it }
        binding.tvDict.setMarkdown(renderer, renderer.toMarkdown(content), imgOnLongClickListener = {})
    }
}
