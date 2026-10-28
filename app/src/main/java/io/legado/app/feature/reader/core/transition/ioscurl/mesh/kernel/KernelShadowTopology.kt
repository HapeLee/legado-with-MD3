// Generated curl mesh algorithm stages.
@file:Suppress("UNUSED_VARIABLE", "UNUSED_VALUE", "NAME_SHADOWING", "RedundantConversion")

package io.legado.app.feature.reader.core.transition.ioscurl.mesh.kernel

import io.legado.app.feature.reader.core.transition.ioscurl.math.BinaryMath.fma

// Reference step 59: L185344d50
internal fun CurlMeshKernel.prepareShadowBreakpoints(): KernelStep {
    shadowAddress = readLongBits((scratchBase + 96L))
    shadowValue = readDouble((shadowAddress + 72L))
    shadowValue2 = readDouble(((shadowAddress + 72L) + 8L))
    shadowValue3 = readDouble((shadowAddress + 88L))
    shadowValue4 = readDouble(((shadowAddress + 88L) + 8L))
    shadowValue5 = (shadowValue + shadowValue3)
    shadowValue6 = (shadowValue2 + shadowValue4)
    compareFloatingPoint(projectionValue104.double(0), 0.0)
    shadowValue7 = (if (negativeResult) shadowValue2 else shadowValue6)
    shadowValue8 = (if (negativeResult) shadowValue6 else shadowValue2)
    shadowValue9 = (if (!(negativeResult)) shadowValue else shadowValue5)
    shadowValue10 = (if (!(negativeResult)) shadowValue5 else shadowValue)
    compareFloatingPoint(projectionValue128.double(0), 0.0)
    shadowValue11 =
        (if ((!(zeroResult) && (negativeResult == arithmeticOverflow))) shadowValue
        else shadowValue5)
    shadowValue12 =
        (if ((!(zeroResult) && (negativeResult == arithmeticOverflow))) shadowValue5
        else shadowValue)
    shadowValue13 =
        (if ((!(zeroResult) && (negativeResult == arithmeticOverflow))) shadowValue2
        else shadowValue6)
    shadowValue14 =
        (if ((!(zeroResult) && (negativeResult == arithmeticOverflow))) shadowValue6
        else shadowValue2)
    writeDouble((scratchBase + 456L), shadowValue10)
    writeDouble(((scratchBase + 456L) + 8L), shadowValue9)
    writeDouble((scratchBase + 432L), shadowValue14)
    writeDouble(((scratchBase + 432L) + 8L), shadowValue13)
    return if (((surfaceAddress22 and KernelLayout.WORD_MASK) == 0L)) KernelStep.SkipDisabledShadow
    else KernelStep.InspectShadowStart
}

// Reference step 60: fallthrough60
internal fun CurlMeshKernel.inspectShadowStart(): KernelStep {
    shadowValue15 = readDouble((scratchBase + 672L))
    shadowValue16.double(0, readDouble((scratchBase + 664L)))
    shadowValue17 = (shadowValue11 - shadowValue16.double(0))
    shadowValue18 = (shadowValue7 - shadowValue15)
    shadowValue19 = readDouble((scratchBase + 528L))
    shadowValue20 = (shadowValue19 * shadowValue18)
    shadowValue21 = readDouble((scratchBase + 536L))
    shadowValue22.double(0, fma(shadowValue21, shadowValue17, shadowValue20))
    shadowValue23.double(0, readDouble((scratchBase + 520L)))
    compareFloatingPoint(shadowValue23.double(0), shadowValue22.double(0))
    return if ((zeroResult || (negativeResult != arithmeticOverflow))) KernelStep.SkipEmptyShadow
    else KernelStep.BeginShadowStartSamples
}

// Reference step 61: fallthrough61
internal fun CurlMeshKernel.beginShadowStartSamples(): KernelStep {
    shadowIndex = 0L
    shadowValue24 = readDouble((scratchBase + 16L))
    shadowValue25 = readDouble(((scratchBase + 16L) + 8L))
    shadowValue26 = (surfaceValue101.double(0) * shadowValue24)
    shadowValue27 = 0.02
    shadowValue28 = (shadowValue26 * shadowValue27)
    shadowValue29 = (surfaceValue102 - surfaceValue100.double(0))
    shadowValue30 = fma(surfaceValue101.double(0), shadowValue29, shadowValue23.double(0))
    compareFloatingPoint(shadowValue22.double(0), shadowValue30)
    shadowValue31.double(
        0,
        (if ((!(zeroResult) && (negativeResult == arithmeticOverflow))) shadowValue22.double(0)
        else shadowValue30),
    )
    shadowAddress2 = (scratchBase + 680L)
    shadowAddress3 = (shadowAddress2 + 8L)
    return KernelStep.InspectShadowStartSample
}

