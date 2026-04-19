# Quick Device Performance Test Runner
# Run this script to easily test performance on your connected Pixel

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  Device Performance Test Runner" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

# Check if device is connected
Write-Host "Checking for connected devices..." -ForegroundColor Yellow
$devices = adb devices | Select-String "device$"

if ($devices.Count -eq 0) {
    Write-Host "`n❌ No devices found!" -ForegroundColor Red
    Write-Host "`nPlease:" -ForegroundColor Yellow
    Write-Host "1. Connect your Pixel via USB"
    Write-Host "2. Enable USB debugging on your Pixel:"
    Write-Host "   Settings → About Phone → Tap 'Build Number' 7 times"
    Write-Host "   Settings → System → Developer Options → Enable 'USB Debugging'"
    Write-Host "3. Run this script again`n"
    exit 1
}

Write-Host "✓ Device connected!`n" -ForegroundColor Green

# Show menu
Write-Host "Select test to run:" -ForegroundColor Cyan
Write-Host "1. All Tests (recommended for first run)" -ForegroundColor White
Write-Host "2. Device Baseline (fastest)" -ForegroundColor White
Write-Host "3. Realistic Load (mid-game scenario)" -ForegroundColor White
Write-Host "4. Maximum Load (superstore scenario)" -ForegroundColor White
Write-Host "5. Transaction Processing" -ForegroundColor White
Write-Host "6. Memory Stability" -ForegroundColor White
Write-Host "7. Device Summary Report" -ForegroundColor White
Write-Host "8. View Last Test Report (HTML)" -ForegroundColor White
Write-Host ""

$choice = Read-Host "Enter choice (1-8)"

switch ($choice) {
    "1" {
        Write-Host "`nRunning ALL performance tests on device..." -ForegroundColor Yellow
        Write-Host "This will take ~30 seconds...`n" -ForegroundColor Yellow
        ./gradlew connectedAndroidTest

        Write-Host "`n✓ Tests complete!" -ForegroundColor Green
        Write-Host "Opening HTML report...`n" -ForegroundColor Cyan
        Start-Process "app\build\reports\androidTests\connected\index.html"
    }
    "2" {
        Write-Host "`nRunning Device Baseline test..." -ForegroundColor Yellow
        adb shell am instrument -w -e class com.example.superstoresimulator.DevicePerformanceTest#testDeviceBaseline com.example.superstoresimulator.test/androidx.test.runner.AndroidJUnitRunner
    }
    "3" {
        Write-Host "`nRunning Realistic Load test..." -ForegroundColor Yellow
        adb shell am instrument -w -e class com.example.superstoresimulator.DevicePerformanceTest#testDeviceRealisticLoad com.example.superstoresimulator.test/androidx.test.runner.AndroidJUnitRunner
    }
    "4" {
        Write-Host "`nRunning Maximum Load (Superstore) test..." -ForegroundColor Yellow
        adb shell am instrument -w -e class com.example.superstoresimulator.DevicePerformanceTest#testDeviceMaximumLoad com.example.superstoresimulator.test/androidx.test.runner.AndroidJUnitRunner
    }
    "5" {
        Write-Host "`nRunning Transaction Processing test..." -ForegroundColor Yellow
        adb shell am instrument -w -e class com.example.superstoresimulator.DevicePerformanceTest#testDeviceTransactionProcessing com.example.superstoresimulator.test/androidx.test.runner.AndroidJUnitRunner
    }
    "6" {
        Write-Host "`nRunning Memory Stability test..." -ForegroundColor Yellow
        adb shell am instrument -w -e class com.example.superstoresimulator.DevicePerformanceTest#testDeviceMemoryStability com.example.superstoresimulator.test/androidx.test.runner.AndroidJUnitRunner
    }
    "7" {
        Write-Host "`nRunning Device Summary..." -ForegroundColor Yellow
        adb shell am instrument -w -e class com.example.superstoresimulator.DevicePerformanceTest#testZZ_DevicePerformanceSummary com.example.superstoresimulator.test/androidx.test.runner.AndroidJUnitRunner
    }
    "8" {
        $reportPath = "app\build\reports\androidTests\connected\index.html"
        if (Test-Path $reportPath) {
            Write-Host "`nOpening last test report..." -ForegroundColor Cyan
            Start-Process $reportPath
        } else {
            Write-Host "`n❌ No test report found. Run tests first (option 1)." -ForegroundColor Red
        }
    }
    default {
        Write-Host "`n❌ Invalid choice. Please run the script again and select 1-8." -ForegroundColor Red
    }
}

Write-Host "`nDone!`n" -ForegroundColor Green

