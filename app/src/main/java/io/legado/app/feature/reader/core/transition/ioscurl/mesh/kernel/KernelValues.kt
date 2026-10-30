// Generated curl mesh algorithm stages.
@file:Suppress("UNUSED_VARIABLE", "UNUSED_VALUE", "NAME_SHADOWING", "RedundantConversion")

package io.legado.app.feature.reader.core.transition.ioscurl.mesh.kernel

/**
 * Semantic views of live values. Views of disjoint live ranges intentionally share storage; these
 * slot indices describe memory reuse, not algorithm constants. Keep these generated bindings
 * synchronized with the stage data-flow analysis.
 */
internal inline var CurlMeshKernel.contextArgument: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.controlInputAddress: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.materialInputAddress: Long
    get() = integerValue(2)
    set(value) {
        integerValue(2, value)
    }

internal inline var CurlMeshKernel.sourceSurfaceAddress: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.outputTransformArgument: Long
    get() = integerValue(4)
    set(value) {
        integerValue(4, value)
    }

internal inline var CurlMeshKernel.materialAddress: Long
    get() = integerValue(20)
    set(value) {
        integerValue(20, value)
    }

internal inline var CurlMeshKernel.controlAddress: Long
    get() = integerValue(21)
    set(value) {
        integerValue(21, value)
    }

internal inline var CurlMeshKernel.renderContextAddress: Long
    get() = integerValue(19)
    set(value) {
        integerValue(19, value)
    }

internal inline var CurlMeshKernel.layerStateAddress: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline val CurlMeshKernel.frontMaterialColor: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.frontColorMultiplier: KernelVector
    get() = vectorValue(1)

internal inline val CurlMeshKernel.backMaterialColor: KernelVector
    get() = vectorValue(2)

internal inline val CurlMeshKernel.backLightingColor: KernelVector
    get() = vectorValue(3)

internal inline val CurlMeshKernel.shadowMaterialColor: KernelVector
    get() = vectorValue(4)

internal inline var CurlMeshKernel.projectionAddress: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline val CurlMeshKernel.frontBaseRgba: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.layerColorMultiplier: KernelVector
    get() = vectorValue(5)

internal inline val CurlMeshKernel.layerTintRgba: KernelVector
    get() = vectorValue(5)

internal inline val CurlMeshKernel.frontTintRgba: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.packedFrontTint: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.frontMultiplierRgba: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.frontSecondaryTintRgba: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.packedFrontSecondaryTint: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.backLightingRgba: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.backLightingTintRgba: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.packedBackLightingTint: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.backBaseRgba: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.backTintRgba: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.shadowBaseRgba: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.shadowTintRgba: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.packedShadowTint: KernelVector
    get() = vectorValue(0)

internal inline var CurlMeshKernel.curlRadius: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline val CurlMeshKernel.minimumCurlAngle: KernelVector
    get() = vectorValue(14)

internal inline var CurlMeshKernel.halfPiRadians: Double
    get() = vectorValue(15).double(0)
    set(value) {
        vectorValue(15).double(0, value)
    }

internal inline var CurlMeshKernel.maximumCurlAngle: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.curlNormalX: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.curlNormalY: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.curlTangentX: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.curlTangentY: Double
    get() = vectorValue(5).double(0)
    set(value) {
        vectorValue(5).double(0, value)
    }

internal inline var CurlMeshKernel.projectedTangentX: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.projectedTangentY: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue2: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue3: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue4: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.sourceLeft: Double
    get() = vectorValue(7).double(0)
    set(value) {
        vectorValue(7).double(0, value)
    }

internal inline var CurlMeshKernel.sourceTop: Double
    get() = vectorValue(6).double(0)
    set(value) {
        vectorValue(6).double(0, value)
    }

