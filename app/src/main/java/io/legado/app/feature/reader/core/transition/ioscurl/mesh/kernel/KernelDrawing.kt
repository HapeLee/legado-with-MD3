// Generated curl mesh algorithm stages.
@file:Suppress("UNUSED_VARIABLE", "UNUSED_VALUE", "NAME_SHADOWING", "RedundantConversion")

package io.legado.app.feature.reader.core.transition.ioscurl.mesh.kernel

// Reference step 215: L1853461c8
internal fun CurlMeshKernel.prepareDrawState(): KernelStep {
    drawStateAddress = readLongBits((renderContextAddress + KernelLayout.Context.DRAW_STATE))
    drawAddress =
        (readUnsignedByte((drawStateAddress + KernelLayout.DrawState.FLAGS)) and
            KernelLayout.WORD_MASK)
    drawIndex = (((drawAddress and KernelLayout.WORD_MASK) or 1L) and KernelLayout.WORD_MASK)
    writeByte(
        (drawStateAddress + KernelLayout.DrawState.FLAGS),
        (drawIndex and KernelLayout.WORD_MASK),
    )
    drawAddress2 = readLongBits((scratchBase + 80L))
    drawAddress3 = readLongBits((drawAddress2 + 24L))
    writeLongBits((renderContextAddress + KernelLayout.Context.ACTIVE_SURFACE), drawAddress3)
    drawAddress4 =
        (readUnsignedWord((renderContextAddress + KernelLayout.Context.FRAME_COUNT)) and
            KernelLayout.WORD_MASK)
    drawAddress5 = (((drawAddress4 and KernelLayout.WORD_MASK) + 1L) and KernelLayout.WORD_MASK)
    writeWord(
        (renderContextAddress + KernelLayout.Context.FRAME_COUNT),
        (drawAddress5 and KernelLayout.WORD_MASK),
    )
    drawStateAddress2 = readLongBits((renderContextAddress + KernelLayout.Context.DRAW_STATE))
    drawIndex2 = ((0L or 3L) and KernelLayout.WORD_MASK)
    writeByte(
        (drawStateAddress2 + KernelLayout.DrawState.PROGRAM),
        (drawIndex2 and KernelLayout.WORD_MASK),
    )
    drawValue = 0L
    drawBits = (9729L and KernelLayout.WORD_MASK)
    drawBits2 = (9729L and KernelLayout.WORD_MASK)
    drawIndex3 = (0L and KernelLayout.WORD_MASK)
    drawIndex4 = (0L and KernelLayout.WORD_MASK)
    drawBits3 = renderContextAddress
    drawAddress6 = readLongBits((scratchBase + 128L))
    outputTransformAddress = readLongBits((scratchBase + 88L))
    return if ((outputTransformAddress == 0L)) KernelStep.BeginClipIteration
    else KernelStep.InspectOutputTransform
}

// Reference step 216: fallthrough216
internal fun CurlMeshKernel.inspectOutputTransform(): KernelStep {
    drawStateAddress3 = readLongBits((renderContextAddress + KernelLayout.Context.DRAW_STATE))
    drawAddress7 = (readUnsignedByte((outputTransformAddress + 144L)) and KernelLayout.WORD_MASK)
    return if ((((drawAddress7 and KernelLayout.WORD_MASK) and (1L shl 4)) != 0L))
        KernelStep.MeasureOutputScale
    else KernelStep.UseAffineOutputScale
}

// Reference step 217: fallthrough217
internal fun CurlMeshKernel.useAffineOutputScale(): KernelStep {
    drawValue2 = readDouble((outputTransformAddress + 128L))
    return KernelStep.ApplyOutputScale
}

// Reference step 218: L185346238
internal fun CurlMeshKernel.measureOutputScale(): KernelStep {
    horizontalScaleAddress = 0L
    verticalScaleAddress = 0L
    drawValue2 =
        measureTransformScale(outputTransformAddress, horizontalScaleAddress, verticalScaleAddress)
    return KernelStep.ApplyOutputScale
}

// Reference step 219: L185346244
internal fun CurlMeshKernel.applyOutputScale(): KernelStep {
    drawValue3 = (shadowValue35 / drawValue2)
    drawValue4.float(0, (drawValue3).toFloat())
    drawValue5.load(memory, (drawStateAddress3 + 124L))
    run {
        var ta = drawValue5.copy()
        var tb = drawValue4.copy()
        var tc = drawValue4.copy()
        for (lane in 0 until 4) {
            drawValue6.float(lane, (ta.float(lane) * tb.float(0)))
        }
    }
    drawValue6.store(memory, (drawStateAddress3 + 124L))
    return KernelStep.BeginClipIteration
}