// Reference step 62: L185344df4
internal fun CurlMeshKernel.inspectShadowStartSample(): KernelStep {
    shadowBits = shadowIndex
    shadowBits2 = shadowAddress3
    shadowValue32 = (((shadowBits and KernelLayout.WORD_MASK)).toInt().toLong()).toFloat()
    shadowValue33 = (shadowValue32).toDouble()
    shadowValue34.double(0, fma(-(shadowValue28), shadowValue33, shadowValue25))
    shadowIndex = (shadowIndex + 1L)
    compareFloatingPoint(shadowValue34.double(0), shadowValue31.double(0))
    return if ((!(unsignedCarry) || zeroResult)) KernelStep.FinishShadowStartRange
    else KernelStep.AppendShadowStartSample
}

// Reference step 63: fallthrough63
internal fun CurlMeshKernel.appendShadowStartSample(): KernelStep {
    writeDouble((shadowAddress2 + (shadowBits shl 3)), shadowValue34.double(0))
    shadowAddress3 = (shadowBits2 + 8L)
    updateSubtractionFlags((shadowIndex and KernelLayout.WORD_MASK), 50L, 32L)
    return if (!(unsignedCarry)) KernelStep.InspectShadowStartSample
    else KernelStep.FinishShadowStartSamples
}

// Reference step 64: fallthrough64
internal fun CurlMeshKernel.finishShadowStartSamples(): KernelStep {
    return KernelStep.InspectShadowEnd
}

// Reference step 65: L185344e28
internal fun CurlMeshKernel.skipDisabledShadow(): KernelStep {
    writeLongBits((scratchBase + 560L), 0L)
    writeWord((scratchBase + 544L), 0L)
    writeLongBits((scratchBase + 568L), 0L)
    writeLongBits((scratchBase + 288L), 0L)
    shadowValue35 = 1.0
    shadowValue23.double(0, readDouble((scratchBase + 520L)))
    return KernelStep.InspectFrontFace
}

// Reference step 66: L185344e44
internal fun CurlMeshKernel.skipEmptyShadow(): KernelStep {
    writeLongBits((scratchBase + 560L), 0L)
    writeWord((scratchBase + 544L), 0L)
    writeLongBits((scratchBase + 568L), 0L)
    writeLongBits((scratchBase + 288L), 0L)
    shadowValue35 = 1.0
    return KernelStep.InspectFrontFace
}

// Reference step 67: L185344e5c
internal fun CurlMeshKernel.finishShadowStartRange(): KernelStep {
    shadowAddress4 = (scratchBase + 680L)
    writeDouble((shadowAddress4 + (shadowBits shl 3)), shadowValue31.double(0))
    shadowValue34.set(shadowValue31)
    return KernelStep.InspectShadowEnd
}

// Reference step 68: L185344e68
internal fun CurlMeshKernel.inspectShadowEnd(): KernelStep {
    shadowValue36 = (shadowValue34.double(0) - shadowValue22.double(0))
    shadowValue37 = 0.1
    shadowValue38 = (shadowValue36 * shadowValue37)
    compareFloatingPoint(shadowValue38, 0.0)
    writeDouble((scratchBase + 344L), shadowValue8)
    return if ((zeroResult || (negativeResult != arithmeticOverflow))) KernelStep.RetainShadowEnd
    else KernelStep.BeginShadowEndSamples
}

// Reference step 69: fallthrough69
internal fun CurlMeshKernel.beginShadowEndSamples(): KernelStep {
    shadowIndex2 = ((0L or 2L) and KernelLayout.WORD_MASK)
    return KernelStep.InspectShadowEndSample
}

// Reference step 70: L185344e88
internal fun CurlMeshKernel.inspectShadowEndSample(): KernelStep {
    shadowBits3 = shadowIndex2
    shadowIndex3 = (((shadowBits3 and KernelLayout.WORD_MASK) - 1L) and KernelLayout.WORD_MASK)
    shadowValue39 = (((shadowIndex3 and KernelLayout.WORD_MASK)).toInt().toLong()).toFloat()
    shadowValue40 = (shadowValue39).toDouble()
    shadowValue41.double(0, fma(-(shadowValue38), shadowValue40, shadowValue34.double(0)))
    compareFloatingPoint(shadowValue41.double(0), shadowValue22.double(0))
    return if ((!(unsignedCarry) || zeroResult)) KernelStep.ClampShadowEnd
    else KernelStep.AppendShadowEndSample
}

