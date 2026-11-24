
package az.crazytime.ai

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

data class WheelDetection(
    val centerX: Int,
    val centerY: Int,
    val radius: Int,
    val confidence: Double
)

class WheelAutoDetector {

    companion object {
        private const val MAX_WIDTH = 900
        private const val MAX_HEIGHT = 900

        private val openCvReady by lazy {
            OpenCVLoader.initDebug()
        }
    }

    private var previousDetection: WheelDetection? = null

    @Synchronized
    fun reset() {
        previousDetection = null
    }

    @Synchronized
    fun detect(bitmap: Bitmap): WheelDetection? {

        if (bitmap.isRecycled || !openCvReady) {
            return null
        }

        val scale = min(
            1.0,
            min(
                MAX_WIDTH.toDouble() / bitmap.width,
                MAX_HEIGHT.toDouble() / bitmap.height
            )
        )

        val source = Mat()
        val gray = Mat()
        val resized = Mat()
        val blurred = Mat()
        val circles = Mat()

        try {
            Utils.bitmapToMat(bitmap, source)

            Imgproc.cvtColor(
                source,
                gray,
                Imgproc.COLOR_RGBA2GRAY
            )

            if (scale < 1.0) {
                Imgproc.resize(
                    gray,
                    resized,
                    Size(
                        gray.cols() * scale,
                        gray.rows() * scale
                    )
                )
            } else {
                gray.copyTo(resized)
            }

            Imgproc.GaussianBlur(
                resized,
                blurred,
                Size(9.0, 9.0),
                2.0
            )

            val minDimension = min(
                blurred.cols(),
                blurred.rows()
            )

            val minRadius = (minDimension * 0.20).toInt()
            val maxRadius = (minDimension * 0.49).toInt()

            Imgproc.HoughCircles(
                blurred,
                circles,
                Imgproc.HOUGH_GRADIENT,
                1.2,
                minDimension * 0.30,
                100.0,
                45.0,
                minRadius,
                maxRadius
            )

            if (circles.empty()) return null

            val candidates = mutableListOf<WheelDetection>()

            for (i in 0 until circles.cols()) {

                val circle = circles.get(0, i) ?: continue

                if (circle.size < 3) continue

                val x = circle[0] / scale
                val y = circle[1] / scale
                val radius = circle[2] / scale

                if (radius <= 0) continue

                // Kənarda qalan natamam dairələri rədd et.
                if (x - radius < 0 ||
                    y - radius < 0 ||
                    x + radius >= bitmap.width ||
                    y + radius >= bitmap.height
                ) continue

                candidates.add(
                    WheelDetection(
                        centerX = x.toInt(),
                        centerY = y.toInt(),
                        radius = radius.toInt(),
                        confidence = 0.0
                    )
                )
            }

            if (candidates.isEmpty()) return null

            val selected = candidates.maxByOrNull {
                calculateScore(it, bitmap)
            } ?: return null

            val score = calculateScore(selected, bitmap)

            val result = selected.copy(
                confidence = score.coerceIn(0.0, 1.0)
            )

            previousDetection = result

            return result

        } catch (e: Exception) {
            return null

        } finally {
            source.release()
            gray.release()
            resized.release()
            blurred.release()
            circles.release()
        }
    }

    private fun calculateScore(
        detection: WheelDetection,
        bitmap: Bitmap
    ): Double {

        val expectedX = bitmap.width / 2.0
        val expectedY = bitmap.height / 2.0

        val expectedRadius =
            min(bitmap.width, bitmap.height) * 0.42

        val centerDistance = sqrt(
            (detection.centerX - expectedX) *
                (detection.centerX - expectedX) +
            (detection.centerY - expectedY) *
                (detection.centerY - expectedY)
        )

        val normalizedDistance =
            centerDistance /
                sqrt(
                    bitmap.width.toDouble() *
                        bitmap.width +
                    bitmap.height.toDouble() *
                        bitmap.height
                )

        val centerScore =
            (1.0 - normalizedDistance * 2.0)
                .coerceIn(0.0, 1.0)

        val radiusScore =
            1.0 - (
                abs(detection.radius - expectedRadius) /
                    expectedRadius
                ).coerceIn(0.0, 1.0)

        var temporalScore = 0.5

        val previous = previousDetection

        if (previous != null) {

            val dx = detection.centerX - previous.centerX
            val dy = detection.centerY - previous.centerY
            val dr = detection.radius - previous.radius

            val difference = sqrt(
                dx * dx + dy * dy + dr * dr
            )

            temporalScore =
                (1.0 - difference / bitmap.width)
                    .coerceIn(0.0, 1.0)
        }

        return centerScore * 0.35 +
            radiusScore * 0.35 +
            temporalScore * 0.30
    }
}
