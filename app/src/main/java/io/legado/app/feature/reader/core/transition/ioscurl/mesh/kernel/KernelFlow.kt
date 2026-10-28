// Generated curl mesh algorithm stages.
@file:Suppress("UNUSED_VARIABLE", "UNUSED_VALUE", "NAME_SHADOWING", "RedundantConversion")

package io.legado.app.feature.reader.core.transition.ioscurl.mesh.kernel

internal enum class KernelStep {
    PrepareMaterialsAndBounds,
    AdvanceProjectedCorner,
    ProjectPageCorner,
    PrepareBreakpointSort,
    AdvanceBreakpointSort,
    BeginBreakpointInsertion,
    CompareBreakpointInsertion,
    ShiftBreakpointForInsertion,
    FinishBreakpointInsertion,
    SelectBreakpointInsertion,
    StoreSortedBreakpoint,
    FinishBreakpointSort,
    UseTwoCurveEndpoints,
    DeriveAdaptiveSampleRange,
    SkipEmptySampleRange,
    PrepareAdaptiveSamples,
    EvaluateAdaptiveSample,
    PreserveNegativeSample,
    AppendPositiveSample,
    AdvanceAdaptiveSample,
    PrepareSampleMerge,
    SelectSampleMergeSource,
    TakeAdaptiveSample,
    InspectCornerSample,
    CompareCornerSample,
    TakeCornerSample,
    AdvanceAdaptiveSource,
    TakeProjectedCorner,
    RetainAdaptiveEndpoint,
    AdvanceCornerSource,
    AppendMergedSample,
    DrainAdaptiveSource,
    PrepareSampleCompaction,
    CompactSampleGap,
    InspectSampleGap,
    PrepareVisibleSampleRange,
    FindVisibleRangeStart,
    PrepareVisibleRangeEnd,
    InspectVisibleRangeEnd,
    ScanVisibleRangeEnd,
    FinishVisibleSampleRange,
    PrepareSurfaceStrips,
    SampleSurfaceCrossSection,
    SelectSurfaceLighting,
    UseLinearSurfaceLighting,
    UseUnlitSurfaceCrossSection,
    UseCurvedSurfaceLighting,
    EvaluateSurfaceLighting,
    ProjectSurfaceCrossSection,
    CheckSurfaceShadowRange,
    RecordSurfaceShadowSample,
    AppendSurfaceStrip,
    PrepareBackStripIndices,
    PrepareBackIndexReversal,
    BeginBackIndexReversal,
    ReverseBackStripIndices,
    FinishBackIndexReversal,
    PrepareBackStripColor,
    CopyBackStripColor,
    PrepareShadowBreakpoints,
    InspectShadowStart,
    BeginShadowStartSamples,
    InspectShadowStartSample,
    AppendShadowStartSample,
    FinishShadowStartSamples,
    SkipDisabledShadow,
    SkipEmptyShadow,
    FinishShadowStartRange,
    InspectShadowEnd,
    BeginShadowEndSamples,
    InspectShadowEndSample,
    AppendShadowEndSample,
    FinishShadowEndSamples,
    RetainShadowEnd,
    ClampShadowEnd,
    PrepareShadowEdgeIntersections,
    UseEmptyShadowEdges,
    AppendFirstShadowEdge,
    InspectSecondShadowEdge,
    SelectShadowMergeOrientation,
    InspectShadowMergeAvailability,
    SkipShadowEdgeMerge,
    AppendSecondShadowEdge,
    CompareShadowEdges,
    SwapShadowEdges,
    SelectTwoShadowEdges,
    SelectOneShadowEdge,
    FinishShadowMergeOrientation,
    PrepareShadowEdgeMerge,
    InspectShadowMergeSource,
    TakeShadowEdge,
    AdvanceShadowEdge,
    InspectShadowMergeOrder,
    CompareShadowMergeValues,
    RetainShadowMergeValue,
    TakeShadowBoundary,
    RetainShadowBoundary,
    AdvanceShadowBoundary,
    AppendShadowMergedValue,
    DrainShadowMerge,
    InspectShadowSegmentCount,
    PrepareShadowSegmentIndices,
    BeginShadowSegmentSearch,
    InspectShadowSegment,
    AdvanceShadowSegmentSearch,
    UseUnmatchedShadowSegment,
    MatchShadowSegment,
    InspectShadowSegmentEnd,
    InspectShadowSegmentOrientation,
    SelectShadowSegmentOrientation,
    ChooseShadowSegmentVertex,
    InspectShadowSegmentCase,
    AdvanceShadowSegmentCase,
    PrepareShadowSegment,
    PrepareNextShadowSegment,
    InspectShadowSegmentClass,
    InspectShadowSegmentBoundary,
    AppendShadowSegmentIndex,
    FinishShadowSegmentIndex,
    InspectShadowEndpointClass,
    SelectShadowEndpointVertex,
    StoreShadowEndpointVertex,
    InspectShadowTerminalSegment,
    AdvanceShadowTerminalSegment,
    FinishShadowTopology,
    PrepareShadowCrossSections,
    SampleShadowCrossSection,
    PreparePenumbraStrips,
    BeginPenumbraSegment,
    FindPenumbraSegment,
    AdvancePenumbraSearch,
    UseUnmatchedPenumbraSegment,
    MatchPenumbraSegment,
    InspectPenumbraSegmentEnd,
    InspectPenumbraSegmentCase,
    SelectPenumbraSegmentCase,
    CalculatePenumbraDirection,
    ValidatePenumbraDirection,
    ValidatePenumbraDirectionMagnitude,
    UseFallbackPenumbraDirection,
    SelectPenumbraEndpoint,
    CalculateAdjacentPenumbraDirection,
    ValidateAdjacentPenumbraDirection,
    ValidateAdjacentPenumbraMagnitude,
    UseAdjacentPenumbraDirection,
    UseTerminalPenumbraDirection,
    BeginLowerPenumbraFade,
    SampleLowerPenumbraFade,
    CopyLowerFadeJoin,
    AppendLowerPenumbraFade,
    BeginUpperPenumbraFade,
    SampleUpperPenumbraFade,
    CopyUpperFadeJoin,
    AppendUpperPenumbraFade,
    AdvancePenumbraSegment,
    FinishPenumbraSegments,
    PrepareEmptyPenumbra,
    InspectPenumbraAxis,
    InspectPenumbraAxisMagnitude,
    PreparePenumbraEndpointLookup,
    FindPenumbraEndpoint,
    InspectPenumbraTerminalEndpoint,
    InspectPenumbraEndpointKind,
    UseInteriorPenumbraEndpoint,
    UseFirstPenumbraEndpoint,
    UseLastPenumbraEndpoint,
    ReadPenumbraEndpoint,
    AdvancePenumbraEndpointLookup,
    FinishPenumbraEndpointLookup,
    PreparePenumbraEndCap,
    AdvancePenumbraEndCap,
    AppendPenumbraEndCap,
    FinishPenumbraEndCap,
    PreparePenumbraCorners,
    PreparePenumbraCornerRotation,
    BeginPenumbraCorner,
    AppendPenumbraCornerVertex,
    AdvancePenumbraCornerRing,
    AdvancePenumbraCorner,
    FinishPenumbraCorners,
    InspectFrontFace,
    InspectFrontSampleRange,
    SkipEmptyFrontSamples,
    SkipDisabledFrontFace,
    PrepareFrontSamples,
    SampleFrontRange,
    InspectFirstFrontEdge,
    UseEmptyFrontEdges,
    AppendFirstFrontEdge,
    InspectSecondFrontEdge,
    SelectFrontMergeOrientation,
    InspectFrontMergeAvailability,
    SkipFrontEdgeMerge,
    AppendSecondFrontEdge,
    CompareFrontEdges,
    SwapFrontEdges,
    SelectTwoFrontEdges,
    SelectOneFrontEdge,
    FinishFrontEdgeSelection,
    PrepareFrontEdgeMerge,
    InspectFrontMergeSource,
    TakeFrontEdge,
    AdvanceFrontEdge,
    InspectFrontMergeOrder,
    CompareFrontMergeValues,
    RetainFrontMergeValue,
    TakeFrontBoundary,
    RetainFrontBoundary,
    AdvanceFrontBoundary,
    AppendFrontMergedValue,
    DrainFrontMerge,
    PrepareFrontCrossSections,
    BeginFrontCrossSections,
    AppendFrontCrossSection,
    FinishFrontCrossSections,
    PrepareDrawState,
    InspectOutputTransform,
    UseAffineOutputScale,
    MeasureOutputScale,
    ApplyOutputScale,
    BeginClipIteration,
    PrepareDrawBuffers,
    InspectSurfaceDraw,
    DrawSurfaceStrips,
    InspectBackDraw,
    InspectBackShadowDraw,
    PrepareShadowDrawLoop,
    InspectShadowDrawSegment,
    PrepareShadowDrawSegment,
    DrawShadowSegment,
    InspectShadowEndCap,
    DrawShadowEndCap,
    InspectShadowCorner,
    DrawShadowCorner,
    AdvanceShadowDrawSegment,
    DrawBackFace,
    DrawBackFaceFringe,
    DrawBackFaceEdges,
    AdvanceClipIteration,
    FinishDrawClips,
    RestoreDrawBlendFlags,
    ResetDrawState,
    CompleteGeometry,
    RejectGeometry,
    Complete,
    Invalid,
}

