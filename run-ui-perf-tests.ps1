# Automated UI Performance Test Runner
# Runs comprehensive UI and rendering performance tests on connected device
#
# Frame timing is collected WITHIN each test using Android's FrameMetrics API.
# Tests use FrameMetricsCollector to gather real-time frame data during execution.
#
# Created: April 21, 2026
# Updated: May 2, 2026 - Tests now collect frame timing internally via FrameMetricsCollector

Write-Host ""
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "      AUTOMATED UI PERFORMANCE TEST SUITE                      " -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Frame timing is collected within each test using Android's" -ForegroundColor Cyan
Write-Host "FrameMetrics API. Results appear in test output automatically." -ForegroundColor Cyan
Write-Host ""

# Find ADB executable
Write-Host "Locating Android Debug Bridge (adb)..." -ForegroundColor Yellow

$adb = $null

# Try to find adb in PATH first
try {
    $adb = Get-Command adb -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
    if ($adb) {
        Write-Host "Found adb in PATH: $adb" -ForegroundColor Green
    }
} catch {
    # adb not in PATH
}

# If not in PATH, try common Android SDK locations
if (-not $adb) {
    Write-Host "adb not found in PATH. Searching common locations..." -ForegroundColor Yellow

    $possiblePaths = @(
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "$env:USERPROFILE\AppData\Local\Android\Sdk\platform-tools\adb.exe",
        "C:\Android\Sdk\platform-tools\adb.exe",
        "C:\Program Files\Android\Sdk\platform-tools\adb.exe",
        "C:\Program Files (x86)\Android\Sdk\platform-tools\adb.exe"
    )

    foreach ($path in $possiblePaths) {
        if (Test-Path $path) {
            $adb = $path
            Write-Host "Found adb at: $adb" -ForegroundColor Green
            break
        }
    }
}

# If still not found, show error and instructions
if (-not $adb) {
    Write-Host ""
    Write-Host "ERROR: Android Debug Bridge (adb) not found!" -ForegroundColor Red
    Write-Host ""
    Write-Host "ADB is required to run device performance tests." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "To fix this, you have two options:" -ForegroundColor Yellow
    Write-Host ""

    Write-Host "OPTION 1: Add adb to your PATH" -ForegroundColor Cyan
    Write-Host "  1. Locate your Android SDK platform-tools folder" -ForegroundColor White
    Write-Host "     Typical location: C:\Users\$env:USERNAME\AppData\Local\Android\Sdk\platform-tools" -ForegroundColor Gray
    Write-Host "  2. Copy the full path" -ForegroundColor White
    Write-Host "  3. Add it to your System PATH:" -ForegroundColor White
    Write-Host "     - Open 'Edit system environment variables'" -ForegroundColor Gray
    Write-Host "     - Click 'Environment Variables'" -ForegroundColor Gray
    Write-Host "     - Under 'User variables', select 'Path' and click 'Edit'" -ForegroundColor Gray
    Write-Host "     - Click 'New' and paste the platform-tools path" -ForegroundColor Gray
    Write-Host "     - Click OK, then restart PowerShell" -ForegroundColor Gray
    Write-Host ""

    Write-Host "OPTION 2: Install Android SDK Platform-Tools" -ForegroundColor Cyan
    Write-Host "  1. Download from: https://developer.android.com/tools/releases/platform-tools" -ForegroundColor White
    Write-Host "  2. Extract to a folder (e.g., C:\Android\platform-tools)" -ForegroundColor White
    Write-Host "  3. Add the folder to your PATH (see Option 1 step 3)" -ForegroundColor White
    Write-Host ""

    Write-Host "After fixing, run this script again." -ForegroundColor Yellow
    Write-Host ""
    exit 1
}

# Create an alias for easier use
Set-Alias -Name adb -Value $adb -Scope Script

# Check for connected device
Write-Host ""
Write-Host "Checking for connected devices..." -ForegroundColor Yellow
$devices = & $adb devices | Select-String "device`$"

if ($devices.Count -eq 0) {
    Write-Host ""
    Write-Host "No devices found!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please:" -ForegroundColor Yellow
    Write-Host "1. Connect your Android device via USB"
    Write-Host "2. Enable USB debugging"
    Write-Host "3. Run this script again"
    Write-Host ""
    exit 1
}

