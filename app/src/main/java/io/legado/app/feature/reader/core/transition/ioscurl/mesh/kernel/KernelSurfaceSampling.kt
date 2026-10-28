// Generated curl mesh algorithm stages.
@file:Suppress("UNUSED_VARIABLE", "UNUSED_VALUE", "NAME_SHADOWING", "RedundantConversion")

package io.legado.app.feature.reader.core.transition.ioscurl.mesh.kernel

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import io.legado.app.feature.reader.core.transition.ioscurl.math.BinaryMath.fma

// Reference step 41: fallthrough41
internal fun CurlMeshKernel.prepareSurfaceStrips(): KernelStep {
    surfaceIndex = 0L
    surfaceValue = readDouble((scratchBase + 456L))
    surfaceValue2 = readDouble(((scratchBase + 456L) + 8L))
    surfaceValue3 = (surfaceValue2 / surfaceValue)
    surfaceValue4 = abs(surfaceValue3)
    surfaceValue5 = 1.0
    surfaceValue6 = (surfaceValue4 + surfaceValue5)
    surfaceValue7 = (surfaceValue5 / surfaceValue6)
    writeDouble((scratchBase + 160L), surfaceValue7)
    surfaceBits = (projectionBits12 and 4294967295L)
    writeLongBits((scratchBase + 216L), surfaceBits)
    surfaceValue8 = readDouble((scratchBase + 672L))
    writeDouble((scratchBase + 280L), surfaceValue8)
    surfaceValue9 = readDouble((scratchBase + 664L))
    surfaceValue10.load(memory, (scratchBase + 112L))
    surfaceColor.unpackHalfColorFrom(surfaceValue10)
    surfaceColor.store(memory, (scratchBase + 256L))
    surfaceValue11.double(0, readDouble((scratchBase + 240L)))
    surfaceColor2.unpackHalfColorFrom(surfaceValue11)
    surfaceColor2.store(memory, (scratchBase + 240L))
    surfaceValue12 = -(1.5000000596046448)
    writeDouble((scratchBase + 200L), surfaceValue12)
    surfaceValue13 = 0.755f.toDouble()
    writeDouble((scratchBase + 192L), surfaceValue13)
    surfaceValue14 = -(0.82f.toDouble())
    writeDouble((scratchBase + 136L), surfaceValue14)
    surfaceValue15.double(0, readDouble((scratchBase + 400L)))
    surfaceValue16.double(0, readDouble((scratchBase + 328L)))
    return KernelStep.SampleSurfaceCrossSection
}

