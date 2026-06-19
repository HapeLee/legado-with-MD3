package io.legado.app.ui.config

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.ActivityAiProviderManageBinding
import io.legado.app.databinding.ItemS3ContainerBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.UiCorner
import io.legado.app.lib.theme.applyUiLabelStyle
import io.legado.app.lib.theme.applyUiSectionTitleStyle
import io.legado.app.lib.theme.secondaryTextColor
import io.legado.app.ui.main.ai.AiProviderConfig
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

class AiProviderManageActivity : BaseActivity<ActivityAiProviderManageBinding>() {

    override val binding by viewBinding(ActivityAiProviderManageBinding::inflate)
    private val adapter by lazy { Adapter() }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        (binding.recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        binding.btnAdd.background = actionBackground()
        binding.btnAdd.setOnClickListener { openEdit(null) }
        binding.tvSummary.applyUiLabelStyle(this)
        binding.tvSummary.setTextColor(secondaryTextColor)
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        binding.tvSummary.text = getString(R.string.ai_provider_manage_summary)
        adapter.setItems(AppConfig.aiProviderList)
    }

    private fun openEdit(provider: AiProviderConfig?) {
        startActivity(Intent(this, AiProviderEditActivity::class.java).apply {
            provider?.id?.let { putExtra(AiProviderEditActivity.EXTRA_PROVIDER_ID, it) }
        })
    }

    private fun showActions(provider: AiProviderConfig) {
        selector(provider.name, listOf(getString(R.string.edit), getString(R.string.delete))) { _, index ->
            when (index) {
                0 -> openEdit(provider)
                1 -> confirmRemoveProvider(provider)
            }
        }
    }

    private fun confirmRemoveProvider(provider: AiProviderConfig) {
        val relatedModelCount = AppConfig.aiModelConfigList.count { it.providerId == provider.id }
        alert(
            title = provider.name,
            message = getString(
                if (relatedModelCount > 0) R.string.ai_remove_provider_confirm_with_models
                else R.string.ai_remove_provider_confirm,
                relatedModelCount
            )
        ) {
            okButton {
                AppConfig.aiProviderList = AppConfig.aiProviderList.filterNot { it.id == provider.id }
                reload()
                toastOnUi(R.string.ai_provider_removed)
            }
            cancelButton()
        }
    }

    private fun actionBackground() = UiCorner.actionSelector(
        ContextCompat.getColor(this, R.color.background_card),
        ContextCompat.getColor(this, R.color.background_menu),
        UiCorner.actionRadius(this)
    )

    private inner class Adapter :
        RecyclerAdapter<AiProviderConfig, ItemS3ContainerBinding>(this@AiProviderManageActivity) {

        override fun getViewBinding(parent: ViewGroup): ItemS3ContainerBinding {
            return ItemS3ContainerBinding.inflate(inflater, parent, false).apply {
                root.background = UiCorner.panelRounded(
                    root.context,
                    ContextCompat.getColor(root.context, R.color.background_card),
                    UiCorner.panelRadius(root.context)
                )
                btnMore.background = actionBackground()
            }
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemS3ContainerBinding,
            item: AiProviderConfig,
            payloads: MutableList<Any>
        ) = binding.run {
            val modelCount = AppConfig.aiModelConfigList.count { it.providerId == item.id }
            val isCurrent = item.id == AppConfig.aiCurrentProviderId
            tvName.text = item.name
            tvPath.text = item.baseUrl
            tvCapacity.text = getString(R.string.ai_manage_models_summary, modelCount)
            tvState.text = getString(if (isCurrent) R.string.ai_current_provider else R.string.ai_provider)
            tvSelected.visibility = if (isCurrent) View.VISIBLE else View.GONE
            tvSelected.setTextColor(ContextCompat.getColor(this@AiProviderManageActivity, R.color.accent))
            tvName.applyUiSectionTitleStyle(this@AiProviderManageActivity)
            tvPath.applyUiLabelStyle(this@AiProviderManageActivity)
            tvCapacity.applyUiLabelStyle(this@AiProviderManageActivity)
            tvState.applyUiLabelStyle(this@AiProviderManageActivity)
            listOf(tvPath, tvCapacity, tvState).forEach { it.setTextColor(secondaryTextColor) }
            btnMore.setOnClickListener { showActions(item) }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemS3ContainerBinding) {
            holder.itemView.setOnClickListener {
                getItem(holder.bindingAdapterPosition - getHeaderCount())?.let { openEdit(it) }
            }
        }
    }
}
