package com.dalal.scalp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.dalal.scalp.R
import com.dalal.scalp.data.LiveQuote
import com.dalal.scalp.data.HelperStatus
import com.dalal.scalp.data.MarketStatus
import com.dalal.scalp.data.TradingSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Service for fetching live market data from Termux helper
 *
 * Connects to 127.0.0.1:8000 (Kite wallpaper helper)
 * Polls every 1-2 seconds during trading hours
 * Updates dashboard with live quotes and market status
 */
class LiveDataService : Service() {

    companion object {
        private const val TAG = "LiveDataService"
        private const val NOTIFICATION_ID = 1001
        private const val HELPER_URL = "http://127.0.0.1:8000"
        private const val POLL_INTERVAL_MS = 2000L // 2 seconds
        private const val STALE_THRESHOLD_MS = 5000L // 5 seconds
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    private var lastSuccessfulPing = 0L
    private var helperStatus = HelperStatus()
    private var isPolling = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "LiveDataService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Start polling loop
        if (!isPolling) {
            isPolling = true
            startPollingLoop()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isPolling = false
        serviceScope.cancel()
        Log.d(TAG, "LiveDataService destroyed")
    }

    private fun startPollingLoop() {
        serviceScope.launch {
            while (isPolling) {
                try {
                    val marketStatus = checkMarketStatus()

                    // Only poll during trading hours
                    if (marketStatus.isOpen) {
                        fetchLiveData()
                    } else {
                        // Poll less frequently when market is closed
                        delay(10000) // 10 seconds
                    }

                    delay(POLL_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in polling loop: ${e.message}")
                    delay(5000) // Backoff on error
                }
            }
        }
    }

    private suspend fun fetchLiveData() {
        try {
            val request = Request.Builder()
                .url("$HELPER_URL/api/quotes")
                .build()

            val startTime = System.currentTimeMillis()
            val response = httpClient.newCall(request).execute()
            val responseTime = System.currentTimeMillis() - startTime

            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrEmpty()) {
                    val quotes = parseQuotes(body)
                    lastSuccessfulPing = System.currentTimeMillis()

                    helperStatus = HelperStatus(
                        isOnline = true,
                        lastPingTime = lastSuccessfulPing,
                        responseTimeMs = responseTime.toInt(),
                        dataFreshness = 0,
                        lastError = null
                    )

                    // Broadcast quotes to registered listeners
                    broadcastQuotes(quotes)
                    updateNotification("Live • ${quotes.size} symbols")

                    Log.d(TAG, "Fetched ${quotes.size} quotes in ${responseTime}ms")
                }
            } else {
                handleHelperError("HTTP ${response.code}")
            }
            response.close()
        } catch (e: Exception) {
            handleHelperError(e.message ?: "Unknown error")
        }
    }

    private suspend fun checkMarketStatus(): MarketStatus {
        try {
            val request = Request.Builder()
                .url("$HELPER_URL/api/status")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrEmpty()) {
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val session = parseMarketSession(json)
                    response.close()
                    return MarketStatus(
                        isOpen = session == TradingSession.TRADING,
                        sessionName = session.name,
                        currentSession = session,
                        timeUntilClose = calculateTimeUntilClose()
                    )
                }
            }
            response.close()
        } catch (e: Exception) {
            Log.e(TAG, "Market status check failed: ${e.message}")
        }

        // Fallback to local time-based check
        return getLocalMarketStatus()
    }

    private fun parseQuotes(json: String): List<LiveQuote> {
        return try {
            val array = gson.fromJson(json, com.google.gson.JsonArray::class.java)
            val quotes = mutableListOf<LiveQuote>()

            for (element in array) {
                val obj = element.asJsonObject
                val freshness = System.currentTimeMillis() - obj.get("timestamp").asLong

                quotes.add(
                    LiveQuote(
                        symbol = obj.get("symbol").asString,
                        lastTradedPrice = obj.get("ltp").asDouble,
                        bid = obj.get("bid").asDouble,
                        ask = obj.get("ask").asDouble,
                        volume = obj.get("volume").asLong,
                        openInterest = obj.get("oi").asLong,
                        change = obj.get("change").asDouble,
                        changePercent = obj.get("change_percent").asDouble,
                        timestamp = obj.get("timestamp").asLong,
                        isStale = freshness > STALE_THRESHOLD_MS
                    )
                )
            }
            quotes
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse quotes: ${e.message}")
            emptyList()
        }
    }

    private fun parseMarketSession(json: JsonObject): TradingSession {
        return try {
            val session = json.get("session").asString
            TradingSession.valueOf(session)
        } catch (e: Exception) {
            TradingSession.CLOSED
        }
    }

    private fun getLocalMarketStatus(): MarketStatus {
        val now = LocalTime.now()
        val session = when {
            now.isAfter(LocalTime.of(8, 45)) && now.isBefore(LocalTime.of(9, 15)) -> TradingSession.PRE_MARKET
            now.isAfter(LocalTime.of(9, 15)) && now.isBefore(LocalTime.of(15, 30)) -> TradingSession.TRADING
            else -> TradingSession.CLOSED
        }

        return MarketStatus(
            isOpen = session == TradingSession.TRADING,
            sessionName = session.name,
            currentSession = session,
            timeUntilClose = calculateTimeUntilClose()
        )
    }

    private fun calculateTimeUntilClose(): Long? {
        val now = LocalTime.now()
        val close = LocalTime.of(15, 30)
        return if (now.isBefore(close)) {
            java.time.temporal.ChronoUnit.MINUTES.between(now, close) * 60 * 1000
        } else {
            null
        }
    }

    private fun handleHelperError(error: String) {
        helperStatus = HelperStatus(
            isOnline = false,
            lastPingTime = lastSuccessfulPing,
            lastError = error,
            dataFreshness = System.currentTimeMillis() - lastSuccessfulPing
        )
        Log.e(TAG, "Helper offline: $error")
        updateNotification("⚠ Offline • $error")
    }

    private fun broadcastQuotes(quotes: List<LiveQuote>) {
        val intent = Intent("com.dalal.LIVE_QUOTES")
        intent.putExtra("quotes_count", quotes.size)
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "live_data_channel",
                "Live Market Data",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live quote updates from Kite"
                setSound(null, null)
                enableVibration(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, "live_data_channel")
            .setContentTitle("DALAL Live Data")
            .setContentText("Connecting...")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = NotificationCompat.Builder(this, "live_data_channel")
            .setContentTitle("DALAL")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }
}
