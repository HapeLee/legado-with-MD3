// Generated curl mesh algorithm stages.
@file:Suppress("UNUSED_VARIABLE", "UNUSED_VALUE", "NAME_SHADOWING", "RedundantConversion")

package io.legado.app.feature.reader.core.transition.ioscurl.mesh.kernel

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import io.legado.app.feature.reader.core.transition.ioscurl.math.BinaryMath.fma

// Reference step 0: entry
internal fun CurlMeshKernel.prepareMaterialsAndBounds(): KernelStep {
    writeLongBits((scratchBase + 88L), outputTransformArgument)
    writeLongBits((scratchBase + 128L), sourceSurfaceAddress)
    materialAddress = materialInputAddress
    controlAddress = controlInputAddress
    renderContextAddress = contextArgument
    layerStateAddress = readLongBits((contextArgument + KernelLayout.Context.LAYER_STATE))
    frontMaterialColor.double(
        0,
        readDouble((materialInputAddress + KernelLayout.Material.FRONT_COLOR)),
    )
    frontColorMultiplier.double(
        0,
        readDouble(((materialInputAddress + KernelLayout.Material.FRONT_COLOR) + 8L)),
    )
    backMaterialColor.double(
        0,
        readDouble((materialInputAddress + KernelLayout.Material.BACK_COLOR)),
    )
    backLightingColor.double(
        0,
        readDouble(((materialInputAddress + KernelLayout.Material.BACK_COLOR) + 8L)),
    )
    shadowMaterialColor.double(
        0,
        readDouble((materialInputAddress + KernelLayout.Material.SHADOW_COLOR)),
    )
    projectionAddress =
        (readUnsignedByte((materialInputAddress + KernelLayout.Material.LIGHTING_ENABLED)) and
            KernelLayout.WORD_MASK)
    writeWord((scratchBase + 440L), (projectionAddress and KernelLayout.WORD_MASK))
    frontBaseRgba.unpackHalfColorFrom(frontMaterialColor)
    writeLongBits((scratchBase + 80L), layerStateAddress)
    layerColorMultiplier.double(0, readDouble((layerStateAddress + 32L)))
    layerTintRgba.unpackHalfColorFrom(layerColorMultiplier)
    frontTintRgba.multiplyColorsFrom(frontBaseRgba, layerTintRgba)
    packedFrontTint.packHalfColorFrom(frontTintRgba)
    writeDouble((scratchBase + 520L), packedFrontTint.double(0))
    frontMultiplierRgba.unpackHalfColorFrom(frontColorMultiplier)
    frontSecondaryTintRgba.multiplyColorsFrom(frontMultiplierRgba, layerTintRgba)
    packedFrontSecondaryTint.packHalfColorFrom(frontSecondaryTintRgba)
    writeDouble((scratchBase + 72L), packedFrontSecondaryTint.double(0))
    backLightingRgba.unpackHalfColorFrom(backLightingColor)
    backLightingTintRgba.multiplyColorsFrom(backLightingRgba, layerTintRgba)
    packedBackLightingTint.packHalfColorFrom(backLightingTintRgba)
    writeDouble((scratchBase + 240L), packedBackLightingTint.double(0))
    backBaseRgba.unpackHalfColorFrom(backMaterialColor)
    backTintRgba.multiplyColorsFrom(backBaseRgba, layerTintRgba)
    backTintRgba.store(memory, (scratchBase + 400L))
    shadowBaseRgba.unpackHalfColorFrom(shadowMaterialColor)
    shadowTintRgba.multiplyColorsFrom(shadowBaseRgba, layerTintRgba)
    packedShadowTint.packHalfColorFrom(shadowTintRgba)
    writeDouble((scratchBase + 208L), packedShadowTint.double(0))
    curlRadius = readDouble((controlInputAddress + KernelLayout.Control.RADIUS))
    minimumCurlAngle.double(
        0,
        readDouble(((controlInputAddress + KernelLayout.Control.RADIUS) + 8L)),
    )
    writeDouble((scratchBase + 512L), curlRadius)
    halfPiRadians = 1.5707963267948966
    maximumCurlAngle = readDouble((controlInputAddress + KernelLayout.Control.END_ANGLE))
    writeDouble((scratchBase + 504L), maximumCurlAngle)
    curlNormalX = readDouble((controlInputAddress + KernelLayout.Control.NORMAL_X))
    writeDouble((scratchBase + 536L), curlNormalX)
    curlNormalY = readDouble((controlInputAddress + KernelLayout.Control.NORMAL_Y))
    curlTangentX = readDouble(((controlInputAddress + KernelLayout.Control.NORMAL_Y) + 8L))
    writeDouble((scratchBase + 528L), curlNormalY)
    curlTangentY = readDouble((controlInputAddress + KernelLayout.Control.TANGENT_Y))
    projectedTangentX = readDouble((materialInputAddress + KernelLayout.Material.TANGENT_X))
    projectedTangentY = readDouble(((materialInputAddress + KernelLayout.Material.TANGENT_X) + 8L))
    compareFloatingPoint(projectedTangentY, 0.0)
    projectionValue = 0.45f.toDouble()
    writeDouble((scratchBase + 544L), projectedTangentY)
    projectionValue2 = (if (!(zeroResult)) projectionValue else projectedTangentY)
    projectionValue3 = -(projectionValue2)
    projectionValue4 =
        (if ((!(zeroResult) && (negativeResult == arithmeticOverflow))) projectionValue2
        else projectionValue3)
    writeDouble((scratchBase + 304L), projectionValue4)
    sourceLeft = readDouble((materialInputAddress + KernelLayout.Material.SOURCE_BOUNDS))
    sourceTop = readDouble(((materialInputAddress + KernelLayout.Material.SOURCE_BOUNDS) + 8L))
    sourceWidth = readDouble((materialInputAddress + 184L))
    sourceHeight = readDouble(((materialInputAddress + 184L) + 8L))
    projectionValue5 = (sourceLeft + sourceWidth)
    projectionValue6 =
        (if ((!(zeroResult) && (negativeResult == arithmeticOverflow))) sourceLeft
        else projectionValue5)
    writeDouble((scratchBase + 568L), projectionValue6)
    projectionValue7 =
        (if ((!(zeroResult) && (negativeResult == arithmeticOverflow))) projectionValue5
        else sourceLeft)
    writeDouble((scratchBase + 472L), projectionValue7)
    writeDouble(((scratchBase + 472L) + 8L), curlTangentY)
    compareFloatingPoint(projectedTangentX, 0.0)
    writeDouble((scratchBase + 560L), projectedTangentX)
    projectionValue8 = (if (!(zeroResult)) projectionValue else projectedTangentX)
    projectionValue9 = -(projectionValue8)
    projectionValue10 = (if (negativeResult) projectionValue9 else projectionValue8)
    projectionValue11 = (sourceTop + sourceHeight)
    projectionValue12 = (if (negativeResult) sourceTop else projectionValue11)
    writeDouble((scratchBase + 320L), sourceTop)
    writeDouble((scratchBase + 344L), projectionValue11)
    projectionValue13 = (if (negativeResult) projectionValue11 else sourceTop)
    writeDouble((scratchBase + 416L), projectionValue13)
    projectionValue14 = (if (!(negativeResult)) sourceLeft else projectionValue5)
    writeDouble((scratchBase + 392L), sourceLeft)
    projectionValue15 = (if (!(negativeResult)) projectionValue5 else sourceLeft)
    pageLeft = readDouble((materialInputAddress + KernelLayout.Material.PAGE_BOUNDS))
    pageTop = readDouble(((materialInputAddress + KernelLayout.Material.PAGE_BOUNDS) + 8L))
    pageWidth = readDouble((materialInputAddress + 152L))
    pageHeight = readDouble(((materialInputAddress + 152L) + 8L))
    projectionValue16 = (pageLeft + pageWidth)
    compareFloatingPoint(curlTangentY, 0.0)
    projectionValue17 =
        (if ((!(zeroResult) && (negativeResult == arithmeticOverflow))) pageLeft
        else projectionValue16)
    projectionValue18 = (pageTop + pageHeight)
    projectionValue19 =
        (if ((!(zeroResult) && (negativeResult == arithmeticOverflow))) projectionValue16
        else pageLeft)
    writeDouble((scratchBase + 336L), projectionValue19)
    writeDouble((scratchBase + 488L), curlTangentX)
    compareFloatingPoint(curlTangentX, 0.0)
    projectionValue20 = (if (negativeResult) pageTop else projectionValue18)
    projectionValue21 = (if (negativeResult) projectionValue18 else pageTop)
    projectedNormalX = readDouble((materialInputAddress + KernelLayout.Material.NORMAL_X))
    writeDouble((scratchBase + 464L), projectedNormalX)
    projectedNormalY = readDouble((materialInputAddress + KernelLayout.Material.NORMAL_Y))
    writeDouble((scratchBase + 456L), projectedNormalY)
    writeDouble((scratchBase + 672L), projectionValue12)
    writeDouble((scratchBase + 664L), projectionValue6)
    pointTransformAddress = (materialInputAddress + KernelLayout.Material.INVERSE_TRANSFORM)
    projectionValue22.double(0, max(minimumCurlAngle.double(0), halfPiRadians))
    writeDouble((scratchBase + 368L), projectionValue14)
    projectionValue23 = (projectionValue14 - projectionValue10)
    writeDouble((scratchBase + 432L), projectionValue23)
    writeDouble((scratchBase + 352L), projectionValue15)
    projectionValue24 = (projectionValue15 - projectionValue10)
    writeDouble((scratchBase + 424L), projectionValue24)
    transformedPointAddress = (scratchBase + 664L)
    transformPointInPlace(pointTransformAddress, transformedPointAddress)
    projectionValue25.set(projectionValue22)
    projectionValue26 = readDouble((scratchBase + 304L))
    projectionValue27 = readDouble((scratchBase + 672L))
    projectionValue28 = readDouble((scratchBase + 664L))
    projectionValue29 = (projectionValue17 - projectionValue28)
    projectionValue30 = (projectionValue20 - projectionValue27)
    normalX = readDouble((scratchBase + 536L))
    normalY = readDouble((scratchBase + 528L))
    projectionValue31 = (normalY * projectionValue30)
    projectionValue32 = fma(normalX, projectionValue29, projectionValue31)
    projectionValue33 = (halfPiRadians - minimumCurlAngle.double(0))
    curlRadius2 = readDouble((scratchBase + 512L))
    projectionValue34 = (curlRadius2 * projectionValue33)
    projectionValue35 = (projectionValue32 - projectionValue34)
    projectionValue36 = readDouble((scratchBase + 336L))
    projectionValue37 = (projectionValue36 - projectionValue28)
    projectionValue38.set(minimumCurlAngle)
    projectionValue39 = (projectionValue21 - projectionValue27)
    projectionValue40 = (normalY * projectionValue39)
    projectionValue41 = fma(normalX, projectionValue37, projectionValue40)
    projectionValue42 = (projectionValue25.double(0)).toFloat()
    projectionValue43 = (projectionValue42 * projectionValue42)
    projectionValue44 = -(0.1534627f)
    projectionValue45 = 0.0054653967f
    projectionValue46 = fma(projectionValue43, projectionValue45, projectionValue44)
    projectionValue47 = 0.9844168f
    projectionValue48 = fma(projectionValue43, projectionValue46, projectionValue47)
    projectionValue49 = (projectionValue48 * projectionValue42)
    projectionValue50 = (projectionValue49).toDouble()
    projectionValue51 = (projectionValue50 - projectionValue25.double(0))
    projectionValue52 = (minimumCurlAngle.double(0)).toFloat()
    projectionValue53 = (projectionValue52 * projectionValue52)
    projectionValue54 = fma(projectionValue53, projectionValue45, projectionValue44)
    projectionValue55 = fma(projectionValue53, projectionValue54, projectionValue47)
    projectionValue56 = (projectionValue55 * projectionValue52)
    projectionValue57 = (projectionValue56).toDouble()
    projectionValue58 = (minimumCurlAngle.double(0) - projectionValue57)
    projectionValue59 = (projectionValue58 + projectionValue51)
    halfPiRadians2 = -(1.5707963267948966)
    projectionValue60 = (projectionValue25.double(0) + halfPiRadians2)
    projectionValue61 = (projectionValue60 + projectionValue59)
    projectionValue62 = fma(curlRadius2, projectionValue61, projectionValue41)
    projectionValue63 = readDouble(controlAddress)
    projectionValue64 = (projectionValue62 - projectionValue35)
    projectionValue65 = readDouble((scratchBase + 472L))
    projectionValue66 = readDouble((scratchBase + 568L))
    projectionValue67 = (projectionValue65 - projectionValue66)
    writeDouble((scratchBase + 472L), projectionValue12)
    projectionValue68 = readDouble((scratchBase + 416L))
    projectionValue69 = (projectionValue68 - projectionValue12)
    projectionValue70.float(0, (projectionValue10).toFloat())
    projectionValue71.float(0, (projectionValue67).toFloat())
    for (lane in 0 until 4) {
        projectionValue72.wordBits(lane, (128L shl 24))
    }
    for (lane in 0 until 2) {
        projectionValue73.longBits(
            lane,
            ((projectionValue70.longBits(lane) and (projectionValue72.longBits(lane)).inv()) or
                (projectionValue71.longBits(lane) and projectionValue72.longBits(lane))),
        )
    }
    projectionValue74 = (projectionValue73.float(0)).toDouble()
    projectionValue75.float(0, (projectionValue26).toFloat())
    projectionValue76.float(0, (projectionValue69).toFloat())
    for (lane in 0 until 2) {
        projectionValue77.longBits(
            lane,
            ((projectionValue75.longBits(lane) and (projectionValue72.longBits(lane)).inv()) or
                (projectionValue76.longBits(lane) and projectionValue72.longBits(lane))),
        )
    }
    projectionValue78 = (projectionValue77.float(0)).toDouble()
    projectionValue79 = -(projectionValue74)
    projectionValue80 = (projectionValue67 + projectionValue74)
    projectionValue81 = (projectionValue69 + projectionValue78)
    writeDouble((scratchBase + 2536L), projectionValue81)
    writeDouble((scratchBase + 2528L), projectionValue80)
    projectionValue82 = (projectionValue67 - projectionValue74)
    projectionValue83 = (projectionValue69 - projectionValue78)
    writeDouble((scratchBase + 2552L), projectionValue83)
    writeDouble((scratchBase + 2544L), projectionValue82)
    writeDouble((scratchBase + 2568L), projectionValue81)
    writeDouble((scratchBase + 2560L), projectionValue79)
    projectionAddress2 = (scratchBase + 2528L)
    projectionAddress3 = (projectionAddress2 + 40L)
    writeDouble((scratchBase + 2584L), projectionValue83)
    writeDouble((scratchBase + 2576L), projectionValue74)
    projectionValue84 = -(projectionValue78)
    writeDouble((scratchBase + 2600L), projectionValue84)
    writeDouble((scratchBase + 2592L), projectionValue80)
    writeDouble((scratchBase + 2616L), projectionValue78)
    writeDouble((scratchBase + 2608L), projectionValue82)
    writeDouble((scratchBase + 2632L), projectionValue78)
    writeDouble((scratchBase + 2624L), projectionValue74)
    writeDouble((scratchBase + 2648L), projectionValue84)
    writeDouble((scratchBase + 2640L), projectionValue79)
    projectionValue85 = readDouble((materialAddress + KernelLayout.Material.INVERSE_TRANSFORM))
    projectionValue86 =
        readDouble(((materialAddress + KernelLayout.Material.INVERSE_TRANSFORM) + 8L))
    projectionValue87 = readDouble((materialAddress + 40L))
    projectionValue88 = readDouble(((materialAddress + 40L) + 8L))
    projectionValue89 = (projectionValue87 * projectionValue81)
    projectionValue90 = fma(projectionValue85, projectionValue80, projectionValue89)
    projectionValue91 = (projectionValue88 * projectionValue81)
    projectionValue92 = fma(projectionValue86, projectionValue80, projectionValue91)
    projectionValue93 = (normalY * projectionValue92)
    projectionValue94 = fma(normalX, projectionValue90, projectionValue93)
    writeDouble((scratchBase + 2656L), projectionValue94)
    projectionIndex = ((0L or 8L) and KernelLayout.WORD_MASK)
    projectionAddress4 = (scratchBase + 2656L)
    return KernelStep.ProjectPageCorner
}

