// Generated curl mesh algorithm stages.
@file:Suppress("UNUSED_VARIABLE", "UNUSED_VALUE", "NAME_SHADOWING", "RedundantConversion")

package io.legado.app.feature.reader.core.transition.ioscurl.mesh.kernel

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import io.legado.app.feature.reader.core.transition.ioscurl.math.AppleMath
import io.legado.app.feature.reader.core.transition.ioscurl.math.BinaryMath.fma

// Reference step 180: L185345ca4
internal fun CurlMeshKernel.inspectFrontFace(): KernelStep {
    faceBits = (readUnsignedByte(surfaceAddress21) and KernelLayout.WORD_MASK)
    faceValue = readDouble((scratchBase + 528L))
    faceValue2.double(0, readDouble((scratchBase + 496L)))
    return if (((faceBits and KernelLayout.WORD_MASK) == 0L)) KernelStep.SkipDisabledFrontFace
    else KernelStep.InspectFrontSampleRange
}

// Reference step 181: fallthrough181
internal fun CurlMeshKernel.inspectFrontSampleRange(): KernelStep {
    faceValue3 = readDouble((scratchBase + 56L))
    faceValue4 = (faceValue3 + shadowValue23.double(0))
    faceValue5 = readDouble((scratchBase + 672L))
    faceValue6 = readDouble((scratchBase + 664L))
    faceValue7 = (shadowValue11 - faceValue6)
    faceValue8 = (shadowValue7 - faceValue5)
    faceValue9 = (faceValue * faceValue8)
    faceValue10 = readDouble((scratchBase + 536L))
    faceValue11 = fma(faceValue10, faceValue7, faceValue9)
    faceValue12 = (shadowValue12 - faceValue6)
    faceValue13 = (shadowValue8 - faceValue5)
    faceValue14 = (faceValue * faceValue13)
    faceValue15 = fma(faceValue10, faceValue12, faceValue14)
    faceValue16 = readDouble((scratchBase + 48L))
    faceValue17 = (surfaceValue101.double(0) * faceValue16)
    faceValue18 = 0.01
    compareFloatingPoint(faceValue11, shadowValue23.double(0))
    faceValue19 =
        (if ((!(zeroResult) && (negativeResult == arithmeticOverflow))) faceValue11
        else shadowValue23.double(0))
    compareFloatingPoint(faceValue15, faceValue4)
    faceValue20 = (if (negativeResult) faceValue15 else faceValue4)
    faceValue21 = (faceValue20 - shadowValue23.double(0))
    faceValue22.longBits(0, 0L)
    faceValue23 = max(faceValue21, faceValue22.double(0))
    faceValue24 = -((faceValue17 * faceValue18))
    faceValue25 = (faceValue23 / faceValue24)
    faceValue26 = (faceValue25).toFloat()
    faceValue27 = floor(faceValue26)
    faceValue28 = -(1e+2f)
    faceValue29 = max(faceValue27, faceValue28)
    faceIndex = (((faceValue29).toLong()).toInt().toLong() and KernelLayout.WORD_MASK)
    updateSubtractionFlags((faceIndex and KernelLayout.WORD_MASK), 0L, 32L)
    return if ((zeroResult || (negativeResult != arithmeticOverflow)))
        KernelStep.PrepareFrontSamples
    else KernelStep.SkipEmptyFrontSamples
}

// Reference step 182: fallthrough182
internal fun CurlMeshKernel.skipEmptyFrontSamples(): KernelStep {
    faceIndex2 = (0L and KernelLayout.WORD_MASK)
    return KernelStep.InspectFirstFrontEdge
}

// Reference step 183: L185345d44
internal fun CurlMeshKernel.skipDisabledFrontFace(): KernelStep {
    writeWord((scratchBase + 528L), 0L)
    return KernelStep.PrepareDrawState
}

