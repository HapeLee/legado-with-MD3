// Generated curl mesh algorithm stages.
@file:Suppress("UNUSED_VARIABLE", "UNUSED_VALUE", "NAME_SHADOWING", "RedundantConversion")

package io.legado.app.feature.reader.core.transition.ioscurl.mesh.kernel

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt
import io.legado.app.feature.reader.core.transition.ioscurl.math.AppleMath
import io.legado.app.feature.reader.core.transition.ioscurl.math.BinaryMath.fma

// Reference step 125: fallthrough125
internal fun CurlMeshKernel.prepareShadowCrossSections(): KernelStep {
    penumbraBits = shadowBits6
    penumbraIndex = 0L
    penumbraValue = 0.3f.toDouble()
    penumbraValue2 = 30.0
    penumbraValue3 = fma(surfaceValue101.double(0), penumbraValue, penumbraValue2)
    penumbraValue4 = 50.0
    penumbraValue5 = (surfaceValue101.double(0) + penumbraValue4)
    penumbraValue6 = (penumbraValue3 / penumbraValue5)
    penumbraValue7 = (surfaceValue101.double(0) * penumbraValue6)
    compareFloatingPoint(shadowValue65, shadowValue66)
    penumbraIndex2 = ((if (negativeResult) 1L else 0L) and KernelLayout.WORD_MASK)
    penumbraBits2 = CLAMP_STEPS2
    penumbraValue8 =
        readDouble(
            (penumbraBits2 +
                (((penumbraIndex2 and KernelLayout.WORD_MASK) and KernelLayout.WORD_MASK) shl 3))
        )
    penumbraValue9 = 243.00000429153442
    penumbraValue10 = (shadowValue61 * penumbraValue9)
    updateSubtractionFlags((shadowBits8 and KernelLayout.WORD_MASK), 0L, 32L)
    penumbraValue11 = (if (!(zeroResult)) penumbraValue8 else penumbraValue10)
    penumbraValue12 = 1.0
    penumbraValue13 = (penumbraValue12 / surfaceValue101.double(0))
    writeDouble((scratchBase + 424L), penumbraValue13)
    penumbraValue14 = readDouble((scratchBase + 568L))
    penumbraValue15 = (penumbraValue11 - penumbraValue14)
    writeDouble((scratchBase + 416L), penumbraValue15)
    writeDouble((scratchBase + 472L), penumbraValue7)
    penumbraValue16 = (penumbraValue12 / penumbraValue7)
    writeDouble((scratchBase + 400L), penumbraValue16)
    penumbraBits3 = ((shadowIndex13 and KernelLayout.WORD_MASK) and KernelLayout.WORD_MASK)
    penumbraAddress = (scratchBase + (4L shl 12))
    penumbraAddress2 = (scratchBase + (7L shl 12))
    penumbraAddress3 = (penumbraAddress2 + 896L)
    penumbraValue17 = KernelTuning.NORMALIZED_CHANNEL_SCALE_DOUBLE
    writeDouble((scratchBase + 392L), penumbraValue17)
    penumbraColor.unpackHalfColorFrom(surfaceValue99)
    penumbraColor.store(memory, (scratchBase + 368L))
    penumbraAddress4 = (scratchBase + (28L shl 12))
    penumbraAddress5 = (penumbraAddress4 + 272L)
    penumbraValue18 = KernelLayout.POSITION_Z_ZERO_W_ONE
    penumbraValue19.longBits(1, 0L)
    penumbraValue19.store(memory, (scratchBase + 352L))
    penumbraAddress6 = (scratchBase + (21L shl 12))
    penumbraAddress7 = (penumbraAddress6 + 3600L)
    penumbraBits4 = penumbraBits3
    writeDouble((scratchBase + 544L), shadowValue15)
    penumbraValue20 = readDouble((scratchBase + 528L))
    penumbraValue21.double(0, readDouble((scratchBase + 520L)))
    return KernelStep.SampleShadowCrossSection
}

// Reference step 126: L185345244
internal fun CurlMeshKernel.sampleShadowCrossSection(): KernelStep {
    penumbraValue22.double(0, readDouble(penumbraAddress))
    penumbraAddress = (penumbraAddress + 8L)
    penumbraValue23.set(surfaceValue100)
    penumbraValue24 = readDouble((scratchBase + 536L))
    penumbraValue25.double(
        0,
        fma(penumbraValue24, penumbraValue22.double(0), shadowValue16.double(0)),
    )
    penumbraValue26.double(0, fma(penumbraValue20, penumbraValue22.double(0), shadowValue15))
    penumbraValue27.set(penumbraValue22)
    penumbraValue28.set(penumbraValue21)
    penumbraValue29.set(surfaceValue101)
    penumbraValue30 = readDouble((scratchBase + 496L))
    sineOutputAddress2 = 0L
    penumbraValue31.set(shadowValue16)
    penumbraValue32 =
        curlDisplacement(
            penumbraValue27.double(0),
            penumbraValue28.double(0),
            penumbraValue29.double(0),
            sineOutputAddress2,
            penumbraValue30,
            penumbraValue23.double(0),
        )
    penumbraValue33 = (penumbraValue32).toDouble()
    penumbraValue34 = fma(penumbraValue24, penumbraValue33, penumbraValue25.double(0))
    penumbraValue35 = fma(penumbraValue20, penumbraValue33, penumbraValue26.double(0))
    clipStartAddress3 = (scratchBase + 648L)
    clipEndAddress3 = (scratchBase + 640L)
    penumbraValue36 = readDouble((scratchBase + 456L))
    penumbraValue37 = readDouble(((scratchBase + 456L) + 8L))
    penumbraValue38 = readDouble((scratchBase + 432L))
    penumbraValue39 = readDouble(((scratchBase + 432L) + 8L))
    penumbraValue40.set(penumbraValue25)
    penumbraValue41 = readDouble((scratchBase + 480L))
    penumbraValue42.set(penumbraValue26)
    penumbraValue43 = readDouble((scratchBase + 224L))
    penumbraValue44 = readDouble(((scratchBase + 224L) + 8L))
    clipProjectedLine(
        clipStartAddress3,
        clipEndAddress3,
        penumbraValue37,
        penumbraValue39,
        penumbraValue36,
        penumbraValue38,
        penumbraValue40.double(0),
        penumbraValue42.double(0),
        penumbraValue44,
        penumbraValue43,
    )
    shadowValue16.set(penumbraValue31)
    shadowValue15 = readDouble((scratchBase + 544L))
    penumbraValue45 = (penumbraValue21.double(0) - penumbraValue22.double(0))
    penumbraValue46 = readDouble((scratchBase + 424L))
    penumbraValue47 = (penumbraValue45 * penumbraValue46)
    penumbraValue48 = readDouble((scratchBase + 448L))
    penumbraValue49 = min(penumbraValue47, penumbraValue48)
    penumbraValue50 = (penumbraValue49).toFloat()
    penumbraValue51 = (penumbraValue50 * penumbraValue50)
    penumbraValue52 = readFloat((scratchBase + 152L))
    penumbraValue53 = readFloat(((scratchBase + 152L) + 4L))
    penumbraValue54 = fma(penumbraValue51, penumbraValue52, penumbraValue53)
    penumbraValue55 = readFloat((scratchBase + 148L))
    penumbraValue56 = fma(penumbraValue51, penumbraValue54, penumbraValue55)
    penumbraValue57 = (penumbraValue56 * penumbraValue50)
    penumbraValue58 = (penumbraValue57).toDouble()
    penumbraValue59 = readDouble((scratchBase + 472L))
    penumbraValue60 = (penumbraValue59 * penumbraValue58)
    writeDouble(penumbraAddress3, penumbraValue60)
    penumbraAddress3 = (penumbraAddress3 + 8L)
    penumbraValue61 = 1.0
    penumbraValue62 = readDouble((scratchBase + 400L))
    penumbraValue63 = fma(-(penumbraValue60), penumbraValue62, penumbraValue61)
    penumbraValue64 = readDouble((scratchBase + 568L))
    penumbraValue65 = readDouble((scratchBase + 416L))
    penumbraValue66 = fma(penumbraValue65, penumbraValue63, penumbraValue64)
    penumbraValue67 = readDouble((scratchBase + 392L))
    penumbraValue68 = (penumbraValue66 * penumbraValue67)
    penumbraValue69.float(0, (penumbraValue68).toFloat())
    penumbraValue70 = readDouble((scratchBase + 648L))
    penumbraValue71 = readDouble((scratchBase + 488L))
    penumbraValue72 = fma(penumbraValue71, penumbraValue70, penumbraValue34)
    penumbraValue73 = fma(penumbraValue41, penumbraValue70, penumbraValue35)
    penumbraValue74 = readDouble((scratchBase + 640L))
    penumbraValue75 = fma(penumbraValue71, penumbraValue74, penumbraValue34)
    penumbraValue76.load(memory, (scratchBase + 368L))
    run {
        var ta = penumbraValue76.copy()
        var tb = penumbraValue69.copy()
        var tc = penumbraValue69.copy()
        for (lane in 0 until 4) {
            penumbraValue77.float(lane, (ta.float(lane) * tb.float(0)))
        }
    }
    penumbraValue78 = fma(penumbraValue41, penumbraValue74, penumbraValue35)
    surfaceValue100.double(0, readDouble((scratchBase + 504L)))
    surfaceValue101.double(0, readDouble(((scratchBase + 504L) + 8L)))
    penumbraValue79 = (penumbraValue72).toFloat()
    penumbraBits5 = (penumbraAddress5 + penumbraIndex)
    penumbraValue80 = (penumbraValue73).toFloat()
    writeFloat((penumbraBits5 + 64L), penumbraValue79)
    writeFloat(((penumbraBits5 + 64L) + 4L), penumbraValue80)
    penumbraColor2.packHalfColorFrom(penumbraValue77)
    penumbraValue81.load(memory, (scratchBase + 352L))
    penumbraValue81.store(memory, (penumbraBits5 + 72L))
    penumbraValue82 = (penumbraValue75).toFloat()
    penumbraBits6 = (penumbraAddress7 + penumbraIndex)
    penumbraValue83 = (penumbraValue78).toFloat()
    writeDouble((penumbraBits5 + 104L), penumbraColor2.double(0))
    writeFloat(penumbraBits6, penumbraValue82)
    writeFloat((penumbraBits6 + 4L), penumbraValue83)
    penumbraValue81.store(memory, (penumbraBits6 + 8L))
    writeDouble((penumbraBits6 + 40L), penumbraColor2.double(0))
    penumbraIndex = (penumbraIndex + 128L)
    updateSubtractionFlags(penumbraBits4, 1L, 64L)
    penumbraBits4 = (penumbraBits4 - 1L)
    return if (!(zeroResult)) KernelStep.SampleShadowCrossSection
    else KernelStep.PreparePenumbraStrips
}

