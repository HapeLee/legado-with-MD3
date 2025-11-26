package io.legado.app.ui.replace.edit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.databinding.ActivityReplaceEditBinding
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.ui.widget.keyboard.KeyboardToolPop
import io.legado.app.utils.GSON
import io.legado.app.utils.imeHeight
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import io.legado.app.utils.showHelp
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.data.appDb
import io.legado.app.lib.dialogs.alert
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 编辑替换规则
 */
class ReplaceEditActivity :
    VMBaseActivity<ActivityReplaceEditBinding, ReplaceEditViewModel>(),
    KeyboardToolPop.CallBack {

    companion object {
        fun startIntent(
            context: Context,
            id: Long = -1,
            pattern: String? = null,
            isRegex: Boolean = false,
            scope: String? = null,
            isScopeTitle: Boolean = false,
            isScopeContent: Boolean = false
        ): Intent = Intent(context, ReplaceEditActivity::class.java).apply {
            putExtra("id", id)
            putExtra("pattern", pattern)
            putExtra("isRegex", isRegex)
            putExtra("scope", scope)
            putExtra("isScopeTitle", isScopeTitle)
            putExtra("isScopeContent", isScopeContent)
        }
    }

    override val binding by viewBinding(ActivityReplaceEditBinding::inflate)
    override val viewModel by viewModels<ReplaceEditViewModel>()
    private val softKeyboardTool by lazy {
        KeyboardToolPop(this, lifecycleScope, binding.root, this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        softKeyboardTool.attachToWindow(window)
        initView()
        viewModel.initData(intent, ::upReplaceView)
    }

    override fun onDestroy() {
        super.onDestroy()
        softKeyboardTool.dismiss()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.replace_edit, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_save -> viewModel.save(getReplaceRule()) {
                setResult(RESULT_OK)
                finish()
            }

            R.id.menu_copy_rule -> sendToClip(GSON.toJson(getReplaceRule()))
            R.id.menu_paste_rule -> viewModel.pasteRule(::upReplaceView)
        }
        return true
    }

    private fun initView() = binding.run {
        ivHelp.setOnClickListener { showHelp("regexHelp") }
        ivManageGroups.setOnClickListener { showGroupManageDialog() }

        root.setOnApplyWindowInsetsListenerCompat { _, insets ->
            softKeyboardTool.initialPadding = insets.imeHeight
            insets
        }

        setupGroupAutoComplete()
    }

    private fun setupGroupAutoComplete() = binding.run {
        lifecycleScope.launch {
            appDb.replaceRuleDao.flowGroups().collectLatest { groups ->
                val allGroups = listOf("默认") + groups
                val adapter = ArrayAdapter(
                    this@ReplaceEditActivity,
                    android.R.layout.simple_dropdown_item_1line,
                    allGroups
                )
                etGroup.setAdapter(adapter)
            }
        }

        val defaultGroup = viewModel.replaceRule?.group ?: "默认"
        etGroup.setText(defaultGroup, false)
    }


    private fun showGroupManageDialog() {
        lifecycleScope.launch {
            val allGroups = appDb.replaceRuleDao.allGroups()

            if (allGroups.isEmpty()) {
                toastOnUi("暂无分组")
                return@launch
            }

            val checked = BooleanArray(allGroups.size)

            alert(title = getString(R.string.group_manage)) {
                multiChoiceItems(allGroups.toTypedArray(), checked) { _, _, _ -> }
                negativeButton("关闭")
                neutralButton("删除所选") {

                    val toDelete = allGroups.filterIndexed { i, _ -> checked[i] }

                    if (toDelete.isEmpty()) {
                        toastOnUi("未选择任何分组")
                        return@neutralButton
                    }

                    lifecycleScope.launch {
                        appDb.replaceRuleDao.clearGroups(toDelete)
                        toastOnUi("已清空 ${toDelete.size} 个分组")
                    }
                }
            }
        }
    }

    private fun upReplaceView(rule: ReplaceRule) = binding.run {
        etName.setText(rule.name)
        val group = rule.group.takeUnless { it.isNullOrBlank() } ?: "默认"
        etGroup.setText(group, false)
        etReplaceRule.setText(rule.pattern)
        cbUseRegex.isChecked = rule.isRegex
        etReplaceTo.setText(rule.replacement)
        cbScopeTitle.isChecked = rule.scopeTitle
        cbScopeContent.isChecked = rule.scopeContent
        etScope.setText(rule.scope)
        etExcludeScope.setText(rule.excludeScope)
        etTimeout.setText(rule.timeoutMillisecond.toString())
    }

    private fun getReplaceRule(): ReplaceRule = binding.run {
        val rule = viewModel.replaceRule ?: ReplaceRule()

        rule.name = etName.text.toString()
        val groupText = binding.etGroup.text.toString().trim()
        rule.group = when {
            groupText.isBlank()       -> null
            groupText == "默认"        -> null
            else                      -> groupText
        }
        rule.pattern = etReplaceRule.text.toString()
        rule.isRegex = cbUseRegex.isChecked
        rule.replacement = etReplaceTo.text.toString()
        rule.scopeTitle = cbScopeTitle.isChecked
        rule.scopeContent = cbScopeContent.isChecked
        rule.scope = etScope.text.toString()
        rule.excludeScope = etExcludeScope.text.toString()
        rule.timeoutMillisecond = etTimeout.text.toString().ifEmpty { "3000" }.toLong()

        rule
    }

    override fun helpActions(): List<SelectItem<String>> =
        listOf(SelectItem("正则教程", "regexHelp"))

    override fun onHelpActionSelect(action: String) {
        if (action == "regexHelp") showHelp("regexHelp")
    }

    override fun sendText(text: String) {
        if (text.isBlank()) return
        val view = window?.decorView?.findFocus()
        if (view is EditText) {
            val start = view.selectionStart
            val end = view.selectionEnd
            val edit = view.editableText
            if (start < 0 || start >= edit.length) {
                edit.append(text)
            } else if (start > end) {
                edit.replace(end, start, text)
            } else {
                edit.replace(start, end, text)
            }
        }
    }
}
