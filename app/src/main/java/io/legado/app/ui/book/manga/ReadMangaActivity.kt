package io.legado.app.ui.book.manga

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import io.legado.app.ui.main.MainIntent

/**
 * Samsung SPen 远程操作（`com.samsung.android.support.REMOTE_ACTION`）的外部入口。
 *
 * 阅读漫画页本身已在主界面 nav3 返回栈内（`MainRouteReadManga`），亮度、音量键翻页、网络监听、
 * 生命周期等平台行为都由 `MangaReaderRouteScreen` 承担，所以这里不再承载任何 UI：只把外部
 * intent 的参数转写成主界面的启动路由后转发出去。
 *
 * 保留本 Activity 而非把 intent-filter 直接挂到 MainActivity，是为了让「外部协议适配」这一
 * 职责有独立落点，不去动主入口的 intent 解析。
 */
class ReadMangaActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        forward(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        forward(intent)
        finish()
    }

    private fun forward(source: Intent) {
        startActivity(
            MainIntent.createReadMangaIntent(
                context = this,
                bookUrl = source.getStringExtra("bookUrl"),
                inBookshelf = source.getBooleanExtra("inBookshelf", true),
                chapterChanged = source.getBooleanExtra("chapterChanged", false),
            )
        )
    }
}
