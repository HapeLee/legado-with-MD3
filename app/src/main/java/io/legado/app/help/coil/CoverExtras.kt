package io.legado.app.help.coil

// ============================================================================
// [FIX-AI] 本文件由 AI 助手（Chatbox）修改（2026-09-13）。
// 搜索 [FIX-AI] 可定位本文件全部改动点，每处均注明 原版行为 -> 修复后行为。
// 问题背景与完整清单见 LegadoMD3/fix/README.md。
// ============================================================================


import coil3.Extras
import io.legado.app.data.entities.BaseSource

/**
 * 封面请求在 Coil 3 中通过 [Extras] 传参。Extras.Key 只按实例相等比较，
 * 因此写入方（封面请求构造）与读取方（CoverInterceptor / CoverFetcher）
 * 必须共享这组单例 key。
 */
object CoverExtras {
    /** 书源标识，CoverInterceptor 用它解析最终 URL 和请求头。 */
    val SourceOrigin = Extras.Key<String?>(null)

    /** CoverInterceptor 解析得到的书源，CoverFetcher 用它解密图片。 */
    val Source = Extras.Key<BaseSource?>(null)

    /** CoverInterceptor 解析得到的请求头，CoverFetcher 构造 OkHttp 请求用。 */
    val Headers = Extras.Key<Map<String, String>?>(null)

    /** 仅 WiFi 时加载。 */
    val LoadOnlyWifi = Extras.Key<Boolean?>(null)

    /** 漫画模式。 */
    val Manga = Extras.Key<Boolean?>(null)

    /** 漫画图片所属书籍。图片解密必须显式携带，不能读取全局阅读会话。 */
    val MangaBookUrl = Extras.Key<String?>(null)

    /**
     * [FIX-AI] 新增（原版无）：CoverInterceptor 在改写 request.data 为最终解析 URL 之前，
     * 把书架/详情页存储的原始封面地址存进来。CoverFetcher 回写持久文件缓存时用这个
     * 稳定键（而不是带动态 token 的最终 URL），保证重启后拦截器快速路径仍能命中。
     */
    val OriginalUrl = Extras.Key<String?>(null)

    /**
     * [FIX-AI] 新增（原版无）：封面请求所属书籍的 bookUrl。用于“别名缓存键”：
     * 部分书源启动刷新时会把 coverUrl 重写成带新 token 的链接（图片内容相同），
     * 纯 URL 键会 miss → 书架重新下载。别名键＝“本书最近一次成功缓存的封面”，
     * 与链接无关，书架优先命中它。
     */
    val BookUrl = Extras.Key<String?>(null)

    /**
     * [FIX-AI] 新增（原版无）：书架类请求专用——本地有缓存（含别名命中）就直接用，
     * 绝不进“解析书源规则→可能跑登录检测脚本/弹 toast/走网络”的慢速路径。
     * 详情页不设置此标志：在线时仍会拉新链接并刷新别名，保证封面真换图后能更新。
     */
    val PreferCache = Extras.Key<Boolean?>(null)
}
