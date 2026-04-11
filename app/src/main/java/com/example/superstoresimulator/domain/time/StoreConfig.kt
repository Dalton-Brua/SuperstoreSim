package com.example.superstoresimulator.domain.time

/**
 * Store configuration including opening/closing times
 */
data class StoreConfig(
    val openTimeMinutes: Int = 360,         // 6:00 AM
    val closeTimeMinutes: Int = 1260,       // 9:00 PM (21:00)
    val closingProcedureDuration: Int = 30, // 30 minutes
    val allowTransactionsDuringClosing: Boolean = true,
    val gameSpeedMultiplier: Float = 1.0f,  // Multiplier: 1x, 2x, 4x, etc. (base speed is 60.0f = 1 sec = 1 min)
    /**
     * Maximum units of a single item that can be stored in the backroom at once.
     * Prevents unlimited stockpiling via bulk or repeated single orders.
     *
     * Interim value (50) matches the SMALL_GROCERY cap planned in PROGRESSION_SYSTEM.md.
     * Will be superseded by StoreSize.maxBackroomPerItem once the Store Size system is implemented.
     */
    val backroomCapPerItem: Int = 50,
) {
    fun isOpen(gameTime: GameTime): Boolean {
        return gameTime.isOpen(openTimeMinutes, closeTimeMinutes)
    }
    
    fun isClosing(gameTime: GameTime): Boolean {
        val currentMin = gameTime.getTotalMinutesOfDay()
        return currentMin >= closeTimeMinutes && 
               currentMin < closeTimeMinutes + closingProcedureDuration
    }
    
    fun isClosed(gameTime: GameTime): Boolean {
        val currentMin = gameTime.getTotalMinutesOfDay()
        return currentMin < openTimeMinutes || 
               currentMin >= closeTimeMinutes + closingProcedureDuration
    }
}

/**
 * Represents the current state of the store
 */
enum class StoreState {
    CLOSED,              // Before opening time or after closing procedures
    OPENING,             // Opening procedures (not implemented in Phase 1)
    OPEN,                // Normal operations
    CLOSING,             // Grace period before close (existing customers finish)
    CLOSING_PROCEDURES   // After close (staff cleanup)
}