// Reference step 42: L1853449d0
internal fun CurlMeshKernel.sampleSurfaceCrossSection(): KernelStep {
    surfaceAddress = (scratchBase + 2928L)
    surfaceValue17.double(0, readDouble((surfaceAddress + (surfaceIndex shl 3))))
    surfaceValue18 = readDouble((scratchBase + 464L))
    surfaceValue19 = (surfaceValue18 * surfaceValue17.double(0))
    surfaceValue20 = readDouble((scratchBase + 456L))
    surfaceValue21 = (surfaceValue20 * surfaceValue17.double(0))
    surfaceValue22 = readDouble((scratchBase + 568L))
    surfaceValue23.double(0, (surfaceValue22 + surfaceValue19))
    surfaceValue24 = readDouble((scratchBase + 472L))
    surfaceValue25.double(0, (surfaceValue24 + surfaceValue21))
    surfaceAddress2 = (scratchBase + (28L shl 12))
    clipStartAddress = (surfaceAddress2 + 272L)
    surfaceAddress3 = (scratchBase + (7L shl 12))
    clipEndAddress = (surfaceAddress3 + 896L)
    surfaceValue26 = readDouble((scratchBase + 368L))
    surfaceValue27 = readDouble((scratchBase + 344L))
    surfaceValue28 = readDouble((scratchBase + 424L))
    surfaceValue29 = readDouble((scratchBase + 304L))
    surfaceValue30.set(surfaceValue23)
    surfaceValue31.set(surfaceValue25)
    surfaceValue32.set(surfaceValue15)
    surfaceValue33.set(surfaceValue16)
    clipProjectedLine(
        clipStartAddress,
        clipEndAddress,
        surfaceValue26,
        surfaceValue27,
        surfaceValue28,
        surfaceValue29,
        surfaceValue30.double(0),
        surfaceValue31.double(0),
        surfaceValue32.double(0),
        surfaceValue33.double(0),
    )
    surfaceAddress4 = (scratchBase + (21L shl 12))
    clipStartAddress2 = (surfaceAddress4 + 3600L)
    clipEndAddress2 = (scratchBase + (4L shl 12))
    surfaceValue34 = readDouble((scratchBase + 432L))
    surfaceValue35 = readDouble((scratchBase + 312L))
    surfaceValue36 = readDouble((scratchBase + 352L))
    surfaceValue37 = readDouble((scratchBase + 336L))
    surfaceValue38.set(surfaceValue23)
    surfaceValue39.set(surfaceValue25)
    surfaceValue40.double(0, readDouble((scratchBase + 496L)))
    surfaceValue41.set(surfaceValue15)
    surfaceValue42.set(surfaceValue16)
    clipProjectedLine(
        clipStartAddress2,
        clipEndAddress2,
        surfaceValue34,
        surfaceValue35,
        surfaceValue36,
        surfaceValue37,
        surfaceValue38.double(0),
        surfaceValue39.double(0),
        surfaceValue41.double(0),
        surfaceValue42.double(0),
    )
    surfaceAddress5 = (scratchBase + (10L shl 12))
    sineOutputAddress = (surfaceAddress5 + 1808L)
    surfaceValue43.set(surfaceValue17)
    surfaceValue44 = readDouble((scratchBase + 520L))
    surfaceValue45 = readDouble((scratchBase + 504L))
    surfaceValue46 = readDouble(((scratchBase + 504L) + 8L))
    surfaceValue47.set(surfaceValue40)
    surfaceValue48 =
        curlDisplacement(
            surfaceValue43.double(0),
            surfaceValue44,
            surfaceValue46,
            sineOutputAddress,
            surfaceValue47.double(0),
            surfaceValue45,
        )
    surfaceValue49 = (surfaceValue48).toDouble()
    surfaceValue50 = (surfaceValue17.double(0) + surfaceValue49)
    surfaceAddress6 = (scratchBase + (10L shl 12))
    surfaceAddress7 = (surfaceAddress6 + 1792L)
    surfaceValue51 = readDouble((surfaceAddress7 + 16L))
    surfaceAddress8 = (readUnsignedWord((scratchBase + 440L)) and KernelLayout.WORD_MASK)
    return if (((surfaceAddress8 and KernelLayout.WORD_MASK) == 0L))
        KernelStep.UseUnlitSurfaceCrossSection
    else KernelStep.SelectSurfaceLighting
}

// Reference step 43: fallthrough43
internal fun CurlMeshKernel.selectSurfaceLighting(): KernelStep {
    surfaceValue52 = readDouble((scratchBase + 200L))
    surfaceValue53 = 2.5
    surfaceValue54 = fma(surfaceValue51, surfaceValue53, surfaceValue52)
    surfaceValue55 = 1.0
    surfaceValue56 = min(surfaceValue54, surfaceValue55)
    surfaceValue57.longBits(0, 0L)
    surfaceValue58 = max(surfaceValue56, surfaceValue57.double(0))
    writeDouble((surfaceAddress7 + 16L), surfaceValue58)
    surfaceValue59 = readDouble((scratchBase + 192L))
    compareFloatingPoint(surfaceValue58, surfaceValue59)
    surfaceValue60 = readDouble((scratchBase + 528L))
    return if ((zeroResult || (negativeResult != arithmeticOverflow)))
        KernelStep.UseCurvedSurfaceLighting
    else KernelStep.UseLinearSurfaceLighting
}

