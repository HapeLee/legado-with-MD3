package io.legado.app.help.coil

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
}