// Reference step 184: L185345d4c
internal fun CurlMeshKernel.prepareFrontSamples(): KernelStep {
    faceIndex2 = (0L and KernelLayout.WORD_MASK)
    faceValue30 = (faceValue17 * faceValue18)
    faceAddress = (scratchBase + 1504L)
    return KernelStep.SampleFrontRange
}

// Reference step 185: L185345d58
internal fun CurlMeshKernel.sampleFrontRange(): KernelStep {
    faceBits2 =
        (((faceIndex and KernelLayout.WORD_MASK) + (faceIndex2 and KernelLayout.WORD_MASK)) and
            KernelLayout.WORD_MASK)
    faceValue31 = (((faceBits2 and KernelLayout.WORD_MASK)).toInt().toLong()).toFloat()
    faceValue32 = (faceValue31).toDouble()
    faceValue33 = fma(-(faceValue30), faceValue32, shadowValue23.double(0))
    compareFloatingPoint(faceValue20, faceValue33)
    faceValue34 = (if (negativeResult) faceValue20 else faceValue33)
    compareFloatingPoint(faceValue19, faceValue34)
    faceValue35 =
        (if ((!(zeroResult) && (negativeResult == arithmeticOverflow))) faceValue19
        else faceValue34)
    writeDouble(faceAddress, faceValue35)
    faceAddress = (faceAddress + 8L)
    faceIndex2 = (((faceIndex2 and KernelLayout.WORD_MASK) + 1L) and KernelLayout.WORD_MASK)
    faceBits3 =
        (((faceIndex and KernelLayout.WORD_MASK) + (faceIndex2 and KernelLayout.WORD_MASK)) and
            KernelLayout.WORD_MASK)
    updateSubtractionFlags((faceBits3 and KernelLayout.WORD_MASK), 1L, 32L)
    return if (negativeResult) KernelStep.SampleFrontRange else KernelStep.InspectFirstFrontEdge
}

// Reference step 186: L185345d8c
internal fun CurlMeshKernel.inspectFirstFrontEdge(): KernelStep {
    faceValue36 = (shadowValue9 - faceValue6)
    faceValue37 = (shadowValue13 - faceValue5)
    faceValue38 = (faceValue * faceValue37)
    faceValue39 = readDouble((scratchBase + 536L))
    faceValue40 = fma(faceValue39, faceValue36, faceValue38)
    compareFloatingPoint(faceValue40, faceValue19)
    if ((!(zeroResult) && (negativeResult == arithmeticOverflow))) {
        run {
            compareFloatingPoint(faceValue40, faceValue20)
        }
    } else {
        run {
            negativeResult = (0L != 0L)
            zeroResult = (0L != 0L)
            unsignedCarry = (0L != 0L)
            arithmeticOverflow = (0L != 0L)
        }
    }
    return if (negativeResult) KernelStep.AppendFirstFrontEdge else KernelStep.UseEmptyFrontEdges
}

// Reference step 187: fallthrough187
internal fun CurlMeshKernel.useEmptyFrontEdges(): KernelStep {
    faceIndex3 = (0L and KernelLayout.WORD_MASK)
    return KernelStep.InspectSecondFrontEdge
}

// Reference step 188: L185345db4
internal fun CurlMeshKernel.appendFirstFrontEdge(): KernelStep {
    writeDouble((scratchBase + 2432L), faceValue40)
    faceIndex3 = ((0L or 1L) and KernelLayout.WORD_MASK)
    return KernelStep.InspectSecondFrontEdge
}

