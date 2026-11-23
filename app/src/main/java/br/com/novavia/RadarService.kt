package br.com.novavia

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import android.widget.LinearLayout

class RadarService : AccessibilityService() {
    private val manager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private var bubble: TextView? = null
    private var panel: TextView? = null
    private var lastEventAt = 0L
    private var currentOffer = "Aguardando oferta da 99"
    private var expanded = false
    private val prefs by lazy { getSharedPreferences("novavia", MODE_PRIVATE) }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun enabled() = prefs.getBoolean("radar_99", false) && Settings.canDrawOverlays(this)
    private fun shape(color: Int, stroke: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(19).toFloat()
        setStroke(dp(1), stroke)
    }
    private fun params(width: Int, height: Int, y: Int) =
        WindowManager.LayoutParams(width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.END
            this.y = dp(y)
            x = dp(10)
        }

    private fun ensureBubble() {
        if (!enabled()) { removeAll(); return }
        if (bubble != null) return
        try {
            val view = TextView(this).apply {
                text = "N"
                textSize = 23f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(5, 39, 29))
                background = shape(Color.rgb(80, 230, 178), Color.rgb(130, 250, 210))
                elevation = dp(7).toFloat()
            }
            val lp = params(dp(54), dp(54), 210)
            var startX = 0f
            var startY = 0f
            var originalY = 0
            var moved = false
            view.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX; startY = event.rawY
                        originalY = lp.y; moved = false; true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val delta = event.rawY - startY
                        if (kotlin.math.abs(delta) > dp(8)) moved = true
                        if (moved) {
                            lp.y = (originalY + delta.toInt()).coerceIn(dp(45), resources.displayMetrics.heightPixels - dp(100))
                            try { manager.updateViewLayout(view, lp) } catch (_: Exception) {}
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            expanded = !expanded
                            updatePanel()
                        }
                        true
                    }
                    else -> false
                }
            }
            manager.addView(view, lp)
            bubble = view
        } catch (_: Exception) { removeAll() }
    }

    private fun updatePanel() {
        if (!enabled() || !expanded) {
            panel?.let { try { manager.removeView(it) } catch (_: Exception) {} }
            panel = null
            return
        }
        try {
            if (panel == null) {
                val view = TextView(this).apply {
                    textSize = 13f
                    setTextColor(Color.rgb(231, 255, 244))
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                    background = shape(Color.rgb(16, 42, 46), Color.rgb(80, 230, 178))
                    maxWidth = resources.displayMetrics.widthPixels - dp(36)
                }
                manager.addView(view, params(WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT, 275))
                panel = view
            }
            panel?.text = "NOVAVIA • RADAR 99\n$currentOffer\n\nToque na bolinha para fechar."
        } catch (_: Exception) { expanded = false; updatePanel() }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ensureBubble()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        ensureBubble()
        val pkg = event?.packageName?.toString() ?: return
        if (pkg != "com.app99.driver" && pkg != "com.taxis99") {
            currentOffer = "Aguardando oferta da 99"
            updatePanel()
            return
        }
        if (!enabled()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastEventAt < 450) return
        lastEventAt = now
        val root = rootInActiveWindow ?: return
        val lines = mutableListOf<String>()
        fun visit(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 16 || lines.size >= 120) return
            node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { lines.add(it) }
            node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { lines.add(it) }
            for (i in 0 until node.childCount) visit(node.getChild(i), depth + 1)
        }
        visit(root, 0)
        val offer = Analyzer.parse(lines.joinToString(" "), "99")
        currentOffer = if (offer == null) "Oferta ainda não reconhecida.\nAguardando valor, km e minutos visíveis."
        else {
            val cost = prefs.getString("cost", "0.80")?.toDoubleOrNull() ?: 0.8
            "DADOS PARCIAIS • confirme o trajeto\n" +
                "${Analyzer.money(offer.price)} • ${offer.km} km • ${offer.minutes.toInt()} min\n" +
                "${Analyzer.money(offer.perKm)}/km • ${Analyzer.money(offer.perHour)}/h\n" +
                "Saldo parcial: ${Analyzer.money(offer.price - offer.km * cost)}"
        }
        if (offer != null) expanded = true
        updatePanel()
    }

    private fun removeAll() {
        panel?.let { try { manager.removeView(it) } catch (_: Exception) {} }
        bubble?.let { try { manager.removeView(it) } catch (_: Exception) {} }
        panel = null; bubble = null; expanded = false
    }
    override fun onInterrupt() = removeAll()
    override fun onDestroy() { removeAll(); super.onDestroy() }
}