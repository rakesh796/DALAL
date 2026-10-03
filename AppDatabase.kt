package com.dalal.scalp.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// ============ Database Definition ============

@Database(
    entities = [TradeEntry::class, DailyStats::class, LossLimitEntry::class],
    version = 1,
    exportSchema = false
)
abstract class DALALDatabase : RoomDatabase() {
    abstract fun tradeDao(): TradeDao
    abstract fun statsDao(): StatsDao
    abstract fun lossLimitDao(): LossLimitDao

    companion object {
        @Volatile
        private var INSTANCE: DALALDatabase? = null

        fun getDatabase(context: Context): DALALDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DALALDatabase::class.java,
                    "dalal_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// ============ Data Access Objects ============

@Dao
interface TradeDao {
    @Insert
    suspend fun insertTrade(trade: TradeEntry): Long

    @Update
    suspend fun updateTrade(trade: TradeEntry)

    @Query("SELECT * FROM trades ORDER BY timestamp DESC")
    fun getAllTrades(): Flow<List<TradeEntry>>

    @Query("SELECT * FROM trades WHERE DATE(timestamp / 1000, 'unixepoch') = :date ORDER BY timestamp DESC")
    fun getTradesToday(date: String): Flow<List<TradeEntry>>

    @Query("SELECT * FROM trades WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    fun getTradesAfter(startTime: Long): Flow<List<TradeEntry>>

    @Query("SELECT COUNT(*) FROM trades WHERE DATE(timestamp / 1000, 'unixepoch') = :date")
    fun getTradCountToday(date: String): Flow<Int>

    @Query("SELECT SUM(profitLoss) FROM trades WHERE DATE(timestamp / 1000, 'unixepoch') = :date AND profitLoss IS NOT NULL")
    fun getDailyProfitLoss(date: String): Flow<Double?>

    @Query("SELECT * FROM trades WHERE symbol = :symbol ORDER BY timestamp DESC LIMIT 1")
    fun getLastTradeForSymbol(symbol: String): Flow<TradeEntry?>
}

@Dao
interface StatsDao {
    @Insert
    suspend fun insertStats(stats: DailyStats)

    @Update
    suspend fun updateStats(stats: DailyStats)

    @Query("SELECT * FROM daily_stats WHERE date = :date")
    fun getStatsForDate(date: String): Flow<DailyStats?>

    @Query("SELECT * FROM daily_stats ORDER BY date DESC LIMIT 30")
    fun getLast30DaysStats(): Flow<List<DailyStats>>

    @Query("SELECT AVG(winTrades * 100 / (winTrades + lossTrades)) FROM daily_stats WHERE date >= date('now', '-30 days')")
    fun getLast30DaysWinRate(): Flow<Double?>
}

@Dao
interface LossLimitDao {
    @Insert
    suspend fun insertLossLimit(entry: LossLimitEntry)

    @Update
    suspend fun updateLossLimit(entry: LossLimitEntry)

    @Query("SELECT * FROM loss_limits WHERE date = :date")
    fun getLossLimitForDate(date: String): Flow<LossLimitEntry?>

    @Query("SELECT * FROM loss_limits WHERE DATE(date) = DATE('now') ORDER BY date DESC LIMIT 1")
    fun getTodayLossLimit(): Flow<LossLimitEntry?>

    @Query("SELECT COUNT(*) FROM loss_limits WHERE killSwitchEngaged = 1 AND DATE(date) = DATE('now')")
    fun getTodayKillSwitchStatus(): Flow<Int>
}

// ============ Repository ============

class AppRepository(context: Context) {
    private val database = DALALDatabase.getDatabase(context)
    private val tradeDao = database.tradeDao()
    private val statsDao = database.statsDao()
    private val lossLimitDao = database.lossLimitDao()
    private val packageManager = context.packageManager

    // Trades
    fun getAllTrades() = tradeDao.getAllTrades()
    fun getTradesToday(date: String) = tradeDao.getTradesToday(date)
    suspend fun addTrade(trade: TradeEntry) = tradeDao.insertTrade(trade)
    suspend fun updateTrade(trade: TradeEntry) = tradeDao.updateTrade(trade)

    // Daily Stats
    fun getStatsForDate(date: String) = statsDao.getStatsForDate(date)
    fun getLast30DaysStats() = statsDao.getLast30DaysStats()
    suspend fun updateDailyStats(stats: DailyStats) = statsDao.updateStats(stats)

    // Loss Limits
    fun getTodayLossLimit() = lossLimitDao.getTodayLossLimit()
    fun getTodayKillSwitchStatus() = lossLimitDao.getTodayKillSwitchStatus()
    suspend fun setKillSwitchEngaged(date: String) {
        val entry = LossLimitEntry(
            date = date,
            limitActive = false,
            killSwitchEngaged = true,
            dailyLimitBreakTime = System.currentTimeMillis()
        )
        lossLimitDao.updateLossLimit(entry)
    }

    // Daily loss calculation
    fun dailyLossFlow() = tradeDao.getDailyProfitLoss(
        java.time.LocalDate.now().toString()
    )

    // App List
    fun getAllInstalledApps(): List<InstalledApp> {
        val apps = mutableListOf<InstalledApp>()
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
        intent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)

        val resolveInfos = packageManager.queryIntentActivities(intent, 0)
        for (resolveInfo in resolveInfos) {
            val packageName = resolveInfo.activityInfo.packageName
            if (!packageName.startsWith("android.") && packageName != "com.dalal.scalp") {
                apps.add(
                    InstalledApp(
                        packageName = packageName,
                        appName = resolveInfo.loadLabel(packageManager).toString(),
                        isSystem = false,
                        icon = resolveInfo.loadIcon(packageManager)
                    )
                )
            }
        }

        return apps.sortedBy { it.appName }
    }
}
