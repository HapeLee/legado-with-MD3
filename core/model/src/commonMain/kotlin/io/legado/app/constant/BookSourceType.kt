package io.legado.app.constant

@Suppress("ConstPropertyName")
object BookSourceType {

    const val default = 0           // 0 文本
    const val audio = 1             // 1 音频
    const val image = 2            // 2 图片
    const val file = 3               // 3 只提供下载服务的网站

    /**
     * 书源类型取值标记。移除 `androidx.annotation.IntDef` 的原因见 [PageAnim.Anim]。
     */
    @Target(AnnotationTarget.VALUE_PARAMETER)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Type

}