// Reference step 1: L18534454c
internal fun CurlMeshKernel.advanceProjectedCorner(): KernelStep {
    projectionValue82 = readDouble((projectionAddress3 + -(8L)))
    projectionValue83 = readDouble(((projectionAddress3 + -(8L)) + 8L))
    projectionAddress3 = (projectionAddress3 + 16L)
    projectionIndex = (projectionIndex + 8L)
    return KernelStep.ProjectPageCorner
}

// Reference step 2: L185344558
internal fun CurlMeshKernel.projectPageCorner(): KernelStep {
    projectionValue95 = (projectionValue87 * projectionValue83)
    projectionValue96 = fma(projectionValue85, projectionValue82, projectionValue95)
    projectionValue97 = (projectionValue88 * projectionValue83)
    projectionValue98 = fma(projectionValue86, projectionValue82, projectionValue97)
    projectionValue99 = (normalY * projectionValue98)
    projectionValue100 = fma(normalX, projectionValue96, projectionValue99)
    writeDouble((projectionAddress4 + projectionIndex), projectionValue100)
    updateSubtractionFlags(projectionIndex, 56L, 64L)
    return if (!(zeroResult)) KernelStep.AdvanceProjectedCorner
    else KernelStep.PrepareBreakpointSort
}

// Reference step 3: fallthrough3
internal fun CurlMeshKernel.prepareBreakpointSort(): KernelStep {
    projectionValue101 = readDouble((scratchBase + 520L))
    writeDouble((scratchBase + 416L), projectionValue101)
    projectionValue102.load(memory, (scratchBase + 400L))
    projectionColor16.packHalfColorFrom(projectionValue102)
    projectionColor16.store(memory, (scratchBase + 112L))
    projectionValue103 = 1.0
    projectionValue104.double(0, readDouble((scratchBase + 488L)))
    projectionValue105 = (projectionValue103 / projectionValue104.double(0))
    projectionValue106 = readDouble((scratchBase + 560L))
    projectionValue107 = (projectionValue103 / projectionValue106)
    writeDouble((scratchBase + 400L), projectionValue107)
    projectionValue108 = readDouble((scratchBase + 568L))
    projectionValue109 = readDouble((scratchBase + 392L))
    projectionValue110 = (projectionValue108 - projectionValue109)
    writeDouble((scratchBase + 392L), projectionValue110)
    projectionValue111 = readDouble((scratchBase + 368L))
    projectionValue112 = (projectionValue10 + projectionValue111)
    writeDouble((scratchBase + 368L), projectionValue112)
    projectionValue113 = readDouble((scratchBase + 352L))
    projectionValue114 = (projectionValue10 + projectionValue113)
    writeDouble((scratchBase + 352L), projectionValue114)
    projectionValue115 = (projectionValue63 * projectionValue64)
    projectionValue116 = readDouble((scratchBase + 544L))
    compareFloatingPoint(projectionValue116, 0.0)
    projectionValue117 = readDouble((scratchBase + 320L))
    projectionValue118 = readDouble((scratchBase + 344L))
    projectionValue119 =
        (if ((!(zeroResult) && (negativeResult == arithmeticOverflow))) projectionValue117
        else projectionValue118)
    projectionValue120 =
        (if ((!(zeroResult) && (negativeResult == arithmeticOverflow))) projectionValue118
        else projectionValue117)
    projectionValue121 = (projectionValue26 + projectionValue119)
    writeDouble((scratchBase + 344L), projectionValue121)
    projectionValue122 = (projectionValue26 + projectionValue120)
    writeDouble((scratchBase + 336L), projectionValue122)
    projectionAddress5 = (scratchBase + 2656L)
    projectionValue123 = readDouble((scratchBase + 2704L))
    projectionValue124.double(0, readDouble((scratchBase + 2664L)))
    projectionIndex2 = ((0L or 1L) and KernelLayout.WORD_MASK)
    projectionAddress6 = (scratchBase + 2656L)
    projectionValue125.set(projectionValue124)
    projectionValue126 = (projectionValue103 / projectionValue116)
    writeDouble((scratchBase + 328L), projectionValue126)
    projectionValue127 = readDouble((scratchBase + 504L))
    projectionValue128.double(0, readDouble((scratchBase + 480L)))
    return KernelStep.BeginBreakpointInsertion
}

