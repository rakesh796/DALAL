# DALAL Android Project Structure

## Core Kotlin Files (Business Logic)

### 1. **LauncherActivity.kt** (Main Entry Point)
- Home launcher screen
- Handles gesture detection (swipe to open drawer)
- Manages background services
- Loss limit monitoring
- ~300 lines

### 2. **AppModels.kt** (Data Classes)
- InstalledApp
- TradeEntry, DailyStats, LossLimitEntry
- LiveQuote, MarketStatus, Position, Order
- UserSettings, HelperStatus, AppHealth
- Comprehensive domain models
- ~200 lines

### 3. **AppDatabase.kt** (Room Database + Repository)
- TradeDao, StatsDao, LossLimitDao
- DALALDatabase definition
- AppRepository (main data access layer)
- ~250 lines

### 4. **LiveDataService.kt** (Market Data Polling)
- Connects to Termux helper @ 127.0.0.1:8000
- Polls every 2 seconds during trading
- JSON parsing for quotes
- Handles offline/stale data
- Foreground service with notifications
- ~350 lines

### 5. **LossLimitEnforcerService.kt** (Kill Switch)
- Monitors daily P&L vs ₹2,000 limit
- Engages Zerodha kill switch on breach
- Vibration alerts (80% warning + critical)
- Database logging
- ~300 lines

### 6. **ScalperKeyboardService.kt** (Custom IME)
- Number mode (lot buttons, price nudges)
- Letter mode (symbol search)
- Side buttons (LTP, CLR, ABC, DONE)
- Hardware keyboard integration
- ~200 lines

### 7. **DashboardOverlay.kt** (UI Components)
- Dashboard canvas renderer (120Hz optimized)
- App drawer (swipe left)
- SurfaceView-based for smooth rendering
- Selective redraws on data change
- ~250 lines

## Configuration Files

### 1. **AndroidManifest.xml**
- App declaration
- Activity registrations (Launcher, Dashboard, Order Form, Journal, Settings)
- Service declarations (LiveDataService, LossLimitEnforcerService, ScalperKeyboardService, PriceBubbleService)
- Broadcast receiver (BootReceiver)
- File provider
- Permissions (INTERNET, VIBRATE, SYSTEM_ALERT_WINDOW, etc.)
- Intent filters for launcher home/default

### 2. **build.gradle.kts**
- App-level build configuration
- Gradle 8.2, Kotlin 1.9.22, Java 17
- Dependencies: AndroidX (Core, Appcompat, Activity, Fragment, Lifecycle)
- Room database, DataStore, WorkManager, OkHttp, Gson
- Coroutines, Material3

### 3. **settings.gradle.kts**
- Project-level configuration
- Plugin management
- Dependency resolution management

## Resource Files

### 1. **strings.xml**
- App strings for all UI elements
- Localized text (can be extended for multiple languages)

### 2. **colors.xml**
- Midnight theme color palette
- BG: #080E1E
- Text: #E6EDF3
- Green: #34D399, Red: #F85C66, Amber: #F4BE3C

### 3. **activity_launcher.xml**
- Main launcher layout
- FrameLayout container
- Dashboard surface view
- App drawer placeholder
- Floating bubble container

### 4. **keyboard_layout.xml**
- Keyboard IME layout
- Mode label TextView
- KeyboardView for dynamic key rendering

## Build & CI/CD

### 1. **build.yml** (GitHub Actions Workflow)
- Automatic APK builds on push to main/develop
- Debug & release APK builds
- Lint checks
- Test execution
- Release artifacts upload
- Triggered on: push, pull request, manual dispatch

## Documentation

### 1. **README.md**
- Feature list (11 v1 features)
- Architecture overview
- Build instructions
- API integration guide
- 120Hz optimization details
- OnePlus 9R setup steps (7 one-time steps)
- Market data reference (lot sizes, sessions, charges)
- Trading rules enforced
- Future versions roadmap
- Development notes & debugging guide

### 2. **PROJECT_STRUCTURE.md** (This File)
- Complete file breakdown
- Purpose of each component
- Line counts and responsibilities

## File Statistics

```
Total Source Files: 7 Kotlin + 4 XML + 1 YAML + 2 Markdown
Total Lines of Code: ~1,900+ (Kotlin)
Dependencies: 20+ (AndroidX, Room, OkHttp, Coroutines, etc.)
Min API: 29 (Android 10)
Target API: 35 (Android 15)
```

## Next Steps to Complete APK Build

1. **GitHub Repository Setup**
   - Create repo at github.com/USER/DALAL
   - Push all files
   - Enable Actions

2. **Keystore for Signing**
   - Generate keystore: `keytool -genkey -v -keystore dalal.jks -keyalg RSA -keysize 2048 -validity 365`
   - Add to GitHub Secrets for CI/CD

3. **Optional: ProGuard/R8**
   - Add proguard-rules.pro
   - Configure in build.gradle.kts for release builds

4. **Missing Implementations** (TODO)
   - Zerodha kill switch API call
   - Floating bubble service
   - Order form activity
   - Journal activity
   - Settings activity
   - Kite login flow
   - Receipt of broadcasts from services
   - Bluetooth/hardware keyboard support

5. **Testing**
   - Unit tests for data models
   - Integration tests for DB
   - UI tests for launcher
   - Performance tests for 120Hz rendering

6. **Phone Setup** (User Side)
   7 one-time steps documented in README
   - Battery optimization OFF
   - Recents lock ON
   - Termux child process = 0
   - Per-app 120Hz settings
   - Permissions granted
   - Helper bound to localhost
   - Kill switch familiarization

## Quick Command Reference

```bash
# Build
./gradlew assembleDebug          # Debug APK
./gradlew assembleRelease        # Release APK
./gradlew installDebug           # Install to device

# Test
./gradlew test                   # Run unit tests
./gradlew lint                   # Lint check

# Clean
./gradlew clean                  # Clean build

# Debug
adb logcat com.dalal.scalp       # View logs
adb shell dumpsys meminfo com.dalal.scalp  # Memory
```

## Version Control

- **Language**: Kotlin (JVM)
- **Target**: Android 10+ (API 29+)
- **Build System**: Gradle 8.2
- **CI/CD**: GitHub Actions

## Dependencies Summary

**AndroidX**
- core, appcompat, activity, fragment, lifecycle, room, datastore, work

**Networking**
- okhttp3, gson

**Async**
- kotlinx-coroutines

**UI**
- material, constraintlayout, recyclerview

---

**Status**: Ready for GitHub repository initialization and automated builds.