// Reference step 71: fallthrough71
internal fun CurlMeshKernel.appendShadowEndSample(): KernelStep {
    writeDouble(shadowBits2, shadowValue41.double(0))
    shadowBits2 = (shadowBits2 + 8L)
    shadowIndex2 = (shadowBits3 + 1L)
    shadowIndex4 = (((shadowIndex2 and KernelLayout.WORD_MASK) - 1L) and KernelLayout.WORD_MASK)
    updateSubtractionFlags((shadowIndex4 and KernelLayout.WORD_MASK), 11L, 32L)
    return if (!(unsignedCarry)) KernelStep.InspectShadowEndSample
    else KernelStep.FinishShadowEndSamples
}

// Reference step 72: fallthrough72
internal fun CurlMeshKernel.finishShadowEndSamples(): KernelStep {
    shadowIndex = (shadowBits + shadowBits3)
    return KernelStep.PrepareShadowEdgeIntersections
}

// Reference step 73: L185344ec0
internal fun CurlMeshKernel.retainShadowEnd(): KernelStep {
    shadowValue41.set(shadowValue34)
    return KernelStep.PrepareShadowEdgeIntersections
}

// Reference step 74: L185344ec8
internal fun CurlMeshKernel.clampShadowEnd(): KernelStep {
    writeDouble(shadowBits2, shadowValue22.double(0))
    shadowIndex = (shadowBits + shadowBits3)
    shadowValue41.set(shadowValue22)
    return KernelStep.PrepareShadowEdgeIntersections
}

// Reference step 75: L185344ed4
internal fun CurlMeshKernel.prepareShadowEdgeIntersections(): KernelStep {
    shadowBits4 = ((shadowIndex).toInt().toLong() shl 3)
    shadowAddress5 = (scratchBase + 680L)
    writeDouble((shadowAddress5 + shadowBits4), shadowValue41.double(0))
    shadowValue42 = (shadowValue9 - shadowValue16.double(0))
    shadowValue43 = (shadowValue13 - shadowValue15)
    shadowValue44 = readDouble((scratchBase + 528L))
    shadowValue45 = (shadowValue44 * shadowValue43)
    shadowValue46 = readDouble((scratchBase + 536L))
    shadowValue47 = fma(shadowValue46, shadowValue42, shadowValue45)
    compareFloatingPoint(shadowValue47, shadowValue22.double(0))
    if ((!(zeroResult) && (negativeResult == arithmeticOverflow))) {
        run {
            compareFloatingPoint(shadowValue47, shadowValue25)
        }
    } else {
        run {
            negativeResult = (0L != 0L)
            zeroResult = (0L != 0L)
            unsignedCarry = (0L != 0L)
            arithmeticOverflow = (0L != 0L)
        }
    }
    writeDouble((scratchBase + 336L), shadowValue7)
    return if (negativeResult) KernelStep.AppendFirstShadowEdge else KernelStep.UseEmptyShadowEdges
}

// Reference step 76: fallthrough76
internal fun CurlMeshKernel.useEmptyShadowEdges(): KernelStep {
    shadowIndex5 = (0L and KernelLayout.WORD_MASK)
    return KernelStep.InspectSecondShadowEdge
}

// Reference step 77: L185344f10
internal fun CurlMeshKernel.appendFirstShadowEdge(): KernelStep {
    writeDouble((scratchBase + 2320L), shadowValue47)
    shadowIndex5 = ((0L or 1L) and KernelLayout.WORD_MASK)
    return KernelStep.InspectSecondShadowEdge
}

// Reference step 78: L185344f18
internal fun CurlMeshKernel.inspectSecondShadowEdge(): KernelStep {
    shadowValue48 = (shadowValue10 - shadowValue16.double(0))
    shadowValue49 = (shadowValue14 - shadowValue15)
    shadowValue50 = readDouble((scratchBase + 528L))
    shadowValue51 = (shadowValue50 * shadowValue49)
    shadowValue52 = readDouble((scratchBase + 536L))
    shadowValue53 = fma(shadowValue52, shadowValue48, shadowValue51)
    compareFloatingPoint(shadowValue53, shadowValue22.double(0))
    if ((!(zeroResult) && (negativeResult == arithmeticOverflow))) {
        run {
            compareFloatingPoint(shadowValue53, shadowValue25)
        }
    } else {
        run {
            negativeResult = (0L != 0L)
            zeroResult = (0L != 0L)
            unsignedCarry = (0L != 0L)
            arithmeticOverflow = (0L != 0L)
        }
    }
    return if (negativeResult) KernelStep.AppendSecondShadowEdge
    else KernelStep.SelectShadowMergeOrientation
}

