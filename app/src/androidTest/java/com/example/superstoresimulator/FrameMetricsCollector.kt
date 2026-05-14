package com.example.superstoresimulator

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import androidx.test.core.app.ActivityScenario
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Frame Metrics Collector for In-Test Performance Measurement
 * 
 * Uses Android's Window.OnFrameMetricsAvailableListener to collect real-time
 * frame timing data during instrumented tests.
 * 
 * Usage:
 * ```
 * val collector = FrameMetricsCollector()
 * collector.startCollecting(activity)
 * // ... perform UI operations ...
 * val frameTimes = collector.stopCollecting()
 * ```
 * 
 * Created: May 2, 2026
 */
class FrameMetricsCollector : Window.OnFrameMetricsAvailableListener {
    
    private val frameTimes = mutableListOf<Double>()
    private val isCollecting = AtomicBoolean(false)
    private var window: Window? = null
    private val handler = Handler(Looper.getMainLooper())
    
    companion object {
        private const val TAG = "FrameMetricsCollector"
    }
    
    /**
     * Starts collecting frame metrics from the given activity's window.
     * Must be called on the main thread or from instrumentation.
     */
    fun startCollecting(activity: Activity) {
        handler.post {
            try {
                window = activity.window
                frameTimes.clear()
                isCollecting.set(true)
                
                window?.addOnFrameMetricsAvailableListener(this, handler)
                Log.d(TAG, "Started collecting frame metrics")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start collecting: ${e.message}")
            }
        }
    }
    
    /**
     * Stops collecting frame metrics and returns the collected data.
     * Returns list of frame times in milliseconds.
     */
    fun stopCollecting(): List<Double> {
        isCollecting.set(false)
        
        handler.post {
            try {
                window?.removeOnFrameMetricsAvailableListener(this)
                Log.d(TAG, "Stopped collecting. Frames collected: ${frameTimes.size}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop collecting: ${e.message}")
            }
        }
        
        // Wait a bit for handler to process removal
        Thread.sleep(100)
        
        return frameTimes.toList()
    }
    
    /**
     * Called by Android framework when a frame is rendered.
     * Extracts total frame time and stores it.
     */
    override fun onFrameMetricsAvailable(
        window: Window?,
        frameMetrics: FrameMetrics?,
        dropCountSinceLastInvocation: Int
    ) {
        if (!isCollecting.get() || frameMetrics == null) return
        
        try {
            // Get total frame time (nanoseconds)
            // TOTAL_DURATION includes:
            // - Input handling
            // - Animation
            // - Layout/measure
            // - Draw
            // - GPU composition
            val totalDurationNs = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
            val frameTimeMs = totalDurationNs / 1_000_000.0
            
            if (frameTimeMs > 0 && frameTimeMs < 1000) { // Sanity check
                synchronized(frameTimes) {
                    frameTimes.add(frameTimeMs)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to process frame metrics: ${e.message}")
        }
    }
    
    /**
     * Returns current frame count without stopping collection.
     */
    fun getFrameCount(): Int = synchronized(frameTimes) { frameTimes.size }
}