// Reference step 44: fallthrough44
internal fun CurlMeshKernel.useLinearSurfaceLighting(): KernelStep {
    surfaceValue61 = readDouble((scratchBase + 136L))
    surfaceValue62 = (surfaceValue58 + surfaceValue61)
    surfaceValue58 = (surfaceValue62 * surfaceValue62)
    surfaceValue63 = 0.4f.toDouble()
    surfaceValue64 = 15.0
    return KernelStep.EvaluateSurfaceLighting
}

// Reference step 45: L185344ae4
internal fun CurlMeshKernel.useUnlitSurfaceCrossSection(): KernelStep {
    surfaceValue60 = readDouble((scratchBase + 528L))
    return KernelStep.ProjectSurfaceCrossSection
}

// Reference step 46: L185344aec
internal fun CurlMeshKernel.useCurvedSurfaceLighting(): KernelStep {
    surfaceValue65 = (surfaceValue58 * surfaceValue58)
    surfaceValue66 = 0.375
    surfaceValue63 = (surfaceValue65 * surfaceValue66)
    surfaceValue64 = 0.35f.toDouble()
    return KernelStep.EvaluateSurfaceLighting
}

// Reference step 47: L185344b00
internal fun CurlMeshKernel.evaluateSurfaceLighting(): KernelStep {
    surfaceValue51 = fma(surfaceValue58, surfaceValue64, surfaceValue63)
    writeDouble((surfaceAddress7 + 16L), surfaceValue51)
    return KernelStep.ProjectSurfaceCrossSection
}

// Reference step 48: L185344b08
internal fun CurlMeshKernel.projectSurfaceCrossSection(): KernelStep {
    projectionValue104.double(0, readDouble((scratchBase + 488L)))
    surfaceValue67 = readDouble((scratchBase + 392L))
    surfaceValue68 = (surfaceValue67 + surfaceValue19)
    surfaceValue69 = readDouble((scratchBase + 320L))
    surfaceValue70 = (surfaceValue69 + surfaceValue21)
    surfaceValue71 = readDouble((scratchBase + 536L))
    surfaceValue72 = fma(surfaceValue71, surfaceValue50, surfaceValue9)
    surfaceValue73 = readDouble((scratchBase + 280L))
    surfaceValue74 = fma(surfaceValue60, surfaceValue50, surfaceValue73)
    surfaceValue75.float(0, (surfaceValue51).toFloat())
    surfaceValue76 = (1.0).toFloat()
    surfaceValue77.float(0, (surfaceValue76 - surfaceValue75.float(0)))
    surfaceValue78.load(memory, (scratchBase + 240L))
    run {
        var ta = surfaceValue78.copy()
        var tb = surfaceValue75.copy()
        var tc = surfaceValue75.copy()
        for (lane in 0 until 4) {
            surfaceValue79.float(lane, (ta.float(lane) * tb.float(0)))
        }
    }
    surfaceValue80.load(memory, (scratchBase + 256L))
    run {
        var ta = surfaceValue80.copy()
        var tb = surfaceValue77.copy()
        var tc = surfaceValue79.copy()
        for (lane in 0 until 4) {
            surfaceValue81.float(lane, fma(ta.float(lane), tb.float(0), tc.float(lane)))
        }
    }
    surfaceColor3.packHalfColorFrom(surfaceValue81)
    surfaceIndex2 = surfaceColor3.longBits(0)
    surfaceAddress9 = readLongBits((scratchBase + 288L))
    updateSubtractionFlags(surfaceIndex, surfaceAddress9, 64L)
    return if (!(unsignedCarry)) KernelStep.RecordSurfaceShadowSample
    else KernelStep.CheckSurfaceShadowRange
}

// Reference step 49: fallthrough49
internal fun CurlMeshKernel.checkSurfaceShadowRange(): KernelStep {
    surfaceAddress10 = readLongBits((scratchBase + 216L))
    updateSubtractionFlags(surfaceIndex, surfaceAddress10, 64L)
    return if ((!(unsignedCarry) || zeroResult)) KernelStep.AppendSurfaceStrip
    else KernelStep.RecordSurfaceShadowSample
}