// Reference step 189: L185345dbc
internal fun CurlMeshKernel.inspectSecondFrontEdge(): KernelStep {
    faceValue41 = (shadowValue10 - faceValue6)
    faceValue42 = (shadowValue14 - faceValue5)
    faceValue43 = (faceValue * faceValue42)
    faceValue44 = readDouble((scratchBase + 536L))
    faceValue45 = fma(faceValue44, faceValue41, faceValue43)
    compareFloatingPoint(faceValue45, faceValue19)
    if ((!(zeroResult) && (negativeResult == arithmeticOverflow))) {
        run {
            compareFloatingPoint(faceValue45, faceValue20)
        }
    } else {
        run {
            negativeResult = (0L != 0L)
            zeroResult = (0L != 0L)
            unsignedCarry = (0L != 0L)
            arithmeticOverflow = (0L != 0L)
        }
    }
    writeDouble((scratchBase + 416L), faceValue6)
    writeDouble(((scratchBase + 416L) + 8L), faceValue5)
    return if (negativeResult) KernelStep.AppendSecondFrontEdge
    else KernelStep.SelectFrontMergeOrientation
}

// Reference step 190: fallthrough190
internal fun CurlMeshKernel.selectFrontMergeOrientation(): KernelStep {
    updateSubtractionFlags((faceIndex2 and KernelLayout.WORD_MASK), 0L, 32L)
    faceIndex4 =
        ((if ((!(zeroResult) && (negativeResult == arithmeticOverflow))) 1L else 0L) and
            KernelLayout.WORD_MASK)
    return if ((!(zeroResult) && (negativeResult == arithmeticOverflow)))
        KernelStep.PrepareFrontEdgeMerge
    else KernelStep.InspectFrontMergeAvailability
}

// Reference step 191: fallthrough191
internal fun CurlMeshKernel.inspectFrontMergeAvailability(): KernelStep {
    return if (((faceIndex3 and KernelLayout.WORD_MASK) != 0L)) KernelStep.PrepareFrontEdgeMerge
    else KernelStep.SkipFrontEdgeMerge
}

// Reference step 192: fallthrough192
internal fun CurlMeshKernel.skipFrontEdgeMerge(): KernelStep {
    faceIndex5 = (0L and KernelLayout.WORD_MASK)
    return KernelStep.PrepareFrontCrossSections
}

// Reference step 193: L185345df8
internal fun CurlMeshKernel.appendSecondFrontEdge(): KernelStep {
    faceAddress2 = (scratchBase + 2432L)
    writeDouble(
        (faceAddress2 +
            (((faceIndex3 and KernelLayout.WORD_MASK) and KernelLayout.WORD_MASK) shl 3)),
        faceValue45,
    )
    return if (((faceIndex3 and KernelLayout.WORD_MASK) == 0L)) KernelStep.SelectOneFrontEdge
    else KernelStep.CompareFrontEdges
}

// Reference step 194: fallthrough194
internal fun CurlMeshKernel.compareFrontEdges(): KernelStep {
    faceValue46 = readDouble((scratchBase + 2440L))
    faceValue47 = readDouble((scratchBase + 2432L))
    compareFloatingPoint(faceValue46, faceValue47)
    return if ((zeroResult || (negativeResult != arithmeticOverflow)))
        KernelStep.SelectTwoFrontEdges
    else KernelStep.SwapFrontEdges
}

// Reference step 195: fallthrough195
internal fun CurlMeshKernel.swapFrontEdges(): KernelStep {
    writeDouble((scratchBase + 2440L), faceValue47)
    writeDouble((scratchBase + 2432L), faceValue45)
    return KernelStep.SelectTwoFrontEdges
}

// Reference step 196: L185345e1c
internal fun CurlMeshKernel.selectTwoFrontEdges(): KernelStep {
    faceIndex3 = ((0L or 2L) and KernelLayout.WORD_MASK)
    return KernelStep.FinishFrontEdgeSelection
}

// Reference step 197: L185345e24
internal fun CurlMeshKernel.selectOneFrontEdge(): KernelStep {
    faceIndex3 = ((0L or 1L) and KernelLayout.WORD_MASK)
    return KernelStep.FinishFrontEdgeSelection
}

