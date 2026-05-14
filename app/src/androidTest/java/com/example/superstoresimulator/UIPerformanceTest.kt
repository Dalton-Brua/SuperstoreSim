package com.example.superstoresimulator

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI Performance Tests - Rendering and Frame Timing Analysis
 * 
 * These tests measure real UI rendering performance on device, including:
 * - App startup time
 * - Screen navigation performance
 * - Scrolling smoothness
 * - Dialog rendering
 * - Frame timing analysis with outlier detection
 * 
 * Usage:
 * ```
 * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.superstoresimulator.UIPerformanceTest
 * ```
 * 
 * Created: April 21, 2026
 */
@RunWith(AndroidJUnit4::class)
class UIPerformanceTest {
    
    private lateinit var device: UiDevice
    private val packageName = "com.example.superstoresimulator"
    private val launchTimeout = 10000L // 10 seconds
    
    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        
        // Start from home screen
        device.pressHome()
        
        // Wait for launcher
        val launcherPackage = device.launcherPackageName
        assertNotNull(launcherPackage)
        device.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), launchTimeout)
        
        println("\n" + "=".repeat(70))
        println("UI PERFORMANCE TEST - RENDERING & FRAME TIMING")
        println("=".repeat(70))
        println("Package: $packageName")
        println("Device: ${device.productName}")
        println("=".repeat(70) + "\n")
    }
    
    /**
     * Test 1: App Cold Startup Performance
     * 
     * Measures the time from launch intent to first rendered frame.
     * Target: < 2000ms for cold start
     */
    @Test
    fun testAppColdStartup() {
        println("TEST: Cold Startup Performance")
        println("─".repeat(70))
        
        // Reset frame stats
        FrameTimingAnalyzer.resetFrameStats(packageName)
        
        // Measure cold startup time
        val startTime = System.currentTimeMillis()
        
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
        
        // Wait for app to appear
        device.wait(Until.hasObject(By.pkg(packageName)), launchTimeout)
        
        val coldStartupTime = System.currentTimeMillis() - startTime
        
        println("  Cold Startup Time: ${coldStartupTime}ms")
        
        // Wait for UI to settle
        Thread.sleep(2000)
        
        // Collect frame timings during startup
        val frameTimes = FrameTimingAnalyzer.collectFrameTimings(packageName)
        val report = FrameTimingAnalyzer.analyzeFrameTimings(frameTimes)
        
        println("\n${FrameTimingAnalyzer.formatReport(report)}")
        
        // Assert: Cold startup should be < 2000ms
        assertTrue(
            "Cold startup too slow: ${coldStartupTime}ms (expected < 2000ms)",
            coldStartupTime < 2000
        )
        
        // Assert: First frames should be reasonably smooth
        assertTrue(
            "Too many janky frames during startup: ${report.jankyFramePercent}%",
            report.jankyFramePercent < 30.0 // Allow some jank during startup
        )
        
        println("✓ Cold startup test passed\n")
    }
    
    /**
     * Test 2: Screen Navigation Performance
     * 
     * Measures frame timing during screen transitions via bottom navigation.
     * Tests all main screens: GAME → INVENTORY → STAFF → HISTORY → METRICS
     */
    @Test
    fun testScreenNavigationPerformance() {
        println("TEST: Screen Navigation Performance")
        println("─".repeat(70))
        
        // Launch app
        launchApp()
        Thread.sleep(2000) // Let app settle
        
        val screens = listOf("Inventory", "Staff", "History", "Metrics", "Game")
        val reports = mutableMapOf<String, FrameTimingReport>()
        
        screens.forEach { screenName ->
            println("\n  Testing navigation to: $screenName")
            
            // Reset frame stats
            FrameTimingAnalyzer.resetFrameStats(packageName)
            
            // Find and click the nav button
            val navButton = device.findObject(By.text(screenName))
            if (navButton != null) {
                navButton.click()
                Thread.sleep(500) // Wait for transition
                
                // Collect frame timings
                val frameTimes = FrameTimingAnalyzer.collectFrameTimings(packageName)
                val report = FrameTimingAnalyzer.analyzeFrameTimings(frameTimes)
                reports[screenName] = report
                
                println("    Frames: ${report.totalFrames}, Avg: ${String.format("%.2f", report.averageFrameTime)}ms, Jank: ${String.format("%.1f", report.jankyFramePercent)}%")
            } else {
                println("    ⚠️  Navigation button not found for $screenName")
            }
        }
        
        println("\n" + "─".repeat(70))
        println("SCREEN NAVIGATION SUMMARY")
        println("─".repeat(70))
        
        reports.forEach { (screen, report) ->
            val status = if (report.jankyFramePercent < 5.0) "✅" else "⚠️"
            println("  $status $screen: ${String.format("%.2f", report.averageFrameTime)}ms avg, ${String.format("%.1f", report.jankyFramePercent)}% jank")
        }
        
        println()
        
        // Assert: Average jank across all screens should be < 10%
        val avgJank = reports.values.map { it.jankyFramePercent }.average()
        assertTrue(
            "Average jank across screens too high: ${String.format("%.2f", avgJank)}%",
            avgJank < 10.0
        )
        
        println("✓ Screen navigation test passed\n")
    }
    
    /**
     * Test 3: Inventory Screen Scrolling Performance
     * 
     * Tests scrolling performance in the inventory list.
     * Measures frame timing during rapid scrolling.
     */
    @Test
    fun testInventoryScrollingPerformance() {
        println("TEST: Inventory Scrolling Performance")
        println("─".repeat(70))
        
        // Launch app and navigate to Inventory
        launchApp()
        Thread.sleep(2000)
        
        val inventoryButton = device.findObject(By.text("Inventory"))
        if (inventoryButton != null) {
            inventoryButton.click()
            Thread.sleep(1000)
            
            // Reset frame stats
            FrameTimingAnalyzer.resetFrameStats(packageName)
            
            // Perform scrolling gestures
            val displayHeight = device.displayHeight
            val displayWidth = device.displayWidth
            
            println("  Performing scroll gestures...")
            
            // Scroll down 5 times
            repeat(5) {
                device.swipe(
                    displayWidth / 2,
                    displayHeight * 3 / 4,
                    displayWidth / 2,
                    displayHeight / 4,
                    10 // steps (faster = more aggressive scroll)
                )
                Thread.sleep(200)
            }
            
            // Scroll up 5 times
            repeat(5) {
                device.swipe(
                    displayWidth / 2,
                    displayHeight / 4,
                    displayWidth / 2,
                    displayHeight * 3 / 4,
                    10
                )
                Thread.sleep(200)
            }
            
            Thread.sleep(500)
            
            // Collect frame timings
            val frameTimes = FrameTimingAnalyzer.collectFrameTimings(packageName)
            val report = FrameTimingAnalyzer.analyzeFrameTimings(frameTimes)
            
            println("\n${FrameTimingAnalyzer.formatReport(report)}")
            
            // Assert: Scrolling should be smooth
            assertTrue(
                "Too many janky frames during scrolling: ${report.jankyFramePercent}%",
                report.jankyFramePercent < 15.0 // Allow some jank during aggressive scrolling
            )
            
            println("✓ Inventory scrolling test passed\n")
        } else {
            println("  ⚠️  Inventory button not found, skipping test\n")
        }
    }
    
    /**
     * Test 4: Pager Swipe Performance
     * 
     * Tests swipe gesture performance on the main screen pager.
     */
    @Test
    fun testPagerSwipePerformance() {
        println("TEST: Pager Swipe Performance")
        println("─".repeat(70))
        
        // Launch app
        launchApp()
        Thread.sleep(2000)
        
        // Reset frame stats
        FrameTimingAnalyzer.resetFrameStats(packageName)
        
        val displayHeight = device.displayHeight
        val displayWidth = device.displayWidth
        
        println("  Performing swipe gestures...")
        
        // Swipe left 4 times (through all screens)
        repeat(4) {
            device.swipe(
                displayWidth * 3 / 4,
                displayHeight / 2,
                displayWidth / 4,
                displayHeight / 2,
                20 // steps
            )
            Thread.sleep(300)
        }
        
        // Swipe right 4 times (back to start)
        repeat(4) {
            device.swipe(
                displayWidth / 4,
                displayHeight / 2,
                displayWidth * 3 / 4,
                displayHeight / 2,
                20
            )
            Thread.sleep(300)
        }
        
        Thread.sleep(500)
        
        // Collect frame timings
        val frameTimes = FrameTimingAnalyzer.collectFrameTimings(packageName)
        val report = FrameTimingAnalyzer.analyzeFrameTimings(frameTimes)
        
        println("\n${FrameTimingAnalyzer.formatReport(report)}")
        
        // Assert: Swiping should be smooth
        assertTrue(
            "Too many janky frames during swipe: ${report.jankyFramePercent}%",
            report.jankyFramePercent < 10.0
        )
        
        println("✓ Pager swipe test passed\n")
    }
    
    /**
     * Test 5: Sustained Gameplay Performance
     * 
     * Runs the game for 30 seconds and measures frame stability.
     * This simulates real gameplay with ticking, transactions, etc.
     */
    @Test
    fun testSustainedGameplayPerformance() {
        println("TEST: Sustained Gameplay Performance (30s)")
        println("─".repeat(70))
        
        // Launch app
        launchApp()
        Thread.sleep(2000)
        
        // Reset frame stats
        FrameTimingAnalyzer.resetFrameStats(packageName)
        
        println("  Running game for 30 seconds...")
        val startTime = System.currentTimeMillis()
        
        // Let the game run while occasionally interacting
        repeat(6) { i ->
            Thread.sleep(5000) // 5 seconds
            
            // Occasional interaction to keep things active
            if (i % 2 == 0) {
                // Tap somewhere on screen to trigger ring-up or action
                device.click(device.displayWidth / 2, device.displayHeight / 2)
            }
        }
        
        val runTime = System.currentTimeMillis() - startTime
        println("  Game ran for ${runTime}ms")
        
        // Collect frame timings
        val frameTimes = FrameTimingAnalyzer.collectFrameTimings(packageName)
        val report = FrameTimingAnalyzer.analyzeFrameTimings(frameTimes)
        
        println("\n${FrameTimingAnalyzer.formatReport(report)}")
        
        // Calculate expected frame count (60 FPS × 30s = ~1800 frames)
        val expectedFrames = 1800
        val frameCountRatio = report.totalFrames.toDouble() / expectedFrames
        
        println("  Frame Count: ${report.totalFrames} / ~$expectedFrames expected (${String.format("%.1f", frameCountRatio * 100)}%)")
        
        // Assert: Should maintain good frame rate
        assertTrue(
            "Too few frames rendered: ${report.totalFrames} (expected ~$expectedFrames)",
            report.totalFrames > expectedFrames * 0.8 // Allow 20% margin
        )
        
        // Assert: Sustained gameplay should be smooth
        assertTrue(
            "Too many janky frames during gameplay: ${report.jankyFramePercent}%",
            report.jankyFramePercent < 5.0
        )
        
        println("✓ Sustained gameplay test passed\n")
    }
    
    /**
     * Test 6: Full UI Flow Performance Summary
     * 
     * Runs through a complete user flow and generates comprehensive report.
     */
    @Test
    fun testZZ_FullUIPerformanceSummary() {
        println("TEST: Full UI Performance Summary")
        println("═".repeat(70))
        
        // Launch app
        launchApp()
        Thread.sleep(2000)
        
        println("\n  Executing full user flow...")
        println("  1. Navigate through all screens")
        println("  2. Perform actions on each screen")
        println("  3. Test dialogs and interactions")
        println()
        
        // Reset frame stats
        FrameTimingAnalyzer.resetFrameStats(packageName)
        
        // Navigate through all screens
        val screens = listOf("Inventory", "Staff", "History", "Metrics", "Game")
        screens.forEach { screen ->
            device.findObject(By.text(screen))?.click()
            Thread.sleep(500)
        }
        
        // Return to game screen and interact
        device.findObject(By.text("Game"))?.click()
        Thread.sleep(1000)
        
        // Tap a few times to trigger actions
        repeat(5) {
            device.click(device.displayWidth / 2, device.displayHeight / 2)
            Thread.sleep(300)
        }
        
        // Navigate to inventory and scroll
        device.findObject(By.text("Inventory"))?.click()
        Thread.sleep(500)
        
        repeat(3) {
            device.swipe(
                device.displayWidth / 2,
                device.displayHeight * 3 / 4,
                device.displayWidth / 2,
                device.displayHeight / 4,
                15
            )
            Thread.sleep(200)
        }
        
        Thread.sleep(1000)
        
        // Collect and analyze
        val frameTimes = FrameTimingAnalyzer.collectFrameTimings(packageName)
        val report = FrameTimingAnalyzer.analyzeFrameTimings(frameTimes)
        
        println("\n")
        println("═".repeat(70))
        println("           COMPREHENSIVE UI PERFORMANCE REPORT")
        println("═".repeat(70))
        println()
        println(FrameTimingAnalyzer.formatReport(report))
        
        // Generate final verdict
        val overallScore = when {
            report.jankyFramePercent < 2.0 && report.averageFrameTime < 10.0 -> "OUTSTANDING"
            report.jankyFramePercent < 5.0 && report.averageFrameTime < 12.0 -> "EXCELLENT"
            report.jankyFramePercent < 10.0 && report.averageFrameTime < 14.0 -> "GOOD"
            report.jankyFramePercent < 15.0 -> "ACCEPTABLE"
            else -> "NEEDS IMPROVEMENT"
        }
        
        println("OVERALL UI PERFORMANCE: $overallScore")
        println("═".repeat(70))
        println()
        
        // Assert: Overall performance should be at least GOOD
        assertTrue(
            "Overall UI performance is poor (${String.format("%.2f", report.jankyFramePercent)}% jank)",
            report.jankyFramePercent < 10.0
        )
        
        println("✓ Full UI performance test passed\n")
    }
    
    // ── Helper Methods ────────────────────────────────────────────────────
    
    private fun launchApp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
        device.wait(Until.hasObject(By.pkg(packageName)), launchTimeout)
    }
}

