
package az.crazytime.ai

import android.graphics.Bitmap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class WheelFrameEstimate(
    val angleChangeDegrees: Double,
    val confidence: Double,
    val timestampMs: Long,
    val valid: Boolean
)

class WheelFrameAnalyzer(
    private val centerX: Int,
    private val centerY: Int,
    private val radius: Int
) {

    companion object {
        private const val SAMPLE_COUNT = 360
        private const val MAX_SHIFT = 60
        private const val MIN_CONFIDENCE = 0.55
    }

    private var previousSignature: DoubleArray? = null
    private var previousTimestamp = 0L

    @Synchronized
    fun reset() {
        previousSignature = null
        previousTimestamp = 0L
    }

    @Synchronized
    fun analyze(
        bitmap: Bitmap,
        timestampMs: Long
    ): WheelFrameEstimate {

        if (bitmap.isRecycled ||
            radius <= 0 ||
            centerX - radius < 0 ||
            centerY - radius < 0 ||
            centerX + radius >= bitmap.width ||
            centerY + radius >= bitmap.height
        ) {
            return invalid(timestampMs)
        }

        val signature = extractSignature(bitmap)
            ?: return invalid(timestampMs)

        val previous = previousSignature

        previousSignature = signature

        if (previous == null) {
            previousTimestamp = timestampMs
            return invalid(timestampMs)
        }

        val elapsed = timestampMs - previousTimestamp

        if (elapsed <= 0L || elapsed > 2000L) {
            previousTimestamp = timestampMs
            return invalid(timestampMs)
        }

        previousTimestamp = timestampMs

        var bestShift = 0
        var bestScore = Double.NEGATIVE_INFINITY
        var secondScore = Double.NEGATIVE_INFINITY

        for (shift in -MAX_SHIFT..MAX_SHIFT) {
            var score = 0.0

            for (i in 0 until SAMPLE_COUNT) {
                val j = (i + shift + SAMPLE_COUNT) % SAMPLE_COUNT
                score += previous[i] * signature[j]
            }

            score /= SAMPLE_COUNT

            if (score > bestScore) {
                secondScore = bestScore
                bestScore = score
                bestShift = shift
            } else if (score > secondScore) {
                secondScore = score
            }
        }

        val confidence = (
            (bestScore - secondScore).coerceAtLeast(0.0) /
                (kotlin.math.abs(bestScore) + 0.0001)
            ).coerceIn(0.0, 1.0)

        val valid = confidence >= MIN_CONFIDENCE &&
            bestShift != -MAX_SHIFT &&
            bestShift != MAX_SHIFT

        return WheelFrameEstimate(
            angleChangeDegrees = bestShift.toDouble(),
            confidence = confidence,
            timestampMs = timestampMs,
            valid = valid
        )
    }

    private fun extractSignature(
        bitmap: Bitmap
    ): DoubleArray? {

        val values = DoubleArray(SAMPLE_COUNT)

        for (i in 0 until SAMPLE_COUNT) {
            val angle = 2.0 * PI * i / SAMPLE_COUNT

            var total = 0.0
            var count = 0

            for (offset in -2..2) {
                val r = radius * 0.78 + offset

                val x = centerX + (r * cos(angle)).toInt()
                val y = centerY + (r * sin(angle)).toInt()

                if (x in 0 until bitmap.width &&
                    y in 0 until bitmap.height
                ) {
                    val pixel = bitmap.getPixel(x, y)

                    val red = (pixel shr 16) and 0xFF
                    val green = (pixel shr 8) and 0xFF
                    val blue = pixel and 0xFF

                    total += (
                        0.299 * red +
                        0.587 * green +
                        0.114 * blue
                    ) / 255.0

                    count++
                }
            }

            if (count == 0) return null

            values[i] = total / count
        }

        val mean = values.average()

        for (i in values.indices) {
            values[i] -= mean
        }

        val energy = values.sumOf { it * it } /
            SAMPLE_COUNT

        if (energy < 0.0001) return null

        val scale = kotlin.math.sqrt(energy)

        for (i in values.indices) {
            values[i] /= scale
        }

        return values
    }

    private fun invalid(
        timestampMs: Long
    ) = WheelFrameEstimate(
        angleChangeDegrees = 0.0,
        confidence = 0.0,
        timestampMs = timestampMs,
        valid = false
    )
}