// Reference step 127: fallthrough127
internal fun CurlMeshKernel.preparePenumbraStrips(): KernelStep {
    penumbraIndex3 = 0L
    penumbraAddress8 = readLongBits((scratchBase + 288L))
    penumbraIndex4 =
        (((penumbraAddress8 and KernelLayout.WORD_MASK) - 1L) and KernelLayout.WORD_MASK)
    penumbraAddress9 = (scratchBase + (10L shl 12))
    penumbraAddress10 = (penumbraAddress9 + 1792L)
    writeWord(penumbraAddress10, 0L)
    shadowBits6 = penumbraBits
    penumbraBits7 = ((penumbraBits and KernelLayout.WORD_MASK)).toInt().toLong()
    penumbraBits8 = ((penumbraIndex4 and KernelLayout.WORD_MASK)).toInt().toLong()
    penumbraValue84.longBits(0, 0L)
    penumbraIndex5 = ((0L or 1L) and KernelLayout.WORD_MASK)
    writeLongBits((scratchBase + 568L), penumbraIndex5)
    penumbraAddress11 = (scratchBase + (28L shl 12))
    penumbraAddress12 = (penumbraAddress11 + 272L)
    penumbraAddress13 = (scratchBase + (21L shl 12))
    penumbraAddress14 = (penumbraAddress13 + 3600L)
    penumbraAddress15 = (scratchBase + (10L shl 12))
    penumbraAddress16 = (penumbraAddress15 + 1792L)
    penumbraAddress17 = (scratchBase + 2512L)
    penumbraAddress18 = (scratchBase + 2496L)
    penumbraIndex6 = ((0L or 3L) and KernelLayout.WORD_MASK)
    penumbraBits9 = (5L and KernelLayout.WORD_MASK)
    penumbraIndex7 = ((0L or 2L) and KernelLayout.WORD_MASK)
    penumbraIndex8 = ((0L or 4L) and KernelLayout.WORD_MASK)
    penumbraAddress19 = (scratchBase + (7L shl 12))
    penumbraAddress20 = (penumbraAddress19 + 896L)
    penumbraValue85 = KernelTuning.SHADOW_PLANE_Z
    penumbraBits10 = (8448L and KernelLayout.WORD_MASK)
    penumbraAddress21 = (scratchBase + (21L shl 12))
    penumbraAddress22 = (penumbraAddress21 + 3600L)
    penumbraAddress23 = (scratchBase + (28L shl 12))
    penumbraAddress24 = (penumbraAddress23 + 272L)
    penumbraValue86.longBits(0, 0L)
    penumbraBits11 = FADE_WEIGHTS
    penumbraValue87.longBits(0, 0L)
    penumbraValue88.longBits(0, 0L)
    penumbraBits12 = FADE_STEPS
    penumbraValue89 = readDouble((scratchBase + 536L))
    penumbraValue90 = readDouble((scratchBase + 528L))
    shadowValue35 = 1.0
    return KernelStep.BeginPenumbraSegment
}

// Reference step 128: L18534542c
internal fun CurlMeshKernel.beginPenumbraSegment(): KernelStep {
    penumbraIndex9 = 0L
    penumbraAddress25 = readLongBits((scratchBase + 568L))
    penumbraBits13 = ((penumbraAddress25 and KernelLayout.WORD_MASK)).toInt().toLong()
    penumbraBits14 =
        (penumbraAddress16 +
            (((penumbraAddress25 and KernelLayout.WORD_MASK)).toInt().toLong() shl 2))
    penumbraBits15 = (readUnsignedWord((penumbraBits14 + -(4L))) and KernelLayout.WORD_MASK)
    penumbraAddress26 =
        (((penumbraBits15 and KernelLayout.WORD_MASK) + 1L) and KernelLayout.WORD_MASK)
    writeWord((penumbraBits14 + -(4L)), (penumbraAddress26 and KernelLayout.WORD_MASK))
    return KernelStep.FindPenumbraSegment
}

// Reference step 129: L185345448
internal fun CurlMeshKernel.findPenumbraSegment(): KernelStep {
    penumbraBits16 =
        (readUnsignedWord((penumbraAddress17 + (penumbraIndex9 shl 2))) and KernelLayout.WORD_MASK)
    updateSubtractionFlags(penumbraIndex3, penumbraBits16, 64L)
    return if (zeroResult) KernelStep.MatchPenumbraSegment else KernelStep.AdvancePenumbraSearch
}

// Reference step 130: fallthrough130
internal fun CurlMeshKernel.advancePenumbraSearch(): KernelStep {
    penumbraAddress27 = (penumbraIndex9 + 1L)
    updateSubtractionFlags(penumbraIndex9, shadowIndex5, 64L)
    penumbraIndex9 = penumbraAddress27
    return if (!(unsignedCarry)) KernelStep.FindPenumbraSegment
    else KernelStep.UseUnmatchedPenumbraSegment
}

// Reference step 131: fallthrough131
internal fun CurlMeshKernel.useUnmatchedPenumbraSegment(): KernelStep {
    penumbraIndex10 = (0L and KernelLayout.WORD_MASK)
    penumbraIndex11 = ((0L or 4L) and KernelLayout.WORD_MASK)
    return KernelStep.InspectPenumbraSegmentEnd
}

// Reference step 132: L185345470
internal fun CurlMeshKernel.matchPenumbraSegment(): KernelStep {
    penumbraIndex11 =
        (readUnsignedWord((penumbraAddress18 + (penumbraIndex9 shl 2))) and KernelLayout.WORD_MASK)
    penumbraAddress28 = readLongBits((scratchBase + 568L))
    penumbraAddress29 =
        (((penumbraAddress28 and KernelLayout.WORD_MASK) + 1L) and KernelLayout.WORD_MASK)
    writeLongBits((scratchBase + 568L), penumbraAddress29)
    writeWord((penumbraAddress16 + (penumbraBits13 shl 2)), 0L)
    penumbraIndex10 = ((0L or 1L) and KernelLayout.WORD_MASK)
    return KernelStep.InspectPenumbraSegmentEnd
}

// Reference step 133: L185345488
internal fun CurlMeshKernel.inspectPenumbraSegmentEnd(): KernelStep {
    updateSubtractionFlags(penumbraIndex3, penumbraBits8, 64L)
    return if ((negativeResult == arithmeticOverflow)) KernelStep.UseTerminalPenumbraDirection
    else KernelStep.InspectPenumbraSegmentCase
}

// Reference step 134: fallthrough134
internal fun CurlMeshKernel.inspectPenumbraSegmentCase(): KernelStep {
    penumbraIndex12 =
        (((penumbraIndex10 and KernelLayout.WORD_MASK) xor 1L) and KernelLayout.WORD_MASK)
    updateSubtractionFlags((penumbraIndex11 and KernelLayout.WORD_MASK), 2L, 32L)
    return if (zeroResult) KernelStep.CalculatePenumbraDirection
    else KernelStep.SelectPenumbraSegmentCase
}

