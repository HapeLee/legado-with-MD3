package io.legado.app.constant

@Suppress("ConstPropertyName")
object SourceType {

    const val book = 0
    const val rss = 1

    /**
     * 书源类型取值标记。移除 `androidx.annotation.IntDef` 的原因见 [PageAnim.Anim]。
     */
    @Target(AnnotationTarget.VALUE_PARAMETER)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Type

}