// Reference step 4: L185344628
internal fun CurlMeshKernel.advanceBreakpointSort(): KernelStep {
    projectionValue125.double(0, readDouble((projectionAddress5 + (projectionIndex2 shl 3))))
    projectionAddress6 = (projectionAddress6 + 8L)
    return KernelStep.BeginBreakpointInsertion
}

// Reference step 5: L185344630
internal fun CurlMeshKernel.beginBreakpointInsertion(): KernelStep {
    projectionIndex3 = 0L
    return KernelStep.CompareBreakpointInsertion
}

// Reference step 6: L185344634
internal fun CurlMeshKernel.compareBreakpointInsertion(): KernelStep {
    projectionValue129 = readDouble((projectionAddress6 + (projectionIndex3 shl 3)))
    compareFloatingPoint(projectionValue125.double(0), projectionValue129)
    return if ((zeroResult || (negativeResult != arithmeticOverflow)))
        KernelStep.SelectBreakpointInsertion
    else KernelStep.ShiftBreakpointForInsertion
}

// Reference step 7: fallthrough7
internal fun CurlMeshKernel.shiftBreakpointForInsertion(): KernelStep {
    projectionBits = (projectionAddress6 + (projectionIndex3 shl 3))
    writeDouble((projectionBits + 8L), projectionValue129)
    projectionIndex3 = (projectionIndex3 - 1L)
    projectionBits2 = (projectionIndex2 + projectionIndex3)
    updateSubtractionFlags(projectionBits2, 0L, 64L)
    return if ((!(zeroResult) && (negativeResult == arithmeticOverflow)))
        KernelStep.CompareBreakpointInsertion
    else KernelStep.FinishBreakpointInsertion
}

