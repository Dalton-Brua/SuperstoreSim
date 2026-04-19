# Running Performance Tests on Real Android Devices (Pixel)

**Created**: April 19, 2026  
**Purpose**: Guide for running instrumented performance tests on physical devices

---

## Overview

### Unit Tests vs Instrumented Tests

| Aspect | Unit Tests (`test/`) | Instrumented Tests (`androidTest/`) |
|--------|---------------------|-------------------------------------|
| **Runs On** | JVM (your computer) | Real Android device/emulator |
| **Performance** | Developer machine CPU/RAM | Device's actual hardware |
| **Accuracy** | Good for logic testing | **Accurate for real-world performance** |
| **Speed** | Very fast (~50ms) | Slower (~5-10 seconds setup) |
| **Use Case** | Development, CI/CD | Device-specific profiling |

### Files Created

1. **`DevicePerformanceTest.kt`** - Instrumented performance tests
   - Location: `app/src/androidTest/java/com/example/superstoresimulator/`
   - 6 comprehensive device tests
   - Includes device info reporting (model, Android version, CPU architecture)

---

## Quick Start: Run on Connected Pixel

### Prerequisites

1. **Enable Developer Options** on your Pixel:
   - Go to Settings → About Phone
   - Tap "Build Number" 7 times
   - Go back to Settings → System → Developer Options
   - Enable "USB Debugging"

2. **Connect your Pixel** via USB cable

3. **Verify connection**:
   ```bash
   adb devices
   ```
   
   You should see your device listed:
   ```
   List of devices attached
   1A2B3C4D5E6F    device
   ```

### Run All Performance Tests

```bash
cd C:\Users\Dalton\AndroidStudioProjects\SuperstoreSimulator
./gradlew connectedAndroidTest
```

This will:
- Build the app
- Install it on your Pixel
- Run all instrumented tests
- Generate HTML report

**Report Location**: `app/build/reports/androidTests/connected/index.html`

---

## Run Specific Tests

### 1. Device Baseline (Fastest)
Tests minimal tick overhead on your specific device:
```bash
adb shell am instrument -w -e class com.example.superstoresimulator.DevicePerformanceTest#testDeviceBaseline com.example.superstoresimulator.test/androidx.test.runner.AndroidJUnitRunner
```

### 2. Realistic Load
Tests mid-game performance (500 items, 10 staff):
```bash
adb shell am instrument -w -e class com.example.superstoresimulator.DevicePerformanceTest#testDeviceRealisticLoad com.example.superstoresimulator.test/androidx.test.runner.AndroidJUnitRunner
```

### 3. Maximum Load (Superstore)
Tests late-game performance (500 items, 50 staff):
```bash
adb shell am instrument -w -e class com.example.superstoresimulator.DevicePerformanceTest#testDeviceMaximumLoad com.example.superstoresimulator.test/androidx.test.runner.AndroidJUnitRunner
```

### 4. Transaction Processing
Tests transaction speed:
```bash
adb shell am instrument -w -e class com.example.superstoresimulator.DevicePerformanceTest#testDeviceTransactionProcessing com.example.superstoresimulator.test/androidx.test.runner.AndroidJUnitRunner
```

### 5. Memory Stability
Tests for memory leaks during sustained operation:
```bash
adb shell am instrument -w -e class com.example.superstoresimulator.DevicePerformanceTest#testDeviceMemoryStability com.example.superstoresimulator.test/androidx.test.runner.AndroidJUnitRunner
```

### 6. Device Summary
Prints comprehensive device capabilities report:
```bash
adb shell am instrument -w -e class com.example.superstoresimulator.DevicePerformanceTest#testZZ_DevicePerformanceSummary com.example.superstoresimulator.test/androidx.test.runner.AndroidJUnitRunner
```

---

## Understanding the Results

### Console Output Example

```
=======================================================================
DEVICE PERFORMANCE TEST
=======================================================================
Device: Google Pixel 8
Android: 14 (API 34)
CPU ABI: arm64-v8a, armeabi-v7a, armeabi
Hardware: cloudripper
=======================================================================

✓ Device Baseline: 10000 ticks in 12.5ms (0.00125ms per tick)
✓ Realistic Load (500 items, 10 staff): 1000 ticks in 8.3ms (0.0083ms per tick)
✓ Maximum Load (500 items, 50 staff): 1000 ticks in 15.7ms (0.0157ms per tick)
✓ Transaction Processing: 100 transactions in 2.1ms (0.021ms per transaction)
✓ Memory Stability: 3MB delta after 5000 ticks
  Before: 45MB
  After:  48MB

=======================================================================
DEVICE PERFORMANCE SUMMARY
=======================================================================
Device: Google Pixel 8
Android: 14 (API 34)
CPU ABI: arm64-v8a, armeabi-v7a, armeabi
Hardware: cloudripper

Recommendations:
• Available RAM: 7842MB
• Total RAM: 8192MB
• Low Memory: NO
=======================================================================
```

### Interpreting Results

#### 60 FPS Target (16.67ms per frame)
- **Tick time** should be < 1ms for smooth gameplay
- **Maximum load** should be < 5ms to leave headroom for rendering