// Reference step 135: fallthrough135
internal fun CurlMeshKernel.selectPenumbraSegmentCase(): KernelStep {
    return if (((penumbraIndex12 and KernelLayout.WORD_MASK) == 0L))
        KernelStep.UseFallbackPenumbraDirection
    else KernelStep.CalculatePenumbraDirection
}

// Reference step 136: L1853454a0
internal fun CurlMeshKernel.calculatePenumbraDirection(): KernelStep {
    updateSubtractionFlags((penumbraIndex11 and KernelLayout.WORD_MASK), 2L, 32L)
    penumbraIndex13 = ((if (zeroResult) 1L else 0L) and KernelLayout.WORD_MASK)
    penumbraBits17 =
        (((penumbraIndex10 and KernelLayout.WORD_MASK) and
            (penumbraIndex13 and KernelLayout.WORD_MASK)) and KernelLayout.WORD_MASK)
    updateSubtractionFlags(penumbraIndex3, penumbraBits7, 64L)
    penumbraIndex14 =
        ((if ((negativeResult != arithmeticOverflow)) 1L else 0L) and KernelLayout.WORD_MASK)
    negativeResult = (0L != 0L)
    zeroResult =
        (((penumbraIndex14 and KernelLayout.WORD_MASK) and
            (penumbraBits17 and KernelLayout.WORD_MASK)) == 0L)
    arithmeticOverflow = (0L != 0L)
    unsignedCarry = arithmeticOverflow
    penumbraBits18 =
        ((if (!(zeroResult)) (penumbraBits9 and KernelLayout.WORD_MASK)
        else (penumbraIndex6 and KernelLayout.WORD_MASK)) and KernelLayout.WORD_MASK)
    penumbraIndex15 =
        (((penumbraBits18 and KernelLayout.WORD_MASK) +
            ((penumbraIndex3 and KernelLayout.WORD_MASK) shl 1)) and KernelLayout.WORD_MASK)
    penumbraIndex16 = ((0L or 64L) and KernelLayout.WORD_MASK)
    penumbraIndex17 =
        ((penumbraIndex16 and (-128L).inv()) or
            ((penumbraIndex3 and KernelLayout.VERTEX_PAIR_INDEX_MASK) shl 7))
    penumbraBits19 = (penumbraAddress12 + penumbraIndex17)
    penumbraBits20 = (penumbraAddress12 + (penumbraIndex15 shl 6))
    penumbraValue91 = readFloat(penumbraBits19)
    penumbraValue92 = readFloat((penumbraBits19 + 4L))
    penumbraValue93 = readFloat(penumbraBits20)
    penumbraValue94 = readFloat((penumbraBits20 + 4L))
    penumbraValue95 = (penumbraValue92).toDouble()
    penumbraValue96 = (penumbraValue91).toDouble()
    penumbraValue97 = (penumbraValue94).toDouble()
    penumbraValue98 = (penumbraValue93).toDouble()
    penumbraValue99 = (penumbraValue97 - penumbraValue95)
    penumbraValue100 = (penumbraValue96 - penumbraValue98)
    penumbraValue101 = (penumbraValue100 * penumbraValue100)
    penumbraValue102 = fma(penumbraValue99, penumbraValue99, penumbraValue101)
    penumbraValue103 = sqrt(penumbraValue102)
    penumbraValue104 = (shadowValue35 / penumbraValue103)
    penumbraValue105.double(0, (penumbraValue99 * penumbraValue104))
    compareFloatingPoint(penumbraValue105.double(0), penumbraValue105.double(0))
    return if (arithmeticOverflow) KernelStep.UseFallbackPenumbraDirection
    else KernelStep.ValidatePenumbraDirection
}

// Reference step 137: fallthrough137
internal fun CurlMeshKernel.validatePenumbraDirection(): KernelStep {
    penumbraValue106.double(0, (penumbraValue100 * penumbraValue104))
    penumbraValue107 = abs(penumbraValue105.double(0))
    penumbraValue108 = (Double.POSITIVE_INFINITY).toDouble()
    compareFloatingPoint(penumbraValue107, penumbraValue108)
    if (!(zeroResult)) {
        run {
            compareFloatingPoint(penumbraValue106.double(0), penumbraValue106.double(0))
        }
    } else {
        run {
            negativeResult = (0L != 0L)
            zeroResult = (0L != 0L)
            unsignedCarry = (0L != 0L)
            arithmeticOverflow = (1L != 0L)
        }
    }
    return if (arithmeticOverflow) KernelStep.UseFallbackPenumbraDirection
    else KernelStep.ValidatePenumbraDirectionMagnitude
}

// Reference step 138: fallthrough138
internal fun CurlMeshKernel.validatePenumbraDirectionMagnitude(): KernelStep {
    penumbraValue109 = abs(penumbraValue106.double(0))
    compareFloatingPoint(penumbraValue109, penumbraValue108)
    return if (!(zeroResult)) KernelStep.SelectPenumbraEndpoint
    else KernelStep.UseFallbackPenumbraDirection
}

// Reference step 139: L185345530
internal fun CurlMeshKernel.useFallbackPenumbraDirection(): KernelStep {
    penumbraValue105.set(penumbraValue86)
    penumbraValue106.set(penumbraValue84)
    return KernelStep.SelectPenumbraEndpoint
}

// Reference step 140: L185345538
internal fun CurlMeshKernel.selectPenumbraEndpoint(): KernelStep {
    updateSubtractionFlags((penumbraIndex11 and KernelLayout.WORD_MASK), 0L, 32L)
    penumbraIndex18 = ((if (!(zeroResult)) 1L else 0L) and KernelLayout.WORD_MASK)
    penumbraIndex19 =
        (((penumbraIndex12 and KernelLayout.WORD_MASK) xor 1L) and KernelLayout.WORD_MASK)
    penumbraBits21 =
        (((penumbraIndex18 and KernelLayout.WORD_MASK) and
            (penumbraIndex19 and KernelLayout.WORD_MASK)) and KernelLayout.WORD_MASK)
    return if ((((penumbraBits21 and KernelLayout.WORD_MASK) and (1L shl 0)) != 0L))
        KernelStep.BeginLowerPenumbraFade
    else KernelStep.CalculateAdjacentPenumbraDirection
}

// Reference step 141: fallthrough141
internal fun CurlMeshKernel.calculateAdjacentPenumbraDirection(): KernelStep {
    updateSubtractionFlags((penumbraIndex11 and KernelLayout.WORD_MASK), 0L, 32L)
    penumbraIndex20 = ((if (zeroResult) 1L else 0L) and KernelLayout.WORD_MASK)
    penumbraBits22 =
        (((penumbraIndex10 and KernelLayout.WORD_MASK) and
            (penumbraIndex20 and KernelLayout.WORD_MASK)) and KernelLayout.WORD_MASK)
    updateSubtractionFlags(penumbraIndex3, penumbraBits7, 64L)
    penumbraIndex21 =
        ((if ((negativeResult != arithmeticOverflow)) 1L else 0L) and KernelLayout.WORD_MASK)
    negativeResult = (0L != 0L)
    zeroResult =
        (((penumbraIndex21 and KernelLayout.WORD_MASK) and
            (penumbraBits22 and KernelLayout.WORD_MASK)) == 0L)
    arithmeticOverflow = (0L != 0L)
    unsignedCarry = arithmeticOverflow
    penumbraBits23 =
        ((if (!(zeroResult)) (penumbraIndex8 and KernelLayout.WORD_MASK)
        else (penumbraIndex7 and KernelLayout.WORD_MASK)) and KernelLayout.WORD_MASK)
    penumbraIndex22 =
        (((penumbraBits23 and KernelLayout.WORD_MASK) +
            ((penumbraIndex3 and KernelLayout.WORD_MASK) shl 1)) and KernelLayout.WORD_MASK)
    penumbraBits24 = (penumbraAddress14 + (penumbraIndex3 shl 7))
    penumbraBits25 = (penumbraAddress14 + (penumbraIndex22 shl 6))
    penumbraValue110 = readFloat(penumbraBits24)
    penumbraValue111 = readFloat((penumbraBits24 + 4L))
    penumbraValue112 = readFloat(penumbraBits25)
    penumbraValue113 = readFloat((penumbraBits25 + 4L))
    penumbraValue114 = (penumbraValue111).toDouble()
    penumbraValue115 = (penumbraValue110).toDouble()
    penumbraValue116 = (penumbraValue113).toDouble()
    penumbraValue117 = (penumbraValue112).toDouble()
    penumbraValue118 = (penumbraValue117 - penumbraValue115)
    penumbraValue119 = (penumbraValue114 - penumbraValue116)
    penumbraValue120 = (penumbraValue118 * penumbraValue118)
    penumbraValue121 = fma(penumbraValue119, penumbraValue119, penumbraValue120)
    penumbraValue122 = sqrt(penumbraValue121)
    penumbraValue123 = (shadowValue35 / penumbraValue122)
    penumbraValue124.double(0, (penumbraValue119 * penumbraValue123))
    compareFloatingPoint(penumbraValue124.double(0), penumbraValue124.double(0))
    return if (arithmeticOverflow) KernelStep.BeginLowerPenumbraFade
    else KernelStep.ValidateAdjacentPenumbraDirection
}

