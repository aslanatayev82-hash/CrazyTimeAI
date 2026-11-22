package az.crazytime.ai

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private val requestCode = 1001
    private lateinit var info: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.rgb(18, 18, 28))
        }

        val title = TextView(this).apply {
            text = "CRAZY TIME AI LIVE"
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        info = TextView(this).apply {
            text = "Sistem hazırdır.\nCanlı analizə başlamaq üçün düyməyə bas."
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 30)
        }

        val startButton = Button(this).apply {
            text = "ANALİZƏ BAŞLA"
            setOnClickListener {
                val manager = getSystemService(
                    MEDIA_PROJECTION_SERVICE
                ) as MediaProjectionManager

                info.text = "Ekran paylaşımı icazəsi gözlənilir..."

                startActivityForResult(
                    manager.createScreenCaptureIntent(),
                    requestCode
                )
            }
        }

        val stopButton = Button(this).apply {
            text = "ANALİZİ DAYANDIR"
            setOnClickListener {
                stopService(
                    Intent(this@MainActivity, CaptureService::class.java)
                )
                info.text = "Analiz dayandırıldı."
            }
        }

        layout.addView(title)
        layout.addView(info)
        layout.addView(startButton)
        layout.addView(stopButton)

        setContentView(layout)
    }

    @Deprecated("Android callback")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == this.requestCode &&
            resultCode == RESULT_OK &&
            data != null
        ) {
            val serviceIntent = Intent(
                this,
                CaptureService::class.java
            ).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }

            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            info.text = "Ekran paylaşılır. OCR analizi başladılır."
        } else {
            info.text = "Ekran paylaşımına icazə verilmədi."
        }
    }
}