// Reference step 220: L185346258
internal fun CurlMeshKernel.beginClipIteration(): KernelStep {
    writeLongBits((scratchBase + 680L), renderContextAddress)
    drawAddress8 = readLongBits((renderContextAddress + KernelLayout.Context.LAYER_STATE))
    drawAddress9 = readLongBits((drawAddress8 + 8L))
    writeLongBits((scratchBase + 688L), drawAddress9)
    writeLongBits((scratchBase + 720L), 0L)
    writeLongBits((scratchBase + 704L), 0L)
    writeLongBits((scratchBase + 696L), 0L)
    writeHalfWord((scratchBase + 712L), 0L)
    drawAddress10 = (scratchBase + 680L)
    clipped = 0L
    drawAddress11 = (scratchBase + 680L)
    clipRectangleAddress = (scratchBase + 1504L)
    drawBits4 = nextClipRectangle(clipRectangleAddress)
    return if (((drawBits4 and KernelLayout.WORD_MASK) == 0L)) KernelStep.FinishDrawClips
    else KernelStep.PrepareDrawBuffers
}

// Reference step 221: fallthrough221
internal fun CurlMeshKernel.prepareDrawBuffers(): KernelStep {
    drawAddress12 = readLongBits((scratchBase + 32L))
    drawBits5 = (drawAddress12 and 4294967294L)
    drawAddress13 = (scratchBase + (39L shl 12))
    drawAddress14 = (drawAddress13 + 2320L)
    drawBits6 = (drawAddress14 + (drawBits5 shl 6))
    writeLongBits((scratchBase + 520L), drawBits6)
    drawAddress15 = (readUnsignedWord((scratchBase + 44L)) and KernelLayout.WORD_MASK)
    drawBits7 = (((drawAddress15 and KernelLayout.WORD_MASK) - 2L) and KernelLayout.WORD_MASK)
    writeWord((scratchBase + 512L), (drawBits7 and KernelLayout.WORD_MASK))
    drawAddress16 = (readUnsignedWord((scratchBase + 280L)) and KernelLayout.WORD_MASK)
    drawIndex5 = (((drawAddress16 and KernelLayout.WORD_MASK) shl 1) and KernelLayout.WORD_MASK)
    drawAddress17 = (scratchBase + (36L shl 12))
    drawAddress18 = (drawAddress17 + 1552L)
    drawAddress19 = readLongBits((scratchBase + 104L))
    drawBits8 = (drawAddress18 + drawAddress19)
    writeLongBits((scratchBase + 496L), drawBits8)
    drawAddress20 = (scratchBase + (35L shl 12))
    drawAddress21 = (drawAddress20 + 1296L)
    drawBits9 = (drawAddress21 + drawAddress19)
    writeLongBits((scratchBase + 464L), drawBits9)
    drawAddress22 = (scratchBase + (34L shl 12))
    drawAddress23 = (drawAddress22 + 1040L)
    drawBits10 = (drawAddress23 + drawAddress19)
    writeLongBits((scratchBase + 456L), drawBits10)
    drawBits11 = (drawAddress14 + drawAddress19)
    writeLongBits((scratchBase + 488L), drawBits11)
    drawAddress24 = (scratchBase + (38L shl 12))
    drawAddress25 = (drawAddress24 + 2064L)
    drawBits12 = (drawAddress25 + drawAddress19)
    writeLongBits((scratchBase + 480L), drawBits12)
    drawAddress26 = (scratchBase + (37L shl 12))
    drawAddress27 = (drawAddress26 + 1808L)
    drawBits13 = (drawAddress27 + drawAddress19)
    writeLongBits((scratchBase + 472L), drawBits13)
    drawValue7.load(memory, (scratchBase + 112L))
    drawColor = drawValue7.halfBits(3)
    drawIndex6 = ((0L or 3L) and KernelLayout.WORD_MASK)
    drawColor2 = halfFloat(drawColor)
    drawColor3 = 0L
    drawColor4 = halfFloat(drawColor3)
    drawIndex7 = (11520L and KernelLayout.WORD_MASK)
    drawAddress28 = readLongBits((scratchBase + 568L))
    drawBits14 = ((drawAddress28 and KernelLayout.WORD_MASK) and KernelLayout.WORD_MASK)
    writeLongBits((scratchBase + 536L), drawBits14)
    writeWord((scratchBase + 504L), (drawIndex5 and KernelLayout.WORD_MASK))
    return KernelStep.InspectSurfaceDraw
}