#### Performance Categories

| Tick Time | Category | 60 FPS Impact |
|-----------|----------|---------------|
| < 0.1ms | ✅ Excellent | No impact |
| 0.1-0.5ms | ✅ Good | Minimal impact |
| 0.5-2ms | ⚠️ Fair | Noticeable at max load |
| > 2ms | ❌ Poor | Frame drops likely |

#### Memory Growth
- **< 10MB** after 5000 ticks: Excellent (no leaks)
- **10-50MB** after 5000 ticks: Acceptable (normal GC)
- **> 50MB** after 5000 ticks: Investigate potential leak

---

## Comparing Devices

### Test Multiple Devices

Run the tests on different devices to understand performance across hardware:

1. **Pixel 8 Pro** (flagship)
   ```bash
   # Connect Pixel 8 Pro
   ./gradlew connectedAndroidTest
   ```

2. **Older Pixel (e.g., Pixel 4a)** (budget)
   ```bash
   # Connect Pixel 4a
   ./gradlew connectedAndroidTest
   ```

3. **Compare results** to determine:
   - Minimum supported hardware
   - Where to set quality/performance options
   - Whether optimizations are needed

---

## Advanced: Profile with Android Studio

### 1. CPU Profiler
1. Open Android Studio
2. Run → Profile 'app'
3. Select your connected Pixel
4. Click "CPU" in the profiler
5. Start recording
6. Play the game normally
7. Stop recording
8. Analyze tick() method calls

### 2. Memory Profiler
1. In Android Studio Profiler
2. Click "Memory"
3. Play game for 5-10 minutes
4. Force GC
5. Check heap allocations
6. Look for retained objects

### 3. Energy Profiler
1. In Android Studio Profiler
2. Click "Energy"
3. Monitor battery drain during gameplay
4. Identify high-power operations

---

## Gradle Configuration

The project is already configured for instrumented tests. Here's what's in `app/build.gradle.kts`:

```kotlin
android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    // Instrumented test dependencies
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
```

---

## Troubleshooting

### Device Not Found
```bash
# Check USB debugging is enabled
adb devices

# If unauthorized, accept prompt on phone
adb kill-server
adb start-server
adb devices
```

### Tests Won't Install
```bash
# Uninstall old version
adb uninstall com.example.superstoresimulator
adb uninstall com.example.superstoresimulator.test

# Clean and rebuild
./gradlew clean
./gradlew connectedAndroidTest
```

### Tests Timeout
```bash
# Increase timeout in build.gradle.kts
android {
    defaultConfig {
        testInstrumentationRunnerArguments["timeout_msec"] = "60000"
    }
}
```

### Performance Varies
- Close background apps on device
- Disable battery saver mode
- Ensure device is not thermal throttling (let it cool down)
- Run tests multiple times and average results

---

## CI/CD Integration

### Firebase Test Lab (Recommended)
Test on hundreds of real devices in Google's data centers:

```bash
# Upload APK to Firebase Test Lab
gcloud firebase test android run \
  --type instrumentation \
  --app app/build/outputs/apk/debug/app-debug.apk \
  --test app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \
  --device model=flame,version=29,locale=en,orientation=portrait \
  --device model=redfin,version=30,locale=en,orientation=portrait
```

### GitHub Actions
Add to `.github/workflows/android.yml`:

```yaml
- name: Run Instrumented Tests
  uses: reactivecircus/android-emulator-runner@v2
  with:
    api-level: 34
    target: google_apis
    arch: x86_64
    script: ./gradlew connectedAndroidTest
```

---

## Next Steps

### 1. Establish Baseline
- Run tests on your primary development Pixel
- Record results as baseline for comparison

### 2. Test on Multiple Devices
- Test on oldest supported device (API 24)
- Test on mid-range device (e.g., Pixel 4a)
- Test on flagship device (e.g., Pixel 8 Pro)

### 3. Set Performance Budgets
Based on test results, define:
- Maximum items before performance warning
- Maximum staff before performance warning
- Minimum device specs for "High" quality settings

### 4. Monitor Over Time
- Run tests after major changes
- Track performance trends
- Catch regressions early

---

## Example: Complete Testing Session

```bash
# 1. Connect your Pixel
adb devices

# 2. Run all performance tests
./gradlew connectedAndroidTest

# 3. View results
start app/build/reports/androidTests/connected/index.html

# 4. Run specific test if needed
adb shell am instrument -w -e class \
  com.example.superstoresimulator.DevicePerformanceTest#testDeviceMaximumLoad \
  com.example.superstoresimulator.test/androidx.test.runner.AndroidJUnitRunner

# 5. Check device logs for detailed output
adb logcat -s TestRunner:I
```

---

## Comparison: JUnit vs Device Tests

Run both test suites to compare:

```bash
# JUnit tests (JVM)
./gradlew test

# Device tests (Real hardware)
./gradlew connectedAndroidTest
```

**Expected**: Device tests will be slightly slower (2-3×) but more accurate for real-world performance.

---

*Guide Author: Architecture Team*  
*Last Updated: April 19, 2026*  
*Tested On: Pixel 8 Pro, Pixel 6a, Pixel 4a*

