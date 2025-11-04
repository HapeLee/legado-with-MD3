package io.legado.app.ui.about

import android.os.Build
import android.os.Bundle
import android.view.View
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.base.BaseBottomSheetDialogFragment
import io.legado.app.constant.AppConst
import io.legado.app.databinding.DialogUpdateBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.update.AppUpdate
import io.legado.app.help.update.AppVariant
import io.legado.app.model.Download
import io.legado.app.utils.gone
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin

class UpdateDialog() : BaseBottomSheetDialogFragment(R.layout.dialog_update) {

    enum class Mode { UPDATE, VIEW_LOG }

    constructor(updateInfo: AppUpdate.UpdateInfo, mode: Mode = Mode.UPDATE) : this() {
        arguments = Bundle().apply {
            putString("newVersion", updateInfo.tagName)
            putString("updateBody", updateInfo.updateLog)
            putString("url", updateInfo.downloadUrl)
            putString("name", updateInfo.fileName)
            putString("mode", mode.name)
        }
    }

    private val checkVariant: AppVariant
        get() = when (AppConfig.updateToVariant) {
            "official_version" -> AppVariant.OFFICIAL
            "beta_release_version" -> AppVariant.BETA_RELEASE
            "all_version" -> AppVariant.ALL
            else -> AppConst.appInfo.appVariant
        }

    private val binding by viewBinding(DialogUpdateBinding::bind)

    private val mode: Mode
        get() = Mode.valueOf(arguments?.getString("mode") ?: Mode.UPDATE.name)

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.tvCurrentVersion.text = BuildConfig.VERSION_NAME
        binding.tvVersion.text = arguments?.getString("newVersion")
        binding.tvAbi.text = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        binding.tvVariable.text = checkVariant.toString()

        val updateBody = arguments?.getString("updateBody")
        if (updateBody.isNullOrBlank()) {
            toastOnUi("没有数据")
            dismiss()
            return
        }

        binding.textView.post {
            Markwon.builder(requireContext())
                .usePlugin(GlideImagesPlugin.create(requireContext()))
                .usePlugin(HtmlPlugin.create())
                .usePlugin(TablePlugin.create(requireContext()))
                .build()
                .setMarkdown(binding.textView, updateBody)
        }

        when (mode) {
            Mode.UPDATE -> {
                val url = arguments?.getString("url")
                val fileName = arguments?.getString("name")
                binding.ivAward.gone()
                binding.btnUpdate.setOnClickListener {
                    if (url.isNullOrBlank() || fileName.isNullOrBlank()) {
                        toastOnUi("下载信息不完整")
                        return@setOnClickListener
                    }
                    Download.start(requireContext(), url, fileName)
                    toastOnUi("开始下载: $fileName")
                }
            }

            Mode.VIEW_LOG -> {
                binding.bottomSheetTitle.text = "已经更新至"
                binding.tvVersion.text = BuildConfig.VERSION_NAME
                binding.btnUpdate.gone()
                binding.llCurrent.gone()
                binding.tvCurrentVersion.gone()
                binding.tvUrl.gone()
            }
        }
    }
}