// Reference step 222: L185346348
internal fun CurlMeshKernel.inspectSurfaceDraw(): KernelStep {
    drawBits15 = (readUnsignedByte(surfaceAddress21) and KernelLayout.WORD_MASK)
    return if (((drawBits15 and KernelLayout.WORD_MASK) == 0L)) KernelStep.InspectBackDraw
    else KernelStep.DrawSurfaceStrips
}

// Reference step 223: fallthrough223
internal fun CurlMeshKernel.drawSurfaceStrips(): KernelStep {
    drawStateAddress4 = readLongBits((renderContextAddress + KernelLayout.Context.DRAW_STATE))
    writeByte((drawStateAddress4 + KernelLayout.DrawState.PROGRAM), 0L)
    drawAddress29 = readLongBits(renderContextAddress)
    drawAddress30 = readLongBits((drawAddress29 + 208L))
    primitiveKind = ((0L or 2L) and KernelLayout.WORD_MASK)
    drawAddress31 = (scratchBase + (7L shl 12))
    vertexBufferAddress = (drawAddress31 + 896L)
    drawAddress32 = (readUnsignedWord((scratchBase + 528L)) and KernelLayout.WORD_MASK)
    drawIndexCount = drawAddress32
    vertexBufferOffset = (0L and KernelLayout.WORD_MASK)
    drawVertexCount = drawAddress32
    renderContextAddress2 = renderContextAddress
    indexBufferAddress = 0L
    attributePlanes = 0L
    if (
        (appendDrawBatch(
            renderContextAddress2,
            primitiveKind,
            drawIndexCount,
            indexBufferAddress,
            vertexBufferAddress,
            vertexBufferOffset,
            drawVertexCount,
            attributePlanes,
        ) != 0L)
    ) {
        return KernelStep.Invalid
    }
    drawAddress33 = readLongBits(renderContextAddress)
    drawAddress34 = readLongBits((drawAddress33 + 208L))
    primitiveKind2 = ((0L or 2L) and KernelLayout.WORD_MASK)
    vertexBufferAddress2 = (scratchBase + (4L shl 12))
    drawIndexCount2 = drawAddress32
    vertexBufferOffset2 = (0L and KernelLayout.WORD_MASK)
    drawVertexCount2 = drawAddress32
    renderContextAddress3 = renderContextAddress
    indexBufferAddress2 = 0L
    attributePlanes2 = 0L
    if (
        (appendDrawBatch(
            renderContextAddress3,
            primitiveKind2,
            drawIndexCount2,
            indexBufferAddress2,
            vertexBufferAddress2,
            vertexBufferOffset2,
            drawVertexCount2,
            attributePlanes2,
        ) != 0L)
    ) {
        return KernelStep.Invalid
    }
    drawAddress35 = readLongBits(renderContextAddress)
    drawAddress36 = readLongBits((drawAddress35 + 208L))
    primitiveKind3 = ((0L or 2L) and KernelLayout.WORD_MASK)
    vertexBufferAddress3 = (scratchBase + 3200L)
    drawIndexCount3 = drawAddress32
    vertexBufferOffset3 = (0L and KernelLayout.WORD_MASK)
    drawVertexCount3 = drawAddress32
    renderContextAddress4 = renderContextAddress
    indexBufferAddress3 = 0L
    attributePlanes3 = 0L
    if (
        (appendDrawBatch(
            renderContextAddress4,
            primitiveKind3,
            drawIndexCount3,
            indexBufferAddress3,
            vertexBufferAddress3,
            vertexBufferOffset3,
            drawVertexCount3,
            attributePlanes3,
        ) != 0L)
    ) {
        return KernelStep.Invalid
    }
    drawStateAddress5 = readLongBits((renderContextAddress + KernelLayout.Context.DRAW_STATE))
    writeByte(
        (drawStateAddress5 + KernelLayout.DrawState.PROGRAM),
        (drawIndex6 and KernelLayout.WORD_MASK),
    )
    drawAddress37 = readLongBits(renderContextAddress)
    drawAddress38 = readLongBits((drawAddress37 + 208L))
    primitiveKind4 = ((0L or 2L) and KernelLayout.WORD_MASK)
    drawIndexCount4 = (readUnsignedWord((scratchBase + 172L)) and KernelLayout.WORD_MASK)
    vertexBufferOffset4 = (0L and KernelLayout.WORD_MASK)
    drawVertexCount4 = drawIndexCount4
    renderContextAddress5 = renderContextAddress
    indexBufferAddress4 = 0L
    vertexBufferAddress4 = readLongBits((scratchBase + 520L))
    attributePlanes4 = 0L
    if (
        (appendDrawBatch(
            renderContextAddress5,
            primitiveKind4,
            drawIndexCount4,
            indexBufferAddress4,
            vertexBufferAddress4,
            vertexBufferOffset4,
            drawVertexCount4,
            attributePlanes4,
        ) != 0L)
    ) {
        return KernelStep.Invalid
    }
    drawAddress39 = readLongBits(renderContextAddress)
    drawAddress40 = readLongBits((drawAddress39 + 208L))
    primitiveKind5 = ((0L or 2L) and KernelLayout.WORD_MASK)
    drawAddress41 = (scratchBase + (38L shl 12))
    vertexBufferAddress5 = (drawAddress41 + 2064L)
    drawAddress42 = (readUnsignedWord((scratchBase + 512L)) and KernelLayout.WORD_MASK)
    drawIndexCount5 = drawAddress42
    vertexBufferOffset5 = (0L and KernelLayout.WORD_MASK)
    drawVertexCount5 = drawAddress42
    renderContextAddress6 = renderContextAddress
    indexBufferAddress5 = 0L
    attributePlanes5 = 0L
    if (
        (appendDrawBatch(
            renderContextAddress6,
            primitiveKind5,
            drawIndexCount5,
            indexBufferAddress5,
            vertexBufferAddress5,
            vertexBufferOffset5,
            drawVertexCount5,
            attributePlanes5,
        ) != 0L)
    ) {
        return KernelStep.Invalid
    }
    drawAddress43 = readLongBits(renderContextAddress)
    drawAddress44 = readLongBits((drawAddress43 + 208L))
    primitiveKind6 = ((0L or 2L) and KernelLayout.WORD_MASK)
    drawAddress45 = (scratchBase + (37L shl 12))
    vertexBufferAddress6 = (drawAddress45 + 1808L)
    drawIndexCount6 = drawAddress42
    vertexBufferOffset6 = (0L and KernelLayout.WORD_MASK)
    drawVertexCount6 = drawAddress42
    renderContextAddress7 = renderContextAddress
    indexBufferAddress6 = 0L
    attributePlanes6 = 0L
    if (
        (appendDrawBatch(
            renderContextAddress7,
            primitiveKind6,
            drawIndexCount6,
            indexBufferAddress6,
            vertexBufferAddress6,
            vertexBufferOffset6,
            drawVertexCount6,
            attributePlanes6,
        ) != 0L)
    ) {
        return KernelStep.Invalid
    }
    return KernelStep.InspectBackDraw
}

