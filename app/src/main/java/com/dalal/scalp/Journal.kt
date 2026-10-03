package com.dalal.scalp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Trade journal stored on the phone (files/journal.json). Kite fills the trade; you add 3 taps. */
object Journal {

    data class Entry(
        val id: Long, val ts: Long, val tsym: String, val pnl: Double,
        val setup: String = "", val plan: String = "", val feeling: String = ""
    ) {
        val done: Boolean get() = setup.isNotEmpty() && plan.isNotEmpty() && feeling.isNotEmpty()
    }

    val SETUPS = listOf("Breakout", "Reversal", "Range", "Level bounce", "Other")
    val PLANS = listOf("Yes", "No")
    val FEELINGS = listOf("Calm", "Rushed", "Revenge", "Bored", "FOMO")

    private fun file(ctx: Context) = File(ctx.filesDir, "journal.json")

    @Synchronized
    fun all(ctx: Context): List<Entry> {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        return try {
            val a = JSONArray(f.readText())
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                Entry(o.optLong("id"), o.optLong("ts"), o.optString("tsym"), o.optDouble("pnl", 0.0),
                    o.optString("setup"), o.optString("plan"), o.optString("feeling"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    private fun save(ctx: Context, list: List<Entry>) {
        val a = JSONArray()
        list.takeLast(2000).forEach { e ->
            a.put(JSONObject().put("id", e.id).put("ts", e.ts).put("tsym", e.tsym).put("pnl", e.pnl)
                .put("setup", e.setup).put("plan", e.plan).put("feeling", e.feeling))
        }
        file(ctx).writeText(a.toString())
    }

    @Synchronized
    fun addTrade(ctx: Context, tsym: String, pnl: Double): Entry {
        val now = System.currentTimeMillis()
        val e = Entry(now, now, tsym, pnl)
        save(ctx, all(ctx) + e)
        return e
    }

    @Synchronized
    fun complete(ctx: Context, id: Long, setup: String, plan: String, feeling: String) {
        save(ctx, all(ctx).map { if (it.id == id) it.copy(setup = setup, plan = plan, feeling = feeling) else it })
    }

    fun pending(ctx: Context): List<Entry> = all(ctx).filter { !it.done }.sortedBy { it.ts }

    fun lastDays(ctx: Context, days: Int): List<Entry> {
        val from = System.currentTimeMillis() - days * 24L * 3600 * 1000
        return all(ctx).filter { it.ts >= from }
    }
}