// Reference step 79: fallthrough79
internal fun CurlMeshKernel.selectShadowMergeOrientation(): KernelStep {
    shadowIndex6 = (((shadowIndex and KernelLayout.WORD_MASK) shr 31) and KernelLayout.WORD_MASK)
    shadowIndex7 = (((shadowIndex6 and KernelLayout.WORD_MASK) xor 1L) and KernelLayout.WORD_MASK)
    return if ((((shadowIndex and KernelLayout.WORD_MASK) and (1L shl 31)) == 0L))
        KernelStep.PrepareShadowEdgeMerge
    else KernelStep.InspectShadowMergeAvailability
}

// Reference step 80: fallthrough80
internal fun CurlMeshKernel.inspectShadowMergeAvailability(): KernelStep {
    return if (((shadowIndex5 and KernelLayout.WORD_MASK) != 0L)) KernelStep.PrepareShadowEdgeMerge
    else KernelStep.SkipShadowEdgeMerge
}

// Reference step 81: fallthrough81
internal fun CurlMeshKernel.skipShadowEdgeMerge(): KernelStep {
    shadowIndex8 = (0L and KernelLayout.WORD_MASK)
    return KernelStep.InspectShadowSegmentCount
}

// Reference step 82: L185344f54
internal fun CurlMeshKernel.appendSecondShadowEdge(): KernelStep {
    shadowAddress6 = (scratchBase + 2320L)
    writeDouble(
        (shadowAddress6 +
            (((shadowIndex5 and KernelLayout.WORD_MASK) and KernelLayout.WORD_MASK) shl 3)),
        shadowValue53,
    )
    return if (((shadowIndex5 and KernelLayout.WORD_MASK) == 0L)) KernelStep.SelectOneShadowEdge
    else KernelStep.CompareShadowEdges
}

// Reference step 83: fallthrough83
internal fun CurlMeshKernel.compareShadowEdges(): KernelStep {
    shadowValue54 = readDouble((scratchBase + 2328L))
    shadowValue55 = readDouble((scratchBase + 2320L))
    compareFloatingPoint(shadowValue54, shadowValue55)
    return if ((zeroResult || (negativeResult != arithmeticOverflow)))
        KernelStep.SelectTwoShadowEdges
    else KernelStep.SwapShadowEdges
}

// Reference step 84: fallthrough84
internal fun CurlMeshKernel.swapShadowEdges(): KernelStep {
    writeDouble((scratchBase + 2328L), shadowValue55)
    writeDouble((scratchBase + 2320L), shadowValue53)
    return KernelStep.SelectTwoShadowEdges
}

// Reference step 85: L185344f78
internal fun CurlMeshKernel.selectTwoShadowEdges(): KernelStep {
    shadowIndex5 = ((0L or 2L) and KernelLayout.WORD_MASK)
    return KernelStep.FinishShadowMergeOrientation
}

// Reference step 86: L185344f80
internal fun CurlMeshKernel.selectOneShadowEdge(): KernelStep {
    shadowIndex5 = ((0L or 1L) and KernelLayout.WORD_MASK)
    return KernelStep.FinishShadowMergeOrientation
}

// Reference step 87: L185344f84
internal fun CurlMeshKernel.finishShadowMergeOrientation(): KernelStep {
    shadowIndex9 = (((shadowIndex and KernelLayout.WORD_MASK) shr 31) and KernelLayout.WORD_MASK)
    shadowIndex7 = (((shadowIndex9 and KernelLayout.WORD_MASK) xor 1L) and KernelLayout.WORD_MASK)
    return KernelStep.PrepareShadowEdgeMerge
}

// Reference step 88: L185344f8c
internal fun CurlMeshKernel.prepareShadowEdgeMerge(): KernelStep {
    shadowIndex8 = 0L
    shadowIndex10 = (0L and KernelLayout.WORD_MASK)
    shadowIndex11 = (0L and KernelLayout.WORD_MASK)
    shadowAddress7 = (scratchBase + 680L)
    shadowAddress8 = (scratchBase + 3200L)
    shadowAddress9 = (scratchBase + 2320L)
    return KernelStep.InspectShadowMergeSource
}

