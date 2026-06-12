package com.example.superstoresimulator.domain.time

import com.example.superstoresimulator.domain.store.StoreConfig
import com.example.superstoresimulator.domain.store.StoreState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimeManager @Inject constructor() {
    var config: StoreConfig = StoreConfig()
    var currentTime = GameTime(0)
        private set
    
    // Accumulate fractional time to avoid rounding errors with small tick deltas
    internal var accumulatedMilliseconds = 0L

    fun update(deltaMilliseconds: Long) {
        // At 1x multiplier: 1 real second = 2 game minutes
        val baseSpeed = BASE_SPEED
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

    companion object {
        const val BASE_SPEED = 120.0f
    }
}