// Reference step 198: L185345e28
internal fun CurlMeshKernel.finishFrontEdgeSelection(): KernelStep {
    updateSubtractionFlags((faceIndex2 and KernelLayout.WORD_MASK), 0L, 32L)
    faceIndex4 =
        ((if ((!(zeroResult) && (negativeResult == arithmeticOverflow))) 1L else 0L) and
            KernelLayout.WORD_MASK)
    return KernelStep.PrepareFrontEdgeMerge
}

// Reference step 199: L185345e30
internal fun CurlMeshKernel.prepareFrontEdgeMerge(): KernelStep {
    faceIndex5 = 0L
    faceIndex6 = (0L and KernelLayout.WORD_MASK)
    faceIndex7 = (0L and KernelLayout.WORD_MASK)
    faceAddress3 = (scratchBase + 1504L)
    faceAddress4 = (scratchBase + 680L)
    faceAddress5 = (scratchBase + 2432L)
    return KernelStep.InspectFrontMergeSource
}

// Reference step 200: L185345e48
internal fun CurlMeshKernel.inspectFrontMergeSource(): KernelStep {
    updateSubtractionFlags(
        (faceIndex6 and KernelLayout.WORD_MASK),
        (faceIndex3 and KernelLayout.WORD_MASK),
        32L,
    )
    return if (!(zeroResult)) KernelStep.InspectFrontMergeOrder else KernelStep.TakeFrontEdge
}

// Reference step 201: fallthrough201
internal fun CurlMeshKernel.takeFrontEdge(): KernelStep {
    faceAddress6 =
        readLongBits(
            (faceAddress3 + (((faceIndex7 and KernelLayout.WORD_MASK)).toInt().toLong() shl 3))
        )
    return KernelStep.AdvanceFrontEdge
}

// Reference step 202: L185345e54
internal fun CurlMeshKernel.advanceFrontEdge(): KernelStep {
    faceIndex7 = (((faceIndex7 and KernelLayout.WORD_MASK) + 1L) and KernelLayout.WORD_MASK)
    return KernelStep.AppendFrontMergedValue
}

// Reference step 203: L185345e5c
internal fun CurlMeshKernel.inspectFrontMergeOrder(): KernelStep {
    return if ((((faceIndex4 and KernelLayout.WORD_MASK) and (1L shl 0)) == 0L))
        KernelStep.TakeFrontBoundary
    else KernelStep.CompareFrontMergeValues
}

// Reference step 204: fallthrough204
internal fun CurlMeshKernel.compareFrontMergeValues(): KernelStep {
    faceValue48.double(
        0,
        readDouble(
            (faceAddress3 + (((faceIndex7 and KernelLayout.WORD_MASK)).toInt().toLong() shl 3))
        ),
    )
    faceValue49.double(
        0,
        readDouble(
            (faceAddress5 + (((faceIndex6 and KernelLayout.WORD_MASK)).toInt().toLong() shl 3))
        ),
    )
    compareFloatingPoint(faceValue48.double(0), faceValue49.double(0))
    return if ((zeroResult || (negativeResult != arithmeticOverflow)))
        KernelStep.RetainFrontBoundary
    else KernelStep.RetainFrontMergeValue
}

// Reference step 205: fallthrough205
internal fun CurlMeshKernel.retainFrontMergeValue(): KernelStep {
    faceAddress6 = faceValue48.longBits(0)
    return KernelStep.AdvanceFrontEdge
}

// Reference step 206: L185345e78
internal fun CurlMeshKernel.takeFrontBoundary(): KernelStep {
    faceAddress6 =
        readLongBits(
            (faceAddress5 + (((faceIndex6 and KernelLayout.WORD_MASK)).toInt().toLong() shl 3))
        )
    return KernelStep.AdvanceFrontBoundary
}

// Reference step 207: L185345e80
internal fun CurlMeshKernel.retainFrontBoundary(): KernelStep {
    faceAddress6 = faceValue49.longBits(0)
    return KernelStep.AdvanceFrontBoundary
}