// Reference step 89: L185344fa4
internal fun CurlMeshKernel.inspectShadowMergeSource(): KernelStep {
    updateSubtractionFlags(
        (shadowIndex10 and KernelLayout.WORD_MASK),
        (shadowIndex5 and KernelLayout.WORD_MASK),
        32L,
    )
    return if (!(zeroResult)) KernelStep.InspectShadowMergeOrder else KernelStep.TakeShadowEdge
}

// Reference step 90: fallthrough90
internal fun CurlMeshKernel.takeShadowEdge(): KernelStep {
    shadowAddress10 =
        readLongBits(
            (shadowAddress7 + (((shadowIndex11 and KernelLayout.WORD_MASK)).toInt().toLong() shl 3))
        )
    return KernelStep.AdvanceShadowEdge
}

// Reference step 91: L185344fb0
internal fun CurlMeshKernel.advanceShadowEdge(): KernelStep {
    shadowIndex11 = (((shadowIndex11 and KernelLayout.WORD_MASK) + 1L) and KernelLayout.WORD_MASK)
    return KernelStep.AppendShadowMergedValue
}

// Reference step 92: L185344fb8
internal fun CurlMeshKernel.inspectShadowMergeOrder(): KernelStep {
    return if ((((shadowIndex7 and KernelLayout.WORD_MASK) and (1L shl 0)) == 0L))
        KernelStep.TakeShadowBoundary
    else KernelStep.CompareShadowMergeValues
}

// Reference step 93: fallthrough93
internal fun CurlMeshKernel.compareShadowMergeValues(): KernelStep {
    shadowValue56.double(
        0,
        readDouble(
            (shadowAddress7 + (((shadowIndex11 and KernelLayout.WORD_MASK)).toInt().toLong() shl 3))
        ),
    )
    shadowValue57.double(
        0,
        readDouble(
            (shadowAddress9 + (((shadowIndex10 and KernelLayout.WORD_MASK)).toInt().toLong() shl 3))
        ),
    )
    compareFloatingPoint(shadowValue56.double(0), shadowValue57.double(0))
    return if ((zeroResult || (negativeResult != arithmeticOverflow)))
        KernelStep.RetainShadowBoundary
    else KernelStep.RetainShadowMergeValue
}

// Reference step 94: fallthrough94
internal fun CurlMeshKernel.retainShadowMergeValue(): KernelStep {
    shadowAddress10 = shadowValue56.longBits(0)
    return KernelStep.AdvanceShadowEdge
}

// Reference step 95: L185344fd4
internal fun CurlMeshKernel.takeShadowBoundary(): KernelStep {
    shadowAddress10 =
        readLongBits(
            (shadowAddress9 + (((shadowIndex10 and KernelLayout.WORD_MASK)).toInt().toLong() shl 3))
        )
    return KernelStep.AdvanceShadowBoundary
}

// Reference step 96: L185344fdc
internal fun CurlMeshKernel.retainShadowBoundary(): KernelStep {
    shadowAddress10 = shadowValue57.longBits(0)
    return KernelStep.AdvanceShadowBoundary
}

// Reference step 97: L185344fe0
internal fun CurlMeshKernel.advanceShadowBoundary(): KernelStep {
    shadowIndex10 = (((shadowIndex10 and KernelLayout.WORD_MASK) + 1L) and KernelLayout.WORD_MASK)
    return KernelStep.AppendShadowMergedValue
}

// Reference step 98: L185344fe4
internal fun CurlMeshKernel.appendShadowMergedValue(): KernelStep {
    writeLongBits((shadowAddress8 + (shadowIndex8 shl 3)), shadowAddress10)
    shadowIndex8 = (shadowIndex8 + 1L)
    updateSubtractionFlags(
        (shadowIndex11 and KernelLayout.WORD_MASK),
        (shadowIndex and KernelLayout.WORD_MASK),
        32L,
    )
    shadowIndex7 =
        ((if ((zeroResult || (negativeResult != arithmeticOverflow))) 1L else 0L) and
            KernelLayout.WORD_MASK)
    return if ((zeroResult || (negativeResult != arithmeticOverflow)))
        KernelStep.InspectShadowMergeSource
    else KernelStep.DrainShadowMerge
}

// Reference step 99: fallthrough99
internal fun CurlMeshKernel.drainShadowMerge(): KernelStep {
    updateSubtractionFlags(
        (shadowIndex10 and KernelLayout.WORD_MASK),
        (shadowIndex5 and KernelLayout.WORD_MASK),
        32L,
    )
    return if ((negativeResult != arithmeticOverflow)) KernelStep.InspectShadowMergeSource
    else KernelStep.InspectShadowSegmentCount
}