// Reference step 8: fallthrough8
internal fun CurlMeshKernel.finishBreakpointInsertion(): KernelStep {
    return KernelStep.StoreSortedBreakpoint
}

// Reference step 9: L18534465c
internal fun CurlMeshKernel.selectBreakpointInsertion(): KernelStep {
    projectionBits2 = (projectionIndex2 + projectionIndex3)
    return KernelStep.StoreSortedBreakpoint
}

// Reference step 10: L185344660
internal fun CurlMeshKernel.storeSortedBreakpoint(): KernelStep {
    projectionBits3 = ((projectionBits2).toInt().toLong() shl 3)
    writeDouble((projectionAddress5 + projectionBits3), projectionValue125.double(0))
    projectionIndex2 = (projectionIndex2 + 1L)
    updateSubtractionFlags(projectionIndex2, 8L, 64L)
    return if (!(zeroResult)) KernelStep.AdvanceBreakpointSort else KernelStep.FinishBreakpointSort
}

// Reference step 11: fallthrough11
internal fun CurlMeshKernel.finishBreakpointSort(): KernelStep {
    projectionValue130 = (projectionValue35 + projectionValue115)
    projectionValue131 = fma(-(curlRadius2), projectionValue60, projectionValue130)
    projectionValue132 = (projectionValue25.double(0) - projectionValue38.double(0))
    projectionValue133 = (projectionValue127 - projectionValue25.double(0))
    compareFloatingPoint(projectionValue132, projectionValue133)
    writeDouble((scratchBase + 48L), projectionValue132)
    writeDouble((scratchBase + 16L), projectionValue133)
    projectionValue134 =
        (if ((!(zeroResult) && (negativeResult == arithmeticOverflow))) projectionValue132
        else projectionValue133)
    projectionValue135 = (curlRadius2 * projectionValue134)
    projectionValue136 = 0.08333333333333333
    projectionValue137 = (projectionValue135 * projectionValue136)
    compareFloatingPoint(projectionValue137, 0.0)
    writeDouble((scratchBase + 520L), projectionValue130)
    return if (!(zeroResult)) KernelStep.DeriveAdaptiveSampleRange
    else KernelStep.UseTwoCurveEndpoints
}

