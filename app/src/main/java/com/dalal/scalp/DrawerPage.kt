package com.dalal.scalp

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged

/** Swipe-up drawer: search, recents, Zerodha apps, all apps. Distraction apps hide in trading mode. */
class DrawerPage(private val a: LauncherActivity) {

    val view: FrameLayout = FrameLayout(a).apply {
        setBackgroundColor(0xF7080E1E.toInt())
        visibility = View.GONE
        isClickable = true
    }
    var isOpen = false
        private set

    private var all: List<AppEntry> = emptyList()
    private val scroll = ScrollView(a).apply { isVerticalScrollBarEnabled = false }
    private val search = EditText(a)
    private val recentsTitle = a.section("RECENTS")
    private val recentsRow = a.hbox()
    private val zerodhaTitle = a.section("ZERODHA")
    private val zerodhaRow = a.hbox()
    private val modeNote = a.tv("", 12f, C.MUTED)
    private val grid = a.vbox()

    init {
        val col = a.vbox().apply { setPadding(a.dp(16), a.dp(16), a.dp(16), a.dp(40)) }
        scroll.addView(col)
        view.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))

        search.apply {
            hint = "Search apps"
            setHintTextColor(C.DIM)
            setTextColor(C.TEXT)
            textSize = 15f
            isSingleLine = true
            background = a.rounded(C.PANEL, 12, C.BORDER)
            setPadding(a.dp(14), a.dp(10), a.dp(14), a.dp(10))
            doAfterTextChanged { rebuildGrid() }
        }
        col.addView(search)
        col.addView(recentsTitle)
        col.addView(recentsRow)
        col.addView(zerodhaTitle)
        col.addView(zerodhaRow)
        col.addView(modeNote.apply {
            background = a.rounded(0x00000000, 12, 0xFF3A4766.toInt())
            setPadding(a.dp(12), a.dp(10), a.dp(12), a.dp(10))
        }, a.lp().apply { topMargin = a.dp(16) })
        col.addView(a.section("ALL APPS · long-press for options"))
        col.addView(grid)
    }

    fun setApps(list: List<AppEntry>) {
        all = list
        if (isOpen) refresh()
    }

    fun atTop(): Boolean = scroll.scrollY == 0

    fun open() {
        if (isOpen) return
        isOpen = true
        search.setText("")
        refresh()
        scroll.scrollTo(0, 0)
        view.visibility = View.VISIBLE
        view.translationY = view.height.toFloat().coerceAtLeast(1f)
        view.animate().translationY(0f).setDuration(220).start()
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        view.animate().translationY(view.height.toFloat()).setDuration(200)
            .withEndAction { if (!isOpen) view.visibility = View.GONE }.start()
    }

    fun refresh() {
        val trading = MarketCalendar.tradingModeActive(a.prefs)
        val hidden = a.prefs.hidden
        val hiddenNow = if (trading) all.count { it.pkg in hidden } else 0

        // Recents
        recentsRow.removeAllViews()
        if (a.repo.hasUsageAccess()) {
            val byPkg = all.associateBy { it.pkg }
            val recent = a.repo.recents(4).mapNotNull { byPkg[it] }
                .filter { !(trading && it.pkg in hidden) }.take(4)
            recent.forEach { recentsRow.addView(cell(it), a.weighted()) }
            repeat(4 - recent.size) { recentsRow.addView(View(a), a.weighted()) }
        } else {
            recentsRow.addView(a.btn("Allow usage access to show recent apps") {
                a.safeStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }, a.lp())
        }

        // Zerodha apps
        zerodhaRow.removeAllViews()
        val zerodha = all.filter {
            it.pkg.startsWith("com.zerodha") || it.label.contains("Kite", true) ||
                it.label.contains("Coin", true) || it.label.contains("Varsity", true) || it.label.contains("Console", true)
        }.take(4)
        zerodhaTitle.visibility = if (zerodha.isEmpty()) View.GONE else View.VISIBLE
        zerodha.forEach { zerodhaRow.addView(cell(it), a.weighted()) }
        repeat(if (zerodha.isEmpty()) 0 else 4 - zerodha.size) { zerodhaRow.addView(View(a), a.weighted()) }

        modeNote.text = if (trading) {
            "TRADING MODE · until 3:40 PM\n$hiddenNow distraction app${if (hiddenNow == 1) "" else "s"} hidden. Long-press any app to change the list."
        } else {
            "Trading mode is off right now. Apps you mark are hidden on trading days, 9:00 AM–3:40 PM. Long-press any app to mark it."
        }
        modeNote.setTextColor(if (trading) C.AMBER else C.MUTED)

        rebuildGrid()
    }

    private fun rebuildGrid() {
        grid.removeAllViews()
        val q = search.text?.toString()?.trim()?.lowercase() ?: ""
        val trading = MarketCalendar.tradingModeActive(a.prefs)
        val hidden = a.prefs.hidden
        val list = all.filter { (!trading || it.pkg !in hidden) && (q.isEmpty() || it.label.lowercase().contains(q)) }
        list.chunked(4).forEach { rowApps ->
            val row = a.hbox().apply { setPadding(0, a.dp(6), 0, a.dp(6)) }
            rowApps.forEach { row.addView(cell(it), a.weighted()) }
            repeat(4 - rowApps.size) { row.addView(View(a), a.weighted()) }
            grid.addView(row)
        }
        if (list.isEmpty()) grid.addView(a.tv(if (all.isEmpty()) "Loading apps…" else "No apps match", 13f, C.MUTED))
    }

    private fun cell(app: AppEntry): View = a.vbox().apply {
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, a.dp(4), 0, a.dp(4))
        addView(ImageView(a).apply { setImageDrawable(app.freshIcon()) }, LinearLayout.LayoutParams(a.dp(50), a.dp(50)))
        addView(a.tv(app.label, 11f, C.MUTED, true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(a.dp(2), a.dp(4), a.dp(2), 0)
        }, LinearLayout.LayoutParams(MATCH, WRAP))
        setOnClickListener {
            a.launchPkg(app.pkg)
            a.closeOverlays()
        }
        setOnLongClickListener { options(app); true }
    }

    private fun options(app: AppEntry) {
        val hidden = a.prefs.hidden.toMutableSet()
        val isHidden = app.pkg in hidden
        val items = arrayOf(
            if (isHidden) "Show during trading mode" else "Hide during trading mode",
            "Put in dock slot 1", "Put in dock slot 2", "Put in dock slot 3", "Put in dock slot 4",
            "App info"
        )
        AlertDialog.Builder(a)
            .setTitle(app.label)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        if (isHidden) hidden.remove(app.pkg) else hidden.add(app.pkg)
                        a.prefs.hidden = hidden
                        refresh()
                    }
                    in 1..4 -> {
                        a.prefs.setDock(which - 1, app.pkg)
                        a.loadApps()
                    }
                    5 -> a.safeStart(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
                    )
                }
            }
            .show()
    }
}