// Reference step 100: L185345000
internal fun CurlMeshKernel.inspectShadowSegmentCount(): KernelStep {
    updateSubtractionFlags((shadowIndex8 and KernelLayout.WORD_MASK), 0L, 32L)
    shadowIndex12 =
        ((if ((!(zeroResult) && (negativeResult == arithmeticOverflow))) 1L else 0L) and
            KernelLayout.WORD_MASK)
    return if ((!(zeroResult) && (negativeResult == arithmeticOverflow)))
        KernelStep.InspectShadowSegment
    else KernelStep.PrepareShadowSegmentIndices
}

// Reference step 101: fallthrough101
internal fun CurlMeshKernel.prepareShadowSegmentIndices(): KernelStep {
    updateSubtractionFlags((shadowIndex5 and KernelLayout.WORD_MASK), 1L, 32L)
    return if ((negativeResult == arithmeticOverflow)) KernelStep.InspectShadowSegment
    else KernelStep.BeginShadowSegmentSearch
}

// Reference step 102: fallthrough102
internal fun CurlMeshKernel.beginShadowSegmentSearch(): KernelStep {
    shadowIndex13 = (0L and KernelLayout.WORD_MASK)
    return KernelStep.InspectShadowSegmentClass
}

// Reference step 103: L18534501c
internal fun CurlMeshKernel.inspectShadowSegment(): KernelStep {
    shadowIndex13 = 0L
    shadowIndex14 = (0L and KernelLayout.WORD_MASK)
    shadowIndex15 = (0L and KernelLayout.WORD_MASK)
    shadowAddress11 = (scratchBase + 3200L)
    shadowAddress12 = (scratchBase + (4L shl 12))
    shadowAddress13 = (scratchBase + 2320L)
    shadowAddress14 = (scratchBase + 2512L)
    return KernelStep.AdvanceShadowSegmentSearch
}

// Reference step 104: L185345038
internal fun CurlMeshKernel.advanceShadowSegmentSearch(): KernelStep {
    updateSubtractionFlags(
        (shadowIndex14 and KernelLayout.WORD_MASK),
        (shadowIndex5 and KernelLayout.WORD_MASK),
        32L,
    )
    return if (!(zeroResult)) KernelStep.InspectShadowSegmentEnd
    else KernelStep.UseUnmatchedShadowSegment
}

// Reference step 105: fallthrough105
internal fun CurlMeshKernel.useUnmatchedShadowSegment(): KernelStep {
    shadowAddress15 =
        readLongBits(
            (shadowAddress11 +
                (((shadowIndex15 and KernelLayout.WORD_MASK)).toInt().toLong() shl 3))
        )
    return KernelStep.MatchShadowSegment
}

// Reference step 106: L185345044
internal fun CurlMeshKernel.matchShadowSegment(): KernelStep {
    shadowIndex15 = (((shadowIndex15 and KernelLayout.WORD_MASK) + 1L) and KernelLayout.WORD_MASK)
    return KernelStep.PrepareShadowSegment
}

// Reference step 107: L18534504c
internal fun CurlMeshKernel.inspectShadowSegmentEnd(): KernelStep {
    return if ((((shadowIndex12 and KernelLayout.WORD_MASK) and (1L shl 0)) == 0L))
        KernelStep.ChooseShadowSegmentVertex
    else KernelStep.InspectShadowSegmentOrientation
}

// Reference step 108: fallthrough108
internal fun CurlMeshKernel.inspectShadowSegmentOrientation(): KernelStep {
    shadowValue58.double(
        0,
        readDouble(
            (shadowAddress11 +
                (((shadowIndex15 and KernelLayout.WORD_MASK)).toInt().toLong() shl 3))
        ),
    )
    shadowValue59.double(
        0,
        readDouble(
            (shadowAddress13 +
                (((shadowIndex14 and KernelLayout.WORD_MASK)).toInt().toLong() shl 3))
        ),
    )
    compareFloatingPoint(shadowValue58.double(0), shadowValue59.double(0))
    return if ((zeroResult || (negativeResult != arithmeticOverflow)))
        KernelStep.InspectShadowSegmentCase
    else KernelStep.SelectShadowSegmentOrientation
}

// Reference step 109: fallthrough109
internal fun CurlMeshKernel.selectShadowSegmentOrientation(): KernelStep {
    shadowAddress15 = shadowValue58.longBits(0)
    return KernelStep.MatchShadowSegment
}

