package br.com.novavia

import java.util.Locale

data class Offer(
    val price: Double,
    val km: Double,
    val minutes: Double,
    val source: String,
    val partial: Boolean = false
) {
    val perKm get() = price / km
    val perHour get() = price * 60.0 / minutes
}

object Analyzer {
    private val priceRx = Regex("""R\$\s*([0-9]{1,4}(?:[.,][0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val kmRx = Regex("""\b([0-9]{1,3}(?:[.,][0-9]{1,2})?)\s*km\b""", RegexOption.IGNORE_CASE)
    private val minRx = Regex("""\b([0-9]{1,3})\s*(?:minutos?|min)\b""", RegexOption.IGNORE_CASE)
    private val segmentRx = Regex("""\b([0-9]{1,3})\s*(?:minutos?|min)\s*\(\s*([0-9]{1,3}(?:[.,][0-9]{1,2})?)\s*km\s*\)""", RegexOption.IGNORE_CASE)
    private fun number(s: String) = s.replace(',', '.').toDoubleOrNull()

    fun parse(text: String, source: String): Offer? {
        val prices = priceRx.findAll(text).mapNotNull { number(it.groupValues[1]) }.distinct().toList()
        if (prices.size != 1) return null
        val price = prices.single()
        if (price !in 1.0..2000.0) return null

        // 99 Entrega Moto: "10 min (2,2 km)" and "4 min (1,8 km)".
        // Accept exactly two complete segments; never combine unrelated figures.
        val segments = segmentRx.findAll(text).mapNotNull {
            val minutes = number(it.groupValues[1])
            val km = number(it.groupValues[2])
            if (minutes != null && km != null) Pair(minutes, km) else null
        }.toList()
        if (segments.size == 2) {
            val totalMinutes = segments.sumOf { it.first }
            val totalKm = segments.sumOf { it.second }
            if (totalMinutes in 1.0..600.0 && totalKm in 0.1..300.0)
                return Offer(price, totalKm, totalMinutes, source, partial = false)
            return null
        }
        if (segments.isNotEmpty()) return null

        val distances = kmRx.findAll(text).mapNotNull { number(it.groupValues[1]) }.toList()
        val durations = minRx.findAll(text).mapNotNull { number(it.groupValues[1]) }.toList()
        if (distances.size != 1 || durations.size != 1) return null
        val km = distances.single()
        val minutes = durations.single()
        if (km !in 0.1..300.0 || minutes !in 1.0..600.0) return null
        return Offer(price, km, minutes, source, partial = true)
    }

    fun money(n: Double) = String.format(Locale("pt", "BR"), "R$ %.2f", n)
}
