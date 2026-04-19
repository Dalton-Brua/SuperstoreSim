package com.example.superstoresimulator.domain.time

import com.example.superstoresimulator.domain.store.StoreConfig
import com.example.superstoresimulator.domain.store.StoreState

/**
 * Manages game time progression and store state
 */
class TimeManager(
    var config: StoreConfig = StoreConfig()
) {
    var currentTime = GameTime(0)
        private set
    
    // Accumulate fractional time to avoid rounding errors with small tick deltas
    private var accumulatedMilliseconds = 0L

    fun update(deltaMilliseconds: Long) {
        // Base speed: 60.0f = 1 real second = 1 game minute
        // gameSpeedMultiplier is the control multiplier (1x, 2x, 4x, etc.)
        val baseSpeed = 120.0f
        val acceleratedMilliseconds = deltaMilliseconds * baseSpeed * config.gameSpeedMultiplier
        accumulatedMilliseconds += acceleratedMilliseconds.toLong()
        
        // Convert accumulated ms to game minutes
        // 60,000 ms = 1 minute
        val gameMinutes = accumulatedMilliseconds / 60000L
        
        if (gameMinutes > 0) {
            currentTime = currentTime.addMinutes(gameMinutes)
            accumulatedMilliseconds -= (gameMinutes * 60000L)
        }
    }

    /**
     * Reset time to start of game (6 AM on day 0)
     */
    fun reset() {
        currentTime = GameTime(0)
        accumulatedMilliseconds = 0L
    }
    
    /**
     * Sync time manager to a specific GameTime (used when loading saved games)
     */
    fun syncTime(gameTime: GameTime) {
        currentTime = gameTime
        accumulatedMilliseconds = 0L
    }
    
    /**
     * Jump to specific time on current day (or next day if time has passed)
     */
    fun jumpToTime(hour: Int, minute: Int = 0): GameTime {
        val currentDayMin = currentTime.getTotalMinutesOfDay()
        val targetDayMin = hour * 60 + minute
        
        val newTime = if (targetDayMin <= currentDayMin) {
            // Jump to next day at target time
            currentTime.copy(
                totalMinutesElapsed = currentTime.totalMinutesElapsed + 
                    ((24 - currentTime.hour) * 60 - currentTime.minute) +
                    targetDayMin
            )
        } else {
            // Jump to later today
            currentTime.copy(
                totalMinutesElapsed = currentTime.totalMinutesElapsed +
                    (targetDayMin - currentDayMin)
            )
        }
        
        currentTime = newTime
        return currentTime
    }
    
    /**
     * Get current store state based on time
     */
    fun getStoreState(): StoreState {
        return when {
            config.isClosed(currentTime) -> StoreState.CLOSED
            config.isClosing(currentTime) -> StoreState.CLOSING
            config.isOpen(currentTime) -> StoreState.OPEN
            else -> StoreState.CLOSED
        }
    }
    
    /**
     * Set game speed multiplier (for speed controls 1x, 2x, 4x, etc.)
     */
    fun setSpeedMultiplier(multiplier: Float) {
        config = config.copy(gameSpeedMultiplier = multiplier)
    }
}