// Reference step 142: fallthrough142
internal fun CurlMeshKernel.validateAdjacentPenumbraDirection(): KernelStep {
    penumbraValue125.double(0, (penumbraValue118 * penumbraValue123))
    penumbraValue126 = abs(penumbraValue124.double(0))
    penumbraValue127 = (Double.POSITIVE_INFINITY).toDouble()
    compareFloatingPoint(penumbraValue126, penumbraValue127)
    if (!(zeroResult)) {
        run {
            compareFloatingPoint(penumbraValue125.double(0), penumbraValue125.double(0))
        }
    } else {
        run {
            negativeResult = (0L != 0L)
            zeroResult = (0L != 0L)
            unsignedCarry = (0L != 0L)
            arithmeticOverflow = (1L != 0L)
        }
    }
    return if (arithmeticOverflow) KernelStep.BeginLowerPenumbraFade
    else KernelStep.ValidateAdjacentPenumbraMagnitude
}

// Reference step 143: fallthrough143
internal fun CurlMeshKernel.validateAdjacentPenumbraMagnitude(): KernelStep {
    penumbraValue128 = abs(penumbraValue125.double(0))
    compareFloatingPoint(penumbraValue128, penumbraValue127)
    return if (zeroResult) KernelStep.BeginLowerPenumbraFade
    else KernelStep.UseAdjacentPenumbraDirection
}

// Reference step 144: fallthrough144
internal fun CurlMeshKernel.useAdjacentPenumbraDirection(): KernelStep {
    penumbraValue88.set(penumbraValue124)
    penumbraValue87.set(penumbraValue125)
    return KernelStep.BeginLowerPenumbraFade
}

// Reference step 145: L1853455e0
internal fun CurlMeshKernel.useTerminalPenumbraDirection(): KernelStep {
    penumbraValue105.set(penumbraValue86)
    penumbraValue106.set(penumbraValue84)
    return KernelStep.BeginLowerPenumbraFade
}

// Reference step 146: L1853455e8
internal fun CurlMeshKernel.beginLowerPenumbraFade(): KernelStep {
    penumbraIndex23 = 0L
    penumbraIndex24 = 0L
    penumbraIndex25 = (penumbraIndex3 shl 1)
    penumbraIndex26 = ((0L or 64L) and KernelLayout.WORD_MASK)
    penumbraIndex27 =
        ((penumbraIndex26 and (-128L).inv()) or
            ((penumbraIndex3 and KernelLayout.VERTEX_PAIR_INDEX_MASK) shl 7))
    penumbraBits26 = (penumbraAddress12 + penumbraIndex27)
    penumbraValue129 = readFloat(penumbraBits26)
    penumbraValue130 = readFloat((penumbraBits26 + 4L))
    penumbraValue131 = (penumbraValue129).toDouble()
    penumbraValue132 = (penumbraValue130).toDouble()
    penumbraValue133.double(0, readDouble((penumbraBits26 + 40L)))
    penumbraValue134 = readDouble((penumbraAddress20 + (penumbraIndex3 shl 3)))
    penumbraColor3.unpackHalfColorFrom(penumbraValue133)
    return KernelStep.SampleLowerPenumbraFade
}

// Reference step 147: L185345618
internal fun CurlMeshKernel.sampleLowerPenumbraFade(): KernelStep {
    penumbraValue135 = readDouble((penumbraBits11 + penumbraIndex24))
    penumbraValue136 = readDouble((penumbraBits12 + penumbraIndex24))
    return if ((penumbraIndex23 == 0L)) KernelStep.AppendLowerPenumbraFade
    else KernelStep.CopyLowerFadeJoin
}

// Reference step 148: fallthrough148
internal fun CurlMeshKernel.copyLowerFadeJoin(): KernelStep {
    penumbraValue137 = (penumbraValue75).toFloat()
    penumbraBits27 = (penumbraAddress24 + penumbraIndex23)
    penumbraValue138 = (penumbraValue78).toFloat()
    writeFloat((penumbraBits27 + 64L), penumbraValue137)
    writeFloat(((penumbraBits27 + 64L) + 4L), penumbraValue138)
    writeDouble((penumbraBits27 + 72L), penumbraValue85)
    writeLongBits((penumbraBits27 + 104L), penumbraAddress)
    return KernelStep.AppendLowerPenumbraFade
}

// Reference step 149: L18534563c
internal fun CurlMeshKernel.appendLowerPenumbraFade(): KernelStep {
    penumbraValue139 = (penumbraValue105.double(0) * penumbraValue136)
    penumbraValue140 = (penumbraValue106.double(0) * penumbraValue136)
    penumbraValue75 = fma(penumbraValue139, penumbraValue134, penumbraValue131)
    penumbraValue78 = fma(penumbraValue140, penumbraValue134, penumbraValue132)
    penumbraValue141 = (penumbraValue75).toFloat()
    penumbraBits28 = (penumbraAddress24 + penumbraIndex23)
    penumbraValue142 = (penumbraValue78).toFloat()
    writeFloat(penumbraBits28, penumbraValue141)
    writeFloat((penumbraBits28 + 4L), penumbraValue142)
    writeDouble((penumbraBits28 + 8L), penumbraValue85)
    penumbraValue143.float(0, (penumbraValue135).toFloat())
    run {
        var ta = penumbraColor3.copy()
        var tb = penumbraValue143.copy()
        var tc = penumbraValue143.copy()
        for (lane in 0 until 4) {
            penumbraValue144.float(lane, (ta.float(lane) * tb.float(0)))
        }
    }
    penumbraColor4.packHalfColorFrom(penumbraValue144)
    writeDouble((scratchBase + 576L), penumbraColor4.double(0))
    writeDouble((penumbraBits28 + 40L), penumbraColor4.double(0))
    penumbraAddress = penumbraColor4.longBits(0)
    penumbraIndex24 = (penumbraIndex24 + 8L)
    penumbraIndex23 = (penumbraIndex23 + penumbraBits10)
    updateSubtractionFlags(penumbraIndex24, 24L, 64L)
    return if (!(zeroResult)) KernelStep.SampleLowerPenumbraFade
    else KernelStep.BeginUpperPenumbraFade
}

// Reference step 150: fallthrough150
internal fun CurlMeshKernel.beginUpperPenumbraFade(): KernelStep {
    penumbraIndex28 = 0L
    penumbraIndex29 = 0L
    penumbraBits29 = (penumbraAddress14 + (penumbraIndex25 shl 6))
    penumbraValue145 = readFloat(penumbraBits29)
    penumbraValue146 = readFloat((penumbraBits29 + 4L))
    penumbraValue147 = (penumbraValue145).toDouble()
    penumbraValue148 = (penumbraValue146).toDouble()
    penumbraValue149.double(0, readDouble((penumbraBits29 + 40L)))
    penumbraColor5.unpackHalfColorFrom(penumbraValue149)
    return KernelStep.SampleUpperPenumbraFade
}

// Reference step 151: L1853456a8
internal fun CurlMeshKernel.sampleUpperPenumbraFade(): KernelStep {
    penumbraValue150 = readDouble((penumbraBits11 + penumbraIndex29))
    penumbraValue151 = readDouble((penumbraBits12 + penumbraIndex29))
    return if ((penumbraIndex28 == 0L)) KernelStep.AppendUpperPenumbraFade
    else KernelStep.CopyUpperFadeJoin
}

// Reference step 152: fallthrough152
internal fun CurlMeshKernel.copyUpperFadeJoin(): KernelStep {
    penumbraValue152 = (penumbraValue75).toFloat()
    penumbraBits30 = (penumbraAddress22 + penumbraIndex28)
    writeFloat(penumbraBits30, penumbraValue152)
    penumbraValue153 = (penumbraValue78).toFloat()
    writeFloat((penumbraBits30 + 4L), penumbraValue153)
    writeDouble((penumbraBits30 + 8L), penumbraValue85)
    writeLongBits((penumbraBits30 + 40L), penumbraAddress)
    return KernelStep.AppendUpperPenumbraFade
}

