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
    private var lastUpdateTime = System.currentTimeMillis()
    private var showLossPauseWarning = false

    fun getView(): FrameLayout {
        val container = FrameLayout(context)
        surfaceView = SurfaceView(context)
        container.addView(surfaceView)

        renderThread.start()

        return container
    }

    fun resume() {
        // Start render loop
    }

    fun pause() {
        // Pause render loop
    }

    fun destroy() {
        // Cleanup
    }

    private fun renderLoop() {
        var lastFrameTime = System.nanoTime()
        var frameCount = 0
        var fps = 60

        while (true) {
            val frameStartTime = System.nanoTime()

            try {
                val canvas = surfaceView.holder.lockCanvas() ?: continue

                // Check if data has changed (selective redraw)
                val timeSinceLastUpdate = System.currentTimeMillis() - lastUpdateTime
                if (timeSinceLastUpdate < 50) { // Only redraw every 50ms = 20 FPS max
                    canvas.drawColor(0xFF080E1E.toInt())
                    renderDashboard(canvas)
                    surfaceView.holder.unlockCanvasAndPost(canvas)
                }

                // Frame rate calculation
                frameCount++
                val currentTime = System.nanoTime()
                if (currentTime - lastFrameTime >= 1_000_000_000) { // 1 second
                    fps = frameCount
                    frameCount = 0
                    lastFrameTime = currentTime
                }

                // 120Hz target: ~8.3ms per frame
                Thread.sleep(8)

            } catch (e: Exception) {
                e.printStackTrace()
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
        lastUpdateTime = System.currentTimeMillis()
    }

    fun updateLossIndicator(loss: Double) {
        dailyLoss = loss
        lastUpdateTime = System.currentTimeMillis()
    }

    fun updateTradesLeft(count: Int) {
        tradesLeft = count
        lastUpdateTime = System.currentTimeMillis()
    }

    fun updateSessionStatus(text: String) {
        currentSession = text
        lastUpdateTime = System.currentTimeMillis()
    }

    fun showLossPauseWarning() {
        showLossPauseWarning = true
        lastUpdateTime = System.currentTimeMillis()
    }

    fun hideLossPauseWarning() {
        showLossPauseWarning = false
        lastUpdateTime = System.currentTimeMillis()
    }
}

/**
 * App Drawer - Swipe-up drawer showing installed apps
 */
class AppDrawer(private val context: Context) {

    private var apps = emptyList<com.dalal.scalp.data.InstalledApp>()
    private var isVisible = false

    fun setApps(appList: List<com.dalal.scalp.data.InstalledApp>) {
        apps = appList.sortedByDescending { it.usageCount }
    }

    fun show(parent: FrameLayout, activity: android.app.Activity): FrameLayout {
        isVisible = true

        val drawerLayout = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFF080E1E.toInt())
        }

        // TODO: Implement smooth slide animation
        parent.addView(drawerLayout)

        return drawerLayout
    }

    fun hide() {
        isVisible = false
        // TODO: Implement slide-out animation
    }
}
