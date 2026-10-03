package com.dalal.scalp

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/** Swipe-right page: clock that knows the 3:40 PM close, expiries, events, risk calculator, checklist. */
class TodayPage(private val a: LauncherActivity) {

    val view: FrameLayout = FrameLayout(a).apply {
        setBackgroundColor(C.BG)
        visibility = View.GONE
        isClickable = true
    }
    var isOpen = false
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val clock = a.tv("", 50f, C.TEXT, true)
    private val date = a.tv("", 13f, C.MUTED)
    private val status = a.tv("", 13f, C.AMBER, true)
    private val badges = a.hbox()
    private val events = a.vbox()
    private val checks = a.vbox()
    private val limits = a.tv("", 12.5f, C.MUTED)
    private val loginTile = a.card()
    private val loginText = a.tv("", 13f, C.TEXT)
    private val riskTitle = a.tv("", 15f, C.TEXT, true)
    private val riskBarFill = android.view.View(a)
    private val riskBar = FrameLayout(a)
    private val riskSub = a.tv("", 12.5f, C.MUTED)
    private val alerts = a.vbox()
    private lateinit var scroll: ScrollView

    // calculator
    private var index = Charges.INDICES[0]
    private val indexTabs = a.hbox()
    private lateinit var entry: EditText
    private lateinit var stop: EditText
    private lateinit var risk: EditText
    private val result = a.tv("", 14f, C.TEXT, true)
    private val resultSub = a.tv("", 12.5f, C.MUTED)

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            if (isOpen) handler.postDelayed(this, 1000)
        }
    }

    private val checklistItems = listOf(
        "Termux helper running, prices live",
        "Levels marked for today",
        "Max loss set: stop for the day at the limit",
        "Stop order with every entry",
        "Kill switch route known (Kite → Profile → Segments)"
    )

    init {
        build()
    }

    private fun build() {
        scroll = ScrollView(a).apply { isVerticalScrollBarEnabled = false }
        val col = a.vbox().apply { setPadding(a.dp(18), a.dp(18), a.dp(18), a.dp(40)) }
        scroll.addView(col)
        view.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))

        col.addView(clock)
        col.addView(date)
        col.addView(status, a.lp().apply { topMargin = a.dp(4) })
        col.addView(HorizontalScrollView(a).apply {
            isHorizontalScrollBarEnabled = false
            addView(badges)
        }, a.lp().apply { topMargin = a.dp(12) })

        // Morning login tile (shows when Kite login is needed or the helper is down)
        loginTile.addView(a.tv("MORNING LOGIN", 11f, C.AMBER, true).apply { letterSpacing = 0.08f })
        loginTile.addView(loginText, a.lp().apply { topMargin = a.dp(4) })
        val lrow = a.hbox()
        lrow.addView(a.btn("Log in to Kite", filled = true) { a.openUrl(Engine.baseUrl(a.prefs.wallUrl) + "/login") },
            a.weighted().apply { rightMargin = a.dp(8) })
        lrow.addView(a.btn("Restart helper") { Termux.run(a, "restart") }, a.weighted())
        loginTile.addView(lrow, a.lp().apply { topMargin = a.dp(10) })
        loginTile.visibility = View.GONE
        col.addView(loginTile, a.lp().apply { topMargin = a.dp(14) })

        // Live risk (loss after charges, from your Kite positions via the helper)
        col.addView(a.section("RISK TODAY"))
        val riskCard = a.card()
        riskCard.addView(riskTitle)
        riskBar.background = a.rounded(0xFF1A2540.toInt(), 4)
        riskBarFill.background = a.rounded(C.GREEN, 4)
        riskBar.addView(riskBarFill, FrameLayout.LayoutParams(0, MATCH))
        riskCard.addView(riskBar, LinearLayout.LayoutParams(MATCH, a.dp(8)).apply { topMargin = a.dp(8) })
        riskCard.addView(riskSub, a.lp().apply { topMargin = a.dp(8) })
        col.addView(riskCard)

        col.addView(a.section("ALERTS"))
        col.addView(alerts)

        val jrow = a.hbox()
        jrow.addView(a.btn("Journal & weekly review", filled = true) { a.startActivity(Intent(a, JournalActivity::class.java)) }, a.weighted())
        col.addView(jrow, a.lp().apply { topMargin = a.dp(12) })

        // Morning / reliability kit
        col.addView(a.section("MORNING & RELIABILITY"))
        val row1 = a.hbox()
        row1.addView(a.btn("Open Termux") { a.launchPkg("com.termux") }, a.weighted().apply { rightMargin = a.dp(8) })
        row1.addView(a.btn("Open Kite", filled = true) { a.launchPkg("com.zerodha.kite3") }, a.weighted())
        col.addView(row1)
        val row2 = a.hbox()
        row2.addView(a.btn("Restart helper") { Termux.run(a, "restart") }, a.weighted().apply { rightMargin = a.dp(8) })
        row2.addView(a.btn("Battery settings") {
            a.safeStart(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }, a.weighted())
        col.addView(row2, a.lp().apply { topMargin = a.dp(8) })

        // Risk & cost calculator
        col.addView(a.section("RISK & COST CALCULATOR"))
        val calc = a.card()
        calc.addView(indexTabs)
        entry = numberField("Entry premium", "100")
        stop = numberField("Stop distance (points)", "10")
        risk = numberField("Risk per trade (₹)", a.prefs.riskPerTrade.toString())
        calc.addView(labeled("Entry premium", entry))
        calc.addView(labeled("Stop distance (points)", stop))
        calc.addView(labeled("Risk per trade ₹", risk))
        calc.addView(result, a.lp().apply { topMargin = a.dp(10) })
        calc.addView(resultSub, a.lp().apply { topMargin = a.dp(2) })
        col.addView(calc)
        buildTabs()
        listOf(entry, stop, risk).forEach { f -> f.doAfterTextChanged { calculate() } }

        col.addView(a.section("RULES"))
        col.addView(limits)

        col.addView(a.section("EVENTS"))
        col.addView(events)

        col.addView(a.section("CHECKLIST · resets daily"))
        col.addView(checks)

        col.addView(a.tv("News shows here after 3:40 PM in a later version. During market hours it stays off.", 11.5f, C.DIM),
            a.lp().apply { topMargin = a.dp(18) })

        col.addView(a.btn("DALAL settings · wallpaper, dock, trading mode") {
            a.startActivity(Intent(a, SettingsActivity::class.java))
        }, a.lp().apply { topMargin = a.dp(14) })
    }

    private fun numberField(hint: String, value: String): EditText = EditText(a).apply {
        this.hint = hint
        setText(value)
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        setTextColor(C.TEXT)
        setHintTextColor(C.DIM)
        textSize = 15f
        gravity = Gravity.END
        background = a.rounded(C.PANEL2, 10)
        setPadding(a.dp(10), a.dp(8), a.dp(10), a.dp(8))
        minWidth = a.dp(110)
    }

    private fun labeled(label: String, field: EditText): LinearLayout = a.hbox().apply {
        setPadding(0, a.dp(8), 0, 0)
        addView(a.tv(label, 13f, C.MUTED), a.weighted())
        addView(field, LinearLayout.LayoutParams(a.dp(120), WRAP))
    }

    private fun buildTabs() {
        indexTabs.removeAllViews()
        Charges.INDICES.forEach { idx ->
            val on = idx == index
            val t = a.tv("${idx.name} · ${idx.lot}", 12f, if (on) C.INK else C.MUTED, true).apply {
                gravity = Gravity.CENTER
                setPadding(a.dp(6), a.dp(8), a.dp(6), a.dp(8))
                background = if (on) a.rounded(C.AMBER, 9) else a.rounded(C.PANEL2, 9)
                setOnClickListener {
                    index = idx
                    buildTabs()
                    calculate()
                }
            }
            indexTabs.addView(t, a.weighted().apply { rightMargin = a.dp(6) })
        }
    }

    private fun calculate() {
        val e = entry.text.toString().toDoubleOrNull()
        val s = stop.text.toString().toDoubleOrNull()
        val r = risk.text.toString().toDoubleOrNull()
        if (e == null || s == null || r == null || e <= 0 || s <= 0 || r <= 0) {
            result.text = "Enter entry, stop and risk"
            resultSub.text = ""
            return
        }
        val lots = floor(r / (s * index.lot)).toInt()
        val qty = maxOf(1, lots) * index.lot
        val charges = Charges.roundTrip(e, e + s, qty, index.bse)
        val breakEven = charges / qty
        if (lots < 1) {
            result.text = "Stop too wide for ₹${r.roundToInt()} risk"
            result.setTextColor(C.RED)
            resultSub.text = "Even 1 lot risks ₹${(s * index.lot).roundToInt()} + ≈₹${charges.roundToInt()} charges"
        } else {
            result.text = "$lots lot${if (lots > 1) "s" else ""} · $qty qty"
            result.setTextColor(C.GREEN)
            resultSub.text = "Max loss at stop ₹${(s * qty).roundToInt()} + ≈₹${charges.roundToInt()} charges\n" +
                "Points to break even: ${String.format(Locale.ENGLISH, "%.1f", breakEven)}"
        }
    }

    fun open() {
        if (isOpen) return
        isOpen = true
        refresh()
        scroll.scrollTo(0, 0)
        view.visibility = View.VISIBLE
        view.translationX = -view.width.toFloat().coerceAtLeast(1f)
        view.animate().translationX(0f).setDuration(220).start()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        handler.removeCallbacks(ticker)
        view.animate().translationX(-view.width.toFloat()).setDuration(200)
            .withEndAction { if (!isOpen) view.visibility = View.GONE }.start()
    }

    fun refresh() {
        tick()
        badges.removeAllViews()
        MarketCalendar.expiryBadges().forEach { b ->
            badges.addView(a.tv(b.text, 12f, if (b.hot) C.AMBER else C.TEXT, true).apply {
                background = a.rounded(C.PANEL, 8, if (b.hot) C.AMBER else C.BORDER)
                setPadding(a.dp(10), a.dp(6), a.dp(10), a.dp(6))
            }, LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = a.dp(6) })
        }
        events.removeAllViews()
        val ev = MarketCalendar.upcomingEvents()
        if (ev.isEmpty()) events.addView(a.tv("No events listed", 12.5f, C.MUTED))
        ev.forEach { events.addView(a.tv("• $it", 13f, C.TEXT).apply { setPadding(0, a.dp(3), 0, a.dp(3)) }) }

        limits.text = "Daily loss limit ₹${a.prefs.effectiveLossLimit()} after charges · max ${a.prefs.maxTrades} trades · risk ₹${a.prefs.riskPerTrade} per trade\n" +
            "Trading mode ${if (a.prefs.tradingMode) "on: distraction apps hidden 9:00 AM–3:40 PM" else "off"}"

        val day = MarketCalendar.today().toString()
        val done = a.prefs.checklist(day).toMutableSet()
        checks.removeAllViews()
        checklistItems.forEach { item ->
            checks.addView(CheckBox(a).apply {
                text = item
                setTextColor(C.TEXT)
                textSize = 13.5f
                buttonTintList = ColorStateList.valueOf(C.AMBER)
                isChecked = item in done
                setOnCheckedChangeListener { _, checked ->
                    if (checked) done.add(item) else done.remove(item)
                    a.prefs.setChecklist(day, done)
                }
            })
        }
        if (risk.text.isNullOrEmpty()) risk.setText(a.prefs.riskPerTrade.toString())
        calculate()
        onEngine(Engine.snap)
    }

    fun onEngine(s: Engine.Snap) {
        val limit = a.prefs.effectiveLossLimit()
        // login tile
        val needLogin = s.ok && (s.login == "needed" || s.login == "nocreds")
        loginTile.visibility = if (!s.ok || needLogin) View.VISIBLE else View.GONE
        loginText.text = when {
            !s.ok -> "Helper not responding. Restart it, or open Termux and run: kw start"
            s.login == "nocreds" -> "Helper has no API key. Run the helper once in Termux."
            else -> "Kite login expired at 6 AM. One tap, then 2FA."
        }
        // risk
        if (!s.ok) {
            riskTitle.text = "Can't see your P&L (helper offline)"
            riskTitle.setTextColor(C.MUTED)
            riskSub.text = ""
            riskBarFill.layoutParams = (riskBarFill.layoutParams as FrameLayout.LayoutParams).apply { width = 0 }
        } else {
            val loss = s.loss
            val color = when {
                loss >= limit -> C.RED
                loss >= limit * 0.5 -> C.AMBER
                else -> C.GREEN
            }
            riskTitle.text = if (s.net >= 0) "+₹${s.net.roundToInt()} after charges · limit ₹$limit"
                else "−₹${loss.roundToInt()} of ₹$limit after charges"
            riskTitle.setTextColor(color)
            (riskBarFill.background as? android.graphics.drawable.GradientDrawable)?.setColor(color)
            val w = riskBar.width
            if (w > 0) {
                riskBarFill.layoutParams = (riskBarFill.layoutParams as FrameLayout.LayoutParams).apply {
                    width = (w * (loss / limit).coerceIn(0.0, 1.0)).toInt()
                }
            } else if (isOpen) {
                riskBar.post { if (isOpen) onEngine(s) }
            }
            val gross = if (s.gross < 0) "−₹${(-s.gross).roundToInt()}" else "+₹${s.gross.roundToInt()}"
            riskSub.text = "Trades ${s.trades} of ${a.prefs.maxTrades} · charges ₹${s.charges.roundToInt()} · gross $gross" +
                (if (s.openCount > 0) "\n${s.openCount} open position${if (s.openCount > 1) "s" else ""}" else "") +
                (if (s.stale) "\nPrices stale: no tick for ${s.staleSec}s" else "")
        }
        // alerts feed
        alerts.removeAllViews()
        val f = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
        val ev = Engine.events.take(6)
        if (ev.isEmpty()) alerts.addView(a.tv("No alerts today", 12.5f, C.MUTED))
        ev.forEach { e ->
            val t = java.time.Instant.ofEpochMilli(e.time).atZone(MarketCalendar.ZONE).format(f)
            alerts.addView(a.tv("$t  ${e.text}", 13f, e.color).apply { setPadding(0, a.dp(3), 0, a.dp(3)) })
        }
    }

    private fun tick() {
        val n = MarketCalendar.now()
        clock.text = n.format(DateTimeFormatter.ofPattern("h:mm", Locale.ENGLISH))
        date.text = n.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH))
        status.text = MarketCalendar.statusLine()
    }
}
