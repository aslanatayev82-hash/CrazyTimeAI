package az.crazytime.ai
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        private const val REQUEST_CAPTURE = 1001
        private const val PREFS = "crazy_time_history"
        private const val HISTORY_KEY = "results"

        // CaptureService bu ACTION və EXTRA adları ilə uyğunlaşdırılmalıdır.
        const val ACTION_OCR = "az.crazytime.ai.OCR"
        const val EXTRA_TEXT = "ocr_text"
        const val EXTRA_CANDIDATE = "ocr_candidate"
    }

    private lateinit var statusText: TextView
    private lateinit var ocrText: TextView
    private lateinit var candidateText: TextView
    private lateinit var historyText: TextView
    private lateinit var statsText: TextView
    private lateinit var historyLimitText: TextView

    private var currentCandidate = ""
    private var historyLimit = 20
    private var receiverRegistered = false

    private val results = mutableListOf<String>()

    private val ocrReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_OCR) return

            val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
            val candidate = intent.getStringExtra(EXTRA_CANDIDATE).orEmpty()

            if (text.isNotBlank()) {
                ocrText.text = "Oxunan mətn:\n$text"
            }

            if (candidate.isNotBlank()) {
                currentCandidate = candidate
                candidateText.text = "Namizəd nəticə: $candidate"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadHistory()
        buildScreen()
        updateScreen()
        registerOcrReceiver()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFF101820.toInt())
        }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val title = TextView(this).apply {
            text = "CRAZY TIME AI LIVE"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFD54F.toInt())
            setPadding(0, 10, 0, 20)
        }

        statusText = makeText("Status: Hazırdır", 17f)
        ocrText = makeText("OCR: Hələ məlumat yoxdur", 15f)
        candidateText = makeText("Namizəd nəticə: yoxdur", 18f)
        historyLimitText = makeText("Statistika: Son 20 nəticə", 15f)
        statsText = makeText("", 15f)
        historyText = makeText("", 15f)

        content.addView(title)
        content.addView(statusText)
        content.addView(makeButton("EKRANI OXUMAĞI BAŞLAT") {
            startScreenCapture()
        })
        content.addView(makeButton("OXUMAĞI DAYANDIR") {
            stopCapture()
        })

        content.addView(makeDivider())
        content.addView(ocrText)
        content.addView(candidateText)

        content.addView(makeButton("NAMİZƏD NƏTİCƏNİ TƏSDİQLƏ") {
            confirmCandidate()
        })

        content.addView(makeButton("NƏTİCƏNİ ƏL İLƏ DAXİL ET") {
            showManualEntry()
        })

        content.addView(makeButton("SON NƏTİCƏNİ GERİ GÖTÜR") {
            undoLast()
        })

        content.addView(makeButton("BÜTÜN TARİXÇƏNİ SİL") {
            confirmClearHistory()
        })

        content.addView(makeDivider())
        content.addView(historyLimitText)

        val limitButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        listOf(20, 50, 100, 500).forEach { limit ->
            val button = Button(this).apply {
                text = limit.toString()
                textSize = 12f
                setOnClickListener {
                    historyLimit = limit
                    updateScreen()
                }
            }
            limitButtons.addView(
                button,
                LinearLayout.LayoutParams(0, -2, 1f)
            )
        }

        content.addView(limitButtons)
        content.addView(statsText)
        content.addView(makeDivider())
        content.addView(makeText("NƏTİCƏLƏRİN TARİXÇƏSİ", 18f))
        content.addView(historyText)

        content.addView(makeButton("STATİSTİKANI PAYLAŞ") {
            shareStatistics()
        })

        scroll.addView(content)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.MATCH_PARENT,
                LinearLayout.MATCH_PARENT
            )
        )

        setContentView(root)
    }

    private fun makeText(value: String, size: Float): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(4, 12, 4, 12)
        }
    }

    private fun makeButton(
        label: String,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            textSize = 14f
            setOnClickListener { action() }
        }
    }

    private fun makeDivider(): View {
        return View(this).apply {
            setBackgroundColor(0xFF52616B.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.MATCH_PARENT,
                1
            ).apply {
                setMargins(0, 14, 0, 14)
            }
        }
    }

    private fun startScreenCapture() {
        try {
            val manager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                    as MediaProjectionManager

            val intent = manager.createScreenCaptureIntent()
            startActivityForResult(intent, REQUEST_CAPTURE)

        } catch (e: Exception) {
            statusText.text = "Ekran icazəsi alınmadı: ${e.message}"
        }
    }

    @Deprecated("Android ekran icazəsi nəticəsi")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                val serviceIntent = Intent(this, CaptureService::class.java).apply {
                    putExtra("resultCode", resultCode)
                    putExtra("data", data)
                }

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }

                    statusText.text = "Status: Ekran oxunması başladıldı"

                } catch (e: Exception) {
                    statusText.text =
                        "Xidmət başladılmadı: ${e.message}"
                }

            } else {
                statusText.text = "Ekran icazəsi verilmədi"
            }
        }
    }

    private fun stopCapture() {
        stopService(Intent(this, CaptureService::class.java))
        statusText.text = "Status: Dayandırıldı"
    }

    private fun confirmCandidate() {
        if (currentCandidate.isBlank()) {
            Toast.makeText(
                this,
                "Təsdiqləmək üçün nəticə yoxdur",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val candidate = normalizeResult(currentCandidate)

        if (candidate == null) {
            Toast.makeText(
                this,
                "Nəticə tanınmadı. Əl ilə daxil edin.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        addResult(candidate)
        currentCandidate = ""
        candidateText.text = "Namizəd nəticə: yoxdur"
    }

    private fun showManualEntry() {
        val options = arrayOf(
            "1", "2", "5", "10",
            "Coin Flip",
            "Cash Hunt",
            "Pachinko",
            "Crazy Time"
        )

        AlertDialog.Builder(this)
            .setTitle("Nəticəni seç")
            .setItems(options) { _, which ->
                addResult(options[which])
            }
            .setNegativeButton("Bağla", null)
            .show()
    }

    private fun normalizeResult(value: String): String? {
        val text = value.trim().lowercase()

        val allowed = listOf(
            "1", "2", "5", "10",
            "coin flip",
            "cash hunt",
            "pachinko",
            "crazy time"
        )

        return allowed.firstOrNull { it == text }
    }

    private fun addResult(value: String) {
        results.add(0, value)
        saveHistory()
        updateScreen()

        Toast.makeText(
            this,
            "Nəticə əlavə edildi: $value",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun undoLast() {
        if (results.isEmpty()) {
            Toast.makeText(
                this,
                "Tarixçə boşdur",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        results.removeAt(0)
        saveHistory()
        updateScreen()
    }

    private fun confirmClearHistory() {
        AlertDialog.Builder(this)
            .setTitle("Tarixçə silinsin?")
            .setMessage("Bütün qeydə alınmış nəticələr silinəcək.")
            .setPositiveButton("Sil") { _, _ ->
                results.clear()
                saveHistory()
                updateScreen()
            }
            .setNegativeButton("Ləğv et", null)
            .show()
    }

    private fun loadHistory() {
        val saved = getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        ).getString(HISTORY_KEY, "") ?: ""

        results.clear()

        if (saved.isNotBlank()) {
            results.addAll(
                saved.split("|").filter { it.isNotBlank() }
            )
        }
    }

    private fun saveHistory() {
        getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        ).edit()
            .putString(HISTORY_KEY, results.joinToString("|"))
            .apply()
    }

    private fun updateScreen() {
        historyLimitText.text =
            "Statistika: Son $historyLimit nəticə"

        val selected = results.take(historyLimit)

        if (selected.isEmpty()) {
            statsText.text = "Hələ nəticə qeydə alınmayıb."
            historyText.text = "Tarixçə boşdur."
            return
        }

        val counts = selected.groupingBy { it }.eachCount()

        val order = listOf(
            "1", "2", "5", "10",
            "Coin Flip",
            "Cash Hunt",
            "Pachinko",
            "Crazy Time"
        )

        val stats = StringBuilder()
        stats.append("Ümumi nəticə sayı: ${selected.size}\n\n")
        stats.append("Nəticələrin sayı və faizi:\n")

        order.forEach { item ->
            val count = counts[item] ?: 0
            val percent = count * 100.0 / selected.size

            stats.append(
                "$item : $count (${String.format("%.1f", percent)}%)\n"
            )
        }

        statsText.text = stats.toString()

        val history = StringBuilder()
        results.take(100).forEachIndexed { index, item ->
            history.append("${index + 1}. $item\n")
        }

        historyText.text = history.toString()
    }

    private fun shareStatistics() {
        val text = "CRAZY TIME NƏTİCƏLƏRİ\n\n" +
            statsText.text.toString() +
            "\nTarixçə:\n" +
            results.take(100).joinToString("\n")

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }

        startActivity(
            Intent.createChooser(shareIntent, "Statistikanı paylaş")
        )
    }

    private fun registerOcrReceiver() {
        val filter = IntentFilter(ACTION_OCR)

        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(
                    ocrReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(ocrReceiver, filter)
            }

            receiverRegistered = true

        } catch (e: Exception) {
            statusText.text = "OCR qəbuledicisi açıla bilmədi"
        }
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(ocrReceiver)
            } catch (_: Exception) {
            }
        }

        super.onDestroy()
    }
}