Write-Host "Device connected!" -ForegroundColor Green
Write-Host ""

# Get device info
$deviceInfo = & $adb shell getprop ro.product.model
$androidVersion = & $adb shell getprop ro.build.version.release
Write-Host "Device: $deviceInfo" -ForegroundColor White
Write-Host "Android: $androidVersion" -ForegroundColor White
Write-Host ""

# Test Selection Menu
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "                    TEST SUITE MENU                            " -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  COMPREHENSIVE TESTS (Recommended)" -ForegroundColor Yellow
Write-Host "  ----------------------------------" -ForegroundColor DarkGray
Write-Host "  1. Full UI Performance Suite (all tests + frame timing)" -ForegroundColor White
Write-Host "  2. Quick Performance Check (summary only)" -ForegroundColor White
Write-Host ""
Write-Host "  INDIVIDUAL UI TESTS" -ForegroundColor Yellow
Write-Host "  ----------------------------------" -ForegroundColor DarkGray
Write-Host "  3. App Startup Performance (startup time only)" -ForegroundColor White
Write-Host "  4. Screen Navigation Performance" -ForegroundColor White
Write-Host "  5. Inventory Scrolling Performance" -ForegroundColor White
Write-Host "  6. Pager Swipe Performance" -ForegroundColor White
Write-Host "  7. Sustained Gameplay Performance - 30s" -ForegroundColor White
Write-Host ""
Write-Host "  ENGINE PERFORMANCE TESTS" -ForegroundColor Yellow
Write-Host "  ----------------------------------" -ForegroundColor DarkGray
Write-Host "  8. Device Engine Tests (baseline/realistic/max load)" -ForegroundColor White
Write-Host ""
Write-Host "  UTILITIES" -ForegroundColor Yellow
Write-Host "  ----------------------------------" -ForegroundColor DarkGray
Write-Host "  9. View Last Test Report (HTML)" -ForegroundColor White
Write-Host "  10. Clean Build and Install Fresh APK" -ForegroundColor White
Write-Host ""
Write-Host "  💡 TIP: All UI tests (1-7) collect frame timing data automatically" -ForegroundColor Cyan
Write-Host "          using the built-in FrameTimingAnalyzer. Check test output!" -ForegroundColor Cyan
Write-Host ""

$choice = Read-Host "Enter choice (1-10)"

# Package name
$packageName = "com.example.superstoresimulator"