// Reference step 224: L18534647c
internal fun CurlMeshKernel.inspectBackDraw(): KernelStep {
    drawAddress46 = (readUnsignedByte((surfaceAddress21 + 1L)) and KernelLayout.WORD_MASK)
    return if (((drawAddress46 and KernelLayout.WORD_MASK) == 0L)) KernelStep.AdvanceClipIteration
    else KernelStep.InspectBackShadowDraw
}

// Reference step 225: fallthrough225
internal fun CurlMeshKernel.inspectBackShadowDraw(): KernelStep {
    drawAddress47 = readLongBits((scratchBase + 288L))
    return if (((drawAddress47 and KernelLayout.WORD_MASK) == 0L)) KernelStep.DrawBackFace
    else KernelStep.PrepareShadowDrawLoop
}

// Reference step 226: fallthrough226
internal fun CurlMeshKernel.prepareShadowDrawLoop(): KernelStep {
    drawIndex8 = 0L
    drawStateAddress6 = readLongBits((renderContextAddress + KernelLayout.Context.DRAW_STATE))
    writeByte((drawStateAddress6 + KernelLayout.DrawState.PROGRAM), 0L)
    drawAddress48 = (scratchBase + (10L shl 12))
    drawAddress49 = (drawAddress48 + 1808L)
    return KernelStep.InspectShadowDrawSegment
}

// Reference step 227: L1853464a0
internal fun CurlMeshKernel.inspectShadowDrawSegment(): KernelStep {
    drawAddress50 = readLongBits((scratchBase + 568L))
    updateSubtractionFlags((drawAddress50 and KernelLayout.WORD_MASK), 1L, 32L)
    return if ((negativeResult != arithmeticOverflow)) KernelStep.InspectShadowEndCap
    else KernelStep.PrepareShadowDrawSegment
}

