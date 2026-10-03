package com.dalal.scalp

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast

/**
 * Runs your ~/kite/kw.sh in Termux (restart / start the helper).
 * One-time Termux setting: allow-external-apps=true in ~/.termux/termux.properties
 */
object Termux {
    const val PERMISSION = "com.termux.permission.RUN_COMMAND"
    private const val KW = "/data/data/com.termux/files/home/kite/kw.sh"

    fun run(a: Activity, arg: String) {
        if (a.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(arrayOf(PERMISSION), 41)
            Toast.makeText(a, "Allow DALAL to run Termux commands, then tap again", Toast.LENGTH_LONG).show()
            return
        }
        val i = Intent("com.termux.RUN_COMMAND")
            .setClassName("com.termux", "com.termux.app.RunCommandService")
            .putExtra("com.termux.RUN_COMMAND_PATH", KW)
            .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf(arg))
            .putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        try {
            a.startService(i)
            Toast.makeText(a, "Helper: $arg sent to Termux", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(a, "Termux refused. In Termux run the one-time setup from DALAL settings.", Toast.LENGTH_LONG).show()
        }
    }
}
