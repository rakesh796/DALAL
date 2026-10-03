package com.dalal.scalp.ime

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.dalal.scalp.R

/**
 * Custom Input Method for DALAL Scalper Keyboard
 *
 * Provides two modes:
 * 1. Number Mode: Quick order entry (lots, prices, SL/TG adjustments)
 * 2. Letter Mode: Symbol search and strike selection
 *
 * Optimized for 120Hz display
 */
class ScalperKeyboardService : InputMethodService() {

    private lateinit var keyboard: KeyboardView
    private lateinit var modeLabel: TextView
    private var currentMode: KeyboardMode = KeyboardMode.NUMBERS

    private val numberRows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "⌫")
    )

    private val numberSideButtons = listOf(
        "LTP",      // Paste last traded price
        "CLR",      // Clear field
        "ABC",      // Switch to letter mode
        "DONE"      // Submit
    )

    private val letterRows = listOf(
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
        listOf("⇧", "Z", "X", "C", "V", "B", "N", "M", "⌫")
    )

    private val letterSideButtons = listOf(
        "123",      // Switch to number mode
        "DALAL",    // Show shortcuts menu
        "SPACE",    // Space bar
        "GO"        // Submit
    )

    enum class KeyboardMode {
        NUMBERS,
        LETTERS
    }

    override fun onCreateInputView(): View {
        val root = LayoutInflater.from(this).inflate(R.layout.keyboard_layout, null) as FrameLayout
        keyboard = root.findViewById(R.id.keyboard_view)
        modeLabel = root.findViewById(R.id.mode_label)

        buildNumbersKeyboard()

        return root
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        // Detect context and switch mode if needed
        if (attribute?.inputType == EditorInfo.TYPE_CLASS_TEXT) {
            switchToLetters()
        } else {
            switchToNumbers()
        }
    }

    private fun buildNumbersKeyboard() {
        keyboard.removeAllViews()
        currentMode = KeyboardMode.NUMBERS
        modeLabel.text = "Number Mode · Placing Orders"

        val container = FrameLayout(this)

        // Main grid of number buttons
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        for ((rowIdx, row) in numberRows.withIndex()) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    140
                )
            }

            for (num in row) {
                val btn = Button(this).apply {
                    text = num
                    layoutParams = LinearLayout.LayoutParams(0, 140, 1f)
                    setOnClickListener { onNumberKeyPressed(num) }
                }
                rowLayout.addView(btn)
            }

            // Add side button
            val sideBtn = Button(this).apply {
                text = numberSideButtons[rowIdx]
                layoutParams = LinearLayout.LayoutParams(200, 140)
                setOnClickListener { onSideButtonPressed(numberSideButtons[rowIdx]) }
            }
            rowLayout.addView(sideBtn)

            grid.addView(rowLayout)
        }

        container.addView(grid)
        keyboard.addView(container)
    }

    private fun buildLettersKeyboard() {
        keyboard.removeAllViews()
        currentMode = KeyboardMode.LETTERS
        modeLabel.text = "Letter Mode · Finding Contracts"

        val container = FrameLayout(this)
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        for ((rowIdx, row) in letterRows.withIndex()) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    140
                )
            }

            for (letter in row) {
                val btn = Button(this).apply {
                    text = letter
                    layoutParams = LinearLayout.LayoutParams(0, 140, 1f)
                    setOnClickListener { onLetterKeyPressed(letter) }
                }
                rowLayout.addView(btn)
            }

            grid.addView(rowLayout)
        }

        container.addView(grid)
        keyboard.addView(container)
    }

    private fun onNumberKeyPressed(key: String) {
        val inputConnection = currentInputConnection ?: return

        when (key) {
            "⌫" -> inputConnection.deleteSurroundingText(1, 0)
            else -> inputConnection.commitText(key, 1)
        }
    }

    private fun onLetterKeyPressed(letter: String) {
        val inputConnection = currentInputConnection ?: return

        when (letter) {
            "⇧" -> {
                // TODO: Toggle caps lock
            }
            "⌫" -> inputConnection.deleteSurroundingText(1, 0)
            else -> inputConnection.commitText(letter, 1)
        }
    }

    private fun onSideButtonPressed(button: String) {
        val inputConnection = currentInputConnection ?: return

        when (button) {
            "LTP" -> {
                // Paste last traded price from live data
                inputConnection.commitText("211.80", 1) // TODO: Get actual LTP
            }
            "CLR" -> {
                // Clear entire field
                inputConnection.beginBatchEdit()
                inputConnection.deleteSurroundingText(100, 100)
                inputConnection.endBatchEdit()
            }
            "ABC" -> switchToLetters()
            "123" -> switchToNumbers()
            "DONE", "GO" -> {
                sendKeyCode(KeyEvent.KEYCODE_ENTER)
            }
            "SPACE" -> inputConnection.commitText(" ", 1)
            "DALAL" -> showShortcutsMenu()
        }
    }

    private fun switchToNumbers() {
        if (currentMode != KeyboardMode.NUMBERS) {
            buildNumbersKeyboard()
        }
    }

    private fun switchToLetters() {
        if (currentMode != KeyboardMode.LETTERS) {
            buildLettersKeyboard()
        }
    }

    private fun showShortcutsMenu() {
        // TODO: Implement shortcuts menu for:
        // - NIFTY, BANKNIFTY, SENSEX
        // - Expiry dates
        // - Common strikes
    }

    private fun sendKeyCode(keyCode: Int) {
        val inputConnection = currentInputConnection ?: return
        inputConnection.sendKeyEvent(
            KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        )
        inputConnection.sendKeyEvent(
            KeyEvent(KeyEvent.ACTION_UP, keyCode)
        )
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        // Handle hardware keyboard input
    }

    override fun onPress(primaryCode: Int) {
        // Haptic feedback on key press
    }

    override fun onRelease(primaryCode: Int) {
        // Haptic release
    }
}