/** Explicit geometry stages; labels preserve the reference algorithm's branch ordering. */
internal fun CurlMeshKernel.execute() {
    var step = KernelStep.PrepareMaterialsAndBounds
    var remaining = MAX_GEOMETRY_STEPS
    while (step != KernelStep.Complete) {
        check(--remaining > 0) { "Curl geometry iteration limit" }
        step =
            when (step) {
                KernelStep.PrepareMaterialsAndBounds -> prepareMaterialsAndBounds()
                KernelStep.AdvanceProjectedCorner -> advanceProjectedCorner()
                KernelStep.ProjectPageCorner -> projectPageCorner()
                KernelStep.PrepareBreakpointSort -> prepareBreakpointSort()
                KernelStep.AdvanceBreakpointSort -> advanceBreakpointSort()
                KernelStep.BeginBreakpointInsertion -> beginBreakpointInsertion()
                KernelStep.CompareBreakpointInsertion -> compareBreakpointInsertion()
                KernelStep.ShiftBreakpointForInsertion -> shiftBreakpointForInsertion()
                KernelStep.FinishBreakpointInsertion -> finishBreakpointInsertion()
                KernelStep.SelectBreakpointInsertion -> selectBreakpointInsertion()
                KernelStep.StoreSortedBreakpoint -> storeSortedBreakpoint()
                KernelStep.FinishBreakpointSort -> finishBreakpointSort()
                KernelStep.UseTwoCurveEndpoints -> useTwoCurveEndpoints()
                KernelStep.DeriveAdaptiveSampleRange -> deriveAdaptiveSampleRange()
                KernelStep.SkipEmptySampleRange -> skipEmptySampleRange()
                KernelStep.PrepareAdaptiveSamples -> prepareAdaptiveSamples()
                KernelStep.EvaluateAdaptiveSample -> evaluateAdaptiveSample()
                KernelStep.PreserveNegativeSample -> preserveNegativeSample()
                KernelStep.AppendPositiveSample -> appendPositiveSample()
                KernelStep.AdvanceAdaptiveSample -> advanceAdaptiveSample()
                KernelStep.PrepareSampleMerge -> prepareSampleMerge()
                KernelStep.SelectSampleMergeSource -> selectSampleMergeSource()
                KernelStep.TakeAdaptiveSample -> takeAdaptiveSample()
                KernelStep.InspectCornerSample -> inspectCornerSample()
                KernelStep.CompareCornerSample -> compareCornerSample()
                KernelStep.TakeCornerSample -> takeCornerSample()
                KernelStep.AdvanceAdaptiveSource -> advanceAdaptiveSource()
                KernelStep.TakeProjectedCorner -> takeProjectedCorner()
                KernelStep.RetainAdaptiveEndpoint -> retainAdaptiveEndpoint()
                KernelStep.AdvanceCornerSource -> advanceCornerSource()
                KernelStep.AppendMergedSample -> appendMergedSample()
                KernelStep.DrainAdaptiveSource -> drainAdaptiveSource()
                KernelStep.PrepareSampleCompaction -> prepareSampleCompaction()
                KernelStep.CompactSampleGap -> compactSampleGap()
                KernelStep.InspectSampleGap -> inspectSampleGap()
                KernelStep.PrepareVisibleSampleRange -> prepareVisibleSampleRange()
                KernelStep.FindVisibleRangeStart -> findVisibleRangeStart()
                KernelStep.PrepareVisibleRangeEnd -> prepareVisibleRangeEnd()
                KernelStep.InspectVisibleRangeEnd -> inspectVisibleRangeEnd()
                KernelStep.ScanVisibleRangeEnd -> scanVisibleRangeEnd()
                KernelStep.FinishVisibleSampleRange -> finishVisibleSampleRange()
                KernelStep.PrepareSurfaceStrips -> prepareSurfaceStrips()
                KernelStep.SampleSurfaceCrossSection -> sampleSurfaceCrossSection()
                KernelStep.SelectSurfaceLighting -> selectSurfaceLighting()
                KernelStep.UseLinearSurfaceLighting -> useLinearSurfaceLighting()
                KernelStep.UseUnlitSurfaceCrossSection -> useUnlitSurfaceCrossSection()
                KernelStep.UseCurvedSurfaceLighting -> useCurvedSurfaceLighting()
                KernelStep.EvaluateSurfaceLighting -> evaluateSurfaceLighting()
                KernelStep.ProjectSurfaceCrossSection -> projectSurfaceCrossSection()
                KernelStep.CheckSurfaceShadowRange -> checkSurfaceShadowRange()
                KernelStep.RecordSurfaceShadowSample -> recordSurfaceShadowSample()
                KernelStep.AppendSurfaceStrip -> appendSurfaceStrip()
                KernelStep.PrepareBackStripIndices -> prepareBackStripIndices()
                KernelStep.PrepareBackIndexReversal -> prepareBackIndexReversal()
                KernelStep.BeginBackIndexReversal -> beginBackIndexReversal()
                KernelStep.ReverseBackStripIndices -> reverseBackStripIndices()
                KernelStep.FinishBackIndexReversal -> finishBackIndexReversal()
                KernelStep.PrepareBackStripColor -> prepareBackStripColor()
                KernelStep.CopyBackStripColor -> copyBackStripColor()
                KernelStep.PrepareShadowBreakpoints -> prepareShadowBreakpoints()
                KernelStep.InspectShadowStart -> inspectShadowStart()
                KernelStep.BeginShadowStartSamples -> beginShadowStartSamples()
                KernelStep.InspectShadowStartSample -> inspectShadowStartSample()
                KernelStep.AppendShadowStartSample -> appendShadowStartSample()
                KernelStep.FinishShadowStartSamples -> finishShadowStartSamples()
                KernelStep.SkipDisabledShadow -> skipDisabledShadow()
                KernelStep.SkipEmptyShadow -> skipEmptyShadow()
                KernelStep.FinishShadowStartRange -> finishShadowStartRange()
                KernelStep.InspectShadowEnd -> inspectShadowEnd()
                KernelStep.BeginShadowEndSamples -> beginShadowEndSamples()
                KernelStep.InspectShadowEndSample -> inspectShadowEndSample()
                KernelStep.AppendShadowEndSample -> appendShadowEndSample()
                KernelStep.FinishShadowEndSamples -> finishShadowEndSamples()
                KernelStep.RetainShadowEnd -> retainShadowEnd()
                KernelStep.ClampShadowEnd -> clampShadowEnd()
                KernelStep.PrepareShadowEdgeIntersections -> prepareShadowEdgeIntersections()
                KernelStep.UseEmptyShadowEdges -> useEmptyShadowEdges()
                KernelStep.AppendFirstShadowEdge -> appendFirstShadowEdge()
                KernelStep.InspectSecondShadowEdge -> inspectSecondShadowEdge()
                KernelStep.SelectShadowMergeOrientation -> selectShadowMergeOrientation()
                KernelStep.InspectShadowMergeAvailability -> inspectShadowMergeAvailability()
                KernelStep.SkipShadowEdgeMerge -> skipShadowEdgeMerge()
                KernelStep.AppendSecondShadowEdge -> appendSecondShadowEdge()
                KernelStep.CompareShadowEdges -> compareShadowEdges()
                KernelStep.SwapShadowEdges -> swapShadowEdges()
                KernelStep.SelectTwoShadowEdges -> selectTwoShadowEdges()
                KernelStep.SelectOneShadowEdge -> selectOneShadowEdge()
                KernelStep.FinishShadowMergeOrientation -> finishShadowMergeOrientation()
                KernelStep.PrepareShadowEdgeMerge -> prepareShadowEdgeMerge()
                KernelStep.InspectShadowMergeSource -> inspectShadowMergeSource()
                KernelStep.TakeShadowEdge -> takeShadowEdge()
                KernelStep.AdvanceShadowEdge -> advanceShadowEdge()
                KernelStep.InspectShadowMergeOrder -> inspectShadowMergeOrder()
                KernelStep.CompareShadowMergeValues -> compareShadowMergeValues()
                KernelStep.RetainShadowMergeValue -> retainShadowMergeValue()
                KernelStep.TakeShadowBoundary -> takeShadowBoundary()
                KernelStep.RetainShadowBoundary -> retainShadowBoundary()
                KernelStep.AdvanceShadowBoundary -> advanceShadowBoundary()
                KernelStep.AppendShadowMergedValue -> appendShadowMergedValue()
                KernelStep.DrainShadowMerge -> drainShadowMerge()
                KernelStep.InspectShadowSegmentCount -> inspectShadowSegmentCount()
                KernelStep.PrepareShadowSegmentIndices -> prepareShadowSegmentIndices()
                KernelStep.BeginShadowSegmentSearch -> beginShadowSegmentSearch()
                KernelStep.InspectShadowSegment -> inspectShadowSegment()
                KernelStep.AdvanceShadowSegmentSearch -> advanceShadowSegmentSearch()
                KernelStep.UseUnmatchedShadowSegment -> useUnmatchedShadowSegment()
                KernelStep.MatchShadowSegment -> matchShadowSegment()
                KernelStep.InspectShadowSegmentEnd -> inspectShadowSegmentEnd()
                KernelStep.InspectShadowSegmentOrientation -> inspectShadowSegmentOrientation()
                KernelStep.SelectShadowSegmentOrientation -> selectShadowSegmentOrientation()
                KernelStep.ChooseShadowSegmentVertex -> chooseShadowSegmentVertex()
                KernelStep.InspectShadowSegmentCase -> inspectShadowSegmentCase()
                KernelStep.AdvanceShadowSegmentCase -> advanceShadowSegmentCase()
                KernelStep.PrepareShadowSegment -> prepareShadowSegment()
                KernelStep.PrepareNextShadowSegment -> prepareNextShadowSegment()
                KernelStep.InspectShadowSegmentClass -> inspectShadowSegmentClass()
                KernelStep.InspectShadowSegmentBoundary -> inspectShadowSegmentBoundary()
                KernelStep.AppendShadowSegmentIndex -> appendShadowSegmentIndex()
                KernelStep.FinishShadowSegmentIndex -> finishShadowSegmentIndex()
                KernelStep.InspectShadowEndpointClass -> inspectShadowEndpointClass()
                KernelStep.SelectShadowEndpointVertex -> selectShadowEndpointVertex()
                KernelStep.StoreShadowEndpointVertex -> storeShadowEndpointVertex()
                KernelStep.InspectShadowTerminalSegment -> inspectShadowTerminalSegment()
                KernelStep.AdvanceShadowTerminalSegment -> advanceShadowTerminalSegment()
                KernelStep.FinishShadowTopology -> finishShadowTopology()
                KernelStep.PrepareShadowCrossSections -> prepareShadowCrossSections()
                KernelStep.SampleShadowCrossSection -> sampleShadowCrossSection()
                KernelStep.PreparePenumbraStrips -> preparePenumbraStrips()
                KernelStep.BeginPenumbraSegment -> beginPenumbraSegment()
                KernelStep.FindPenumbraSegment -> findPenumbraSegment()
                KernelStep.AdvancePenumbraSearch -> advancePenumbraSearch()
                KernelStep.UseUnmatchedPenumbraSegment -> useUnmatchedPenumbraSegment()
                KernelStep.MatchPenumbraSegment -> matchPenumbraSegment()
                KernelStep.InspectPenumbraSegmentEnd -> inspectPenumbraSegmentEnd()
                KernelStep.InspectPenumbraSegmentCase -> inspectPenumbraSegmentCase()
                KernelStep.SelectPenumbraSegmentCase -> selectPenumbraSegmentCase()
                KernelStep.CalculatePenumbraDirection -> calculatePenumbraDirection()
                KernelStep.ValidatePenumbraDirection -> validatePenumbraDirection()
                KernelStep.ValidatePenumbraDirectionMagnitude ->
                    validatePenumbraDirectionMagnitude()
                KernelStep.UseFallbackPenumbraDirection -> useFallbackPenumbraDirection()
                KernelStep.SelectPenumbraEndpoint -> selectPenumbraEndpoint()
                KernelStep.CalculateAdjacentPenumbraDirection ->
                    calculateAdjacentPenumbraDirection()
                KernelStep.ValidateAdjacentPenumbraDirection -> validateAdjacentPenumbraDirection()
                KernelStep.ValidateAdjacentPenumbraMagnitude -> validateAdjacentPenumbraMagnitude()
                KernelStep.UseAdjacentPenumbraDirection -> useAdjacentPenumbraDirection()
                KernelStep.UseTerminalPenumbraDirection -> useTerminalPenumbraDirection()
                KernelStep.BeginLowerPenumbraFade -> beginLowerPenumbraFade()
                KernelStep.SampleLowerPenumbraFade -> sampleLowerPenumbraFade()
                KernelStep.CopyLowerFadeJoin -> copyLowerFadeJoin()
                KernelStep.AppendLowerPenumbraFade -> appendLowerPenumbraFade()
                KernelStep.BeginUpperPenumbraFade -> beginUpperPenumbraFade()
                KernelStep.SampleUpperPenumbraFade -> sampleUpperPenumbraFade()
                KernelStep.CopyUpperFadeJoin -> copyUpperFadeJoin()
                KernelStep.AppendUpperPenumbraFade -> appendUpperPenumbraFade()
                KernelStep.AdvancePenumbraSegment -> advancePenumbraSegment()
                KernelStep.FinishPenumbraSegments -> finishPenumbraSegments()
                KernelStep.PrepareEmptyPenumbra -> prepareEmptyPenumbra()
                KernelStep.InspectPenumbraAxis -> inspectPenumbraAxis()
                KernelStep.InspectPenumbraAxisMagnitude -> inspectPenumbraAxisMagnitude()
                KernelStep.PreparePenumbraEndpointLookup -> preparePenumbraEndpointLookup()
                KernelStep.FindPenumbraEndpoint -> findPenumbraEndpoint()
                KernelStep.InspectPenumbraTerminalEndpoint -> inspectPenumbraTerminalEndpoint()
                KernelStep.InspectPenumbraEndpointKind -> inspectPenumbraEndpointKind()
                KernelStep.UseInteriorPenumbraEndpoint -> useInteriorPenumbraEndpoint()
                KernelStep.UseFirstPenumbraEndpoint -> useFirstPenumbraEndpoint()
                KernelStep.UseLastPenumbraEndpoint -> useLastPenumbraEndpoint()
                KernelStep.ReadPenumbraEndpoint -> readPenumbraEndpoint()
                KernelStep.AdvancePenumbraEndpointLookup -> advancePenumbraEndpointLookup()
                KernelStep.FinishPenumbraEndpointLookup -> finishPenumbraEndpointLookup()
                KernelStep.PreparePenumbraEndCap -> preparePenumbraEndCap()
                KernelStep.AdvancePenumbraEndCap -> advancePenumbraEndCap()
                KernelStep.AppendPenumbraEndCap -> appendPenumbraEndCap()
                KernelStep.FinishPenumbraEndCap -> finishPenumbraEndCap()
                KernelStep.PreparePenumbraCorners -> preparePenumbraCorners()
                KernelStep.PreparePenumbraCornerRotation -> preparePenumbraCornerRotation()
                KernelStep.BeginPenumbraCorner -> beginPenumbraCorner()
                KernelStep.AppendPenumbraCornerVertex -> appendPenumbraCornerVertex()
                KernelStep.AdvancePenumbraCornerRing -> advancePenumbraCornerRing()
                KernelStep.AdvancePenumbraCorner -> advancePenumbraCorner()
                KernelStep.FinishPenumbraCorners -> finishPenumbraCorners()
                KernelStep.InspectFrontFace -> inspectFrontFace()
                KernelStep.InspectFrontSampleRange -> inspectFrontSampleRange()
                KernelStep.SkipEmptyFrontSamples -> skipEmptyFrontSamples()
                KernelStep.SkipDisabledFrontFace -> skipDisabledFrontFace()
                KernelStep.PrepareFrontSamples -> prepareFrontSamples()
                KernelStep.SampleFrontRange -> sampleFrontRange()
                KernelStep.InspectFirstFrontEdge -> inspectFirstFrontEdge()
                KernelStep.UseEmptyFrontEdges -> useEmptyFrontEdges()
                KernelStep.AppendFirstFrontEdge -> appendFirstFrontEdge()
                KernelStep.InspectSecondFrontEdge -> inspectSecondFrontEdge()
                KernelStep.SelectFrontMergeOrientation -> selectFrontMergeOrientation()
                KernelStep.InspectFrontMergeAvailability -> inspectFrontMergeAvailability()
                KernelStep.SkipFrontEdgeMerge -> skipFrontEdgeMerge()
                KernelStep.AppendSecondFrontEdge -> appendSecondFrontEdge()
                KernelStep.CompareFrontEdges -> compareFrontEdges()
                KernelStep.SwapFrontEdges -> swapFrontEdges()
                KernelStep.SelectTwoFrontEdges -> selectTwoFrontEdges()
                KernelStep.SelectOneFrontEdge -> selectOneFrontEdge()
                KernelStep.FinishFrontEdgeSelection -> finishFrontEdgeSelection()
                KernelStep.PrepareFrontEdgeMerge -> prepareFrontEdgeMerge()
                KernelStep.InspectFrontMergeSource -> inspectFrontMergeSource()
                KernelStep.TakeFrontEdge -> takeFrontEdge()
                KernelStep.AdvanceFrontEdge -> advanceFrontEdge()
                KernelStep.InspectFrontMergeOrder -> inspectFrontMergeOrder()
                KernelStep.CompareFrontMergeValues -> compareFrontMergeValues()
                KernelStep.RetainFrontMergeValue -> retainFrontMergeValue()
                KernelStep.TakeFrontBoundary -> takeFrontBoundary()
                KernelStep.RetainFrontBoundary -> retainFrontBoundary()
                KernelStep.AdvanceFrontBoundary -> advanceFrontBoundary()
                KernelStep.AppendFrontMergedValue -> appendFrontMergedValue()
                KernelStep.DrainFrontMerge -> drainFrontMerge()
                KernelStep.PrepareFrontCrossSections -> prepareFrontCrossSections()
                KernelStep.BeginFrontCrossSections -> beginFrontCrossSections()
                KernelStep.AppendFrontCrossSection -> appendFrontCrossSection()
                KernelStep.FinishFrontCrossSections -> finishFrontCrossSections()
                KernelStep.PrepareDrawState -> prepareDrawState()
                KernelStep.InspectOutputTransform -> inspectOutputTransform()
                KernelStep.UseAffineOutputScale -> useAffineOutputScale()
                KernelStep.MeasureOutputScale -> measureOutputScale()
                KernelStep.ApplyOutputScale -> applyOutputScale()
                KernelStep.BeginClipIteration -> beginClipIteration()
                KernelStep.PrepareDrawBuffers -> prepareDrawBuffers()
                KernelStep.InspectSurfaceDraw -> inspectSurfaceDraw()
                KernelStep.DrawSurfaceStrips -> drawSurfaceStrips()
                KernelStep.InspectBackDraw -> inspectBackDraw()
                KernelStep.InspectBackShadowDraw -> inspectBackShadowDraw()
                KernelStep.PrepareShadowDrawLoop -> prepareShadowDrawLoop()
                KernelStep.InspectShadowDrawSegment -> inspectShadowDrawSegment()
                KernelStep.PrepareShadowDrawSegment -> prepareShadowDrawSegment()
                KernelStep.DrawShadowSegment -> drawShadowSegment()
                KernelStep.InspectShadowEndCap -> inspectShadowEndCap()
                KernelStep.DrawShadowEndCap -> drawShadowEndCap()
                KernelStep.InspectShadowCorner -> inspectShadowCorner()
                KernelStep.DrawShadowCorner -> drawShadowCorner()
                KernelStep.AdvanceShadowDrawSegment -> advanceShadowDrawSegment()
                KernelStep.DrawBackFace -> drawBackFace()
                KernelStep.DrawBackFaceFringe -> drawBackFaceFringe()
                KernelStep.DrawBackFaceEdges -> drawBackFaceEdges()
                KernelStep.AdvanceClipIteration -> advanceClipIteration()
                KernelStep.FinishDrawClips -> finishDrawClips()
                KernelStep.RestoreDrawBlendFlags -> restoreDrawBlendFlags()
                KernelStep.ResetDrawState -> resetDrawState()
                KernelStep.CompleteGeometry -> completeGeometry()
                KernelStep.RejectGeometry -> rejectGeometry()
                KernelStep.Complete -> KernelStep.Complete
                KernelStep.Invalid -> error("Invalid curl geometry")
            }
    }
}

private const val MAX_GEOMETRY_STEPS = 100_000
