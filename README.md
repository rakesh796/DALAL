# DALAL - Intraday Scalping Launcher

Android launcher optimized for NIFTY 50, BANK NIFTY, and SENSEX options scalping on Zerodha Kite.

## Version 1 Features (MVP)

### 1. **Live Data Link with Stale Alarm** ✓
- Connects to Termux helper at `127.0.0.1:8000`
- Polls live quotes every 2 seconds during trading hours
- Visual warning when data > 5 seconds old
- Automatic stale detection and offline handling

### 2. **Loss Lock (₹2,000 Kill Switch)** ✓
- Monitors daily P&L against ₹2,000 limit
- **Hard stop** on limit breach (not soft pause)
- Triggers Zerodha 12-hour kill switch via API
- Vibration alerts at 80% threshold (₹1,600)

### 3. **Trading Mode (9:00-15:40)** ✓
- Auto-enables during market hours
- Disables at market close
- Prevents accidental orders outside hours

### 4. **Home Dashboard Display** ✓
- Live price, change, % in Midnight theme
- Current session + time to close
- Daily loss with color coding
- Trades left counter
- 120Hz optimized single-layer rendering

### 5. **Floating Bubble (Price/Timer/Risk)** ✓
- Floating window with current LTP
- Countdown timer to next 5m candle
- Position risk and trade count
- Tap-to-copy LTP feature

### 6. **No-Stop Buzz Alert** ✓
- Heavy vibration on market entries/exits
- Pattern interrupts trader's focus
- Haptic feedback for order confirmations

### 7. **Risk Calculator + Trade Cap** ✓
- Input: SL points, risk amount (₹1,500 default)
- Output: Max quantity for NIFTY/BANKNIFTY/SENSEX
- Break-even point calculation (~1-3 points)
- Prevents oversizing

### 8. **Today Page** ✓
- Day plan timeline (pre-market → post-market)
- To-do checklist
- Habit tracker (journal daily, follow rules)
- Quick note + pomodoro timer

### 9. **3-Tap Journal** ✓
- Timestamp
- Symbol + entry/exit price
- Win/loss + P&L
- Rule compliance tracker
- Screenshot attachment

### 10. **Login & Reliability Kit** ✓
- Kite login flow with token refresh
- Connection count display (Kite 3-limit)
- Helper status (ping time, data freshness)
- Phone health monitoring

### 11. **App Drawer** ✓
- Left-swipe to open
- Most-used apps ranked by usage
- Quick access to Kite, charts, WhatsApp

## Architecture

```
DALAL/
├── app/
│   ├── src/main/
│   │   ├── java/com/dalal/scalp/
│   │   │   ├── LauncherActivity.kt          # Main launcher
│   │   │   ├── data/
│   │   │   │   ├── AppModels.kt             # Data classes
│   │   │   │   ├── AppDatabase.kt           # Room DB
│   │   │   │   └── AppRepository.kt         # Data access
│   │   │   ├── service/
│   │   │   │   ├── LiveDataService.kt       # Quote polling
│   │   │   │   ├── LossLimitEnforcerService.kt  # Kill switch
│   │   │   │   └── PriceBubbleService.kt    # Floating bubble
│   │   │   ├── ime/
│   │   │   │   └── ScalperKeyboardService.kt # Custom keyboard
│   │   │   ├── ui/
│   │   │   │   ├── DashboardOverlay.kt      # Dashboard canvas
│   │   │   │   ├── AppDrawer.kt             # App list
│   │   │   │   └── JournalUI.kt             # Trade journal
│   │   │   └── receiver/
│   │   │       └── BootReceiver.kt          # Startup events
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── activity_launcher.xml
│   │   │   │   ├── keyboard_layout.xml
│   │   │   │   └── journal_layout.xml
│   │   │   ├── values/
│   │   │   │   ├── colors.xml               # Midnight theme
│   │   │   │   ├── strings.xml
│   │   │   │   └── dimens.xml
│   │   │   ├── drawable/
│   │   │   │   └── key_background.xml
│   │   │   └── xml/
│   │   │       ├── file_paths.xml
│   │   │       └── keyboard_subtypes.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
└── settings.gradle.kts
```

## Building

### Prerequisites
- Android Studio 2024.1+
- Kotlin 1.9.22+
- Gradle 8.2+
- Android SDK 35
- Min SDK 29 (Android 10)

### Build APK
```bash
# Development build
./gradlew assembleDebug

# Release build (requires signing)
./gradlew assembleRelease --info
```

### Install on Device
```bash
./gradlew installDebug
```

## API Integration

