package com.dalal.scalp.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * Data models for DALAL Launcher
 */

// ============ UI/System Models ============

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val isSystem: Boolean = false,
    val usageCount: Int = 0,
    val lastOpenTime: Long = 0,
    val icon: android.graphics.drawable.Drawable? = null
)

// ============ Trading Models ============

@Entity(tableName = "trades")
data class TradeEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val symbol: String, // e.g., "NIFTY 22600 PE"
    val direction: String, // "BUY" or "SELL"
    val entryPrice: Double,
    val quantity: Int,
    val exitPrice: Double? = null,
    val exitTime: Long? = null,
    val profitLoss: Double? = null,
    val notes: String? = null,
    val ruleFollowed: Boolean = true, // Did this follow trading rules?
    val imageUrl: String? = null // Screenshot of the trade
)

@Entity(tableName = "daily_stats")
data class DailyStats(
    @PrimaryKey
    val date: String, // Format: YYYY-MM-DD
    val totalTrades: Int = 0,
    val winTrades: Int = 0,
    val lossTrades: Int = 0,
    val totalProfitLoss: Double = 0.0,
    val maxLoss: Double = 0.0,
    val maxGain: Double = 0.0,
    val ruleFollowRate: Double = 100.0 // 0-100%
)

@Entity(tableName = "loss_limits")
data class LossLimitEntry(
    @PrimaryKey
    val date: String, // Format: YYYY-MM-DD
    val dailyLimitBreakTime: Long? = null, // Timestamp when ₹2000 loss hit
    val limitActive: Boolean = true,
    val killSwitchEngaged: Boolean = false,
    val positions: String = "[]" // JSON array of open positions at limit breach
)

// ============ Live Data Models ============

data class LiveQuote(
    val symbol: String,
    val lastTradedPrice: Double,
    val bid: Double,
    val ask: Double,
    val volume: Long,
    val openInterest: Long,
    val change: Double,
    val changePercent: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val isStale: Boolean = false // True if data > 5 seconds old
)

data class MarketStatus(
    val isOpen: Boolean,
    val timeUntilClose: Long? = null, // milliseconds
    val timeUntilOpen: Long? = null,
    val sessionName: String, // "PRE-MARKET", "TRADING", "CLOSED"
    val currentSession: TradingSession = TradingSession.CLOSED
)

enum class TradingSession {
    PRE_MARKET,  // 8:45 - 9:15
    TRADING,     // 9:15 - 15:30
    CLOSED       // Otherwise
}

data class RiskCalculation(
    val symbol: String,
    val stopLossPips: Int,
    val riskAmount: Double = 1500.0, // ₹1500 default
    val maxQuantity: Int,
    val breakEvenPoints: Double,
    val recommendedLotCount: Int
)

// ============ Kite Integration Models ============

data class KiteLoginState(
    val isLoggedIn: Boolean = false,
    val userId: String? = null,
    val sessionToken: String? = null,
    val expiryTime: Long? = null,
    val totalConnections: Int = 0, // Kite has 3-connection limit
    val lastLoginTime: Long? = null
)

data class Position(
    val instrumentToken: String,
    val symbol: String,
    val quantity: Int,
    val averagePrice: Double,
    val lastPrice: Double,
    val profitLoss: Double,
    val unrealizedProfitLoss: Double,
    val multiplier: Int // 1 for stocks, 65 for NIFTY, etc.
)

data class Order(
    val orderId: String,
    val symbol: String,
    val direction: String, // BUY or SELL
    val quantity: Int,
    val price: Double,
    val status: OrderStatus,
    val filledQuantity: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

enum class OrderStatus {
    PENDING,
    COMPLETED,
    FAILED,
    CANCELLED,
    REJECTED
}

// ============ Settings Models ============

data class UserSettings(
    val dailyLossLimit: Double = 2000.0,
    val riskPerTrade: Double = 1500.0,
    val preferredLotSize: Int = 1,
    val enableNotifications: Boolean = true,
    val enableBubble: Boolean = true,
    val trading24h: Boolean = false,
    val maxPositions: Int = 3,
    val enableSound: Boolean = true,
    val enableVibration: Boolean = true,
    val helperUrl: String = "http://127.0.0.1:8000", // Termux helper
    val selectedKeyboard: String = "DALAL_SCALPER"
)

// ============ Health Check Models ============

data class HelperStatus(
    val isOnline: Boolean = false,
    val lastPingTime: Long = 0,
    val responseTimeMs: Int = 0,
    val lastError: String? = null,
    val dataFreshness: Long = 0 // milliseconds since last quote update
)

data class AppHealth(
    val memoryUsagePercent: Double = 0.0,
    val cpuUsagePercent: Double = 0.0,
    val frameRate: Int = 0, // Current FPS
    val batteryPercent: Int = 100,
    val isOverheating: Boolean = false,
    val serviceStatus: Map<String, Boolean> = emptyMap() // service_name -> isRunning
)
