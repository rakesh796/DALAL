package com.dalal.scalp

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * DALAL home. Your live dashboard page fills the screen (instead of Wallpaper Reactor),
 * with a 4-app dock. Swipe right: Today page. Swipe up: app drawer.
 */
class LauncherActivity : AppCompatActivity() {

    val prefs by lazy { Prefs(this) }
    val repo by lazy { AppsRepo(this) }
    var apps: List<AppEntry> = emptyList()
        private set

    private lateinit var root: FrameLayout
    private lateinit var wallImg: ImageView
    private var web: WebView? = null
    private lateinit var banner: TextView
    private lateinit var dock: LinearLayout
    private lateinit var today: TodayPage
    private lateinit var drawer: DrawerPage
    private lateinit var gestures: GestureDetector

    private val handler = Handler(Looper.getMainLooper())
    private var appliedKey = ""
    private var loadFailed = false
    private val dockHeight by lazy { dp(78) }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = loadApps()
    }

    private val retryLoad = Runnable {
        if (loadFailed) web?.loadUrl(prefs.wallUrl)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = C.BG

        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)

        // 0: image wallpaper
        wallImg = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }
        root.addView(wallImg, FrameLayout.LayoutParams(MATCH, MATCH))

        // 1: dock (live page is inserted below it)
        dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xF20B1222.toInt())
        }
        root.addView(dock, FrameLayout.LayoutParams(MATCH, dockHeight, Gravity.BOTTOM))

        // 2: "data stale / helper offline" alarm
        banner = tv("", 13f, 0xFFFFD5D9.toInt(), true).apply {
            background = rounded(0xFF3A0D16.toInt(), 14, C.RED)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            visibility = View.GONE
            setOnClickListener { Termux.run(this@LauncherActivity, "restart") }
        }
        root.addView(banner, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP).apply {
            setMargins(dp(10), dp(10), dp(10), 0)
        })

        today = TodayPage(this)
        root.addView(today.view, FrameLayout.LayoutParams(MATCH, MATCH))
        drawer = DrawerPage(this)
        root.addView(drawer.view, FrameLayout.LayoutParams(MATCH, MATCH))

        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val start = e1 ?: return false
                val dx = e2.x - start.x
                val dy = e2.y - start.y
                val far = dp(90)
                when {
                    today.isOpen -> if (dx < -far && abs(dx) > 1.5f * abs(dy)) { today.close(); return true }
                    drawer.isOpen -> if (dy > dp(120) && abs(dy) > 1.5f * abs(dx) && drawer.atTop()) { closeOverlays(); return true }
                    else -> {
                        if (dx > far && abs(velocityX) > 900 && abs(dx) > 2f * abs(dy)) { today.open(); return true }
                        if (dy < -far && abs(velocityY) > 900 && abs(dy) > 2f * abs(dx) && start.y > root.height * 0.55f) {
                            drawer.open(); return true
                        }
                    }
                }
                return false
            }
        })

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                closeOverlays()
            }
        })

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        loadApps()

        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 5)
        }
    }

    private val engineListener: (Engine.Snap) -> Unit = { s -> onEngine(s) }

    /** Data-stale alarm: helper up but no price tick for 15s during the session */
    private fun onEngine(s: Engine.Snap) {
        if (prefs.wallMode != "web" || loadFailed) return
        if (s.ok && s.stale) {
            banner.text = "DATA STALE · no price tick for ${s.staleSec}s · don't trust the screen\nTap to restart the helper"
            banner.visibility = View.VISIBLE
        } else if (banner.text.startsWith("DATA STALE")) {
            banner.visibility = View.GONE
        }
        if (today.isOpen) today.onEngine(s)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestures.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        applyWallpaper()
        web?.onResume()
        GuardService.start(this)
        Engine.addListener(engineListener)
        onEngine(Engine.snap)
        buildDock()
        if (today.isOpen) today.refresh()
        if (drawer.isOpen) drawer.refresh()
    }

    override fun onPause() {
        super.onPause()
        web?.onPause()
        Engine.removeListener(engineListener)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        closeOverlays() // Home pressed while already home
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(packageReceiver) } catch (e: IllegalArgumentException) { }
        destroyWeb()
        super.onDestroy()
    }

    // ---------- apps & dock ----------

    fun loadApps() {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { repo.load() }
            apps = list
            buildDock()
            drawer.setApps(list)
        }
    }

    fun findApp(pkg: String): AppEntry? = apps.firstOrNull { it.pkg == pkg }

    private fun buildDock() {
        if (!::dock.isInitialized) return
        dock.removeAllViews()
        val dockPkgs = prefs.dock()
        dockPkgs.forEachIndexed { slot, pkg ->
            val app = findApp(pkg)
            val cell = FrameLayout(this)
            val icon = ImageView(this)
            if (app != null) {
                icon.setImageDrawable(app.freshIcon())
            } else {
                icon.setImageDrawable(rounded(C.PANEL2, 14, C.BORDER))
            }
            if (pkg == LockActivity.KITE && LockActivity.blockMode(prefs) != null) icon.alpha = 0.3f
            cell.addView(icon, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER))
            if (app == null) {
                cell.addView(tv("+", 22f, C.MUTED, true).apply { gravity = Gravity.CENTER },
                    FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER))
            }
            cell.setOnClickListener { if (app != null) launchPkg(app.pkg) else pickDockApp(slot) }
            cell.setOnLongClickListener { pickDockApp(slot); true }
            dock.addView(cell, LinearLayout.LayoutParams(0, MATCH, 1f))
        }
    }

    fun pickDockApp(slot: Int) {
        pickApp("Dock slot ${slot + 1}") { app ->
            prefs.setDock(slot, app.pkg)
            buildDock()
        }
    }

    fun pickApp(title: String, onPick: (AppEntry) -> Unit) {
        if (apps.isEmpty()) return
        val labels = apps.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(labels) { _, i -> onPick(apps[i]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun launchPkg(pkg: String) {
        if (pkg == LockActivity.KITE) {
            val block = LockActivity.blockMode(prefs)
            if (block != null) {
                startActivity(Intent(this, LockActivity::class.java).putExtra(LockActivity.EXTRA_MODE, block))
                return
            }
        }
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            Toast.makeText(this, "App not installed: $pkg", Toast.LENGTH_SHORT).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(this, "Can't open $url", Toast.LENGTH_SHORT).show()
        }
    }

    fun closeOverlays() {
        if (::drawer.isInitialized && drawer.isOpen) drawer.close()
        if (::today.isInitialized && today.isOpen) today.close()
        val imm = getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(root.windowToken, 0)
    }

    // ---------- wallpaper ----------

    private fun applyWallpaper() {
        val key = prefs.wallMode + "|" + prefs.wallUrl + "|" + prefs.wallImage
        if (key == appliedKey) return
        appliedKey = key
        banner.visibility = View.GONE
        when (prefs.wallMode) {
            "web" -> {
                wallImg.visibility = View.GONE
                val w = ensureWeb()
                loadFailed = false
                w.loadUrl(prefs.wallUrl)
            }
            "image" -> {
                destroyWeb()
                val uri = prefs.wallImage
                if (uri != null) {
                    try {
                        wallImg.setImageURI(Uri.parse(uri))
                        wallImg.visibility = View.VISIBLE
                    } catch (e: Exception) {
                        wallImg.visibility = View.GONE
                    }
                } else {
                    wallImg.visibility = View.GONE
                }
            }
            else -> {
                destroyWeb()
                wallImg.visibility = View.GONE
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWeb(): WebView {
        web?.let { return it }
        val w = WebView(this)
        w.setBackgroundColor(Color.BLACK)
        w.isVerticalScrollBarEnabled = false
        w.isHorizontalScrollBarEnabled = false
        w.overScrollMode = View.OVER_SCROLL_NEVER
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.mediaPlaybackRequiresUserGesture = true
        w.webChromeClient = WebChromeClient()
        w.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                loadFailed = false
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (!loadFailed) {
                    banner.visibility = View.GONE
                    view.visibility = View.VISIBLE
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    view.visibility = View.INVISIBLE
                    showOffline(error.description?.toString() ?: "no response")
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host ?: return false
                if (host == "127.0.0.1" || host == "localhost") return false
                openUrl(request.url.toString()) // e.g. Kite login opens in the browser
                return true
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                // Page crashed: rebuild it instead of taking the launcher down
                destroyWeb()
                appliedKey = ""
                handler.post { applyWallpaper() }
                return true
            }
        }
        root.addView(w, 1, FrameLayout.LayoutParams(MATCH, MATCH).apply { bottomMargin = dockHeight })
        web = w
        return w
    }

    private fun destroyWeb() {
        val w = web ?: return
        web = null
        root.removeView(w)
        w.stopLoading()
        w.destroy()
    }

    private fun showOffline(reason: String) {
        loadFailed = true
        banner.text = "Live dashboard offline · start the Termux helper\n$reason · retrying every 5s · tap to open Termux"
        banner.visibility = View.VISIBLE
        handler.removeCallbacks(retryLoad)
        handler.postDelayed(retryLoad, 5000)
    }
}
