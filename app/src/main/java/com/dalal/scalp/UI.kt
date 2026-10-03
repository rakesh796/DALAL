package com.dalal.scalp

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

const val MATCH = -1
const val WRAP = -2

/** Midnight theme */
object C {
    val BG = 0xFF080E1E.toInt()
    val PANEL = 0xFF111A30.toInt()
    val PANEL2 = 0xFF182A3E.toInt()
    val BORDER = 0xFF1B2744.toInt()
    val TEXT = 0xFFE6EDF3.toInt()
    val MUTED = 0xFF8B96AA.toInt()
    val DIM = 0xFF5A646C.toInt()
    val GREEN = 0xFF34D399.toInt()
    val RED = 0xFFF85C66.toInt()
    val AMBER = 0xFFF4BE3C.toInt()
    val INK = 0xFF1A1203.toInt()
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun Context.tv(text: CharSequence, sizeSp: Float = 14f, color: Int = C.TEXT, bold: Boolean = false): TextView =
    TextView(this).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

fun Context.rounded(color: Int, radiusDp: Int, strokeColor: Int? = null): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeColor != null) setStroke(dp(1), strokeColor)
    }

fun Context.vbox(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

fun Context.hbox(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}

fun Context.btn(text: String, filled: Boolean = false, onClick: () -> Unit): TextView =
    tv(text, 13f, if (filled) C.INK else C.AMBER, true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = if (filled) rounded(C.AMBER, 12) else rounded(0x00000000, 12, C.AMBER)
        setOnClickListener { onClick() }
    }

fun Context.section(title: String): TextView =
    tv(title, 11f, C.DIM, true).apply {
        letterSpacing = 0.08f
        setPadding(0, dp(18), 0, dp(6))
    }

fun Context.card(): LinearLayout = vbox().apply {
    background = rounded(C.PANEL, 14, C.BORDER)
    setPadding(dp(12), dp(10), dp(12), dp(10))
}

fun Context.lp(w: Int = MATCH, h: Int = WRAP): LinearLayout.LayoutParams = LinearLayout.LayoutParams(w, h)

fun Context.weighted(weight: Float = 1f): LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, WRAP, weight)

/** startActivity that can't crash if a settings screen is missing on this phone */
fun Context.safeStart(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: Exception) {
        android.widget.Toast.makeText(this, "Not available on this phone", android.widget.Toast.LENGTH_SHORT).show()
    }
}
