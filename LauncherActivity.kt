package com.dalal.scalp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.dalal.scalp.data.AppRepository
import com.dalal.scalp.service.LiveDataService
import com.dalal.scalp.service.LossLimitEnforcerService
import com.dalal.scalp.ui.AppDrawer
import com.dalal.scalp.ui.DashboardOverlay
import kotlinx.coroutines.launch

/**
 * DALAL Launcher - Main entry point
 *
 * Displays home screen with:
 * - Dashboard overlay with live data
 * - Dock with most-used apps at bottom
 * - Swipe drawer for full app list
 * - Custom keyboard for trading
 */
class LauncherActivity : AppCompatActivity() {

    private lateinit var container: FrameLayout
    private lateinit var dashboardOverlay: DashboardOverlay
    private lateinit var appDrawer: AppDrawer
    private lateinit var vibrator: Vibrator
    private lateinit var repository: AppRepository

    private var gestureDetector: GestureDetector? = null
    private var isDrawerOpen = false

    // Loss limit state
    private var dailyLoss = 0.0
    private var isLossPaused = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent default launcher behavior interference
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        setContentView(R.layout.activity_launcher)

        container = findViewById(R.id.launcher_container)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        repository = AppRepository(this)

        // Initialize UI components
        dashboardOverlay = DashboardOverlay(this)
        appDrawer = AppDrawer(this)
        container.addView(dashboardOverlay.getView())

        // Set up gesture detection for swipe drawer
        val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (e1.x > 100) { // Avoid edge gesture navigation
                    if (distanceX > 50 && !isDrawerOpen) {
                        // Swipe left - open drawer
                        openAppDrawer()
                        return true
                    } else if (distanceX < -50 && isDrawerOpen) {
                        // Swipe right - close drawer
                        closeAppDrawer()
                        return true
                    }
                }
                return false
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (isDrawerOpen) {
                    closeAppDrawer()
                    return true
                }
                return false
            }
        }

        gestureDetector = GestureDetector(this, gestureListener)

        // Handle back press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isDrawerOpen) {
                    closeAppDrawer()
                } else {
                    // Don't exit launcher - bring to home
                    moveTaskToBack(false)
                }
            }
        })

        // Start background services
        startLiveDataService()
        startLossLimitEnforcer()

        // Load app list
        loadAppList()

        // Start monitoring loss limit
        monitorLossLimit()
    }

    override fun onResume() {
        super.onResume()
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        dashboardOverlay.resume()
    }

    override fun onPause() {
        super.onPause()
        dashboardOverlay.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        dashboardOverlay.destroy()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event != null && gestureDetector?.onTouchEvent(event) == true) {
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_HOME -> true // Prevent home key default behavior
            KeyEvent.KEYCODE_BACK -> {
                if (isDrawerOpen) {
                    closeAppDrawer()
                    true
                } else {
                    moveTaskToBack(false)
                    true
                }
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun openAppDrawer() {
        isDrawerOpen = true
        val drawerView = appDrawer.show(container, this)
        vibrate(10) // Haptic feedback
    }

    private fun closeAppDrawer() {
        isDrawerOpen = false
        appDrawer.hide()
        vibrate(5)
    }

    private fun startLiveDataService() {
        val intent = Intent(this, LiveDataService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun startLossLimitEnforcer() {
        val intent = Intent(this, LossLimitEnforcerService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun loadAppList() {
        lifecycleScope.launch {
            val apps = repository.getAllInstalledApps()
            appDrawer.setApps(apps)
        }
    }

    private fun monitorLossLimit() {
        lifecycleScope.launch {
            repository.dailyLossFlow().collect { loss ->
                dailyLoss = loss
                dashboardOverlay.updateLossIndicator(loss)

                if (loss >= 2000.0) {
                    isLossPaused = true
                    vibrate(listOf(0, 50, 30, 100, 30, 100)) // Alert pattern
                    dashboardOverlay.showLossPauseWarning()
                }
            }
        }
    }

    private fun vibrate(pattern: List<Long>) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern.toLongArray(), -1))
        }
    }

    private fun vibrate(duration: Long) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }
}
