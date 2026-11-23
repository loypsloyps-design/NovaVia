package br.com.novavia

import org.junit.Assert.*
import org.junit.Test

class AnalyzerTest {
    @Test fun recognizesReal99MotoDeliveryTwoLegs() {
        val offer = Analyzer.parse("Entrega Moto R$4,80 5,00 CPF verif. 10 min (2,2 km) 4 min (1,8 km) Aceitar", "99")
        assertNotNull(offer)
        assertEquals(4.80, offer!!.price, 0.001)
        assertEquals(4.0, offer.km, 0.001)
        assertEquals(14.0, offer.minutes, 0.001)
        assertEquals(1.20, offer.perKm, 0.001)
        assertEquals(4.8 * 60.0 / 14.0, offer.perHour, 0.001)
        assertFalse(offer.partial)
    }

    @Test fun refusesIncompleteTwoLegOffer() {
        assertNull(Analyzer.parse("R$4,80 10 min (2,2 km) 4 min", "99"))
    }

    @Test fun recognizesSimpleOffer() {
        val offer = Analyzer.parse("Oferta 99 R$ 25,50 10 km 30 min", "99")
        assertNotNull(offer)
        assertEquals(25.5, offer!!.price, 0.001)
        assertEquals(10.0, offer.km, 0.001)
        assertEquals(30.0, offer.minutes, 0.001)
        assertEquals(2.55, offer.perKm, 0.001)
        assertEquals(51.0, offer.perHour, 0.001)
        assertTrue(offer.partial)
    }

    @Test fun acceptsBrazilianDecimalComma() {
        val offer = Analyzer.parse("R$ 18,90 7,5 km 22 min", "99")
        assertNotNull(offer)
        assertEquals(7.5, offer!!.km, 0.001)
    }

    @Test fun refusesAmbiguousPrices() {
        assertNull(Analyzer.parse("R$ 20,00 R$ 15,00 8 km 20 min", "99"))
    }

    @Test fun refusesMultipleDistancesWithoutLabels() {
        assertNull(Analyzer.parse("R$ 30,00 2 km 8 km 25 min", "99"))
    }

    @Test fun refusesMultipleDurationsWithoutLabels() {
        assertNull(Analyzer.parse("R$ 30,00 10 km 5 min 25 min", "99"))
    }

    @Test fun refusesMissingFields() {
        assertNull(Analyzer.parse("R$ 30,00 10 km", "99"))
        assertNull(Analyzer.parse("10 km 20 min", "99"))
    }

    @Test fun refusesInvalidOrOutlierValues() {
        assertNull(Analyzer.parse("R$ 0,00 10 km 20 min", "99"))
        assertNull(Analyzer.parse("R$ 25,00 0 km 20 min", "99"))
        assertNull(Analyzer.parse("R$ 25,00 10 km 0 min", "99"))
        assertNull(Analyzer.parse("R$ 25,00 301 km 20 min", "99"))
    }

    @Test fun noAccidentalCalculationFromBusyScreen() {
        assertNull(Analyzer.parse("Saldo R$ 150,00 corrida R$ 24,00 2 km 7 km 5 min 18 min", "99"))
    }
}