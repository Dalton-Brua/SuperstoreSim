package com.example.superstoresimulator.domain.time

import kotlinx.serialization.Serializable

@Serializable
data class GameTime(
    val totalMinutesElapsed: Long = 0  // Total game minutes since start
) {
    // Properties
    val hour: Int
        get() = ((totalMinutesElapsed / 60) % 24).toInt()
    
    val minute: Int
        get() = (totalMinutesElapsed % 60).toInt()
    
    val dayNumber: Int
        get() = (totalMinutesElapsed / 1440).toInt()  // 1440 minutes per day
    
    val dayOfWeek: Int
        get() = dayNumber % 7  // 0 = Monday, 6 = Sunday
    
    val weekNumber: Int
        get() = dayNumber / 7
    
    // Methods
    fun getTotalMinutesOfDay(): Int = hour * 60 + minute
    
    fun isOpen(openTimeMinutes: Int, closeTimeMinutes: Int): Boolean {
        val currentMin = getTotalMinutesOfDay()
        return currentMin >= openTimeMinutes && currentMin < closeTimeMinutes
    }
    
    fun addMinutes(minutes: Long): GameTime {
        return copy(totalMinutesElapsed = totalMinutesElapsed + minutes)
    }
    
    fun addSeconds(seconds: Long): GameTime {
        val minutes = seconds / 60
        return addMinutes(minutes)
    }
    
    fun getFormattedTime(): String {
        val hourStr = hour.toString().padStart(2, '0')
        val minStr = minute.toString().padStart(2, '0')
        return "${shortDayName(dayOfWeek)} $hourStr:$minStr"
    }

    fun getDayOfWeekName(): String = fullDayName(dayOfWeek)

    companion object {
        // 0 = Monday … 6 = Sunday; index with any day number, absolute or day-of-week
        val SHORT_DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val FULL_DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

        fun shortDayName(day: Int): String = SHORT_DAY_NAMES[day % 7]
        fun fullDayName(day: Int): String = FULL_DAY_NAMES[day % 7]
    }
}

