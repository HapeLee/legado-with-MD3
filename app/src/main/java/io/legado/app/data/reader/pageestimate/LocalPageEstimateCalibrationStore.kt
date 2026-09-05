package io.legado.app.data.reader.pageestimate

import android.content.Context
import io.legado.app.feature.reader.core.pageestimate.PageEstimateCalibration
import io.legado.app.feature.reader.core.pageestimate.PageEstimateCalibrationStore
import io.legado.app.feature.reader.core.pageestimate.PageEstimateSamples
import splitties.init.appCtx

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