// Reference step 12: fallthrough12
internal fun CurlMeshKernel.useTwoCurveEndpoints(): KernelStep {
    writeDouble((scratchBase + 2728L), projectionValue131)
    writeDouble((scratchBase + 2720L), projectionValue131)
    projectionIndex4 = ((0L or 2L) and KernelLayout.WORD_MASK)
    return KernelStep.PrepareSampleMerge
}

// Reference step 13: L1853446c0
internal fun CurlMeshKernel.deriveAdaptiveSampleRange(): KernelStep {
    projectionValue138 = (projectionValue34 + projectionValue130)
    compareFloatingPoint(projectionValue124.double(0), projectionValue138)
    projectionValue139 = (if (negativeResult) projectionValue124.double(0) else projectionValue138)
    projectionValue140 = (halfPiRadians - projectionValue127)
    projectionValue141 = fma(curlRadius2, projectionValue140, projectionValue130)
    compareFloatingPoint(projectionValue123, projectionValue141)
    projectionValue142 =
        (if ((!(zeroResult) && (negativeResult == arithmeticOverflow))) projectionValue123
        else projectionValue141)
    projectionValue143 = (projectionValue131 - projectionValue139)
    projectionValue144 = (projectionValue143 / projectionValue137)
    projectionValue145 = (projectionValue144).toFloat()
    projectionValue146 = floor(projectionValue145)
    projectionValue147 = (-(12.0)).toFloat()
    projectionValue148 = max(projectionValue146, projectionValue147)
    projectionIndex5 = (((projectionValue148).toLong()).toInt().toLong() and KernelLayout.WORD_MASK)
    projectionValue149 = (projectionValue131 - projectionValue142)
    projectionValue150 = (projectionValue149 / projectionValue137)
    projectionValue151 = (projectionValue150).toFloat()
    projectionValue152 = ceil(projectionValue151)
    projectionValue153 = (12.0).toFloat()
    projectionValue154 = min(projectionValue152, projectionValue153)
    projectionIndex6 = (((projectionValue154).toLong()).toInt().toLong() and KernelLayout.WORD_MASK)
    updateSubtractionFlags(
        (projectionIndex5 and KernelLayout.WORD_MASK),
        (projectionIndex6 and KernelLayout.WORD_MASK),
        32L,
    )
    return if ((zeroResult || (negativeResult != arithmeticOverflow)))
        KernelStep.PrepareAdaptiveSamples
    else KernelStep.SkipEmptySampleRange
}

// Reference step 14: fallthrough14
internal fun CurlMeshKernel.skipEmptySampleRange(): KernelStep {
    projectionIndex4 = (0L and KernelLayout.WORD_MASK)
    return KernelStep.PrepareSampleMerge
}

// Reference step 15: L185344724
internal fun CurlMeshKernel.prepareAdaptiveSamples(): KernelStep {
    projectionIndex4 = (0L and KernelLayout.WORD_MASK)
    projectionAddress7 = (scratchBase + 2720L)
    return KernelStep.EvaluateAdaptiveSample
}

// Reference step 16: L18534472c
internal fun CurlMeshKernel.evaluateAdaptiveSample(): KernelStep {
    projectionValue155 =
        (((projectionIndex5 and KernelLayout.WORD_MASK)).toInt().toLong()).toFloat()
    projectionValue156 = (projectionValue155).toDouble()
    projectionValue157 = fma(-(projectionValue137), projectionValue156, projectionValue131)
    compareFloatingPoint(projectionValue139, projectionValue157)
    projectionValue158 = (if (negativeResult) projectionValue139 else projectionValue157)
    compareFloatingPoint(projectionValue142, projectionValue158)
    projectionValue159 =
        (if ((!(zeroResult) && (negativeResult == arithmeticOverflow))) projectionValue142
        else projectionValue158)
    updateSubtractionFlags((projectionIndex5 and KernelLayout.WORD_MASK), 0L, 32L)
    return if ((!(zeroResult) && (negativeResult == arithmeticOverflow)))
        KernelStep.AppendPositiveSample
    else KernelStep.PreserveNegativeSample
}

