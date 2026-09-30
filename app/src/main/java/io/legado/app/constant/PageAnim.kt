package io.legado.app.constant

import androidx.annotation.IntDef

@Suppress("ConstPropertyName")
object PageAnim {

    const val coverPageAnim = 0

    const val slidePageAnim = 1

    const val simulationPageAnim = 2

    const val scrollPageAnim = 3

    const val fadePageAnim = 4
    const val noAnim = 5

    /** 我们加的叠页（iPhone Duo 风格）翻页，接在官方取值后面，不复用已有编号。 */
    const val duoPageAnim = 6

    @Target(AnnotationTarget.VALUE_PARAMETER)
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(
        coverPageAnim,
        slidePageAnim,
        simulationPageAnim,
        scrollPageAnim,
        fadePageAnim,
        noAnim,
        duoPageAnim,
    )
    annotation class Anim

}
