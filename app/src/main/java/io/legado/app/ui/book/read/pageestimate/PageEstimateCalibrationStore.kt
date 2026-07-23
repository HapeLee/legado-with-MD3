package io.legado.app.ui.book.read.pageestimate

import android.content.Context
import splitties.init.appCtx
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * 桶内的仿射修正：`真实页数 ≈ slope × 估算连续页数 + intercept`。
 *
 * 一个桶内字号、行距、段距、版心宽高全是常数（见 [PageEstimateConfig.calibrationBucket]），
 * 所以待拟合的自由度只剩两个：[slope] 学段落断行的实际损耗，[intercept] 学标题、章末留白
 * 和取整偏置。后者是乘性系数表达不出来的 —— 这也是原来单个 EMA scale 修不好短章节的原因。
 *
 * 默认值 `slope=1, intercept=0.5` 让未拟合时的行为等价于 `ceil(估算值)`，即冷启动不退化。
 */
data class PageEstimateCalibration(
    val slope: Float = 1f,
    val intercept: Float = 0f,
    val sampleCount: Int = 0,
    /** 显式标记而不是由 [sampleCount] 推导：样本够但拟合被判越界时也必须退回冷启动。 */
    val fitted: Boolean = false,
) {
    /**
     * 未拟合时用 `ceil` —— 和引入回归之前的行为逐位一致，冷启动不退化。
     * 拟合后用 `round`，因为取整偏置已经被 [intercept] 学进去了，再 ceil 会算两遍。
     */
    fun apply(estimatedPages: Float): Int {
        if (!estimatedPages.isFinite()) return 1
        if (!fitted) return ceil(estimatedPages).toInt().coerceAtLeast(1)
        return (slope * estimatedPages + intercept)
            .takeIf(Float::isFinite)
            ?.roundToInt()
            ?.coerceAtLeast(1)
            ?: 1
    }

    companion object {
        const val MIN_SAMPLES = 4
    }
}

interface PageEstimateCalibrationStore {
    fun get(bucket: Long): PageEstimateCalibration

    /** 累积一条样本并返回该桶更新后的拟合结果。 */
    fun record(bucket: Long, estimatedPages: Float, realPages: Int): PageEstimateCalibration

    companion object {
        val None = object : PageEstimateCalibrationStore {
            override fun get(bucket: Long) = PageEstimateCalibration()

            override fun record(bucket: Long, estimatedPages: Float, realPages: Int) =
                PageEstimateCalibration()
        }
    }
}

/**
 * 流式最小二乘的累加器。只留 5 个标量（n, Σx, Σy, Σx², Σxy），不保留任何原始样本 ——
 * 几十字节，也就没有"数据集"可言，自然不需要服务器。
 */
internal data class PageEstimateSamples(
    val count: Int = 0,
    val sumX: Double = 0.0,
    val sumY: Double = 0.0,
    val sumXX: Double = 0.0,
    val sumXY: Double = 0.0,
) {
    fun plus(x: Double, y: Double): PageEstimateSamples {
        val retention = if (count >= MAX_EFFECTIVE_SAMPLES) {
            (MAX_EFFECTIVE_SAMPLES - 1).toDouble() / count
        } else {
            1.0
        }
        return PageEstimateSamples(
            count = (count + 1).coerceAtMost(MAX_EFFECTIVE_SAMPLES),
            sumX = sumX * retention + x,
            sumY = sumY * retention + y,
            sumXX = sumXX * retention + x * x,
            sumXY = sumXY * retention + x * y,
        )
    }

    /** 样本不足、x 无变化（行列式退化）或结果越界时退回冷启动，由调用方 `ceil` 兜底。 */
    fun fit(): PageEstimateCalibration {
        val cold = PageEstimateCalibration(sampleCount = count)
        if (count < PageEstimateCalibration.MIN_SAMPLES) return cold

        val determinant = count * sumXX - sumX * sumX
        if (determinant <= DEGENERATE_DETERMINANT) return cold

        val slope = (count * sumXY - sumX * sumY) / determinant
        val intercept = (sumY - slope * sumX) / count
        if (!slope.isFinite() || !intercept.isFinite()) return cold
        if (slope < MIN_SLOPE || slope > MAX_SLOPE) return cold
        if (intercept < MIN_INTERCEPT || intercept > MAX_INTERCEPT) return cold

        return PageEstimateCalibration(
            slope = slope.toFloat(),
            intercept = intercept.toFloat(),
            sampleCount = count,
            fitted = true,
        )
    }

    private companion object {
        const val MAX_EFFECTIVE_SAMPLES = 256
        const val DEGENERATE_DETERMINANT = 1e-6
        const val MIN_SLOPE = 0.3
        const val MAX_SLOPE = 3.0
        const val MIN_INTERCEPT = -2.0
        const val MAX_INTERCEPT = 5.0
    }
}

object LocalPageEstimateCalibrationStore : PageEstimateCalibrationStore {
    private const val STORE_NAME = "page_estimate_calibration"

    private val preferences by lazy {
        appCtx.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    override fun get(bucket: Long): PageEstimateCalibration = read(bucket).fit()

    @Synchronized
    override fun record(
        bucket: Long,
        estimatedPages: Float,
        realPages: Int,
    ): PageEstimateCalibration {
        if (!estimatedPages.isFinite() || estimatedPages <= 0f || realPages <= 0) {
            return read(bucket).fit()
        }
        val updated = read(bucket).plus(estimatedPages.toDouble(), realPages.toDouble())
        val key = bucket.toULong().toString(16)
        preferences.edit()
            .putInt("n_$key", updated.count)
            .putFloat("sx_$key", updated.sumX.toFloat())
            .putFloat("sy_$key", updated.sumY.toFloat())
            .putFloat("sxx_$key", updated.sumXX.toFloat())
            .putFloat("sxy_$key", updated.sumXY.toFloat())
            .apply()
        return updated.fit()
    }

    private fun read(bucket: Long): PageEstimateSamples {
        val key = bucket.toULong().toString(16)
        return PageEstimateSamples(
            count = preferences.getInt("n_$key", 0).coerceAtLeast(0),
            sumX = preferences.getFloat("sx_$key", 0f).toDouble(),
            sumY = preferences.getFloat("sy_$key", 0f).toDouble(),
            sumXX = preferences.getFloat("sxx_$key", 0f).toDouble(),
            sumXY = preferences.getFloat("sxy_$key", 0f).toDouble(),
        )
    }
}
