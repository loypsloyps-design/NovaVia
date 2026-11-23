package br.com.novavia

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

class RadarService : AccessibilityService() {
    private val manager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val handler = Handler(Looper.getMainLooper())
    private val heartbeat = object : Runnable { override fun run() { ensureBubble(); handler.postDelayed(this, 1200) } }
    private var bubble: TextView? = null
    private var panel: TextView? = null
    private var lastEventAt = 0L
    private var lastRecognizedAt = 0L
    private val retry = Runnable { scan99() }
    private var currentOffer = "Aguardando oferta da 99"
    private var expanded = false
    private var lastOfferSignature = ""
    private var lastOfferSavedAt = 0L
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
            var startY = 0f
            var originalY = 0
            var moved = false
            view.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startY = event.rawY
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
                    WindowManager.LayoutParams.WRAP_CONTENT, 275).apply { flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE })
                panel = view
            }
            panel?.text = "NOVAVIA • RADAR 99\n$currentOffer\n\nToque na bolinha para fechar."
        } catch (_: Exception) { expanded = false; updatePanel() }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        handler.removeCallbacks(heartbeat)
        handler.post(heartbeat)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        ensureBubble()
        val pkg = event?.packageName?.toString() ?: return
        if (pkg != "com.app99.driver") {
            if (currentOffer == "Aguardando oferta da 99") updatePanel()
            return
        }
        if (!enabled()) return
        prefs.edit().putLong("last_99_event", System.currentTimeMillis()).apply()
        handler.removeCallbacks(retry)
        handler.postDelayed(retry, 300)
        if (SystemClock.elapsedRealtime() - lastEventAt >= 450) scan99()
    }

    private fun scan99() {
        if (!enabled()) return
        lastEventAt = SystemClock.elapsedRealtime()
        val root = rootInActiveWindow?.takeIf { it.packageName?.toString() == "com.app99.driver" } ?: run { prefs.edit().putString("last_99_state", "Tela indisponível para acessibilidade").apply(); return }
        val lines = mutableListOf<String>()
        fun visit(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 16 || lines.size >= 120) return
            node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { lines.add(it) }
            node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() && it != node.text?.toString()?.trim() }?.let { lines.add(it) }
            for (i in 0 until node.childCount) visit(node.getChild(i), depth + 1)
        }
        visit(root, 0)
        val offer = Analyzer.parse(lines.joinToString(" "), "99")
        prefs.edit().putString("last_99_state",
            if (offer == null) "Evento recebido, mas valor/km/tempo não identificados"
            else "Oferta reconhecida").putInt("last_node_count", lines.size).apply()
        if (offer == null && SystemClock.elapsedRealtime() - lastRecognizedAt < 8000L) return
        if (offer != null) {
            lastRecognizedAt = SystemClock.elapsedRealtime()
            val timestamp = System.currentTimeMillis()
            prefs.edit().putLong("last_99_offer", timestamp).apply()
            val signature = "${offer.price}|${offer.km}|${offer.minutes}"
            if (signature != lastOfferSignature || timestamp - lastOfferSavedAt > 120000L) {
                lastOfferSignature = signature
                lastOfferSavedAt = timestamp
                try {
                    val history = JSONArray(prefs.getString("auto_history", "[]") ?: "[]")
                    val item = JSONObject()
                        .put("id", timestamp)
                        .put("date", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US).format(java.util.Date(timestamp)))
                        .put("platform", "99")
                        .put("price", offer.price)
                        .put("km", offer.km)
                        .put("min", offer.minutes)
                        .put("perKm", offer.perKm)
                        .put("perHour", offer.perHour)
                        .put("net", offer.price - offer.km * (prefs.getString("cost", "0.80")?.toDoubleOrNull() ?: 0.8))
                        .put("rating", if (offer.partial) "partial" else {
                            val targetKm = prefs.getString("km", "1.80")?.toDoubleOrNull() ?: 1.8
                            val targetHour = prefs.getString("hour", "35")?.toDoubleOrNull() ?: 35.0
                            if (offer.perKm >= targetKm && offer.perHour >= targetHour) "good"
                            else if (offer.perKm >= targetKm * 0.8 && offer.perHour >= targetHour * 0.8) "medium"
                            else "bad"
                        })
                        .put("partial", offer.partial)
                    val updated = JSONArray().put(item)
                    for (i in 0 until minOf(history.length(), 99)) updated.put(history.get(i))
                    prefs.edit().putString("auto_history", updated.toString()).apply()
                } catch (_: Exception) {
                    prefs.edit().putString("last_99_state", "Oferta reconhecida; falha ao salvar histórico").apply()
                }
            }
        }
        currentOffer = if (offer == null) "Oferta ainda não reconhecida.\nAguardando valor, km e minutos visíveis."
        else {
            val cost = prefs.getString("cost", "0.80")?.toDoubleOrNull() ?: 0.8
            (if (offer.partial) "DADOS PARCIAIS • confirme o trajeto\n" else { val k = prefs.getString("km","1.80")?.toDoubleOrNull() ?: 1.8; val h = prefs.getString("hour","35")?.toDoubleOrNull() ?: 35.0; if (offer.perKm >= k && offer.perHour >= h) "🟢 BOA CORRIDA\n" else if (offer.perKm >= k * 0.8 && offer.perHour >= h * 0.8) "🟡 CORRIDA REGULAR\n" else "🔴 ABAIXO DAS METAS\n" }) +
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
    override fun onDestroy() { handler.removeCallbacks(heartbeat); handler.removeCallbacks(retry); removeAll(); super.onDestroy() }
}