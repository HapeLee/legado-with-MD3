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
import androidx.core.content.edit
import io.legado.app.lib.dialogs.alert
import io.legado.app.utils.toastOnUi

/**
 * 编辑替换规则
 */
class ReplaceEditActivity :
    VMBaseActivity<ActivityReplaceEditBinding, ReplaceEditViewModel>(),
    KeyboardToolPop.CallBack {

    object GroupManager {
        private const val PREF_NAME = "replace_groups"
        private const val KEY_CUSTOM_GROUPS = "custom_groups"
        private const val KEY_LAST_SELECTED = "last_selected_group"

        private val presetGroups = listOf(
            "默认", "地名", "国家", "企业", "人名", "敏感词", "生僻字", "乱码或广告"
        )

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREF_NAME, MODE_PRIVATE)

        fun getAllGroups(context: Context): List<String> =
            (getCustomGroups(context) + presetGroups).distinct()

        fun getCustomGroups(context: Context): List<String> {
            val json = prefs(context).getString(KEY_CUSTOM_GROUPS, "[]") ?: "[]"
            return GSON.fromJson(json, Array<String>::class.java).toList()
        }

        fun saveCustomGroups(context: Context, groups: List<String>) {
            prefs(context).edit { putString(KEY_CUSTOM_GROUPS, GSON.toJson(groups)) }
        }

        fun addCustomGroup(context: Context, group: String) {
            if (group.isBlank() || presetGroups.contains(group)) return
            val custom = getCustomGroups(context).toMutableList()
            if (group !in custom) {
                custom += group
                saveCustomGroups(context, custom)
            }
        }

        fun removeCustomGroups(context: Context, groupsToRemove: List<String>) {
            if (groupsToRemove.isEmpty()) return
            val updated = getCustomGroups(context).filterNot { it in groupsToRemove }
            saveCustomGroups(context, updated)
        }

        fun getLastSelectedGroup(context: Context): String =
            prefs(context).getString(KEY_LAST_SELECTED, "默认") ?: "默认"

        fun setLastSelectedGroup(context: Context, group: String) {
            prefs(context).edit { putString(KEY_LAST_SELECTED, group) }
        }
    }

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
        val allGroups = GroupManager.getAllGroups(this@ReplaceEditActivity)
        val adapter = ArrayAdapter(
            this@ReplaceEditActivity,
            android.R.layout.simple_dropdown_item_1line,
            allGroups
        )
        etGroup.setAdapter(adapter)

        etGroup.setText(GroupManager.getLastSelectedGroup(this@ReplaceEditActivity), false)

        etGroup.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveCurrentGroup()
        }

        etGroup.setOnItemClickListener { parent, _, position, _ ->
            val selected = parent.getItemAtPosition(position) as String
            GroupManager.setLastSelectedGroup(this@ReplaceEditActivity, selected)
        }
    }

    private fun refreshGroupList() {
        val allGroups = GroupManager.getAllGroups(this)
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            allGroups
        )
        binding.etGroup.setAdapter(adapter)
    }

    private fun saveCurrentGroup() {
        val current = binding.etGroup.text.toString().trim()
        if (current.isNotBlank()) {
            GroupManager.addCustomGroup(this, current)
            GroupManager.setLastSelectedGroup(this, current)
        }
    }

    private fun showGroupManageDialog() {
        val allGroups = GroupManager.getCustomGroups(this).toMutableList()
        if (allGroups.isEmpty()) {
            toastOnUi("暂无自定义分组")
            return
        }

        val checked = BooleanArray(allGroups.size)
        alert(title = getString(R.string.group_manage)) {
            multiChoiceItems(allGroups.toTypedArray(), checked) { _, _, _ -> }
            negativeButton("关闭")
            neutralButton("删除所选") {
                val toDelete = allGroups.filterIndexed { i, _ -> checked[i] }
                if (toDelete.isNotEmpty()) {
                    GroupManager.removeCustomGroups(this@ReplaceEditActivity, toDelete)
                    refreshGroupList()
                    toastOnUi("已删除 ${toDelete.size} 个分组")
                } else {
                    toastOnUi("未选择任何分组")
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
        saveCurrentGroup()
        val rule = viewModel.replaceRule ?: ReplaceRule()

        rule.name = etName.text.toString()
        val groupText = etGroup.text.toString().trim()
        rule.group = if (groupText == "默认") {
            null
        } else {
            groupText.ifBlank { null }
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
            } else {
                edit.replace(start, end, text)
            }
        }
    }
}