// Reference step 153: L1853456d0
internal fun CurlMeshKernel.appendUpperPenumbraFade(): KernelStep {
    penumbraValue154 = (penumbraValue88.double(0) * penumbraValue151)
    penumbraValue155 = (penumbraValue87.double(0) * penumbraValue151)
    penumbraValue75 = fma(penumbraValue154, penumbraValue134, penumbraValue147)
    penumbraValue78 = fma(penumbraValue155, penumbraValue134, penumbraValue148)
    penumbraValue156 = (penumbraValue75).toFloat()
    penumbraBits31 = (penumbraAddress22 + penumbraIndex28)
    penumbraValue157 = (penumbraValue78).toFloat()
    writeFloat((penumbraBits31 + 64L), penumbraValue156)
    writeFloat(((penumbraBits31 + 64L) + 4L), penumbraValue157)
    writeDouble((penumbraBits31 + 72L), penumbraValue85)
    penumbraValue158.float(0, (penumbraValue150).toFloat())
    run {
        var ta = penumbraColor5.copy()
        var tb = penumbraValue158.copy()
        var tc = penumbraValue158.copy()
        for (lane in 0 until 4) {
            penumbraValue159.float(lane, (ta.float(lane) * tb.float(0)))
        }
    }
    penumbraColor6.packHalfColorFrom(penumbraValue159)
    writeDouble((scratchBase + 576L), penumbraColor6.double(0))
    writeDouble((penumbraBits31 + 104L), penumbraColor6.double(0))
    penumbraAddress = penumbraColor6.longBits(0)
    penumbraIndex29 = (penumbraIndex29 + 8L)
    penumbraIndex28 = (penumbraIndex28 + penumbraBits10)
    updateSubtractionFlags(penumbraIndex29, 24L, 64L)
    return if (!(zeroResult)) KernelStep.SampleUpperPenumbraFade
    else KernelStep.AdvancePenumbraSegment
}

// Reference step 154: fallthrough154
internal fun CurlMeshKernel.advancePenumbraSegment(): KernelStep {
    penumbraIndex3 = (penumbraIndex3 + 1L)
    penumbraAddress24 = (penumbraAddress24 + 128L)
    penumbraAddress22 = (penumbraAddress22 + 128L)
    penumbraValue84.set(penumbraValue106)
    penumbraValue86.set(penumbraValue105)
    updateSubtractionFlags(penumbraIndex3, penumbraBits3, 64L)
    return if (!(zeroResult)) KernelStep.BeginPenumbraSegment else KernelStep.FinishPenumbraSegments
}

// Reference step 155: fallthrough155
internal fun CurlMeshKernel.finishPenumbraSegments(): KernelStep {
    return KernelStep.InspectPenumbraAxis
}

// Reference step 156: L18534573c
internal fun CurlMeshKernel.prepareEmptyPenumbra(): KernelStep {
    penumbraAddress30 = (scratchBase + (10L shl 12))
    penumbraAddress31 = (penumbraAddress30 + 1792L)
    writeWord(penumbraAddress31, 0L)
    penumbraIndex30 = ((0L or 1L) and KernelLayout.WORD_MASK)
    writeLongBits((scratchBase + 568L), penumbraIndex30)
    penumbraValue89 = readDouble((scratchBase + 536L))
    penumbraValue90 = readDouble((scratchBase + 528L))
    shadowValue35 = 1.0
    return KernelStep.InspectPenumbraAxis
}

// Reference step 157: L18534575c
internal fun CurlMeshKernel.inspectPenumbraAxis(): KernelStep {
    compareFloatingPoint(penumbraValue89, 0.0)
    return if (zeroResult) KernelStep.PreparePenumbraEndCap
    else KernelStep.InspectPenumbraAxisMagnitude
}

// Reference step 158: fallthrough158
internal fun CurlMeshKernel.inspectPenumbraAxisMagnitude(): KernelStep {
    penumbraValue160 = abs(penumbraValue89)
    compareFloatingPoint(penumbraValue160, shadowValue35)
    return if (zeroResult) KernelStep.PreparePenumbraEndCap
    else KernelStep.PreparePenumbraEndpointLookup
}

// Reference step 159: fallthrough159
internal fun CurlMeshKernel.preparePenumbraEndpointLookup(): KernelStep {
    penumbraIndex31 = 0L
    penumbraAddress32 = readLongBits((scratchBase + 560L))
    penumbraBits32 = (penumbraAddress32 shl 4)
    penumbraAddress33 = (scratchBase + 2336L)
    penumbraAddress34 = (scratchBase + 2496L)
    penumbraAddress35 = (scratchBase + 2512L)
    penumbraAddress36 = (scratchBase + 1504L)
    penumbraAddress37 = (scratchBase + 2368L)
    penumbraAddress38 = (scratchBase + 2432L)
    penumbraAddress39 = (scratchBase + (28L shl 12))
    penumbraAddress40 = (penumbraAddress39 + 272L)
    penumbraAddress41 = (scratchBase + (21L shl 12))
    penumbraAddress42 = (penumbraAddress41 + 3600L)
    penumbraBits33 = (16896L and KernelLayout.WORD_MASK)
    penumbraBits34 = (17024L and KernelLayout.WORD_MASK)
    return KernelStep.FindPenumbraEndpoint
}

// Reference step 160: L1853457ac
internal fun CurlMeshKernel.findPenumbraEndpoint(): KernelStep {
    penumbraBits35 = (readUnsignedWord(penumbraAddress35) and KernelLayout.WORD_MASK)
    penumbraBits36 = ((penumbraBits35 and KernelLayout.WORD_MASK)).toInt().toLong()
    penumbraBits37 = (readUnsignedWord(penumbraAddress34) and KernelLayout.WORD_MASK)
    penumbraBits38 = (penumbraAddress36 + penumbraIndex31)
    writeLongBits(penumbraBits38, 0L)
    writeLongBits((penumbraBits38 + 8L), 0L)
    penumbraBits39 = (penumbraAddress37 + penumbraIndex31)
    penumbraBits40 = (penumbraAddress38 + penumbraIndex31)
    writeLongBits(penumbraBits40, 0L)
    writeLongBits((penumbraBits40 + 8L), 0L)
    writeLongBits(penumbraBits39, 0L)
    writeLongBits((penumbraBits39 + 8L), 0L)
    updateSubtractionFlags(
        (penumbraBits35 and KernelLayout.WORD_MASK),
        (shadowBits6 and KernelLayout.WORD_MASK),
        32L,
    )
    if ((negativeResult != arithmeticOverflow)) {
        run {
            updateSubtractionFlags((penumbraBits35 and KernelLayout.WORD_MASK), 1L, 32L)
        }
    } else {
        run {
            negativeResult = (1L != 0L)
            zeroResult = (0L != 0L)
            unsignedCarry = (0L != 0L)
            arithmeticOverflow = (0L != 0L)
        }
    }
    if ((negativeResult == arithmeticOverflow)) {
        run {
            updateSubtractionFlags((penumbraBits37 and KernelLayout.WORD_MASK), 0L, 32L)
        }
    } else {
        run {
            negativeResult = (0L != 0L)
            zeroResult = (0L != 0L)
            unsignedCarry = (0L != 0L)
            arithmeticOverflow = (0L != 0L)
        }
    }
    return if (zeroResult) KernelStep.UseFirstPenumbraEndpoint
    else KernelStep.InspectPenumbraTerminalEndpoint
}

// Reference step 161: fallthrough161
internal fun CurlMeshKernel.inspectPenumbraTerminalEndpoint(): KernelStep {
    updateSubtractionFlags(
        (penumbraBits36 and KernelLayout.WORD_MASK),
        (shadowBits6 and KernelLayout.WORD_MASK),
        32L,
    )
    if ((negativeResult != arithmeticOverflow)) {
        run {
            updateSubtractionFlags((penumbraBits36 and KernelLayout.WORD_MASK), 1L, 32L)
        }
    } else {
        run {
            negativeResult = (1L != 0L)
            zeroResult = (0L != 0L)
            unsignedCarry = (0L != 0L)
            arithmeticOverflow = (0L != 0L)
        }
    }
    if ((negativeResult == arithmeticOverflow)) {
        run {
            updateSubtractionFlags((penumbraBits37 and KernelLayout.WORD_MASK), 2L, 32L)
        }
    } else {
        run {
            negativeResult = (0L != 0L)
            zeroResult = (0L != 0L)
            unsignedCarry = (0L != 0L)
            arithmeticOverflow = (0L != 0L)
        }
    }
    return if (zeroResult) KernelStep.UseLastPenumbraEndpoint
    else KernelStep.InspectPenumbraEndpointKind
}

// Reference step 162: fallthrough162
internal fun CurlMeshKernel.inspectPenumbraEndpointKind(): KernelStep {
    updateSubtractionFlags((penumbraBits37 and KernelLayout.WORD_MASK), 1L, 32L)
    return if (!(zeroResult)) KernelStep.AdvancePenumbraEndpointLookup
    else KernelStep.UseInteriorPenumbraEndpoint
}

