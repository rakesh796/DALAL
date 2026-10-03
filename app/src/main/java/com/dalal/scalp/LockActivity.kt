package com.dalal.scalp

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.roundToInt

/**
 * Pause (75% of limit, 15 minutes) and Day-over lock (100%).
 * Exits are never blocked: "I need to close a position" opens Kite after a 10-second wait and is logged.
 */
class LockActivity : AppCompatActivity() {

    private val prefs by lazy { Prefs(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var mode = MODE_LOCK
    private lateinit var col: LinearLayout
    private lateinit var timer: TextView
    private lateinit var openText: TextView
    private lateinit var overrideBtn: TextView
    private var overrideAt = 0L
    private var killDone = false

    private val listener: (Engine.Snap) -> Unit = { s -> render(s) }

    private val tick = object : Runnable {
        override fun run() {
            updateTimers()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_LOCK
        window.statusBarColor = 0xFF0B0509.toInt()
        val scroll = ScrollView(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF3A0D16.toInt(), 0xFF0B0509.toInt(), 0xFF0B0509.toInt()))
        }
        col = vbox().apply { setPadding(dp(24), dp(48), dp(24), dp(40)) }
        scroll.addView(col)
        setContentView(scroll)
        build()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        mode = intent.getStringExtra(EXTRA_MODE) ?: mode
        build()
    }

    override fun onResume() {
        super.onResume()
        Engine.addListener(listener)
        render(Engine.snap)
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        Engine.removeListener(listener)
        handler.removeCallbacks(tick)
    }

    private fun build() {
        col.removeAllViews()
        val s = Engine.snap
        val limit = prefs.effectiveLossLimit()
        if (mode == MODE_PAUSE) {
            col.addView(tv("PAUSE · ${(limit * 0.75).roundToInt()} OF ₹$limit USED", 12f, C.AMBER, true).apply { letterSpacing = 0.1f })
            timer = tv("15:00", 64f, C.TEXT, true)
            col.addView(timer, lp().apply { topMargin = dp(10) })
            col.addView(tv("15-minute pause. Step away from the screen and re-read your plan.\nLoss now: ₹${s.loss.roundToInt()} after charges.",
                15f, 0xFFF4D9A5.toInt()), lp().apply { topMargin = dp(6) })
            openText = tv("", 13f, C.MUTED)
            col.addView(openText, lp().apply { topMargin = dp(18) })
            col.addView(btn("Back to home", filled = true) { goHome() }, lp().apply { topMargin = dp(22) })
        } else {
            col.addView(tv("LOSS LIMIT HIT", 12f, C.RED, true).apply { letterSpacing = 0.12f })
            col.addView(tv("Day over.", 44f, C.TEXT, true), lp().apply { topMargin = dp(6) })
            col.addView(tv("−₹${s.loss.roundToInt()} after charges · limit ₹$limit", 16f, 0xFFF4A5AD.toInt(), true))
            timer = tv("", 1f, C.BG) // unused in lock mode

            col.addView(stepCard("1", "Close all positions", null).also { card ->
                openText = tv("", 12.5f, C.MUTED)
                card.addView(openText)
                card.addView(btn("Open Kite positions") { openKite(logOverride = false) }, lp().apply { topMargin = dp(8) })
            }, lp().apply { topMargin = dp(22) })

            col.addView(stepCard("2", "Turn on the kill switch", "Kite → Profile → Segments. Locks F&O for 12 hours. Only possible once you're flat.").also { card ->
                card.addView(btn(if (killDone) "Kill switch on ✓" else "I've turned it on", filled = killDone) {
                    killDone = true
                    Engine.log("Kill switch confirmed", C.GREEN)
                    build()
                }, lp().apply { topMargin = dp(8) })
            }, lp().apply { topMargin = dp(10) })

            col.addView(stepCard("3", "Journal today", "3 taps per trade, while it's fresh.").also { card ->
                card.addView(btn("Open journal") { startActivity(Intent(this, JournalActivity::class.java)) }, lp().apply { topMargin = dp(8) })
            }, lp().apply { topMargin = dp(10) })

            col.addView(tv("Kite stays greyed out in DALAL until tomorrow's session.", 12.5f, C.MUTED).apply { gravity = Gravity.CENTER },
                lp().apply { topMargin = dp(22) })
            col.addView(btn("Back to home") { goHome() }, lp().apply { topMargin = dp(12) })
        }

        overrideBtn = tv("I need to close a position", 13f, 0xFFF4A5AD.toInt(), true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(0x00000000, 12, 0xFF5A2A33.toInt())
            setOnClickListener {
                if (overrideAt == 0L) {
                    overrideAt = System.currentTimeMillis() + 10_000
                    updateTimers()
                }
            }
        }
        col.addView(overrideBtn, lp().apply { topMargin = dp(12) })
        render(s)
    }

    private fun stepCard(n: String, title: String, sub: String?): LinearLayout = vbox().apply {
        background = rounded(0x0FFFFFFF, 14, 0xFF3A1B24.toInt())
        setPadding(dp(14), dp(12), dp(14), dp(12))
        addView(tv("$n  $title", 15f, C.TEXT, true))
        if (sub != null) addView(tv(sub, 12.5f, C.MUTED), lp().apply { topMargin = dp(4) })
    }

    private fun render(s: Engine.Snap) {
        if (!::openText.isInitialized) return
        openText.text = when {
            !s.ok -> "Helper offline: can't see your positions. Check Kite directly."
            s.openCount == 0 -> "You're flat: no open positions."
            else -> "${s.openCount} open position${if (s.openCount > 1) "s" else ""} · open P&L ${if (s.openPnl < 0) "−" else "+"}₹${kotlin.math.abs(s.openPnl).roundToInt()}"
        }
        openText.setTextColor(if (s.ok && s.openCount == 0) C.GREEN else C.MUTED)
    }

    private fun updateTimers() {
        if (mode == MODE_PAUSE && ::timer.isInitialized) {
            val left = ((prefs.pauseUntil - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            timer.text = String.format(java.util.Locale.ENGLISH, "%d:%02d", left / 60, left % 60)
            if (left == 0L) timer.setTextColor(C.GREEN)
        }
        if (overrideAt > 0 && ::overrideBtn.isInitialized) {
            val left = ((overrideAt - System.currentTimeMillis()) / 1000.0).let { kotlin.math.ceil(it).toInt() }
            if (left > 0) {
                overrideBtn.text = "Opening Kite in ${left}s…"
            } else {
                overrideAt = 0
                overrideBtn.text = "I need to close a position"
                openKite(logOverride = true)
            }
        }
    }

    private fun openKite(logOverride: Boolean) {
        if (logOverride) {
            prefs.overrides = prefs.overrides + 1
            Engine.log("Override: opened Kite during ${if (mode == MODE_PAUSE) "pause" else "lock"}", C.AMBER)
        }
        val i = packageManager.getLaunchIntentForPackage(KITE)
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        }
    }

    private fun goHome() {
        startActivity(Intent(this, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_PAUSE = "pause"
        const val MODE_LOCK = "lock"
        const val KITE = "com.zerodha.kite3"

        /** Is Kite blocked in DALAL right now? Returns the mode to show, or null. */
        fun blockMode(prefs: Prefs): String? {
            val today = MarketCalendar.today().toString()
            if (prefs.lockDay == today) return MODE_LOCK
            if (prefs.pauseDay == today && System.currentTimeMillis() < prefs.pauseUntil) return MODE_PAUSE
            return null
        }
    }
}
