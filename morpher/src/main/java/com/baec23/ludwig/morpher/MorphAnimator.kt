package com.baec23.ludwig.morpher

import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathNode
import com.baec23.ludwig.morpher.model.morpher.MorpherAnimationData
import com.baec23.ludwig.morpher.model.morpher.MorpherPathData
import com.baec23.ludwig.morpher.model.path.PairedSubpath
import com.baec23.ludwig.morpher.model.path.UnpairedSubpath
import com.baec23.ludwig.morpher.model.morpher.VectorSource
import com.baec23.ludwig.morpher.util.calcLength
import com.baec23.ludwig.morpher.util.normalize
import com.baec23.ludwig.morpher.util.splitPaths
import com.baec23.ludwig.morpher.util.toPath

class MorphAnimator(
    private val pathData: MorpherPathData,
    private val animationData: MorpherAnimationData,
) {

    companion object {
        operator fun invoke(
            start: VectorSource,
            end: VectorSource,
            width: Float = 0f,
            height: Float = 0f,
            smoothness: Int = 100
        ): MorphAnimator {
            val pathData = generatePathData(start, end, width, height)
            val pairedArray = Array<Path?>(smoothness + 1) { null }
            val unpairedStartArray = Array<Path?>(smoothness + 1) { null }
            val unpairedEndArray = Array<Path?>(smoothness + 1) { null }

            pairedArray[0] = pathData.pairedSubpaths.flatMap { pairedSubpath ->
                pairedSubpath.getInterpolatedPathNodes(0f)
            }.toPath()
            pairedArray[smoothness] = pathData.pairedSubpaths.flatMap { pairedSubpath ->
                pairedSubpath.getInterpolatedPathNodes(1f)
            }.toPath()

            unpairedStartArray[0] = pathData.unpairedStartSubpaths.flatMap { pairedSubpath ->
                pairedSubpath.getInterpolatedPathNodes(0f)
            }.toPath()
            unpairedStartArray[smoothness] =
                pathData.unpairedStartSubpaths.flatMap { pairedSubpath ->
                    pairedSubpath.getInterpolatedPathNodes(1f)
                }.toPath()

            unpairedEndArray[0] = pathData.unpairedEndSubpaths.flatMap { pairedSubpath ->
                pairedSubpath.getInterpolatedPathNodes(0f)
            }.toPath()
            unpairedEndArray[smoothness] = pathData.unpairedEndSubpaths.flatMap { pairedSubpath ->
                pairedSubpath.getInterpolatedPathNodes(1f)
            }.toPath()

            val animationData = MorpherAnimationData(
                pairedPaths = pairedArray,
                unpairedStartPaths = unpairedStartArray,
                unpairedEndPaths = unpairedEndArray
            )
            return MorphAnimator(pathData, animationData)
        }

        fun precomputeData(
            start: VectorSource,
            end: VectorSource,
            width: Float = 0f,
            height: Float = 0f,
            smoothness: Int = 200
        ): Pair<MorpherPathData, MorpherAnimationData> {
            val pathData = generatePathData(start, end, width, height)
            val animationData = generateAnimationData(pathData, smoothness)
            return Pair(pathData, animationData)
        }

        private fun generatePathData(
            start: VectorSource,
            end: VectorSource,
            width: Float = 0f,
            height: Float = 0f
        ): MorpherPathData {
            //Calc offset / scale
            val startBounds = start.bounds
            val endBounds = end.bounds
            val targetWidth = if (width >= 0f) width else maxOf(startBounds.width, endBounds.width)
            val targetHeight =
                if (height >= 0f) height else maxOf(startBounds.height, endBounds.height)
            val startScaleFactorX = targetWidth / startBounds.width
            val startScaleFactorY = targetHeight / startBounds.height
            val endScaleFactorX = targetWidth / endBounds.width
            val endScaleFactorY = targetHeight / endBounds.height

            //Normalize (offset + scale paths)
            val normalizedStartNodes = start.pathData.normalize(
                Offset(startBounds.left, startBounds.top), startScaleFactorX, startScaleFactorY
            )
            val normalizedEndNodes = end.pathData.normalize(
                Offset(endBounds.left, endBounds.top), endScaleFactorX, endScaleFactorY
            )

            //Split into subpaths
            val startSubpathNodes =
                normalizedStartNodes.splitPaths().sortedByDescending(List<PathNode>::calcLength)

            val endSubpathNodes =
                normalizedEndNodes.splitPaths().sortedByDescending(List<PathNode>::calcLength)

            //Arrange into paired (morphing) and unpaired (no morphing)
            val numStartSubpaths = startSubpathNodes.size
            val numEndSubpaths = endSubpathNodes.size
            val numAnimatedSubpaths = minOf(numStartSubpaths, numEndSubpaths)
            val _pairedSubpaths = mutableListOf<PairedSubpath>()
            val _unpairedStartSubpaths = mutableListOf<UnpairedSubpath>()
            val _unpairedEndSubpaths = mutableListOf<UnpairedSubpath>()

            for (i in 0 until numAnimatedSubpaths) {
                _pairedSubpaths.add(PairedSubpath(startSubpathNodes[i], endSubpathNodes[i]))
            }
            for (i in numAnimatedSubpaths until numStartSubpaths) {
                _unpairedStartSubpaths.add(
                    UnpairedSubpath(
                        startSubpathNodes[i]
                    )
                )
            }
            for (i in numAnimatedSubpaths until numEndSubpaths) {
                _unpairedEndSubpaths.add(UnpairedSubpath(endSubpathNodes[i]))
            }
            return MorpherPathData(
                pairedSubpaths = _pairedSubpaths.toList(),
                unpairedStartSubpaths = _unpairedStartSubpaths.toList(),
                unpairedEndSubpaths = _unpairedEndSubpaths.toList()
            )
        }

        private fun generateAnimationData(
            pathData: MorpherPathData,
            smoothness: Int
        ): MorpherAnimationData {
            val paired =
                Array<Path?>(smoothness + 1) { index ->
                    pathData.pairedSubpaths.flatMap { subpath ->
                        subpath.getInterpolatedPathNodes(index / smoothness.toFloat())
                    }.toPath()
                }
            val unpairedStart =
                Array<Path?>(smoothness + 1) { index ->
                    pathData.unpairedStartSubpaths.flatMap { subpath ->
                        subpath.getInterpolatedPathNodes(index / smoothness.toFloat())

                    }.toPath()
                }
            val unpairedEnd =
                Array<Path?>(smoothness + 1) { index ->
                    pathData.unpairedEndSubpaths.flatMap { subpath ->
                        subpath.getInterpolatedPathNodes(index / smoothness.toFloat())

                    }.toPath()
                }
            return MorpherAnimationData(
                pairedPaths = paired,
                unpairedStartPaths = unpairedStart,
                unpairedEndPaths = unpairedEnd
            )
        }
    }

    fun getInterpolatedPairedPath(fraction: Float): Path {
        var cachedPath = animationData.getInterpolatedPairedPath(fraction)
        if (cachedPath == null) {
//            Log.d("DEBUG", "Paired path cache miss - ${fraction}")
            val pathNodes = pathData.pairedSubpaths.flatMap { it.getInterpolatedPathNodes(fraction) }
            cachedPath = pathNodes.toPath()
            animationData.setInterpolatedPairedPath(fraction, cachedPath)
        }
        return cachedPath
    }

    fun getInterpolatedUnpairedStartPath(fraction: Float): Path {
        var cachedPath = animationData.getInterpolatedUnpairedStartPath(fraction)
        if (cachedPath == null) {
//            Log.d("DEBUG", "Unpaired start path cache miss - ${fraction}")
            val pathNodes =
                pathData.unpairedStartSubpaths.flatMap { it.getInterpolatedPathNodes(fraction) }
            cachedPath = pathNodes.toPath()
            animationData.setInterpolatedUnpairedStartPath(fraction, cachedPath)
        }
        return cachedPath
    }

    fun getInterpolatedUnpairedEndPath(fraction: Float): Path {
        var cachedPath = animationData.getInterpolatedUnpairedEndPath(fraction)
        if (cachedPath == null) {
//            Log.d("DEBUG", "Unpaired end path cache miss - ${fraction}")
            val pathNodes =
                pathData.unpairedEndSubpaths.flatMap { it.getInterpolatedPathNodes(fraction) }
            cachedPath = pathNodes.toPath()
            animationData.setInterpolatedEndPath(fraction, cachedPath)
        }
        return cachedPath
    }
}