// Reference step 50: L185344b68
internal fun CurlMeshKernel.recordSurfaceShadowSample(): KernelStep {
    surfaceAddress11 = (scratchBase + (21L shl 12))
    surfaceAddress12 = (surfaceAddress11 + 2832L)
    surfaceValue82 = readDouble((surfaceAddress12 + 768L))
    surfaceValue83 = readDouble((scratchBase + 16384L))
    surfaceValue84 = (surfaceValue83 - surfaceValue82)
    surfaceValue85 = readDouble((scratchBase + 160L))
    surfaceValue86 = fma(surfaceValue85, surfaceValue84, surfaceValue82)
    writeDouble((scratchBase + 29568L), surfaceValue86)
    writeDouble((surfaceAddress12 + 26112L), surfaceValue86)
    return KernelStep.AppendSurfaceStrip
}

// Reference step 51: L185344b8c
internal fun CurlMeshKernel.appendSurfaceStrip(): KernelStep {
    updateSubtractionFlags(surfaceIndex, 0L, 64L)
    surfaceIndex3 = ((if (zeroResult) 1L else 0L) and KernelLayout.WORD_MASK)
    updateSubtractionFlags(projectionIndex24, surfaceIndex, 64L)
    surfaceIndex4 = ((if (zeroResult) 1L else 0L) and KernelLayout.WORD_MASK)
    surfaceBits2 =
        (((surfaceIndex3 and KernelLayout.WORD_MASK) or
            (surfaceIndex4 and KernelLayout.WORD_MASK)) and KernelLayout.WORD_MASK)
    updateSubtractionFlags((surfaceBits2 and KernelLayout.WORD_MASK), 0L, 32L)
    surfaceIndex5 = (if (!(zeroResult)) 0L else surfaceIndex2)
    surfaceAddress13 = readLongBits((scratchBase + 416L))
    surfaceIndex6 = (if (!(zeroResult)) 0L else surfaceAddress13)
    updateSubtractionFlags(surfaceIndex, projectionAddress14, 64L)
    surfaceBits3 = (if (!(unsignedCarry)) surfaceIndex6 else surfaceIndex5)
    writeLongBits((scratchBase + 656L), surfaceBits3)
    surfaceAddress14 = (scratchBase + (21L shl 12))
    surfaceAddress15 = (surfaceAddress14 + 2832L)
    surfaceValue87 = readDouble((surfaceAddress15 + 768L))
    surfaceValue88 = (surfaceValue87).toFloat()
    surfaceValue89 = readDouble((scratchBase + 16384L))
    surfaceValue90 = (surfaceValue89).toFloat()
    surfaceValue91 = readDouble((surfaceAddress15 + 26112L))
    surfaceValue92 = (surfaceValue91).toFloat()
    surfaceValue93 = readDouble((scratchBase + 29568L))
    surfaceValue94 = (surfaceValue93).toFloat()
    writeFloat((scratchBase + 8L), surfaceValue92)
    writeFloat(((scratchBase + 8L) + 4L), surfaceValue94)
    surfaceAddress16 = (scratchBase + (39L shl 12))
    centerStripAddress = (surfaceAddress16 + 2320L)
    surfaceAddress17 = (scratchBase + (38L shl 12))
    lowerStripAddress = (surfaceAddress17 + 2064L)
    surfaceAddress18 = (scratchBase + (37L shl 12))
    upperStripAddress = (surfaceAddress18 + 1808L)
    packedColorAddress = (scratchBase + 656L)
    writeFloat(scratchBase, surfaceValue88)
    writeFloat((scratchBase + 4L), surfaceValue90)
    surfaceValue95.set(projectionValue104)
    projectionValue128.double(0, readDouble((scratchBase + 480L)))
    surfaceValue96.set(projectionValue128)
    surfaceValue97 = readDouble((scratchBase + 560L))
    surfaceValue98 = readDouble((scratchBase + 544L))
    stripIndex = surfaceIndex
    appendSurfaceStrip(
        centerStripAddress,
        lowerStripAddress,
        upperStripAddress,
        stripIndex,
        packedColorAddress,
        surfaceValue72,
        surfaceValue74,
        surfaceValue68,
        surfaceValue70,
        surfaceValue95.double(0),
        surfaceValue96.double(0),
        surfaceValue97,
        surfaceValue98,
        scratchBase,
    )
    surfaceIndex = (surfaceIndex + 1L)
    surfaceAddress19 = readLongBits((scratchBase + 296L))
    updateSubtractionFlags(surfaceAddress19, surfaceIndex, 64L)
    return if (!(zeroResult)) KernelStep.SampleSurfaceCrossSection
    else KernelStep.PrepareBackStripIndices
}