# Execute selected test
switch ($choice) {
    "1" {
        Write-Host ""
        Write-Host "================================================================" -ForegroundColor Green
        Write-Host "          RUNNING FULL UI PERFORMANCE SUITE                    " -ForegroundColor Green
        Write-Host "================================================================" -ForegroundColor Green
        Write-Host ""

        Write-Host "This will run:" -ForegroundColor Yellow
        Write-Host "  - App cold startup test" -ForegroundColor White
        Write-Host "  - Screen navigation test" -ForegroundColor White
        Write-Host "  - Inventory scrolling test" -ForegroundColor White
        Write-Host "  - Pager swipe test" -ForegroundColor White
        Write-Host "  - Sustained gameplay test (30s)" -ForegroundColor White
        Write-Host "  - Full UI flow summary" -ForegroundColor White
        Write-Host ""
        Write-Host "Estimated time: ~2 minutes" -ForegroundColor Yellow
        Write-Host ""

        ./gradlew connectedAndroidTest

        Write-Host ""
        Write-Host "Full UI performance suite completed!" -ForegroundColor Green
        Write-Host ""
        Write-Host "NOTE: Frame timing analysis is included in the test output above" -ForegroundColor Cyan
        Write-Host "      and in the HTML report (opening below)." -ForegroundColor Cyan
        Write-Host ""
        Write-Host "Each test collects frame timing data during execution using" -ForegroundColor Cyan
        Write-Host "the FrameTimingAnalyzer built into the test code." -ForegroundColor Cyan
        Write-Host ""

        # Check multiple possible report paths
        $possibleReportPaths = @(
            "app\build\reports\androidTests\connected\debug\index.html",
            "app\build\reports\androidTests\connected\index.html",
            "app/build/reports/androidTests/connected/debug/index.html",
            "app/build/reports/androidTests/connected/index.html",
            ".\app\build\reports\androidTests\connected\debug\index.html",
            ".\app\build\reports\androidTests\connected\index.html"
        )

        $reportPath = $null
        foreach ($path in $possibleReportPaths) {
            if (Test-Path $path) {
                $reportPath = $path
                break
            }
        }

        if ($reportPath) {
            Write-Host ""
            Write-Host "Opening HTML report..." -ForegroundColor Cyan
            Write-Host ""
            Start-Process $reportPath
        } else {
            Write-Host ""
            Write-Host "HTML report not found at expected locations:" -ForegroundColor Yellow
            Write-Host "  - app\build\reports\androidTests\connected\debug\index.html" -ForegroundColor Gray
            Write-Host "  - app\build\reports\androidTests\connected\index.html" -ForegroundColor Gray
            Write-Host "Searching for report..." -ForegroundColor Yellow

            # Search for the report
            $foundReports = Get-ChildItem -Path "app\build\reports" -Recurse -Filter "index.html" -ErrorAction SilentlyContinue
            if ($foundReports) {
                $htmlReport = $foundReports | Where-Object { $_.FullName -match "androidTests" } | Select-Object -First 1
                if ($htmlReport) {
                    Write-Host "  Found at: $($htmlReport.FullName)" -ForegroundColor Green
                    Start-Process $htmlReport.FullName
                } else {
                    Write-Host "  Could not find androidTests report" -ForegroundColor Yellow
                }
            } else {
                Write-Host "  No reports found in app\build\reports" -ForegroundColor Yellow
            }
        }
    }

    "2" {
        Write-Host ""
        Write-Host "================================================================" -ForegroundColor Green
        Write-Host "             QUICK PERFORMANCE CHECK                           " -ForegroundColor Green
        Write-Host "================================================================" -ForegroundColor Green
        Write-Host ""

        Write-Host "Running comprehensive UI summary test..." -ForegroundColor Yellow
        Write-Host "Frame timing data is collected automatically by the test." -ForegroundColor Cyan
        Write-Host ""

        # Run the test (test includes performance metrics and frame timing internally)
        ./gradlew connectedAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.example.superstoresimulator.UIPerformanceTest#testZZ_FullUIPerformanceSummary"

        Write-Host ""
        Write-Host "Quick performance check completed!" -ForegroundColor Green
        Write-Host ""
        Write-Host "NOTE: Frame timing analysis is included in the test output above." -ForegroundColor Cyan
        Write-Host ""
    }

    "3" {
        Write-Host ""
        Write-Host "================================================================" -ForegroundColor Green
        Write-Host "            APP STARTUP PERFORMANCE TEST                       " -ForegroundColor Green
        Write-Host "================================================================" -ForegroundColor Green
        Write-Host ""

        Write-Host "Measuring cold startup time..." -ForegroundColor Yellow
        Write-Host ""

        # Force stop app first for true cold start
        & $adb shell am force-stop $packageName
        Start-Sleep -Seconds 1

        # Run the test (test measures startup time internally)
        ./gradlew connectedAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.example.superstoresimulator.UIPerformanceTest#testAppColdStartup"

        Write-Host ""
        Write-Host "Startup performance test completed!" -ForegroundColor Green
        Write-Host ""
        Write-Host "NOTE: The test output above shows the cold startup time." -ForegroundColor Cyan
        Write-Host "Frame timing analysis is not applicable to startup measurement." -ForegroundColor Cyan
        Write-Host ""
    }

    "4" {
        Write-Host ""
        Write-Host "================================================================" -ForegroundColor Green
        Write-Host "          SCREEN NAVIGATION PERFORMANCE TEST                   " -ForegroundColor Green
        Write-Host "================================================================" -ForegroundColor Green
        Write-Host ""

        Write-Host "Testing navigation through all main screens..." -ForegroundColor Yellow
        Write-Host ""

        # Run the test (test collects frame timing internally)
        ./gradlew connectedAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.example.superstoresimulator.UIPerformanceTest#testScreenNavigationPerformance"

        Write-Host ""
        Write-Host "Screen navigation test completed!" -ForegroundColor Green
        Write-Host ""
        Write-Host "NOTE: Frame timing data was collected by the test itself." -ForegroundColor Cyan
        Write-Host "      See the test output above for frame analysis results." -ForegroundColor Cyan
        Write-Host ""
    }

    "5" {
        Write-Host ""
        Write-Host "================================================================" -ForegroundColor Green
        Write-Host "         INVENTORY SCROLLING PERFORMANCE TEST                  " -ForegroundColor Green
        Write-Host "================================================================" -ForegroundColor Green
        Write-Host ""

        Write-Host "Testing scrolling performance in inventory list..." -ForegroundColor Yellow
        Write-Host ""

        # Run the test (test collects frame timing internally)
        ./gradlew connectedAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.example.superstoresimulator.UIPerformanceTest#testInventoryScrollingPerformance"

        Write-Host ""
        Write-Host "Inventory scrolling test completed!" -ForegroundColor Green
        Write-Host ""
        Write-Host "NOTE: Frame timing data was collected by the test itself." -ForegroundColor Cyan
        Write-Host "      See the test output above for frame analysis results." -ForegroundColor Cyan
        Write-Host ""
    }

    "6" {
        Write-Host ""
        Write-Host "================================================================" -ForegroundColor Green
        Write-Host "            PAGER SWIPE PERFORMANCE TEST                       " -ForegroundColor Green
        Write-Host "================================================================" -ForegroundColor Green
        Write-Host ""

        Write-Host "Testing swipe gesture performance..." -ForegroundColor Yellow
        Write-Host ""

        # Run the test (test collects frame timing internally)
        ./gradlew connectedAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.example.superstoresimulator.UIPerformanceTest#testPagerSwipePerformance"

        Write-Host ""
        Write-Host "Pager swipe test completed!" -ForegroundColor Green
        Write-Host ""
        Write-Host "NOTE: Frame timing data was collected by the test itself." -ForegroundColor Cyan
        Write-Host "      See the test output above for frame analysis results." -ForegroundColor Cyan
        Write-Host ""
    }

    "7" {
        Write-Host ""
        Write-Host "================================================================" -ForegroundColor Green
        Write-Host "        SUSTAINED GAMEPLAY PERFORMANCE TEST (30s)              " -ForegroundColor Green
        Write-Host "================================================================" -ForegroundColor Green
        Write-Host ""

        Write-Host "Running 30-second sustained gameplay test..." -ForegroundColor Yellow
        Write-Host "Please wait..." -ForegroundColor Yellow
        Write-Host ""

        # Run the test (test collects frame timing internally)
        ./gradlew connectedAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.example.superstoresimulator.UIPerformanceTest#testSustainedGameplayPerformance"

        Write-Host ""
        Write-Host "Sustained gameplay test completed!" -ForegroundColor Green
        Write-Host ""
        Write-Host "NOTE: Frame timing data was collected by the test itself." -ForegroundColor Cyan
        Write-Host "      See the test output above for comprehensive frame analysis." -ForegroundColor Cyan
        Write-Host ""
    }

    "8" {
        Write-Host ""
        Write-Host "================================================================" -ForegroundColor Green
        Write-Host "          DEVICE ENGINE PERFORMANCE TESTS                      " -ForegroundColor Green
        Write-Host "================================================================" -ForegroundColor Green
        Write-Host ""

        Write-Host "Running engine performance tests..." -ForegroundColor Yellow
        Write-Host ""

        # Run all connected Android tests (includes both UI and engine tests)
        ./gradlew connectedAndroidTest

        Write-Host ""
        Write-Host "Device engine tests completed!" -ForegroundColor Green

        # Check multiple possible report paths
        $possibleReportPaths = @(
            "app\build\reports\androidTests\connected\debug\index.html",
            "app\build\reports\androidTests\connected\index.html",
            "app/build/reports/androidTests/connected/debug/index.html",
            "app/build/reports/androidTests/connected/index.html",
            ".\app\build\reports\androidTests\connected\debug\index.html",
            ".\app\build\reports\androidTests\connected\index.html"
        )

        $reportPath = $null
        foreach ($path in $possibleReportPaths) {
            if (Test-Path $path) {
                $reportPath = $path
                break
            }
        }

        if ($reportPath) {
            Write-Host "Opening HTML report..." -ForegroundColor Cyan
            Write-Host ""
            Start-Process $reportPath
        } else {
            Write-Host ""
            Write-Host "HTML report not found at expected locations:" -ForegroundColor Yellow
            Write-Host "  - app\build\reports\androidTests\connected\debug\index.html" -ForegroundColor Gray
            Write-Host "  - app\build\reports\androidTests\connected\index.html" -ForegroundColor Gray
            Write-Host "Searching for report..." -ForegroundColor Yellow

            # Search for the report
            $foundReports = Get-ChildItem -Path "app\build\reports" -Recurse -Filter "index.html" -ErrorAction SilentlyContinue
            if ($foundReports) {
                $htmlReport = $foundReports | Where-Object { $_.FullName -match "androidTests" } | Select-Object -First 1
                if ($htmlReport) {
                    Write-Host "  Found at: $($htmlReport.FullName)" -ForegroundColor Green
                    Start-Process $htmlReport.FullName
                } else {
                    Write-Host "  Could not find androidTests report" -ForegroundColor Yellow
                }
            } else {
                Write-Host "  No reports found in app\build\reports" -ForegroundColor Yellow
            }
        }
    }

    "9" {
        # Check multiple possible report paths
        $possibleReportPaths = @(
            "app\build\reports\androidTests\connected\debug\index.html",
            "app\build\reports\androidTests\connected\index.html"
        )

        $reportPath = $null
        foreach ($path in $possibleReportPaths) {
            if (Test-Path $path) {
                $reportPath = $path
                break
            }
        }

        if ($reportPath) {
            Write-Host ""
            Write-Host "Opening last test report..." -ForegroundColor Cyan
            Start-Process $reportPath
        } else {
            Write-Host ""
            Write-Host "No test report found. Run tests first." -ForegroundColor Red
        }
    }

    "10" {
        Write-Host ""
        Write-Host "================================================================" -ForegroundColor Green
        Write-Host "              CLEAN BUILD and FRESH INSTALL                    " -ForegroundColor Green
        Write-Host "================================================================" -ForegroundColor Green
        Write-Host ""

        Write-Host "Cleaning build..." -ForegroundColor Yellow
        ./gradlew clean

        Write-Host ""
        Write-Host "Uninstalling old APK..." -ForegroundColor Yellow
        & $adb uninstall $packageName 2>$null
        & $adb uninstall "$packageName.test" 2>$null

        Write-Host ""
        Write-Host "Building and installing fresh APK..." -ForegroundColor Yellow
        ./gradlew installDebug
        ./gradlew installDebugAndroidTest

        Write-Host ""
        Write-Host "Fresh installation completed!" -ForegroundColor Green
        Write-Host ""
    }

    default {
        Write-Host ""
        Write-Host "Invalid choice. Please run the script again and select 1-10." -ForegroundColor Red
    }
}

# Additional frame timing analysis (for manual inspection)
if ($choice -in @("1", "2", "4", "5", "6", "7")) {
    Write-Host ""
    Write-Host "================================================================" -ForegroundColor Cyan
    Write-Host "ADDITIONAL FRAME TIMING DATA" -ForegroundColor Cyan
    Write-Host "================================================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "You can manually inspect frame timings with:" -ForegroundColor Yellow
    Write-Host "  ""$adb"" shell dumpsys gfxinfo $packageName" -ForegroundColor White
    Write-Host ""
    Write-Host "To reset frame stats:" -ForegroundColor Yellow
    Write-Host "  ""$adb"" shell dumpsys gfxinfo $packageName reset" -ForegroundColor White
    Write-Host ""
}

Write-Host ""
Write-Host "================================================================" -ForegroundColor Green
Write-Host "Done!" -ForegroundColor Green
Write-Host "================================================================" -ForegroundColor Green
Write-Host ""

