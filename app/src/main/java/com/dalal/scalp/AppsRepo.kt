package com.dalal.scalp

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Process

data class AppEntry(val pkg: String, val label: String, val icon: Drawable) {
    /** A separate copy per view: one Drawable shared by several views draws wrongly. */
    fun freshIcon(): Drawable = icon.constantState?.newDrawable()?.mutate() ?: icon
}

class AppsRepo(private val ctx: Context) {

    /** All launchable apps, sorted by name. Call off the main thread. */
    fun load(): List<AppEntry> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
            .sortedBy { it.label.lowercase() }
    }

    fun hasUsageAccess(): Boolean {
        val aom = ctx.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = aom.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Most recently used packages (needs usage access). */
    fun recents(limit: Int): List<String> {
        if (!hasUsageAccess()) return emptyList()
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val end = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, end - 2L * 24 * 3600 * 1000, end) ?: return emptyList()
        return stats.filter { it.lastTimeUsed > 0 && it.packageName != ctx.packageName }
            .sortedByDescending { it.lastTimeUsed }
            .map { it.packageName }
            .distinct()
            .take(limit * 3)
    }
}
