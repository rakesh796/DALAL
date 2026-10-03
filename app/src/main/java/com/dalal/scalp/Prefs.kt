package com.dalal.scalp

import android.content.Context

class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("dalal", Context.MODE_PRIVATE)

    /** "web" = your live dashboard page, "image" = picked image, "black" = plain black */
    var wallMode: String
        get() = p.getString("wallMode", "web") ?: "web"
        set(v) = p.edit().putString("wallMode", v).apply()

    var wallUrl: String
        get() = p.getString("wallUrl", DEFAULT_URL) ?: DEFAULT_URL
        set(v) = p.edit().putString("wallUrl", v).apply()

    var wallImage: String?
        get() = p.getString("wallImage", null)
        set(v) = p.edit().putString("wallImage", v).apply()

    fun dock(): MutableList<String> {
        val list = (p.getString("dock", DEFAULT_DOCK) ?: DEFAULT_DOCK).split(",").toMutableList()
        while (list.size < 4) list.add("")
        return list.take(4).toMutableList()
    }

    fun setDock(slot: Int, pkg: String) {
        val list = dock()
        list[slot] = pkg
        p.edit().putString("dock", list.joinToString(",")).apply()
    }

    /** Apps hidden from the drawer during trading mode */
    var hidden: Set<String>
        get() = p.getStringSet("hidden", DEFAULT_HIDDEN)?.toSet() ?: DEFAULT_HIDDEN
        set(v) = p.edit().putStringSet("hidden", HashSet(v)).apply()

    var tradingMode: Boolean
        get() = p.getBoolean("tradingMode", true)
        set(v) = p.edit().putBoolean("tradingMode", v).apply()

    var lossLimit: Int
        get() = p.getInt("lossLimit", 2000)
        set(v) = p.edit().putInt("lossLimit", v).apply()

    var riskPerTrade: Int
        get() = p.getInt("riskPerTrade", 1000)
        set(v) = p.edit().putInt("riskPerTrade", v).apply()

    var maxTrades: Int
        get() = p.getInt("maxTrades", 6)
        set(v) = p.edit().putInt("maxTrades", v).apply()

    var bubble: Boolean
        get() = p.getBoolean("bubble", true)
        set(v) = p.edit().putBoolean("bubble", v).apply()

    /** Date (yyyy-mm-dd) the loss lock fired; Kite stays greyed in DALAL for that day */
    var lockDay: String
        get() = p.getString("lockDay", "") ?: ""
        set(v) = p.edit().putString("lockDay", v).apply()

    var pauseDay: String
        get() = p.getString("pauseDay", "") ?: ""
        set(v) = p.edit().putString("pauseDay", v).apply()

    var pauseUntil: Long
        get() = p.getLong("pauseUntil", 0L)
        set(v) = p.edit().putLong("pauseUntil", v).apply()

    var overrides: Int
        get() = p.getInt("overrides", 0)
        set(v) = p.edit().putInt("overrides", v).apply()

    /** Raising the loss limit waits 24 hours; lowering is instant */
    fun requestLossLimit(newLimit: Int): Boolean {
        val current = effectiveLossLimit()
        return if (newLimit <= current) {
            p.edit().putInt("lossLimit", newLimit).remove("pendingLimit").remove("pendingAt").apply()
            true
        } else {
            p.edit().putInt("pendingLimit", newLimit)
                .putLong("pendingAt", System.currentTimeMillis() + 24L * 3600 * 1000).apply()
            false
        }
    }

    fun effectiveLossLimit(): Int {
        val pending = p.getInt("pendingLimit", 0)
        val at = p.getLong("pendingAt", 0L)
        if (pending > 0 && at > 0 && System.currentTimeMillis() >= at) {
            p.edit().putInt("lossLimit", pending).remove("pendingLimit").remove("pendingAt").apply()
        }
        return p.getInt("lossLimit", 2000)
    }

    fun getCapDay(): String = p.getString("capDay", "") ?: ""
    fun setCapDay(day: String) = p.edit().putString("capDay", day).apply()

    fun pendingLossLimit(): Int = p.getInt("pendingLimit", 0)

    fun checklist(day: String): Set<String> =
        if (p.getString("checkDay", "") == day) p.getStringSet("checks", emptySet())?.toSet() ?: emptySet() else emptySet()

    fun setChecklist(day: String, items: Set<String>) =
        p.edit().putString("checkDay", day).putStringSet("checks", HashSet(items)).apply()

    companion object {
        const val DEFAULT_URL = "http://127.0.0.1:8000/"
        // Your current dock: Kite, Termux, ChatGPT, Claude
        const val DEFAULT_DOCK = "com.zerodha.kite3,com.termux,com.openai.chatgpt,com.anthropic.claude"
        val DEFAULT_HIDDEN = setOf(
            "com.google.android.youtube", "com.instagram.android", "com.twitter.android",
            "com.facebook.katana", "com.snapchat.android", "com.netflix.mediaclient"
        )
    }
}
