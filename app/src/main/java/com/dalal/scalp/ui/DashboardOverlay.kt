package com.dalal.scalp.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.core.content.res.ResourcesCompat
import com.dalal.scalp.R
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Dashboard Overlay with 120Hz Optimization
 *
 * Single-layer drawing (no wallpaper interference)
 * Selective redraws only on data changes
 * Cheap animations (slides/fades only)
 */
class DashboardOverlay(private val context: Context) {

    private lateinit var surfaceView: SurfaceView
    private val uiHandler = Handler(Looper.getMainLooper())
    private val renderThread = Thread { renderLoop() }

    private val paintText = Paint().apply {
        color = 0xFFE6EDF3.toInt()
        typeface = Typeface.MONOSPACE
        textSize = 48f
    }

    private val paintMuted = Paint().apply {
        color = 0xFF8B96AA.toInt()
        typeface = Typeface.MONOSPACE
        textSize = 32f
    }

    private val paintGreen = Paint().apply {
        color = 0xFF34D399.toInt()
        typeface = Typeface.MONOSPACE
        textSize = 48f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private val paintRed = Paint().apply {
        color = 0xFFF85C66.toInt()
        typeface = Typeface.MONOSPACE
        textSize = 48f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private val paintAmber = Paint().apply {
        color = 0xFFF4BE3C.toInt()
        typeface = Typeface.MONOSPACE
        textSize = 48f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private val paintBackground = Paint().apply {
        color = 0xFF080E1E.toInt()
        style = Paint.Style.FILL
    }

    // Dashboard data
    private var livePrice = 211.80
    private var priceChange = +2.45
    private var currentTime = ""
    private var dailyLoss = 0.0
    private var tradesLeft = 2
    private var currentLTPText = "LTP 211.80"
    private var currentSession = "OPEN · 5h 48m left"
    private var showLossPauseWarning = false

    // Render-thread state
    @Volatile private var running = true
    @Volatile private var paused = false
    @Volatile private var dirty = true
    private var lastDrawMs = 0L

    fun getView(): FrameLayout {
        val container = FrameLayout(context)
        surfaceView = SurfaceView(context)
        container.addView(surfaceView)

        renderThread.isDaemon = true
        renderThread.start()

        return container
    }

    fun resume() {
        paused = false
        dirty = true
    }

    fun pause() {
        paused = true
    }

    fun destroy() {
        running = false
    }

    /**
     * Redraws only when data changed (dirty) or once a second for the clock.
     * Sleeps when the surface isn't ready, so it never spins the CPU.
     */
    private fun renderLoop() {
        while (running) {
            try {
                val holder = surfaceView.holder
                val now = System.currentTimeMillis()
                if (!paused && holder.surface.isValid && (dirty || now - lastDrawMs >= 1000)) {
                    val canvas = holder.lockCanvas()
                    if (canvas != null) {
                        try {
                            canvas.drawColor(0xFF080E1E.toInt())
                            renderDashboard(canvas)
                        } finally {
                            holder.unlockCanvasAndPost(canvas)
                        }
                        dirty = false
                        lastDrawMs = now
                    }
                }
                // ~120Hz poll for changes; drawing itself only happens when needed
                Thread.sleep(8)
            } catch (e: InterruptedException) {
                return
            } catch (e: Exception) {
                e.printStackTrace()
                try { Thread.sleep(100) } catch (ie: InterruptedException) { return }
            }
        }
    }

    private fun renderDashboard(canvas: Canvas) {
        updateCurrentTime()

        // Status bar
        canvas.drawText(currentTime, 60f, 80f, paintText)
        canvas.drawText("5G  78%", 800f, 80f, paintMuted)

        // Live price display (main)
        canvas.drawText(currentLTPText, 60f, 250f, paintAmber)

        // Price change
        val changeSymbol = if (priceChange > 0) "▲" else "▼"
        val changePaint = if (priceChange > 0) paintGreen else paintRed
        canvas.drawText(String.format("%+.2f %s", priceChange, changeSymbol), 450f, 250f, changePaint)

        // Session status
        canvas.drawText(currentSession, 60f, 350f, paintMuted)

        // Daily loss with color coding
        val lossPaint = if (dailyLoss < 1600) paintMuted else paintRed
        canvas.drawText(String.format("Loss: ₹%.0f / ₹2000", dailyLoss), 60f, 500f, lossPaint)

        // Trades left
        canvas.drawText(String.format("Trades left: %d", tradesLeft), 60f, 650f, paintGreen)

        // Warning if kill switch engaged
        if (showLossPauseWarning) {
            val redAlertPaint = Paint().apply {
                color = 0xFFF85C66.toInt()
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textSize = 56f
            }
            canvas.drawText("🔒 KILL SWITCH ACTIVE", 60f, 1200f, redAlertPaint)
        }

        // Bottom dock hint
        canvas.drawText("← Swipe left for apps", 60f, 2350f, paintMuted)
    }

    private fun updateCurrentTime() {
        val now = LocalTime.now()
        currentTime = now.format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    // Called from services to update dashboard data
    fun updateLivePrice(price: Double, change: Double) {
        livePrice = price
        priceChange = change
        currentLTPText = "LTP ${String.format("%.2f", price)}"
        dirty = true
    }

    fun updateLossIndicator(loss: Double) {
        dailyLoss = loss
        dirty = true
    }

    fun updateTradesLeft(count: Int) {
        tradesLeft = count
        dirty = true
    }

    fun updateSessionStatus(text: String) {
        currentSession = text
        dirty = true
    }

    fun showLossPauseWarning() {
        showLossPauseWarning = true
        dirty = true
    }

    fun hideLossPauseWarning() {
        showLossPauseWarning = false
        dirty = true
    }
}

/**
 * App Drawer - full-screen scrollable list of installed apps. Tap to launch.
 */
class AppDrawer(private val context: Context) {

    private var apps = emptyList<com.dalal.scalp.data.InstalledApp>()
    private var drawerView: android.view.View? = null

    fun setApps(appList: List<com.dalal.scalp.data.InstalledApp>) {
        apps = appList.sortedBy { it.appName.lowercase() }
    }

    fun show(parent: FrameLayout, activity: android.app.Activity): android.view.View {
        drawerView?.let { parent.removeView(it) }
        val density = context.resources.displayMetrics.density

        val list = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((24 * density).toInt(), (48 * density).toInt(), (24 * density).toInt(), (24 * density).toInt())
        }

        if (apps.isEmpty()) {
            list.addView(android.widget.TextView(context).apply {
                text = "Loading apps…"
                setTextColor(0xFF8B96AA.toInt())
                textSize = 16f
            })
        }

        for (app in apps) {
            val row = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
                isClickable = true
                setOnClickListener {
                    val launch = context.packageManager.getLaunchIntentForPackage(app.packageName)
                    if (launch != null) {
                        activity.startActivity(launch)
                    }
                }
            }
            val iconSize = (40 * density).toInt()
            row.addView(android.widget.ImageView(context).apply {
                setImageDrawable(app.icon)
                layoutParams = android.widget.LinearLayout.LayoutParams(iconSize, iconSize)
            })
            row.addView(android.widget.TextView(context).apply {
                text = app.appName
                setTextColor(0xFFE6EDF3.toInt())
                textSize = 17f
                setPadding((16 * density).toInt(), 0, 0, 0)
            })
            list.addView(row)
        }

        val scroll = android.widget.ScrollView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFF080E1E.toInt())
            addView(list)
        }

        parent.addView(scroll)
        drawerView = scroll
        return scroll
    }

    fun hide(parent: FrameLayout) {
        drawerView?.let { parent.removeView(it) }
        drawerView = null
    }
}
