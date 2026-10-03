package com.dalal.scalp

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

/** Settings: Wallpaper page (live dashboard / image / black), dock, trading mode, limits, system shortcuts. */
class SettingsActivity : AppCompatActivity() {

    private val prefs by lazy { Prefs(this) }

    private lateinit var modeGroup: RadioGroup
    private lateinit var rbWeb: RadioButton
    private lateinit var rbImage: RadioButton
    private lateinit var rbBlack: RadioButton
    private lateinit var urlField: EditText
    private lateinit var preview: FrameLayout
    private var previewWeb: WebView? = null
    private var pickedImage: String? = null

    private lateinit var tradingSwitch: SwitchCompat
    private lateinit var bubbleSwitch: SwitchCompat
    private lateinit var lossField: EditText
    private lateinit var riskField: EditText
    private lateinit var tradesField: EditText

    private val pickImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                // Some pickers don't offer persistable access; the image may not survive a reboot
            }
            pickedImage = uri.toString()
            rbImage.isChecked = true
            showPreview()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = C.BG
        pickedImage = prefs.wallImage

        val scroll = ScrollView(this).apply { setBackgroundColor(C.BG) }
        val col = vbox().apply { setPadding(dp(18), dp(20), dp(18), dp(48)) }
        scroll.addView(col)
        setContentView(scroll)

        col.addView(tv("DALAL settings", 24f, C.TEXT, true))

        // ---------- Wallpaper ----------
        col.addView(section("WALLPAPER"))
        val wall = card()
        modeGroup = RadioGroup(this)
        rbWeb = radio("Your live dashboard (Termux helper page)")
        rbImage = radio("Image from your gallery")
        rbBlack = radio("Plain black (lowest battery)")
        modeGroup.addView(rbWeb)
        modeGroup.addView(rbImage)
        modeGroup.addView(rbBlack)
        wall.addView(modeGroup)

        urlField = EditText(this).apply {
            setText(prefs.wallUrl)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setTextColor(C.TEXT)
            textSize = 14f
            isSingleLine = true
            background = rounded(C.PANEL2, 10)
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        wall.addView(tv("Dashboard address", 12f, C.MUTED), lp().apply { topMargin = dp(8) })
        wall.addView(urlField, lp().apply { topMargin = dp(4) })

        val wallBtns = hbox()
        wallBtns.addView(btn("Pick image") { pickImage.launch(arrayOf("image/*")) }, weighted().apply { rightMargin = dp(8) })
        wallBtns.addView(btn("Preview") { showPreview() }, weighted())
        wall.addView(wallBtns, lp().apply { topMargin = dp(10) })

        preview = FrameLayout(this).apply { background = rounded(Color.BLACK, 12, C.BORDER) }
        wall.addView(preview, LinearLayout.LayoutParams(MATCH, dp(300)).apply { topMargin = dp(10) })
        wall.addView(tv("Your dashboard stays touchable on the home screen. The lock screen can't show it: OxygenOS only allows a still image there.",
            11.5f, C.DIM), lp().apply { topMargin = dp(8) })
        col.addView(wall)

        when (prefs.wallMode) {
            "image" -> rbImage.isChecked = true
            "black" -> rbBlack.isChecked = true
            else -> rbWeb.isChecked = true
        }
        modeGroup.setOnCheckedChangeListener { _, _ -> showPreview() }

        // ---------- Home ----------
        col.addView(section("HOME"))
        col.addView(btn("Set DALAL as default Home app", filled = true) {
            safeStart(Intent(Settings.ACTION_HOME_SETTINGS))
        })
        col.addView(tv("Dock: long-press any dock icon to change it. Drawer: long-press an app to hide it in trading mode or put it in the dock.",
            12f, C.MUTED), lp().apply { topMargin = dp(8) })

        // ---------- Trading ----------
        col.addView(section("TRADING"))
        val trading = card()
        tradingSwitch = SwitchCompat(this).apply {
            text = "Trading mode: hide distraction apps 9:00 AM–3:40 PM"
            setTextColor(C.TEXT)
            textSize = 13.5f
            isChecked = prefs.tradingMode
            thumbTintList = ColorStateList.valueOf(C.AMBER)
        }
        trading.addView(tradingSwitch)
        bubbleSwitch = SwitchCompat(this).apply {
            text = "Floating bubble over Kite (9:00 AM–3:40 PM)"
            setTextColor(C.TEXT)
            textSize = 13.5f
            isChecked = prefs.bubble
            thumbTintList = ColorStateList.valueOf(C.AMBER)
        }
        trading.addView(bubbleSwitch, lp().apply { topMargin = dp(8) })
        lossField = numberRow(trading, "Daily loss limit ₹ (after charges)", prefs.effectiveLossLimit())
        val pending = prefs.pendingLossLimit()
        if (pending > 0) trading.addView(tv("Raise to ₹$pending pending: takes effect 24 hours after you asked.", 11.5f, C.AMBER),
            lp().apply { topMargin = dp(4) })
        trading.addView(tv("Lowering the limit is instant. Raising it waits 24 hours.", 11.5f, C.DIM), lp().apply { topMargin = dp(4) })
        riskField = numberRow(trading, "Risk per trade ₹", prefs.riskPerTrade)
        tradesField = numberRow(trading, "Max trades per day", prefs.maxTrades)
        col.addView(trading)

        // ---------- Phone setup ----------
        col.addView(section("PHONE SETUP"))
        col.addView(btn("Allow display over other apps (bubble + loss lock)", filled = true) {
            safeStart(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        col.addView(btn("Allow usage access (recent apps)") { safeStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
            lp().apply { topMargin = dp(8) })
        col.addView(btn("Battery: set DALAL and Termux to Don't optimise") {
            safeStart(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }, lp().apply { topMargin = dp(8) })
        col.addView(btn("Turn on DALAL Scalper keyboard (optional)") { safeStart(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
            lp().apply { topMargin = dp(8) })
        col.addView(tv("120Hz: Settings → Display → Screen refresh rate → High, then set DALAL, Kite and Termux to 120Hz in the per-app list.",
            12f, C.MUTED), lp().apply { topMargin = dp(8) })

        col.addView(section("TERMUX HELPER"))
        col.addView(tv("So DALAL can restart your helper (kw restart), run this once in Termux:", 12.5f, C.MUTED))
        col.addView(tv("mkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties && termux-reload-settings",
            12.5f, C.AMBER).apply {
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
            background = rounded(C.PANEL, 10, C.BORDER)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }, lp().apply { topMargin = dp(6) })

        col.addView(btn("Save and go home", filled = true) { save() }, lp().apply { topMargin = dp(22) })

        showPreview()
    }

    private fun radio(text: String): RadioButton = RadioButton(this).apply {
        id = View.generateViewId()
        this.text = text
        setTextColor(C.TEXT)
        textSize = 14f
        buttonTintList = ColorStateList.valueOf(C.AMBER)
    }

    private fun numberRow(parent: LinearLayout, label: String, value: Int): EditText {
        val field = EditText(this).apply {
            setText(value.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(C.TEXT)
            textSize = 15f
            background = rounded(C.PANEL2, 10)
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        val row = hbox().apply { setPadding(0, dp(10), 0, 0) }
        row.addView(tv(label, 13f, C.MUTED), weighted())
        row.addView(field, LinearLayout.LayoutParams(dp(100), WRAP))
        parent.addView(row)
        return field
    }

    private fun selectedMode(): String = when {
        rbImage.isChecked -> "image"
        rbBlack.isChecked -> "black"
        else -> "web"
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showPreview() {
        preview.removeAllViews()
        previewWeb?.destroy()
        previewWeb = null
        when (selectedMode()) {
            "web" -> {
                val w = WebView(this)
                w.setBackgroundColor(Color.BLACK)
                w.settings.javaScriptEnabled = true
                w.settings.domStorageEnabled = true
                w.settings.loadWithOverviewMode = true
                w.settings.useWideViewPort = true
                w.webViewClient = WebViewClient()
                w.loadUrl(urlField.text.toString().trim())
                preview.addView(w, FrameLayout.LayoutParams(MATCH, MATCH))
                previewWeb = w
            }
            "image" -> {
                val img = pickedImage
                if (img == null) {
                    preview.addView(tv("Tap \"Pick image\"", 14f, C.MUTED).apply { gravity = android.view.Gravity.CENTER },
                        FrameLayout.LayoutParams(MATCH, MATCH))
                } else {
                    preview.addView(ImageView(this).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        try { setImageURI(Uri.parse(img)) } catch (e: Exception) { }
                    }, FrameLayout.LayoutParams(MATCH, MATCH))
                }
            }
            else -> Unit
        }
    }

    private fun save() {
        val mode = selectedMode()
        if (mode == "image" && pickedImage == null) {
            Toast.makeText(this, "Pick an image first", Toast.LENGTH_SHORT).show()
            return
        }
        var url = urlField.text.toString().trim()
        if (url.isEmpty()) url = Prefs.DEFAULT_URL
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
        prefs.wallUrl = url
        prefs.wallImage = pickedImage
        prefs.wallMode = mode
        prefs.tradingMode = tradingSwitch.isChecked
        prefs.bubble = bubbleSwitch.isChecked
        lossField.text.toString().toIntOrNull()?.let {
            if (it > 0 && it != prefs.effectiveLossLimit()) {
                val instant = prefs.requestLossLimit(it)
                if (!instant) Toast.makeText(this, "Raising the limit takes effect in 24 hours", Toast.LENGTH_LONG).show()
            }
        }
        riskField.text.toString().toIntOrNull()?.let { if (it > 0) prefs.riskPerTrade = it }
        tradesField.text.toString().toIntOrNull()?.let { if (it > 0) prefs.maxTrades = it }
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        previewWeb?.destroy()
        previewWeb = null
        super.onDestroy()
    }
}
