package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.animation.Animation
import android.widget.FrameLayout
import androidx.core.view.isVisible
import io.legado.app.R
import io.legado.app.databinding.ViewSearchMenuBinding
//import io.legado.app.lib.theme.bottomBackground
//import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.searchContent.SearchResult
import io.legado.app.utils.activity
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.invisible
import io.legado.app.utils.loadAnimation
import io.legado.app.utils.visible

/**
 * 搜索界面菜单
 */
class SearchMenu @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val callBack: CallBack get() = activity as CallBack
    private val binding = ViewSearchMenuBinding.inflate(LayoutInflater.from(context), this, true)

    private val menuBottomIn: Animation = loadAnimation(context, R.anim.anim_readbook_bottom_in)
    private val menuBottomOut: Animation = loadAnimation(context, R.anim.anim_readbook_bottom_out)

    private var onMenuOutEnd: (() -> Unit)? = null
    private var isMenuOutAnimating = false

    private val searchResultList: MutableList<SearchResult> = mutableListOf()
    private var currentSearchResultIndex: Int = -1
    private var lastSearchResultIndex: Int = -1
    private val hasSearchResult: Boolean
        get() = searchResultList.isNotEmpty()
    val selectedSearchResult: SearchResult?
        get() = searchResultList.getOrNull(currentSearchResultIndex)
    val previousSearchResult: SearchResult?
        get() = searchResultList.getOrNull(lastSearchResultIndex)
    val bottomMenuVisible get() = isVisible && binding.llBottomMenu.isVisible

    init {
        initAnimation()
        initView()
        bindEvent()
        updateSearchInfo()
    }

    fun upSearchResultList(resultList: List<SearchResult>) {
        searchResultList.clear()
        searchResultList.addAll(resultList)
        updateSearchInfo()
    }

    private fun initView() = binding.run {
        applyNavigationBarPadding()
    }


    fun runMenuIn() {
        this.visible()
        binding.llBottomMenu.visible()
        binding.vwMenuBg.visible()
        binding.llBottomMenu.startAnimation(menuBottomIn)
    }

    fun runMenuOut(onMenuOutEnd: (() -> Unit)? = null) {
        if (isMenuOutAnimating) {
            return
        }
        this.onMenuOutEnd = onMenuOutEnd
        if (this.isVisible) {
            binding.llBottomMenu.startAnimation(menuBottomOut)
        }
    }

    @SuppressLint("SetTextI18n")
    fun updateSearchInfo() {
        ReadBook.curTextChapter?.let {
            binding.tvCurrentChapter.text = "当前章节: ${it.title}"
        }
        updateSearchProgress()
    }

    fun updateSearchResultIndex(updateIndex: Int) {
        lastSearchResultIndex = currentSearchResultIndex
        currentSearchResultIndex = when {
            updateIndex < 0 -> 0
            updateIndex >= searchResultList.size -> searchResultList.size - 1
            else -> updateIndex
        }
        updateSearchProgress()
    }

    private fun updateSearchProgress() {
        val total = searchResultList.size
        if (total == 0) {
            binding.tvSearchProgress.text = "0%"
            binding.tvSearchFraction.text = "0/0"
        } else {
            val current = currentSearchResultIndex + 1
            val progress = (current * 100 / total)
            binding.tvSearchProgress.text = "$progress%"
            binding.tvSearchFraction.text = "$current / $total"
        }
    }

    private fun bindEvent() = binding.run {
        //搜索结果
        ivSearchResults.setOnClickListener {
            runMenuOut {
                callBack.openSearchActivity(selectedSearchResult?.query)
            }
        }

        //主菜单
        ivMainMenu.setOnClickListener {
            runMenuOut {
                callBack.cancelSelect()
                callBack.showMenuBar()
                this@SearchMenu.invisible()
            }
        }

        //退出
        ivSearchExit.setOnClickListener {
            runMenuOut {
                callBack.exitSearchMenu()
            }
        }

        fabLeft.setOnClickListener {
            updateSearchResultIndex(currentSearchResultIndex - 1)
            callBack.navigateToSearch(
                searchResultList[currentSearchResultIndex],
                currentSearchResultIndex
            )
        }

        fabRight.setOnClickListener {
            updateSearchResultIndex(currentSearchResultIndex + 1)
            callBack.navigateToSearch(
                searchResultList[currentSearchResultIndex],
                currentSearchResultIndex
            )
        }
    }

    private fun initAnimation() {
        //显示菜单
        menuBottomIn.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                callBack.upSystemUiVisibility()
                binding.fabLeft.visible(hasSearchResult)
                binding.fabRight.visible(hasSearchResult)
            }

            @SuppressLint("RtlHardcoded")
            override fun onAnimationEnd(animation: Animation) {
                binding.vwMenuBg.setOnClickListener { runMenuOut() }
                callBack.upSystemUiVisibility()
            }

            override fun onAnimationRepeat(animation: Animation) = Unit
        })

        //隐藏菜单
        menuBottomOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                isMenuOutAnimating = true
                binding.vwMenuBg.setOnClickListener(null)
            }

            override fun onAnimationEnd(animation: Animation) {
                isMenuOutAnimating = false
                binding.llBottomMenu.invisible()
                binding.vwMenuBg.invisible()
                binding.vwMenuBg.setOnClickListener { runMenuOut() }

                onMenuOutEnd?.invoke()
                callBack.upSystemUiVisibility()
            }

            override fun onAnimationRepeat(animation: Animation) = Unit
        })
    }

    interface CallBack {
        var isShowingSearchResult: Boolean
        fun openSearchActivity(searchWord: String?)
        fun showSearchSetting()
        fun upSystemUiVisibility()
        fun exitSearchMenu()
        fun showMenuBar()
        fun navigateToSearch(searchResult: SearchResult, index: Int)
        fun onMenuShow()
        fun onMenuHide()
        fun cancelSelect()
    }

}
