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
    
    const val simulationPageAnimV2 = 6

    @Target(AnnotationTarget.VALUE_PARAMETER)
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(coverPageAnim, slidePageAnim, simulationPageAnim, scrollPageAnim, fadePageAnim, noAnim, simulationPageAnimV2)
    annotation class Anim

}