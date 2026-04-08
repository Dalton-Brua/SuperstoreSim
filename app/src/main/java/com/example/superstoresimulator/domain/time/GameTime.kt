package com.example.superstoresimulator.domain.time

/**
 * Represents a point in time within the game.
 * Tracks time as total minutes elapsed since game start.
 */
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
        val dayStr = when (dayOfWeek) {
            0 -> "Mon"; 1 -> "Tue"; 2 -> "Wed"; 3 -> "Thu"
            4 -> "Fri"; 5 -> "Sat"; 6 -> "Sun"
            else -> "???"
        }
        return "$dayStr $hourStr:$minStr"
    }
    
    fun getDayOfWeekName(): String = when (dayOfWeek) {
        0 -> "Monday"; 1 -> "Tuesday"; 2 -> "Wednesday"; 3 -> "Thursday"
        4 -> "Friday"; 5 -> "Saturday"; 6 -> "Sunday"
        else -> "Unknown"
    }
}

