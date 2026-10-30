package io.legado.app.feature.reader.core.transition.ioscurl.mesh.kernel

import io.legado.app.feature.reader.core.transition.ioscurl.mesh.CurlDrawBatch
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlRect

/**
 * Reusable geometry storage. KernelValues names each live range separately. Disjoint live ranges
 * reuse storage; vector bit views preserve packed colors.
 */
internal class CurlMeshKernel {
    val memory = KernelMemory()
    val scratchBase = KernelLayout.Workspace.SCRATCH
    private val integers = LongArray(KernelLayout.Workspace.INTEGER_SLOTS)
    private val vectors = Array(KernelLayout.Workspace.VECTOR_SLOTS) { KernelVector() }

    fun integerValue(slot: Int): Long = integers[slot]

    fun integerValue(slot: Int, value: Long) {
        integers[slot] = value
    }

    fun vectorValue(slot: Int): KernelVector = vectors[slot]

    var negativeResult = false
    var zeroResult = false
    var unsignedCarry = false
    var arithmeticOverflow = false
    var clipped = 0L
    val FADE_WEIGHTS = KernelLayout.Workspace.FADE_WEIGHTS
    val FADE_STEPS = KernelLayout.Workspace.FADE_STEPS
    val CLAMP_STEPS = KernelLayout.Workspace.INNER_CLAMP
    val CLAMP_STEPS2 = KernelLayout.Workspace.OUTER_CLAMP
    var clip = CurlRect(0.0, 0.0, 0.0, 0.0)
    val batches = ArrayList<CurlDrawBatch>(KernelTuning.INITIAL_BATCH_CAPACITY)

    fun resetWorkspace() {
        memory.clear()
        batches.clear()
        integers.fill(0L)
        vectors.forEach { it.clear() }
        clipped = 0L
        negativeResult = false
        zeroResult = false
        unsignedCarry = false
        arithmeticOverflow = false
    }
}