// Reference step 17: fallthrough17
internal fun CurlMeshKernel.preserveNegativeSample(): KernelStep {
    writeDouble(
        (projectionAddress7 +
            (((projectionIndex4 and KernelLayout.WORD_MASK)).toInt().toLong() shl 3)),
        projectionValue159,
    )
    projectionIndex4 =
        (((projectionIndex4 and KernelLayout.WORD_MASK) + 1L) and KernelLayout.WORD_MASK)
    return if ((((projectionIndex5 and KernelLayout.WORD_MASK) and (1L shl 31)) != 0L))
        KernelStep.AdvanceAdaptiveSample
    else KernelStep.AppendPositiveSample
}

// Reference step 18: L18534475c
internal fun CurlMeshKernel.appendPositiveSample(): KernelStep {
    writeDouble(
        (projectionAddress7 +
            (((projectionIndex4 and KernelLayout.WORD_MASK)).toInt().toLong() shl 3)),
        projectionValue159,
    )
    projectionIndex4 =
        (((projectionIndex4 and KernelLayout.WORD_MASK) + 1L) and KernelLayout.WORD_MASK)
    return KernelStep.AdvanceAdaptiveSample
}

// Reference step 19: L185344764
internal fun CurlMeshKernel.advanceAdaptiveSample(): KernelStep {
    projectionAddress8 =
        (((projectionIndex5 and KernelLayout.WORD_MASK) + 1L) and KernelLayout.WORD_MASK)
    updateSubtractionFlags(
        (projectionIndex5 and KernelLayout.WORD_MASK),
        (projectionIndex6 and KernelLayout.WORD_MASK),
        32L,
    )
    projectionIndex5 = projectionAddress8
    return if ((negativeResult != arithmeticOverflow)) KernelStep.EvaluateAdaptiveSample
    else KernelStep.PrepareSampleMerge
}

// Reference step 20: L185344774
internal fun CurlMeshKernel.prepareSampleMerge(): KernelStep {
    projectionIndex7 = 0L
    projectionIndex8 = (0L and KernelLayout.WORD_MASK)
    projectionIndex9 = (0L and KernelLayout.WORD_MASK)
    projectionIndex10 = (0L and KernelLayout.WORD_MASK)
    projectionIndex11 = (0L and KernelLayout.WORD_MASK)
    projectionValue160 = readDouble((scratchBase + 472L))
    projectionValue161 = (projectionValue160 - projectionValue117)
    writeDouble((scratchBase + 320L), projectionValue161)
    projectionValue162 = (projectionValue119 - projectionValue26)
    writeDouble((scratchBase + 312L), projectionValue162)
    projectionValue163 = (projectionValue120 - projectionValue26)
    writeDouble((scratchBase + 304L), projectionValue163)
    updateSubtractionFlags((projectionIndex4 and KernelLayout.WORD_MASK), 0L, 32L)
    projectionIndex12 =
        ((if ((!(zeroResult) && (negativeResult == arithmeticOverflow))) 1L else 0L) and
            KernelLayout.WORD_MASK)
    projectionAddress9 = (scratchBase + 2928L)
    projectionAddress10 = (scratchBase + 2720L)
    projectionIndex13 = (0L or 4294967296L)
    projectionAddress11 = (scratchBase + 2656L)
    projectionValue164 = 1.0
    projectionValue165 = (projectionValue164 / projectionValue128.double(0))
    return KernelStep.SelectSampleMergeSource
}

// Reference step 21: L1853447c4
internal fun CurlMeshKernel.selectSampleMergeSource(): KernelStep {
    updateSubtractionFlags((projectionIndex10 and KernelLayout.WORD_MASK), 8L, 32L)
    return if (!(zeroResult)) KernelStep.InspectCornerSample else KernelStep.TakeAdaptiveSample
}

// Reference step 22: fallthrough22
internal fun CurlMeshKernel.takeAdaptiveSample(): KernelStep {
    projectionAddress12 =
        readLongBits(
            (projectionAddress10 +
                (((projectionIndex11 and KernelLayout.WORD_MASK)).toInt().toLong() shl 3))
        )
    return KernelStep.AdvanceAdaptiveSource
}

// Reference step 23: L1853447d4
internal fun CurlMeshKernel.inspectCornerSample(): KernelStep {
    return if ((((projectionIndex12 and KernelLayout.WORD_MASK) and (1L shl 0)) == 0L))
        KernelStep.TakeProjectedCorner
    else KernelStep.CompareCornerSample
}

// Reference step 24: fallthrough24
internal fun CurlMeshKernel.compareCornerSample(): KernelStep {
    projectionValue166.double(
        0,
        readDouble(
            (projectionAddress10 +
                (((projectionIndex11 and KernelLayout.WORD_MASK)).toInt().toLong() shl 3))
        ),
    )
    projectionValue167.double(
        0,
        readDouble(
            (projectionAddress11 +
                (((projectionIndex10 and KernelLayout.WORD_MASK)).toInt().toLong() shl 3))
        ),
    )
    compareFloatingPoint(projectionValue166.double(0), projectionValue167.double(0))
    return if ((zeroResult || (negativeResult != arithmeticOverflow)))
        KernelStep.RetainAdaptiveEndpoint
    else KernelStep.TakeCornerSample
}

// Reference step 25: fallthrough25
internal fun CurlMeshKernel.takeCornerSample(): KernelStep {
    projectionAddress12 = projectionValue166.longBits(0)
    return KernelStep.AdvanceAdaptiveSource
}