// Reference step 228: fallthrough228
internal fun CurlMeshKernel.prepareShadowDrawSegment(): KernelStep {
    drawBits16 = (drawIndex8 + (drawIndex8 shl 5))
    drawAddress51 = (scratchBase + (21L shl 12))
    drawAddress52 = (drawAddress51 + 3600L)
    drawBits17 = (drawAddress52 + (drawBits16 shl 8))
    drawAddress53 = (scratchBase + (28L shl 12))
    drawAddress54 = (drawAddress53 + 272L)
    drawBits18 = (drawAddress54 + (drawBits16 shl 8))
    drawAddress55 = (scratchBase + (10L shl 12))
    drawAddress56 = (drawAddress55 + 1792L)
    drawAddress57 = readLongBits((scratchBase + 536L))
    return KernelStep.DrawShadowSegment
}

// Reference step 229: L1853464d4
internal fun CurlMeshKernel.drawShadowSegment(): KernelStep {
    drawBits19 = (readUnsignedWord(drawAddress56)).toInt().toLong()
    drawAddress56 = (drawAddress56 + 4L)
    drawIndex9 = (((drawBits19 and KernelLayout.WORD_MASK) shl 1) and KernelLayout.WORD_MASK)
    drawAddress58 = readLongBits(renderContextAddress)
    drawAddress59 = readLongBits((drawAddress58 + 208L))
    primitiveKind7 = ((0L or 2L) and KernelLayout.WORD_MASK)
    drawIndexCount7 = drawIndex9
    vertexBufferOffset7 = (0L and KernelLayout.WORD_MASK)
    drawVertexCount7 = drawIndex9
    renderContextAddress8 = renderContextAddress
    indexBufferAddress7 = 0L
    vertexBufferAddress7 = drawBits18
    attributePlanes7 = 0L
    if (
        (appendDrawBatch(
            renderContextAddress8,
            primitiveKind7,
            drawIndexCount7,
            indexBufferAddress7,
            vertexBufferAddress7,
            vertexBufferOffset7,
            drawVertexCount7,
            attributePlanes7,
        ) != 0L)
    ) {
        return KernelStep.Invalid
    }
    drawAddress60 = readLongBits(renderContextAddress)
    drawAddress61 = readLongBits((drawAddress60 + 208L))
    primitiveKind8 = ((0L or 2L) and KernelLayout.WORD_MASK)
    drawIndexCount8 = drawIndex9
    vertexBufferOffset8 = (0L and KernelLayout.WORD_MASK)
    drawVertexCount8 = drawIndex9
    renderContextAddress9 = renderContextAddress
    indexBufferAddress8 = 0L
    vertexBufferAddress8 = drawBits17
    attributePlanes8 = 0L
    if (
        (appendDrawBatch(
            renderContextAddress9,
            primitiveKind8,
            drawIndexCount8,
            indexBufferAddress8,
            vertexBufferAddress8,
            vertexBufferOffset8,
            drawVertexCount8,
            attributePlanes8,
        ) != 0L)
    ) {
        return KernelStep.Invalid
    }
    drawBits18 = (drawBits18 + (drawBits19 shl 7))
    drawBits17 = (drawBits17 + (drawBits19 shl 7))
    updateSubtractionFlags(drawAddress57, 1L, 64L)
    drawAddress57 = (drawAddress57 - 1L)
    return if (!(zeroResult)) KernelStep.DrawShadowSegment else KernelStep.InspectShadowEndCap
}

// Reference step 230: L185346544
internal fun CurlMeshKernel.inspectShadowEndCap(): KernelStep {
    drawAddress62 = (readUnsignedWord((scratchBase + 544L)) and KernelLayout.WORD_MASK)
    return if ((((drawAddress62 and KernelLayout.WORD_MASK) and (1L shl 0)) == 0L))
        KernelStep.InspectShadowCorner
    else KernelStep.DrawShadowEndCap
}

// Reference step 231: fallthrough231
internal fun CurlMeshKernel.drawShadowEndCap(): KernelStep {
    drawAddress63 = (scratchBase + (21L shl 12))
    drawAddress64 = (drawAddress63 + 2832L)
    vertexBufferAddress9 = (drawAddress64 + (drawIndex8 shl 8))
    drawAddress65 = readLongBits(renderContextAddress)
    drawAddress66 = readLongBits((drawAddress65 + 208L))
    primitiveKind9 = ((0L or 2L) and KernelLayout.WORD_MASK)
    drawIndexCount9 = ((0L or 4L) and KernelLayout.WORD_MASK)
    drawVertexCount9 = ((0L or 4L) and KernelLayout.WORD_MASK)
    vertexBufferOffset9 = (0L and KernelLayout.WORD_MASK)
    renderContextAddress10 = renderContextAddress
    indexBufferAddress9 = 0L
    attributePlanes9 = 0L
    if (
        (appendDrawBatch(
            renderContextAddress10,
            primitiveKind9,
            drawIndexCount9,
            indexBufferAddress9,
            vertexBufferAddress9,
            vertexBufferOffset9,
            drawVertexCount9,
            attributePlanes9,
        ) != 0L)
    ) {
        return KernelStep.Invalid
    }
    return KernelStep.InspectShadowCorner
}

