package io.legado.app.constant

@Suppress("ConstPropertyName")
object PageAnim {

    const val coverPageAnim = 0

    const val slidePageAnim = 1

    const val simulationPageAnim = 2

    const val scrollPageAnim = 3

    const val fadePageAnim = 4
    const val noAnim = 5

    /**
     * 翻页动画取值标记。
     *
     * 迁移到 commonMain 时移除了 `androidx.annotation.IntDef`：它是 `@Retention(SOURCE)`
     * 的编译期辅助注解，运行时与 R8 均无影响，而 commonMain 拿不到 androidx.annotation。
     * 保留本注解类是为了让既有调用点（`@PageAnim.Anim`）无需改动；
     * 代价是失去 IDE 对「取值必须落在上述常量集合内」的校验。
     */
    @Target(AnnotationTarget.VALUE_PARAMETER)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Anim

}