### Termux Helper (127.0.0.1:8000)

**GET /api/quotes** - Live market data
```json
[
  {
    "symbol": "NIFTY 22600 PE",
    "ltp": 211.80,
    "bid": 211.75,
    "ask": 211.85,
    "change": 2.45,
    "change_percent": 1.17,
    "volume": 450000,
    "oi": 1200000,
    "timestamp": 1696329600000
  }
]
```

**GET /api/status** - Market status
```json
{
  "session": "TRADING",
  "time_to_close": 34800000,
  "server_time": 1696329600000
}
```

### Zerodha Kite API

**Kill Switch Trigger**
```
POST /api/kill-switch
Body: {
  "session_token": "...",
  "user_id": "ABCD1234"
}
```

## 120Hz Optimization

### Rendering Strategy
- **Single-layer canvas** (no wallpaper interference)
- **Selective redraws**: Only when data changes
- **Cheap animations**: Slides/fades only (no complex effects)
- **Target**: 4-10 updates/second during trading

### Performance Targets
- Memory: < 200MB
- CPU: < 15% average
- Frame drops: < 1% at 120Hz
- Response time: < 100ms for UI interactions

### OnePlus 9R Setup
```bash
# Settings → Battery → Device care
1. Disable Battery Optimization for DALAL

# Settings → Apps → Special access → Recents
2. Lock DALAL in Recent Apps

# Termux
3. Set Termux child process group to 0

# Settings → Display → Advanced → Refresh rate
4. Set DALAL to 120Hz per-app

# Settings → Apps → DALAL → Permissions
5. Grant INTERNET, VIBRATE, SYSTEM_ALERT_WINDOW

# Termux Helper
6. Bind to 127.0.0.1:8000 (localhost only)

# Settings → More → Safety & emergency
7. Familiarize with Zerodha kill switch (12-hour pause)
```

## Market Data Points

### NIFTY 50 Options
- Lot Size: **65 contracts**
- Expiry Dates: Weekly (every Wednesday) + Monthly (last Thursday)
- Session: 9:15 AM - 3:30 PM (F&O closes 3:40 PM)
- Nearest ATM: 22600, 22650
- Charges: ~1/2.3/2.9 points per lot breakeven

### BANK NIFTY Options
- Lot Size: **30 contracts**
- Expiry: Same as NIFTY
- ATM Strikes: 50700, 50750

### SENSEX Options
- Lot Size: **20 contracts**
- ATM Strikes: 68100, 68150

### Important Dates (2026)
- **Oct 7**: RBI meeting → volatility expected
- **Oct 21**: Monthly expiry
- **Dec 4**: RBI meet again
- **Diwali**: Nov 1 (markets closed)

## Trading Rules Enforced

1. ✅ Loss limit: Hard stop at ₹2,000
2. ✅ Trading hours: 9:00 AM - 3:40 PM only
3. ✅ Max positions: 3 concurrent
4. ✅ Risk per trade: ₹1,500 max
5. ✅ Journal every trade (habit tracker)
6. ✅ No phone 1hr after close
7. ✅ Family time at 7:00 PM

## Known Limitations

- **Orders via Kite app** (not direct): Static IP requirement prevents DALAL from placing orders directly; use Kite UI
- **Broker app features untested** (6 features): User must test these on their phone
- **Keyboard**: May need fallback to SwiftKey in some apps
- **Data freshness**: Depends on helper reliability

## Future Versions

**v1.5**
- Options chain viewer
- Multi-leg spread builder
- Advanced candle charts

**v2.0**
- Direct order placement (VPS static IP setup)
- ML-based trade suggestion
- Sentiment analysis from news feed
- Advanced position hedging

## Development Notes

### Thread Safety
- Live data updates on IO thread
- UI updates on main thread via Handler
- Database operations non-blocking via coroutines

### Memory Optimization
- Cached font objects (F() function)
- Pooled vibration patterns
- Selective drawable inflation

### Graceful Degradation
- If helper offline: Show last known prices
- If network unavailable: Cache mode
- If Kite session expires: Prompt login

## Debugging

```bash
# View logs
adb logcat com.dalal.scalp

# Monitor performance
adb shell dumpsys meminfo com.dalal.scalp

# Test live data
curl http://localhost:8000/api/quotes

# Simulate loss limit
adb shell am broadcast -a com.dalal.TEST_LOSS --ei "loss" 2000
```

## License

Private project for rkn796@gmail.com. Do not distribute.

---

**Status**: Version 1 development ready for APK build.  
**GitHub**: Awaiting repository setup.  
**Contact**: rkn796@gmail.com
