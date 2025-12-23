package io.legado.app.ui.dict

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import androidx.fragment.app.viewModels
import com.google.android.material.tabs.TabLayout
import io.legado.app.R
import io.legado.app.base.BaseBottomSheetDialogFragment
import io.legado.app.data.entities.DictRule
import io.legado.app.databinding.DialogDictBinding
import io.legado.app.help.GlideImageGetter
import io.legado.app.utils.gone
import io.legado.app.utils.setHtml
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible

/**
 * 词典
 */
class DictDialog() : BaseBottomSheetDialogFragment(R.layout.dialog_dict) {

    constructor(word: String) : this() {
        arguments = Bundle().apply {
            putString("word", word)
        }
    }

    private val viewModel by viewModels<DictViewModel>()
    private val binding by viewBinding(DialogDictBinding::bind)
    private var word: String? = null
    private var glideImageGetter: GlideImageGetter? = null

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.tvDict.movementMethod = LinkMovementMethod()
        word = arguments?.getString("word")
        if (word.isNullOrEmpty()) {
            toastOnUi(R.string.cannot_empty)
            dismiss()
            return
        }

        setupTabs()
        observeDicts()
    }

    override fun onDestroyView() {
        glideImageGetter?.clear()
        glideImageGetter = null
        super.onDestroyView()
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabSelected(tab: TabLayout.Tab) {
                val dictRule = tab.tag as DictRule
                loadDict(dictRule)
            }
        })
    }

    private fun observeDicts() {
        viewModel.initData { dictRules ->
            if (dictRules.isEmpty()) {
                showEmptyView("暂无可用词典")
                return@initData
            }

            binding.emptyMessageView.gone()
            dictRules.forEach {
                binding.tabLayout.addTab(binding.tabLayout.newTab().apply {
                    text = it.name
                    tag = it
                })
            }
            setupTabLayoutMode(dictRules.size)


            binding.tabLayout.getTabAt(0)?.select()
        }
    }

    private fun loadDict(dictRule: DictRule) {

        binding.tvDict.gone()
        binding.emptyMessageView.gone()
        binding.rotateLoading.visible()

        viewModel.dict(dictRule, word!!) { result ->
            binding.rotateLoading.gone()
            if (result.isBlank()) {
                showEmptyView("没有查询到结果")
            } else {
                binding.tvDict.visible()
                binding.tvDict.setHtml(result)
                glideImageGetter?.clear()
                glideImageGetter = GlideImageGetter.create(requireContext(), binding.tvDict, result)
                binding.tvDict.setHtml(result, glideImageGetter)
            }
        }
    }

    private fun setupTabLayoutMode(dictCount: Int) {
        if (dictCount <= 4) {
            binding.tabLayout.tabMode = TabLayout.MODE_FIXED
            binding.tabLayout.tabGravity = TabLayout.GRAVITY_FILL
        } else {
            binding.tabLayout.tabMode = TabLayout.MODE_SCROLLABLE
            binding.tabLayout.tabGravity = TabLayout.GRAVITY_CENTER
        }
    }

    private fun showEmptyView(message: String) {
        binding.tvDict.gone()
        binding.rotateLoading.gone()
        binding.emptyMessageView.visible()
        binding.emptyMessageView.setMessage(message)
    }
}
