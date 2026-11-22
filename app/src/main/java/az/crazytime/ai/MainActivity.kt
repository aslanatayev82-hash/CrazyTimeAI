
package az.crazytime.ai

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import org.json.JSONArray
import java.util.Locale

class MainActivity : Activity() {

    companion object {
        private const val REQUEST_CAPTURE = 1001
        private const val PREFS = "crazy_time_history"
    }

    private val names = listOf(
        "1", "2", "5", "10",
        "Coin Flip", "Cash Hunt",
        "Pachinko", "Crazy Time"
    )

    private lateinit var status: TextView
    private lateinit var historyText: TextView
    private lateinit var statsText: TextView
    private lateinit var resultPicker: Spinner

    private val prefs by lazy {
        getSharedPreferences(PREFS, MODE_PRIVATE)
    }

    private fun makeText(
        value: String,
        size: Float = 16f
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.WHITE)
            setPadding(8, 12, 8, 12)
        }
    }

    private fun makeButton(
        value: String,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = value
            setOnClickListener { action() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 25, 22, 25)
            setBackgroundColor(Color.rgb(18, 18, 30))
        }

        val title = makeText("CRAZY TIME AI LIVE", 25f)
        title.gravity = Gravity.CENTER

        root.addView(title)

        root.addView(
            makeText(
                "Canlı ekran analizi və spin statistikası",
                14f
            )
        )

        status = makeText("Status: Hazır", 16f)
        root.addView(status)

        root.addView(
            makeButton("CANLI ANALİZƏ BAŞLA") {
                startScreenCapture()
            }
        )

        root.addView(
            makeButton("ANALİZİ DAYANDIR") {
                stopCapture()
            }
        )

        root.addView(makeText("SPİN NƏTİCƏSİNİ ƏLAVƏ ET", 18f))

        resultPicker = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                names
            )
        }

        root.addView(resultPicker)

        root.addView(
            makeButton("NƏTİCƏNİ YADDA SAXLA") {
                addResult()
            }
        )

        root.addView(
            makeButton("SON NƏTİCƏNİ GERİ AL") {
                undoResult()
            }
        )

        root.addView(
            makeButton("BÜTÜN TARİXÇƏNİ SİL") {
                AlertDialogBuilder()
            }
        )

        root.addView(makeText("STATİSTİKA", 20f))

        statsText = makeText("")
        root.addView(statsText)

        root.addView(makeText("SON NƏTİCƏLƏR", 20f))

        historyText = makeText("")
        root.addView(historyText)

        root.addView(
            makeText(
                "Qeyd: Statistikalar keçmiş nəticələri göstərir. " +
                "Növbəti spin və bonus üçün zəmanətli proqnoz vermir.",
                13f
            )
        )

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        updateScreen()
    }

    private fun startScreenCapture() {
        try {
            val manager = getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

            status.text = "Ekran paylaşımı icazəsi gözlənilir..."

            startActivityForResult(
                manager.createScreenCaptureIntent(),
                REQUEST_CAPTURE
            )
        } catch (e: Exception) {
            status.text = "Xəta: ${e.message}"
        }
    }

    @Deprecated("Android activity result callback")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_CAPTURE) return

        if (resultCode == RESULT_OK && data != null) {
            try {
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

                status.text = "Status: Ekran analizi başladıldı"
            } catch (e: Exception) {
                status.text = "Servis xətası: ${e.message}"
            }
        } else {
            status.text = "Ekran paylaşımına icazə verilmədi"
        }
    }

    private fun stopCapture() {
        stopService(
            Intent(this, CaptureService::class.java)
        )
        status.text = "Status: Analiz dayandırıldı"
    }

    private fun readHistory(): MutableList<String> {
        val array = try {
            JSONArray(prefs.getString("history", "[]"))
        } catch (e: Exception) {
            JSONArray()
        }

        val result = mutableListOf<String>()

        for (i in 0 until array.length()) {
            val value = array.optString(i)
            if (value in names) result.add(value)
        }

        return result
    }

    private fun saveHistory(list: List<String>) {
        val array = JSONArray()

        list.forEach {
            array.put(it)
        }

        prefs.edit()
            .putString("history", array.toString())
            .apply()
    }

    private fun addResult() {
        val history = readHistory()
        val selected = resultPicker.selectedItem.toString()

        history.add(selected)
        saveHistory(history)

        updateScreen()
        status.text = "Nəticə yadda saxlanıldı: $selected"
    }

    private fun undoResult() {
        val history = readHistory()

        if (history.isNotEmpty()) {
            val removed = history.removeAt(history.lastIndex)
            saveHistory(history)
            status.text = "Geri alındı: $removed"
        } else {
            status.text = "Tarixçə boşdur"
        }

        updateScreen()
    }

    private fun AlertDialogBuilder() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Tarixçəni sil")
            .setMessage("Bütün spin nəticələri silinsin?")
            .setNegativeButton("Xeyr", null)
            .setPositiveButton("Bəli") { _, _ ->
                saveHistory(emptyList())
                updateScreen()
                status.text = "Tarixçə təmizləndi"
            }
            .show()
    }

    private fun updateScreen() {
        val history = readHistory()

        val last100 = history.takeLast(100)
        val last500 = history.takeLast(500)

        val output = StringBuilder()

        output.append("Ümumi spin: ${history.size}\n")
        output.append("Son 100 spin: ${last100.size}\n")
        output.append("Son 500 spin: ${last500.size}\n\n")

        output.append("SON 100 SPİNİN STATİSTİKASI\n")

        names.forEach { name ->
            val count = last100.count { it == name }
            val percent = if (last100.isEmpty()) {
                0.0
            } else {
                count * 100.0 / last100.size
            }

            output.append(
                "$name: $count | " +
                "${String.format(Locale.US, "%.1f", percent)}%\n"
            )
        }

        output.append("\nSON 500 SPİNİN STATİSTİKASI\n")

        names.forEach { name ->
            val count = last500.count { it == name }
            val percent = if (last500.isEmpty()) {
                0.0
            } else {
                count * 100.0 / last500.size
            }

            output.append(
                "$name: $count | " +
                "${String.format(Locale.US, "%.1f", percent)}%\n"
            )
        }

        statsText.text = output.toString()

        historyText.text = if (history.isEmpty()) {
            "Hələ nəticə əlavə edilməyib."
        } else {
            history.takeLast(30)
                .asReversed()
                .mapIndexed { index, value ->
                    "${index + 1}. $value"
                }
                .joinToString("\n")
        }
    }
}
