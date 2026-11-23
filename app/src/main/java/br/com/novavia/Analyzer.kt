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
    private fun number(s: String) = s.replace(',', '.').toDoubleOrNull()

    // Conservative: never sum arbitrary figures from the screen.
    // Only one distance and one duration are accepted; this may omit pickup distance/time.
    // Ambiguous offers are skipped instead of being labeled profitable.
    fun parse(text: String, source: String): Offer? {
        val prices = priceRx.findAll(text).mapNotNull { number(it.groupValues[1]) }.distinct().toList()
        val distances = kmRx.findAll(text).mapNotNull { number(it.groupValues[1]) }.toList()
        val durations = minRx.findAll(text).mapNotNull { number(it.groupValues[1]) }.toList()
        if (prices.size != 1 || distances.size != 1 || durations.size != 1) return null
        val price = prices.single()
        val km = distances.single()
        val minutes = durations.single()
        if (price !in 1.0..2000.0 || km !in 0.1..300.0 || minutes !in 1.0..600.0) return null
        return Offer(price, km, minutes, source, partial = true)
    }

    fun money(n: Double) = String.format(Locale("pt", "BR"), "R$ %.2f", n)
}