// Reference step 26: L1853447ec
internal fun CurlMeshKernel.advanceAdaptiveSource(): KernelStep {
    projectionIndex11 =
        (((projectionIndex11 and KernelLayout.WORD_MASK) + 1L) and KernelLayout.WORD_MASK)
    return KernelStep.AppendMergedSample
}

// Reference step 27: L1853447f4
internal fun CurlMeshKernel.takeProjectedCorner(): KernelStep {
    projectionAddress12 =
        readLongBits(
            (projectionAddress11 +
                (((projectionIndex10 and KernelLayout.WORD_MASK)).toInt().toLong() shl 3))
        )
    return KernelStep.AdvanceCornerSource
}

// Reference step 28: L1853447fc
internal fun CurlMeshKernel.retainAdaptiveEndpoint(): KernelStep {
    projectionAddress12 = projectionValue167.longBits(0)
    return KernelStep.AdvanceCornerSource
}

// Reference step 29: L185344800
internal fun CurlMeshKernel.advanceCornerSource(): KernelStep {
    projectionIndex10 =
        (((projectionIndex10 and KernelLayout.WORD_MASK) + 1L) and KernelLayout.WORD_MASK)
    return KernelStep.AppendMergedSample
}

// Reference step 30: L185344804
internal fun CurlMeshKernel.appendMergedSample(): KernelStep {
    writeLongBits(projectionAddress9, projectionAddress12)
    projectionAddress9 = (projectionAddress9 + 8L)
    projectionIndex9 =
        (((projectionIndex9 and KernelLayout.WORD_MASK) + 2L) and KernelLayout.WORD_MASK)
    projectionIndex8 =
        (((projectionIndex8 and KernelLayout.WORD_MASK) + 1L) and KernelLayout.WORD_MASK)
    projectionIndex7 = (projectionIndex7 + projectionIndex13)
    updateSubtractionFlags(
        (projectionIndex11 and KernelLayout.WORD_MASK),
        (projectionIndex4 and KernelLayout.WORD_MASK),
        32L,
    )
    projectionIndex12 =
        ((if ((negativeResult != arithmeticOverflow)) 1L else 0L) and KernelLayout.WORD_MASK)
    return if ((negativeResult != arithmeticOverflow)) KernelStep.SelectSampleMergeSource
    else KernelStep.DrainAdaptiveSource
}

// Reference step 31: fallthrough31
internal fun CurlMeshKernel.drainAdaptiveSource(): KernelStep {
    updateSubtractionFlags((projectionIndex10 and KernelLayout.WORD_MASK), 8L, 32L)
    return if ((negativeResult != arithmeticOverflow)) KernelStep.SelectSampleMergeSource
    else KernelStep.PrepareSampleCompaction
}

// Reference step 32: fallthrough32
internal fun CurlMeshKernel.prepareSampleCompaction(): KernelStep {
    writeLongBits((scratchBase + 96L), controlAddress)
    projectionIndex14 = 0L
    projectionIndex15 = 0L
    projectionBits4 = (projectionIndex7 shr 32)
    projectionIndex16 = ((0L or 2L) and KernelLayout.WORD_MASK)
    projectionAddress13 = (scratchBase + 2928L)
    return KernelStep.CompactSampleGap
}

// Reference step 33: L185344840
internal fun CurlMeshKernel.compactSampleGap(): KernelStep {
    projectionAddress14 = (projectionIndex15 + 1L)
    projectionIndex16 =
        (((projectionIndex16 and KernelLayout.WORD_MASK) + 2L) and KernelLayout.WORD_MASK)
    projectionIndex14 = (projectionIndex14 + 128L)
    projectionIndex9 =
        (((projectionIndex9 and KernelLayout.WORD_MASK) - 2L) and KernelLayout.WORD_MASK)
    updateSubtractionFlags(projectionAddress14, projectionBits4, 64L)
    return if ((negativeResult == arithmeticOverflow)) KernelStep.PrepareVisibleSampleRange
    else KernelStep.InspectSampleGap
}

// Reference step 34: fallthrough34
internal fun CurlMeshKernel.inspectSampleGap(): KernelStep {
    projectionValue168 = readDouble((projectionAddress13 + (projectionIndex15 shl 3)))
    compareFloatingPoint(projectionValue168, projectionValue131)
    projectionIndex15 = projectionAddress14
    return if ((!(zeroResult) && (negativeResult == arithmeticOverflow)))
        KernelStep.CompactSampleGap
    else KernelStep.PrepareVisibleSampleRange
}

// Reference step 35: L185344868
internal fun CurlMeshKernel.prepareVisibleSampleRange(): KernelStep {
    writeLongBits((scratchBase + 176L), projectionIndex9)
    writeLongBits(((scratchBase + 176L) + 8L), materialAddress)
    projectionIndex17 = (0L and KernelLayout.WORD_MASK)
    projectionIndex18 = 0L
    projectionIndex19 =
        (((projectionIndex16 and KernelLayout.WORD_MASK) - 1L) and KernelLayout.WORD_MASK)
    projectionAddress15 = (scratchBase + (35L shl 12))
    projectionAddress16 = (projectionAddress15 + 1296L)
    projectionBits5 = (projectionAddress16 + projectionIndex14)
    projectionAddress17 = (projectionBits5 + 104L)
    projectionAddress18 = (scratchBase + (34L shl 12))
    projectionAddress19 = (projectionAddress18 + 1040L)
    writeLongBits((scratchBase + 104L), projectionIndex14)
    projectionBits6 = (projectionAddress19 + projectionIndex14)
    projectionAddress20 = (projectionBits6 + 40L)
    projectionBits7 = ((projectionIndex8 and KernelLayout.WORD_MASK) and KernelLayout.WORD_MASK)
    writeLongBits((scratchBase + 296L), projectionBits7)
    projectionBits8 = (projectionBits7 - projectionAddress14)
    projectionIndex20 = (0L or -2L)
    projectionAddress21 = (scratchBase + 2928L)
    writeWord((scratchBase + 44L), (projectionIndex16 and KernelLayout.WORD_MASK))
    projectionBits9 = projectionIndex16
    return KernelStep.FindVisibleRangeStart
}

