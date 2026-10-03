package com.dalal.scalp

/** Zerodha F&O option charges (zerodha.com/charges). Lot sizes current for 2026 series. */
object Charges {
    data class Index(val name: String, val lot: Int, val bse: Boolean)

    val INDICES = listOf(
        Index("NIFTY", 65, false),
        Index("BANKNIFTY", 30, false),
        Index("SENSEX", 20, true)
    )

    /** Total charges for one buy + one sell of [qty] at the given premiums. */
    fun roundTrip(buyPrice: Double, sellPrice: Double, qty: Int, bse: Boolean): Double {
        val buyT = buyPrice * qty
        val sellT = sellPrice * qty
        val turnover = buyT + sellT
        val brokerage = 40.0                                   // ₹20 per order
        val stt = sellT * 0.0015                               // 0.15% on sell premium
        val exchange = turnover * (if (bse) 0.000325 else 0.0003553)
        val sebi = turnover * 0.000001                         // ₹10 per crore
        val stamp = buyT * 0.00003                             // 0.003% on buys
        val gst = 0.18 * (brokerage + exchange + sebi)
        return brokerage + stt + exchange + sebi + stamp + gst
    }
}
