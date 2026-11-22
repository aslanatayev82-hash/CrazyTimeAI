package az.crazytime.ai

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setPadding(24, 24, 24, 24)
        layout.setBackgroundColor(Color.rgb(18, 18, 28))

        val title = TextView(this)
        title.text = "CRAZY TIME AI LIVE"
        title.textSize = 26f
        title.setTextColor(Color.WHITE)
        title.gravity = Gravity.CENTER

        val info = TextView(this)
        info.text = "AI analiz sistemi\n\nHazırdır. Başlamaq üçün düyməyə bas."
        info.textSize = 18f
        info.setTextColor(Color.WHITE)
        info.gravity = Gravity.CENTER

        val button = Button(this)
        button.text = "ANALİZƏ BAŞLA"

        button.setOnClickListener {
            info.text = "Analiz başladı.\nNəticələr yalnız statistik müşahidədir."
        }

        layout.addView(title)
        layout.addView(info)
        layout.addView(button)

        setContentView(layout)
    }
}