// Reference step 36: L1853448b8
internal fun CurlMeshKernel.findVisibleRangeStart(): KernelStep {
    projectionValue169 = readDouble((projectionAddress21 + (projectionIndex18 shl 3)))
    projectionIndex18 = (projectionIndex18 + 1L)
    projectionIndex17 =
        (((projectionIndex17 and KernelLayout.WORD_MASK) - 1L) and KernelLayout.WORD_MASK)
    projectionIndex20 = (projectionIndex20 + 2L)
    projectionBits9 =
        (((projectionBits9 and KernelLayout.WORD_MASK) - 2L) and KernelLayout.WORD_MASK)
    compareFloatingPoint(projectionValue169, projectionValue124.double(0))
    return if ((!(zeroResult) && (negativeResult == arithmeticOverflow)))
        KernelStep.FindVisibleRangeStart
    else KernelStep.PrepareVisibleRangeEnd
}

// Reference step 37: fallthrough37
internal fun CurlMeshKernel.prepareVisibleRangeEnd(): KernelStep {
    writeDouble((scratchBase + 24L), projectionValue131)
    writeFloat((scratchBase + 148L), projectionValue47)
    writeFloat(((scratchBase + 148L) + 4L), projectionValue45)
    writeFloat((scratchBase + 156L), projectionValue44)
    writeDouble((scratchBase + 56L), projectionValue34)
    writeLongBits((scratchBase + 32L), projectionIndex20)
    writeDouble((scratchBase + 224L), projectionValue165)
    writeDouble(((scratchBase + 224L) + 8L), projectionValue105)
    writeDouble((scratchBase + 448L), halfPiRadians)
    projectionAddress22 =
        (((projectionIndex4 and KernelLayout.WORD_MASK) + 7L) and KernelLayout.WORD_MASK)
    projectionIndex21 =
        (((projectionIndex18 and KernelLayout.WORD_MASK) - 1L) and KernelLayout.WORD_MASK)
    projectionBits10 = ((projectionAddress22 and KernelLayout.WORD_MASK)).toInt().toLong()
    projectionBits11 =
        (((projectionIndex18 and KernelLayout.WORD_MASK) -
            (projectionAddress14 and KernelLayout.WORD_MASK)) and KernelLayout.WORD_MASK)
    projectionAddress23 = (scratchBase + 2928L)
    writeLongBits((scratchBase + 288L), projectionIndex21)
    return KernelStep.InspectVisibleRangeEnd
}

// Reference step 38: L185344908
internal fun CurlMeshKernel.inspectVisibleRangeEnd(): KernelStep {
    projectionBits12 = projectionIndex21
    projectionBits13 = projectionBits11
    updateSubtractionFlags(projectionIndex21, projectionBits10, 64L)
    return if ((negativeResult == arithmeticOverflow)) KernelStep.FinishVisibleSampleRange
    else KernelStep.ScanVisibleRangeEnd
}

// Reference step 39: fallthrough39
internal fun CurlMeshKernel.scanVisibleRangeEnd(): KernelStep {
    projectionIndex21 = (projectionBits12 + 1L)
    projectionBits14 = (projectionAddress23 + (projectionBits12 shl 3))
    projectionValue170 = readDouble((projectionBits14 + 8L))
    projectionBits11 =
        (((projectionBits13 and KernelLayout.WORD_MASK) + 1L) and KernelLayout.WORD_MASK)
    compareFloatingPoint(projectionValue170, projectionValue123)
    return if ((negativeResult == arithmeticOverflow)) KernelStep.InspectVisibleRangeEnd
    else KernelStep.FinishVisibleSampleRange
}

// Reference step 40: L185344930
internal fun CurlMeshKernel.finishVisibleSampleRange(): KernelStep {
    projectionIndex22 =
        (((projectionBits13 and KernelLayout.WORD_MASK) - 1L) and KernelLayout.WORD_MASK)
    writeWord((scratchBase + 68L), (projectionIndex22 and KernelLayout.WORD_MASK))
    projectionBits15 =
        (((projectionAddress14 and KernelLayout.WORD_MASK) -
            (projectionIndex18 and KernelLayout.WORD_MASK)) and KernelLayout.WORD_MASK)
    projectionAddress24 =
        (((projectionBits15 and KernelLayout.WORD_MASK) + 1L) and KernelLayout.WORD_MASK)
    updateSubtractionFlags((projectionAddress24 and KernelLayout.WORD_MASK), 0L, 32L)
    projectionIndex23 =
        ((if ((!(zeroResult) && (negativeResult == arithmeticOverflow)))
            (projectionBits9 and KernelLayout.WORD_MASK)
        else 0L) and KernelLayout.WORD_MASK)
    writeWord((scratchBase + 172L), (projectionIndex23 and KernelLayout.WORD_MASK))
    updateSubtractionFlags((projectionIndex8 and KernelLayout.WORD_MASK), 1L, 32L)
    projectionIndex24 =
        (((projectionIndex8 and KernelLayout.WORD_MASK) - 1L) and KernelLayout.WORD_MASK)
    writeDouble((scratchBase + 496L), projectionValue38.double(0))
    return if ((negativeResult != arithmeticOverflow)) KernelStep.PrepareBackStripIndices
    else KernelStep.PrepareSurfaceStrips
}
