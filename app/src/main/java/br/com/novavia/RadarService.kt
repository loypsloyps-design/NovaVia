package br.com.novavia

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView

class RadarService : AccessibilityService() {
    private var overlay: TextView? = null
    private var lastMessage = ""
    private var lastEventAt = 0L
    private val manager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        val source = when (pkg) {
            "com.app99.driver", "com.taxis99" -> "99"
            "com.ubercab.driver" -> "Uber"
            else -> { hide(); return }
        }
        if (source == "99" && !getSharedPreferences("novavia", MODE_PRIVATE).getBoolean("radar_99", false)) { hide(); return }
        val now = SystemClock.elapsedRealtime()
        if (now - lastEventAt < 350) return
        lastEventAt = now

        val root = rootInActiveWindow ?: run { hide(); return }
        val lines = mutableListOf<String>()
        fun visit(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 16 || lines.size >= 120) return
            val t = node.text?.toString()?.trim()
            if (!t.isNullOrBlank()) lines.add(t)
            val d = node.contentDescription?.toString()?.trim()
            if (!d.isNullOrBlank() && d != t) lines.add(d)
            for (i in 0 until node.childCount) visit(node.getChild(i), depth + 1)
        }
        visit(root, 0)
        val offer = Analyzer.parse(lines.joinToString(" "), source) ?: run { hide(); return }
        if (!Settings.canDrawOverlays(this)) { hide(); return }

        // A single visible distance/time may exclude the approach to the rider.
        // Therefore never give an automatic "good ride" recommendation from partial data.
        val prefs = getSharedPreferences("novavia", MODE_PRIVATE)
        val cost = prefs.getString("cost", "0.80")?.toDoubleOrNull() ?: 0.8
        val message = "NOVAVIA • $source • DADOS PARCIAIS\n" +
            "${Analyzer.money(offer.price)} • ${offer.km} km • ${offer.minutes.toInt()} min\n" +
            "${Analyzer.money(offer.perKm)}/km • ${Analyzer.money(offer.perHour)}/h\n" +
            "Saldo parcial: ${Analyzer.money(offer.price - offer.km * cost)}\n" +
            "Confirme deslocamento e tempo total"
        if (message == lastMessage) return
        lastMessage = message

        try {
            if (overlay == null) {
                val view = TextView(this).apply {
                    textSize = 13f
                    setTextColor(Color.rgb(232, 255, 246))
                    setPadding(24, 17, 24, 17)
                    maxWidth = (resources.displayMetrics.widthPixels * 0.92).toInt()
                    background = GradientDrawable().apply {
                        setColor(Color.rgb(16, 42, 46))
                        cornerRadius = 24f
                        setStroke(2, Color.rgb(72, 217, 172))
                    }
                }
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = (90 * resources.displayMetrics.density).toInt()
                }
                manager.addView(view, params)
                overlay = view
            }
            overlay?.text = message
        } catch (_: Exception) {
            hide()
        }
    }

    private fun hide() {
        lastMessage = ""
        overlay?.let {
            try { manager.removeView(it) } catch (_: Exception) {}
        }
        overlay = null
    }

    override fun onInterrupt() = hide()
    override fun onDestroy() { hide(); super.onDestroy() }
}