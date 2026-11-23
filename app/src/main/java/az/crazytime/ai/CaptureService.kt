
package az.crazytime.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class CaptureService : Service() {

    companion object {
        private const val TAG = "CrazyTimeAI"
        private const val NOTIFICATION_ID = 4101
        private const val CHANNEL_ID = "capture_channel"

        private const val EXTRA_RESULT_CODE = "resultCode"
        private const val EXTRA_DATA = "data"

        const val ACTION_OCR = "az.crazytime.ai.OCR"
        const val EXTRA_TEXT = "ocr_text"
        const val EXTRA_CANDIDATE = "ocr_candidate"

        private const val OCR_INTERVAL_MS = 300L
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null

    private val recognizer by lazy {
        TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        )
    }

    private val processing = AtomicBoolean(false)

    private var lastOcrTime = 0L
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    private val projectionCallback =
        object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d(TAG, "MediaProjection dayandırıldı")
                stopSelf()
            }
        }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        startWorkerThread()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent.getIntExtra(
            EXTRA_RESULT_CODE,
            0
        )

        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(
                EXTRA_DATA,
                Intent::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_DATA)
        }

        if (data == null || resultCode == 0) {
            Log.e(TAG, "Ekran icazəsi məlumatı yoxdur")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startForegroundSafely()
            startCapture(resultCode, data)
        } catch (e: Exception) {
            Log.e(TAG, "Xidmət başlatma xətası", e)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startForegroundSafely() {
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(
            this,
            MainActivity::class.java
        )

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 23) {
                    PendingIntent.FLAG_IMMUTABLE
                } else {
                    0
                }
        )

        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Crazy Time AI")
                .setContentText("Ekran oxunması aktivdir")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Crazy Time AI")
                .setContentText("Ekran oxunması aktivdir")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Crazy Time ekran oxunması",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager = getSystemService(
                NotificationManager::class.java
            )

            manager.createNotificationChannel(channel)
        }
    }

    private fun startWorkerThread() {
        workerThread = HandlerThread(
            "CrazyTimeOCR"
        ).also {
            it.start()
            workerHandler = Handler(it.looper)
        }
    }

    private fun startCapture(
        resultCode: Int,
        data: Intent
    ) {
        if (mediaProjection != null) {
            stopCaptureResources()
        }

        val manager = getSystemService(
            Context.MEDIA_PROJECTION_SERVICE
        ) as MediaProjectionManager

        mediaProjection = manager.getMediaProjection(
            resultCode,
            data
        )

        val projection = mediaProjection
            ?: throw IllegalStateException(
                "MediaProjection yaradıla bilmədi"
            )

        projection.registerCallback(
            projectionCallback,
            workerHandler
        )

        getScreenSize()

        imageReader = ImageReader.newInstance(
            screenWidth,
            screenHeight,
            PixelFormat.RGBA_8888,
            2
        )

        imageReader?.setOnImageAvailableListener(
            { reader ->
                processLatestImage(reader)
            },
            workerHandler
        )

        virtualDisplay = projection.createVirtualDisplay(
            "CrazyTimeScreen",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            workerHandler
        )

        Log.d(
            TAG,
            "Ekran oxunması başladı: ${screenWidth}x${screenHeight}"
        )
    }

    @Suppress("DEPRECATION")
    private fun getScreenSize() {
        val metrics = DisplayMetrics()

        if (Build.VERSION.SDK_INT >= 30) {
            screenWidth = resources.displayMetrics.widthPixels
            screenHeight = resources.displayMetrics.heightPixels
            screenDensity = resources.displayMetrics.densityDpi
        } else {
            val windowManager =
                getSystemService(Context.WINDOW_SERVICE) as WindowManager

            windowManager.defaultDisplay.getRealMetrics(metrics)

            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            screenDensity = metrics.densityDpi
        }

        if (screenWidth <= 0 || screenHeight <= 0) {
            throw IllegalStateException(
                "Ekran ölçüləri düzgün deyil"
            )
        }
    }

    private fun processLatestImage(
        reader: ImageReader
    ) {
        val now = System.currentTimeMillis()

        if (now - lastOcrTime < OCR_INTERVAL_MS) {
            return
        }

        if (!processing.compareAndSet(false, true)) {
            return
        }

        lastOcrTime = now

        var image: Image? = null
        var bitmap: Bitmap? = null

        try {
            image = reader.acquireLatestImage()

            if (image == null) {
                processing.set(false)
                return
            }

            // Düzəldilmiş Bitmap hissəsi
            val capturedBitmap = imageToBitmap(image)
            bitmap = capturedBitmap

            image.close()
            image = null

            val inputImage = InputImage.fromBitmap(
                capturedBitmap,
                0
            )

            recognizer.process(inputImage)
                .addOnSuccessListener { result ->
                    val recognizedText = result.text

                    if (recognizedText.isNotBlank()) {
                        val candidate =
                            findCandidate(recognizedText)

                        sendOcrResult(
                            recognizedText,
                            candidate
                        )
                    }
                }
                .addOnFailureListener { error ->
                    Log.e(
                        TAG,
                        "OCR xətası",
                        error
                    )
                }
                .addOnCompleteListener {
                    try {
                        bitmap?.recycle()
                    } catch (_: Exception) {
                    }

                    processing.set(false)
                }

        } catch (e: Exception) {
            Log.e(TAG, "Şəkil emalı xətası", e)

            try {
                image?.close()
                bitmap?.recycle()
            } catch (_: Exception) {
            }

            processing.set(false)
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer

        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride

        val rowPadding =
            rowStride - pixelStride * image.width

        val paddedWidth =
            image.width + rowPadding / pixelStride

        val paddedBitmap = Bitmap.createBitmap(
            paddedWidth,
            image.height,
            Bitmap.Config.ARGB_8888
        )

        buffer.rewind()
        paddedBitmap.copyPixelsFromBuffer(buffer)

        val croppedBitmap = Bitmap.createBitmap(
            paddedBitmap,
            0,
            0,
            image.width,
            image.height
        )

        if (croppedBitmap !== paddedBitmap) {
            paddedBitmap.recycle()
        }

        return croppedBitmap
    }

    private fun findCandidate(text: String): String {
        val normalized = text
            .lowercase(Locale.ROOT)
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")

        val bonusWords = listOf(
            "crazy time",
            "cash hunt",
            "pachinko",
            "coin flip"
        )

        for (bonus in bonusWords) {
            if (normalized.contains(bonus)) {
                return when (bonus) {
                    "crazy time" -> "Crazy Time"
                    "cash hunt" -> "Cash Hunt"
                    "pachinko" -> "Pachinko"
                    "coin flip" -> "Coin Flip"
                    else -> ""
                }
            }
        }

        val numberMatches = Regex(
            "(?<![a-z0-9])(?:10|5|2|1)(?![a-z0-9])"
        ).findAll(normalized)
            .map { it.value }
            .distinct()
            .toList()

        return if (numberMatches.size == 1) {
            numberMatches.first()
        } else {
            ""
        }
    }

    private fun sendOcrResult(
        text: String,
        candidate: String
    ) {
        val intent = Intent(ACTION_OCR).apply {
            setPackage(packageName)
            putExtra(EXTRA_TEXT, text)
            putExtra(EXTRA_CANDIDATE, candidate)
        }

        sendBroadcast(intent)

        Log.d(
            TAG,
            "OCR nəticəsi göndərildi: $candidate"
        )
    }

    private fun stopCaptureResources() {
        try {
            imageReader?.setOnImageAvailableListener(
                null,
                null
            )
        } catch (_: Exception) {
        }

        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }

        virtualDisplay = null

        try {
            imageReader?.close()
        } catch (_: Exception) {
        }

        imageReader = null

        try {
            mediaProjection?.unregisterCallback(
                projectionCallback
            )
        } catch (_: Exception) {
        }

        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
        }

        mediaProjection = null
    }

    override fun onDestroy() {
        stopCaptureResources()

        try {
            recognizer.close()
        } catch (_: Exception) {
        }

        try {
            workerThread?.quitSafely()
            workerThread?.join(500)
        } catch (_: Exception) {
        }

        workerThread = null
        workerHandler = null

        stopForeground(true)

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
