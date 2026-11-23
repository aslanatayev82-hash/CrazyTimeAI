
package az.crazytime.ai

import kotlin.math.abs

data class MotionEstimate(
    val angleDegrees: Double,
    val angularVelocity: Double,
    val angularAcceleration: Double,
    val estimatedStopSeconds: Double?,
    val sampleCount: Int
)

class WheelMotionAnalyzer {

    private data class Sample(
        val timeSeconds: Double,
        val angle: Double,
        val speed: Double
    )

    private val samples = mutableListOf<Sample>()

    private var previousRawAngle: Double? = null
    private var unwrappedAngle = 0.0

    @Synchronized
    fun reset() {
        samples.clear()
        previousRawAngle = null
        unwrappedAngle = 0.0
    }

    @Synchronized
    fun addSample(
        timestampMs: Long,
        angleDegrees: Double
    ): MotionEstimate? {

        if (!angleDegrees.isFinite() ||
            !timestampMs.toDouble().isFinite()
        ) return null

        val time = timestampMs / 1000.0
        val raw = ((angleDegrees % 360.0) + 360.0) % 360.0

        val previous = previousRawAngle

        if (previous == null) {
            previousRawAngle = raw
            unwrappedAngle = raw

            samples.add(Sample(time, unwrappedAngle, 0.0))
            return null
        }

        val previousSample = samples.lastOrNull() ?: return null
        val dt = time - previousSample.timeSeconds

        if (dt <= 0.0 || dt > 2.0) {
            return null
        }

        var delta = raw - previous

        if (delta > 180.0) delta -= 360.0
        if (delta < -180.0) delta += 360.0

        if (abs(delta) < 0.05) {
            return null
        }

        unwrappedAngle += delta
        previousRawAngle = raw

        val speed = delta / dt

        samples.add(
            Sample(time, unwrappedAngle, speed)
        )

        if (samples.size > 12) {
            samples.removeAt(0)
        }

        val currentSpeed = speed

        if (samples.size < 3) {
            return MotionEstimate(
                unwrappedAngle,
                currentSpeed,
                0.0,
                null,
                samples.size
            )
        }

        val recent = samples.takeLast(8)
        val meanTime = recent.map { it.timeSeconds }.average()
        val meanSpeed = recent.map { it.speed }.average()

        var numerator = 0.0
        var denominator = 0.0

        for (sample in recent) {
            val x = sample.timeSeconds - meanTime
            val y = sample.speed - meanSpeed

            numerator += x * y
            denominator += x * x
        }

        val acceleration =
            if (denominator > 0.000001) {
                numerator / denominator
            } else {
                0.0
            }

        val stopTime =
            if (abs(acceleration) > 0.01 &&
                currentSpeed * acceleration < 0
            ) {
                -currentSpeed / acceleration
            } else {
                null
            }

        return MotionEstimate(
            angleDegrees = unwrappedAngle,
            angularVelocity = currentSpeed,
            angularAcceleration = acceleration,
            estimatedStopSeconds = stopTime?.coerceAtLeast(0.0),
            sampleCount = samples.size
        )
    }
}