// Reference step 232: L185346580
internal fun CurlMeshKernel.inspectShadowCorner(): KernelStep {
    drawBits20 = drawAddress49
    drawAddress67 = readLongBits((scratchBase + 560L))
    drawBits21 = drawAddress67
    updateSubtractionFlags((drawAddress67 and KernelLayout.WORD_MASK), 1L, 32L)
    return if ((negativeResult != arithmeticOverflow)) KernelStep.AdvanceShadowDrawSegment
    else KernelStep.DrawShadowCorner
}

// Reference step 233: L185346594
internal fun CurlMeshKernel.drawShadowCorner(): KernelStep {
    drawAddress68 = readLongBits(renderContextAddress)
    drawAddress69 = readLongBits((drawAddress68 + 208L))
    primitiveKind10 = ((0L or 2L) and KernelLayout.WORD_MASK)
    drawIndexCount10 = ((0L or 60L) and KernelLayout.WORD_MASK)
    drawVertexCount10 = ((0L or 60L) and KernelLayout.WORD_MASK)
    vertexBufferOffset10 = (0L and KernelLayout.WORD_MASK)
    renderContextAddress11 = renderContextAddress
    indexBufferAddress10 = 0L
    vertexBufferAddress10 = drawBits20
    attributePlanes10 = 0L
    if (
        (appendDrawBatch(
            renderContextAddress11,
            primitiveKind10,
            drawIndexCount10,
            indexBufferAddress10,
            vertexBufferAddress10,
            vertexBufferOffset10,
            drawVertexCount10,
            attributePlanes10,
        ) != 0L)
    ) {
        return KernelStep.Invalid
    }
    drawBits20 = (drawBits20 + drawIndex7)
    updateSubtractionFlags(drawBits21, 1L, 64L)
    drawBits21 = (drawBits21 - 1L)
    return if (!(zeroResult)) KernelStep.DrawShadowCorner else KernelStep.AdvanceShadowDrawSegment
}

// Reference step 234: L1853465cc
internal fun CurlMeshKernel.advanceShadowDrawSegment(): KernelStep {
    drawIndex8 = (drawIndex8 + 1L)
    drawAddress49 = (drawAddress49 + 3840L)
    updateSubtractionFlags(drawIndex8, 3L, 64L)
    return if (!(zeroResult)) KernelStep.InspectShadowDrawSegment else KernelStep.DrawBackFace
}

// Reference step 235: L1853465dc
internal fun CurlMeshKernel.drawBackFace(): KernelStep {
    drawStateAddress7 = readLongBits((renderContextAddress + KernelLayout.Context.DRAW_STATE))
    drawIndex6 = ((0L or 3L) and KernelLayout.WORD_MASK)
    writeByte(
        (drawStateAddress7 + KernelLayout.DrawState.PROGRAM),
        (drawIndex6 and KernelLayout.WORD_MASK),
    )
    drawAddress70 = readLongBits(renderContextAddress)
    drawAddress71 = readLongBits((drawAddress70 + 208L))
    primitiveKind11 = ((0L or 2L) and KernelLayout.WORD_MASK)
    drawAddress72 = (readUnsignedWord((scratchBase + 504L)) and KernelLayout.WORD_MASK)
    drawIndexCount11 = drawAddress72
    vertexBufferOffset11 = (0L and KernelLayout.WORD_MASK)
    drawVertexCount11 = drawAddress72
    renderContextAddress12 = renderContextAddress
    indexBufferAddress11 = 0L
    vertexBufferAddress11 = readLongBits((scratchBase + 496L))
    attributePlanes11 = 0L
    if (
        (appendDrawBatch(
            renderContextAddress12,
            primitiveKind11,
            drawIndexCount11,
            indexBufferAddress11,
            vertexBufferAddress11,
            vertexBufferOffset11,
            drawVertexCount11,
            attributePlanes11,
        ) != 0L)
    ) {
        return KernelStep.Invalid
    }
    compareFloatingPoint((drawColor2).toDouble(), (drawColor4).toDouble())
    drawAddress73 = readLongBits((scratchBase + 176L))
    return if (!(zeroResult)) KernelStep.DrawBackFaceEdges else KernelStep.DrawBackFaceFringe
}