// Reference step 52: L185344c34
internal fun CurlMeshKernel.prepareBackStripIndices(): KernelStep {
    surfaceAddress20 = (readUnsignedWord((scratchBase + 68L)) and KernelLayout.WORD_MASK)
    surfaceIndex7 =
        (((projectionBits13 and KernelLayout.WORD_MASK) and
            ((((surfaceAddress20 and KernelLayout.WORD_MASK)).toInt().toLong() shr 31)).inv()) and
            KernelLayout.WORD_MASK)
    writeWord((scratchBase + 280L), (surfaceIndex7 and KernelLayout.WORD_MASK))
    surfaceAddress21 = readLongBits((scratchBase + 184L))
    surfaceAddress22 = (readUnsignedByte((surfaceAddress21 + 1L)) and KernelLayout.WORD_MASK)
    surfaceValue99.double(0, readDouble((scratchBase + 208L)))
    surfaceValue100.double(0, readDouble((scratchBase + 504L)))
    surfaceValue101.double(0, readDouble(((scratchBase + 504L) + 8L)))
    surfaceValue102 = readDouble((scratchBase + 448L))
    surfaceValue103 = readDouble((scratchBase + 72L))
    return if (((surfaceAddress22 and KernelLayout.WORD_MASK) == 0L))
        KernelStep.PrepareShadowBreakpoints
    else KernelStep.PrepareBackIndexReversal
}

// Reference step 53: fallthrough53
internal fun CurlMeshKernel.prepareBackIndexReversal(): KernelStep {
    writeWord((scratchBase + 568L), (surfaceAddress22 and KernelLayout.WORD_MASK))
    surfaceAddress23 = (scratchBase + (36L shl 12))
    surfaceAddress24 = (surfaceAddress23 + 1552L)
    surfaceAddress25 = readLongBits((scratchBase + 104L))
    surfaceBits4 = (surfaceAddress24 + surfaceAddress25)
    surfaceAddress26 = (scratchBase + (39L shl 12))
    surfaceAddress27 = (surfaceAddress26 + 2320L)
    surfaceBits5 = (surfaceAddress27 + surfaceAddress25)
    surfaceAddress28 = (readUnsignedWord((scratchBase + 280L)) and KernelLayout.WORD_MASK)
    surfaceIndex8 =
        (((surfaceAddress28 and KernelLayout.WORD_MASK) shl 1) and KernelLayout.WORD_MASK)
    surfaceBits6 = ((surfaceIndex8).toInt().toLong() shl 6)
    memory.copy(surfaceBits4, surfaceBits5, surfaceBits6)
    updateSubtractionFlags((surfaceAddress28 and KernelLayout.WORD_MASK), 1L, 32L)
    return if ((negativeResult != arithmeticOverflow)) KernelStep.FinishBackIndexReversal
    else KernelStep.BeginBackIndexReversal
}

// Reference step 54: fallthrough54
internal fun CurlMeshKernel.beginBackIndexReversal(): KernelStep {
    surfaceIndex9 = 0L
    surfaceAddress29 = (readUnsignedWord((scratchBase + 280L)) and KernelLayout.WORD_MASK)
    surfaceBits7 = ((surfaceAddress29 and KernelLayout.WORD_MASK) and KernelLayout.WORD_MASK)
    return KernelStep.ReverseBackStripIndices
}

