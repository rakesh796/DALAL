package com.dalal.scalp

import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Reads your Termux helper's read-only GET /api/state (positions, orders, login, last tick)
 * and works out loss after charges, trades, open positions and data freshness.
 */
object Engine {

    data class Pos(val token: Long, val tsym: String, val exch: String, val qty: Int, val pnl: Double, val lp: Double)

    data class Ord(
        val id: String, val token: Long, val tsym: String, val exch: String, val side: String,
        val type: String, val qty: Int, val filled: Int, val avgp: Double, val status: String, val ts: Long
    )

    data class Event(val time: Long, val text: String, val color: Int)

    data class Snap(
        val ok: Boolean = false,               // helper reachable
        val login: String = "unknown",         // ok | needed | checking | nocreds
        val streaming: Boolean = false,
        val stale: Boolean = false,
        val staleSec: Long = 0,
        val priceName: String = "",
        val price: Double? = null,
        val priceChg: Double? = null,
        val positions: List<Pos> = emptyList(),
        val orders: List<Ord> = emptyList(),
        val gross: Double = 0.0,
        val charges: Double = 0.0,
        val net: Double = 0.0,
        val trades: Int = 0,
        val openCount: Int = 0,
        val openPnl: Double = 0.0,
        val error: String? = null,
        val time: Long = 0
    ) {
        /** Loss after charges as a positive number (0 when in profit) */
        val loss: Double get() = (-net).coerceAtLeast(0.0)
    }

    @Volatile
    var snap: Snap = Snap()
        private set

    private val listeners = CopyOnWriteArrayList<(Snap) -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    val events = CopyOnWriteArrayList<Event>()

    fun addListener(l: (Snap) -> Unit) { listeners.add(l) }
    fun removeListener(l: (Snap) -> Unit) { listeners.remove(l) }

    fun log(text: String, color: Int) {
        events.add(0, Event(System.currentTimeMillis(), text, color))
        while (events.size > 30) events.removeAt(events.size - 1)
    }

    fun baseUrl(wallUrl: String): String {
        val u = Uri.parse(wallUrl)
        val scheme = u.scheme ?: "http"
        val auth = u.authority ?: "127.0.0.1:8000"
        return "$scheme://$auth"
    }

    /** Blocking: call from a background thread. Publishes the result to listeners on the main thread. */
    fun poll(base: String): Snap {
        val s = try {
            parse(JSONObject(get("$base/api/state")))
        } catch (e: Exception) {
            Snap(ok = false, error = e.javaClass.simpleName + ": " + (e.message ?: ""), time = System.currentTimeMillis())
        }
        val wasOk = snap.ok
        if (wasOk && !s.ok && snap.time > 0) log("Helper stopped responding", C.RED)
        if (!wasOk && s.ok && snap.time > 0) log("Helper back online", C.GREEN)
        if (!snap.stale && s.stale) log("Prices stale: no tick for ${s.staleSec}s", C.RED)
        snap = s
        main.post { listeners.forEach { it(s) } }
        return s
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 2500
        c.readTimeout = 4000
        c.useCaches = false
        try {
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun parse(j: JSONObject): Snap {
        val st = j.optJSONObject("st") ?: JSONObject()
        val server = j.optLong("server", System.currentTimeMillis())
        val lastTick = j.optLong("lt", 0)
        val streaming = st.optBoolean("stream", false)
        val staleMs = if (lastTick > 0) server - lastTick else 0
        val stale = streaming && lastTick > 0 && staleMs > 15_000

        val pf = j.optJSONObject("pf") ?: JSONObject()
        val positions = mutableListOf<Pos>()
        val pa = pf.optJSONArray("positions") ?: JSONArray()
        for (i in 0 until pa.length()) {
            val p = pa.optJSONObject(i) ?: continue
            positions.add(
                Pos(p.optLong("token"), p.optString("tsym"), p.optString("exch"), p.optInt("qty"),
                    p.optDouble("pnl", 0.0), p.optDouble("lp", 0.0))
            )
        }
        val orders = mutableListOf<Ord>()
        val oa = pf.optJSONArray("orders") ?: JSONArray()
        for (i in 0 until oa.length()) {
            val o = oa.optJSONObject(i) ?: continue
            orders.add(
                Ord(o.optString("id"), o.optLong("token"), o.optString("tsym"), o.optString("exch"),
                    o.optString("side"), o.optString("type"), o.optInt("qty"), o.optInt("filled"),
                    o.optDouble("avgp", 0.0), o.optString("status").uppercase(), o.optLong("ts"))
            )
        }

        val gross = positions.sumOf { it.pnl }
        val done = orders.filter { it.status == "COMPLETE" && it.filled > 0 }
        val charges = done.sumOf { orderCharges(it) }
        val open = positions.filter { it.qty != 0 }

        // Price for the bubble: an open position first, else the first option/future on your list
        var name = ""
        var price: Double? = null
        var chg: Double? = null
        val items = j.optJSONArray("items") ?: JSONArray()
        val openTokens = open.map { it.token }.toSet()
        var pick: JSONObject? = null
        for (i in 0 until items.length()) {
            val it = items.optJSONObject(i) ?: continue
            if (it.optLong("token") in openTokens) { pick = it; break }
        }
        if (pick == null) {
            for (i in 0 until items.length()) {
                val it = items.optJSONObject(i) ?: continue
                if (it.optString("itype") in setOf("CE", "PE", "FUT")) { pick = it; break }
            }
        }
        if (pick == null && items.length() > 0) pick = items.optJSONObject(0)
        if (pick != null) {
            name = pick.optString("label").ifEmpty { pick.optString("tsym") }
            val d = pick.optJSONObject("data")
            if (d != null && d.has("ltp")) price = d.optDouble("ltp")
            if (d != null && d.has("chg")) chg = d.optDouble("chg")
        }

        return Snap(
            ok = true,
            login = st.optString("login", "unknown"),
            streaming = streaming,
            stale = stale,
            staleSec = staleMs / 1000,
            priceName = name,
            price = price,
            priceChg = chg,
            positions = positions,
            orders = orders,
            gross = gross,
            charges = charges,
            net = gross - charges,
            trades = (done.size + 1) / 2,
            openCount = open.size,
            openPnl = open.sumOf { it.pnl },
            time = System.currentTimeMillis()
        )
    }

    /** Zerodha charges for one executed options order */
    fun orderCharges(o: Ord): Double {
        val t = o.avgp * o.filled
        if (t <= 0) return 0.0
        val bse = o.exch == "BFO" || o.exch == "BSE"
        val brokerage = 20.0
        val exchange = t * (if (bse) 0.000325 else 0.0003553)
        val sebi = t * 0.000001
        val stt = if (o.side == "SELL") t * 0.0015 else 0.0
        val stamp = if (o.side == "BUY") t * 0.00003 else 0.0
        val gst = 0.18 * (brokerage + exchange + sebi)
        return brokerage + exchange + sebi + stt + stamp + gst
    }

    fun hasStop(token: Long, orders: List<Ord>): Boolean = orders.any {
        it.token == token && (it.type == "SL" || it.type == "SL-M") &&
            (it.status == "OPEN" || it.status == "TRIGGER PENDING")
    }
}
