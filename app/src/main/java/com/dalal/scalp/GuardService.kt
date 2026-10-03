package com.dalal.scalp

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Background guard (foreground service, keeps running while you're in Kite):
 *  - polls your helper, enforces the loss lock (pause at 75%, day over at 100%)
 *  - "no stop placed" buzz 20s after a fill without an SL order
 *  - journal prompt when a trade closes
 *  - floating bubble: price, candle timer, risk left, trades left, quick switcher
 */
class GuardService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null
    private val main = Handler(Looper.getMainLooper())
    private val prefs by lazy { Prefs(this) }

    // trade tracking
    private val openSince = HashMap<Long, Long>()
    private val pnlAtOpen = HashMap<Long, Double>()
    private val buzzed = HashSet<Long>()
    private val names = HashMap<Long, String>()
    private var firstPoll = true

    // bubble
    private var wm: WindowManager? = null
    private var bubble: LinearLayout? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var bPrice: TextView? = null
    private var bTimer: TextView? = null
    private var bRisk: TextView? = null
    private var bTrades: TextView? = null
    private var bExtra: LinearLayout? = null
    private var bOpenPnl: TextView? = null

    private val secondTick = object : Runnable {
        override fun run() {
            updateBubbleTimer()
            main.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        wm = getSystemService(WindowManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, guardNotification("Starting…"), type)
        } catch (e: Exception) {
            // Android refused a background restart; DALAL starts the guard again when you return home
            stopSelf()
            return START_NOT_STICKY
        }
        if (loop?.isActive != true) {
            loop = scope.launch {
                while (isActive) {
                    val s = Engine.poll(Engine.baseUrl(prefs.wallUrl))
                    main.post { evaluate(s) }
                    val inSession = MarketCalendar.isTradingDay(MarketCalendar.today()) &&
                        MarketCalendar.now().toLocalTime().let { !it.isBefore(LocalTime.of(8, 55)) && it.isBefore(LocalTime.of(15, 50)) }
                    delay(if (inSession) 2000L else 15000L)
                }
            }
            main.removeCallbacks(secondTick)
            main.post(secondTick)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        main.removeCallbacksAndMessages(null)
        removeBubble()
        super.onDestroy()
    }

    // ---------------- rules ----------------

    private fun evaluate(s: Engine.Snap) {
        val limit = prefs.effectiveLossLimit()
        val today = MarketCalendar.today().toString()
        val nm = getSystemService(NotificationManager::class.java)

        val text = when {
            !s.ok -> "Helper offline · loss guard can't see your P&L"
            s.login == "needed" -> "Kite login needed · open DALAL → Today"
            else -> "Loss ₹${s.loss.roundToInt()} / ₹$limit · trades ${s.trades}/${prefs.maxTrades} · charges ₹${s.charges.roundToInt()}"
        }
        nm?.notify(NOTIF_ID, guardNotification(text))

        if (s.ok) {
            // 75% of the limit: one 15-minute pause per day
            if (s.loss >= limit * 0.75 && s.loss < limit && prefs.pauseDay != today) {
                prefs.pauseDay = today
                prefs.pauseUntil = System.currentTimeMillis() + 15 * 60 * 1000L
                Engine.log("15-minute pause at −₹${s.loss.roundToInt()}", C.AMBER)
                vibrate(longArrayOf(0, 400, 150, 400))
                alert(2, "15-minute pause", "Loss ₹${s.loss.roundToInt()} of ₹$limit. Step away, re-read your plan.", LockActivity.MODE_PAUSE)
                openLock(LockActivity.MODE_PAUSE)
            }
            // 100%: day over
            if (s.loss >= limit && prefs.lockDay != today) {
                prefs.lockDay = today
                Engine.log("LOSS LIMIT HIT −₹${s.loss.roundToInt()} · day over", C.RED)
                vibrate(longArrayOf(0, 600, 200, 600, 200, 600))
                alert(3, "Day over: −₹${s.loss.roundToInt()} after charges", "Close positions, turn on the kill switch, journal.", LockActivity.MODE_LOCK)
                openLock(LockActivity.MODE_LOCK)
            }
            // trade cap
            if (s.trades >= prefs.maxTrades && prefs.getCapDay() != today) {
                prefs.setCapDay(today)
                Engine.log("Trade cap reached: ${s.trades}/${prefs.maxTrades}", C.AMBER)
                alert(4, "Trade cap reached", "${s.trades} of ${prefs.maxTrades} trades today. Done for the day?", null)
            }
            trackTrades(s)
        }
        updateBubble(s, limit)
    }

    private fun trackTrades(s: Engine.Snap) {
        val now = System.currentTimeMillis()
        val open = s.positions.filter { it.qty != 0 }.associateBy { it.token }
        s.positions.forEach { names[it.token] = it.tsym }

        // newly opened
        for ((tok, p) in open) {
            if (tok !in openSince) {
                openSince[tok] = if (firstPoll) now - 60_000 else now
                pnlAtOpen[tok] = if (firstPoll) 0.0 else p.pnl
            }
            // no stop order ~20s after the fill
            if (tok !in buzzed && now - (openSince[tok] ?: now) >= 20_000 && !Engine.hasStop(tok, s.orders)) {
                buzzed.add(tok)
                Engine.log("No stop order: ${p.tsym}", C.AMBER)
                vibrate(longArrayOf(0, 120, 80, 120, 80, 300))
                alert(5, "No stop order on your trade", "${p.tsym} · ${abs(p.qty)} qty · no SL found in Kite", null)
            }
        }
        // closed since last poll -> journal prompt
        val closed = openSince.keys.filter { it !in open }
        for (tok in closed) {
            val p = s.positions.firstOrNull { it.token == tok }
            val tradePnl = (p?.pnl ?: 0.0) - (pnlAtOpen[tok] ?: 0.0)
            val e = Journal.addTrade(this, names[tok] ?: "Trade", tradePnl)
            Engine.log("Closed ${e.tsym}: ${rupees(tradePnl)}", if (tradePnl >= 0) C.GREEN else C.RED)
            journalNotification(e)
            openSince.remove(tok); pnlAtOpen.remove(tok); buzzed.remove(tok)
        }
        firstPoll = false
    }

    private fun rupees(v: Double): String = (if (v < 0) "−₹" else "+₹") + abs(v).roundToInt()

    private fun openLock(mode: String) {
        // Allowed from the background because DALAL has "display over other apps"
        try {
            startActivity(Intent(this, LockActivity::class.java)
                .putExtra(LockActivity.EXTRA_MODE, mode)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        } catch (e: Exception) { }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(pattern: LongArray) {
        val v = getSystemService(Vibrator::class.java) ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    // ---------------- notifications ----------------

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CH_GUARD, "Loss guard (ongoing)", NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Trading alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(false) // DALAL vibrates with its own patterns
        })
    }

    private fun homeIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun guardNotification(text: String): Notification =
        NotificationCompat.Builder(this, CH_GUARD)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("DALAL guard")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(homeIntent())
            .build()

    private fun alert(id: Int, title: String, text: String, lockMode: String?) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val target = if (lockMode != null)
            Intent(this, LockActivity::class.java).putExtra(LockActivity.EXTRA_MODE, lockMode)
        else Intent(this, LauncherActivity::class.java)
        val pi = PendingIntent.getActivity(this, id, target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(100 + id, NotificationCompat.Builder(this, CH_ALERT)
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build())
    }

    private fun journalNotification(e: Journal.Entry) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val pi = PendingIntent.getActivity(this, 900, Intent(this, JournalActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(900, NotificationCompat.Builder(this, CH_ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Journal: ${e.tsym} ${rupees(e.pnl)}")
            .setContentText("3 taps while it's fresh: setup, plan followed, feeling")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build())
    }

    // ---------------- floating bubble ----------------

    private fun bubbleWanted(): Boolean {
        if (!prefs.bubble || !Settings.canDrawOverlays(this)) return false
        val n = MarketCalendar.now()
        val t = n.toLocalTime()
        return MarketCalendar.isTradingDay(n.toLocalDate()) && !t.isBefore(MarketCalendar.PRE_OPEN) && t.isBefore(MarketCalendar.CLOSE)
    }

    private fun updateBubble(s: Engine.Snap, limit: Int) {
        if (!bubbleWanted()) { removeBubble(); return }
        if (bubble == null) addBubble()
        val price = s.price
        bPrice?.text = if (!s.ok) "offline" else if (price == null) "—" else String.format(java.util.Locale.ENGLISH, "%.2f", price)
        bPrice?.setTextColor(if (!s.ok || s.stale) C.RED else C.TEXT)
        val left = (limit - s.loss).coerceAtLeast(0.0)
        bRisk?.text = "₹${left.roundToInt()}"
        bRisk?.setTextColor(when {
            s.loss >= limit * 0.75 -> C.RED
            s.loss >= limit * 0.5 -> C.AMBER
            else -> C.GREEN
        })
        bTrades?.text = "${(prefs.maxTrades - s.trades).coerceAtLeast(0)}"
        bOpenPnl?.text = if (s.openCount == 0) "No open position" else "Open P&L ${rupees(s.openPnl)}"
    }

    private fun updateBubbleTimer() {
        val sec = 60 - MarketCalendar.now().second
        bTimer?.text = "0:" + String.format(java.util.Locale.ENGLISH, "%02d", if (sec == 60) 0 else sec)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addBubble() {
        val w = wm ?: return
        val root = vbox().apply {
            background = rounded(0xF0080E1E.toInt(), 16, C.AMBER)
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        bPrice = tv("—", 16f, C.TEXT, true)
        root.addView(bPrice)
        fun row(label: String): TextView {
            val r = hbox()
            r.addView(tv(label, 10.5f, C.MUTED), LinearLayout.LayoutParams(dp(58), WRAP))
            val v = tv("", 11f, C.TEXT, true)
            r.addView(v)
            root.addView(r)
            return v
        }
        bTimer = row("candle")
        bRisk = row("risk left")
        bTrades = row("trades left")

        bExtra = vbox().apply { visibility = View.GONE; setPadding(0, dp(6), 0, 0) }
        bOpenPnl = tv("", 11f, C.AMBER, true)
        bExtra?.addView(bOpenPnl)
        val sw = hbox().apply { setPadding(0, dp(6), 0, 0) }
        val apps = prefs.dock().filter { it.isNotEmpty() } + packageName
        for (pkg in apps) {
            val icon = (try { packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null }) ?: continue
            sw.addView(ImageView(this).apply {
                setImageDrawable(icon)
                setOnClickListener {
                    val i = if (pkg == packageName) Intent(this@GuardService, LauncherActivity::class.java)
                    else packageManager.getLaunchIntentForPackage(pkg)
                    if (i != null) {
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try { startActivity(i) } catch (e: Exception) { }
                    }
                    bExtra?.visibility = View.GONE
                }
            }, LinearLayout.LayoutParams(dp(34), dp(34)).apply { rightMargin = dp(6) })
        }
        bExtra?.addView(sw)
        root.addView(bExtra)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - dp(130)
            y = dp(260)
        }

        // drag to move, tap to expand (open-trade P&L + quick switcher)
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        root.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    if (moved) {
                        lp.x = startX + dx.toInt(); lp.y = startY + dy.toInt()
                        try { w.updateViewLayout(root, lp) } catch (ex: Exception) { }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) bExtra?.let { it.visibility = if (it.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
                    true
                }
                else -> false
            }
        }
        try {
            w.addView(root, lp)
            bubble = root
            bubbleParams = lp
            updateBubbleTimer()
        } catch (e: Exception) {
            bubble = null
        }
    }

    private fun removeBubble() {
        val b = bubble ?: return
        try { wm?.removeView(b) } catch (e: Exception) { }
        bubble = null
        bPrice = null; bTimer = null; bRisk = null; bTrades = null; bExtra = null; bOpenPnl = null
    }

    companion object {
        const val NOTIF_ID = 7
        const val CH_GUARD = "guard"
        const val CH_ALERT = "alerts"

        fun start(ctx: Context) {
            try {
                androidx.core.content.ContextCompat.startForegroundService(ctx, Intent(ctx, GuardService::class.java))
            } catch (e: Exception) { }
        }
    }
}
