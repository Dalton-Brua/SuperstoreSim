package com.example.superstoresimulator

/**
 * Analyzes collected frame timing data and produces statistical reports.
 *
 * Takes raw frame durations (in milliseconds) from [FrameMetricsCollector]
 * and computes percentiles, jank percentage, and outlier classification.
 */
object FrameTimingAnalyzer {

    private const val JANK_THRESHOLD_MS = 16.67 // 60 FPS target

    data class FrameTimingReport(
        val totalFrames: Int,
        val averageFrameTime: Double,
        val medianFrameTime: Double,
        val p90FrameTime: Double,
        val p95FrameTime: Double,
        val p99FrameTime: Double,
        val maxFrameTime: Double,
        val jankyFrameCount: Int,
        val jankyFramePercent: Double,
        val outliers: List<Outlier>,
    )

    data class Outlier(
        val frameIndex: Int,
        val frameTimeMs: Double,
        val severity: Severity,
    )

    enum class Severity(val label: String) {
        MINOR("Frame jank"),
        MODERATE("Slow frame, likely complex composable"),
        MAJOR("Expensive UI operation or layout pass"),
        SEVERE("Heavy computation or slow recomposition"),
        CRITICAL("Blocking issue (GC pause, I/O on main thread)"),
    }

    fun analyzeFrameTimings(frameTimes: List<Double>): FrameTimingReport {
        if (frameTimes.isEmpty()) {
            return FrameTimingReport(
                totalFrames = 0,
                averageFrameTime = 0.0,
                medianFrameTime = 0.0,
                p90FrameTime = 0.0,
                p95FrameTime = 0.0,
                p99FrameTime = 0.0,
                maxFrameTime = 0.0,
                jankyFrameCount = 0,
                jankyFramePercent = 0.0,
                outliers = emptyList(),
            )
        }

        val sorted = frameTimes.sorted()
        val janky = frameTimes.filter { it > JANK_THRESHOLD_MS }

        val outliers = frameTimes.mapIndexedNotNull { index, time ->
            if (time <= JANK_THRESHOLD_MS) return@mapIndexedNotNull null
            val severity = when {
                time > 100.0 -> Severity.CRITICAL
                time > 50.0 -> Severity.SEVERE
                time > 32.0 -> Severity.MAJOR
                time > 20.0 -> Severity.MODERATE
                else -> Severity.MINOR
            }
            Outlier(frameIndex = index, frameTimeMs = time, severity = severity)
        }

        return FrameTimingReport(
            totalFrames = frameTimes.size,
            averageFrameTime = frameTimes.average(),
            medianFrameTime = percentile(sorted, 50.0),
            p90FrameTime = percentile(sorted, 90.0),
            p95FrameTime = percentile(sorted, 95.0),
            p99FrameTime = percentile(sorted, 99.0),
            maxFrameTime = sorted.last(),
            jankyFrameCount = janky.size,
            jankyFramePercent = (janky.size.toDouble() / frameTimes.size) * 100.0,
            outliers = outliers,
        )
    }

    fun formatReport(report: FrameTimingReport): String {
        if (report.totalFrames == 0) {
            return "No frame data collected."
        }

        val sb = StringBuilder()
        sb.appendLine("================================================================")
        sb.appendLine("              FRAME TIMING ANALYSIS REPORT")
        sb.appendLine("================================================================")
        sb.appendLine()
        sb.appendLine("SUMMARY")
        sb.appendLine("----------------------------------------------------------------")
        sb.appendLine("  Total Frames Analyzed: ${report.totalFrames}")
        sb.appendLine("  Average Frame Time:    ${String.format("%.3f", report.averageFrameTime)}ms")
        sb.appendLine("  Median Frame Time:     ${String.format("%.3f", report.medianFrameTime)}ms")
        sb.appendLine()
        sb.appendLine("PERCENTILES")
        sb.appendLine("----------------------------------------------------------------")
        sb.appendLine("  90th Percentile:       ${String.format("%.3f", report.p90FrameTime)}ms${flag(report.p90FrameTime)}")
        sb.appendLine("  95th Percentile:       ${String.format("%.3f", report.p95FrameTime)}ms${flag(report.p95FrameTime)}")
        sb.appendLine("  99th Percentile:       ${String.format("%.3f", report.p99FrameTime)}ms${flag(report.p99FrameTime)}")
        sb.appendLine("  Maximum Frame Time:    ${String.format("%.3f", report.maxFrameTime)}ms${flag(report.maxFrameTime)}")
        sb.appendLine()
        sb.appendLine("JANK ANALYSIS (60 FPS = 16.67ms target)")
        sb.appendLine("----------------------------------------------------------------")
        sb.appendLine("  Janky Frames:          ${report.jankyFrameCount} / ${report.totalFrames}")
        sb.appendLine("  Jank Percentage:       ${String.format("%.2f", report.jankyFramePercent)}%")
        sb.appendLine("  Verdict:               ${verdict(report.jankyFramePercent)}")

        if (report.outliers.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("OUTLIERS (${report.outliers.size} detected)")
            sb.appendLine("----------------------------------------------------------------")
            val shown = report.outliers.sortedByDescending { it.frameTimeMs }.take(10)
            for (outlier in shown) {
                sb.appendLine("  Frame #${outlier.frameIndex}: ${outlier.severity.name}: ${outlier.severity.label} (${String.format("%.2f", outlier.frameTimeMs)}ms)")
            }
            if (report.outliers.size > 10) {
                sb.appendLine("  ... and ${report.outliers.size - 10} more outliers")
            }
        }

        sb.appendLine()
        sb.appendLine("================================================================")
        return sb.toString()
    }

    private fun percentile(sorted: List<Double>, p: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val index = (p / 100.0 * (sorted.size - 1)).coerceIn(0.0, (sorted.size - 1).toDouble())
        val lower = index.toInt()
        val upper = (lower + 1).coerceAtMost(sorted.size - 1)
        val fraction = index - lower
        return sorted[lower] + (sorted[upper] - sorted[lower]) * fraction
    }

    private fun flag(ms: Double): String = when {
        ms > 32.0 -> " ❌"
        ms > JANK_THRESHOLD_MS -> " ⚠️"
        else -> ""
    }

    private fun verdict(jankPercent: Double): String = when {
        jankPercent < 3.0 -> "✅ EXCELLENT"
        jankPercent < 5.0 -> "✅ GOOD"
        jankPercent < 10.0 -> "⚠️ ACCEPTABLE"
        jankPercent < 15.0 -> "⚠️ POOR - Needs attention"
        else -> "❌ POOR - IMMEDIATE ACTION REQUIRED"
    }
}