// Reference step 55: L185344ca0
internal fun CurlMeshKernel.reverseBackStripIndices(): KernelStep {
    surfaceIndex10 =
        (((projectionIndex19 and KernelLayout.WORD_MASK) - 1L) and KernelLayout.WORD_MASK)
    surfaceBits8 = (surfaceAddress24 + (surfaceIndex10 shl 6))
    writeDouble((surfaceBits8 + 40L), surfaceValue103)
    surfaceBits9 = ((projectionIndex19 and KernelLayout.WORD_MASK) and KernelLayout.WORD_MASK)
    surfaceBits10 = (surfaceAddress24 + (surfaceBits9 shl 6))
    writeDouble((surfaceBits10 + 40L), surfaceValue103)
    surfaceIndex9 = (surfaceIndex9 + 1L)
    projectionIndex19 =
        (((projectionIndex19 and KernelLayout.WORD_MASK) + 2L) and KernelLayout.WORD_MASK)
    updateSubtractionFlags(surfaceIndex9, surfaceBits7, 64L)
    return if (!(unsignedCarry)) KernelStep.ReverseBackStripIndices
    else KernelStep.FinishBackIndexReversal
}

// Reference step 56: L185344cc8
internal fun CurlMeshKernel.finishBackIndexReversal(): KernelStep {
    surfaceValue104.load(memory, (scratchBase + 112L))
    surfaceColor4 = surfaceValue104.halfBits(3)
    surfaceColor5 = halfFloat(surfaceColor4)
    surfaceColor6 = 0L
    surfaceColor7 = halfFloat(surfaceColor6)
    compareFloatingPoint((surfaceColor5).toDouble(), (surfaceColor7).toDouble())
    surfaceAddress22 = (readUnsignedWord((scratchBase + 568L)) and KernelLayout.WORD_MASK)
    return if (!(zeroResult)) KernelStep.PrepareShadowBreakpoints
    else KernelStep.PrepareBackStripColor
}

// Reference step 57: fallthrough57
internal fun CurlMeshKernel.prepareBackStripColor(): KernelStep {
    surfaceAddress30 = (scratchBase + (35L shl 12))
    surfaceAddress31 = (surfaceAddress30 + 1296L)
    surfaceAddress32 = readLongBits((scratchBase + 104L))
    surfaceBits11 = (surfaceAddress31 + surfaceAddress32)
    surfaceAddress33 = (scratchBase + (38L shl 12))
    surfaceAddress34 = (surfaceAddress33 + 2064L)
    surfaceBits12 = (surfaceAddress34 + surfaceAddress32)
    surfaceAddress35 = readLongBits((scratchBase + 176L))
    surfaceBits13 = ((surfaceAddress35).toInt().toLong() shl 6)
    surfaceBits14 = surfaceBits13
    memory.copy(surfaceBits11, surfaceBits12, surfaceBits14)
    surfaceAddress36 = (scratchBase + (34L shl 12))
    surfaceAddress37 = (surfaceAddress36 + 1040L)
    surfaceBits15 = (surfaceAddress37 + surfaceAddress32)
    surfaceAddress38 = (scratchBase + (37L shl 12))
    surfaceAddress39 = (surfaceAddress38 + 1808L)
    surfaceBits16 = (surfaceAddress39 + surfaceAddress32)
    surfaceBits17 = surfaceBits13
    memory.copy(surfaceBits15, surfaceBits16, surfaceBits17)
    updateSubtractionFlags(
        (projectionIndex8 and KernelLayout.WORD_MASK),
        (projectionAddress14 and KernelLayout.WORD_MASK),
        32L,
    )
    return if ((zeroResult || (negativeResult != arithmeticOverflow)))
        KernelStep.PrepareShadowBreakpoints
    else KernelStep.CopyBackStripColor
}

// Reference step 58: L185344d40
internal fun CurlMeshKernel.copyBackStripColor(): KernelStep {
    writeDouble(projectionAddress17, surfaceValue103)
    projectionAddress17 = (projectionAddress17 + 128L)
    writeDouble(projectionAddress20, surfaceValue103)
    projectionAddress20 = (projectionAddress20 + 128L)
    updateSubtractionFlags(projectionBits8, 1L, 64L)
    projectionBits8 = (projectionBits8 - 1L)
    return if (!(zeroResult)) KernelStep.CopyBackStripColor else KernelStep.PrepareShadowBreakpoints
}