// Reference step 208: L185345e84
internal fun CurlMeshKernel.advanceFrontBoundary(): KernelStep {
    faceIndex6 = (((faceIndex6 and KernelLayout.WORD_MASK) + 1L) and KernelLayout.WORD_MASK)
    return KernelStep.AppendFrontMergedValue
}

// Reference step 209: L185345e88
internal fun CurlMeshKernel.appendFrontMergedValue(): KernelStep {
    writeLongBits((faceAddress4 + (faceIndex5 shl 3)), faceAddress6)
    faceIndex5 = (faceIndex5 + 1L)
    updateSubtractionFlags(
        (faceIndex7 and KernelLayout.WORD_MASK),
        (faceIndex2 and KernelLayout.WORD_MASK),
        32L,
    )
    faceIndex4 =
        ((if ((negativeResult != arithmeticOverflow)) 1L else 0L) and KernelLayout.WORD_MASK)
    return if ((negativeResult != arithmeticOverflow)) KernelStep.InspectFrontMergeSource
    else KernelStep.DrainFrontMerge
}

// Reference step 210: fallthrough210
internal fun CurlMeshKernel.drainFrontMerge(): KernelStep {
    updateSubtractionFlags(
        (faceIndex6 and KernelLayout.WORD_MASK),
        (faceIndex3 and KernelLayout.WORD_MASK),
        32L,
    )
    return if ((negativeResult != arithmeticOverflow)) KernelStep.InspectFrontMergeSource
    else KernelStep.PrepareFrontCrossSections
}

// Reference step 211: L185345ea4
internal fun CurlMeshKernel.prepareFrontCrossSections(): KernelStep {
    faceValue50.set(shadowValue23)
    faceValue51.set(shadowValue23)
    faceValue52.set(surfaceValue101)
    faceValue53.set(faceValue2)
    faceValue54.set(surfaceValue100)
    sineOutputAddress3 = 0L
    faceValue55 =
        curlDisplacement(
            faceValue50.double(0),
            faceValue51.double(0),
            faceValue52.double(0),
            sineOutputAddress3,
            faceValue53.double(0),
            faceValue54.double(0),
        )
    faceValue56 = (faceValue55).toDouble()
    faceValue57.longBits(0, 0L)
    faceValue58 = (faceValue57.double(0) / surfaceValue101.double(0))
    faceValue59 = (surfaceValue102 - faceValue58)
    faceValue60 = (faceValue59).toFloat()
    faceValue61 = (faceValue60 * faceValue60)
    faceValue62 = -(0.03922769f)
    faceValue63 = 0.000969668f
    writeFloat((scratchBase + 400L), faceValue62)
    writeFloat((scratchBase + 392L), faceValue63)
    faceValue64 = fma(faceValue61, faceValue63, faceValue62)
    faceValue65 = 0.4953497f
    writeFloat((scratchBase + 368L), faceValue65)
    faceValue66 = fma(faceValue61, faceValue64, faceValue65)
    faceValue67 = -(0.99860674f)
    writeFloat((scratchBase + 352L), faceValue67)
    faceValue68 = fma(faceValue61, faceValue66, faceValue67)
    faceValue69 = (faceValue68).toDouble()
    faceValue70 = (faceValue69 + shadowValue35)
    faceValue71 = (surfaceValue101.double(0) * faceValue70)
    faceValue72 = (shadowValue23.double(0) + faceValue56)
    faceValue73 = 0.4f.toDouble()
    writeDouble((scratchBase + 336L), faceValue4)
    writeDouble(((scratchBase + 336L) + 8L), faceValue73)
    faceValue74 = fma(faceValue71, faceValue73, -(faceValue72))
    faceValue75 = (faceValue4 + faceValue74)
    faceValue76 = (faceValue75 / surfaceValue101.double(0))
    faceValue77 = -(0.95f.toDouble())
    writeDouble((scratchBase + 328L), faceValue77)
    faceValue78 = (faceValue76 + faceValue77)
    faceValue79 = max(faceValue78, faceValue57.double(0))
    faceValue80 = 3.0
    faceValue81 = (faceValue79 * faceValue80)
    faceValue82 = (faceValue81 * faceValue81)
    faceValue83 = (faceValue82).toFloat()
    faceValue84 = -(faceValue83)
    faceValue85 = AppleMath.exp(faceValue84)
    updateSubtractionFlags((faceIndex5 and KernelLayout.WORD_MASK), 1L, 32L)
    faceValue86 = readDouble((scratchBase + 536L))
    faceValue87 = readDouble((scratchBase + 528L))
    faceValue88 = readDouble((scratchBase + 416L))
    faceValue89 = readDouble(((scratchBase + 416L) + 8L))
    return if ((negativeResult != arithmeticOverflow)) KernelStep.FinishFrontCrossSections
    else KernelStep.BeginFrontCrossSections
}