// Reference step 236: fallthrough236
internal fun CurlMeshKernel.drawBackFaceFringe(): KernelStep {
    drawAddress74 = readLongBits(renderContextAddress)
    drawAddress75 = readLongBits((drawAddress74 + 208L))
    primitiveKind12 = ((0L or 2L) and KernelLayout.WORD_MASK)
    drawIndexCount12 = drawAddress73
    vertexBufferOffset12 = (0L and KernelLayout.WORD_MASK)
    drawVertexCount12 = drawAddress73
    renderContextAddress13 = renderContextAddress
    indexBufferAddress12 = 0L
    vertexBufferAddress12 = readLongBits((scratchBase + 464L))
    attributePlanes12 = 0L
    if (
        (appendDrawBatch(
            renderContextAddress13,
            primitiveKind12,
            drawIndexCount12,
            indexBufferAddress12,
            vertexBufferAddress12,
            vertexBufferOffset12,
            drawVertexCount12,
            attributePlanes12,
        ) != 0L)
    ) {
        return KernelStep.Invalid
    }
    drawAddress76 = readLongBits(renderContextAddress)
    drawAddress77 = readLongBits((drawAddress76 + 208L))
    primitiveKind13 = ((0L or 2L) and KernelLayout.WORD_MASK)
    drawIndexCount13 = drawAddress73
    vertexBufferOffset13 = (0L and KernelLayout.WORD_MASK)
    drawVertexCount13 = drawAddress73
    renderContextAddress14 = renderContextAddress
    indexBufferAddress13 = 0L
    vertexBufferAddress13 = readLongBits((scratchBase + 456L))
    attributePlanes13 = 0L
    if (
        (appendDrawBatch(
            renderContextAddress14,
            primitiveKind13,
            drawIndexCount13,
            indexBufferAddress13,
            vertexBufferAddress13,
            vertexBufferOffset13,
            drawVertexCount13,
            attributePlanes13,
        ) != 0L)
    ) {
        return KernelStep.Invalid
    }
    return KernelStep.DrawBackFaceEdges
}

// Reference step 237: L18534667c
internal fun CurlMeshKernel.drawBackFaceEdges(): KernelStep {
    drawStateAddress8 = readLongBits((renderContextAddress + KernelLayout.Context.DRAW_STATE))
    drawIndex10 = ((0L or 12L) and KernelLayout.WORD_MASK)
    writeByte(
        (drawStateAddress8 + KernelLayout.DrawState.PROGRAM),
        (drawIndex10 and KernelLayout.WORD_MASK),
    )
    drawAddress78 = readLongBits(renderContextAddress)
    drawAddress79 = readLongBits((drawAddress78 + 208L))
    primitiveKind14 = ((0L or 2L) and KernelLayout.WORD_MASK)
    drawIndexCount14 = drawAddress72
    vertexBufferOffset14 = (0L and KernelLayout.WORD_MASK)
    drawVertexCount14 = drawAddress72
    renderContextAddress15 = renderContextAddress
    indexBufferAddress14 = 0L
    vertexBufferAddress14 = readLongBits((scratchBase + 488L))
    attributePlanes14 = 0L
    if (
        (appendDrawBatch(
            renderContextAddress15,
            primitiveKind14,
            drawIndexCount14,
            indexBufferAddress14,
            vertexBufferAddress14,
            vertexBufferOffset14,
            drawVertexCount14,
            attributePlanes14,
        ) != 0L)
    ) {
        return KernelStep.Invalid
    }
    drawAddress80 = readLongBits(renderContextAddress)
    drawAddress81 = readLongBits((drawAddress80 + 208L))
    primitiveKind15 = ((0L or 2L) and KernelLayout.WORD_MASK)
    drawIndexCount15 = drawAddress73
    vertexBufferOffset15 = (0L and KernelLayout.WORD_MASK)
    drawVertexCount15 = drawAddress73
    renderContextAddress16 = renderContextAddress
    indexBufferAddress15 = 0L
    vertexBufferAddress15 = readLongBits((scratchBase + 480L))
    attributePlanes15 = 0L
    if (
        (appendDrawBatch(
            renderContextAddress16,
            primitiveKind15,
            drawIndexCount15,
            indexBufferAddress15,
            vertexBufferAddress15,
            vertexBufferOffset15,
            drawVertexCount15,
            attributePlanes15,
        ) != 0L)
    ) {
        return KernelStep.Invalid
    }
    drawAddress82 = readLongBits(renderContextAddress)
    drawAddress83 = readLongBits((drawAddress82 + 208L))
    primitiveKind16 = ((0L or 2L) and KernelLayout.WORD_MASK)
    drawIndexCount16 = drawAddress73
    vertexBufferOffset16 = (0L and KernelLayout.WORD_MASK)
    drawVertexCount16 = drawAddress73
    renderContextAddress17 = renderContextAddress
    indexBufferAddress16 = 0L
    vertexBufferAddress16 = readLongBits((scratchBase + 472L))
    attributePlanes16 = 0L
    if (
        (appendDrawBatch(
            renderContextAddress17,
            primitiveKind16,
            drawIndexCount16,
            indexBufferAddress16,
            vertexBufferAddress16,
            vertexBufferOffset16,
            drawVertexCount16,
            attributePlanes16,
        ) != 0L)
    ) {
        return KernelStep.Invalid
    }
    surfaceAddress21 = readLongBits((scratchBase + 184L))
    return KernelStep.AdvanceClipIteration
}