internal inline var CurlMeshKernel.sourceWidth: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.sourceHeight: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue5: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue6: Double
    get() = vectorValue(17).double(0)
    set(value) {
        vectorValue(17).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue7: Double
    get() = vectorValue(16).double(0)
    set(value) {
        vectorValue(16).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue8: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue9: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue10: Double
    get() = vectorValue(13).double(0)
    set(value) {
        vectorValue(13).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue11: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue12: Double
    get() = vectorValue(10).double(0)
    set(value) {
        vectorValue(10).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue13: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue14: Double
    get() = vectorValue(16).double(0)
    set(value) {
        vectorValue(16).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue15: Double
    get() = vectorValue(6).double(0)
    set(value) {
        vectorValue(6).double(0, value)
    }

internal inline var CurlMeshKernel.pageLeft: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.pageTop: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.pageWidth: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.pageHeight: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue16: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue17: Double
    get() = vectorValue(12).double(0)
    set(value) {
        vectorValue(12).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue18: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue19: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue20: Double
    get() = vectorValue(11).double(0)
    set(value) {
        vectorValue(11).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue21: Double
    get() = vectorValue(9).double(0)
    set(value) {
        vectorValue(9).double(0, value)
    }

internal inline var CurlMeshKernel.projectedNormalX: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.projectedNormalY: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.pointTransformAddress: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline val CurlMeshKernel.projectionValue22: KernelVector
    get() = vectorValue(8)

internal inline var CurlMeshKernel.projectionValue23: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue24: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.transformedPointAddress: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline val CurlMeshKernel.projectionValue25: KernelVector
    get() = vectorValue(30)

internal inline var CurlMeshKernel.projectionValue26: Double
    get() = vectorValue(29).double(0)
    set(value) {
        vectorValue(29).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue27: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue28: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue29: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue30: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.normalX: Double
    get() = vectorValue(23).double(0)
    set(value) {
        vectorValue(23).double(0, value)
    }

internal inline var CurlMeshKernel.normalY: Double
    get() = vectorValue(24).double(0)
    set(value) {
        vectorValue(24).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue31: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue32: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue33: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.curlRadius2: Double
    get() = vectorValue(21).double(0)
    set(value) {
        vectorValue(21).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue34: Double
    get() = vectorValue(25).double(0)
    set(value) {
        vectorValue(25).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue35: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue36: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue37: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline val CurlMeshKernel.projectionValue38: KernelVector
    get() = vectorValue(8)

internal inline var CurlMeshKernel.projectionValue39: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue40: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue41: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue42: Float
    get() = vectorValue(1).float(0)
    set(value) {
        vectorValue(1).float(0, value)
    }

internal inline var CurlMeshKernel.projectionValue43: Float
    get() = vectorValue(3).float(0)
    set(value) {
        vectorValue(3).float(0, value)
    }

internal inline var CurlMeshKernel.projectionValue44: Float
    get() = vectorValue(26).float(0)
    set(value) {
        vectorValue(26).float(0, value)
    }

internal inline var CurlMeshKernel.projectionValue45: Float
    get() = vectorValue(27).float(0)
    set(value) {
        vectorValue(27).float(0, value)
    }

internal inline var CurlMeshKernel.projectionValue46: Float
    get() = vectorValue(4).float(0)
    set(value) {
        vectorValue(4).float(0, value)
    }

internal inline var CurlMeshKernel.projectionValue47: Float
    get() = vectorValue(28).float(0)
    set(value) {
        vectorValue(28).float(0, value)
    }

internal inline var CurlMeshKernel.projectionValue48: Float
    get() = vectorValue(3).float(0)
    set(value) {
        vectorValue(3).float(0, value)
    }

internal inline var CurlMeshKernel.projectionValue49: Float
    get() = vectorValue(1).float(0)
    set(value) {
        vectorValue(1).float(0, value)
    }

internal inline var CurlMeshKernel.projectionValue50: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue51: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue52: Float
    get() = vectorValue(3).float(0)
    set(value) {
        vectorValue(3).float(0, value)
    }

internal inline var CurlMeshKernel.projectionValue53: Float
    get() = vectorValue(4).float(0)
    set(value) {
        vectorValue(4).float(0, value)
    }

internal inline var CurlMeshKernel.projectionValue54: Float
    get() = vectorValue(5).float(0)
    set(value) {
        vectorValue(5).float(0, value)
    }

internal inline var CurlMeshKernel.projectionValue55: Float
    get() = vectorValue(4).float(0)
    set(value) {
        vectorValue(4).float(0, value)
    }

internal inline var CurlMeshKernel.projectionValue56: Float
    get() = vectorValue(3).float(0)
    set(value) {
        vectorValue(3).float(0, value)
    }

internal inline var CurlMeshKernel.projectionValue57: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue58: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue59: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.halfPiRadians2: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue60: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue61: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue62: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue63: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue64: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue65: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue66: Double
    get() = vectorValue(5).double(0)
    set(value) {
        vectorValue(5).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue67: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue68: Double
    get() = vectorValue(5).double(0)
    set(value) {
        vectorValue(5).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue69: Double
    get() = vectorValue(5).double(0)
    set(value) {
        vectorValue(5).double(0, value)
    }

internal inline val CurlMeshKernel.projectionValue70: KernelVector
    get() = vectorValue(6)

internal inline val CurlMeshKernel.projectionValue71: KernelVector
    get() = vectorValue(7)

internal inline val CurlMeshKernel.projectionValue72: KernelVector
    get() = vectorValue(16)

internal inline val CurlMeshKernel.projectionValue73: KernelVector
    get() = vectorValue(6)

internal inline var CurlMeshKernel.projectionValue74: Double
    get() = vectorValue(6).double(0)
    set(value) {
        vectorValue(6).double(0, value)
    }

internal inline val CurlMeshKernel.projectionValue75: KernelVector
    get() = vectorValue(7)

internal inline val CurlMeshKernel.projectionValue76: KernelVector
    get() = vectorValue(17)

internal inline val CurlMeshKernel.projectionValue77: KernelVector
    get() = vectorValue(7)

internal inline var CurlMeshKernel.projectionValue78: Double
    get() = vectorValue(7).double(0)
    set(value) {
        vectorValue(7).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue79: Double
    get() = vectorValue(16).double(0)
    set(value) {
        vectorValue(16).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue80: Double
    get() = vectorValue(18).double(0)
    set(value) {
        vectorValue(18).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue81: Double
    get() = vectorValue(19).double(0)
    set(value) {
        vectorValue(19).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue82: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue83: Double
    get() = vectorValue(5).double(0)
    set(value) {
        vectorValue(5).double(0, value)
    }

internal inline var CurlMeshKernel.projectionAddress2: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.projectionAddress3: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.projectionValue84: Double
    get() = vectorValue(17).double(0)
    set(value) {
        vectorValue(17).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue85: Double
    get() = vectorValue(6).double(0)
    set(value) {
        vectorValue(6).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue86: Double
    get() = vectorValue(7).double(0)
    set(value) {
        vectorValue(7).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue87: Double
    get() = vectorValue(16).double(0)
    set(value) {
        vectorValue(16).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue88: Double
    get() = vectorValue(17).double(0)
    set(value) {
        vectorValue(17).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue89: Double
    get() = vectorValue(20).double(0)
    set(value) {
        vectorValue(20).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue90: Double
    get() = vectorValue(20).double(0)
    set(value) {
        vectorValue(20).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue91: Double
    get() = vectorValue(19).double(0)
    set(value) {
        vectorValue(19).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue92: Double
    get() = vectorValue(18).double(0)
    set(value) {
        vectorValue(18).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue93: Double
    get() = vectorValue(18).double(0)
    set(value) {
        vectorValue(18).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue94: Double
    get() = vectorValue(18).double(0)
    set(value) {
        vectorValue(18).double(0, value)
    }

internal inline var CurlMeshKernel.projectionIndex: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.projectionAddress4: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.projectionValue95: Double
    get() = vectorValue(18).double(0)
    set(value) {
        vectorValue(18).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue96: Double
    get() = vectorValue(18).double(0)
    set(value) {
        vectorValue(18).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue97: Double
    get() = vectorValue(5).double(0)
    set(value) {
        vectorValue(5).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue98: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue99: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue100: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue101: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline val CurlMeshKernel.projectionValue102: KernelVector
    get() = vectorValue(4)

internal inline val CurlMeshKernel.projectionColor16: KernelVector
    get() = vectorValue(4)

internal inline var CurlMeshKernel.projectionValue103: Double
    get() = vectorValue(16).double(0)
    set(value) {
        vectorValue(16).double(0, value)
    }

internal inline val CurlMeshKernel.projectionValue104: KernelVector
    get() = vectorValue(14)

internal inline var CurlMeshKernel.projectionValue105: Double
    get() = vectorValue(18).double(0)
    set(value) {
        vectorValue(18).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue106: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue107: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue108: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue109: Double
    get() = vectorValue(5).double(0)
    set(value) {
        vectorValue(5).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue110: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue111: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue112: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue113: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue114: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue115: Double
    get() = vectorValue(6).double(0)
    set(value) {
        vectorValue(6).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue116: Double
    get() = vectorValue(17).double(0)
    set(value) {
        vectorValue(17).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue117: Double
    get() = vectorValue(20).double(0)
    set(value) {
        vectorValue(20).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue118: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue119: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue120: Double
    get() = vectorValue(5).double(0)
    set(value) {
        vectorValue(5).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue121: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue122: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.projectionAddress5: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.projectionValue123: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline val CurlMeshKernel.projectionValue124: KernelVector
    get() = vectorValue(1)

internal inline var CurlMeshKernel.projectionIndex2: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.projectionAddress6: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline val CurlMeshKernel.projectionValue125: KernelVector
    get() = vectorValue(7)

internal inline var CurlMeshKernel.projectionValue126: Double
    get() = vectorValue(16).double(0)
    set(value) {
        vectorValue(16).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue127: Double
    get() = vectorValue(17).double(0)
    set(value) {
        vectorValue(17).double(0, value)
    }

internal inline val CurlMeshKernel.projectionValue128: KernelVector
    get() = vectorValue(9)

internal inline var CurlMeshKernel.projectionIndex3: Long
    get() = integerValue(11)
    set(value) {
        integerValue(11, value)
    }

internal inline var CurlMeshKernel.projectionValue129: Double
    get() = vectorValue(16).double(0)
    set(value) {
        vectorValue(16).double(0, value)
    }

internal inline var CurlMeshKernel.projectionBits: Long
    get() = integerValue(12)
    set(value) {
        integerValue(12, value)
    }

internal inline var CurlMeshKernel.projectionBits2: Long
    get() = integerValue(12)
    set(value) {
        integerValue(12, value)
    }

internal inline var CurlMeshKernel.projectionBits3: Long
    get() = integerValue(11)
    set(value) {
        integerValue(11, value)
    }

internal inline var CurlMeshKernel.projectionValue130: Double
    get() = vectorValue(7).double(0)
    set(value) {
        vectorValue(7).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue131: Double
    get() = vectorValue(19).double(0)
    set(value) {
        vectorValue(19).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue132: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue133: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue134: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue135: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue136: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue137: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.projectionIndex4: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.projectionValue138: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue139: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue140: Double
    get() = vectorValue(6).double(0)
    set(value) {
        vectorValue(6).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue141: Double
    get() = vectorValue(6).double(0)
    set(value) {
        vectorValue(6).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue142: Double
    get() = vectorValue(6).double(0)
    set(value) {
        vectorValue(6).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue143: Double
    get() = vectorValue(7).double(0)
    set(value) {
        vectorValue(7).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue144: Double
    get() = vectorValue(7).double(0)
    set(value) {
        vectorValue(7).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue145: Float
    get() = vectorValue(7).float(0)
    set(value) {
        vectorValue(7).float(0, value)
    }

internal inline var CurlMeshKernel.projectionValue146: Float
    get() = vectorValue(7).float(0)
    set(value) {
        vectorValue(7).float(0, value)
    }

internal inline var CurlMeshKernel.projectionValue147: Float
    get() = vectorValue(16).float(0)
    set(value) {
        vectorValue(16).float(0, value)
    }

internal inline var CurlMeshKernel.projectionValue148: Float
    get() = vectorValue(7).float(0)
    set(value) {
        vectorValue(7).float(0, value)
    }

internal inline var CurlMeshKernel.projectionIndex5: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.projectionValue149: Double
    get() = vectorValue(7).double(0)
    set(value) {
        vectorValue(7).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue150: Double
    get() = vectorValue(7).double(0)
    set(value) {
        vectorValue(7).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue151: Float
    get() = vectorValue(7).float(0)
    set(value) {
        vectorValue(7).float(0, value)
    }

internal inline var CurlMeshKernel.projectionValue152: Float
    get() = vectorValue(7).float(0)
    set(value) {
        vectorValue(7).float(0, value)
    }

internal inline var CurlMeshKernel.projectionValue153: Float
    get() = vectorValue(16).float(0)
    set(value) {
        vectorValue(16).float(0, value)
    }

internal inline var CurlMeshKernel.projectionValue154: Float
    get() = vectorValue(7).float(0)
    set(value) {
        vectorValue(7).float(0, value)
    }

internal inline var CurlMeshKernel.projectionIndex6: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.projectionAddress7: Long
    get() = integerValue(11)
    set(value) {
        integerValue(11, value)
    }

internal inline var CurlMeshKernel.projectionValue155: Float
    get() = vectorValue(7).float(0)
    set(value) {
        vectorValue(7).float(0, value)
    }

internal inline var CurlMeshKernel.projectionValue156: Double
    get() = vectorValue(7).double(0)
    set(value) {
        vectorValue(7).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue157: Double
    get() = vectorValue(7).double(0)
    set(value) {
        vectorValue(7).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue158: Double
    get() = vectorValue(7).double(0)
    set(value) {
        vectorValue(7).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue159: Double
    get() = vectorValue(7).double(0)
    set(value) {
        vectorValue(7).double(0, value)
    }

internal inline var CurlMeshKernel.projectionAddress8: Long
    get() = integerValue(12)
    set(value) {
        integerValue(12, value)
    }

internal inline var CurlMeshKernel.projectionIndex7: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.projectionIndex8: Long
    get() = integerValue(27)
    set(value) {
        integerValue(27, value)
    }

internal inline var CurlMeshKernel.projectionIndex9: Long
    get() = integerValue(17)
    set(value) {
        integerValue(17, value)
    }

internal inline var CurlMeshKernel.projectionIndex10: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.projectionIndex11: Long
    get() = integerValue(11)
    set(value) {
        integerValue(11, value)
    }

internal inline var CurlMeshKernel.projectionValue160: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue161: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue162: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue163: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.projectionIndex12: Long
    get() = integerValue(16)
    set(value) {
        integerValue(16, value)
    }

internal inline var CurlMeshKernel.projectionAddress9: Long
    get() = integerValue(12)
    set(value) {
        integerValue(12, value)
    }

internal inline var CurlMeshKernel.projectionAddress10: Long
    get() = integerValue(13)
    set(value) {
        integerValue(13, value)
    }

internal inline var CurlMeshKernel.projectionIndex13: Long
    get() = integerValue(14)
    set(value) {
        integerValue(14, value)
    }

internal inline var CurlMeshKernel.projectionAddress11: Long
    get() = integerValue(15)
    set(value) {
        integerValue(15, value)
    }

internal inline var CurlMeshKernel.projectionValue164: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.projectionValue165: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.projectionAddress12: Long
    get() = integerValue(16)
    set(value) {
        integerValue(16, value)
    }

internal inline val CurlMeshKernel.projectionValue166: KernelVector
    get() = vectorValue(2)

internal inline val CurlMeshKernel.projectionValue167: KernelVector
    get() = vectorValue(3)

internal inline var CurlMeshKernel.projectionIndex14: Long
    get() = integerValue(12)
    set(value) {
        integerValue(12, value)
    }

internal inline var CurlMeshKernel.projectionIndex15: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.projectionBits4: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.projectionIndex16: Long
    get() = integerValue(13)
    set(value) {
        integerValue(13, value)
    }

internal inline var CurlMeshKernel.projectionAddress13: Long
    get() = integerValue(11)
    set(value) {
        integerValue(11, value)
    }

internal inline var CurlMeshKernel.projectionAddress14: Long
    get() = integerValue(25)
    set(value) {
        integerValue(25, value)
    }

internal inline var CurlMeshKernel.projectionValue168: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.projectionIndex17: Long
    get() = integerValue(11)
    set(value) {
        integerValue(11, value)
    }

internal inline var CurlMeshKernel.projectionIndex18: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.projectionIndex19: Long
    get() = integerValue(22)
    set(value) {
        integerValue(22, value)
    }

internal inline var CurlMeshKernel.projectionAddress15: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.projectionAddress16: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.projectionBits5: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.projectionAddress17: Long
    get() = integerValue(26)
    set(value) {
        integerValue(26, value)
    }

internal inline var CurlMeshKernel.projectionAddress18: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.projectionAddress19: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.projectionBits6: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.projectionAddress20: Long
    get() = integerValue(24)
    set(value) {
        integerValue(24, value)
    }

internal inline var CurlMeshKernel.projectionBits7: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.projectionBits8: Long
    get() = integerValue(28)
    set(value) {
        integerValue(28, value)
    }

internal inline var CurlMeshKernel.projectionIndex20: Long
    get() = integerValue(14)
    set(value) {
        integerValue(14, value)
    }

internal inline var CurlMeshKernel.projectionAddress21: Long
    get() = integerValue(12)
    set(value) {
        integerValue(12, value)
    }

internal inline var CurlMeshKernel.projectionBits9: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.projectionValue169: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.projectionAddress22: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.projectionIndex21: Long
    get() = integerValue(14)
    set(value) {
        integerValue(14, value)
    }

internal inline var CurlMeshKernel.projectionBits10: Long
    get() = integerValue(11)
    set(value) {
        integerValue(11, value)
    }

internal inline var CurlMeshKernel.projectionBits11: Long
    get() = integerValue(13)
    set(value) {
        integerValue(13, value)
    }

internal inline var CurlMeshKernel.projectionAddress23: Long
    get() = integerValue(12)
    set(value) {
        integerValue(12, value)
    }

internal inline var CurlMeshKernel.projectionBits12: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.projectionBits13: Long
    get() = integerValue(23)
    set(value) {
        integerValue(23, value)
    }

internal inline var CurlMeshKernel.projectionBits14: Long
    get() = integerValue(13)
    set(value) {
        integerValue(13, value)
    }

internal inline var CurlMeshKernel.projectionValue170: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.projectionIndex22: Long
    get() = integerValue(11)
    set(value) {
        integerValue(11, value)
    }

internal inline var CurlMeshKernel.projectionBits15: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.projectionAddress24: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.projectionIndex23: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.projectionIndex24: Long
    get() = integerValue(21)
    set(value) {
        integerValue(21, value)
    }

internal inline var CurlMeshKernel.surfaceIndex: Long
    get() = integerValue(20)
    set(value) {
        integerValue(20, value)
    }

internal inline var CurlMeshKernel.surfaceValue: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue2: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue3: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue4: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue5: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue6: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue7: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceBits: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.surfaceValue8: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue9: Double
    get() = vectorValue(13).double(0)
    set(value) {
        vectorValue(13).double(0, value)
    }

internal inline val CurlMeshKernel.surfaceValue10: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.surfaceColor: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.surfaceValue11: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.surfaceColor2: KernelVector
    get() = vectorValue(0)

internal inline var CurlMeshKernel.surfaceValue12: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue13: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue14: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline val CurlMeshKernel.surfaceValue15: KernelVector
    get() = vectorValue(11)

internal inline val CurlMeshKernel.surfaceValue16: KernelVector
    get() = vectorValue(15)

internal inline var CurlMeshKernel.surfaceAddress: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline val CurlMeshKernel.surfaceValue17: KernelVector
    get() = vectorValue(12)

internal inline var CurlMeshKernel.surfaceValue18: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue19: Double
    get() = vectorValue(9).double(0)
    set(value) {
        vectorValue(9).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue20: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue21: Double
    get() = vectorValue(10).double(0)
    set(value) {
        vectorValue(10).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue22: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline val CurlMeshKernel.surfaceValue23: KernelVector
    get() = vectorValue(8)

internal inline var CurlMeshKernel.surfaceValue24: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline val CurlMeshKernel.surfaceValue25: KernelVector
    get() = vectorValue(14)

internal inline var CurlMeshKernel.surfaceAddress2: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.clipStartAddress: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.surfaceAddress3: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.clipEndAddress: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.surfaceValue26: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue27: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue28: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue29: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline val CurlMeshKernel.surfaceValue30: KernelVector
    get() = vectorValue(4)

internal inline val CurlMeshKernel.surfaceValue31: KernelVector
    get() = vectorValue(5)

internal inline val CurlMeshKernel.surfaceValue32: KernelVector
    get() = vectorValue(6)

internal inline val CurlMeshKernel.surfaceValue33: KernelVector
    get() = vectorValue(7)

internal inline var CurlMeshKernel.surfaceAddress4: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.clipStartAddress2: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.clipEndAddress2: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.surfaceValue34: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue35: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue36: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue37: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline val CurlMeshKernel.surfaceValue38: KernelVector
    get() = vectorValue(4)

internal inline val CurlMeshKernel.surfaceValue39: KernelVector
    get() = vectorValue(5)

internal inline val CurlMeshKernel.surfaceValue40: KernelVector
    get() = vectorValue(8)

internal inline val CurlMeshKernel.surfaceValue41: KernelVector
    get() = vectorValue(6)

internal inline val CurlMeshKernel.surfaceValue42: KernelVector
    get() = vectorValue(7)

internal inline var CurlMeshKernel.surfaceAddress5: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.sineOutputAddress: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline val CurlMeshKernel.surfaceValue43: KernelVector
    get() = vectorValue(0)

internal inline var CurlMeshKernel.surfaceValue44: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue45: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue46: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline val CurlMeshKernel.surfaceValue47: KernelVector
    get() = vectorValue(3)

internal inline var CurlMeshKernel.surfaceValue48: Float
    get() = vectorValue(0).float(0)
    set(value) {
        vectorValue(0).float(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue49: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue50: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceAddress6: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.surfaceAddress7: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.surfaceValue51: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceAddress8: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.surfaceValue52: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue53: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue54: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue55: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue56: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline val CurlMeshKernel.surfaceValue57: KernelVector
    get() = vectorValue(2)

internal inline var CurlMeshKernel.surfaceValue58: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue59: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue60: Double
    get() = vectorValue(5).double(0)
    set(value) {
        vectorValue(5).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue61: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue62: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue63: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue64: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue65: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue66: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue67: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue68: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue69: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue70: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue71: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue72: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue73: Double
    get() = vectorValue(6).double(0)
    set(value) {
        vectorValue(6).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue74: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline val CurlMeshKernel.surfaceValue75: KernelVector
    get() = vectorValue(4)

internal inline var CurlMeshKernel.surfaceValue76: Float
    get() = vectorValue(5).float(0)
    set(value) {
        vectorValue(5).float(0, value)
    }

internal inline val CurlMeshKernel.surfaceValue77: KernelVector
    get() = vectorValue(5)

internal inline val CurlMeshKernel.surfaceValue78: KernelVector
    get() = vectorValue(6)

internal inline val CurlMeshKernel.surfaceValue79: KernelVector
    get() = vectorValue(4)

internal inline val CurlMeshKernel.surfaceValue80: KernelVector
    get() = vectorValue(6)

internal inline val CurlMeshKernel.surfaceValue81: KernelVector
    get() = vectorValue(4)

internal inline val CurlMeshKernel.surfaceColor3: KernelVector
    get() = vectorValue(4)

internal inline var CurlMeshKernel.surfaceIndex2: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.surfaceAddress9: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.surfaceAddress10: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.surfaceAddress11: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.surfaceAddress12: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.surfaceValue82: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue83: Double
    get() = vectorValue(5).double(0)
    set(value) {
        vectorValue(5).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue84: Double
    get() = vectorValue(5).double(0)
    set(value) {
        vectorValue(5).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue85: Double
    get() = vectorValue(6).double(0)
    set(value) {
        vectorValue(6).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue86: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceIndex3: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.surfaceIndex4: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.surfaceBits2: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.surfaceIndex5: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.surfaceAddress13: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.surfaceIndex6: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.surfaceBits3: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.surfaceAddress14: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.surfaceAddress15: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.surfaceValue87: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue88: Float
    get() = vectorValue(4).float(0)
    set(value) {
        vectorValue(4).float(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue89: Double
    get() = vectorValue(5).double(0)
    set(value) {
        vectorValue(5).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue90: Float
    get() = vectorValue(5).float(0)
    set(value) {
        vectorValue(5).float(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue91: Double
    get() = vectorValue(6).double(0)
    set(value) {
        vectorValue(6).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue92: Float
    get() = vectorValue(6).float(0)
    set(value) {
        vectorValue(6).float(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue93: Double
    get() = vectorValue(7).double(0)
    set(value) {
        vectorValue(7).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue94: Float
    get() = vectorValue(7).float(0)
    set(value) {
        vectorValue(7).float(0, value)
    }

internal inline var CurlMeshKernel.surfaceAddress16: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.centerStripAddress: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.surfaceAddress17: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.lowerStripAddress: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.surfaceAddress18: Long
    get() = integerValue(2)
    set(value) {
        integerValue(2, value)
    }

internal inline var CurlMeshKernel.upperStripAddress: Long
    get() = integerValue(2)
    set(value) {
        integerValue(2, value)
    }

internal inline var CurlMeshKernel.packedColorAddress: Long
    get() = integerValue(4)
    set(value) {
        integerValue(4, value)
    }

internal inline val CurlMeshKernel.surfaceValue95: KernelVector
    get() = vectorValue(4)

internal inline val CurlMeshKernel.surfaceValue96: KernelVector
    get() = vectorValue(5)

internal inline var CurlMeshKernel.surfaceValue97: Double
    get() = vectorValue(6).double(0)
    set(value) {
        vectorValue(6).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue98: Double
    get() = vectorValue(7).double(0)
    set(value) {
        vectorValue(7).double(0, value)
    }

internal inline var CurlMeshKernel.stripIndex: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.surfaceAddress19: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.surfaceAddress20: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.surfaceIndex7: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.surfaceAddress21: Long
    get() = integerValue(23)
    set(value) {
        integerValue(23, value)
    }

internal inline var CurlMeshKernel.surfaceAddress22: Long
    get() = integerValue(21)
    set(value) {
        integerValue(21, value)
    }

internal inline val CurlMeshKernel.surfaceValue99: KernelVector
    get() = vectorValue(8)

internal inline val CurlMeshKernel.surfaceValue100: KernelVector
    get() = vectorValue(15)

internal inline val CurlMeshKernel.surfaceValue101: KernelVector
    get() = vectorValue(13)

internal inline var CurlMeshKernel.surfaceValue102: Double
    get() = vectorValue(11).double(0)
    set(value) {
        vectorValue(11).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceValue103: Double
    get() = vectorValue(10).double(0)
    set(value) {
        vectorValue(10).double(0, value)
    }

internal inline var CurlMeshKernel.surfaceAddress23: Long
    get() = integerValue(20)
    set(value) {
        integerValue(20, value)
    }

internal inline var CurlMeshKernel.surfaceAddress24: Long
    get() = integerValue(20)
    set(value) {
        integerValue(20, value)
    }

internal inline var CurlMeshKernel.surfaceAddress25: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.surfaceBits4: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.surfaceAddress26: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.surfaceAddress27: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.surfaceBits5: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.surfaceAddress28: Long
    get() = integerValue(21)
    set(value) {
        integerValue(21, value)
    }

internal inline var CurlMeshKernel.surfaceIndex8: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.surfaceBits6: Long
    get() = integerValue(2)
    set(value) {
        integerValue(2, value)
    }

internal inline var CurlMeshKernel.surfaceIndex9: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.surfaceAddress29: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.surfaceBits7: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.surfaceIndex10: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.surfaceBits8: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.surfaceBits9: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.surfaceBits10: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline val CurlMeshKernel.surfaceValue104: KernelVector
    get() = vectorValue(0)

internal inline var CurlMeshKernel.surfaceColor4: Long
    get() = vectorValue(0).halfBits(0)
    set(value) {
        vectorValue(0).halfBits(0, value)
    }

internal inline var CurlMeshKernel.surfaceColor5: Float
    get() = vectorValue(0).float(0)
    set(value) {
        vectorValue(0).float(0, value)
    }

internal inline var CurlMeshKernel.surfaceColor6: Long
    get() = vectorValue(1).halfBits(0)
    set(value) {
        vectorValue(1).halfBits(0, value)
    }

internal inline var CurlMeshKernel.surfaceColor7: Float
    get() = vectorValue(1).float(0)
    set(value) {
        vectorValue(1).float(0, value)
    }

internal inline var CurlMeshKernel.surfaceAddress30: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.surfaceAddress31: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.surfaceAddress32: Long
    get() = integerValue(22)
    set(value) {
        integerValue(22, value)
    }

internal inline var CurlMeshKernel.surfaceBits11: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.surfaceAddress33: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.surfaceAddress34: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.surfaceBits12: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.surfaceAddress35: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.surfaceBits13: Long
    get() = integerValue(20)
    set(value) {
        integerValue(20, value)
    }

internal inline var CurlMeshKernel.surfaceBits14: Long
    get() = integerValue(2)
    set(value) {
        integerValue(2, value)
    }

internal inline var CurlMeshKernel.surfaceAddress36: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.surfaceAddress37: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.surfaceBits15: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.surfaceAddress38: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.surfaceAddress39: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.surfaceBits16: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.surfaceBits17: Long
    get() = integerValue(2)
    set(value) {
        integerValue(2, value)
    }

internal inline var CurlMeshKernel.shadowAddress: Long
    get() = integerValue(24)
    set(value) {
        integerValue(24, value)
    }

internal inline var CurlMeshKernel.shadowValue: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue2: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue3: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue4: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue5: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue6: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue7: Double
    get() = vectorValue(5).double(0)
    set(value) {
        vectorValue(5).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue8: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue9: Double
    get() = vectorValue(7).double(0)
    set(value) {
        vectorValue(7).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue10: Double
    get() = vectorValue(16).double(0)
    set(value) {
        vectorValue(16).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue11: Double
    get() = vectorValue(20).double(0)
    set(value) {
        vectorValue(20).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue12: Double
    get() = vectorValue(19).double(0)
    set(value) {
        vectorValue(19).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue13: Double
    get() = vectorValue(17).double(0)
    set(value) {
        vectorValue(17).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue14: Double
    get() = vectorValue(18).double(0)
    set(value) {
        vectorValue(18).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue15: Double
    get() = vectorValue(21).double(0)
    set(value) {
        vectorValue(21).double(0, value)
    }

internal inline val CurlMeshKernel.shadowValue16: KernelVector
    get() = vectorValue(22)

internal inline var CurlMeshKernel.shadowValue17: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue18: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue19: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue20: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue21: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline val CurlMeshKernel.shadowValue22: KernelVector
    get() = vectorValue(3)

internal inline val CurlMeshKernel.shadowValue23: KernelVector
    get() = vectorValue(12)

internal inline var CurlMeshKernel.shadowIndex: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.shadowValue24: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue25: Double
    get() = vectorValue(6).double(0)
    set(value) {
        vectorValue(6).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue26: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue27: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue28: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue29: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue30: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline val CurlMeshKernel.shadowValue31: KernelVector
    get() = vectorValue(2)

internal inline var CurlMeshKernel.shadowAddress2: Long
    get() = integerValue(11)
    set(value) {
        integerValue(11, value)
    }

internal inline var CurlMeshKernel.shadowAddress3: Long
    get() = integerValue(12)
    set(value) {
        integerValue(12, value)
    }

internal inline var CurlMeshKernel.shadowBits: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.shadowBits2: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.shadowValue32: Float
    get() = vectorValue(1).float(0)
    set(value) {
        vectorValue(1).float(0, value)
    }

internal inline var CurlMeshKernel.shadowValue33: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline val CurlMeshKernel.shadowValue34: KernelVector
    get() = vectorValue(1)

internal inline var CurlMeshKernel.shadowValue35: Double
    get() = vectorValue(14).double(0)
    set(value) {
        vectorValue(14).double(0, value)
    }

internal inline var CurlMeshKernel.shadowAddress4: Long
    get() = integerValue(11)
    set(value) {
        integerValue(11, value)
    }

internal inline var CurlMeshKernel.shadowValue36: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue37: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue38: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.shadowIndex2: Long
    get() = integerValue(11)
    set(value) {
        integerValue(11, value)
    }

internal inline var CurlMeshKernel.shadowBits3: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.shadowIndex3: Long
    get() = integerValue(11)
    set(value) {
        integerValue(11, value)
    }

internal inline var CurlMeshKernel.shadowValue39: Float
    get() = vectorValue(0).float(0)
    set(value) {
        vectorValue(0).float(0, value)
    }

internal inline var CurlMeshKernel.shadowValue40: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline val CurlMeshKernel.shadowValue41: KernelVector
    get() = vectorValue(0)

internal inline var CurlMeshKernel.shadowIndex4: Long
    get() = integerValue(12)
    set(value) {
        integerValue(12, value)
    }

internal inline var CurlMeshKernel.shadowBits4: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.shadowAddress5: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.shadowValue42: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue43: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue44: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue45: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue46: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue47: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.shadowIndex5: Long
    get() = integerValue(20)
    set(value) {
        integerValue(20, value)
    }

internal inline var CurlMeshKernel.shadowValue48: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue49: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue50: Double
    get() = vectorValue(5).double(0)
    set(value) {
        vectorValue(5).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue51: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue52: Double
    get() = vectorValue(5).double(0)
    set(value) {
        vectorValue(5).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue53: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.shadowIndex6: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.shadowIndex7: Long
    get() = integerValue(15)
    set(value) {
        integerValue(15, value)
    }

internal inline var CurlMeshKernel.shadowIndex8: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.shadowAddress6: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.shadowValue54: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue55: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.shadowIndex9: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.shadowIndex10: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.shadowIndex11: Long
    get() = integerValue(11)
    set(value) {
        integerValue(11, value)
    }

internal inline var CurlMeshKernel.shadowAddress7: Long
    get() = integerValue(12)
    set(value) {
        integerValue(12, value)
    }

internal inline var CurlMeshKernel.shadowAddress8: Long
    get() = integerValue(13)
    set(value) {
        integerValue(13, value)
    }

internal inline var CurlMeshKernel.shadowAddress9: Long
    get() = integerValue(14)
    set(value) {
        integerValue(14, value)
    }

internal inline var CurlMeshKernel.shadowAddress10: Long
    get() = integerValue(15)
    set(value) {
        integerValue(15, value)
    }

internal inline val CurlMeshKernel.shadowValue56: KernelVector
    get() = vectorValue(3)

internal inline val CurlMeshKernel.shadowValue57: KernelVector
    get() = vectorValue(4)

internal inline var CurlMeshKernel.shadowIndex12: Long
    get() = integerValue(15)
    set(value) {
        integerValue(15, value)
    }

internal inline var CurlMeshKernel.shadowIndex13: Long
    get() = integerValue(17)
    set(value) {
        integerValue(17, value)
    }

internal inline var CurlMeshKernel.shadowIndex14: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.shadowIndex15: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.shadowAddress11: Long
    get() = integerValue(11)
    set(value) {
        integerValue(11, value)
    }

internal inline var CurlMeshKernel.shadowAddress12: Long
    get() = integerValue(12)
    set(value) {
        integerValue(12, value)
    }

internal inline var CurlMeshKernel.shadowAddress13: Long
    get() = integerValue(13)
    set(value) {
        integerValue(13, value)
    }

internal inline var CurlMeshKernel.shadowAddress14: Long
    get() = integerValue(14)
    set(value) {
        integerValue(14, value)
    }

internal inline var CurlMeshKernel.shadowAddress15: Long
    get() = integerValue(15)
    set(value) {
        integerValue(15, value)
    }

internal inline val CurlMeshKernel.shadowValue58: KernelVector
    get() = vectorValue(4)

internal inline val CurlMeshKernel.shadowValue59: KernelVector
    get() = vectorValue(3)

internal inline var CurlMeshKernel.shadowBits5: Long
    get() = integerValue(16)
    set(value) {
        integerValue(16, value)
    }

internal inline var CurlMeshKernel.shadowBits6: Long
    get() = integerValue(30)
    set(value) {
        integerValue(30, value)
    }

internal inline var CurlMeshKernel.shadowAddress16: Long
    get() = integerValue(14)
    set(value) {
        integerValue(14, value)
    }

internal inline var CurlMeshKernel.shadowAddress17: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.shadowAddress18: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.shadowAddress19: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.shadowIndex16: Long
    get() = integerValue(11)
    set(value) {
        integerValue(11, value)
    }

internal inline var CurlMeshKernel.shadowIndex17: Long
    get() = integerValue(12)
    set(value) {
        integerValue(12, value)
    }

internal inline var CurlMeshKernel.shadowIndex18: Long
    get() = integerValue(13)
    set(value) {
        integerValue(13, value)
    }

internal inline var CurlMeshKernel.shadowBits7: Long
    get() = integerValue(15)
    set(value) {
        integerValue(15, value)
    }

internal inline var CurlMeshKernel.shadowValue60: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue61: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue62: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue63: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue64: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.shadowIndex19: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.shadowValue65: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue66: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.shadowIndex20: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.shadowBits8: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.shadowBits9: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.shadowValue67: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue68: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue69: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.shadowValue70: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraBits: Long
    get() = integerValue(25)
    set(value) {
        integerValue(25, value)
    }

internal inline var CurlMeshKernel.penumbraIndex: Long
    get() = integerValue(21)
    set(value) {
        integerValue(21, value)
    }

internal inline var CurlMeshKernel.penumbraValue: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue2: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue3: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue4: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue5: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue6: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue7: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraIndex2: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.penumbraBits2: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.penumbraValue8: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue9: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue10: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue11: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue12: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue13: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue14: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue15: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue16: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraBits3: Long
    get() = integerValue(24)
    set(value) {
        integerValue(24, value)
    }

internal inline var CurlMeshKernel.penumbraAddress: Long
    get() = integerValue(22)
    set(value) {
        integerValue(22, value)
    }

internal inline var CurlMeshKernel.penumbraAddress2: Long
    get() = integerValue(23)
    set(value) {
        integerValue(23, value)
    }

internal inline var CurlMeshKernel.penumbraAddress3: Long
    get() = integerValue(23)
    set(value) {
        integerValue(23, value)
    }

internal inline var CurlMeshKernel.penumbraValue17: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline val CurlMeshKernel.penumbraColor: KernelVector
    get() = vectorValue(0)

internal inline var CurlMeshKernel.penumbraAddress4: Long
    get() = integerValue(26)
    set(value) {
        integerValue(26, value)
    }

internal inline var CurlMeshKernel.penumbraAddress5: Long
    get() = integerValue(26)
    set(value) {
        integerValue(26, value)
    }

internal inline var CurlMeshKernel.penumbraValue18: Long
    get() = vectorValue(0).longBits(0)
    set(value) {
        vectorValue(0).longBits(0, value)
    }

internal inline val CurlMeshKernel.penumbraValue19: KernelVector
    get() = vectorValue(0)

internal inline var CurlMeshKernel.penumbraAddress6: Long
    get() = integerValue(28)
    set(value) {
        integerValue(28, value)
    }

internal inline var CurlMeshKernel.penumbraAddress7: Long
    get() = integerValue(28)
    set(value) {
        integerValue(28, value)
    }

internal inline var CurlMeshKernel.penumbraBits4: Long
    get() = integerValue(27)
    set(value) {
        integerValue(27, value)
    }

internal inline var CurlMeshKernel.penumbraValue20: Double
    get() = vectorValue(12).double(0)
    set(value) {
        vectorValue(12).double(0, value)
    }

internal inline val CurlMeshKernel.penumbraValue21: KernelVector
    get() = vectorValue(11)

internal inline val CurlMeshKernel.penumbraValue22: KernelVector
    get() = vectorValue(8)

internal inline val CurlMeshKernel.penumbraValue23: KernelVector
    get() = vectorValue(4)

internal inline var CurlMeshKernel.penumbraValue24: Double
    get() = vectorValue(15).double(0)
    set(value) {
        vectorValue(15).double(0, value)
    }

internal inline val CurlMeshKernel.penumbraValue25: KernelVector
    get() = vectorValue(9)

internal inline val CurlMeshKernel.penumbraValue26: KernelVector
    get() = vectorValue(10)

internal inline val CurlMeshKernel.penumbraValue27: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.penumbraValue28: KernelVector
    get() = vectorValue(1)

internal inline val CurlMeshKernel.penumbraValue29: KernelVector
    get() = vectorValue(2)

internal inline var CurlMeshKernel.penumbraValue30: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.sineOutputAddress2: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline val CurlMeshKernel.penumbraValue31: KernelVector
    get() = vectorValue(14)

internal inline var CurlMeshKernel.penumbraValue32: Float
    get() = vectorValue(0).float(0)
    set(value) {
        vectorValue(0).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue33: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue34: Double
    get() = vectorValue(13).double(0)
    set(value) {
        vectorValue(13).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue35: Double
    get() = vectorValue(15).double(0)
    set(value) {
        vectorValue(15).double(0, value)
    }

internal inline var CurlMeshKernel.clipStartAddress3: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.clipEndAddress3: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.penumbraValue36: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue37: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue38: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue39: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline val CurlMeshKernel.penumbraValue40: KernelVector
    get() = vectorValue(4)

internal inline var CurlMeshKernel.penumbraValue41: Double
    get() = vectorValue(9).double(0)
    set(value) {
        vectorValue(9).double(0, value)
    }

internal inline val CurlMeshKernel.penumbraValue42: KernelVector
    get() = vectorValue(5)

internal inline var CurlMeshKernel.penumbraValue43: Double
    get() = vectorValue(7).double(0)
    set(value) {
        vectorValue(7).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue44: Double
    get() = vectorValue(6).double(0)
    set(value) {
        vectorValue(6).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue45: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue46: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue47: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue48: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue49: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue50: Float
    get() = vectorValue(0).float(0)
    set(value) {
        vectorValue(0).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue51: Float
    get() = vectorValue(1).float(0)
    set(value) {
        vectorValue(1).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue52: Float
    get() = vectorValue(3).float(0)
    set(value) {
        vectorValue(3).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue53: Float
    get() = vectorValue(2).float(0)
    set(value) {
        vectorValue(2).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue54: Float
    get() = vectorValue(2).float(0)
    set(value) {
        vectorValue(2).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue55: Float
    get() = vectorValue(3).float(0)
    set(value) {
        vectorValue(3).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue56: Float
    get() = vectorValue(1).float(0)
    set(value) {
        vectorValue(1).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue57: Float
    get() = vectorValue(0).float(0)
    set(value) {
        vectorValue(0).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue58: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue59: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue60: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue61: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue62: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue63: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue64: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue65: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue66: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue67: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue68: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline val CurlMeshKernel.penumbraValue69: KernelVector
    get() = vectorValue(0)

internal inline var CurlMeshKernel.penumbraValue70: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue71: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue72: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue73: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue74: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue75: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline val CurlMeshKernel.penumbraValue76: KernelVector
    get() = vectorValue(5)

internal inline val CurlMeshKernel.penumbraValue77: KernelVector
    get() = vectorValue(0)

internal inline var CurlMeshKernel.penumbraValue78: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue79: Float
    get() = vectorValue(2).float(0)
    set(value) {
        vectorValue(2).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraBits5: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.penumbraValue80: Float
    get() = vectorValue(1).float(0)
    set(value) {
        vectorValue(1).float(0, value)
    }

internal inline val CurlMeshKernel.penumbraColor2: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.penumbraValue81: KernelVector
    get() = vectorValue(5)

internal inline var CurlMeshKernel.penumbraValue82: Float
    get() = vectorValue(1).float(0)
    set(value) {
        vectorValue(1).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraBits6: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.penumbraValue83: Float
    get() = vectorValue(2).float(0)
    set(value) {
        vectorValue(2).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraIndex3: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.penumbraAddress8: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.penumbraIndex4: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.penumbraAddress9: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.penumbraAddress10: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.penumbraBits7: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.penumbraBits8: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline val CurlMeshKernel.penumbraValue84: KernelVector
    get() = vectorValue(7)

internal inline var CurlMeshKernel.penumbraIndex5: Long
    get() = integerValue(11)
    set(value) {
        integerValue(11, value)
    }

internal inline var CurlMeshKernel.penumbraAddress11: Long
    get() = integerValue(11)
    set(value) {
        integerValue(11, value)
    }

internal inline var CurlMeshKernel.penumbraAddress12: Long
    get() = integerValue(11)
    set(value) {
        integerValue(11, value)
    }

internal inline var CurlMeshKernel.penumbraAddress13: Long
    get() = integerValue(12)
    set(value) {
        integerValue(12, value)
    }

internal inline var CurlMeshKernel.penumbraAddress14: Long
    get() = integerValue(12)
    set(value) {
        integerValue(12, value)
    }

internal inline var CurlMeshKernel.penumbraAddress15: Long
    get() = integerValue(13)
    set(value) {
        integerValue(13, value)
    }

internal inline var CurlMeshKernel.penumbraAddress16: Long
    get() = integerValue(13)
    set(value) {
        integerValue(13, value)
    }

internal inline var CurlMeshKernel.penumbraAddress17: Long
    get() = integerValue(14)
    set(value) {
        integerValue(14, value)
    }

internal inline var CurlMeshKernel.penumbraAddress18: Long
    get() = integerValue(15)
    set(value) {
        integerValue(15, value)
    }

internal inline var CurlMeshKernel.penumbraIndex6: Long
    get() = integerValue(16)
    set(value) {
        integerValue(16, value)
    }

internal inline var CurlMeshKernel.penumbraBits9: Long
    get() = integerValue(17)
    set(value) {
        integerValue(17, value)
    }

internal inline var CurlMeshKernel.penumbraIndex7: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.penumbraIndex8: Long
    get() = integerValue(2)
    set(value) {
        integerValue(2, value)
    }

internal inline var CurlMeshKernel.penumbraAddress19: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.penumbraAddress20: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.penumbraValue85: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraBits10: Long
    get() = integerValue(4)
    set(value) {
        integerValue(4, value)
    }

internal inline var CurlMeshKernel.penumbraAddress21: Long
    get() = integerValue(5)
    set(value) {
        integerValue(5, value)
    }

internal inline var CurlMeshKernel.penumbraAddress22: Long
    get() = integerValue(5)
    set(value) {
        integerValue(5, value)
    }

internal inline var CurlMeshKernel.penumbraAddress23: Long
    get() = integerValue(6)
    set(value) {
        integerValue(6, value)
    }

internal inline var CurlMeshKernel.penumbraAddress24: Long
    get() = integerValue(6)
    set(value) {
        integerValue(6, value)
    }

internal inline val CurlMeshKernel.penumbraValue86: KernelVector
    get() = vectorValue(16)

internal inline var CurlMeshKernel.penumbraBits11: Long
    get() = integerValue(7)
    set(value) {
        integerValue(7, value)
    }

internal inline val CurlMeshKernel.penumbraValue87: KernelVector
    get() = vectorValue(2)

internal inline val CurlMeshKernel.penumbraValue88: KernelVector
    get() = vectorValue(1)

internal inline var CurlMeshKernel.penumbraBits12: Long
    get() = integerValue(28)
    set(value) {
        integerValue(28, value)
    }

internal inline var CurlMeshKernel.penumbraValue89: Double
    get() = vectorValue(25).double(0)
    set(value) {
        vectorValue(25).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue90: Double
    get() = vectorValue(26).double(0)
    set(value) {
        vectorValue(26).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraIndex9: Long
    get() = integerValue(21)
    set(value) {
        integerValue(21, value)
    }

internal inline var CurlMeshKernel.penumbraAddress25: Long
    get() = integerValue(25)
    set(value) {
        integerValue(25, value)
    }

internal inline var CurlMeshKernel.penumbraBits13: Long
    get() = integerValue(23)
    set(value) {
        integerValue(23, value)
    }

internal inline var CurlMeshKernel.penumbraBits14: Long
    get() = integerValue(25)
    set(value) {
        integerValue(25, value)
    }

internal inline var CurlMeshKernel.penumbraBits15: Long
    get() = integerValue(26)
    set(value) {
        integerValue(26, value)
    }

internal inline var CurlMeshKernel.penumbraAddress26: Long
    get() = integerValue(26)
    set(value) {
        integerValue(26, value)
    }

internal inline var CurlMeshKernel.penumbraBits16: Long
    get() = integerValue(25)
    set(value) {
        integerValue(25, value)
    }

internal inline var CurlMeshKernel.penumbraAddress27: Long
    get() = integerValue(25)
    set(value) {
        integerValue(25, value)
    }

internal inline var CurlMeshKernel.penumbraIndex10: Long
    get() = integerValue(23)
    set(value) {
        integerValue(23, value)
    }

internal inline var CurlMeshKernel.penumbraIndex11: Long
    get() = integerValue(21)
    set(value) {
        integerValue(21, value)
    }

internal inline var CurlMeshKernel.penumbraAddress28: Long
    get() = integerValue(25)
    set(value) {
        integerValue(25, value)
    }

internal inline var CurlMeshKernel.penumbraAddress29: Long
    get() = integerValue(25)
    set(value) {
        integerValue(25, value)
    }

internal inline var CurlMeshKernel.penumbraIndex12: Long
    get() = integerValue(26)
    set(value) {
        integerValue(26, value)
    }

internal inline var CurlMeshKernel.penumbraIndex13: Long
    get() = integerValue(25)
    set(value) {
        integerValue(25, value)
    }

internal inline var CurlMeshKernel.penumbraBits17: Long
    get() = integerValue(25)
    set(value) {
        integerValue(25, value)
    }

internal inline var CurlMeshKernel.penumbraIndex14: Long
    get() = integerValue(27)
    set(value) {
        integerValue(27, value)
    }

internal inline var CurlMeshKernel.penumbraBits18: Long
    get() = integerValue(25)
    set(value) {
        integerValue(25, value)
    }

internal inline var CurlMeshKernel.penumbraIndex15: Long
    get() = integerValue(25)
    set(value) {
        integerValue(25, value)
    }

internal inline var CurlMeshKernel.penumbraIndex16: Long
    get() = integerValue(27)
    set(value) {
        integerValue(27, value)
    }

internal inline var CurlMeshKernel.penumbraIndex17: Long
    get() = integerValue(27)
    set(value) {
        integerValue(27, value)
    }

internal inline var CurlMeshKernel.penumbraBits19: Long
    get() = integerValue(27)
    set(value) {
        integerValue(27, value)
    }

internal inline var CurlMeshKernel.penumbraBits20: Long
    get() = integerValue(25)
    set(value) {
        integerValue(25, value)
    }

internal inline var CurlMeshKernel.penumbraValue91: Float
    get() = vectorValue(5).float(0)
    set(value) {
        vectorValue(5).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue92: Float
    get() = vectorValue(6).float(0)
    set(value) {
        vectorValue(6).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue93: Float
    get() = vectorValue(17).float(0)
    set(value) {
        vectorValue(17).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue94: Float
    get() = vectorValue(18).float(0)
    set(value) {
        vectorValue(18).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue95: Double
    get() = vectorValue(6).double(0)
    set(value) {
        vectorValue(6).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue96: Double
    get() = vectorValue(5).double(0)
    set(value) {
        vectorValue(5).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue97: Double
    get() = vectorValue(18).double(0)
    set(value) {
        vectorValue(18).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue98: Double
    get() = vectorValue(17).double(0)
    set(value) {
        vectorValue(17).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue99: Double
    get() = vectorValue(18).double(0)
    set(value) {
        vectorValue(18).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue100: Double
    get() = vectorValue(6).double(0)
    set(value) {
        vectorValue(6).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue101: Double
    get() = vectorValue(5).double(0)
    set(value) {
        vectorValue(5).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue102: Double
    get() = vectorValue(5).double(0)
    set(value) {
        vectorValue(5).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue103: Double
    get() = vectorValue(5).double(0)
    set(value) {
        vectorValue(5).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue104: Double
    get() = vectorValue(17).double(0)
    set(value) {
        vectorValue(17).double(0, value)
    }

internal inline val CurlMeshKernel.penumbraValue105: KernelVector
    get() = vectorValue(5)

internal inline val CurlMeshKernel.penumbraValue106: KernelVector
    get() = vectorValue(6)

internal inline var CurlMeshKernel.penumbraValue107: Double
    get() = vectorValue(18).double(0)
    set(value) {
        vectorValue(18).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue108: Double
    get() = vectorValue(17).double(0)
    set(value) {
        vectorValue(17).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue109: Double
    get() = vectorValue(18).double(0)
    set(value) {
        vectorValue(18).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraIndex18: Long
    get() = integerValue(25)
    set(value) {
        integerValue(25, value)
    }

internal inline var CurlMeshKernel.penumbraIndex19: Long
    get() = integerValue(26)
    set(value) {
        integerValue(26, value)
    }

internal inline var CurlMeshKernel.penumbraBits21: Long
    get() = integerValue(25)
    set(value) {
        integerValue(25, value)
    }

internal inline var CurlMeshKernel.penumbraIndex20: Long
    get() = integerValue(21)
    set(value) {
        integerValue(21, value)
    }

internal inline var CurlMeshKernel.penumbraBits22: Long
    get() = integerValue(21)
    set(value) {
        integerValue(21, value)
    }

internal inline var CurlMeshKernel.penumbraIndex21: Long
    get() = integerValue(23)
    set(value) {
        integerValue(23, value)
    }

internal inline var CurlMeshKernel.penumbraBits23: Long
    get() = integerValue(21)
    set(value) {
        integerValue(21, value)
    }

internal inline var CurlMeshKernel.penumbraIndex22: Long
    get() = integerValue(21)
    set(value) {
        integerValue(21, value)
    }

internal inline var CurlMeshKernel.penumbraBits24: Long
    get() = integerValue(23)
    set(value) {
        integerValue(23, value)
    }

internal inline var CurlMeshKernel.penumbraBits25: Long
    get() = integerValue(21)
    set(value) {
        integerValue(21, value)
    }

internal inline var CurlMeshKernel.penumbraValue110: Float
    get() = vectorValue(7).float(0)
    set(value) {
        vectorValue(7).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue111: Float
    get() = vectorValue(16).float(0)
    set(value) {
        vectorValue(16).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue112: Float
    get() = vectorValue(17).float(0)
    set(value) {
        vectorValue(17).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue113: Float
    get() = vectorValue(18).float(0)
    set(value) {
        vectorValue(18).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue114: Double
    get() = vectorValue(19).double(0)
    set(value) {
        vectorValue(19).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue115: Double
    get() = vectorValue(7).double(0)
    set(value) {
        vectorValue(7).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue116: Double
    get() = vectorValue(18).double(0)
    set(value) {
        vectorValue(18).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue117: Double
    get() = vectorValue(16).double(0)
    set(value) {
        vectorValue(16).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue118: Double
    get() = vectorValue(16).double(0)
    set(value) {
        vectorValue(16).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue119: Double
    get() = vectorValue(7).double(0)
    set(value) {
        vectorValue(7).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue120: Double
    get() = vectorValue(17).double(0)
    set(value) {
        vectorValue(17).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue121: Double
    get() = vectorValue(17).double(0)
    set(value) {
        vectorValue(17).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue122: Double
    get() = vectorValue(17).double(0)
    set(value) {
        vectorValue(17).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue123: Double
    get() = vectorValue(17).double(0)
    set(value) {
        vectorValue(17).double(0, value)
    }

internal inline val CurlMeshKernel.penumbraValue124: KernelVector
    get() = vectorValue(7)

internal inline val CurlMeshKernel.penumbraValue125: KernelVector
    get() = vectorValue(16)

internal inline var CurlMeshKernel.penumbraValue126: Double
    get() = vectorValue(18).double(0)
    set(value) {
        vectorValue(18).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue127: Double
    get() = vectorValue(17).double(0)
    set(value) {
        vectorValue(17).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue128: Double
    get() = vectorValue(18).double(0)
    set(value) {
        vectorValue(18).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraIndex23: Long
    get() = integerValue(21)
    set(value) {
        integerValue(21, value)
    }

internal inline var CurlMeshKernel.penumbraIndex24: Long
    get() = integerValue(23)
    set(value) {
        integerValue(23, value)
    }

internal inline var CurlMeshKernel.penumbraIndex25: Long
    get() = integerValue(26)
    set(value) {
        integerValue(26, value)
    }

internal inline var CurlMeshKernel.penumbraIndex26: Long
    get() = integerValue(25)
    set(value) {
        integerValue(25, value)
    }

internal inline var CurlMeshKernel.penumbraIndex27: Long
    get() = integerValue(25)
    set(value) {
        integerValue(25, value)
    }

internal inline var CurlMeshKernel.penumbraBits26: Long
    get() = integerValue(25)
    set(value) {
        integerValue(25, value)
    }

internal inline var CurlMeshKernel.penumbraValue129: Float
    get() = vectorValue(7).float(0)
    set(value) {
        vectorValue(7).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue130: Float
    get() = vectorValue(17).float(0)
    set(value) {
        vectorValue(17).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue131: Double
    get() = vectorValue(16).double(0)
    set(value) {
        vectorValue(16).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue132: Double
    get() = vectorValue(17).double(0)
    set(value) {
        vectorValue(17).double(0, value)
    }

internal inline val CurlMeshKernel.penumbraValue133: KernelVector
    get() = vectorValue(18)

internal inline var CurlMeshKernel.penumbraValue134: Double
    get() = vectorValue(7).double(0)
    set(value) {
        vectorValue(7).double(0, value)
    }

internal inline val CurlMeshKernel.penumbraColor3: KernelVector
    get() = vectorValue(18)

internal inline var CurlMeshKernel.penumbraValue135: Double
    get() = vectorValue(19).double(0)
    set(value) {
        vectorValue(19).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue136: Double
    get() = vectorValue(20).double(0)
    set(value) {
        vectorValue(20).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue137: Float
    get() = vectorValue(4).float(0)
    set(value) {
        vectorValue(4).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraBits27: Long
    get() = integerValue(25)
    set(value) {
        integerValue(25, value)
    }

internal inline var CurlMeshKernel.penumbraValue138: Float
    get() = vectorValue(3).float(0)
    set(value) {
        vectorValue(3).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue139: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue140: Double
    get() = vectorValue(20).double(0)
    set(value) {
        vectorValue(20).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue141: Float
    get() = vectorValue(20).float(0)
    set(value) {
        vectorValue(20).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraBits28: Long
    get() = integerValue(22)
    set(value) {
        integerValue(22, value)
    }

internal inline var CurlMeshKernel.penumbraValue142: Float
    get() = vectorValue(21).float(0)
    set(value) {
        vectorValue(21).float(0, value)
    }

internal inline val CurlMeshKernel.penumbraValue143: KernelVector
    get() = vectorValue(19)

internal inline val CurlMeshKernel.penumbraValue144: KernelVector
    get() = vectorValue(19)

internal inline val CurlMeshKernel.penumbraColor4: KernelVector
    get() = vectorValue(19)

internal inline var CurlMeshKernel.penumbraIndex28: Long
    get() = integerValue(21)
    set(value) {
        integerValue(21, value)
    }

internal inline var CurlMeshKernel.penumbraIndex29: Long
    get() = integerValue(23)
    set(value) {
        integerValue(23, value)
    }

internal inline var CurlMeshKernel.penumbraBits29: Long
    get() = integerValue(25)
    set(value) {
        integerValue(25, value)
    }

internal inline var CurlMeshKernel.penumbraValue145: Float
    get() = vectorValue(16).float(0)
    set(value) {
        vectorValue(16).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue146: Float
    get() = vectorValue(17).float(0)
    set(value) {
        vectorValue(17).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue147: Double
    get() = vectorValue(16).double(0)
    set(value) {
        vectorValue(16).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue148: Double
    get() = vectorValue(17).double(0)
    set(value) {
        vectorValue(17).double(0, value)
    }

internal inline val CurlMeshKernel.penumbraValue149: KernelVector
    get() = vectorValue(18)

internal inline val CurlMeshKernel.penumbraColor5: KernelVector
    get() = vectorValue(18)

internal inline var CurlMeshKernel.penumbraValue150: Double
    get() = vectorValue(19).double(0)
    set(value) {
        vectorValue(19).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue151: Double
    get() = vectorValue(20).double(0)
    set(value) {
        vectorValue(20).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue152: Float
    get() = vectorValue(4).float(0)
    set(value) {
        vectorValue(4).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraBits30: Long
    get() = integerValue(25)
    set(value) {
        integerValue(25, value)
    }

internal inline var CurlMeshKernel.penumbraValue153: Float
    get() = vectorValue(3).float(0)
    set(value) {
        vectorValue(3).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue154: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue155: Double
    get() = vectorValue(20).double(0)
    set(value) {
        vectorValue(20).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue156: Float
    get() = vectorValue(20).float(0)
    set(value) {
        vectorValue(20).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraBits31: Long
    get() = integerValue(22)
    set(value) {
        integerValue(22, value)
    }

internal inline var CurlMeshKernel.penumbraValue157: Float
    get() = vectorValue(21).float(0)
    set(value) {
        vectorValue(21).float(0, value)
    }

internal inline val CurlMeshKernel.penumbraValue158: KernelVector
    get() = vectorValue(19)

internal inline val CurlMeshKernel.penumbraValue159: KernelVector
    get() = vectorValue(19)

internal inline val CurlMeshKernel.penumbraColor6: KernelVector
    get() = vectorValue(19)

internal inline var CurlMeshKernel.penumbraAddress30: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.penumbraAddress31: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.penumbraIndex30: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.penumbraValue160: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraIndex31: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.penumbraAddress32: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.penumbraBits32: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.penumbraAddress33: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.penumbraAddress34: Long
    get() = integerValue(11)
    set(value) {
        integerValue(11, value)
    }

internal inline var CurlMeshKernel.penumbraAddress35: Long
    get() = integerValue(12)
    set(value) {
        integerValue(12, value)
    }

internal inline var CurlMeshKernel.penumbraAddress36: Long
    get() = integerValue(13)
    set(value) {
        integerValue(13, value)
    }

internal inline var CurlMeshKernel.penumbraAddress37: Long
    get() = integerValue(14)
    set(value) {
        integerValue(14, value)
    }

internal inline var CurlMeshKernel.penumbraAddress38: Long
    get() = integerValue(15)
    set(value) {
        integerValue(15, value)
    }

internal inline var CurlMeshKernel.penumbraAddress39: Long
    get() = integerValue(16)
    set(value) {
        integerValue(16, value)
    }

internal inline var CurlMeshKernel.penumbraAddress40: Long
    get() = integerValue(16)
    set(value) {
        integerValue(16, value)
    }

internal inline var CurlMeshKernel.penumbraAddress41: Long
    get() = integerValue(17)
    set(value) {
        integerValue(17, value)
    }

internal inline var CurlMeshKernel.penumbraAddress42: Long
    get() = integerValue(17)
    set(value) {
        integerValue(17, value)
    }

internal inline var CurlMeshKernel.penumbraBits33: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.penumbraBits34: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.penumbraBits35: Long
    get() = integerValue(5)
    set(value) {
        integerValue(5, value)
    }

internal inline var CurlMeshKernel.penumbraBits36: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.penumbraBits37: Long
    get() = integerValue(4)
    set(value) {
        integerValue(4, value)
    }

internal inline var CurlMeshKernel.penumbraBits38: Long
    get() = integerValue(2)
    set(value) {
        integerValue(2, value)
    }

internal inline var CurlMeshKernel.penumbraBits39: Long
    get() = integerValue(2)
    set(value) {
        integerValue(2, value)
    }

internal inline var CurlMeshKernel.penumbraBits40: Long
    get() = integerValue(6)
    set(value) {
        integerValue(6, value)
    }

internal inline var CurlMeshKernel.penumbraIndex32: Long
    get() = integerValue(4)
    set(value) {
        integerValue(4, value)
    }

internal inline var CurlMeshKernel.penumbraIndex33: Long
    get() = integerValue(5)
    set(value) {
        integerValue(5, value)
    }

internal inline var CurlMeshKernel.penumbraIndex34: Long
    get() = integerValue(5)
    set(value) {
        integerValue(5, value)
    }

internal inline var CurlMeshKernel.penumbraBits41: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline val CurlMeshKernel.penumbraValue161: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.penumbraValue162: KernelVector
    get() = vectorValue(0)

internal inline var CurlMeshKernel.penumbraAddress43: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.penumbraBits42: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.penumbraBits43: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline val CurlMeshKernel.penumbraValue163: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.penumbraValue164: KernelVector
    get() = vectorValue(0)

internal inline var CurlMeshKernel.penumbraBits44: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.penumbraBits45: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.penumbraIndex35: Long
    get() = integerValue(4)
    set(value) {
        integerValue(4, value)
    }

internal inline var CurlMeshKernel.penumbraIndex36: Long
    get() = integerValue(5)
    set(value) {
        integerValue(5, value)
    }

internal inline var CurlMeshKernel.penumbraIndex37: Long
    get() = integerValue(5)
    set(value) {
        integerValue(5, value)
    }

internal inline var CurlMeshKernel.penumbraBits46: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline val CurlMeshKernel.penumbraValue165: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.penumbraValue166: KernelVector
    get() = vectorValue(0)

internal inline var CurlMeshKernel.penumbraAddress44: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.penumbraBits47: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.penumbraBits48: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline val CurlMeshKernel.penumbraValue167: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.penumbraValue168: KernelVector
    get() = vectorValue(0)

internal inline var CurlMeshKernel.penumbraIndex38: Long
    get() = integerValue(4)
    set(value) {
        integerValue(4, value)
    }

internal inline var CurlMeshKernel.penumbraBits49: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline val CurlMeshKernel.penumbraValue169: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.penumbraValue170: KernelVector
    get() = vectorValue(0)

internal inline var CurlMeshKernel.penumbraAddress45: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.penumbraBits50: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.penumbraIndex39: Long
    get() = integerValue(4)
    set(value) {
        integerValue(4, value)
    }

internal inline var CurlMeshKernel.penumbraBits51: Long
    get() = integerValue(5)
    set(value) {
        integerValue(5, value)
    }

internal inline var CurlMeshKernel.penumbraBits52: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline val CurlMeshKernel.penumbraValue171: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.penumbraValue172: KernelVector
    get() = vectorValue(0)

internal inline var CurlMeshKernel.penumbraIndex40: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.penumbraIndex41: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.penumbraValue173: Float
    get() = vectorValue(0).float(0)
    set(value) {
        vectorValue(0).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue174: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraBits53: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.penumbraValue175: Float
    get() = vectorValue(0).float(0)
    set(value) {
        vectorValue(0).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue176: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraIndex42: Long
    get() = integerValue(25)
    set(value) {
        integerValue(25, value)
    }

internal inline var CurlMeshKernel.penumbraIndex43: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.penumbraAddress46: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.penumbraAddress47: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.penumbraAddress48: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.penumbraBits54: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.penumbraValue177: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraIndex44: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.penumbraIndex45: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.penumbraAddress49: Long
    get() = integerValue(11)
    set(value) {
        integerValue(11, value)
    }

internal inline var CurlMeshKernel.penumbraAddress50: Long
    get() = integerValue(11)
    set(value) {
        integerValue(11, value)
    }

internal inline var CurlMeshKernel.penumbraBits55: Long
    get() = integerValue(12)
    set(value) {
        integerValue(12, value)
    }

internal inline var CurlMeshKernel.penumbraValue178: Float
    get() = vectorValue(0).float(0)
    set(value) {
        vectorValue(0).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue179: Float
    get() = vectorValue(1).float(0)
    set(value) {
        vectorValue(1).float(0, value)
    }

internal inline val CurlMeshKernel.penumbraValue180: KernelVector
    get() = vectorValue(3)

internal inline val CurlMeshKernel.penumbraValue181: KernelVector
    get() = vectorValue(4)

internal inline var CurlMeshKernel.penumbraBits56: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.penumbraBits57: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.penumbraAddress51: Long
    get() = integerValue(11)
    set(value) {
        integerValue(11, value)
    }

internal inline var CurlMeshKernel.penumbraAddress52: Long
    get() = integerValue(11)
    set(value) {
        integerValue(11, value)
    }

internal inline var CurlMeshKernel.penumbraBits58: Long
    get() = integerValue(13)
    set(value) {
        integerValue(13, value)
    }

internal inline var CurlMeshKernel.penumbraValue182: Float
    get() = vectorValue(0).float(0)
    set(value) {
        vectorValue(0).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue183: Float
    get() = vectorValue(1).float(0)
    set(value) {
        vectorValue(1).float(0, value)
    }

internal inline val CurlMeshKernel.penumbraValue184: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.penumbraValue185: KernelVector
    get() = vectorValue(1)

internal inline var CurlMeshKernel.penumbraAddress53: Long
    get() = integerValue(11)
    set(value) {
        integerValue(11, value)
    }

internal inline var CurlMeshKernel.penumbraAddress54: Long
    get() = integerValue(13)
    set(value) {
        integerValue(13, value)
    }

internal inline val CurlMeshKernel.penumbraValue186: KernelVector
    get() = vectorValue(5)

internal inline var CurlMeshKernel.penumbraBits59: Long
    get() = integerValue(14)
    set(value) {
        integerValue(14, value)
    }

internal inline val CurlMeshKernel.penumbraColor7: KernelVector
    get() = vectorValue(5)

internal inline var CurlMeshKernel.penumbraBits60: Long
    get() = integerValue(15)
    set(value) {
        integerValue(15, value)
    }

internal inline var CurlMeshKernel.penumbraAddress55: Long
    get() = integerValue(16)
    set(value) {
        integerValue(16, value)
    }

internal inline var CurlMeshKernel.penumbraAddress56: Long
    get() = integerValue(16)
    set(value) {
        integerValue(16, value)
    }

internal inline var CurlMeshKernel.penumbraValue187: Double
    get() = vectorValue(6).double(0)
    set(value) {
        vectorValue(6).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraBits61: Long
    get() = integerValue(17)
    set(value) {
        integerValue(17, value)
    }

internal inline var CurlMeshKernel.penumbraBits62: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline val CurlMeshKernel.penumbraValue188: KernelVector
    get() = vectorValue(17)

internal inline val CurlMeshKernel.penumbraValue189: KernelVector
    get() = vectorValue(18)

internal inline val CurlMeshKernel.penumbraValue190: KernelVector
    get() = vectorValue(7)

internal inline val CurlMeshKernel.penumbraValue191: KernelVector
    get() = vectorValue(16)

internal inline var CurlMeshKernel.penumbraValue192: Double
    get() = vectorValue(19).double(0)
    set(value) {
        vectorValue(19).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue193: Double
    get() = vectorValue(20).double(0)
    set(value) {
        vectorValue(20).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue194: Float
    get() = vectorValue(18).float(0)
    set(value) {
        vectorValue(18).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraBits63: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.penumbraValue195: Float
    get() = vectorValue(17).float(0)
    set(value) {
        vectorValue(17).float(0, value)
    }

internal inline val CurlMeshKernel.penumbraValue196: KernelVector
    get() = vectorValue(21)

internal inline val CurlMeshKernel.penumbraValue197: KernelVector
    get() = vectorValue(17)

internal inline val CurlMeshKernel.penumbraColor8: KernelVector
    get() = vectorValue(17)

internal inline val CurlMeshKernel.penumbraValue198: KernelVector
    get() = vectorValue(17)

internal inline val CurlMeshKernel.penumbraColor9: KernelVector
    get() = vectorValue(19)

internal inline var CurlMeshKernel.penumbraValue199: Double
    get() = vectorValue(17).double(0)
    set(value) {
        vectorValue(17).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue200: Double
    get() = vectorValue(22).double(0)
    set(value) {
        vectorValue(22).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue201: Double
    get() = vectorValue(17).double(0)
    set(value) {
        vectorValue(17).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue202: Double
    get() = vectorValue(23).double(0)
    set(value) {
        vectorValue(23).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue203: Float
    get() = vectorValue(20).float(0)
    set(value) {
        vectorValue(20).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue204: Float
    get() = vectorValue(24).float(0)
    set(value) {
        vectorValue(24).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue205: Float
    get() = vectorValue(16).float(0)
    set(value) {
        vectorValue(16).float(0, value)
    }

internal inline val CurlMeshKernel.penumbraValue206: KernelVector
    get() = vectorValue(7)

internal inline val CurlMeshKernel.penumbraValue207: KernelVector
    get() = vectorValue(7)

internal inline val CurlMeshKernel.penumbraColor10: KernelVector
    get() = vectorValue(20)

internal inline var CurlMeshKernel.penumbraValue208: Float
    get() = vectorValue(21).float(0)
    set(value) {
        vectorValue(21).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue209: Float
    get() = vectorValue(22).float(0)
    set(value) {
        vectorValue(22).float(0, value)
    }

internal inline val CurlMeshKernel.penumbraValue210: KernelVector
    get() = vectorValue(2)

internal inline val CurlMeshKernel.penumbraValue211: KernelVector
    get() = vectorValue(2)

internal inline var CurlMeshKernel.penumbraAddress57: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.penumbraAddress58: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.penumbraBits64: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline val CurlMeshKernel.penumbraValue212: KernelVector
    get() = vectorValue(2)

internal inline val CurlMeshKernel.penumbraValue213: KernelVector
    get() = vectorValue(2)

internal inline var CurlMeshKernel.penumbraAddress59: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.penumbraAddress60: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline val CurlMeshKernel.penumbraValue214: KernelVector
    get() = vectorValue(3)

internal inline val CurlMeshKernel.penumbraValue215: KernelVector
    get() = vectorValue(3)

internal inline val CurlMeshKernel.penumbraValue216: KernelVector
    get() = vectorValue(2)

internal inline val CurlMeshKernel.penumbraValue217: KernelVector
    get() = vectorValue(2)

internal inline var CurlMeshKernel.penumbraAddress61: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.penumbraAddress62: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.penumbraBits65: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline val CurlMeshKernel.penumbraValue218: KernelVector
    get() = vectorValue(3)

internal inline val CurlMeshKernel.penumbraValue219: KernelVector
    get() = vectorValue(3)

internal inline var CurlMeshKernel.penumbraIndex46: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.penumbraIndex47: Long
    get() = integerValue(21)
    set(value) {
        integerValue(21, value)
    }

internal inline var CurlMeshKernel.penumbraAddress63: Long
    get() = integerValue(22)
    set(value) {
        integerValue(22, value)
    }

internal inline var CurlMeshKernel.penumbraAddress64: Long
    get() = integerValue(22)
    set(value) {
        integerValue(22, value)
    }

internal inline var CurlMeshKernel.penumbraValue220: Double
    get() = vectorValue(8).double(0)
    set(value) {
        vectorValue(8).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraAddress65: Long
    get() = integerValue(27)
    set(value) {
        integerValue(27, value)
    }

internal inline var CurlMeshKernel.penumbraBits66: Long
    get() = integerValue(28)
    set(value) {
        integerValue(28, value)
    }

internal inline var CurlMeshKernel.penumbraValue221: Long
    get() = vectorValue(20).longBits(0)
    set(value) {
        vectorValue(20).longBits(0, value)
    }

internal inline val CurlMeshKernel.penumbraValue222: KernelVector
    get() = vectorValue(20)

internal inline var CurlMeshKernel.penumbraBits67: Long
    get() = integerValue(20)
    set(value) {
        integerValue(20, value)
    }

internal inline var CurlMeshKernel.penumbraBits68: Long
    get() = integerValue(23)
    set(value) {
        integerValue(23, value)
    }

internal inline var CurlMeshKernel.penumbraIndex48: Long
    get() = integerValue(24)
    set(value) {
        integerValue(24, value)
    }

internal inline var CurlMeshKernel.penumbraBits69: Long
    get() = integerValue(26)
    set(value) {
        integerValue(26, value)
    }

internal inline var CurlMeshKernel.penumbraAddress66: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.penumbraBits70: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.penumbraAddress67: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.penumbraBits71: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.penumbraValue223: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue224: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline val CurlMeshKernel.penumbraValue225: KernelVector
    get() = vectorValue(9)

internal inline val CurlMeshKernel.penumbraValue226: KernelVector
    get() = vectorValue(10)

internal inline var CurlMeshKernel.penumbraValue227: Double
    get() = vectorValue(12).double(0)
    set(value) {
        vectorValue(12).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue228: Double
    get() = vectorValue(11).double(0)
    set(value) {
        vectorValue(11).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraAddress68: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.penumbraBits72: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.penumbraValue229: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue230: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue231: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue232: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue233: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue234: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue235: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue236: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue237: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue238: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue239: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue240: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue241: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue242: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue243: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue244: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue245: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue246: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue247: Float
    get() = vectorValue(0).float(0)
    set(value) {
        vectorValue(0).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue248: Float
    get() = vectorValue(0).float(0)
    set(value) {
        vectorValue(0).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue249: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue250: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.rotationMatrixAddress: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline val CurlMeshKernel.penumbraValue251: KernelVector
    get() = vectorValue(20)

internal inline var CurlMeshKernel.penumbraIndex49: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.penumbraAddress69: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline val CurlMeshKernel.penumbraValue252: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.penumbraColor11: KernelVector
    get() = vectorValue(0)

internal inline var CurlMeshKernel.penumbraValue253: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue254: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue255: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue256: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue257: Double
    get() = vectorValue(5).double(0)
    set(value) {
        vectorValue(5).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue258: Double
    get() = vectorValue(6).double(0)
    set(value) {
        vectorValue(6).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraBits73: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.penumbraIndex50: Long
    get() = integerValue(11)
    set(value) {
        integerValue(11, value)
    }

internal inline var CurlMeshKernel.penumbraBits74: Long
    get() = integerValue(12)
    set(value) {
        integerValue(12, value)
    }

internal inline var CurlMeshKernel.penumbraBits75: Long
    get() = integerValue(13)
    set(value) {
        integerValue(13, value)
    }

internal inline val CurlMeshKernel.penumbraValue259: KernelVector
    get() = vectorValue(7)

internal inline val CurlMeshKernel.penumbraValue260: KernelVector
    get() = vectorValue(16)

internal inline var CurlMeshKernel.penumbraBits76: Long
    get() = integerValue(14)
    set(value) {
        integerValue(14, value)
    }

internal inline var CurlMeshKernel.penumbraValue261: Double
    get() = vectorValue(17).double(0)
    set(value) {
        vectorValue(17).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue262: Double
    get() = vectorValue(18).double(0)
    set(value) {
        vectorValue(18).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue263: Float
    get() = vectorValue(16).float(0)
    set(value) {
        vectorValue(16).float(0, value)
    }

internal inline val CurlMeshKernel.penumbraValue264: KernelVector
    get() = vectorValue(19)

internal inline var CurlMeshKernel.penumbraValue265: Float
    get() = vectorValue(7).float(0)
    set(value) {
        vectorValue(7).float(0, value)
    }

internal inline val CurlMeshKernel.penumbraValue266: KernelVector
    get() = vectorValue(18)

internal inline val CurlMeshKernel.penumbraValue267: KernelVector
    get() = vectorValue(17)

internal inline val CurlMeshKernel.penumbraValue268: KernelVector
    get() = vectorValue(17)

internal inline val CurlMeshKernel.penumbraColor12: KernelVector
    get() = vectorValue(17)

internal inline var CurlMeshKernel.penumbraBits77: Long
    get() = integerValue(15)
    set(value) {
        integerValue(15, value)
    }

internal inline var CurlMeshKernel.penumbraValue269: Float
    get() = vectorValue(7).float(0)
    set(value) {
        vectorValue(7).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue270: Float
    get() = vectorValue(7).float(0)
    set(value) {
        vectorValue(7).float(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue271: Double
    get() = vectorValue(7).double(0)
    set(value) {
        vectorValue(7).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue272: Double
    get() = vectorValue(7).double(0)
    set(value) {
        vectorValue(7).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue273: Double
    get() = vectorValue(16).double(0)
    set(value) {
        vectorValue(16).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraValue274: Double
    get() = vectorValue(16).double(0)
    set(value) {
        vectorValue(16).double(0, value)
    }

internal inline var CurlMeshKernel.penumbraAddress70: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.penumbraIndex51: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.faceBits: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.faceValue: Double
    get() = vectorValue(6).double(0)
    set(value) {
        vectorValue(6).double(0, value)
    }

internal inline val CurlMeshKernel.faceValue2: KernelVector
    get() = vectorValue(9)

internal inline var CurlMeshKernel.faceValue3: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue4: Double
    get() = vectorValue(8).double(0)
    set(value) {
        vectorValue(8).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue5: Double
    get() = vectorValue(22).double(0)
    set(value) {
        vectorValue(22).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue6: Double
    get() = vectorValue(23).double(0)
    set(value) {
        vectorValue(23).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue7: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue8: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue9: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue10: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue11: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue12: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue13: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue14: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue15: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue16: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue17: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue18: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue19: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue20: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue21: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline val CurlMeshKernel.faceValue22: KernelVector
    get() = vectorValue(5)

internal inline var CurlMeshKernel.faceValue23: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue24: Double
    get() = vectorValue(5).double(0)
    set(value) {
        vectorValue(5).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue25: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue26: Float
    get() = vectorValue(4).float(0)
    set(value) {
        vectorValue(4).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue27: Float
    get() = vectorValue(4).float(0)
    set(value) {
        vectorValue(4).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue28: Float
    get() = vectorValue(5).float(0)
    set(value) {
        vectorValue(5).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue29: Float
    get() = vectorValue(4).float(0)
    set(value) {
        vectorValue(4).float(0, value)
    }

internal inline var CurlMeshKernel.faceIndex: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.faceIndex2: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.faceValue30: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.faceAddress: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.faceBits2: Long
    get() = integerValue(11)
    set(value) {
        integerValue(11, value)
    }

internal inline var CurlMeshKernel.faceValue31: Float
    get() = vectorValue(3).float(0)
    set(value) {
        vectorValue(3).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue32: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue33: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue34: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue35: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.faceBits3: Long
    get() = integerValue(11)
    set(value) {
        integerValue(11, value)
    }

internal inline var CurlMeshKernel.faceValue36: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue37: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue38: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue39: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue40: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.faceIndex3: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.faceValue41: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue42: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue43: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue44: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue45: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.faceIndex4: Long
    get() = integerValue(15)
    set(value) {
        integerValue(15, value)
    }

internal inline var CurlMeshKernel.faceIndex5: Long
    get() = integerValue(22)
    set(value) {
        integerValue(22, value)
    }

internal inline var CurlMeshKernel.faceAddress2: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.faceValue46: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue47: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceIndex6: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.faceIndex7: Long
    get() = integerValue(11)
    set(value) {
        integerValue(11, value)
    }

internal inline var CurlMeshKernel.faceAddress3: Long
    get() = integerValue(12)
    set(value) {
        integerValue(12, value)
    }

internal inline var CurlMeshKernel.faceAddress4: Long
    get() = integerValue(13)
    set(value) {
        integerValue(13, value)
    }

internal inline var CurlMeshKernel.faceAddress5: Long
    get() = integerValue(14)
    set(value) {
        integerValue(14, value)
    }

internal inline var CurlMeshKernel.faceAddress6: Long
    get() = integerValue(15)
    set(value) {
        integerValue(15, value)
    }

internal inline val CurlMeshKernel.faceValue48: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.faceValue49: KernelVector
    get() = vectorValue(1)

internal inline val CurlMeshKernel.faceValue50: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.faceValue51: KernelVector
    get() = vectorValue(1)

internal inline val CurlMeshKernel.faceValue52: KernelVector
    get() = vectorValue(2)

internal inline val CurlMeshKernel.faceValue53: KernelVector
    get() = vectorValue(3)

internal inline val CurlMeshKernel.faceValue54: KernelVector
    get() = vectorValue(4)

internal inline var CurlMeshKernel.sineOutputAddress3: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.faceValue55: Float
    get() = vectorValue(0).float(0)
    set(value) {
        vectorValue(0).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue56: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline val CurlMeshKernel.faceValue57: KernelVector
    get() = vectorValue(3)

internal inline var CurlMeshKernel.faceValue58: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue59: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue60: Float
    get() = vectorValue(1).float(0)
    set(value) {
        vectorValue(1).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue61: Float
    get() = vectorValue(1).float(0)
    set(value) {
        vectorValue(1).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue62: Float
    get() = vectorValue(2).float(0)
    set(value) {
        vectorValue(2).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue63: Float
    get() = vectorValue(4).float(0)
    set(value) {
        vectorValue(4).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue64: Float
    get() = vectorValue(2).float(0)
    set(value) {
        vectorValue(2).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue65: Float
    get() = vectorValue(4).float(0)
    set(value) {
        vectorValue(4).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue66: Float
    get() = vectorValue(2).float(0)
    set(value) {
        vectorValue(2).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue67: Float
    get() = vectorValue(4).float(0)
    set(value) {
        vectorValue(4).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue68: Float
    get() = vectorValue(1).float(0)
    set(value) {
        vectorValue(1).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue69: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue70: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue71: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue72: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue73: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue74: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue75: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue76: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue77: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue78: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue79: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue80: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue81: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue82: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue83: Float
    get() = vectorValue(0).float(0)
    set(value) {
        vectorValue(0).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue84: Float
    get() = vectorValue(0).float(0)
    set(value) {
        vectorValue(0).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue85: Float
    get() = vectorValue(0).float(0)
    set(value) {
        vectorValue(0).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue86: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue87: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue88: Double
    get() = vectorValue(6).double(0)
    set(value) {
        vectorValue(6).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue89: Double
    get() = vectorValue(5).double(0)
    set(value) {
        vectorValue(5).double(0, value)
    }

internal inline val CurlMeshKernel.faceValue90: KernelVector
    get() = vectorValue(1)

internal inline val CurlMeshKernel.faceValue91: KernelVector
    get() = vectorValue(12)

internal inline var CurlMeshKernel.faceIndex8: Long
    get() = integerValue(20)
    set(value) {
        integerValue(20, value)
    }

internal inline var CurlMeshKernel.faceValue92: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue93: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue94: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceBits4: Long
    get() = integerValue(21)
    set(value) {
        integerValue(21, value)
    }

internal inline var CurlMeshKernel.faceAddress7: Long
    get() = integerValue(23)
    set(value) {
        integerValue(23, value)
    }

internal inline var CurlMeshKernel.faceValue95: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue96: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue97: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline val CurlMeshKernel.faceValue98: KernelVector
    get() = vectorValue(10)

internal inline val CurlMeshKernel.faceValue99: KernelVector
    get() = vectorValue(8)

internal inline val CurlMeshKernel.faceValue100: KernelVector
    get() = vectorValue(9)

internal inline val CurlMeshKernel.faceValue101: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.faceValue102: KernelVector
    get() = vectorValue(2)

internal inline val CurlMeshKernel.faceValue103: KernelVector
    get() = vectorValue(3)

internal inline val CurlMeshKernel.faceValue104: KernelVector
    get() = vectorValue(4)

internal inline var CurlMeshKernel.sineOutputAddress4: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.faceValue105: Float
    get() = vectorValue(0).float(0)
    set(value) {
        vectorValue(0).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue106: Double
    get() = vectorValue(12).double(0)
    set(value) {
        vectorValue(12).double(0, value)
    }

internal inline var CurlMeshKernel.clipStartAddress4: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.clipEndAddress4: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.faceValue107: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue108: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue109: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue110: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline val CurlMeshKernel.faceValue111: KernelVector
    get() = vectorValue(4)

internal inline val CurlMeshKernel.faceValue112: KernelVector
    get() = vectorValue(5)

internal inline var CurlMeshKernel.faceValue113: Double
    get() = vectorValue(7).double(0)
    set(value) {
        vectorValue(7).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue114: Double
    get() = vectorValue(6).double(0)
    set(value) {
        vectorValue(6).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue115: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue116: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue117: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue118: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue119: Float
    get() = vectorValue(0).float(0)
    set(value) {
        vectorValue(0).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue120: Float
    get() = vectorValue(0).float(0)
    set(value) {
        vectorValue(0).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue121: Float
    get() = vectorValue(1).float(0)
    set(value) {
        vectorValue(1).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue122: Float
    get() = vectorValue(2).float(0)
    set(value) {
        vectorValue(2).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue123: Float
    get() = vectorValue(1).float(0)
    set(value) {
        vectorValue(1).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue124: Float
    get() = vectorValue(2).float(0)
    set(value) {
        vectorValue(2).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue125: Float
    get() = vectorValue(1).float(0)
    set(value) {
        vectorValue(1).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue126: Float
    get() = vectorValue(2).float(0)
    set(value) {
        vectorValue(2).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue127: Float
    get() = vectorValue(0).float(0)
    set(value) {
        vectorValue(0).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue128: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue129: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue130: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue131: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue132: Double
    get() = vectorValue(14).double(0)
    set(value) {
        vectorValue(14).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue133: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue134: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue135: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue136: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline val CurlMeshKernel.faceValue137: KernelVector
    get() = vectorValue(9)

internal inline var CurlMeshKernel.faceValue138: Double
    get() = vectorValue(13).double(0)
    set(value) {
        vectorValue(13).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue139: Double
    get() = vectorValue(11).double(0)
    set(value) {
        vectorValue(11).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue140: Double
    get() = vectorValue(15).double(0)
    set(value) {
        vectorValue(15).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue141: Double
    get() = vectorValue(8).double(0)
    set(value) {
        vectorValue(8).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue142: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue143: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue144: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue145: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue146: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue147: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline val CurlMeshKernel.faceValue148: KernelVector
    get() = vectorValue(1)

internal inline var CurlMeshKernel.faceValue149: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue150: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue151: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue152: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue153: Float
    get() = vectorValue(0).float(0)
    set(value) {
        vectorValue(0).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue154: Float
    get() = vectorValue(0).float(0)
    set(value) {
        vectorValue(0).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue155: Float
    get() = vectorValue(0).float(0)
    set(value) {
        vectorValue(0).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue156: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue157: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue158: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue159: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue160: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue161: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue162: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue163: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue164: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue165: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue166: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue167: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue168: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue169: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline val CurlMeshKernel.faceValue170: KernelVector
    get() = vectorValue(2)

internal inline val CurlMeshKernel.faceValue171: KernelVector
    get() = vectorValue(3)

internal inline var CurlMeshKernel.faceValue172: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue173: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue174: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue175: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue176: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue177: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue178: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue179: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue180: Double
    get() = vectorValue(2).double(0)
    set(value) {
        vectorValue(2).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue181: Double
    get() = vectorValue(1).double(0)
    set(value) {
        vectorValue(1).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue182: Double
    get() = vectorValue(3).double(0)
    set(value) {
        vectorValue(3).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue183: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline val CurlMeshKernel.faceValue184: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.faceValue185: KernelVector
    get() = vectorValue(3)

internal inline val CurlMeshKernel.faceColor: KernelVector
    get() = vectorValue(3)

internal inline val CurlMeshKernel.faceValue186: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.faceColor2: KernelVector
    get() = vectorValue(0)

internal inline var CurlMeshKernel.faceValue187: Float
    get() = vectorValue(0).float(0)
    set(value) {
        vectorValue(0).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue188: Float
    get() = vectorValue(4).float(0)
    set(value) {
        vectorValue(4).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue189: Float
    get() = vectorValue(2).float(0)
    set(value) {
        vectorValue(2).float(0, value)
    }

internal inline var CurlMeshKernel.faceValue190: Float
    get() = vectorValue(1).float(0)
    set(value) {
        vectorValue(1).float(0, value)
    }

internal inline val CurlMeshKernel.faceValue191: KernelVector
    get() = vectorValue(2)

internal inline val CurlMeshKernel.faceValue192: KernelVector
    get() = vectorValue(3)

internal inline val CurlMeshKernel.faceValue193: KernelVector
    get() = vectorValue(6)

internal inline var CurlMeshKernel.faceAddress8: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.centerStripAddress2: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline val CurlMeshKernel.faceValue194: KernelVector
    get() = vectorValue(7)

internal inline var CurlMeshKernel.lowerStripAddress2: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.upperStripAddress2: Long
    get() = integerValue(2)
    set(value) {
        integerValue(2, value)
    }

internal inline var CurlMeshKernel.packedColorAddress2: Long
    get() = integerValue(4)
    set(value) {
        integerValue(4, value)
    }

internal inline var CurlMeshKernel.faceValue195: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.faceValue196: Double
    get() = vectorValue(5).double(0)
    set(value) {
        vectorValue(5).double(0, value)
    }

internal inline val CurlMeshKernel.faceValue197: KernelVector
    get() = vectorValue(1)

internal inline var CurlMeshKernel.faceValue198: Double
    get() = vectorValue(4).double(0)
    set(value) {
        vectorValue(4).double(0, value)
    }

internal inline var CurlMeshKernel.stripIndex2: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.faceIndex9: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawStateAddress: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.drawIndex: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.drawAddress2: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress3: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress4: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress5: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawStateAddress2: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawIndex2: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.drawValue: Long
    get() = vectorValue(0).wordBits(0)
    set(value) {
        vectorValue(0).wordBits(0, value)
    }

internal inline var CurlMeshKernel.drawBits: Long
    get() = integerValue(4)
    set(value) {
        integerValue(4, value)
    }

internal inline var CurlMeshKernel.drawBits2: Long
    get() = integerValue(5)
    set(value) {
        integerValue(5, value)
    }

internal inline var CurlMeshKernel.drawIndex3: Long
    get() = integerValue(2)
    set(value) {
        integerValue(2, value)
    }

internal inline var CurlMeshKernel.drawIndex4: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.drawBits3: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.drawAddress6: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.outputTransformAddress: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.drawStateAddress3: Long
    get() = integerValue(20)
    set(value) {
        integerValue(20, value)
    }

internal inline var CurlMeshKernel.drawAddress7: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawValue2: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline var CurlMeshKernel.horizontalScaleAddress: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.verticalScaleAddress: Long
    get() = integerValue(2)
    set(value) {
        integerValue(2, value)
    }

internal inline var CurlMeshKernel.drawValue3: Double
    get() = vectorValue(0).double(0)
    set(value) {
        vectorValue(0).double(0, value)
    }

internal inline val CurlMeshKernel.drawValue4: KernelVector
    get() = vectorValue(0)

internal inline val CurlMeshKernel.drawValue5: KernelVector
    get() = vectorValue(1)

internal inline val CurlMeshKernel.drawValue6: KernelVector
    get() = vectorValue(0)

internal inline var CurlMeshKernel.drawAddress8: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress9: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress10: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.drawAddress11: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.clipRectangleAddress: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.drawBits4: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.drawAddress12: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawBits5: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress13: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.drawAddress14: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.drawBits6: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress15: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawBits7: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress16: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawIndex5: Long
    get() = integerValue(21)
    set(value) {
        integerValue(21, value)
    }

internal inline var CurlMeshKernel.drawAddress17: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress18: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress19: Long
    get() = integerValue(10)
    set(value) {
        integerValue(10, value)
    }

internal inline var CurlMeshKernel.drawBits8: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress20: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress21: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawBits9: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress22: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress23: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawBits10: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawBits11: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress24: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress25: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawBits12: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress26: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress27: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawBits13: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline val CurlMeshKernel.drawValue7: KernelVector
    get() = vectorValue(0)

internal inline var CurlMeshKernel.drawColor: Long
    get() = vectorValue(0).halfBits(0)
    set(value) {
        vectorValue(0).halfBits(0, value)
    }

internal inline var CurlMeshKernel.drawIndex6: Long
    get() = integerValue(22)
    set(value) {
        integerValue(22, value)
    }

internal inline var CurlMeshKernel.drawColor2: Float
    get() = vectorValue(8).float(0)
    set(value) {
        vectorValue(8).float(0, value)
    }

internal inline var CurlMeshKernel.drawColor3: Long
    get() = vectorValue(0).halfBits(0)
    set(value) {
        vectorValue(0).halfBits(0, value)
    }

internal inline var CurlMeshKernel.drawColor4: Float
    get() = vectorValue(9).float(0)
    set(value) {
        vectorValue(9).float(0, value)
    }

internal inline var CurlMeshKernel.drawIndex7: Long
    get() = integerValue(24)
    set(value) {
        integerValue(24, value)
    }

internal inline var CurlMeshKernel.drawAddress28: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawBits14: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawBits15: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawStateAddress4: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress29: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress30: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.primitiveKind: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.drawAddress31: Long
    get() = integerValue(4)
    set(value) {
        integerValue(4, value)
    }

internal inline var CurlMeshKernel.vertexBufferAddress: Long
    get() = integerValue(4)
    set(value) {
        integerValue(4, value)
    }

internal inline var CurlMeshKernel.drawAddress32: Long
    get() = integerValue(20)
    set(value) {
        integerValue(20, value)
    }

internal inline var CurlMeshKernel.drawIndexCount: Long
    get() = integerValue(2)
    set(value) {
        integerValue(2, value)
    }

internal inline var CurlMeshKernel.vertexBufferOffset: Long
    get() = integerValue(5)
    set(value) {
        integerValue(5, value)
    }

internal inline var CurlMeshKernel.drawVertexCount: Long
    get() = integerValue(6)
    set(value) {
        integerValue(6, value)
    }

internal inline var CurlMeshKernel.renderContextAddress2: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.indexBufferAddress: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.attributePlanes: Long
    get() = integerValue(7)
    set(value) {
        integerValue(7, value)
    }

internal inline var CurlMeshKernel.drawAddress33: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress34: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.primitiveKind2: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.vertexBufferAddress2: Long
    get() = integerValue(4)
    set(value) {
        integerValue(4, value)
    }

internal inline var CurlMeshKernel.drawIndexCount2: Long
    get() = integerValue(2)
    set(value) {
        integerValue(2, value)
    }

internal inline var CurlMeshKernel.vertexBufferOffset2: Long
    get() = integerValue(5)
    set(value) {
        integerValue(5, value)
    }

internal inline var CurlMeshKernel.drawVertexCount2: Long
    get() = integerValue(6)
    set(value) {
        integerValue(6, value)
    }

internal inline var CurlMeshKernel.renderContextAddress3: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.indexBufferAddress2: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.attributePlanes2: Long
    get() = integerValue(7)
    set(value) {
        integerValue(7, value)
    }

internal inline var CurlMeshKernel.drawAddress35: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress36: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.primitiveKind3: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.vertexBufferAddress3: Long
    get() = integerValue(4)
    set(value) {
        integerValue(4, value)
    }

internal inline var CurlMeshKernel.drawIndexCount3: Long
    get() = integerValue(2)
    set(value) {
        integerValue(2, value)
    }

internal inline var CurlMeshKernel.vertexBufferOffset3: Long
    get() = integerValue(5)
    set(value) {
        integerValue(5, value)
    }

internal inline var CurlMeshKernel.drawVertexCount3: Long
    get() = integerValue(6)
    set(value) {
        integerValue(6, value)
    }

internal inline var CurlMeshKernel.renderContextAddress4: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.indexBufferAddress3: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.attributePlanes3: Long
    get() = integerValue(7)
    set(value) {
        integerValue(7, value)
    }

internal inline var CurlMeshKernel.drawStateAddress5: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress37: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress38: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.primitiveKind4: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.drawIndexCount4: Long
    get() = integerValue(2)
    set(value) {
        integerValue(2, value)
    }

internal inline var CurlMeshKernel.vertexBufferOffset4: Long
    get() = integerValue(5)
    set(value) {
        integerValue(5, value)
    }

internal inline var CurlMeshKernel.drawVertexCount4: Long
    get() = integerValue(6)
    set(value) {
        integerValue(6, value)
    }

internal inline var CurlMeshKernel.renderContextAddress5: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.indexBufferAddress4: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.vertexBufferAddress4: Long
    get() = integerValue(4)
    set(value) {
        integerValue(4, value)
    }

internal inline var CurlMeshKernel.attributePlanes4: Long
    get() = integerValue(7)
    set(value) {
        integerValue(7, value)
    }

internal inline var CurlMeshKernel.drawAddress39: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress40: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.primitiveKind5: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.drawAddress41: Long
    get() = integerValue(4)
    set(value) {
        integerValue(4, value)
    }

internal inline var CurlMeshKernel.vertexBufferAddress5: Long
    get() = integerValue(4)
    set(value) {
        integerValue(4, value)
    }

internal inline var CurlMeshKernel.drawAddress42: Long
    get() = integerValue(20)
    set(value) {
        integerValue(20, value)
    }

internal inline var CurlMeshKernel.drawIndexCount5: Long
    get() = integerValue(2)
    set(value) {
        integerValue(2, value)
    }

internal inline var CurlMeshKernel.vertexBufferOffset5: Long
    get() = integerValue(5)
    set(value) {
        integerValue(5, value)
    }

internal inline var CurlMeshKernel.drawVertexCount5: Long
    get() = integerValue(6)
    set(value) {
        integerValue(6, value)
    }

internal inline var CurlMeshKernel.renderContextAddress6: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.indexBufferAddress5: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.attributePlanes5: Long
    get() = integerValue(7)
    set(value) {
        integerValue(7, value)
    }

internal inline var CurlMeshKernel.drawAddress43: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress44: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.primitiveKind6: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.drawAddress45: Long
    get() = integerValue(4)
    set(value) {
        integerValue(4, value)
    }

internal inline var CurlMeshKernel.vertexBufferAddress6: Long
    get() = integerValue(4)
    set(value) {
        integerValue(4, value)
    }

internal inline var CurlMeshKernel.drawIndexCount6: Long
    get() = integerValue(2)
    set(value) {
        integerValue(2, value)
    }

internal inline var CurlMeshKernel.vertexBufferOffset6: Long
    get() = integerValue(5)
    set(value) {
        integerValue(5, value)
    }

internal inline var CurlMeshKernel.drawVertexCount6: Long
    get() = integerValue(6)
    set(value) {
        integerValue(6, value)
    }

internal inline var CurlMeshKernel.renderContextAddress7: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.indexBufferAddress6: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.attributePlanes6: Long
    get() = integerValue(7)
    set(value) {
        integerValue(7, value)
    }

internal inline var CurlMeshKernel.drawAddress46: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress47: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawIndex8: Long
    get() = integerValue(20)
    set(value) {
        integerValue(20, value)
    }

internal inline var CurlMeshKernel.drawStateAddress6: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress48: Long
    get() = integerValue(22)
    set(value) {
        integerValue(22, value)
    }

internal inline var CurlMeshKernel.drawAddress49: Long
    get() = integerValue(22)
    set(value) {
        integerValue(22, value)
    }

internal inline var CurlMeshKernel.drawAddress50: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawBits16: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress51: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.drawAddress52: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.drawBits17: Long
    get() = integerValue(23)
    set(value) {
        integerValue(23, value)
    }

internal inline var CurlMeshKernel.drawAddress53: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.drawAddress54: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.drawBits18: Long
    get() = integerValue(28)
    set(value) {
        integerValue(28, value)
    }

internal inline var CurlMeshKernel.drawAddress55: Long
    get() = integerValue(27)
    set(value) {
        integerValue(27, value)
    }

internal inline var CurlMeshKernel.drawAddress56: Long
    get() = integerValue(27)
    set(value) {
        integerValue(27, value)
    }

internal inline var CurlMeshKernel.drawAddress57: Long
    get() = integerValue(26)
    set(value) {
        integerValue(26, value)
    }

internal inline var CurlMeshKernel.drawBits19: Long
    get() = integerValue(25)
    set(value) {
        integerValue(25, value)
    }

internal inline var CurlMeshKernel.drawIndex9: Long
    get() = integerValue(21)
    set(value) {
        integerValue(21, value)
    }

internal inline var CurlMeshKernel.drawAddress58: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress59: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.primitiveKind7: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.drawIndexCount7: Long
    get() = integerValue(2)
    set(value) {
        integerValue(2, value)
    }

internal inline var CurlMeshKernel.vertexBufferOffset7: Long
    get() = integerValue(5)
    set(value) {
        integerValue(5, value)
    }

internal inline var CurlMeshKernel.drawVertexCount7: Long
    get() = integerValue(6)
    set(value) {
        integerValue(6, value)
    }

internal inline var CurlMeshKernel.renderContextAddress8: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.indexBufferAddress7: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.vertexBufferAddress7: Long
    get() = integerValue(4)
    set(value) {
        integerValue(4, value)
    }

internal inline var CurlMeshKernel.attributePlanes7: Long
    get() = integerValue(7)
    set(value) {
        integerValue(7, value)
    }

internal inline var CurlMeshKernel.drawAddress60: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress61: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.primitiveKind8: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.drawIndexCount8: Long
    get() = integerValue(2)
    set(value) {
        integerValue(2, value)
    }

internal inline var CurlMeshKernel.vertexBufferOffset8: Long
    get() = integerValue(5)
    set(value) {
        integerValue(5, value)
    }

internal inline var CurlMeshKernel.drawVertexCount8: Long
    get() = integerValue(6)
    set(value) {
        integerValue(6, value)
    }

internal inline var CurlMeshKernel.renderContextAddress9: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.indexBufferAddress8: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.vertexBufferAddress8: Long
    get() = integerValue(4)
    set(value) {
        integerValue(4, value)
    }

internal inline var CurlMeshKernel.attributePlanes8: Long
    get() = integerValue(7)
    set(value) {
        integerValue(7, value)
    }

internal inline var CurlMeshKernel.drawAddress62: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress63: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress64: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.vertexBufferAddress9: Long
    get() = integerValue(4)
    set(value) {
        integerValue(4, value)
    }

internal inline var CurlMeshKernel.drawAddress65: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress66: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.primitiveKind9: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.drawIndexCount9: Long
    get() = integerValue(2)
    set(value) {
        integerValue(2, value)
    }

internal inline var CurlMeshKernel.drawVertexCount9: Long
    get() = integerValue(6)
    set(value) {
        integerValue(6, value)
    }

internal inline var CurlMeshKernel.vertexBufferOffset9: Long
    get() = integerValue(5)
    set(value) {
        integerValue(5, value)
    }

internal inline var CurlMeshKernel.renderContextAddress10: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.indexBufferAddress9: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.attributePlanes9: Long
    get() = integerValue(7)
    set(value) {
        integerValue(7, value)
    }

internal inline var CurlMeshKernel.drawBits20: Long
    get() = integerValue(23)
    set(value) {
        integerValue(23, value)
    }

internal inline var CurlMeshKernel.drawAddress67: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawBits21: Long
    get() = integerValue(21)
    set(value) {
        integerValue(21, value)
    }

internal inline var CurlMeshKernel.drawAddress68: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress69: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.primitiveKind10: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.drawIndexCount10: Long
    get() = integerValue(2)
    set(value) {
        integerValue(2, value)
    }

internal inline var CurlMeshKernel.drawVertexCount10: Long
    get() = integerValue(6)
    set(value) {
        integerValue(6, value)
    }

internal inline var CurlMeshKernel.vertexBufferOffset10: Long
    get() = integerValue(5)
    set(value) {
        integerValue(5, value)
    }

internal inline var CurlMeshKernel.renderContextAddress11: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.indexBufferAddress10: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.vertexBufferAddress10: Long
    get() = integerValue(4)
    set(value) {
        integerValue(4, value)
    }

internal inline var CurlMeshKernel.attributePlanes10: Long
    get() = integerValue(7)
    set(value) {
        integerValue(7, value)
    }

internal inline var CurlMeshKernel.drawStateAddress7: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress70: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress71: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.primitiveKind11: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.drawAddress72: Long
    get() = integerValue(21)
    set(value) {
        integerValue(21, value)
    }

internal inline var CurlMeshKernel.drawIndexCount11: Long
    get() = integerValue(2)
    set(value) {
        integerValue(2, value)
    }

internal inline var CurlMeshKernel.vertexBufferOffset11: Long
    get() = integerValue(5)
    set(value) {
        integerValue(5, value)
    }

internal inline var CurlMeshKernel.drawVertexCount11: Long
    get() = integerValue(6)
    set(value) {
        integerValue(6, value)
    }

internal inline var CurlMeshKernel.renderContextAddress12: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.indexBufferAddress11: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.vertexBufferAddress11: Long
    get() = integerValue(4)
    set(value) {
        integerValue(4, value)
    }

internal inline var CurlMeshKernel.attributePlanes11: Long
    get() = integerValue(7)
    set(value) {
        integerValue(7, value)
    }

internal inline var CurlMeshKernel.drawAddress73: Long
    get() = integerValue(20)
    set(value) {
        integerValue(20, value)
    }

internal inline var CurlMeshKernel.drawAddress74: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress75: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.primitiveKind12: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.drawIndexCount12: Long
    get() = integerValue(2)
    set(value) {
        integerValue(2, value)
    }

internal inline var CurlMeshKernel.vertexBufferOffset12: Long
    get() = integerValue(5)
    set(value) {
        integerValue(5, value)
    }

internal inline var CurlMeshKernel.drawVertexCount12: Long
    get() = integerValue(6)
    set(value) {
        integerValue(6, value)
    }

internal inline var CurlMeshKernel.renderContextAddress13: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.indexBufferAddress12: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.vertexBufferAddress12: Long
    get() = integerValue(4)
    set(value) {
        integerValue(4, value)
    }

internal inline var CurlMeshKernel.attributePlanes12: Long
    get() = integerValue(7)
    set(value) {
        integerValue(7, value)
    }

internal inline var CurlMeshKernel.drawAddress76: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress77: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.primitiveKind13: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.drawIndexCount13: Long
    get() = integerValue(2)
    set(value) {
        integerValue(2, value)
    }

internal inline var CurlMeshKernel.vertexBufferOffset13: Long
    get() = integerValue(5)
    set(value) {
        integerValue(5, value)
    }

internal inline var CurlMeshKernel.drawVertexCount13: Long
    get() = integerValue(6)
    set(value) {
        integerValue(6, value)
    }

internal inline var CurlMeshKernel.renderContextAddress14: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.indexBufferAddress13: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.vertexBufferAddress13: Long
    get() = integerValue(4)
    set(value) {
        integerValue(4, value)
    }

internal inline var CurlMeshKernel.attributePlanes13: Long
    get() = integerValue(7)
    set(value) {
        integerValue(7, value)
    }

internal inline var CurlMeshKernel.drawStateAddress8: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawIndex10: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.drawAddress78: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress79: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.primitiveKind14: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.drawIndexCount14: Long
    get() = integerValue(2)
    set(value) {
        integerValue(2, value)
    }

internal inline var CurlMeshKernel.vertexBufferOffset14: Long
    get() = integerValue(5)
    set(value) {
        integerValue(5, value)
    }

internal inline var CurlMeshKernel.drawVertexCount14: Long
    get() = integerValue(6)
    set(value) {
        integerValue(6, value)
    }

internal inline var CurlMeshKernel.renderContextAddress15: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.indexBufferAddress14: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.vertexBufferAddress14: Long
    get() = integerValue(4)
    set(value) {
        integerValue(4, value)
    }

internal inline var CurlMeshKernel.attributePlanes14: Long
    get() = integerValue(7)
    set(value) {
        integerValue(7, value)
    }

internal inline var CurlMeshKernel.drawAddress80: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress81: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.primitiveKind15: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.drawIndexCount15: Long
    get() = integerValue(2)
    set(value) {
        integerValue(2, value)
    }

internal inline var CurlMeshKernel.vertexBufferOffset15: Long
    get() = integerValue(5)
    set(value) {
        integerValue(5, value)
    }

internal inline var CurlMeshKernel.drawVertexCount15: Long
    get() = integerValue(6)
    set(value) {
        integerValue(6, value)
    }

internal inline var CurlMeshKernel.renderContextAddress16: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.indexBufferAddress15: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.vertexBufferAddress15: Long
    get() = integerValue(4)
    set(value) {
        integerValue(4, value)
    }

internal inline var CurlMeshKernel.attributePlanes15: Long
    get() = integerValue(7)
    set(value) {
        integerValue(7, value)
    }

internal inline var CurlMeshKernel.drawAddress82: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress83: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.primitiveKind16: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.drawIndexCount16: Long
    get() = integerValue(2)
    set(value) {
        integerValue(2, value)
    }

internal inline var CurlMeshKernel.vertexBufferOffset16: Long
    get() = integerValue(5)
    set(value) {
        integerValue(5, value)
    }

internal inline var CurlMeshKernel.drawVertexCount16: Long
    get() = integerValue(6)
    set(value) {
        integerValue(6, value)
    }

internal inline var CurlMeshKernel.renderContextAddress17: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.indexBufferAddress16: Long
    get() = integerValue(3)
    set(value) {
        integerValue(3, value)
    }

internal inline var CurlMeshKernel.vertexBufferAddress16: Long
    get() = integerValue(4)
    set(value) {
        integerValue(4, value)
    }

internal inline var CurlMeshKernel.attributePlanes16: Long
    get() = integerValue(7)
    set(value) {
        integerValue(7, value)
    }

internal inline var CurlMeshKernel.drawAddress84: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.clipRectangleAddress2: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.drawBits22: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.drawIndex11: Long
    get() = integerValue(2)
    set(value) {
        integerValue(2, value)
    }

internal inline var CurlMeshKernel.drawBits23: Long
    get() = integerValue(0)
    set(value) {
        integerValue(0, value)
    }

internal inline var CurlMeshKernel.drawIndex12: Long
    get() = integerValue(1)
    set(value) {
        integerValue(1, value)
    }

internal inline var CurlMeshKernel.drawAddress85: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress86: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawStateAddress9: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress87: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.drawBits24: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.drawAddress88: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress89: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawStateAddress10: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawStateAddress11: Long
    get() = integerValue(8)
    set(value) {
        integerValue(8, value)
    }

internal inline var CurlMeshKernel.drawAddress90: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }

internal inline var CurlMeshKernel.drawBits25: Long
    get() = integerValue(9)
    set(value) {
        integerValue(9, value)
    }