// Reference step 163: fallthrough163
internal fun CurlMeshKernel.useInteriorPenumbraEndpoint(): KernelStep {
    penumbraIndex32 = (penumbraBits36 shl 1)
    penumbraIndex33 = ((0L or 64L) and KernelLayout.WORD_MASK)
    penumbraIndex34 =
        ((penumbraIndex33 and (-128L).inv()) or
            ((penumbraBits36 and KernelLayout.VERTEX_PAIR_INDEX_MASK) shl 7))
    penumbraBits41 = (penumbraAddress40 + penumbraIndex34)
    penumbraValue161.double(0, readDouble(penumbraBits41))
    run {
        var t = penumbraValue161.copy()
        for (lane in 0 until 2) {
            penumbraValue162.double(lane, (t.float(lane)).toDouble())
        }
    }
    penumbraValue162.store(memory, (penumbraAddress36 + penumbraIndex31))
    penumbraAddress43 = readLongBits((penumbraBits41 + 40L))
    writeLongBits(penumbraAddress33, penumbraAddress43)
    penumbraBits42 = ((penumbraIndex32 and KernelLayout.WORD_MASK)).toInt().toLong()
    penumbraBits43 = (penumbraAddress40 + (penumbraBits42 shl 6))
    penumbraValue163.double(0, readDouble((penumbraBits43 + 16896L)))
    run {
        var t = penumbraValue163.copy()
        for (lane in 0 until 2) {
            penumbraValue164.double(lane, (t.float(lane)).toDouble())
        }
    }
    penumbraValue164.store(memory, (penumbraAddress38 + penumbraIndex31))
    penumbraBits44 = (penumbraAddress42 + penumbraIndex34)
    penumbraBits45 = (penumbraBits44 + penumbraBits33)
    return KernelStep.ReadPenumbraEndpoint
}

// Reference step 164: L18534583c
internal fun CurlMeshKernel.useFirstPenumbraEndpoint(): KernelStep {
    penumbraIndex35 = (penumbraBits36 shl 1)
    penumbraIndex36 = ((0L or 64L) and KernelLayout.WORD_MASK)
    penumbraIndex37 =
        ((penumbraIndex36 and (-128L).inv()) or
            ((penumbraBits36 and KernelLayout.VERTEX_PAIR_INDEX_MASK) shl 7))
    penumbraBits46 = (penumbraAddress40 + penumbraIndex37)
    penumbraValue165.double(0, readDouble(penumbraBits46))
    run {
        var t = penumbraValue165.copy()
        for (lane in 0 until 2) {
            penumbraValue166.double(lane, (t.float(lane)).toDouble())
        }
    }
    penumbraValue166.store(memory, (penumbraAddress36 + penumbraIndex31))
    penumbraAddress44 = readLongBits((penumbraBits46 + 40L))
    writeLongBits(penumbraAddress33, penumbraAddress44)
    penumbraBits47 = ((penumbraIndex35 and KernelLayout.WORD_MASK)).toInt().toLong()
    penumbraBits48 = (penumbraAddress40 + (penumbraBits47 shl 6))
    penumbraValue167.double(0, readDouble((penumbraBits48 + 16896L)))
    run {
        var t = penumbraValue167.copy()
        for (lane in 0 until 2) {
            penumbraValue168.double(lane, (t.float(lane)).toDouble())
        }
    }
    penumbraValue168.store(memory, (penumbraAddress38 + penumbraIndex31))
    penumbraBits45 = (penumbraBits48 + penumbraBits34)
    return KernelStep.ReadPenumbraEndpoint
}

// Reference step 165: L18534587c
internal fun CurlMeshKernel.useLastPenumbraEndpoint(): KernelStep {
    penumbraIndex38 = (penumbraBits36 shl 1)
    penumbraBits49 = (penumbraAddress42 + (penumbraBits36 shl 7))
    penumbraValue169.double(0, readDouble(penumbraBits49))
    run {
        var t = penumbraValue169.copy()
        for (lane in 0 until 2) {
            penumbraValue170.double(lane, (t.float(lane)).toDouble())
        }
    }
    penumbraValue170.store(memory, (penumbraAddress36 + penumbraIndex31))
    penumbraAddress45 = readLongBits((penumbraBits49 + 40L))
    writeLongBits(penumbraAddress33, penumbraAddress45)
    penumbraBits50 = ((penumbraIndex38 and KernelLayout.WORD_MASK)).toInt().toLong()
    penumbraIndex39 = (penumbraBits50 ushr 1)
    penumbraBits51 = (penumbraAddress42 + penumbraBits33)
    penumbraBits52 = (penumbraBits51 + (penumbraBits50 shl 6))
    penumbraValue171.double(0, readDouble((penumbraBits52 + 192L)))
    run {
        var t = penumbraValue171.copy()
        for (lane in 0 until 2) {
            penumbraValue172.double(lane, (t.float(lane)).toDouble())
        }
    }
    penumbraValue172.store(memory, (penumbraAddress38 + penumbraIndex31))
    penumbraIndex40 = ((0L or 64L) and KernelLayout.WORD_MASK)
    penumbraIndex41 =
        ((penumbraIndex40 and (-128L).inv()) or
            ((penumbraIndex39 and KernelLayout.VERTEX_PAIR_INDEX_MASK) shl 7))
    penumbraBits45 = (penumbraBits51 + penumbraIndex41)
    return KernelStep.ReadPenumbraEndpoint
}

// Reference step 166: L1853458c0
internal fun CurlMeshKernel.readPenumbraEndpoint(): KernelStep {
    penumbraValue173 = readFloat(penumbraBits45)
    penumbraValue174 = (penumbraValue173).toDouble()
    writeDouble((penumbraAddress37 + penumbraIndex31), penumbraValue174)
    penumbraBits53 = (penumbraBits45 or 4L)
    penumbraValue175 = readFloat(penumbraBits53)
    penumbraValue176 = (penumbraValue175).toDouble()
    writeDouble((penumbraBits39 + 8L), penumbraValue176)
    return KernelStep.AdvancePenumbraEndpointLookup
}

// Reference step 167: L1853458dc
internal fun CurlMeshKernel.advancePenumbraEndpointLookup(): KernelStep {
    penumbraIndex31 = (penumbraIndex31 + 16L)
    penumbraAddress33 = (penumbraAddress33 + 8L)
    penumbraAddress34 = (penumbraAddress34 + 4L)
    penumbraAddress35 = (penumbraAddress35 + 4L)
    updateSubtractionFlags(penumbraBits32, penumbraIndex31, 64L)
    return if (!(zeroResult)) KernelStep.FindPenumbraEndpoint
    else KernelStep.FinishPenumbraEndpointLookup
}

// Reference step 168: fallthrough168
internal fun CurlMeshKernel.finishPenumbraEndpointLookup(): KernelStep {
    penumbraIndex42 = (0L and KernelLayout.WORD_MASK)
    return KernelStep.PreparePenumbraCorners
}

// Reference step 169: L1853458fc
internal fun CurlMeshKernel.preparePenumbraEndCap(): KernelStep {
    penumbraIndex43 = 0L
    penumbraAddress46 = (scratchBase + (7L shl 12))
    penumbraAddress47 = (penumbraAddress46 + 896L)
    penumbraAddress48 = readLongBits((scratchBase + 288L))
    penumbraBits54 =
        (penumbraAddress47 +
            (((penumbraAddress48 and KernelLayout.WORD_MASK)).toInt().toLong() shl 3))
    penumbraValue177 = readDouble((penumbraBits54 + -(8L)))
    penumbraIndex44 = ((penumbraAddress48).toInt().toLong() shl 1)
    penumbraIndex45 = (penumbraIndex44 - 1L)
    penumbraAddress49 = (scratchBase + (28L shl 12))
    penumbraAddress50 = (penumbraAddress49 + 272L)
    penumbraBits55 = (penumbraAddress50 + (penumbraIndex45 shl 6))
    penumbraValue178 = readFloat(penumbraBits55)
    penumbraValue179 = readFloat((penumbraBits55 + 4L))
    penumbraValue180.double(0, (penumbraValue178).toDouble())
    penumbraValue181.double(0, (penumbraValue179).toDouble())
    penumbraBits56 = ((penumbraIndex44 and KernelLayout.WORD_MASK)).toInt().toLong()
    penumbraBits57 = (penumbraBits56 - 2L)
    penumbraAddress51 = (scratchBase + (21L shl 12))
    penumbraAddress52 = (penumbraAddress51 + 3600L)
    penumbraBits58 = (penumbraAddress52 + (penumbraBits57 shl 6))
    penumbraValue182 = readFloat(penumbraBits58)
    penumbraValue183 = readFloat((penumbraBits58 + 4L))
    penumbraValue184.double(0, (penumbraValue182).toDouble())
    penumbraValue185.double(0, (penumbraValue183).toDouble())
    penumbraAddress53 = readLongBits((penumbraBits55 + 40L))
    penumbraAddress54 = readLongBits((penumbraBits58 + 40L))
    penumbraValue186.longBits(0, penumbraAddress54)
    penumbraBits59 = FADE_WEIGHTS
    penumbraColor7.unpackHalfColorFrom(penumbraValue186)
    penumbraBits60 = FADE_STEPS
    penumbraAddress55 = (scratchBase + (21L shl 12))
    penumbraAddress56 = (penumbraAddress55 + 2832L)
    penumbraValue187 = KernelTuning.SHADOW_PLANE_Z
    penumbraBits61 = penumbraAddress54
    penumbraBits62 = penumbraAddress53
    penumbraValue188.set(penumbraValue181)
    penumbraValue189.set(penumbraValue180)
    penumbraValue190.set(penumbraValue185)
    penumbraValue191.set(penumbraValue184)
    return KernelStep.AppendPenumbraEndCap
}

