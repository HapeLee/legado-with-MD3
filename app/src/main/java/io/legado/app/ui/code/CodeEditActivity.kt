package io.legado.app.ui.code

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.databinding.ActivityCodeEditBinding
import io.legado.app.ui.widget.code.addJsPattern
import io.legado.app.ui.widget.code.addJsonPattern
import io.legado.app.ui.widget.code.addLegadoPattern
import io.legado.app.utils.viewbindingdelegate.viewBinding

class CodeEditActivity : BaseActivity<ActivityCodeEditBinding>() {

    override val binding by viewBinding(ActivityCodeEditBinding::inflate)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        val title = intent.getStringExtra("title").orEmpty()
        val text = intent.getStringExtra("text").orEmpty()
        val languageName = intent.getStringExtra("languageName").orEmpty()
        if (title.isNotEmpty()) setTitle(title)
        binding.codeView.setText(text)
        when {
            languageName.contains("js", ignoreCase = true) -> {
                binding.codeView.addLegadoPattern()
                binding.codeView.addJsPattern()
            }
            languageName.contains("json", ignoreCase = true) -> {
                binding.codeView.addJsonPattern()
            }
            else -> {
                binding.codeView.addLegadoPattern()
            }
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_code_edit, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_save -> {
                val text = binding.codeView.text?.toString().orEmpty()
                val data = Intent().apply { putExtra("text", text) }
                setResult(Activity.RESULT_OK, data)
                finish()
                return true
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    companion object {
        fun start(context: Context, title: String, text: String, languageName: String = "source.js") {
            context.startActivity(Intent(context, CodeEditActivity::class.java).apply {
                putExtra("title", title)
                putExtra("text", text)
                putExtra("languageName", languageName)
            })
        }
    }
}
