package io.legado.app.ui.welcome

import android.os.Bundle
import android.view.View
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.databinding.FragmentPrivacyBinding
import io.legado.app.utils.viewbindingdelegate.viewBinding

class PrivacyFragment : BaseFragment(R.layout.fragment_privacy) {

    private val binding by viewBinding(FragmentPrivacyBinding::bind)

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {

        binding.tvPrivacy.text =
            String(requireContext().assets.open("privacyPolicy.md").readBytes())
        binding.tvDisclaimer.text =
            String(requireContext().assets.open("disclaimer.md").readBytes())

    }

}
