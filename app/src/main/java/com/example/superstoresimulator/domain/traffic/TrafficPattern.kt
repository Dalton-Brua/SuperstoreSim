package com.example.superstoresimulator.domain.traffic

import com.example.superstoresimulator.domain.time.GameTime

/**
 * Traffic pattern for a specific hour of day.
 *
 * [baseCustomerRate] is customers per real-minute at 1× game speed.
 * TimeManager runs at a 120× base acceleration, so the effective interval is:
 *   realSeconds between customers = 60 / baseCustomerRate
 * Examples at 1× speed:
 *   1.5  → ~40 s  (very quiet)
 *   5.0  → ~12 s  (moderate)
 *  12.0  →  ~5 s  (peak rush)
 *  14.0  →  ~4 s  (evening rush peak)
 */
data class TrafficPattern(
    val hour: Int,
    val baseCustomerRate: Float,
    val averageBasketSize: Int,
    val peakMultiplier: Float = 1.0f
)

object TrafficSchedule {

    // ── WEEKDAY (Monday–Friday, dayOfWeek 0–4) ──────────────────────────────
    val WEEKDAY = listOf(
        // Early morning — very quiet
        TrafficPattern(6,  baseCustomerRate = 1.5f,  averageBasketSize = 3),
        TrafficPattern(7,  baseCustomerRate = 2.5f,  averageBasketSize = 3),
        // Morning build-up
        TrafficPattern(8,  baseCustomerRate = 4.0f,  averageBasketSize = 4),
        TrafficPattern(9,  baseCustomerRate = 5.0f,  averageBasketSize = 4),
        TrafficPattern(10, baseCustomerRate = 3.5f,  averageBasketSize = 3),
        // Pre-lunch
        TrafficPattern(11, baseCustomerRate = 5.0f,  averageBasketSize = 4),
        TrafficPattern(12, baseCustomerRate = 12.0f, averageBasketSize = 5), // LUNCH RUSH
        // Post-lunch
        TrafficPattern(13, baseCustomerRate = 8.0f,  averageBasketSize = 4),
        TrafficPattern(14, baseCustomerRate = 3.5f,  averageBasketSize = 3),
        TrafficPattern(15, baseCustomerRate = 4.0f,  averageBasketSize = 3),
        TrafficPattern(16, baseCustomerRate = 5.5f,  averageBasketSize = 4),
        // Evening rush
        TrafficPattern(17, baseCustomerRate = 14.0f, averageBasketSize = 5), // EVENING RUSH PEAK
        TrafficPattern(18, baseCustomerRate = 11.0f, averageBasketSize = 4),
        TrafficPattern(19, baseCustomerRate = 7.0f,  averageBasketSize = 4),
        // Winding down
        TrafficPattern(20, baseCustomerRate = 4.0f,  averageBasketSize = 3),
        TrafficPattern(21, baseCustomerRate = 2.0f,  averageBasketSize = 2),
    )

    // ── WEEKEND (Saturday–Sunday, dayOfWeek 5–6) ────────────────────────────
    val WEEKEND = listOf(
        // Late start — still quiet, but busier than a weekday morning
        TrafficPattern(6,  baseCustomerRate = 1.5f,  averageBasketSize = 2),
        TrafficPattern(7,  baseCustomerRate = 3.0f,  averageBasketSize = 4),
        TrafficPattern(8,  baseCustomerRate = 5.0f,  averageBasketSize = 5),
        TrafficPattern(9,  baseCustomerRate = 8.0f,  averageBasketSize = 5),
        // Morning rush — families and big-shop crowds arrive early
        TrafficPattern(10, baseCustomerRate = 12.0f, averageBasketSize = 5),
        TrafficPattern(11, baseCustomerRate = 16.0f, averageBasketSize = 6),
        TrafficPattern(12, baseCustomerRate = 20.0f, averageBasketSize = 7), // WEEKEND PEAK
        TrafficPattern(13, baseCustomerRate = 18.0f, averageBasketSize = 6),
        // Sustained afternoon — weekend shoppers linger all day
        TrafficPattern(14, baseCustomerRate = 16.0f, averageBasketSize = 6),
        TrafficPattern(15, baseCustomerRate = 14.0f, averageBasketSize = 5),
        TrafficPattern(16, baseCustomerRate = 15.0f, averageBasketSize = 5),
        // Weekend evening — still well above a weekday evening
        TrafficPattern(17, baseCustomerRate = 16.0f, averageBasketSize = 6),
        TrafficPattern(18, baseCustomerRate = 13.0f, averageBasketSize = 5),
        TrafficPattern(19, baseCustomerRate = 9.0f,  averageBasketSize = 5),
        // Winding down
        TrafficPattern(20, baseCustomerRate = 5.0f,  averageBasketSize = 4),
        TrafficPattern(21, baseCustomerRate = 2.5f,  averageBasketSize = 3),
    )

    fun getPatternForTime(gameTime: GameTime): TrafficPattern {
        val hour = gameTime.hour
        val isWeekend = gameTime.dayOfWeek in 5..6
        val schedule = if (isWeekend) WEEKEND else WEEKDAY
        return schedule.firstOrNull { it.hour == hour }
            ?: TrafficPattern(hour, 0.5f, 3, 1.0f)
    }
}