// Reference step 110: L185345068
internal fun CurlMeshKernel.chooseShadowSegmentVertex(): KernelStep {
    shadowBits5 = ((shadowIndex14 and KernelLayout.WORD_MASK)).toInt().toLong()
    shadowAddress15 =
        readLongBits(
            (shadowAddress13 +
                (((shadowIndex14 and KernelLayout.WORD_MASK)).toInt().toLong() shl 3))
        )
    return KernelStep.AdvanceShadowSegmentCase
}

// Reference step 111: L185345074
internal fun CurlMeshKernel.inspectShadowSegmentCase(): KernelStep {
    shadowBits5 = ((shadowIndex14 and KernelLayout.WORD_MASK)).toInt().toLong()
    shadowAddress15 = shadowValue59.longBits(0)
    return KernelStep.AdvanceShadowSegmentCase
}

// Reference step 112: L18534507c
internal fun CurlMeshKernel.advanceShadowSegmentCase(): KernelStep {
    writeWord((shadowAddress14 + (shadowBits5 shl 2)), (shadowIndex13 and KernelLayout.WORD_MASK))
    shadowIndex14 = (((shadowIndex14 and KernelLayout.WORD_MASK) + 1L) and KernelLayout.WORD_MASK)
    return KernelStep.PrepareShadowSegment
}

// Reference step 113: L185345084
internal fun CurlMeshKernel.prepareShadowSegment(): KernelStep {
    writeLongBits((shadowAddress12 + (shadowIndex13 shl 3)), shadowAddress15)
    shadowIndex13 = (shadowIndex13 + 1L)
    updateSubtractionFlags(
        (shadowIndex15 and KernelLayout.WORD_MASK),
        (shadowIndex8 and KernelLayout.WORD_MASK),
        32L,
    )
    shadowIndex12 =
        ((if ((negativeResult != arithmeticOverflow)) 1L else 0L) and KernelLayout.WORD_MASK)
    return if ((negativeResult != arithmeticOverflow)) KernelStep.AdvanceShadowSegmentSearch
    else KernelStep.PrepareNextShadowSegment
}

// Reference step 114: fallthrough114
internal fun CurlMeshKernel.prepareNextShadowSegment(): KernelStep {
    updateSubtractionFlags(
        (shadowIndex14 and KernelLayout.WORD_MASK),
        (shadowIndex5 and KernelLayout.WORD_MASK),
        32L,
    )
    return if ((negativeResult != arithmeticOverflow)) KernelStep.AdvanceShadowSegmentSearch
    else KernelStep.InspectShadowSegmentClass
}

// Reference step 115: L1853450a0
internal fun CurlMeshKernel.inspectShadowSegmentClass(): KernelStep {
    shadowBits6 = (((shadowIndex13 and KernelLayout.WORD_MASK) - 2L) and KernelLayout.WORD_MASK)
    shadowAddress16 = (((shadowIndex5 and KernelLayout.WORD_MASK) + 1L) and KernelLayout.WORD_MASK)
    shadowAddress17 = (scratchBase + 2512L)
    writeWord(
        (shadowAddress17 +
            (((shadowIndex5 and KernelLayout.WORD_MASK) and KernelLayout.WORD_MASK) shl 2)),
        (shadowBits6 and KernelLayout.WORD_MASK),
    )
    shadowAddress18 = (scratchBase + 2496L)
    shadowAddress19 = (scratchBase + (4L shl 12))
    shadowIndex16 = ((0L or 1L) and KernelLayout.WORD_MASK)
    shadowIndex17 = ((0L or 2L) and KernelLayout.WORD_MASK)
    shadowIndex18 = ((0L or 4L) and KernelLayout.WORD_MASK)
    writeLongBits((scratchBase + 560L), shadowAddress16)
    return KernelStep.InspectShadowSegmentBoundary
}

// Reference step 116: L1853450c8
internal fun CurlMeshKernel.inspectShadowSegmentBoundary(): KernelStep {
    shadowBits7 = (readUnsignedWord(shadowAddress17)).toInt().toLong()
    shadowValue60 = readDouble((shadowAddress19 + (shadowBits7 shl 3)))
    compareFloatingPoint(shadowValue60, shadowValue41.double(0))
    return if (!(zeroResult)) KernelStep.FinishShadowSegmentIndex
    else KernelStep.AppendShadowSegmentIndex
}

