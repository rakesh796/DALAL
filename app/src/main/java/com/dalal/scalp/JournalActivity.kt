package com.dalal.scalp

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Three-tap journal (Kite fills the trade) + weekly leak review. */
class JournalActivity : AppCompatActivity() {

    private lateinit var col: LinearLayout
    private var setup = ""
    private var plan = ""
    private var feeling = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = C.BG
        val scroll = ScrollView(this).apply { setBackgroundColor(C.BG) }
        col = vbox().apply { setPadding(dp(18), dp(20), dp(18), dp(48)) }
        scroll.addView(col)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun money(v: Double) = (if (v < 0) "−₹" else "+₹") + abs(v).roundToInt()

    private fun render() {
        col.removeAllViews()
        col.addView(tv("Journal", 24f, C.TEXT, true))
        val pending = Journal.pending(this)
        if (pending.isNotEmpty()) {
            val e = pending.first()
            col.addView(tv("${pending.size} trade${if (pending.size > 1) "s" else ""} to journal", 13f, C.AMBER, true),
                lp().apply { topMargin = dp(4) })
            val card = card()
            card.addView(tv("FILLED FROM KITE", 10.5f, C.GREEN, true).apply { letterSpacing = 0.06f })
            val row = hbox()
            row.addView(tv(e.tsym, 15f, C.TEXT, true), weighted())
            row.addView(tv(money(e.pnl), 15f, if (e.pnl >= 0) C.GREEN else C.RED, true))
            card.addView(row, lp().apply { topMargin = dp(4) })
            card.addView(tv(time(e.ts), 12f, C.MUTED))
            col.addView(card, lp().apply { topMargin = dp(12) })

            col.addView(section("SETUP"))
            col.addView(chips(Journal.SETUPS, setup) { setup = it; maybeSave(e.id) })
            col.addView(section("PLAN FOLLOWED?"))
            col.addView(chips(Journal.PLANS, plan) { plan = it; maybeSave(e.id) })
            col.addView(section("FEELING"))
            col.addView(chips(Journal.FEELINGS, feeling) { feeling = it; maybeSave(e.id) })
        } else {
            col.addView(tv("All trades journaled.", 13f, C.GREEN, true), lp().apply { topMargin = dp(4) })
        }
        review()
    }

    private fun chips(options: List<String>, selected: String, onPick: (String) -> Unit): LinearLayout {
        val wrap = vbox()
        options.chunked(3).forEach { rowOpts ->
            val row = hbox().apply { setPadding(0, 0, 0, dp(6)) }
            rowOpts.forEach { o ->
                val on = o == selected
                row.addView(tv(o, 14f, if (on) C.INK else C.TEXT, true).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(11), dp(8), dp(11))
                    background = if (on) rounded(C.AMBER, 11) else rounded(C.PANEL, 11, 0xFF24345A.toInt())
                    setOnClickListener { onPick(o) }
                }, weighted().apply { rightMargin = dp(6) })
            }
            repeat(3 - rowOpts.size) { row.addView(android.view.View(this), weighted().apply { rightMargin = dp(6) }) }
            wrap.addView(row)
        }
        return wrap
    }

    private fun maybeSave(id: Long) {
        if (setup.isNotEmpty() && plan.isNotEmpty() && feeling.isNotEmpty()) {
            Journal.complete(this, id, setup, plan, feeling)
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            setup = ""; plan = ""; feeling = ""
        }
        render()
    }

    private fun time(ts: Long): String =
        DateTimeFormatter.ofPattern("EEE d MMM · h:mm a", Locale.ENGLISH).format(Instant.ofEpochMilli(ts).atZone(MarketCalendar.ZONE))

    /** Weekly leak review: where the money goes */
    private fun review() {
        val week = Journal.lastDays(this, 7).filter { it.done }
        col.addView(section("WEEKLY LEAK REVIEW · last 7 days"))
        if (week.isEmpty()) {
            col.addView(tv("Journal a few trades and your leaks show up here.", 13f, C.MUTED))
            return
        }
        val c = card()
        val total = week.sumOf { it.pnl }
        val followed = week.filter { it.plan == "Yes" }
        val broke = week.filter { it.plan == "No" }
        c.addView(line("Trades", "${week.size}"))
        c.addView(line("P&L (before charges)", money(total), if (total >= 0) C.GREEN else C.RED))
        c.addView(line("Plan followed", "${(followed.size * 100.0 / week.size).roundToInt()}%"))
        c.addView(line("P&L when plan followed", money(followed.sumOf { it.pnl })))
        c.addView(line("P&L when plan broken", money(broke.sumOf { it.pnl }), if (broke.sumOf { it.pnl } < 0) C.RED else C.TEXT))
        val worstFeeling = week.groupBy { it.feeling }.mapValues { e -> e.value.sumOf { it.pnl } }.minByOrNull { it.value }
        val worstSetup = week.groupBy { it.setup }.mapValues { e -> e.value.sumOf { it.pnl } }.minByOrNull { it.value }
        if (worstFeeling != null && worstFeeling.value < 0) c.addView(line("Costliest feeling", "${worstFeeling.key} ${money(worstFeeling.value)}", C.RED))
        if (worstSetup != null && worstSetup.value < 0) c.addView(line("Costliest setup", "${worstSetup.key} ${money(worstSetup.value)}", C.RED))
        col.addView(c)

        col.addView(section("RECENT"))
        Journal.all(this).filter { it.done }.sortedByDescending { it.ts }.take(15).forEach { e ->
            val r = hbox().apply { setPadding(0, dp(6), 0, dp(6)) }
            val left = vbox()
            left.addView(tv(e.tsym, 13f, C.TEXT, true))
            left.addView(tv("${time(e.ts)} · ${e.setup} · plan ${e.plan} · ${e.feeling}", 11.5f, C.MUTED))
            r.addView(left, weighted())
            r.addView(tv(money(e.pnl), 13f, if (e.pnl >= 0) C.GREEN else C.RED, true))
            col.addView(r)
        }
    }

    private fun line(label: String, value: String, color: Int = C.TEXT): LinearLayout = hbox().apply {
        setPadding(0, dp(4), 0, dp(4))
        addView(tv(label, 13f, C.MUTED), weighted())
        addView(tv(value, 13.5f, color, true))
    }
}