// Reference step 238: L185346710
internal fun CurlMeshKernel.advanceClipIteration(): KernelStep {
    drawAddress84 = (scratchBase + 680L)
    clipRectangleAddress2 = (scratchBase + 1504L)
    drawBits22 = nextClipRectangle(clipRectangleAddress2)
    return if ((((drawBits22 and KernelLayout.WORD_MASK) and (1L shl 0)) != 0L))
        KernelStep.InspectSurfaceDraw
    else KernelStep.FinishDrawClips
}

// Reference step 239: L185346720
internal fun CurlMeshKernel.finishDrawClips(): KernelStep {
    drawIndex11 = (0L and KernelLayout.WORD_MASK)
    drawBits23 = renderContextAddress
    drawIndex12 = 0L
    drawAddress85 = readLongBits((scratchBase + 128L))
    drawAddress86 = (readUnsignedByte((drawAddress85 + 61L)) and KernelLayout.WORD_MASK)
    return if ((((drawAddress86 and KernelLayout.WORD_MASK) and (1L shl 5)) == 0L))
        KernelStep.ResetDrawState
    else KernelStep.RestoreDrawBlendFlags
}

// Reference step 240: fallthrough240
internal fun CurlMeshKernel.restoreDrawBlendFlags(): KernelStep {
    drawStateAddress9 = readLongBits((renderContextAddress + KernelLayout.Context.DRAW_STATE))
    drawAddress87 =
        (readUnsignedByte((drawStateAddress9 + KernelLayout.DrawState.FLAGS)) and
            KernelLayout.WORD_MASK)
    drawBits24 =
        (((drawAddress87 and KernelLayout.WORD_MASK) and 4294967293L) and KernelLayout.WORD_MASK)
    writeByte(
        (drawStateAddress9 + KernelLayout.DrawState.FLAGS),
        (drawBits24 and KernelLayout.WORD_MASK),
    )
    return KernelStep.ResetDrawState
}

// Reference step 241: L18534674c
internal fun CurlMeshKernel.resetDrawState(): KernelStep {
    writeLongBits((renderContextAddress + KernelLayout.Context.ACTIVE_SURFACE), 0L)
    drawAddress88 =
        (readUnsignedWord((renderContextAddress + KernelLayout.Context.FRAME_COUNT)) and
            KernelLayout.WORD_MASK)
    drawAddress89 = (((drawAddress88 and KernelLayout.WORD_MASK) + 1L) and KernelLayout.WORD_MASK)
    writeWord(
        (renderContextAddress + KernelLayout.Context.FRAME_COUNT),
        (drawAddress89 and KernelLayout.WORD_MASK),
    )
    drawStateAddress10 = readLongBits((renderContextAddress + KernelLayout.Context.DRAW_STATE))
    writeByte((drawStateAddress10 + KernelLayout.DrawState.PROGRAM), 0L)
    drawStateAddress11 = readLongBits((renderContextAddress + KernelLayout.Context.DRAW_STATE))
    drawAddress90 =
        (readUnsignedByte((drawStateAddress11 + KernelLayout.DrawState.FLAGS)) and
            KernelLayout.WORD_MASK)
    drawBits25 = (((drawAddress90 and KernelLayout.WORD_MASK) and 254L) and KernelLayout.WORD_MASK)
    writeByte(
        (drawStateAddress11 + KernelLayout.DrawState.FLAGS),
        (drawBits25 and KernelLayout.WORD_MASK),
    )
    return KernelStep.CompleteGeometry
}

// Reference step 242: L185346774
internal fun CurlMeshKernel.completeGeometry(): KernelStep {
    return KernelStep.Complete
}

// Reference step 243: L1853467c0
internal fun CurlMeshKernel.rejectGeometry(): KernelStep {
    return KernelStep.Invalid
}