// Reference step 117: fallthrough117
internal fun CurlMeshKernel.appendShadowSegmentIndex(): KernelStep {
    writeWord(shadowAddress18, (shadowIndex16 and KernelLayout.WORD_MASK))
    return KernelStep.AdvanceShadowTerminalSegment
}

// Reference step 118: L1853450e0
internal fun CurlMeshKernel.finishShadowSegmentIndex(): KernelStep {
    compareFloatingPoint(shadowValue60, shadowValue47)
    return if (!(zeroResult)) KernelStep.SelectShadowEndpointVertex
    else KernelStep.InspectShadowEndpointClass
}

// Reference step 119: fallthrough119
internal fun CurlMeshKernel.inspectShadowEndpointClass(): KernelStep {
    writeWord(shadowAddress18, 0L)
    return KernelStep.AdvanceShadowTerminalSegment
}

// Reference step 120: L1853450f0
internal fun CurlMeshKernel.selectShadowEndpointVertex(): KernelStep {
    compareFloatingPoint(shadowValue60, shadowValue53)
    return if (!(zeroResult)) KernelStep.InspectShadowTerminalSegment
    else KernelStep.StoreShadowEndpointVertex
}

// Reference step 121: fallthrough121
internal fun CurlMeshKernel.storeShadowEndpointVertex(): KernelStep {
    writeWord(shadowAddress18, (shadowIndex17 and KernelLayout.WORD_MASK))
    return KernelStep.AdvanceShadowTerminalSegment
}

// Reference step 122: L185345100
internal fun CurlMeshKernel.inspectShadowTerminalSegment(): KernelStep {
    writeWord(shadowAddress18, (shadowIndex18 and KernelLayout.WORD_MASK))
    return KernelStep.AdvanceShadowTerminalSegment
}

// Reference step 123: L185345104
internal fun CurlMeshKernel.advanceShadowTerminalSegment(): KernelStep {
    shadowAddress18 = (shadowAddress18 + 4L)
    shadowAddress17 = (shadowAddress17 + 4L)
    updateSubtractionFlags(shadowAddress16, 1L, 64L)
    shadowAddress16 = (shadowAddress16 - 1L)
    return if (!(zeroResult)) KernelStep.InspectShadowSegmentBoundary
    else KernelStep.FinishShadowTopology
}

// Reference step 124: fallthrough124
internal fun CurlMeshKernel.finishShadowTopology(): KernelStep {
    writeDouble((scratchBase + 320L), shadowValue11)
    writeDouble(((scratchBase + 320L) + 8L), shadowValue12)
    shadowValue61 = readDouble(shadowAddress)
    shadowValue62 = 1.35f.toDouble()
    shadowValue63 = (shadowValue61 * shadowValue62)
    shadowValue64 = 1.0
    compareFloatingPoint(shadowValue63, shadowValue64)
    shadowIndex19 =
        ((if ((!(zeroResult) && (negativeResult == arithmeticOverflow))) 1L else 0L) and
            KernelLayout.WORD_MASK)
    shadowValue65 =
        (if ((!(zeroResult) && (negativeResult == arithmeticOverflow))) shadowValue64
        else shadowValue63)
    shadowValue66 = 0.4f.toDouble()
    compareFloatingPoint(shadowValue65, shadowValue66)
    shadowIndex20 = ((if (negativeResult) 1L else 0L) and KernelLayout.WORD_MASK)
    shadowBits8 =
        (((shadowIndex19 and KernelLayout.WORD_MASK) or
            (shadowIndex20 and KernelLayout.WORD_MASK)) and KernelLayout.WORD_MASK)
    shadowBits9 = CLAMP_STEPS
    shadowValue67 =
        readDouble(
            (shadowBits9 +
                (((shadowIndex20 and KernelLayout.WORD_MASK) and KernelLayout.WORD_MASK) shl 3))
        )
    shadowValue68 = 229.50000405311584
    shadowValue69 = (shadowValue61 * shadowValue68)
    updateSubtractionFlags((shadowBits8 and KernelLayout.WORD_MASK), 0L, 32L)
    shadowValue70 = (if (!(zeroResult)) shadowValue67 else shadowValue69)
    writeDouble((scratchBase + 568L), shadowValue70)
    updateSubtractionFlags((shadowIndex13 and KernelLayout.WORD_MASK), 0L, 32L)
    writeLongBits((scratchBase + 288L), shadowIndex13)
    return if ((zeroResult || (negativeResult != arithmeticOverflow)))
        KernelStep.PrepareEmptyPenumbra
    else KernelStep.PrepareShadowCrossSections
}