// Reference step 170: L1853459a0
internal fun CurlMeshKernel.advancePenumbraEndCap(): KernelStep {
    penumbraBits61 = penumbraColor10.longBits(0)
    penumbraBits62 = penumbraColor9.longBits(0)
    penumbraIndex43 = (penumbraIndex43 + 256L)
    penumbraBits59 = (penumbraBits59 + 8L)
    penumbraBits60 = (penumbraBits60 + 8L)
    return KernelStep.AppendPenumbraEndCap
}

// Reference step 171: L1853459b4
internal fun CurlMeshKernel.appendPenumbraEndCap(): KernelStep {
    penumbraValue192 = readDouble(penumbraBits59)
    penumbraValue193 = readDouble(penumbraBits60)
    penumbraValue194 = (penumbraValue189.double(0)).toFloat()
    penumbraBits63 = (penumbraAddress56 + penumbraIndex43)
    penumbraValue195 = (penumbraValue188.double(0)).toFloat()
    writeFloat(penumbraBits63, penumbraValue194)
    writeFloat((penumbraBits63 + 4L), penumbraValue195)
    writeDouble((penumbraBits63 + 8L), penumbraValue187)
    writeLongBits((penumbraBits63 + 40L), penumbraBits62)
    penumbraValue196.float(0, (penumbraValue192).toFloat())
    penumbraValue197.longBits(0, penumbraAddress53)
    penumbraColor8.unpackHalfColorFrom(penumbraValue197)
    run {
        var ta = penumbraColor8.copy()
        var tb = penumbraValue196.copy()
        var tc = penumbraColor8.copy()
        for (lane in 0 until 4) {
            penumbraValue198.float(lane, (ta.float(lane) * tb.float(0)))
        }
    }
    penumbraColor9.packHalfColorFrom(penumbraValue198)
    penumbraValue199 = (penumbraValue89 * penumbraValue193)
    penumbraValue200 = (penumbraValue177 * penumbraValue199)
    penumbraValue189.double(0, (penumbraValue200 + penumbraValue180.double(0)))
    penumbraValue201 = (penumbraValue90 * penumbraValue193)
    penumbraValue202 = (penumbraValue177 * penumbraValue201)
    penumbraValue188.double(0, (penumbraValue202 + penumbraValue181.double(0)))
    penumbraValue203 = (penumbraValue189.double(0)).toFloat()
    penumbraValue204 = (penumbraValue188.double(0)).toFloat()
    writeFloat((penumbraBits63 + 64L), penumbraValue203)
    writeFloat(((penumbraBits63 + 64L) + 4L), penumbraValue204)
    writeDouble((penumbraBits63 + 72L), penumbraValue187)
    writeDouble((penumbraBits63 + 104L), penumbraColor9.double(0))
    penumbraValue205 = (penumbraValue191.double(0)).toFloat()
    penumbraValue206.float(0, (penumbraValue190.double(0)).toFloat())
    writeFloat((penumbraBits63 + 128L), penumbraValue205)
    writeFloat(((penumbraBits63 + 128L) + 4L), penumbraValue206.float(0))
    writeDouble((penumbraBits63 + 136L), penumbraValue187)
    writeLongBits((penumbraBits63 + 168L), penumbraBits61)
    run {
        var ta = penumbraColor7.copy()
        var tb = penumbraValue196.copy()
        var tc = penumbraValue206.copy()
        for (lane in 0 until 4) {
            penumbraValue207.float(lane, (ta.float(lane) * tb.float(0)))
        }
    }
    penumbraColor10.packHalfColorFrom(penumbraValue207)
    penumbraValue191.double(0, (penumbraValue200 + penumbraValue184.double(0)))
    penumbraValue190.double(0, (penumbraValue202 + penumbraValue185.double(0)))
    penumbraValue208 = (penumbraValue191.double(0)).toFloat()
    penumbraValue209 = (penumbraValue190.double(0)).toFloat()
    writeFloat((penumbraBits63 + 192L), penumbraValue208)
    writeFloat(((penumbraBits63 + 192L) + 4L), penumbraValue209)
    writeDouble((penumbraBits63 + 200L), penumbraValue187)
    writeDouble((penumbraBits63 + 232L), penumbraColor10.double(0))
    updateSubtractionFlags(penumbraIndex43, 512L, 64L)
    return if (!(zeroResult)) KernelStep.AdvancePenumbraEndCap else KernelStep.FinishPenumbraEndCap
}

// Reference step 172: fallthrough172
internal fun CurlMeshKernel.finishPenumbraEndCap(): KernelStep {
    penumbraValue210.double(0, readDouble(penumbraBits55))
    run {
        var t = penumbraValue210.copy()
        for (lane in 0 until 2) {
            penumbraValue211.double(lane, (t.float(lane)).toDouble())
        }
    }
    penumbraValue211.store(memory, (scratchBase + 1504L))
    writeLongBits((scratchBase + 2344L), penumbraAddress54)
    writeLongBits((scratchBase + 2336L), penumbraAddress53)
    penumbraAddress57 = (scratchBase + (28L shl 12))
    penumbraAddress58 = (penumbraAddress57 + 272L)
    penumbraBits64 = (penumbraAddress58 + (penumbraBits57 shl 6))
    penumbraValue212.double(0, readDouble((penumbraBits64 + 16896L)))
    run {
        var t = penumbraValue212.copy()
        for (lane in 0 until 2) {
            penumbraValue213.double(lane, (t.float(lane)).toDouble())
        }
    }
    penumbraAddress59 = (scratchBase + (21L shl 12))
    penumbraAddress60 = (penumbraAddress59 + 2832L)
    penumbraValue214.double(0, readDouble((penumbraAddress60 + 704L)))
    run {
        var t = penumbraValue214.copy()
        for (lane in 0 until 2) {
            penumbraValue215.double(lane, (t.float(lane)).toDouble())
        }
    }
    penumbraValue215.store(memory, (scratchBase + 2448L))
    penumbraValue213.store(memory, (scratchBase + 2432L))
    penumbraValue216.double(0, readDouble((penumbraAddress60 + 576L)))
    run {
        var t = penumbraValue216.copy()
        for (lane in 0 until 2) {
            penumbraValue217.double(lane, (t.float(lane)).toDouble())
        }
    }
    penumbraAddress61 = (scratchBase + (21L shl 12))
    penumbraAddress62 = (penumbraAddress61 + 3600L)
    penumbraBits65 = (penumbraAddress62 + (penumbraIndex45 shl 6))
    penumbraValue218.double(0, readDouble((penumbraBits65 + 16896L)))
    run {
        var t = penumbraValue218.copy()
        for (lane in 0 until 2) {
            penumbraValue219.double(lane, (t.float(lane)).toDouble())
        }
    }
    penumbraValue219.store(memory, (scratchBase + 2384L))
    penumbraValue217.store(memory, (scratchBase + 2368L))
    writeDouble((scratchBase + 1528L), penumbraValue185.double(0))
    writeDouble((scratchBase + 1520L), penumbraValue184.double(0))
    penumbraIndex42 = ((0L or 1L) and KernelLayout.WORD_MASK)
    penumbraIndex46 = ((0L or 2L) and KernelLayout.WORD_MASK)
    writeLongBits((scratchBase + 560L), penumbraIndex46)
    return KernelStep.PreparePenumbraCorners
}

// Reference step 173: L185345acc
internal fun CurlMeshKernel.preparePenumbraCorners(): KernelStep {
    penumbraIndex47 = 0L
    penumbraAddress63 = (scratchBase + (10L shl 12))
    penumbraAddress64 = (penumbraAddress63 + 1808L)
    penumbraValue220 = 0.034482758620689655
    penumbraAddress65 = (scratchBase + 2336L)
    penumbraBits66 = FADE_WEIGHTS
    penumbraValue221 = KernelLayout.POSITION_Z_ZERO_W_ONE
    penumbraValue222.longBits(1, 0L)
    penumbraBits67 = (11528L and KernelLayout.WORD_MASK)
    penumbraBits68 = (11592L and KernelLayout.WORD_MASK)
    penumbraIndex48 = (11520L and KernelLayout.WORD_MASK)
    penumbraBits69 = FADE_STEPS
    penumbraValue222.store(memory, (scratchBase + 544L))
    return KernelStep.PreparePenumbraCornerRotation
}