// Reference step 212: fallthrough212
internal fun CurlMeshKernel.beginFrontCrossSections(): KernelStep {
    faceValue90.set(shadowValue23)
    faceValue91.set(faceValue2)
    faceIndex8 = 0L
    faceValue92 = (faceValue85).toDouble()
    faceValue93 = (faceValue92 * faceValue92)
    faceValue94 = fma(faceValue93, faceValue93, faceValue92)
    writeDouble((scratchBase + 320L), faceValue94)
    faceBits4 = ((faceIndex5 and KernelLayout.WORD_MASK) and KernelLayout.WORD_MASK)
    faceAddress7 = (scratchBase + 680L)
    faceValue95 = 76.5
    writeDouble((scratchBase + 312L), faceValue95)
    faceValue96 = KernelTuning.NORMALIZED_CHANNEL_SCALE_DOUBLE
    writeDouble((scratchBase + 304L), faceValue96)
    faceValue97 = (shadowValue35 / surfaceValue101.double(0))
    writeDouble((scratchBase + 296L), faceValue97)
    return KernelStep.AppendFrontCrossSection
}

// Reference step 213: L185345fc0
internal fun CurlMeshKernel.appendFrontCrossSection(): KernelStep {
    faceValue98.double(0, readDouble((faceAddress7 + (faceIndex8 shl 3))))
    faceValue99.double(0, fma(faceValue86, faceValue98.double(0), faceValue88))
    faceValue100.double(0, fma(faceValue87, faceValue98.double(0), faceValue89))
    faceValue101.set(faceValue98)
    faceValue102.set(surfaceValue101)
    faceValue103.set(faceValue91)
    faceValue104.set(surfaceValue100)
    sineOutputAddress4 = 0L
    faceValue105 =
        curlDisplacement(
            faceValue101.double(0),
            faceValue90.double(0),
            faceValue102.double(0),
            sineOutputAddress4,
            faceValue103.double(0),
            faceValue104.double(0),
        )
    faceValue106 = (faceValue105).toDouble()
    clipStartAddress4 = (scratchBase + 2368L)
    clipEndAddress4 = (scratchBase + 592L)
    faceValue107 = readDouble((scratchBase + 456L))
    faceValue108 = readDouble(((scratchBase + 456L) + 8L))
    faceValue109 = readDouble((scratchBase + 432L))
    faceValue110 = readDouble(((scratchBase + 432L) + 8L))
    faceValue111.set(faceValue99)
    faceValue112.set(faceValue100)
    faceValue113 = readDouble((scratchBase + 224L))
    faceValue114 = readDouble(((scratchBase + 224L) + 8L))
    clipProjectedLine(
        clipStartAddress4,
        clipEndAddress4,
        faceValue108,
        faceValue110,
        faceValue107,
        faceValue109,
        faceValue111.double(0),
        faceValue112.double(0),
        faceValue114,
        faceValue113,
    )
    faceValue115 = readDouble((scratchBase + 520L))
    faceValue116 = (faceValue115 - faceValue98.double(0))
    faceValue117 = readDouble((scratchBase + 296L))
    faceValue118 = fma(faceValue116, faceValue117, surfaceValue102)
    faceValue119 = (faceValue118).toFloat()
    faceValue120 = (faceValue119 * faceValue119)
    faceValue121 = readFloat((scratchBase + 400L))
    faceValue122 = readFloat((scratchBase + 392L))
    faceValue123 = fma(faceValue120, faceValue122, faceValue121)
    faceValue124 = readFloat((scratchBase + 368L))
    faceValue125 = fma(faceValue120, faceValue123, faceValue124)
    faceValue126 = readFloat((scratchBase + 352L))
    faceValue127 = fma(faceValue120, faceValue125, faceValue126)
    faceValue128 = (faceValue127).toDouble()
    faceValue129 = (faceValue128 + shadowValue35)
    faceValue130 = (surfaceValue101.double(0) * faceValue129)
    faceValue131 = readDouble((scratchBase + 344L))
    faceValue132 = (faceValue130 * faceValue131)
    faceValue133 = (faceValue106 - faceValue132)
    faceValue134 = readDouble((scratchBase + 536L))
    faceValue135 = fma(faceValue134, faceValue133, faceValue99.double(0))
    writeDouble((scratchBase + 472L), faceValue135)
    faceValue136 = readDouble((scratchBase + 528L))
    faceValue137.double(0, fma(faceValue136, faceValue133, faceValue100.double(0)))
    faceValue138 = readDouble((scratchBase + 2368L))
    faceValue139 = (faceValue138 - faceValue132)
    faceValue140 = readDouble((scratchBase + 592L))
    faceValue141 = (faceValue140 + faceValue132)
    faceValue142 = (faceValue98.double(0) + faceValue106)
    faceValue143 = (faceValue132 - faceValue142)
    faceValue144 = readDouble((scratchBase + 336L))
    faceValue145 = (faceValue144 + faceValue143)
    faceValue146 = readDouble((scratchBase + 328L))
    faceValue147 = fma(faceValue145, faceValue117, faceValue146)
    faceValue148.longBits(0, 0L)
    faceValue149 = max(faceValue147, faceValue148.double(0))
    faceValue150 = 3.0
    faceValue151 = (faceValue149 * faceValue150)
    faceValue152 = (faceValue151 * faceValue151)
    faceValue153 = (faceValue152).toFloat()
    faceValue154 = -(faceValue153)
    faceValue155 = AppleMath.exp(faceValue154)
    faceValue156 = (faceValue155).toDouble()
    faceValue157 = (faceValue156 * faceValue156)
    faceValue158 = fma(faceValue157, faceValue157, faceValue156)
    faceValue159 = readDouble((scratchBase + 320L))
    faceValue160 = (faceValue158 - faceValue159)
    faceValue161 = readDouble(shadowAddress)
    faceValue162 = 15.0
    faceValue163 = (faceValue161 * faceValue162)
    faceValue164 = 1.0
    faceValue165 = min(faceValue163, faceValue164)
    faceValue166 = readDouble((scratchBase + 312L))
    faceValue167 = (faceValue166 * faceValue165)
    faceValue168 = (faceValue160 * faceValue167)
    faceValue169 = (faceValue140 - faceValue138)
    faceValue170.double(0, 0.5)
    faceValue171.set(faceValue170)
    faceValue172 = (faceValue169 * faceValue170.double(0))
    compareFloatingPoint(faceValue172, faceValue132)
    faceValue173 = 0.25
    faceValue174 = (faceValue169 * faceValue173)
    faceValue175 = (faceValue174 / faceValue132)
    faceValue176 = (faceValue175 + faceValue171.double(0))
    faceValue177 = (faceValue176 * faceValue168)
    faceValue178 = (if (!(negativeResult)) faceValue168 else faceValue177)
    compareFloatingPoint(faceValue132, faceValue172)
    faceValue179 = (if (negativeResult) faceValue132 else faceValue172)
    shadowValue35 = 1.0
    faceValue180 = (faceValue138 + faceValue179)
    surfaceValue101.double(0, readDouble((scratchBase + 512L)))
    faceValue181 = (faceValue140 - faceValue179)
    faceValue91.double(0, readDouble((scratchBase + 496L)))
    surfaceValue100.double(0, readDouble(((scratchBase + 496L) + 8L)))
    faceValue182 = readDouble((scratchBase + 304L))
    faceValue183 = (faceValue178 * faceValue182)
    faceValue184.float(0, (faceValue183).toFloat())
    faceValue185.double(0, readDouble((scratchBase + 208L)))
    faceColor.unpackHalfColorFrom(faceValue185)
    run {
        var ta = faceColor.copy()
        var tb = faceValue184.copy()
        var tc = faceValue184.copy()
        for (lane in 0 until 4) {
            faceValue186.float(lane, (ta.float(lane) * tb.float(0)))
        }
    }
    faceColor2.packHalfColorFrom(faceValue186)
    writeDouble((scratchBase + 584L), faceColor2.double(0))
    faceValue187 = (faceValue139).toFloat()
    surfaceValue102 = readDouble((scratchBase + 448L))
    faceValue188 = (faceValue141).toFloat()
    faceValue189 = (faceValue180).toFloat()
    faceValue190 = (faceValue181).toFloat()
    writeFloat((scratchBase + 8L), faceValue189)
    writeFloat(((scratchBase + 8L) + 4L), faceValue190)
    faceValue191.longBits(0, 0L)
    faceValue192.longBits(0, 0L)
    faceValue193.longBits(0, 0L)
    faceAddress8 = (scratchBase + (7L shl 12))
    centerStripAddress2 = (faceAddress8 + 896L)
    faceValue194.longBits(0, 0L)
    lowerStripAddress2 = (scratchBase + (4L shl 12))
    upperStripAddress2 = (scratchBase + 3200L)
    writeFloat(scratchBase, faceValue187)
    writeFloat((scratchBase + 4L), faceValue188)
    packedColorAddress2 = (scratchBase + 584L)
    faceValue195 = readDouble((scratchBase + 472L))
    faceValue196 = readDouble(((scratchBase + 472L) + 8L))
    faceValue197.set(faceValue137)
    faceValue198 = readDouble((scratchBase + 488L))
    stripIndex2 = faceIndex8
    appendSurfaceStrip(
        centerStripAddress2,
        lowerStripAddress2,
        upperStripAddress2,
        stripIndex2,
        packedColorAddress2,
        faceValue195,
        faceValue197.double(0),
        faceValue191.double(0),
        faceValue192.double(0),
        faceValue198,
        faceValue196,
        faceValue193.double(0),
        faceValue194.double(0),
        scratchBase,
    )
    faceValue88 = readDouble((scratchBase + 416L))
    faceValue89 = readDouble(((scratchBase + 416L) + 8L))
    faceValue90.double(0, readDouble((scratchBase + 520L)))
    faceValue87 = readDouble((scratchBase + 528L))
    faceValue86 = readDouble((scratchBase + 536L))
    faceIndex8 = (faceIndex8 + 1L)
    updateSubtractionFlags(faceBits4, faceIndex8, 64L)
    return if (!(zeroResult)) KernelStep.AppendFrontCrossSection
    else KernelStep.FinishFrontCrossSections
}

// Reference step 214: L1853461bc
internal fun CurlMeshKernel.finishFrontCrossSections(): KernelStep {
    faceIndex9 = (((faceIndex5 and KernelLayout.WORD_MASK) shl 1) and KernelLayout.WORD_MASK)
    writeWord((scratchBase + 528L), (faceIndex9 and KernelLayout.WORD_MASK))
    surfaceAddress21 = readLongBits((scratchBase + 184L))
    return KernelStep.PrepareDrawState
}