// Reference step 174: L185345b0c
internal fun CurlMeshKernel.preparePenumbraCornerRotation(): KernelStep {
    penumbraAddress66 = (scratchBase + 2432L)
    penumbraBits70 = (penumbraAddress66 + (penumbraIndex47 shl 4))
    penumbraAddress67 = (scratchBase + 1504L)
    penumbraBits71 = (penumbraAddress67 + (penumbraIndex47 shl 4))
    penumbraValue223 = readDouble(penumbraBits70)
    penumbraValue224 = readDouble((penumbraBits70 + 8L))
    penumbraValue225.double(0, readDouble(penumbraBits71))
    penumbraValue226.double(0, readDouble((penumbraBits71 + 8L)))
    penumbraValue227 = (penumbraValue223 - penumbraValue225.double(0))
    penumbraValue228 = (penumbraValue224 - penumbraValue226.double(0))
    penumbraAddress68 = (scratchBase + 2368L)
    penumbraBits72 = (penumbraAddress68 + (penumbraIndex47 shl 4))
    penumbraValue229 = readDouble(penumbraBits72)
    penumbraValue230 = readDouble((penumbraBits72 + 8L))
    penumbraValue231 = (penumbraValue229 - penumbraValue225.double(0))
    penumbraValue232 = (penumbraValue230 - penumbraValue226.double(0))
    penumbraValue233 = (penumbraValue228 * penumbraValue228)
    penumbraValue234 = fma(penumbraValue227, penumbraValue227, penumbraValue233)
    penumbraValue235 = sqrt(penumbraValue234)
    penumbraValue236 = (shadowValue35 / penumbraValue235)
    penumbraValue237 = (penumbraValue227 * penumbraValue236)
    penumbraValue238 = (penumbraValue228 * penumbraValue236)
    penumbraValue239 = (penumbraValue232 * penumbraValue232)
    penumbraValue240 = fma(penumbraValue231, penumbraValue231, penumbraValue239)
    penumbraValue241 = sqrt(penumbraValue240)
    penumbraValue242 = (shadowValue35 / penumbraValue241)
    penumbraValue243 = (penumbraValue231 * penumbraValue242)
    penumbraValue244 = (penumbraValue232 * penumbraValue242)
    penumbraValue245 = (penumbraValue238 * penumbraValue244)
    penumbraValue246 = fma(penumbraValue237, penumbraValue243, penumbraValue245)
    penumbraValue247 = (penumbraValue246).toFloat()
    penumbraValue248 = AppleMath.acos(penumbraValue247)
    penumbraValue249 = (penumbraValue248).toDouble()
    penumbraValue250 = (penumbraValue249 * penumbraValue220)
    rotationMatrixAddress = (scratchBase + 592L)
    writeRotationMatrix(penumbraValue250, rotationMatrixAddress)
    penumbraValue251.load(memory, (scratchBase + 544L))
    penumbraIndex49 = 0L
    penumbraAddress69 = readLongBits((penumbraAddress65 + (penumbraIndex47 shl 3)))
    penumbraValue252.longBits(0, penumbraAddress69)
    penumbraColor11.unpackHalfColorFrom(penumbraValue252)
    penumbraValue253 = readDouble((scratchBase + 600L))
    penumbraValue254 = readDouble((scratchBase + 592L))
    penumbraValue255 = readDouble((scratchBase + 616L))
    penumbraValue256 = readDouble((scratchBase + 608L))
    penumbraValue257 = readDouble((scratchBase + 632L))
    penumbraValue258 = readDouble((scratchBase + 624L))
    penumbraBits73 = penumbraAddress64
    return KernelStep.BeginPenumbraCorner
}

// Reference step 175: L185345bc0
internal fun CurlMeshKernel.beginPenumbraCorner(): KernelStep {
    penumbraIndex50 = -11520L
    penumbraBits74 = penumbraBits69
    penumbraBits75 = penumbraBits66
    penumbraValue259.set(penumbraValue226)
    penumbraValue260.set(penumbraValue225)
    penumbraBits76 = penumbraAddress69
    return KernelStep.AppendPenumbraCornerVertex
}

// Reference step 176: L185345bd8
internal fun CurlMeshKernel.appendPenumbraCornerVertex(): KernelStep {
    penumbraValue261 = readDouble(penumbraBits75)
    penumbraBits75 = (penumbraBits75 + 8L)
    penumbraValue262 = readDouble(penumbraBits74)
    penumbraBits74 = (penumbraBits74 + 8L)
    penumbraValue263 = (penumbraValue260.double(0)).toFloat()
    penumbraValue264.double(0, fma(penumbraValue227, penumbraValue262, penumbraValue225.double(0)))
    penumbraValue265 = (penumbraValue259.double(0)).toFloat()
    penumbraValue266.double(0, fma(penumbraValue228, penumbraValue262, penumbraValue226.double(0)))
    penumbraValue267.float(0, (penumbraValue261).toFloat())
    run {
        var ta = penumbraColor11.copy()
        var tb = penumbraValue267.copy()
        var tc = penumbraValue267.copy()
        for (lane in 0 until 4) {
            penumbraValue268.float(lane, (ta.float(lane) * tb.float(0)))
        }
    }
    penumbraColor12.packHalfColorFrom(penumbraValue268)
    penumbraBits77 = (penumbraBits73 + penumbraIndex50)
    writeFloat((penumbraBits77 + 11520L), penumbraValue263)
    writeFloat((penumbraBits77 + 11524L), penumbraValue265)
    penumbraValue251.store(memory, (penumbraBits77 + penumbraBits67))
    writeLongBits((penumbraBits77 + 11560L), penumbraBits76)
    penumbraValue269 = (penumbraValue264.double(0)).toFloat()
    writeFloat((penumbraBits77 + 11584L), penumbraValue269)
    penumbraValue270 = (penumbraValue266.double(0)).toFloat()
    writeFloat((penumbraBits77 + 11588L), penumbraValue270)
    penumbraValue251.store(memory, (penumbraBits77 + penumbraBits68))
    writeDouble((penumbraBits77 + 11624L), penumbraColor12.double(0))
    penumbraBits76 = penumbraColor12.longBits(0)
    penumbraValue259.set(penumbraValue266)
    penumbraValue260.set(penumbraValue264)
    updateAdditionFlags(penumbraIndex50, 3840L, 64L)
    penumbraIndex50 = (penumbraIndex50 + 3840L)
    return if (!(zeroResult)) KernelStep.AppendPenumbraCornerVertex
    else KernelStep.AdvancePenumbraCornerRing
}

// Reference step 177: fallthrough177
internal fun CurlMeshKernel.advancePenumbraCornerRing(): KernelStep {
    penumbraValue271 = (penumbraValue228 * penumbraValue256)
    penumbraValue272 = fma(penumbraValue227, penumbraValue254, penumbraValue271)
    penumbraValue273 = (penumbraValue228 * penumbraValue255)
    penumbraValue274 = fma(penumbraValue227, penumbraValue253, penumbraValue273)
    penumbraValue227 = (penumbraValue258 + penumbraValue272)
    penumbraValue228 = (penumbraValue257 + penumbraValue274)
    penumbraIndex49 = (penumbraIndex49 + 1L)
    penumbraBits73 = (penumbraBits73 + 128L)
    updateSubtractionFlags(penumbraIndex49, 30L, 64L)
    return if (!(zeroResult)) KernelStep.BeginPenumbraCorner else KernelStep.AdvancePenumbraCorner
}

// Reference step 178: fallthrough178
internal fun CurlMeshKernel.advancePenumbraCorner(): KernelStep {
    penumbraIndex47 = (penumbraIndex47 + 1L)
    penumbraAddress64 = (penumbraAddress64 + penumbraIndex48)
    penumbraAddress70 = readLongBits((scratchBase + 560L))
    updateSubtractionFlags(penumbraIndex47, penumbraAddress70, 64L)
    return if (!(zeroResult)) KernelStep.PreparePenumbraCornerRotation
    else KernelStep.FinishPenumbraCorners
}

// Reference step 179: fallthrough179
internal fun CurlMeshKernel.finishPenumbraCorners(): KernelStep {
    updateSubtractionFlags((penumbraIndex42 and KernelLayout.WORD_MASK), 0L, 32L)
    penumbraIndex51 = ((if (!(zeroResult)) 1L else 0L) and KernelLayout.WORD_MASK)
    writeWord((scratchBase + 544L), (penumbraIndex51 and KernelLayout.WORD_MASK))
    surfaceAddress21 = readLongBits((scratchBase + 184L))
    shadowAddress = readLongBits((scratchBase + 96L))
    surfaceValue102 = readDouble((scratchBase + 448L))
    shadowValue10 = readDouble(((scratchBase + 448L) + 8L))
    shadowValue23.double(0, readDouble((scratchBase + 520L)))
    shadowValue9 = readDouble((scratchBase + 464L))
    shadowValue14 = readDouble((scratchBase + 432L))
    shadowValue13 = readDouble(((scratchBase + 432L) + 8L))
    shadowValue7 = readDouble((scratchBase + 336L))
    shadowValue8 = readDouble(((scratchBase + 336L) + 8L))
    shadowValue11 = readDouble((scratchBase + 320L))
    shadowValue12 = readDouble(((scratchBase + 320L) + 8L))
    return KernelStep.InspectFrontFace
}
